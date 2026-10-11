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
package org.apereo.portal.events;

/**
 * A search the user submitted, published by {@code /api/v6-0/search} for searches that the search
 * components start. {@link org.apereo.portal.events.aggr.action.SearchRequestAggregator} counts it
 * with the portlet action events that the Search portlet's searches produced before 6.0.
 */
public class SearchRequestEvent extends PortalEvent {
    private static final long serialVersionUID = 1L;

    private final String searchTerms;

    SearchRequestEvent() {
        super();
        this.searchTerms = "";
    }

    SearchRequestEvent(PortalEventBuilder portalEventBuilder, String searchTerms) {
        super(portalEventBuilder);
        this.searchTerms = searchTerms;
    }

    /** What the user searched for. */
    public String getSearchTerms() {
        return searchTerms;
    }

    @Override
    public String toString() {
        return super.toString() + ", searchTerms=" + searchTerms;
    }
}
