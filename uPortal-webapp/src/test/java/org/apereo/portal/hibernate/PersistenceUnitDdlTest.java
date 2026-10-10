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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import javax.sql.DataSource;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.io.IOUtils;
import org.apereo.portal.hibernate.DelegatingHibernateIntegrator.HibernateConfiguration;
import org.apereo.portal.tools.dbloader.TableXmlHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.xml.sax.InputSource;

/**
 * Pins the DDL that {@link SchemaTool} produces for each persistence unit and for {@code
 * tables.xml}, per database the DB upgrade harness covers, so that a Hibernate upgrade shows
 * exactly which statements it changes.
 *
 * <p>Each persistence unit is bootstrapped the way the portal does it (Spring, {@code
 * persistence.xml}, the {@code hibernate*.cfg.xml} files and {@code PortalDialectResolver}) on an
 * in-memory HSQLDB. For the other databases the JDBC metadata reports that database's product name
 * and version, as its driver does, so the dialect is chosen exactly as on a real server.
 *
 * <p>The expected DDL is in {@code src/test/resources/org/apereo/portal/hibernate/ddl}, one file
 * per database, with each section's statements sorted. The DDL the test produced is always written
 * to {@code build/ddl}; when a change to the DDL is intended, copy it over the expected file and
 * explain the difference in the commit.
 *
 * @see org.apereo.portal.utils.PortalDialectResolver
 */
@RunWith(Parameterized.class)
public class PersistenceUnitDdlTest {

    private static final List<String> PERSISTENCE_UNITS =
            Arrays.asList("PortalDb", "RawEventsDb", "AggrEventsDb");

    private static final String TABLES_FILE = "/properties/db/tables.xml";

    @Parameters(name = "{0}")
    public static Collection<Object[]> databases() {
        // What each harness vendor's JDBC driver reports (tests/db/snapshots in uPortal-start)
        return Arrays.asList(
                new Object[][] {
                    {"hsqldb", null, null, 0, 0},
                    {"postgres-16", "PostgreSQL", "16.15", 16, 15},
                    {"mysql-8.4", "MySQL", "8.4.11", 8, 4},
                    // MySQL Connector/J reports MariaDB 10.x as version 5.5.5-10.x
                    {"mariadb-10.6", "MySQL", "5.5.5-10.6.28-MariaDB-ubu2204", 5, 5},
                    // MariaDB Connector/J with useMysqlMetadata=true
                    {"mariadb-11.4", "MySQL", "11.4.13-MariaDB-ubu2404", 11, 4},
                    {"sqlserver-2022", "Microsoft SQL Server", "16.00.4295", 16, 0},
                    {
                        "oracle-23",
                        "Oracle",
                        "Oracle AI Database 26ai Free Release 23.26.3.0.0",
                        23,
                        26
                    }
                });
    }

    private final String database;
    private final String productName;
    private final String productVersion;
    private final int majorVersion;
    private final int minorVersion;

