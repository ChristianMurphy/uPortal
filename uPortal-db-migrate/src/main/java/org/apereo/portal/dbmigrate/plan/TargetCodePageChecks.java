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
package org.apereo.portal.dbmigrate.plan;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apereo.portal.dbmigrate.schema.Column;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.schema.Names;
import org.apereo.portal.dbmigrate.schema.SchemaModel;
import org.apereo.portal.dbmigrate.types.ColumnType;
import org.apereo.portal.dbmigrate.types.ColumnType.Family;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * Checks, before anything is written, that every source text value fits the characters its target
 * column can hold (MOD-I202). A SQL Server {@code varchar} on a collation that isn't UTF-8 keeps
 * one code page (windows-1252 by default, MOD-I201), a MySQL/MariaDB column has a character set of
 * its own ({@code latin1} is windows-1252; {@code utf8mb3} can't hold emoji), and a PostgreSQL or
 * Oracle database has one for all its columns. A character the target can't hold is stored as '?'
 * or refused, which verification only finds after the whole copy. A plan error names the target
 * column, its encoding, the number of source rows that don't fit, and the key and the first
 * unencodable character of one of them.
 *
 * <p>Not covered, so still found by verification: PostgreSQL text columns that hold large object
 * OIDs (the text is in the large object), and columns copied through a transform.
 */
public final class TargetCodePageChecks {

    /** The encoding of a column, or null when it holds every character. */
    public interface Encodings {
        TextEncoding of(String table, Column column);
    }

    private TargetCodePageChecks() {}

    /** Adds a plan error for every target text column that source values don't fit. */
    public static void check(MigrationPlan plan, JdbcOperations source, JdbcOperations target) {
        List<String> unknown = new ArrayList<>();
        Encodings targetEncodings = read(plan.target(), target, unknown);
        Encodings sourceEncodings = read(plan.source(), source, new ArrayList<>());
        for (String u : new LinkedHashSet<>(unknown)) {
            plan.warn(
                    "Target character set "
                            + u
                            + " is not one the plan knows; its text columns are not checked"
                            + " (verification still compares every value)");
        }
        check(plan, source, targetEncodings, sourceEncodings);
    }

    /** {@link #check(MigrationPlan, JdbcOperations, JdbcOperations)} with the encodings given. */
    public static void check(
            MigrationPlan plan,
            JdbcOperations source,
            Encodings targetEncodings,
            Encodings sourceEncodings) {
        for (TableMapping m : plan.tables()) {
            for (ColumnMapping cm : m.columns()) {
                if (cm.source() == null || cm.transform() != null) {
                    continue;
                }
                ColumnType st = cm.pair().source();
                if (cm.pair().logical() != Family.STRING
                        || st.postgresLargeObject()
                        || st.postgresOidText()) {
                    continue;
                }
                TextEncoding te = targetEncodings.of(m.target().name(), cm.target());
                if (te == null || te.isUnicode()) {
                    continue;
                }
                TextEncoding se = sourceEncodings.of(m.source().name(), cm.source());
                if (se != null && se.name().equals(te.name())) {
                    continue; // the source column holds the same characters
                }
                Finding f = scan(plan.source().dialect(), source, m, cm.source().name(), te);
                if (f.rows > 0) {
                    plan.error(
                            "Target column "
                                    + m.target().name()
                                    + "."
                                    + cm.target().name()
                                    + " holds "
                                    + te.name()
                                    + " only, but "
                                    + f.rows
                                    + " source rows of "
                                    + m.source().name()
                                    + "."
                                    + cm.source().name()
                                    + " have other characters: "
                                    + f.example);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the source values

    private static final class Finding {
        long rows;
        String example;
    }

    private static Finding scan(
            Dialect sd, JdbcOperations source, TableMapping m, String column, TextEncoding te) {
        List<String> key = m.source().primaryKey();
        List<String> selected = new ArrayList<>();
        for (String k : key) {
            selected.add(sd.quote(k));
        }
        String col = sd.quote(column);
        selected.add(col);
        String sql =
                "SELECT "
                        + String.join(", ", selected)
                        + " FROM "
                        + sd.qualify(m.source().name())
                        + " WHERE "
                        + col
                        + " IS NOT NULL"
                        + (m.sourceFilter() == null ? "" : " AND (" + m.sourceFilter() + ")")
                        // The example is the offending row with the lowest key
                        + (key.isEmpty()
                                ? ""
                                : " ORDER BY "
                                        + String.join(", ", selected.subList(0, key.size())));
        Finding f = new Finding();
        source.execute(
                (ConnectionCallback<Void>)
                        c -> {
                            read(c, sd, sql, key, te, f);
                            return null;
                        });
        return f;
    }

    @SuppressFBWarnings(
            value = "SECSQLIJDBC",
            justification =
                    "Table and column names come from the source database's own metadata, quoted by"
                            + " Dialect.quote and Dialect.qualify; the only other condition is the"
                            + " plan's large-object filter, which the tool builds from those names."
                            + " No row value or command-line text enters it.")
    private static void read(
            Connection c, Dialect sd, String sql, List<String> key, TextEncoding te, Finding f)
            throws SQLException {
        // PostgreSQL streams only inside a transaction
        boolean autoCommit = c.getAutoCommit();
        if (autoCommit) {
            c.setAutoCommit(false);
        }
        try (PreparedStatement ps =
                c.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            ps.setFetchSize(sd.streamingFetchSize(1000));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String value = rs.getString(key.size() + 1);
                    int cp = value == null ? -1 : te.firstUnencodable(value);
                    if (cp < 0) {
                        continue;
                    }
                    f.rows++;
                    if (f.example == null) {
                        f.example = example(rs, key, cp);
                    }
                }
            }
        } finally {
            if (autoCommit) {
                c.rollback();
                c.setAutoCommit(true);
            }
        }
    }

    private static String example(ResultSet rs, List<String> key, int codePoint)
            throws SQLException {
        String character = String.format(Locale.ROOT, "U+%04X", codePoint);
        if (key.isEmpty()) {
            return "the first has " + character;
        }
        List<String> values = new ArrayList<>();
        for (int i = 1; i <= key.size(); i++) {
            values.add(String.valueOf(rs.getObject(i)));
        }
        return "the one with "
                + String.join(", ", key)
                + " = "
                + String.join(", ", values)
                + " has "
                + character;
    }

    // ------------------------------------------------------------------ the encodings

    /**
     * The encodings of a schema's text columns, from the database's catalog. Character sets with no
     * known mapping are added to {@code unknown} and treated as Unicode.
     */
    static Encodings read(SchemaModel model, JdbcOperations jdbc, List<String> unknown) {
        Dialect d = model.dialect();
        switch (model.vendor()) {
            case SQLSERVER:
                Map<String, TextEncoding> sqlServer = new HashMap<>();
                jdbc.query(
                        "SELECT t.name, c.name, c.collation_name,"
                                + " CAST(COLLATIONPROPERTY(c.collation_name, 'CodePage') AS int)"
                                + " FROM sys.columns c"
                                + " JOIN sys.tables t ON t.object_id = c.object_id"
                                + " JOIN sys.schemas s ON s.schema_id = t.schema_id"
                                + " WHERE s.name = ? AND c.collation_name IS NOT NULL"
                                + " AND TYPE_NAME(c.system_type_id) IN ('char', 'varchar', 'text')",
                        rs -> {
                            int codePage = rs.getInt(4);
                            TextEncoding e = TextEncoding.forSqlServerCodePage(codePage);
                            if (e == null) {
                                unknown.add(rs.getString(3) + " (code page " + codePage + ")");
                            } else if (!e.isUnicode()) {
                                sqlServer.put(columnKey(rs.getString(1), rs.getString(2)), e);
                            }
                        },
                        d.schema());
                return (table, column) -> sqlServer.get(columnKey(table, column.name()));
            case MYSQL:
            case MARIADB:
                Map<String, TextEncoding> mysql = new HashMap<>();
                jdbc.query(
                        "SELECT TABLE_NAME, COLUMN_NAME, CHARACTER_SET_NAME"
                                + " FROM information_schema.COLUMNS"
                                + " WHERE TABLE_SCHEMA = ? AND CHARACTER_SET_NAME IS NOT NULL",
                        rs -> {
                            String cs = rs.getString(3);
                            TextEncoding e = TextEncoding.forMySql(cs);
                            if (e == null) {
                                unknown.add(cs);
                            } else if (!e.isUnicode()) {
                                mysql.put(columnKey(rs.getString(1), rs.getString(2)), e);
                            }
                        },
                        d.catalog());
                return (table, column) -> mysql.get(columnKey(table, column.name()));
            case POSTGRESQL:
                String pg =
                        jdbc.queryForObject(
                                "SELECT pg_encoding_to_char(encoding) FROM pg_database"
                                        + " WHERE datname = current_database()",
                                String.class);
                TextEncoding database =
                        pg == null
                                ? TextEncoding.UNICODE
                                : known(TextEncoding.forPostgres(pg), pg, unknown);
                return (table, column) -> database;
            case ORACLE:
                Map<String, String> nls = new HashMap<>();
                jdbc.query(
                        "SELECT parameter, value FROM nls_database_parameters"
                                + " WHERE parameter IN ('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET')",
                        rs -> {
                            nls.put(rs.getString(1), rs.getString(2));
                        });
                String cs = nls.getOrDefault("NLS_CHARACTERSET", "AL32UTF8");
                String ncs = nls.getOrDefault("NLS_NCHAR_CHARACTERSET", "AL16UTF16");
                TextEncoding chars = known(TextEncoding.forOracle(cs), cs, unknown);
                TextEncoding nchars = known(TextEncoding.forOracle(ncs), ncs, unknown);
                return (table, column) ->
                        column.typeName() != null
                                        && column.typeName()
                                                .toUpperCase(Locale.ROOT)
                                                .startsWith("N")
                                ? nchars
                                : chars;
            default:
                return (table, column) -> null;
        }
    }

    private static TextEncoding known(TextEncoding e, String name, List<String> unknown) {
        if (e == null) {
            unknown.add(name);
            return TextEncoding.UNICODE;
        }
        return e;
    }

    private static String columnKey(String table, String column) {
        return Names.key(table) + "." + Names.key(column);
    }
}
