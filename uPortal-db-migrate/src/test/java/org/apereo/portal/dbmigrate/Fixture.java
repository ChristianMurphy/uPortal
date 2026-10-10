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
package org.apereo.portal.dbmigrate;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A small uPortal-shaped schema for copy tests, in each vendor's 5.17 spelling: foreign keys
 * (including a self-reference whose child rows come before their parents), booleans, CHAR,
 * decimals, doubles, a CLOB / PostgreSQL OID-in-text event column, a BLOB, date, time and timestamp
 * values (one inside the America/New_York DST gap), sequences or Hibernate's sequence tables, and a
 * UP_SEQUENCE counter table.
 */
public final class Fixture {

    /** The DST-gap timestamp: 02:30 does not exist on 2026-03-08 in America/New_York. */
    public static final LocalDateTime DST_GAP = LocalDateTime.of(2026, 3, 8, 2, 30, 0, 123_456_000);

    public static final String RULES =
            "generator UP_THING_SEQ allocation=5 serves=UP_THING.THING_ID\n"
                    + "generator UP_THING_TYPE_SEQ allocation=1 serves=UP_THING_TYPE.TYPE_ID\n"
                    + "generator UP_RAW_EVENTS_SEQ allocation=1000 serves=UP_RAW_EVENTS.EVENT_ID\n";

    static final String[] GENERATORS = {"UP_THING_SEQ", "UP_THING_TYPE_SEQ", "UP_RAW_EVENTS_SEQ"};
    static final int[] INCREMENTS = {5, 1, 1000};

    private Fixture() {}

    /** Creates the empty schema. */
    public static void createSchema(Connection c, Vendor v) throws SQLException {
        String bool = v == Vendor.POSTGRESQL || v == Vendor.HSQLDB ? "BOOLEAN" : "BIT(1)";
        String ts = v.isMySqlFamily() ? "DATETIME" : "TIMESTAMP";
        String clob = v == Vendor.POSTGRESQL ? "TEXT" : v.isMySqlFamily() ? "LONGTEXT" : "CLOB(1M)";
        String blob =
                v == Vendor.POSTGRESQL ? "BYTEA" : v.isMySqlFamily() ? "LONGBLOB" : "BLOB(1M)";
        String dbl = v == Vendor.POSTGRESQL ? "FLOAT8" : "DOUBLE";
        List<String> ddl = new ArrayList<>();
        ddl.add(
                "CREATE TABLE UP_THING_TYPE (TYPE_ID BIGINT NOT NULL PRIMARY KEY,"
                        + " NAME VARCHAR(100) NOT NULL, ENABLED "
                        + bool
                        + " NOT NULL)");
        ddl.add(
                "CREATE TABLE UP_THING (THING_ID BIGINT NOT NULL PRIMARY KEY,"
                        + " TYPE_ID BIGINT NOT NULL, PARENT_ID BIGINT, CODE CHAR(8),"
                        + " TITLE VARCHAR(200), CREATED "
                        + ts
                        + " NOT NULL, DAY_VALUE DATE, AT_TIME TIME, AMOUNT DECIMAL(12,4),"
                        + " RATIO "
                        + dbl
                        + ", BODY "
                        + clob
                        + ", PAYLOAD "
                        + blob
                        + ", CONSTRAINT FK_THING_TYPE FOREIGN KEY (TYPE_ID)"
                        + " REFERENCES UP_THING_TYPE (TYPE_ID),"
                        + " CONSTRAINT FK_THING_PARENT FOREIGN KEY (PARENT_ID)"
                        + " REFERENCES UP_THING (THING_ID))");
        ddl.add(
                "CREATE TABLE UP_RAW_EVENTS (EVENT_ID BIGINT NOT NULL PRIMARY KEY, EVENT_DATA "
                        + (v == Vendor.POSTGRESQL ? "TEXT" : clob)
                        + " NOT NULL, "
                        + (v == Vendor.HSQLDB || v == Vendor.POSTGRESQL
                                ? "\"TIMESTAMP\""
                                : "TIMESTAMP")
                        + " "
                        + ts
                        + " NOT NULL, AGGREGATED "
                        + bool
                        + ")");
        ddl.add(
                "CREATE TABLE UP_SEQUENCE (SEQUENCE_NAME VARCHAR(255) NOT NULL PRIMARY KEY,"
                        + " SEQUENCE_VALUE INTEGER)");
        for (int i = 0; i < GENERATORS.length; i++) {
            if (v.isMySqlFamily()) {
                ddl.add("CREATE TABLE " + GENERATORS[i] + " (next_val BIGINT)");
                ddl.add("INSERT INTO " + GENERATORS[i] + " VALUES (1)");
            } else {
                ddl.add(
                        "CREATE SEQUENCE "
                                + GENERATORS[i]
                                + " START WITH 1 INCREMENT BY "
                                + INCREMENTS[i]);
            }
        }
        try (Statement st = c.createStatement()) {
            for (String sql : ddl) {
                st.execute(v == Vendor.POSTGRESQL ? sql.toLowerCase(java.util.Locale.ROOT) : sql);
            }
        }
        if (!c.getAutoCommit()) {
            c.commit();
        }
    }

