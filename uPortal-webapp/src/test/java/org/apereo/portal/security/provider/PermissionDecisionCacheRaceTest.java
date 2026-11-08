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
package org.apereo.portal.security.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import net.sf.ehcache.config.FactoryConfiguration;
import net.sf.ehcache.distribution.CacheReplicator;
import net.sf.ehcache.event.CacheEventListenerAdapter;
import net.sf.ehcache.event.NotificationScope;
import net.sf.ehcache.util.PropertyUtil;
import org.apereo.portal.concurrency.caching.MapBackedEntityCache;
import org.apereo.portal.groups.IGroupMember;
import org.apereo.portal.permission.IPermissionActivity;
import org.apereo.portal.permission.IPermissionOwner;
import org.apereo.portal.permission.dao.IPermissionOwnerDao;
import org.apereo.portal.permission.target.IPermissionTarget;
import org.apereo.portal.permission.target.IPermissionTarget.TargetType;
import org.apereo.portal.permission.target.IPermissionTargetProvider;
import org.apereo.portal.permission.target.IPermissionTargetProviderRegistry;
import org.apereo.portal.security.IAuthorizationPrincipal;
import org.apereo.portal.security.IPermission;
import org.apereo.portal.security.IPermissionSet;
import org.apereo.portal.security.IPermissionStore;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.utils.cache.MapCacheProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.cache.Cache;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A permission check that runs while a permission changes must not leave the old decision in the
 * caches, on the node that made the change or on another one.
 *
 * <p>Two nodes share one permission store. Each node has the caches of uPortal's ehcache.xml with
 * JGroups taken out, and the real {@link AuthorizationImpl} and {@link
 * AnyUnblockedGrantPermissionPolicy}. Node A makes the change. In place of each JGroups replicator,
 * {@link Replication} records the removals ehcache.xml has JGroups replicate, in the order JGroups
 * sends them, and the test applies them to node B the way JGroups' receiver does: one at a time, in
 * that order, without notifying the replicators. The decision caches replicate removals only, so
 * these removals are all node B learns of the change.
 *
 * <p>Each test runs for a grant and for a revocation of the student's SUBSCRIBE permission on a
 * portlet, as uPortal-start's cluster test makes them. The student's permission set (the {@code
 * IPermissionSet} cache) is cached through {@link MapBackedEntityCache} over the node's cache, as
 * the portal's entity caching service does; the test binds it to the node instead of the portal's
 * static lookup.
 */
@RunWith(Parameterized.class)
public class PermissionDecisionCacheRaceTest {

    private static final long WAIT_SECONDS = 30;

    private static final String OWNER = IPermission.PORTAL_SUBSCRIBE;
    private static final String ACTIVITY = IPermission.PORTLET_SUBSCRIBER_ACTIVITY;
    private static final String TARGET = IPermission.PORTLET_PREFIX + "42";
    private static final String TARGET_PROVIDER = "portlets";
    private static final String STUDENT = "student";
    private static final String STUDENT_PRINCIPAL = "2." + STUDENT;

    /** The change node A makes. */
    private enum Change {
        GRANT,
        REVOKE
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> changes() {
        return Arrays.asList(new Object[][] {{Change.GRANT}, {Change.REVOKE}});
    }

    @Rule public final TemporaryFolder diskStores = new TemporaryFolder();

    private final Change change;
    private final boolean before;
    private final boolean after;

    private final PermissionStore store = new PermissionStore();
    private final IAuthorizationPrincipal student = mock(IAuthorizationPrincipal.class);
    private final IPermission grant = new PermissionImpl(OWNER);
    private Replication replication;
    private Node a;
    private Node b;

    public PermissionDecisionCacheRaceTest(Change change) {
        this.change = change;
        this.before = change == Change.REVOKE;
        this.after = change == Change.GRANT;
    }

