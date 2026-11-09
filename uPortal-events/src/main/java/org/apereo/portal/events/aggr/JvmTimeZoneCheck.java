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

import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Warns at startup when the JVM's default time zone id is one that uPortal 5.17 read in another
 * zone. The event aggregation reads stored timestamps, and builds its days, terms and intervals, in
 * the JVM's default zone. Joda-Time, which 5.17 used, mapped {@code EST}, {@code MST} and {@code
 * HST} to America/New_York, America/Denver and Pacific/Honolulu, and every {@code SystemV/} id to
 * UTC. {@code java.time} reads the first three without daylight saving time, as fixed offsets up to
 * Java 23 and as America/Panama, America/Phoenix and Pacific/Honolulu from Java 24, and the {@code
 * SystemV/} ids as their own rules. With one of these ids, aggregation days and hours and term
 * dates move against 5.17's rows in the months where the zones differ.
 */
@Component
public class JvmTimeZoneCheck {
    private static final Logger logger = LoggerFactory.getLogger(JvmTimeZoneCheck.class);

    /** The region ids Joda-Time used for the short ids that java.time reads another way */
    private static final Map<String, String> JODA_REGIONS =
            Map.of("EST", "America/New_York", "MST", "America/Denver", "HST", "Pacific/Honolulu");

    @PostConstruct
    public void warnAboutTheDefaultZone() {
        final String warning = warningFor(TimeZone.getDefault().getID());
        if (warning != null) {
            logger.warn(warning);
        }
    }

    /**
     * @return the warning for a JVM whose default time zone has this id, or null when java.time
     *     reads the id as Joda-Time did
     */
    static @Nullable String warningFor(String zoneId) {
        final String region;
        final String jodaZone;
        if (StringUtils.startsWith(zoneId, "SystemV/")) {
            jodaZone = "UTC";
            region = "UTC";
        } else if (JODA_REGIONS.containsKey(zoneId)
                && !JODA_REGIONS.get(zoneId).equals(ZoneId.of(zoneId, ZoneId.SHORT_IDS).getId())) {
            // From Java 24, HST is Pacific/Honolulu, as it was for Joda-Time
            region = JODA_REGIONS.get(zoneId);
            jodaZone = region;
        } else {
            return null;
        }
        return "The JVM's default time zone is "
                + zoneId
                + ", which java.time reads as "
                + ZoneId.of(zoneId, ZoneId.SHORT_IDS)
                + ". uPortal 5.17 read it with Joda-Time as "
                + jodaZone
                + ", so the event aggregation's days, hours and term dates can differ from the"
                + " rows 5.17 wrote. Set a region id, such as -Duser.timezone="
                + region
                + ", to keep 5.17's reading, or the region the portal is in (UPGRADING-6.0.md,"
                + " \"Joda-Time removed from the database mappings and the event aggregation"
                + " API\").";
    }
}
