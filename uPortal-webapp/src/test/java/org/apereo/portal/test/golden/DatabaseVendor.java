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
package org.apereo.portal.test.golden;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;

/**
 * The database the JPA/DAO tests run on (dataSource.properties, set by db-integration.yml). Goldens
 * are recorded on {@link #HSQLDB}; each documented difference of another vendor is keyed by this
 * enum.
 */
public enum DatabaseVendor {
    HSQLDB,
    POSTGRESQL,
    MYSQL,
    MARIADB,
    SQLSERVER,
    ORACLE;

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    public static DatabaseVendor of(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return of(connection.getMetaData());
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the database metadata", e);
        }
    }

    public static DatabaseVendor of(DatabaseMetaData metaData) throws SQLException {
        final String product = metaData.getDatabaseProductName().toLowerCase(Locale.ROOT);
        final String version =
                String.valueOf(metaData.getDatabaseProductVersion()).toLowerCase(Locale.ROOT);
        final String driver = String.valueOf(metaData.getDriverName()).toLowerCase(Locale.ROOT);
        if (product.contains("hsql")) {
            return HSQLDB;
        }
        if (product.contains("postgres")) {
            return POSTGRESQL;
        }
        // db-integration.yml connects to MariaDB with useMysqlMetadata=true (Hibernate 4.3 has no
        // dialect for the product name "MariaDB"), so look at the version and driver as well
        if (product.contains("mariadb")
                || version.contains("mariadb")
                || driver.contains("mariadb")) {
            return MARIADB;
        }
        if (product.contains("mysql")) {
            return MYSQL;
        }
        if (product.contains("microsoft") || product.contains("sql server")) {
            return SQLSERVER;
        }
        if (product.contains("oracle")) {
            return ORACLE;
        }
        throw new IllegalStateException("Unknown database: " + product + " " + version);
    }

    /**
     * A string as it reads back from a 5.17 string column of this database:
     *
     * <ul>
     *   <li>Oracle: the empty string is stored as null.
     *   <li>SQL Server: Hibernate 4.3's SQLServer2005Dialect maps strings to {@code varchar}, not
     *       {@code nvarchar}, so the database's code page (windows-1252 for the default collation)
     *       applies: every UTF-16 unit outside it is stored as '?'.
     *   <li>The others store the string as it is.
     * </ul>
     */
    public String storedString(String value) {
        if (value == null) {
            return null;
        }
        switch (this) {
            case ORACLE:
                return value.isEmpty() ? null : value;
            case SQLSERVER:
                return windows1252(value);
            default:
                return value;
        }
    }

    /** Each UTF-16 unit that windows-1252 cannot encode as '?' */
    public static String windows1252(String value) {
        final CharsetEncoder encoder = WINDOWS_1252.newEncoder();
        final StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            result.append(encoder.canEncode(c) ? c : '?');
        }
        return result.toString();
    }

    /** Lower-case name used in messages and file names */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
