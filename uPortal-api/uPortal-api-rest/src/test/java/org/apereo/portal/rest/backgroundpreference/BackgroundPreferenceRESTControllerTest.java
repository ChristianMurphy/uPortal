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
package org.apereo.portal.rest.backgroundpreference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.security.csrf.PortalCsrfSecurityRequestMatcher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
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
 * The background preference API over HTTP, with the portal's CSRF protection in front of it (the
 * same CsrfFilter and request matcher that securityContext.xml configures).
 */
public class BackgroundPreferenceRESTControllerTest {

    private static final String URI = "/api/v6-0/background-preference/background-preference";
    private static final String GALAXY = "/uPortal/media/skins/common/backgroundImages/galaxy.jpg";
    private static final BackgroundPreference CHOSEN =
            new BackgroundPreference(
                    "body.portal #portalPageBody::before",
                    Collections.singletonList(
                            new BackgroundImage(
                                    GALAXY, "/uPortal/thumbnails/galaxy.jpg", "Galaxy")),
                    GALAXY);

    private BackgroundPreferenceService service;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private CsrfToken csrfToken;

    @Before
    public void setUp() {
        service = mock(BackgroundPreferenceService.class);
        final HttpSessionCsrfTokenRepository tokenRepository = new HttpSessionCsrfTokenRepository();
        final PortalCsrfSecurityRequestMatcher csrfMatcher = new PortalCsrfSecurityRequestMatcher();
        csrfMatcher.init();
        final CsrfFilter csrfFilter = new CsrfFilter(tokenRepository);
        csrfFilter.setRequireCsrfProtectionMatcher(csrfMatcher);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new BackgroundPreferenceRESTController(service))
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
    public void answersTheBackgrounds() throws Exception {
        when(service.getPreference(any(HttpServletRequest.class), eq("background-preference")))
                .thenReturn(CHOSEN);

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        content()
                                .json(
                                        "{\"containerSelector\":\"body.portal #portalPageBody::before\","
                                                + "\"images\":[{\"url\":\""
                                                + GALAXY
                                                + "\",\"thumbnailUrl\":\"/uPortal/thumbnails/galaxy.jpg\","
                                                + "\"caption\":\"Galaxy\"}],"
                                                + "\"selected\":\""
                                                + GALAXY
                                                + "\"}",
                                        true));
    }

    @Test
    public void savesAChoiceWithTheCsrfToken() throws Exception {
        when(service.setPreference(
                        any(HttpServletRequest.class), eq("background-preference"), eq(GALAXY)))
                .thenReturn(CHOSEN);

        mockMvc.perform(
                        withCsrf(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"selected\":\"" + GALAXY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"selected\":\"" + GALAXY + "\"}"));
    }

    @Test
    public void clearsTheChoiceWithNull() throws Exception {
        when(service.setPreference(any(HttpServletRequest.class), any(), isNull()))
                .thenReturn(
                        new BackgroundPreference(
                                "body", Collections.<BackgroundImage>emptyList(), null));

        mockMvc.perform(
                        withCsrf(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"selected\":null}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"selected\":null}"));
    }

    @Test
    public void refusesSavingWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(
                        api(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"selected\":\"" + GALAXY + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        api(put("/uPortal" + URI))
                                .header(csrfToken.getHeaderName(), "forged")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"selected\":\"" + GALAXY + "\"}"))
                .andExpect(status().isForbidden());

        verify(service, never()).setPreference(any(), any(), any());
    }

    @Test
    public void answersAnImageThatIsNotOfferedWith400() throws Exception {
        when(service.setPreference(any(HttpServletRequest.class), any(), eq("/elsewhere.jpg")))
                .thenThrow(
                        new BackgroundPreferenceException(
                                HttpStatus.BAD_REQUEST, "Not one of the offered backgrounds"));

        mockMvc.perform(
                        withCsrf(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"selected\":\"/elsewhere.jpg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":400,"
                                                + "\"message\":\"Not one of the offered backgrounds\"}",
                                        true));
    }

    @Test
    public void answersGuestsWith401() throws Exception {
        when(service.getPreference(any(HttpServletRequest.class), any()))
                .thenThrow(
                        new BackgroundPreferenceException(
                                HttpStatus.UNAUTHORIZED, "Sign in to choose a background"));

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":401,"
                                                + "\"message\":\"Sign in to choose a background\"}",
                                        true));
    }
}
