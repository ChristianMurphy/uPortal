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

import static java.time.temporal.ChronoField.NANO_OF_SECOND;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.apereo.portal.dbmigrate.it.DbServer;
import org.apereo.portal.dbmigrate.it.DbServer.Kind;
import org.apereo.portal.dbmigrate.it.PropColumn;
import org.apereo.portal.dbmigrate.it.TestDatabase;
import org.apereo.portal.dbmigrate.report.MigrationReport;

/**
 * One vendor pair of the type property tests: writes rows into a new source database, copies them
 * with the tool into a new target database, and compares every value read back from the target with
 * the value read back from the source, by this class's own rules rather than the tool's
 * verification:
 *
 * <ul>
 *   <li>exact equality, except for the documented conversions: fractional seconds truncated to the
 *       lower precision of the two columns (MOD-I95), SQL Server {@code datetime}'s 1/300 s, CHAR
 *       padding, Oracle's '' as NULL;
 *   <li>a documented conversion that changed a value must be in the report (and on the console),
 *       never silent;
 *   <li>dates and timestamps are also compared as the servers render them as text, so a driver that
 *       shifts a value on the way in and back on the way out cannot hide it;
 *   <li>the tool's exit status must be 0, and its own verification must agree.
 * </ul>
 */
final class PairCopy {

