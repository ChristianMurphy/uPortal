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
import java.util.Collection;
import java.util.List;

/**
 * The DDL Hibernate generates for one persistence unit, and for the tables described outside the
 * entity mappings (<code>tables.xml</code>).
 *
 * <p>This is the only place in uPortal that depends on how the Hibernate version in use builds
 * schema scripts. Callers execute or write out the statements themselves, so their error handling
 * and output stay the same whichever implementation produced the statements.
 */
public interface SchemaTool {

    /** Statements that drop every table, constraint and sequence the mappings define. */
    List<String> getDropScript();

    /** Statements that create every table, constraint and sequence the mappings define. */
    List<String> getCreateScript();

    /**
     * Statements that add what the mappings define and the database behind the connection lacks.
     */
    List<String> getUpdateScript(Connection connection) throws SQLException;

    /** Statements that drop the given tables. */
    List<String> getDropScript(Collection<TableDefinition> tables);

    /** Statements that create the given tables with their unique keys and indexes. */
    List<String> getCreateScript(Collection<TableDefinition> tables);
}
