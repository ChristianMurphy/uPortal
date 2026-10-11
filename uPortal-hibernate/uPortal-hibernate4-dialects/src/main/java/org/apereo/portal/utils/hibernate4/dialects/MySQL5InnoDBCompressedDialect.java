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
package org.apereo.portal.utils.hibernate4.dialects;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.hibernate.boot.Metadata;
import org.hibernate.dialect.MySQL5InnoDBDialect;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.internal.StandardTableExporter;
import org.hibernate.tool.schema.spi.Exporter;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicBinder;
import org.hibernate.type.descriptor.sql.DoubleTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Uses the COMPRESSED row format in an InnoDB engine, needed for long index support with UTF-8 */
public class MySQL5InnoDBCompressedDialect extends MySQL5InnoDBDialect {

    private final StandardTableExporter tableExporter =
            new StandardTableExporter(this) {
                /**
                 * Hibernate 4.3 created the one-row tables that stand in for sequences without the
                 * table type, so they use the server's default row format. Hibernate 5 exports them
                 * like any other table; strip the table type again so new databases match.
                 */
                @Override
                public String[] getSqlCreateStrings(Table table, Metadata metadata) {
                    final String[] sql = super.getSqlCreateStrings(table, metadata);
                    final String tableType = getTableTypeString();
                    if (!table.getInitCommands().isEmpty() && sql[0].endsWith(tableType)) {
                        sql[0] = sql[0].substring(0, sql[0].length() - tableType.length());
                    }
                    return sql;
                }
            };

    public MySQL5InnoDBCompressedDialect() {
        super();
    }

    @Override
    public String getTableTypeString() {
        return super.getTableTypeString() + " ROW_FORMAT=COMPRESSED";
    }

    @Override
    public Exporter<Table> getTableExporter() {
        return tableExporter;
    }

    @Override
    protected SqlTypeDescriptor getSqlTypeDescriptorOverride(int sqlCode) {
        switch (sqlCode) {
            case Types.DOUBLE:
                {
                    return MySqlDoubleTypeDescriptor.INSTANCE;
                }
            default:
                {
                    return super.getSqlTypeDescriptorOverride(sqlCode);
                }
        }
    }

    private static class MySqlDoubleTypeDescriptor extends DoubleTypeDescriptor {
        private static final Logger LOGGER =
                LoggerFactory.getLogger(MySqlDoubleTypeDescriptor.class);
        private static final long serialVersionUID = 1L;

        public static final MySqlDoubleTypeDescriptor INSTANCE = new MySqlDoubleTypeDescriptor();

        @Override
        public <X> ValueBinder<X> getBinder(final JavaTypeDescriptor<X> javaTypeDescriptor) {
            return new BasicBinder<X>(javaTypeDescriptor, this) {
                @Override
                protected void doBind(
                        PreparedStatement st, X value, int index, WrapperOptions options)
                        throws SQLException {
                    st.setDouble(index, toMySqlDouble(value, options));
                }

                @Override
                protected void doBind(
                        CallableStatement st, X value, String name, WrapperOptions options)
                        throws SQLException {
                    st.setDouble(name, toMySqlDouble(value, options));
                }

                private double toMySqlDouble(X value, WrapperOptions options) {
                    Double unwrappedValue = javaTypeDescriptor.unwrap(value, Double.class, options);
                    if (unwrappedValue == Double.NEGATIVE_INFINITY) {
                        LOGGER.debug("Converting double from NEGATIVE_INFINITY to MIN_VALUE");
                        unwrappedValue = Double.MIN_VALUE;
                    } else if (unwrappedValue == Double.POSITIVE_INFINITY) {
                        LOGGER.debug("Converting double from POSITIVE_INFINITY to MAX_VALUE");
                        unwrappedValue = Double.MAX_VALUE;
                    } else if (unwrappedValue.isNaN()) {
                        LOGGER.debug("Converting double from NaN to 0");
                        unwrappedValue = 0d;
                    }
                    return unwrappedValue;
                }
            };
        }
    }
}
