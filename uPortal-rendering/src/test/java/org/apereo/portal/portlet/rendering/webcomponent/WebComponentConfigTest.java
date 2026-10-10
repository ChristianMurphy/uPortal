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
package org.apereo.portal.portlet.rendering.webcomponent;

import static org.apereo.portal.portlet.rendering.webcomponent.WebComponentTestSupport.definition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDescriptorKey;
import org.junit.Test;

public class WebComponentConfigTest {

    private static Map<String, String> parameters(String tagName, String moduleUrl) {
        final Map<String, String> result = new HashMap<>();
        result.put(WebComponentChannel.TAG_NAME_PARAMETER, tagName);
        result.put(WebComponentChannel.MODULE_URL_PARAMETER, moduleUrl);
        return result;
    }

    private static WebComponentConfig parse(Map<String, String> parameters) {
        return WebComponentConfig.fromDefinition(definition("demo", parameters));
    }

    private static String invalid(Map<String, String> parameters) {
        return assertThrows(IllegalArgumentException.class, () -> parse(parameters)).getMessage();
    }

    @Test
    public void readsTheTagNameAndModule() {
        final WebComponentConfig config = parse(parameters(" up-favorites ", "up-favorites"));

        assertEquals("demo", config.getFname());
        assertEquals("up-favorites", config.getTagName());
        assertEquals("up-favorites", config.getModuleUrl());
        assertTrue(config.getAttributes().isEmpty());
        assertTrue(config.getPreferenceAttributes().isEmpty());
    }

    @Test
    public void requiresTheTagName() {
        final Map<String, String> parameters = parameters("  ", "up-favorites");

        final String message = invalid(parameters);

        assertTrue(message, message.contains("'demo'"));
        assertTrue(message, message.contains(WebComponentChannel.TAG_NAME_PARAMETER));
        assertTrue(message, message.contains("required"));
    }

    @Test
    public void requiresTheModule() {
        final Map<String, String> parameters = parameters("up-demo", null);
        parameters.remove(WebComponentChannel.MODULE_URL_PARAMETER);

        assertTrue(invalid(parameters).contains(WebComponentChannel.MODULE_URL_PARAMETER));
    }

    @Test
    public void acceptsValidCustomElementNames() {
        for (final String name : new String[] {"up-a", "x-1", "my.app-widget_2", "a-b-c-"}) {
            assertEquals(name, parse(parameters(name, "up-demo")).getTagName());
        }
    }

    @Test
    public void refusesInvalidCustomElementNames() {
        for (final String name :
                new String[] {
                    "favorites",
                    "Up-Favorites",
                    "1-up",
                    "-up",
                    "up favorites",
                    "up-<b>",
                    "font-face",
                    "annotation-xml"
                }) {
            final String message = invalid(parameters(name, "up-demo"));
            assertTrue(message, message.contains("not a valid custom element name"));
        }
    }

    @Test
    public void acceptsBundledNamesUrlsAndPaths() {
        for (final String url :
                new String[] {
                    "up-favorites",
                    "https://cdn.example.edu/widgets/clock.js",
                    "http://localhost:8080/widget.js",
                    "/resource-server/widgets/clock.js"
                }) {
            assertEquals(url, parse(parameters("up-demo", url)).getModuleUrl());
        }
    }

    @Test
    public void refusesOtherModuleUrls() {
        for (final String url :
                new String[] {
                    "javascript:alert(1)",
                    "//cdn.example.edu/widget.js",
                    "widgets/clock.js",
                    "Up-Favorites",
                    "/widget.js\" onload=\"alert(1)",
                    "https://example.edu/a b.js",
                    "data:text/javascript,alert(1)"
                }) {
            final String message = invalid(parameters("up-demo", url));
            assertTrue(message, message.contains(WebComponentChannel.MODULE_URL_PARAMETER));
        }
    }

