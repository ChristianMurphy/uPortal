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
package org.apereo.portal.concurrency.locking;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.Assert;
import org.apereo.portal.IPortalInfoProvider;
import org.apereo.portal.concurrency.CallableWithoutResult;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.apereo.portal.test.ThreadGroupRunner;
import org.apereo.portal.utils.threading.ThrowingRunnable;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaClusterLockDaoTestContext.xml")
public class JpaClusterLockDaoTest extends BasePortalJpaDaoTest {
    @Autowired private IClusterLockDao clusterLockDao;
    @Autowired private IPortalInfoProvider portalInfoProvider;
    @Autowired private AutowireCapableBeanFactory beanFactory;

    /** Misses the mutex on its first lookup, as if another server created it a moment later. */
    static class LateJpaClusterLockDao extends JpaClusterLockDao {
        private final AtomicBoolean missNextLookup = new AtomicBoolean(true);

        @Override
        protected ClusterMutex getClusterMutexInternal(String mutexName) {
            if (missNextLookup.getAndSet(false)) {
                return null;
            }
            return super.getClusterMutexInternal(mutexName);
        }
    }

    @Test
    public void testLostCreationRace() {
        final String mutexName = "testLostCreationRace";
        final ClusterMutex otherServersMutex =
                execute(() -> clusterLockDao.getClusterMutex(mutexName));

        // This server's insert fails on the unique name; it must find the other mutex instead
        final IClusterLockDao lateDao =
                (IClusterLockDao)
                        beanFactory.createBean(
                                LateJpaClusterLockDao.class,
                                AutowireCapableBeanFactory.AUTOWIRE_NO,
                                false);
        final ClusterMutex mutex = execute(() -> lateDao.getClusterMutex(mutexName));
        assertEquals(otherServersMutex.getId(), mutex.getId());
    }

    /** Pauses getLock after it has read the mutex in its transaction, before it locks it. */
    static class PausingJpaClusterLockDao extends JpaClusterLockDao {
        // The bean comes back behind a proxy, so the test hands the latches over statically
        static volatile CountDownLatch read;
        static volatile CountDownLatch resume;

