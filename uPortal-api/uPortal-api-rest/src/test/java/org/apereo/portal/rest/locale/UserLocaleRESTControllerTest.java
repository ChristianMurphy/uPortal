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
package org.apereo.portal.rest.locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The locale API over HTTP, with the portal's CSRF protection in front of it (the same CsrfFilter
 * and request matcher that securityContext.xml configures).
 */
public class UserLocaleRESTControllerTest {

    private static final String URI = "/api/v6-0/locale";

    private static final UserLocale FRENCH =
            new UserLocale(
                    "fr_FR",
                    Arrays.asList(
                            new LocaleOption("en_US", "en-US", "anglais", "English"),
                            new LocaleOption("fr_FR", "fr-FR", "français", "Français")));

    private static final String FRENCH_JSON =
            "{\"selected\":\"fr_FR\",\"locales\":["
                    + "{\"code\":\"en_US\",\"tag\":\"en-US\",\"name\":\"anglais\","
                    + "\"nativeName\":\"English\"},"
                    + "{\"code\":\"fr_FR\",\"tag\":\"fr-FR\",\"name\":\"français\","
                    + "\"nativeName\":\"Français\"}]}";

    private UserLocaleService service;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private CsrfToken csrfToken;

    @Before
    public void setUp() {
        service = mock(UserLocaleService.class);
        final HttpSessionCsrfTokenRepository tokenRepository = new HttpSessionCsrfTokenRepository();
        final PortalCsrfSecurityRequestMatcher csrfMatcher = new PortalCsrfSecurityRequestMatcher();
        csrfMatcher.init();
        final CsrfFilter csrfFilter = new CsrfFilter(tokenRepository);
        csrfFilter.setRequireCsrfProtectionMatcher(csrfMatcher);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new UserLocaleRESTController(service))
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

    private MockHttpServletRequestBuilder update(String body) {
        return api(put("/uPortal" + URI))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @Test
    public void returnsTheLocales() throws Exception {
        when(service.getUserLocale(any(HttpServletRequest.class))).thenReturn(FRENCH);

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(FRENCH_JSON, true));
    }

    @Test
    public void returnsNullWhenNothingIsChosen() throws Exception {
        when(service.getUserLocale(any(HttpServletRequest.class)))
                .thenReturn(new UserLocale(null, Arrays.asList()));

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"selected\":null,\"locales\":[]}", true));
    }

    @Test
    public void setsTheLocale() throws Exception {
        when(service.setUserLocale(any(HttpServletRequest.class), eq("fr_FR"))).thenReturn(FRENCH);

        mockMvc.perform(update("{\"code\":\"fr_FR\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json(FRENCH_JSON, true));
    }

    @Test
    public void refusesAnUnknownLocaleWith400() throws Exception {
        when(service.setUserLocale(any(HttpServletRequest.class), eq("xx_YY")))
                .thenThrow(new IllegalArgumentException("Not one of the portal's locales: xx_YY"));

        mockMvc.perform(update("{\"code\":\"xx_YY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":400,\"message\":\"Not one of the portal's"
                                                + " locales: xx_YY\"}",
                                        true));
    }

    @Test
    public void refusesAChangeWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(
                        api(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"fr_FR\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        api(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"fr_FR\"}")
                                .header(csrfToken.getHeaderName(), "forged"))
                .andExpect(status().isForbidden());
        verify(service, never()).setUserLocale(any(HttpServletRequest.class), anyString());
    }
}
