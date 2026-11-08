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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.apereo.portal.events.aggr.dao.DateDimensionDao;
import org.apereo.portal.events.aggr.dao.IEventAggregationManagementDao;
import org.apereo.portal.events.aggr.dao.TimeDimensionDao;
import org.apereo.portal.events.aggr.dao.jpa.AcademicTermDetailImpl;
import org.apereo.portal.events.aggr.dao.jpa.QuarterDetailImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The helper's intervals in zones whose daylight saving change isn't on a local hour or repeats
 * midnight. The expected values were recorded on uPortal 47d6333e2 with Joda-Time 2.14.3.
 */
public class AggregationIntervalHelperImplZoneTest {
    private static final ZoneId CHATHAM = ZoneId.of("Pacific/Chatham");
    private static final ZoneId HAVANA = ZoneId.of("America/Havana");

    private final IEventAggregationManagementDao managementDao =
            mock(IEventAggregationManagementDao.class);
    private final AggregationIntervalHelperImpl helper = new AggregationIntervalHelperImpl();
    private TimeZone defaultZone;

    @Before
    public void setUp() {
        this.helper.setEventAggregationManagementDao(this.managementDao);
        this.helper.setTimeDimensionDao(mock(TimeDimensionDao.class));
        this.helper.setDateDimensionDao(mock(DateDimensionDao.class));
        this.defaultZone = TimeZone.getDefault();
    }

    @After
    public void restoreJvmZone() {
        TimeZone.setDefault(this.defaultZone);
    }

    /** A statistics report of five-minute intervals for Chatham's fall-back day */
    @Test(timeout = 30000)
    public void listsTheFiveMinuteIntervalsOfChathamsFallBackDay() {
        final ZonedDateTime start = LocalDate.of(2026, 4, 5).atStartOfDay(CHATHAM);
        final ZonedDateTime end = start.plusDays(1).minusSeconds(1);
        final List<ZonedDateTime> starts =
                this.helper.getIntervalStartDateTimesBetween(
                        AggregationInterval.FIVE_MINUTE, start, end, 400);
        assertEquals(300, starts.size());
        assertEquals(
                300, this.helper.intervalsBetween(AggregationInterval.FIVE_MINUTE, start, end));
    }

    @Test
    public void startsAQuarterOnTheMidnightOfTheInstantsOffset() {
        when(this.managementDao.getQuartersDetails())
                .thenReturn(
                        Arrays.<QuarterDetail>asList(
                                quarter("--02-01", "--05-01", 0),
                                quarter("--05-01", "--08-01", 1),
                                quarter("--08-01", "--11-01", 2),
                                quarter("--11-01", "--02-01", 3)));
        // 2026-11-01: 01:00 -04:00 becomes 00:00 -05:00
        assertInterval(
                AggregationInterval.CALENDAR_QUARTER,
                "2026-12-01T00:30-05:00",
                "2026-11-01T00:00-05:00",
                "2027-02-01T00:00-05:00");
        assertInterval(
                AggregationInterval.CALENDAR_QUARTER,
                "2026-10-01T01:30-04:00",
                "2026-08-01T00:00-04:00",
                "2026-11-01T00:00-05:00");
    }

    @Test
    public void startsATermOnTheMidnightItWasStoredWith() {
        TimeZone.setDefault(TimeZone.getTimeZone(HAVANA));
        // As the import stores a term that starts on 2026-11-01 in Havana: the second midnight
        final AcademicTermDetail term =
                new AcademicTermDetailImpl(
                        at("2026-11-01T00:00-05:00"), at("2027-03-14T01:00-04:00"), "winter");
        when(this.managementDao.getAcademicTermDetails())
                .thenReturn(Collections.singletonList(term));
        assertInterval(
                AggregationInterval.ACADEMIC_TERM,
                "2026-11-15T12:00-05:00",
                "2026-11-01T00:00-05:00",
                "2027-03-14T01:00-04:00");
    }

    private void assertInterval(
            AggregationInterval interval, String instant, String start, String end) {
        final AggregationIntervalInfo info = this.helper.getIntervalInfo(interval, at(instant));
        assertEquals(
                interval + " start for " + instant,
                at(start).toInstant(),
                info.getStart().toInstant());
        assertEquals(
                interval + " end for " + instant, at(end).toInstant(), info.getEnd().toInstant());
    }

    private static ZonedDateTime at(String offsetDateTime) {
        return OffsetDateTime.parse(offsetDateTime).atZoneSameInstant(HAVANA);
    }

    private static QuarterDetail quarter(String start, String end, int id) {
        return new QuarterDetailImpl(MonthDay.parse(start), MonthDay.parse(end), id);
    }
}
