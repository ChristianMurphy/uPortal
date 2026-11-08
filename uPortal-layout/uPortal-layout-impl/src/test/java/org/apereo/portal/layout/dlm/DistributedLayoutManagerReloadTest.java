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
package org.apereo.portal.layout.dlm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import org.apereo.portal.IUserProfile;
import org.apereo.portal.layout.IUserLayoutStore;
import org.apereo.portal.security.IAuthorizationService;
import org.apereo.portal.security.IPerson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * How a session reloads its layout after the cache entry is gone. The layout cache is shared by all
 * sessions of a user, and every sign-in evicts it, so each other session of the user reloads at its
 * next request (MOD-I363).
 *
 * <p>The store here does what RDBMDistributedLayoutStore does with the PLF: it uses the copy on the
 * person if there is one, and reads the saved one otherwise. Then it merges, which edits the PLF in
 * place (PLFIntegrator removes the position set it has applied), and saves the PLF when the merge
 * changed it.
 */
public class DistributedLayoutManagerReloadTest {
    private static final String USER_NAME = "student";

    private CacheManager ehcacheManager;
    private LayoutCachingService cachingService;
    private IUserLayoutStore store;
    private IUserProfile profile;

    /** The PLF in the database. {@code null} after a reset: the next read gets the default. */
    private volatile Document savedPlf;

    /** Runs in the store between the merge's lookup of the position set and its removal. */
    private volatile Runnable inMerge = () -> {};

    @Before
    public void setUp() {
        ehcacheManager =
                new CacheManager(
                        new Configuration()
                                .name("DistributedLayoutManagerReloadTest-" + UUID.randomUUID()));
        ehcacheManager.addCache(
                new net.sf.ehcache.Cache(
                        new CacheConfiguration(LayoutCachingService.LAYOUT_CACHE_NAME, 100)));
        final EhCacheCacheManager springCacheManager = new EhCacheCacheManager(ehcacheManager);
        springCacheManager.afterPropertiesSet();
        cachingService = new LayoutCachingService();
        cachingService.setCacheManager(springCacheManager);

        profile = mock(IUserProfile.class);
        when(profile.getProfileId()).thenReturn(1);

        store = mock(IUserLayoutStore.class);
        doAnswer(invocation -> load(invocation.getArgument(0)))
                .when(store)
                .getUserLayout(any(IPerson.class), any(IUserProfile.class));
        doAnswer(
                        invocation -> {
                            final IPerson person = invocation.getArgument(0);
                            savedPlf = copy((Document) person.getAttribute(Constants.PLF));
                            return null;
                        })
                .when(store)
                .setUserLayout(
                        any(IPerson.class),
                        any(IUserProfile.class),
                        any(Document.class),
                        anyBoolean());

        savedPlf = layout("Welcome");
    }

    @After
    public void tearDown() {
        ehcacheManager.shutdown();
    }

