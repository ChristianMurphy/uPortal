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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

public class RemoteJsonSearchSettingsTest {

    private static final String P = "org.apereo.portal.search.remote.";

    private static MockEnvironment google() {
        return new MockEnvironment()
                .withProperty(P + "providers", "google")
                .withProperty(
                        P + "google.url",
                        "https://www.googleapis.com/customsearch/v1?key=K&cx=C&q={query}");
    }

    @Test
    public void defaultsFitGoogleProgrammableSearch() {
        final RemoteJsonSearchSettings settings =
                RemoteJsonSearchSettings.fromProperties(google(), "google");

        assertEquals("google", settings.getName());
        assertEquals("google", settings.getLabel());
        assertEquals("/items", settings.getResultsPointer());
        assertEquals("/title", settings.getTitlePointer());
        assertEquals("/link", settings.getLinkPointer());
        assertEquals("/snippet", settings.getDescriptionPointer());
        assertEquals(Duration.ofSeconds(5), settings.getTimeout());
        assertTrue(settings.getHeaders().isEmpty());
    }

    @Test
    public void readsEverySetting() {
        final MockEnvironment properties =
                new MockEnvironment()
                        .withProperty(P + "dir.url", " https://dir.example.edu/api?q={query} ")
                        .withProperty(P + "dir.label", "Directory")
                        .withProperty(P + "dir.headers", "X-Api-Key: abc; Accept-Language: en;")
                        .withProperty(P + "dir.results", "/data/hits")
                        .withProperty(P + "dir.title", "/name")
                        .withProperty(P + "dir.link", "/profile")
                        .withProperty(P + "dir.description", "/dept")
                        .withProperty(P + "dir.timeout", "1500");

        final RemoteJsonSearchSettings settings =
                RemoteJsonSearchSettings.fromProperties(properties, "dir");

        assertEquals("Directory", settings.getLabel());
        assertEquals("https://dir.example.edu/api?q={query}", settings.getUrlTemplate());
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Api-Key", "abc");
        headers.put("Accept-Language", "en");
        assertEquals(headers, settings.getHeaders());
        assertEquals("/data/hits", settings.getResultsPointer());
        assertEquals("/name", settings.getTitlePointer());
        assertEquals("/profile", settings.getLinkPointer());
        assertEquals("/dept", settings.getDescriptionPointer());
        assertEquals(Duration.ofMillis(1500), settings.getTimeout());
    }

    @Test
    public void refusesUnusableSettings() {
        expectRefused(new MockEnvironment(), "set org.apereo.portal.search.remote.google.url");
        expectRefused(google().withProperty(P + "google.url", "ftp://x/{query}"), "http(s)");
        expectRefused(google().withProperty(P + "google.url", "https://x/search"), "{query}");
        expectRefused(google().withProperty(P + "google.timeout", "soon"), "milliseconds");
        expectRefused(google().withProperty(P + "google.timeout", "0"), "positive");
        expectRefused(google().withProperty(P + "google.headers", "X-Api-Key abc"), "Name: value");
        expectRefused(google().withProperty(P + "google.results", "items"), "JSON Pointer");
    }

    @Test
    public void refusesNamesThatAreNotIdentifiers() {
        try {
            new RemoteJsonSearchSettings(
                    "web search",
                    "x",
                    "https://x/{query}",
                    Collections.emptyMap(),
                    "/items",
                    "/title",
                    "/link",
                    "/snippet",
                    Duration.ofSeconds(1));
            fail("Accepted a name with a space");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("web search"));
        }
    }

    @Test
    public void listsTheConfiguredProviders() {
        final MockEnvironment properties =
                google().withProperty(P + "providers", " google , dir ,")
                        .withProperty(P + "dir.url", "https://dir.example.edu/?q={query}");

        final RemoteJsonSearchProviders providers = new RemoteJsonSearchProviders(properties);

        assertEquals(2, providers.getStrategies().size());
        assertEquals("google", providers.getStrategies().get(0).getResultTypeName());
        assertEquals("dir", providers.getStrategies().get(1).getResultTypeName());
        assertTrue(new RemoteJsonSearchProviders(new MockEnvironment()).getStrategies().isEmpty());
    }

    private static void expectRefused(MockEnvironment properties, String message) {
        try {
            RemoteJsonSearchSettings.fromProperties(properties, "google");
            fail("Accepted settings without " + message);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(message));
        }
    }
}
