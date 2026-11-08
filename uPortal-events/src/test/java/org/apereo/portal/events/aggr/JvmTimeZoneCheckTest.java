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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.TimeZone;
import org.junit.Test;

/** The startup warning for JVM zone ids that Joda-Time read as another zone */
public class JvmTimeZoneCheckTest {
    @Test
    public void namesTheRegionJodaTimeUsedForAFixedOffsetShortId() {
        assertWarning("EST", "-05:00", "-Duser.timezone=America/New_York");
        assertWarning("MST", "-07:00", "-Duser.timezone=America/Denver");
        assertWarning("HST", "-10:00", "-Duser.timezone=Pacific/Honolulu");
    }

    @Test
    public void namesUtcForASystemVId() {
        assertWarning("SystemV/EST5EDT", "SystemV/EST5EDT", "-Duser.timezone=UTC");
        assertWarning("SystemV/MST7", "SystemV/MST7", "-Duser.timezone=UTC");
    }

    @Test
    public void staysQuietForIdsJodaTimeReadTheSameWay() {
        for (String id :
                new String[] {
                    "America/New_York", "UTC", "GMT", "PST", "Australia/Sydney", "+02:00"
                }) {
            assertNull(id, JvmTimeZoneCheck.warningFor(id));
        }
    }

    @Test
    public void readsTheJvmDefaultZone() {
        final TimeZone defaultZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("EST"));
            assertNotNull(JvmTimeZoneCheck.warningFor(TimeZone.getDefault().getID()));
            new JvmTimeZoneCheck().warnAboutTheDefaultZone();
        } finally {
            TimeZone.setDefault(defaultZone);
        }
    }

    private static void assertWarning(String id, String javaTimeZone, String setting) {
        final String warning = JvmTimeZoneCheck.warningFor(id);
        assertNotNull(id, warning);
        assertTrue(warning, warning.contains("is " + id + ","));
        assertTrue(warning, warning.contains("reads as " + javaTimeZone + "."));
        assertTrue(warning, warning.contains(setting));
    }
}
