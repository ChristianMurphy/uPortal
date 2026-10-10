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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import org.apereo.portal.IPortalInfoProvider;
import org.apereo.portal.concurrency.CallableWithoutResult;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Uses mutexes for the first time on a database whose transactions read from a snapshot, as MySQL
 * and MariaDB do by default (see jpaClusterLockDaoSnapshotIsolationTestContext.xml). A mutex that
 * is created while a transaction is open must still be found.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaClusterLockDaoSnapshotIsolationTestContext.xml")
public class JpaClusterLockDaoSnapshotIsolationTest extends BasePortalJpaDaoTest {
    @Autowired private IClusterLockDao clusterLockDao;
    @Autowired private IPortalInfoProvider portalInfoProvider;

    @Before
    public void setUp() {
        reset(portalInfoProvider);
        when(portalInfoProvider.getUniqueServerName()).thenReturn("ServerA");
    }

    @Test
    public void testLockNewMutex() {
        final String mutexName = "testLockNewMutex";

        final ClusterMutex lockedMutex = execute(() -> clusterLockDao.getLock(mutexName));
        assertNotNull(lockedMutex);
        assertTrue(lockedMutex.isLocked());

        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.releaseLock(mutexName);
                    }
                });
        assertFalse(execute(() -> clusterLockDao.getClusterMutex(mutexName)).isLocked());
    }

    @Test
    public void testGetNewMutexAfterTheTransactionHasRead() {
        executeInTransaction(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        // Fixes this transaction's snapshot before the mutex exists
                        getEntityManager()
                                .createQuery("SELECT m FROM ClusterMutex m")
                                .getResultList();

                        assertNotNull(
                                clusterLockDao.getClusterMutex(
                                        "testGetNewMutexAfterTheTransactionHasRead"));
                    }
                });
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUpdateNewMutex() {
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.updateLock("testUpdateNewMutex");
                    }
                });
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testReleaseNewMutex() {
        execute(
                new CallableWithoutResult() {
                    @Override
                    protected void callWithoutResult() {
                        clusterLockDao.releaseLock("testReleaseNewMutex");
                    }
                });
    }
}
