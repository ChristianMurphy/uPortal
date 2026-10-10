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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.RandomDistribution;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import org.apereo.portal.dbmigrate.PropertySettings;
import org.apereo.portal.dbmigrate.Vendor;
import org.apereo.portal.dbmigrate.schema.Column;

/**
 * The value conversions and the verification normalization, for any value and any pair of the
 * vendors' column types:
 *
 * <ul>
 *   <li>a value the copy wrote ({@link ValueCodec#adapt}) verifies against its source value: the
 *       copy and the verification agree on every documented conversion;
 *   <li>two values the target keeps apart are never normalized to the same bytes, so the
 *       verification sees every change the target can hold;
 *   <li>precision is reduced by truncation, never rounding (MOD-I95), and SQL Server's {@code
 *       datetime} grid is 1/300 s.
 * </ul>
 */
@AddLifecycleHook(PropertySettings.class)
class ValueCodecProperties {

    private static ColumnType type(Vendor v, int jdbc, String name, int size, Integer digits) {
        return ColumnType.classify(v, Column.of("C", jdbc, name, size, digits), false);
    }

    /** Timestamp columns as the vendors' JDBC metadata describes them. */
    static final List<ColumnType> TIMESTAMPS =
            Arrays.asList(
                    type(Vendor.POSTGRESQL, Types.TIMESTAMP, "timestamp", 29, 6),
                    type(Vendor.POSTGRESQL, Types.TIMESTAMP, "timestamp", 22, 3),
                    type(Vendor.HSQLDB, Types.TIMESTAMP, "TIMESTAMP", 26, 6),
                    type(Vendor.MARIADB, Types.TIMESTAMP, "DATETIME", 19, 0),
                    type(Vendor.MYSQL, Types.TIMESTAMP, "DATETIME", 23, 3),
                    type(Vendor.MYSQL, Types.TIMESTAMP, "DATETIME", 26, 6),
                    type(Vendor.SQLSERVER, Types.TIMESTAMP, "datetime", 23, 3),
                    type(Vendor.SQLSERVER, Types.TIMESTAMP, "datetime2", 27, 7),
                    type(Vendor.ORACLE, Types.TIMESTAMP, "TIMESTAMP(6)", 11, 6),
                    type(Vendor.ORACLE, Types.TIMESTAMP, "TIMESTAMP(9)", 11, 9),
                    type(Vendor.ORACLE, Types.TIMESTAMP, "DATE", 7, null));

    static final List<ColumnType> STRINGS =
            Arrays.asList(
                    type(Vendor.POSTGRESQL, Types.VARCHAR, "varchar", 255, null),
                    type(Vendor.POSTGRESQL, Types.CHAR, "bpchar", 10, null),
                    type(Vendor.MARIADB, Types.VARCHAR, "VARCHAR", 255, null),
                    type(Vendor.MYSQL, Types.CHAR, "CHAR", 10, null),
                    type(Vendor.SQLSERVER, Types.NVARCHAR, "nvarchar", 255, null),
                    type(Vendor.ORACLE, Types.VARCHAR, "VARCHAR2", 255, null),
                    type(Vendor.ORACLE, Types.CHAR, "CHAR", 10, null),
                    type(Vendor.ORACLE, Types.CLOB, "CLOB", -1, null),
                    type(Vendor.HSQLDB, Types.CLOB, "CLOB", 1 << 20, null));

    @Provide
    Arbitrary<ColumnType> timestampTypes() {
        return Arbitraries.of(TIMESTAMPS);
    }

    @Provide
    Arbitrary<ColumnType> stringTypes() {
        return Arbitraries.of(STRINGS);
    }

    /** Any instant from year 1 to 9999, to the nanosecond. */
    @Provide
    Arbitrary<LocalDateTime> timestamps() {
        LocalDateTime from = LocalDateTime.of(1, 1, 1, 0, 0);
        long span = ChronoUnit.SECONDS.between(from, LocalDateTime.of(9999, 12, 31, 0, 0));
        // Uniform: jqwik's default leans towards the low end of a range
        return Arbitraries.longs()
                .between(0, span)
                .withDistribution(RandomDistribution.uniform())
                .flatMap(
                        s ->
                                Arbitraries.integers()
                                        .between(0, 999_999_999)
                                        .withDistribution(RandomDistribution.uniform())
                                        .edgeCases(
                                                c ->
                                                        c.add(
                                                                0,
                                                                1,
                                                                999_999_999,
                                                                500_000_000,
                                                                1_666_667,
                                                                998_333_333,
                                                                999_500_000))
                                        .map(n -> from.plusSeconds(s).withNano(n)));
    }

    /** What a column of this type can hold: the value at its precision (or 1/300 s grid). */
    private static LocalDateTime held(ColumnType t, LocalDateTime v) {
        if (t.sqlServerDatetime()) {
            return ValueCodec.sqlServerDatetime(v);
        }
        return v.withNano(independentTruncate(v.getNano(), t.fractionDigits()));
    }

