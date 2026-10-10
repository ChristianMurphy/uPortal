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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.Validate;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.apereo.services.persondir.IPersonAttributeDaoFilter;
import org.apereo.services.persondir.IPersonAttributes;
import org.apereo.services.persondir.support.AbstractDefaultAttributePersonAttributeDao;
import org.apereo.services.persondir.support.BasePersonImpl;
import org.apereo.services.persondir.support.MultivaluedPersonAttributeUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.KeyGenerator;

/**
 * Caches the results of a wrapped {@link IPersonAttributeDao} in a Spring {@link Cache}.
 *
 * <p>Replaces Person Directory's {@code CachingPersonAttributeDaoImpl}, which needs the
 * spring-modules-cache {@code CacheKeyGenerator}. It behaves the same: every query is answered by
 * {@link #getPeopleWithMultivaluedAttributes(Map, IPersonAttributeDaoFilter)}, keys come from the
 * {@link KeyGenerator} for that method with the seed as the only argument, a {@code null} key skips
 * the cache, and {@code null} results are cached as a marker when {@code cacheNullResults} is set.
 *
 * @since 6.0
 */
public class CachingPersonAttributeDao extends AbstractDefaultAttributePersonAttributeDao {

    /** Stored in the cache in place of a {@code null} result. */
    static final Set<IPersonAttributes> NULL_RESULTS_OBJECT =
            Collections.singleton(new NullResultsPerson());

    private static final Method GET_PEOPLE_WITH_MULTIVALUED_ATTRIBUTES;

    static {
        try {
            GET_PEOPLE_WITH_MULTIVALUED_ATTRIBUTES =
                    IPersonAttributeDao.class.getMethod(
                            "getPeopleWithMultivaluedAttributes",
                            Map.class,
                            IPersonAttributeDaoFilter.class);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private IPersonAttributeDao cachedPersonAttributesDao;
    private KeyGenerator cacheKeyGenerator;
    private Cache userInfoCache;
    private boolean cacheNullResults = false;

    public void setCachedPersonAttributesDao(IPersonAttributeDao cachedPersonAttributesDao) {
        Validate.notNull(cachedPersonAttributesDao, "cachedPersonAttributesDao may not be null");
        this.cachedPersonAttributesDao = cachedPersonAttributesDao;
    }

    public void setCacheKeyGenerator(KeyGenerator cacheKeyGenerator) {
        Validate.notNull(cacheKeyGenerator, "cacheKeyGenerator may not be null");
        this.cacheKeyGenerator = cacheKeyGenerator;
    }

    public void setUserInfoCache(Cache userInfoCache) {
        Validate.notNull(userInfoCache, "userInfoCache may not be null");
        this.userInfoCache = userInfoCache;
    }

    /** If null results should be cached to avoid repeating failed lookups. Defaults to false. */
    public void setCacheNullResults(boolean cacheNullResults) {
        this.cacheNullResults = cacheNullResults;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<IPersonAttributes> getPeopleWithMultivaluedAttributes(
            Map<String, List<Object>> seed, IPersonAttributeDaoFilter filter) {
        Validate.notNull(seed, "The query seed Map cannot be null.");
        Validate.validState(
                this.cachedPersonAttributesDao != null,
                "No 'cachedPersonAttributesDao' has been specified.");
        Validate.validState(this.userInfoCache != null, "No 'userInfoCache' has been specified.");

        final Object cacheKey = this.generateKey(seed);
        if (cacheKey != null) {
            final Cache.ValueWrapper cached = this.userInfoCache.get(cacheKey);
            if (cached != null && cached.get() != null) {
                Set<IPersonAttributes> cacheResults = (Set<IPersonAttributes>) cached.get();
                if (NULL_RESULTS_OBJECT.equals(cacheResults)) {
                    cacheResults = null;
                }
                logger.debug("Retrieved query from cache. key='{}'", cacheKey);
                return cacheResults;
            }
        }

        final Set<IPersonAttributes> queryResults =
                this.cachedPersonAttributesDao.getPeopleWithMultivaluedAttributes(seed, filter);

        if (cacheKey != null) {
            if (queryResults != null) {
                this.userInfoCache.put(cacheKey, queryResults);
            } else if (this.cacheNullResults) {
                this.userInfoCache.put(cacheKey, NULL_RESULTS_OBJECT);
            }
            logger.debug("Stored query results in cache. key='{}'", cacheKey);
        }

        return queryResults;
    }

    /** Evicts the cached attributes of the user with the given uid. */
    public void removeUserAttributes(String uid) {
        Validate.notNull(uid, "uid may not be null.");
        this.removeUserAttributesMultivaluedSeed(this.toSeedMap(uid));
    }

    /** Evicts the cached result for the given seed. */
    public void removeUserAttributes(Map<String, Object> seed) {
        this.removeUserAttributesMultivaluedSeed(
                MultivaluedPersonAttributeUtils.toMultivaluedMap(seed));
    }

    /** Evicts the cached result for the given multivalued seed. */
    public void removeUserAttributesMultivaluedSeed(Map<String, List<Object>> seed) {
        final Object cacheKey = this.generateKey(seed);
        if (cacheKey != null) {
            this.userInfoCache.evict(cacheKey);
        }
    }

    private Object generateKey(Map<String, List<Object>> seed) {
        return this.cacheKeyGenerator.generate(this, GET_PEOPLE_WITH_MULTIVALUED_ATTRIBUTES, seed);
    }

    @Override
    public String[] getId() {
        final List<String> ids = new ArrayList<>();
        ids.add(this.getClass().getSimpleName());
        ids.addAll(Arrays.asList(this.cachedPersonAttributesDao.getId()));
        return ids.toArray(new String[0]);
    }

    @Override
    public Set<String> getPossibleUserAttributeNames(IPersonAttributeDaoFilter filter) {
        return this.cachedPersonAttributesDao.getPossibleUserAttributeNames(filter);
    }

    @Override
    public Set<String> getAvailableQueryAttributes(IPersonAttributeDaoFilter filter) {
        return this.cachedPersonAttributesDao.getAvailableQueryAttributes(filter);
    }

    private static final class NullResultsPerson extends BasePersonImpl implements Serializable {
        private static final long serialVersionUID = 1L;

        NullResultsPerson() {
            super(new HashMap<>());
        }

        @Override
        public String getName() {
            return CachingPersonAttributeDao.class.getName() + "UNIQUE_NULL_RESULTS";
        }
    }
}
