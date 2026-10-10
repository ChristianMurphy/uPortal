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
package org.apereo.portal.utils.cache.resource;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

public class TemplatesBuilderTest {

    /** Called from extensionFunctionTest.xsl through the Xalan-Java extension namespace. */
    public static final class Greeter {
        private final String greeting;

        public Greeter(String greeting) {
            this.greeting = greeting;
        }

        /** Same pattern as XsltPortalUrlProvider.getUrlProvider(Object). */
        public static Greeter getGreeter(Object greeter) {
            return (Greeter) greeter;
        }

        public String greet(String name) {
            return greeting + ", " + name;
        }
    }

    /**
     * uPortal's structure and theme stylesheets call Java extension functions. JDK 25 disables them
     * by default (jdk.xml.enableExtensionFunctions=false in conf/jaxp.properties), so
     * TemplatesBuilder must enable them on the factory it creates.
     */
    @Test
    public void testStylesheetCanCallJavaExtensionFunctions() throws Exception {
        final TemplatesBuilder templatesBuilder = new TemplatesBuilder();
        templatesBuilder.setResourceLoader(new DefaultResourceLoader());

        final Templates templates =
                templatesBuilder
                        .loadResource(
                                new ClassPathResource("extensionFunctionTest.xsl", getClass()))
                        .getLoadedResource();

        final Transformer transformer = templates.newTransformer();
        transformer.setParameter("GREETER", new Greeter("Hello"));
        final StringWriter result = new StringWriter();
        transformer.transform(
                new StreamSource(new StringReader("<name>uPortal</name>")),
                new StreamResult(result));

        assertEquals("Hello, uPortal", result.toString());
    }
}
