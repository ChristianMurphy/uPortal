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
package org.apereo.portal.dao.usertype;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * Stores a {@link LocalTime} in a {@code TIME} column as the same time of day, whatever the JVM's
 * zone, as 5.17 stored it: the JVM's local time of day.
 *
 * <p>Hibernate binds and reads {@code TIME} with the calendar of {@code hibernate.jdbc.time_zone},
 * which uPortal sets to UTC. So the JDBC value is the time of day on 1970-01-01 in UTC. {@link
 * Time#valueOf(LocalTime)} would give the time in the JVM's zone, and the stored time would move by
 * the JVM's offset (MOD-I221). Without {@code hibernate.jdbc.time_zone=UTC} this converter stores
 * shifted times.
 */
@Converter
public class LocalTimeColumnConverter implements AttributeConverter<LocalTime, Time> {
    private static final LocalDate TIME_DATE = LocalDate.of(1970, 1, 1);

    @Override
    public Time convertToDatabaseColumn(LocalTime attribute) {
        return attribute == null
                ? null
                : new Time(attribute.atDate(TIME_DATE).toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    @Override
    public LocalTime convertToEntityAttribute(Time column) {
        return column == null
                ? null
                : Instant.ofEpochMilli(column.getTime()).atOffset(ZoneOffset.UTC).toLocalTime();
    }
}
