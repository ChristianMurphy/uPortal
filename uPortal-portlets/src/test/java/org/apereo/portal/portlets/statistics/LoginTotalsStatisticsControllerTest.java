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
package org.apereo.portal.portlets.statistics;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apereo.portal.events.aggr.AggregationInterval;
import org.apereo.portal.events.aggr.AggregationIntervalHelper;
import org.apereo.portal.events.aggr.groups.AggregatedGroupLookupDao;
import org.apereo.portal.events.aggr.groups.AggregatedGroupMapping;
import org.apereo.portal.events.aggr.login.LoginAggregation;
import org.apereo.portal.events.aggr.login.LoginAggregationDao;
import org.apereo.portal.events.aggr.login.LoginAggregationDiscriminatorImpl;
import org.apereo.portal.spring.web.portlet.ModelAndView;
import org.apereo.portal.web.view.ReportTableCsvView;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

/**
 * Runs a statistics report from the aggregation DAO to the response body: the controller builds the
 * report table, and the views the report resource uses write it as JSON (what the report charts
 * read) and CSV.
 */
public class LoginTotalsStatisticsControllerTest {

    private static final DateTime DAY1 = new DateTime(2026, 10, 1, 0, 0, DateTimeZone.UTC);
    private static final DateTime DAY2 = DAY1.plusDays(1);
    private static final DateTime DAY3 = DAY1.plusDays(2);

    private LoginTotalsStatisticsController controller;
    private LoginReportForm form;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        final AggregatedGroupMapping everyone = mock(AggregatedGroupMapping.class);
        when(everyone.getGroupName()).thenReturn("Everyone");
        final AggregatedGroupLookupDao groupDao = mock(AggregatedGroupLookupDao.class);
        when(groupDao.getGroupMapping(1L)).thenReturn(everyone);

        final AggregationIntervalHelper intervalHelper = mock(AggregationIntervalHelper.class);
        when(intervalHelper.getIntervalStartDateTimesBetween(
                        eq(AggregationInterval.DAY), any(), any(), anyInt()))
                .thenReturn(Arrays.asList(DAY1, DAY2, DAY3));

        // Logins on the first and third days, none recorded on the second. The aggregation mocks
        // are built before the DAO is stubbed, since Mockito can't stub inside another stubbing.
        final List<LoginAggregation> aggregations =
                Arrays.asList(
                        aggregation(everyone, DAY1, 3, 10), aggregation(everyone, DAY3, 1, 2));
        final LoginAggregationDao<LoginAggregation> loginDao = mock(LoginAggregationDao.class);
        when(loginDao.getAggregations(any(), any(), anySet(), any(AggregatedGroupMapping[].class)))
                .thenReturn(aggregations);

        controller = new LoginTotalsStatisticsController();
        ReflectionTestUtils.setField(
                controller,
                BaseStatisticsReportController.class,
                "intervalHelper",
                intervalHelper,
                AggregationIntervalHelper.class);
        ReflectionTestUtils.setField(
                controller,
                BaseStatisticsReportController.class,
                "aggregatedGroupDao",
                groupDao,
                AggregatedGroupLookupDao.class);
        ReflectionTestUtils.setField(
                controller,
                LoginTotalsStatisticsController.class,
                "aggregatedGroupDao",
                groupDao,
                AggregatedGroupLookupDao.class);
        ReflectionTestUtils.setField(
                controller,
                LoginTotalsStatisticsController.class,
                "loginDao",
                loginDao,
                LoginAggregationDao.class);

        form = new LoginReportForm();
        form.setInterval(AggregationInterval.DAY);
        form.setStart(new DateMidnight(2026, 10, 1, DateTimeZone.UTC));
        form.setEnd(new DateMidnight(2026, 10, 3, DateTimeZone.UTC));
        form.setGroups(Collections.singletonList(1L));
        form.setTotalLogins(true);
        form.setUniqueLogins(true);
    }

    private static LoginAggregation aggregation(
            AggregatedGroupMapping group, DateTime dateTime, int unique, int total) {
        final LoginAggregation aggregation = mock(LoginAggregation.class, RETURNS_DEEP_STUBS);
        when(aggregation.getDateTime()).thenReturn(dateTime);
        when(aggregation.getDateDimension().getDate()).thenReturn(dateTime.toDateMidnight());
        when(aggregation.getTimeDimension().getTime()).thenReturn(dateTime.toLocalTime());
        when(aggregation.getAggregationDiscriminator())
                .thenReturn(new LoginAggregationDiscriminatorImpl(group));
        when(aggregation.getUniqueLoginCount()).thenReturn(unique);
        when(aggregation.getLoginCount()).thenReturn(total);
        return aggregation;
    }

    @Test
    public void jsonReportIsTheDataTableTheChartsRead() throws Exception {
        final ModelAndView mav = controller.renderLoginAggregationReport(form);
        assertEquals("json", mav.getViewName());

        final MockHttpServletResponse response = new MockHttpServletResponse();
        new MappingJackson2JsonView()
                .render(mav.getModel(), new MockHttpServletRequest(), response);

        assertEquals(
                "{\"table\":{\"cols\":["
                        + "{\"id\":\"date\",\"label\":\"Date\",\"type\":\"date\",\"pattern\":\"\"},{\"id\":\"Everyone-uniqueLogins\",\"label\":\"Everyone"
                        + " - Unique Logins\","
                        + "\"type\":\"number\",\"pattern\":\"\"},{\"id\":\"Everyone-totalLogins\",\"label\":\"Everyone"
                        + " - Total Logins\",\"type\":\"number\",\"pattern\":\"\"}],\"rows\":["
                        + "{\"c\":[{\"v\":\"Date(2026,9,1)\"},{\"v\":3.0},{\"v\":10.0}]},"
                        + "{\"c\":[{\"v\":\"Date(2026,9,2)\"},{\"v\":0.0},{\"v\":0.0}]},"
                        + "{\"c\":[{\"v\":\"Date(2026,9,3)\"},{\"v\":1.0},{\"v\":2.0}]}]}}",
                response.getContentAsString());
    }

    @Test
    public void csvReportIsTheSameTable() throws Exception {
        form.setFormat(BaseReportForm.OutputFormat.csv);
        final ModelAndView mav = controller.renderLoginAggregationReport(form);
        assertEquals("dataTableCsvView", mav.getViewName());

        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addPreferredLocale(Locale.US);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        new ReportTableCsvView().render(mav.getModel(), request, response);

        assertEquals(
                "\"Date\",\"Everyone - Unique Logins\",\"Everyone - Total Logins\"\n"
                        + "2026-10-01,3,10\n"
                        + "2026-10-02,0,0\n"
                        + "2026-10-03,1,2\n",
                response.getContentAsString());
    }
}
