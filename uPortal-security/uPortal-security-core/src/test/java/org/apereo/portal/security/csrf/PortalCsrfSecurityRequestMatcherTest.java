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
package org.apereo.portal.security.csrf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

public class PortalCsrfSecurityRequestMatcherTest {

    /**
     * Every state-changing REST endpoint under /api (MOD-I130): method, servlet path, path info and
     * query string, as web.xml maps them.
     */
    private static final String[][] PROTECTED_API_REQUESTS = {
        {"POST", "/api", "/layout", "action=addFavorite&channelId=12"},
        {"POST", "/api", "/layout", "action=removeByFName&fname=weather"},
        {"POST", "/api", "/layout", "action=chooseSkin&skinName=defaultSkin"},
        {"POST", "/api", "/marketplace/weather/rating/5", "review=good"},
        {"PUT", "/api", "/preferences/weather", null},
        {"PUT", "/api", "/preferences/definition/weather", null},
        {"POST", "/api", "/portlet-list/", null},
        {"PUT", "/api", "/portlet-list/aaa-bbb-ccc", null},
        {"DELETE", "/api", "/portlet-list/aaa-bbb-ccc", null},
        {"POST", "/api", "/v4-3/pags/Everyone.json", null},
        {"PUT", "/api", "/v4-3/pags/Everyone.json", null},
        {"POST", "/api", "/import", null},
        {"DELETE", "/api", "/entity/portlet-definition/weather", null},
        {"POST", "/api", "/deletePermission", null},
        {"POST", "/api", "/updatePermission", null},
        {"DELETE", "/api", "/v5-4/caches", null},
        {"POST", "/api/analytics", null, null},
        {"POST", "/api", "/v6-0/favorites", null},
        {"DELETE", "/api", "/v6-0/favorites/u12l1n3", null},
        {"PUT", "/api", "/v6-0/favorites/u12l1n3", null},
    };

    /** Unsafe requests that stay exempt: login, logout and the OIDC endpoints. */
    private static final String[][] EXEMPT_REQUESTS = {
        {"POST", "/Login", null, null},
        {"POST", "/Logout", null, null},
        {"POST", "/api", "/v5-5/oauth/token", "client_id=app&client_secret=secret"},
        {"POST", "/api", "/v5-1/userinfo", "claims=email"},
    };

    private PortalCsrfSecurityRequestMatcher matcher;
    private HttpSessionCsrfTokenRepository tokens;
    private CsrfFilter csrfFilter;

    @Before
    public void setUp() {
        matcher = new PortalCsrfSecurityRequestMatcher();
        matcher.init();
        // As securityContext.xml configures it:  <security:csrf request-matcher-ref=.../>
        tokens = new HttpSessionCsrfTokenRepository();
        csrfFilter = new CsrfFilter(tokens);
        csrfFilter.setRequireCsrfProtectionMatcher(matcher);
    }

    private static MockHttpServletRequest request(String method, String servletPath, String path) {
        return request(method, servletPath, path, null);
    }

    private static MockHttpServletRequest request(
            String method, String servletPath, String path, String query) {
        final MockHttpServletRequest request =
                new MockHttpServletRequest(
                        method, "/uPortal" + servletPath + (path == null ? "" : path));
        request.setContextPath("/uPortal");
        request.setServletPath(servletPath);
        request.setPathInfo(path);
        request.setQueryString(query);
        request.setSession(new MockHttpSession());
        return request;
    }

    private static MockHttpServletRequest request(String[] spec) {
        return request(spec[0], spec[1], spec[2], spec[3]);
    }

    private static String describe(String[] spec) {
        return spec[0] + " " + spec[1] + (spec[2] == null ? "" : spec[2]);
    }

