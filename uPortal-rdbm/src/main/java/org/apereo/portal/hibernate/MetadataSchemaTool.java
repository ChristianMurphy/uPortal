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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.hibernate.MappingException;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.Index;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.ExceptionHandlerHaltImpl;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.hibernate.tool.schema.spi.ScriptTargetOutput;
import org.hibernate.tool.schema.spi.TargetDescriptor;

/**
 * {@link SchemaTool} on the Hibernate 5 {@link MetadataImplementor} and schema management tool:
 * {@link SchemaExport} for create and drop, the schema migrator behind {@code SchemaUpdate} for
 * update, and the dialect's exporters for {@code tables.xml}. Every script goes to a {@link
 * TargetType#SCRIPT} target that collects the statements; nothing is executed here.
 */
public class MetadataSchemaTool implements SchemaTool {
    private final MetadataImplementor metadata;
    private final ServiceRegistry serviceRegistry;
    private final Dialect dialect;

    public MetadataSchemaTool(MetadataImplementor metadata, ServiceRegistry serviceRegistry) {
        this.metadata = metadata;
        this.serviceRegistry = serviceRegistry;
        this.dialect = serviceRegistry.getService(JdbcServices.class).getDialect();
    }

    @Override
    public List<String> getDropScript() {
        return export(SchemaExport.Action.DROP);
    }

    @Override
    public List<String> getCreateScript() {
        return export(SchemaExport.Action.CREATE);
    }

    private List<String> export(SchemaExport.Action action) {
        final CollectingTarget target = new CollectingTarget();
        new SchemaExport().doExecution(action, false, metadata, serviceRegistry, target);
        return target.statements;
    }

    @Override
    public List<String> getUpdateScript(Connection connection) {
        // SchemaUpdate itself always connects through the session factory's connection provider;
        // the migrator behind it reads the database through the caller's connection instead.
        final Map<Object, Object> settings =
                new HashMap<>(serviceRegistry.getService(ConfigurationService.class).getSettings());
        settings.put(AvailableSettings.HBM2DDL_CONNECTION, connection);
        settings.put(AvailableSettings.FORMAT_SQL, false);
        final ExecutionOptions options =
                SchemaManagementToolCoordinator.buildExecutionOptions(
                        settings, ExceptionHandlerHaltImpl.INSTANCE);
        final CollectingTarget target = new CollectingTarget();
        serviceRegistry
                .getService(SchemaManagementTool.class)
                .getSchemaMigrator(settings)
                .doMigration(metadata, options, target);
        return target.statements;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getDropScript(Collection<TableDefinition> tableDefinitions) {
        final Collection<Table> tables = toTables(tableDefinitions);
        final List<String> script = new ArrayList<>(tables.size() * 2);

        if (dialect.dropConstraints()) {
            for (final Table table : tables) {
                for (final Iterator<ForeignKey> fks = table.getForeignKeyIterator();
                        fks.hasNext(); ) {
                    final ForeignKey fk = fks.next();
                    if (fk.isPhysicalConstraint()) {
                        Collections.addAll(
                                script,
                                dialect.getForeignKeyExporter().getSqlDropStrings(fk, metadata));
                    }
                }
            }
        }

        for (final Table table : tables) {
            Collections.addAll(
                    script, dialect.getTableExporter().getSqlDropStrings(table, metadata));
        }

        return script;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getCreateScript(Collection<TableDefinition> tableDefinitions) {
        final Collection<Table> tables = toTables(tableDefinitions);
        final List<String> script = new ArrayList<>(tables.size() * 2);

        for (final Table table : tables) {
            Collections.addAll(
                    script, dialect.getTableExporter().getSqlCreateStrings(table, metadata));
        }

        for (final Table table : tables) {
            for (final Iterator<UniqueKey> uks = table.getUniqueKeyIterator(); uks.hasNext(); ) {
                Collections.addAll(
                        script,
                        dialect.getUniqueKeyExporter().getSqlCreateStrings(uks.next(), metadata));
            }

            for (final Iterator<Index> indexes = table.getIndexIterator(); indexes.hasNext(); ) {
                Collections.addAll(
                        script,
                        dialect.getIndexExporter().getSqlCreateStrings(indexes.next(), metadata));
            }

            if (dialect.hasAlterTable()) {
                for (final Iterator<ForeignKey> fks = table.getForeignKeyIterator();
                        fks.hasNext(); ) {
                    final ForeignKey fk = fks.next();
                    if (fk.isPhysicalConstraint()) {
                        Collections.addAll(
                                script,
                                dialect.getForeignKeyExporter().getSqlCreateStrings(fk, metadata));
                    }
                }
            }
        }

        return script;
    }

    /**
     * Builds the Hibernate table model in the default namespace ({@code hibernate.default_catalog}
     * and {@code hibernate.default_schema}), typing each column the way the dialect maps it.
     */
    private Collection<Table> toTables(Collection<TableDefinition> tableDefinitions) {
        final List<Table> tables = new ArrayList<>(tableDefinitions.size());
        for (final TableDefinition tableDefinition : tableDefinitions) {
            final Table table =
                    new Table(
                            metadata.getDatabase().getDefaultNamespace(),
                            Identifier.toIdentifier(tableDefinition.getName()),
                            false);
            final Map<String, Column> columns = new HashMap<>();
            for (final TableDefinition.Column columnDefinition : tableDefinition.getColumns()) {
                final Column column = new Column(columnDefinition.getName());
                final SimpleValue value = new SimpleValue(metadata, table);
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
                final PrimaryKey primaryKey = new PrimaryKey(table);
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

    /** A script target that keeps the statements in memory. */
    private static final class CollectingTarget implements TargetDescriptor, ScriptTargetOutput {
        private final List<String> statements = new ArrayList<>();

        @Override
        public EnumSet<TargetType> getTargetTypes() {
            return EnumSet.of(TargetType.SCRIPT);
        }

        @Override
        public ScriptTargetOutput getScriptTargetOutput() {
            return this;
        }

        @Override
        public void prepare() {}

        @Override
        public void accept(String command) {
            statements.add(command);
        }

        @Override
        public void release() {}
    }
}
