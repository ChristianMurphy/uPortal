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
import static org.apereo.portal.portlet.rendering.webcomponent.WebComponentTestSupport.definition;
import static org.apereo.portal.portlet.rendering.webcomponent.WebComponentTestSupport.moduleResolver;
import static org.apereo.portal.portlet.rendering.webcomponent.WebComponentTestSupport.preference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.portlet.WindowState;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletEntity;
import org.apereo.portal.portlet.om.IPortletPreference;
import org.apereo.portal.portlet.om.IPortletWindow;
import org.apereo.portal.portlet.rendering.IPortletRenderer;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

public class WebComponentChannelRendererTest {

    private static final String SCRIPT =
            "<script type=\"module\" src=\"/uPortal/webcomponents/up-favorites-Ab12Cd34.js\">"
                    + "</script>";

    private WebComponentChannelRenderer renderer;
    private MockHttpServletRequest request;
    private LocaleManager localeManager;

    @Before
    public void setUp() {
        localeManager = mock(LocaleManager.class);
        when(localeManager.getLocales())
                .thenReturn(Collections.singletonList(Locale.CANADA_FRENCH));
        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstance.getLocaleManager()).thenReturn(localeManager);
        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);

        request = new MockHttpServletRequest();
        request.setContextPath("/uPortal");
        when(userInstanceManager.getUserInstance(request)).thenReturn(userInstance);

        renderer = new WebComponentChannelRenderer();
        renderer.setUserInstanceManager(userInstanceManager);
        renderer.setModuleResolver(moduleResolver(MANIFEST));
    }

    private static Map<String, String> favoritesParameters() {
        final Map<String, String> parameters = new HashMap<>();
        parameters.put(WebComponentChannel.TAG_NAME_PARAMETER, "up-favorites");
        parameters.put(WebComponentChannel.MODULE_URL_PARAMETER, "up-favorites");
        return parameters;
    }

    private static IPortletWindow window(
            IPortletDefinition definition,
            WindowState windowState,
            List<IPortletPreference> entityPreferences) {
        final IPortletEntity entity = mock(IPortletEntity.class);
        when(entity.getPortletDefinition()).thenReturn(definition);
        when(entity.getPortletPreferences()).thenReturn(entityPreferences);
        final IPortletWindow window = mock(IPortletWindow.class);
        when(window.getPortletEntity()).thenReturn(entity);
        when(window.getWindowState()).thenReturn(windowState);
        return window;
    }

    private static IPortletWindow window(Map<String, String> parameters, WindowState state) {
        return window(definition("favorites", parameters), state, Collections.emptyList());
    }

    @Test
    public void rendersTheModuleScriptAndTheElementWithItsContext() {
        request.setAttribute(
                CsrfToken.class.getName(),
                new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-123"));

        final String html =
                renderer.render(request, window(favoritesParameters(), WindowState.NORMAL));

        assertEquals(
                SCRIPT
                        + "<up-favorites locale=\"fr-CA\" window-state=\"normal\""
                        + " context-path=\"/uPortal\" fname=\"favorites\""
                        + " csrf-header=\"X-CSRF-TOKEN\" csrf-token=\"token-123\"></up-favorites>",
                html);
    }

    @Test
    public void omitsTheCsrfAttributesWithoutAToken() {
        final String html =
                renderer.render(request, window(favoritesParameters(), WindowState.NORMAL));

        assertFalse(html, html.contains("csrf"));
    }

    @Test
    public void passesTheMaximizedWindowState() {
        final String html =
                renderer.render(request, window(favoritesParameters(), WindowState.MAXIMIZED));

        assertTrue(html, html.contains(" window-state=\"maximized\""));
    }

    @Test
    public void passesTheExclusiveWindowState() {
        final String html =
                renderer.render(request, window(favoritesParameters(), IPortletRenderer.EXCLUSIVE));

        assertTrue(html, html.contains(" window-state=\"exclusive\""));
    }

    @Test
    public void rendersNothingWhenMinimizedAndKeepsTheModuleForTheNextChannel() {
        final Map<String, String> parameters = favoritesParameters();

        assertEquals("", renderer.render(request, window(parameters, WindowState.MINIMIZED)));
        assertTrue(
                renderer.render(request, window(parameters, WindowState.NORMAL))
                        .startsWith(SCRIPT));
    }

    @Test
    public void fallsBackToTheRequestLocale() {
        when(localeManager.getLocales()).thenReturn(Collections.emptyList());
        request.addPreferredLocale(Locale.GERMANY);

        final String html =
                renderer.render(request, window(favoritesParameters(), WindowState.NORMAL));

        assertTrue(html, html.contains(" locale=\"de-DE\""));
    }

    @Test
    public void emitsEachModuleOncePerRequest() {
        final Map<String, String> parameters = favoritesParameters();
        final Map<String, String> clockParameters = new HashMap<>();
        clockParameters.put(WebComponentChannel.TAG_NAME_PARAMETER, "x-clock");
        clockParameters.put(
                WebComponentChannel.MODULE_URL_PARAMETER, "https://cdn.example.edu/clock.js");

        final String first = renderer.render(request, window(parameters, WindowState.NORMAL));
        final String second = renderer.render(request, window(parameters, WindowState.NORMAL));
        final String clock = renderer.render(request, window(clockParameters, WindowState.NORMAL));

        assertTrue(first, first.startsWith(SCRIPT));
        assertTrue(second, second.startsWith("<up-favorites "));
        assertTrue(
                clock,
                clock.startsWith(
                        "<script type=\"module\" src=\"https://cdn.example.edu/clock.js\"></script>"
                                + "<x-clock "));

        // A new request (the next page) loads the module again
        final MockHttpServletRequest nextRequest = new MockHttpServletRequest();
        nextRequest.setContextPath("/uPortal");
        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);
        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstanceManager.getUserInstance(nextRequest)).thenReturn(userInstance);
        renderer.setUserInstanceManager(userInstanceManager);
        assertTrue(
                renderer.render(nextRequest, window(parameters, WindowState.NORMAL))
                        .startsWith(SCRIPT));
    }

    @Test
    public void addsStaticAttributesAfterTheContext() {
        final Map<String, String> parameters = favoritesParameters();
        parameters.put(
                WebComponentChannel.ATTRIBUTES_PARAMETER, "max-items=10\nheading=Mes favoris");

        final String html = renderer.render(request, window(parameters, WindowState.NORMAL));

        assertTrue(
                html,
                html.endsWith(
                        " fname=\"favorites\" max-items=\"10\" heading=\"Mes favoris\"></up-favorites>"));
    }

    @Test
    public void escapesAttributeValues() {
        final Map<String, String> parameters = favoritesParameters();
        parameters.put(
                WebComponentChannel.ATTRIBUTES_PARAMETER, "label=\"><script>alert('x')</script>&é");

        final String html = renderer.render(request, window(parameters, WindowState.NORMAL));

        assertTrue(
                html,
                html.contains(
                        " label=\"&quot;&gt;&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;&amp;é\""));
    }

    @Test
    public void passesPreferencesWithTheUsersValueFirst() {
        final Map<String, String> parameters = favoritesParameters();
        parameters.put(
                WebComponentChannel.PREFERENCE_ATTRIBUTES_PARAMETER,
                "max-height-pixels=maxHeightPixels\ntopics=topics\ncolor=color\nmissing=missing\nnone=none");
        final IPortletDefinition definition = definition("favorites", parameters);
        final List<IPortletPreference> definitionPreferences =
                Arrays.asList(
                        preference("maxHeightPixels", "300"),
                        preference("topics", "a", "b \"c\""),
                        preference("color", "blue"),
                        preference("none"));
        when(definition.getPortletPreferences()).thenReturn(definitionPreferences);
        final List<IPortletPreference> userPreferences =
                Collections.singletonList(preference("color", "red"));

        final String html =
                renderer.render(request, window(definition, WindowState.NORMAL, userPreferences));

        assertTrue(
                html,
                html.endsWith(
                        " max-height-pixels=\"300\""
                                + " topics=\"[&quot;a&quot;,&quot;b \\&quot;c\\&quot;&quot;]\""
                                + " color=\"red\"></up-favorites>"));
    }

    @Test
    public void toleratesMissingPreferenceLists() {
        final Map<String, String> parameters = favoritesParameters();
        parameters.put(WebComponentChannel.PREFERENCE_ATTRIBUTES_PARAMETER, "color=color");

        final String html =
                renderer.render(
                        request,
                        window(definition("favorites", parameters), WindowState.NORMAL, null));

        assertFalse(html, html.contains("color="));
    }

    @Test
    public void refusesAnInvalidChannel() {
        final Map<String, String> parameters = favoritesParameters();
        parameters.put(WebComponentChannel.TAG_NAME_PARAMETER, "favorites");
        final IPortletWindow window = window(parameters, WindowState.NORMAL);

        assertThrows(IllegalArgumentException.class, () -> renderer.render(request, window));
    }

    @Test
    public void recognisesWebComponentWindows() {
        assertTrue(renderer.isWebComponent(window(favoritesParameters(), WindowState.NORMAL)));

        final IPortletDefinition portlet = mock(IPortletDefinition.class);
        assertFalse(
                renderer.isWebComponent(
                        window(portlet, WindowState.NORMAL, Collections.emptyList())));
    }
}
