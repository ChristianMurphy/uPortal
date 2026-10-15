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

import com.google.common.collect.ImmutableList;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import org.apereo.portal.events.aggr.EventDateTimeUtils;
import org.apereo.portal.events.aggr.QuarterDetail;
import org.junit.Test;

/** */
public class QuarterDetailsImplTest {

    @Test
    public void testOrder() {
        List<QuarterDetail> quarters = createStandardQuarters();
        verifyOrder(quarters);

        quarters = createOffsetQuarters();
        verifyOrder(quarters);
    }

    protected void verifyOrder(List<QuarterDetail> quarters) {
        final Iterator<QuarterDetail> itr = quarters.iterator();
        assertEquals(0, itr.next().getQuarterId());
        assertEquals(1, itr.next().getQuarterId());
        assertEquals(2, itr.next().getQuarterId());
        assertEquals(3, itr.next().getQuarterId());
    }

    @Test
    public void testCompareToInstant() {
        List<QuarterDetail> quarters = createStandardQuarters();

        verifyCompareToInstant(
                ZonedDateTime.of(2012, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()),
                quarters,
                0,
                1,
                1,
                1);
        verifyCompareToInstant(
                ZonedDateTime.of(2012, 2, 29, 15, 48, 0, 0, ZoneId.systemDefault()),
                quarters,
                0,
                1,
                1,
                1);
        verifyCompareToInstant(
                ZonedDateTime.of(2012, 4, 29, 15, 48, 0, 0, ZoneId.systemDefault()),
                quarters,
                -1,
                0,
                1,
                1);
        verifyCompareToInstant(
                ZonedDateTime.of(2012, 8, 29, 15, 48, 0, 0, ZoneId.systemDefault()),
                quarters,
                -1,
                -1,
                0,
                1);
        verifyCompareToInstant(
                ZonedDateTime.of(2012, 12, 31, 23, 59, 59, 999, ZoneId.systemDefault()),
                quarters,
                -1,
                -1,
                -1,
                0);

        quarters = createOffsetQuarters();

        verifyCompareToInstant(
                ZonedDateTime.of(2012, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()),
                quarters,
                1,
                1,
                0,
                1);
        verifyCompareToInstant(
                ZonedDateTime.of(2012, 2, 29, 15, 48, 0, 0, ZoneId.systemDefault()),
                quarters,
                1,
                1,
                1,
                0);
        verifyCompareToInstant(
                ZonedDateTime.of(2012, 4, 29, 15, 48, 0, 0, ZoneId.systemDefault()),
                quarters,
                1,
                1,
                1,
                0);
        verifyCompareToInstant(
                ZonedDateTime.of(2012, 8, 29, 15, 48, 0, 0, ZoneId.systemDefault()),
                quarters,
                -1,
                0,
                1,
                -1);
        verifyCompareToInstant(
                ZonedDateTime.of(2012, 12, 31, 23, 59, 59, 999, ZoneId.systemDefault()),
                quarters,
                -1,
                -1,
                0,
                -1);
    }

    protected void verifyCompareToInstant(
            ZonedDateTime dt, List<QuarterDetail> quarters, int... contains) {
        final Iterator<QuarterDetail> itr = quarters.iterator();
        for (int q = 0; q < 4; q++) {
            final QuarterDetail quarter = itr.next();
            assertEquals(
                    q
                            + ": "
                            + dt
                            + " is not between "
                            + quarter.getStart()
                            + " and "
                            + quarter.getEnd(),
                    contains[q],
                    quarter.compareTo(dt));
        }
    }

    @Test
    public void testValidateQuarters() {
        EventDateTimeUtils.validateQuarters(createStandardQuarters());

        EventDateTimeUtils.validateQuarters(createOffsetQuarters());
    }

    protected List<QuarterDetail> createStandardQuarters() {
        return EventDateTimeUtils.createStandardQuarters();
    }

    protected List<QuarterDetail> createOffsetQuarters() {
        return ImmutableList.<QuarterDetail>of(
                new QuarterDetailImpl(MonthDay.of(5, 1), MonthDay.of(8, 1), 0),
                new QuarterDetailImpl(MonthDay.of(8, 1), MonthDay.of(11, 1), 1),
                new QuarterDetailImpl(MonthDay.of(11, 1), MonthDay.of(2, 1), 2),
                new QuarterDetailImpl(MonthDay.of(2, 1), MonthDay.of(5, 1), 3));
    }
}
