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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** A cache too small for its contents is reported to the health report, by name. */
public class InsufficientSizeCacheEventListenerTest {

    private CacheManager ehcacheManager;
    private Ehcache cache;
    private CacheHealthReporterService reporter;

    @Before
    public void setUp() {
        ehcacheManager =
                new CacheManager(
                        new Configuration()
                                .name(
                                        "InsufficientSizeCacheEventListenerTest-"
                                                + UUID.randomUUID()));
        ehcacheManager.addCache(new net.sf.ehcache.Cache(new CacheConfiguration("tooSmall", 1)));
        cache = ehcacheManager.getEhcache("tooSmall");
        reporter = mock(CacheHealthReporterService.class);
        final InsufficientSizeCacheEventListener listener =
                new InsufficientSizeCacheEventListener();
        ReflectionTestUtils.setField(listener, "cacheHealthReporterService", reporter);
        cache.getCacheEventNotificationService().registerListener(listener);
    }

    @After
    public void tearDown() {
        ehcacheManager.shutdown();
    }

    @Test
    public void evictionForLackOfRoomIsReported() {
        cache.put(new Element("a", "1"));
        cache.put(new Element("b", "2"));

        verify(reporter).reportCacheSizeNotLargeEnough("tooSmall");
    }

    @Test
    public void entriesThatFitAreNotReported() {
        cache.put(new Element("a", "1"));
        cache.remove("a");

        verify(reporter, never()).reportCacheSizeNotLargeEnough("tooSmall");
    }
}