        @Override
        protected ClusterMutex findClusterMutex(String mutexName) {
            final ClusterMutex clusterMutex = super.findClusterMutex(mutexName);
            read.countDown();
            try {
                if (!resume.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Not resumed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return clusterMutex;
        }
    }

    /**
     * Two servers lock the same free mutex at once: B reads it, A locks it and commits, then B
     * tries to lock the copy it read. B has to lose quietly, with no lock and no error. Under
     * snapshot isolation the update of a row that changed after the transaction read it fails
     * instead of matching no row: MariaDB 11.6 and later do that by default under REPEATABLE READ
     * (innodb_snapshot_isolation; "Record has changed since last read in table 'UP_MUTEX'",
     * MOD-I99). The databases that read the latest committed row pass either way.
     *
     * <p>B runs inside a transaction of its caller, as the threads of testConcurrentLocking do, so
     * getLock's own transaction gets a new EntityManager and really reads the row; with an
     * EntityManager already bound to the thread it would find the mutex in that persistence context
     * instead, and take no snapshot.
     */
    @Test
    public void testLockTakenBetweenReadAndUpdate() throws Exception {
        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName()).thenReturn("ServerA");
        // B only asks for its name after A has locked; MySQL and MariaDB keep LOCK_START in whole
        // seconds, so the owner tells the two locks apart
        final String mutexName = "testLockTakenBetweenReadAndUpdate";
        execute(() -> clusterLockDao.getClusterMutex(mutexName));

        final CountDownLatch read = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        PausingJpaClusterLockDao.read = read;
        PausingJpaClusterLockDao.resume = resume;
        final IClusterLockDao serverB =
                (IClusterLockDao)
                        beanFactory.createBean(
                                PausingJpaClusterLockDao.class,
                                AutowireCapableBeanFactory.AUTOWIRE_NO,
                                false);
        final AtomicReference<ClusterMutex> lockOfB = new AtomicReference<>();
        final AtomicReference<Throwable> errorOfB = new AtomicReference<>();
        final Thread threadB =
                new Thread(
                        () -> {
                            try {
                                lockOfB.set(executeInTransaction(() -> serverB.getLock(mutexName)));
                            } catch (Throwable t) {
                                errorOfB.set(t);
                            }
                        },
                        "JpaClusterLockDaoTest-serverB");
        threadB.start();
        assertTrue("B read the mutex", read.await(30, TimeUnit.SECONDS));

        final ClusterMutex lockOfA = execute(() -> clusterLockDao.getLock(mutexName));
        assertNotNull("A took the free lock", lockOfA);

        when(portalInfoProvider.getUniqueServerName()).thenReturn("ServerB");
        resume.countDown();
        threadB.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(threadB.isAlive());
        if (errorOfB.get() != null) {
            throw new AssertionError("B failed instead of losing the race", errorOfB.get());
        }
        assertNull("B lost the race", lockOfB.get());

        final ClusterMutex mutex = execute(() -> clusterLockDao.getClusterMutex(mutexName));
        assertTrue(mutex.isLocked());
        assertEquals("ServerA", mutex.getServerId());
        when(portalInfoProvider.getUniqueServerName()).thenReturn("ServerA");
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.releaseLock(mutexName);
                    }
                });
    }

    @Test
    public void testConcurrentCreation() throws InterruptedException {
        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName()).thenReturn("ServerA");

        final ThreadGroupRunner threadGroupRunner =
                new ThreadGroupRunner("JpaClusterLockDaoTest-", true);

        threadGroupRunner.addTask(
                3,
                new ThrowingRunnable() {
                    @Override
                    public void runWithException() throws Throwable {
                        executeInTransaction(
                                new CallableWithoutResult() {
                                    @Override
                                    protected void callWithoutResult() {
                                        try {
                                            final String mutexName = "testConcurrentCreation";

                                            threadGroupRunner.tick(1);
                                            ClusterMutex mutex =
                                                    clusterLockDao.getClusterMutex(mutexName);
                                            assertNotNull(mutex);
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                    }
                });

        threadGroupRunner.start();
        threadGroupRunner.join();
    }

    @Test
    public void testConcurrentLocking() throws InterruptedException {
        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName()).thenReturn("ServerA");

        final String mutexName = "testConcurrentLocking";

        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        final ClusterMutex mutex = clusterLockDao.getClusterMutex(mutexName);
                        assertNotNull(mutex);
                    }
                });

        // The context's DAO treats a lock as abandoned after 100ms, but MySQL and MariaDB keep
        // LOCK_UPDATE in whole seconds: a thread that reads the lock just after another thread
        // took it can see it as older than that, and take it over. Lock with the default (60s).
        final IClusterLockDao lockingDao =
                (IClusterLockDao)
                        beanFactory.createBean(
                                JpaClusterLockDao.class,
                                AutowireCapableBeanFactory.AUTOWIRE_NO,
                                false);

        final ThreadGroupRunner threadGroupRunner =
                new ThreadGroupRunner("JpaClusterLockDaoTest-", true);

        final AtomicInteger lockCounter = new AtomicInteger();

        threadGroupRunner.addTask(
                3,
                new ThrowingRunnable() {
                    @Override
                    public void runWithException() throws Throwable {
                        executeInTransaction(
                                new CallableWithoutResult() {
                                    @Override
                                    protected void callWithoutResult() {
                                        try {
                                            threadGroupRunner.tick(1);
                                            try {
                                                final ClusterMutex mutex =
                                                        lockingDao.getLock(mutexName);
                                                if (mutex != null) {
                                                    lockCounter.incrementAndGet();
                                                }
                                            } finally {
                                                threadGroupRunner.tick(3);
                                            }
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                    }
                });

        threadGroupRunner.start();
        threadGroupRunner.join();

        assertEquals(1, lockCounter.intValue());

        ClusterMutex mutex = clusterLockDao.getClusterMutex(mutexName);
        assertTrue(mutex.isLocked());

        clusterLockDao.releaseLock(mutexName);

        mutex = clusterLockDao.getClusterMutex(mutexName);
        assertFalse(mutex.isLocked());
    }

    /**
     * This test turns out to be nondeterministic under load and so can yield false-negatives
     * (failures that don't seem to actually indicate a regression).
     *
     * @throws InterruptedException
     */
    @Test
    @Ignore
    public void testConcurrentCreateLocking() throws InterruptedException {
        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName()).thenReturn("ServerA");

        final String mutexName = "testConcurrentLocking";
        final ThreadGroupRunner threadGroupRunner =
                new ThreadGroupRunner("JpaClusterLockDaoTest-", true);

        final AtomicInteger lockCounter = new AtomicInteger();

        threadGroupRunner.addTask(
                3,
                new ThrowingRunnable() {
                    @Override
                    public void runWithException() throws Throwable {
                        executeInTransaction(
                                new CallableWithoutResult() {
                                    @Override
                                    protected void callWithoutResult() {
                                        try {
                                            threadGroupRunner.tick(1);
                                            try {
                                                final ClusterMutex mutex =
                                                        clusterLockDao.getLock(mutexName);
                                                if (mutex != null) {
                                                    lockCounter.incrementAndGet();
                                                }
                                            } finally {
                                                threadGroupRunner.tick(3);
                                            }
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                    }
                });

        threadGroupRunner.start();
        threadGroupRunner.join();

        assertEquals(1, lockCounter.intValue());

        ClusterMutex mutex = clusterLockDao.getClusterMutex(mutexName);
        assertTrue(mutex.isLocked());

        clusterLockDao.releaseLock(mutexName);

        mutex = clusterLockDao.getClusterMutex(mutexName);
        assertFalse(mutex.isLocked());
    }

    /**
     * Ignoring this test because it is nondeterministic.
     *
     * @throws Exception
     */
    @Test
    @Ignore
    public void testNotAbandoned() throws Exception {
        // Used to make a 'mutable string'
        final AtomicReference<String> currentServer = new AtomicReference<String>("ServerA");
        final String mutexName = "testNotAbandoned";

        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return currentServer.get();
                            }
                        });

        // get/create the mutex
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        final ClusterMutex mutex = clusterLockDao.getClusterMutex(mutexName);
                        assertNotNull(mutex);
                    }
                });

        // lock serverA
        currentServer.set("ServerA");
        final ClusterMutex lockedMutex =
                execute(
                        new Callable<ClusterMutex>() {
                            @Override
                            public ClusterMutex call() throws Exception {
                                final ClusterMutex mutex = clusterLockDao.getLock(mutexName);
                                assertNotNull(mutex);
                                return mutex;
                            }
                        });

        // test context configures a 100ms abandoned lock timeout, spin in tryLock/updateLock for
        // 110ms
        while (lockedMutex.getLockStart() + 110 > System.currentTimeMillis()) {
            // try lock ServerB
            currentServer.set("ServerB");
            execute(
                    new CallableWithoutResult() {
                        @Override
                        protected void callWithoutResult() {
                            final ClusterMutex mutex = clusterLockDao.getLock(mutexName);
                            assertNull(mutex);
                        }
                    });
            TimeUnit.MILLISECONDS.sleep(1);
            // ServerA update ping
            currentServer.set("ServerA");
            execute(
                    new CallableWithoutResult() {
                        @Override
                        protected void callWithoutResult() {
                            clusterLockDao.updateLock(mutexName);
                        }
                    });
            TimeUnit.MILLISECONDS.sleep(1);
        }

        currentServer.set("ServerA");

        ClusterMutex mutex = clusterLockDao.getClusterMutex(mutexName);
        assertTrue(mutex.isLocked());

        clusterLockDao.releaseLock(mutexName);

        mutex = clusterLockDao.getClusterMutex(mutexName);
        assertFalse(mutex.isLocked());
    }

    /**
     * Ignoring this test because it is nondeterministic.
     *
     * @throws Exception
     */
    @Test
    @Ignore
    public void testAbandoned() throws Exception {
        // Used to make a 'mutable string'
        final AtomicReference<String> currentServer = new AtomicReference<String>("ServerA");
        final String mutexName = "testNotAbandoned";

        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return currentServer.get();
                            }
                        });

        // get/create the mutex
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        final ClusterMutex mutex = clusterLockDao.getClusterMutex(mutexName);
                        Assert.assertNotNull(mutex);
                    }
                });

        // lock serverA
        currentServer.set("ServerA");
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        final ClusterMutex mutex = clusterLockDao.getLock(mutexName);
                        Assert.assertNotNull(mutex);
                    }
                });

        final AtomicInteger lockFailCount = new AtomicInteger(0);
        final AtomicBoolean serverBLocked = new AtomicBoolean(false);

        currentServer.set("ServerB");

        // test context configures a 100ms abandoned lock timeout, wait 110 between tests
        for (int i = 0; i < 5 && !serverBLocked.get(); i++) {
            // try lock ServerB
            execute(
                    new CallableWithoutResult() {
                        @Override
                        protected void callWithoutResult() {
                            final ClusterMutex mutex = clusterLockDao.getLock(mutexName);
                            if (mutex == null) {
                                lockFailCount.incrementAndGet();
                            } else {
                                serverBLocked.set(true);
                            }
                        }
                    });

            TimeUnit.MILLISECONDS.sleep(110);
        }

        assertTrue(serverBLocked.get());
        assertEquals(1, lockFailCount.get());

        currentServer.set("ServerB");

        ClusterMutex mutex = clusterLockDao.getClusterMutex(mutexName);
        assertTrue(mutex.isLocked());

        clusterLockDao.releaseLock(mutexName);

        mutex = clusterLockDao.getClusterMutex(mutexName);
        assertFalse(mutex.isLocked());
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUnlockedUpdate() throws Exception {
        // Used to make a 'mutable string'
        final AtomicReference<String> currentServer = new AtomicReference<String>("ServerA");
        final String mutexName = "testUnlockedUpdate";

        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return currentServer.get();
                            }
                        });

        // get/create the mutex
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.updateLock(mutexName);
                    }
                });
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUnlockedRelease() throws Exception {
        // Used to make a 'mutable string'
        final AtomicReference<String> currentServer = new AtomicReference<String>("ServerA");
        final String mutexName = "testUnlockedRelease";

        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return currentServer.get();
                            }
                        });

        // get/create the mutex
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.releaseLock(mutexName);
                    }
                });
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testWrongServerUpdate() throws Exception {
        // Used to make a 'mutable string'
        final AtomicReference<String> currentServer = new AtomicReference<String>("ServerA");
        final String mutexName = "testUnlockedUpdate";

        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return currentServer.get();
                            }
                        });

        // get/create the mutex
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.getLock(mutexName);
                    }
                });

        currentServer.set("ServerB");
        // get/create the mutex
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.updateLock(mutexName);
                    }
                });
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testWrongServerRelease() throws Exception {
        // Used to make a 'mutable string'
        final AtomicReference<String> currentServer = new AtomicReference<String>("ServerA");
        final String mutexName = "testUnlockedRelease";

        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return currentServer.get();
                            }
                        });

        // get/create the mutex
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.getLock(mutexName);
                    }
                });

        currentServer.set("ServerB");

        // get/create the mutex
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.releaseLock(mutexName);
                    }
                });
    }

    /** A DAO of its own with the given abandoned-lock age, for the takeover tests. */
    private IClusterLockDao daoWithAbandonedLockAge(long millis) throws Exception {
        final IClusterLockDao dao =
                (IClusterLockDao)
                        beanFactory.createBean(
                                JpaClusterLockDao.class,
                                AutowireCapableBeanFactory.AUTOWIRE_NO,
                                false);
        // The bean is a proxy of the interface; the age is set on the DAO behind it
        final JpaClusterLockDao target =
                (JpaClusterLockDao) ((Advised) dao).getTargetSource().getTarget();
        target.setAbandonedLockAge(org.joda.time.Duration.millis(millis));
        return dao;
    }

    /**
     * A lock its server stopped updating is taken over once it is older than the abandoned-lock
     * age. Deterministic, unlike testAbandoned: the age is 1 ms and the lock is 50 ms old.
     */
    @Test
    public void anAbandonedLockIsTakenOver() throws Exception {
        final AtomicReference<String> server = new AtomicReference<>("ServerA");
        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName()).thenAnswer(invocation -> server.get());
        final IClusterLockDao dao = daoWithAbandonedLockAge(1);
        final String mutexName = "anAbandonedLockIsTakenOver";
        assertNotNull(execute(() -> dao.getLock(mutexName)));
        TimeUnit.MILLISECONDS.sleep(50);

        server.set("ServerB");
        final ClusterMutex taken = execute(() -> dao.getLock(mutexName));
        assertNotNull("ServerB takes over the abandoned lock", taken);
        assertEquals("ServerB", taken.getServerId());
        assertTrue(execute(() -> dao.getClusterMutex(mutexName)).isLocked());
    }

    /**
     * A lock younger than the abandoned-lock age stays with its server, and its server's update
     * moves its last update on. Deterministic, unlike testNotAbandoned: the age is one hour.
     */
    @Test
    public void aLiveLockStaysWithItsServer() throws Exception {
        final AtomicReference<String> server = new AtomicReference<>("ServerA");
        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName()).thenAnswer(invocation -> server.get());
        final IClusterLockDao dao = daoWithAbandonedLockAge(TimeUnit.HOURS.toMillis(1));
        final String mutexName = "aLiveLockStaysWithItsServer";
        final ClusterMutex locked = execute(() -> dao.getLock(mutexName));
        assertNotNull(locked);

        server.set("ServerB");
        assertNull("the lock is not abandoned", execute(() -> dao.getLock(mutexName)));

        // MySQL and MariaDB keep the times in whole seconds
        TimeUnit.MILLISECONDS.sleep(1100);
        server.set("ServerA");
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        dao.updateLock(mutexName);
                    }
                });
        final ClusterMutex updated = execute(() -> dao.getClusterMutex(mutexName));
        assertTrue(updated.isLocked());
        assertEquals("ServerA", updated.getServerId());
        assertTrue(
                updated.getLastUpdate() + " after " + locked.getLastUpdate(),
                updated.getLastUpdate() > locked.getLastUpdate());
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        dao.releaseLock(mutexName);
                    }
                });
        assertFalse(execute(() -> dao.getClusterMutex(mutexName)).isLocked());
    }

    /**
     * Every lock operation runs in a new READ COMMITTED transaction (MOD-I99): the optimistic
     * update must see the latest committed row. HSQLDB reads committed rows anyway, so this checks
     * the transaction the DAO asks for.
     */
    @Test
    public void lockTransactionsAreNewAndReadCommitted() {
        final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        final JpaClusterLockDao dao = new JpaClusterLockDao();
        dao.setPlatformTransactionManager(transactions);
        assertEquals("result", dao.executeIgnoreRollback(status -> "result"));
        final ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions).getTransaction(definition.capture());
        assertEquals(
                TransactionDefinition.ISOLATION_READ_COMMITTED,
                definition.getValue().getIsolationLevel());
        assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definition.getValue().getPropagationBehavior());
    }
}
