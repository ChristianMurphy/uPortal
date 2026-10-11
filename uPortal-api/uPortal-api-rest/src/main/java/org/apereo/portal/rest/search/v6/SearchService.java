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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.rest.search.ISearchStrategy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Runs the portal's {@link ISearchStrategy} beans and turns what each returns (a map of attributes,
 * or a bean) into {@link SearchResult}s, so that clients get one shape whatever the strategy.
 */
@Service
public class SearchService {

    /**
     * The longest query accepted, in characters (Unicode code points, so a four-byte character such
     * as an emoji counts once), as the search boxes allow.
     */
    public static final int MAX_QUERY_LENGTH = 200;

    private static final String URL_ATTRIBUTE = "url";

    private static final List<String> TITLE_ATTRIBUTES =
            Collections.unmodifiableList(Arrays.asList("displayName", "title", "name"));

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<ISearchStrategy> strategies;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** The portal's strategy beans and the configured remote JSON search providers. */
    @Autowired
    public SearchService(List<ISearchStrategy> strategies, RemoteJsonSearchProviders remote) {
        this(concat(strategies, remote.getStrategies()));
    }

    public SearchService(List<? extends ISearchStrategy> strategies) {
        this.strategies =
                strategies.stream()
                        .sorted(Comparator.comparing(ISearchStrategy::getResultTypeName))
                        .collect(Collectors.toList());
    }

    private static List<ISearchStrategy> concat(
            List<ISearchStrategy> first, List<? extends ISearchStrategy> second) {
        final List<ISearchStrategy> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    /**
     * Searches with every strategy whose type is in {@code types}, or with all of them when {@code
     * types} is null, empty or contains an empty string (as {@code /api/v5-0} does).
     */
    public SearchResults search(
            @Nullable String query, @Nullable Set<String> types, HttpServletRequest request) {
        final String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new SearchException(HttpStatus.BAD_REQUEST, "Enter something to search for");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_QUERY_LENGTH) {
            throw new SearchException(
                    HttpStatus.BAD_REQUEST,
                    "Search for at most " + MAX_QUERY_LENGTH + " characters");
        }
        final List<SearchResultGroup> groups = new ArrayList<>();
        for (ISearchStrategy strategy : strategies) {
            final String type = strategy.getResultTypeName();
            if (types == null || types.isEmpty() || types.contains("") || types.contains(type)) {
                groups.add(search(strategy, trimmed, request));
            }
        }
        return new SearchResults(trimmed, groups);
    }

    /** One strategy's group; a strategy that fails gives a failed group, not a failed search. */
    private SearchResultGroup search(
            ISearchStrategy strategy, String query, HttpServletRequest request) {
        final String type = strategy.getResultTypeName();
        final String label =
                strategy instanceof RemoteJsonSearchStrategy
                        ? ((RemoteJsonSearchStrategy) strategy).getSettings().getLabel()
                        : null;
        try {
            final List<SearchResult> results = new ArrayList<>();
            for (Object result : strategy.search(query, request)) {
                results.add(toSearchResult(result));
            }
            return new SearchResultGroup(type, label, results, false);
        } catch (RuntimeException e) {
            logger.warn("Search strategy {} failed", type, e);
            return SearchResultGroup.failed(type, label);
        }
    }

    private SearchResult toSearchResult(@Nullable Object result) {
        if (result == null || isScalar(result)) {
            return new SearchResult(
                    result == null ? "" : result.toString(), null, Collections.emptyMap());
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> map = objectMapper.convertValue(result, Map.class);
        final Map<String, List<String>> attributes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            final List<String> values = toStrings(entry.getValue());
            if (!values.isEmpty()) {
                attributes.put(entry.getKey(), values);
            }
        }
        final List<String> urls = attributes.remove(URL_ATTRIBUTE);
        final String url = urls == null ? null : urls.get(0);
        String title = "";
        for (String name : TITLE_ATTRIBUTES) {
            final List<String> values = attributes.get(name);
            if (values != null) {
                title = values.get(0);
                break;
            }
        }
        return new SearchResult(title, url, attributes);
    }

    private static boolean isScalar(Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Boolean;
    }

    private static List<String> toStrings(@Nullable Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection) {
            final List<String> strings = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                if (item != null) {
                    strings.add(item.toString());
                }
            }
            return strings;
        }
        return Collections.singletonList(value.toString());
    }
}
