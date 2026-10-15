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
package org.apereo.portal.events.analytics;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * The requested dates become the same range the Joda-Time code computed: UTC midnight seen in the
 * default zone, to a calendar day later minus one second. The expected instants were recorded with
 * Joda-Time 2.14.3.
 */
public class AnalyticsPortalEventsControllerTest {
    private TimeZone defaultTimeZone;

    @Before
    public void setUp() {
        defaultTimeZone = TimeZone.getDefault();
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(defaultTimeZone);
    }

    @Test
    public void rangeMatchesJodaInSeveralZones() {
        for (final String zone : new String[] {"UTC", "America/New_York", "Asia/Kolkata"}) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            // The day of the 2026 US spring-forward change, and an ordinary range
            assertRange(
                    "2026-03-08",
                    "2026-03-08",
                    "2026-03-08T00:00:00Z",
                    zone.equals("America/New_York")
                            ? "2026-03-08T22:59:59Z"
                            : "2026-03-08T23:59:59Z");
            assertRange("2026-01-01", "2026-01-31", "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z");
        }
    }

    private void assertRange(String start, String end, String expectedStart, String expectedEnd) {
        final IAnalyticsPortalEventService service = mock(IAnalyticsPortalEventService.class);
        new AnalyticsPortalEventsController(service).getAnalytics(start, end, null, null);

        final ArgumentCaptor<ZonedDateTime> startDate =
                ArgumentCaptor.forClass(ZonedDateTime.class);
        final ArgumentCaptor<ZonedDateTime> endDate = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(service).getAnalytics(startDate.capture(), endDate.capture(), any(), any());

        assertEquals(ZoneId.systemDefault(), startDate.getValue().getZone());
        assertEquals(Instant.parse(expectedStart), startDate.getValue().toInstant());
        assertEquals(Instant.parse(expectedEnd), endDate.getValue().toInstant());
    }
}