    private static int independentTruncate(int nanos, int digits) {
        long unit = 1;
        for (int i = digits; i < 9; i++) {
            unit *= 10;
        }
        return (int) (nanos / unit * unit);
    }

    // ------------------------------------------------------------------ precision

    @Property(tries = 500)
    void truncationNeverRoundsAndKeepsTheDigits(
            @ForAll("timestamps") LocalDateTime t, @ForAll @IntRange(min = 0, max = 9) int digits) {
        LocalDateTime cut = ValueCodec.truncate(t, digits);
        assertEquals(t.withNano(independentTruncate(t.getNano(), digits)), cut);
        assertFalse(cut.isAfter(t));
        assertEquals(cut, ValueCodec.truncate(cut, digits));
        LocalTime time = ValueCodec.truncate(t.toLocalTime(), digits);
        assertEquals(cut.toLocalTime(), time);
    }

    @Property(tries = 500)
    void sqlServerDatetimeIsTheNearestTickInWholeMilliseconds(
            @ForAll("timestamps") LocalDateTime t) {
        LocalDateTime g = ValueCodec.sqlServerDatetime(t);
        long millisOfSecond = g.getNano() / 1_000_000;
        assertEquals(t + " -> " + g, 0, g.getNano() % 1_000_000);
        assertTrue(t + " -> " + g, Arrays.asList(0L, 3L, 7L).contains(millisOfSecond % 10));
        // At most half a tick (1/600 s) plus the millisecond rounding away from the value
        long diff = Math.abs(Duration.between(t, g).toNanos());
        assertTrue(t + " -> " + g + " is " + diff + " ns away", diff <= 1_666_667 + 500_000);
        assertEquals(g, ValueCodec.sqlServerDatetime(g));
    }

    // ------------------------------------------------------------------ copy versus verify

    @Property(tries = 1000)
    void aCopiedTimestampVerifies(
            @ForAll("timestampTypes") ColumnType source,
            @ForAll("timestampTypes") ColumnType target,
            @ForAll("timestamps") LocalDateTime value) {
        ColumnPair pair = ColumnPair.resolve(source, target, null);
        LocalDateTime inSource = held(source, value);
        Object copied = ValueCodec.adapt(inSource, pair);
        // What the target column then keeps of the written value
        LocalDateTime inTarget = held(target, (LocalDateTime) copied);
        assertEquals(pair + " " + inSource, copied, inTarget);
        assertArrayEquals(
                pair + " " + inSource + " -> " + inTarget,
                ValueCodec.normalize(inSource, pair),
                ValueCodec.normalize(inTarget, pair));
    }

    @Property(tries = 1000)
    void timestampsTheTargetKeepsApartVerifyApart(
            @ForAll("timestampTypes") ColumnType source,
            @ForAll("timestampTypes") ColumnType target,
            @ForAll("timestamps") LocalDateTime a,
            @ForAll("timestamps") LocalDateTime b) {
        ColumnPair pair = ColumnPair.resolve(source, target, null);
        LocalDateTime ta = held(target, (LocalDateTime) ValueCodec.adapt(held(source, a), pair));
        LocalDateTime tb = held(target, (LocalDateTime) ValueCodec.adapt(held(source, b), pair));
        if (!ta.equals(tb)) {
            assertFalse(
                    pair + ": " + ta + " and " + tb,
                    Arrays.equals(ValueCodec.normalize(ta, pair), ValueCodec.normalize(tb, pair)));
        }
    }

    @Property(tries = 300)
    void aTimestampOneSecondOffNeverVerifies(
            @ForAll("timestampTypes") ColumnType source,
            @ForAll("timestampTypes") ColumnType target,
            @ForAll("timestamps") LocalDateTime value) {
        ColumnPair pair = ColumnPair.resolve(source, target, null);
        LocalDateTime inTarget =
                held(target, (LocalDateTime) ValueCodec.adapt(held(source, value), pair));
        assertFalse(
                pair + " " + inTarget,
                Arrays.equals(
                        ValueCodec.normalize(inTarget, pair),
                        ValueCodec.normalize(inTarget.plusSeconds(1), pair)));
    }

    @Property(tries = 300)
    void zonedTimestampsCompareAsInstants(
            @ForAll("timestamps") LocalDateTime t,
            @ForAll @IntRange(min = -14 * 60, max = 14 * 60) int offsetMinutes,
            @ForAll @IntRange(min = 0, max = 9) int digits) {
        ColumnType tz =
                type(Vendor.POSTGRESQL, Types.TIMESTAMP_WITH_TIMEZONE, "timestamptz", 35, digits);
        ColumnPair pair = ColumnPair.resolve(tz, tz, null);
        OffsetDateTime here = t.atOffset(ZoneOffset.ofTotalSeconds(offsetMinutes * 60));
        OffsetDateTime utc = here.withOffsetSameInstant(ZoneOffset.UTC);
        assertArrayEquals(ValueCodec.normalize(here, pair), ValueCodec.normalize(utc, pair));
        OffsetDateTime adapted = (OffsetDateTime) ValueCodec.adapt(here, pair);
        assertEquals(independentTruncate(here.getNano(), digits), adapted.getNano());
        assertFalse(
                Arrays.equals(
                        ValueCodec.normalize(here, pair),
                        ValueCodec.normalize(here.plusSeconds(1), pair)));
    }

