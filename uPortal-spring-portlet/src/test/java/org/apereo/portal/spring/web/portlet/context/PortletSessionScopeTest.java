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
package org.apereo.portal.spring.web.portlet.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.portlet.PortletContext;
import javax.portlet.PortletRequest;
import javax.portlet.PortletSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The portlet "session" and "globalSession" scopes store beans in the portlet session's portlet
 * scope and application scope respectively.
 */
public class PortletSessionScopeTest {

    private PortletSession session;

    @Before
    public void bindRequest() {
        final PortletRequest request = mock(PortletRequest.class);
        session = mock(PortletSession.class);
        when(request.getPortletSession()).thenReturn(session);
        when(request.getPortletSession(false)).thenReturn(session);
        when(request.getPortletSession(true)).thenReturn(session);
        RequestContextHolder.setRequestAttributes(new PortletRequestAttributes(request));
    }

    @After
    public void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static Scope registeredScope(String name) {
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        PortletApplicationContextUtils.registerPortletApplicationScopes(
                beanFactory, mock(PortletContext.class));
        return beanFactory.getRegisteredScope(name);
    }

    @Test
    public void scopeConstants() {
        assertEquals("globalSession", ConfigurablePortletApplicationContext.SCOPE_GLOBAL_SESSION);
        assertEquals(2, PortletRequestAttributes.SCOPE_GLOBAL_SESSION);
    }

    @Test
    public void sessionScopeUsesThePortletScope() {
        final Object bean = new Object();
        final ObjectFactory<Object> factory = () -> bean;

        assertSame(bean, registeredScope(WebApplicationContext.SCOPE_SESSION).get("bean", factory));

        // Two-argument form: the portlet scope
        verify(session).setAttribute("bean", bean);
    }

    @Test
    public void globalSessionScopeUsesTheApplicationScope() {
        final Object bean = new Object();
        final ObjectFactory<Object> factory = () -> bean;

        assertSame(
                bean,
                registeredScope(ConfigurablePortletApplicationContext.SCOPE_GLOBAL_SESSION)
                        .get("bean", factory));

        verify(session).setAttribute("bean", bean, PortletSession.APPLICATION_SCOPE);
    }

    @Test
    public void defaultConstructorIsTheSessionScope() {
        final Object bean = new Object();
        assertSame(bean, new PortletSessionScope().get("bean", () -> bean));
        // Two-argument form: the portlet scope
        verify(session).setAttribute("bean", bean);
    }
}
