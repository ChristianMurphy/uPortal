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

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current user's locale, for the {@code up-user-locale} web component and other clients.
 *
 * <ul>
 *   <li>{@code GET /api/v6-0/locale}: the portal's locales, named in the user's language, and the
 *       code of the one the user chose ({@code null} when none).
 *   <li>{@code PUT /api/v6-0/locale} with {@code {"code": "fr_FR"}}: makes it the user's locale;
 *       200 with the same body as GET, now in the new language. 400 when the code is not one of the
 *       portal's locales.
 * </ul>
 *
 * <p>Guests may use it too: their choice lasts the session, a signed-in user's is also stored. PUT
 * needs Spring Security's CSRF token unless it carries a bearer token (see {@code
 * PortalCsrfSecurityRequestMatcher}).
 */
@RestController
public class UserLocaleRESTController {

    public static final String LOCALE_URI = "/v6-0/locale";

    private final UserLocaleService userLocaleService;

    public UserLocaleRESTController(UserLocaleService userLocaleService) {
        this.userLocaleService = userLocaleService;
    }

    /** Body of {@code PUT /api/v6-0/locale}. */
    public static class UpdateRequest {
        private @Nullable String code;

        public @Nullable String getCode() {
            return code;
        }

        public void setCode(@Nullable String code) {
            this.code = code;
        }
    }

    @GetMapping(value = LOCALE_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public UserLocale getUserLocale(HttpServletRequest request) {
        return userLocaleService.getUserLocale(request);
    }

    @PutMapping(
            value = LOCALE_URI,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public UserLocale setUserLocale(@RequestBody UpdateRequest body, HttpServletRequest request) {
        return userLocaleService.setUserLocale(request, body.getCode());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> refused(IllegalArgumentException e) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("message", e.getMessage());
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
