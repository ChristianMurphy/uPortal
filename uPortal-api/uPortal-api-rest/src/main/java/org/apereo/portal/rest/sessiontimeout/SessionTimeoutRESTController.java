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

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current user's session timeout, for the {@code up-session-timeout} web component.
 *
 * <ul>
 *   <li>{@code GET /api/v6-0/session}: {@code {"maxInactiveIntervalSeconds": 1800}}, the session's
 *       timeout; zero or less means it never expires.
 *   <li>{@code POST /api/v6-0/session/keep-alive}: extends the session (any request does) and
 *       answers the same body, so the client starts its next period from it.
 * </ul>
 *
 * <p>Guests and requests without a session get 401 with {@code {"message": "..."}}: only a
 * signed-in user has a session to keep. The POST needs Spring Security's CSRF token unless it
 * carries a bearer token (see {@code PortalCsrfSecurityRequestMatcher}). Answers are not cached.
 */
@RestController
public class SessionTimeoutRESTController {

    public static final String SESSION_URI = "/v6-0/session";
    public static final String KEEP_ALIVE_URI = SESSION_URI + "/keep-alive";

    private final IPersonManager personManager;

    public SessionTimeoutRESTController(IPersonManager personManager) {
        this.personManager = personManager;
    }

    @GetMapping(value = SESSION_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getSession(HttpServletRequest request) {
        return describe(request);
    }

    @PostMapping(value = KEEP_ALIVE_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> keepAlive(HttpServletRequest request) {
        return describe(request);
    }

    private ResponseEntity<Map<String, Object>> describe(HttpServletRequest request) {
        final Map<String, Object> body = new LinkedHashMap<>();
        // Never create a session here: a request without one has nothing to keep
        final HttpSession session = request.getSession(false);
        final @Nullable IPerson person = session == null ? null : personManager.getPerson(request);
        if (session == null || person == null || person.isGuest()) {
            body.put("status", HttpStatus.UNAUTHORIZED.value());
            body.put("message", "Sign in to have a session timeout");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }
        body.put("maxInactiveIntervalSeconds", session.getMaxInactiveInterval());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
