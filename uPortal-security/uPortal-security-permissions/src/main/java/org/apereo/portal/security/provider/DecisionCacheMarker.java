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
package org.apereo.portal.security.provider;

import java.util.UUID;
import org.springframework.cache.Cache;

/**
 * A marker entry in {@code HAS_UNBLOCKED_GRANT} that tells a permission check whether a permission
 * changed while it ran. Without it, a check that read the permissions before a change could store
 * its result after the caches were cleared, and the old decision would stay cached until it
 * expired.
 *
 * <p>{@link AuthorizationImpl} removes the marker before each step of a change: before it removes
 * the changed principals' permission sets, before it clears {@code HAS_UNBLOCKED_GRANT} and before
 * it clears {@code PRINCIPAL_HAS_PERMISSION}. The cache replicates removals, so the marker goes on
 * the other nodes too, in the same order as the steps. A check reads the marker before it reads
 * anything its result depends on, and stores the result only while that marker is still in the
 * cache. If the marker goes between the check and the put, the result is taken out again.
 *
 * <p>The marker lives in the cache itself and is read through Spring's {@link Cache} API, so a
 * removal sent by another node is seen without a cache event listener.
 */
final class DecisionCacheMarker {

    /** The marker's key. The decision caches' own keys are {@code CacheKey}s. */
    static final String KEY = DecisionCacheMarker.class.getName();

    private final Cache cache;

    DecisionCacheMarker(Cache cache) {
        this.cache = cache;
    }

    /**
     * Returns the marker in the cache, and puts a new one there if there is none. Call it before
     * reading the permissions, groups or cached decisions that the result depends on.
     */
    String current() {
        final String present = read();
        if (present != null) {
            return present;
        }
        // A plain put: Ehcache refuses putIfAbsent on a cache with a replicator. When two checks
        // put a marker at once, the one whose marker is replaced doesn't store its result.
        final String created = UUID.randomUUID().toString();
        cache.put(KEY, created);
        return created;
    }

    /** Removes the marker, on this node and, as the cache replicates removals, on the others. */
    void remove() {
        cache.evict(KEY);
    }

    /**
     * Stores a result computed after {@link #current()} returned {@code marker}, unless the marker
     * was removed since. If it is removed between the check and the put, {@code undo} takes the
     * result out again.
     */
    void store(String marker, Runnable put, Runnable undo) {
        if (!marker.equals(read())) {
            return;
        }
        put.run();
        if (!marker.equals(read())) {
            undo.run();
        }
    }

    private String read() {
        final Cache.ValueWrapper present = cache.get(KEY);
        return present == null ? null : (String) present.get();
    }
}
