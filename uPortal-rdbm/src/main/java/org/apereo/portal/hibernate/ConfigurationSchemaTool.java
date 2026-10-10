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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.MappingException;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.Mappings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.Index;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;
import org.hibernate.tool.hbm2ddl.FixedDatabaseMetadata;

/** {@link SchemaTool} on the Hibernate 4 {@link Configuration} API. */
public class ConfigurationSchemaTool implements SchemaTool {
    private final Configuration configuration;
    private final Dialect dialect;

    public ConfigurationSchemaTool(Configuration configuration, Dialect dialect) {
        this.configuration = configuration;
        this.dialect = dialect;
    }

    @Override
    public List<String> getDropScript() {
        return Arrays.asList(configuration.generateDropSchemaScript(dialect));
    }

    @Override
    public List<String> getCreateScript() {
        return Arrays.asList(configuration.generateSchemaCreationScript(dialect));
    }

    @Override
    public List<String> getUpdateScript(Connection connection) throws SQLException {
        final FixedDatabaseMetadata databaseMetadata =
                new FixedDatabaseMetadata(connection, dialect);
        return Arrays.asList(configuration.generateSchemaUpdateScript(dialect, databaseMetadata));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getDropScript(Collection<TableDefinition> tableDefinitions) {
        final Collection<Table> tables = toTables(tableDefinitions);
        final String defaultCatalog = configuration.getProperty(Environment.DEFAULT_CATALOG);
        final String defaultSchema = configuration.getProperty(Environment.DEFAULT_SCHEMA);
        final List<String> script = new ArrayList<String>(tables.size() * 2);

        if (dialect.dropConstraints()) {
            for (final Table table : tables) {
                if (table.isPhysicalTable()) {
                    for (final Iterator<ForeignKey> subIter = table.getForeignKeyIterator();
                            subIter.hasNext(); ) {
                        final ForeignKey fk = subIter.next();
                        if (fk.isPhysicalConstraint()) {
                            script.add(fk.sqlDropString(dialect, defaultCatalog, defaultSchema));
                        }
                    }
                }
            }
        }

        for (final Table table : tables) {
            if (table.isPhysicalTable()) {
                script.add(table.sqlDropString(dialect, defaultCatalog, defaultSchema));
            }
        }

        return script;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getCreateScript(Collection<TableDefinition> tableDefinitions) {
        final Collection<Table> tables = toTables(tableDefinitions);
        final Mapping mapping = configuration.buildMapping();
        final String defaultCatalog = configuration.getProperty(Environment.DEFAULT_CATALOG);
        final String defaultSchema = configuration.getProperty(Environment.DEFAULT_SCHEMA);
        final List<String> script = new ArrayList<String>(tables.size() * 2);

        for (final Table table : tables) {
            if (table.isPhysicalTable()) {
                script.add(table.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema));
            }
        }

        for (final Table table : tables) {
            if (table.isPhysicalTable()) {
                for (final Iterator<UniqueKey> subIter = table.getUniqueKeyIterator();
                        subIter.hasNext(); ) {
                    final UniqueKey uk = subIter.next();
                    final String constraintString =
                            uk.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema);
                    if (StringUtils.isNotBlank(constraintString)) {
                        script.add(constraintString);
                    }
                }

                for (final Iterator<Index> subIter = table.getIndexIterator();
                        subIter.hasNext(); ) {
                    final Index index = subIter.next();
                    script.add(
                            index.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema));
                }

                if (dialect.hasAlterTable()) {
                    for (final Iterator<ForeignKey> subIter = table.getForeignKeyIterator();
                            subIter.hasNext(); ) {
                        final ForeignKey fk = subIter.next();
                        if (fk.isPhysicalConstraint()) {
                            script.add(
                                    fk.sqlCreateString(
                                            dialect, mapping, defaultCatalog, defaultSchema));
                        }
                    }
                }
            }
        }

        return script;
    }

    /** Builds the Hibernate table model, typing each column the way the dialect maps it. */
    private Collection<Table> toTables(Collection<TableDefinition> tableDefinitions) {
        final Mappings mappings = new Configuration().createMappings();
        final List<Table> tables = new ArrayList<Table>(tableDefinitions.size());
        for (final TableDefinition tableDefinition : tableDefinitions) {
            final Table table = new Table(tableDefinition.getName());
            final Map<String, Column> columns = new HashMap<String, Column>();
            for (final TableDefinition.Column columnDefinition : tableDefinition.getColumns()) {
                final Column column = new Column(columnDefinition.getName());
                final SimpleValue value = new SimpleValue(mappings, table);
                value.setTypeName(getHibernateType(columnDefinition));
                column.setValue(value);
                if (columnDefinition.getLength() != null) {
                    column.setLength(columnDefinition.getLength());
                }
                column.setNullable(columnDefinition.isNullable());
                table.addColumn(column);
                columns.put(column.getName(), column);
            }

            if (!tableDefinition.getPrimaryKey().isEmpty()) {
                final PrimaryKey primaryKey = new PrimaryKey();
                for (final String columnName : tableDefinition.getPrimaryKey()) {
                    primaryKey.addColumn(columns.get(columnName));
                }
                table.setPrimaryKey(primaryKey);
            }

            for (final Map.Entry<String, List<String>> uniqueKeyDefinition :
                    tableDefinition.getUniqueKeys().entrySet()) {
                final UniqueKey uniqueKey = new UniqueKey();
                uniqueKey.setName(uniqueKeyDefinition.getKey());
                uniqueKey.setTable(table);
                for (final String columnName : uniqueKeyDefinition.getValue()) {
                    uniqueKey.addColumn(columns.get(columnName));
                }
                table.addUniqueKey(uniqueKey);
            }

            for (final Map.Entry<String, List<String>> indexDefinition :
                    tableDefinition.getIndexes().entrySet()) {
                final Index index = new Index();
                index.setName(indexDefinition.getKey());
                index.setTable(table);
                for (final String columnName : indexDefinition.getValue()) {
                    index.addColumn(columns.get(columnName));
                }
                table.addIndex(index);
            }

            tables.add(table);
        }
        return tables;
    }

    private String getHibernateType(TableDefinition.Column column) {
        try {
            return dialect.getHibernateTypeName(column.getSqlType());
        } catch (MappingException e) {
            throw new IllegalArgumentException(
                    "No mapped hibernate type found for '"
                            + column.getSqlType()
                            + "' Types value="
                            + column.getSqlType()
                            + " for column '"
                            + column.getName()
                            + "'",
                    e);
        }
    }
}
