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
package org.apereo.portal.utils;

import static org.junit.Assert.assertEquals;

import java.sql.Types;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.internal.StandardDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.junit.Test;

/**
 * The dialect uPortal uses for each database: {@link PortalDialectResolver} first, then Hibernate's
 * standard resolver, as {@code hibernate.dialect_resolvers} configures it. The dialect decides
 * column types and whether ids are backed by sequences or one-row tables, so these choices must not
 * move without a database migration.
 */
public class PortalDialectResolverTest {

    @Test
    public void hsqldb() {
        assertDialect("org.hibernate.dialect.HSQLDialect", "HSQL Database Engine", 2, 5, null);
    }

    @Test
    public void postgresql() {
        assertDialect(
                "org.apereo.portal.utils.hibernate4.dialects.PostgreSQL81TextClobDialect",
                "PostgreSQL",
                16,
                15,
                null);
        assertDialect(
                "org.apereo.portal.utils.hibernate4.dialects.PostgreSQL81TextClobDialect",
                "PostgreSQL",
                10,
                0,
                null);
        assertDialect("org.hibernate.dialect.PostgreSQL9Dialect", "PostgreSQL", 9, 6, null);
        assertDialect("org.hibernate.dialect.PostgreSQL82Dialect", "PostgreSQL", 8, 4, null);
        assertDialect(
                "org.apereo.portal.utils.hibernate4.dialects.PostgreSQL81Dialect",
                "PostgreSQL",
                8,
                1,
                null);
    }

    @Test
    public void mysqlAndMariadb() {
        // Hibernate 4.3 chose MySQL5Dialect, which named no storage engine (InnoDB by default);
        // MySQL55Dialect has the same types and names InnoDB, unlike Hibernate 5's MySQL5Dialect
        assertDialect("org.hibernate.dialect.MySQL55Dialect", "MySQL", 8, 4, "MySQL Connector/J");
        // MySQL Connector/J reports MariaDB 10.x as 5.5.5-10.x
        assertDialect(
                "org.apereo.portal.utils.hibernate4.dialects.MySQL5InnoDBCompressedDialect",
                "MySQL",
                5,
                5,
                "MySQL Connector/J");
        // MariaDB Connector/J with useMysqlMetadata=true
        assertDialect(
                "org.hibernate.dialect.MySQL55Dialect", "MySQL", 11, 4, "MariaDB Connector/J");
        assertDialect(
                "org.hibernate.dialect.MySQL55Dialect", "MySQL", 10, 6, "MariaDB Connector/J");
    }

    @Test
    public void sqlServer() {
        assertDialect(
                "org.hibernate.dialect.SQLServer2005Dialect", "Microsoft SQL Server", 16, 0, null);
        assertDialect(
                "org.hibernate.dialect.SQLServer2005Dialect", "Microsoft SQL Server", 10, 50, null);
        assertDialect(
                "org.hibernate.dialect.SQLServer2005Dialect", "Microsoft SQL Server", 9, 0, null);
    }

    @Test
    public void oracle() {
        assertDialect(
                "org.apereo.portal.utils.hibernate4.dialects.Oracle12ForceClobDialect",
                "Oracle",
                23,
                26,
                null);
        assertDialect(
                "org.apereo.portal.utils.hibernate4.dialects.Oracle12ForceClobDialect",
                "Oracle",
                19,
                0,
                null);
        assertDialect("org.hibernate.dialect.Oracle10gDialect", "Oracle", 11, 2, null);
    }

    /**
     * The large-object strings (UP_RAW_EVENTS.EVENT_DATA, UP_ANALYTICS_EVENTS.EVENT_DATA) stay text
     * columns on PostgreSQL, as in every existing uPortal database (Hibernate 5.6.2+ would declare
     * them oid).
     */
    @Test
    public void postgresqlClobColumnsAreText() {
        final Dialect dialect = resolve("PostgreSQL", 16, 15, null);
        assertEquals("text", dialect.getTypeName(Types.CLOB));
    }

    private static void assertDialect(
            String expected, String databaseName, int major, int minor, String driverName) {
        final Dialect dialect = resolve(databaseName, major, minor, driverName);
        assertEquals(
                databaseName + " " + major + "." + minor,
                expected,
                dialect == null ? null : dialect.getClass().getName());
    }

    private static Dialect resolve(String databaseName, int major, int minor, String driverName) {
        final DialectResolutionInfo info =
                new DialectResolutionInfo() {
                    @Override
                    public String getDatabaseName() {
                        return databaseName;
                    }

                    @Override
                    public int getDatabaseMajorVersion() {
                        return major;
                    }

                    @Override
                    public int getDatabaseMinorVersion() {
                        return minor;
                    }

                    @Override
                    public String getDriverName() {
                        return driverName;
                    }

                    @Override
                    public int getDriverMajorVersion() {
                        return NO_VERSION;
                    }

                    @Override
                    public int getDriverMinorVersion() {
                        return NO_VERSION;
                    }
                };
        final Dialect dialect = new PortalDialectResolver().resolveDialect(info);
        return dialect != null ? dialect : new StandardDialectResolver().resolveDialect(info);
    }
}
