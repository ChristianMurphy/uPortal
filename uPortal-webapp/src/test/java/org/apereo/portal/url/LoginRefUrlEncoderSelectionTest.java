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
package org.apereo.portal.url;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import org.apereo.portal.spring.context.SpringXmlContextValidator.DefinitionsOnlyContext;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * MOD-I180: {@code login.refUrlEncoder} selects how mvcContext.xml sends a guest's deep link to the
 * Sign In URL, and the URL canonicalizing filter uses that selection.
 */
public class LoginRefUrlEncoderSelectionTest {

    private static ConfigurableListableBeanFactory mvcContext(String refUrlEncoder) {
        final Properties properties = new Properties();
        properties.setProperty("portal.allServerNames", "localhost:8080");
        properties.setProperty("org.apereo.portal.channels.CLogin.CasLoginUrl", "/uPortal/p/login");
        if (refUrlEncoder != null) {
            properties.setProperty("login.refUrlEncoder", refUrlEncoder);
        }
        final DefinitionsOnlyContext context =
                new DefinitionsOnlyContext(
                        null, new ClassPathResource("properties/contexts/mvcContext.xml"));
        context.registerBeanDefinition(
                "testPlaceholders",
                BeanDefinitionBuilder.genericBeanDefinition(
                                PropertySourcesPlaceholderConfigurer.class)
                        .addPropertyValue("properties", properties)
                        .getBeanDefinition());
        context.loadDefinitions();
        return context.getBeanFactory();
    }

    private static MockHttpServletRequest deepLink() {
        final MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/uPortal/p/user-administration");
        request.setContextPath("/uPortal");
        return request;
    }

    @Test
    public void casIsTheDefault() throws Exception {
        final ConfigurableListableBeanFactory beans = mvcContext(null);
        final Object encoder = beans.getBean("loginRefUrlEncoder");
        assertTrue(encoder.getClass().getName(), encoder instanceof CasLoginRefUrlEncoder);
    }

    @Test
    public void localSendsADeepLinkToTheLoginFormWithARefUrl() throws Exception {
        final ConfigurableListableBeanFactory beans = mvcContext("local");
        final LoginRefUrlEncoder encoder =
                beans.getBean("loginRefUrlEncoder", LoginRefUrlEncoder.class);
        assertEquals(
                "/uPortal/p/login?refUrl=%2FuPortal%2Fp%2Fuser-administration",
                encoder.encodeLoginAndRefUrl(deepLink()));
    }

    @Test
    public void theFilterUsesTheSelectedEncoder() {
        final Object reference =
                mvcContext("local")
                        .getBeanDefinition("urlCanonicalizingFilter")
                        .getPropertyValues()
                        .get("loginRefUrlEncoder");
        assertEquals("loginRefUrlEncoder", ((RuntimeBeanReference) reference).getBeanName());
    }

    @Test(expected = NoSuchBeanDefinitionException.class)
    public void anUnknownValueFailsAtStartup() {
        mvcContext("kerberos").getBean("loginRefUrlEncoder");
    }
}
