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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import org.apereo.portal.dbmigrate.copy.GeneratorPositioner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Copies the {@link Fixture} between two in-memory HSQLDB databases through the command line and
 * checks the copy, the verification, the generator positions and the failure modes. Runs in a JVM
 * whose zone has a DST gap (America/New_York, set by the build).
 */
public class HsqldbCopyTest {

    private static final AtomicInteger RUN = new AtomicInteger();

    private String sourceUrl;
    private String targetUrl;
    private Connection source;
    private Connection target;
    private Path rules;
    private Path report;

    @Before
    public void setUp() throws Exception {
        int n = RUN.incrementAndGet();
        sourceUrl = "jdbc:hsqldb:mem:dbmig_src_" + n;
        targetUrl = "jdbc:hsqldb:mem:dbmig_tgt_" + n;
        source = DriverManager.getConnection(sourceUrl, "SA", "");
        target = DriverManager.getConnection(targetUrl, "SA", "");
        Fixture.createSchema(source, Vendor.HSQLDB);
        Fixture.insertRows(source, Vendor.HSQLDB);
        Fixture.createSchema(target, Vendor.HSQLDB);
        rules = Files.createTempFile("dbmig", ".rules");
        Files.write(rules, Fixture.RULES.getBytes(StandardCharsets.UTF_8));
        report = Files.createTempFile("dbmig", ".json");
    }

    @After
    public void tearDown() throws Exception {
        for (Connection c : Arrays.asList(source, target)) {
            try (Statement st = c.createStatement()) {
                st.execute("SHUTDOWN");
            }
            c.close();
        }
        Files.deleteIfExists(rules);
        Files.deleteIfExists(report);
        Files.deleteIfExists(
                report.resolveSibling(report.getFileName().toString().replace(".json", ".txt")));
    }

    private int run(String... extra) {
        List<String> args =
                new ArrayList<>(
                        Arrays.asList(
                                "--source-url",
                                sourceUrl,
                                "--source-user",
                                "SA",
                                "--source-password",
                                "",
                                "--target-url",
                                targetUrl,
                                "--target-user",
                                "SA",
                                "--target-password",
                                "",
                                "--rules",
                                rules.toString(),
                                "--report",
                                report.toString(),
                                "--batch-size",
                                "7",
                                "--threads",
                                "3"));
        args.addAll(Arrays.asList(extra));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int status =
                DbMigrateCli.run(
                        args.toArray(new String[0]),
                        new PrintStream(out, true),
                        new PrintStream(out, true));
        System.out.println(out);
        return status;
    }

    @Test
    public void runsInAZoneWithDaylightSavingGaps() {
        assertEquals("America/New_York", TimeZone.getDefault().getID());
    }

    @Test
    public void copiesVerifiesAndPositionsGenerators() throws Exception {
        assertEquals(0, run());

        assertEquals(20, count(target, "UP_THING"));
        assertEquals(30, count(target, "UP_RAW_EVENTS"));
        assertEquals(2, count(target, "UP_SEQUENCE"));
        // The DST-gap wall-clock time is copied unchanged (not shifted to 03:30)
        try (Statement st = target.createStatement();
                ResultSet rs = st.executeQuery("SELECT CREATED FROM UP_THING WHERE THING_ID = 1")) {
            rs.next();
            assertEquals(Fixture.DST_GAP, rs.getObject(1, LocalDateTime.class));
        }
        // The self-reference was loaded in the second pass
        try (Statement st = target.createStatement();
                ResultSet rs =
                        st.executeQuery("SELECT PARENT_ID FROM UP_THING WHERE THING_ID = 3")) {
            rs.next();
            assertEquals(13, rs.getLong(1));
        }
        try (Statement st = target.createStatement();
                ResultSet rs = st.executeQuery("SELECT BODY FROM UP_THING WHERE THING_ID = 3")) {
            rs.next();
            assertEquals(Fixture.longText(), rs.getString(1));
        }
        // Generators: max(id) + allocationSize, never below the source's own position
        assertEquals(26, next(target, "UP_THING_SEQ")); // source next 26 >= 20 + 5
        assertEquals(6, next(target, "UP_THING_TYPE_SEQ")); // source next 3 < 5 + 1
        assertEquals(2030, next(target, "UP_RAW_EVENTS_SEQ")); // 1030 + 1000 > source 2001

        String json = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
        assertTrue(json, json.contains("\"result\": \"SUCCESS\""));
        assertTrue(json, json.contains("\"verify\": \"OK\""));
    }

    @Test
    public void verifyOnlyDetectsAChangedValue() throws Exception {
        assertEquals(0, run());
        try (Statement st = target.createStatement()) {
            st.execute("UPDATE UP_THING SET TITLE = 'changed' WHERE THING_ID = 9");
        }
        assertEquals(1, run("--verify-only"));
        String json = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
        assertTrue(json, json.contains("\"verify\": \"MISMATCH\""));
    }

    @Test
    public void verifyOnlyDetectsAGeneratorBehindTheData() throws Exception {
        assertEquals(0, run());
        try (Statement st = target.createStatement()) {
            st.execute("ALTER SEQUENCE UP_THING_SEQ RESTART WITH 21");
        }
        assertEquals(1, run("--verify-only"));
    }

    @Test
    public void dryRunWritesNothing() throws Exception {
        assertEquals(0, run("--dry-run"));
        assertEquals(0, count(target, "UP_THING"));
        assertEquals(1, next(target, "UP_THING_SEQ"));
    }

    @Test
    public void refusesANonEmptyTargetUnlessCleared() throws Exception {
        assertEquals(0, run());
        assertEquals(2, run());
        assertEquals(0, run("--clear-target"));
        assertEquals(20, count(target, "UP_THING"));
    }

    @Test
    public void failsOnAnUnmappedSourceTableUnlessExcluded() throws Exception {
        try (Statement st = source.createStatement()) {
            st.execute("CREATE TABLE UP_EXTRA (ID INTEGER PRIMARY KEY)");
        }
        assertEquals(2, run());
        assertEquals(0, count(target, "UP_THING"));
        assertEquals(0, run("--exclude", "UP_EXTRA"));
    }

    @Test
    public void failsOnAnUnmappedSourceColumn() throws Exception {
        try (Statement st = source.createStatement()) {
            st.execute("ALTER TABLE UP_THING ADD COLUMN EXTRA VARCHAR(10)");
        }
        assertEquals(2, run());
    }

    @Test
    public void skipRawEventsLeavesTheTableOut() throws Exception {
        assertEquals(0, run("--skip-raw-events"));
        assertEquals(0, count(target, "UP_RAW_EVENTS"));
        assertEquals(20, count(target, "UP_THING"));
    }

    @Test
    public void positionFormulaMatchesTheCopy() {
        assertEquals(26, GeneratorPositioner.position(20L, 5, 26L, 1L));
        assertEquals(6, GeneratorPositioner.position(5L, 1, 3L, 1L));
    }

    private static long count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long next(Connection c, String sequence) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT NEXT_VALUE FROM INFORMATION_SCHEMA.SEQUENCES"
                                        + " WHERE SEQUENCE_NAME = '"
                                        + sequence
                                        + "'")) {
            rs.next();
            return Long.parseLong(rs.getString(1));
        }
    }
}
