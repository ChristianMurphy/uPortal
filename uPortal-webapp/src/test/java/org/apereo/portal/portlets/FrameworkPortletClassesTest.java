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
package org.apereo.portal.portlets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.portlet.GenericPortlet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apereo.portal.portlets.webcomponent.WebComponentPortlet;
import org.apereo.portal.spring.web.portlet.DispatcherPortlet;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The portal's own portlets are configured by class name: in WEB-INF/portlet.xml and in the Spring
 * context of each portlet. Those contexts load only when the portlet is first used, so a class name
 * that does not resolve would otherwise go unnoticed until then.
 */
public class FrameworkPortletClassesTest {

    private static final File PORTLET_XML = new File("src/main/webapp/WEB-INF/portlet.xml");

    /**
     * Portlets that are deliberately not Spring dispatcher portlets: the framework portlet that
     * anchors web-component channels in the portlet registry and never renders (MOD-D18).
     */
    private static final Set<Class<? extends GenericPortlet>> OTHER_PORTLETS =
            Collections.singleton(WebComponentPortlet.class);

    @Test
    public void portletClassesAreDispatcherPortlets() throws Exception {
        final NodeList portletClasses =
                parse(PORTLET_XML).getElementsByTagNameNS("*", "portlet-class");
        assertTrue(portletClasses.getLength() > 0);
        for (int i = 0; i < portletClasses.getLength(); i++) {
            final String className = portletClasses.item(i).getTextContent().trim();
            final Class<?> portletClass = load(className);
            assertTrue(
                    className,
                    DispatcherPortlet.class.isAssignableFrom(portletClass)
                            || OTHER_PORTLETS.contains(portletClass));
        }
    }

    @Test
    public void portletContextBeanClassesResolve() throws Exception {
        final List<String> contexts = portletContextLocations();
        assertFalse(contexts.isEmpty());
        for (String location : contexts) {
            final Document context;
            try (InputStream in = new ClassPathResource(location).getInputStream()) {
                context = newDocumentBuilder().parse(in);
            }
            final NodeList beans = context.getElementsByTagNameNS("*", "bean");
            for (int i = 0; i < beans.getLength(); i++) {
                final String className = ((Element) beans.item(i)).getAttribute("class");
                if (!className.isEmpty()) {
                    try {
                        load(className);
                    } catch (ClassNotFoundException e) {
                        throw new AssertionError(location + ": " + className, e);
                    }
                }
            }
        }
    }

    /** The contextConfigLocation of every portlet in portlet.xml, as classpath locations. */
    private static List<String> portletContextLocations() throws Exception {
        final List<String> locations = new ArrayList<>();
        final NodeList initParams = parse(PORTLET_XML).getElementsByTagNameNS("*", "init-param");
        for (int i = 0; i < initParams.getLength(); i++) {
            final Element initParam = (Element) initParams.item(i);
            final String name =
                    initParam.getElementsByTagNameNS("*", "name").item(0).getTextContent().trim();
            if ("contextConfigLocation".equals(name)) {
                final String value =
                        initParam
                                .getElementsByTagNameNS("*", "value")
                                .item(0)
                                .getTextContent()
                                .trim();
                locations.add(value.replaceFirst("^(classpath:|/WEB-INF/classes)/?", ""));
            }
        }
        return locations;
    }

    /** Loads a class without initializing it. */
    private static Class<?> load(String className) throws ClassNotFoundException {
        return Class.forName(className, false, FrameworkPortletClassesTest.class.getClassLoader());
    }

    private static Document parse(File file) throws Exception {
        return newDocumentBuilder().parse(file);
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }
}
