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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Concrete implementation of Spring's <code>RequestMatcher</code> that tells Spring Security which
 * requests (URLs) to protect with CSRF tokens. Every request with an unsafe method (anything but
 * GET and HEAD) is protected, the REST APIs under {@code /api} included: their browser clients
 * authenticate with the session cookie, which the browser would also send with a forged request.
 *
 * <p>The exceptions:
 *
 * <ul>
 *   <li>{@code /Login} and {@code /Logout};
 *   <li>an {@code /api} request that carries a bearer token in the {@code Authorization} header,
 *       because a browser never adds that header by itself;
 *   <li>the OIDC endpoints {@code /api/v5-5/oauth/token} (a client authenticates with its secret,
 *       not the session) and {@code /api/v5-1/userinfo} (it only reads), and the Swagger UI.
 * </ul>
 *
 * <p>A client sends the token in the {@code X-CSRF-TOKEN} header or the {@code _csrf} parameter.
 * Portal pages carry it in their {@code _csrf} and {@code _csrf_header} meta tags.
 */
@Component("portalCsrfSecurityRequestMatcher")
public class PortalCsrfSecurityRequestMatcher implements RequestMatcher {

    private static final String[] IGNORED_METHODS =
            new String[] {HttpMethod.GET.toString(), HttpMethod.HEAD.toString()};

    private static final String LOGIN_PATTERN = "/Login.*";

    private static final String LOGOUT_PATTERN = "/Logout.*";

    private static final String API_DOCS_PATTERN = "/api/swagger-ui.html";

    private static final String OAUTH_TOKEN_PATTERN = "/api/v5-5/oauth/token(\\?.*)?";

    private static final String USERINFO_PATTERN = "/api/v5-1/userinfo(\\?.*)?";

    private static final String[] IGNORED_PATTERNS =
            new String[] {
                LOGIN_PATTERN,
                LOGOUT_PATTERN,
                API_DOCS_PATTERN,
                OAUTH_TOKEN_PATTERN,
                USERINFO_PATTERN
            };

    /** The REST APIs, where a bearer token stands in for the CSRF token. */
    private static final String API_PATTERN = "/api(/.*|\\?.*)?";

    private static final String BEARER_PREFIX = "Bearer ";

    private final Set<RequestMatcher> ignoredMatchers = new HashSet<>();

    private final RequestMatcher apiMatcher = new RegexRequestMatcher(API_PATTERN, null);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void init() {
        Arrays.stream(IGNORED_PATTERNS)
                .forEach(pattern -> ignoredMatchers.add(new RegexRequestMatcher(pattern, null)));
        logger.info(
                "CSRF token protection ignoring the following methods: {}",
                Arrays.asList(IGNORED_METHODS));
        logger.info(
                "CSRF token protection ignoring the following patterns: {}",
                Arrays.asList(IGNORED_PATTERNS));
    }

    @Override
    public boolean matches(HttpServletRequest request) {

        // Check the method
        boolean result =
                Arrays.stream(IGNORED_METHODS)
                        .noneMatch(s -> s.equalsIgnoreCase(request.getMethod()));

        // Check URI
        if (result) {
            if (ignoredMatchers.stream().anyMatch(matcher -> matcher.matches(request))) {
                result = false;
            } else if (apiMatcher.matches(request)) {
                result = !hasBearerToken(request);
            }
        }

        logger.trace(
                "Spring CSRF protection for method='{}', URI='{}' is {}",
                request.getMethod(),
                request.getRequestURI(),
                result ? "ENABLED" : "DISABLED");

        return result;
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        final String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    }
}
