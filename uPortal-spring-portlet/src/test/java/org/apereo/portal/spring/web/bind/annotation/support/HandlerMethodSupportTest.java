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
package org.apereo.portal.spring.web.bind.annotation.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * The annotation-handler support classes that Spring Framework 5 removed and the vendored portlet
 * {@code AnnotationMethodHandlerAdapter} builds on.
 */
public class HandlerMethodSupportTest {

    @Controller
    public static class GreetingController {
        boolean binderInitialised;

        @InitBinder
        public void initBinder(WebDataBinder binder) {
            binderInitialised = true;
        }

        @ModelAttribute("salutation")
        public String salutation() {
            return "Hello";
        }

        @RequestMapping("greet")
        public String greet(
                @RequestParam("name") String name,
                @ModelAttribute("salutation") String salutation) {
            return salutation + " " + name;
        }
    }

    private static Method greetMethod() throws NoSuchMethodException {
        return GreetingController.class.getMethod("greet", String.class, String.class);
    }

    @Test
    public void resolverFindsAnnotatedMethods() throws Exception {
        final HandlerMethodResolver resolver = new HandlerMethodResolver();
        resolver.init(GreetingController.class);

        assertTrue(resolver.hasHandlerMethods());
        assertEquals(1, resolver.getHandlerMethods().size());
        assertTrue(resolver.getHandlerMethods().contains(greetMethod()));
        assertEquals(1, resolver.getInitBinderMethods().size());
        assertEquals(1, resolver.getModelAttributeMethods().size());
    }

    @Test
    public void invokerResolvesArgumentsAndModelAttributes() throws Exception {
        final HandlerMethodResolver resolver = new HandlerMethodResolver();
        resolver.init(GreetingController.class);
        final GreetingController controller = new GreetingController();
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("name", "World");
        final ExtendedModelMap model = new ExtendedModelMap();

        final Object result =
                new HandlerMethodInvoker(resolver)
                        .invokeHandlerMethod(
                                greetMethod(), controller, new ServletWebRequest(request), model);

        assertEquals("Hello World", result);
        assertEquals("Hello", model.get("salutation"));
        assertTrue(controller.binderInitialised);
    }

    @Test
    public void missingParameterIsReportedWithTheHandlerMethod() throws Exception {
        final HandlerMethodResolver resolver = new HandlerMethodResolver();
        resolver.init(GreetingController.class);

        try {
            new HandlerMethodInvoker(resolver)
                    .invokeHandlerMethod(
                            greetMethod(),
                            new GreetingController(),
                            new ServletWebRequest(new MockHttpServletRequest()),
                            new ExtendedModelMap());
            fail("expected HandlerMethodInvocationException");
        } catch (HandlerMethodInvocationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("greet"));
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }
}
