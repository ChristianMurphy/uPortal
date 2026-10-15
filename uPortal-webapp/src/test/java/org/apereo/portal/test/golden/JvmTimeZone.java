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
package org.apereo.portal.test.golden;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.apereo.portal.test.TimeZoneTestUtils;
import org.joda.time.DateTimeZone;

/**
 * Runs code as if the JVM had been started with {@code -Duser.timezone=<zone>}: sets the JDK and
 * Joda-Time default zones and {@code user.timezone}, and restores them on {@link #close()}. Callers
 * create their Spring context (and so every JDBC connection) after this, because JDBC drivers read
 * the default zone when they connect.
 */
public final class JvmTimeZone implements AutoCloseable {
    /**
     * The zones the goldens are recorded in: UTC, a zone with DST that is behind UTC (the repeated
     * hour in November is where JVM-local timestamps become ambiguous), and a zone with DST that is
     * ahead of UTC. Local midnight there falls on the previous UTC day, so a local date or time
     * converted through UTC is stored a day or several hours off.
     */
    public static final List<String> GOLDEN_ZONES =
            Collections.unmodifiableList(
                    Arrays.asList("UTC", "America/New_York", "Australia/Sydney"));

    private final TimeZoneTestUtils timeZoneTestUtils;
    private final String id;

    private JvmTimeZone(String id) {
        this.id = id;
        this.timeZoneTestUtils = new TimeZoneTestUtils(DateTimeZone.forID(id));
        this.timeZoneTestUtils.beforeTest();
        if (!TimeZone.getDefault().getID().equals(id)) {
            throw new IllegalStateException("Could not set the JVM time zone to " + id);
        }
    }

    public static JvmTimeZone set(String id) {
        return new JvmTimeZone(id);
    }

    public String id() {
        return id;
    }

    /** File-name friendly id, e.g. America_New_York */
    public String fileId() {
        return id.replace('/', '_');
    }

    @Override
    public void close() {
        timeZoneTestUtils.afterTest();
    }
}
