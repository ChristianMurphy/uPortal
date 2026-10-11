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
package org.apereo.portal.rest.i18n;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.user.IUserInstanceManager;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the portal's messages to web components, so they show the same localized text as the rest
 * of the portal: {@code Messages.properties}, its translations, and messages edited in the portal
 * (the database message source).
 *
 * <p>{@code GET /api/v6-0/messages?keys=a,b&locale=fr-CA} answers {@code {"locale": "fr-CA",
 * "messages": {"a": "...", "b": "..."}}}. Messages come back as the message source resolves them
 * without arguments: quotes unescaped and {@code {0}}-style placeholders kept for the component to
 * fill. Unknown keys are left out, so the component keeps its own default text. Without a valid
 * {@code locale} the user's portal locale is used. Anyone may read messages, guests included.
 */
@RestController
public class MessagesRESTController {

    public static final String MESSAGES_URI = "/v6-0/messages";

    /** Most keys one request may ask for. */
    static final int MAX_KEYS = 200;

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]{1,200}");

    private final MessageSource messageSource;
    private final IUserInstanceManager userInstanceManager;

    public MessagesRESTController(
            MessageSource messageSource, IUserInstanceManager userInstanceManager) {
        this.messageSource = messageSource;
        this.userInstanceManager = userInstanceManager;
    }

    @GetMapping(value = MESSAGES_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getMessages(
            @RequestParam("keys") String keys,
            @RequestParam(value = "locale", required = false) String locale,
            HttpServletRequest request) {
        final Set<String> keySet = new LinkedHashSet<>();
        for (final String key : keys.split(",", -1)) {
            final String trimmed = key.trim();
            if (!trimmed.isEmpty()) {
                keySet.add(trimmed);
            }
        }
        if (keySet.isEmpty() || keySet.size() > MAX_KEYS) {
            return badRequest("Ask for 1 to " + MAX_KEYS + " comma-separated keys");
        }
        for (final String key : keySet) {
            if (!KEY.matcher(key).matches()) {
                return badRequest("Invalid message key");
            }
        }

        final Locale resolvedLocale = resolveLocale(locale, request);
        final Map<String, String> messages = new TreeMap<>();
        for (final String key : keySet) {
            final String message = messageSource.getMessage(key, null, null, resolvedLocale);
            // The portal's message source answers unknown keys with the key itself
            if (message != null && !message.equals(key)) {
                messages.put(key, message);
            }
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("locale", resolvedLocale.toLanguageTag());
        body.put("messages", messages);
        return ResponseEntity.ok(body);
    }

    private Locale resolveLocale(String locale, HttpServletRequest request) {
        if (locale != null) {
            // zh_TW as Java and portal_locales write it, as well as the language tag zh-TW
            final Locale requested = Locale.forLanguageTag(locale.trim().replace('_', '-'));
            if (!requested.getLanguage().isEmpty()) {
                return requested;
            }
        }
        final LocaleManager localeManager =
                userInstanceManager.getUserInstance(request).getLocaleManager();
        final List<Locale> locales = localeManager != null ? localeManager.getLocales() : null;
        if (locales != null && !locales.isEmpty() && locales.get(0) != null) {
            return locales.get(0);
        }
        return request.getLocale();
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Collections.<String, Object>singletonMap("message", message));
    }
}
