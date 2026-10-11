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
package org.apereo.portal.rest.search.v6;

import java.util.Collections;
import java.util.List;

/** What {@code GET /api/v6-0/search} returns: the query and one group per result type. */
public final class SearchResults {

    private final String query;
    private final List<SearchResultGroup> groups;

    public SearchResults(String query, List<SearchResultGroup> groups) {
        this.query = query;
        this.groups = Collections.unmodifiableList(groups);
    }

    /** The query as searched, trimmed. */
    public String getQuery() {
        return query;
    }

    /** One group per search strategy that ran, ordered by type, even when it found nothing. */
    public List<SearchResultGroup> getGroups() {
        return groups;
    }
}