    @Before
    public void setUp() throws IOException {
        when(student.getKey()).thenReturn(STUDENT);
        when(student.getType()).thenReturn((Class) IPerson.class);
        when(student.getPrincipalString()).thenReturn(STUDENT_PRINCIPAL);
        when(student.isGroup()).thenReturn(false);
        grant.setActivity(ACTIVITY);
        grant.setPrincipal(STUDENT_PRINCIPAL);
        grant.setTarget(TARGET);
        grant.setType(IPermission.PERMISSION_TYPE_GRANT);
        if (change == Change.REVOKE) {
            store.add(grant);
        }

        final Configuration ehcacheA = ehcacheXml("a");
        final Map<String, Properties> replicated = takeOutJGroups(ehcacheA);
        final Configuration ehcacheB = ehcacheXml("b");
        takeOutJGroups(ehcacheB);
        a = new Node(ehcacheA);
        b = new Node(ehcacheB);
        replication = new Replication(replicated);
        replication.registerOn(a);
        // What node B sends goes nowhere: the tests change permissions on node A only
        new Replication(replicated).registerOn(b);
    }

    @After
    public void tearDown() {
        store.inSelect.release();
        for (Node node : new Node[] {a, b}) {
            if (node != null) {
                node.decisions.inPut.release();
                node.ehcache.shutdown();
            }
        }
    }

    /** uPortal's ehcache.xml without its peer provider, so no JGroups channel. */
    private Configuration ehcacheXml(String node) throws IOException {
        final Configuration configuration =
                ConfigurationFactory.parseConfiguration(
                        getClass().getResource("/properties/ehcache.xml"));
        configuration.setName(getClass().getSimpleName() + "-" + node + "-" + UUID.randomUUID());
        configuration.getDiskStoreConfiguration().setPath(diskStores.newFolder().getPath());
        configuration.getCacheManagerPeerProviderFactoryConfiguration().clear();
        configuration.getCacheManagerPeerListenerFactoryConfigurations().clear();
        assertTrue(configuration.getCacheManagerPeerProviderFactoryConfiguration().isEmpty());
        return configuration;
    }

    /**
     * Takes the JGroups replicators out of the configuration, and returns, for each cache with one,
     * whether it replicates removals and how it replicates updates.
     */
    private static Map<String, Properties> takeOutJGroups(Configuration configuration) {
        final Map<String, Properties> replicated = new HashMap<>();
        for (CacheConfiguration cache : configuration.getCacheConfigurations().values()) {
            for (Iterator<?> listeners = cache.getCacheEventListenerConfigurations().iterator();
                    listeners.hasNext(); ) {
                final FactoryConfiguration<?> listener = (FactoryConfiguration<?>) listeners.next();
                if (listener.getFullyQualifiedClassPath().contains(".jgroups.")) {
                    replicated.put(
                            cache.getName(),
                            PropertyUtil.parseProperties(
                                    listener.getProperties(), listener.getPropertySeparator()));
                    listeners.remove();
                }
            }
        }
        for (String cache :
                new String[] {
                    AuthorizationImpl.PRINCIPAL_HAS_PERMISSION_CACHE_NAME,
                    AnyUnblockedGrantPermissionPolicy.HAS_UNBLOCKED_GRANT_CACHE_NAME,
                    IPermissionSet.class.getName()
                }) {
            assertTrue("ehcache.xml replicates " + cache, replicated.containsKey(cache));
        }
        // JGroups sends the removals of the three caches in the order they were made only when they
        // share one queue: all asynchronous with one interval (JGroupsCachePeer keys its queues by
        // the interval, and sends a synchronous event at once)
        final Properties decisions =
                replicated.get(AuthorizationImpl.PRINCIPAL_HAS_PERMISSION_CACHE_NAME);
        for (String cache :
                new String[] {
                    AnyUnblockedGrantPermissionPolicy.HAS_UNBLOCKED_GRANT_CACHE_NAME,
                    IPermissionSet.class.getName()
                }) {
            for (String[] setting :
                    new String[][] {
                        {"replicateAsynchronously", "true"},
                        {"asynchronousReplicationIntervalMillis", "1000"}
                    }) {
                assertEquals(
                        cache + " " + setting[0] + " as PRINCIPAL_HAS_PERMISSION",
                        setting(decisions, setting[0], setting[1]),
                        setting(replicated.get(cache), setting[0], setting[1]));
            }
        }
        for (String cache :
                new String[] {
                    AuthorizationImpl.PRINCIPAL_HAS_PERMISSION_CACHE_NAME,
                    AnyUnblockedGrantPermissionPolicy.HAS_UNBLOCKED_GRANT_CACHE_NAME,
                    IPermissionSet.class.getName()
                }) {
            assertEquals(
                    cache + " replicates removals",
                    "true",
                    setting(replicated.get(cache), "replicateRemovals", "true"));
        }
        return replicated;
    }

