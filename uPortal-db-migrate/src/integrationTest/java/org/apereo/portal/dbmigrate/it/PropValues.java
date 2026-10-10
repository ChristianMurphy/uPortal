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
package org.apereo.portal.dbmigrate.it;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.RandomDistribution;
import net.jqwik.api.Tuple;
import org.apereo.portal.dbmigrate.it.DbServer.Kind;

/**
 * Generators of column values for the property tests, including the edge cases a copy between
 * vendors gets wrong first: empty versus NULL, maximal lengths, multi-byte text and emoji, control
 * characters, extreme numbers, -0.0, timestamps in a daylight-saving gap or overlap of the JVM's
 * zone (America/New_York), before 1970, around the Julian/Gregorian switch, and at the end of year
 * 9999.
 *
 * <p>Every value is one that every server of the run can store, so the same rows serve every vendor
 * pair: e.g. no U+0000 (PostgreSQL text cannot hold it), dates from year 1000 (MySQL), {@code
 * datetime} values from 1753 when SQL Server takes part. Values outside those limits are the
 * subject of the fail-closed tests instead.
 */
public final class PropValues {

    /** The JVM zone's 2026 DST gap (02:00-03:00 does not exist) and overlap (01:00-02:00 twice). */
    static final LocalDateTime DST_GAP = LocalDateTime.of(2026, 3, 8, 2, 30, 0, 123_456_000);

    static final LocalDateTime DST_OVERLAP = LocalDateTime.of(2026, 11, 1, 1, 30, 0, 654_321_000);

    private final List<Kind> kinds;
    private final int maxLob;

    /**
     * @param kinds the servers of the run (values must suit all of them)
     * @param maxLob the largest CLOB / BLOB value, in characters or bytes
     */
    public PropValues(Collection<Kind> kinds, int maxLob) {
        this.kinds = new ArrayList<>(kinds);
        this.maxLob = maxLob;
    }

    private boolean has(Kind k) {
        return kinds.contains(k);
    }

    // ------------------------------------------------------------------ rows

    /** Rows of every column, ids 1..n. */
    public Arbitrary<List<Map<PropColumn, Object>>> rows(int maxRows) {
        List<Arbitrary<Object>> columns = new ArrayList<>();
        for (PropColumn c : PropColumn.values()) {
            if (c != PropColumn.ID) {
                columns.add(column(c).injectNull(0.08));
            }
        }
        Arbitrary<Map<PropColumn, Object>> row =
                Combinators.combine(columns)
                        .as(
                                values -> {
                                    Map<PropColumn, Object> r = new EnumMap<>(PropColumn.class);
                                    int i = 0;
                                    for (PropColumn c : PropColumn.values()) {
                                        if (c != PropColumn.ID) {
                                            r.put(c, values.get(i++));
                                        }
                                    }
                                    return r;
                                });
        return row.list().ofMinSize(1).ofMaxSize(maxRows).map(PropValues::numbered);
    }

    private static List<Map<PropColumn, Object>> numbered(List<Map<PropColumn, Object>> rows) {
        List<Map<PropColumn, Object>> out = new ArrayList<>();
        long id = 1;
        for (Map<PropColumn, Object> r : rows) {
            Map<PropColumn, Object> copy = new EnumMap<>(r);
            copy.put(PropColumn.ID, id++);
            out.add(copy);
        }
        return out;
    }

