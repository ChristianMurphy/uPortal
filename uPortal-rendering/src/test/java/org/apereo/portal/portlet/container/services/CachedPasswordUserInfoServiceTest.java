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
package org.apereo.portal.portlet.container.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import javax.portlet.PortletRequest;
import javax.servlet.http.HttpServletRequest;
import org.apache.pluto.container.PortletWindow;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.ISecurityContext;
import org.apereo.portal.security.provider.NotSoOpaqueCredentials;
import org.apereo.portal.url.IPortalRequestUtils;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.junit.Before;
import org.junit.Test;

/** Tests the password that {@link CachedPasswordUserInfoService} passes to a portlet. */
public class CachedPasswordUserInfoServiceTest {

    private final PortletRequest portletRequest = mock(PortletRequest.class);
    private final PortletWindow portletWindow = mock(PortletWindow.class, RETURNS_DEEP_STUBS);
    private final IPerson person = mock(IPerson.class);

    /** A service for a portlet that asks for the password user attribute. */
    private final CachedPasswordUserInfoService service =
            new CachedPasswordUserInfoService() {
                @Override
                public boolean isPasswordRequested(
                        PortletRequest request, PortletWindow plutoPortletWindow) {
                    return true;
                }
            };

    @Before
    public void setUp() {
        final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        final IPortalRequestUtils portalRequestUtils = mock(IPortalRequestUtils.class);
        when(portalRequestUtils.getPortletHttpRequest(portletRequest))
                .thenReturn(httpServletRequest);
        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstance.getPerson()).thenReturn(person);
        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);
        when(userInstanceManager.getUserInstance(httpServletRequest)).thenReturn(userInstance);

        service.setPortalRequestUtils(portalRequestUtils);
        service.setUserInstanceManager(userInstanceManager);
    }

    @Test
    public void passesTheCachedPassword() throws Exception {
        final NotSoOpaqueCredentials credentials = mock(NotSoOpaqueCredentials.class);
        when(credentials.getCredentials()).thenReturn("secret");
        final ISecurityContext securityContext = mock(ISecurityContext.class);
        when(securityContext.getOpaqueCredentials()).thenReturn(credentials);
        when(person.getSecurityContext()).thenReturn(securityContext);

        final Map<String, String> userInfo = service.getUserInfo(portletRequest, portletWindow);

        assertEquals(Collections.singletonMap("password", "secret"), userInfo);
    }

    /** A person can have no security context, for example one created from an OIDC ID token. */
    @Test
    public void personWithoutSecurityContextHasNoPassword() throws Exception {
        when(person.getSecurityContext()).thenReturn(null);

        final Map<String, String> userInfo = service.getUserInfo(portletRequest, portletWindow);

        assertTrue(userInfo.isEmpty());
    }
}
