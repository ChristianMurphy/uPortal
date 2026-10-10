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
package org.apereo.portal.pluto;

import static org.junit.Assert.assertEquals;

import org.apache.pluto.container.ContainerInfo;
import org.apache.pluto.driver.container.PlutoContainerInfo;
import org.junit.Test;

/**
 * The container info that portlets see through PortletContext.getServerInfo(), getMajorVersion()
 * and getMinorVersion() comes from pluto-configuration.properties, which Pluto's Maven build
 * filtered. The in-tree build has to fill in the same values as Apache's 2.1.0-M3 jar.
 */
public class PlutoContainerInfoTest {

    private final ContainerInfo info = PlutoContainerInfo.getInfo();

    @Test
    public void reportsTheContainerAsApacheJarDid() {
        assertEquals("Pluto", info.getPortletContainerName());
        assertEquals("2.1.0-M3", info.getPortletContainerVersion());
        assertEquals("Pluto/2.1.0-M3", info.getServerInfo());
    }

    @Test
    public void reportsPortlet20() {
        assertEquals(2, info.getMajorSpecificationVersion());
        assertEquals(0, info.getMinorSpecificationVersion());
    }
}
