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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own account, for the {@code up-self-edit-account} web component.
 *
 * <ul>
 *   <li>{@code GET /api/v6-0/account}: the user's details, whether they may edit their account, and
 *       the attributes they may edit with their values (see {@link Account}).
 *   <li>{@code PUT /api/v6-0/account} with {@code {"attributes": {"department": "..."}, "password":
 *       "...", "confirmPassword": "..."}}: saves the changes; 204. Every field is optional.
 * </ul>
 *
 * <p>Both act on the user of the portal session only; guests get 401. PUT needs Spring Security's
 * CSRF token unless it carries a bearer token (see {@code PortalCsrfSecurityRequestMatcher}).
 * Refusals answer {@code {"status": ..., "message": ...}}, plus {@code "code"}, a {@code
 * Messages.properties} key, when a password was refused. No answer contains a password.
 */
@RestController
public class SelfEditAccountRESTController {

    public static final String ACCOUNT_URI = "/v6-0/account";

    private final SelfEditAccountService accountService;

    public SelfEditAccountRESTController(SelfEditAccountService accountService) {
        this.accountService = accountService;
    }

    /** Body of {@code PUT /api/v6-0/account}. */
    public static class UpdateRequest {
        private @Nullable Map<String, String> attributes;
        private @Nullable String password;
        private @Nullable String confirmPassword;

        public @Nullable Map<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(@Nullable Map<String, String> attributes) {
            this.attributes = attributes;
        }

        public @Nullable String getPassword() {
            return password;
        }

        public void setPassword(@Nullable String password) {
            this.password = password;
        }

        public @Nullable String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(@Nullable String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }
    }

    @GetMapping(value = ACCOUNT_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public Account getAccount(HttpServletRequest request) {
        return accountService.getAccount(request);
    }

    @PutMapping(value = ACCOUNT_URI, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateAccount(
            @RequestBody UpdateRequest body, HttpServletRequest request) {
        final Map<String, String> attributes =
                body.getAttributes() == null
                        ? Collections.<String, String>emptyMap()
                        : body.getAttributes();
        accountService.updateAccount(
                request,
                new SelfEditAccountService.Update(
                        attributes, body.getPassword(), body.getConfirmPassword()));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AccountException.class)
    public ResponseEntity<Map<String, Object>> refused(AccountException e) {
        return error(e.getStatus(), String.valueOf(e.getMessage()), e.getCode());
    }

    /**
     * A body that is not the expected JSON. Spring's default handler would log the parser's
     * message, which can quote the body and so a password; this answers without it.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "The request body is not an account update", null);
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String message, @Nullable String code) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        if (code != null) {
            body.put("code", code);
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
