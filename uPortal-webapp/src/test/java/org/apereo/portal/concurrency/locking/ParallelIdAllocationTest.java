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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Several threads insert new rows of one entity at the same moment, each on its own connections.
 * Every insert takes its id from the entity's generator: a sequence, or on MySQL, MariaDB and SQL
 * Server a one-row table that Hibernate reads and updates in a transaction of its own. {@code
 * UP_MUTEX_SEQ} has an allocation size of 1, so every new {@link ClusterMutex} goes to the database
 * for its id. No insert may fail, and no id may be handed out twice.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaParallelIdAllocationTestContext.xml")
public class ParallelIdAllocationTest extends BasePortalJpaDaoTest {
    private static final int THREADS = 6;
    private static final int ROUNDS = 10;

    @Autowired private IClusterLockDao clusterLockDao;

    @Test
    public void parallelInsertsGetDistinctIds() throws Exception {
        final Set<Long> ids = ConcurrentHashMap.newKeySet();
        final List<String> failures = new ArrayList<>();
        final CyclicBarrier startOfRound = new CyclicBarrier(THREADS);
        final ExecutorService threads = Executors.newFixedThreadPool(THREADS);
        try {
            final List<Future<List<String>>> results = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                final String prefix = "parallelIds-" + thread + "-";
                results.add(threads.submit(() -> insertMutexes(prefix, startOfRound, ids)));
            }
            for (final Future<List<String>> result : results) {
                failures.addAll(result.get(5, TimeUnit.MINUTES));
            }
        } finally {
            threads.shutdownNow();
        }

        assertEquals(
                failures.size()
                        + " of "
                        + THREADS * ROUNDS
                        + " parallel inserts failed: "
                        + failures,
                0,
                failures.size());
        assertEquals("Each insert must get an id of its own", THREADS * ROUNDS, ids.size());
    }

    /** One new mutex per round. Every thread starts its round at the same moment. */
    private List<String> insertMutexes(String prefix, CyclicBarrier startOfRound, Set<Long> ids)
            throws Exception {
        final List<String> failures = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            final String mutexName = prefix + round;
            startOfRound.await(1, TimeUnit.MINUTES);
            try {
                final Callable<ClusterMutex> insert =
                        () -> clusterLockDao.getClusterMutex(mutexName);
                ids.add(executeInTransaction(insert).getId());
            } catch (RuntimeException e) {
                failures.add(mutexName + ": " + rootCause(e));
            }
        }
        return failures;
    }

    private static String rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.toString();
    }
}
