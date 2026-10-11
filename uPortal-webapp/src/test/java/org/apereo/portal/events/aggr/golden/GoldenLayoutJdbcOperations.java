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
package org.apereo.portal.events.aggr.golden;

import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Answers the two layout queries of {@code JpaAggregatedTabLookupDao} (the tab name of a DLM node
 * and the user name of its fragment owner) for the tabs of {@code GoldenEventStream}: fragment
 * owners 5 and 6 own the "Welcome" and "Academics" tabs; owner 7 and its tab are unknown.
 */
public class GoldenLayoutJdbcOperations extends JdbcTemplate {
    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
        final int userId = ((Number) args[0]).intValue();
        if (sql.contains("UP_LAYOUT_STRUCT")) {
            final int structId = ((Number) args[2]).intValue();
            if (userId == 5 && structId == 10) {
                return (List<T>) Collections.singletonList("Welcome");
            }
            if (userId == 6 && structId == 20) {
                return (List<T>) Collections.singletonList("Academics");
            }
            return Collections.emptyList();
        }
        if (sql.contains("UP_USER")) {
            if (userId == 5) {
                return (List<T>) Collections.singletonList("fragment-welcome");
            }
            if (userId == 6) {
                return (List<T>) Collections.singletonList("fragment-academics");
            }
            return Collections.emptyList();
        }
        throw new UnsupportedOperationException(sql);
    }
}
