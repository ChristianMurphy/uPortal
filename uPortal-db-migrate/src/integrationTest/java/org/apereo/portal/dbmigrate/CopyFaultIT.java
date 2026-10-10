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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apereo.portal.dbmigrate.it.DbServer;
import org.apereo.portal.dbmigrate.it.DbServer.Kind;
import org.apereo.portal.dbmigrate.it.TestDatabase;
import org.apereo.portal.dbmigrate.report.MigrationReport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Fault injection: the copy tool must fail closed. For each target server of the run, with the
 * {@link Fixture} as source:
 *
 * <ul>
 *   <li>a copy that loses its target (sessions killed, server stopped, JVM killed or interrupted)
 *       ends with a non-zero status and never reports success; the target is left partly filled,
 *       generators unpositioned, and a rerun with {@code --clear-target} recovers;
 *   <li>one corrupted target row (changed, deleted, added, a LOB byte, a timestamp second, ...)
 *       fails {@code --verify-only}, naming the table and only that table;
 *   <li>a plan the target cannot take (a missing table, MOD-I92; an unexpected source column; a
 *       target constraint the source data violates) exits with 2 and writes nothing;
 *   <li>a generator set below the copied ids fails verification.
 * </ul>
 */
@RunWith(Parameterized.class)
public class CopyFaultIT {

    private static final String RULES = Fixture.RULES;

    @Parameters(name = "{0}")
    public static Collection<Object[]> targets() {
        List<Object[]> out = new ArrayList<>();
        for (Kind k : DbServer.enabled()) {
            if (k != Kind.HSQLDB) {
                out.add(new Object[] {k});
            }
        }
        return out;
    }

    private final Kind kind;

    public CopyFaultIT(Kind kind) {
        this.kind = kind;
    }

    private Vendor vendor() {
        return kind.vendor();
    }

    // ------------------------------------------------------------------ fixtures

    /** The fixture's source, in this JVM's HSQLDB. */
    private static TestDatabase source() throws Exception {
        TestDatabase s = DbServer.shared(Kind.HSQLDB).create("fsrc");
        try (Connection c = s.connect()) {
            Fixture.createSchema(c, Vendor.HSQLDB);
            Fixture.insertRows(c, Vendor.HSQLDB);
        }
        return s;
    }

    private TestDatabase target() throws Exception {
        return target(DbServer.shared(kind));
    }

    private TestDatabase target(DbServer server) throws Exception {
        TestDatabase t = server.create("ftgt");
        try (Connection c = t.connect()) {
            c.setAutoCommit(true);
            Fixture.createSchema(c, vendor());
        }
        return t;
    }

    /**
     * Adds {@code n} UP_SEQUENCE rows, so a copy takes long enough to be interrupted (plain rows:
     * HSQLDB keeps LOBs of an in-memory database on the heap).
     */
    private static void addCounters(TestDatabase db, int n) throws SQLException {
        try (Connection c = db.connect()) {
            addCounters(c, n);
        }
    }

    private static void addCounters(Connection c, int n) throws SQLException {
        c.setAutoCommit(false);
        try (PreparedStatement ps =
                c.prepareStatement(
                        "INSERT INTO UP_SEQUENCE (SEQUENCE_NAME, SEQUENCE_VALUE) VALUES (?, ?)")) {
            for (int i = 0; i < n; i++) {
                ps.setString(1, "COUNTER_" + i);
                ps.setInt(2, i);
                ps.addBatch();
                // HSQLDB refuses to execute an empty batch
                if (i % 1000 == 999 || i == n - 1) {
                    ps.executeBatch();
                }
            }
        }
        c.commit();
    }

    /** The fixture's two counters plus the added ones. */
    private static final int COUNTERS = 30_000;

    private static final long ALL_COUNTERS = COUNTERS + 2;

    private static long count(TestDatabase db, String table) throws SQLException {
        return db.scalar("SELECT COUNT(*) FROM " + table);
    }

    private static final String[] TABLES = {
        "UP_THING_TYPE", "UP_THING", "UP_RAW_EVENTS", "UP_SEQUENCE"
    };

