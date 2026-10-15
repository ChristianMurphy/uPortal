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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.apereo.portal.events.aggr.dao.DateDimensionDao;
import org.apereo.portal.events.aggr.dao.TimeDimensionDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** */
@RunWith(MockitoJUnitRunner.class)
public class IntervalHelperImplTest {
    @InjectMocks
    private AggregationIntervalHelperImpl helperImpl = new AggregationIntervalHelperImpl();

    @Mock private DateDimensionDao dateDimensionDao;
    @Mock private TimeDimensionDao timeDimensionDao;

    @Test
    public void testGetIntervalInfo() {
        final ZonedDateTime instant =
                java.time.Instant.ofEpochMilli(1325881376117l).atZone(ZoneOffset.UTC);

        assertEquals(2012, instant.getYear());
        assertEquals(1, instant.getMonthValue());
        assertEquals(6, instant.getDayOfMonth());
        assertEquals(1, instant.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(20, instant.getHour());
        assertEquals(22, instant.getMinute());
        assertEquals(56, instant.getSecond());
        assertEquals(117, instant.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        ZonedDateTime start, end;

        // TEST YEAR

        start = AggregationInterval.YEAR.determineStart(instant);
        assertEquals(2012, start.getYear());
        assertEquals(1, start.getMonthValue());
        assertEquals(1, start.getDayOfMonth());
        assertEquals(52, start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(0, start.getHour());
        assertEquals(0, start.getMinute());
        assertEquals(0, start.getSecond());
        assertEquals(0, start.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        end = AggregationInterval.YEAR.determineEnd(start);
        assertEquals(2013, end.getYear());
        assertEquals(1, end.getMonthValue());
        assertEquals(1, end.getDayOfMonth());
        assertEquals(1, end.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(0, end.getHour());
        assertEquals(0, end.getMinute());
        assertEquals(0, end.getSecond());
        assertEquals(0, end.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        // TEST MONTH

        start = AggregationInterval.MONTH.determineStart(instant);
        assertEquals(2012, start.getYear());
        assertEquals(1, start.getMonthValue());
        assertEquals(1, start.getDayOfMonth());
        assertEquals(52, start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(0, start.getHour());
        assertEquals(0, start.getMinute());
        assertEquals(0, start.getSecond());
        assertEquals(0, start.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        end = AggregationInterval.MONTH.determineEnd(start);
        assertEquals(2012, end.getYear());
        assertEquals(2, end.getMonthValue());
        assertEquals(1, end.getDayOfMonth());
        assertEquals(5, end.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(0, end.getHour());
        assertEquals(0, end.getMinute());
        assertEquals(0, end.getSecond());
        assertEquals(0, end.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        // TEST WEEK

        start = AggregationInterval.WEEK.determineStart(instant);
        assertEquals(2012, start.getYear());
        assertEquals(1, start.getMonthValue());
        assertEquals(2, start.getDayOfMonth());
        assertEquals(1, start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(0, start.getHour());
        assertEquals(0, start.getMinute());
        assertEquals(0, start.getSecond());
        assertEquals(0, start.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        end = AggregationInterval.WEEK.determineEnd(start);
        assertEquals(2012, end.getYear());
        assertEquals(1, end.getMonthValue());
        assertEquals(9, end.getDayOfMonth());
        assertEquals(2, end.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(0, end.getHour());
        assertEquals(0, end.getMinute());
        assertEquals(0, end.getSecond());
        assertEquals(0, end.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        // TEST DAY

        start = AggregationInterval.DAY.determineStart(instant);
        assertEquals(2012, start.getYear());
        assertEquals(1, start.getMonthValue());
        assertEquals(6, start.getDayOfMonth());
        assertEquals(1, start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(0, start.getHour());
        assertEquals(0, start.getMinute());
        assertEquals(0, start.getSecond());
        assertEquals(0, start.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        end = AggregationInterval.DAY.determineEnd(start);
        assertEquals(2012, end.getYear());
        assertEquals(1, end.getMonthValue());
        assertEquals(7, end.getDayOfMonth());
        assertEquals(1, end.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(0, end.getHour());
        assertEquals(0, end.getMinute());
        assertEquals(0, end.getSecond());
        assertEquals(0, end.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        // TEST HOUR

        start = AggregationInterval.HOUR.determineStart(instant);
        assertEquals(2012, start.getYear());
        assertEquals(1, start.getMonthValue());
        assertEquals(6, start.getDayOfMonth());
        assertEquals(1, start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(20, start.getHour());
        assertEquals(0, start.getMinute());
        assertEquals(0, start.getSecond());
        assertEquals(0, start.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        end = AggregationInterval.HOUR.determineEnd(start);
        assertEquals(2012, end.getYear());
        assertEquals(1, end.getMonthValue());
        assertEquals(6, end.getDayOfMonth());
        assertEquals(1, end.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(21, end.getHour());
        assertEquals(0, end.getMinute());
        assertEquals(0, end.getSecond());
        assertEquals(0, end.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        // TEST FIVE_MINUTE

        start = AggregationInterval.FIVE_MINUTE.determineStart(instant);
        assertEquals(2012, start.getYear());
        assertEquals(1, start.getMonthValue());
        assertEquals(6, start.getDayOfMonth());
        assertEquals(1, start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(20, start.getHour());
        assertEquals(20, start.getMinute());
        assertEquals(0, start.getSecond());
        assertEquals(0, start.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        end = AggregationInterval.FIVE_MINUTE.determineEnd(start);
        assertEquals(2012, end.getYear());
        assertEquals(1, end.getMonthValue());
        assertEquals(6, end.getDayOfMonth());
        assertEquals(1, end.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(20, end.getHour());
        assertEquals(25, end.getMinute());
        assertEquals(0, end.getSecond());
        assertEquals(0, end.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        // TEST MINUTE

        start = AggregationInterval.MINUTE.determineStart(instant);
        assertEquals(2012, start.getYear());
        assertEquals(1, start.getMonthValue());
        assertEquals(6, start.getDayOfMonth());
        assertEquals(1, start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(20, start.getHour());
        assertEquals(22, start.getMinute());
        assertEquals(0, start.getSecond());
        assertEquals(0, start.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));

        end = AggregationInterval.MINUTE.determineEnd(start);
        assertEquals(2012, end.getYear());
        assertEquals(1, end.getMonthValue());
        assertEquals(6, end.getDayOfMonth());
        assertEquals(1, end.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(20, end.getHour());
        assertEquals(23, end.getMinute());
        assertEquals(0, end.getSecond());
        assertEquals(0, end.get(java.time.temporal.ChronoField.MILLI_OF_SECOND));
    }
}
