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

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;

/** Describes a dimension in time (hours). Each object represents one minute in a 24 period */
public interface DateDimension extends Serializable {
    /**
     * @return The ID of the dimension
     */
    long getId();

    /**
     * @return The fully built date for this date dimension
     */
    LocalDate getDate();

    /**
     * @return The year the day exists in
     * @see ChronoField#YEAR
     */
    int getYear();

    /**
     * Will only return values between 0 and 3 inclusive
     *
     * @return The quarter of the year the day exists in
     */
    int getQuarter();

    /**
     * Will only return values between 0 and 11 inclusive
     *
     * @return The month of the year the day exists in
     * @see ChronoField#MONTH_OF_YEAR
     */
    int getMonth();

    /**
     * @return The ISO week-based year times 100 plus the ISO week of that year
     * @see IsoFields#WEEK_OF_WEEK_BASED_YEAR
     */
    int getWeek();

    /**
     * Will only return values between 0 and 31 inclusive
     *
     * @return Day in the month
     * @see ChronoField#DAY_OF_MONTH
     */
    int getDay();

    /**
     * @return The optional designation of the current term the day exists in, may be null
     */
    String getTerm();

    /** Set the term, only allowed if {@link #getTerm()} returns null */
    public void setTerm(String term);
}
