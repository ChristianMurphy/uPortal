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
package org.apereo.portal.events.tincan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apereo.portal.events.tincan.om.LrsActor;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/** The LRS actor of a user is resolved once and then served from the cache. */
public class LrsActorServiceTest {

    private IPersonAttributeDao personAttributeDao;
    private LrsActorService service;

    @Before
    public void setUp() {
        personAttributeDao = mock(IPersonAttributeDao.class);
        service = new LrsActorService();
        service.setPersonAttributeDao(personAttributeDao);
        service.setCacheManager(
                new ConcurrentMapCacheManager(LrsActorService.LRS_ACTOR_CACHE_NAME));
    }

    @Test
    public void actorIsResolvedOncePerUser() {
        final LrsActor first = service.getLrsActor("student");

        assertSame(first, service.getLrsActor("student"));
        assertEquals("mailto:student", first.getMbox());
        service.getLrsActor("staff");
        verify(personAttributeDao, times(1)).getPerson("student", null);
        verify(personAttributeDao, times(1)).getPerson("staff", null);
    }

    @Test(expected = IllegalStateException.class)
    public void missingCacheFailsFast() {
        service.setCacheManager(new ConcurrentMapCacheManager("someOtherCache"));
    }
}
