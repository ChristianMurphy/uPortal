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

import java.beans.PropertyEditorSupport;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Converts ISO-8601 period text, such as {@code P1D}, {@code PT30S} or {@code P1DT12H}, to a {@link
 * TemporalAmount}. It accepts the values the Joda-Time {@code ReadablePeriod} editor accepted.
 *
 * <p>The date part ({@code PnYnMnWnD}) becomes a {@link Period} and the time part ({@code TnHnMnS})
 * a {@link Duration}, so applying the amount to a zoned date-time keeps Joda's semantics: days,
 * weeks, months and years move the local calendar date, and hours, minutes and seconds move the
 * instant. A value with both parts applies the date part first, as Joda did.
 */
public class TemporalAmountEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        try {
            this.setValue(parse(text));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid period: \"" + text + "\"", e);
        }
    }

    @Override
    public String getAsText() {
        final Object value = this.getValue();
        return value == null ? "" : value.toString();
    }

    /** Parses ISO-8601 period text with an optional time part into a {@link TemporalAmount}. */
    public static TemporalAmount parse(String text) {
        final int timeStart = text.toUpperCase(Locale.ROOT).indexOf('T');
        if (timeStart < 0) {
            return Period.parse(text);
        }
        final String datePart = text.substring(0, timeStart);
        final Duration duration = Duration.parse("P" + text.substring(timeStart));
        if (datePart.length() == 1) {
            // Only the "P" designator, e.g. "PT30S"
            return duration;
        }
        return new PeriodAndDuration(Period.parse(datePart), duration);
    }

    /** A date-based {@link Period} followed by a time-based {@link Duration}. */
    static final class PeriodAndDuration implements TemporalAmount {
        private final Period period;
        private final Duration duration;

        PeriodAndDuration(Period period, Duration duration) {
            this.period = period;
            this.duration = duration;
        }

        @Override
        public long get(TemporalUnit unit) {
            if (this.period.getUnits().contains(unit)) {
                return this.period.get(unit);
            }
            if (this.duration.getUnits().contains(unit)) {
                return this.duration.get(unit);
            }
            throw new UnsupportedTemporalTypeException("Unsupported unit: " + unit);
        }

        @Override
        public List<TemporalUnit> getUnits() {
            final List<TemporalUnit> units = new ArrayList<>(this.period.getUnits());
            units.addAll(this.duration.getUnits());
            return Collections.unmodifiableList(units);
        }

        @Override
        public Temporal addTo(Temporal temporal) {
            return temporal.plus(this.period).plus(this.duration);
        }

        @Override
        public Temporal subtractFrom(Temporal temporal) {
            return temporal.minus(this.period).minus(this.duration);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PeriodAndDuration)) {
                return false;
            }
            final PeriodAndDuration other = (PeriodAndDuration) obj;
            return this.period.equals(other.period) && this.duration.equals(other.duration);
        }

        @Override
        public int hashCode() {
            return 31 * this.period.hashCode() + this.duration.hashCode();
        }

        @Override
        public String toString() {
            return this.period + this.duration.toString().substring(1);
        }
    }
}
