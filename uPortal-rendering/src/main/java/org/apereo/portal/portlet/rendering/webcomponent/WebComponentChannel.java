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

import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDescriptorKey;

/**
 * Names shared by the web-component channel type.
 *
 * <p>A web-component channel is an ordinary portlet definition whose descriptor is the framework
 * portlet {@value #PORTLET_NAME}. The portal renders such a channel itself, as a custom element
 * plus its ES module script, instead of asking the portlet container to render it. The definition
 * parameters below configure the element.
 */
public final class WebComponentChannel {

    /** The framework portlet (in {@code WEB-INF/portlet.xml}) that web-component channels use. */
    public static final String PORTLET_NAME = "WebComponent";

    /** Required. The custom element to render, for example {@code up-favorites}. */
    public static final String TAG_NAME_PARAMETER = "webComponent.tagName";

    /**
     * Required. The ES module that defines the element: the name of a module bundled with uPortal
     * (for example {@code up-favorites}), an absolute {@code http(s)} URL, or a path that starts
     * with {@code /}.
     */
    public static final String MODULE_URL_PARAMETER = "webComponent.moduleUrl";

    /** Optional. Static attributes, one {@code attribute-name=value} per line. */
    public static final String ATTRIBUTES_PARAMETER = "webComponent.attributes";

    /**
     * Optional. Portlet preferences passed as attributes, one {@code attribute-name=preferenceName}
     * per line. The user's value wins over the value set on the definition.
     */
    public static final String PREFERENCE_ATTRIBUTES_PARAMETER =
            "webComponent.preferenceAttributes";

    /** Context attribute: the user's locale as a BCP 47 language tag. */
    public static final String LOCALE_ATTRIBUTE = "locale";

    /** Context attribute: the window state, for example {@code normal} or {@code maximized}. */
    public static final String WINDOW_STATE_ATTRIBUTE = "window-state";

    /** Context attribute: the portal's context path, the base for its REST APIs. */
    public static final String CONTEXT_PATH_ATTRIBUTE = "context-path";

    /** Context attribute: the channel's functional name. */
    public static final String FNAME_ATTRIBUTE = "fname";

    /** Context attribute: the HTTP header that carries the CSRF token. */
    public static final String CSRF_HEADER_ATTRIBUTE = "csrf-header";

    /** Context attribute: the CSRF token for unsafe (state-changing) requests. */
    public static final String CSRF_TOKEN_ATTRIBUTE = "csrf-token";

    private WebComponentChannel() {}

    /** True when the definition is a web-component channel. */
    public static boolean isWebComponent(IPortletDefinition portletDefinition) {
        final IPortletDescriptorKey descriptorKey = portletDefinition.getPortletDescriptorKey();
        return descriptorKey != null
                && descriptorKey.isFrameworkPortlet()
                && PORTLET_NAME.equals(descriptorKey.getPortletName());
    }
}
