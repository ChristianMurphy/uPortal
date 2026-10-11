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
import org.jspecify.annotations.Nullable;

/**
 * The results of one search strategy, such as {@code people} or {@code portlets}, or the failure of
 * that strategy.
 */
public final class SearchResultGroup {

    private final String type;
    private final @Nullable String label;
    private final List<SearchResult> results;
    private final boolean failed;

    public SearchResultGroup(String type, List<SearchResult> results) {
        this(type, null, results, false);
    }

    public SearchResultGroup(
            String type, @Nullable String label, List<SearchResult> results, boolean failed) {
        this.type = type;
        this.label = label;
        this.results = Collections.unmodifiableList(results);
        this.failed = failed;
    }

    /** A group whose strategy failed: no results, and the other groups are unaffected. */
    public static SearchResultGroup failed(String type, @Nullable String label) {
        return new SearchResultGroup(type, label, Collections.emptyList(), true);
    }

    /** The strategy's result type name, which the {@code type} request parameter selects. */
    public String getType() {
        return type;
    }

    /** The name of the group's tab, as configured for a remote provider; null for the others. */
    public @Nullable String getLabel() {
        return label;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    /** True when the strategy failed (a remote service did not answer, say). */
    public boolean isFailed() {
        return failed;
    }
}