    /**
     * Every edge case of every column, at least once: row i takes edge case i of each column
     * (cycling), plus a row of NULLs.
     */
    public List<Map<PropColumn, Object>> edgeRows() {
        int n = 0;
        Map<PropColumn, List<Object>> edges = new EnumMap<>(PropColumn.class);
        for (PropColumn c : PropColumn.values()) {
            if (c != PropColumn.ID) {
                edges.put(c, edges(c));
                n = Math.max(n, edges.get(c).size());
            }
        }
        List<Map<PropColumn, Object>> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<PropColumn, Object> r = new EnumMap<>(PropColumn.class);
            for (Map.Entry<PropColumn, List<Object>> e : edges.entrySet()) {
                List<Object> list = e.getValue();
                r.put(e.getKey(), list.get(i % list.size()));
            }
            rows.add(r);
        }
        Map<PropColumn, Object> nulls = new EnumMap<>(PropColumn.class);
        for (PropColumn c : edges.keySet()) {
            nulls.put(c, null);
        }
        rows.add(nulls);
        return numbered(rows);
    }

    // ------------------------------------------------------------------ columns

    @SuppressWarnings("unchecked")
    Arbitrary<Object> column(PropColumn c) {
        Arbitrary<?> random = random(c);
        List<Object> edges = edges(c);
        return (Arbitrary<Object>)
                Arbitraries.frequencyOf(
                        Tuple.of(3, Arbitraries.of(edges)),
                        Tuple.of(7, (Arbitrary<Object>) random));
    }

    private Arbitrary<?> random(PropColumn c) {
        switch (c) {
            case I16:
                return Arbitraries.shorts().map(Short::longValue);
            case I32:
                return Arbitraries.integers()
                        .withDistribution(RandomDistribution.uniform())
                        .map(Integer::longValue);
            case I64:
                return Arbitraries.longs().withDistribution(RandomDistribution.uniform());
            case AMOUNT:
                return Arbitraries.bigDecimals()
                        .between(maxAmount().negate(), maxAmount())
                        .ofScale(10);
            case RATIO:
                return Arbitraries.longs()
                        .withDistribution(RandomDistribution.uniform())
                        .map(Double::longBitsToDouble)
                        .filter(this::storable);
            case FLAG:
                return Arbitraries.of(true, false);
            case CODE:
            case S1:
            case S255:
            case S4000:
                return text(c.length(), varcharBytes(c));
            case BODY:
            case LOB_TEXT:
                return text(maxLob, Integer.MAX_VALUE);
            case PAYLOAD:
            case LOB_DATA:
                return Arbitraries.bytes().array(byte[].class).ofMaxSize(maxLob);
            case DAY_VALUE:
                return Arbitraries.longs()
                        .between(minDate().toEpochDay(), LocalDate.of(9999, 12, 31).toEpochDay())
                        .withDistribution(RandomDistribution.uniform())
                        .map(LocalDate::ofEpochDay);
            case AT_TIME:
                return Arbitraries.longs()
                        .between(0, LocalTime.MAX.toNanoOfDay() / 1000)
                        .withDistribution(RandomDistribution.uniform())
                        .map(us -> LocalTime.ofNanoOfDay(us * 1000));
            case TS0:
            case TS3:
            case TS6:
            case TS517:
                return timestamps(minTimestamp(c), maxTimestamp(c));
            case TSTZ:
                return Combinators.combine(
                                timestamps(
                                        LocalDateTime.of(1000, 1, 2, 0, 0),
                                        LocalDateTime.of(9999, 12, 30, 0, 0)),
                                Arbitraries.integers()
                                        .between(-14 * 60, 14 * 60)
                                        .withDistribution(RandomDistribution.uniform()))
                        .as((t, minutes) -> t.atOffset(ZoneOffset.ofTotalSeconds(minutes * 60)));
            case GUID:
                return Arbitraries.longs().tuple2().map(t -> new UUID(t.get1(), t.get2()));
            default:
                throw new IllegalArgumentException(c.toString());
        }
    }

    /** Timestamps to the microsecond (no server keeps more) between two values. */
    private static Arbitrary<LocalDateTime> timestamps(LocalDateTime from, LocalDateTime to) {
        LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 0, 0);
        return Arbitraries.longs()
                .between(
                        ChronoUnit.MICROS.between(epoch, from),
                        ChronoUnit.MICROS.between(epoch, to))
                // jqwik's default leans towards one end of a range
                .withDistribution(RandomDistribution.uniform())
                .map(us -> epoch.plus(us, ChronoUnit.MICROS));
    }

    List<Object> edges(PropColumn c) {
        switch (c) {
            case I16:
                return list(0L, -1L, 1L, (long) Short.MIN_VALUE, (long) Short.MAX_VALUE);
            case I32:
                return list(0L, -1L, (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE);
            case I64:
                return list(
                        0L,
                        -1L,
                        Long.MIN_VALUE,
                        Long.MAX_VALUE,
                        (1L << 53) + 1, // not a double: must not travel as one
                        -(1L << 53) - 1);
            case AMOUNT:
                return list(
                        BigDecimal.ZERO,
                        new BigDecimal("0.0000000001"),
                        new BigDecimal("-0.0000000001"),
                        maxAmount(),
                        maxAmount().negate(),
                        new BigDecimal("12345678901234567890.1234567890"),
                        new BigDecimal("1E+20"),
                        new BigDecimal("100.5000000000"));
            case RATIO:
                {
                    List<Object> d =
                            list(
                                    0.0,
                                    1.0,
                                    -1.0,
                                    0.1,
                                    0.1 + 0.2,
                                    1.0 / 3,
                                    Math.PI,
                                    -Math.E,
                                    9007199254740993.0,
                                    123456789.123456789,
                                    1e-100,
                                    -1e100);
                    for (double x :
                            new double[] {
                                -0.0,
                                Double.MAX_VALUE,
                                -Double.MAX_VALUE,
                                Double.MIN_NORMAL,
                                Double.MIN_VALUE,
                                1e-300,
                                1e125,
                                1e-129
                            }) {
                        if (storable(x)) {
                            d.add(x);
                        }
                    }
                    return d;
                }
            case FLAG:
                return list(true, false);
            case CODE:
            case S1:
            case S255:
            case S4000:
                return stringEdges(c.length(), varcharBytes(c));
            case BODY:
            case LOB_TEXT:
                {
                    List<Object> s = stringEdges(64, Integer.MAX_VALUE);
                    s.add(repeat("Grüße ✓ 𝄞 line\r\n", maxLob / 16));
                    return s;
                }
            case PAYLOAD:
            case LOB_DATA:
                {
                    byte[] all = new byte[256];
                    for (int i = 0; i < all.length; i++) {
                        all[i] = (byte) i;
                    }
                    byte[] big = new byte[maxLob];
                    Arrays.fill(big, (byte) 0xFF);
                    big[big.length / 2] = 0;
                    return list(new byte[0], new byte[] {0}, all, big);
                }
            case DAY_VALUE:
                {
                    List<Object> d =
                            list(
                                    minDate(),
                                    LocalDate.of(1582, 10, 15), // first Gregorian day
                                    LocalDate.of(1883, 11, 18), // US railway time zones
                                    LocalDate.of(1899, 12, 31),
                                    LocalDate.of(1900, 1, 1),
                                    LocalDate.of(1969, 12, 31),
                                    LocalDate.of(1970, 1, 1),
                                    LocalDate.of(2000, 2, 29),
                                    DST_GAP.toLocalDate(),
                                    DST_OVERLAP.toLocalDate(),
                                    LocalDate.of(2038, 1, 19),
                                    LocalDate.of(9999, 12, 31));
                    if (minDate().getYear() <= 1582) {
                        d.add(LocalDate.of(1582, 10, 4)); // last Julian day
                        // The day after the Julian calendar's (not the Gregorian) 1500-02-29: a
                        // driver going through java.util.Calendar lands a day off
                        d.add(LocalDate.of(1500, 3, 1));
                    }
                    d.removeIf(x -> x == null || ((LocalDate) x).isBefore(minDate()));
                    return d;
                }
            case AT_TIME:
                return list(
                        LocalTime.MIDNIGHT,
                        LocalTime.of(23, 59, 59, 999_999_000),
                        LocalTime.of(12, 0, 0, 500_000_000),
                        DST_GAP.toLocalTime(),
                        DST_OVERLAP.toLocalTime(),
                        LocalTime.of(0, 0, 0, 1000));
            case TS0:
            case TS3:
            case TS6:
            case TS517:
                {
                    List<Object> t =
                            list(
                                    LocalDateTime.of(1000, 1, 1, 0, 0),
                                    LocalDateTime.of(1582, 10, 4, 12, 0),
                                    LocalDateTime.of(1582, 10, 15, 0, 0),
                                    LocalDateTime.of(1753, 1, 1, 0, 0),
                                    // New York's last local mean time second (LMT -4:56:02)
                                    LocalDateTime.of(1883, 11, 18, 12, 3, 57),
                                    LocalDateTime.of(1900, 1, 1, 0, 0),
                                    LocalDateTime.of(1969, 12, 31, 23, 59, 59, 999_999_000),
                                    LocalDateTime.of(1970, 1, 1, 0, 0),
                                    LocalDateTime.of(1970, 1, 1, 0, 0, 0, 1000),
                                    LocalDateTime.of(2026, 3, 8, 2, 0),
                                    DST_GAP,
                                    LocalDateTime.of(2026, 3, 8, 2, 59, 59, 999_999_000),
                                    DST_OVERLAP,
                                    LocalDateTime.of(2026, 11, 1, 1, 59, 59, 999_999_000),
                                    LocalDateTime.of(2026, 10, 10, 12, 0, 0, 500_000_000),
                                    LocalDateTime.of(2038, 1, 19, 3, 14, 8),
                                    LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000));
                    t.removeIf(
                            x ->
                                    ((LocalDateTime) x).isBefore(minTimestamp(c))
                                            || ((LocalDateTime) x).isAfter(maxTimestamp(c)));
                    return t;
                }
            case TSTZ:
                return list(
                        DST_GAP.atOffset(ZoneOffset.ofHours(-5)),
                        DST_GAP.atOffset(ZoneOffset.UTC),
                        DST_OVERLAP.atOffset(ZoneOffset.ofHoursMinutes(5, 45)),
                        LocalDateTime.of(1969, 12, 31, 23, 59, 59, 999_999_000)
                                .atOffset(ZoneOffset.ofHoursMinutes(-9, -30)),
                        LocalDateTime.of(1900, 1, 1, 0, 0).atOffset(ZoneOffset.ofHours(14)),
                        LocalDateTime.of(9999, 12, 30, 0, 0).atOffset(ZoneOffset.ofHours(-12)));
            case GUID:
                return list(
                        new UUID(0, 0),
                        new UUID(-1, -1),
                        UUID.fromString("0192f0a0-8b5e-7c3a-9f4d-0123456789ab"));
            default:
                throw new IllegalArgumentException(c.toString());
        }
    }

    // ------------------------------------------------------------------ limits of the run

    /** DECIMAL(38,10)'s largest value. */
    static BigDecimal maxAmount() {
        return new BigDecimal(BigInteger.TEN.pow(38).subtract(BigInteger.ONE), 10);
    }

    /** MySQL documents DATE and DATETIME from year 1000. */
    LocalDate minDate() {
        return has(Kind.MYSQL) || has(Kind.MARIADB)
                ? LocalDate.of(1000, 1, 1)
                : LocalDate.of(1, 1, 1);
    }

    LocalDateTime minTimestamp(PropColumn c) {
        if (c == PropColumn.TS517 && has(Kind.SQLSERVER)) {
            return LocalDateTime.of(1753, 1, 1, 0, 0); // datetime
        }
        return minDate().atStartOfDay();
    }

    LocalDateTime maxTimestamp(PropColumn c) {
        if (c == PropColumn.TS517 && has(Kind.SQLSERVER)) {
            // datetime rounds to 1/300 s; .999 would round into year 10000
            return LocalDateTime.of(9999, 12, 31, 23, 59, 59, 997_000_000);
        }
        return LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000);
    }

    /** Doubles every server of the run stores (NaN and infinities: see the fail-closed tests). */
    boolean storable(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return false;
        }
        double abs = Math.abs(d);
        if (has(Kind.ORACLE)) {
            // 5.17's "double precision" is a decimal NUMBER: 1e-130 to below 1e126
            return d == 0 ? !isNegativeZero(d) : abs >= 1e-129 && abs < 9.99e125;
        }
        if (has(Kind.SQLSERVER) && d != 0 && abs < Double.MIN_NORMAL) {
            return false; // float has no subnormal numbers
        }
        return true;
    }

    static boolean isNegativeZero(double d) {
        return d == 0 && Double.doubleToRawLongBits(d) != 0;
    }

    /** Oracle's VARCHAR2 holds 4000 bytes at most, whatever the length in characters. */
    int varcharBytes(PropColumn c) {
        return has(Kind.ORACLE) ? Math.min(4000, c.length() * 4) : Integer.MAX_VALUE;
    }

    // ------------------------------------------------------------------ text

    /**
     * Strings of at most {@code maxUnits} UTF-16 units (SQL Server counts those; the others count
     * characters) and {@code maxBytes} UTF-8 bytes, from a mix of ASCII, whitespace and control
     * characters, accented and non-Latin letters, combining marks, right-to-left text and
     * supplementary characters (emoji, musical symbols). No U+0000 and no unpaired surrogates.
     */
    static Arbitrary<String> text(int maxUnits, int maxBytes) {
        Arbitrary<Integer> codePoint =
                Arbitraries.frequencyOf(
                        Tuple.of(40, Arbitraries.integers().between(0x20, 0x7E)),
                        Tuple.of(
                                6,
                                Arbitraries.of(
                                        0x09, 0x0A, 0x0D, 0x01, 0x07, 0x08, 0x0B, 0x0C, 0x1B, 0x1F,
                                        0x7F, 0x85, 0x9F, 0xA0, 0x2028, 0x2029)),
                        Tuple.of(10, Arbitraries.integers().between(0xA1, 0x24F)),
                        Tuple.of(4, Arbitraries.integers().between(0x300, 0x36F)),
                        Tuple.of(6, Arbitraries.integers().between(0x370, 0x6FF)),
                        Tuple.of(6, Arbitraries.integers().between(0x4E00, 0x9FFF)),
                        Tuple.of(
                                3,
                                Arbitraries.of(
                                        0x200B, 0x200D, 0x200E, 0x202E, 0xFEFF, 0xFFFD, 0xFFFF)),
                        Tuple.of(6, Arbitraries.integers().between(0x1F300, 0x1FAFF)),
                        Tuple.of(2, Arbitraries.integers().between(0x10000, 0x10FFFF)));
        int maxCodePoints = Math.min(maxUnits, 20_000);
        return codePoint.list().ofMaxSize(maxCodePoints).map(cps -> fit(cps, maxUnits, maxBytes));
    }

    private static String fit(List<Integer> codePoints, int maxUnits, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        int bytes = 0;
        for (int cp : codePoints) {
            int units = Character.charCount(cp);
            int b = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (sb.length() + units > maxUnits || bytes + b > maxBytes) {
                break;
            }
            sb.appendCodePoint(cp);
            bytes += b;
        }
        return sb.toString();
    }

    private static List<Object> stringEdges(int maxUnits, int maxBytes) {
        List<Object> s =
                list(
                        "",
                        " ",
                        "a",
                        "é",
                        "\t\n\r",
                        "\u0001\u001F\u007F",
                        "é",
                        "👨‍👩‍👧",
                        "𝄞",
                        "﻿bom",
                        "�￿",
                        "a  ",
                        "  a",
                        "'; DROP TABLE UP_PROP; --",
                        "\\",
                        "%_\"",
                        "עברית",
                        "中文",
                        repeat("x", maxUnits),
                        repeat("é", maxUnits),
                        repeat("€", maxUnits),
                        repeat("😀", maxUnits / 2),
                        repeat(" ", maxUnits));
        List<Object> out = new ArrayList<>();
        for (Object o : s) {
            out.add(fit(((String) o).codePoints().boxed().toList(), maxUnits, maxBytes));
        }
        return out;
    }

    static String repeat(String s, int times) {
        return s.repeat(Math.max(0, times));
    }

    private static List<Object> list(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    /** {@link OffsetDateTime} values compare as instants. */
    static OffsetDateTime utc(OffsetDateTime o) {
        return o == null ? null : o.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
