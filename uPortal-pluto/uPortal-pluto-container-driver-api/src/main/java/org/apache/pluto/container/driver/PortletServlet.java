/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Changed by uPortal (see uPortal-pluto/README.md): a request that arrives before the portlet has
 * registered no longer takes the portlet out of service until Tomcat restarts (MOD-I350).
 */
package org.apache.pluto.container.driver;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.EventPortlet;
import javax.portlet.EventRequest;
import javax.portlet.EventResponse;
import javax.portlet.Portlet;
import javax.portlet.PortletConfig;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.ResourceServingPortlet;
import javax.portlet.UnavailableException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.pluto.container.FilterManager;
import org.apache.pluto.container.PortletContainerException;
import org.apache.pluto.container.PortletInvokerService;
import org.apache.pluto.container.PortletRequestContext;
import org.apache.pluto.container.PortletResponseContext;
import org.apache.pluto.container.PortletWindow;
import org.apache.pluto.container.om.portlet.PortletDefinition;

/**
 * Portlet Invocation Servlet. This servlet recieves cross context requests from
 * the the container and services the portlet request for the specified method.
 *
 * @version 1.1
 * @since 09/22/2004
 */
public class PortletServlet extends HttpServlet
{
    private static final long serialVersionUID = -5096339022539360365L;

    static class NullPortlet implements EventPortlet, ResourceServingPortlet, Portlet
    {
        public void processEvent(EventRequest arg0, EventResponse arg1)
        throws PortletException, IOException
        {
        }

        public void serveResource(ResourceRequest arg0, ResourceResponse arg1)
        throws PortletException, IOException
        {
        }

        public void destroy()
        {
        }

        public void init(PortletConfig arg0) throws PortletException
        {
        }

        public void processAction(ActionRequest arg0, ActionResponse arg1)
        throws PortletException, IOException
        {
        }

        public void render(RenderRequest arg0, RenderResponse arg1)
        throws PortletException, IOException
        {
        }
    }

    // Private Member Variables ------------------------------------------------
    /**
     * The portlet name as defined in the portlet app descriptor.
     */
    private String portletName;

    /**
     * The portlet instance wrapped by this servlet.
     */
    // uPortal: volatile, because dispatch() reads it without the lock. It is set only once the
    // portlet is initialised, and after the fields that dispatch() uses with it (MOD-I350).
    private volatile Portlet portlet;

    /**
     * The internal portlet context instance.
     */
    private DriverPortletContext portletContext;

    /**
     * The internal portlet config instance.
     */
    private DriverPortletConfig portletConfig;

    /**
     * The Event Portlet instance (the same object as portlet) wrapped by this
     * servlet.
     */
    private EventPortlet eventPortlet;

    /** The resource serving portlet instance wrapped by this servlet. */
    private ResourceServingPortlet resourceServingPortlet;

    private PortletContextService contextService;

    private boolean started = false;
    // uPortal: volatile, because awaitRegistration() reads it without the lock (MOD-I350)
    volatile Timer   startTimer;

    // uPortal (MOD-I350): Apache Pluto tried to register every 10 seconds, so a portlet could stay
    // unregistered for up to 10 seconds after the portal's container services had started.
    static final long REGISTRATION_PERIOD_MILLIS = 1000;

    // uPortal (MOD-I350): how long a request that comes before the registration waits for it.
    // A field, so that the tests can shorten it.
    long registrationWaitMillis = 10000;

    // uPortal (MOD-I350): how long Tomcat holds the servlet unavailable when that wait runs out
    static final int UNAVAILABLE_SECONDS = 1;

    // uPortal (MOD-I350): a field, so that a registration task can be added after init()
    private transient ClassLoader paClassLoader;

    // uPortal (MOD-I350): counted down when the registration is over (startTimer is then null)
    private transient CountDownLatch registrationOver;

    // HttpServlet Impl --------------------------------------------------------

    public String getServletInfo()
    {
        return "Pluto PortletServlet [" + portletName + "]";
    }

    /**
     * Initialize the portlet invocation servlet.
     *
     * @throws ServletException
     *             if an error occurs while loading portlet.
     */
    public void init(ServletConfig config) throws ServletException
    {

        // Call the super initialization method.
        super.init(config);

        // Retrieve portlet name as defined as an initialization parameter.
        portletName = getInitParameter("portlet-name");

        started = false;

        startTimer = new Timer(true);
        paClassLoader = Thread.currentThread().getContextClassLoader();
        registrationOver = new CountDownLatch(1);
        startTimer.schedule(registrationTask(), 1, REGISTRATION_PERIOD_MILLIS);
    }

    // uPortal: the timer task of init(), moved here so that awaitRegistration() can add one
    // (MOD-I350)
    private TimerTask registrationTask()
    {
        final ServletContext servletContext = getServletContext();
        return new TimerTask()
        {
            public void run()
            {
                synchronized(servletContext)
                {
                    if (startTimer != null)
                    {
                        if (attemptRegistration(servletContext, paClassLoader ))
                        {
                            startTimer.cancel();
                            startTimer = null;
                            registrationOver.countDown();
                        }
                    }
                }
            }
        };
    }

