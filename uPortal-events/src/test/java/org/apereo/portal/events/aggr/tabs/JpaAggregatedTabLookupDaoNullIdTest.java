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
package org.apereo.portal.events.aggr.tabs;

import static org.junit.Assert.assertEquals;

import org.apereo.portal.utils.Tuple;
import org.junit.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

/**
 * An event without a layout node id resolves to the missing-tab names without using the cache,
 * whose key can't be null (Spring 5 marks it non-null, and ConcurrentMapCache throws) (MOD-I225).
 */
public class JpaAggregatedTabLookupDaoNullIdTest {

    @Test
    public void resolvesAMissingNodeIdWithoutTheCache() {
        final JpaAggregatedTabLookupDao dao = new JpaAggregatedTabLookupDao();
        dao.setLayoutNodeIdNameResolutionCache(new ConcurrentMapCache("tabNames"));
        final Tuple<String, String> names = dao.resolveTabName(null);
        assertEquals(AggregatedTabMapping.MISSING_TAB_FRAGMENT_NAME, names.first);
        assertEquals(AggregatedTabMapping.MISSING_TAB_NAME, names.second);
    }
}
