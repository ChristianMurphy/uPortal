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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import org.apereo.portal.dbmigrate.Vendor;
import org.apereo.portal.dbmigrate.schema.Column;
import org.junit.Test;

/**
 * How a pair of column types is copied and verified: the flags and notes a pair gets from its two
 * types, and the classification of the vendors' types behind them. The value properties use these
 * flags to decide what equal means, so they cannot catch a wrong flag; these examples pin them.
 */
public class ColumnPairTest {

    private static ColumnType type(Vendor v, int jdbc, String name, int size, Integer digits) {
        return ColumnType.classify(v, Column.of("C", jdbc, name, size, digits), false);
    }

    private static ColumnPair pair(ColumnType s, ColumnType t) {
        return ColumnPair.resolve(s, t, null);
    }

    private static final ColumnType PG_TIMESTAMP =
            type(Vendor.POSTGRESQL, Types.TIMESTAMP, "timestamp", 29, 6);
    private static final ColumnType PG_TIME6 = type(Vendor.POSTGRESQL, Types.TIME, "time", 15, 6);
    private static final ColumnType PG_TIME3 = type(Vendor.POSTGRESQL, Types.TIME, "time", 12, 3);
    private static final ColumnType SQLSERVER_DATETIME =
            type(Vendor.SQLSERVER, Types.TIMESTAMP, "datetime", 23, 3);
    private static final ColumnType PG_VARCHAR =
            type(Vendor.POSTGRESQL, Types.VARCHAR, "varchar", 255, null);
    private static final ColumnType PG_CHAR =
            type(Vendor.POSTGRESQL, Types.CHAR, "bpchar", 10, null);
    private static final ColumnType MARIADB_VARCHAR100 =
            type(Vendor.MARIADB, Types.VARCHAR, "VARCHAR", 100, null);

    @Test
    public void trimmingFollowsCharPaddingOnEitherSide() {
        assertFalse(pair(PG_VARCHAR, PG_VARCHAR).trimTrailingSpaces());
        assertFalse(pair(PG_VARCHAR, MARIADB_VARCHAR100).trimTrailingSpaces());
        assertTrue(pair(PG_CHAR, PG_VARCHAR).trimTrailingSpaces());
        assertTrue(pair(PG_VARCHAR, PG_CHAR).trimTrailingSpaces());
        assertFalse(PG_VARCHAR.padded());
        assertTrue(PG_CHAR.padded());
    }

    @Test
    public void sqlServerDatetimeGridOnlyWhenOneSideIsADatetime() {
        assertFalse(pair(PG_TIMESTAMP, PG_TIMESTAMP).sqlServerGrid());
        assertTrue(pair(SQLSERVER_DATETIME, PG_TIMESTAMP).sqlServerGrid());
        assertTrue(pair(PG_TIMESTAMP, SQLSERVER_DATETIME).sqlServerGrid());
        assertEquals(3, pair(SQLSERVER_DATETIME, PG_TIMESTAMP).fractionDigits());
        // Into a datetime: the precision and the grid are both noted, once
        assertEquals(
                Arrays.asList(
                        "fractional seconds reduced from 6 to 3 digits (truncated)",
                        "rounded to SQL Server datetime's 1/300 s"),
                pair(PG_TIMESTAMP, SQLSERVER_DATETIME).notes());
        // From a datetime, or between two, nothing is lost
        assertEquals(Collections.emptyList(), pair(SQLSERVER_DATETIME, PG_TIMESTAMP).notes());
        assertEquals(Collections.emptyList(), pair(SQLSERVER_DATETIME, SQLSERVER_DATETIME).notes());
    }

    @Test
    public void timesKeepTheLowerPrecisionAndNoteOnlyALoss() {
        ColumnPair down = pair(PG_TIME6, PG_TIME3);
        assertEquals(ColumnType.Family.TIME, down.logical());
        assertEquals(3, down.fractionDigits());
        assertEquals(
                Collections.singletonList(
                        "fractional seconds reduced from 6 to 3 digits (truncated)"),
                down.notes());
        assertEquals(3, pair(PG_TIME3, PG_TIME6).fractionDigits());
        assertEquals(Collections.emptyList(), pair(PG_TIME3, PG_TIME6).notes());
        assertEquals(Collections.emptyList(), pair(PG_TIME6, PG_TIME6).notes());
        // 5.17 keeps a time of day in SQL Server's datetime: its milliseconds count
        ColumnPair fromDatetime = pair(SQLSERVER_DATETIME, PG_TIME6);
        assertEquals(ColumnType.Family.TIME, fromDatetime.logical());
        assertEquals(3, fromDatetime.fractionDigits());
    }

    @Test
    public void timesAreCopiedAndVerifiedAtTheirPrecision() {
        LocalTime t = LocalTime.of(12, 0, 0, 123_456_789);
        assertEquals(
                LocalTime.of(12, 0, 0, 123_456_000), ValueCodec.adapt(t, pair(PG_TIME6, PG_TIME6)));
        assertEquals(
                LocalTime.of(12, 0, 0, 123_000_000), ValueCodec.adapt(t, pair(PG_TIME6, PG_TIME3)));
        // Into a datetime: onto the 1/300 s grid, .002 is nearest to .00333
        assertEquals(
                LocalTime.of(12, 0, 0, 3_000_000),
                ValueCodec.adapt(
                        LocalTime.of(12, 0, 0, 2_000_000), pair(PG_TIME6, SQLSERVER_DATETIME)));
        // One microsecond apart in time(6) columns: two values, and not NULL
        ColumnPair p = pair(PG_TIME6, PG_TIME6);
        byte[] a = ValueCodec.normalize(LocalTime.of(12, 0, 0, 1_000), p);
        byte[] b = ValueCodec.normalize(LocalTime.of(12, 0, 0, 2_000), p);
        assertNotNull(a);
        assertEquals("43200000001000n", new String(a, StandardCharsets.US_ASCII));
        assertFalse(Arrays.equals(a, b));
    }

