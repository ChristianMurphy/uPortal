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
package org.apereo.portal.dbmigrate.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDateTime;
import java.util.Collections;
import org.apereo.portal.dbmigrate.types.ColumnType.Family;
import org.junit.Test;

/** local-time-to-utc converts 5.17's local clock times to UTC clock times (MOD-I141) */
public class LocalTimeToUtcTransformTest {

    private static ValueTransform.Function bind(String zone) {
        for (ValueTransform transform : BuiltInTransforms.all()) {
            if (transform.name().equals("local-time-to-utc")) {
                return transform.bind(Collections.singletonMap("zone", zone));
            }
        }
        throw new AssertionError("no local-time-to-utc transform");
    }

    private static String utc(String zone, String local) {
        Object value = bind(zone).apply(LocalDateTime.parse(local), Collections.emptyMap());
        return value.toString();
    }

    @Test
    public void convertsStandardAndDaylightTime() {
        assertEquals("2026-01-15T17:00", utc("America/New_York", "2026-01-15T12:00"));
        assertEquals("2026-07-01T16:00", utc("America/New_York", "2026-07-01T12:00"));
        assertEquals("2026-07-01T11:00", utc("Europe/London", "2026-07-01T12:00"));
    }

    @Test
    public void takesTheEarlierOffsetInTheRepeatedHour() {
        // 01:30 on 2026-11-01 happens twice in New York: at -04:00 (EDT), then at -05:00 (EST)
        assertEquals("2026-11-01T05:30", utc("America/New_York", "2026-11-01T01:30"));
        assertEquals("2026-11-01T05:59:59.999", utc("America/New_York", "2026-11-01T01:59:59.999"));
        assertEquals("2026-11-01T07:00", utc("America/New_York", "2026-11-01T02:00"));
        assertEquals("2026-10-25T00:30", utc("Europe/London", "2026-10-25T01:30"));
    }

    @Test
    public void movesATimeInTheSkippedHourForward() {
        // 02:30 on 2026-03-08 doesn't exist in New York; it is read as 03:30 EDT
        assertEquals("2026-03-08T07:30", utc("America/New_York", "2026-03-08T02:30"));
    }

    @Test
    public void keepsUtcAndNullAsTheyAre() {
        assertEquals("2026-11-01T01:30", utc("UTC", "2026-11-01T01:30"));
        assertNull(bind("America/New_York").apply(null, Collections.emptyMap()));
        assertEquals(Family.TIMESTAMP, bind("UTC").resultFamily());
    }
}
