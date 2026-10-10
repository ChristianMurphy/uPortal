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
package org.apereo.portal.portlet.session;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import javax.portlet.PortletContext;
import javax.portlet.PortletSession;
import javax.servlet.http.HttpSession;
import org.apache.pluto.container.PortletWindow;
import org.apereo.portal.portlet.om.IPortletEntityId;
import org.apereo.portal.spring.web.portlet.DispatcherPortlet;
import org.junit.Before;
import org.junit.Test;

/**
 * DispatcherPortlet keeps an action-phase exception in the portlet session for the render phase
 * (SPR-9287); uPortal rethrows it instead. This applies to uPortal's own portlets, which use the
 * vendored DispatcherPortlet, and to portlet applications that still use Spring's.
 */
public class ScopingPortletSessionImplTest {

    private static final String SPRING_ACTION_EXCEPTION_ATTRIBUTE =
            "org.springframework.web.portlet.DispatcherPortlet.ACTION_EXCEPTION";

    private HttpSession httpSession;
    private ScopingPortletSessionImpl portletSession;

    @Before
    public void setUp() {
        IPortletEntityId entityId = mock(IPortletEntityId.class);
        when(entityId.getStringId()).thenReturn("42");
        httpSession = mock(HttpSession.class);
        portletSession =
                new ScopingPortletSessionImpl(
                        entityId,
                        mock(PortletContext.class),
                        mock(PortletWindow.class),
                        httpSession);
    }

    @Test
    public void rethrowsActionExceptionStoredBySpringDispatcherPortlet() {
        IllegalStateException ex = new IllegalStateException("action failed");
        try {
            portletSession.setAttribute(SPRING_ACTION_EXCEPTION_ATTRIBUTE, ex);
            throw new AssertionError("expected the action exception to be rethrown");
        } catch (IllegalStateException e) {
            assertSame(ex, e);
        }
    }

    @Test
    public void rethrowsActionExceptionStoredByVendoredDispatcherPortlet() {
        IllegalStateException ex = new IllegalStateException("action failed");
        try {
            portletSession.setAttribute(DispatcherPortlet.ACTION_EXCEPTION_SESSION_ATTRIBUTE, ex);
            throw new AssertionError("expected the action exception to be rethrown");
        } catch (IllegalStateException e) {
            assertSame(ex, e);
        }
    }

    @Test
    public void wrapsCheckedActionException() {
        IOException ex = new IOException("action failed");
        try {
            portletSession.setAttribute(
                    DispatcherPortlet.ACTION_EXCEPTION_SESSION_ATTRIBUTE,
                    ex,
                    PortletSession.APPLICATION_SCOPE);
            throw new AssertionError("expected the action exception to be rethrown");
        } catch (RuntimeException e) {
            assertSame(ex, e.getCause());
        }
    }

    @Test
    public void storesOtherAttributes() {
        IllegalStateException value = new IllegalStateException("just a value");
        portletSession.setAttribute("someAttribute", value);
        verify(httpSession).setAttribute("javax.portlet.p.42?someAttribute", value);
    }
}
