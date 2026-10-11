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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apereo.portal.dbmigrate.Vendor;
import org.apereo.portal.dbmigrate.schema.Column;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.schema.ForeignKey;
import org.apereo.portal.dbmigrate.schema.Names;
import org.apereo.portal.dbmigrate.types.ColumnType;
import org.apereo.portal.dbmigrate.types.ColumnType.Family;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * Checks, before anything is written, that the source rows fit the constraints the target declares
 * where the two schemas differ: a NOT NULL column the source has NULLs for (or empty strings, which
 * Oracle stores as NULL), a column shorter than source values, a unique key or a foreign key the
 * source rows violate. Without this the copy would fail half-way, with some tables loaded. A target
 * constraint the source declares too is not queried: the source database already enforces it.
 *
 * <p>Not covered, so still found by the copy itself (exit status 1): lengths a vendor counts in
 * bytes (Oracle VARCHAR2 in BYTE semantics), values that collide only under the target's collation
 * (a case- or accent-insensitive MySQL/MariaDB unique key), and CHECK constraints.
 */
public final class TargetConstraintChecks {

    private TargetConstraintChecks() {}

    /** Adds a plan error for every target constraint the source rows violate. */
    public static void check(MigrationPlan plan, JdbcOperations source) {
        Map<String, TableMapping> byTarget = new HashMap<>();
        for (TableMapping m : plan.tables()) {
            byTarget.put(m.target().key(), m);
        }
        for (TableMapping m : plan.tables()) {
            columns(plan, m, source);
            uniqueKeys(plan, m, source);
            foreignKeys(plan, m, byTarget, source);
        }
    }

    // ------------------------------------------------------------------ NOT NULL and length

    private static void columns(MigrationPlan plan, TableMapping m, JdbcOperations source) {
        Dialect sd = plan.source().dialect();
        for (ColumnMapping cm : m.columns()) {
            if (cm.source() == null || cm.transform() != null) {
                continue;
            }
            ColumnType st = cm.pair().source();
            ColumnType tt = cm.pair().target();
            String col = sd.quote(cm.source().name());
            boolean text = cm.pair().logical() == Family.STRING && !st.postgresLargeObject();
            if (!cm.target().nullable() && !cm.target().autoIncrement()) {
                List<String> conditions = new ArrayList<>();
                if (cm.source().nullable()) {
                    conditions.add(col + " IS NULL");
                }
                if (text && tt.emptyStringIsNull() && !st.emptyStringIsNull() && !st.padded()) {
                    conditions.add(lengthExpression(sd, col, true) + " = 0");
                }
                if (!conditions.isEmpty()) {
                    long n = count(source, sd, m, "(" + String.join(" OR ", conditions) + ")");
                    if (n > 0) {
                        plan.error(
                                "Target column "
                                        + m.target().name()
                                        + "."
                                        + cm.target().name()
                                        + " is NOT NULL, but "
                                        + n
                                        + " source rows of "
                                        + m.source().name()
                                        + "."
                                        + cm.source().name()
                                        + " are NULL"
                                        + (tt.emptyStringIsNull() ? " or empty" : ""));
                    }
                }
            }
            int size = tt.column().size();
            if (text
                    && !tt.largeText()
                    && !tt.postgresLargeObject()
                    && size > 0
                    && size < Integer.MAX_VALUE
                    && (st.largeText() || st.column().size() <= 0 || st.column().size() > size)) {
                long n = count(source, sd, m, lengthExpression(sd, col, false) + " > " + size);
                if (n > 0) {
                    plan.error(
                            "Target column "
                                    + m.target().name()
                                    + "."
                                    + cm.target().name()
                                    + " holds "
                                    + size
                                    + " characters, but "
                                    + n
                                    + " source rows of "
                                    + m.source().name()
                                    + "."
                                    + cm.source().name()
                                    + " are longer");
                }
            }
        }
    }

    /**
     * The length of a string in characters, trailing CHAR padding not counted ({@code exact}: every
     * character, for telling '' apart).
     */
    static String lengthExpression(Dialect d, String column, boolean exact) {
        switch (d.vendor()) {
            case SQLSERVER:
                // LEN ignores trailing spaces; DATALENGTH counts bytes, 0 only for ''
                return exact ? "DATALENGTH(" + column + ")" : "LEN(" + column + ")";
            case ORACLE:
                return "LENGTH(" + column + ")";
            default:
                return "CHAR_LENGTH(" + column + ")";
        }
    }

    // ------------------------------------------------------------------ unique keys

