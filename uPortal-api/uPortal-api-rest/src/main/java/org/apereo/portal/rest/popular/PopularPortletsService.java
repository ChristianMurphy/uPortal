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
package org.apereo.portal.rest.popular;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apereo.portal.events.aggr.AggregationInterval;
import org.apereo.portal.events.aggr.groups.AggregatedGroupLookupDao;
import org.apereo.portal.events.aggr.groups.AggregatedGroupMapping;
import org.apereo.portal.events.aggr.portletlayout.PortletLayoutAggregation;
import org.apereo.portal.events.aggr.portletlayout.PortletLayoutAggregationDao;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.security.IAuthorizationPrincipal;
import org.apereo.portal.security.IAuthorizationService;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.services.GroupService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Counts how many times users added each portlet to their layouts, from the daily portlet-layout
 * aggregations of the group of all users, as the Popular Portlets portlet does ({@code
 * PopularPortletsController}). Portlets that no longer exist, and portlets the user may not
 * subscribe to, are left out.
 */
@Service
public class PopularPortletsService {

    private static final AggregationInterval INTERVAL = AggregationInterval.DAY;

    private final PortletLayoutAggregationDao<PortletLayoutAggregation> portletLayoutDao;
    private final AggregatedGroupLookupDao aggregatedGroupLookupDao;
    private final IPortletDefinitionDao portletDefinitionDao;
    private final IAuthorizationService authorizationService;

    @Autowired
    public PopularPortletsService(
            PortletLayoutAggregationDao<PortletLayoutAggregation> portletLayoutDao,
            AggregatedGroupLookupDao aggregatedGroupLookupDao,
            IPortletDefinitionDao portletDefinitionDao,
            IAuthorizationService authorizationService) {
        this.portletLayoutDao = portletLayoutDao;
        this.aggregatedGroupLookupDao = aggregatedGroupLookupDao;
        this.portletDefinitionDao = portletDefinitionDao;
        this.authorizationService = authorizationService;
    }

    /**
     * The portlets added in the {@code days} days before now, most added first.
     *
     * @param contextPath the portal's context path, for the portlets' URLs
     */
    public PopularPortlets getPopularPortlets(
            IPerson user, int days, Locale locale, String contextPath) {
        final ZonedDateTime end = ZonedDateTime.now(ZoneId.systemDefault());
        final ZonedDateTime begin = end.minusDays(days);
        final AggregatedGroupMapping everyone =
                aggregatedGroupLookupDao.getGroupMapping(everyoneGroupKey());
        final List<PortletLayoutAggregation> aggregations =
                portletLayoutDao.getAggregationsForAllPortlets(begin, end, INTERVAL, everyone);

        final IAuthorizationPrincipal principal =
                authorizationService.newPrincipal(user.getUserName(), IPerson.class);
        final String localeName = locale.toString();
        final Map<String, Counter> counters = new HashMap<>();
        for (final PortletLayoutAggregation aggregation : aggregations) {
            final String fname = aggregation.getPortletMapping().getFname();
            Counter counter = counters.get(fname);
            if (counter == null) {
                final IPortletDefinition definition =
                        portletDefinitionDao.getPortletDefinitionByFname(fname);
                counter = new Counter(definition);
                counters.put(fname, counter);
            }
            counter.count += aggregation.getAddCount();
        }

        final List<PopularPortlet> portlets = new ArrayList<>();
        for (final Map.Entry<String, Counter> entry : counters.entrySet()) {
            final IPortletDefinition definition = entry.getValue().definition;
            if (definition == null
                    || !principal.canSubscribe(definition.getPortletDefinitionId().getStringId())) {
                continue;
            }
            final String fname = entry.getKey();
            portlets.add(
                    new PopularPortlet(
                            fname,
                            definition.getTitle(localeName),
                            definition.getDescription(localeName),
                            contextPath + "/p/" + fname,
                            entry.getValue().count));
        }
        portlets.sort(
                Comparator.comparingInt(PopularPortlet::getCount)
                        .reversed()
                        .thenComparing(PopularPortlet::getFname));
        return new PopularPortlets(days, portlets);
    }

    /** The key of the group of all users, whose aggregations count every user's additions. */
    String everyoneGroupKey() {
        return GroupService.getRootGroup(IPerson.class).getKey();
    }

    private static final class Counter {
        /** Null when the portlet no longer exists. */
        private final @Nullable IPortletDefinition definition;

        private int count;

        Counter(@Nullable IPortletDefinition definition) {
            this.definition = definition;
        }
    }
}
