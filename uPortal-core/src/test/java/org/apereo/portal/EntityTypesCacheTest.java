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
package org.apereo.portal;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import org.apereo.portal.security.IPerson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.util.ClassUtils;

/**
 * EntityTypes behind Spring's cache proxy, as in the portal.
 *
 * <p>Spring 5.3's SpEL looks up the interface method of each getter it calls, and caches that
 * lookup in {@code ClassUtils} with soft references. On a miss, the key {@code #type.Name} on
 * {@code IPerson.class} called {@code Principal.getName()} on the Class object and failed with
 * "object is not an instance of declaring class". A miss happens on the first call in a JVM and
 * after the garbage collector clears the entry, so the tests empty that cache first.
 */
public class EntityTypesCacheTest {
    private static final String ID_QUERY =
            "SELECT ENTITY_TYPE_ID FROM UP_ENTITY_TYPE WHERE ENTITY_TYPE_NAME = ?";

    private AnnotationConfigApplicationContext context;
    private EntityTypes entityTypes;
    private JdbcOperations jdbcOperations;

    @Before
    public void setUp() {
        context = new AnnotationConfigApplicationContext(CachingConfiguration.class);
        entityTypes = context.getBean(EntityTypes.class);
        jdbcOperations = context.getBean("PortalDb", JdbcOperations.class);
        when(jdbcOperations.queryForList(ID_QUERY, Integer.class, IPerson.class.getName()))
                .thenReturn(Collections.singletonList(13));
        when(jdbcOperations.queryForList(ID_QUERY, Integer.class, IBasicEntity.class.getName()))
                .thenReturn(Collections.singletonList(7));
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testIdOfIPersonAfterTheInterfaceMethodCacheIsEmptied() throws Exception {
        emptyInterfaceMethodCache();
        assertEquals(Integer.valueOf(13), entityTypes.getEntityIDFromType(IPerson.class));

        emptyInterfaceMethodCache();
        assertEquals(Integer.valueOf(13), entityTypes.getEntityIDFromType(IPerson.class));
        verify(jdbcOperations, times(1))
                .queryForList(ID_QUERY, Integer.class, IPerson.class.getName());
    }

    @Test
    public void testEachTypeHasItsOwnEntryAndDeleteEvictsThem() throws Exception {
        assertEquals(Integer.valueOf(13), entityTypes.getEntityIDFromType(IPerson.class));
        assertEquals(Integer.valueOf(7), entityTypes.getEntityIDFromType(IBasicEntity.class));
        assertEquals(Integer.valueOf(13), entityTypes.getEntityIDFromType(IPerson.class));
        verify(jdbcOperations, times(1))
                .queryForList(ID_QUERY, Integer.class, IPerson.class.getName());
        verify(jdbcOperations, times(1))
                .queryForList(ID_QUERY, Integer.class, IBasicEntity.class.getName());

        entityTypes.deleteEntityType(IBasicEntity.class);
        assertEquals(Integer.valueOf(13), entityTypes.getEntityIDFromType(IPerson.class));
        verify(jdbcOperations, times(2))
                .queryForList(ID_QUERY, Integer.class, IPerson.class.getName());
        verify(jdbcOperations)
                .update(
                        eq("DELETE FROM UP_ENTITY_TYPE WHERE ENTITY_TYPE_NAME = ?"),
                        eq(IBasicEntity.class.getName()));
    }

    /** Empties the cache that the garbage collector clears in a running portal. */
    @SuppressWarnings("unchecked")
    private static void emptyInterfaceMethodCache() throws IllegalAccessException {
        final Field field;
        try {
            field = ClassUtils.class.getDeclaredField("interfaceMethodCache");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(
                    "Spring's ClassUtils has no interfaceMethodCache: find the cache that SpEL's"
                            + " getter lookup uses now, and empty that one",
                    e);
        }
        field.setAccessible(true);
        ((Map<Method, Method>) field.get(null)).clear();
    }

    @Configuration
    @EnableCaching
    static class CachingConfiguration {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean(name = "PortalDb")
        JdbcOperations portalDb() {
            return mock(JdbcOperations.class);
        }

        @Bean
        ICounterStore counterStore() {
            return mock(ICounterStore.class);
        }

        @Bean
        EntityTypes entityTypes() {
            return new EntityTypes();
        }
    }
}
