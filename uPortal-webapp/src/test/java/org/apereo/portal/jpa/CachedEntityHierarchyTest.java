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
package org.apereo.portal.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.TreeSet;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Every entity below a cached root entity must use the second-level cache too.
 *
 * <p>Since Hibernate 5.3 each class of a hierarchy has its own cache setting. A subclass without
 * {@code @Cache} and {@code @Cacheable} inherits the root's. A subclass with {@code @Cache} gets
 * warning HHH000482 and no caching at all, so every load of one of its rows goes to the database.
 * Up to Hibernate 5.2 the root's setting applied to the whole hierarchy.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaPortalSecondLevelCacheTestContext.xml")
public class CachedEntityHierarchyTest extends BasePortalJpaDaoTest {

    @Test
    public void subclassesOfACachedRootAreCached() {
        final SessionFactoryImplementor sessionFactory =
                getEntityManager()
                        .getEntityManagerFactory()
                        .unwrap(SessionFactoryImplementor.class);
        final Map<String, EntityPersister> persisters =
                sessionFactory.getMetamodel().entityPersisters();

        final TreeSet<String> uncached = new TreeSet<>();
        int cachedSubclasses = 0;
        for (final EntityPersister persister : persisters.values()) {
            final EntityPersister root = persisters.get(persister.getRootEntityName());
            if (root == persister || !root.canReadFromCache()) {
                continue;
            }
            if (persister.canReadFromCache() && persister.canWriteToCache()) {
                cachedSubclasses++;
            } else {
                uncached.add(persister.getEntityName() + " (root " + root.getEntityName() + ")");
            }
        }

        assertEquals(
                "These entities don't use the second-level cache although their root entity"
                        + " does. Remove @Cache and @Cacheable from them, so that they inherit"
                        + " the root's setting",
                new TreeSet<String>(),
                uncached);
        assertTrue("The check found no subclass of a cached root entity", cachedSubclasses > 0);
    }
}
