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
package org.apereo.portal.portlet.rendering.worker;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletEntity;
import org.apereo.portal.portlet.om.IPortletWindow;
import org.apereo.portal.portlet.om.IPortletWindowId;
import org.apereo.portal.portlet.registry.IPortletWindowRegistry;
import org.apereo.portal.portlet.rendering.IPortletRenderer;
import org.apereo.portal.portlet.rendering.webcomponent.WebComponentChannelRenderer;
import org.apereo.portal.portlet.rendering.webcomponent.WebComponentRenderExecutionWorker;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class PortletWorkerFactoryImplTest {

    private PortletWorkerFactoryImpl factory;
    private IPortletWindowRegistry windowRegistry;
    private WebComponentChannelRenderer webComponentChannelRenderer;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private IPortletWindowId windowId;
    private IPortletWindow window;

    @Before
    public void setUp() {
        windowRegistry = mock(IPortletWindowRegistry.class);
        webComponentChannelRenderer = mock(WebComponentChannelRenderer.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        windowId = mock(IPortletWindowId.class);

        final IPortletDefinition definition = mock(IPortletDefinition.class);
        when(definition.getTimeout()).thenReturn(5000);
        final IPortletEntity entity = mock(IPortletEntity.class);
        when(entity.getPortletDefinition()).thenReturn(definition);
        window = mock(IPortletWindow.class);
        when(window.getPortletWindowId()).thenReturn(windowId);
        when(window.getPortletEntity()).thenReturn(entity);
        when(windowRegistry.getPortletWindow(request, windowId)).thenReturn(window);

        factory = new PortletWorkerFactoryImpl();
        factory.setPortletWindowRegistry(windowRegistry);
        factory.setWebComponentChannelRenderer(webComponentChannelRenderer);
        factory.setPortletThreadPool(mock(ExecutorService.class));
        factory.setPortletRenderer(mock(IPortletRenderer.class));
        factory.setExecutionInterceptors(Collections.emptyList());
    }

    @Test
    public void rendersWebComponentChannelsWithoutThePortletContainer() {
        when(webComponentChannelRenderer.isWebComponent(window)).thenReturn(true);

        final IPortletRenderExecutionWorker worker =
                factory.createRenderWorker(request, response, windowId);

        assertTrue(worker instanceof WebComponentRenderExecutionWorker);
    }

    @Test
    public void rendersPortletsThroughThePortletContainer() {
        final IPortletRenderExecutionWorker worker =
                factory.createRenderWorker(request, response, windowId);

        assertTrue(worker instanceof PortletRenderExecutionWorker);
    }

    @Test
    public void refusesAnUnknownWindow() {
        final IPortletWindowId unknownWindowId = mock(IPortletWindowId.class);

        assertThrows(
                RuntimeException.class,
                () -> factory.createRenderWorker(request, response, unknownWindowId));
    }
}
