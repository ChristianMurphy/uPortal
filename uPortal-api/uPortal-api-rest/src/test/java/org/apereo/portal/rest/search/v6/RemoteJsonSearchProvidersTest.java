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
package org.apereo.portal.rest.search.v6;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apereo.portal.spring.context.support.PortalPropertySourcesPlaceholderConfigurer;
import org.junit.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockPropertySource;

/**
 * {@link RemoteJsonSearchProviders} in contexts laid out as the portal's: the root context holds
 * {@code primaryPropertyPlaceholderConfigurer} and the {@code propertyResolver} bean made from it,
 * and the MVC servlet's context, which scans {@code org.apereo.portal.rest}, holds a placeholder
 * configurer of its own.
 */
public class RemoteJsonSearchProvidersTest {

    private static GenericApplicationContext rootContext(MockPropertySource properties) {
        final GenericApplicationContext root = new GenericApplicationContext();
        final RootBeanDefinition configurer =
                new RootBeanDefinition(PortalPropertySourcesPlaceholderConfigurer.class);
        configurer.getPropertyValues().add("properties", properties.getSource());
        root.registerBeanDefinition("primaryPropertyPlaceholderConfigurer", configurer);
        final RootBeanDefinition resolver = new RootBeanDefinition();
        resolver.setFactoryBeanName("primaryPropertyPlaceholderConfigurer");
        resolver.setFactoryMethodName("getPropertyResolver");
        root.registerBeanDefinition("propertyResolver", resolver);
        root.refresh();
        return root;
    }

    private static RemoteJsonSearchProviders inServletContext(MockPropertySource properties) {
        final GenericApplicationContext servlet =
                new GenericApplicationContext(rootContext(properties));
        AnnotationConfigUtils.registerAnnotationConfigProcessors(servlet);
        // The servlet context's own configurer, as <bean
        // parent="primaryPropertyPlaceholderConfigurer"/>
        servlet.registerBeanDefinition(
                "servletConfigurer",
                new RootBeanDefinition(PortalPropertySourcesPlaceholderConfigurer.class));
        servlet.registerBeanDefinition(
                "remoteJsonSearchProviders",
                new RootBeanDefinition(RemoteJsonSearchProviders.class));
        servlet.refresh();
        return servlet.getBean(RemoteJsonSearchProviders.class);
    }

    @Test
    public void startsInTheServletContextWithoutProviders() {
        assertTrue(inServletContext(new MockPropertySource()).getStrategies().isEmpty());
    }

    @Test
    public void readsTheProvidersFromThePortalProperties() {
        final MockPropertySource properties =
                new MockPropertySource()
                        .withProperty(RemoteJsonSearchProviders.PROVIDERS_PROPERTY, "web, news")
                        .withProperty(
                                "org.apereo.portal.search.remote.web.url",
                                "https://search.example.edu/?q={query}")
                        .withProperty(
                                "org.apereo.portal.search.remote.news.url",
                                "https://news.example.edu/find?q={query}");

        assertEquals(2, inServletContext(properties).getStrategies().size());
    }
}
