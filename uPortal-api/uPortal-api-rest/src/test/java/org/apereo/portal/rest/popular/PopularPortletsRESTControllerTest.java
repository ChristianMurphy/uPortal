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
package org.apereo.portal.rest.popular;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Locale;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class PopularPortletsRESTControllerTest {

    private static final String URI = "/api/v6-0/popular-portlets";

    private PopularPortletsService service;
    private IPerson person;
    private IPersonManager personManager;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private CsrfToken csrfToken;

    @Before
    public void setUp() {
        service = mock(PopularPortletsService.class);
        person = mock(IPerson.class);
        personManager = mock(IPersonManager.class);
        when(personManager.getPerson(any(HttpServletRequest.class))).thenReturn(person);

        final HttpSessionCsrfTokenRepository tokenRepository = new HttpSessionCsrfTokenRepository();
        final PortalCsrfSecurityRequestMatcher csrfMatcher = new PortalCsrfSecurityRequestMatcher();
        csrfMatcher.init();
        final CsrfFilter csrfFilter = new CsrfFilter(tokenRepository);
        csrfFilter.setRequireCsrfProtectionMatcher(csrfMatcher);
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new PopularPortletsRESTController(service, personManager))
                        .addFilters(csrfFilter)
                        .build();

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

    @Test
    public void listsThePopularPortlets() throws Exception {
        when(service.getPopularPortlets(person, 7, Locale.forLanguageTag("fr-CA"), "/uPortal"))
                .thenReturn(
                        new PopularPortlets(
                                7,
                                Arrays.asList(
                                        new PopularPortlet(
                                                "calendar",
                                                "Calendrier",
                                                "Vos événements",
                                                "/uPortal/p/calendar",
                                                5),
                                        new PopularPortlet(
                                                "xkcd", "XKCD", null, "/uPortal/p/xkcd", 1))));

        mockMvc.perform(api(get("/uPortal" + URI).param("days", "7").param("locale", "fr-CA")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        "{\"days\":7,\"portlets\":["
                                                + "{\"fname\":\"calendar\",\"title\":\"Calendrier\","
                                                + "\"description\":\"Vos événements\","
                                                + "\"url\":\"/uPortal/p/calendar\",\"count\":5},"
                                                + "{\"fname\":\"xkcd\",\"title\":\"XKCD\","
                                                + "\"description\":null,"
                                                + "\"url\":\"/uPortal/p/xkcd\",\"count\":1}]}",
                                        true));
    }

    @Test
    public void countsThirtyDaysInTheRequestLocaleByDefault() throws Exception {
        when(service.getPopularPortlets(person, 30, Locale.GERMANY, "/uPortal"))
                .thenReturn(new PopularPortlets(30, Arrays.asList()));

        mockMvc.perform(api(get("/uPortal" + URI)).locale(Locale.GERMANY))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"days\":30,\"portlets\":[]}", true));
    }

    @Test
    public void ignoresALocaleThatIsNoLanguageTag() throws Exception {
        when(service.getPopularPortlets(person, 30, Locale.GERMANY, "/uPortal"))
                .thenReturn(new PopularPortlets(30, Arrays.asList()));

        mockMvc.perform(api(get("/uPortal" + URI).param("locale", "%%")).locale(Locale.GERMANY))
                .andExpect(status().isOk());
        verify(service).getPopularPortlets(person, 30, Locale.GERMANY, "/uPortal");
    }

    @Test
    public void refusesDaysOutsideTheRange() throws Exception {
        for (final String days : new String[] {"0", "-1", "366", "thirty", ""}) {
            mockMvc.perform(api(get("/uPortal" + URI).param("days", days)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(content().json("{\"status\":400}"));
        }
        verify(service, never())
                .getPopularPortlets(any(IPerson.class), anyInt(), any(Locale.class), anyString());
    }

    @Test
    public void acceptsTheLimits() throws Exception {
        when(service.getPopularPortlets(eq(person), anyInt(), any(Locale.class), eq("/uPortal")))
                .thenReturn(new PopularPortlets(1, Arrays.asList()));

        mockMvc.perform(api(get("/uPortal" + URI).param("days", "1"))).andExpect(status().isOk());
        mockMvc.perform(api(get("/uPortal" + URI).param("days", " 365 ")))
                .andExpect(status().isOk());
        verify(service).getPopularPortlets(eq(person), eq(1), any(Locale.class), eq("/uPortal"));
        verify(service).getPopularPortlets(eq(person), eq(365), any(Locale.class), eq("/uPortal"));
    }

    @Test
    public void answers401WithoutAPortalSession() throws Exception {
        when(personManager.getPerson(any(HttpServletRequest.class))).thenReturn(null);

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"status\":401}"));
    }

    @Test
    public void needsNoCsrfTokenToRead() throws Exception {
        when(service.getPopularPortlets(eq(person), eq(30), any(Locale.class), eq("/uPortal")))
                .thenReturn(new PopularPortlets(30, Arrays.asList()));

        mockMvc.perform(api(get("/uPortal" + URI))).andExpect(status().isOk());
    }

    @Test
    public void refusesAnUnsafeRequestWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(api(post("/uPortal" + URI))).andExpect(status().isForbidden());
        mockMvc.perform(
                        api(post("/uPortal" + URI))
                                .header(csrfToken.getHeaderName(), csrfToken.getToken()))
                .andExpect(status().isMethodNotAllowed());
    }
}
