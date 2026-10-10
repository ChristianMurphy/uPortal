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
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.pluto.container.PortletContainer;
import org.apereo.portal.portlet.container.properties.IRequestPropertiesManager;
import org.apereo.portal.portlet.container.services.IPortletCookieService;
import org.apereo.portal.portlet.container.services.RequestAttributeService;
import org.apereo.portal.portlet.om.IPortletWindow;
import org.apereo.portal.portlet.om.IPortletWindowId;
import org.apereo.portal.url.IPortalRequestInfo;
import org.junit.Before;
import org.junit.Test;

/**
 * Request attributes a portlet sets and removes (PLT.11.1.3). Pluto's PortletRequestImpl removes an
 * attribute by setting it to null; the name used to stay in getAttributeNames() (found by Pluto's
 * test suite, SimpleAttributeTest.checkEnumerateAttributes).
 */
public class PortletRequestContextImplTest {

    private PortletRequestContextImpl context;

    @Before
    public void setUp() {
        final IPortletWindow portletWindow = mock(IPortletWindow.class);
        when(portletWindow.getPortletWindowId()).thenReturn(mock(IPortletWindowId.class));
        final IPortalRequestInfo portalRequestInfo = mock(IPortalRequestInfo.class);
        when(portalRequestInfo.getPortletRequestInfoMap()).thenReturn(Collections.emptyMap());
        context =
                new PortletRequestContextImpl(
                        mock(PortletContainer.class),
                        portletWindow,
                        mock(HttpServletRequest.class),
                        mock(HttpServletResponse.class),
                        mock(IRequestPropertiesManager.class),
                        portalRequestInfo,
                        mock(IPortletCookieService.class),
                        mock(RequestAttributeService.class));
    }

    @Test
    public void setAttributesAreEnumerated() {
        context.setAttribute("a", "1");
        context.setAttribute("b", "2");
        assertEquals(List.of("a", "b"), Collections.list(context.getAttributeNames()));
        assertEquals("1", context.getAttribute("a"));
    }

    @Test
    public void removedAttributeIsNotEnumerated() {
        context.setAttribute("a", "1");
        context.setAttribute("b", "2");
        // PortletRequestImpl.removeAttribute("a")
        context.setAttribute("a", null);
        assertEquals(List.of("b"), Collections.list(context.getAttributeNames()));
        assertNull(context.getAttribute("a"));
    }

    @Test
    public void removingAnUnsetAttributeChangesNothing() {
        context.setAttribute("a", null);
        assertEquals(List.of(), Collections.list(context.getAttributeNames()));
    }
}
