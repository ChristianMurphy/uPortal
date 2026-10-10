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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionParameter;

/**
 * The publishing parameters of one web-component channel, parsed and validated.
 *
 * <p>Invalid parameters raise an {@link IllegalArgumentException} that names the channel, so the
 * portal shows its usual channel error instead of emitting broken or unsafe markup.
 */
public final class WebComponentConfig {

    /**
     * Valid custom element names, restricted to ASCII: a lowercase letter, then lowercase letters,
     * digits, {@code .}, {@code _} or {@code -}, with at least one {@code -}.
     */
    private static final Pattern TAG_NAME = Pattern.compile("[a-z][a-z0-9._]*(-[a-z0-9._]*)+");

    /** Names the HTML specification reserves, so they cannot be custom elements. */
    private static final Set<String> RESERVED_TAG_NAMES =
            Set.of(
                    "annotation-xml",
                    "color-profile",
                    "font-face",
                    "font-face-src",
                    "font-face-uri",
                    "font-face-format",
                    "font-face-name",
                    "missing-glyph");

    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[a-z][a-z0-9-]*");

    /** A module bundled with uPortal, looked up in the build manifest. */
    static final Pattern BUNDLED_MODULE_NAME = Pattern.compile("[a-z][a-z0-9-]*");

    /** An absolute http(s) URL, or a path that starts with a single {@code /}. */
    private static final Pattern MODULE_URL =
            Pattern.compile("(https?://[^\\s\"'<>]+|/[^/\\s\"'<>][^\\s\"'<>]*)");

    /** Attributes the portal sets on every element; a channel cannot set them itself. */
    static final Set<String> CONTEXT_ATTRIBUTES =
            Set.of(
                    WebComponentChannel.LOCALE_ATTRIBUTE,
                    WebComponentChannel.WINDOW_STATE_ATTRIBUTE,
                    WebComponentChannel.CONTEXT_PATH_ATTRIBUTE,
                    WebComponentChannel.FNAME_ATTRIBUTE,
                    WebComponentChannel.CSRF_HEADER_ATTRIBUTE,
                    WebComponentChannel.CSRF_TOKEN_ATTRIBUTE);

    private final String fname;
    private final String tagName;
    private final String moduleUrl;
    private final Map<String, String> attributes;
    private final Map<String, String> preferenceAttributes;

    private WebComponentConfig(
            String fname,
            String tagName,
            String moduleUrl,
            Map<String, String> attributes,
            Map<String, String> preferenceAttributes) {
        this.fname = fname;
        this.tagName = tagName;
        this.moduleUrl = moduleUrl;
        this.attributes = Collections.unmodifiableMap(attributes);
        this.preferenceAttributes = Collections.unmodifiableMap(preferenceAttributes);
    }

    /**
     * Reads and validates the web-component parameters of a portlet definition.
     *
     * @throws IllegalArgumentException if a parameter is missing or invalid
     */
    public static WebComponentConfig fromDefinition(IPortletDefinition portletDefinition) {
        final String fname = portletDefinition.getFName();

        final String tagName =
                required(portletDefinition, fname, WebComponentChannel.TAG_NAME_PARAMETER);
        if (!TAG_NAME.matcher(tagName).matches() || RESERVED_TAG_NAMES.contains(tagName)) {
            throw invalid(
                    fname,
                    WebComponentChannel.TAG_NAME_PARAMETER,
                    "'" + tagName + "' is not a valid custom element name");
        }

        final String moduleUrl =
                required(portletDefinition, fname, WebComponentChannel.MODULE_URL_PARAMETER);
        if (!BUNDLED_MODULE_NAME.matcher(moduleUrl).matches()
                && !MODULE_URL.matcher(moduleUrl).matches()) {
            throw invalid(
                    fname,
                    WebComponentChannel.MODULE_URL_PARAMETER,
                    "'"
                            + moduleUrl
                            + "' is neither a bundled module name, an http(s) URL nor a path"
                            + " starting with /");
        }

        final Map<String, String> attributes =
                parsePairs(
                        fname,
                        WebComponentChannel.ATTRIBUTES_PARAMETER,
                        optional(portletDefinition, WebComponentChannel.ATTRIBUTES_PARAMETER),
                        false);
        final Map<String, String> preferenceAttributes =
                parsePairs(
                        fname,
                        WebComponentChannel.PREFERENCE_ATTRIBUTES_PARAMETER,
                        optional(
                                portletDefinition,
                                WebComponentChannel.PREFERENCE_ATTRIBUTES_PARAMETER),
                        true);
        for (final String name : preferenceAttributes.keySet()) {
            if (attributes.containsKey(name)) {
                throw invalid(
                        fname,
                        WebComponentChannel.PREFERENCE_ATTRIBUTES_PARAMETER,
                        "attribute '" + name + "' is also set statically");
            }
        }

        return new WebComponentConfig(fname, tagName, moduleUrl, attributes, preferenceAttributes);
    }

    public String getFname() {
        return fname;
    }

    public String getTagName() {
        return tagName;
    }

    /** The module as configured: a bundled module name, a URL or a path. */
    public String getModuleUrl() {
        return moduleUrl;
    }

    /** Static attribute values, by attribute name, in the configured order. */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /** Preference names, by the attribute name that receives the preference's value. */
    public Map<String, String> getPreferenceAttributes() {
        return preferenceAttributes;
    }

    private static String required(
            IPortletDefinition portletDefinition, String fname, String parameterName) {
        final String value = optional(portletDefinition, parameterName);
        if (value == null) {
            throw invalid(fname, parameterName, "the parameter is required");
        }
        return value;
    }

    private static String optional(IPortletDefinition portletDefinition, String parameterName) {
        final IPortletDefinitionParameter parameter = portletDefinition.getParameter(parameterName);
        if (parameter == null || parameter.getValue() == null) {
            return null;
        }
        final String value = parameter.getValue().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Parses one {@code attribute-name=value} pair per line. Blank lines and lines that start with
     * {@code #} are ignored; the value is everything after the first {@code =}.
     */
    private static Map<String, String> parsePairs(
            String fname, String parameterName, String text, boolean valueRequired) {
        final Map<String, String> result = new LinkedHashMap<>();
        if (text == null) {
            return result;
        }
        for (final String rawLine : text.split("\\r?\\n")) {
            final String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            final int separator = line.indexOf('=');
            if (separator < 0) {
                throw invalid(fname, parameterName, "'" + line + "' is not name=value");
            }
            final String name = line.substring(0, separator).trim();
            final String value = line.substring(separator + 1).trim();
            if (!ATTRIBUTE_NAME.matcher(name).matches()) {
                throw invalid(fname, parameterName, "'" + name + "' is not a valid attribute name");
            }
            if (name.startsWith("on")) {
                throw invalid(
                        fname, parameterName, "event handler attribute '" + name + "' is refused");
            }
            if (CONTEXT_ATTRIBUTES.contains(name)) {
                throw invalid(
                        fname,
                        parameterName,
                        "attribute '" + name + "' is set by the portal and cannot be configured");
            }
            if (valueRequired && value.isEmpty()) {
                throw invalid(fname, parameterName, "attribute '" + name + "' names no preference");
            }
            if (result.put(name, value) != null) {
                throw invalid(fname, parameterName, "attribute '" + name + "' is set twice");
            }
        }
        return result;
    }

    private static IllegalArgumentException invalid(
            String fname, String parameterName, String reason) {
        return new IllegalArgumentException(
                "Web-component channel '"
                        + fname
                        + "' has an invalid "
                        + parameterName
                        + " parameter: "
                        + reason);
    }
}
