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
import static org.mockito.Mockito.reset;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

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
     */
    @Test
    public void testLockTakenBetweenReadAndUpdate() throws Exception {
        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName()).thenReturn("ServerA");
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
                                lockOfB.set(execute(() -> serverB.getLock(mutexName)));
                            } catch (Throwable t) {
                                errorOfB.set(t);
                            }
                        },
                        "JpaClusterLockDaoTest-serverB");
        threadB.start();
        assertTrue("B read the mutex", read.await(30, TimeUnit.SECONDS));

        final ClusterMutex lockOfA = execute(() -> clusterLockDao.getLock(mutexName));
        assertNotNull("A took the free lock", lockOfA);

        resume.countDown();
        threadB.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(threadB.isAlive());
        if (errorOfB.get() != null) {
            throw new AssertionError("B failed instead of losing the race", errorOfB.get());
        }
        assertNull("B lost the race", lockOfB.get());

        final ClusterMutex mutex = execute(() -> clusterLockDao.getClusterMutex(mutexName));
        assertTrue(mutex.isLocked());
        assertEquals(lockOfA.getLockStart(), mutex.getLockStart());
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
}
