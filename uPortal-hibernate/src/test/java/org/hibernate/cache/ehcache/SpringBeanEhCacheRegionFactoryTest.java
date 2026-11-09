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
package org.hibernate.cache.ehcache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.Configuration;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.support.SimpleTimestamper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SpringBeanEhCacheRegionFactoryTest {
    private static final String MANAGER_NAME = "SpringBeanEhCacheRegionFactoryTest.cacheManager";

    /** 60 seconds in the units of Hibernate's timestamps, the default of hibernate-ehcache 5.2 */
    private static final long SIXTY_SECONDS = 60000L * SimpleTimestamper.ONE_MS;

    private final SessionFactoryOptions options = mock(SessionFactoryOptions.class);
    private final SpringBeanEhCacheRegionFactory factory = new SpringBeanEhCacheRegionFactory();
    private CacheManager cacheManager;

    @Before
    public void createCacheManager() {
        cacheManager = new CacheManager(new Configuration().name(MANAGER_NAME));
    }

    @After
    public void shutdownCacheManager() {
        cacheManager.shutdown();
    }

    private Map<String, Object> settings() {
        final Map<String, Object> settings = new HashMap<>();
        settings.put(SpringBeanEhCacheRegionFactory.CACHE_MANAGER_NAME, " " + MANAGER_NAME + " ");
        return settings;
    }

    @Test
    public void usesTheCacheManagerWithTheConfiguredName() {
        factory.start(options, settings());

        assertSame(cacheManager, factory.getCacheManager());
    }

    @Test
    public void stopLeavesTheSharedCacheManagerRunning() {
        factory.start(options, settings());

        factory.stop();

        assertEquals(Status.STATUS_ALIVE, cacheManager.getStatus());
    }

    @Test
    public void keepsLocksForSixtySecondsWhenNoTimeoutIsConfigured() {
        factory.start(options, settings());

        assertEquals(SIXTY_SECONDS, factory.getTimeout());
    }

    @Test
    public void usesTheConfiguredLockTimeout() {
        final Map<String, Object> settings = settings();
        settings.put(ConfigSettings.EHCACHE_CONFIGURATION_CACHE_LOCK_TIMEOUT, "1500");

        factory.start(options, settings);

        assertEquals(1500L * SimpleTimestamper.ONE_MS, factory.getTimeout());
    }

    @Test
    public void failsWhenTheNameIsMissing() {
        // AbstractRegionFactory.start keeps the failure and reports it when a region is built
        factory.start(options, new HashMap<>());

        assertStartFailed(SpringBeanEhCacheRegionFactory.CACHE_MANAGER_NAME);
    }

    @Test
    public void failsWhenNoCacheManagerHasTheName() {
        final Map<String, Object> settings = new HashMap<>();
        settings.put(SpringBeanEhCacheRegionFactory.CACHE_MANAGER_NAME, "noSuchCacheManager");

        factory.start(options, settings);

        assertStartFailed("noSuchCacheManager");
    }

    private void assertStartFailed(String expectedInMessage) {
        try {
            factory.buildTimestampsRegion("timestamps", null);
            fail("A region was built although the factory didn't start");
        } catch (IllegalStateException e) {
            final Throwable cause = e.getCause();
            assertTrue(String.valueOf(cause), cause instanceof CacheException);
            assertTrue(cause.getMessage(), cause.getMessage().contains(expectedInMessage));
        }
    }
}
