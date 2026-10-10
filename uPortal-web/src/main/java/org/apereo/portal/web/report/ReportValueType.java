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
package org.apereo.portal.web.report;

/** The type of a {@link ReportColumn} and of the values in it. */
public enum ReportValueType {
    /** A {@code double} */
    NUMBER("number"),
    /** A {@link String} */
    TEXT("string"),
    /** A {@link java.time.LocalDate} */
    DATE("date"),
    /** A {@link java.time.LocalDateTime} */
    DATETIME("datetime"),
    /** A {@link java.time.LocalTime} */
    TIMEOFDAY("timeofday");

    private final String jsonName;

    ReportValueType(String jsonName) {
        this.jsonName = jsonName;
    }

    /** The name of this type in the table JSON, as the report charts read it. */
    public String getJsonName() {
        return jsonName;
    }
}
