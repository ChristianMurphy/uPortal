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
package org.apereo.portal.security.mvc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.portal.security.ISecurityContext;
import org.apereo.portal.url.CasLoginRefUrlEncoder;
import org.apereo.portal.url.IPortalUrlBuilder;
import org.apereo.portal.url.IPortalUrlProvider;
import org.apereo.portal.url.LocalLoginRefUrlEncoder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** Where /Login sends the user after a local login attempt (MOD-I180, MOD-I181). */
public class LoginControllerTest {

    private static final String DEFAULT_PAGE = "/uPortal/f/welcome/normal/render.uP";
    private static final String DEEP_LINK = "/uPortal/p/user-administration";

    private LoginController controller;
    private ISecurityContext securityContext;
    private LocalLoginRefUrlEncoder localEncoder;

    @Before
    public void setUp() {
        final IPortalUrlBuilder defaultUrl = mock(IPortalUrlBuilder.class);
        when(defaultUrl.getUrlString()).thenReturn(DEFAULT_PAGE);
        final IPortalUrlProvider urlProvider = mock(IPortalUrlProvider.class);
        when(urlProvider.getDefaultUrl(any())).thenReturn(defaultUrl);

        securityContext = mock(ISecurityContext.class);
        final IPerson person = mock(IPerson.class);
        when(person.getSecurityContext()).thenReturn(securityContext);
        final IPersonManager personManager = mock(IPersonManager.class);
        when(personManager.getPerson(any())).thenReturn(person);

        localEncoder = new LocalLoginRefUrlEncoder();
        localEncoder.setLoginUrl("/uPortal/p/login");

        controller = new LoginController();
        controller.setPortalUrlProvider(urlProvider);
        controller.setPersonManager(personManager);
        controller.setLoginRefUrlEncoder(localEncoder);
    }

    private static MockHttpServletRequest login(String userName, String refUrl) {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/uPortal/Login");
        request.setContextPath("/uPortal");
        request.setServletPath("/Login");
        request.setSession(new MockHttpSession());
        if (userName != null) {
            request.addParameter("userName", userName);
            request.addParameter("password", "wrong");
        }
        if (refUrl != null) {
            request.addParameter(LoginController.REFERER_URL_PARAM, refUrl);
        }
        return request;
    }

    private String redirectFor(MockHttpServletRequest request) throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        controller.service(request, response);
        return response.getRedirectedUrl();
    }

    @Test
    public void aFailedLocalLoginReturnsToTheLoginFormWithItsRefUrl() throws Exception {
        final MockHttpServletRequest request = login("student", DEEP_LINK);
        assertEquals(
                "/uPortal/p/login?refUrl=%2FuPortal%2Fp%2Fuser-administration",
                redirectFor(request));
        assertEquals(
                "student",
                request.getSession().getAttribute(LoginController.ATTEMPTED_USERNAME_KEY));
    }

    @Test
    public void aFailedLocalLoginWithoutARefUrlReturnsToTheLoginForm() throws Exception {
        assertEquals("/uPortal/p/login", redirectFor(login("student", null)));
    }

    @Test
    public void aFailedLocalLoginDropsARefUrlOnAnotherServer() throws Exception {
        assertEquals(
                "/uPortal/p/login", redirectFor(login("student", "https://elsewhere.example/")));
    }

    @Test
    public void anAuthenticationErrorReturnsToTheLoginForm() throws Exception {
        final MockHttpServletRequest request = login("student", null);
        request.getSession().setAttribute(LoginController.AUTH_ERROR_KEY, true);
        assertEquals("/uPortal/p/login", redirectFor(request));
    }

    @Test
    public void aSuccessfulLoginOpensTheRequestedPage() throws Exception {
        when(securityContext.isAuthenticated()).thenReturn(true);
        assertEquals("http://localhost" + DEEP_LINK, redirectFor(login("student", DEEP_LINK)));
        assertEquals(DEFAULT_PAGE, redirectFor(login("student", null)));
    }

    @Test
    public void aRequestWithoutCredentialsKeepsTheDefaultRedirect() throws Exception {
        assertEquals(DEFAULT_PAGE, redirectFor(login(null, null)));
        assertEquals("http://localhost" + DEEP_LINK, redirectFor(login(null, DEEP_LINK)));
    }

    @Test
    public void withTheCasEncoderAFailedLoginGoesToTheDefaultPage() throws Exception {
        controller.setLoginRefUrlEncoder(new CasLoginRefUrlEncoder());
        assertEquals(DEFAULT_PAGE, redirectFor(login("student", null)));
        controller.setLoginRefUrlEncoder(null);
        assertEquals(DEFAULT_PAGE, redirectFor(login("student", null)));
    }
}
