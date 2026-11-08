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
package org.apereo.portal.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.apereo.portal.security.IPerson;
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
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Two sessions of one user save the user's language at the same moment, such as two browser tabs or
 * two tests signed in as the same user. The store deletes the user's rows in UP_USER_LOCALE and
 * inserts the new ones. When both saves deleted before either inserted, the same locale twice
 * violated the primary key (USER_ID, LOCALE), and two different locales both stayed. Both saves
 * have to succeed, and the later one has to win.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:rdbmLocaleStoreTestContext.xml")
public class RDBMLocaleStoreConcurrencyTest {
    private static final int USER_ID = 9001;
    private static final String INSERT = "INSERT INTO UP_USER_LOCALE";

    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;

    private JdbcTemplate jdbcTemplate;
    private PausingLocaleStore store;
    private IPerson person;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        runSql(
                "CREATE TABLE UP_USER (USER_ID integer NOT NULL, USER_NAME varchar(100),"
                        + " USER_DFLT_LAY_ID integer, NEXT_STRUCT_ID integer,"
                        + " LST_CHAN_UPDT_DT timestamp, PRIMARY KEY (USER_ID))");
        runSql(
                "CREATE TABLE UP_USER_LOCALE (USER_ID integer NOT NULL,"
                        + " LOCALE varchar(64) NOT NULL, PRIORITY integer,"
                        + " PRIMARY KEY (USER_ID, LOCALE))");
        jdbcTemplate.update(
                "INSERT INTO UP_USER (USER_ID, USER_NAME, USER_DFLT_LAY_ID, NEXT_STRUCT_ID)"
                        + " VALUES (?, 'student', 1, 1)",
                USER_ID);

        store = new PausingLocaleStore();
        store.setPlatformTransactionManager(transactionManager);
        store.setDataSource(dataSource);
        person = mock(IPerson.class);
        when(person.getID()).thenReturn(USER_ID);
    }

    @After
    public void tearDown() {
        runSql("DROP TABLE UP_USER_LOCALE");
        runSql("DROP TABLE UP_USER");
    }

    /** Both save the same language, and the user had none: both inserted the same row. */
    @Test
    public void testSameLocaleSavedTwiceAtOnce() throws Exception {
        saveAtOnce(Locale.forLanguageTag("es-ES"), Locale.forLanguageTag("es-ES"));

        assertEquals(Collections.singletonList("es_ES"), storedLocales());
        assertSavedUnderReadCommitted();
    }

    /** A saves French and B Spanish over English. B saves later, so Spanish remains. */
    @Test
    public void testLaterSaveReplacesTheEarlierOne() throws Exception {
        store.updateUserLocales(person, new Locale[] {Locale.US});

        saveAtOnce(Locale.FRANCE, Locale.forLanguageTag("es-ES"));

        assertEquals(Collections.singletonList("es_ES"), storedLocales());
        assertSavedUnderReadCommitted();
    }

    /**
     * A user whose session has an id without an UP_USER row, such as a user deleted while signed
     * in: there is no row to lock, and the save goes on as before.
     */
    @Test
    public void testUserWithoutUserRowStillSaves() {
        final IPerson personWithoutRow = mock(IPerson.class);
        when(personWithoutRow.getID()).thenReturn(USER_ID + 1);

        store.updateUserLocales(personWithoutRow, new Locale[] {Locale.FRANCE});

        assertEquals(
                Collections.singletonList("fr_FR"),
                jdbcTemplate.queryForList(
                        "SELECT LOCALE FROM UP_USER_LOCALE WHERE USER_ID=?",
                        String.class,
                        USER_ID + 1));
        assertSavedUnderReadCommitted();
    }

    /**
     * A saves {@code localeOfA} and stops before its insert. B saves {@code localeOfB} meanwhile,
     * then A goes on. Both saves have to succeed.
     */
    private void saveAtOnce(Locale localeOfA, Locale localeOfB) throws InterruptedException {
        final AtomicReference<Throwable> errorOfA = new AtomicReference<>();
        final AtomicReference<Throwable> errorOfB = new AtomicReference<>();
        final Thread threadA = saveInThread("A", localeOfA, errorOfA);
        final Thread threadB = saveInThread("B", localeOfB, errorOfB);
        store.pause(threadA, threadB);

        threadA.start();
        assertTrue("A reached its insert", store.paused.await(30, TimeUnit.SECONDS));
        threadB.start();
        assertTrue("B started", store.otherStarted.await(30, TimeUnit.SECONDS));
        // B finishes unless the database makes it wait for A
        threadB.join(TimeUnit.SECONDS.toMillis(2));
        store.resume.countDown();
        threadA.join(TimeUnit.SECONDS.toMillis(60));
        threadB.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(threadA.isAlive());
        assertFalse(threadB.isAlive());

        if (errorOfA.get() != null) {
            throw new AssertionError("The save of A failed", errorOfA.get());
        }
        if (errorOfB.get() != null) {
            throw new AssertionError("The save of B failed", errorOfB.get());
        }
    }

    private Thread saveInThread(String name, Locale locale, AtomicReference<Throwable> error) {
        return new Thread(
                () -> {
                    try {
                        store.updateUserLocales(person, new Locale[] {locale});
                    } catch (Throwable t) {
                        error.set(t);
                    }
                },
                "RDBMLocaleStoreConcurrencyTest-" + name);
    }

    /**
     * The test's connections default to SERIALIZABLE (rdbmLocaleStoreTestContext.xml), so a save
     * that doesn't ask for READ COMMITTED itself shows up here on every database.
     */
    private void assertSavedUnderReadCommitted() {
        assertFalse("The store prepared statements", store.isolations.isEmpty());
        for (final int isolation : store.isolations) {
            assertEquals(
                    "Isolation of a save's transaction",
                    Connection.TRANSACTION_READ_COMMITTED,
                    isolation);
        }
    }

    private List<String> storedLocales() {
        return jdbcTemplate.queryForList(
                "SELECT LOCALE FROM UP_USER_LOCALE WHERE USER_ID=? ORDER BY PRIORITY, LOCALE",
                String.class,
                USER_ID);
    }

    private void runSql(String sql) {
        new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes())).execute(dataSource);
    }

    /**
     * Stops one thread before its insert until the test lets it go on, tells the test when another
     * thread prepares its first statement, and records the isolation level of every statement's
     * connection.
     */
    static class PausingLocaleStore extends RDBMLocaleStore {
        final List<Integer> isolations = new CopyOnWriteArrayList<>();
        final CountDownLatch paused = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final CountDownLatch otherStarted = new CountDownLatch(1);
        private volatile Thread pausedThread;
        private volatile Thread otherThread;

        void pause(Thread thread, Thread other) {
            pausedThread = thread;
            otherThread = other;
        }

        @Override
        public void setDataSource(DataSource dataSource) {
            jdbcOperations =
                    new JdbcTemplate(dataSource) {
                        @Override
                        protected Connection createConnectionProxy(Connection con) {
                            return pausing(super.createConnectionProxy(con));
                        }
                    };
        }

        private Connection pausing(Connection target) {
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                if ("prepareStatement".equals(method.getName())) {
                                    isolations.add(target.getTransactionIsolation());
                                    beforeStatement((String) args[0]);
                                }
                                try {
                                    return method.invoke(target, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
        }

        private void beforeStatement(String sql) throws InterruptedException {
            final Thread current = Thread.currentThread();
            if (current == otherThread) {
                otherStarted.countDown();
            } else if (current == pausedThread && sql.startsWith(INSERT)) {
                pausedThread = null;
                paused.countDown();
                if (!resume.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Not resumed");
                }
            }
        }
    }
}
