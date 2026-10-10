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

import java.time.Duration;
import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;
import org.apereo.portal.IPortalInfoProvider;
import org.apereo.portal.jpa.BasePortalJpaDao;
import org.apereo.portal.jpa.cache.EntityManagerCache;
import org.apereo.portal.utils.cache.CacheKey;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/** DB based locking DAO using JPA2 locking APIs */
@Repository
public class JpaClusterLockDao extends BasePortalJpaDao implements IClusterLockDao {
    private static final String CLUSTER_MUTEX_SOURCE =
            JpaClusterLockDao.class.getName() + "_CLUSTER_MUTEX";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Duration abandonedLockAge = Duration.ofSeconds(5);
    private IPortalInfoProvider portalInfoProvider;
    private TransactionTemplate newTransactionTemplate;
    private EntityManagerCache entityManagerCache;

    /**
     * Maximum age of the {@link ClusterMutex#getLastUpdate()} field for a locked mutex. A
     * ClusterMutex with an old lastUpdate value will be considered abandoned and be forcibly
     * unlocked. Defaults to 5 seconds.
     *
     * <p>IMPORTANT: this value must be larger than the maximum possible clock skew across all
     * servers in the cluster.
     */
    @Value("${org.apereo.portal.concurrency.locking.ClusterLockDao.abandonedLockAge:PT60S}")
    public void setAbandonedLockAge(Duration abandonedLockAge) {
        this.abandonedLockAge = abandonedLockAge;
    }

    @Autowired
    public void setPortalInfoProvider(IPortalInfoProvider portalInfoProvider) {
        this.portalInfoProvider = portalInfoProvider;
    }

    @Autowired
    public void setPlatformTransactionManager(
            @Qualifier(BasePortalJpaDao.PERSISTENCE_UNIT_NAME)
                    PlatformTransactionManager platformTransactionManager) {
        this.newTransactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.newTransactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        /*
         * Locking is optimistic: read the mutex, then update it where its version is unchanged; an
         * update that matches no row means another server won. That needs the update to see the
         * latest committed row, as READ COMMITTED guarantees everywhere. Under snapshot isolation
         * it fails instead: MariaDB 11.6+ by default rejects it under REPEATABLE READ with "Record
         * has changed since last read" (MOD-I99).
         */
        this.newTransactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.newTransactionTemplate.afterPropertiesSet();
    }

    @Autowired
    public void setEntityManagerCache(EntityManagerCache entityManagerCache) {
        this.entityManagerCache = entityManagerCache;
    }

    /**
     * Finds the mutex, creating it if needed. Each step runs in a new transaction, so the result
     * does not depend on what a transaction of the caller has already read.
     */
    @Override
    public ClusterMutex getClusterMutex(final String mutexName) {
        ClusterMutex clusterMutex = this.findOrCreateClusterMutex(mutexName);
        if (clusterMutex == null) {
            // Another thread or server created it first; look again in a new transaction
            clusterMutex = this.findOrCreateClusterMutex(mutexName);
        }
        if (clusterMutex == null) {
            throw new IllegalStateException("Failed to find or create ClusterMutex " + mutexName);
        }
        logger.trace("Retrieved {}", clusterMutex);
        return clusterMutex;
    }

    @Override
    public ClusterMutex getLock(final String mutexName) {
        // Create the mutex before the transaction below starts; see findClusterMutex
        getClusterMutex(mutexName);
        return this.executeIgnoreRollback(
                new TransactionCallback<ClusterMutex>() {
                    @Override
                    public ClusterMutex doInTransaction(TransactionStatus status) {
                        final EntityManager entityManager = getEntityManager();

                        final ClusterMutex clusterMutex = findClusterMutex(mutexName);

                        // Check if the mutex is already locked
                        if (clusterMutex.isLocked()) {
                            // Check if the mutex is abandoned
                            if (isLockAbandoned(clusterMutex)) {
                                // Unlock the abandoned mutex
                                unlockAbandonedLock(mutexName);

                                // Attempt to get the lock again
                                return getLock(mutexName);
                            }

                            // Already locked
                            logger.trace("Mutex {} is already locked: {}", mutexName, clusterMutex);
                            return null;
                        }

                        // Lock the mutex and update the DB
                        final String uniqueServerName = portalInfoProvider.getUniqueServerName();
                        clusterMutex.lock(uniqueServerName);
                        entityManager.persist(clusterMutex);
                        try {
                            entityManager.flush();
                            logger.trace("Locked {}", clusterMutex);
                        } catch (OptimisticLockException e) {
                            logger.trace(
                                    "Mutex {} was locked by another thread or server", mutexName);
                            return null;
                        }

                        return clusterMutex;
                    }
                },
                null);
    }

