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
package org.apache.pluto.container.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Timer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.pluto.container.FilterManager;
import org.apache.pluto.container.PortletInvokerService;
import org.apache.pluto.container.PortletRequestContext;
import org.apache.pluto.container.PortletResponseContext;
import org.apache.pluto.container.PortletWindow;
import org.apache.pluto.container.om.portlet.PortletDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * A portlet request that reaches Pluto's PortletServlet before the servlet has registered its
 * portlet with the portal must not take the portlet out of service until Tomcat restarts
 * (MOD-I350). Apache Pluto 2.1.0-M3 threw a permanent UnavailableException there, and Tomcat then
 * refused the servlet for good: after a portal start, a page rendered a few seconds too early broke
 * SimpleContentPortlet for the whole test run.
 */
public class PortletServletRegistrationTest {

    private static final String APPLICATION = "/SimpleContentPortlet";
    private static final String PORTLET = "cms";

    private final ServletContext servletContext = mock(ServletContext.class);
    private final ServletConfig servletConfig = mock(ServletConfig.class);
    private final PortletContextService contextService = mock(PortletContextService.class);
    private final DriverPortletContext portletContext = mock(DriverPortletContext.class);
    private final FilterManager filterManager = mock(FilterManager.class);
    private final PortletServlet servlet = new PortletServlet();

    @Before
    public void setUp() throws Exception {
        setPlutoServices(null);
        TestPortlet.reset();
        when(servletConfig.getServletContext()).thenReturn(servletContext);
        when(servletConfig.getInitParameter("portlet-name")).thenReturn(PORTLET);
        when(contextService.register(servletConfig)).thenReturn(APPLICATION);
        when(contextService.getPortletContext(APPLICATION)).thenReturn(portletContext);
        DriverPortletConfig portletConfig = mock(DriverPortletConfig.class);
        PortletDefinition definition = mock(PortletDefinition.class);
        when(contextService.getPortletConfig(APPLICATION, PORTLET)).thenReturn(portletConfig);
        when(portletConfig.getPortletDefinition()).thenReturn(definition);
        when(definition.getPortletClass()).thenReturn(TestPortlet.class.getName());
    }

    @After
    public void tearDown() throws Exception {
        TestPortlet.releaseInit();
        servlet.destroy();
        setPlutoServices(null);
    }

    /** The portal's container services start (uPortal creates them when its context starts). */
    private void startContainer() {
        PortalDriverServices driverServices = mock(PortalDriverServices.class);
        PortalAdministrationService administration = mock(PortalAdministrationService.class);
        when(driverServices.getPortletContextService()).thenReturn(contextService);
        when(driverServices.getPortalAdministrationService()).thenReturn(administration);
        when(administration.getPortletInvocationListeners()).thenReturn(Collections.emptyList());
        new PlutoServices(driverServices);
    }

    private static void setPlutoServices(PlutoServices services) throws Exception {
        Field singleton = PlutoServices.class.getDeclaredField("singleton");
        singleton.setAccessible(true);
        singleton.set(null, services);
    }

    /** A render request, as the portal's PortletInvokerService dispatches it to the servlet. */
    private void render() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RenderRequest renderRequest = mock(RenderRequest.class);
        RenderResponse renderResponse = mock(RenderResponse.class);
        PortletRequestContext requestContext = mock(PortletRequestContext.class);
        when(request.getAttribute(PortletInvokerService.METHOD_ID))
                .thenReturn(PortletInvokerService.METHOD_RENDER);
        when(request.getAttribute(PortletInvokerService.PORTLET_REQUEST)).thenReturn(renderRequest);
        when(request.getAttribute(PortletInvokerService.PORTLET_RESPONSE))
                .thenReturn(renderResponse);
        when(request.getAttribute(PortletInvokerService.FILTER_MANAGER)).thenReturn(filterManager);
        when(renderRequest.getAttribute(PortletInvokerService.REQUEST_CONTEXT))
                .thenReturn(requestContext);
        when(renderRequest.getAttribute(PortletInvokerService.RESPONSE_CONTEXT))
                .thenReturn(mock(PortletResponseContext.class));
        when(requestContext.getPortletWindow()).thenReturn(mock(PortletWindow.class));

        servlet.doGet(request, mock(HttpServletResponse.class));

