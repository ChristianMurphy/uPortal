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

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Enumeration of all time intervals the event aggregation can handle. All of the example ranges are
 * inclusive on both ends.
 *
 * <p>The interval arithmetic works in the zone of the {@link ZonedDateTime} it is given, the way
 * Joda-Time's did: a minute or an hour is elapsed time, and a day or longer follows the calendar.
 */
public enum AggregationInterval {
    /** 1 Minute */
    MINUTE(ChronoUnit.MINUTES, true),
    /** 5 Minutes (0-4,5-9,...,55-59) */
    FIVE_MINUTE(null, true),
    /** 1 Hour */
    HOUR(ChronoUnit.HOURS, true),
    /** 1 Day */
    DAY(ChronoUnit.DAYS, false),
    /** 1 ISO week, starting on Monday */
    WEEK(ChronoUnit.WEEKS, false),
    /** 1 Calendar month */
    MONTH(ChronoUnit.MONTHS, false),
    /**
     * As defined by the deployer, divides the calendar into 4 sections. Default configuration is: 3
     * Calendar months (Jan 1 - Mar 31, Apr 1 - Jun 30, Jul 1 - Sep 30, Oct 1 - Dec 31)
     */
    CALENDAR_QUARTER(null, false),
    /** As defined by the deployer, unusable unless term boundaries have been configured. */
    ACADEMIC_TERM(null, false),
    /** 1 Year */
    YEAR(ChronoUnit.YEARS, false);

    private final ChronoUnit unit;
    private final boolean hasTimePart;

    private AggregationInterval(ChronoUnit unit, boolean hasTimePart) {
        this.unit = unit;
        this.hasTimePart = hasTimePart;
    }

    /**
     * @return true if the interval has a time part, false if the interval is date only
     */
    public boolean isHasTimePart() {
        return hasTimePart;
    }

    /**
     * @return the {@link ChronoUnit} of one {@link AggregationInterval}, null if there is no
     *     mapping
     */
    public ChronoUnit getUnit() {
        return this.unit;
    }

    /**
     * @return If the {@link #determineEnd(ZonedDateTime)} and {@link
     *     #determineStart(ZonedDateTime)} methods work on this interval
     */
    public boolean isSupportsDetermination() {
        return this.unit != null || this == FIVE_MINUTE;
    }

    /**
     * Determine the number of intervals between the start and end dates
     *
     * @param start Start, inclusive
     * @param end End, exclusive
     * @return Number of intervals between start and end
     */
    public int determineIntervalsBetween(ZonedDateTime start, ZonedDateTime end) {
        if (!this.isSupportsDetermination()) {
            throw new IllegalArgumentException(
                    "Cannot compute intervals between for "
                            + this
                            + " please use "
                            + AggregationIntervalHelper.class);
        }

        if (this == FIVE_MINUTE) {
            return (int) Math.round(ChronoUnit.MINUTES.between(start, end) / 5d);
        }
        return (int) this.unit.between(start, end);
    }

    /**
     * Determine the starting date and time (inclusive) of an interval based on an instant in time
     *
     * @param instant The instant in time to get the interval starting value for
     * @return The start of this interval in relation to the provided instant
     */
    public ZonedDateTime determineStart(ZonedDateTime instant) {
        switch (this) {
            case MINUTE:
                return instant.truncatedTo(ChronoUnit.MINUTES);
            case FIVE_MINUTE:
                return instant.truncatedTo(ChronoUnit.HOURS)
                        .plusMinutes((instant.getMinute() / 5) * 5);
            case HOUR:
                return instant.truncatedTo(ChronoUnit.HOURS);
            case DAY:
                return instant.toLocalDate().atStartOfDay(instant.getZone());
            case WEEK:
                return instant.toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .atStartOfDay(instant.getZone());
            case MONTH:
                return instant.toLocalDate().withDayOfMonth(1).atStartOfDay(instant.getZone());
            case YEAR:
                return instant.toLocalDate().withDayOfYear(1).atStartOfDay(instant.getZone());
            default:
                throw new IllegalArgumentException(
                        "Cannot compute interval start time for "
                                + this
                                + " please use "
                                + AggregationIntervalHelper.class);
        }
    }

    /**
     * Determine the ending date and time (exclusive) of an interval based on an instant in time
     *
     * @param instant The start of an instant
     * @return The end of this interval in relation to the provided instant
     */
    public ZonedDateTime determineEnd(ZonedDateTime instant) {
        if (this == FIVE_MINUTE) {
            return this.determineStart(instant).plusMinutes(5);
        }
        if (this.unit == null) {
            throw new IllegalArgumentException(
                    "Cannot compute interval end time for "
                            + this
                            + " please use "
                            + AggregationIntervalHelper.class);
        }
        return this.determineStart(instant).plus(1, this.unit);
    }
}
