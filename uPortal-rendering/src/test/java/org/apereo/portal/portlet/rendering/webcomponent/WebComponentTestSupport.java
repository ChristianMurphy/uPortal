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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.ServletContext;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionParameter;
import org.apereo.portal.portlet.om.IPortletDescriptorKey;
import org.apereo.portal.portlet.om.IPortletPreference;

/** Builders for the mocks the web-component tests share. */
final class WebComponentTestSupport {

    /** A Vite manifest with one bundled entry, {@code up-favorites}, and its shared chunk. */
    static final String MANIFEST =
            "{\"src/components/up-favorites/up-favorites.ts\":"
                    + "{\"file\":\"up-favorites-Ab12Cd34.js\",\"name\":\"up-favorites\","
                    + "\"src\":\"src/components/up-favorites/up-favorites.ts\",\"isEntry\":true,"
                    + "\"imports\":[\"_up-element-Ef56.js\"]},"
                    + "\"_up-element-Ef56.js\":{\"file\":\"up-element-Ef56.js\",\"name\":\"up-element\"}}";

    private WebComponentTestSupport() {}

    /** A web-component definition with the given parameters. */
    static IPortletDefinition definition(String fname, Map<String, String> parameters) {
        final IPortletDefinition definition = mock(IPortletDefinition.class);
        when(definition.getFName()).thenReturn(fname);
        when(definition.getTitle()).thenReturn("Title of " + fname);
        final IPortletDescriptorKey descriptorKey = mock(IPortletDescriptorKey.class);
        when(descriptorKey.isFrameworkPortlet()).thenReturn(true);
        when(descriptorKey.getPortletName()).thenReturn(WebComponentChannel.PORTLET_NAME);
        when(definition.getPortletDescriptorKey()).thenReturn(descriptorKey);
        for (final Map.Entry<String, String> entry : parameters.entrySet()) {
            final IPortletDefinitionParameter parameter = mock(IPortletDefinitionParameter.class);
            when(parameter.getName()).thenReturn(entry.getKey());
            when(parameter.getValue()).thenReturn(entry.getValue());
            when(definition.getParameter(entry.getKey())).thenReturn(parameter);
        }
        return definition;
    }

    static IPortletPreference preference(String name, String... values) {
        final IPortletPreference preference = mock(IPortletPreference.class);
        when(preference.getName()).thenReturn(name);
        when(preference.getValues()).thenReturn(values);
        return preference;
    }

    /** A servlet context that serves the given manifest, or none when it is null. */
    static ServletContext servletContext(String manifest) {
        final ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getResourceAsStream(WebComponentModuleResolver.MANIFEST_PATH))
                .thenAnswer(
                        invocation ->
                                manifest == null
                                        ? null
                                        : new ByteArrayInputStream(
                                                manifest.getBytes(StandardCharsets.UTF_8)));
        return servletContext;
    }

    static WebComponentModuleResolver moduleResolver(String manifest) {
        final WebComponentModuleResolver resolver = new WebComponentModuleResolver();
        resolver.setServletContext(servletContext(manifest));
        return resolver;
    }
}
