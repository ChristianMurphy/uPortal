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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.jgroups.MergeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Empties this node's caches when JGroups merges the cluster after a split.
 *
 * <p>The caches in {@code ehcache.xml} replicate removals (invalidation) over JGroups. While the
 * nodes are split, by the network or by a node that failed to join, each side drops the other's
 * invalidations, and nothing resends them when the two sides merge again. An entry read during the
 * split would keep its old value until it expires. When {@link MergeViewNotifier} reports a
 * MergeView, this class therefore empties every cache of the Spring {@link CacheManager}, and the
 * node reads the entries again from the database. A plain view change, such as a node joining or
 * leaving, clears nothing.
 *
 * <p>Spring's cache API doesn't say which caches replicate, so all of them are emptied, apart from
 * the caches listed in {@link #setExcludedCacheNames(Set)}: those hold the only copy of something,
 * such as lock objects or tickets, or must outlive other caches. On a replicated cache the clear
 * also reaches the other nodes, which empty theirs on the same merge.
 */
public class MergeViewCacheClearer implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(MergeViewCacheClearer.class);

    private final Consumer<MergeView> listener = this::onMerge;
    private CacheManager cacheManager;
    private boolean enabled = true;
    private Set<String> excludedCacheNames = Collections.emptySet();
    private ExecutorService executor;

    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Whether a MergeView empties the caches (org.apereo.portal.cache.clearCachesOnMerge, default
     * true).
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Caches a merge leaves as they are. */
    public void setExcludedCacheNames(Set<String> excludedCacheNames) {
        this.excludedCacheNames = new HashSet<>(excludedCacheNames);
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            logger.info(
                    "The caches are not emptied when JGroups merges the cluster "
                            + "(org.apereo.portal.cache.clearCachesOnMerge=false)");
            return;
        }
        if (!MergeViewNotifier.isInAStack()) {
            logger.info(
                    "No JGroups stack has {}: the caches are not emptied when the cluster merges "
                            + "after a split",
                    MergeViewNotifier.class.getName());
        }
        executor =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            final Thread thread = new Thread(runnable, "uP-CacheMergeClear");
                            thread.setDaemon(true);
                            return thread;
                        });
        MergeViewNotifier.addListener(listener);
    }

    @Override
    public void destroy() {
        MergeViewNotifier.removeListener(listener);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** Clears the caches off the JGroups thread, which must not wait for them. */
    private void onMerge(MergeView view) {
        executor.execute(() -> clearCaches(view));
    }

    /** Empties every cache but the excluded ones, and returns how many it emptied. */
    int clearCaches(MergeView view) {
        int cleared = 0;
        for (String cacheName : cacheManager.getCacheNames()) {
            final Cache cache = cacheManager.getCache(cacheName);
            if (cache != null && !excludedCacheNames.contains(cacheName)) {
                cache.clear();
                cleared++;
            }
        }
        logger.info(
                "JGroups merged the cluster into {}: emptied {} caches on this node",
                view,
                cleared);
        return cleared;
    }
}