    /**
     * Two requests of one session reload at once. They must not merge into the session's PLF at the
     * same time: the second removal of the same node fails with "NOT_FOUND_ERR: An attempt is made
     * to reference a node in a context where it does not exist", and the request answers 500.
     */
    @Test
    public void testConcurrentReloadsInOneSession() throws Exception {
        final DistributedLayoutManager session = signIn();
        session.getUserLayoutDOM();
        // The session's PLF has a position set again, as after a move
        addPositionSet((Document) session.owner.getAttribute(Constants.PLF));
        savedPlf = copy((Document) session.owner.getAttribute(Constants.PLF));
        cachingService.removeCachedLayout(session.owner, profile);

        final CountDownLatch firstInMerge = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final CountDownLatch secondInMerge = new CountDownLatch(1);
        final CountDownLatch firstMerged = new CountDownLatch(1);
        final AtomicReference<Thread> first = new AtomicReference<>();
        inMerge =
                () -> {
                    try {
                        if (Thread.currentThread() == first.get()) {
                            firstInMerge.countDown();
                            assertTrue(releaseFirst.await(30, TimeUnit.SECONDS));
                        } else {
                            secondInMerge.countDown();
                            assertTrue(firstMerged.await(30, TimeUnit.SECONDS));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                };

        final AtomicReference<Throwable> errorOfFirst = new AtomicReference<>();
        final AtomicReference<Throwable> errorOfSecond = new AtomicReference<>();
        final Thread threadA = reload(session, errorOfFirst, firstMerged);
        first.set(threadA);
        threadA.start();
        assertTrue("A is in its merge", firstInMerge.await(30, TimeUnit.SECONDS));

        final Thread threadB = reload(session, errorOfSecond, null);
        threadB.start();
        // B either reaches the merge too, or waits for A's reload to finish
        while (secondInMerge.getCount() > 0 && threadB.getState() != Thread.State.BLOCKED) {
            Thread.onSpinWait();
        }

        releaseFirst.countDown();
        threadA.join(TimeUnit.SECONDS.toMillis(30));
        threadB.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(threadA.isAlive());
        assertFalse(threadB.isAlive());
        if (errorOfFirst.get() != null) {
            throw new AssertionError("The first reload failed", errorOfFirst.get());
        }
        if (errorOfSecond.get() != null) {
            throw new AssertionError("The second reload failed", errorOfSecond.get());
        }
    }

    /**
     * Session 2 signed in before session 1 added a tab. A third sign-in evicts the cache, and
     * session 2 reloads before the third session loads. The tab must still be saved afterwards.
     */
    @Test
    public void testTabAddedInAnotherSessionSurvivesAReload() {
        final DistributedLayoutManager session2 = signIn();
        session2.getUserLayoutDOM();
        final DistributedLayoutManager session1 = signIn();
        session1.getUserLayoutDOM();

        addTab((Document) session1.owner.getAttribute(Constants.PLF), "Reset test");
        session1.saveUserLayout();
        // A third sign-in evicts the cache, and session 2's next request comes first
        signIn();
        session2.getUserLayoutDOM();

        assertEquals(List.of("Welcome", "Reset test"), tabNames(savedPlf));
        assertEquals(
                List.of("Welcome", "Reset test"),
                tabNames((Document) session2.owner.getAttribute(Constants.PLF)));
    }

    /**
     * Session 2 holds a customized layout when session 1 resets it. A sign-in evicts the cache, and
     * session 2 reloads. The reset must hold.
     */
    @Test
    public void testResetHoldsAfterAnotherSessionReloads() {
        addTab(savedPlf, "Reset test");
        final DistributedLayoutManager session2 = signIn();
        session2.getUserLayoutDOM();

        // The reset: the next read gets the default layout
        savedPlf = null;
        signIn().getUserLayoutDOM();
        cachingService.removeCachedLayout(session2.owner, profile);
        session2.getUserLayoutDOM();

        assertEquals(List.of("Welcome"), tabNames(savedPlf));
    }

    /**
     * A session changes its PLF, and a sign-in evicts the cache before the session saves. The save
     * must still write the change.
     */
    @Test
    public void testSaveAfterAnEvictionKeepsTheChange() {
        final DistributedLayoutManager session = signIn();
        session.getUserLayoutDOM();

        addTab((Document) session.owner.getAttribute(Constants.PLF), "Reset test");
        cachingService.removeCachedLayout(session.owner, profile);
        session.saveUserLayout();

        assertEquals(List.of("Welcome", "Reset test"), tabNames(savedPlf));
    }

    /**
     * A new session of the user: its sign-in drops the cached layout, as afterPropertiesSet does.
     */
    private DistributedLayoutManager signIn() {
        final IPerson person = person();
        final DistributedLayoutManager manager = new DistributedLayoutManager(person, profile);
        manager.setLayoutCachingService(cachingService);
        manager.setDistributedLayoutStore(store);
        manager.setAuthorizationService(mock(IAuthorizationService.class));
        cachingService.removeCachedLayout(person, profile);
        return manager;
    }

    private static Thread reload(
            DistributedLayoutManager session,
            AtomicReference<Throwable> error,
            CountDownLatch done) {
        return new Thread(
                () -> {
                    try {
                        session.getUserLayoutDOM();
                    } catch (Throwable t) {
                        error.set(t);
                    } finally {
                        if (done != null) {
                            done.countDown();
                        }
                    }
                },
                "DistributedLayoutManagerReloadTest-reload");
    }

    /** What RDBMDistributedLayoutStore.getUserLayout does with the person's PLF. */
    private DistributedUserLayout load(IPerson person) {
        Document plf = (Document) person.getAttribute(Constants.PLF);
        if (plf == null) {
            plf = savedPlf == null ? layout("Welcome") : copy(savedPlf);
            person.setAttribute(Constants.PLF, plf);
        }
        // The merge, which edits the PLF in place and saves it when it changed
        final Element root = plf.getDocumentElement();
        final Node positionSet = root.getElementsByTagName(Constants.ELM_POSITION_SET).item(0);
        inMerge.run();
        if (positionSet != null) {
            root.removeChild(positionSet);
        }
        savedPlf = copy(plf);
        return new DistributedUserLayout(copy(plf));
    }

    /** A person whose attributes live in a map, as PersonImpl keeps them. */
    private static IPerson person() {
        final Map<String, Object> attributes = new ConcurrentHashMap<>();
        final IPerson person = mock(IPerson.class);
        when(person.getUserName()).thenReturn(USER_NAME);
        when(person.getAttribute(anyString()))
                .thenAnswer(invocation -> attributes.get(invocation.<String>getArgument(0)));
        doAnswer(
                        invocation -> {
                            attributes.put(invocation.getArgument(0), invocation.getArgument(1));
                            return null;
                        })
                .when(person)
                .setAttribute(anyString(), any(Object.class));
        doAnswer(
                        invocation -> {
                            final List<?> values = invocation.getArgument(1);
                            if (values == null) {
                                attributes.remove(invocation.<String>getArgument(0));
                            } else {
                                attributes.put(invocation.getArgument(0), values.get(0));
                            }
                            return null;
                        })
                .when(person)
                .setAttribute(anyString(), anyList());
        doAnswer(
                        invocation -> {
                            attributes.remove(invocation.<String>getArgument(0));
                            return null;
                        })
                .when(person)
                .setAttribute(anyString(), ArgumentMatchers.<List<Object>>isNull());
        return person;
    }

    private static Document layout(String... tabs) {
        final Document document;
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        document.appendChild(document.createElement("layout"));
        for (String tab : tabs) {
            addTab(document, tab);
        }
        return document;
    }

    private static void addTab(Document plf, String name) {
        final Element folder = plf.createElement("folder");
        folder.setAttribute("name", name);
        plf.getDocumentElement().appendChild(folder);
    }

    private static void addPositionSet(Document plf) {
        plf.getDocumentElement().appendChild(plf.createElement(Constants.ELM_POSITION_SET));
    }

    private static List<String> tabNames(Document plf) {
        final List<String> names = new ArrayList<>();
        final NodeList folders = plf.getElementsByTagName("folder");
        for (int i = 0; i < folders.getLength(); i++) {
            names.add(((Element) folders.item(i)).getAttribute("name"));
        }
        return names;
    }

    private static Document copy(Document document) {
        return (Document) document.cloneNode(true);
    }
}
