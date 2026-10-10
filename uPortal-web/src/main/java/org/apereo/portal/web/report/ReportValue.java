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
package org.apereo.portal.web.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One cell of a {@link ReportTable}: a typed value, which may be null. Immutable. */
public final class ReportValue {

    private final ReportValueType type;
    private final @Nullable Object value;

    private ReportValue(ReportValueType type, @Nullable Object value) {
        this.type = type;
        this.value = value;
    }

    public static ReportValue number(double value) {
        return new ReportValue(ReportValueType.NUMBER, value);
    }

    public static ReportValue text(String value) {
        return new ReportValue(ReportValueType.TEXT, Objects.requireNonNull(value));
    }

    public static ReportValue date(LocalDate value) {
        return new ReportValue(ReportValueType.DATE, Objects.requireNonNull(value));
    }

    public static ReportValue dateTime(LocalDateTime value) {
        return new ReportValue(ReportValueType.DATETIME, Objects.requireNonNull(value));
    }

    public static ReportValue timeOfDay(LocalTime value) {
        return new ReportValue(ReportValueType.TIMEOFDAY, Objects.requireNonNull(value));
    }

    /** A missing value in a column of the given type. */
    public static ReportValue nullValue(ReportValueType type) {
        return new ReportValue(type, null);
    }

    public ReportValueType getType() {
        return type;
    }

    /**
     * The value: a {@link Double}, {@link String}, {@link LocalDate}, {@link LocalDateTime} or
     * {@link LocalTime} as {@link #getType()} says, or null.
     */
    public @Nullable Object getValue() {
        return value;
    }

    public boolean isNull() {
        return value == null;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof ReportValue)) {
            return false;
        }
        final ReportValue other = (ReportValue) o;
        return type == other.type && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return type + ":" + value;
    }
}
