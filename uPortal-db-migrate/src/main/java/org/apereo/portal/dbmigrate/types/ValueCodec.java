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
package org.apereo.portal.dbmigrate.types;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.apereo.portal.dbmigrate.Vendor;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.types.ColumnType.Family;

/**
 * Reads and writes column values in a vendor-neutral Java form, and turns them into the canonical
 * bytes the verification hash is built from.
 *
 * <p>Values travel as Boolean, Long / BigDecimal, Double, String, byte[], LocalDate, LocalTime,
 * LocalDateTime or OffsetDateTime. Timestamps without a zone are read and written as {@link
 * LocalDateTime} (JDBC 4.2), never through {@link java.sql.Timestamp}, so the JVM time zone cannot
 * shift them, even inside a daylight-saving gap.
 */
// Precision handling works on the fraction of a second only, on purpose
@SuppressWarnings({"JavaLocalDateTimeGetNano", "JavaLocalTimeGetNano"})
public final class ValueCodec {

    private static final LocalDate EPOCH_DATE = LocalDate.of(1970, 1, 1);
    private static final MathContext DOUBLE_DIGITS = new MathContext(15);

    private ValueCodec() {}

    // ------------------------------------------------------------------ SQL fragments

    /**
     * The select-list expression for a column. A PostgreSQL large object is resolved to its bytes
     * on the server with {@code lo_get} (PostgreSQL's large-object API), in the same streaming
     * query, instead of one open/read/close round trip per row.
     */
    public static String selectExpression(ColumnType c, Dialect d) {
        String col = d.quote(c.column().name());
        if (c.postgresLargeObject()) {
            return "lo_get(CAST(" + col + " AS oid))";
        }
        return col;
    }

    /**
     * The VALUES placeholder for a column. For a PostgreSQL large object the server creates the
     * object from the bound bytes ({@code lo_from_bytea}) inside the batched INSERT; an OID-in-text
     * column then stores the OID as text, exactly as Hibernate 4.3 does for 5.17.
     */
    public static String placeholder(ColumnType c) {
        if (c.postgresOidText()) {
            return "CAST(lo_from_bytea(0, ?) AS text)";
        }
        if (c.postgresLargeObject()) {
            return "lo_from_bytea(0, ?)";
        }
        return "?";
    }

    // ------------------------------------------------------------------ read

    public static Object read(ResultSet rs, int i, ColumnType c, Family logical)
            throws SQLException {
        switch (logical) {
            case BOOLEAN:
                {
                    boolean b = rs.getBoolean(i);
                    return rs.wasNull() ? null : b;
                }
            case INTEGER:
                return readInteger(rs, i, c);
            case DECIMAL:
                if (c.family() == Family.INTEGER) {
                    Object v = readInteger(rs, i, c);
                    return v instanceof Long ? BigDecimal.valueOf((Long) v) : v;
                }
                return rs.getBigDecimal(i);
            case DOUBLE:
                {
                    double d = rs.getDouble(i);
                    return rs.wasNull() ? null : d;
                }
            case STRING:
                if (c.postgresLargeObject()) {
                    byte[] bytes = rs.getBytes(i);
                    return bytes == null ? null : decodeUtf8(bytes, c);
                }
                return rs.getString(i);
            case BINARY:
                return rs.getBytes(i);
            case DATE:
                if (c.family() == Family.DATE) {
                    return rs.getObject(i, LocalDate.class);
                }
                {
                    LocalDateTime ldt = rs.getObject(i, LocalDateTime.class);
                    return ldt == null ? null : ldt.toLocalDate();
                }
            case TIME:
                if (c.family() == Family.TIME) {
                    return rs.getObject(i, LocalTime.class);
                }
                {
                    LocalDateTime ldt = rs.getObject(i, LocalDateTime.class);
                    return ldt == null ? null : ldt.toLocalTime();
                }
            case TIMESTAMP:
                return rs.getObject(i, LocalDateTime.class);
            case TIMESTAMP_TZ:
                return rs.getObject(i, OffsetDateTime.class);
            default:
                return rs.getObject(i);
        }
    }

