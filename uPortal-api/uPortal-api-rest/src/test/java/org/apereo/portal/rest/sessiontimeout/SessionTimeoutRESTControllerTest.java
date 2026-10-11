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
package org.apereo.portal.rest.sessiontimeout;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.portal.security.csrf.PortalCsrfSecurityRequestMatcher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The session API over HTTP, with the portal's CSRF protection in front of it (the same CsrfFilter
 * and request matcher that securityContext.xml configures).
 */
public class SessionTimeoutRESTControllerTest {

    private static final String SESSION = "/api/v6-0/session";
    private static final String KEEP_ALIVE = SESSION + "/keep-alive";

    private IPersonManager personManager;
    private IPerson person;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private CsrfToken csrfToken;

    @Before
    public void setUp() {
        personManager = mock(IPersonManager.class);
        person = mock(IPerson.class);
        when(person.isGuest()).thenReturn(false);
        when(personManager.getPerson(any(HttpServletRequest.class))).thenReturn(person);

        final HttpSessionCsrfTokenRepository tokenRepository = new HttpSessionCsrfTokenRepository();
        final PortalCsrfSecurityRequestMatcher csrfMatcher = new PortalCsrfSecurityRequestMatcher();
        csrfMatcher.init();
        final CsrfFilter csrfFilter = new CsrfFilter(tokenRepository);
        csrfFilter.setRequireCsrfProtectionMatcher(csrfMatcher);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new SessionTimeoutRESTController(personManager))
                        .addFilters(csrfFilter)
                        .build();

        // The user's session, with the token the portal put on the page
        session = new MockHttpSession();
        session.setMaxInactiveInterval(1800);
        final MockHttpServletRequest pageRequest = new MockHttpServletRequest();
        pageRequest.setSession(session);
        csrfToken = tokenRepository.generateToken(pageRequest);
        tokenRepository.saveToken(csrfToken, pageRequest, new MockHttpServletResponse());
    }

    /** A request as the browser sends it to /uPortal/api/... with the session cookie. */
    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath("/uPortal").servletPath("/api").session(session);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return api(builder).header(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @Test
    public void answersTheSessionTimeout() throws Exception {
        mockMvc.perform(api(get("/uPortal" + SESSION)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"maxInactiveIntervalSeconds\":1800}", true));
    }

    @Test
    public void answersASessionThatNeverExpires() throws Exception {
        session.setMaxInactiveInterval(-1);

        mockMvc.perform(api(get("/uPortal" + SESSION)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"maxInactiveIntervalSeconds\":-1}", true));
    }

    @Test
    public void keepAliveAnswersTheTimeoutOfTheExtendedSession() throws Exception {
        session.setMaxInactiveInterval(900);

        mockMvc.perform(withCsrf(post("/uPortal" + KEEP_ALIVE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"maxInactiveIntervalSeconds\":900}", true));
    }

    @Test
    public void keepAliveNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(api(post("/uPortal" + KEEP_ALIVE))).andExpect(status().isForbidden());
        mockMvc.perform(
                        api(post("/uPortal" + KEEP_ALIVE))
                                .header(csrfToken.getHeaderName(), "not-the-token"))
                .andExpect(status().isForbidden());

        verify(personManager, never()).getPerson(any(HttpServletRequest.class));
    }

    @Test
    public void refusesGuests() throws Exception {
        when(person.isGuest()).thenReturn(true);

        mockMvc.perform(api(get("/uPortal" + SESSION)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":401,"
                                                + "\"message\":\"Sign in to have a session timeout\"}",
                                        true));
        mockMvc.perform(withCsrf(post("/uPortal" + KEEP_ALIVE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void refusesARequestWithoutASessionAndDoesNotCreateOne() throws Exception {
        // The controller alone: the CsrfFilter of this test saves its token in a new session,
        // while the portal answers a GET without a session cookie without creating one
        // (uPortal-start's tests/api/session-timeout.spec.ts checks both kinds of guest)
        final MockMvc controllerOnly =
                MockMvcBuilders.standaloneSetup(new SessionTimeoutRESTController(personManager))
                        .build();

        final MvcResult result =
                controllerOnly
                        .perform(
                                get("/uPortal" + SESSION)
                                        .contextPath("/uPortal")
                                        .servletPath("/api"))
                        .andExpect(status().isUnauthorized())
                        .andReturn();

        assertNull(result.getRequest().getSession(false));
        verify(personManager, never()).getPerson(any(HttpServletRequest.class));
    }
}
