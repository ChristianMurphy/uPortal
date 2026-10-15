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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import org.apereo.portal.events.aggr.dao.DateDimensionDao;
import org.apereo.portal.events.aggr.dao.IEventAggregationManagementDao;
import org.apereo.portal.events.aggr.dao.TimeDimensionDao;
import org.apereo.portal.events.handlers.db.IPortalEventDao;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@Ignore // Breaks on move to Gradle
@RunWith(MockitoJUnitRunner.class)
public class PortalEventDimensionPopulatorImplTest {
    @InjectMocks
    private PortalEventDimensionPopulatorImpl portalEventDimensionPopulator =
            new PortalEventDimensionPopulatorImpl() {
                @Override
                ZonedDateTime getNow() {
                    return java.time.Instant.ofEpochMilli(1325881376117L)
                            .atZone(ZoneId.systemDefault());
                }
            };

    @Mock TimeDimensionDao timeDimensionDao;
    @Mock DateDimensionDao dateDimensionDao;
    @Mock IPortalEventDao portalEventDao;
    @Mock AggregationIntervalHelper aggregationIntervalHelper;
    @Mock IEventAggregationManagementDao eventAggregationManagementDao;

    protected TimeDimension createMockTimeDimension(LocalTime time) {
        final TimeDimension td = mock(TimeDimension.class);

        when(td.getTime())
                .thenReturn(
                        time.truncatedTo(
                                java.time.temporal.ChronoUnit.MINUTES)); // truncate at minute level
        when(td.getHour()).thenReturn(time.getHour());
        when(td.getMinute()).thenReturn(time.getMinute());
        when(td.getFiveMinuteIncrement()).thenReturn(time.getMinute() / 5);

        return td;
    }

    @Test
    public void populateAllTimeDimensions() {
        when(timeDimensionDao.getTimeDimensions())
                .thenReturn(Collections.<TimeDimension>emptyList());

        this.portalEventDimensionPopulator.doPopulateTimeDimensions();

        verify(timeDimensionDao).getTimeDimensions();
        verify(timeDimensionDao, times(60 * 24))
                .createTimeDimension(ArgumentMatchers.<LocalTime>any());
        verifyNoMoreInteractions(timeDimensionDao, dateDimensionDao);
    }

    @Test
    public void populateSomeTimeDimensions() {
        final Builder<Object> existingTimeDimensionBuilder = ImmutableList.builder();

        TimeDimension td = mock(TimeDimension.class);
        when(td.getTime()).thenReturn(LocalTime.of(0, 1));
        existingTimeDimensionBuilder.add(td);

        final List<TimeDimension> existingTimeDimensions =
                ImmutableList.of(
                        createMockTimeDimension(LocalTime.of(0, 1)),
                        createMockTimeDimension(LocalTime.of(0, 2)),
                        createMockTimeDimension(LocalTime.of(0, 3)),
                        createMockTimeDimension(LocalTime.of(0, 4)),
                        createMockTimeDimension(LocalTime.of(0, 7)),
                        createMockTimeDimension(LocalTime.of(0, 8)),
                        createMockTimeDimension(LocalTime.of(0, 9)),
                        createMockTimeDimension(LocalTime.of(1, 23)),
                        createMockTimeDimension(LocalTime.of(23, 58)));

        when(timeDimensionDao.getTimeDimensions()).thenReturn(existingTimeDimensions);

        this.portalEventDimensionPopulator.doPopulateTimeDimensions();

        verify(timeDimensionDao).getTimeDimensions();
        verify(timeDimensionDao, times((60 * 24) - existingTimeDimensions.size()))
                .createTimeDimension(ArgumentMatchers.<LocalTime>any());
        verifyNoMoreInteractions(timeDimensionDao, dateDimensionDao);
    }

    @Test
    public void populateDefaultDateDimensions() {
        final ZonedDateTime now =
                java.time.Instant.ofEpochMilli(1325881376117L).atZone(ZoneId.systemDefault());
        final Period dimensionBuffer = Period.ofDays(30);
        final ZonedDateTime oldestPortalEvent = now.minusDays(30);
        final ZonedDateTime newestPortalEvent = now;
        final ZonedDateTime dimensionStart = oldestPortalEvent.minus(dimensionBuffer);
        final ZonedDateTime dimensionEnd = newestPortalEvent.plus(dimensionBuffer);

        when(dateDimensionDao.getDateDimensionsBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.<DateDimension>emptyList());
        when(portalEventDao.getOldestPortalEventTimestamp())
                .thenReturn(oldestPortalEvent.toInstant());
        when(portalEventDao.getOldestPortalEventTimestamp())
                .thenReturn(oldestPortalEvent.toInstant());

        final AggregationIntervalInfo startIntervalInfo = mock(AggregationIntervalInfo.class);
        when(startIntervalInfo.getStart())
                .thenReturn(AggregationInterval.YEAR.determineStart(dimensionStart));

        final AggregationIntervalInfo endIntervalInfo = mock(AggregationIntervalInfo.class);
        when(endIntervalInfo.getEnd())
                .thenReturn(AggregationInterval.YEAR.determineEnd(dimensionStart));

        when(aggregationIntervalHelper.getIntervalInfo(AggregationInterval.YEAR, dimensionStart))
                .thenReturn(startIntervalInfo);
        when(aggregationIntervalHelper.getIntervalInfo(AggregationInterval.YEAR, dimensionEnd))
                .thenReturn(endIntervalInfo);

        when(eventAggregationManagementDao.getAcademicTermDetails())
                .thenReturn(Collections.<AcademicTermDetail>emptyList());
        when(eventAggregationManagementDao.getQuartersDetails())
                .thenReturn(EventDateTimeUtils.createStandardQuarters());

        portalEventDimensionPopulator.setDimensionBuffer(java.time.Period.ofDays(30));
        portalEventDimensionPopulator.doPopulateDateDimensions();

        verify(dateDimensionDao)
                .getDateDimensionsBetween(any(LocalDate.class), any(LocalDate.class));

        verify(dateDimensionDao, times(365))
                .createDateDimension(ArgumentMatchers.<LocalDate>any(), anyInt(), anyString());
        verifyNoMoreInteractions(timeDimensionDao, dateDimensionDao);
    }
}