    /** A replicator setting, or JGroupsCacheReplicatorFactory's default; it trims the values. */
    private static String setting(Properties replicator, String name, String byDefault) {
        return replicator.getProperty(name, byDefault).trim().toLowerCase(Locale.ROOT);
    }

    /** One portal node: its caches, its AuthorizationImpl and its permission policy. */
    private final class Node {
        private final CacheManager ehcache;
        private final AuthorizationImpl authorization;
        private final DecisionCache decisions;

        Node(Configuration configuration) {
            ehcache = new CacheManager(configuration);
            final EhCacheCacheManager cacheManager = new EhCacheCacheManager(ehcache);
            cacheManager.afterPropertiesSet();
            final MapBackedEntityCache permissionSets =
                    new MapBackedEntityCache(
                            new MapCacheProvider<>(
                                    ehcache.getEhcache(IPermissionSet.class.getName())),
                            IPermissionSet.class);

            final IPermissionOwnerDao owners = owners();
            final IPermissionTargetProviderRegistry targets = targets();
            final AnyUnblockedGrantPermissionPolicy policy =
                    new AnyUnblockedGrantPermissionPolicy();
            policy.setCacheManager(cacheManager);
            ReflectionTestUtils.setField(policy, "permissionOwnerDao", owners);
            ReflectionTestUtils.setField(policy, "targetProviderRegistry", targets);

            final IGroupMember noGroups = mock(IGroupMember.class);
            when(noGroups.getParentGroups()).thenReturn(Collections.emptySet());
            authorization =
                    new AuthorizationImpl() {
                        // The portal looks these up through static locators

                        @Override
                        public String getPrincipalString(IAuthorizationPrincipal principal) {
                            return principal.getPrincipalString();
                        }

                        @Override
                        public IAuthorizationPrincipal getPrincipal(IPermission permission) {
                            assertEquals(STUDENT_PRINCIPAL, permission.getPrincipal());
                            return student;
                        }

                        @Override
                        public IGroupMember getGroupMember(IAuthorizationPrincipal principal) {
                            return noGroups;
                        }

                        @Override
                        protected IPermissionSet cacheGet(IAuthorizationPrincipal principal) {
                            return (IPermissionSet)
                                    permissionSets.get(principal.getPrincipalString());
                        }

                        @Override
                        protected void cacheAdd(IPermissionSet permissionSet) {
                            permissionSets.add(permissionSet);
                        }

                        @Override
                        protected void cacheRemove(IAuthorizationPrincipal principal) {
                            permissionSets.remove(principal.getPrincipalString());
                        }
                    };
            authorization.setCacheManager(cacheManager);
            decisions =
                    new DecisionCache(
                            cacheManager.getCache(
                                    AuthorizationImpl.PRINCIPAL_HAS_PERMISSION_CACHE_NAME));
            authorization.setDoesPrincipalHavePermissionCache(decisions);
            authorization.setPermissionStore(store);
            authorization.setCachePermissions(true);
            authorization.setDefaultPermissionPolicy(policy);
            authorization.setPermissionOwner(owners);
            authorization.setNonEntityPermissionTargetProviders(
                    Collections.singleton(TARGET_PROVIDER));
            ReflectionTestUtils.setField(authorization, "permissionOwnerDao", owners);
            ReflectionTestUtils.setField(authorization, "targetProviderRegistry", targets);
        }

        /** What a request of the student's session asks: may the student subscribe? */
        boolean check() {
            return authorization.doesPrincipalHavePermission(student, OWNER, ACTIVITY, TARGET);
        }
    }

    private static IPermissionOwnerDao owners() {
        final IPermissionOwnerDao owners = mock(IPermissionOwnerDao.class);
        final Map<String, IPermissionOwner> ownerMocks = new ConcurrentHashMap<>();
        final Map<String, IPermissionActivity> activityMocks = new ConcurrentHashMap<>();
        when(owners.getPermissionOwner(anyString()))
                .thenAnswer(
                        call ->
                                ownerMocks.computeIfAbsent(
                                        call.getArgument(0),
                                        fname -> {
                                            final IPermissionOwner owner =
                                                    mock(IPermissionOwner.class);
                                            when(owner.getFname()).thenReturn(fname);
                                            return owner;
                                        }));
        when(owners.getPermissionActivity(anyString(), anyString()))
                .thenAnswer(
                        call ->
                                activityMocks.computeIfAbsent(
                                        call.getArgument(0) + "/" + call.getArgument(1),
                                        key -> {
                                            final IPermissionActivity activity =
                                                    mock(IPermissionActivity.class);
                                            when(activity.getFname())
                                                    .thenReturn((String) call.getArgument(1));
                                            when(activity.getTargetProviderKey())
                                                    .thenReturn(TARGET_PROVIDER);
                                            return activity;
                                        }));
        return owners;
    }