    /** A 100 kB string with characters outside the BMP. */
    public static String longText() {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 100_000) {
            sb.append("Grüße ✓ 𝄞 line ").append(sb.length()).append('\n');
        }
        return sb.toString();
    }

    /** Inserts the rows and moves the generators the way a running portal would have. */
    public static void insertRows(Connection c, Vendor v) throws SQLException {
        boolean pg = v == Vendor.POSTGRESQL;
        try (PreparedStatement ps =
                c.prepareStatement(
                        "INSERT INTO UP_THING_TYPE (TYPE_ID, NAME, ENABLED) VALUES (?, ?, ?)")) {
            for (int i = 1; i <= 5; i++) {
                ps.setLong(1, i);
                ps.setString(2, "type " + i);
                ps.setBoolean(3, i % 2 == 0);
                ps.executeUpdate();
            }
        }
        byte[] payload = new byte[256];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        String insert =
                "INSERT INTO UP_THING (THING_ID, TYPE_ID, PARENT_ID, CODE, TITLE, CREATED, DAY_VALUE,"
                        + " AT_TIME, AMOUNT, RATIO, BODY, PAYLOAD) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,"
                        + " ?, ?, ?)";
        // Children (1..10) reference parents with higher ids (11..20): needs the second pass
        try (PreparedStatement ps = c.prepareStatement(insert)) {
            for (int id = 20; id >= 1; id--) {
                ps.setLong(1, id);
                ps.setLong(2, 1 + id % 5);
                if (id <= 10) {
                    ps.setLong(3, id + 10);
                } else {
                    ps.setNull(3, Types.BIGINT);
                }
                ps.setString(4, id % 3 == 0 ? null : "C" + id);
                ps.setString(5, id == 7 ? "" : "Thing " + id + " é");
                ps.setObject(
                        6,
                        id == 1
                                ? DST_GAP
                                : LocalDateTime.of(2025, 11, 2, 1, 30)
                                        .plusMinutes(id * 7)
                                        .plusNanos(id * 1000L));
                ps.setObject(7, LocalDate.of(2026, 3, 8).plusDays(id));
                ps.setObject(8, LocalTime.of(id % 24, 15, 30));
                ps.setBigDecimal(
                        9,
                        new java.math.BigDecimal("12.3400")
                                .multiply(java.math.BigDecimal.valueOf(id)));
                ps.setDouble(10, id == 2 ? 0.1 + 0.2 : 1.0 / id);
                ps.setString(11, id == 3 ? longText() : id == 4 ? null : "body " + id);
                if (id == 5) {
                    ps.setNull(12, pg ? Types.BINARY : Types.BLOB);
                } else {
                    ps.setBytes(12, payload);
                }
                ps.executeUpdate();
            }
        }
        String event =
                pg
                        ? "INSERT INTO UP_RAW_EVENTS (EVENT_ID, EVENT_DATA, \"timestamp\", AGGREGATED)"
                                + " VALUES (?, CAST(lo_from_bytea(0, ?) AS text), ?, ?)"
                        : "INSERT INTO UP_RAW_EVENTS (EVENT_ID, EVENT_DATA, "
                                + (v == Vendor.HSQLDB ? "\"TIMESTAMP\"" : "TIMESTAMP")
                                + ", AGGREGATED) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(event)) {
            for (int id = 1; id <= 30; id++) {
                String json = "{\"@c\":\".LoginEvent\",\"id\":" + id + ",\"user\":\"ü" + id + "\"}";
                ps.setLong(1, 1000 + id);
                if (pg) {
                    ps.setBytes(2, json.getBytes(StandardCharsets.UTF_8));
                } else {
                    ps.setString(2, json);
                }
                ps.setObject(3, LocalDateTime.of(2026, 10, 10, 12, 0).plusSeconds(id));
                if (id % 3 == 0) {
                    ps.setNull(4, v == Vendor.HSQLDB || pg ? Types.BOOLEAN : Types.BIT);
                } else {
                    ps.setBoolean(4, id % 2 == 0);
                }
                ps.executeUpdate();
            }
        }
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO UP_SEQUENCE VALUES ('UP_USER', 110)");
            st.execute("INSERT INTO UP_SEQUENCE VALUES ('UP_GROUP', 60)");
            // Generators the way a portal leaves them: UP_THING_SEQ is ahead of max(id),
            // UP_THING_TYPE_SEQ is behind it (rows imported with explicit ids)
            setNext(st, v, "UP_THING_SEQ", 26);
            setNext(st, v, "UP_THING_TYPE_SEQ", 3);
            setNext(st, v, "UP_RAW_EVENTS_SEQ", 2001);
        }
        if (!c.getAutoCommit()) {
            c.commit();
        }
    }

    private static void setNext(Statement st, Vendor v, String gen, long next) throws SQLException {
        switch (v) {
            case POSTGRESQL:
                st.execute(
                        "SELECT setval('"
                                + gen.toLowerCase(java.util.Locale.ROOT)
                                + "', "
                                + next
                                + ", false)");
                break;
            case MYSQL:
            case MARIADB:
                st.execute("UPDATE " + gen + " SET next_val = " + next);
                break;
            default:
                st.execute("ALTER SEQUENCE " + gen + " RESTART WITH " + next);
        }
    }
}
