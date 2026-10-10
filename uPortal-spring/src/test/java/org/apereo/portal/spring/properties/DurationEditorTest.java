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
import static org.junit.Assert.fail;

import java.time.Duration;
import org.junit.Test;

public class DurationEditorTest {

    /**
     * Values the Joda-Time ReadableDurationEditor accepted, including those in portal.properties.
     */
    private static final String[] JODA_VALUES = {
        "PT0.100S",
        "PT1S",
        "PT30S",
        "PT60S",
        "PT120S",
        "PT3600S",
        "pt30s",
        "PT-5S",
        "PT1.5S",
        "PT1.25S",
        "PT5.S",
        "PT0.0001S"
    };

    @Test
    public void acceptsEveryJodaValueWithTheSameMillis() {
        final DurationEditor editor = new DurationEditor();
        for (final String text : JODA_VALUES) {
            editor.setAsText(text);
            final long expected = org.joda.time.Duration.parse(text).getMillis();
            assertEquals(text, expected, ((Duration) editor.getValue()).toMillis());
        }
    }

    @Test
    public void acceptsOtherIsoDurations() {
        final DurationEditor editor = new DurationEditor();
        editor.setAsText("PT1M");
        assertEquals(Duration.ofMinutes(1), editor.getValue());
        editor.setAsText("P1DT2H");
        assertEquals(Duration.ofHours(26), editor.getValue());
    }

    @Test
    public void rejectsInvalidText() {
        final DurationEditor editor = new DurationEditor();
        for (final String text : new String[] {"30", "30S", "PT", "PTS", ""}) {
            try {
                editor.setAsText(text);
                fail("Expected IllegalArgumentException for '" + text + "'");
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @Test
    public void formatsAsIsoText() {
        final DurationEditor editor = new DurationEditor();
        editor.setValue(Duration.ofSeconds(30));
        assertEquals("PT30S", editor.getAsText());
    }
}
