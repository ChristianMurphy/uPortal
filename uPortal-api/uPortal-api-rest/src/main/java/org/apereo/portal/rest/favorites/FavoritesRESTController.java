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
package org.apereo.portal.rest.favorites;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current user's favorites, for the {@code up-favorites} web component and other clients.
 *
 * <ul>
 *   <li>{@code GET /api/v6-0/favorites}: the favorite collections and portlets, in layout order,
 *       and the marketplace URL.
 *   <li>{@code POST /api/v6-0/favorites} with {@code {"fname": "calendar"}}: adds a portlet; 201
 *       with the new favorite, or 200 with the existing one.
 *   <li>{@code DELETE /api/v6-0/favorites/{nodeId}}: removes a favorite; 204.
 *   <li>{@code POST /api/v6-0/favorites/{nodeId}/move} with {@code {"before": "<nodeId>"}} or
 *       {@code {"after": "<nodeId>"}}: reorders; 200 with the favorites.
 * </ul>
 *
 * <p>The user is the one of the portal session (or of an OIDC bearer token), established by Spring
 * Security's pre-authentication filter; guests get 401. Unsafe requests need Spring Security's CSRF
 * token unless they carry a bearer token (see {@code PortalCsrfSecurityRequestMatcher}). Refusals
 * answer {@code {"message": "..."}} with 400 (bad request), 403 (not permitted, including a layout
 * that refuses the change), 404 (unknown portlet or favorite) or 409 (the layout has no favorites
 * folder).
 */
@RestController
public class FavoritesRESTController {

    public static final String FAVORITES_URI = "/v6-0/favorites";

    private final FavoritesService favoritesService;

    public FavoritesRESTController(FavoritesService favoritesService) {
        this.favoritesService = favoritesService;
    }

    /** Body of {@code POST /api/v6-0/favorites}. */
    public static class AddRequest {
        private String fname;

        public String getFname() {
            return fname;
        }

        public void setFname(String fname) {
            this.fname = fname;
        }
    }

    /** Body of {@code POST /api/v6-0/favorites/{nodeId}/move}. */
    public static class MoveRequest {
        private String before;
        private String after;

        public String getBefore() {
            return before;
        }

        public void setBefore(String before) {
            this.before = before;
        }

        public String getAfter() {
            return after;
        }

        public void setAfter(String after) {
            this.after = after;
        }
    }

    @GetMapping(value = FAVORITES_URI, produces = MediaType.APPLICATION_JSON_VALUE)
    public Favorites getFavorites(HttpServletRequest request) {
        return favoritesService.getFavorites(request);
    }

    @PostMapping(
            value = FAVORITES_URI,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FavoritePortlet> addFavorite(
            @RequestBody AddRequest body, HttpServletRequest request) {
        final FavoritesService.AddResult result =
                favoritesService.addFavorite(request, body.getFname());
        if (!result.isCreated()) {
            return ResponseEntity.ok(result.getFavorite());
        }
        final URI location =
                URI.create(
                        request.getContextPath()
                                + "/api"
                                + FAVORITES_URI
                                + "/"
                                + result.getFavorite().getNodeId());
        return ResponseEntity.created(location).body(result.getFavorite());
    }

    @DeleteMapping(FAVORITES_URI + "/{nodeId}")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable("nodeId") String nodeId, HttpServletRequest request) {
        favoritesService.removeFavorite(request, nodeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(
            value = FAVORITES_URI + "/{nodeId}/move",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Favorites moveFavorite(
            @PathVariable("nodeId") String nodeId,
            @RequestBody MoveRequest body,
            HttpServletRequest request) {
        favoritesService.moveFavorite(request, nodeId, body.getBefore(), body.getAfter());
        return favoritesService.getFavorites(request);
    }

    @ExceptionHandler(FavoritesException.class)
    public ResponseEntity<Map<String, Object>> refused(FavoritesException e) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", e.getStatus().value());
        body.put("message", e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
