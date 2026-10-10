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
package org.apereo.portal.rest.favorites;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * The favorites API over HTTP, with the portal's CSRF protection in front of it (the same
 * CsrfFilter and request matcher that securityContext.xml configures).
 */
public class FavoritesRESTControllerTest {

    private static final String URI = "/api/v6-0/favorites";

    private static final FavoritePortlet CALENDAR =
            new FavoritePortlet(
                    "u12l1n20",
                    "calendar",
                    "Calendar",
                    "Your events",
                    "/uPortal/p/calendar/render.uP",
                    null,
                    "/icons/calendar.png",
                    true,
                    true);

    private FavoritesService service;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private CsrfToken csrfToken;

    @Before
    public void setUp() {
        service = mock(FavoritesService.class);
        final HttpSessionCsrfTokenRepository tokenRepository = new HttpSessionCsrfTokenRepository();
        final PortalCsrfSecurityRequestMatcher csrfMatcher = new PortalCsrfSecurityRequestMatcher();
        csrfMatcher.init();
        final CsrfFilter csrfFilter = new CsrfFilter(tokenRepository);
        csrfFilter.setRequireCsrfProtectionMatcher(csrfMatcher);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new FavoritesRESTController(service))
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
    public void listsTheFavorites() throws Exception {
        when(service.getFavorites(any(HttpServletRequest.class)))
                .thenReturn(
                        new Favorites(
                                Collections.singletonList(
                                        new FavoriteCollection(
                                                "u12l1s7",
                                                "News",
                                                "/uPortal/f/u12l1s7/render.uP",
                                                false,
                                                true)),
                                Collections.singletonList(CALENDAR),
                                "/uPortal/p/portletmarketplace/max/render.uP"));

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        "{\"collections\":[{\"nodeId\":\"u12l1s7\",\"name\":\"News\","
                                                + "\"url\":\"/uPortal/f/u12l1s7/render.uP\","
                                                + "\"moveAllowed\":false,\"deleteAllowed\":true}],"
                                                + "\"portlets\":[{\"nodeId\":\"u12l1n20\","
                                                + "\"fname\":\"calendar\",\"name\":\"Calendar\","
                                                + "\"description\":\"Your events\","
                                                + "\"url\":\"/uPortal/p/calendar/render.uP\","
                                                + "\"target\":null,"
                                                + "\"iconUrl\":\"/icons/calendar.png\","
                                                + "\"moveAllowed\":true,\"deleteAllowed\":true}],"
                                                + "\"marketplaceUrl\":"
                                                + "\"/uPortal/p/portletmarketplace/max/render.uP\"}",
                                        true));
    }

    @Test
    public void answersRefusalsWithTheirStatusAndMessage() throws Exception {
        when(service.getFavorites(any(HttpServletRequest.class)))
                .thenThrow(
                        new FavoritesException(
                                HttpStatus.UNAUTHORIZED, "Sign in to use favorites"));

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":401,\"message\":\"Sign in to use favorites\"}",
                                        true));
    }

    @Test
    public void addsAFavorite() throws Exception {
        when(service.addFavorite(any(HttpServletRequest.class), eq("calendar")))
                .thenReturn(new FavoritesService.AddResult(CALENDAR, true));

        mockMvc.perform(
                        withCsrf(post("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fname\":\"calendar\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/uPortal" + URI + "/u12l1n20"))
                .andExpect(content().json("{\"nodeId\":\"u12l1n20\",\"fname\":\"calendar\"}"));
    }

    @Test
    public void addingAnExistingFavoriteReturnsIt() throws Exception {
        when(service.addFavorite(any(HttpServletRequest.class), eq("calendar")))
                .thenReturn(new FavoritesService.AddResult(CALENDAR, false));

        mockMvc.perform(
                        withCsrf(post("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fname\":\"calendar\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().json("{\"nodeId\":\"u12l1n20\"}"));
    }

    @Test
    public void refusesAddingWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(
                        api(post("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fname\":\"calendar\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        api(post("/uPortal" + URI))
                                .header(csrfToken.getHeaderName(), "forged")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fname\":\"calendar\"}"))
                .andExpect(status().isForbidden());

        verify(service, never()).addFavorite(any(), any());
    }

    @Test
    public void acceptsABearerTokenWithoutTheCsrfToken() throws Exception {
        when(service.addFavorite(any(HttpServletRequest.class), eq("calendar")))
                .thenReturn(new FavoritesService.AddResult(CALENDAR, true));

        mockMvc.perform(
                        api(post("/uPortal" + URI))
                                .header("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.e30.sig")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fname\":\"calendar\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    public void refusesAMalformedBody() throws Exception {
        mockMvc.perform(
                        withCsrf(post("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void removesAFavorite() throws Exception {
        mockMvc.perform(withCsrf(delete("/uPortal" + URI + "/u12l1n20")))
                .andExpect(status().isNoContent());

        verify(service).removeFavorite(any(HttpServletRequest.class), eq("u12l1n20"));
    }

    @Test
    public void refusesRemovingWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(api(delete("/uPortal" + URI + "/u12l1n20")))
                .andExpect(status().isForbidden());

        verify(service, never()).removeFavorite(any(), any());
    }

    @Test
    public void reportsAFavoriteThatCannotBeRemoved() throws Exception {
        doThrow(new FavoritesException(HttpStatus.FORBIDDEN, "The favorite may not be removed"))
                .when(service)
                .removeFavorite(any(HttpServletRequest.class), eq("u12l1n20"));

        mockMvc.perform(withCsrf(delete("/uPortal" + URI + "/u12l1n20")))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"message\":\"The favorite may not be removed\"}"));
    }

    @Test
    public void movesAFavoriteAndReturnsTheNewOrder() throws Exception {
        when(service.getFavorites(any(HttpServletRequest.class)))
                .thenReturn(
                        new Favorites(
                                Collections.emptyList(),
                                Collections.singletonList(CALENDAR),
                                null));

        mockMvc.perform(
                        withCsrf(post("/uPortal" + URI + "/u12l1n20/move"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"before\":\"u12l1n18\"}"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .json(
                                        "{\"portlets\":[{\"nodeId\":\"u12l1n20\"}],"
                                                + "\"marketplaceUrl\":null}"));

        verify(service)
                .moveFavorite(
                        any(HttpServletRequest.class), eq("u12l1n20"), eq("u12l1n18"), isNull());
    }

    @Test
    public void refusesMovingWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(
                        api(post("/uPortal" + URI + "/u12l1n20/move"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"after\":\"u12l1n18\"}"))
                .andExpect(status().isForbidden());

        verify(service, never()).moveFavorite(any(), any(), any(), any());
    }
}
