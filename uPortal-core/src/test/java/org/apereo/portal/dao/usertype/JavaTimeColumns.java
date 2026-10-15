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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/** An entity with one column of each kind the Jadira typedefs used to map */
@Entity
@Table(name = "JAVA_TIME_COLUMNS")
public class JavaTimeColumns {
    @Id
    @Column(name = "ID")
    long id;

    @Column(name = "A_DATE")
    @Convert(converter = LocalDateColumnConverter.class)
    LocalDate date;

    @Column(name = "A_TIME")
    @Convert(converter = LocalTimeColumnConverter.class)
    LocalTime time;

    @Column(name = "A_MONTH_DAY")
    @Convert(converter = MonthDayColumnConverter.class)
    MonthDay monthDay;

    @Column(name = "A_TIMESTAMP")
    Instant timestamp;
}
