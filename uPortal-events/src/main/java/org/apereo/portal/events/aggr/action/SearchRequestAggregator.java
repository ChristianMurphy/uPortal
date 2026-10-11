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

import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apereo.portal.events.PortalEvent;
import org.apereo.portal.events.PortletActionExecutionEvent;
import org.apereo.portal.events.SearchRequestEvent;
import org.apereo.portal.events.aggr.AggregationInterval;
import org.apereo.portal.events.aggr.AggregationIntervalInfo;
import org.apereo.portal.events.aggr.BaseAggregationPrivateDao;
import org.apereo.portal.events.aggr.BaseIntervalAwarePortalEventAggregator;
import org.apereo.portal.events.aggr.DateDimension;
import org.apereo.portal.events.aggr.EventAggregationContext;
import org.apereo.portal.events.aggr.TimeDimension;
import org.apereo.portal.events.aggr.groups.AggregatedGroupMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SearchRequestAggregator
        extends BaseIntervalAwarePortalEventAggregator<
                PortalEvent, SearchRequestAggregationImpl, SearchRequestAggregationKey> {
    private static final String TARGET_FNAME = "search";
    private static final String TARGET_PARAM = "query";

    private SearchRequestAggregationPrivateDao searchRequestAggregationDao;

    @Autowired
    public void setSearchRequestAggregationDao(
            SearchRequestAggregationPrivateDao searchRequestAggregationDao) {
        this.searchRequestAggregationDao = searchRequestAggregationDao;
    }

    @Override
    public boolean supports(Class<? extends PortalEvent> type) {
        return PortletActionExecutionEvent.class.isAssignableFrom(type)
                || SearchRequestEvent.class.isAssignableFrom(type);
    }

    @Override
    public boolean supports(PortalEvent event) {
        return searchTermsOf(event) != null;
    }

    /**
     * The search terms of a search: a {@link SearchRequestEvent} from {@code /api/v6-0/search}, or
     * (stored before 6.0) an action of the {@code search} portlet with a {@code query} parameter.
     * Null for any other event, or when the terms are blank.
     */
    static String searchTermsOf(PortalEvent event) {
        String terms = null;
        if (event instanceof SearchRequestEvent) {
            terms = ((SearchRequestEvent) event).getSearchTerms();
        } else if (event instanceof PortletActionExecutionEvent) {
            final PortletActionExecutionEvent action = (PortletActionExecutionEvent) event;
            final List<String> values =
                    TARGET_FNAME.equals(action.getFname())
                            ? action.getParameters().get(TARGET_PARAM)
                            : null;
            terms = values == null || values.isEmpty() ? null : values.get(0);
        }
        return StringUtils.isBlank(terms) ? null : terms;
    }

    @Override
    protected BaseAggregationPrivateDao<SearchRequestAggregationImpl, SearchRequestAggregationKey>
            getAggregationDao() {
        return this.searchRequestAggregationDao;
    }

    @Override
    protected void updateAggregation(
            PortalEvent e,
            EventAggregationContext eventAggregationContext,
            AggregationIntervalInfo intervalInfo,
            SearchRequestAggregationImpl aggregation) {
        final int duration = intervalInfo.getDurationTo(e.getTimestampAsDate());
        aggregation.setDuration(duration);
        aggregation.increment();
    }

    @Override
    protected SearchRequestAggregationKey createAggregationKey(
            PortalEvent e,
            EventAggregationContext eventAggregationContext,
            AggregationIntervalInfo intervalInfo,
            AggregatedGroupMapping aggregatedGroup) {

        final TimeDimension timeDimension = intervalInfo.getTimeDimension();
        final DateDimension dateDimension = intervalInfo.getDateDimension();
        final AggregationInterval aggregationInterval = intervalInfo.getAggregationInterval();
        String query = searchTermsOf(e);
        SearchRequestAggregationKey key =
                new SearchRequestAggregationKeyImpl(
                        dateDimension, timeDimension, aggregationInterval, aggregatedGroup, query);
        return key;
    }
}
