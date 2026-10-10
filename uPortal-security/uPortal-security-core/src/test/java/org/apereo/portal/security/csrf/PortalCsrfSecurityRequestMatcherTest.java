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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class PortalCsrfSecurityRequestMatcherTest {

    private PortalCsrfSecurityRequestMatcher matcher;

    @Before
    public void setUp() {
        matcher = new PortalCsrfSecurityRequestMatcher();
        matcher.init();
    }

    private static MockHttpServletRequest request(String method, String servletPath, String path) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContextPath("/uPortal");
        request.setServletPath(servletPath);
        request.setPathInfo(path);
        return request;
    }

    @Test
    public void protectsUnsafeRequestsToPortalPages() {
        assertTrue(matcher.matches(request("POST", "/f", "/welcome/normal/action.uP")));
        assertFalse(matcher.matches(request("GET", "/f", "/welcome/normal/render.uP")));
    }

    @Test
    public void leavesLoginLogoutAndTheLegacyApisAlone() {
        assertFalse(matcher.matches(request("POST", "/Login", null)));
        assertFalse(matcher.matches(request("POST", "/api", "/layout")));
        assertFalse(matcher.matches(request("DELETE", "/api", "/v5-0/portlet-list/abc")));
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
    public void exemptsBearerTokenRequests() {
        final MockHttpServletRequest bearer = request("POST", "/api", "/v6-0/favorites");
        bearer.addHeader("Authorization", "bearer eyJhbGciOiJIUzUxMiJ9.e30.sig");
        assertFalse(matcher.matches(bearer));

        final MockHttpServletRequest basic = request("POST", "/api", "/v6-0/favorites");
        basic.addHeader("Authorization", "Basic YWRtaW46YWRtaW4=");
        assertTrue(matcher.matches(basic));
    }
}
