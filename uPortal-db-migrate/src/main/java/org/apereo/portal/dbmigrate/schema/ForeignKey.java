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
package org.apereo.portal.dbmigrate.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A foreign key: {@code table(columns) -> referencedTable(referencedColumns)}. */
public final class ForeignKey {

    private final String name;
    private final String table;
    private final List<String> columns;
    private final String referencedTable;
    private final List<String> referencedColumns;

    public ForeignKey(
            String name,
            String table,
            List<String> columns,
            String referencedTable,
            List<String> referencedColumns) {
        this.name = name;
        this.table = table;
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        this.referencedTable = referencedTable;
        this.referencedColumns = Collections.unmodifiableList(new ArrayList<>(referencedColumns));
    }

    public String name() {
        return name;
    }

    public String table() {
        return table;
    }

    public List<String> columns() {
        return columns;
    }

    public String referencedTable() {
        return referencedTable;
    }

    public List<String> referencedColumns() {
        return referencedColumns;
    }

    public boolean isSelfReference() {
        return Names.key(table).equals(Names.key(referencedTable));
    }

    @Override
    public String toString() {
        return (name == null ? "" : name + " ")
                + table
                + columns
                + " -> "
                + referencedTable
                + referencedColumns;
    }
}
