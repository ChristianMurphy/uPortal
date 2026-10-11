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
package org.apereo.portal.events.handlers.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The portal event queue is flushed to the database from the moment uPortal starts. The aggregator
 * reads only events newer than the last one it aggregated, so on a cluster an event that a node
 * stores after another node's aggregator passed its time is never aggregated: holding the flush
 * back with the other scheduled tasks (120 s) lost the events of every node's first minutes.
 */
public class EventFlushScheduleTest {

    private static final String CONTEXT = "/properties/contexts/schedulerContext.xml";
    private static final String TASK_NS = "http://www.springframework.org/schema/task";
    private static final String BEANS_NS = "http://www.springframework.org/schema/beans";

    /** A property placeholder with a default: ${name:default} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^:}]+:([^}]+)}");

    private static Document context() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (InputStream in = EventFlushScheduleTest.class.getResourceAsStream(CONTEXT)) {
            assertNotNull(CONTEXT + " is not on the test classpath", in);
            return factory.newDocumentBuilder().parse(in);
        }
    }

    /** The scheduler that runs portalEventDaoQueuingEventHandler.flush */
    private static Element flushScheduler(Document context) {
        final NodeList tasks = context.getElementsByTagNameNS(TASK_NS, "scheduled");
        for (int i = 0; i < tasks.getLength(); i++) {
            final Element task = (Element) tasks.item(i);
            if ("portalEventDaoQueuingEventHandler".equals(task.getAttribute("ref"))
                    && "flush".equals(task.getAttribute("method"))) {
                final String scheduler = ((Element) task.getParentNode()).getAttribute("scheduler");
                final NodeList beans = context.getElementsByTagNameNS(BEANS_NS, "bean");
                for (int j = 0; j < beans.getLength(); j++) {
                    final Element bean = (Element) beans.item(j);
                    if (scheduler.equals(bean.getAttribute("id"))) {
                        return bean;
                    }
                }
                throw new AssertionError("No bean " + scheduler + " in " + CONTEXT);
            }
        }
        throw new AssertionError(
                "No scheduled portalEventDaoQueuingEventHandler.flush in " + CONTEXT);
    }

    /** The scheduler's initialDelay, with a placeholder resolved to its default */
    private static Duration initialDelay(Element scheduler) {
        final NodeList properties = scheduler.getElementsByTagNameNS(BEANS_NS, "property");
        for (int i = 0; i < properties.getLength(); i++) {
            final Element property = (Element) properties.item(i);
            if ("initialDelay".equals(property.getAttribute("name"))) {
                String value = property.getAttribute("value");
                final Matcher m = PLACEHOLDER.matcher(value);
                if (m.matches()) {
                    value = m.group(1);
                }
                return Duration.parse(value);
            }
        }
        return Duration.ZERO;
    }

    @Test
    public void eventsAreFlushedFromTheStart() throws Exception {
        assertEquals(Duration.ZERO, initialDelay(flushScheduler(context())));
    }
}
