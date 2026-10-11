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
package org.apereo.portal.rest.backgroundpreference;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's page background, for the {@code up-background-preference} web component.
 *
 * <ul>
 *   <li>{@code GET /api/v6-0/background-preference/{fname}}: {@code {"containerSelector": ...,
 *       "images": [{"url", "thumbnailUrl", "caption"}, ...], "selected": url or null}}.
 *   <li>{@code PUT /api/v6-0/background-preference/{fname}} with {@code {"selected": url}} (or null
 *       for none): saves the choice and answers the same body. 400 when the URL is not one of the
 *       offered images.
 * </ul>
 *
 * <p>{@code fname} is the channel's: a Background Preference channel (the web component or the old
 * portlet), in the user's layout, that the user may render; otherwise 404. The choice is stored on
 * the user's own portlet entity of that channel; the request names no user or entity. Guests get
 * 401. The PUT needs Spring Security's CSRF token unless it carries a bearer token (see {@code
 * PortalCsrfSecurityRequestMatcher}). Answers are not cached.
 */
@RestController
public class BackgroundPreferenceRESTController {

    public static final String BACKGROUND_URI = "/v6-0/background-preference/{fname}";

    private final BackgroundPreferenceService backgroundPreferenceService;

    public BackgroundPreferenceRESTController(
            BackgroundPreferenceService backgroundPreferenceService) {
        this.backgroundPreferenceService = backgroundPreferenceService;
    }

    /** Body of the PUT. */
    public static class UpdateRequest {
        private @Nullable String selected;

        public @Nullable String getSelected() {
            return selected;
        }

        public void setSelected(@Nullable String selected) {
            this.selected = selected;
        }
    }

    @GetMapping(value = BACKGROUND_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BackgroundPreference> getPreference(
            @PathVariable("fname") String fname, HttpServletRequest request) {
        return answer(backgroundPreferenceService.getPreference(request, fname));
    }

    @PutMapping(
            value = BACKGROUND_URI,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BackgroundPreference> setPreference(
            @PathVariable("fname") String fname,
            @RequestBody UpdateRequest body,
            HttpServletRequest request) {
        return answer(
                backgroundPreferenceService.setPreference(request, fname, body.getSelected()));
    }

    @ExceptionHandler(BackgroundPreferenceException.class)
    public ResponseEntity<Map<String, Object>> refused(BackgroundPreferenceException e) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", e.getStatus().value());
        body.put("message", e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static ResponseEntity<BackgroundPreference> answer(BackgroundPreference preference) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(preference);
    }
}
