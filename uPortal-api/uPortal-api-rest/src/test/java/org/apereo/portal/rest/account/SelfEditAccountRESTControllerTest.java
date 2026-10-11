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
package org.apereo.portal.rest.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.security.csrf.PortalCsrfSecurityRequestMatcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
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
 * The account API over HTTP, with the portal's CSRF protection in front of it (the same CsrfFilter
 * and request matcher that securityContext.xml configures).
 */
public class SelfEditAccountRESTControllerTest {

    private static final String URI = "/api/v6-0/account";
    private static final String SECRET = "n3w-Secret!";

    private static final Account ACCOUNT =
            new Account(
                    "student",
                    Arrays.asList(
                            new AccountAttribute("title", "Title", null),
                            new AccountAttribute("mail", "Email", "student@example.org")),
                    true,
                    Collections.singletonList(
                            new AccountAttribute("department", "Department", "Biology")));

    private static final String ACCOUNT_JSON =
            "{\"username\":\"student\",\"details\":["
                    + "{\"name\":\"title\",\"label\":\"Title\",\"value\":null},"
                    + "{\"name\":\"mail\",\"label\":\"Email\",\"value\":\"student@example.org\"}],"
                    + "\"canEdit\":true,\"editable\":["
                    + "{\"name\":\"department\",\"label\":\"Department\",\"value\":\"Biology\"}]}";

    private SelfEditAccountService service;
    private MockMvc mockMvc;
    private HttpSessionCsrfTokenRepository tokenRepository;
    private MockHttpSession session;
    private CsrfToken csrfToken;

    @Before
    public void setUp() {
        service = mock(SelfEditAccountService.class);
        tokenRepository = new HttpSessionCsrfTokenRepository();
        final PortalCsrfSecurityRequestMatcher csrfMatcher = new PortalCsrfSecurityRequestMatcher();
        csrfMatcher.init();
        final CsrfFilter csrfFilter = new CsrfFilter(tokenRepository);
        csrfFilter.setRequireCsrfProtectionMatcher(csrfMatcher);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new SelfEditAccountRESTController(service))
                        .addFilters(csrfFilter)
                        .build();

        session = new MockHttpSession();
        csrfToken = tokenFor(session);
    }

    /** The token the portal put on the page, in that session. */
    private CsrfToken tokenFor(MockHttpSession userSession) {
        final MockHttpServletRequest pageRequest = new MockHttpServletRequest();
        pageRequest.setSession(userSession);
        final CsrfToken token = tokenRepository.generateToken(pageRequest);
        tokenRepository.saveToken(token, pageRequest, new MockHttpServletResponse());
        return token;
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

    private SelfEditAccountService.Update savedUpdate() {
        final ArgumentCaptor<SelfEditAccountService.Update> captor =
                ArgumentCaptor.forClass(SelfEditAccountService.Update.class);
        verify(service).updateAccount(any(HttpServletRequest.class), captor.capture());
        return captor.getValue();
    }

    @Test
    public void returnsTheAccount() throws Exception {
        when(service.getAccount(any(HttpServletRequest.class))).thenReturn(ACCOUNT);

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(ACCOUNT_JSON, true));
    }

    @Test
    public void answersGuestsWith401() throws Exception {
        when(service.getAccount(any(HttpServletRequest.class)))
                .thenThrow(
                        new AccountException(
                                HttpStatus.UNAUTHORIZED, "Sign in to see your account"));

        mockMvc.perform(api(get("/uPortal" + URI)))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":401,\"message\":\"Sign in to see your"
                                                + " account\"}",
                                        true));
    }

    @Test
    public void savesTheChanges() throws Exception {
        mockMvc.perform(
                        update(
                                "{\"attributes\":{\"department\":\"Physics\"},"
                                        + "\"password\":\""
                                        + SECRET
                                        + "\",\"confirmPassword\":\""
                                        + SECRET
                                        + "\"}"))
                .andExpect(status().isNoContent());

        final SelfEditAccountService.Update saved = savedUpdate();
        assertEquals(Collections.singletonMap("department", "Physics"), saved.getAttributes());
        assertEquals(SECRET, saved.getPassword());
        assertEquals(SECRET, saved.getConfirmPassword());
    }

    @Test
    public void treatsMissingFieldsAsNoChange() throws Exception {
        mockMvc.perform(update("{}")).andExpect(status().isNoContent());

        final SelfEditAccountService.Update saved = savedUpdate();
        assertEquals(Collections.emptyMap(), saved.getAttributes());
        assertEquals(null, saved.getPassword());
    }

    @Test
    public void answersRefusalsWithTheirStatusAndMessage() throws Exception {
        doThrow(new AccountException(HttpStatus.FORBIDDEN, "You may not edit your account"))
                .when(service)
                .updateAccount(any(HttpServletRequest.class), any());

        mockMvc.perform(update("{\"password\":\"" + SECRET + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":403,\"message\":\"You may not edit your"
                                                + " account\"}",
                                        true));
    }

    @Test
    public void addsTheMessageCodeToARefusedPassword() throws Exception {
        doThrow(new AccountException(HttpStatus.BAD_REQUEST, "Refused", "passwords.must.match"))
                .when(service)
                .updateAccount(any(HttpServletRequest.class), any());

        mockMvc.perform(update("{\"password\":\"" + SECRET + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"status\":400,\"message\":\"Refused\","
                                                + "\"code\":\"passwords.must.match\"}",
                                        true));
    }

    @Test
    public void refusesAChangeWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(
                        api(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"" + SECRET + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        api(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"" + SECRET + "\"}")
                                .header(csrfToken.getHeaderName(), "forged"))
                .andExpect(status().isForbidden());
        verify(service, never()).updateAccount(any(HttpServletRequest.class), any());
    }

    @Test
    public void refusesTheTokenOfAnotherUsersSession() throws Exception {
        final CsrfToken otherToken = tokenFor(new MockHttpSession());

        mockMvc.perform(
                        api(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"" + SECRET + "\"}")
                                .header(otherToken.getHeaderName(), otherToken.getToken()))
                .andExpect(status().isForbidden());
        verify(service, never()).updateAccount(any(HttpServletRequest.class), any());
    }

    @Test
    public void acceptsABearerTokenWithoutTheCsrfToken() throws Exception {
        mockMvc.perform(
                        api(put("/uPortal" + URI))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .header("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.e30.sig"))
                .andExpect(status().isNoContent());
        verify(service).updateAccount(any(HttpServletRequest.class), any());
    }

    @Test
    public void refusesAMalformedBodyWithoutQuotingIt() throws Exception {
        final MvcResult result =
                mockMvc.perform(update("{\"password\": " + SECRET + "}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(
                                content()
                                        .json(
                                                "{\"status\":400,\"message\":\"The request body"
                                                        + " is not an account update\"}",
                                                true))
                        .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(SECRET));
        verify(service, never()).updateAccount(any(HttpServletRequest.class), any());
    }
}
