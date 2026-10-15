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
package org.apereo.portal.events.aggr.dao.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apereo.portal.events.aggr.EventDateTimeUtils;
import org.junit.Test;

/** */
public class EventDateTimeUtilsTest {
    @Test
    public void testFindAcademicTerms() {
        final AcademicTermDetailImpl spring2012 =
                new AcademicTermDetailImpl(
                        LocalDate.of(2012, 1, 1), LocalDate.of(2012, 6, 1), "Spring 2012");
        final AcademicTermDetailImpl summer2012 =
                new AcademicTermDetailImpl(
                        LocalDate.of(2012, 6, 1), LocalDate.of(2012, 9, 1), "Summer 2012");
        final AcademicTermDetailImpl fall2012 =
                new AcademicTermDetailImpl(
                        LocalDate.of(2012, 9, 1), LocalDate.of(2013, 1, 1), "Fall 2012");

        List<AcademicTermDetailImpl> terms = Arrays.asList(spring2012, summer2012, fall2012);

        AcademicTermDetailImpl result;
        Collections.sort(terms);

        result =
                EventDateTimeUtils.findDateRangeSorted(
                        LocalDate.of(2011, 3, 1).atStartOfDay(ZoneId.systemDefault()), terms);
        assertNull(result);

        result =
                EventDateTimeUtils.findDateRangeSorted(
                        LocalDate.of(2012, 3, 1).atStartOfDay(ZoneId.systemDefault()), terms);
        assertEquals(spring2012, result);

        result =
                EventDateTimeUtils.findDateRangeSorted(
                        LocalDate.of(2012, 7, 1).atStartOfDay(ZoneId.systemDefault()), terms);
        assertEquals(summer2012, result);

        result =
                EventDateTimeUtils.findDateRangeSorted(
                        LocalDate.of(2012, 12, 31).atStartOfDay(ZoneId.systemDefault()), terms);
        assertEquals(fall2012, result);
    }
}
