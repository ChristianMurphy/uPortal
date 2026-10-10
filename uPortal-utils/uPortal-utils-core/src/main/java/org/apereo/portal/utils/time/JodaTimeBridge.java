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
package org.apereo.portal.utils.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAmount;
import java.util.TimeZone;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * Converts between Joda-Time and {@code java.time} where code that already uses {@code java.time}
 * meets a JPA entity or DAO that still uses Joda-Time. Both directions keep the instant and the
 * time zone, so calendar arithmetic gives the same results on either side.
 *
 * <p>Temporary: it goes away when the JPA entities and DAOs move to {@code java.time}.
 */
public final class JodaTimeBridge {
    private JodaTimeBridge() {}

    /** Converts a Joda {@link DateTime} to a {@link ZonedDateTime} in the same zone. */
    public static ZonedDateTime toZonedDateTime(DateTime dateTime) {
        return ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(dateTime.getMillis()), ZoneId.of(dateTime.getZone().getID()));
    }

    /** Converts a {@link ZonedDateTime} to a Joda {@link DateTime} in the same zone. */
    public static DateTime toDateTime(ZonedDateTime dateTime) {
        return new DateTime(
                dateTime.toInstant().toEpochMilli(),
                DateTimeZone.forTimeZone(TimeZone.getTimeZone(dateTime.getZone())));
    }

    /**
     * Subtracts a {@code java.time} amount from a Joda {@link DateTime} in the date-time's zone,
     * the way {@code DateTime.minus(ReadablePeriod)} does.
     */
    public static DateTime minus(DateTime dateTime, TemporalAmount amount) {
        return toDateTime(toZonedDateTime(dateTime).minus(amount));
    }

    /**
     * Adds a {@code java.time} amount to a Joda {@link DateTime} in the date-time's zone, the way
     * {@code DateTime.plus(ReadablePeriod)} does.
     */
    public static DateTime plus(DateTime dateTime, TemporalAmount amount) {
        return toDateTime(toZonedDateTime(dateTime).plus(amount));
    }
}
