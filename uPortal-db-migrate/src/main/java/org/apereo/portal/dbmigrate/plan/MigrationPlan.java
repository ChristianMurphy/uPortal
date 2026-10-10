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
package org.apereo.portal.dbmigrate.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apereo.portal.dbmigrate.schema.Column;
import org.apereo.portal.dbmigrate.schema.SchemaModel;
import org.apereo.portal.dbmigrate.schema.Table;

/** What a run will do, worked out before any row is written. */
public final class MigrationPlan {

    private final SchemaModel source;
    private final SchemaModel target;
    private final Map<String, TableMapping> tables = new LinkedHashMap<>();
    private LoadOrder order;
    private final List<GeneratorTarget> generators = new ArrayList<>();
    private final List<IdentityColumn> identityColumns = new ArrayList<>();
    private final List<String> excludedTables = new ArrayList<>();
    private final List<String> generatorTables = new ArrayList<>();
    private final List<String> targetOnlyTables = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public MigrationPlan(SchemaModel source, SchemaModel target) {
        this.source = source;
        this.target = target;
    }

    public SchemaModel source() {
        return source;
    }

    public SchemaModel target() {
        return target;
    }

    public void addTable(TableMapping m) {
        tables.put(m.key(), m);
    }

    public TableMapping table(String key) {
        return tables.get(key);
    }

    /** Table mappings in load order. */
    public List<TableMapping> tables() {
        if (order == null) {
            return new ArrayList<>(tables.values());
        }
        List<TableMapping> out = new ArrayList<>();
        for (String k : order.ordered()) {
            out.add(tables.get(k));
        }
        return out;
    }

    public LoadOrder order() {
        return order;
    }

    public void order(LoadOrder order) {
        this.order = order;
    }

    public List<GeneratorTarget> generators() {
        return generators;
    }

    public List<IdentityColumn> identityColumns() {
        return identityColumns;
    }

    public List<String> excludedTables() {
        return excludedTables;
    }

    /** Source tables that store generator positions and are handled by the generator step. */
    public List<String> generatorTables() {
        return generatorTables;
    }

    public List<String> targetOnlyTables() {
        return targetOnlyTables;
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void error(String message) {
        errors.add(message);
    }

    public void warn(String message) {
        warnings.add(message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** An identity / auto-increment column of the target. */
    public static final class IdentityColumn {
        private final Table table;
        private final Column column;

        public IdentityColumn(Table table, Column column) {
            this.table = table;
            this.column = column;
        }

        public Table table() {
            return table;
        }

        public Column column() {
            return column;
        }

        @Override
        public String toString() {
            return table.name() + "." + column.name();
        }
    }
}