    public PersistenceUnitDdlTest(
            String database,
            String productName,
            String productVersion,
            int majorVersion,
            int minorVersion) {
        this.database = database;
        this.productName = productName;
        this.productVersion = productVersion;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    @Test
    public void ddlMatchesExpected() throws Exception {
        final DriverManagerDataSource hsqldb =
                new DriverManagerDataSource("jdbc:hsqldb:mem:ddl-" + database, "sa", "");
        hsqldb.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");
        final DataSource dataSource = productName == null ? hsqldb : reportingVendor(hsqldb);

        final Map<String, HibernateConfiguration> configurations = new LinkedHashMap<>();
        final HibernateConfigurationAwareInjector injector =
                new HibernateConfigurationAwareInjector();
        injector.setHibernateConfigurationAwareBeans(
                Collections.singleton(
                        new HibernateConfigurationAware() {
                            @Override
                            public boolean supports(String persistenceUnit) {
                                return true;
                            }

                            @Override
                            public void setConfiguration(
                                    String persistenceUnit, HibernateConfiguration configuration) {
                                configurations.put(persistenceUnit, configuration);
                            }
                        }));
        injector.registerConfigurationAwareBean();
        // Registering replays the units other tests already built; keep only this test's
        configurations.clear();

        final List<LocalContainerEntityManagerFactoryBean> factories = new ArrayList<>();
        final Map<String, Collection<String>> ddl = new LinkedHashMap<>();
        try {
            for (final String persistenceUnit : PERSISTENCE_UNITS) {
                factories.add(entityManagerFactory(persistenceUnit, dataSource));
            }

            for (final String persistenceUnit : PERSISTENCE_UNITS) {
                final SchemaTool schemaTool = configurations.get(persistenceUnit).getSchemaTool();
                ddl.put(persistenceUnit + " create", schemaTool.getCreateScript());
                ddl.put(persistenceUnit + " drop", schemaTool.getDropScript());
            }

            final SchemaTool portalDb = configurations.get("PortalDb").getSchemaTool();
            final Collection<TableDefinition> tables = readTables().values();
            ddl.put("tables.xml create", portalDb.getCreateScript(tables));
            ddl.put("tables.xml drop", portalDb.getDropScript(tables));

            if (productName == null) {
                // Only HSQLDB can run the DDL: after creating everything, update has nothing to do
                try (Connection connection = hsqldb.getConnection();
                        Statement statement = connection.createStatement()) {
                    for (final String persistenceUnit : PERSISTENCE_UNITS) {
                        for (final String sql : ddl.get(persistenceUnit + " create")) {
                            statement.execute(sql);
                        }
                    }
                    for (final String persistenceUnit : PERSISTENCE_UNITS) {
                        ddl.put(
                                persistenceUnit + " update after create",
                                configurations
                                        .get(persistenceUnit)
                                        .getSchemaTool()
                                        .getUpdateScript(connection));
                    }
                }
            }
        } finally {
            for (final LocalContainerEntityManagerFactoryBean factory : factories) {
                factory.destroy();
            }
            injector.unregisterConfigurationAwareBean();
            try (Connection connection = hsqldb.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }

        assertExpected(render(ddl));
    }

    private LocalContainerEntityManagerFactoryBean entityManagerFactory(
            String persistenceUnit, DataSource dataSource) {
        final LocalContainerEntityManagerFactoryBean factory =
                new LocalContainerEntityManagerFactoryBean();
        factory.setPersistenceUnitName(persistenceUnit);
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        final Properties properties = new Properties();
        // The caches do not change the schema and would need the portal's cache manager
        properties.setProperty("hibernate.cache.use_second_level_cache", "false");
        properties.setProperty("hibernate.cache.use_query_cache", "false");
        properties.setProperty("persistenceUnitName", persistenceUnit);
        factory.setJpaProperties(properties);
        factory.afterPropertiesSet();
        return factory;
    }

    private Map<String, TableDefinition> readTables() throws Exception {
        final TableXmlHandler handler = new TableXmlHandler();
        try (InputStream tablesXml = getClass().getResourceAsStream(TABLES_FILE)) {
            SAXParserFactory.newInstance()
                    .newSAXParser()
                    .parse(new InputSource(tablesXml), handler);
        }
        return handler.getTables();
    }

    /** One section per script; statements on one line each, whitespace collapsed, sorted. */
    private static String render(Map<String, Collection<String>> ddl) {
        final StringBuilder out = new StringBuilder();
        for (final Map.Entry<String, Collection<String>> section : ddl.entrySet()) {
            out.append("-- ").append(section.getKey()).append('\n');
            final TreeSet<String> statements = new TreeSet<>();
            for (final String sql : section.getValue()) {
                statements.add(sql.trim().replaceAll("\\s+", " "));
            }
            for (final String sql : statements) {
                out.append(sql).append('\n');
            }
        }
        return out.toString();
    }

    private void assertExpected(String actual) throws IOException {
        final Path actualFile = Paths.get("build", "ddl", database + ".sql");
        Files.createDirectories(actualFile.getParent());
        Files.write(actualFile, actual.getBytes(UTF_8));

        final String resource = "ddl/" + database + ".sql";
        final String expected;
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                fail("No expected DDL " + resource + "; the generated DDL is in " + actualFile);
                return;
            }
            expected = IOUtils.toString(in, UTF_8);
        }
        if (expected.equals(actual)) {
            return;
        }

        final List<String> expectedLines = Arrays.asList(expected.split("\n"));
        final List<String> actualLines = Arrays.asList(actual.split("\n"));
        final StringBuilder message =
                new StringBuilder("DDL for ")
                        .append(database)
                        .append(" changed; the generated DDL is in ")
                        .append(actualFile.toAbsolutePath())
                        .append('\n');
        for (final String line : expectedLines) {
            if (!actualLines.contains(line)) {
                message.append("- ").append(line).append('\n');
            }
        }
        for (final String line : actualLines) {
            if (!expectedLines.contains(line)) {
                message.append("+ ").append(line).append('\n');
            }
        }
        fail(message.toString());
    }

    /**
     * Reports this test's database product and version in the JDBC metadata of every connection.
     */
    private DataSource reportingVendor(DataSource target) {
        return proxy(
                DataSource.class,
                target,
                (method, result) ->
                        method.getName().equals("getConnection")
                                ? proxy(
                                        Connection.class,
                                        result,
                                        (connectionMethod, connectionResult) ->
                                                connectionMethod.getName().equals("getMetaData")
                                                        ? proxy(
                                                                DatabaseMetaData.class,
                                                                connectionResult,
                                                                this::vendorMetaData)
                                                        : connectionResult)
                                : result);
    }

    private Object vendorMetaData(Method method, Object result) {
        switch (method.getName()) {
            case "getDatabaseProductName":
                return productName;
            case "getDatabaseProductVersion":
                return productVersion;
            case "getDatabaseMajorVersion":
                return majorVersion;
            case "getDatabaseMinorVersion":
                return minorVersion;
            default:
                return result;
        }
    }

    private interface ResultFilter {
        Object filter(Method method, Object result) throws SQLException;
    }

    private static <T> T proxy(Class<T> type, Object target, ResultFilter filter) {
        final InvocationHandler handler =
                (proxy, method, args) -> {
                    try {
                        return filter.filter(method, method.invoke(target, args));
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                };
        return type.cast(
                Proxy.newProxyInstance(
                        PersistenceUnitDdlTest.class.getClassLoader(),
                        new Class<?>[] {type},
                        handler));
    }
}
