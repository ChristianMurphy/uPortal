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
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.apereo.portal.dbmigrate.Vendor;
import org.apereo.portal.dbmigrate.schema.Column;
import org.junit.Test;

public class ValueCodecTest {

    private static ColumnType type(Vendor v, int jdbc, String name, int size, Integer digits) {
        return ColumnType.classify(v, Column.of("C", jdbc, name, size, digits), false);
    }

    private static ColumnPair pair(ColumnType s, ColumnType t) {
        return ColumnPair.resolve(s, t, null);
    }

    private static String norm(Object v, ColumnPair p) {
        byte[] b = ValueCodec.normalize(v, p);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    private static final ColumnType PG_TIMESTAMP =
            type(Vendor.POSTGRESQL, Types.TIMESTAMP, "timestamp", 29, 6);
    private static final ColumnType MARIADB_DATETIME =
            type(Vendor.MARIADB, Types.TIMESTAMP, "DATETIME", 19, 0);
    private static final ColumnType SQLSERVER_DATETIME =
            type(Vendor.SQLSERVER, Types.TIMESTAMP, "datetime", 23, 3);

    @Test
    public void booleansNormalizeAcrossVendors() {
        ColumnType pg = type(Vendor.POSTGRESQL, Types.BIT, "bool", 1, 0);
        ColumnType oracle = type(Vendor.ORACLE, Types.NUMERIC, "NUMBER", 1, 0);
        ColumnPair p = pair(pg, oracle);
        assertEquals(ColumnType.Family.BOOLEAN, p.logical());
        assertEquals("T", norm(true, p));
        assertEquals("F", norm(false, p));
        assertNull(norm(null, p));
        assertEquals(
                ColumnType.Family.BOOLEAN,
                pair(type(Vendor.MARIADB, Types.BIT, "BIT", 1, 0), pg).logical());
    }

    @Test
    public void decimalsIgnoreScale() {
        ColumnType d = type(Vendor.POSTGRESQL, Types.NUMERIC, "numeric", 12, 4);
        ColumnPair p = pair(d, d);
        assertEquals(norm(new BigDecimal("12.3400"), p), norm(new BigDecimal("12.34"), p));
        assertEquals("0", norm(new BigDecimal("0.0000"), p));
        assertEquals("1200", norm(new BigDecimal("1.2E+3"), p));
    }

    @Test
    public void integersFromNumberAndBigint() {
        ColumnType oracle = type(Vendor.ORACLE, Types.NUMERIC, "NUMBER", 19, 0);
        ColumnType pg = type(Vendor.POSTGRESQL, Types.BIGINT, "int8", 19, 0);
        ColumnPair p = pair(oracle, pg);
        assertEquals(ColumnType.Family.INTEGER, p.logical());
        assertEquals(norm(42L, p), norm(new BigDecimal("42"), p));
    }

    @Test
    public void doublesCompareAtFifteenDigits() {
        ColumnType pg = type(Vendor.POSTGRESQL, Types.DOUBLE, "float8", 17, 17);
        ColumnType oracle = type(Vendor.ORACLE, Types.FLOAT, "FLOAT", 126, null);
        ColumnPair p = pair(pg, oracle);
        assertEquals(ColumnType.Family.DOUBLE, p.logical());
        assertEquals("0.3", norm(0.1 + 0.2, p));
        assertEquals(norm(0.30000000000000004, p), norm(0.3, p));
        assertEquals("0", norm(-0.0, p));
    }

    @Test
    public void charPaddingAndOracleEmptyStrings() {
        ColumnType chr = type(Vendor.POSTGRESQL, Types.CHAR, "bpchar", 8, 0);
        ColumnType vc = type(Vendor.MARIADB, Types.VARCHAR, "VARCHAR", 8, 0);
        ColumnPair p = pair(chr, vc);
        assertEquals("AB", norm("AB      ", p));
        assertEquals("AB", norm("AB", p));
        ColumnType ora = type(Vendor.ORACLE, Types.VARCHAR, "VARCHAR2", 255, 0);
        ColumnPair toOracle = pair(vc, ora);
        assertNull(norm("", toOracle));
        assertNull(ValueCodec.adapt("", toOracle));
        assertEquals("", norm("", pair(vc, vc)));
    }

    @Test
    public void timestampsAreInstantsIndependentOfTheJvmZone() {
        ColumnPair p = pair(PG_TIMESTAMP, PG_TIMESTAMP);
        // 02:30 on 2026-03-08 does not exist in America/New_York; it must stay 02:30
        LocalDateTime gap = LocalDateTime.of(2026, 3, 8, 2, 30, 0, 123_456_000);
        long epoch = gap.toEpochSecond(ZoneOffset.UTC);
        assertEquals(epoch + ".123456000", norm(gap, p));
    }

    @Test
    public void precisionIsTheLowerSideAndTheCopyTruncates() {
        ColumnPair p = pair(PG_TIMESTAMP, MARIADB_DATETIME);
        assertEquals(0, p.fractionDigits());
        assertEquals(1, p.notes().size());
        LocalDateTime v = LocalDateTime.of(2026, 10, 10, 23, 59, 59, 999_999_000);
        LocalDateTime stored = (LocalDateTime) ValueCodec.adapt(v, p);
        // Truncated, not rounded into the next day
        assertEquals(LocalDateTime.of(2026, 10, 10, 23, 59, 59), stored);
        assertEquals(norm(v, p), norm(stored, p));
        // The reverse direction keeps everything
        ColumnPair back = pair(MARIADB_DATETIME, PG_TIMESTAMP);
        assertEquals(0, back.fractionDigits());
        assertEquals(stored, ValueCodec.adapt(stored, back));
    }

    @Test
    public void sqlServerDatetimeGrid() {
        LocalDateTime base = LocalDateTime.of(1998, 1, 1, 23, 59, 59);
        assertEquals(
                LocalDateTime.of(1998, 1, 2, 0, 0),
                ValueCodec.sqlServerDatetime(base.withNano(999_000_000)));
        assertEquals(
                base.withNano(997_000_000),
                ValueCodec.sqlServerDatetime(base.withNano(995_000_000)));
        assertEquals(
                base.withNano(997_000_000),
                ValueCodec.sqlServerDatetime(base.withNano(998_000_000)));
        assertEquals(
                base.withNano(993_000_000),
                ValueCodec.sqlServerDatetime(base.withNano(992_000_000)));
        assertEquals(
                base.withNano(990_000_000),
                ValueCodec.sqlServerDatetime(base.withNano(991_000_000)));
        ColumnPair p = pair(PG_TIMESTAMP, SQLSERVER_DATETIME);
        assertEquals(true, p.sqlServerGrid());
        LocalDateTime v = base.withNano(995_123_000);
        Object stored = ValueCodec.adapt(v, p);
        assertEquals(base.withNano(997_000_000), stored);
        assertEquals(norm(v, p), norm(stored, p));
    }

    /**
     * SQL Server datetime into a column with whole seconds (MySQL and MariaDB DATETIME under 5.17):
     * the copy truncates .997 to the second, so verification must compare both sides at the second
     * too, not on SQL Server's 1/300 s grid. G-migrate run 38080746162 reported five tables of SQL
     * Server 2019 and 2022 sources as MISMATCH in MariaDB 12.3 and MySQL 9.7.
     */
    @Test
    public void sqlServerDatetimeIntoWholeSeconds() {
        LocalDateTime v = LocalDateTime.of(2026, 10, 10, 19, 56, 41, 997_000_000);
        ColumnPair p = pair(SQLSERVER_DATETIME, MARIADB_DATETIME);
        assertEquals(0, p.fractionDigits());
        Object stored = ValueCodec.adapt(v, p);
        assertEquals(v.withNano(0), stored);
        assertEquals(norm(v, p), norm(stored, p));

        LocalTime time = LocalTime.of(19, 56, 41, 997_000_000);
        ColumnPair timePair =
                pair(
                        type(Vendor.SQLSERVER, Types.TIMESTAMP, "datetime", 23, 3),
                        type(Vendor.MARIADB, Types.TIME, "TIME", 8, 0));
        assertEquals(ColumnType.Family.TIME, timePair.logical());
        assertEquals(norm(time, timePair), norm(ValueCodec.adapt(time, timePair), timePair));
    }

    @Test
    public void dateAndTimeStoredInDatetimeColumns() {
        ColumnType pgDate = type(Vendor.POSTGRESQL, Types.DATE, "date", 13, 0);
        ColumnType pgTime = type(Vendor.POSTGRESQL, Types.TIME, "time", 15, 6);
        ColumnType oracleDate = type(Vendor.ORACLE, Types.TIMESTAMP, "DATE", 7, 0);
        assertEquals(ColumnType.Family.DATE, pair(pgDate, SQLSERVER_DATETIME).logical());
        assertEquals(ColumnType.Family.DATE, pair(pgDate, oracleDate).logical());
        assertEquals(ColumnType.Family.DATE, pair(oracleDate, pgDate).logical());
        ColumnPair time = pair(pgTime, oracleDate);
        assertEquals(ColumnType.Family.TIME, time.logical());
        assertEquals(
                LocalTime.of(10, 15, 30), ValueCodec.adapt(LocalTime.of(10, 15, 30, 500), time));
        assertEquals("2026-03-08", norm(LocalDate.of(2026, 3, 8), pair(pgDate, oracleDate)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void timestampWithoutZoneDoesNotSilentlyBecomeAnInstant() {
        ColumnType tz = type(Vendor.POSTGRESQL, Types.TIMESTAMP, "timestamptz", 35, 6);
        pair(PG_TIMESTAMP, tz);
    }

    @Test
    public void timestampWithZoneComparesInstants() {
        ColumnType tz = type(Vendor.POSTGRESQL, Types.TIMESTAMP, "timestamptz", 35, 6);
        ColumnType offset = type(Vendor.SQLSERVER, -155, "datetimeoffset", 34, 7);
        ColumnPair p = pair(tz, offset);
        assertEquals(ColumnType.Family.TIMESTAMP_TZ, p.logical());
        OffsetDateTime a = OffsetDateTime.of(2026, 10, 10, 12, 0, 0, 0, ZoneOffset.ofHours(2));
        OffsetDateTime b = OffsetDateTime.of(2026, 10, 10, 10, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(norm(a, p), norm(b, p));
    }

    @Test
    public void postgresLargeObjectsResolveOnTheServer() {
        ColumnType oidText =
                ColumnType.classify(
                        Vendor.POSTGRESQL,
                        Column.of("EVENT_DATA", Types.VARCHAR, "text", Integer.MAX_VALUE, 0),
                        true);
        ColumnType oid = type(Vendor.POSTGRESQL, Types.BIGINT, "oid", 10, 0);
        org.apereo.portal.dbmigrate.schema.Dialect d =
                org.apereo.portal.dbmigrate.schema.Dialect.forVendor(
                        Vendor.POSTGRESQL, null, "public");
        assertEquals(
                "lo_get(CAST(\"EVENT_DATA\" AS oid))", ValueCodec.selectExpression(oidText, d));
        assertEquals("CAST(lo_from_bytea(0, ?) AS text)", ValueCodec.placeholder(oidText));
        assertEquals("lo_from_bytea(0, ?)", ValueCodec.placeholder(oid));
        assertEquals(ColumnType.Family.BINARY, oid.family());
        // Copied into a non-PostgreSQL CLOB the content is a string
        ColumnType clob = type(Vendor.ORACLE, Types.CLOB, "CLOB", 4000, 0);
        ColumnPair p = pair(oidText, clob);
        assertEquals(ColumnType.Family.STRING, p.logical());
        assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), ValueCodec.normalize("{}", p));
    }

    @Test
    public void postgresOidColumnsPairWithTextColumns() {
        // Notification's @Lob String: an oid column on PostgreSQL, LONGTEXT on MariaDB
        ColumnType oid = type(Vendor.POSTGRESQL, Types.BIGINT, "oid", 10, 0);
        ColumnType longtext =
                type(Vendor.MARIADB, Types.LONGVARCHAR, "LONGTEXT", Integer.MAX_VALUE, 0);
        assertEquals(ColumnType.Family.STRING, pair(oid, longtext).logical());
        assertEquals(ColumnType.Family.STRING, pair(longtext, oid).logical());
        assertEquals(
                ColumnType.Family.BINARY,
                pair(oid, type(Vendor.MARIADB, Types.LONGVARBINARY, "LONGBLOB", 1, 0)).logical());
    }

    @Test
    public void transformsCanFillTypesWithoutAFamily() {
        // e.g. a 6.0 rule turning JSON text into jsonb, or a VARCHAR(36) id into uuid
        ColumnType text = type(Vendor.MARIADB, Types.LONGVARCHAR, "LONGTEXT", Integer.MAX_VALUE, 0);
        ColumnType jsonb = type(Vendor.POSTGRESQL, Types.OTHER, "jsonb", Integer.MAX_VALUE, 0);
        assertEquals(ColumnType.Family.OTHER, jsonb.family());
        assertEquals(
                ColumnType.Family.OTHER,
                ColumnPair.resolve(text, jsonb, ColumnType.Family.OTHER).logical());
        try {
            pair(text, jsonb);
            org.junit.Assert.fail("text into jsonb needs a rule");
        } catch (IllegalArgumentException expected) {
            // no silent conversion
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void lobOidOnlyAppliesToPostgresText() {
        ColumnType.classify(
                Vendor.MARIADB, Column.of("EVENT_DATA", Types.LONGVARCHAR, "LONGTEXT", 1, 0), true);
    }

    @Test
    public void fractionDigitsFromMysqlDisplaySize() {
        assertEquals(0, MARIADB_DATETIME.fractionDigits());
        assertEquals(6, type(Vendor.MYSQL, Types.TIMESTAMP, "DATETIME", 26, null).fractionDigits());
        assertEquals(0, type(Vendor.MYSQL, Types.TIME, "TIME", 8, null).fractionDigits());
    }

    @Test
    public void fractionalDigitsOfColumnsWithoutDecimalDigits() {
        // HSQLDB reports no DECIMAL_DIGITS for TIMESTAMP(0): its display size tells
        assertEquals(
                0, type(Vendor.HSQLDB, Types.TIMESTAMP, "TIMESTAMP", 19, null).fractionDigits());
        assertEquals(
                3, type(Vendor.HSQLDB, Types.TIMESTAMP, "TIMESTAMP", 23, null).fractionDigits());
        // MariaDB Connector/J sizes TIME(0) as 10 ("-838:59:59"); the schema reader passes the
        // precision from information_schema, which wins over the size
        assertEquals(0, type(Vendor.MARIADB, Types.TIME, "TIME", 10, 0).fractionDigits());
        assertEquals(3, type(Vendor.MARIADB, Types.TIME, "TIME", 14, 3).fractionDigits());
        assertEquals(6, type(Vendor.MARIADB, Types.TIMESTAMP, "DATETIME", 26, 6).fractionDigits());
    }

}
