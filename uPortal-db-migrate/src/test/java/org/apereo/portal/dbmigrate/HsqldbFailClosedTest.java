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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A target the source data does not fit must fail the plan (exit status 2) before anything is
 * written, not fail half-way through the copy: a NOT NULL column the source has NULLs for, a column
 * shorter than the source values, a unique key or a foreign key the source data violates. And a
 * documented conversion that loses data is reported on the console too. The same checks run on the
 * real servers in CopyFaultIT.
 */
public class HsqldbFailClosedTest {

    private static final AtomicInteger RUN = new AtomicInteger();
    private static final String[] TABLES = {
        "UP_THING_TYPE", "UP_THING", "UP_RAW_EVENTS", "UP_SEQUENCE"
    };

    private String sourceUrl;
    private String targetUrl;
    private Connection source;
    private Connection target;
    private Path rules;

    @Before
    public void setUp() throws Exception {
        int n = RUN.incrementAndGet();
        sourceUrl = "jdbc:hsqldb:mem:dbmig_fc_src_" + n;
        targetUrl = "jdbc:hsqldb:mem:dbmig_fc_tgt_" + n;
        source = DriverManager.getConnection(sourceUrl, "SA", "");
        target = DriverManager.getConnection(targetUrl, "SA", "");
        Fixture.createSchema(source, Vendor.HSQLDB);
        Fixture.insertRows(source, Vendor.HSQLDB);
        Fixture.createSchema(target, Vendor.HSQLDB);
        rules = Files.createTempFile("dbmig", ".rules");
        Files.write(rules, Fixture.RULES.getBytes(StandardCharsets.UTF_8));
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
    }

    private String output;

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
                                "--batch-size",
                                "3"));
        args.addAll(Arrays.asList(extra));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8);
        int status = DbMigrateCli.run(args.toArray(new String[0]), ps, ps);
        output = out.toString(StandardCharsets.UTF_8);
        System.out.println(output);
        return status;
    }

    private void execute(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private void assertRefusedBeforeWriting(String expected) throws SQLException {
        assertEquals(output, DbMigrate.PLAN_ERROR, run());
        assertTrue(output, output.contains(expected));
        assertFalse(output, output.contains("SUCCESS"));
        for (String table : TABLES) {
            assertEquals(table + " must stay empty", 0, count(target, table));
        }
    }

    @Test
    public void aNotNullTargetColumnTheSourceLeavesEmptyFailsThePlan() throws Exception {
        // CODE is NULL in every third source row
        execute(target, "ALTER TABLE UP_THING ALTER COLUMN CODE SET NOT NULL");
        assertRefusedBeforeWriting("UP_THING.CODE");
    }

    @Test
    public void aTargetColumnShorterThanTheSourceValuesFailsThePlan() throws Exception {
        // TITLE values are up to 10 characters long
        execute(target, "ALTER TABLE UP_THING ALTER COLUMN TITLE SET DATA TYPE VARCHAR(5)");
        assertRefusedBeforeWriting("UP_THING.TITLE");
    }

    @Test
    public void aTargetUniqueKeyTheSourceRowsShareFailsThePlan() throws Exception {
        // Four things per type
        execute(target, "CREATE UNIQUE INDEX UQ_THING_TYPE ON UP_THING (TYPE_ID)");
        assertRefusedBeforeWriting("UP_THING");
    }

    @Test
    public void aTargetForeignKeyTheSourceRowsBreakFailsThePlan() throws Exception {
        execute(source, "ALTER TABLE UP_THING DROP CONSTRAINT FK_THING_TYPE");
        execute(source, "UPDATE UP_THING SET TYPE_ID = 77 WHERE THING_ID = 15");
        assertRefusedBeforeWriting("UP_THING_TYPE");
    }

    @Test
    public void constraintsTheSourceDataMeetsPass() throws Exception {
        execute(target, "ALTER TABLE UP_THING ALTER COLUMN TITLE SET NOT NULL");
        execute(target, "ALTER TABLE UP_THING ALTER COLUMN TITLE SET DATA TYPE VARCHAR(10)");
        execute(target, "CREATE UNIQUE INDEX UQ_THING_TITLE ON UP_THING (TITLE)");
        assertEquals(output, DbMigrate.OK, run());
    }

    @Test
    public void lostFractionalSecondsAreReportedOnTheConsole() throws Exception {
        execute(target, "ALTER TABLE UP_THING ALTER COLUMN CREATED SET DATA TYPE TIMESTAMP(0)");
        assertEquals(output, DbMigrate.OK, run());
        assertTrue(
                output, output.contains("CREATED: fractional seconds reduced from 6 to 0 digits"));
    }

    private static long count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
