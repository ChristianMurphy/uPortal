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

import java.util.Objects;

/** A column of a {@link ReportTable}: an id unique in the table, a type and a label. Immutable. */
public final class ReportColumn {

    private final String id;
    private final ReportValueType type;
    private final String label;

    public ReportColumn(String id, ReportValueType type, String label) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.label = Objects.requireNonNull(label);
    }

    public String getId() {
        return id;
    }

    public ReportValueType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return "ReportColumn[" + id + ", " + type + ", " + label + "]";
    }
}
