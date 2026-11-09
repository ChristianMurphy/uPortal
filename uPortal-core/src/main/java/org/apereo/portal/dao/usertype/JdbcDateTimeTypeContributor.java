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
package org.apereo.portal.dao.usertype;

import java.sql.Date;
import java.sql.Time;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.java.JdbcDateTypeDescriptor;
import org.hibernate.type.descriptor.java.JdbcTimeTypeDescriptor;

/**
 * Gives Hibernate a Java type descriptor for {@link Date java.sql.Date} and one for {@link Time
 * java.sql.Time}, so that an {@code AttributeConverter} to one of them gets a {@code DATE} or
 * {@code TIME} column.
 *
 * <p>Hibernate 5.6 takes a converted attribute's JDBC type from the descriptor of the converter's
 * database type. It registers all of its date descriptors under {@code java.util.Date}, finds no
 * entry for the two JDBC classes, and falls back to the first entry they can be assigned to, which
 * recommends {@code TIMESTAMP}. The column of {@link LocalDateColumnConverter} and of {@link
 * LocalTimeColumnConverter} then becomes a {@code TIMESTAMP}, and the value is bound with the
 * {@code hibernate.jdbc.time_zone} calendar. Hibernate 5.2 mapped the classes straight to {@code
 * DATE} and {@code TIME}. {@code JavaTimeColumnStorageTest} checks the column types and the stored
 * values.
 *
 * <p>Listed in {@code META-INF/services/org.hibernate.boot.model.TypeContributor}.
 */
public class JdbcDateTimeTypeContributor implements TypeContributor {
    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        typeContributions.contributeJavaTypeDescriptor(new SqlDateJavaDescriptor());
        typeContributions.contributeJavaTypeDescriptor(new SqlTimeJavaDescriptor());
    }

    /**
     * Hibernate's descriptor for JDBC dates, registered under {@code java.sql.Date}. The JDBC type
     * that {@code BasicJavaDescriptor} recommends follows the Java type: {@code DATE}.
     */
    private static final class SqlDateJavaDescriptor extends JdbcDateTypeDescriptor {
        private static final long serialVersionUID = 1L;

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Class getJavaType() {
            return Date.class;
        }
    }

    /** As {@link SqlDateJavaDescriptor}, for {@code java.sql.Time} and {@code TIME}. */
    private static final class SqlTimeJavaDescriptor extends JdbcTimeTypeDescriptor {
        private static final long serialVersionUID = 1L;

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Class getJavaType() {
            return Time.class;
        }
    }
}
