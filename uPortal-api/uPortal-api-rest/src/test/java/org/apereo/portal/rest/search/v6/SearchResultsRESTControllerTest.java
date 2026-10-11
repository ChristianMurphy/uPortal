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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.security.csrf.PortalCsrfSecurityRequestMatcher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The search API over HTTP, with the portal's CSRF protection in front of it (the same CsrfFilter
 * and request matcher that securityContext.xml configures).
 */
public class SearchResultsRESTControllerTest {

    private static final String URI = "/api/v6-0/search";

    private SearchService service;
    private MockMvc mockMvc;
    private final MockHttpSession session = new MockHttpSession();

    @Before
    public void setUp() {
        service = mock(SearchService.class);
        final PortalCsrfSecurityRequestMatcher csrfMatcher = new PortalCsrfSecurityRequestMatcher();
        csrfMatcher.init();
        final CsrfFilter csrfFilter = new CsrfFilter(new HttpSessionCsrfTokenRepository());
        csrfFilter.setRequireCsrfProtectionMatcher(csrfMatcher);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new SearchResultsRESTController(service))
                        .addFilters(csrfFilter)
                        .build();
    }

    /** A request as the browser sends it to /uPortal/api/... with the session cookie. */
    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath("/uPortal").servletPath("/api").session(session);
    }

    @Test
    public void returnsTheGroupsAsJsonWithoutACsrfToken() throws Exception {
        final Map<String, List<String>> attributes = new LinkedHashMap<>();
        attributes.put("name", Collections.singletonList("Daily Business Cartoon"));
        attributes.put("score", Collections.singletonList("4.0"));
        when(service.search(eq("cartoon"), isNull(), any(HttpServletRequest.class)))
                .thenReturn(
                        new SearchResults(
                                "cartoon",
                                Arrays.asList(
                                        new SearchResultGroup("people", Collections.emptyList()),
                                        new SearchResultGroup(
                                                "portlets",
                                                Collections.singletonList(
                                                        new SearchResult(
                                                                "Daily Business Cartoon",
                                                                "/uPortal/p/cartoon/max/render.uP",
                                                                attributes))))));

        mockMvc.perform(api(get("/uPortal" + URI).param("q", "cartoon")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        "{\"query\":\"cartoon\",\"groups\":["
                                                + "{\"type\":\"people\",\"results\":[]},"
                                                + "{\"type\":\"portlets\",\"results\":[{"
                                                + "\"title\":\"Daily Business Cartoon\","
                                                + "\"url\":\"/uPortal/p/cartoon/max/render.uP\","
                                                + "\"attributes\":{"
                                                + "\"name\":[\"Daily Business Cartoon\"],"
                                                + "\"score\":[\"4.0\"]}}]}]}",
                                        true));
    }

    @Test
    public void passesTheRequestedTypes() throws Exception {
        when(service.search(any(), any(), any(HttpServletRequest.class)))
                .thenReturn(new SearchResults("x", Collections.emptyList()));

        mockMvc.perform(api(get("/uPortal" + URI).param("q", "x").param("type", "people,portlets")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"query\":\"x\",\"groups\":[]}", true));
        mockMvc.perform(
                        api(get("/uPortal" + URI))
                                .param("q", "x")
                                .param("type", "people")
                                .param("type", "portlets"))
                .andExpect(status().isOk());

        verify(service, times(2))
                .search(
                        eq("x"),
                        eq(new HashSet<>(Arrays.asList("people", "portlets"))),
                        any(HttpServletRequest.class));
    }

    @Test
    public void answersRefusalsWithTheirStatusAndMessage() throws Exception {
        when(service.search(isNull(), isNull(), any(HttpServletRequest.class)))
                .thenThrow(
                        new SearchException(
                                HttpStatus.BAD_REQUEST, "Enter something to search for"));

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":400,"
                                                + "\"message\":\"Enter something to search for\"}",
                                        true));
    }

    @Test
    public void refusesUnsafeRequestsWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(api(post("/uPortal" + URI).param("q", "x")))
                .andExpect(status().isForbidden());
    }
}
