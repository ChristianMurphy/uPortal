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
package org.apereo.portal.portlet.dao.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.servlet.http.Cookie;
import org.apereo.portal.portlet.om.IPortalCookie;
import org.junit.Test;

/**
 * The cookie dates keep Joda-Time's millisecond precision: a creation or expiry date holds whole
 * milliseconds, as Jadira stored them, and an expiry is the time the cookie was set plus its
 * maximum age, not a second earlier.
 */
public class CookieDateStorageTest {
    /** Instant.now() has microseconds on JDK 21, so a few samples show a missing truncation */
    private static final int SAMPLES = 20;

    @Test
    public void portalCookieDatesHoldWholeMilliseconds() {
        for (int i = 0; i < SAMPLES; i++) {
            final PortalCookieImpl cookie = new PortalCookieImpl("value", Instant.EPOCH);
            assertWholeMilliseconds("created", cookie.getCreated());
        }
    }

    @Test
    public void portletCookieExpiresAtTheMaxAgeFromNowInMilliseconds() {
        final int maxAge = 3600;
        for (int i = 0; i < SAMPLES; i++) {
            final Cookie cookie = new Cookie("name", "value");
            cookie.setMaxAge(maxAge);
            final Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            final PortletCookieImpl portletCookie =
                    new PortletCookieImpl(mock(IPortalCookie.class), cookie);
            final Instant after = Instant.now();
            final Instant expires = portletCookie.getExpires();
            assertWholeMilliseconds("expires", expires);
            assertFalse(
                    "expires " + expires + " before " + before.plusSeconds(maxAge),
                    expires.isBefore(before.plusSeconds(maxAge)));
            assertFalse(
                    "expires " + expires + " after " + after.plusSeconds(maxAge),
                    expires.isAfter(after.plusSeconds(maxAge)));
        }
    }

    private static void assertWholeMilliseconds(String what, Instant instant) {
        assertEquals(
                what + " " + instant + " has a fraction of a millisecond",
                0,
                instant.getNano() % 1_000_000);
    }
}
