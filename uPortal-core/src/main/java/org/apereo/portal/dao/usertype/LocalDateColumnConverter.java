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

import java.sql.Date;
import java.time.LocalDate;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * Stores a {@link LocalDate} in a {@code DATE} column as the same date, whatever the JVM's zone, as
 * 5.17 stored it through Jadira's {@code PersistentLocalDate} with {@code databaseZone=jvm}.
 *
 * <p>The JDBC value is the date at the JVM's midnight. Hibernate 5.2 binds and reads {@code DATE}
 * without a calendar and ignores {@code hibernate.jdbc.time_zone}, so the driver converts in the
 * JVM's zone and the date is unchanged. If a Hibernate version binds {@code DATE} with the UTC
 * calendar, every date is stored a day early in a zone ahead of UTC; {@code
 * JavaTimeColumnStorageTest} fails then.
 */
@Converter
public class LocalDateColumnConverter implements AttributeConverter<LocalDate, Date> {
    @Override
    public Date convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : Date.valueOf(attribute);
    }

    @Override
    public LocalDate convertToEntityAttribute(Date column) {
        return column == null ? null : column.toLocalDate();
    }
}