    @Override
    public void updateLock(final String mutexName) {
        // Create the mutex before the transaction below starts; see findClusterMutex
        getClusterMutex(mutexName);
        this.executeIgnoreRollback(
                new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        final EntityManager entityManager = getEntityManager();

                        final ClusterMutex clusterMutex = findClusterMutex(mutexName);

                        validateLockedMutex(clusterMutex);

                        clusterMutex.updateLock();
                        entityManager.persist(clusterMutex);
                        try {
                            entityManager.flush();
                            logger.trace("Updated {}", clusterMutex);
                        } catch (OptimisticLockException e) {
                            final IllegalMonitorStateException imse =
                                    new IllegalMonitorStateException(
                                            "Failed to update "
                                                    + mutexName
                                                    + " due to another thread/server updating the mutex");
                            imse.initCause(e);
                            throw imse;
                        }
                    }
                });
    }

    @Override
    public void releaseLock(final String mutexName) {
        // Create the mutex before the transaction below starts; see findClusterMutex
        getClusterMutex(mutexName);
        this.executeIgnoreRollback(
                new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        final EntityManager entityManager = getEntityManager();

                        final ClusterMutex clusterMutex = findClusterMutex(mutexName);

                        validateLockedMutex(clusterMutex);

                        clusterMutex.unlock();
                        entityManager.persist(clusterMutex);
                        try {
                            entityManager.flush();
                            logger.trace("Released {}", clusterMutex);
                        } catch (OptimisticLockException e) {
                            final IllegalMonitorStateException imse =
                                    new IllegalMonitorStateException(
                                            "Failed to unlock "
                                                    + mutexName
                                                    + " due to another thread/server updating the mutex");
                            imse.initCause(e);
                            throw imse;
                        }
                    }
                });
    }

    /**
     * Reads the mutex in the current transaction. It must exist before that transaction makes its
     * first read: on a database that reads from a snapshot, such as MySQL and MariaDB under
     * InnoDB's default REPEATABLE READ, a transaction never sees rows that other transactions
     * commit after that. Callers therefore run {@link #getClusterMutex(String)} before they start
     * the transaction.
     */
    protected ClusterMutex findClusterMutex(final String mutexName) {
        final ClusterMutex clusterMutex = this.getClusterMutexInternal(mutexName);
        if (clusterMutex == null) {
            throw new IllegalStateException("Failed to find or create ClusterMutex " + mutexName);
        }
        return clusterMutex;
    }

    /** Retrieves a ClusterMutex in the current TX, or in a new one if there is none */
    protected ClusterMutex getClusterMutexInternal(final String mutexName) {
        final TransactionOperations transactionOperations = this.getTransactionOperations();
        return transactionOperations.execute(
                new TransactionCallback<ClusterMutex>() {
                    @Override
                    public ClusterMutex doInTransaction(TransactionStatus status) {
                        final CacheKey key = CacheKey.build(CLUSTER_MUTEX_SOURCE, mutexName);
                        ClusterMutex clusterMutex =
                                entityManagerCache.get(BasePortalJpaDao.PERSISTENCE_UNIT_NAME, key);
                        if (clusterMutex != null) {
                            return clusterMutex;
                        }

                        final NaturalIdQuery<ClusterMutex> query =
                                createNaturalIdQuery(ClusterMutex.class);
                        query.using(ClusterMutex_.name, mutexName);
                        clusterMutex = query.load();

                        entityManagerCache.put(
                                BasePortalJpaDao.PERSISTENCE_UNIT_NAME, key, clusterMutex);

                        return clusterMutex;
                    }
                });
    }

    /**
     * Finds the ClusterMutex with the specified name, or creates it, in a new transaction that has
     * committed when this returns. Returns null if another thread or server created the mutex at
     * the same time: this insert then fails on the unique mutex name, and a new transaction finds
     * the other one's mutex.
     */
    protected ClusterMutex findOrCreateClusterMutex(final String mutexName) {
        return this.executeIgnoreRollback(
                new TransactionCallback<ClusterMutex>() {
                    @Override
                    public ClusterMutex doInTransaction(TransactionStatus status) {
                        final ClusterMutex existing = getClusterMutexInternal(mutexName);
                        if (existing != null) {
                            return existing;
                        }

                        final EntityManager entityManager = getEntityManager();
                        final ClusterMutex clusterMutex = new ClusterMutex(mutexName);
                        entityManager.persist(clusterMutex);
                        try {
                            entityManager.flush();
                            logger.trace("Created {}", clusterMutex);
                        } catch (PersistenceException e) {
                            if (e.getCause() instanceof ConstraintViolationException) {
                                // ignore, another thread beat us to creation
                                logger.debug(
                                        "Failed to create mutex, it was likely created concurrently by another thread: "
                                                + clusterMutex,
                                        e);
                                // Roll back quietly; committing a transaction that Hibernate
                                // marked rollback-only throws UnexpectedRollbackException
                                status.setRollbackOnly();
                                return null;
                            }

                            // re-throw exception with unhandled cause
                            throw e;
                        }
                        return clusterMutex;
                    }
                },
                null);
    }

    /**
     * Validates that the specified mutex is locked by this server, throws
     * IllegalMonitorStateException if either test fails
     */
    protected void validateLockedMutex(ClusterMutex clusterMutex) {
        if (!clusterMutex.isLocked()) {
            throw new IllegalMonitorStateException(
                    "Mutex is not currently locked, it cannot be updated: " + clusterMutex);
        }
        final String serverName = this.portalInfoProvider.getUniqueServerName();
        if (!serverName.equals(clusterMutex.getServerId())) {
            throw new IllegalMonitorStateException(
                    "Mutex is currently locked by another server: "
                            + clusterMutex
                            + " local serverName: "
                            + serverName);
        }
    }

    /** Unlocks an abandoned mutex */
    protected void unlockAbandonedLock(final String mutexName) {
        this.executeIgnoreRollback(
                new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        final EntityManager entityManager = getEntityManager();
                        final ClusterMutex clusterMutex = findClusterMutex(mutexName);

                        if (!isLockAbandoned(clusterMutex)) {
                            // No longer abandoned
                            return;
                        }

                        logger.warn("Unlocking abandoned " + clusterMutex);
                        clusterMutex.unlock();
                        entityManager.persist(clusterMutex);
                        try {
                            entityManager.flush();
                        } catch (OptimisticLockException e) {
                            logger.trace(
                                    "Abandoned mutex {} was cleared by another thread or server",
                                    mutexName);
                        }
                    }
                });
    }

    /** Checks if the specified mutex is abandoned */
    protected boolean isLockAbandoned(final ClusterMutex clusterMutex) {
        return clusterMutex.getLastUpdate()
                < (System.currentTimeMillis() - abandonedLockAge.toMillis());
    }

    protected <T> T executeIgnoreRollback(TransactionCallback<T> action) {
        return this.executeIgnoreRollback(action, null);
    }

    /**
     * Utility for using TransactionTemplate when we know that a rollback might happen and just want
     * to ignore it
     */
    protected <T> T executeIgnoreRollback(TransactionCallback<T> action, T rollbackValue) {
        try {
            // Try to create the mutex in a new TX
            return this.newTransactionTemplate.execute(action);
        } catch (TransactionSystemException e) {
            if (e.getCause() instanceof RollbackException) {
                // Ignore rollbacks
                return rollbackValue;
            }

            // re-throw exception with unhandled cause
            throw e;
        }
    }
}
