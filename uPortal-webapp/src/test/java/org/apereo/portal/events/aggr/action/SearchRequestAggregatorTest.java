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
package org.apereo.portal.events.aggr.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apereo.portal.events.PortalEvent;
import org.apereo.portal.events.PortletActionExecutionEvent;
import org.apereo.portal.events.SearchRequestEvent;
import org.apereo.portal.spring.beans.factory.ObjectMapperFactoryBean;
import org.junit.Before;
import org.junit.Test;

/**
 * Search statistics count the searches stored before 6.0 (the Search portlet's actions with a
 * {@code query} parameter) and the searches {@code /api/v6-0/search} publishes, read back from
 * the JSON the raw event store keeps.
 */
public class SearchRequestAggregatorTest {

    private static final String STORED =
            "\"timestamp\":1371745598080,\"serverId\":\"example.com\","
                    + "\"eventSessionId\":\"1234567890123_student_AAAAAAAAAAA\",\"userName\":\"student\"";

    /** An action of the Search portlet, as uPortal 5.17 stored it. */
    private static final String PORTLET_SEARCH =
            "{\"@c\":\".PortletActionExecutionEvent\","
                    + STORED
                    + ",\"fname\":\"search\",\"windowState\":\"maximized\",\"portletMode\":\"view\","
                    + "\"executionTimeNano\":12345,\"parameters\":{\"query\":[\"cartoon\"]}}";

    /** A search the search components submitted in 6.0. */
    private static final String API_SEARCH =
            "{\"@c\":\".SearchRequestEvent\"," + STORED + ",\"searchTerms\":\"campus map\"}";

    private ObjectMapper mapper;
    private final SearchRequestAggregator aggregator = new SearchRequestAggregator();

    @Before
    public void setUp() throws Exception {
        final ObjectMapperFactoryBean factory = new ObjectMapperFactoryBean();
        factory.afterPropertiesSet();
        mapper = factory.getObject();
    }

    private PortalEvent read(String json) throws Exception {
        return mapper.readValue(json, PortalEvent.class);
    }

    @Test
    public void countsTheSearchPortletsStoredActions() throws Exception {
        final PortalEvent event = read(PORTLET_SEARCH);

        assertTrue(event instanceof PortletActionExecutionEvent);
        assertTrue(aggregator.supports(PortletActionExecutionEvent.class));
        assertTrue(aggregator.supports(event));
        assertEquals("cartoon", SearchRequestAggregator.searchTermsOf(event));
    }

    @Test
    public void countsTheSearchesTheApiPublishes() throws Exception {
        final PortalEvent event = read(API_SEARCH);

        assertTrue(event instanceof SearchRequestEvent);
        assertTrue(aggregator.supports(SearchRequestEvent.class));
        assertTrue(aggregator.supports(event));
        assertEquals("campus map", SearchRequestAggregator.searchTermsOf(event));
    }

    @Test
    public void leavesOutOtherPortletsBlankSearchesAndOtherEvents() throws Exception {
        final PortalEvent otherPortlet = read(PORTLET_SEARCH.replace("\"search\"", "\"calendar\""));
        final PortalEvent noQuery = read(PORTLET_SEARCH.replace("\"query\"", "\"q\""));
        final PortalEvent blankQuery = read(PORTLET_SEARCH.replace("[\"cartoon\"]", "[\" \"]"));
        final PortalEvent noValue = read(PORTLET_SEARCH.replace("[\"cartoon\"]", "[]"));
        final PortalEvent blankSearch = read(API_SEARCH.replace("campus map", "  "));
        final PortalEvent logout = read("{\"@c\":\".LogoutEvent\"," + STORED + "}");

        for (PortalEvent event :
                new PortalEvent[] {otherPortlet, noQuery, blankQuery, noValue, blankSearch, logout}) {
            assertFalse(event.toString(), aggregator.supports(event));
            assertNull(SearchRequestAggregator.searchTermsOf(event));
        }
    }
}
