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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import org.apereo.portal.events.aggr.AggregationInterval;
import org.apereo.portal.events.aggr.AggregationIntervalHelper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class StatisticsPortletControllerTest {
    /**
     * The interval count runs from the start day's midnight to the end day's last second, in the
     * JVM's zone. 2026-03-08 is New York's spring-forward day.
     */
    @Test
    public void countsToTheLastSecondOfTheEndDay() {
        final TimeZone defaultZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            final AggregationIntervalHelper helper = mock(AggregationIntervalHelper.class);
            final StatisticsPortletController controller = new StatisticsPortletController();
            ReflectionTestUtils.setField(controller, "intervalHelper", helper);
            controller.getIntervalCount(
                    AggregationInterval.DAY, LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 9));
            final ZoneId zone = ZoneId.of("America/New_York");
            verify(helper)
                    .intervalsBetween(
                            eq(AggregationInterval.DAY),
                            eq(LocalDate.of(2026, 3, 8).atStartOfDay(zone)),
                            eq(LocalDate.of(2026, 3, 10).atStartOfDay(zone).minusSeconds(1)));
        } finally {
            TimeZone.setDefault(defaultZone);
        }
    }
}
