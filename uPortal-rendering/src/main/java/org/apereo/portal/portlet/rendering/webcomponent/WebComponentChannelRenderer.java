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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.portlet.WindowState;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletEntity;
import org.apereo.portal.portlet.om.IPortletPreference;
import org.apereo.portal.portlet.om.IPortletWindow;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders a web-component channel: the custom element with its attributes, preceded by the
 * element's ES module script.
 *
 * <p>The element gets the attributes configured on the channel plus these context attributes:
 *
 * <ul>
 *   <li>{@code locale}: the user's locale as a BCP 47 language tag
 *   <li>{@code window-state}: {@code normal}, {@code maximized}, {@code detached} or {@code
 *       exclusive}
 *   <li>{@code context-path}: the portal's context path, the base for its REST APIs
 *   <li>{@code fname}: the channel's functional name
 *   <li>{@code csrf-header} and {@code csrf-token}: what to send with unsafe requests to the
 *       portal's REST APIs, which the element calls with the user's session cookie
 * </ul>
 *
 * <p>A module script is emitted only for the first channel that needs it in a request, so a module
 * loads once per page even when its channel is placed several times. Channels are rendered in page
 * order (see {@link WebComponentRenderExecutionWorker}), so that first channel is the first on the
 * page. A minimized channel renders nothing, like a portlet that honours the minimized window
 * state.
 */
@Component
public class WebComponentChannelRenderer {

    /** Request attribute holding the module URLs already emitted in the current request. */
    static final String EMITTED_MODULES_ATTRIBUTE =
            WebComponentChannelRenderer.class.getName() + ".EMITTED_MODULES";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IUserInstanceManager userInstanceManager;
    private WebComponentModuleResolver moduleResolver;

    @Autowired
    public void setUserInstanceManager(IUserInstanceManager userInstanceManager) {
        this.userInstanceManager = userInstanceManager;
    }

    @Autowired
    public void setModuleResolver(WebComponentModuleResolver moduleResolver) {
        this.moduleResolver = moduleResolver;
    }

    /** True when the window shows a web-component channel. */
    public boolean isWebComponent(IPortletWindow portletWindow) {
        return WebComponentChannel.isWebComponent(
                portletWindow.getPortletEntity().getPortletDefinition());
    }

    /**
     * Renders the channel shown in the window.
     *
     * @throws IllegalArgumentException if the channel's parameters are invalid
     */
    public String render(HttpServletRequest request, IPortletWindow portletWindow) {
        final WindowState windowState = portletWindow.getWindowState();
        if (WindowState.MINIMIZED.equals(windowState)) {
            return "";
        }

        final IPortletEntity portletEntity = portletWindow.getPortletEntity();
        final IPortletDefinition portletDefinition = portletEntity.getPortletDefinition();
        final WebComponentConfig config = WebComponentConfig.fromDefinition(portletDefinition);
        final String moduleSrc = moduleResolver.resolve(request, config.getModuleUrl());

        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(WebComponentChannel.LOCALE_ATTRIBUTE, getLocale(request).toLanguageTag());
        attributes.put(WebComponentChannel.WINDOW_STATE_ATTRIBUTE, windowState.toString());
        attributes.put(WebComponentChannel.CONTEXT_PATH_ATTRIBUTE, request.getContextPath());
        attributes.put(WebComponentChannel.FNAME_ATTRIBUTE, config.getFname());
        final CsrfToken csrfToken = getCsrfToken(request);
        if (csrfToken != null) {
            attributes.put(WebComponentChannel.CSRF_HEADER_ATTRIBUTE, csrfToken.getHeaderName());
            attributes.put(WebComponentChannel.CSRF_TOKEN_ATTRIBUTE, csrfToken.getToken());
        }
        attributes.putAll(config.getAttributes());
        for (final Map.Entry<String, String> mapping :
                config.getPreferenceAttributes().entrySet()) {
            final String value = getPreferenceValue(portletEntity, mapping.getValue());
            if (value != null) {
                attributes.put(mapping.getKey(), value);
            }
        }

        final StringBuilder html = new StringBuilder();
        if (markModuleEmitted(request, moduleSrc)) {
            html.append("<script type=\"module\" src=\"").append(escape(moduleSrc)).append("\">");
            html.append("</script>");
        }
        html.append('<').append(config.getTagName());
        for (final Map.Entry<String, String> attribute : attributes.entrySet()) {
            html.append(' ').append(attribute.getKey());
            html.append("=\"").append(escape(attribute.getValue())).append('"');
        }
        html.append("></").append(config.getTagName()).append('>');
        return html.toString();
    }

    /** Records the module as emitted in this request; false if it already was. */
    @SuppressWarnings("unchecked")
    private boolean markModuleEmitted(HttpServletRequest request, String moduleSrc) {
        Set<String> emitted = (Set<String>) request.getAttribute(EMITTED_MODULES_ATTRIBUTE);
        if (emitted == null) {
            emitted = new HashSet<>();
            request.setAttribute(EMITTED_MODULES_ATTRIBUTE, emitted);
        }
        return emitted.add(moduleSrc);
    }

    /** The locale the theme renders the page in, else the request's. */
    private Locale getLocale(HttpServletRequest request) {
        final IUserInstance userInstance = userInstanceManager.getUserInstance(request);
        final LocaleManager localeManager = userInstance.getLocaleManager();
        final List<Locale> locales = localeManager != null ? localeManager.getLocales() : null;
        if (locales != null && !locales.isEmpty() && locales.get(0) != null) {
            return locales.get(0);
        }
        return request.getLocale();
    }

    /** Spring Security's token for this request (the same one portlet action URLs carry). */
    private CsrfToken getCsrfToken(HttpServletRequest request) {
        final Object token = request.getAttribute(CsrfToken.class.getName());
        return token instanceof CsrfToken ? (CsrfToken) token : null;
    }

    /**
     * The user's value of the preference, else the definition's. A single value is passed as is,
     * several values as a JSON array, and a preference without values not at all.
     */
    private String getPreferenceValue(IPortletEntity portletEntity, String preferenceName) {
        IPortletPreference preference =
                findPreference(portletEntity.getPortletPreferences(), preferenceName);
        if (preference == null) {
            preference =
                    findPreference(
                            portletEntity.getPortletDefinition().getPortletPreferences(),
                            preferenceName);
        }
        final String[] values = preference != null ? preference.getValues() : null;
        if (values == null || values.length == 0) {
            return null;
        }
        if (values.length == 1) {
            return values[0];
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to write preference " + preferenceName, e);
        }
    }

    private static IPortletPreference findPreference(
            List<IPortletPreference> preferences, String preferenceName) {
        if (preferences != null) {
            for (final IPortletPreference preference : preferences) {
                if (preferenceName.equals(preference.getName())) {
                    return preference;
                }
            }
        }
        return null;
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }
}
