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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.apereo.services.persondir.IPersonAttributeDaoFilter;
import org.apereo.services.persondir.IPersonAttributes;
import org.apereo.services.persondir.support.NamedPersonImpl;
import org.apereo.services.persondir.support.SimpleUsernameAttributeProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.ehcache.EhCacheCache;

/**
 * Behaviour of the user attribute cache: hits and misses, key equality, cached null results,
 * eviction by seed and purging by username tag. Runs on a real Ehcache with the {@link
 * TagTrackingCacheEventListener} attached, as configured in ehcache.xml.
 */
public class CachingPersonAttributeDaoTest {

    private static final String CACHE_NAME = "org.apereo.services.persondir.USER_INFO.merged";

    @Mock private IPersonAttributeDao delegate;
    @Mock private IPersonAttributeDaoFilter filter;

    private CacheManager ehcacheManager;
    private Ehcache ehcache;
    private TagTrackingCacheEventListener tagTracker;
    private PersonDirectoryCacheKeyGenerator keyGenerator;
    private CachingPersonAttributeDao dao;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ehcacheManager =
                new CacheManager(
                        new Configuration()
                                .name("CachingPersonAttributeDaoTest-" + UUID.randomUUID()));
        ehcacheManager.addCache(new net.sf.ehcache.Cache(new CacheConfiguration(CACHE_NAME, 100)));
        ehcache = ehcacheManager.getEhcache(CACHE_NAME);
        tagTracker = new TagTrackingCacheEventListener();
        ehcache.getCacheEventNotificationService().registerListener(tagTracker);

        final SimpleUsernameAttributeProvider usernameAttributeProvider =
                new SimpleUsernameAttributeProvider("username");
        keyGenerator = new PersonDirectoryCacheKeyGenerator();
        keyGenerator.setUsernameAttributeProvider(usernameAttributeProvider);
        keyGenerator.setIgnoreEmptyAttributes(true);

        dao = new CachingPersonAttributeDao();
        dao.setUsernameAttributeProvider(usernameAttributeProvider);
        dao.setCacheNullResults(true);
        dao.setCacheKeyGenerator(keyGenerator);
        dao.setUserInfoCache(new EhCacheCache(ehcache));
        dao.setCachedPersonAttributesDao(delegate);

        when(delegate.getPeopleWithMultivaluedAttributes(any(), any()))
                .thenAnswer(
                        invocation -> {
                            final Map<String, List<Object>> seed = invocation.getArgument(0);
                            final List<Object> username = seed.get("username");
                            if (username == null || username.isEmpty()) {
                                return null;
                            }
                            final String name = (String) username.get(0);
                            if (name.startsWith("unknown")) {
                                return null;
                            }
                            return Collections.singleton(
                                    new NamedPersonImpl(name, Collections.emptyMap()));
                        });
    }

    @After
    public void tearDown() {
        ehcacheManager.shutdown();
    }

    private static Map<String, List<Object>> seed(String username) {
        final Map<String, List<Object>> seed = new HashMap<>();
        seed.put("username", Collections.singletonList(username));
        return seed;
    }

    @Test
    public void missThenHitForEqualSeeds() {
        final Set<IPersonAttributes> first =
                dao.getPeopleWithMultivaluedAttributes(seed("student"), filter);
        final Set<IPersonAttributes> second =
                dao.getPeopleWithMultivaluedAttributes(seed("student"), filter);

        assertEquals("student", first.iterator().next().getName());
        assertSame(first, second);
        verify(delegate, times(1)).getPeopleWithMultivaluedAttributes(any(), any());
        assertEquals(1, ehcache.getSize());
    }

    @Test
    public void differentUsersAreCachedSeparately() {
        dao.getPeopleWithMultivaluedAttributes(seed("student"), filter);
        dao.getPeopleWithMultivaluedAttributes(seed("staff"), filter);
        dao.getPeopleWithMultivaluedAttributes(seed("staff"), filter);

        verify(delegate, times(2)).getPeopleWithMultivaluedAttributes(any(), any());
        assertEquals(2, ehcache.getSize());
    }

    @Test
    public void nullResultsAreCachedAsMarker() {
        assertNull(dao.getPeopleWithMultivaluedAttributes(seed("unknown1"), filter));
        assertNull(dao.getPeopleWithMultivaluedAttributes(seed("unknown1"), filter));

        verify(delegate, times(1)).getPeopleWithMultivaluedAttributes(any(), any());
        assertEquals(1, ehcache.getSize());
    }

    @Test
    public void nullResultsAreNotCachedWhenDisabled() {
        dao.setCacheNullResults(false);
        assertNull(dao.getPeopleWithMultivaluedAttributes(seed("unknown1"), filter));
        assertNull(dao.getPeopleWithMultivaluedAttributes(seed("unknown1"), filter));

        verify(delegate, times(2)).getPeopleWithMultivaluedAttributes(any(), any());
        assertEquals(0, ehcache.getSize());
    }

    @Test
    public void seedWithoutKeyAttributesIsNotCached() {
        dao.getPeopleWithMultivaluedAttributes(new HashMap<>(), filter);
        dao.getPeopleWithMultivaluedAttributes(new HashMap<>(), filter);

        verify(delegate, times(2)).getPeopleWithMultivaluedAttributes(any(), any());
        assertEquals(0, ehcache.getSize());
    }

    @Test
    public void removeUserAttributesEvictsTheUser() {
        dao.getPeopleWithMultivaluedAttributes(seed("student"), filter);
        dao.getPeopleWithMultivaluedAttributes(seed("staff"), filter);

        dao.removeUserAttributes("student");
        dao.getPeopleWithMultivaluedAttributes(seed("student"), filter);
        dao.getPeopleWithMultivaluedAttributes(seed("staff"), filter);

        // student was loaded twice, staff once
        verify(delegate, times(3)).getPeopleWithMultivaluedAttributes(any(), any());
    }

    @Test
    public void usernameTagPurgeEvictsOnlyThatUser() {
        dao.getPeopleWithMultivaluedAttributes(seed("student"), filter);
        dao.getPeopleWithMultivaluedAttributes(seed("staff"), filter);

        final int purged =
                tagTracker.purgeCacheEntries(
                        UsernameTaggedCacheEntryPurger.createCacheEntryTag("student"));

        assertEquals(1, purged);
        assertEquals(1, ehcache.getSize());
        dao.getPeopleWithMultivaluedAttributes(seed("student"), filter);
        dao.getPeopleWithMultivaluedAttributes(seed("staff"), filter);
        verify(delegate, times(3)).getPeopleWithMultivaluedAttributes(any(), any());
    }

    @Test
    public void keysAreEqualForEqualSeedsAndIgnoreEmptyAttributes() throws Exception {
        final Method method =
                IPersonAttributeDao.class.getMethod(
                        "getPeopleWithMultivaluedAttributes",
                        Map.class,
                        IPersonAttributeDaoFilter.class);
        final Map<String, List<Object>> withEmpty = seed("student");
        withEmpty.put("mail", Collections.emptyList());

        final Object key = keyGenerator.generate(dao, method, seed("student"));
        assertEquals(key, keyGenerator.generate(dao, method, seed("student")));
        assertEquals(
                key.hashCode(), keyGenerator.generate(dao, method, seed("student")).hashCode());
        assertEquals(key, keyGenerator.generate(dao, method, withEmpty));
        assertNotEquals(key, keyGenerator.generate(dao, method, seed("staff")));
        assertEquals(
                Collections.singleton(
                        UsernameTaggedCacheEntryPurger.createCacheEntryTag("student")),
                ((CacheKey) key).getTags());
    }
}
