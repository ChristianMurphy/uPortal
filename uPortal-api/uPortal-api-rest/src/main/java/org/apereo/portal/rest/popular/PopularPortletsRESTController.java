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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * {@code GET /api/v6-0/popular-portlets}: the portlets users added to their layouts most often in
 * the last {@code days} days (1 to 365, default 30), with titles in {@code locale} (a BCP 47 tag,
 * default the request's locale). Every user may ask, guests included; each sees only the portlets
 * they may subscribe to.
 */
@RestController
public class PopularPortletsRESTController {

    public static final String POPULAR_PORTLETS_URI = "/v6-0/popular-portlets";

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 365;

    private final PopularPortletsService popularPortletsService;
    private final IPersonManager personManager;

    public PopularPortletsRESTController(
            PopularPortletsService popularPortletsService, IPersonManager personManager) {
        this.popularPortletsService = popularPortletsService;
        this.personManager = personManager;
    }

    @GetMapping(value = POPULAR_PORTLETS_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPopularPortlets(
            @RequestParam(value = "days", required = false) @Nullable String days,
            @RequestParam(value = "locale", required = false) @Nullable String locale,
            HttpServletRequest request) {
        final int dayCount;
        try {
            dayCount = days == null ? DEFAULT_DAYS : Integer.parseInt(days.trim());
        } catch (NumberFormatException e) {
            return error(HttpStatus.BAD_REQUEST, "days must be a whole number");
        }
        if (dayCount < 1 || dayCount > MAX_DAYS) {
            return error(HttpStatus.BAD_REQUEST, "days must be between 1 and " + MAX_DAYS);
        }
        final IPerson person = personManager.getPerson(request);
        if (person == null) {
            return error(HttpStatus.UNAUTHORIZED, "No portal session");
        }
        return ResponseEntity.ok(
                popularPortletsService.getPopularPortlets(
                        person, dayCount, toLocale(locale, request), request.getContextPath()));
    }

    private static Locale toLocale(@Nullable String tag, HttpServletRequest request) {
        if (StringUtils.isNotBlank(tag)) {
            final Locale locale = Locale.forLanguageTag(tag.trim());
            if (!locale.getLanguage().isEmpty()) {
                return locale;
            }
        }
        return RequestContextUtils.getLocale(request);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
