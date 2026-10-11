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
package org.apereo.portal.utils.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.distribution.CacheReplicator;
import net.sf.ehcache.distribution.jgroups.JGroupsCacheManagerPeerProvider;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.MergeView;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.ViewId;
import org.jgroups.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * After a network split heals, JGroups installs a MergeView. Each node then clears the caches it
 * replicates, since invalidations sent while the nodes were split never reached it. A plain view
 * change, such as a node joining, clears nothing.
 */
public class ReplicatedCachesMergeClearerTest {

    private CacheManager ehcacheManager;
    private Ehcache replicated;
    private Ehcache local;
    private CacheReplicator replicator;
    private ReplicatedCachesMergeClearer clearer;

    @Before
    public void setUp() {
        final Configuration configuration =
                new Configuration().name("merge-clearer-" + java.util.UUID.randomUUID());
        ehcacheManager = new CacheManager(configuration);
        ehcacheManager.addCache(new net.sf.ehcache.Cache(new CacheConfiguration("replicated", 10)));
        ehcacheManager.addCache(new net.sf.ehcache.Cache(new CacheConfiguration("local", 10)));
        replicated = ehcacheManager.getEhcache("replicated");
        local = ehcacheManager.getEhcache("local");
        replicator = mock(CacheReplicator.class);
        when(replicator.isReplicateUpdatesViaCopy()).thenReturn(false);
        replicated.getCacheEventNotificationService().registerListener(replicator);
        replicated.put(new Element("k", "v"));
        local.put(new Element("k", "v"));

        clearer = new ReplicatedCachesMergeClearer();
        clearer.setCacheManager(ehcacheManager);
    }

    @After
    public void tearDown() {
        ehcacheManager.shutdown();
    }

    @Test
    public void clearsOnlyTheReplicatedCaches() {
        final int cleared = clearer.clearReplicatedCaches(mergeView());

        assertEquals(1, cleared);
        assertEquals(0, replicated.getSize());
        assertEquals(1, local.getSize());
    }

    @Test
    public void clearsWithoutTellingTheOtherNodes() {
        clearer.clearReplicatedCaches(mergeView());

        verify(replicator, never()).notifyRemoveAll(any(Ehcache.class));
    }

    @Test
    public void aMergeViewTriggersTheClearAndReachesTheLibrary() {
        final Receiver library = mock(Receiver.class);
        final List<MergeView> merges = new ArrayList<>();
        final Receiver receiver =
                new ReplicatedCachesMergeClearer.MergeAwareReceiver(library, merges::add);
        final MergeView view = mergeView();

        receiver.viewAccepted(view);

        assertEquals(Arrays.asList(view), merges);
        verify(library).viewAccepted(view);
    }

    @Test
    public void aPlainViewClearsNothing() {
        final Receiver library = mock(Receiver.class);
        final List<MergeView> merges = new ArrayList<>();
        final Receiver receiver =
                new ReplicatedCachesMergeClearer.MergeAwareReceiver(library, merges::add);
        final View view = new View(new ViewId(member(), 2), Arrays.asList(member(), member()));

        receiver.viewAccepted(view);

        assertEquals(0, merges.size());
        verify(library).viewAccepted(view);
    }

    @Test
    public void messagesStillReachTheLibrary() throws Exception {
        final Receiver library = mock(Receiver.class);
        final Receiver receiver =
                new ReplicatedCachesMergeClearer.MergeAwareReceiver(library, view -> {});
        final Message message = new Message();

        receiver.receive(message);
        receiver.block();
        receiver.unblock();

        verify(library).receive(message);
        verify(library).block();
        verify(library).unblock();
    }

    @Test
    public void findsTheChannelOfTheJGroupsPeerProvider() throws Exception {
        // A field renamed in a new ehcache-jgroups3replication would silently switch the clear off
        final JChannel channel = new JChannel(false);
        final JGroupsCacheManagerPeerProvider provider =
                new JGroupsCacheManagerPeerProvider(ehcacheManager, "");
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "channel", channel);

        assertSame(channel, ReplicatedCachesMergeClearer.channelOf(provider));
    }

    @Test
    public void withoutJGroupsThereIsNothingToHookInto() throws Exception {
        clearer.setEnabled(true);
        clearer.afterPropertiesSet();

        assertNotNull(clearer);
        assertEquals(1, replicated.getSize());
    }

    private static MergeView mergeView() {
        final Address a = member();
        final Address b = member();
        final View sideA = new View(new ViewId(a, 3), Arrays.asList(a));
        final View sideB = new View(new ViewId(b, 1), Arrays.asList(b));
        return new MergeView(new ViewId(a, 4), Arrays.asList(a, b), Arrays.asList(sideA, sideB));
    }

    private static Address member() {
        return UUID.randomUUID();
    }
}
