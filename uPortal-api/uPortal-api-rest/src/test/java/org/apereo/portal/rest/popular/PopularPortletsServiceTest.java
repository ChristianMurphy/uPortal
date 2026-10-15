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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apereo.portal.events.aggr.AggregationInterval;
import org.apereo.portal.events.aggr.groups.AggregatedGroupLookupDao;
import org.apereo.portal.events.aggr.groups.AggregatedGroupMapping;
import org.apereo.portal.events.aggr.portletlayout.PortletLayoutAggregation;
import org.apereo.portal.events.aggr.portletlayout.PortletLayoutAggregationDao;
import org.apereo.portal.events.aggr.portlets.AggregatedPortletMapping;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionId;
import org.apereo.portal.security.IAuthorizationPrincipal;
import org.apereo.portal.security.IAuthorizationService;
import org.apereo.portal.security.IPerson;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PopularPortletsServiceTest {

    private PortletLayoutAggregationDao<PortletLayoutAggregation> portletLayoutDao;
    private AggregatedGroupMapping everyone;
    private IPortletDefinitionDao portletDefinitionDao;
    private IAuthorizationPrincipal principal;
    private IPerson user;
    private List<PortletLayoutAggregation> aggregations;
    private PopularPortletsService service;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        portletLayoutDao = mock(PortletLayoutAggregationDao.class);
        final AggregatedGroupLookupDao groupLookupDao = mock(AggregatedGroupLookupDao.class);
        everyone = mock(AggregatedGroupMapping.class);
        when(groupLookupDao.getGroupMapping("local.0")).thenReturn(everyone);
        portletDefinitionDao = mock(IPortletDefinitionDao.class);
        final IAuthorizationService authorizationService = mock(IAuthorizationService.class);
        principal = mock(IAuthorizationPrincipal.class);
        user = mock(IPerson.class);
        when(user.getUserName()).thenReturn("student");
        when(authorizationService.newPrincipal("student", IPerson.class)).thenReturn(principal);

        aggregations = new ArrayList<>();
        when(portletLayoutDao.getAggregationsForAllPortlets(
                        any(ZonedDateTime.class),
                        any(ZonedDateTime.class),
                        eq(AggregationInterval.DAY),
                        eq(everyone)))
                .thenReturn(aggregations);

        service =
                new PopularPortletsService(
                        portletLayoutDao,
                        groupLookupDao,
                        portletDefinitionDao,
                        authorizationService) {
                    @Override
                    String everyoneGroupKey() {
                        return "local.0";
                    }
                };
    }

    private void added(String fname, int count) {
        final AggregatedPortletMapping mapping = mock(AggregatedPortletMapping.class);
        when(mapping.getFname()).thenReturn(fname);
        final PortletLayoutAggregation aggregation = mock(PortletLayoutAggregation.class);
        when(aggregation.getPortletMapping()).thenReturn(mapping);
        when(aggregation.getAddCount()).thenReturn(count);
        aggregations.add(aggregation);
    }

    private IPortletDefinition portlet(String fname, String id, boolean canSubscribe) {
        final IPortletDefinitionId definitionId = mock(IPortletDefinitionId.class);
        when(definitionId.getStringId()).thenReturn(id);
        final IPortletDefinition definition = mock(IPortletDefinition.class);
        when(definition.getPortletDefinitionId()).thenReturn(definitionId);
        when(definition.getTitle("fr_CA")).thenReturn("Titre " + fname);
        when(definition.getDescription("fr_CA")).thenReturn("Description " + fname);
        when(portletDefinitionDao.getPortletDefinitionByFname(fname)).thenReturn(definition);
        when(principal.canSubscribe(id)).thenReturn(canSubscribe);
        return definition;
    }

    private PopularPortlets popular(int days) {
        return service.getPopularPortlets(user, days, Locale.CANADA_FRENCH, "/uPortal");
    }

    @Test
    public void sumsTheDailyAdditionsOfEachPortlet() {
        portlet("calendar", "11", true);
        portlet("xkcd", "12", true);
        added("calendar", 2);
        added("xkcd", 1);
        added("calendar", 3);

        final PopularPortlets result = popular(30);

        assertEquals(30, result.getDays());
        assertEquals(2, result.getPortlets().size());
        final PopularPortlet calendar = result.getPortlets().get(0);
        assertEquals("calendar", calendar.getFname());
        assertEquals("Titre calendar", calendar.getTitle());
        assertEquals("Description calendar", calendar.getDescription());
        assertEquals("/uPortal/p/calendar", calendar.getUrl());
        assertEquals(5, calendar.getCount());
        assertEquals(1, result.getPortlets().get(1).getCount());
    }

    @Test
    public void listsTheMostAddedFirstAndTiesByFname() {
        portlet("b", "1", true);
        portlet("a", "2", true);
        portlet("c", "3", true);
        added("b", 1);
        added("a", 1);
        added("c", 4);

        final List<String> fnames = new ArrayList<>();
        for (final PopularPortlet portlet : popular(30).getPortlets()) {
            fnames.add(portlet.getFname());
        }
        assertEquals(Arrays.asList("c", "a", "b"), fnames);
    }

    @Test
    public void leavesOutPortletsTheUserMayNotSubscribeTo() {
        portlet("calendar", "11", true);
        portlet("portlet-admin", "12", false);
        added("calendar", 1);
        added("portlet-admin", 9);

        final List<PopularPortlet> portlets = popular(30).getPortlets();

        assertEquals(1, portlets.size());
        assertEquals("calendar", portlets.get(0).getFname());
    }

    @Test
    public void leavesOutPortletsThatNoLongerExist() {
        added("deleted", 3);

        assertTrue(popular(30).getPortlets().isEmpty());
    }

    @Test
    public void keepsAMissingDescription() {
        final IPortletDefinition calendar = portlet("calendar", "11", true);
        when(calendar.getDescription("fr_CA")).thenReturn(null);
        added("calendar", 1);

        assertNull(popular(30).getPortlets().get(0).getDescription());
    }

    @Test
    public void countsTheGivenNumberOfDaysEndingNow() {
        final long before = System.currentTimeMillis();
        popular(7);
        final long after = System.currentTimeMillis();

        final ArgumentCaptor<ZonedDateTime> start = ArgumentCaptor.forClass(ZonedDateTime.class);
        final ArgumentCaptor<ZonedDateTime> end = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(portletLayoutDao)
                .getAggregationsForAllPortlets(
                        start.capture(), end.capture(), eq(AggregationInterval.DAY), eq(everyone));
        assertTrue(end.getValue().toInstant().toEpochMilli() >= before);
        assertTrue(end.getValue().toInstant().toEpochMilli() <= after);
        assertEquals(end.getValue().minusDays(7), start.getValue());
    }
}