    private static final DateTimeFormatter SERVER_TEXT =
            new DateTimeFormatterBuilder()
                    .appendPattern("uuuu-MM-dd")
                    .optionalStart()
                    .appendLiteral(' ')
                    .appendPattern("HH:mm:ss")
                    .optionalStart()
                    .appendFraction(NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .optionalEnd()
                    .toFormatter(Locale.ROOT);

    final Kind source;
    final Kind target;
    final List<PropColumn> columns;

    PairCopy(Kind source, Kind target) {
        this.source = source;
        this.target = target;
        this.columns = PropColumn.forPair(source, target);
    }

    /** Every ordered pair of the run's servers; HSQLDB only as a source. */
    static List<PairCopy> all(List<Kind> kinds) {
        List<PairCopy> out = new ArrayList<>();
        for (Kind s : kinds) {
            for (Kind t : kinds) {
                if (t == Kind.HSQLDB) {
                    continue;
                }
                // Same vendor: PostgreSQL only (old to new server, and its own types)
                if (s != t || s == Kind.POSTGRES) {
                    out.add(new PairCopy(s, t));
                }
            }
        }
        return out;
    }

    String rules() {
        StringBuilder sb = new StringBuilder();
        for (PropColumn c : columns) {
            if (c.rule() != null) {
                sb.append(c.rule()).append('\n');
            }
        }
        return sb.toString();
    }

    /** Copies the rows and returns every problem found; empty when all is as documented. */
    List<String> check(List<Map<PropColumn, Object>> rows, String... options) throws Exception {
        List<String> problems = new ArrayList<>();
        try (TestDatabase s = DbServer.shared(source).create("ps_" + source.id());
                TestDatabase t = DbServer.shared(target).create("pt_" + target.id())) {
            createTable(s, source, columns);
            createTable(t, target, columns);
            insert(s, source, columns, rows);
            Map<Long, Map<PropColumn, Object>> sourceValues = read(s, source, columns);
            checkSourceKeptTheValues(rows, sourceValues, problems);

            List<String> args = new ArrayList<>(Arrays.asList("--batch-size", "3"));
            args.addAll(Arrays.asList(options));
            ToolRun run = ToolRun.run(s, t, rules(), args.toArray(new String[0]));
            if (run.status != DbMigrate.OK) {
                problems.add(this + ": exit status " + run.status);
                // Which values differ, by this class's rules (none: the tool's own verification
                // disagrees with its copy), then the tool's output
                List<String> differences = new ArrayList<>();
                try {
                    compare(
                            sourceValues,
                            read(t, target, columns),
                            EnumSet.noneOf(PropColumn.class),
                            differences);
                } catch (SQLException | RuntimeException e) {
                    differences.add(this + ": reading the target failed: " + e);
                }
                if (differences.isEmpty()) {
                    differences.add(this + ": every value is as documented by this test's rules");
                }
                problems.addAll(differences);
                problems.add(tail(run.output));
                return problems;
            }
            Map<Long, Map<PropColumn, Object>> targetValues = read(t, target, columns);
            Set<PropColumn> changed = EnumSet.noneOf(PropColumn.class);
            compare(sourceValues, targetValues, changed, problems);
            compareServerText(s, t, problems);
            checkReported(run, changed, problems);
        }
        return problems;
    }

    // ------------------------------------------------------------------ the rules

    /** What the target must hold for a source value: the documented conversions applied. */
    Object expected(PropColumn c, Object v) {
        if (v == null) {
            return null;
        }
        if (c.isString()) {
            String s = (String) v;
            return target == Kind.ORACLE && s.isEmpty() ? null : s;
        }
        switch (c) {
            case AT_TIME:
                return truncate((LocalTime) v, digits(c));
            case TS0:
            case TS3:
            case TS6:
            case TS517:
                {
                    LocalDateTime t = (LocalDateTime) v;
                    if (c.sqlServerDatetime(target) && !c.sqlServerDatetime(source)) {
                        return sqlServerDatetime(t);
                    }
                    return truncate(t, digits(c));
                }
            case TSTZ:
                {
                    OffsetDateTime o = (OffsetDateTime) v;
                    return o.withNano(truncate(o.getNano(), digits(c)));
                }
            default:
                return v;
        }
    }

    boolean same(PropColumn c, Object expected, Object actual) {
        if (c == PropColumn.CODE) {
            return Objects.equals(blank(expected), blank(actual));
        }
        if (expected == null || actual == null) {
            return expected == actual;
        }
        switch (c) {
            case AMOUNT:
                return ((BigDecimal) expected).compareTo((BigDecimal) actual) == 0;
            case RATIO:
                {
                    double e = (Double) expected;
                    double a = (Double) actual;
                    // MySQL and MariaDB keep -0.0 as 0 (equal by value; not reported)
                    if (source.isMySqlFamily() || target.isMySqlFamily()) {
                        e = e == 0 ? 0.0 : e;
                        a = a == 0 ? 0.0 : a;
                    }
                    return Double.compare(e, a) == 0;
                }
            case PAYLOAD:
            case LOB_DATA:
                return Arrays.equals((byte[]) expected, (byte[]) actual);
            case TSTZ:
                return ((OffsetDateTime) expected).isEqual((OffsetDateTime) actual);
            default:
                return expected.equals(actual);
        }
    }

    /** Fractional digits both columns keep. */
    int digits(PropColumn c) {
        return Math.min(c.precision(source), c.precision(target));
    }

    /** A value as written into the source column: what the source column can keep. */
    Object forSource(PropColumn c, Object v) {
        if (v == null) {
            return null;
        }
        switch (c) {
            case AT_TIME:
                return truncate((LocalTime) v, c.precision(source));
            case TS0:
            case TS3:
            case TS6:
            case TS517:
                return c.sqlServerDatetime(source)
                        ? sqlServerDatetime((LocalDateTime) v)
                        : truncate((LocalDateTime) v, c.precision(source));
            default:
                return v;
        }
    }

    static int truncate(int nanos, int digits) {
        if (digits >= 9) {
            return nanos;
        }
        int unit = (int) Math.pow(10, 9 - digits);
        return nanos / unit * unit;
    }

    static LocalTime truncate(LocalTime t, int digits) {
        return t.withNano(truncate(t.getNano(), digits));
    }

    static LocalDateTime truncate(LocalDateTime t, int digits) {
        return t.withNano(truncate(t.getNano(), digits));
    }

    /**
     * SQL Server {@code datetime}: the nearest 1/300 s tick, read back as whole milliseconds (.000,
     * .003, .007); tick 300 is the next second.
     */
    static LocalDateTime sqlServerDatetime(LocalDateTime t) {
        long ticks = (t.getNano() * 300L + 500_000_000L) / 1_000_000_000L;
        long millis = (ticks * 1000 + 150) / 300;
        return t.withNano(0).plusNanos(millis * 1_000_000L);
    }

    /**
     * A CHAR value without its padding; with Oracle on either side a blank value is NULL, as Oracle
     * stores '' (the tool's verification compares it the same way).
     */
    private String blank(Object v) {
        if (v == null) {
            return null;
        }
        String s = rtrim((String) v);
        return s.isEmpty() && (source == Kind.ORACLE || target == Kind.ORACLE) ? null : s;
    }

    static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }

