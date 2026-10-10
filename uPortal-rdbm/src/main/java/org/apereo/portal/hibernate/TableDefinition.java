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
package org.apereo.portal.hibernate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A table that is not mapped by an entity, as <code>tables.xml</code> describes it: columns with
 * their {@link java.sql.Types} code, an optional primary key, and named unique keys and indexes.
 * {@link SchemaTool} turns it into DDL.
 */
public final class TableDefinition {
    private final String name;
    private final Map<String, Column> columns = new LinkedHashMap<>();
    private final List<String> primaryKey = new ArrayList<>();
    private final Map<String, List<String>> uniqueKeys = new LinkedHashMap<>();
    private final Map<String, List<String>> indexes = new LinkedHashMap<>();

    public TableDefinition(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** Adds a column; columns keep the order they were added in. */
    public Column addColumn(String columnName, int sqlType) {
        final Column column = new Column(columnName, sqlType);
        columns.put(columnName, column);
        return column;
    }

    /**
     * @return the column with the given name, or null
     */
    public Column getColumn(String columnName) {
        return columns.get(columnName);
    }

    public List<Column> getColumns() {
        return Collections.unmodifiableList(new ArrayList<>(columns.values()));
    }

    public void addPrimaryKeyColumn(String columnName) {
        primaryKey.add(columnName);
    }

    /**
     * @return the primary key column names in key order; empty if the table has no key
     */
    public List<String> getPrimaryKey() {
        return Collections.unmodifiableList(primaryKey);
    }

    public void addUniqueKey(String keyName, List<String> columnNames) {
        uniqueKeys.put(keyName, new ArrayList<>(columnNames));
    }

    /**
     * @return unique key name to column names, in the order the keys were added
     */
    public Map<String, List<String>> getUniqueKeys() {
        return Collections.unmodifiableMap(uniqueKeys);
    }

    public void addIndex(String indexName, List<String> columnNames) {
        indexes.put(indexName, new ArrayList<>(columnNames));
    }

    /**
     * @return index name to column names, in the order the indexes were added
     */
    public Map<String, List<String>> getIndexes() {
        return Collections.unmodifiableMap(indexes);
    }

    /** A column: its name, {@link java.sql.Types} code, optional length and nullability. */
    public static final class Column {
        private final String name;
        private final int sqlType;
        private Integer length;
        private boolean nullable = true;

        private Column(String name, int sqlType) {
            this.name = name;
            this.sqlType = sqlType;
        }

        public String getName() {
            return name;
        }

        public int getSqlType() {
            return sqlType;
        }

        /**
         * @return the declared length, or null to use the dialect's default
         */
        public Integer getLength() {
            return length;
        }

        public void setLength(Integer length) {
            this.length = length;
        }

        public boolean isNullable() {
            return nullable;
        }

        public void setNullable(boolean nullable) {
            this.nullable = nullable;
        }
    }
}
