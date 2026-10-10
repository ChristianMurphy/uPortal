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
 * Copies on HSQLDB whose plan notes a documented conversion that loses data: the note must
 * reach the console, not only the JSON report.
 */
public class HsqldbFailClosedTest {

    private static final AtomicInteger RUN = new AtomicInteger();

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

    @Test
    public void lostFractionalSecondsAreReportedOnTheConsole() throws Exception {
        execute(target, "ALTER TABLE UP_THING ALTER COLUMN CREATED SET DATA TYPE TIMESTAMP(0)");
        assertEquals(output, DbMigrate.OK, run());
        assertTrue(
                output, output.contains("CREATED: fractional seconds reduced from 6 to 0 digits"));
    }
}
