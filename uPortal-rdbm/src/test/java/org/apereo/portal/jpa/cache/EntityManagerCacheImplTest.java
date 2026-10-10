/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.jpa.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apereo.portal.jpa.EntityManagerClosingEvent;
import org.apereo.portal.jpa.EntityManagerCreatedEvent;
import org.apereo.portal.utils.cache.TaggedCacheEntryPurger;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that {@link EntityManagerCacheImpl} survives a close it has no record of (MOD-I102). It
 * logs the inconsistency, and must not then dereference the per-thread state it just found missing.
 */
public class EntityManagerCacheImplTest {

    private static final String PERSISTENCE_UNIT = "testPersistenceUnit";
    private static final String OTHER_PERSISTENCE_UNIT = "otherPersistenceUnit";

    private final TaggedCacheEntryPurger purger = mock(TaggedCacheEntryPurger.class);
    private final EntityManagerCacheImpl cache = new EntityManagerCacheImpl();

    @Before
    public void setUp() {
        cache.setTaggedCacheEntryPurger(purger);
    }

    @Test
    public void closingWhenThisThreadHasNoOpenEntityManager() {
        cache.onApplicationEvent(closing(1L, PERSISTENCE_UNIT));

        // The cached data of the closed EntityManager is still purged
        verify(purger).purgeCacheEntries(any());
    }

    @Test
    public void closingWhenThisThreadHasNoOpenEntityManagerForThatPersistenceUnit() {
        cache.onApplicationEvent(created(2L, OTHER_PERSISTENCE_UNIT));

        cache.onApplicationEvent(closing(3L, PERSISTENCE_UNIT));

        // The other persistence unit is still tracked, and closes normally
        cache.onApplicationEvent(closing(2L, OTHER_PERSISTENCE_UNIT));
        verify(purger, times(2)).purgeCacheEntries(any());
    }

    @Test
    public void createdThenClosed() {
        cache.onApplicationEvent(created(4L, PERSISTENCE_UNIT));
        cache.onApplicationEvent(created(5L, PERSISTENCE_UNIT));
        cache.onApplicationEvent(closing(5L, PERSISTENCE_UNIT));
        cache.onApplicationEvent(closing(4L, PERSISTENCE_UNIT));

        // Nothing is tracked any more, so a further close is the case above
        cache.onApplicationEvent(closing(4L, PERSISTENCE_UNIT));
        verify(purger, times(3)).purgeCacheEntries(any());
    }

    private EntityManagerCreatedEvent created(long id, String persistenceUnit) {
        return new EntityManagerCreatedEvent(this, id, persistenceUnit, null);
    }

    private EntityManagerClosingEvent closing(long id, String persistenceUnit) {
        return new EntityManagerClosingEvent(this, id, persistenceUnit, null);
    }
}
