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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import org.junit.Test;

public class TemporalAmountEditorTest {

    /**
     * What Joda-Time's DateTime.minus and plus gave for each value the ReadablePeriodEditor
     * accepted, including those in portal.properties, in UTC, New York and London, around midnight
     * and the 2026 DST changes (none of the results lands in a repeated or skipped hour). Recorded
     * with Joda-Time 2.14.3 before it was removed.
     */
    private static final String JODA_RESULTS = "joda-period-results.txt";

    @Test
    public void addsAndSubtractsLikeJodaPeriods() throws IOException {
        final TemporalAmountEditor editor = new TemporalAmountEditor();
        int checked = 0;
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                getClass().getResourceAsStream(JODA_RESULTS),
                                StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith("#")) {
                    continue;
                }
                final String[] fields = line.split(" ");
                editor.setAsText(fields[0]);
                final TemporalAmount amount = (TemporalAmount) editor.getValue();
                final ZonedDateTime javaTime =
                        Instant.parse(fields[2]).atZone(ZoneId.of(fields[1]));
                assertEquals(
                        "minus " + line,
                        Instant.parse(fields[3]),
                        javaTime.minus(amount).toInstant());
                assertEquals(
                        "plus " + line,
                        Instant.parse(fields[4]),
                        javaTime.plus(amount).toInstant());
                checked++;
            }
        }
        assertEquals(15 * 3 * 8, checked);
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