    /** Runs the request through Spring Security's CsrfFilter; true if it reached the endpoint. */
    private boolean passes(MockHttpServletRequest request) throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();
        csrfFilter.doFilter(request, response, chain);
        if (chain.getRequest() == null) {
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
            return false;
        }
        return true;
    }

    /** Puts a token in the request's session and sends it the way up.csrfHeaders() does. */
    private MockHttpServletRequest withToken(MockHttpServletRequest request) {
        final CsrfToken token = tokens.generateToken(request);
        tokens.saveToken(token, request, new MockHttpServletResponse());
        request.addHeader(token.getHeaderName(), token.getToken());
        return request;
    }

    @Test
    public void protectsUnsafeRequestsToPortalPages() {
        assertTrue(matcher.matches(request("POST", "/f", "/welcome/normal/action.uP")));
        assertFalse(matcher.matches(request("GET", "/f", "/welcome/normal/render.uP")));
    }

    @Test
    public void leavesLoginAndLogoutAlone() {
        assertFalse(matcher.matches(request("POST", "/Login", null)));
        assertFalse(matcher.matches(request("POST", "/Logout", null)));
    }

    @Test
    public void protectsUnsafeRequestsToTheWebComponentApis() {
        assertTrue(matcher.matches(request("POST", "/api", "/v6-0/favorites")));
        assertTrue(matcher.matches(request("DELETE", "/api", "/v6-0/favorites/u12l1n3")));
        assertTrue(matcher.matches(request("PUT", "/api", "/v6-0/favorites/u12l1n3")));
        assertFalse(matcher.matches(request("GET", "/api", "/v6-0/favorites")));
        assertFalse(matcher.matches(request("HEAD", "/api", "/v6-0/favorites")));
    }

    @Test
    public void protectsUnsafeRequestsToTheLegacyApis() {
        assertTrue(matcher.matches(request("POST", "/api", "/layout")));
        assertTrue(matcher.matches(request("DELETE", "/api", "/v5-0/portlet-list/abc")));
        assertTrue(matcher.matches(request("POST", "/api", null)));
        assertFalse(matcher.matches(request("GET", "/api", "/portlet-list/")));
        assertFalse(matcher.matches(request("GET", "/api", "/swagger-ui.html")));
    }

    @Test
    public void exemptsBearerTokenRequests() {
        final MockHttpServletRequest bearer = request("POST", "/api", "/v6-0/favorites");
        bearer.addHeader("Authorization", "bearer eyJhbGciOiJIUzUxMiJ9.e30.sig");
        assertFalse(matcher.matches(bearer));

        final MockHttpServletRequest legacy = request("POST", "/api", "/layout");
        legacy.addHeader("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.e30.sig");
        assertFalse(matcher.matches(legacy));

        final MockHttpServletRequest basic = request("POST", "/api", "/v6-0/favorites");
        basic.addHeader("Authorization", "Basic YWRtaW46YWRtaW4=");
        assertTrue(matcher.matches(basic));

        // Portal pages are not APIs:  a bearer token does not stand in for the CSRF token there
        final MockHttpServletRequest page = request("POST", "/f", "/welcome/normal/action.uP");
        page.addHeader("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.e30.sig");
        assertTrue(matcher.matches(page));
    }

    @Test
    public void everyStateChangingApiRefusesARequestWithoutTheToken() throws Exception {
        final List<String> accepted = new ArrayList<>();
        for (final String[] spec : PROTECTED_API_REQUESTS) {
            if (passes(request(spec))) {
                accepted.add(describe(spec));
            }
        }
        assertEquals("accepted without a CSRF token", new ArrayList<>(), accepted);
    }

    @Test
    public void everyStateChangingApiAcceptsARequestWithTheToken() throws Exception {
        final List<String> refused = new ArrayList<>();
        for (final String[] spec : PROTECTED_API_REQUESTS) {
            if (!passes(withToken(request(spec)))) {
                refused.add(describe(spec));
            }
        }
        assertEquals("refused with the CSRF token", new ArrayList<>(), refused);
    }

    @Test
    public void aWrongTokenIsRefused() throws Exception {
        final MockHttpServletRequest request = withToken(request("POST", "/api", "/layout"));
        final MockHttpServletRequest forged = request("POST", "/api", "/layout");
        forged.setSession(request.getSession());
        forged.addHeader("X-CSRF-TOKEN", "not-the-session-token");
        assertFalse(passes(forged));
    }

    @Test
    public void bearerTokenRequestsPassWithoutTheCsrfToken() throws Exception {
        for (final String[] spec : PROTECTED_API_REQUESTS) {
            final MockHttpServletRequest request = request(spec);
            request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.e30.sig");
            assertTrue(describe(spec), passes(request));
        }
    }

    @Test
    public void exemptRequestsPassWithoutTheToken() throws Exception {
        for (final String[] spec : EXEMPT_REQUESTS) {
            assertTrue(describe(spec), passes(request(spec)));
        }
    }

    @Test
    public void theFilterPublishesTheTokenForPages() throws Exception {
        final MockHttpServletRequest page = request("GET", "/f", "/welcome/normal/render.uP");
        assertTrue(passes(page));
        final CsrfToken token = (CsrfToken) page.getAttribute(CsrfToken.class.getName());
        assertNotNull(token);
        assertEquals("X-CSRF-TOKEN", token.getHeaderName());
    }
}
