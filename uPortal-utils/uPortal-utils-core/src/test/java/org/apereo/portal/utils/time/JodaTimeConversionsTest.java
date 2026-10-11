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
package org.apereo.portal.utils.time;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class JodaTimeConversionsTest {

    @Test
    public void keepsInstantAndZoneBothWays() {
        for (final String zone : new String[] {"UTC", "America/New_York", "+05:30"}) {
            final DateTime joda =
                    new DateTime(2026, 3, 8, 1, 30, 15, 123, DateTimeZone.forID(zone));
            final ZonedDateTime javaTime = JodaTimeConversions.toZonedDateTime(joda);
            assertEquals(zone, ZoneId.of(zone), javaTime.getZone());
            assertEquals(zone, joda.getMillis(), javaTime.toInstant().toEpochMilli());
            assertEquals(zone, joda, JodaTimeConversions.toDateTime(javaTime));
        }
    }

    @Test
    public void mapsTheZOffsetToJodaUtc() {
        final ZonedDateTime utc = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(DateTimeZone.UTC, JodaTimeConversions.toDateTime(utc).getZone());
    }

    @Test
    public void periodArithmeticMatchesJodaAcrossDst() {
        final DateTimeZone newYork = DateTimeZone.forID("America/New_York");
        // Noon on the days of the 2026 spring-forward and fall-back changes
        for (final DateTime noon :
                new DateTime[] {
                    new DateTime(2026, 3, 8, 12, 0, newYork),
                    new DateTime(2026, 11, 1, 12, 0, newYork)
                }) {
            assertEquals(
                    noon.minus(org.joda.time.Period.days(1)),
                    JodaTimeConversions.minus(noon, Period.ofDays(1)));
            assertEquals(
                    noon.minus(org.joda.time.Period.hours(24)),
                    JodaTimeConversions.minus(noon, Duration.ofHours(24)));
            assertEquals(
                    noon.plus(org.joda.time.Period.days(30)),
                    JodaTimeConversions.plus(noon, Period.ofDays(30)));
        }
    }
}
