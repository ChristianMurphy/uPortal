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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Enumeration of all time intervals the event aggregation can handle. All of the example ranges are
 * inclusive on both ends.
 *
 * <p>The interval arithmetic works in the zone of the {@link ZonedDateTime} it is given, the way
 * Joda-Time's did: a minute or an hour is elapsed time, and a day or longer follows the calendar.
 *
 * <p>{@link #determineStart(ZonedDateTime)} floors the way Joda-Time's {@code roundFloorCopy} did.
 * A minute, five minutes or an hour is floored in the instant's own offset, so a zone whose
 * daylight saving change isn't on a local hour, such as Pacific/Chatham, still steps forward. A day
 * or longer starts at the local midnight with the instant's offset where that midnight has it, so
 * where a fall-back repeats midnight, such as in America/Havana, an instant in the repeated hour
 * gets the second midnight. A day that starts in a gap starts at the gap's end.
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
        final OffsetDateTime inOwnOffset = instant.toOffsetDateTime();
        switch (this) {
            case MINUTE:
                return inOwnOffset
                        .truncatedTo(ChronoUnit.MINUTES)
                        .atZoneSameInstant(instant.getZone());
            case FIVE_MINUTE:
                return inOwnOffset
                        .truncatedTo(ChronoUnit.HOURS)
                        .plusMinutes((instant.getMinute() / 5) * 5)
                        .atZoneSameInstant(instant.getZone());
            case HOUR:
                return inOwnOffset
                        .truncatedTo(ChronoUnit.HOURS)
                        .atZoneSameInstant(instant.getZone());
            case DAY:
                return startOfDay(instant, instant.toLocalDate());
            case WEEK:
                return startOfDay(
                        instant,
                        instant.toLocalDate()
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            case MONTH:
                return startOfDay(instant, instant.toLocalDate().withDayOfMonth(1));
            case YEAR:
                return startOfDay(instant, instant.toLocalDate().withDayOfYear(1));
            default:
                throw new IllegalArgumentException(
                        "Cannot compute interval start time for "
                                + this
                                + " please use "
                                + AggregationIntervalHelper.class);
        }
    }

    /**
     * The start of the day in the instant's zone, with the instant's offset where that offset is
     * valid at midnight, as Joda-Time's date fields floored. A day that starts in a gap starts at
     * the gap's end.
     */
    private static ZonedDateTime startOfDay(ZonedDateTime instant, LocalDate date) {
        final ZonedDateTime start =
                ZonedDateTime.ofLocal(date.atStartOfDay(), instant.getZone(), instant.getOffset());
        return start.toLocalDate().equals(date) ? start : date.atStartOfDay(instant.getZone());
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
