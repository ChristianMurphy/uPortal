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

import java.time.Instant;
import java.util.TimeZone;
import org.apereo.portal.concurrency.locking.IClusterLockService;
import org.apereo.portal.events.aggr.dao.IEventAggregationManagementDao;
import org.apereo.portal.events.aggr.session.EventSessionDao;
import org.apereo.portal.spring.properties.TemporalAmountEditor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * The configured session duration is applied in the JVM's zone, the way the Joda-Time period was
 * applied to the DateTime Jadira read. The expected instants are the ones Joda-Time computed.
 */
public class PortalEventSessionPurgerImplTest {
    private TimeZone jvmZone;

    private final PortalEventSessionPurgerImpl purger = new PortalEventSessionPurgerImpl();
    private final EventSessionDao eventSessionDao = mock(EventSessionDao.class);
    private final IEventAggregatorStatus aggregatorStatus = mock(IEventAggregatorStatus.class);

    @Before
    public void setUp() {
        jvmZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
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

    @After
    public void tearDown() {
        TimeZone.setDefault(jvmZone);
    }

    @Test
    public void oneDayAcrossSpringForwardIsACalendarDay() {
        // Noon on the day of the 2026 spring-forward change: a calendar day back is 23 hours
        assertPurgeDate("P1D", "2026-03-08T16:00:00Z", "2026-03-07T17:00:00Z");
    }

    @Test
    public void hoursAcrossFallBackAreElapsedTime() {
        // Noon EST on the day of the fall-back change, 24 elapsed hours back
        assertPurgeDate("PT24H", "2026-11-01T17:00:00Z", "2026-10-31T17:00:00Z");
    }

    @Test
    public void mixedPeriodMatchesJoda() {
        // 06:00 EDT the day after spring-forward: a calendar day back, then 12 elapsed hours
        assertPurgeDate("P1DT12H", "2026-03-09T10:00:00Z", "2026-03-07T22:00:00Z");
    }

    private void assertPurgeDate(String configured, String lastEventDate, String expected) {
        when(aggregatorStatus.getLastEventDate()).thenReturn(Instant.parse(lastEventDate));
        purger.setEventSessionDuration(TemporalAmountEditor.parse(configured));

        purger.doPurgeEventSessions();

        final ArgumentCaptor<Instant> purgeDate = ArgumentCaptor.forClass(Instant.class);
        verify(eventSessionDao).purgeEventSessionsBefore(purgeDate.capture());
        assertEquals(Instant.parse(expected), purgeDate.getValue());
    }
}
