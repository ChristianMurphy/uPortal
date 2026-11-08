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
package org.apereo.portal.io.xml.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.apereo.portal.ICounterStore;
import org.apereo.portal.io.xml.IDataImporter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Two imports of new users at once, such as two tests that each import an account of their own. The
 * import updates the user's UP_USER row and inserts it when the update matched none. Under
 * REPEATABLE READ, InnoDB (MariaDB, MySQL) locks the gap of the unique index UPU_USER_NAME where
 * the missing name would go. When both names fall in the same gap, each insert waits for the other
 * import's gap lock, and one import fails with deadlock error 1213. The other databases insert both
 * users either way.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:userImporterExporterTestContext.xml")
public class UserImporterExporterConcurrencyTest {
    private static final int ID_OF_A = 101;
    private static final int ID_OF_B = 102;

    @Autowired private DataSource dataSource;
    @Autowired private IDataImporter<UserType> userImporter;
    @Autowired private ICounterStore counterStore;

    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        runSql(
                "CREATE TABLE UP_USER (USER_ID integer NOT NULL, USER_NAME varchar(100),"
                        + " USER_DFLT_LAY_ID integer, NEXT_STRUCT_ID integer,"
                        + " LST_CHAN_UPDT_DT timestamp, PRIMARY KEY (USER_ID))");
        runSql("CREATE UNIQUE INDEX UPU_USER_NAME ON UP_USER (USER_NAME)");
        runSql(
                "CREATE TABLE UP_LAYOUT_STRUCT (USER_ID integer NOT NULL,"
                        + " LAYOUT_ID integer NOT NULL, STRUCT_ID integer NOT NULL,"
                        + " PRIMARY KEY (LAYOUT_ID, USER_ID, STRUCT_ID))");
        // The two new names fall in the gap between these two
        jdbcTemplate.update(
                "INSERT INTO UP_USER (USER_ID, USER_NAME, USER_DFLT_LAY_ID, NEXT_STRUCT_ID)"
                        + " VALUES (1, 'admin', 1, 1)");
        jdbcTemplate.update(
                "INSERT INTO UP_USER (USER_ID, USER_NAME, USER_DFLT_LAY_ID, NEXT_STRUCT_ID)"
                        + " VALUES (2, 'system', 1, 1)");
        reset(counterStore);
    }

    @After
    public void tearDown() {
        runSql("DROP TABLE UP_LAYOUT_STRUCT");
        runSql("DROP TABLE UP_USER");
    }

    /**
     * A updates no row and stops before its insert. B updates no row and goes on to its insert,
     * then A goes on. Both users are there afterwards.
     */
    @Test
    public void testNewUsersImportedAtOnce() throws Exception {
        final CountDownLatch aBeforeInsert = new CountDownLatch(1);
        final CountDownLatch bBeforeInsert = new CountDownLatch(1);
        final CountDownLatch resumeA = new CountDownLatch(1);
        final AtomicReference<Thread> threadOfA = new AtomicReference<>();
        // The import takes the new user's id between its update and its insert
        when(counterStore.getNextId("UP_USER"))
                .thenAnswer(
                        invocation -> {
                            if (Thread.currentThread() == threadOfA.get()) {
                                aBeforeInsert.countDown();
                                if (!resumeA.await(30, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("A not resumed");
                                }
                                return ID_OF_A;
                            }
                            bBeforeInsert.countDown();
                            return ID_OF_B;
                        });

        final AtomicReference<Throwable> errorOfA = new AtomicReference<>();
        final AtomicReference<Throwable> errorOfB = new AtomicReference<>();
        final Thread threadA = importInThread("selfedit-a", errorOfA);
        final Thread threadB = importInThread("selfedit-b", errorOfB);
        threadOfA.set(threadA);

        threadA.start();
        assertTrue("A reached its insert", aBeforeInsert.await(30, TimeUnit.SECONDS));
        threadB.start();
        // B gets to its insert at once unless the database makes its update wait for A
        bBeforeInsert.await(10, TimeUnit.SECONDS);
        resumeA.countDown();
        threadA.join(TimeUnit.SECONDS.toMillis(60));
        threadB.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(threadA.isAlive());
        assertFalse(threadB.isAlive());

        if (errorOfA.get() != null) {
            throw new AssertionError("The import of A failed", errorOfA.get());
        }
        if (errorOfB.get() != null) {
            throw new AssertionError("The import of B failed", errorOfB.get());
        }
        final List<Integer> ids =
                jdbcTemplate.queryForList(
                        "SELECT USER_ID FROM UP_USER WHERE USER_NAME IN ('selfedit-a', 'selfedit-b')"
                                + " ORDER BY USER_NAME",
                        Integer.class);
        assertEquals(Arrays.asList(ID_OF_A, ID_OF_B), ids);
    }

    private Thread importInThread(String username, AtomicReference<Throwable> error) {
        final UserType user = new UserType();
        user.setUsername(username);
        return new Thread(
                () -> {
                    try {
                        userImporter.importData(user);
                    } catch (Throwable t) {
                        error.set(t);
                    }
                },
                "UserImporterExporterConcurrencyTest-" + username);
    }

    private void runSql(String sql) {
        new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes())).execute(dataSource);
    }
}
