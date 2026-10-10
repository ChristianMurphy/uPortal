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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.portlet.WindowState;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletEntity;
import org.apereo.portal.portlet.om.IPortletWindow;
import org.apereo.portal.portlet.om.IPortletWindowId;
import org.apereo.portal.portlet.rendering.IPortletRenderer;
import org.apereo.portal.portlet.rendering.PortletRenderResult;
import org.apereo.portal.portlet.rendering.worker.IPortletExecutionContext.ExecutionType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class WebComponentRenderExecutionWorkerTest {

    private static final String OUTPUT = "<up-demo locale=\"en\"></up-demo>";

    private WebComponentChannelRenderer renderer;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private IPortletWindow window;
    private IPortletWindowId windowId;

    @Before
    public void setUp() {
        renderer = mock(WebComponentChannelRenderer.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        windowId = mock(IPortletWindowId.class);
        final IPortletDefinition definition = mock(IPortletDefinition.class);
        when(definition.getFName()).thenReturn("demo");
        final IPortletEntity entity = mock(IPortletEntity.class);
        when(entity.getPortletDefinition()).thenReturn(definition);
        window = mock(IPortletWindow.class);
        when(window.getPortletEntity()).thenReturn(entity);
        when(window.getPortletWindowId()).thenReturn(windowId);
        when(window.getWindowState()).thenReturn(WindowState.NORMAL);
        when(renderer.render(request, window)).thenReturn(OUTPUT);
    }

    private WebComponentRenderExecutionWorker worker() {
        return new WebComponentRenderExecutionWorker(renderer, request, response, window);
    }

    @Test
    public void rendersOnFirstRetrievalNotOnSubmit() throws Exception {
        final WebComponentRenderExecutionWorker worker = worker();

        worker.submit();

        assertTrue(worker.isSubmitted());
        assertFalse(worker.isStarted());
        assertFalse(worker.isComplete());
        assertFalse(worker.isRetrieved());
        assertEquals(0, worker.waitForStart(1000));
        verify(renderer, never()).render(request, window);

        assertEquals(OUTPUT, worker.getOutput(1000));
        assertEquals(OUTPUT, worker.getOutput(1000));

        assertTrue(worker.isStarted());
        assertTrue(worker.isComplete());
        assertTrue(worker.isRetrieved());
        assertTrue(worker.getSubmittedTime() > 0);
        assertTrue(worker.getStartedTime() >= worker.getSubmittedTime());
        assertTrue(worker.getCompleteTime() >= worker.getStartedTime());
        assertTrue(worker.getWait() >= 0);
        assertTrue(worker.getDuration() >= 0);
        verify(renderer, times(1)).render(request, window);
    }

    @Test
    public void reportsTheDefinitionTitleAndNoNewItemsOrLink() throws Exception {
        final WebComponentRenderExecutionWorker worker = worker();
        worker.submit();

        final PortletRenderResult result = worker.get(1000);

        // A null title makes the execution manager use the definition's title
        assertNull(result.getTitle());
        assertNull(result.getExternalLink());
        assertEquals(0, result.getNewItemCount());
        assertEquals("", response.getContentAsString());
    }

    @Test
    public void rethrowsTheRenderingFailureOnEveryRetrieval() {
        final IllegalArgumentException failure = new IllegalArgumentException("bad channel");
        when(renderer.render(request, window)).thenThrow(failure);
        final WebComponentRenderExecutionWorker worker = worker();
        worker.submit();

        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> worker.get(1000)));
        assertSame(
                failure,
                assertThrows(IllegalArgumentException.class, () -> worker.getOutput(1000)));
        assertTrue(worker.isComplete());
        verify(renderer, times(1)).render(request, window);
    }

    @Test
    public void writesTheOutputToTheResponseInTheExclusiveWindowState() throws Exception {
        when(window.getWindowState()).thenReturn(IPortletRenderer.EXCLUSIVE);
        final WebComponentRenderExecutionWorker worker = worker();
        worker.submit();

        worker.getOutput(1000);
        worker.getOutput(1000);

        assertEquals(OUTPUT, response.getContentAsString());
        assertEquals("text/html;charset=UTF-8", response.getContentType());
    }

    @Test
    public void keepsAContentTypeAlreadySet() throws Exception {
        when(window.getWindowState()).thenReturn(IPortletRenderer.EXCLUSIVE);
        response.setContentType("text/html;charset=ISO-8859-1");
        final WebComponentRenderExecutionWorker worker = worker();
        worker.submit();

        worker.getOutput(1000);

        assertEquals("text/html;charset=ISO-8859-1", response.getContentType());
    }

    @Test
    public void mustBeSubmittedExactlyOnce() {
        final WebComponentRenderExecutionWorker worker = worker();

        assertThrows(IllegalStateException.class, () -> worker.get(1000));

        worker.submit();
        assertThrows(IllegalStateException.class, worker::submit);
    }

    @Test
    public void describesTheExecution() {
        final WebComponentRenderExecutionWorker worker = worker();

        assertEquals(ExecutionType.RENDER, worker.getExecutionType());
        assertSame(windowId, worker.getPortletWindowId());
        assertEquals("demo", worker.getPortletFname());
        assertEquals(-1, worker.getApplicableTimeout());
        assertEquals(0, worker.getWait());
        assertEquals(0, worker.getDuration());
        assertTrue(worker.toString().contains("WebComponentRenderExecutionWorker"));

        worker.cancel();
        assertEquals(0, worker.getCancelCount());
    }

    @Test
    public void keepsExecutionAttributes() {
        final WebComponentRenderExecutionWorker worker = worker();

        assertNull(worker.setExecutionAttribute("a", "1"));
        assertEquals("1", worker.getExecutionAttribute("a"));
        assertEquals("1", worker.setExecutionAttribute("a", null));
        assertNull(worker.getExecutionAttribute("a"));
    }
}
