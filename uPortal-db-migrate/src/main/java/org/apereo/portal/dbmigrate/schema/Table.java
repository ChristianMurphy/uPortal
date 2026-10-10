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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A table with its columns (in database order), primary key and foreign keys. */
public final class Table {

    private final String catalog;
    private final String schema;
    private final String name;
    private final Map<String, Column> columns = new LinkedHashMap<>();
    private final List<String> primaryKey = new ArrayList<>();
    private final List<ForeignKey> foreignKeys = new ArrayList<>();

    public Table(String catalog, String schema, String name) {
        this.catalog = catalog;
        this.schema = schema;
        this.name = name;
    }

    public String catalog() {
        return catalog;
    }

    public String schema() {
        return schema;
    }

    public String name() {
        return name;
    }

    public String key() {
        return Names.key(name);
    }

    public Table addColumn(Column c) {
        columns.put(c.key(), c);
        return this;
    }

    public Table primaryKey(List<String> pk) {
        primaryKey.clear();
        primaryKey.addAll(pk);
        return this;
    }

    public Table addForeignKey(ForeignKey fk) {
        foreignKeys.add(fk);
        return this;
    }

    public List<Column> columns() {
        return Collections.unmodifiableList(new ArrayList<>(columns.values()));
    }

    /** Case-insensitive lookup; null when the table has no such column. */
    public Column column(String columnName) {
        return columns.get(Names.key(columnName));
    }

    public List<String> primaryKey() {
        return Collections.unmodifiableList(primaryKey);
    }

    public List<ForeignKey> foreignKeys() {
        return Collections.unmodifiableList(foreignKeys);
    }

    @Override
    public String toString() {
        return name;
    }
}
