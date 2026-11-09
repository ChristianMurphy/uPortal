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

import java.util.Map;
import net.sf.ehcache.CacheManager;
import org.apache.commons.lang.StringUtils;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.ehcache.internal.EhcacheRegionFactory;
import org.hibernate.cache.spi.support.SimpleTimestamper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Region factory that loads the {@link CacheManager} by name. The manager belongs to Spring, which
 * also stops it, so this factory never shuts it down.
 */
public class SpringBeanEhCacheRegionFactory extends EhcacheRegionFactory {
    private static final long serialVersionUID = 1L;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String CACHE_MANAGER_NAME = "org.apereo.portal.cache.CacheManagerName";

    private volatile boolean cacheLockTimeoutConfigured;

    @Override
    protected void prepareForUse(SessionFactoryOptions settings, Map configValues) {
        super.prepareForUse(settings, configValues);
        this.cacheLockTimeoutConfigured =
                configValues.get(ConfigSettings.EHCACHE_CONFIGURATION_CACHE_LOCK_TIMEOUT) != null;
    }

    @Override
    protected CacheManager resolveCacheManager(SessionFactoryOptions settings, Map properties) {
        String cacheManagerName = null;
        if (properties != null) {
            final Object name = properties.get(CACHE_MANAGER_NAME);
            cacheManagerName = name == null ? null : StringUtils.trimToNull(name.toString());
        }

        if (cacheManagerName == null) {
            throw new CacheException("The '" + CACHE_MANAGER_NAME + "' property must be set");
        }

        logger.debug("Getting CacheManager named {}", cacheManagerName);
        final CacheManager cacheManager = CacheManager.getCacheManager(cacheManagerName);
        if (cacheManager == null) {
            throw new CacheException(
                    "No Ehcache CacheManager named '"
                            + cacheManagerName
                            + "' exists. It must be created before the session factory.");
        }
        return cacheManager;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link EhcacheRegionFactory} keeps the soft locks of read-write regions and the
     * pre-invalidation of the query cache for no time at all unless the timeout setting is present:
     * its field stays 0. Hibernate 5.2 kept them for 60 seconds, as {@link
     * org.hibernate.cache.spi.AbstractRegionFactory} still does.
     */
    @Override
    public long getTimeout() {
        return cacheLockTimeoutConfigured ? super.getTimeout() : SimpleTimestamper.timeOut();
    }

    /** Spring stops the cache manager, so there is nothing to release. */
    @Override
    protected void releaseFromUse() {
        // EhcacheRegionFactory would shut the shared manager down here
    }
}
