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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.MergeView;
import org.jgroups.View;
import org.jgroups.ViewId;
import org.jgroups.stack.Protocol;
import org.jgroups.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/**
 * After a network split heals, JGroups installs a MergeView. Each node then empties its caches,
 * since invalidations sent while the nodes were split never reached it. A plain view change, such
 * as a node joining, clears nothing.
 */
public class MergeViewCacheClearerTest {

    private ConcurrentMapCacheManager cacheManager;
    private MergeViewCacheClearer clearer;
    private final List<MergeView> merges = new ArrayList<>();
    private final Consumer<MergeView> recorder = merges::add;

    @Before
    public void setUp() {
        cacheManager = new ConcurrentMapCacheManager("portlets", "permissions");
        cacheManager.getCache("portlets").put("k", "v");
        cacheManager.getCache("permissions").put("k", "v");
        clearer = new MergeViewCacheClearer();
        clearer.setCacheManager(cacheManager);
        MergeViewNotifier.addListener(recorder);
    }

    @After
    public void tearDown() {
        MergeViewNotifier.removeListener(recorder);
        clearer.destroy();
    }

    @Test
    public void emptiesEveryCache() {
        final int cleared = clearer.clearCaches(mergeView());

        assertEquals(2, cleared);
        assertNull(cacheManager.getCache("portlets").get("k"));
        assertNull(cacheManager.getCache("permissions").get("k"));
    }

    @Test
    public void leavesTheExcludedCachesAlone() {
        clearer.setExcludedCacheNames(new HashSet<>(Arrays.asList("permissions")));

        final int cleared = clearer.clearCaches(mergeView());

        assertEquals(1, cleared);
        assertNull(cacheManager.getCache("portlets").get("k"));
        assertEquals("v", cacheManager.getCache("permissions").get("k").get());
    }

    @Test
    public void theNotifierReportsAMergeViewAndPassesItUp() {
        final Protocol above = mock(Protocol.class);
        final MergeViewNotifier notifier = new MergeViewNotifier();
        notifier.setUpProtocol(above);
        final Event event = new Event(Event.VIEW_CHANGE, mergeView());

        notifier.up(event);

        assertEquals(1, merges.size());
        assertSame(event.getArg(), merges.get(0));
        verify(above).up(event);
    }

    @Test
    public void aPlainViewClearsNothing() {
        final Protocol above = mock(Protocol.class);
        final MergeViewNotifier notifier = new MergeViewNotifier();
        notifier.setUpProtocol(above);
        final Address a = member();
        final Event event =
                new Event(
                        Event.VIEW_CHANGE, new View(new ViewId(a, 2), Arrays.asList(a, member())));

        notifier.up(event);

        assertEquals(0, merges.size());
        verify(above).up(event);
    }

    @Test
    public void otherEventsPassUpUnchanged() {
        final Protocol above = mock(Protocol.class);
        final MergeViewNotifier notifier = new MergeViewNotifier();
        notifier.setUpProtocol(above);
        final Event event = new Event(Event.SUSPECT, member());

        notifier.up(event);

        assertEquals(0, merges.size());
        verify(above).up(event);
    }

    @Test
    public void switchedOffItListensToNoMerge() throws Exception {
        MergeViewNotifier.removeListener(recorder);
        clearer.setEnabled(false);
        clearer.afterPropertiesSet();
        final MergeViewNotifier notifier = new MergeViewNotifier();
        notifier.setUpProtocol(mock(Protocol.class));

        notifier.up(new Event(Event.VIEW_CHANGE, mergeView()));

        assertEquals("v", cacheManager.getCache("portlets").get("k").get());
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
