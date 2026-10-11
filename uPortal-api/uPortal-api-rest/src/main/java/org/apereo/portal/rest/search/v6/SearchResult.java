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
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** One search result: a title to show, a link when there is one, and its attributes. */
public final class SearchResult {

    private final String title;
    private final @Nullable String url;
    private final Map<String, List<String>> attributes;

    public SearchResult(String title, @Nullable String url, Map<String, List<String>> attributes) {
        this.title = title;
        this.url = url;
        this.attributes = Collections.unmodifiableMap(attributes);
    }

    /** The {@code displayName}, {@code title} or {@code name} attribute, else empty. */
    public String getTitle() {
        return title;
    }

    /** Where the result opens, such as a portlet maximized; null for a person. */
    public @Nullable String getUrl() {
        return url;
    }

    /** Every other attribute the strategy returned, in its order, each as a list of strings. */
    public Map<String, List<String>> getAttributes() {
        return attributes;
    }
}