    @Test
    public void readsStaticAttributesInOrder() {
        final Map<String, String> parameters = parameters("up-demo", "up-demo");
        parameters.put(
                WebComponentChannel.ATTRIBUTES_PARAMETER,
                "# shown items\r\nmax-items = 10\n\n  label=a=b \nempty=\naria-label=Clock");

        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("max-items", "10");
        expected.put("label", "a=b");
        expected.put("empty", "");
        expected.put("aria-label", "Clock");
        assertEquals(expected, parse(parameters).getAttributes());
        assertEquals(
                "[max-items, label, empty, aria-label]",
                parse(parameters).getAttributes().keySet().toString());
    }

    @Test
    public void refusesInvalidStaticAttributes() {
        final String[][] cases = {
            {"max-items", "is not name=value"},
            {"Max-Items=1", "is not a valid attribute name"},
            {"=1", "is not a valid attribute name"},
            {"onclick=alert(1)", "event handler attribute 'onclick' is refused"},
            {"locale=fr", "set by the portal"},
            {"csrf-token=x", "set by the portal"},
            {"a-b=1\na-b=2", "is set twice"}
        };
        for (final String[] testCase : cases) {
            final Map<String, String> parameters = parameters("up-demo", "up-demo");
            parameters.put(WebComponentChannel.ATTRIBUTES_PARAMETER, testCase[0]);

            final String message = invalid(parameters);

            assertTrue(message, message.contains(testCase[1]));
            assertTrue(message, message.contains(WebComponentChannel.ATTRIBUTES_PARAMETER));
        }
    }

    @Test
    public void readsPreferenceAttributes() {
        final Map<String, String> parameters = parameters("up-demo", "up-demo");
        parameters.put(
                WebComponentChannel.PREFERENCE_ATTRIBUTES_PARAMETER,
                "max-height-pixels=FavoritesController.maxHeightPixels\nitems=items");

        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("max-height-pixels", "FavoritesController.maxHeightPixels");
        expected.put("items", "items");
        assertEquals(expected, parse(parameters).getPreferenceAttributes());
    }

    @Test
    public void refusesPreferenceAttributesWithoutAPreference() {
        final Map<String, String> parameters = parameters("up-demo", "up-demo");
        parameters.put(WebComponentChannel.PREFERENCE_ATTRIBUTES_PARAMETER, "items=");

        assertTrue(invalid(parameters).contains("names no preference"));
    }

    @Test
    public void refusesAnAttributeThatIsBothStaticAndAPreference() {
        final Map<String, String> parameters = parameters("up-demo", "up-demo");
        parameters.put(WebComponentChannel.ATTRIBUTES_PARAMETER, "items=3");
        parameters.put(WebComponentChannel.PREFERENCE_ATTRIBUTES_PARAMETER, "items=items");

        assertTrue(invalid(parameters).contains("is also set statically"));
    }

    @Test
    public void ignoresBlankOptionalParameters() {
        final Map<String, String> parameters = parameters("up-demo", "up-demo");
        parameters.put(WebComponentChannel.ATTRIBUTES_PARAMETER, " \n ");

        assertTrue(parse(parameters).getAttributes().isEmpty());
    }

    @Test
    public void recognisesWebComponentDefinitions() {
        assertTrue(WebComponentChannel.isWebComponent(definition("demo", new HashMap<>())));

        final IPortletDefinition otherPortlet = mock(IPortletDefinition.class);
        final IPortletDescriptorKey otherKey = mock(IPortletDescriptorKey.class);
        when(otherKey.isFrameworkPortlet()).thenReturn(true);
        when(otherKey.getPortletName()).thenReturn("Favorites");
        when(otherPortlet.getPortletDescriptorKey()).thenReturn(otherKey);
        assertFalse(WebComponentChannel.isWebComponent(otherPortlet));

        final IPortletDefinition externalPortlet = mock(IPortletDefinition.class);
        final IPortletDescriptorKey externalKey = mock(IPortletDescriptorKey.class);
        when(externalKey.getWebAppName()).thenReturn("/WebComponent");
        when(externalKey.getPortletName()).thenReturn(WebComponentChannel.PORTLET_NAME);
        when(externalPortlet.getPortletDescriptorKey()).thenReturn(externalKey);
        assertFalse(WebComponentChannel.isWebComponent(externalPortlet));

        assertFalse(WebComponentChannel.isWebComponent(mock(IPortletDefinition.class)));
    }
}
