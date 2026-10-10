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
package org.apereo.portal.dbmigrate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * How to reach one database: JDBC URL, credentials and, optionally, the schema to read or write.
 * Without a schema, the connection's current schema (or catalog on MySQL/MariaDB) is used.
 */
public final class ConnectionSpec {

    private final String label;
    private final String url;
    private final String user;
    private final String password;
    private final String schema;

    public ConnectionSpec(String label, String url, String user, String password, String schema) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("No JDBC URL for the " + label + " database");
        }
        this.label = label;
        this.url = url.trim();
        this.user = user;
        this.password = password;
        this.schema = schema == null || schema.trim().isEmpty() ? null : schema.trim();
    }

    /**
     * Reads {@code url}, {@code user} (or {@code username}), {@code password} and {@code schema}.
     * The {@code hibernate.connection.*} keys of a uPortal {@code global.properties} or
     * uPortal-start vendor file work too.
     */
    public static ConnectionSpec fromProperties(String label, Path file) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        return fromProperties(label, p);
    }

    public static ConnectionSpec fromProperties(String label, Properties p) {
        String url = first(p, "url", "jdbc.url", "hibernate.connection.url");
        String user = first(p, "user", "username", "jdbc.user", "hibernate.connection.username");
        String password = first(p, "password", "jdbc.password", "hibernate.connection.password");
        return new ConnectionSpec(label, url, user, password, first(p, "schema"));
    }

    private static String first(Properties p, String... keys) {
        for (String k : keys) {
            String v = p.getProperty(k);
            if (v != null) {
                return v.trim();
            }
        }
        return null;
    }

    public Connection open() throws SQLException {
        Properties props = new Properties();
        if (user != null) {
            props.setProperty("user", user);
        }
        if (password != null) {
            props.setProperty("password", password);
        }
        Connection c = driver().connect(url, props);
        if (c == null) {
            throw new SQLException("The JDBC driver refused " + safeUrl());
        }
        return c;
    }

    /**
     * The JDBC driver for the URL, found with {@link ServiceLoader} in the context class loader.
     * {@link java.sql.DriverManager} is not used: it rejects a driver whose class its caller's
     * class loader resolves differently, which happens when a driver is both inside the executable
     * jar and on {@code loader.path} (HSQLDB).
     */
    public Driver driver() throws SQLException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (Driver d : ServiceLoader.load(Driver.class, cl)) {
            if (d.acceptsURL(url)) {
                return d;
            }
        }
        throw new SQLException("No JDBC driver on the class path accepts " + safeUrl());
    }

    public ConnectionSpec withOverrides(String newUser, String newPassword, String newSchema) {
        return new ConnectionSpec(
                label,
                url,
                newUser != null ? newUser : user,
                newPassword != null ? newPassword : password,
                newSchema != null ? newSchema : schema);
    }

    public String label() {
        return label;
    }

    public String url() {
        return url;
    }

    public String user() {
        return user;
    }

    public String schema() {
        return schema;
    }

    public String password() {
        return password;
    }

    /** The URL without any password parameter, for logs and reports. */
    public String safeUrl() {
        return url.replaceAll("(?i)(password=)[^;&]*", "$1***");
    }

    @Override
    public String toString() {
        return label + " " + safeUrl() + (user == null ? "" : " as " + user);
    }
}
