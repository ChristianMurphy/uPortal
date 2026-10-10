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
package org.apereo.portal.portlet.rendering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import javax.portlet.WindowState;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import org.apereo.portal.events.IPortletExecutionEventFactory;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionParameter;
import org.apereo.portal.portlet.om.IPortletDescriptorKey;
import org.apereo.portal.portlet.om.IPortletEntity;
import org.apereo.portal.portlet.om.IPortletWindow;
import org.apereo.portal.portlet.om.IPortletWindowId;
import org.apereo.portal.portlet.om.PortletLifecycleState;
import org.apereo.portal.portlet.registry.IPortletWindowRegistry;
import org.apereo.portal.portlet.rendering.webcomponent.WebComponentChannel;
import org.apereo.portal.portlet.rendering.webcomponent.WebComponentChannelRenderer;
import org.apereo.portal.portlet.rendering.webcomponent.WebComponentModuleResolver;
import org.apereo.portal.portlet.rendering.worker.PortletWorkerFactoryImpl;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.apereo.portal.utils.personalize.IPersonalizer;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Drives web-component channels through the real execution manager and worker factory, the way the
 * rendering pipeline does: start every channel's render, then ask each one for its title and output
 * in page order.
 */
public class WebComponentChannelExecutionTest {

    private static final String SCRIPT =
            "<script type=\"module\" src=\"/uPortal/webcomponents/up-favorites-Ab12.js\"></script>";

    private PortletExecutionManager executionManager;
    private IPortletWindowRegistry windowRegistry;
    private IPortletRenderer portletRenderer;
    private IPortletExecutionEventFactory eventFactory;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/uPortal");
        response = new MockHttpServletResponse();
        response.setLocale(Locale.US);
        windowRegistry = mock(IPortletWindowRegistry.class);
        portletRenderer = mock(IPortletRenderer.class);
        eventFactory = mock(IPortletExecutionEventFactory.class);

        final LocaleManager localeManager = mock(LocaleManager.class);
        when(localeManager.getLocales()).thenReturn(Collections.singletonList(Locale.US));
        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstance.getLocaleManager()).thenReturn(localeManager);
        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);
        when(userInstanceManager.getUserInstance(request)).thenReturn(userInstance);

        final ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getResourceAsStream(WebComponentModuleResolver.MANIFEST_PATH))
                .thenAnswer(
                        invocation ->
                                new ByteArrayInputStream(
                                        ("{\"up-favorites.ts\":{\"file\":\"up-favorites-Ab12.js\","
                                                        + "\"name\":\"up-favorites\",\"isEntry\":true}}")
                                                .getBytes(StandardCharsets.UTF_8)));
        final WebComponentModuleResolver moduleResolver = new WebComponentModuleResolver();
        moduleResolver.setServletContext(servletContext);
        final WebComponentChannelRenderer channelRenderer =
                new WebComponentChannelRenderer(userInstanceManager, moduleResolver);

        final PortletWorkerFactoryImpl workerFactory = new PortletWorkerFactoryImpl();
        workerFactory.setPortletWindowRegistry(windowRegistry);
        workerFactory.setWebComponentChannelRenderer(channelRenderer);
        workerFactory.setPortletThreadPool(mock(ExecutorService.class));
        workerFactory.setPortletRenderer(portletRenderer);
        workerFactory.setExecutionInterceptors(Collections.emptyList());

        final IPersonalizer personalizer = mock(IPersonalizer.class);
        when(personalizer.personalize(any(), anyString(), any(HttpSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        final IPersonManager personManager = mock(IPersonManager.class);
        when(personManager.getPerson(request)).thenReturn(mock(IPerson.class));

        executionManager = new PortletExecutionManager();
        executionManager.setPortletWindowRegistry(windowRegistry);
        executionManager.setPortletWorkerFactory(workerFactory);
        executionManager.setPersonalizer(personalizer);
        executionManager.setPersonManager(personManager);
        executionManager.setPortletExecutionEventFactory(eventFactory);
    }

    /** A favorites web-component channel in its own window. */
    private IPortletWindowId favoritesWindow(WindowState windowState) {
        final IPortletDefinition definition = mock(IPortletDefinition.class);
        when(definition.getFName()).thenReturn("favorites");
        when(definition.getTitle("en_US")).thenReturn("Favorites");
        when(definition.getTimeout()).thenReturn(5000);
        when(definition.getLifecycleState()).thenReturn(PortletLifecycleState.PUBLISHED);
        final IPortletDescriptorKey descriptorKey = mock(IPortletDescriptorKey.class);
        when(descriptorKey.isFrameworkPortlet()).thenReturn(true);
        when(descriptorKey.getPortletName()).thenReturn(WebComponentChannel.PORTLET_NAME);
        when(definition.getPortletDescriptorKey()).thenReturn(descriptorKey);
        stubParameter(definition, WebComponentChannel.TAG_NAME_PARAMETER, "up-favorites");
        stubParameter(definition, WebComponentChannel.MODULE_URL_PARAMETER, "up-favorites");

        final IPortletEntity entity = mock(IPortletEntity.class);
        when(entity.getPortletDefinition()).thenReturn(definition);
        final IPortletWindowId windowId = mock(IPortletWindowId.class);
        final IPortletWindow window = mock(IPortletWindow.class);
        when(window.getPortletWindowId()).thenReturn(windowId);
        when(window.getPortletEntity()).thenReturn(entity);
        when(window.getWindowState()).thenReturn(windowState);
        when(windowRegistry.getPortletWindow(request, windowId)).thenReturn(window);
        return windowId;
    }

    private static void stubParameter(IPortletDefinition definition, String name, String value) {
        final IPortletDefinitionParameter parameter = mock(IPortletDefinitionParameter.class);
        when(parameter.getValue()).thenReturn(value);
        when(definition.getParameter(name)).thenReturn(parameter);
    }

    @Test
    public void rendersTheSameChannelTwiceWithOneModuleScript() throws Exception {
        final IPortletWindowId first = favoritesWindow(WindowState.NORMAL);
        final IPortletWindowId second = favoritesWindow(WindowState.NORMAL);

        // The StAX phase starts every channel before any output is incorporated
        executionManager.startPortletRender(first, request, response);
        executionManager.startPortletRender(second, request, response);
        assertTrue(executionManager.isPortletRenderRequested(first, request, response));

        assertEquals("Favorites", executionManager.getPortletTitle(first, request, response));
        final String firstOutput = executionManager.getPortletOutput(first, request, response);
        assertEquals("Favorites", executionManager.getPortletTitle(second, request, response));
        final String secondOutput = executionManager.getPortletOutput(second, request, response);

        assertTrue(firstOutput, firstOutput.startsWith(SCRIPT + "<up-favorites locale=\"en-US\""));
        assertTrue(secondOutput, secondOutput.startsWith("<up-favorites locale=\"en-US\""));
        assertEquals(0, executionManager.getPortletNewItemCount(first, request, response));
        assertEquals(
                "/default", executionManager.getPortletLink(first, "/default", request, response));

        // Every worker completed on the request thread: nothing is left to cancel
        executionManager.afterCompletion(request, response, null, null);
        verify(eventFactory, never()).publishPortletHungEvent(any(), any(), any());
        verifyNoInteractions(portletRenderer);
    }

    @Test
    public void rendersTheMaximizedWindowState() {
        final IPortletWindowId maximized = favoritesWindow(WindowState.MAXIMIZED);

        final String output = executionManager.getPortletOutput(maximized, request, response);

        assertTrue(output, output.contains(" window-state=\"maximized\""));
    }
}
