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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.rest.search.ISearchStrategy;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

public class SearchServiceTest {

    /** A strategy that returns fixed results and records the queries it was asked. */
    private static final class FixedStrategy implements ISearchStrategy {
        private final String type;
        private final List<?> results;
        final List<String> queries = new ArrayList<>();

        FixedStrategy(String type, List<?> results) {
            this.type = type;
            this.results = results;
        }

        @Override
        public String getResultTypeName() {
            return type;
        }

        @Override
        public List<?> search(String query, HttpServletRequest request) {
            queries.add(query);
            return results;
        }
    }

    /** A bean, as an adopter's strategy might return. */
    public static final class Building {
        public String getName() {
            return "Library";
        }

        public String getUrl() {
            return "https://example.edu/library";
        }

        public int getFloors() {
            return 4;
        }
    }

    private FixedStrategy people;
    private FixedStrategy portlets;
    private SearchService service;
    private final HttpServletRequest request = new MockHttpServletRequest();

    @Before
    public void setUp() {
        final Map<String, List<Object>> steven = new LinkedHashMap<>();
        steven.put("uid", Collections.singletonList("student"));
        steven.put("displayName", Collections.singletonList("Steven Student"));
        steven.put("mail", Collections.singletonList("steven.student@example.org"));
        steven.put("nickname", Collections.emptyList());
        people = new FixedStrategy("people", Collections.singletonList(steven));

        final Map<String, Object> cartoon = new TreeMap<>();
        cartoon.put("description", "Daily Business Cartoon by Ted Goff");
        cartoon.put("favorite", false);
        cartoon.put("fname", "daily-business-cartoon");
        cartoon.put("name", "Daily Business Cartoon");
        cartoon.put("score", "4.0");
        cartoon.put("title", "The Daily Cartoon");
        cartoon.put("url", "/uPortal/p/daily-business-cartoon.ctf3/max/render.uP");
        portlets = new FixedStrategy("portlets", Collections.singletonList(cartoon));

        // Out of order on purpose: groups come back ordered by type
        service = new SearchService(Arrays.asList(portlets, people));
    }

    @Test
    public void searchesEveryStrategyInTypeOrder() {
        final SearchResults results = service.search("  cartoon ", null, request);

        assertEquals("cartoon", results.getQuery());
        assertEquals(2, results.getGroups().size());
        assertEquals("people", results.getGroups().get(0).getType());
        assertEquals("portlets", results.getGroups().get(1).getType());
        assertEquals(Collections.singletonList("cartoon"), people.queries);
        assertEquals(Collections.singletonList("cartoon"), portlets.queries);
    }

    @Test
    public void takesTheTitleFromDisplayNameAndLeavesPeopleWithoutUrl() {
        final SearchResult steven =
                service.search("steven", null, request).getGroups().get(0).getResults().get(0);

        assertEquals("Steven Student", steven.getTitle());
        assertNull(steven.getUrl());
        assertEquals(
                Arrays.asList("uid", "displayName", "mail"),
                new ArrayList<>(steven.getAttributes().keySet()));
        assertEquals(
                Collections.singletonList("steven.student@example.org"),
                steven.getAttributes().get("mail"));
    }

    @Test
    public void takesTheTitleAndUrlOfAPortlet() {
        final SearchResult cartoon =
                service.search("cartoon", null, request).getGroups().get(1).getResults().get(0);

        assertEquals("The Daily Cartoon", cartoon.getTitle());
        assertEquals("/uPortal/p/daily-business-cartoon.ctf3/max/render.uP", cartoon.getUrl());
        assertTrue(!cartoon.getAttributes().containsKey("url"));
        assertEquals(Collections.singletonList("false"), cartoon.getAttributes().get("favorite"));
        assertEquals(Collections.singletonList("4.0"), cartoon.getAttributes().get("score"));
    }

    @Test
    public void readsBeansAndPlainValues() {
        final SearchService beans =
                new SearchService(
                        Collections.singletonList(
                                new FixedStrategy(
                                        "places",
                                        Arrays.asList(new Building(), "Gym", null))));

        final List<SearchResult> results =
                beans.search("x", null, request).getGroups().get(0).getResults();

        assertEquals("Library", results.get(0).getTitle());
        assertEquals("https://example.edu/library", results.get(0).getUrl());
        assertEquals(Collections.singletonList("4"), results.get(0).getAttributes().get("floors"));
        assertEquals("Gym", results.get(1).getTitle());
        assertTrue(results.get(1).getAttributes().isEmpty());
        assertEquals("", results.get(2).getTitle());
    }

    @Test
    public void limitsTheStrategiesToTheRequestedTypes() {
        final SearchResults results =
                service.search("cartoon", new HashSet<>(Arrays.asList("portlets", "x")), request);

        assertEquals(1, results.getGroups().size());
        assertEquals("portlets", results.getGroups().get(0).getType());
        assertTrue(people.queries.isEmpty());
    }

    @Test
    public void anEmptyTypeMeansEveryType() {
        assertEquals(
                2,
                service.search("cartoon", new HashSet<>(Arrays.asList("x", "")), request)
                        .getGroups()
                        .size());
        assertEquals(2, service.search("cartoon", new HashSet<>(), request).getGroups().size());
    }

    @Test
    public void unknownTypesGiveNoGroups() {
        assertTrue(
                service.search("cartoon", Collections.singleton("x"), request)
                        .getGroups()
                        .isEmpty());
    }

    @Test
    public void refusesBlankAndOverlongQueries() {
        final StringBuilder longest = new StringBuilder();
        for (int i = 0; i < SearchService.MAX_QUERY_LENGTH; i++) {
            longest.append('a');
        }
        service.search(longest.toString(), null, request);

        for (String query : Arrays.asList(null, "", "   ", longest + "a")) {
            try {
                service.search(query, null, request);
                fail("Searched for " + query);
            } catch (SearchException e) {
                assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
            }
        }
        assertEquals(1, people.queries.size());
    }
}
