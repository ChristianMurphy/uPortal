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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

/**
 * The interval starts and ends equal the ones uPortal 5.17 computed with Joda-Time, also in zones
 * whose daylight saving change isn't on a local hour (Pacific/Chatham, Australia/Lord_Howe) or
 * repeats midnight (America/Havana, Atlantic/Azores). Stepping through a day always moves forward.
 */
public class AggregationIntervalJodaParityTest {
    private static final String JODA_RESULTS = "joda-interval-results.txt";
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSxxx", Locale.ROOT);

    /** At most this many intervals in one day, so a loop fails the test instead of hanging it */
    private static final int MAX_STEPS = 2000;

    @Test
    public void startsAndEndsAsJodaTimeDid() throws IOException {
        final List<String> differences = new ArrayList<>();
        int checked = 0;
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                getClass().getResourceAsStream(JODA_RESULTS),
                                StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                final String[] fields = line.split(" ");
                final ZoneId zone = ZoneId.of(fields[0]);
                final ZonedDateTime instant =
                        OffsetDateTime.parse(fields[1]).atZoneSameInstant(zone);
                final AggregationInterval interval = AggregationInterval.valueOf(fields[2]);
                final String expected = fields[3] + " " + fields[4];
                final String actual =
                        FORMAT.format(interval.determineStart(instant))
                                + " "
                                + FORMAT.format(interval.determineEnd(instant));
                if (!expected.equals(actual)) {
                    differences.add(
                            fields[0]
                                    + " "
                                    + fields[1]
                                    + " "
                                    + interval
                                    + ": Joda-Time gave "
                                    + expected
                                    + ", now "
                                    + actual);
                }
                checked++;
            }
        }
        assertEquals("cases in " + JODA_RESULTS, 532, checked);
        if (!differences.isEmpty()) {
            fail(
                    differences.size()
                            + " of "
                            + checked
                            + " differ from Joda-Time, such as\n"
                            + String.join(
                                    "\n",
                                    differences.subList(0, Math.min(10, differences.size()))));
        }
    }

    @Test
    public void fiveMinutesStepForwardThroughChathamsFallBack() {
        // 2026-04-05: 03:45 +13:45 becomes 02:45 +12:45, a day of 25 hours. Joda-Time gave 300
        // intervals.
        assertEquals(
                300,
                stepThroughDay(AggregationInterval.FIVE_MINUTE, "Pacific/Chatham", 2026, 4, 5));
    }

    @Test
    public void fiveMinutesStepForwardThroughChathamsSpringForward() {
        // 2026-09-27: 02:45 +12:45 becomes 03:45 +13:45, a day of 23 hours
        assertEquals(
                276,
                stepThroughDay(AggregationInterval.FIVE_MINUTE, "Pacific/Chatham", 2026, 9, 27));
    }

    @Test
    public void fiveMinutesStepForwardThroughLordHowesFallBack() {
        // 2026-04-05: 02:00 +11:00 becomes 01:30 +10:30, a day of 24.5 hours
        assertEquals(
                294,
                stepThroughDay(AggregationInterval.FIVE_MINUTE, "Australia/Lord_Howe", 2026, 4, 5));
    }

    @Test
    public void daysStepForwardThroughHavanasMidnightFallBack() {
        // 2026-11-01: 01:00 -04:00 becomes 00:00 -05:00. An instant in the repeated hour belongs to
        // the day that starts at the second midnight, as with Joda-Time.
        final ZoneId havana = ZoneId.of("America/Havana");
        final ZonedDateTime repeated =
                OffsetDateTime.parse("2026-11-01T00:30-05:00").atZoneSameInstant(havana);
        assertEquals(
                "2026-11-01T00:00:00.000-05:00",
                FORMAT.format(AggregationInterval.DAY.determineStart(repeated)));
        assertEquals(1, stepThroughDay(AggregationInterval.DAY, "America/Havana", 2026, 11, 1));
    }

    @Test
    public void daysStepForwardThroughAzoresMidnightFallBack() {
        // 2026-10-25: 01:00 +00:00 becomes 00:00 -01:00
        final ZoneId azores = ZoneId.of("Atlantic/Azores");
        final ZonedDateTime repeated =
                OffsetDateTime.parse("2026-10-25T00:30-01:00").atZoneSameInstant(azores);
        assertEquals(
                "2026-10-25T00:00:00.000-01:00",
                FORMAT.format(AggregationInterval.DAY.determineStart(repeated)));
        assertEquals(1, stepThroughDay(AggregationInterval.DAY, "Atlantic/Azores", 2026, 10, 25));
    }

    /**
     * Steps from the start of the day to the start of the next one, as
     * AggregationIntervalHelperImpl.getIntervalStartDateTimesBetween does, and counts the
     * intervals. Fails when a step doesn't move forward or the count passes {@link #MAX_STEPS}.
     */
    private static int stepThroughDay(
            AggregationInterval interval, String zoneId, int year, int month, int day) {
        final ZoneId zone = ZoneId.of(zoneId);
        final ZonedDateTime end = LocalDate.of(year, month, day).plusDays(1).atStartOfDay(zone);
        ZonedDateTime start =
                interval.determineStart(LocalDate.of(year, month, day).atStartOfDay(zone));
        int count = 0;
        while (start.isBefore(end)) {
            final ZonedDateTime next = interval.determineStart(interval.determineEnd(start));
            assertTrue(
                    interval
                            + " in "
                            + zoneId
                            + " doesn't move forward from "
                            + FORMAT.format(start)
                            + ": the next interval starts at "
                            + FORMAT.format(next),
                    next.isAfter(start));
            start = next;
            count++;
            assertTrue(
                    interval + " in " + zoneId + ": more than " + MAX_STEPS + " intervals",
                    count <= MAX_STEPS);
        }
        return count;
    }
}