    private static void uniqueKeys(MigrationPlan plan, TableMapping m, JdbcOperations source) {
        Dialect sd = plan.source().dialect();
        Set<Set<String>> enforced = new HashSet<>();
        for (List<String> uk : m.source().uniqueKeys()) {
            enforced.add(keySet(uk));
        }
        if (!m.source().primaryKey().isEmpty()) {
            enforced.add(keySet(m.source().primaryKey()));
        }
        // SQL Server's unique indexes count NULL as a value; the others let NULLs repeat
        boolean nullsCollide = plan.target().vendor() == Vendor.SQLSERVER;
        List<List<String>> keys = new ArrayList<>(m.target().uniqueKeys());
        if (!m.target().primaryKey().isEmpty()) {
            keys.add(m.target().primaryKey());
        }
        for (List<String> uk : keys) {
            List<String> sourceColumns = sourceColumns(m, uk);
            if (sourceColumns == null || enforced.contains(keySet(sourceColumns))) {
                continue;
            }
            List<String> quoted = new ArrayList<>();
            List<String> notNull = new ArrayList<>();
            for (String c : sourceColumns) {
                quoted.add(sd.quote(c));
                notNull.add(sd.quote(c) + " IS NOT NULL");
            }
            String where = nullsCollide ? null : String.join(" AND ", notNull);
            String filter = and(where, m.sourceFilter());
            String sql =
                    "SELECT COUNT(*) FROM (SELECT "
                            + String.join(", ", quoted)
                            + " FROM "
                            + sd.qualify(m.source().name())
                            + (filter == null ? "" : " WHERE " + filter)
                            + " GROUP BY "
                            + String.join(", ", quoted)
                            + " HAVING COUNT(*) > 1) d";
            long n = scalar(source, sql);
            if (n > 0) {
                plan.error(
                        "Target table "
                                + m.target().name()
                                + " has a unique key on "
                                + uk
                                + ", but "
                                + n
                                + " values of "
                                + m.source().name()
                                + " "
                                + sourceColumns
                                + " occur more than once");
                enforced.add(keySet(sourceColumns));
            }
        }
    }

    // ------------------------------------------------------------------ foreign keys

    private static void foreignKeys(
            MigrationPlan plan,
            TableMapping m,
            Map<String, TableMapping> byTarget,
            JdbcOperations source) {
        Dialect sd = plan.source().dialect();
        for (ForeignKey fk : m.target().foreignKeys()) {
            TableMapping parent = byTarget.get(Names.key(fk.referencedTable()));
            if (parent == null) {
                continue; // not copied; the planner warns about it
            }
            List<String> childColumns = sourceColumns(m, fk.columns());
            List<String> parentColumns = sourceColumns(parent, fk.referencedColumns());
            if (childColumns == null || parentColumns == null) {
                continue;
            }
            if (sourceEnforces(m, parent, childColumns, parentColumns)
                    && parent.sourceFilter() == null) {
                continue;
            }
            List<String> conditions = new ArrayList<>();
            List<String> join = new ArrayList<>();
            for (int i = 0; i < childColumns.size(); i++) {
                String c = "c." + sd.quote(childColumns.get(i));
                conditions.add(c + " IS NOT NULL");
                join.add("p." + sd.quote(parentColumns.get(i)) + " = " + c);
            }
            if (parent.sourceFilter() != null) {
                join.add("(" + parent.sourceFilter() + ")");
            }
            String sql =
                    "SELECT COUNT(*) FROM "
                            + sd.qualify(m.source().name())
                            + " c WHERE "
                            + String.join(" AND ", conditions)
                            + (m.sourceFilter() == null ? "" : " AND (" + m.sourceFilter() + ")")
                            + " AND NOT EXISTS (SELECT 1 FROM "
                            + sd.qualify(parent.source().name())
                            + " p WHERE "
                            + String.join(" AND ", join)
                            + ")";
            long n = scalar(source, sql);
            if (n > 0) {
                plan.error(
                        "Target foreign key "
                                + fk
                                + ": "
                                + n
                                + " source rows of "
                                + m.source().name()
                                + " "
                                + childColumns
                                + " reference no row of "
                                + parent.source().name()
                                + " "
                                + parentColumns);
            }
        }
    }

    /** Whether the source declares the same foreign key, so its rows already satisfy it. */
    private static boolean sourceEnforces(
            TableMapping child,
            TableMapping parent,
            List<String> childColumns,
            List<String> parentColumns) {
        for (ForeignKey fk : child.source().foreignKeys()) {
            if (Names.key(fk.referencedTable()).equals(parent.source().key())
                    && keyList(fk.columns()).equals(keyList(childColumns))
                    && keyList(fk.referencedColumns()).equals(keyList(parentColumns))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    /** The source columns copied as-is into these target columns, or null. */
    private static List<String> sourceColumns(TableMapping m, List<String> targetColumns) {
        List<String> out = new ArrayList<>();
        for (String tc : targetColumns) {
            String found = null;
            for (ColumnMapping cm : m.columns()) {
                if (cm.target().key().equals(Names.key(tc))) {
                    Column sc = cm.source();
                    if (sc != null && cm.transform() == null) {
                        found = sc.name();
                    }
                }
            }
            if (found == null) {
                return null;
            }
            out.add(found);
        }
        return out;
    }

    private static Set<String> keySet(List<String> columns) {
        return new TreeSet<>(keyList(columns));
    }

    private static List<String> keyList(List<String> columns) {
        List<String> out = new ArrayList<>();
        columns.forEach(c -> out.add(Names.key(c)));
        return out;
    }

    private static String and(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b;
        }
        return b == null ? a : "(" + a + ") AND (" + b + ")";
    }

    private static long count(JdbcOperations jdbc, Dialect d, TableMapping m, String condition) {
        String where = and(condition, m.sourceFilter());
        return scalar(
                jdbc, "SELECT COUNT(*) FROM " + d.qualify(m.source().name()) + " WHERE " + where);
    }

    @SuppressFBWarnings(
            value = "SQL_INJECTION_SPRING_JDBC",
            justification =
                    "Table and column names come from the source database's own metadata, quoted by"
                            + " Dialect.quote and Dialect.qualify; the only condition besides the"
                            + " fixed SQL is the plan's large-object filter, which the tool builds"
                            + " from those names. No row value or command-line text enters it.")
    private static long scalar(JdbcOperations jdbc, String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
