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

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.distribution.CacheManagerPeerProvider;
import net.sf.ehcache.distribution.CacheReplicator;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.MergeView;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.ReflectionUtils;

/**
 * Clears this node's replicated caches when JGroups installs a {@link MergeView}.
 *
 * <p>The caches in {@code ehcache.xml} replicate removals (invalidation) over JGroups. While the
 * nodes are split, by the network or by a node that failed to join, each side drops the other's
 * invalidations, and nothing resends them when the two sides merge again. An entry read during the
 * split would keep its old value until it expires. On a MergeView each node therefore empties every
 * cache that has a {@link CacheReplicator}, locally and without telling the other nodes, and reads
 * the entries again from the database. A plain view change, such as a node joining or leaving,
 * clears nothing.
 *
 * <p>The JGroups channel belongs to ehcache-jgroups3replication's {@code
 * JGroupsCacheManagerPeerProvider}, which keeps it in a private field and handles no views. This
 * class wraps the channel's receiver, so the library still gets every message and view.
 */
public class ReplicatedCachesMergeClearer implements InitializingBean, DisposableBean {

    /** The scheme of ehcache-jgroups3replication's peer provider. */
    static final String JGROUPS_SCHEME = "JGroups";

    private static final Logger logger =
            LoggerFactory.getLogger(ReplicatedCachesMergeClearer.class);

    private CacheManager cacheManager;
    private boolean enabled = true;
    private ExecutorService executor;

    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Whether a MergeView clears the replicated caches
     * (org.apereo.portal.cache.clearReplicatedCachesOnMerge, default true).
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            logger.info(
                    "Replicated caches are not cleared when JGroups merges the cluster "
                            + "(org.apereo.portal.cache.clearReplicatedCachesOnMerge=false)");
            return;
        }
        final CacheManagerPeerProvider provider =
                cacheManager.getCacheManagerPeerProvider(JGROUPS_SCHEME);
        if (provider == null) {
            logger.debug("No JGroups peer provider: the caches are not replicated");
            return;
        }
        final JChannel channel = channelOf(provider);
        if (channel == null) {
            logger.warn(
                    "Cannot reach the JGroups channel of {}: replicated caches will not be "
                            + "cleared when the cluster merges after a split",
                    provider.getClass().getName());
            return;
        }
        executor =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            final Thread thread = new Thread(runnable, "uP-CacheMergeClear");
                            thread.setDaemon(true);
                            return thread;
                        });
        channel.setReceiver(new MergeAwareReceiver(channel.getReceiver(), this::onMerge));
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** The provider's JGroups channel, or null when it has none by that name. */
    static JChannel channelOf(Object provider) {
        final Field field =
                ReflectionUtils.findField(provider.getClass(), "channel", JChannel.class);
        if (field == null) {
            return null;
        }
        ReflectionUtils.makeAccessible(field);
        return (JChannel) ReflectionUtils.getField(field, provider);
    }

    /** Clears the caches off the JGroups thread, which must not wait for them. */
    private void onMerge(MergeView view) {
        executor.execute(() -> clearReplicatedCaches(view));
    }

    /** Empties every replicated cache on this node only, and returns how many there were. */
    int clearReplicatedCaches(View view) {
        int cleared = 0;
        for (String cacheName : cacheManager.getCacheNames()) {
            final Ehcache cache = cacheManager.getEhcache(cacheName);
            if (cache != null && isReplicated(cache)) {
                cache.removeAll(true);
                cleared++;
            }
        }
        logger.info(
                "JGroups merged the cluster into {}: cleared {} replicated caches on this node",
                view,
                cleared);
        return cleared;
    }

    private static boolean isReplicated(Ehcache cache) {
        return cache.getCacheEventNotificationService().getCacheEventListeners().stream()
                .anyMatch(listener -> listener instanceof CacheReplicator);
    }

    /** Passes everything on to the library's receiver, and reports each MergeView. */
    static final class MergeAwareReceiver implements Receiver {
        private final Receiver library;
        private final Consumer<MergeView> onMerge;

        MergeAwareReceiver(Receiver library, Consumer<MergeView> onMerge) {
            this.library = library;
            this.onMerge = onMerge;
        }

        @Override
        public void viewAccepted(View view) {
            library.viewAccepted(view);
            if (view instanceof MergeView) {
                onMerge.accept((MergeView) view);
            }
        }

        @Override
        public void receive(Message msg) {
            library.receive(msg);
        }

        @Override
        public void getState(OutputStream output) throws Exception {
            library.getState(output);
        }

        @Override
        public void setState(InputStream input) throws Exception {
            library.setState(input);
        }

        @Override
        public void suspect(Address suspected) {
            library.suspect(suspected);
        }

        @Override
        public void block() {
            library.block();
        }

        @Override
        public void unblock() {
            library.unblock();
        }
    }
}
