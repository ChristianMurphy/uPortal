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

/** The results of one search strategy, such as {@code people} or {@code portlets}. */
public final class SearchResultGroup {

    private final String type;
    private final List<SearchResult> results;

    public SearchResultGroup(String type, List<SearchResult> results) {
        this.type = type;
        this.results = Collections.unmodifiableList(results);
    }

    /** The strategy's result type name, which the {@code type} request parameter selects. */
    public String getType() {
        return type;
    }

    public List<SearchResult> getResults() {
        return results;
    }
}