    private static Object readInteger(ResultSet rs, int i, ColumnType c) throws SQLException {
        switch (c.column().jdbcType()) {
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
                {
                    long v = rs.getLong(i);
                    return rs.wasNull() ? null : v;
                }
            default:
                BigDecimal bd = rs.getBigDecimal(i);
                if (bd == null) {
                    return null;
                }
                BigInteger bi = bd.toBigIntegerExact();
                return bi.bitLength() < 64 ? (Object) bi.longValue() : new BigDecimal(bi);
        }
    }

    private static String decodeUtf8(byte[] bytes, ColumnType c) throws SQLException {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new SQLException(
                    "Large object in " + c.column().name() + " is not valid UTF-8 text", e);
        }
    }

    // ------------------------------------------------------------------ write

    /**
     * Converts a value to what the target column can store: fractional seconds truncated to its
     * precision (SQL Server datetime: rounded to its 1/300 s grid), '' to NULL on Oracle.
     */
    public static Object adapt(Object v, ColumnPair pair) {
        if (v == null) {
            return null;
        }
        ColumnType t = pair.target();
        switch (pair.logical()) {
            case STRING:
                if (t.emptyStringIsNull() && "".equals(v)) {
                    return null;
                }
                return v;
            case TIME:
                return t.sqlServerDatetime()
                        ? sqlServerDatetime(LocalDateTime.of(EPOCH_DATE, (LocalTime) v))
                                .toLocalTime()
                        : truncate((LocalTime) v, t.fractionDigits());
            case TIMESTAMP:
                return t.sqlServerDatetime()
                        ? sqlServerDatetime((LocalDateTime) v)
                        : truncate((LocalDateTime) v, t.fractionDigits());
            case TIMESTAMP_TZ:
                {
                    OffsetDateTime o = (OffsetDateTime) v;
                    return o.withNano(truncateNanos(o.getNano(), t.fractionDigits()));
                }
            default:
                return v;
        }
    }

    public static void write(PreparedStatement ps, int i, ColumnType c, Family logical, Object v)
            throws SQLException {
        if (v == null) {
            int sqlType = c.postgresLargeObject() ? Types.BINARY : c.column().jdbcType();
            ps.setNull(i, sqlType);
            return;
        }
        switch (logical) {
            case BOOLEAN:
                if (c.family() == Family.BOOLEAN) {
                    ps.setBoolean(i, (Boolean) v);
                } else {
                    ps.setInt(i, ((Boolean) v) ? 1 : 0);
                }
                break;
            case INTEGER:
                if (v instanceof Long) {
                    ps.setLong(i, (Long) v);
                } else {
                    ps.setBigDecimal(i, (BigDecimal) v);
                }
                break;
            case DECIMAL:
                ps.setBigDecimal(i, (BigDecimal) v);
                break;
            case DOUBLE:
                ps.setDouble(i, (Double) v);
                break;
            case STRING:
                {
                    String s = (String) v;
                    if (c.postgresLargeObject()) {
                        ps.setBytes(i, s.getBytes(StandardCharsets.UTF_8));
                    } else if (c.largeText()) {
                        ps.setCharacterStream(i, new StringReader(s), s.length());
                    } else {
                        ps.setString(i, s);
                    }
                    break;
                }
            case BINARY:
                {
                    byte[] b = (byte[]) v;
                    if (c.largeBinary()) {
                        ps.setBinaryStream(i, new ByteArrayInputStream(b), b.length);
                    } else {
                        ps.setBytes(i, b);
                    }
                    break;
                }
            case DATE:
                if (c.family() == Family.DATE) {
                    ps.setObject(i, v);
                } else {
                    ps.setObject(i, ((LocalDate) v).atStartOfDay());
                }
                break;
            case TIME:
                if (c.family() == Family.TIME) {
                    ps.setObject(i, v);
                } else {
                    // Hibernate stores a java.sql.Time in a datetime/DATE column on 1970-01-01
                    ps.setObject(i, LocalDateTime.of(EPOCH_DATE, (LocalTime) v));
                }
                break;
            default:
                if (c.vendor() == Vendor.POSTGRESQL && v instanceof String) {
                    // PostgreSQL types without a JDBC family (jsonb, uuid, ...) take text this way
                    ps.setObject(i, v, Types.OTHER);
                } else {
                    ps.setObject(i, v);
                }
        }
    }

    // ------------------------------------------------------------------ normalize

    /**
     * The canonical bytes of a value for the verification hash, or null for NULL. Both sides of a
     * pair are normalized the same way, at the lower precision of the two columns.
     */
    public static byte[] normalize(Object v, ColumnPair pair) {
        if (v == null) {
            return null;
        }
        switch (pair.logical()) {
            case BOOLEAN:
                return new byte[] {(Boolean) v ? (byte) 'T' : (byte) 'F'};
            case INTEGER:
                return ascii(
                        v instanceof Long
                                ? v.toString()
                                : ((BigDecimal) v).toBigInteger().toString());
            case DECIMAL:
                return ascii(plain((BigDecimal) v));
            case DOUBLE:
                return ascii(normalizeDouble((Double) v));
            case STRING:
                {
                    String s = (String) v;
                    if (pair.trimTrailingSpaces()) {
                        s = trimTrailing(s);
                    }
                    if (pair.emptyStringIsNull() && s.isEmpty()) {
                        return null;
                    }
                    return s.getBytes(StandardCharsets.UTF_8);
                }
            case BINARY:
                return (byte[]) v;
            case DATE:
                return ascii(v.toString());
            case TIME:
                {
                    LocalTime t = (LocalTime) v;
                    if (pair.sqlServerGrid()) {
                        t = sqlServerDatetime(LocalDateTime.of(EPOCH_DATE, t)).toLocalTime();
                    }
                    t = truncate(t, pair.fractionDigits());
                    return ascii(t.toNanoOfDay() + "n");
                }
            case TIMESTAMP:
                {
                    // A timestamp without zone is compared as the instant it denotes in UTC; no
                    // JVM or session zone takes part
                    LocalDateTime t = (LocalDateTime) v;
                    // On SQL Server's 1/300 s grid first, then at the pair's precision: from a
                    // datetime into whole seconds the copy truncates .997 to the second
                    if (pair.sqlServerGrid()) {
                        t = sqlServerDatetime(t);
                    }
                    t = truncate(t, pair.fractionDigits());
                    return instant(t.toEpochSecond(ZoneOffset.UTC), t.getNano());
                }
            case TIMESTAMP_TZ:
                {
                    OffsetDateTime o = (OffsetDateTime) v;
                    int nanos = truncateNanos(o.getNano(), pair.fractionDigits());
                    return instant(o.toEpochSecond(), nanos);
                }
            default:
                return ascii(String.valueOf(v));
        }
    }

    static String plain(BigDecimal d) {
        if (d.signum() == 0) {
            return "0";
        }
        return d.stripTrailingZeros().toPlainString();
    }

    /**
     * 15 significant digits: every double survives a round trip through that many, and Oracle's
     * decimal FLOAT may not keep the 17th.
     */
    static String normalizeDouble(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return Double.toString(d);
        }
        if (d == 0) {
            return "0";
        }
        return plain(new BigDecimal(d).round(DOUBLE_DIGITS));
    }

    static String trimTrailing(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }

    private static byte[] instant(long epochSecond, int nanos) {
        return ascii(epochSecond + "." + nanos);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    // ------------------------------------------------------------------ precision

    static int truncateNanos(int nanos, int digits) {
        if (digits >= 9) {
            return nanos;
        }
        int factor = 1;
        for (int k = digits; k < 9; k++) {
            factor *= 10;
        }
        return nanos - nanos % factor;
    }

    public static LocalDateTime truncate(LocalDateTime t, int digits) {
        return t.withNano(truncateNanos(t.getNano(), digits));
    }

    public static LocalTime truncate(LocalTime t, int digits) {
        return t.withNano(truncateNanos(t.getNano(), digits));
    }

    /**
     * SQL Server {@code datetime} keeps 1/300 s ticks and drivers read them back rounded to the
     * millisecond (.000, .003, .007): {@code 23:59:59.999} becomes the next day's {@code
     * 00:00:00.000}, {@code .995} becomes {@code .997}.
     */
    public static LocalDateTime sqlServerDatetime(LocalDateTime t) {
        long ticks = Math.round(t.getNano() * 300.0 / 1_000_000_000.0);
        long millis = Math.round(ticks * 1000.0 / 300.0);
        return t.withNano(0).plusNanos(millis * 1_000_000L);
    }
}
