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
package org.apereo.portal.portlet.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ResourceBundle;
import org.junit.Test;

/**
 * uPortal's copy of Pluto's driver configuration (read by Pluto's {@code Configuration} as the
 * bundle {@code org.apache.pluto.driver.container.pluto-configuration}) names the container that
 * portlets see in {@code PortletContext.getServerInfo()}. Its version was the Maven placeholder
 * {@code ${pluto.version}}, which nothing filled in, so every portlet saw "Pluto/${pluto.version}"
 * (found by Pluto's test suite, MiscTest.checkContextServerInfo).
 */
public class PlutoConfigurationTest {

    private final ResourceBundle bundle =
            ResourceBundle.getBundle("org.apache.pluto.driver.container.pluto-configuration");

    @Test
    public void containerVersionIsFilledIn() {
        final String version = bundle.getString("pluto.container.version");
        assertTrue(
                "pluto.container.version is a version, not a placeholder: " + version,
                version.matches("\\d+\\.\\d+(\\.\\d+)?([.-][A-Za-z0-9]+)*"));
    }

    @Test
    public void portletApiVersionIsTwoZero() {
        assertEquals("2", bundle.getString("javax.portlet.version.major"));
        assertEquals("0", bundle.getString("javax.portlet.version.minor"));
    }
}