    // ------------------------------------------------------------------ checks

    private void checkSourceKeptTheValues(
            List<Map<PropColumn, Object>> rows,
            Map<Long, Map<PropColumn, Object>> stored,
            List<String> problems) {
        for (Map<PropColumn, Object> row : rows) {
            long id = (Long) row.get(PropColumn.ID);
            for (PropColumn c : columns) {
                Object written = forSource(c, row.get(c));
                // What the source vendor itself does with the value: Oracle '' is NULL
                if (source == Kind.ORACLE && "".equals(written)) {
                    written = null;
                }
                Object back = stored.get(id).get(c);
                if (!same(c, written, back)) {
                    problems.add(
                            this
                                    + ": the SOURCE "
                                    + source.id()
                                    + " did not keep "
                                    + c
                                    + " of row "
                                    + id
                                    + ": wrote "
                                    + show(written)
                                    + ", read "
                                    + show(back));
                }
            }
        }
    }

    private void compare(
            Map<Long, Map<PropColumn, Object>> sourceValues,
            Map<Long, Map<PropColumn, Object>> targetValues,
            Set<PropColumn> changed,
            List<String> problems) {
        if (!sourceValues.keySet().equals(targetValues.keySet())) {
            problems.add(
                    this
                            + ": rows "
                            + sourceValues.keySet()
                            + " in the source, "
                            + targetValues.keySet()
                            + " in the target");
        }
        for (Map.Entry<Long, Map<PropColumn, Object>> e : sourceValues.entrySet()) {
            Map<PropColumn, Object> t = targetValues.get(e.getKey());
            if (t == null) {
                continue;
            }
            for (PropColumn c : columns) {
                Object s = e.getValue().get(c);
                Object want = expected(c, s);
                boolean converted =
                        c == PropColumn.CODE ? "".equals(s) && want == null : !same(c, s, want);
                if (converted) {
                    changed.add(c);
                }
                if (!same(c, want, t.get(c))) {
                    problems.add(
                            this
                                    + ": "
                                    + c
                                    + " of row "
                                    + e.getKey()
                                    + ": source "
                                    + show(s)
                                    + ", expected "
                                    + show(want)
                                    + ", target "
                                    + show(t.get(c))
                                    + firstDifference(want, t.get(c)));
                }
            }
        }
    }

    /** '' or an empty byte array, which Oracle stores as NULL. */
    static boolean isEmpty(Object v) {
        return "".equals(v) || (v instanceof byte[] && ((byte[]) v).length == 0);
    }

