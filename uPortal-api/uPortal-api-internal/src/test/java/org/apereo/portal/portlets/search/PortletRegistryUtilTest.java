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
package org.apereo.portal.portlets.search;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.junit.After;
import org.junit.Test;

/** Search terms match whatever the server's default locale (MOD-I277) */
public class PortletRegistryUtilTest {
    private final Locale defaultLocale = Locale.getDefault();

    @After
    public void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    private static IPortletDefinition portlet(String title) {
        final IPortletDefinition portlet = mock(IPortletDefinition.class);
        when(portlet.getTitle()).thenReturn(title);
        when(portlet.getName()).thenReturn(title);
        when(portlet.getFName()).thenReturn("fname");
        return portlet;
    }

    @Test
    public void matchesAccentedTerms() {
        assertTrue(PortletRegistryUtil.matches("école", portlet("ÉCOLE de musique")));
    }

    /** In Turkish, "INFO".toLowerCase() is "ınfo", with a dotless ı */
    @Test
    public void matchesUnderATurkishDefaultLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        assertTrue(PortletRegistryUtil.matches("info", portlet("INFO")));
    }
}
