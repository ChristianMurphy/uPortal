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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A statistics report as a table: typed columns, and rows with one value per column. Jackson writes
 * it as the DataTable JSON that the report charts read ({@link ReportTableJsonSerializer}); {@link
 * ReportTableRenderer} writes it as CSV or HTML.
 */
@JsonSerialize(using = ReportTableJsonSerializer.class)
public class ReportTable {

    private final List<ReportColumn> columns = new ArrayList<>();
    private final Set<String> columnIds = new HashSet<>();
    private final List<List<ReportValue>> rows = new ArrayList<>();

    /**
     * Adds a column. Rows that are already in the table get a null value in it.
     *
     * @throws IllegalArgumentException if the table already has a column with the same id
     */
    public void addColumn(ReportColumn column) {
        if (!columnIds.add(column.getId())) {
            throw new IllegalArgumentException(
                    "Column id [" + column.getId() + "] is already in the table");
        }
        columns.add(column);
        for (final List<ReportValue> row : rows) {
            row.add(ReportValue.nullValue(column.getType()));
        }
    }

    public void addColumns(Collection<ReportColumn> columns) {
        for (final ReportColumn column : columns) {
            addColumn(column);
        }
    }

    /**
     * Adds a row. A row with fewer values than there are columns gets null values for the rest.
     *
     * @throws IllegalArgumentException if the row has more values than there are columns, or a
     *     value's type doesn't match its column's
     */
    public void addRow(List<ReportValue> values) {
        if (values.size() > columns.size()) {
            throw new IllegalArgumentException(
                    "Row has "
                            + values.size()
                            + " values but the table has only "
                            + columns.size()
                            + " columns");
        }
        final List<ReportValue> row = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            final ReportValueType columnType = columns.get(i).getType();
            if (i < values.size()) {
                final ReportValue value = values.get(i);
                if (value.getType() != columnType) {
                    throw new IllegalArgumentException(
                            "Value at index "
                                    + i
                                    + " is of type "
                                    + value.getType()
                                    + " but its column is of type "
                                    + columnType);
                }
                row.add(value);
            } else {
                row.add(ReportValue.nullValue(columnType));
            }
        }
        rows.add(row);
    }

    public List<ReportColumn> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    /** The rows, each with exactly one value per column, in column order. */
    public List<List<ReportValue>> getRows() {
        final List<List<ReportValue>> result = new ArrayList<>(rows.size());
        for (final List<ReportValue> row : rows) {
            result.add(Collections.unmodifiableList(row));
        }
        return Collections.unmodifiableList(result);
    }
}
