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

import java.util.Locale;

/** The database products uPortal supports, detected from the JDBC metadata. */
public enum Vendor {
    HSQLDB("HSQLDB"),
    POSTGRESQL("PostgreSQL"),
    MYSQL("MySQL"),
    MARIADB("MariaDB"),
    SQLSERVER("SQL Server"),
    ORACLE("Oracle"),
    OTHER("other");

    private final String displayName;

    Vendor(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * MariaDB is reported as "MySQL" by MySQL Connector/J and by MariaDB Connector/J with {@code
     * useMysqlMetadata=true} (MOD-I73); its version string still names MariaDB.
     */
    public static Vendor detect(String productName, String productVersion) {
        String p = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        String v = productVersion == null ? "" : productVersion.toLowerCase(Locale.ROOT);
        if (p.contains("postgres")) {
            return POSTGRESQL;
        }
        if (p.contains("hsql")) {
            return HSQLDB;
        }
        if (p.contains("mariadb") || (p.contains("mysql") && v.contains("mariadb"))) {
            return MARIADB;
        }
        if (p.contains("mysql")) {
            return MYSQL;
        }
        if (p.contains("microsoft sql server") || p.contains("sql server")) {
            return SQLSERVER;
        }
        if (p.contains("oracle")) {
            return ORACLE;
        }
        return OTHER;
    }

    public boolean isMySqlFamily() {
        return this == MYSQL || this == MARIADB;
    }
}
