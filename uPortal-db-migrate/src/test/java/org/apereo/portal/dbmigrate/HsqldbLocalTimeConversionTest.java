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
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A copy from 5.17 into the 6.0 profile converts the columns 5.17 stored as the JVM's local clock
 * time to UTC with {@code --source-zone}, and refuses to copy them without it (MOD-I141).
 */
public class HsqldbLocalTimeConversionTest {

    private static final AtomicInteger RUN = new AtomicInteger();

    private static final String[] SCHEMA = {
        "CREATE TABLE UP_PERSON_DIR (USER_DIR_ID INTEGER PRIMARY KEY, USER_NAME VARCHAR(100),"
                + " LST_PSWD_CGH_DT TIMESTAMP)",
        "CREATE TABLE UP_MUTEX (MUTEX_NAME VARCHAR(200) PRIMARY KEY, LOCK_START TIMESTAMP,"
                + " LOCK_UPDATE TIMESTAMP, LOCK_END TIMESTAMP)",
        // The generator of UP_PERSON_DIR, as 5.17 creates it on HSQLDB
        "CREATE SEQUENCE UP_PERSON_DIR_SEQ START WITH 1 INCREMENT BY 5",
    };

    private String sourceUrl;
    private String targetUrl;
    private Connection source;
    private Connection target;

    @Before
    public void setUp() throws Exception {
        int n = RUN.incrementAndGet();
        sourceUrl = "jdbc:hsqldb:mem:dbmig_tz_src_" + n;
        targetUrl = "jdbc:hsqldb:mem:dbmig_tz_tgt_" + n;
        source = DriverManager.getConnection(sourceUrl, "SA", "");
        target = DriverManager.getConnection(targetUrl, "SA", "");
        for (Connection c : Arrays.asList(source, target)) {
            try (Statement st = c.createStatement()) {
                for (String sql : SCHEMA) {
                    st.execute(sql);
                }
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        for (Connection c : Arrays.asList(source, target)) {
            try (Statement st = c.createStatement()) {
                st.execute("SHUTDOWN");
            }
            c.close();
        }
    }

    private void insertLocalTimes() throws Exception {
        try (Statement st = source.createStatement()) {
            // Written by a 5.17 portal in New York: local clock times
            st.execute(
                    "INSERT INTO UP_PERSON_DIR VALUES (1, 'summer', TIMESTAMP '2026-07-01 12:00:00')");
            st.execute(
                    "INSERT INTO UP_PERSON_DIR VALUES (2, 'winter', TIMESTAMP '2026-01-15 12:00:00')");
            // 01:30 happened twice on 2026-11-01; the earlier offset (EDT, -04:00) is taken
            st.execute(
                    "INSERT INTO UP_PERSON_DIR VALUES (3, 'repeated', TIMESTAMP '2026-11-01"
                            + " 01:30:00.250')");
            st.execute("INSERT INTO UP_PERSON_DIR VALUES (4, 'never', NULL)");
            st.execute(
                    "INSERT INTO UP_MUTEX VALUES ('lock', TIMESTAMP '2026-11-01 00:59:59',"
                            + " TIMESTAMP '2026-11-01 01:00:00', TIMESTAMP '2026-11-01 02:00:00')");
        }
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
                                ""));
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

    private LocalDateTime targetValue(String sql) throws Exception {
        try (Statement st = target.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, LocalDateTime.class);
        }
    }

    private long targetRows() throws Exception {
        try (Statement st = target.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM UP_PERSON_DIR")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private LocalDateTime password(int id) throws Exception {
        return targetValue("SELECT LST_PSWD_CGH_DT FROM UP_PERSON_DIR WHERE USER_DIR_ID = " + id);
    }

    @Test
    public void convertsTheLocalTimesToUtc() throws Exception {
        insertLocalTimes();
        assertEquals(0, run("--target-profile", "6.0", "--source-zone", "America/New_York"));
        assertEquals(LocalDateTime.parse("2026-07-01T16:00:00"), password(1));
        assertEquals(LocalDateTime.parse("2026-01-15T17:00:00"), password(2));
        assertEquals(LocalDateTime.parse("2026-11-01T05:30:00.250"), password(3));
        assertNull(password(4));
        assertEquals(
                LocalDateTime.parse("2026-11-01T04:59:59"),
                targetValue("SELECT LOCK_START FROM UP_MUTEX"));
        assertEquals(
                LocalDateTime.parse("2026-11-01T05:00:00"),
                targetValue("SELECT LOCK_UPDATE FROM UP_MUTEX"));
        // 02:00 comes after the repeated hour, in EST (-05:00)
        assertEquals(
                LocalDateTime.parse("2026-11-01T07:00:00"),
                targetValue("SELECT LOCK_END FROM UP_MUTEX"));
    }

    @Test
    public void refusesToCopyLocalTimesWithoutTheSourceZone() throws Exception {
        insertLocalTimes();
        assertEquals(2, run("--target-profile", "6.0"));
        assertEquals(0, targetRows());
    }

    @Test
    public void needsNoZoneWhenTheColumnsAreEmpty() throws Exception {
        try (Statement st = source.createStatement()) {
            st.execute("INSERT INTO UP_PERSON_DIR VALUES (4, 'never', NULL)");
        }
        assertEquals(0, run("--target-profile", "6.0"));
        assertEquals(1, targetRows());
    }

    @Test
    public void convertsByDefault() throws Exception {
        // 6.0 is the default target profile: a copy without --target-profile needs the zone
        insertLocalTimes();
        assertEquals(2, run());
        assertEquals(0, targetRows());
        assertEquals(0, run("--source-zone", "America/New_York"));
        assertEquals(LocalDateTime.parse("2026-11-01T05:30:00.250"), password(3));
    }

    @Test
    public void keepsLocalTimesInACopyBetween517Databases() throws Exception {
        insertLocalTimes();
        assertEquals(0, run("--target-profile", "5.17"));
        assertEquals(LocalDateTime.parse("2026-11-01T01:30:00.250"), password(3));
    }

    @Test
    public void rejectsASourceZoneWithoutAProfileThatConverts() throws Exception {
        insertLocalTimes();
        assertEquals(2, run("--target-profile", "5.17", "--source-zone", "America/New_York"));
        assertEquals(0, targetRows());
    }
}