    // ------------------------------------------------------------------ strings and numbers

    @Provide
    Arbitrary<String> texts() {
        return Arbitraries.strings()
                .ofMaxLength(12)
                .edgeCases(c -> c.add("", " ", "a ", " a", "  ", "é", "\t"));
    }

    @Property(tries = 1000)
    void aCopiedStringVerifiesAndAChangedOneDoesNot(
            @ForAll("stringTypes") ColumnType source,
            @ForAll("stringTypes") ColumnType target,
            @ForAll("texts") String a,
            @ForAll("texts") String b) {
        ColumnPair pair = ColumnPair.resolve(source, target, null);
        Object copied = ValueCodec.adapt(a, pair);
        if (target.emptyStringIsNull() && a.isEmpty()) {
            assertNull(copied);
        }
        assertArrayEquals(
                pair + " '" + a + "'",
                ValueCodec.normalize(a, pair),
                ValueCodec.normalize(copied, pair));
        String ka = canonical(pair, a);
        String kb = canonical(pair, b);
        boolean same = Arrays.equals(ValueCodec.normalize(a, pair), ValueCodec.normalize(b, pair));
        assertEquals(pair + " '" + a + "' vs '" + b + "'", java.util.Objects.equals(ka, kb), same);
    }

    /** The documented string equivalence: CHAR padding, Oracle's '' as NULL. */
    private static String canonical(ColumnPair pair, String s) {
        if (pair.trimTrailingSpaces()) {
            s = s.replaceAll(" +$", "");
        }
        return pair.emptyStringIsNull() && s.isEmpty() ? null : s;
    }

    @Property(tries = 500)
    void numbersVerifyByValueNotScale(
            @ForAll @IntRange(min = 0, max = 10) int scale,
            @ForAll long unscaled,
            @ForAll @IntRange(min = 0, max = 6) int extraZeros) {
        ColumnType d = type(Vendor.POSTGRESQL, Types.NUMERIC, "numeric", 38, 10);
        ColumnType oracle = type(Vendor.ORACLE, Types.NUMERIC, "NUMBER", 38, 10);
        ColumnPair pair = ColumnPair.resolve(d, oracle, null);
        BigDecimal v = BigDecimal.valueOf(unscaled, scale);
        BigDecimal padded = v.setScale(scale + extraZeros);
        assertArrayEquals(ValueCodec.normalize(v, pair), ValueCodec.normalize(padded, pair));
        BigDecimal other = v.add(BigDecimal.ONE.movePointLeft(scale + extraZeros + 1));
        assertFalse(
                Arrays.equals(ValueCodec.normalize(v, pair), ValueCodec.normalize(other, pair)));
    }

    @Property(tries = 500)
    void integersVerifyTheSameFromAnyWidth(@ForAll long v) {
        ColumnType bigint = type(Vendor.POSTGRESQL, Types.BIGINT, "int8", 19, 0);
        ColumnType number = type(Vendor.ORACLE, Types.NUMERIC, "NUMBER", 19, 0);
        ColumnPair pair = ColumnPair.resolve(number, bigint, null);
        assertEquals(ColumnType.Family.INTEGER, pair.logical());
        assertArrayEquals(
                ValueCodec.normalize(v, pair), ValueCodec.normalize(BigDecimal.valueOf(v), pair));
        if (v != Long.MAX_VALUE) {
            assertFalse(
                    Arrays.equals(
                            ValueCodec.normalize(v, pair), ValueCodec.normalize(v + 1, pair)));
        }
    }

    @Property(tries = 300)
    void binaryValuesVerifyByEveryByte(@ForAll byte[] value, @ForAll int position) {
        ColumnType bytea = type(Vendor.POSTGRESQL, Types.BINARY, "bytea", Integer.MAX_VALUE, null);
        ColumnType blob = type(Vendor.MARIADB, Types.LONGVARBINARY, "LONGBLOB", -1, null);
        ColumnPair pair = ColumnPair.resolve(bytea, blob, null);
        byte[] n = ValueCodec.normalize(value, pair);
        assertNotNull(n);
        assertArrayEquals(value, n);
        if (value.length > 0) {
            byte[] changed = value.clone();
            int i = Math.floorMod(position, value.length);
            changed[i] ^= 1;
            assertFalse(Arrays.equals(n, ValueCodec.normalize(changed, pair)));
        }
        assertFalse(Arrays.equals(ValueCodec.normalize(new byte[0], pair), null));
    }
}
