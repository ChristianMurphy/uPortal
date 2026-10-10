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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apereo.portal.IUserProfile;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.utils.cache.CacheKey;
import org.apereo.portal.utils.cache.CacheLookup;
import org.apereo.portal.utils.cache.UsernameTaggedCacheEntryPurger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/** Provides API for layout caching service */
@Service("layoutCachingService")
public class LayoutCachingService implements ILayoutCachingService {
    static final String LAYOUT_CACHE_NAME = "org.apereo.portal.layout.dlm.LAYOUT_CACHE";

    protected final Log logger = LogFactory.getLog(this.getClass());

    private Cache layoutCache;

    @Autowired
    public void setCacheManager(CacheManager cacheManager) {
        this.setLayoutCache(CacheLookup.requireCache(cacheManager, LAYOUT_CACHE_NAME));
    }

    public void setLayoutCache(Cache layoutCache) {
        this.layoutCache = layoutCache;
    }

    @Override
    public void cacheLayout(IPerson owner, IUserProfile profile, DistributedUserLayout layout) {
        final CacheKey cacheKey = this.getCacheKey(owner, profile);
        this.layoutCache.put(cacheKey, layout);
    }

    @Override
    public DistributedUserLayout getCachedLayout(IPerson owner, IUserProfile profile) {
        final CacheKey cacheKey = this.getCacheKey(owner, profile);
        return this.layoutCache.get(cacheKey, DistributedUserLayout.class);
    }

    @Override
    public void removeCachedLayout(IPerson owner, IUserProfile profile) {
        final CacheKey cacheKey = this.getCacheKey(owner, profile);
        this.layoutCache.evict(cacheKey);
    }

    protected CacheKey getCacheKey(IPerson owner, IUserProfile profile) {
        return CacheKey.buildTagged(
                LayoutCachingService.class.getName(),
                UsernameTaggedCacheEntryPurger.createCacheEntryTag(owner.getUserName()),
                owner.getUserName(),
                profile.getProfileId());
    }
}