    /** Where two long strings or byte arrays first differ, for a failure message. */
    static String firstDifference(Object want, Object got) {
        if (want instanceof String && got instanceof String) {
            String a = (String) want;
            String b = (String) got;
            int i = 0;
            while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
                i++;
            }
            int from = Math.max(0, i - 4);
            return "; first difference at unit "
                    + i
                    + ": expected "
                    + units(a, from, i + 6)
                    + ", target "
                    + units(b, from, i + 6);
        }
        if (want instanceof byte[] && got instanceof byte[]) {
            byte[] a = (byte[]) want;
            byte[] b = (byte[]) got;
            int i = 0;
            while (i < a.length && i < b.length && a[i] == b[i]) {
                i++;
            }
            return "; first difference at byte " + i;
        }
        return "";
    }

    /** The UTF-16 units of s from 'from' to 'to', as hex. */
    private static String units(String s, int from, int to) {
        StringBuilder sb = new StringBuilder("[");
        for (int k = from; k < Math.min(to, s.length()); k++) {
            sb.append(String.format(Locale.ROOT, k == from ? "%04X" : " %04X", (int) s.charAt(k)));
        }
        return sb.append(']').toString();
    }

    /** Dates and timestamps as the servers print them, so no driver conversion is involved. */
    private void compareServerText(TestDatabase s, TestDatabase t, List<String> problems)
            throws SQLException {
        for (PropColumn c : columns) {
            String se = c.textExpression(source);
            String te = c.textExpression(target);
            if (se == null || te == null) {
                continue;
            }
            Map<Long, String> st = texts(s, se);
            Map<Long, String> tt = texts(t, te);
            for (Map.Entry<Long, String> e : st.entrySet()) {
                Object want = expected(c, parse(c, e.getValue()));
                Object got = parse(c, tt.get(e.getKey()));
                if (!Objects.equals(want, got)) {
                    problems.add(
                            this
                                    + ": "
                                    + c
                                    + " of row "
                                    + e.getKey()
                                    + " as the servers print it: source '"
                                    + e.getValue()
                                    + "', target '"
                                    + tt.get(e.getKey())
                                    + "', expected "
                                    + want);
                }
            }
        }
    }

    private static Object parse(PropColumn c, String text) {
        if (text == null) {
            return null;
        }
        TemporalAccessor parsed =
                SERVER_TEXT.parseBest(text.trim(), LocalDateTime::from, LocalDate::from);
        LocalDateTime t =
                parsed instanceof LocalDateTime
                        ? (LocalDateTime) parsed
                        : ((LocalDate) parsed).atStartOfDay();
        return c == PropColumn.DAY_VALUE ? t.toLocalDate() : t;
    }

    private static Map<Long, String> texts(TestDatabase db, String expr) throws SQLException {
        Map<Long, String> out = new TreeMap<>();
        try (Connection c = db.connect();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT ID, " + expr + " FROM UP_PROP")) {
            while (rs.next()) {
                out.put(rs.getLong(1), rs.getString(2));
            }
        }
        return out;
    }

    /**
     * A documented conversion that changed a value must be in the report's notes for the column and
     * in the console summary.
     */
    private void checkReported(ToolRun run, Set<PropColumn> changed, List<String> problems) {
        MigrationReport.TableEntry entry = run.table("UP_PROP");
        if (entry == null) {
            problems.add(this + ": no report entry for UP_PROP");
            return;
        }
        if (!"OK".equals(entry.verify)) {
            problems.add(this + ": the tool's verification says " + entry.verify);
        }
        for (PropColumn c : changed) {
            boolean noted = false;
            for (String n : entry.notes) {
                if (n.toUpperCase(Locale.ROOT).startsWith(c.name() + ":")) {
                    noted = true;
                    if (!run.output.contains(n)) {
                        problems.add(
                                this
                                        + ": the note '"
                                        + n
                                        + "' is in the JSON report but not on the console");
                    }
                }
            }
            if (!noted) {
                problems.add(
                        this
                                + ": values of "
                                + c
                                + " were converted, but the report says nothing about "
                                + c
                                + " (notes: "
                                + entry.notes
                                + ")");
            }
        }
    }

    // ------------------------------------------------------------------ SQL

    static void createTable(TestDatabase db, Kind k, List<PropColumn> columns) throws SQLException {
        List<String> defs = new ArrayList<>();
        for (PropColumn c : columns) {
            defs.add(c.name() + " " + c.ddlType(k) + (c == PropColumn.ID ? " NOT NULL" : ""));
        }
        defs.add("PRIMARY KEY (ID)");
        db.execute("CREATE TABLE UP_PROP (" + String.join(", ", defs) + ")");
    }

    void insert(
            TestDatabase db, Kind k, List<PropColumn> columns, List<Map<PropColumn, Object>> rows)
            throws SQLException {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false); // PostgreSQL large objects are created in the transaction
            for (Map<PropColumn, Object> row : rows) {
                List<String> values = new ArrayList<>();
                List<Object[]> binds = new ArrayList<>();
                for (PropColumn col : columns) {
                    Object v = forSource(col, row.get(col));
                    values.add(col.placeholderFor(k, v));
                    if (v != null) {
                        binds.add(new Object[] {col, v});
                    }
                }
                String sql =
                        "INSERT INTO UP_PROP ("
                                + String.join(", ", names(columns))
                                + ") VALUES ("
                                + String.join(", ", values)
                                + ")";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    int i = 1;
                    for (Object[] b : binds) {
                        ((PropColumn) b[0]).bindValue(ps, i++, k, b[1]);
                    }
                    ps.executeUpdate();
                }
            }
            c.commit();
        }
    }

    static Map<Long, Map<PropColumn, Object>> read(
            TestDatabase db, Kind k, List<PropColumn> columns) throws SQLException {
        List<String> exprs = new ArrayList<>();
        for (PropColumn c : columns) {
            exprs.add(c.selectExpressionFor(k));
        }
        Map<Long, Map<PropColumn, Object>> out = new TreeMap<>();
        try (Connection c = db.connect()) {
            c.setAutoCommit(false); // lo_get needs no more, but keeps PostgreSQL happy
            try (Statement st = c.createStatement();
                    ResultSet rs =
                            st.executeQuery(
                                    "SELECT "
                                            + String.join(", ", exprs)
                                            + " FROM UP_PROP ORDER BY ID")) {
                while (rs.next()) {
                    Map<PropColumn, Object> row = new EnumMap<>(PropColumn.class);
                    for (int i = 0; i < columns.size(); i++) {
                        row.put(columns.get(i), columns.get(i).readValue(rs, i + 1, k));
                    }
                    out.put((Long) row.get(PropColumn.ID), row);
                }
            }
            c.commit();
        }
        return out;
    }

    private static List<String> names(List<PropColumn> columns) {
        List<String> out = new ArrayList<>();
        columns.forEach(c -> out.add(c.name()));
        return out;
    }

    static String show(Object v) {
        if (v == null) {
            return "NULL";
        }
        if (v instanceof byte[]) {
            byte[] b = (byte[]) v;
            return "byte[" + b.length + "]";
        }
        if (v instanceof String) {
            String s = (String) v;
            StringBuilder sb = new StringBuilder("\"");
            s.codePoints()
                    .limit(80)
                    .forEach(
                            cp -> {
                                if (cp < 0x20 || cp == 0x7F || (cp >= 0x80 && cp < 0xA0)) {
                                    sb.append(String.format(Locale.ROOT, "\\u%04X", cp));
                                } else {
                                    sb.appendCodePoint(cp);
                                }
                            });
            if (s.codePointCount(0, s.length()) > 80) {
                sb.append("...(").append(s.length()).append(" units)");
            }
            return sb.append('"').toString();
        }
        if (v instanceof Double) {
            return v + " (bits " + Long.toHexString(Double.doubleToRawLongBits((Double) v)) + ")";
        }
        return v.toString();
    }

    private static String tail(String output) {
        return output.length() > 4000 ? "..." + output.substring(output.length() - 4000) : output;
    }

    @Override
    public String toString() {
        return source.id() + " -> " + target.id();
    }
}
