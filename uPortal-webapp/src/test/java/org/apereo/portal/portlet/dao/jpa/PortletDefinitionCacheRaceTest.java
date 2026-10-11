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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao;
import org.apereo.portal.portlet.dao.IPortletTypeDao;
import org.apereo.portal.portlet.om.IPortletDefinition;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * A request that read a portlet definition before a re-import committed must not put the old
 * definition into the second-level cache after it (MOD-I204).
 *
 * <p>Hibernate reads an entity's row, calls the pre-load listeners, then puts the loaded state into
 * the second-level cache. The listener below holds the reading thread between the read and the put
 * while another thread re-imports the definition (new title, localized title, parameter, content
 * preference and one more preference) and commits. When the reader goes on, its put must not
 * replace what the update left: the next request, through the cache, has to see the re-imported
 * definition and be able to save its title and content again. In the portal the reader was the
 * search launcher's /api/portletList, still running when the import committed.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaPortalSecondLevelCacheTestContext.xml")
public class PortletDefinitionCacheRaceTest extends BasePortalJpaDaoTest {

    private static final long WAIT_SECONDS = 30;

    /** Holds one chosen thread at its first pre-load, until released. */
    private static final class HoldBeforeCachePut implements PreLoadEventListener {
        private static final long serialVersionUID = 1L;

        private final AtomicReference<Thread> holdThread = new AtomicReference<>();
        private volatile CountDownLatch read = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        void holdNextLoadOf(Thread thread) {
            read = new CountDownLatch(1);
            release = new CountDownLatch(1);
            holdThread.set(thread);
        }

        @Override
        public void onPreLoad(PreLoadEvent event) {
            if (holdThread.compareAndSet(Thread.currentThread(), null)) {
                read.countDown();
                try {
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static final HoldBeforeCachePut HOLD = new HoldBeforeCachePut();

    @Autowired private IPortletTypeDao portletTypeDao;
    @Autowired private IPortletDefinitionDao portletDefinitionDao;

    private EntityManagerFactory emf;
    private SessionFactoryImplementor sessionFactory;

    @Before
    public void registerHold() {
        emf = getEntityManager().getEntityManagerFactory();
        sessionFactory = emf.unwrap(SessionFactoryImplementor.class);
        final EventListenerRegistry registry =
                sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        boolean registered = false;
        for (PreLoadEventListener listener :
                registry.getEventListenerGroup(EventType.PRE_LOAD).listeners()) {
            registered |= listener == HOLD;
        }
        if (!registered) {
            registry.appendListeners(EventType.PRE_LOAD, HOLD);
        }
    }

    /**
     * Runs before the base class deletes every entity: a stale cached definition would fail that
     * delete with an optimistic-lock error and leave rows for the next test class.
     */
    @After
    public void evictCache() {
        sessionFactory.getCache().evictAllRegions();
    }

    /** What a request sees of the definition: title, localized title, parameter, preferences. */
    private static List<String> view(IPortletDefinition definition) {
        final List<String> preferences = new ArrayList<>();
        for (IPortletPreference preference : definition.getPortletPreferences()) {
            preferences.add(preference.getName() + Arrays.toString(preference.getValues()));
        }
        return Arrays.asList(
                "title=" + definition.getTitle(),
                "fr title=" + definition.getTitle("fr"),
                "parameter=" + definition.getParameter("disableDynamicTitle").getValue(),
                "preferences=" + preferences);
    }

    private long createDefinition() {
        return execute(
                () -> {
                    final IPortletType type = portletTypeDao.createPortletType("CMS", "/cms.xml");
                    final PortletDefinitionImpl definition =
                            new PortletDefinitionImpl(
                                    type,
                                    "cache-race",
                                    "Cache race",
                                    "Old title",
                                    "/SimpleContentPortlet",
                                    "cms",
                                    false);
                    definition.addLocalizedTitle("fr", "Ancien titre");
                    definition.addParameter("disableDynamicTitle", "old");
                    final List<IPortletPreference> preferences = new ArrayList<>();
                    preferences.add(new PortletPreferenceImpl("content", false, "old content"));
                    definition.setPortletPreferences(preferences);
                    portletDefinitionDao.savePortletDefinition(definition);
                    return definition.getPortletDefinitionId().getLongId();
                });
    }

    /** The re-import: the same changes PortletDefinitionImporterExporter makes, in one commit. */
    private void reImport(long id) {
        final EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            final PortletDefinitionImpl definition = em.find(PortletDefinitionImpl.class, id);
            definition.setTitle("New title");
            definition.addLocalizedTitle("fr", "Nouveau titre");
            definition.addParameter("disableDynamicTitle", "new");
            final List<IPortletPreference> preferences = new ArrayList<>();
            preferences.add(new PortletPreferenceImpl("content", false, "new content"));
            preferences.add(new PortletPreferenceImpl("added", false, "by the re-import"));
            definition.setPortletPreferences(preferences);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    @Test
    public void aLoadThatReadTheRowBeforeAReImportDoesNotCacheTheOldDefinition() throws Exception {
        final long id = createDefinition();
        // The reader has to read the rows, as after a restart or an eviction
        sessionFactory.getCache().evictAllRegions();

        final AtomicReference<List<String>> readerSaw = new AtomicReference<>();
        final Thread reader =
                new Thread(
                        () -> {
                            final EntityManager em = emf.createEntityManager();
                            try {
                                readerSaw.set(view(em.find(PortletDefinitionImpl.class, id)));
                            } finally {
                                em.close();
                            }
                        },
                        "cache-race-reader");
        HOLD.holdNextLoadOf(reader);
        reader.start();
        assertTrue(
                "the reader read the definition", HOLD.read.await(WAIT_SECONDS, TimeUnit.SECONDS));

        reImport(id);
        HOLD.release.countDown();
        reader.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        assertEquals(
                "what the reader read, before the re-import",
                Arrays.asList(
                        "title=Old title",
                        "fr title=Ancien titre",
                        "parameter=old",
                        "preferences=[content[old content]]"),
                readerSaw.get());

        // The next request reads through the cache, and saves the definition again
        final EntityManager next = emf.createEntityManager();
        try {
            next.getTransaction().begin();
            final PortletDefinitionImpl definition = next.find(PortletDefinitionImpl.class, id);
            assertEquals(
                    "what the next request sees after the re-import",
                    Arrays.asList(
                            "title=New title",
                            "fr title=Nouveau titre",
                            "parameter=new",
                            "preferences=[content[new content], added[by the re-import]]"),
                    view(definition));
            // An admin's next save: both version checks must match the rows
            definition.setTitle("Saved again");
            final List<IPortletPreference> preferences = new ArrayList<>();
            preferences.add(new PortletPreferenceImpl("content", false, "saved again"));
            preferences.add(new PortletPreferenceImpl("added", false, "by the re-import"));
            definition.setPortletPreferences(preferences);
            next.getTransaction().commit();
        } finally {
            if (next.getTransaction().isActive()) {
                next.getTransaction().rollback();
            }
            next.close();
        }
    }
}
