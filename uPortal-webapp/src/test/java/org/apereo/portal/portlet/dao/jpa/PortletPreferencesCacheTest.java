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
package org.apereo.portal.portlet.dao.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityManagerFactory;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao;
import org.apereo.portal.portlet.dao.IPortletEntityDao;
import org.apereo.portal.portlet.dao.IPortletTypeDao;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletEntity;
import org.apereo.portal.portlet.om.IPortletEntityId;
import org.apereo.portal.portlet.om.IPortletPreference;
import org.apereo.portal.portlet.om.IPortletType;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreLoadEvent;
import org.hibernate.event.spi.PreLoadEventListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * A portlet entity's preferences with the portal's second-level cache turned on, saved from two
 * sessions at once (MOD-I230).
 *
 * <p>The second session reads the preferences from the database before the first one saves, and
 * puts them in the cache after the first one has saved and evicted them. The test holds the second
 * session at that point with a Hibernate pre-load listener, so the order is the same on every run.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaPortletPreferencesCacheTestContext.xml")
public class PortletPreferencesCacheTest extends BasePortalJpaDaoTest {
    private static final String BACKGROUND = "defaultSelectedBackgroundImage";
    private static final long WAIT_SECONDS = 30;

    /** The thread that waits while it loads a preference, and what it waits for. */
    private static volatile Thread heldThread;

    private static volatile CountDownLatch heldThreadLoading = new CountDownLatch(0);
    private static volatile CountDownLatch heldThreadRelease = new CountDownLatch(0);

    private static SessionFactoryImplementor listenersAddedTo;

    @Autowired private IPortletTypeDao jpaChannelTypeDao;
    @Autowired private IPortletDefinitionDao jpaPortletDefinitionDao;
    @Autowired private IPortletEntityDao jpaPortletEntityDao;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private ExecutorService secondSession;

    @Before
    public void holdLoadsOfPreferences() {
        final SessionFactoryImplementor sessionFactory =
                this.entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        // Added once: the listener stays with the cached Spring context
        if (listenersAddedTo != sessionFactory) {
            sessionFactory
                    .getServiceRegistry()
                    .getService(EventListenerRegistry.class)
                    .appendListeners(EventType.PRE_LOAD, new HoldingPreLoadListener());
            listenersAddedTo = sessionFactory;
        }
        sessionFactory.getCache().evictAllRegions();
        this.secondSession = Executors.newSingleThreadExecutor();
    }

    @After
    public void stopSecondSession() {
        heldThread = null;
        heldThreadRelease.countDown();
        this.secondSession.shutdownNow();

        /*
         * Deletes through the DAOs, before deleteAllEntities: that removes each entity type on its
         * own, which would leave a cached preference list naming removed preferences
         */
        execute(
                () -> {
                    for (final IPortletDefinition definition :
                            jpaPortletDefinitionDao.getPortletDefinitions()) {
                        for (final IPortletEntity entity :
                                jpaPortletEntityDao.getPortletEntities(
                                        definition.getPortletDefinitionId())) {
                            jpaPortletEntityDao.deletePortletEntity(entity);
                        }
                        jpaPortletDefinitionDao.deletePortletDefinition(definition);
                    }
                    for (final IPortletType type : jpaChannelTypeDao.getPortletTypes()) {
                        jpaChannelTypeDao.deletePortletType(type);
                    }
                    return null;
                });
        this.entityManagerFactory
                .unwrap(SessionFactoryImplementor.class)
                .getCache()
                .evictAllRegions();
    }

