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

import java.util.Properties;
import java.util.TimeZone;
import org.jadira.usertype.dateandtime.joda.PersistentLocalTime;

/**
 * Jadira's {@code PersistentLocalTime} with the JVM's zone as its database zone. Without a zone,
 * Jadira binds the time through Hibernate's time descriptor, which converts it to {@code
 * hibernate.jdbc.time_zone} (UTC since MOD-I141) and would move every stored time of day by the
 * JVM's offset. With the JVM's zone, Jadira binds it with that zone's calendar, so a time of day is
 * stored as it was before: as the JVM's local clock shows it.
 */
public class JvmZoneLocalTimeType extends PersistentLocalTime {
    private static final long serialVersionUID = 1L;

    @Override
    public void setParameterValues(Properties parameters) {
        final Properties withZone = new Properties();
        if (parameters != null) {
            withZone.putAll(parameters);
        }
        withZone.putIfAbsent("databaseZone", TimeZone.getDefault().getID());
        super.setParameterValues(withZone);
    }
}
