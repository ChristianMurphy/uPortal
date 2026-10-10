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
package org.apereo.portal.dbmigrate.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Types;
import java.util.Collections;
import org.apereo.portal.dbmigrate.Vendor;
import org.junit.Test;

/** SQL generation for every vendor, including the ones only CI runs (SQL Server, Oracle). */
public class DialectTest {

    private static final Dialect PG = Dialect.forVendor(Vendor.POSTGRESQL, null, "public");
    private static final Dialect HSQL = Dialect.forVendor(Vendor.HSQLDB, null, "PUBLIC");
    private static final Dialect ORACLE = Dialect.forVendor(Vendor.ORACLE, null, "UPORTAL");
    private static final Dialect MSSQL = Dialect.forVendor(Vendor.SQLSERVER, "uportal", "dbo");
    private static final Dialect MARIADB = Dialect.forVendor(Vendor.MARIADB, "uportal", null);
    private static final Dialect MYSQL = Dialect.forVendor(Vendor.MYSQL, "uportal", null);

    private static final Table TABLE = new Table(null, null, "UP_THING");
    private static final Column ID = Column.of("ID", Types.BIGINT, "bigint", 19, 0);

    @Test
    public void quotesAndQualifies() {
        assertEquals("\"public\".\"up_user\"", PG.qualify("up_user"));
        assertEquals("\"dbo\".\"UP_USER\"", MSSQL.qualify("UP_USER"));
        assertEquals("\"UPORTAL\".\"UP_USER\"", ORACLE.qualify("UP_USER"));
        assertEquals("`uportal`.`up_user`", MARIADB.qualify("up_user"));
        assertEquals("\"a\"\"b\"", PG.quote("a\"b"));
    }

    @Test
    public void restartsSequences() {
        assertEquals(
                Collections.singletonList("SELECT setval('\"public\".\"up_user_seq\"', 26, false)"),
                PG.restartSequenceSql("up_user_seq", 26));
        assertEquals(
                Collections.singletonList(
                        "ALTER SEQUENCE \"UPORTAL\".\"UP_USER_SEQ\" RESTART START WITH 26"),
                ORACLE.restartSequenceSql("UP_USER_SEQ", 26));
        assertEquals(
                Collections.singletonList("ALTER SEQUENCE \"dbo\".\"UP_USER_SEQ\" RESTART WITH 26"),
                MSSQL.restartSequenceSql("UP_USER_SEQ", 26));
        assertEquals(
                Collections.singletonList(
                        "ALTER SEQUENCE \"PUBLIC\".\"UP_USER_SEQ\" RESTART WITH 26"),
                HSQL.restartSequenceSql("UP_USER_SEQ", 26));
        assertEquals(
                Collections.singletonList("ALTER SEQUENCE `uportal`.`up_user_seq` RESTART WITH 26"),
                MARIADB.restartSequenceSql("up_user_seq", 26));
    }

    @Test(expected = IllegalStateException.class)
    public void mysqlHasNoSequences() {
        MYSQL.restartSequenceSql("x", 1);
    }

    @Test
    public void listsSequencesPerVendor() {
        assertTrue(PG.listSequencesSql().contains("information_schema.sequences"));
        assertTrue(ORACLE.listSequencesSql().contains("ALL_SEQUENCES"));
        assertTrue(MSSQL.listSequencesSql().contains("sys.sequences"));
        assertTrue(MARIADB.listSequencesSql().contains("'SEQUENCE'"));
        assertTrue(HSQL.listSequencesSql().contains("INFORMATION_SCHEMA.SEQUENCES"));
        assertNull(MYSQL.listSequencesSql());
    }

    @Test
    public void sequenceTables() {
        Table seq = new Table(null, null, "UP_USER_SEQ");
        assertEquals(
                "UPDATE `uportal`.`UP_USER_SEQ` SET `next_val` = ?",
                MARIADB.updateSequenceTableSql(seq, "next_val"));
        assertEquals(
                "INSERT INTO \"dbo\".\"UP_USER_SEQ\" (\"next_val\") VALUES (?)",
                MSSQL.insertSequenceTableSql(seq, "next_val"));
    }

    @Test
    public void identityColumns() {
        assertEquals(
                "SET IDENTITY_INSERT \"dbo\".\"UP_THING\" ON",
                MSSQL.identityInsertSql(TABLE, true));
        assertEquals(
                "SET IDENTITY_INSERT \"dbo\".\"UP_THING\" OFF",
                MSSQL.identityInsertSql(TABLE, false));
        assertNull(PG.identityInsertSql(TABLE, true));
        assertEquals(
                "SELECT setval(pg_get_serial_sequence('\"public\".\"UP_THING\"', 'ID'), 42, false)",
                PG.resetIdentitySql(TABLE, ID, 42));
        assertEquals(
                "ALTER TABLE \"PUBLIC\".\"UP_THING\" ALTER COLUMN \"ID\" RESTART WITH 42",
                HSQL.resetIdentitySql(TABLE, ID, 42));
        assertEquals(
                "ALTER TABLE \"UPORTAL\".\"UP_THING\" MODIFY (\"ID\" GENERATED BY DEFAULT AS IDENTITY"
                        + " (START WITH LIMIT VALUE))",
                ORACLE.resetIdentitySql(TABLE, ID, 42));
        assertNull(MSSQL.resetIdentitySql(TABLE, ID, 42));
        assertNull(MARIADB.resetIdentitySql(TABLE, ID, 42));
    }

    @Test
    public void mysqlSessionsAndStreaming() {
        assertEquals("SET time_zone = '+00:00'", MARIADB.sessionInitSql(false));
        assertTrue(MARIADB.sessionInitSql(true).contains("NO_AUTO_VALUE_ON_ZERO"));
        assertNull(PG.sessionInitSql(true));
        Dialect connectorJ = new Dialect(Vendor.MARIADB, "`", "uportal", null, "MySQL Connector/J");
        assertEquals(Integer.MIN_VALUE, connectorJ.streamingFetchSize(1000));
        Dialect mariadbJ = new Dialect(Vendor.MARIADB, "`", "uportal", null, "MariaDB Connector/J");
        assertEquals(1000, mariadbJ.streamingFetchSize(1000));
    }

    @Test
    public void detectsVendors() {
        assertEquals(Vendor.MARIADB, Vendor.detect("MySQL", "5.5.5-10.6.20-MariaDB"));
        assertEquals(Vendor.MARIADB, Vendor.detect("MySQL", "11.4.13-MariaDB-ubu2404"));
        assertEquals(Vendor.MYSQL, Vendor.detect("MySQL", "8.4.0"));
        assertEquals(Vendor.SQLSERVER, Vendor.detect("Microsoft SQL Server", "16.00"));
        assertEquals(Vendor.ORACLE, Vendor.detect("Oracle", "Oracle Database 23ai Free"));
        assertEquals(Vendor.HSQLDB, Vendor.detect("HSQL Database Engine", "2.7.4"));
        assertEquals(Vendor.POSTGRESQL, Vendor.detect("PostgreSQL", "16.15"));
    }
}
