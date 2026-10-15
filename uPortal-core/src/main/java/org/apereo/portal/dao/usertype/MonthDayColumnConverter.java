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

import java.time.MonthDay;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * Stores a {@link MonthDay} as text in the ISO form {@code --MM-dd}, as 5.17 stored it through
 * Jadira's {@code PersistentMonthDayAsString}.
 */
@Converter
public class MonthDayColumnConverter implements AttributeConverter<MonthDay, String> {
    @Override
    public String convertToDatabaseColumn(MonthDay attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public MonthDay convertToEntityAttribute(String column) {
        return column == null ? null : MonthDay.parse(column);
    }
}