    /**
     * The user clears their background in one session while choosing another one in a second. The
     * second save is refused, and afterwards every load sees the first session's choice.
     */
    @Test
    public void refusedConcurrentSaveLeavesNoStaleCachedPreferences() throws Exception {
        final IPortletEntityId entityId = createEntityWithBackground("galaxy");
        heldThreadLoading = new CountDownLatch(1);
        heldThreadRelease = new CountDownLatch(1);

        // The second session reads the preferences, then waits before caching them
        final Future<?> secondSave =
                this.secondSession.submit(
                        () -> {
                            heldThread = Thread.currentThread();
                            saveBackground(entityId, "mountain");
                            return null;
                        });
        assertTrue(
                "The second session did not load the preferences",
                heldThreadLoading.await(WAIT_SECONDS, TimeUnit.SECONDS));

        // The first session clears the choice and commits
        saveBackground(entityId, null);

        // The second session caches what it read, and its save is refused
        heldThreadRelease.countDown();
        final ExecutionException refused =
                assertThrows(
                        ExecutionException.class,
                        () -> secondSave.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(
                "Expected the save to be refused, got " + refused.getCause(),
                isRefusedSave(refused.getCause()));

        // The first load caches the entity again; the second reads it and its list from the cache
        for (int load = 1; load <= 3; load++) {
            assertEquals("Load " + load, Collections.emptyList(), backgroundValues(entityId));
        }

        // And the user can choose again
        saveBackground(entityId, "nebula");
        assertEquals(Arrays.asList("nebula"), backgroundValues(entityId));
    }

    /**
     * Whether a save failed because another session changed the preferences since this one read
     * them. Hibernate's version check refuses it as an optimistic locking failure, except where the
     * database refuses the write first: MariaDB 11.6 and later under REPEATABLE READ with
     * innodb_snapshot_isolation, its default, answer error 1020, "Record has changed since last
     * read" (MOD-I99), which Hibernate does not map.
     */
    private static boolean isRefusedSave(Throwable failure) {
        if (failure instanceof OptimisticLockingFailureException) {
            return true;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException && ((SQLException) cause).getErrorCode() == 1020) {
                return true;
            }
            if (cause instanceof BatchUpdateException) {
                for (SQLException next = ((SQLException) cause).getNextException();
                        next != null;
                        next = next.getNextException()) {
                    if (next.getErrorCode() == 1020) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The second of two saves that do not overlap is not refused. */
    @Test
    public void saveAfterSaveKeepsTheLaterChoice() throws Exception {
        final IPortletEntityId entityId = createEntityWithBackground("galaxy");

        saveBackground(entityId, null);
        saveBackground(entityId, "nebula");

        assertEquals(Arrays.asList("nebula"), backgroundValues(entityId));
        assertEquals(Arrays.asList("nebula"), backgroundValues(entityId));
    }

    private IPortletEntityId createEntityWithBackground(String background) {
        final IPortletDefinition definition =
                executeInTransaction(
                        () -> {
                            final IPortletType type =
                                    jpaChannelTypeDao.createPortletType("BaseType", "foobar");
                            final IPortletDefinition created =
                                    new PortletDefinitionImpl(
                                            type,
                                            "background-preference",
                                            "Background Preference",
                                            "Background Preference",
                                            "/uPortal",
                                            "backgroundPreference",
                                            true);
                            jpaPortletDefinitionDao.savePortletDefinition(created);
                            return created;
                        });
        // In the DAO's own transaction, so the entity has its id when it returns
        final IPortletEntityId entityId =
                execute(
                        () ->
                                jpaPortletEntityDao
                                        .createPortletEntity(
                                                definition.getPortletDefinitionId(), "u12l1n11", 12)
                                        .getPortletEntityId());
        saveBackground(entityId, background);
        return entityId;
    }

    /** Loads the entity, sets or (with null) removes the background, and saves, in one session. */
    private void saveBackground(IPortletEntityId entityId, String background) {
        executeInTransaction(
                () -> {
                    final IPortletEntity entity = jpaPortletEntityDao.getPortletEntity(entityId);
                    final List<IPortletPreference> preferences = new ArrayList<>();
                    if (background != null) {
                        preferences.add(new PortletPreferenceImpl(BACKGROUND, false, background));
                    }
                    entity.setPortletPreferences(preferences);
                    jpaPortletEntityDao.updatePortletEntity(entity);
                    return null;
                });
    }

    /** The background values a new session loads. */
    private List<String> backgroundValues(IPortletEntityId entityId) {
        return executeInTransaction(
                () -> {
                    // find() answers null when an entity it loads names one that is gone
                    final IPortletEntity entity = jpaPortletEntityDao.getPortletEntity(entityId);
                    assertNotNull("The portlet entity could not be loaded", entity);
                    final List<String> values = new ArrayList<>();
                    for (final IPortletPreference preference : entity.getPortletPreferences()) {
                        if (BACKGROUND.equals(preference.getName())) {
                            values.addAll(Arrays.asList(preference.getValues()));
                        }
                    }
                    return values;
                });
    }

    /**
     * Holds the {@link #heldThread} when it loads a portlet preference from the database: after
     * Hibernate read it and before Hibernate puts the preference list in the cache.
     */
    private static final class HoldingPreLoadListener implements PreLoadEventListener {
        private static final long serialVersionUID = 1L;

        @Override
        public void onPreLoad(PreLoadEvent event) {
            if (Thread.currentThread() != heldThread
                    || !(event.getEntity() instanceof PortletPreferenceImpl)) {
                return;
            }
            heldThread = null;
            heldThreadLoading.countDown();
            try {
                if (!heldThreadRelease.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The first session did not save in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