        ArgumentCaptor<Portlet> rendered = ArgumentCaptor.forClass(Portlet.class);
        verify(filterManager)
                .processFilter(
                        same(renderRequest), same(renderResponse), rendered.capture(),
                        same((PortletContext) portletContext));
        assertTrue(rendered.getValue() instanceof TestPortlet);
        assertTrue(((TestPortlet) rendered.getValue()).initialised);
    }

    /** A render request that Tomcat answers with the UnavailableException the servlet throws. */
    private UnavailableException renderUnavailable() throws Exception {
        try {
            render();
        } catch (UnavailableException e) {
            verify(filterManager, never())
                    .processFilter(
                            any(RenderRequest.class),
                            any(RenderResponse.class),
                            any(Portlet.class),
                            any(PortletContext.class));
            return e;
        }
        fail("The servlet rendered the portlet");
        return null;
    }

    @Test
    public void aRequestBeforeTheTimerRegistersThePortletAtOnce() throws Exception {
        servlet.registrationWaitMillis = 2000;
        servlet.init(servletConfig);
        // The start timer has not tried yet: replace it with one that has no task of its own
        synchronized (servletContext) {
            servlet.startTimer.cancel();
            servlet.startTimer = new Timer(true);
        }
        startContainer();

        render();

        assertEquals(1, TestPortlet.instances.get());
    }

    @Test
    public void aRequestThatCannotWaitLeavesTheServletUnavailableOnlyForAWhile() throws Exception {
        TestPortlet.holdInit(); // the portlet's init() takes a while
        servlet.registrationWaitMillis = 200;
        startContainer();
        servlet.init(servletConfig);

        UnavailableException unavailable = renderUnavailable();

        assertFalse("permanently unavailable", unavailable.isPermanent());
        assertEquals(PortletServlet.UNAVAILABLE_SECONDS, unavailable.getUnavailableSeconds());
        assertTrue(unavailable.getUnavailableSeconds() > 0);

        // Tomcat calls the servlet again once that time is over
        TestPortlet.releaseInit();
        servlet.registrationWaitMillis = 10000;
        render();
        assertEquals(1, TestPortlet.instances.get());
    }

    @Test
    public void anInterruptedRequestLeavesTheServletUnavailableOnlyForAWhile() throws Exception {
        TestPortlet.holdInit();
        startContainer();
        servlet.init(servletConfig);

        // uPortal interrupts a portlet render that times out
        Thread.currentThread().interrupt();
        UnavailableException unavailable = renderUnavailable();

        assertTrue("the interrupt is kept", Thread.interrupted());
        assertFalse("permanently unavailable", unavailable.isPermanent());
        TestPortlet.releaseInit();
        render();
    }

    @Test
    public void aPortletThatFailsToStartIsStillOutOfServiceForGood() throws Exception {
        TestPortlet.failInit();
        startContainer();
        servlet.init(servletConfig);

        UnavailableException unavailable = renderUnavailable();

        assertTrue(unavailable.isPermanent());
        assertTrue(TestPortlet.initStarted.await(10, TimeUnit.SECONDS));
    }

    /** The portlet class that the portlet definition names. */
    public static class TestPortlet implements Portlet {

        static final AtomicInteger instances = new AtomicInteger();
        private static volatile CountDownLatch gate = new CountDownLatch(0);
        static volatile CountDownLatch initStarted = new CountDownLatch(1);
        private static volatile boolean failing;

        volatile boolean initialised;

        static void reset() {
            instances.set(0);
            gate = new CountDownLatch(0);
            initStarted = new CountDownLatch(1);
            failing = false;
        }

        /** init() waits until releaseInit() */
        static void holdInit() {
            gate = new CountDownLatch(1);
        }

        static void releaseInit() {
            gate.countDown();
        }

        /** init() throws */
        static void failInit() {
            failing = true;
        }

        public TestPortlet() {
            instances.incrementAndGet();
        }

        @Override
        public void init(PortletConfig config) throws PortletException {
            initStarted.countDown();
            if (failing) {
                throw new PortletException("the portlet fails to start");
            }
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PortletException(e);
            }
            initialised = true;
        }

        @Override
        public void processAction(ActionRequest request, ActionResponse response) {}

        @Override
        public void render(RenderRequest request, RenderResponse response) {}

        @Override
        public void destroy() {}
    }
}
