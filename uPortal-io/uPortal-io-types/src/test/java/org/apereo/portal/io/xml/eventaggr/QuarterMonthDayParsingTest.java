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
package org.apereo.portal.io.xml.eventaggr;

import static org.junit.Assert.assertEquals;

import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import org.junit.Test;

/** A quarter's start and end read the forms Joda-Time's MonthDay.parse read. */
public class QuarterMonthDayParsingTest {
    @Test
    public void readsTheExportedForm() {
        assertEquals(
                MonthDay.of(10, 1),
                EventAggregationConfigurationImporterExporter.parseMonthDay("--10-01"));
    }

    @Test
    public void readsOneDigitFields() {
        assertEquals(
                MonthDay.of(1, 1),
                EventAggregationConfigurationImporterExporter.parseMonthDay("--1-1"));
    }

    @Test
    public void readsTheMonthAndDayOfAFullDate() {
        assertEquals(
                MonthDay.of(8, 29),
                EventAggregationConfigurationImporterExporter.parseMonthDay("2011-08-29"));
    }

    @Test(expected = DateTimeParseException.class)
    public void rejectsAnInvalidDay() {
        EventAggregationConfigurationImporterExporter.parseMonthDay("--02-30");
    }
}
