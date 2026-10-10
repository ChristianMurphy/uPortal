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
package org.apereo.portal.spring.web.portlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.apereo.portal.spring.web.portlet.mvc.SimpleControllerHandlerAdapter;
import org.apereo.portal.spring.web.portlet.mvc.annotation.AnnotationMethodHandlerAdapter;
import org.apereo.portal.spring.web.portlet.mvc.annotation.AnnotationMethodHandlerExceptionResolver;
import org.apereo.portal.spring.web.portlet.mvc.annotation.DefaultAnnotationHandlerMapping;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * The default strategies of {@link DispatcherPortlet} are named in DispatcherPortlet.properties,
 * which the compiler does not check. Every portlet context that declares no bean of a strategy type
 * gets these defaults, so they must resolve to the vendored classes.
 */
public class DispatcherPortletDefaultStrategiesTest {

    private final DispatcherPortlet dispatcherPortlet = new DispatcherPortlet();
    private final StaticApplicationContext context = new StaticApplicationContext();

    @Before
    public void setUp() {
        context.refresh();
    }

    @Test
    public void defaultHandlerMappingIsTheAnnotationHandlerMapping() {
        List<HandlerMapping> mappings =
                dispatcherPortlet.getDefaultStrategies(context, HandlerMapping.class);
        assertEquals(1, mappings.size());
        assertTrue(mappings.get(0) instanceof DefaultAnnotationHandlerMapping);
    }

    @Test
    public void defaultHandlerAdaptersAreTheControllerAndAnnotationAdapters() {
        List<HandlerAdapter> adapters =
                dispatcherPortlet.getDefaultStrategies(context, HandlerAdapter.class);
        assertEquals(2, adapters.size());
        assertTrue(adapters.get(0) instanceof SimpleControllerHandlerAdapter);
        assertTrue(adapters.get(1) instanceof AnnotationMethodHandlerAdapter);
    }

    @Test
    public void defaultExceptionResolverIsTheAnnotationExceptionResolver() {
        List<HandlerExceptionResolver> resolvers =
                dispatcherPortlet.getDefaultStrategies(context, HandlerExceptionResolver.class);
        assertEquals(1, resolvers.size());
        assertTrue(resolvers.get(0) instanceof AnnotationMethodHandlerExceptionResolver);
    }

    @Test
    public void defaultViewResolverIsTheInternalResourceViewResolver() {
        List<ViewResolver> resolvers =
                dispatcherPortlet.getDefaultStrategies(context, ViewResolver.class);
        assertEquals(1, resolvers.size());
        assertTrue(resolvers.get(0) instanceof InternalResourceViewResolver);
    }
}
