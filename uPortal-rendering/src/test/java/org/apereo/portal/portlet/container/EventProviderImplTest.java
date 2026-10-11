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
package org.apereo.portal.portlet.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import javax.portlet.Event;
import javax.xml.namespace.QName;
import org.apache.pluto.container.PortletWindow;
import org.apache.pluto.container.driver.PortletContextService;
import org.apache.pluto.container.om.portlet.EventDefinition;
import org.apache.pluto.container.om.portlet.EventDefinitionReference;
import org.apache.pluto.container.om.portlet.PortletApplicationDefinition;
import org.apache.pluto.container.om.portlet.PortletDefinition;
import org.apereo.portal.portlet.om.IPortletWindow;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Publishing a JSR-286 event with a payload (PLT.15.2.2). The payload is marshalled with JAXB,
 * which finds its implementation through the portlet application's class loader. Java 11 and later
 * have no JAXB implementation of their own, so a portlet WAR without one could not publish events
 * (found by Apache Pluto's test suite: the event tests' event phase never ran).
 */
public class EventProviderImplTest {

    private static final String NAMESPACE = "urn:uportal:test";
    private static final QName EVENT = new QName(NAMESPACE, "event-with-simple-value");

    /** A portlet application class loader that sees the JDK only: no JAXB implementation. */
    private URLClassLoader portletClassLoader;

    private EventProviderImpl eventProvider;

    @Before
    public void setUp() throws Exception {
        portletClassLoader = new URLClassLoader(new URL[0], null);

        final EventDefinitionReference reference = mock(EventDefinitionReference.class);
        when(reference.getQualifiedName(NAMESPACE)).thenReturn(EVENT);
        final EventDefinition definition = mock(EventDefinition.class);
        when(definition.getQName()).thenReturn(EVENT);
        when(definition.getValueType()).thenReturn(String.class.getName());

        final PortletApplicationDefinition application = mock(PortletApplicationDefinition.class);
        when(application.getName()).thenReturn("/test-portlets");
        when(application.getDefaultNamespace()).thenReturn(NAMESPACE);
        doReturn(Collections.singletonList(definition)).when(application).getEventDefinitions();

        final PortletDefinition portletDefinition = mock(PortletDefinition.class);
        when(portletDefinition.getApplication()).thenReturn(application);
        doReturn(Collections.singletonList(reference))
                .when(portletDefinition)
                .getSupportedPublishingEvents();

        final PortletWindow plutoWindow = mock(PortletWindow.class);
        when(plutoWindow.getPortletDefinition()).thenReturn(portletDefinition);
        final IPortletWindow portletWindow = mock(IPortletWindow.class);
        when(portletWindow.getPlutoPortletWindow()).thenReturn(plutoWindow);

        final PortletContextService portletContextService = mock(PortletContextService.class);
        when(portletContextService.getClassLoader("/test-portlets")).thenReturn(portletClassLoader);

        eventProvider = new EventProviderImpl(portletWindow, portletContextService);
    }

    @After
    public void tearDown() throws Exception {
        portletClassLoader.close();
    }

    @Test
    public void publishesAPayloadWhenThePortletHasNoJaxbImplementation() {
        final ClassLoader before = Thread.currentThread().getContextClassLoader();

        final Event event = eventProvider.createEvent(EVENT, "SIMPLE_STRING_VALUE");

        assertNotNull("the event was not created", event);
        assertEquals(EVENT, event.getQName());
        final String xml = String.valueOf(event.getValue());
        assertTrue(xml, xml.contains("SIMPLE_STRING_VALUE"));
        assertTrue(xml, xml.contains("event-with-simple-value"));
        assertSame(
                "the context class loader is restored",
                before,
                Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void publishesAnEventWithoutPayload() {
        final Event event = eventProvider.createEvent(EVENT, null);

        assertNotNull(event);
        assertEquals(EVENT, event.getQName());
    }
}