    private static IPermissionTargetProviderRegistry targets() {
        final Map<String, IPermissionTarget> targetMocks = new ConcurrentHashMap<>();
        final IPermissionTargetProvider provider = mock(IPermissionTargetProvider.class);
        when(provider.getTarget(anyString()))
                .thenAnswer(
                        call ->
                                targetMocks.computeIfAbsent(
                                        call.getArgument(0),
                                        key -> {
                                            final IPermissionTarget target =
                                                    mock(IPermissionTarget.class);
                                            when(target.getKey()).thenReturn(key);
                                            when(target.getTargetType())
                                                    .thenReturn(
                                                            IPermission.ALL_TARGET.equals(key)
                                                                    ? TargetType.PERSON
                                                                    : TargetType.PORTLET);
                                            return target;
                                        }));
        final IPermissionTargetProviderRegistry registry =
                mock(IPermissionTargetProviderRegistry.class);
        when(registry.getTargetProvider(any())).thenReturn(provider);
        return registry;
    }

    /** Node A makes the change, as the permissions API does. */
    private void change() {
        if (change == Change.GRANT) {
            a.authorization.addPermissions(new IPermission[] {grant});
        } else {
            a.authorization.removePermissions(new IPermission[] {grant});
        }
    }

    /**
     * The existing session on node B checked the permission before the change. The removals node A
     * sends reach node B one after the other, and the session's requests go on meanwhile.
     */
    @Test
    public void aCheckBetweenTheReplicatedRemovalsDoesNotKeepTheOldDecision() {
        assertEquals("node B before the change", before, b.check());

        change();
        final List<Removal> removals = replication.drain();
        assertFalse("node A replicated the change", removals.isEmpty());
        final List<String> seen = new ArrayList<>();
        for (Removal removal : removals) {
            removal.applyTo(b);
            seen.add(removal + " -> " + b.check());
        }

        assertEquals("node B after the removals and checks " + seen, after, b.check());
        assertCachedAgain(b, "node B");
    }

    /**
     * After a change the node caches its decisions again: the check after the first one that saw
     * the change is answered from PRINCIPAL_HAS_PERMISSION.
     */
    private void assertCachedAgain(Node node, String nodeName) {
        final int hits = node.decisions.hits.get();
        assertEquals(after, node.check());
        assertEquals(
                nodeName + " answered the next check from PRINCIPAL_HAS_PERMISSION",
                hits + 1,
                node.decisions.hits.get());
    }

