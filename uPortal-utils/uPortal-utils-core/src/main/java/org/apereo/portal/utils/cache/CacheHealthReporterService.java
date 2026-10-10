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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * This service supports custom {@link net.sf.ehcache.event.CacheEventListener} objects that
 * recognize when "something bad" is happening with a uPortal cache. Those listeners quickly contact
 * this service with the details. Periodically this service produces a report on bad things that are
 * occurring, if any, and writes it to the log.
 *
 * <p>The service only takes cache names, so it does not depend on the cache provider; the
 * provider-specific listeners (InsufficientSizeCacheEventListener for Ehcache 2) report to it.
 */
@Service("cacheHealthReporterService")
public class CacheHealthReporterService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private enum Reports {
        CACHE_SIZE_NOT_LARGE_ENOUGH {
            @Override
            protected void doReportInternal(Logger logger, List<String> list) {
                if (list.size() != 0) {
                    final StringBuilder report = new StringBuilder();
                    final Map<String, Integer> counts = new HashMap<>();
                    for (String cacheName : list) {
                        Integer newCount = 1; // default
                        Integer oldCount = counts.get(cacheName);
                        if (oldCount != null) {
                            newCount = ++oldCount;
                        }
                        counts.put(cacheName, newCount);
                    }
                    report.append(
                            "The following cache(s) have insufficient maxElementsInMemory;  "
                                    + "there must be room in the cache to hold every object of "
                                    + "the corresponding type in the portal:");
                    for (Map.Entry<String, Integer> y : counts.entrySet()) {
                        report.append("\n\t- ")
                                .append(y.getKey())
                                .append(" (")
                                .append(y.getValue())
                                .append(
                                        " elements evicted due to insufficient size since last report)");
                    }
                    logger.warn(report.toString());
                }
            }
        };

        private final List<String> entries = Collections.synchronizedList(new ArrayList<>());

        public final void add(String cacheName) {
            entries.add(cacheName);
        }

        public final void writeReport(Logger logger) {

            // Copy & Reset the entries list
            final List<String> list = new ArrayList<>(entries);
            entries.clear();
            doReportInternal(logger, list);
        }

        protected abstract void doReportInternal(Logger logger, List<String> list);
    }

    public void generateReports() {
        for (Reports report : Reports.values()) {
            report.writeReport(logger);
        }
    }

    /** Records that the named cache evicted an entry that had not expired. */
    @Async
    public void reportCacheSizeNotLargeEnough(String cacheName) {
        Reports.CACHE_SIZE_NOT_LARGE_ENOUGH.add(cacheName);
    }
}
