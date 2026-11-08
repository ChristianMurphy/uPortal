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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.apereo.portal.events.aggr.dao.DateDimensionDao;
import org.apereo.portal.events.aggr.dao.IEventAggregationManagementDao;
import org.apereo.portal.events.aggr.dao.jpa.AcademicTermDetailImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A date dimension gets the term of its day in the JVM's zone. In New York, midnight at the start
 * of a term's first day is 04:00 UTC, so a day checked at UTC midnight would fall in the day before
 * the term.
 */
public class PortalEventDimensionPopulatorTermTest {
    private static final LocalDate TERM_START = LocalDate.of(2026, 9, 8);

    private final DateDimensionDao dateDimensionDao = mock(DateDimensionDao.class);
    private final IEventAggregationManagementDao managementDao =
            mock(IEventAggregationManagementDao.class);
    private final PortalEventDimensionPopulatorImpl populator =
            new PortalEventDimensionPopulatorImpl();
    private TimeZone defaultZone;
    private List<AcademicTermDetail> terms;

    @Before
    public void setUp() {
        this.defaultZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        this.populator.setDateDimensionDao(this.dateDimensionDao);
        this.populator.setEventAggregationManagementDao(this.managementDao);
        this.terms =
                Collections.singletonList(
                        new AcademicTermDetailImpl(TERM_START, LocalDate.of(2026, 12, 19), "fall"));
        when(this.managementDao.getAcademicTermDetails()).thenReturn(this.terms);
    }

    @After
    public void restoreJvmZone() {
        TimeZone.setDefault(this.defaultZone);
    }

    @Test
    public void createsTheTermsFirstDayInTheTerm() {
        this.populator.createDateDimension(
                EventDateTimeUtils.createStandardQuarters(), this.terms, TERM_START);
        verify(this.dateDimensionDao).createDateDimension(TERM_START, 2, "fall");
    }

    @Test
    public void addsTheTermToTheTermsFirstDay() {
        final DateDimension firstDay = mock(DateDimension.class);
        when(firstDay.getDate()).thenReturn(TERM_START);
        when(this.dateDimensionDao.getDateDimensionsWithoutTerm())
                .thenReturn(Collections.singletonList(firstDay));
        this.populator.doUpdateDateDimensions();
        verify(firstDay).setTerm("fall");
        verify(this.dateDimensionDao).updateDateDimension(firstDay);
    }
}
