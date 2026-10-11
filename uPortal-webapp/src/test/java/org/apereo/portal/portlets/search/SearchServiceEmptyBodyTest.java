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
package org.apereo.portal.portlets.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.portlet.PortletRequest;
import org.apereo.portal.portlets.search.google.GoogleCustomSearchService;
import org.apereo.portal.portlets.search.gsa.GsaSearchService;
import org.apereo.portal.search.SearchRequest;
import org.apereo.portal.search.SearchResults;
import org.junit.Test;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * A search service whose engine answers without a body (204 No Content) returns no results, not a
 * NullPointerException; Spring 5 marks getForObject's result @Nullable (MOD-I225).
 */
public class SearchServiceEmptyBodyTest {

    private static SearchRequest query() {
        final SearchRequest query = new SearchRequest();
        query.setQueryId("q1");
        query.setSearchTerms("calendar");
        return query;
    }

    @Test
    public void googleReturnsNoResultsForAnEmptyBody() {
        final RestOperations rest = mock(RestOperations.class);
        when(rest.getForObject(anyString(), any(), anyMap())).thenReturn(null);
        final GoogleCustomSearchService service = new GoogleCustomSearchService();
        service.setRestOperations(rest);

        final SearchResults results = service.getSearchResults(mock(PortletRequest.class), query());
        assertEquals("q1", results.getQueryId());
        assertTrue(results.getSearchResult().isEmpty());
    }

    @Test
    public void gsaReturnsNoResultsForAnEmptyBody() {
        final RestTemplate rest = mock(RestTemplate.class);
        when(rest.getForObject(anyString(), any(), anyMap())).thenReturn(null);
        final GsaSearchService service = new GsaSearchService();
        service.setRestTemplate(rest);
        service.setUrlTemplate("http://gsa/search");

        final SearchResults results = service.getSearchResults(mock(PortletRequest.class), query());
        assertTrue(results.getSearchResult().isEmpty());
    }
}
