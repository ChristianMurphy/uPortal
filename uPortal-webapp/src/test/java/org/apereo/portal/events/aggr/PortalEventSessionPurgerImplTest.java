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
package org.apereo.portal.events.aggr;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apereo.portal.concurrency.locking.IClusterLockService;
import org.apereo.portal.events.aggr.dao.IEventAggregationManagementDao;
import org.apereo.portal.events.aggr.session.EventSessionDao;
import org.apereo.portal.spring.properties.TemporalAmountEditor;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** The configured session duration is applied the way the Joda-Time period was. */
public class PortalEventSessionPurgerImplTest {
    private static final DateTimeZone NEW_YORK = DateTimeZone.forID("America/New_York");

    private final PortalEventSessionPurgerImpl purger = new PortalEventSessionPurgerImpl();
    private final EventSessionDao eventSessionDao = mock(EventSessionDao.class);
    private final IEventAggregatorStatus aggregatorStatus = mock(IEventAggregatorStatus.class);

    @Before
    public void setUp() {
        final IClusterLockService clusterLockService = mock(IClusterLockService.class);
        when(clusterLockService.isLockOwner(any())).thenReturn(true);
        final IEventAggregationManagementDao managementDao =
                mock(IEventAggregationManagementDao.class);
        when(managementDao.getEventAggregatorStatus(
                        IEventAggregatorStatus.ProcessingType.AGGREGATION, false))
                .thenReturn(aggregatorStatus);
        purger.setClusterLockService(clusterLockService);
        purger.setEventAggregationManagementDao(managementDao);
        purger.setEventSessionDao(eventSessionDao);
    }

    @Test
    public void oneDayAcrossSpringForwardIsACalendarDay() {
        // Noon on the day of the 2026 spring-forward change: a calendar day back is 23 hours
        assertPurgeDate("P1D", Period.days(1), new DateTime(2026, 3, 8, 12, 0, NEW_YORK));
    }

    @Test
    public void hoursAcrossFallBackAreElapsedTime() {
        assertPurgeDate("PT24H", Period.hours(24), new DateTime(2026, 11, 1, 12, 0, NEW_YORK));
    }

    @Test
    public void mixedPeriodMatchesJoda() {
        assertPurgeDate(
                "P1DT12H", Period.parse("P1DT12H"), new DateTime(2026, 3, 9, 6, 0, NEW_YORK));
    }

    private void assertPurgeDate(String configured, Period jodaPeriod, DateTime lastEventDate) {
        when(aggregatorStatus.getLastEventDate()).thenReturn(lastEventDate);
        purger.setEventSessionDuration(TemporalAmountEditor.parse(configured));

        purger.doPurgeEventSessions();

        final ArgumentCaptor<DateTime> purgeDate = ArgumentCaptor.forClass(DateTime.class);
        verify(eventSessionDao).purgeEventSessionsBefore(purgeDate.capture());
        assertEquals(lastEventDate.minus(jodaPeriod), purgeDate.getValue());
    }
}
