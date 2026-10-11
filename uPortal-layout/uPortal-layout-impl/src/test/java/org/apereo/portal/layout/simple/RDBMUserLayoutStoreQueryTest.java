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
package org.apereo.portal.layout.simple;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.apereo.portal.jdbc.DatabaseMetaDataImpl;
import org.apereo.portal.jdbc.IDatabaseMetadata;
import org.apereo.portal.jdbc.IJoinQueryString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The layout query runs whether or not the database metadata found outer joins.
 * DatabaseMetaDataImpl tests for them only when the portal tables already exist, so a portal that
 * starts on an empty database (dataInit) takes the path without the join.
 */
public class RDBMUserLayoutStoreQueryTest {
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        connection =
                DriverManager.getConnection("jdbc:hsqldb:mem:layoutquery;shutdown=true", "SA", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE UP_LAYOUT_STRUCT (USER_ID INTEGER, LAYOUT_ID INTEGER,"
                            + " STRUCT_ID INTEGER, NAME VARCHAR(35))");
            statement.execute(
                    "CREATE TABLE UP_LAYOUT_PARAM (USER_ID INTEGER, LAYOUT_ID INTEGER,"
                            + " STRUCT_ID INTEGER, STRUCT_PARM_NM VARCHAR(255),"
                            + " STRUCT_PARM_VAL VARCHAR(2000))");
            statement.execute("INSERT INTO UP_LAYOUT_STRUCT VALUES (7, 1, 2, 'Tab')");
            statement.execute("INSERT INTO UP_LAYOUT_STRUCT VALUES (7, 1, 1, 'Root')");
            statement.execute("INSERT INTO UP_LAYOUT_PARAM VALUES (7, 1, 2, 'width', '50%')");
            statement.execute("INSERT INTO UP_LAYOUT_PARAM VALUES (7, 1, 2, 'color', 'red')");
        }
    }

    @After
    public void tearDown() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private List<String> rows(IDatabaseMetadata metadata, int columns) throws Exception {
        final String sql =
                "SELECT ULS.STRUCT_ID" + RDBMUserLayoutStore.layoutRowsQueryTail(metadata, 7, 1);
        final List<String> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                final StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    row.append(i > 1 ? " " : "").append(rs.getString(i));
                }
                rows.add(row.toString());
            }
        }
        return rows;
    }

    @Test
    public void runsWithoutOuterJoins() throws Exception {
        final IDatabaseMetadata metadata = mock(IDatabaseMetadata.class);
        when(metadata.supportsOuterJoins()).thenReturn(false);
        assertEquals("[1, 2]", rows(metadata, 1).toString());
    }

    @Test
    public void runsWithOuterJoins() throws Exception {
        final IJoinQueryString join = new DatabaseMetaDataImpl.PostgreSQLDb("");
        join.addQuery(
                "layout",
                "UP_LAYOUT_STRUCT ULS LEFT OUTER JOIN UP_LAYOUT_PARAM USP"
                        + " ON ULS.USER_ID = USP.USER_ID AND ULS.STRUCT_ID = USP.STRUCT_ID WHERE");
        final IDatabaseMetadata metadata = mock(IDatabaseMetadata.class);
        when(metadata.supportsOuterJoins()).thenReturn(true);
        when(metadata.getJoinQuery()).thenReturn(join);
        assertEquals("[1 null null, 2 color red, 2 width 50%]", rows(metadata, 3).toString());
    }
}