    /**
     * Runs a check on the node in its own thread until the hold stops it, makes the change on node
     * A and applies the removals node A sends to node B, then lets the check go on.
     *
     * @return what the held check decided
     */
    private Boolean checkHeldDuringTheChange(Node node, Hold hold) throws InterruptedException {
        final AtomicReference<Boolean> decided = new AtomicReference<>();
        final AtomicReference<RuntimeException> failed = new AtomicReference<>();
        final Thread check =
                new Thread(
                        () -> {
                            try {
                                decided.set(node.check());
                            } catch (RuntimeException e) {
                                failed.set(e);
                                hold.reached.countDown();
                            }
                        },
                        "permission-check");
        hold.holdNextOf(check);
        check.start();
        assertTrue(
                "the check reached the hold", hold.reached.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertNull("the check failed", failed.get());

        change();
        for (Removal removal : replication.drain()) {
            removal.applyTo(b);
        }
        hold.release();
        check.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        assertFalse("the check finished", check.isAlive());
        assertNull("the check failed", failed.get());
        return decided.get();
    }

    /**
     * A check reads the student's permissions from the store, then waits while node A changes them
     * and both nodes clear their caches, then goes on. Its decision may be the old one, but the
     * next check on the same node must see the change.
     */
    private void aCheckThatReadThePermissionsBeforeTheChange(Node node, String nodeName)
            throws InterruptedException {
        final Boolean held = checkHeldDuringTheChange(node, store.inSelect);

        assertEquals(
                "the next check on " + nodeName + " (the held check decided " + held + ")",
                after,
                node.check());
        assertCachedAgain(node, nodeName);
    }

    @Test
    public void aCheckOnTheChangingNodeThatReadThePermissionsBeforeTheChangeIsNotCached()
            throws InterruptedException {
        aCheckThatReadThePermissionsBeforeTheChange(a, "node A");
    }

    @Test
    public void aCheckOnTheOtherNodeThatReadThePermissionsBeforeTheChangeIsNotCached()
            throws InterruptedException {
        aCheckThatReadThePermissionsBeforeTheChange(b, "node B");
    }

    /**
     * A check computes the old decision and finds the marker in place, then waits inside its put
     * into PRINCIPAL_HAS_PERMISSION while node A changes the permission and both nodes clear their
     * caches. Its put comes after the clear, so it has to take the decision out again.
     */
    private void aCheckHeldInItsPutDuringTheChange(Node node, String nodeName)
            throws InterruptedException {
        final Boolean held = checkHeldDuringTheChange(node, node.decisions.inPut);

        assertEquals("what the held check decided, before the change", before, held);
        assertEquals("the next check on " + nodeName, after, node.check());
        assertCachedAgain(node, nodeName);
    }

    @Test
    public void aCheckOnTheChangingNodeHeldInItsPutDoesNotKeepItsDecision()
            throws InterruptedException {
        aCheckHeldInItsPutDuringTheChange(a, "node A");
    }

    @Test
    public void aCheckOnTheOtherNodeHeldInItsPutDoesNotKeepItsDecision()
            throws InterruptedException {
        aCheckHeldInItsPutDuringTheChange(b, "node B");
    }

    /** Holds one chosen thread at a point of the code, until the test releases it. */
    private static final class Hold {
        private final AtomicReference<Thread> thread = new AtomicReference<>();
        private volatile CountDownLatch reached = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        void holdNextOf(Thread chosen) {
            reached = new CountDownLatch(1);
            release = new CountDownLatch(1);
            thread.set(chosen);
        }

        void release() {
            release.countDown();
        }

        /** Called at the point: the chosen thread waits here the first time it passes. */
        void here() {
            if (thread.compareAndSet(Thread.currentThread(), null)) {
                reached.countDown();
                try {
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * PRINCIPAL_HAS_PERMISSION as AuthorizationImpl sees it: counts the hits, and can hold a thread
     * inside its next put, before the value reaches the cache.
     */
    private static final class DecisionCache implements Cache {
        private final Cache cache;
        private final Hold inPut = new Hold();
        private final AtomicInteger hits = new AtomicInteger();

        DecisionCache(Cache cache) {
            this.cache = cache;
        }

        @Override
        public String getName() {
            return cache.getName();
        }

        @Override
        public Object getNativeCache() {
            return cache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            return cache.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            final T value = cache.get(key, type);
            if (value != null) {
                hits.incrementAndGet();
            }
            return value;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            return cache.get(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            inPut.here();
            cache.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            return cache.putIfAbsent(key, value);
        }

        @Override
        public void evict(Object key) {
            cache.evict(key);
        }

        @Override
        public void clear() {
            cache.clear();
        }
    }

    /** One removal JGroups sends to the other nodes. */
    private static final class Removal {
        private final String cache;
        private final Serializable key; // null: every entry

        Removal(String cache, Serializable key) {
            this.cache = cache;
            this.key = key;
        }

        /** What JGroupsCacheReceiver does with it. */
        void applyTo(Node node) {
            final Ehcache target = node.ehcache.getEhcache(cache);
            if (key == null) {
                target.removeAll(true);
            } else if (target.getQuiet(key) != null) {
                target.remove(key, true);
            }
        }

        @Override
        public String toString() {
            return (key == null ? "removeAll " : "remove " + key + " from ")
                    + cache.substring(cache.lastIndexOf('.') + 1);
        }
    }

    /**
     * Stands in for the JGroups replicator of each cache ehcache.xml replicates, and records the
     * events it would send, in the order it would queue them: removals where ehcache.xml sets
     * replicateRemovals, and updates as removals where it replicates updates by invalidation. No
     * cache here replicates puts or copies. Like the portal's caches, a cache with a replicator
     * refuses putIfAbsent and the other compare-and-set operations.
     */
    private static final class Replication extends CacheEventListenerAdapter
            implements CacheReplicator {
        private final Map<String, Properties> replicated;
        private final List<Removal> sent = new CopyOnWriteArrayList<>();

        Replication(Map<String, Properties> replicated) {
            this.replicated = replicated;
        }

        void registerOn(Node node) {
            for (String cache : replicated.keySet()) {
                final Ehcache ehcache = node.ehcache.getEhcache(cache);
                ehcache.getCacheEventNotificationService()
                        .registerListener(this, NotificationScope.LOCAL);
                assertTrue(ehcache.getCacheEventNotificationService().hasCacheReplicators());
            }
        }

        @Override
        public boolean isReplicateUpdatesViaCopy() {
            return false;
        }

        @Override
        public boolean notAlive() {
            return false;
        }

        @Override
        public boolean alive() {
            return true;
        }

        synchronized List<Removal> drain() {
            final List<Removal> removals = new ArrayList<>(sent);
            sent.clear();
            return removals;
        }

        private boolean replicates(Ehcache cache, String property) {
            // JGroupsCacheReplicatorFactory's defaults, and it trims the values
            return PropertyUtil.parseBoolean(
                    replicated.get(cache.getName()).getProperty(property, "true").trim());
        }

        @Override
        public void notifyElementRemoved(Ehcache cache, Element element) {
            if (replicates(cache, "replicateRemovals")) {
                sent.add(new Removal(cache.getName(), copy(element.getObjectKey())));
            }
        }

        @Override
        public void notifyElementUpdated(Ehcache cache, Element element) {
            if (replicates(cache, "replicateUpdates")) {
                assertFalse(replicates(cache, "replicateUpdatesViaCopy"));
                sent.add(new Removal(cache.getName(), copy(element.getObjectKey())));
            }
        }

        @Override
        public void notifyElementPut(Ehcache cache, Element element) {
            assertFalse(cache.getName() + " replicates puts", replicates(cache, "replicatePuts"));
        }

        @Override
        public void notifyRemoveAll(Ehcache cache) {
            if (replicates(cache, "replicateRemovals")) {
                sent.add(new Removal(cache.getName(), null));
            }
        }

        /** The key as the other node receives it: serialized and read back. */
        private static Serializable copy(Object key) {
            try {
                final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                    out.writeObject(key);
                }
                try (ObjectInputStream in =
                        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                    return (Serializable) in.readObject();
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new AssertionError("JGroups cannot send the key " + key, e);
            }
        }
    }

    /**
     * The shared database. A chosen thread is held in its next select, after it read the rows,
     * until the test releases it.
     */
    private static final class PermissionStore implements IPermissionStore {
        private final List<IPermission> rows = new CopyOnWriteArrayList<>();
        private final Hold inSelect = new Hold();

        @Override
        public IPermission[] select(
                String owner, String principal, String activity, String target, String type) {
            final List<IPermission> selected = new ArrayList<>();
            for (IPermission row : rows) {
                if ((owner == null || owner.equals(row.getOwner()))
                        && (principal == null || principal.equals(row.getPrincipal()))
                        && (activity == null || activity.equals(row.getActivity()))
                        && (target == null || target.equals(row.getTarget()))
                        && (type == null || type.equals(row.getType()))) {
                    selected.add(row);
                }
            }
            inSelect.here();
            return selected.toArray(new IPermission[0]);
        }

        @Override
        public void add(IPermission[] perms) {
            rows.addAll(Arrays.asList(perms));
        }

        @Override
        public void add(IPermission perm) {
            rows.add(perm);
        }

        @Override
        public void delete(IPermission[] perms) {
            rows.removeAll(Arrays.asList(perms));
        }

        @Override
        public void delete(IPermission perm) {
            rows.remove(perm);
        }

        @Override
        public IPermission newInstance(String owner) {
            return new PermissionImpl(owner);
        }

        @Override
        public void update(IPermission[] perms) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(IPermission perm) {
            throw new UnsupportedOperationException();
        }
    }
}
