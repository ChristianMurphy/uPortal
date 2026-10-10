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

import java.sql.Types;

/**
 * Hibernate's {@code PostgreSQL81Dialect}, the dialect uPortal uses for PostgreSQL 10 and later,
 * with CLOB columns declared as {@code text}.
 *
 * <p>The {@code @Lob} strings {@code UP_RAW_EVENTS.EVENT_DATA} and {@code
 * UP_ANALYTICS_EVENTS.EVENT_DATA} are {@code text} columns in every existing uPortal database on
 * PostgreSQL. Hibernate keeps their values as large objects and stores each object's OID in the
 * column (CLOB binding). Hibernate 5.6.2 and later declare CLOB columns as {@code oid}, so a new
 * database would no longer match the existing ones. This keeps {@code text} until a migration
 * converts existing databases. Until then {@code vacuumlo} must not be run against a uPortal
 * database: it deletes large objects that are referenced only from {@code text} columns.
 */
public class PostgreSQL81TextClobDialect extends org.hibernate.dialect.PostgreSQL81Dialect {

    public PostgreSQL81TextClobDialect() {
        super();
        registerColumnType(Types.CLOB, "text");
    }
}
