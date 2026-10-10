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
package org.apereo.portal.portlet.marketplace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;

public class PortletReleaseNotesTest {

    /** The pattern MarketplacePortletDefinition parsed with Joda-Time. */
    private static final DateTimeFormatter JODA_FORMAT =
            DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    public void jsonHasEpochMillisWhicheverModulesAreRegistered() throws Exception {
        final PortletReleaseNotes notes = new PortletReleaseNotes();
        notes.setReleaseDate(LocalDateTime.of(2014, 1, 2, 3, 4, 5));
        notes.setInitialReleaseDate(LocalDateTime.of(2013, 7, 1, 0, 0, 0));
        final long releaseMillis = new DateTime(2014, 1, 2, 3, 4, 5).getMillis();
        final long initialMillis = new DateTime(2013, 7, 1, 0, 0, 0).getMillis();

        for (final ObjectMapper mapper :
                new ObjectMapper[] {
                    new ObjectMapper(), new ObjectMapper().findAndRegisterModules()
                }) {
            final JsonNode json = mapper.readTree(mapper.writeValueAsString(notes));
            assertEquals(releaseMillis, json.get("releaseDate").longValue());
            assertEquals(initialMillis, json.get("initialReleaseDate").longValue());
        }
    }

    @Test
    public void unsetDatesAreNullInJson() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode json = mapper.readTree(mapper.writeValueAsString(new PortletReleaseNotes()));
        assertTrue(json.get("releaseDate").isNull());
        assertTrue(json.get("initialReleaseDate").isNull());
    }

    @Test
    public void parsesTheValuesJodaParsed() {
        for (final String text :
                new String[] {
                    "2014-01-02 03:04:05",
                    "2014-1-2 3:4:5",
                    "14-01-02 03:04:05",
                    "2016-02-29 23:59:59"
                }) {
            final DateTime joda = JODA_FORMAT.parseDateTime(text);
            final LocalDateTime expected =
                    LocalDateTime.of(
                            joda.getYear(),
                            joda.getMonthOfYear(),
                            joda.getDayOfMonth(),
                            joda.getHourOfDay(),
                            joda.getMinuteOfHour(),
                            joda.getSecondOfMinute());
            assertEquals(
                    text,
                    expected,
                    LocalDateTime.parse(text, MarketplacePortletDefinition.releaseDateFormatter));
        }
    }

    @Test
    public void rejectsTheValuesJodaRejected() {
        for (final String text :
                new String[] {
                    "2014-02-30 00:00:00",
                    "2014-13-01 00:00:00",
                    "2014-01-02",
                    "2014-01-02T03:04:05",
                    "2014-01-02 03:04:05 PM",
                    "2014-01-02 24:00:00"
                }) {
            try {
                JODA_FORMAT.parseDateTime(text);
                fail("Joda accepted '" + text + "'");
            } catch (IllegalArgumentException e) {
                // expected
            }
            try {
                LocalDateTime.parse(text, MarketplacePortletDefinition.releaseDateFormatter);
                fail("Accepted '" + text + "'");
            } catch (DateTimeParseException e) {
                // expected
            }
        }
    }
}
