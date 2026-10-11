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

import java.util.Properties;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JvmZoneLocalTimeTypeTest {
    /** Reads the parameters the type passes on to Jadira */
    private static final class ExposedType extends JvmZoneLocalTimeType {
        private static final long serialVersionUID = 1L;

        Properties parameters() {
            return getParameterValues();
        }
    }

    private TimeZone jvmZone;

    @Before
    public void setUp() {
        jvmZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(jvmZone);
    }

    @Test
    public void theDatabaseZoneIsTheJvmZone() {
        final ExposedType type = new ExposedType();
        type.setParameterValues(null);
        assertEquals("America/New_York", type.parameters().getProperty("databaseZone"));
    }

    @Test
    public void aGivenDatabaseZoneIsKept() {
        final Properties parameters = new Properties();
        parameters.setProperty("databaseZone", "UTC");
        final ExposedType type = new ExposedType();
        type.setParameterValues(parameters);
        assertEquals("UTC", type.parameters().getProperty("databaseZone"));
    }
}
