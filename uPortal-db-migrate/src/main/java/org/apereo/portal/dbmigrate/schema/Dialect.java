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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apereo.portal.dbmigrate.Vendor;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * Everything that is spelled differently per database: identifier quoting and qualification,
 * sequences, identity columns and session settings. The SQL is generated here, without a
 * connection, so it can be unit tested for vendors that are not available locally.
 */
public final class Dialect {

    private final Vendor vendor;
    private final String quote;
    private final String catalog;
    private final String schema;
    private final String driverName;

    public Dialect(Vendor vendor, String quote, String catalog, String schema, String driverName) {
        this.vendor = vendor;
        this.quote = quote == null || quote.trim().isEmpty() ? "\"" : quote.trim();
        this.catalog = catalog;
        this.schema = schema;
        this.driverName = driverName == null ? "" : driverName;
    }

    /** Quoting and scope only; for SQL generation in tests. */
    public static Dialect forVendor(Vendor vendor, String catalog, String schema) {
        return new Dialect(vendor, vendor.isMySqlFamily() ? "`" : "\"", catalog, schema, "");
    }

    public Vendor vendor() {
        return vendor;
    }

    public String catalog() {
        return catalog;
    }

    public String schema() {
        return schema;
    }

    public String quote(String identifier) {
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    /** A table or sequence name qualified with the schema (or the catalog on MySQL/MariaDB). */
    public String qualify(String name) {
        if (vendor.isMySqlFamily()) {
            return catalog == null ? quote(name) : quote(catalog) + "." + quote(name);
        }
        return schema == null ? quote(name) : quote(schema) + "." + quote(name);
    }

    // ------------------------------------------------------------------ session and streaming

    /**
     * The statement run on every new pooled connection, or null. MySQL/MariaDB sessions use UTC so
     * that TIMESTAMP columns (stored as UTC, converted to the session zone) keep their instant, and
     * the target fails loudly instead of truncating strings or turning an explicit 0 id into a new
     * auto-increment value.
     */
    public String sessionInitSql(boolean target) {
        if (!vendor.isMySqlFamily()) {
            return null;
        }
        return "SET time_zone = '+00:00'"
                + (target
                        ? ", sql_mode = CONCAT_WS(',', NULLIF(@@sql_mode, ''),"
                                + " 'STRICT_ALL_TABLES', 'NO_AUTO_VALUE_ON_ZERO')"
                        : "");
    }

    /**
     * The fetch size that makes the driver stream instead of loading the whole result: MySQL
     * Connector/J only streams with {@link Integer#MIN_VALUE} (unless useCursorFetch is set).
     */
    public int streamingFetchSize(int requested) {
        if (driverName.toLowerCase(Locale.ROOT).contains("mysql connector")) {
            return Integer.MIN_VALUE;
        }
        return requested;
    }

    // ------------------------------------------------------------------ sequences

    /** The query listing the sequences in scope (one parameter: the schema), or null. */
    public String listSequencesSql() {
        switch (vendor) {
            case POSTGRESQL:
                return "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?";
            case HSQLDB:
                return "SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_SCHEMA = ?";
            case ORACLE:
                return "SELECT SEQUENCE_NAME FROM ALL_SEQUENCES WHERE SEQUENCE_OWNER = ?";
            case SQLSERVER:
                return "SELECT s.name FROM sys.sequences s JOIN sys.schemas sc"
                        + " ON s.schema_id = sc.schema_id WHERE sc.name = ?";
            case MARIADB:
                return "SELECT TABLE_NAME FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'SEQUENCE'";
            default:
                return null;
        }
    }

    public List<String> listSequences(JdbcOperations jdbc) {
        String sql = listSequencesSql();
        if (sql == null) {
            return new ArrayList<>();
        }
        return jdbc.queryForList(sql, String.class, vendor.isMySqlFamily() ? catalog : schema);
    }

    /**
     * The value the next call to the sequence returns, and its increment. On Oracle and MariaDB the
     * cached-ahead value is reported, which is at or above the next value.
     */
    public GeneratorState readSequence(JdbcOperations jdbc, String name) {
        switch (vendor) {
            case POSTGRESQL:
                {
                    Long increment =
                            jdbc.queryForObject(
                                    "SELECT CAST(increment AS bigint) FROM"
                                            + " information_schema.sequences"
                                            + " WHERE sequence_schema = ? AND sequence_name = ?",
                                    Long.class,
                                    schema,
                                    name);
                    return jdbc.queryForObject(
                            "SELECT last_value, is_called FROM " + qualify(name),
                            (rs, n) -> {
                                long last = rs.getLong(1);
                                return GeneratorState.sequence(
                                        name,
                                        rs.getBoolean(2) ? last + increment : last,
                                        increment);
                            });
                }
            case HSQLDB:
                return jdbc.queryForObject(
                        "SELECT NEXT_VALUE, INCREMENT FROM INFORMATION_SCHEMA.SEQUENCES"
                                + " WHERE SEQUENCE_SCHEMA = ? AND SEQUENCE_NAME = ?",
                        (rs, n) ->
                                GeneratorState.sequence(
                                        name,
                                        Long.parseLong(rs.getString(1).trim()),
                                        Long.parseLong(rs.getString(2).trim())),
                        schema,
                        name);
            case ORACLE:
                return jdbc.queryForObject(
                        "SELECT LAST_NUMBER, INCREMENT_BY FROM ALL_SEQUENCES"
                                + " WHERE SEQUENCE_OWNER = ? AND SEQUENCE_NAME = ?",
                        (rs, n) -> GeneratorState.sequence(name, rs.getLong(1), rs.getLong(2)),
                        schema,
                        name);
            case SQLSERVER:
                return jdbc.queryForObject(
                        "SELECT CAST(s.start_value AS bigint), CAST(s.increment AS bigint),"
                                + " CAST(s.last_used_value AS bigint) FROM sys.sequences s"
                                + " JOIN sys.schemas sc ON s.schema_id = sc.schema_id"
                                + " WHERE sc.name = ? AND s.name = ?",
                        (rs, n) -> {
                            long start = rs.getLong(1);
                            long increment = rs.getLong(2);
                            long lastUsed = rs.getLong(3);
                            boolean used = !rs.wasNull();
                            return GeneratorState.sequence(
                                    name, used ? lastUsed + increment : start, increment);
                        },
                        schema,
                        name);
            case MARIADB:
                return jdbc.queryForObject(
                        "SELECT next_not_cached_value, increment FROM " + qualify(name),
                        (rs, n) -> GeneratorState.sequence(name, rs.getLong(1), rs.getLong(2)));
            default:
                throw new IllegalStateException(vendor.displayName() + " has no sequences");
        }
    }

    /** Statements that make the next call to the sequence return {@code next}. */
    public List<String> restartSequenceSql(String name, long next) {
        switch (vendor) {
            case POSTGRESQL:
                return Collections.singletonList(
                        "SELECT setval('"
                                + qualify(name).replace("'", "''")
                                + "', "
                                + next
                                + ", false)");
            case ORACLE:
                // RESTART is documented from Oracle 18c on
                return Collections.singletonList(
                        "ALTER SEQUENCE " + qualify(name) + " RESTART START WITH " + next);
            case HSQLDB:
            case SQLSERVER:
            case MARIADB:
                return Collections.singletonList(
                        "ALTER SEQUENCE " + qualify(name) + " RESTART WITH " + next);
            default:
                throw new IllegalStateException(vendor.displayName() + " has no sequences");
        }
    }

    // ------------------------------------------------------------------ sequence tables

    /** Hibernate's sequence emulation: a one-row table with a {@code next_val} column. */
    public GeneratorState readSequenceTable(JdbcOperations jdbc, Table table, String valueColumn) {
        List<Long> values =
                jdbc.query(
                        "SELECT " + quote(valueColumn) + " FROM " + qualify(table.name()),
                        (rs, n) -> rs.getLong(1));
        Long value = null;
        for (Long v : values) {
            value = value == null ? v : Math.max(value, v);
        }
        return GeneratorState.table(table.name(), value, values.size());
    }

    public String updateSequenceTableSql(Table table, String valueColumn) {
        return "UPDATE " + qualify(table.name()) + " SET " + quote(valueColumn) + " = ?";
    }

    public String insertSequenceTableSql(Table table, String valueColumn) {
        return "INSERT INTO " + qualify(table.name()) + " (" + quote(valueColumn) + ") VALUES (?)";
    }

    // ------------------------------------------------------------------ identity columns

    /** SQL Server refuses explicit values in an identity column unless this is switched on. */
    public String identityInsertSql(Table table, boolean on) {
        if (vendor != Vendor.SQLSERVER) {
            return null;
        }
        return "SET IDENTITY_INSERT " + qualify(table.name()) + (on ? " ON" : " OFF");
    }

    /**
     * Makes the next generated value of an identity / auto-increment column {@code next}. Null
     * where explicit inserts already move the counter (MySQL, MariaDB, SQL Server).
     */
    public String resetIdentitySql(Table table, Column column, long next) {
        switch (vendor) {
            case POSTGRESQL:
                return "SELECT setval(pg_get_serial_sequence('"
                        + qualify(table.name()).replace("'", "''")
                        + "', '"
                        + column.name().replace("'", "''")
                        + "'), "
                        + next
                        + ", false)";
            case HSQLDB:
                return "ALTER TABLE "
                        + qualify(table.name())
                        + " ALTER COLUMN "
                        + quote(column.name())
                        + " RESTART WITH "
                        + next;
            case ORACLE:
                return "ALTER TABLE "
                        + qualify(table.name())
                        + " MODIFY ("
                        + quote(column.name())
                        + " GENERATED BY DEFAULT AS IDENTITY (START WITH LIMIT VALUE))";
            default:
                return null;
        }
    }

    /** Where an ID generator's position lives, and the next value it hands out. */
    public static final class GeneratorState {
        public enum Kind {
            SEQUENCE,
            TABLE
        }

        private final String name;
        private final Kind kind;
        private final Long nextValue;
        private final Long increment;
        private final int rows;

        private GeneratorState(String name, Kind kind, Long nextValue, Long increment, int rows) {
            this.name = name;
            this.kind = kind;
            this.nextValue = nextValue;
            this.increment = increment;
            this.rows = rows;
        }

        public static GeneratorState sequence(String name, long next, long increment) {
            return new GeneratorState(name, Kind.SEQUENCE, next, increment, 1);
        }

        public static GeneratorState table(String name, Long next, int rows) {
            return new GeneratorState(name, Kind.TABLE, next, null, rows);
        }

        public String name() {
            return name;
        }

        public Kind kind() {
            return kind;
        }

        /** Null for an emulation table without a row (Hibernate inserts it on first use). */
        public Long nextValue() {
            return nextValue;
        }

        /** The database increment of a sequence; null for tables. */
        public Long increment() {
            return increment;
        }

        public int rows() {
            return rows;
        }

        @Override
        public String toString() {
            return kind + " " + name + " next=" + nextValue;
        }
    }
}
