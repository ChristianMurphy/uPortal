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
package org.apereo.portal.dao.usertype;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.util.Arrays;
import java.util.Collection;
import java.util.TimeZone;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Pins the literal each java.time column stores, read back through JDBC 4.2 without Hibernate, in
 * UTC and in zones behind and ahead of UTC. The schema is frozen (ADR 0006), so these are the
 * values Jadira stored. A failure after a Hibernate upgrade means the new version binds a type with
 * another calendar; the persistence goldens would fail next.
 */
@RunWith(Parameterized.class)
public class JavaTimeColumnStorageTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 1);
    private static final LocalTime TIME = LocalTime.of(0, 30, 15);
    private static final MonthDay MONTH_DAY = MonthDay.of(2, 29);
    private static final Instant TIMESTAMP = Instant.parse("2026-10-04T15:30:00.250Z");

    private final String zone;
    private TimeZone jvmZone;
    private StandardServiceRegistry registry;
    private SessionFactory sessionFactory;
    private String url;

    public JavaTimeColumnStorageTest(String zone) {
        this.zone = zone;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> zones() {
        return Arrays.asList(
                new Object[] {"UTC"},
                new Object[] {"America/New_York"},
                new Object[] {"Australia/Sydney"});
    }

    @Before
    public void setUp() {
        jvmZone = TimeZone.getDefault();
        // Before the first connection: the driver reads the default zone when it connects
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        url = "jdbc:hsqldb:mem:javaTimeColumns" + zone.replace('/', '_');
        registry =
                new StandardServiceRegistryBuilder()
                        .applySetting("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver")
                        .applySetting("hibernate.connection.url", url)
                        .applySetting("hibernate.connection.username", "sa")
                        .applySetting("hibernate.connection.password", "")
                        .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                        // As uPortal-webapp's hibernate.properties sets it (MOD-I141)
                        .applySetting("hibernate.jdbc.time_zone", "UTC")
                        .build();
        sessionFactory =
                new MetadataSources(registry)
                        .addAnnotatedClass(JavaTimeColumns.class)
                        .buildMetadata()
                        .buildSessionFactory();
    }

    @After
    public void tearDown() {
        try {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
            StandardServiceRegistryBuilder.destroy(registry);
        } finally {
            TimeZone.setDefault(jvmZone);
        }
    }

    @Test
    public void storesTheValuesJadiraStored() throws Exception {
        final JavaTimeColumns written = new JavaTimeColumns();
        written.id = 1;
        written.date = DATE;
        written.time = TIME;
        written.monthDay = MONTH_DAY;
        written.timestamp = TIMESTAMP;
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(written);
            session.getTransaction().commit();
        }

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT A_DATE, A_TIME, A_MONTH_DAY, A_TIMESTAMP"
                                        + " FROM JAVA_TIME_COLUMNS")) {
            rs.next();
            assertEquals(
                    "DATE holds the local date. A day early in a zone ahead of UTC means DATE is"
                            + " bound with the hibernate.jdbc.time_zone calendar: fix"
                            + " LocalDateColumnConverter",
                    DATE,
                    rs.getObject(1, LocalDate.class));
            assertEquals(
                    "TIME holds the local time of day. A shift by the JVM's offset means the"
                            + " converter's JDBC value no longer matches the calendar Hibernate"
                            + " binds TIME with, hibernate.jdbc.time_zone (MOD-I221): fix"
                            + " LocalTimeColumnConverter",
                    TIME,
                    rs.getObject(2, LocalTime.class));
            assertEquals("--02-29", rs.getString(3));
            assertEquals(
                    "TIMESTAMP holds the instant's UTC clock time",
                    LocalDateTime.of(2026, 10, 4, 15, 30, 0, 250_000_000),
                    rs.getObject(4, LocalDateTime.class));
        }

        try (Session session = sessionFactory.openSession()) {
            final JavaTimeColumns read = session.get(JavaTimeColumns.class, 1L);
            assertEquals(DATE, read.date);
            assertEquals(TIME, read.time);
            assertEquals(MONTH_DAY, read.monthDay);
            assertEquals(TIMESTAMP, read.timestamp);
        }
    }
}
