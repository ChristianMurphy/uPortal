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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import org.apereo.portal.IUserProfile;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.utils.cache.TagTrackingCacheEventListener;
import org.apereo.portal.utils.cache.UsernameTaggedCacheEntryPurger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.ehcache.EhCacheCacheManager;

/**
 * Behaviour of the layout cache on a real Ehcache, with the tag listener ehcache.xml attaches to
 * it: hits keyed by username and profile, eviction when a layout changes, and the logout purge.
 */
public class LayoutCachingServiceTest {

    private CacheManager ehcacheManager;
    private Ehcache ehcache;
    private TagTrackingCacheEventListener tagTracker;
    private LayoutCachingService service;

    @Before
    public void setUp() {
        ehcacheManager =
                new CacheManager(
                        new Configuration().name("LayoutCachingServiceTest-" + UUID.randomUUID()));
        ehcacheManager.addCache(
                new net.sf.ehcache.Cache(
                        new CacheConfiguration(LayoutCachingService.LAYOUT_CACHE_NAME, 100)));
        ehcache = ehcacheManager.getEhcache(LayoutCachingService.LAYOUT_CACHE_NAME);
        tagTracker = new TagTrackingCacheEventListener();
        ehcache.getCacheEventNotificationService().registerListener(tagTracker);

        final EhCacheCacheManager springCacheManager = new EhCacheCacheManager(ehcacheManager);
        springCacheManager.afterPropertiesSet();
        service = new LayoutCachingService();
        service.setCacheManager(springCacheManager);
    }

    @After
    public void tearDown() {
        ehcacheManager.shutdown();
    }

    private static IPerson person(String userName) {
        final IPerson person = mock(IPerson.class);
        when(person.getUserName()).thenReturn(userName);
        return person;
    }

    private static IUserProfile profile(int id) {
        final IUserProfile profile = mock(IUserProfile.class);
        when(profile.getProfileId()).thenReturn(id);
        return profile;
    }

    @Test
    public void cachedLayoutIsFoundForAnEqualUserAndProfile() {
        final DistributedUserLayout layout = new DistributedUserLayout(null);
        service.cacheLayout(person("student"), profile(1), layout);

        assertSame(layout, service.getCachedLayout(person("student"), profile(1)));
        assertNull(service.getCachedLayout(person("student"), profile(2)));
        assertNull(service.getCachedLayout(person("staff"), profile(1)));
    }

    @Test
    public void removeCachedLayoutEvictsOnlyThatLayout() {
        service.cacheLayout(person("student"), profile(1), new DistributedUserLayout(null));
        service.cacheLayout(person("student"), profile(2), new DistributedUserLayout(null));

        service.removeCachedLayout(person("student"), profile(1));

        assertNull(service.getCachedLayout(person("student"), profile(1)));
        assertEquals(1, ehcache.getSize());
    }

    @Test
    public void logoutPurgeEvictsTheUsersLayouts() {
        service.cacheLayout(person("student"), profile(1), new DistributedUserLayout(null));
        service.cacheLayout(person("student"), profile(2), new DistributedUserLayout(null));
        final DistributedUserLayout staffLayout = new DistributedUserLayout(null);
        service.cacheLayout(person("staff"), profile(1), staffLayout);

        final int purged =
                tagTracker.purgeCacheEntries(
                        UsernameTaggedCacheEntryPurger.createCacheEntryTag("student"));

        assertEquals(2, purged);
        assertNull(service.getCachedLayout(person("student"), profile(1)));
        assertSame(staffLayout, service.getCachedLayout(person("staff"), profile(1)));
    }

    @Test(expected = IllegalStateException.class)
    public void missingLayoutCacheFailsFast() {
        final EhCacheCacheManager empty =
                new EhCacheCacheManager(
                        new CacheManager(
                                new Configuration()
                                        .name(
                                                "LayoutCachingServiceTest-empty-"
                                                        + UUID.randomUUID())));
        empty.afterPropertiesSet();
        try {
            new LayoutCachingService().setCacheManager(empty);
        } finally {
            empty.getCacheManager().shutdown();
        }
    }
}
