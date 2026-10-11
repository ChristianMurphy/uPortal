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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    /** The SQL Server database last seen by {@link #of(DataSource)}, for {@link #storedString} */
    private static volatile DataSource sqlServer;

    private static final Map<String, String> SQL_SERVER_VARCHAR = new ConcurrentHashMap<>();

    public static DatabaseVendor of(DataSource dataSource) {
        final DatabaseVendor vendor;
        try (Connection connection = dataSource.getConnection()) {
            vendor = of(connection.getMetaData());
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the database metadata", e);
        }
        if (vendor == SQLSERVER) {
            sqlServer = dataSource;
        }
        return vendor;
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
     *       {@code nvarchar}, so the database converts each value to its code page ({@link
     *       #sqlServerVarchar}).
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
                return sqlServerVarchar(value);
            default:
                return value;
        }
    }

    /**
     * {@code value} as a {@code varchar} column of the SQL Server database stores it. The JDBC
     * driver sends strings as {@code nvarchar}, and the database converts them to the code page of
     * its collation (windows-1252 for the default one): characters without an exact or best-fit
     * mapping become '?'. The conversion is read from the database itself, with {@code CAST(? AS
     * VARCHAR(MAX))}, so it matches what an insert stores. Printable ASCII is unchanged.
     */
    public static String sqlServerVarchar(String value) {
        if (value.chars().allMatch(c -> c >= 0x20 && c <= 0x7e)) {
            return value;
        }
        final DataSource dataSource = sqlServer;
        if (dataSource == null) {
            throw new IllegalStateException("No SQL Server database was seen by of(DataSource)");
        }
        return SQL_SERVER_VARCHAR.computeIfAbsent(value, v -> castToVarchar(dataSource, v));
    }

    private static String castToVarchar(DataSource dataSource, String value) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT CAST(? AS VARCHAR(MAX))")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot convert a string on SQL Server", e);
        }
    }

    /** Lower-case name used in messages and file names */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
