/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.persistence.roundtrip;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apereo.portal.test.golden.DatabaseVendor;
import org.apereo.portal.test.golden.GoldenRenderer;

/**
 * The documented ways a database stores a value differently from HSQLDB, where the goldens are
 * recorded, with today's mappings (Hibernate 4.3 and the dialects {@code PortalDialectResolver}
 * picks). Each rule is applied to the <em>expected</em> value, so the round trip and the stored
 * rows still have to match exactly. A new difference fails the test until it is described here;
 * none of them is a blanket skip.
 */
final class VendorDifferences {

    /**
     * Oracle: {@code double precision} is {@code FLOAT(126)}, a decimal NUMBER, which holds
     * magnitudes from 1E-130 to just under 1E126 (ORA-01426 above). The edge values are capped to
     * that range on Oracle; everywhere else they are {@link Double#MAX_VALUE} and {@link
     * Double#MIN_NORMAL}.
     */
    static double storableDouble(DatabaseVendor vendor, double value) {
        if (vendor != DatabaseVendor.ORACLE || value == 0) {
            return value;
        }
        final double max = 9.999999999999998E125;
        final double min = 1.0E-130;
        final double magnitude = Math.min(max, Math.max(min, Math.abs(value)));
        return Math.copySign(magnitude, value);
    }

    /**
     * PostgreSQL: Hibernate binds {@code @Lob} strings as large objects, so these {@code text}
     * columns hold the OID of a large object, not the text (P2 confirmed 5.2 keeps doing this;
     * {@code vacuumlo} would delete them). If a Hibernate upgrade starts writing the text inline,
     * existing rows still hold OIDs: this check fails so the change is handled deliberately.
     */
    static final Map<String, java.util.Set<String>> POSTGRESQL_OID_LOB_COLUMNS =
            Collections.singletonMap(
                    "RawEventsDb",
                    new java.util.TreeSet<>(
                            java.util.Arrays.asList(
                                    "UP_ANALYTICS_EVENTS.EVENT_DATA", "UP_RAW_EVENTS.EVENT_DATA")));

    static void checkPostgresqlLobs(
            String persistenceUnit,
            java.util.Set<String> oidColumns,
            java.util.List<String> failures) {
        final java.util.Set<String> expected =
                POSTGRESQL_OID_LOB_COLUMNS.getOrDefault(persistenceUnit, Collections.emptySet());
        if (!expected.equals(oidColumns)) {
            failures.add(
                    "PostgreSQL large-object columns of "
                            + persistenceUnit
                            + ": expected "
                            + expected
                            + " but was "
                            + oidColumns);
        }
    }

    /**
     * The id, or part of a composite id, an entity is stored under. SQL Server converts a String id
     * to its code page like any other varchar ({@link DatabaseVendor#sqlServerVarchar}), so an
     * entity whose id has other characters (the UNICODE variants of {@code JdbcAuthDao.Table} and
     * of {@code MarketplaceRatingImpl}, keyed by user name) is found by the converted id only.
     * Other ids are stored as they are.
     */
    static Object storedId(DatabaseVendor vendor, Object id) {
        return vendor == DatabaseVendor.SQLSERVER && id instanceof String
                ? DatabaseVendor.sqlServerVarchar((String) id)
                : id;
    }