    /** The next value of a generator, read without using it. */
    private long nextValue(TestDatabase db, String generator) throws SQLException {
        if (Fixture.usesSequenceTables(vendor())) {
            return db.scalar("SELECT next_val FROM " + generator);
        }
        switch (kind) {
            case POSTGRES:
                {
                    String seq = generator.toLowerCase(Locale.ROOT);
                    return db.scalar(
                            "SELECT CASE WHEN is_called THEN last_value + (SELECT increment_by"
                                    + " FROM pg_sequences WHERE sequencename = '"
                                    + seq
                                    + "') ELSE last_value END FROM "
                                    + seq);
                }
            case ORACLE:
                return db.scalar(
                        "SELECT LAST_NUMBER FROM USER_SEQUENCES WHERE SEQUENCE_NAME = '"
                                + generator
                                + "'");
            default:
                throw new IllegalStateException(kind.toString());
        }
    }

    private void setNext(TestDatabase db, String generator, long next) throws SQLException {
        if (Fixture.usesSequenceTables(vendor())) {
            db.execute("UPDATE " + generator + " SET next_val = " + next);
        } else if (kind == Kind.POSTGRES) {
            db.execute(
                    "SELECT setval('"
                            + generator.toLowerCase(Locale.ROOT)
                            + "', "
                            + next
                            + ", false)");
        } else {
            db.execute("ALTER SEQUENCE " + generator + " RESTART START WITH " + next);
        }
    }

    private static void assertFailedClosed(ToolRun run) {
        assertNotEquals(run.toString(), DbMigrate.OK, run.status);
        assertFalse(run.toString(), run.output.contains("SUCCESS"));
        if (run.report != null) {
            assertFalse(run.toString(), run.report.success());
            for (MigrationReport.TableEntry e : run.report.tables) {
                if (e.error != null) {
                    assertNotEquals(e.source, "OK", e.verify);
                }
            }
        }
    }

    // ------------------------------------------------------------------ corruption

    /** One way to corrupt a copied target, and the table it must be reported for. */
    private static final class Corruption {
        final String name;
        final String table;
        final ThrowingConsumer<TestDatabase> apply;

        Corruption(String name, String table, ThrowingConsumer<TestDatabase> apply) {
            this.name = name;
            this.table = table;
            this.apply = apply;
        }
    }

    interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }

    private List<Corruption> corruptions() {
        List<Corruption> c = new ArrayList<>();
        c.add(
                new Corruption(
                        "a changed value",
                        "UP_THING",
                        t ->
                                t.execute(
                                        "UPDATE UP_THING SET TITLE = 'changed' WHERE THING_ID = 9")));
        c.add(
                new Corruption(
                        "a deleted row",
                        "UP_RAW_EVENTS",
                        t -> t.execute("DELETE FROM UP_RAW_EVENTS WHERE EVENT_ID = 1015")));
        c.add(
                new Corruption(
                        "an added row",
                        "UP_THING_TYPE",
                        t -> {
                            try (Connection con = t.connect();
                                    PreparedStatement ps =
                                            con.prepareStatement(
                                                    "INSERT INTO UP_THING_TYPE (TYPE_ID, NAME,"
                                                            + " ENABLED) VALUES (99, 'extra', ?)")) {
                                ps.setBoolean(1, true);
                                ps.executeUpdate();
                            }
                        }));
        c.add(
                new Corruption(
                        "one BLOB byte",
                        "UP_THING",
                        t ->
                                flipByte(
                                        t,
                                        "SELECT PAYLOAD FROM UP_THING WHERE THING_ID = 6",
                                        "UPDATE UP_THING SET PAYLOAD = ? WHERE THING_ID = 6",
                                        100)));
        c.add(
                new Corruption(
                        "one CLOB character",
                        "UP_THING",
                        t -> {
                            String body;
                            try (Connection con = t.connect();
                                    Statement st = con.createStatement();
                                    ResultSet rs =
                                            st.executeQuery(
                                                    "SELECT BODY FROM UP_THING WHERE THING_ID = 3")) {
                                rs.next();
                                body = rs.getString(1);
                            }
                            StringBuilder sb = new StringBuilder(body);
                            int i = body.length() / 2;
                            sb.setCharAt(i, body.charAt(i) == 'x' ? 'y' : 'x');
                            try (Connection con = t.connect();
                                    PreparedStatement ps =
                                            con.prepareStatement(
                                                    "UPDATE UP_THING SET BODY = ? WHERE THING_ID = 3")) {
                                ps.setString(1, sb.toString());
                                ps.executeUpdate();
                            }
                        }));
        c.add(
                new Corruption(
                        "a timestamp one second off",
                        "UP_THING",
                        t -> {
                            LocalDateTime created;
                            try (Connection con = t.connect();
                                    Statement st = con.createStatement();
                                    ResultSet rs =
                                            st.executeQuery(
                                                    "SELECT CREATED FROM UP_THING WHERE THING_ID = 4")) {
                                rs.next();
                                created = rs.getObject(1, LocalDateTime.class);
                            }
                            try (Connection con = t.connect();
                                    PreparedStatement ps =
                                            con.prepareStatement(
                                                    "UPDATE UP_THING SET CREATED = ? WHERE THING_ID = 4")) {
                                ps.setObject(1, created.plusSeconds(1));
                                ps.executeUpdate();
                            }
                        }));
        c.add(
                new Corruption(
                        "values swapped between rows",
                        "UP_THING",
                        // Read, then written back: MySQL refuses an UPDATE whose subquery reads
                        // the table it updates
                        t -> {
                            BigDecimal ten = amount(t, 10);
                            BigDecimal eleven = amount(t, 11);
                            assertNotEquals(ten, eleven);
                            setAmount(t, 10, eleven);
                            setAmount(t, 11, ten);
                        }));
        c.add(
                new Corruption(
                        "a re-pointed self-reference",
                        "UP_THING",
                        t -> t.execute("UPDATE UP_THING SET PARENT_ID = 12 WHERE THING_ID = 1")));
        c.add(
                new Corruption(
                        "a flipped boolean",
                        "UP_RAW_EVENTS",
                        t -> {
                            try (Connection con = t.connect();
                                    PreparedStatement ps =
                                            con.prepareStatement(
                                                    "UPDATE UP_RAW_EVENTS SET AGGREGATED = ?"
                                                            + " WHERE EVENT_ID = 1002")) {
                                ps.setBoolean(1, false); // event 2 is aggregated
                                ps.executeUpdate();
                            }
                        }));
        if (kind != Kind.ORACLE) {
            // Oracle stores '' as NULL; elsewhere the two must not verify as equal
            c.add(
                    new Corruption(
                            "an empty string made NULL",
                            "UP_THING",
                            t -> t.execute("UPDATE UP_THING SET TITLE = NULL WHERE THING_ID = 7")));
        }
        if (kind == Kind.POSTGRES) {
            // The large object behind an OID-in-text column changes, the OID does not (MOD-I24)
            c.add(
                    new Corruption(
                            "one byte of a large object",
                            "UP_RAW_EVENTS",
                            t ->
                                    t.execute(
                                            "SELECT lo_put(CAST(event_data AS oid), 3,"
                                                    + " CAST('Z' AS bytea)) FROM up_raw_events"
                                                    + " WHERE event_id = 1020")));
        } else {
            c.add(
                    new Corruption(
                            "one character of an event",
                            "UP_RAW_EVENTS",
                            t ->
                                    t.execute(
                                            "UPDATE UP_RAW_EVENTS SET EVENT_DATA ="
                                                    + " '{\"@c\":\".LoginEvent\",\"id\":20,\"user\":\"u20\"}'"
                                                    + " WHERE EVENT_ID = 1020")));
        }
        return c;
    }

    private static BigDecimal amount(TestDatabase t, long thingId) throws SQLException {
        try (Connection con = t.connect();
                Statement st = con.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT AMOUNT FROM UP_THING WHERE THING_ID = " + thingId)) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private static void setAmount(TestDatabase t, long thingId, BigDecimal amount)
            throws SQLException {
        try (Connection con = t.connect();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE UP_THING SET AMOUNT = ? WHERE THING_ID = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, thingId);
            ps.executeUpdate();
        }
    }

    private static void flipByte(TestDatabase t, String select, String update, int index)
            throws SQLException {
        byte[] b;
        try (Connection con = t.connect();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(select)) {
            rs.next();
            b = rs.getBytes(1);
        }
        b[index] ^= 0x01;
        try (Connection con = t.connect();
                PreparedStatement ps = con.prepareStatement(update)) {
            ps.setBytes(1, b);
            ps.executeUpdate();
        }
    }

    /** The fixture's rules plus, on PostgreSQL, the OID-in-text event column. */
    private String rules() {
        return RULES + (kind == Kind.POSTGRES ? "lob-oid UP_RAW_EVENTS.EVENT_DATA\n" : "");
    }

    @Test
    public void everyCorruptionFailsVerificationAndNamesItsTable() throws Exception {
        List<String> missed = new ArrayList<>();
        for (Corruption c : corruptions()) {
            try (TestDatabase s = source();
                    TestDatabase t = target()) {
                ToolRun copy = ToolRun.run(s, t, rules());
                assertEquals(copy.toString(), DbMigrate.OK, copy.status);
                c.apply.accept(t);
                ToolRun verify = ToolRun.run(s, t, rules(), "--verify-only");
                List<String> mismatched = new ArrayList<>();
                for (MigrationReport.TableEntry e : verify.report.tables) {
                    if (!"OK".equals(e.verify)) {
                        mismatched.add(e.source.toUpperCase(Locale.ROOT));
                    }
                }
                boolean named =
                        verify.output.toUpperCase(Locale.ROOT).contains(c.table)
                                && verify.output.contains("MISMATCH");
                if (verify.status != DbMigrate.FAILED
                        || !mismatched.equals(Arrays.asList(c.table))
                        || !named) {
                    missed.add(
                            c.name
                                    + ": exit "
                                    + verify.status
                                    + ", mismatched "
                                    + mismatched
                                    + " (expected ["
                                    + c.table
                                    + "])\n"
                                    + verify.output);
                }
            }
        }
        assertTrue(String.join("\n", missed), missed.isEmpty());
    }

    @Test
    public void aGeneratorBelowTheCopiedIdsFailsVerification() throws Exception {
        try (TestDatabase s = source();
                TestDatabase t = target()) {
            assertEquals(DbMigrate.OK, ToolRun.run(s, t, rules()).status);
            // max(THING_ID) = 20, allocationSize 5: 24 would hand out 20 again under POOLED
            setNext(t, "UP_THING_SEQ", 24);
            ToolRun verify = ToolRun.run(s, t, rules(), "--verify-only");
            assertEquals(verify.toString(), DbMigrate.FAILED, verify.status);
            assertTrue(
                    verify.output, verify.output.toUpperCase(Locale.ROOT).contains("UP_THING_SEQ"));
            assertTrue(verify.output, verify.output.contains("FAILED: next value"));
        }
    }

    // ------------------------------------------------------------------ lost target

    /** Runs a copy in another thread and calls {@code fault} once the target has rows. */
    private ToolRun copyWithFault(
            TestDatabase s, TestDatabase t, Consumer<Thread> fault, String... options)
            throws Exception {
        AtomicReference<ToolRun> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        List<String> args = new ArrayList<>(Arrays.asList("--batch-size", "20", "--threads", "1"));
        args.addAll(Arrays.asList(options));
        Thread copy =
                new Thread(
                        () -> {
                            try {
                                result.set(ToolRun.run(s, t, rules(), args.toArray(new String[0])));
                            } catch (Throwable e) {
                                error.set(e);
                            }
                        },
                        "copy-under-fault");
        copy.start();
        waitForRows(t, copy);
        fault.accept(copy);
        copy.join(TimeUnit.MINUTES.toMillis(5));
        assertFalse("the copy hung after the fault", copy.isAlive());
        if (error.get() != null) {
            throw new AssertionError("the tool threw instead of reporting", error.get());
        }
        return result.get();
    }

    private static void waitForRows(TestDatabase t, Thread copy) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
        while (System.nanoTime() < deadline && copy.isAlive()) {
            try {
                if (count(t, "UP_SEQUENCE") >= 200) {
                    return;
                }
            } catch (SQLException e) {
                // Not created yet or busy; try again
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the copy ended before the fault could be injected");
    }

    @Test
    public void killedTargetSessionsFailTheCopyAndARerunRecovers() throws Exception {
        try (TestDatabase s = source();
                TestDatabase t = target()) {
            addCounters(s, COUNTERS);
            ToolRun run =
                    copyWithFault(
                            s,
                            t,
                            thread -> {
                                try {
                                    assertTrue(t.killSessions() > 0);
                                } catch (SQLException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
            assertFailedClosed(run);
            assertEquals(run.toString(), DbMigrate.FAILED, run.status);
            MigrationReport.TableEntry counters = run.table("UP_SEQUENCE");
            assertNotNull(counters.error);
            // What is left: the committed chunks; generators not positioned
            long partial = count(t, "UP_SEQUENCE");
            assertTrue(partial + " rows", partial > 0 && partial < ALL_COUNTERS);
            assertEquals(1, nextValue(t, "UP_THING_SEQ"));
            // The tool is not restartable: a rerun refuses the partly filled target ...
            ToolRun again = ToolRun.run(s, t, rules());
            assertEquals(again.toString(), DbMigrate.PLAN_ERROR, again.status);
            assertTrue(again.output, again.output.contains("--clear-target"));
            assertEquals(partial, count(t, "UP_SEQUENCE"));
            // ... and with --clear-target starts over and verifies
            ToolRun clean = ToolRun.run(s, t, rules(), "--clear-target");
            assertEquals(clean.toString(), DbMigrate.OK, clean.status);
            assertEquals(ALL_COUNTERS, count(t, "UP_SEQUENCE"));
        }
    }

    @Test
    public void aStoppedTargetServerFailsTheCopy() throws Exception {
        DbServer server = DbServer.dedicated(kind);
        try (TestDatabase s = source()) {
            TestDatabase t = target(server);
            addCounters(s, COUNTERS);
            ToolRun run = copyWithFault(s, t, thread -> server.stop());
            assertFailedClosed(run);
            assertEquals(run.toString(), DbMigrate.FAILED, run.status);
        } finally {
            server.stop();
        }
    }

    @Test
    public void anInterruptedCopyNeverReportsAnUnverifiedSuccess() throws Exception {
        try (TestDatabase s = source();
                TestDatabase t = target()) {
            addCounters(s, COUNTERS);
            ToolRun run = copyWithFault(s, t, Thread::interrupt);
            if (run.status == DbMigrate.OK) {
                // The interrupt was absorbed: then everything must be there and verified
                assertEquals(ALL_COUNTERS, count(t, "UP_SEQUENCE"));
                for (MigrationReport.TableEntry e : run.report.tables) {
                    assertEquals(e.source, "OK", e.verify);
                }
            } else {
                assertFailedClosed(run);
            }
            ToolRun clean = ToolRun.run(s, t, rules(), "--clear-target");
            assertEquals(clean.toString(), DbMigrate.OK, clean.status);
        }
    }

    @Test
    public void aKilledJvmLeavesNoSuccessAndARerunRecovers() throws Exception {
        Path dir = Files.createTempDirectory("dbmig-kill");
        String url = "jdbc:hsqldb:file:" + dir.resolve("src") + ";hsqldb.lock_file=false";
        try (TestDatabase t = target()) {
            try (Connection c = java.sql.DriverManager.getConnection(url, "SA", "")) {
                Fixture.createSchema(c, Vendor.HSQLDB);
                Fixture.insertRows(c, Vendor.HSQLDB);
            }
            // Fill through a JDBC URL the child JVM can open too, then close it
            try (Connection c = java.sql.DriverManager.getConnection(url, "SA", "")) {
                addCounters(c, COUNTERS);
                try (Statement st = c.createStatement()) {
                    st.execute("SHUTDOWN");
                }
            }
            List<String> cmd =
                    new ArrayList<>(
                            Arrays.asList(
                                    new File(System.getProperty("java.home"), "bin/java").getPath(),
                                    "-cp",
                                    System.getProperty("java.class.path"),
                                    "org.apereo.portal.dbmigrate.DbMigrateCli",
                                    "--source-url",
                                    url,
                                    "--source-user",
                                    "SA",
                                    "--source-password",
                                    "",
                                    "--batch-size",
                                    "20",
                                    "--threads",
                                    "1"));
            cmd.addAll(t.args("target"));
            Path rulesFile = Files.createTempFile(dir, "rules", ".rules");
            Files.write(rulesFile, rules().getBytes(StandardCharsets.UTF_8));
            cmd.add("--rules");
            cmd.add(rulesFile.toString());
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thread pump = pump(p.getInputStream(), out);
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
            while (count(t, "UP_SEQUENCE") < 200 && p.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue("the copy ended before it could be killed:\n" + out, p.isAlive());
            p.destroyForcibly(); // SIGKILL: no shutdown hook, no last words
            assertTrue(p.waitFor(1, TimeUnit.MINUTES));
            pump.join(10_000);
            String output = out.toString(StandardCharsets.UTF_8);
            assertNotEquals(output, 0, p.exitValue());
            assertFalse(output, output.contains("SUCCESS"));
            assertFalse(output, output.contains("Verified"));
            long partial = count(t, "UP_SEQUENCE");
            assertTrue(partial + " rows", partial < ALL_COUNTERS);
            assertEquals(1, nextValue(t, "UP_THING_SEQ"));
            ToolRun clean =
                    ToolRun.run(
                            concat(
                                    Arrays.asList(
                                            "--source-url",
                                            url,
                                            "--source-user",
                                            "SA",
                                            "--source-password",
                                            "",
                                            "--rules",
                                            rulesFile.toString(),
                                            "--clear-target"),
                                    t.args("target")));
            assertEquals(clean.toString(), DbMigrate.OK, clean.status);
        } finally {
            try (Connection c = java.sql.DriverManager.getConnection(url, "SA", "");
                    Statement st = c.createStatement()) {
                st.execute("SHUTDOWN");
            } catch (SQLException e) {
                // already closed
            }
            deleteRecursively(dir.toFile());
        }
    }

    private static String[] concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all.toArray(new String[0]);
    }

    private static Thread pump(InputStream in, ByteArrayOutputStream out) {
        Thread t =
                new Thread(
                        () -> {
                            try {
                                in.transferTo(out);
                            } catch (IOException e) {
                                // the process is gone
                            }
                        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }

    // ------------------------------------------------------------------ plan errors

    /** A change to one side that the plan must refuse, before anything is written. */
    private void assertPlanRefused(
            ThrowingConsumer<TestDatabase> onSource,
            ThrowingConsumer<TestDatabase> onTarget,
            String expectedInMessage)
            throws Exception {
        try (TestDatabase s = source();
                TestDatabase t = target()) {
            onSource.accept(s);
            onTarget.accept(t);
            ToolRun run = ToolRun.run(s, t, rules());
            assertEquals(run.toString(), DbMigrate.PLAN_ERROR, run.status);
            assertTrue(
                    run.toString(),
                    run.output
                            .toUpperCase(Locale.ROOT)
                            .contains(expectedInMessage.toUpperCase(Locale.ROOT)));
            assertFailedClosed(run);
            assertNothingWritten(t);
        }
    }

    private void assertNothingWritten(TestDatabase t) throws SQLException {
        for (String table : TABLES) {
            try {
                assertEquals(table + " must stay empty", 0, count(t, table));
            } catch (SQLException e) {
                // the table this case dropped
            }
        }
        assertEquals("generators must not move", 1, nextValue(t, "UP_THING_SEQ"));
    }

    private static void noChange(TestDatabase db) {}

    @Test
    public void aMissingTargetTableFailsThePlan() throws Exception {
        // MOD-I92: e.g. a portlet table that only appears after the first portal start
        assertPlanRefused(
                CopyFaultIT::noChange,
                t -> t.execute("DROP TABLE UP_RAW_EVENTS"),
                "has no target table");
    }

    @Test
    public void anUnexpectedSourceColumnFailsThePlan() throws Exception {
        assertPlanRefused(
                s -> s.execute("ALTER TABLE UP_THING ADD COLUMN EXTRA VARCHAR(10)"),
                CopyFaultIT::noChange,
                "has no target column");
    }

    @Test
    public void aTargetColumnNothingFillsFailsThePlan() throws Exception {
        assertPlanRefused(
                CopyFaultIT::noChange,
                t -> t.execute(addColumn("UP_THING", "NEWCOL", "VARCHAR(10)", true)),
                "NOT NULL without a default");
    }

    @Test
    public void aTargetNotNullTheSourceDataViolatesFailsThePlan() throws Exception {
        // CODE is NULL in every third source row
        assertPlanRefused(
                CopyFaultIT::noChange,
                t -> t.execute(notNull("UP_THING", "CODE", "CHAR(8)")),
                "CODE");
    }

    @Test
    public void aTargetColumnTooShortForTheDataFailsThePlan() throws Exception {
        // TITLE values are up to 10 characters long
        assertPlanRefused(
                CopyFaultIT::noChange, t -> t.execute(resize("UP_THING", "TITLE", 5)), "TITLE");
    }

    @Test
    public void aTargetUniqueKeyTheSourceDataViolatesFailsThePlan() throws Exception {
        // Four things per type
        assertPlanRefused(
                CopyFaultIT::noChange,
                t -> t.execute("CREATE UNIQUE INDEX UQ_THING_TYPE ON UP_THING (TYPE_ID)"),
                "UP_THING");
    }

    @Test
    public void aTargetForeignKeyTheSourceDataViolatesFailsThePlan() throws Exception {
        assertPlanRefused(
                s -> {
                    s.execute("ALTER TABLE UP_THING DROP CONSTRAINT FK_THING_TYPE");
                    s.execute("UPDATE UP_THING SET TYPE_ID = 77 WHERE THING_ID = 15");
                },
                CopyFaultIT::noChange,
                "UP_THING_TYPE");
    }

    private String addColumn(String table, String column, String type, boolean notNull) {
        String t = kind == Kind.SQLSERVER ? "N" + type : type;
        return "ALTER TABLE "
                + table
                + (kind.isMySqlFamily() || kind == Kind.SQLSERVER || kind == Kind.ORACLE
                        ? " ADD "
                        : " ADD COLUMN ")
                + column
                + " "
                + t
                + (notNull ? " NOT NULL" : "");
    }

    private String notNull(String table, String column, String type) {
        switch (kind) {
            case POSTGRES:
                return "ALTER TABLE " + table + " ALTER COLUMN " + column + " SET NOT NULL";
            case MARIADB:
            case MYSQL:
                return "ALTER TABLE " + table + " MODIFY " + column + " " + type + " NOT NULL";
            case SQLSERVER:
                return "ALTER TABLE "
                        + table
                        + " ALTER COLUMN "
                        + column
                        + " "
                        + type
                        + " NOT NULL";
            case ORACLE:
                return "ALTER TABLE " + table + " MODIFY " + column + " NOT NULL";
            default:
                throw new IllegalStateException(kind.toString());
        }
    }

    private String resize(String table, String column, int length) {
        switch (kind) {
            case POSTGRES:
                return "ALTER TABLE "
                        + table
                        + " ALTER COLUMN "
                        + column
                        + " TYPE VARCHAR("
                        + length
                        + ")";
            case MARIADB:
            case MYSQL:
                return "ALTER TABLE " + table + " MODIFY " + column + " VARCHAR(" + length + ")";
            case SQLSERVER:
                return "ALTER TABLE "
                        + table
                        + " ALTER COLUMN "
                        + column
                        + " VARCHAR("
                        + length
                        + ")";
            case ORACLE:
                return "ALTER TABLE " + table + " MODIFY " + column + " VARCHAR2(" + length + ")";
            default:
                throw new IllegalStateException(kind.toString());
        }
    }
}
