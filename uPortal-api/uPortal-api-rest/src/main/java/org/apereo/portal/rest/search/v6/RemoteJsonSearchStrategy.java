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
package org.apereo.portal.rest.search.v6;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.rest.search.ISearchStrategy;
import org.jspecify.annotations.Nullable;

/**
 * Searches a remote service that answers JSON, such as Google Programmable Search, a campus
 * directory or a CMS, as {@link RemoteJsonSearchSettings} describe it.
 *
 * <p>The request is the portal's own: it carries the configured headers and nothing from the user's
 * request, so the user's cookies and session never reach the remote service. Redirects are not
 * followed, so the headers (an API key, say) go only to the configured host.
 */
public class RemoteJsonSearchStrategy implements ISearchStrategy {

    private final RemoteJsonSearchSettings settings;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RemoteJsonSearchStrategy(RemoteJsonSearchSettings settings) {
        this(
                settings,
                HttpClient.newBuilder()
                        .connectTimeout(settings.getTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    RemoteJsonSearchStrategy(RemoteJsonSearchSettings settings, HttpClient httpClient) {
        this.settings = settings;
        this.httpClient = httpClient;
    }

    public RemoteJsonSearchSettings getSettings() {
        return settings;
    }

    @Override
    public String getResultTypeName() {
        return settings.getName();
    }

    /**
     * Results as maps with a {@code title}, and a {@code url} and {@code description} when the
     * remote result has them. Throws {@link RemoteSearchException} when the search fails.
     */
    @Override
    public List<?> search(String query, HttpServletRequest request) {
        final JsonNode body = fetch(query);
        final JsonNode items = body.at(settings.getResultsPointer());
        if (items.isMissingNode() || items.isNull()) {
            // Google's API leaves "items" out when nothing matches
            return new ArrayList<>();
        }
        if (!items.isArray()) {
            throw new RemoteSearchException(
                    settings.getName() + ": " + settings.getResultsPointer() + " is not an array");
        }
        final List<Map<String, String>> results = new ArrayList<>();
        for (JsonNode item : items) {
            final String title = text(item, settings.getTitlePointer());
            if (title == null) {
                continue;
            }
            final Map<String, String> result = new LinkedHashMap<>();
            result.put("title", title);
            final String link = text(item, settings.getLinkPointer());
            if (link != null && (link.startsWith("https://") || link.startsWith("http://"))) {
                result.put("url", link);
            }
            final String description = text(item, settings.getDescriptionPointer());
            if (description != null) {
                result.put("description", description);
            }
            results.add(result);
        }
        return results;
    }

    private JsonNode fetch(String query) {
        final String url =
                settings.getUrlTemplate()
                        .replace(
                                RemoteJsonSearchSettings.QUERY_PLACEHOLDER,
                                URLEncoder.encode(query, StandardCharsets.UTF_8));
        final HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(settings.getTimeout())
                        .header("Accept", "application/json")
                        .GET();
        settings.getHeaders().forEach(builder::header);
        final HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RemoteSearchException(settings.getName() + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteSearchException(settings.getName() + ": interrupted", e);
        }
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new RemoteSearchException(
                    settings.getName() + ": answered " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new RemoteSearchException(settings.getName() + ": the answer is not JSON", e);
        }
    }

    /** The text at the pointer, or null when it is missing, not a value, or blank. */
    private static @Nullable String text(JsonNode item, String pointer) {
        final JsonNode node = item.at(JsonPointer.compile(pointer));
        if (!node.isValueNode() || node.isNull()) {
            return null;
        }
        final String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }
}
