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

import static org.apereo.portal.portlet.rendering.webcomponent.WebComponentTestSupport.MANIFEST;
import static org.apereo.portal.portlet.rendering.webcomponent.WebComponentTestSupport.servletContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import javax.servlet.ServletContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class WebComponentModuleResolverTest {

    private MockHttpServletRequest request;

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/uPortal");
    }

    private static WebComponentModuleResolver resolver(ServletContext servletContext) {
        final WebComponentModuleResolver resolver = new WebComponentModuleResolver();
        resolver.setServletContext(servletContext);
        return resolver;
    }

    @Test
    public void resolvesBundledModulesThroughTheManifest() {
        final ServletContext servletContext = servletContext(MANIFEST);
        final WebComponentModuleResolver resolver = resolver(servletContext);

        assertEquals(
                "/uPortal/webcomponents/up-favorites-Ab12Cd34.js",
                resolver.resolve(request, "up-favorites"));
        assertEquals(
                "/uPortal/webcomponents/up-favorites-Ab12Cd34.js",
                resolver.resolve(request, "up-favorites"));
        // The manifest is read once
        verify(servletContext, times(1))
                .getResourceAsStream(WebComponentModuleResolver.MANIFEST_PATH);
    }

    @Test
    public void returnsUrlsAndPathsUnchanged() {
        final ServletContext servletContext = servletContext(MANIFEST);
        final WebComponentModuleResolver resolver = resolver(servletContext);

        assertEquals(
                "https://cdn.example.edu/clock.js",
                resolver.resolve(request, "https://cdn.example.edu/clock.js"));
        assertEquals(
                "/resource-server/clock.js",
                resolver.resolve(request, "/resource-server/clock.js"));
        verify(servletContext, times(0))
                .getResourceAsStream(WebComponentModuleResolver.MANIFEST_PATH);
    }

    @Test
    public void refusesModulesThatAreNotEntries() {
        final WebComponentModuleResolver resolver = resolver(servletContext(MANIFEST));

        final String message =
                assertThrows(
                                IllegalArgumentException.class,
                                () -> resolver.resolve(request, "up-element"))
                        .getMessage();

        assertTrue(message, message.contains("'up-element'"));
    }

    @Test
    public void refusesBundledModulesWhenTheManifestIsMissing() {
        final WebComponentModuleResolver resolver = resolver(servletContext(null));

        assertThrows(
                IllegalArgumentException.class, () -> resolver.resolve(request, "up-favorites"));
    }

    @Test
    public void reportsAMalformedManifest() {
        final WebComponentModuleResolver resolver = resolver(servletContext("{not json"));

        final IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> resolver.resolve(request, "up-favorites"));

        assertTrue(
                e.getMessage(), e.getMessage().contains(WebComponentModuleResolver.MANIFEST_PATH));
    }

    @Test
    public void ignoresEntriesWithoutANameOrFile() {
        final WebComponentModuleResolver resolver =
                resolver(
                        servletContext(
                                "{\"a.ts\":{\"file\":\"a.js\",\"isEntry\":true},"
                                        + "\"b.ts\":{\"name\":\"up-b\",\"isEntry\":true}}"));

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request, "up-b"));
    }
}
