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
package org.apereo.portal.tools.dbloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import org.apereo.portal.hibernate.DelegatingHibernateIntegrator.HibernateConfiguration;
import org.apereo.portal.hibernate.SchemaTool;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * Checks the level of the log lines {@link DataSourceSchemaExport} writes: the statements that
 * failed and are expected to (they filled the build log of every CI job that creates a database) go
 * to DEBUG, and a statement whose failure stops the export is still an ERROR.
 */
public class DataSourceSchemaExportTest {
    private static final String IGNORABLE =
            "Failed to execute (probably ignorable): {}, error message {}";

    private Logger logger;
    private JdbcOperations jdbcOperations;
    private SchemaTool schemaTool;
    private DataSourceSchemaExport export;

    @Before
    public void setUp() {
        logger = mock(Logger.class);
        jdbcOperations = mock(JdbcOperations.class);
        schemaTool = mock(SchemaTool.class);
        final HibernateConfiguration configuration = mock(HibernateConfiguration.class);
        when(configuration.getSchemaTool()).thenReturn(schemaTool);

        // The export keeps the logger LoggerFactory gives it when it is constructed
        try (MockedStatic<LoggerFactory> loggerFactory = mockStatic(LoggerFactory.class)) {
            loggerFactory.when(() -> LoggerFactory.getLogger(any(Class.class))).thenReturn(logger);
            export = new DataSourceSchemaExport();
        }
        export.setPersistenceUnit("PortalDb");
        export.setJdbcOperations(jdbcOperations);
        export.setConfiguration("PortalDb", configuration);
    }

    private void failWith(String sql, String message) {
        doThrow(new BadSqlGrammarException("test", sql, new SQLException(message)))
                .when(jdbcOperations)
                .execute(sql);
    }

    @Test
    public void testFailedDropConstraintInACreateIsDebug() {
        final String sql = "alter table UP_FOO drop constraint FK_BAR";
        when(schemaTool.getCreateScript()).thenReturn(List.of(sql));
        failWith(sql, "constraint does not exist");

        export.create(true, null, false);

        verify(logger).debug(eq(IGNORABLE), eq(sql), contains("constraint does not exist"));
        verify(logger, never()).info(eq(IGNORABLE), any(Object.class), any(Object.class));
        verify(logger, never()).error(anyString(), any(Object.class), any(Object.class));
    }

    @Test
    public void testFailedStatementInADropIsDebug() {
        final String sql = "drop sequence UP_FOO_SEQ";
        when(schemaTool.getDropScript()).thenReturn(List.of(sql));
        failWith(sql, "sequence does not exist");

        export.drop(true, null, false);

        verify(logger).debug(eq(IGNORABLE), eq(sql), contains("sequence does not exist"));
        verify(logger, never()).info(eq(IGNORABLE), any(Object.class), any(Object.class));
        verify(logger, never()).error(anyString(), any(Object.class), any(Object.class));
    }

    @Test
    public void testFailedCreateStatementIsAnErrorAndStopsTheExport() {
        final String failing = "create table UP_FOO (ID bigint not null)";
        final String later = "create table UP_BAR (ID bigint not null)";
        when(schemaTool.getCreateScript()).thenReturn(List.of(failing, later));
        failWith(failing, "table already exists");

        final RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> export.create(true, null, false));

        assertEquals("Failed to execute: " + failing, thrown.getMessage());
        verify(logger)
                .error(
                        eq("Failed to execute: {}\n\t{}"),
                        eq(failing),
                        contains("table already exists"));
        verify(logger, never()).debug(eq(IGNORABLE), any(Object.class), any(Object.class));
        verify(jdbcOperations, never()).execute(later);
    }

    @Test
    public void testExecutedStatementIsInfo() {
        final String sql = "create table UP_FOO (ID bigint not null)";
        when(schemaTool.getCreateScript()).thenReturn(List.of(sql));

        export.create(true, null, false);

        verify(logger).info(sql);
    }
}