    /** What the database is documented to return for {@code value} after a round trip */
    static Object expectedAfterRoundTrip(DatabaseVendor vendor, Slot slot, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String && slot.type == String.class) {
            final String string = (String) value;
            if (slot.isFname()) {
                return string;
            }
            if ("nullSafeString".equals(slot.userType)) {
                // Stored with a '_' prefix, so an empty string survives on Oracle too
                return vendor == DatabaseVendor.SQLSERVER
                        ? DatabaseVendor.sqlServerVarchar(string)
                        : string;
            }
            return expectedString(vendor, string);
        }
        if (value instanceof Instant) {
            return Instant.ofEpochMilli(timestampMillis(vendor, ((Instant) value).toEpochMilli()));
        }
        if (value instanceof Timestamp) {
            return new Timestamp(timestampMillis(vendor, ((Timestamp) value).getTime()));
        }
        if (value instanceof Date) {
            return new Date(timestampMillis(vendor, ((Date) value).getTime()));
        }
        if (value instanceof Calendar) {
            final Calendar calendar = (Calendar) ((Calendar) value).clone();
            calendar.setTimeInMillis(timestampMillis(vendor, calendar.getTimeInMillis()));
            return calendar;
        }
        return value;
    }

    /**
     * Strings in plain {@code varchar}/{@code clob} columns.
     *
     * <ul>
     *   <li>Oracle stores the empty string as NULL.
     *   <li>SQL Server: Hibernate 4.3's SQLServer2005Dialect maps strings to {@code varchar}, not
     *       {@code nvarchar}, so the database converts them to its code page ({@link
     *       DatabaseVendor#sqlServerVarchar}). Columns using uPortal's nullSafeString or fname
     *       types are the same, but fnames are ASCII anyway.
     * </ul>
     */
    static String expectedString(DatabaseVendor vendor, String value) {
        return vendor.storedString(value);
    }

    /**
     * Maps a line of a round-trip golden (recorded on HSQLDB) to what {@code vendor} is documented
     * to produce: the stored rows ({@code row} lines, read by the column tags of the preceding
     * {@code table} line) and the instants in {@code lossy} lines get the same adjustments as
     * {@link #expectedAfterRoundTrip}.
     */
    static UnaryOperator<String> goldenLines(DatabaseVendor vendor) {
        if (vendor == DatabaseVendor.HSQLDB) {
            return UnaryOperator.identity();
        }
        return new UnaryOperator<String>() {
            private Map<String, String> tags = Collections.emptyMap();

            @Override
            public String apply(String line) {
                if (line.startsWith("table ")) {
                    tags =
                            StoredRows.tags(Collections.singletonList(line))
                                    .values()
                                    .iterator()
                                    .next();
                    return line;
                }
                if (line.startsWith("row ")) {
                    return row(vendor, line, tags);
                }
                if (line.startsWith("lossy ")) {
                    return instants(vendor, line);
                }
                return line;
            }
        };
    }

    private static final Pattern INSTANT =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?Z");
    private static final Pattern QUOTED = Pattern.compile("\"((?:[^\"\\\\]|\\\\u[0-9a-f]{4})*)\"");

    private static String instants(DatabaseVendor vendor, String line) {
        final Matcher matcher = INSTANT.matcher(line);
        final StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            final Instant instant = Instant.parse(matcher.group());
            matcher.appendReplacement(
                    result,
                    Instant.ofEpochMilli(timestampMillis(vendor, instant.toEpochMilli()))
                            .toString());
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String row(DatabaseVendor vendor, String line, Map<String, String> tags) {
        final int colon = line.indexOf(": ");
        final StringBuilder result = new StringBuilder(line.substring(0, colon + 2));
        boolean first = true;
        for (final String cell : line.substring(colon + 2).split(" \\| ", -1)) {
            final int equals = cell.indexOf('=');
            final String column = cell.substring(0, equals);
            final String value = cell.substring(equals + 1);
            if (!first) {
                result.append(" | ");
            }
            first = false;
            result.append(column).append('=').append(storedValue(vendor, tags.get(column), value));
        }
        return result.toString();
    }

    private static String storedValue(DatabaseVendor vendor, String tag, String value) {
        if (tag == null
                || StoredRows.NULL.equals(value)
                || value.startsWith("#")
                || value.startsWith("~")) {
            return value;
        }
        switch (tag) {
            case "s":
            case "lob":
                {
                    final Matcher matcher = QUOTED.matcher(value);
                    if (!matcher.matches()) {
                        // abbreviated: long ASCII text, which no vendor changes
                        return value;
                    }
                    final String expected =
                            expectedString(vendor, GoldenRenderer.unescape(matcher.group(1)));
                    return expected == null ? StoredRows.NULL : StoredRows.renderValue(expected);
                }
            case "ts":
                {
                    final LocalDateTime stored = LocalDateTime.parse(value);
                    final long millis = stored.toInstant(ZoneOffset.UTC).toEpochMilli();
                    return LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(timestampMillis(vendor, millis)),
                                    ZoneOffset.UTC)
                            .toString();
                }
            case "f":
                return String.valueOf(storableDouble(vendor, Double.parseDouble(value)));
            default:
                return value;
        }
    }

    /**
     * Timestamps ({@code Instant}, {@code java.util.Date}/{@code Calendar}):
     *
     * <ul>
     *   <li>MySQL: {@code datetime} without fractional seconds (MySQL5Dialect, MOD-I95); the server
     *       rounds to the nearest second.
     *   <li>MariaDB: the same column type, but the server truncates the fraction.
     *   <li>SQL Server: {@code datetime} (SQLServer2005Dialect) has a resolution of 1/300 s, so
     *       milliseconds round to .000, .003 or .007.
     * </ul>
     */
    static long timestampMillis(DatabaseVendor vendor, long millis) {
        final long second = Math.floorDiv(millis, 1000L) * 1000L;
        final long fraction = millis - second;
        switch (vendor) {
            case MYSQL:
                return fraction >= 500 ? second + 1000 : second;
            case MARIADB:
                return second;
            case SQLSERVER:
                {
                    final long ticks = Math.round(fraction * 3 / 10.0);
                    return second + Math.round(ticks * 10 / 3.0);
                }
            default:
                return millis;
        }
    }
}