    @Test
    public void aNarrowerTargetIsNoted() {
        assertEquals(
                Collections.singletonList(
                        "target is narrower (100 < 255); longer values fail the copy"),
                pair(PG_VARCHAR, MARIADB_VARCHAR100).notes());
        assertEquals(Collections.emptyList(), pair(MARIADB_VARCHAR100, PG_VARCHAR).notes());
        assertEquals(Collections.emptyList(), pair(PG_VARCHAR, PG_VARCHAR).notes());
        // A CLOB without a size limit is never narrower
        ColumnType clob = type(Vendor.ORACLE, Types.CLOB, "CLOB", -1, null);
        assertFalse(pair(PG_VARCHAR, clob).notes().toString().contains("narrower"));
    }

    @Test
    public void booleansAndDoublesWinWhicheverSideTheyAreOn() {
        ColumnType pgBool = type(Vendor.POSTGRESQL, Types.BIT, "bool", 1, 0);
        ColumnType number1 = type(Vendor.ORACLE, Types.NUMERIC, "NUMBER", 1, 0);
        assertEquals(ColumnType.Family.BOOLEAN, pair(number1, pgBool).logical());
        assertEquals(ColumnType.Family.BOOLEAN, pair(pgBool, number1).logical());
        ColumnType float8 = type(Vendor.POSTGRESQL, Types.DOUBLE, "float8", 17, 17);
        ColumnType number = type(Vendor.ORACLE, Types.NUMERIC, "NUMBER", 0, -127);
        assertEquals(ColumnType.Family.DOUBLE, pair(float8, number).logical());
        assertEquals(ColumnType.Family.DOUBLE, pair(number, float8).logical());
    }

    @Test
    public void oneDigitNumbersMayBeBooleans() {
        assertTrue(type(Vendor.ORACLE, Types.NUMERIC, "NUMBER", 1, 0).booleanLike());
        assertTrue(type(Vendor.ORACLE, Types.DECIMAL, "NUMBER", 1, 0).booleanLike());
        assertTrue(type(Vendor.MYSQL, Types.TINYINT, "TINYINT", 1, 0).booleanLike());
        assertFalse(type(Vendor.ORACLE, Types.NUMERIC, "NUMBER", 2, 0).booleanLike());
        assertFalse(type(Vendor.MYSQL, Types.INTEGER, "INT", 1, 0).booleanLike());
    }

    @Test
    public void sqlServerDateTimeTypes() {
        assertTrue(SQLSERVER_DATETIME.sqlServerDatetime());
        assertTrue(SQLSERVER_DATETIME.dateTimeStorage());
        ColumnType small = type(Vendor.SQLSERVER, Types.TIMESTAMP, "smalldatetime", 16, 0);
        assertFalse(small.sqlServerDatetime());
        assertTrue(small.dateTimeStorage());
        assertEquals(0, small.fractionDigits());
        ColumnType datetime2 = type(Vendor.SQLSERVER, Types.TIMESTAMP, "datetime2", 27, 7);
        assertFalse(datetime2.sqlServerDatetime());
        assertFalse(datetime2.dateTimeStorage());
        assertEquals(7, datetime2.fractionDigits());
        assertFalse(PG_TIMESTAMP.dateTimeStorage());
        // Only a column that also holds dates may take a date
        ColumnType pgDate = type(Vendor.POSTGRESQL, Types.DATE, "date", 13, 0);
        assertEquals(ColumnType.Family.DATE, pair(pgDate, SQLSERVER_DATETIME).logical());
        try {
            pair(pgDate, PG_TIMESTAMP);
            throw new AssertionError("a date must not be copied into a timestamp without a rule");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                    expected.getMessage(), expected.getMessage().contains("add a transform rule"));
        }
    }

    @Test
    public void fractionDigitsFromTheDisplaySize() {
        // Without DECIMAL_DIGITS: 8 or 19 characters for whole seconds, then a point and digits
        assertEquals(3, type(Vendor.HSQLDB, Types.TIME, "TIME", 12, null).fractionDigits());
        assertEquals(
                9, type(Vendor.HSQLDB, Types.TIMESTAMP, "TIMESTAMP", 29, null).fractionDigits());
        // MySQL and MariaDB keep at most 6
        assertEquals(6, type(Vendor.MYSQL, Types.TIMESTAMP, "DATETIME", 29, null).fractionDigits());
        assertEquals(6, type(Vendor.MYSQL, Types.TIME, "TIME", 15, null).fractionDigits());
        assertNotEquals(
                type(Vendor.HSQLDB, Types.TIME, "TIME", 12, null).fractionDigits(),
                type(Vendor.HSQLDB, Types.TIME, "TIME", 8, null).fractionDigits());
    }
}
