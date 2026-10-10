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
package org.apereo.portal.spring.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class TemporalAmountEditorTest {

    /** Values the Joda-Time ReadablePeriodEditor accepted, including those in portal.properties. */
    private static final String[] JODA_VALUES = {
        "PT30S",
        "PT1H",
        "P1D",
        "P30D",
        "P1W",
        "P1W2D",
        "P1M",
        "P1Y",
        "P1DT12H",
        "P2DT1H30M",
        "PT1.5S",
        "PT90M",
        "p1d",
        "pt30s",
        "P-1D"
    };

    private static final String[] ZONES = {"UTC", "America/New_York", "Europe/London"};

    /**
     * Instants around midnight and the 2026 DST transitions, chosen so that no result lands in an
     * hour that a DST change repeats or skips.
     */
    private static final String[] INSTANTS = {
        "2026-01-15T12:00:00Z",
        "2026-03-08T06:30:00Z", // 01:30 New York, just before spring forward
        "2026-03-08T08:00:00Z", // 04:00 New York, after spring forward
        "2026-03-29T00:30:00Z", // London, just before spring forward
        "2026-11-01T04:30:00Z", // 00:30 New York, before fall back
        "2026-11-01T07:30:00Z", // 02:30 New York, after fall back
        "2026-12-31T23:59:59Z",
        "2028-02-29T10:00:00Z"
    };

    @Test
    public void addsAndSubtractsLikeJodaPeriods() {
        final TemporalAmountEditor editor = new TemporalAmountEditor();
        for (final String text : JODA_VALUES) {
            editor.setAsText(text);
            final TemporalAmount amount = (TemporalAmount) editor.getValue();
            final org.joda.time.Period jodaPeriod = org.joda.time.Period.parse(text);
            for (final String zone : ZONES) {
                for (final String instant : INSTANTS) {
                    final ZonedDateTime javaTime =
                            ZonedDateTime.parse(instant).withZoneSameInstant(ZoneId.of(zone));
                    final DateTime jodaTime =
                            new DateTime(instant).withZone(DateTimeZone.forID(zone));
                    final String context = text + " " + zone + " " + instant;
                    assertEquals(
                            "minus " + context,
                            jodaTime.minus(jodaPeriod).getMillis(),
                            javaTime.minus(amount).toInstant().toEpochMilli());
                    assertEquals(
                            "plus " + context,
                            jodaTime.plus(jodaPeriod).getMillis(),
                            javaTime.plus(amount).toInstant().toEpochMilli());
                }
            }
        }
    }

    @Test
    public void dateOnlyTextIsAPeriodAndTimeOnlyTextIsADuration() {
        assertEquals(Period.ofDays(1), TemporalAmountEditor.parse("P1D"));
        assertEquals(Duration.ofSeconds(30), TemporalAmountEditor.parse("PT30S"));
        final TemporalAmount mixed = TemporalAmountEditor.parse("P1DT12H");
        assertEquals("P1DT12H", mixed.toString());
        assertEquals(1, mixed.get(ChronoUnit.DAYS));
        assertEquals(12 * 3600, mixed.get(ChronoUnit.SECONDS));
        assertTrue(mixed.getUnits().contains(ChronoUnit.NANOS));
    }

    @Test
    public void dayAcrossSpringForwardIsTwentyThreeHours() {
        final ZonedDateTime afterChange =
                ZonedDateTime.of(2026, 3, 8, 12, 0, 0, 0, ZoneId.of("America/New_York"));
        final ZonedDateTime dayBefore = afterChange.minus(TemporalAmountEditor.parse("P1D"));
        assertEquals(
                Duration.ofHours(23),
                Duration.between(dayBefore.toInstant(), afterChange.toInstant()));
        final ZonedDateTime hoursBefore = afterChange.minus(TemporalAmountEditor.parse("PT24H"));
        assertEquals(7, hoursBefore.getDayOfMonth());
        assertEquals(11, hoursBefore.getHour());
    }

    @Test
    public void rejectsInvalidText() {
        final TemporalAmountEditor editor = new TemporalAmountEditor();
        for (final String text : new String[] {"30", "1D", "P", "PT", "P1DT", "P1H", ""}) {
            try {
                editor.setAsText(text);
                fail("Expected IllegalArgumentException for '" + text + "'");
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }
}
