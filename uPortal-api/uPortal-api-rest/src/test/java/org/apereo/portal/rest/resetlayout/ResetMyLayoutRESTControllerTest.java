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
package org.apereo.portal.rest.resetlayout;

import static org.junit.Assert.assertEquals;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.layout.IUserLayoutHelper;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.portal.security.csrf.PortalCsrfSecurityRequestMatcher;
import org.apereo.services.persondir.IPersonAttributes;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The reset-my-layout API over HTTP, with the portal's CSRF protection in front of it (the same
 * CsrfFilter and request matcher that securityContext.xml configures).
 */
public class ResetMyLayoutRESTControllerTest {

    private static final String URI = "/api/v6-0/layout/reset";

    private IPersonManager personManager;
    private IUserLayoutHelper userLayoutHelper;
    private IPerson person;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private CsrfToken csrfToken;

    @Before
    public void setUp() {
        personManager = mock(IPersonManager.class);
        userLayoutHelper = mock(IUserLayoutHelper.class);
        person = mock(IPerson.class);
        when(person.getUserName()).thenReturn("student");
        final Map<String, List<Object>> attributes =
                Collections.singletonMap(IPerson.USERNAME, Collections.singletonList("student"));
        when(person.getAttributeMap()).thenReturn(attributes);
        when(personManager.getPerson(any(HttpServletRequest.class))).thenReturn(person);

        final HttpSessionCsrfTokenRepository tokenRepository = new HttpSessionCsrfTokenRepository();
        final PortalCsrfSecurityRequestMatcher csrfMatcher = new PortalCsrfSecurityRequestMatcher();
        csrfMatcher.init();
        final CsrfFilter csrfFilter = new CsrfFilter(tokenRepository);
        csrfFilter.setRequireCsrfProtectionMatcher(csrfMatcher);
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ResetMyLayoutRESTController(personManager, userLayoutHelper))
                        .addFilters(csrfFilter)
                        .build();

        // The token the portal put on the page, in the user's session
        session = new MockHttpSession();
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
    public void namesTheUserWhoseLayoutWouldBeReset() throws Exception {
        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"userName\":\"student\"}", true));

        verify(userLayoutHelper, never()).resetUserLayout(any());
    }

    @Test
    public void resetsTheSignedInUsersLayout() throws Exception {
        mockMvc.perform(withCsrf(post("/uPortal" + URI)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"userName\":\"student\"}", true));

        final ArgumentCaptor<IPersonAttributes> reset =
                ArgumentCaptor.forClass(IPersonAttributes.class);
        verify(userLayoutHelper).resetUserLayout(reset.capture());
        assertEquals("student", reset.getValue().getName());
        assertEquals("student", reset.getValue().getAttributeValue(IPerson.USERNAME));
    }

    @Test
    public void ignoresAUserNamedInTheRequest() throws Exception {
        mockMvc.perform(
                        withCsrf(post("/uPortal" + URI))
                                .param("userName", "admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userName\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"userName\":\"student\"}", true));

        final ArgumentCaptor<IPersonAttributes> reset =
                ArgumentCaptor.forClass(IPersonAttributes.class);
        verify(userLayoutHelper).resetUserLayout(reset.capture());
        assertEquals("student", reset.getValue().getName());
    }

    @Test
    public void refusesResettingWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(api(post("/uPortal" + URI))).andExpect(status().isForbidden());
        mockMvc.perform(api(post("/uPortal" + URI)).header(csrfToken.getHeaderName(), "forged"))
                .andExpect(status().isForbidden());

        verify(userLayoutHelper, never()).resetUserLayout(any());
    }

    @Test
    public void acceptsABearerTokenWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(
                        api(post("/uPortal" + URI))
                                .header("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.e30.sig"))
                .andExpect(status().isOk());

        verify(userLayoutHelper).resetUserLayout(any());
    }

    @Test
    public void refusesGuests() throws Exception {
        when(person.isGuest()).thenReturn(true);

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":401,\"message\":\"Sign in to reset your layout\"}",
                                        true));
        mockMvc.perform(withCsrf(post("/uPortal" + URI))).andExpect(status().isUnauthorized());

        verify(userLayoutHelper, never()).resetUserLayout(any());
    }

    @Test
    public void refusesARequestWithoutAPerson() throws Exception {
        when(personManager.getPerson(any(HttpServletRequest.class))).thenReturn(null);

        mockMvc.perform(api(get("/uPortal" + URI))).andExpect(status().isUnauthorized());
        mockMvc.perform(withCsrf(post("/uPortal" + URI))).andExpect(status().isUnauthorized());

        verify(userLayoutHelper, never()).resetUserLayout(any());
    }
}
