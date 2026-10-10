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
package org.apereo.portal.dbmigrate.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apereo.portal.dbmigrate.Vendor;

/** One empty database on a {@link DbServer}, with the copy tool's connection arguments. */
public final class TestDatabase implements AutoCloseable {

    private final DbServer server;
    private final String name;
    private final String url;
    private final String user;
    private final String password;

    TestDatabase(DbServer server, String name, String url, String user, String password) {
        this.server = server;
        this.name = name;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public DbServer server() {
        return server;
    }

    public DbServer.Kind kind() {
        return server.kind();
    }

    public Vendor vendor() {
        return server.kind().vendor();
    }

    public String name() {
        return name;
    }

    public String url() {
        return url;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** The copy tool's options for this database as the given side ("source" or "target"). */
    public List<String> args(String side) {
        return new ArrayList<>(
                Arrays.asList(
                        "--" + side + "-url",
                        url,
                        "--" + side + "-user",
                        user,
                        "--" + side + "-password",
                        password));
    }

    /** Runs statements in one connection (autocommit). */
    public void execute(String... statements) throws SQLException {
        try (Connection c = connect();
                Statement st = c.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    public long scalar(String sql) throws SQLException {
        try (Connection c = connect();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Ends every session on this database (see {@link DbServer#killSessions}). */
    public int killSessions() throws SQLException {
        return server.killSessions(this);
    }

    @Override
    public void close() throws SQLException {
        server.drop(this);
    }

    @Override
    public String toString() {
        return server.kind().id() + "/" + name;
    }
}
