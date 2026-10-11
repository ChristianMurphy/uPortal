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
package org.apereo.portal.io.xml.crn;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apereo.portal.layout.dao.IStylesheetDescriptorDao;
import org.apereo.portal.layout.dao.IStylesheetUserPreferencesDao;
import org.apereo.portal.layout.om.IStylesheetDescriptor;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.apereo.portal.utils.Tuple;
import org.danann.cernunnos.runtime.PojoTask;
import org.dom4j.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * dataExport of a layout profile through export-profile.crn (MOD-I200). The script reads the
 * profile's ids from UP_USER and UP_USER_PROFILE and passes them to the stylesheet DAOs, which take
 * {@code long} and {@code int}. Oracle returns its NUMBER columns as BigDecimal, which Groovy does
 * not pass as either, so every profile failed to export on Oracle. The two legacy tables are made
 * here with DECIMAL ids, which every database returns as BigDecimal, so each database of the DB
 * integration run shows what Oracle does. Their names are in lower case, as the script writes them:
 * MySQL and MariaDB on Linux compare table names case-sensitively.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaPortalTestApplicationContext.xml")
public class ProfileExportTest extends BasePortalJpaDaoTest {

    @Autowired private IStylesheetDescriptorDao stylesheetDescriptorDao;
    @Autowired private IStylesheetUserPreferencesDao stylesheetUserPreferencesDao;

    @Autowired
    @Qualifier("PortalDb")
    private DataSource dataSource;

    @Autowired private PlatformTransactionManager transactionManager;

    private JdbcTemplate jdbc;

    @Before
    public void createProfile() {
        jdbc = new JdbcTemplate(dataSource);
        dropTables();
        jdbc.execute(
                "CREATE TABLE up_user (USER_ID DECIMAL(19,0) NOT NULL PRIMARY KEY,"
                        + " USER_NAME VARCHAR(100) NOT NULL)");
        jdbc.execute(
                "CREATE TABLE up_user_profile (USER_ID DECIMAL(19,0) NOT NULL,"
                        + " PROFILE_ID DECIMAL(19,0) NOT NULL, PROFILE_FNAME VARCHAR(100) NOT NULL,"
                        + " PROFILE_NAME VARCHAR(100), DESCRIPTION VARCHAR(255),"
                        + " LAYOUT_ID DECIMAL(19,0), STRUCTURE_SS_ID DECIMAL(19,0),"
                        + " THEME_SS_ID DECIMAL(19,0), PRIMARY KEY (USER_ID, PROFILE_ID))");
        long[] ids =
                this.execute(
                        () -> {
                            IStylesheetDescriptor structure =
                                    stylesheetDescriptorDao.createStylesheetDescriptor(
                                            "DLMTabsColumns",
                                            "classpath:/layout/structure/columns.xsl");
                            IStylesheetDescriptor theme =
                                    stylesheetDescriptorDao.createStylesheetDescriptor(
                                            "Respondr", "classpath:/layout/theme/respondr.xsl");
                            return new long[] {structure.getId(), theme.getId()};
                        });
        jdbc.update(
                "INSERT INTO up_user (USER_ID, USER_NAME) VALUES (?, ?)",
                new BigDecimal(42),
                "fixture-zoe");
        jdbc.update(
                "INSERT INTO up_user_profile (USER_ID, PROFILE_ID, PROFILE_FNAME, PROFILE_NAME,"
                        + " DESCRIPTION, LAYOUT_ID, STRUCTURE_SS_ID, THEME_SS_ID)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                new BigDecimal(42),
                new BigDecimal(1),
                "default",
                "Default Profile",
                "The profile Zoë uses",
                new BigDecimal(1),
                new BigDecimal(ids[0]),
                new BigDecimal(ids[1]));
    }

    @After
    public void dropTables() {
        for (String table : new String[] {"up_user_profile", "up_user"}) {
            try {
                jdbc.execute("DROP TABLE " + table);
            } catch (RuntimeException e) {
                // not there
            }
        }
    }

    private CernunnosDataExporter exporter() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("SqlAttributes.DATA_SOURCE", dataSource);
        attributes.put("SqlAttributes.TRANSACTION_MANAGER", transactionManager);
        attributes.put("stylesheetDescriptorDao", stylesheetDescriptorDao);
        attributes.put("stylesheetUserPreferencesDao", stylesheetUserPreferencesDao);
        PojoTask task = new PojoTask();
        task.setLocation("classpath:/org/apereo/portal/io/export-profile.crn");
        task.setRequestAttributes(attributes);
        task.afterPropertiesSet();
        CernunnosDataExporter exporter = new CernunnosDataExporter();
        exporter.setIdAttributeName("PROFILE_KEY");
        exporter.setTask(task);
        return exporter;
    }

    @Test
    public void exportsAProfileWhoseIdsTheDatabaseReturnsAsBigDecimal() {
        Tuple<String, Element> exported = exporter().exportData("fixture-zoe|default");
        Element profile = exported.second;
        assertEquals("profile", profile.getName());
        assertEquals("fixture-zoe", profile.attributeValue("username"));
        assertEquals("Default Profile", profile.elementText("name"));
        assertEquals("default", profile.elementText("fname"));
        assertEquals("The profile Zoë uses", profile.elementText("description"));
        assertEquals("DLMTabsColumns", profile.element("structure").attributeValue("name"));
        assertEquals("Respondr", profile.element("theme").attributeValue("name"));
    }
}
