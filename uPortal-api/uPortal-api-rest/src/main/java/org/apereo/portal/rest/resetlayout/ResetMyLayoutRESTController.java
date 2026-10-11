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

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.layout.IUserLayoutHelper;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.services.persondir.support.NamedPersonImpl;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resets the signed-in user's own layout, for the {@code up-reset-my-layout} web component.
 *
 * <ul>
 *   <li>{@code GET /api/v6-0/layout/reset}: {@code {"userName": "student"}}, the user whose layout
 *       a reset would act on, for the confirmation step.
 *   <li>{@code POST /api/v6-0/layout/reset}: resets that layout the way the Reset My Layout flow
 *       did ({@link IUserLayoutHelper#resetUserLayout}: the default profile goes back to the
 *       default layout, and the user's structure and theme preferences are deleted), and answers
 *       the same body. The user sees the default layout from their next sign-in on.
 * </ul>
 *
 * <p>The request names no user: the layout is always the one of the portal session's user. Guests
 * get 401 with {@code {"message": "..."}}. The POST needs Spring Security's CSRF token unless it
 * carries a bearer token (see {@code PortalCsrfSecurityRequestMatcher}). Answers are not cached.
 * Administrators reset other users' layouts with the reset-user-layout flow, which is unchanged.
 */
@RestController
public class ResetMyLayoutRESTController {

    public static final String RESET_URI = "/v6-0/layout/reset";

    private final IPersonManager personManager;
    private final IUserLayoutHelper userLayoutHelper;

    public ResetMyLayoutRESTController(
            IPersonManager personManager, IUserLayoutHelper userLayoutHelper) {
        this.personManager = personManager;
        this.userLayoutHelper = userLayoutHelper;
    }

    @GetMapping(value = RESET_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> describeReset(HttpServletRequest request) {
        final @Nullable IPerson person = getSignedInUser(request);
        if (person == null) {
            return refuseGuest();
        }
        return answer(person);
    }

    @PostMapping(value = RESET_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> resetLayout(HttpServletRequest request) {
        final @Nullable IPerson person = getSignedInUser(request);
        if (person == null) {
            return refuseGuest();
        }
        userLayoutHelper.resetUserLayout(
                new NamedPersonImpl(person.getUserName(), person.getAttributeMap()));
        return answer(person);
    }

    private @Nullable IPerson getSignedInUser(HttpServletRequest request) {
        final @Nullable IPerson person = personManager.getPerson(request);
        return person == null || person.isGuest() ? null : person;
    }

    private static ResponseEntity<Map<String, Object>> answer(IPerson person) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("userName", person.getUserName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static ResponseEntity<Map<String, Object>> refuseGuest() {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("message", "Sign in to reset your layout");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