    /**
     * uPortal (MOD-I350): has the start timer try to register the portlet now, and waits for the
     * registration up to {@link #registrationWaitMillis}. The attempt runs on the timer thread, so
     * an interrupted request (uPortal interrupts a portlet render that times out) can't make the
     * portlet's initialisation fail, and the request doesn't wait for the lock the attempt holds.
     *
     * @return false while the registration is still to come, true once it is over (the portlet
     *         is then in service, unless it failed to start)
     */
    private boolean awaitRegistration()
    {
        Timer timer = startTimer;
        if (timer != null)
        {
            try
            {
                timer.schedule(registrationTask(), 0);
            }
            catch (IllegalStateException e)
            {
                // The timer has stopped: the registration is over, or an attempt threw and no
                // registration will come
                return true;
            }
        }
        try
        {
            return registrationOver.await(registrationWaitMillis, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    protected boolean attemptRegistration(ServletContext context, ClassLoader paClassLoader)
    {
        if (PlutoServices.getServices() != null)
        {
            contextService = PlutoServices.getServices().getPortletContextService();
            try
            {
                ServletConfig sConfig = getServletConfig();
                if (sConfig == null)
                {
                    String msg = "Problem obtaining servlet configuration(getServletConfig() returns null).";
                    context.log(msg);
                    return true;
                }

                String applicationName = contextService.register(sConfig);
                started = true;
                portletContext = contextService.getPortletContext(applicationName);
                portletConfig = contextService.getPortletConfig(applicationName, portletName);

            }
            catch (PortletContainerException ex)
            {
                context.log(ex.getMessage(),ex);
                return true;
            }

            PortletDefinition portletDD = portletConfig.getPortletDefinition();

            //          Create and initialize the portlet wrapped in the servlet.
            try
            {
                Class<?> clazz = paClassLoader.loadClass((portletDD.getPortletClass()));
                // uPortal: dispatch() sees the portlet only once it is initialised (MOD-I350)
                Portlet newPortlet = (Portlet) clazz.newInstance();
                newPortlet.init(portletConfig);
                initializeEventPortlet(newPortlet);
                initializeResourceServingPortlet(newPortlet);
                portlet = newPortlet;
                return true;
            }
            catch (Exception ex)
            {
                context.log(ex.getMessage(),ex);
                // take out of service
                portlet = null;
                portletConfig = null;
                return true;
            }
        }
        return false;
    }

    public void destroy()
    {
        synchronized(getServletContext())
        {
            if ( startTimer != null )
            {
                startTimer.cancel();
                startTimer = null;
                // uPortal: release the requests that wait in awaitRegistration() (MOD-I350)
                registrationOver.countDown();
            }
            else if ( started && portletContext != null)
            {
                started = false;
                contextService.unregister(portletContext);
                if (portlet != null)
                {
                    try
                    {
                        portlet.destroy();
                    }
                    catch (Exception e)
                    {
                        // ignore
                    }
                    portlet = null;
                }
            }
            super.destroy();
        }
    }

    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException
            {
        dispatch(request, response);
            }

    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException
            {
        dispatch(request, response);
            }

    protected void doPut(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException
            {
        dispatch(request, response);
            }

    // Private Methods ---------------------------------------------------------

    /**
     * Dispatch the request to the appropriate portlet methods. This method
     * assumes that the following attributes are set in the servlet request
     * scope:
     * <ul>
     * <li>METHOD_ID: indicating which method to dispatch.</li>
     * <li>PORTLET_REQUEST: the internal portlet request.</li>
     * <li>PORTLET_RESPONSE: the internal portlet response.</li>
     * </ul>
     *
     * @param request
     *            the servlet request.
     * @param response
     *            the servlet response.
     * @throws ServletException
     * @throws IOException
     */
    private void dispatch(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException
            {
        if (portlet == null)
        {
            // uPortal (MOD-I350): Apache Pluto threw the one-argument UnavailableException here,
            // which Tomcat takes as permanent. A request that came before the start timer had
            // registered the portlet took the portlet out of service until Tomcat restarted. Now
            // the request has the registration done at once, and if it can't wait for it, Tomcat
            // holds the servlet unavailable for UNAVAILABLE_SECONDS only.
            if (!awaitRegistration())
            {
                throw new javax.servlet.UnavailableException("Portlet "+portletName+" unavailable",
                        UNAVAILABLE_SECONDS);
            }
            if (portlet == null)
            {
                // The portlet failed to start; attemptRegistration() logged why
                throw new javax.servlet.UnavailableException("Portlet "+portletName+" unavailable");
            }
        }

        // Retrieve attributes from the servlet request.
        Integer methodId = (Integer) request.getAttribute(PortletInvokerService.METHOD_ID);

        final PortletRequest portletRequest = (PortletRequest)request.getAttribute(PortletInvokerService.PORTLET_REQUEST);

        final PortletResponse portletResponse = (PortletResponse)request.getAttribute(PortletInvokerService.PORTLET_RESPONSE);

        final PortletRequestContext requestContext = (PortletRequestContext)portletRequest.getAttribute(PortletInvokerService.REQUEST_CONTEXT);
        final PortletResponseContext responseContext = (PortletResponseContext)portletRequest.getAttribute(PortletInvokerService.RESPONSE_CONTEXT);

        final FilterManager filterManager = (FilterManager)request.getAttribute(PortletInvokerService.FILTER_MANAGER);

        request.removeAttribute(PortletInvokerService.METHOD_ID);
        request.removeAttribute(PortletInvokerService.PORTLET_REQUEST);
        request.removeAttribute(PortletInvokerService.PORTLET_RESPONSE);
        request.removeAttribute(PortletInvokerService.FILTER_MANAGER);

        requestContext.init(portletConfig, getServletContext(), request, response);
        responseContext.init(request, response);

        PortletWindow window = requestContext.getPortletWindow();

        PortletInvocationEvent event = new PortletInvocationEvent(portletRequest, window, methodId.intValue());

        notify(event, true, null);

        // FilterManager filtermanager = (FilterManager) request.getAttribute(
        // "filter-manager");

        try
        {

            // The requested method is RENDER: call Portlet.render(..)
            if (methodId == PortletInvokerService.METHOD_RENDER)
            {
                RenderRequest renderRequest = (RenderRequest) portletRequest;
                RenderResponse renderResponse = (RenderResponse) portletResponse;
                filterManager.processFilter(renderRequest, renderResponse,
                        portlet, portletContext);
            }

            // The requested method is RESOURCE: call
            // ResourceServingPortlet.serveResource(..)
            else if (methodId == PortletInvokerService.METHOD_RESOURCE)
            {
                ResourceRequest resourceRequest = (ResourceRequest) portletRequest;
                ResourceResponse resourceResponse = (ResourceResponse) portletResponse;
                filterManager.processFilter(resourceRequest, resourceResponse,
                        resourceServingPortlet, portletContext);
            }

            // The requested method is ACTION: call Portlet.processAction(..)
            else if (methodId == PortletInvokerService.METHOD_ACTION)
            {
                ActionRequest actionRequest = (ActionRequest) portletRequest;
                ActionResponse actionResponse = (ActionResponse) portletResponse;
                filterManager.processFilter(actionRequest, actionResponse,
                        portlet, portletContext);
            }

            // The request methode is Event: call Portlet.processEvent(..)
            else if (methodId == PortletInvokerService.METHOD_EVENT)
            {
                EventRequest eventRequest = (EventRequest) portletRequest;
                EventResponse eventResponse = (EventResponse) portletResponse;
                filterManager.processFilter(eventRequest, eventResponse,
                        eventPortlet, portletContext);
            }
            // The requested method is ADMIN: call handlers.
            else if (methodId == PortletInvokerService.METHOD_ADMIN)
            {
                PortalAdministrationService pas = PlutoServices.getServices().getPortalAdministrationService();

                for (AdministrativeRequestListener l : pas.getAdministrativeRequestListeners())
                {
                    l.administer(portletRequest, portletResponse);
                }
            }

            // The requested method is LOAD: do nothing.
            else if (methodId == PortletInvokerService.METHOD_LOAD)
            {
                // Do nothing.
            }

            notify(event, false, null);

        }
        catch (UnavailableException ex)
        {
            //
            // if (e.isPermanent()) { throw new
            // UnavailableException(e.getMessage()); } else { throw new
            // UnavailableException(e.getMessage(), e.getUnavailableSeconds());
            // }
            //

            // Portlet.destroy() isn't called by Tomcat, so we have to fix it.
            try
            {
                portlet.destroy();
            }
            catch (Throwable th)
            {
                // Don't care for Exception
                this.getServletContext().log("Error during portlet destroy.", th);
            }
            // take portlet out of service
            portlet = null;

            // TODO: Handle everything as permanently for now.
            throw new javax.servlet.UnavailableException(ex.getMessage());

        }
        catch (PortletException ex)
        {
            notify(event, false, ex);
            throw new ServletException(ex);

        }
            }

    protected void notify(PortletInvocationEvent event, boolean pre, Throwable e)
    {
        PortalAdministrationService pas = PlutoServices.getServices().getPortalAdministrationService();

        for (PortletInvocationListener listener : pas.getPortletInvocationListeners())
        {
            if (pre)
            {
                listener.onBegin(event);
            }
            else if (e == null)
            {
                listener.onEnd(event);
            }
            else
            {
                listener.onError(event, e);
            }
        }
    }

    private void initializeEventPortlet(Portlet portlet)
    {
        if (portlet instanceof EventPortlet)
        {
            eventPortlet = (EventPortlet) portlet;
        }
        else
        {
            eventPortlet = new NullPortlet();
        }
    }

    private void initializeResourceServingPortlet(Portlet portlet)
    {
        if (portlet instanceof ResourceServingPortlet)
        {
            resourceServingPortlet = (ResourceServingPortlet) portlet;
        }
        else
        {
            resourceServingPortlet = new NullPortlet();
        }
    }
}
