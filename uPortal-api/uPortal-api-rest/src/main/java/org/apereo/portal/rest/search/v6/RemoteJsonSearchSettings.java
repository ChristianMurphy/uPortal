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
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertyResolver;

/**
 * The settings of one remote JSON search provider, read from portal properties under {@code
 * org.apereo.portal.search.remote.<name>.}:
 *
 * <ul>
 *   <li>{@code url} (required): the search URL, with {@code {query}} where the query goes; the
 *       query is URL-encoded. An API key can be part of it.
 *   <li>{@code headers}: request headers, {@code Name: value} pairs separated by {@code ;}.
 *   <li>{@code label}: the name of the provider's results tab; the provider's name otherwise.
 *   <li>{@code results} (default {@code /items}): JSON Pointer to the array of results.
 *   <li>{@code title} (default {@code /title}), {@code link} (default {@code /link}) and {@code
 *       description} (default {@code /snippet}): JSON Pointers into each result. Results without a
 *       title are left out; a link that is not an http(s) URL is dropped.
 *   <li>{@code timeout} (default 5000): milliseconds for connecting and for the whole request.
 * </ul>
 *
 * <p>The defaults fit Google's Programmable Search JSON API.
 */
public final class RemoteJsonSearchSettings {

    public static final String PREFIX = "org.apereo.portal.search.remote.";

    /** The placeholder for the URL-encoded query in {@code url}. */
    public static final String QUERY_PLACEHOLDER = "{query}";

    private final String name;
    private final String label;
    private final String urlTemplate;
    private final Map<String, String> headers;
    private final String resultsPointer;
    private final String titlePointer;
    private final String linkPointer;
    private final String descriptionPointer;
    private final Duration timeout;

    public RemoteJsonSearchSettings(
            String name,
            String label,
            String urlTemplate,
            Map<String, String> headers,
            String resultsPointer,
            String titlePointer,
            String linkPointer,
            String descriptionPointer,
            Duration timeout) {
        if (!name.matches("[a-zA-Z][a-zA-Z0-9_-]*")) {
            throw new IllegalArgumentException(
                    "Remote search provider name '"
                            + name
                            + "' is not a letter and letters, digits, - or _");
        }
        if (!urlTemplate.startsWith("https://") && !urlTemplate.startsWith("http://")) {
            throw new IllegalArgumentException(
                    "Remote search provider " + name + ": url is not an http(s) URL");
        }
        if (!urlTemplate.contains(QUERY_PLACEHOLDER)) {
            throw new IllegalArgumentException(
                    "Remote search provider " + name + ": url has no " + QUERY_PLACEHOLDER);
        }
        try {
            URI.create(urlTemplate.replace(QUERY_PLACEHOLDER, "q"));
        } catch (IllegalArgumentException e) {
            // Without the URL: it can hold an API key
            throw new IllegalArgumentException(
                    "Remote search provider " + name + ": url is not a valid URL");
        }
        for (String pointer :
                new String[] {resultsPointer, titlePointer, linkPointer, descriptionPointer}) {
            try {
                JsonPointer.compile(pointer);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Remote search provider "
                                + name
                                + ": '"
                                + pointer
                                + "' is not a JSON Pointer such as /items",
                        e);
            }
        }
        this.name = name;
        this.label = label;
        this.urlTemplate = urlTemplate;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.resultsPointer = resultsPointer;
        this.titlePointer = titlePointer;
        this.linkPointer = linkPointer;
        this.descriptionPointer = descriptionPointer;
        this.timeout = timeout;
    }

    /** Reads the provider's settings; throws IllegalArgumentException when they are unusable. */
    public static RemoteJsonSearchSettings fromProperties(
            PropertyResolver properties, String name) {
        final String prefix = PREFIX + name + ".";
        final String url = properties.getProperty(prefix + "url");
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Remote search provider " + name + ": set " + prefix + "url");
        }
        final String timeout = properties.getProperty(prefix + "timeout", "5000").trim();
        final long millis;
        try {
            millis = Long.parseLong(timeout);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Remote search provider " + name + ": timeout is not a number of milliseconds",
                    e);
        }
        if (millis <= 0) {
            throw new IllegalArgumentException(
                    "Remote search provider " + name + ": timeout must be positive");
        }
        return new RemoteJsonSearchSettings(
                name,
                properties.getProperty(prefix + "label", name).trim(),
                url.trim(),
                parseHeaders(name, properties.getProperty(prefix + "headers")),
                properties.getProperty(prefix + "results", "/items").trim(),
                properties.getProperty(prefix + "title", "/title").trim(),
                properties.getProperty(prefix + "link", "/link").trim(),
                properties.getProperty(prefix + "description", "/snippet").trim(),
                Duration.ofMillis(millis));
    }

    /** Parses {@code Name: value; Name: value}. */
    static Map<String, String> parseHeaders(String name, @Nullable String value) {
        final Map<String, String> headers = new LinkedHashMap<>();
        if (value == null) {
            return headers;
        }
        for (String header : value.split(";")) {
            if (header.trim().isEmpty()) {
                continue;
            }
            final int colon = header.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException(
                        "Remote search provider "
                                + name
                                + ": header '"
                                + header.trim()
                                + "' is not 'Name: value'");
            }
            headers.put(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
        }
        return headers;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getResultsPointer() {
        return resultsPointer;
    }

    public String getTitlePointer() {
        return titlePointer;
    }

    public String getLinkPointer() {
        return linkPointer;
    }

    public String getDescriptionPointer() {
        return descriptionPointer;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
