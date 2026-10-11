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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Search, for the {@code up-search} web component and other clients.
 *
 * <ul>
 *   <li>{@code GET /api/v6-0/search?q=cartoon}: {@code {"query": "cartoon", "groups": [{"type":
 *       "people", "results": [...]}, {"type": "portlets", "results": [...]}]}}, one group per
 *       search strategy, ordered by type. A result has a {@code title}, a {@code url} (null for
 *       people) and its other {@code attributes} as lists of strings.
 *   <li>{@code type} (repeated, or comma-separated) limits the strategies; an unknown type gives
 *       no group.
 * </ul>
 *
 * <p>The user is the one of the portal session, so a strategy finds what that user may see:
 * portlets the user may browse, people the user may look up. Guests may search. A blank query or
 * one longer than {@value SearchService#MAX_QUERY_LENGTH} characters answers 400 with {@code
 * {"message": "..."}}. The API has only safe methods; anything else under {@code /api/v6-0/}
 * needs Spring Security's CSRF token (see {@code PortalCsrfSecurityRequestMatcher}).
 */
@RestController
public class SearchResultsRESTController {

    public static final String SEARCH_URI = "/v6-0/search";

    private final SearchService searchService;

    public SearchResultsRESTController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping(value = SEARCH_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResults search(
            @RequestParam(value = "q", required = false) @Nullable String query,
            @RequestParam(value = "type", required = false) @Nullable Set<String> types,
            HttpServletRequest request) {
        return searchService.search(query, types, request);
    }

    @ExceptionHandler(SearchException.class)
    public ResponseEntity<Map<String, Object>> refused(SearchException e) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", e.getStatus().value());
        body.put("message", e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
