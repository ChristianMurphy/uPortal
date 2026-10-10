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
package org.apereo.portal.spring.web.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Map;
import javax.servlet.ServletException;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.View;

/** The bridge servlet that renders portlet views; Spring Framework 5 removed it. */
public class ViewRendererServletTest {

    @Test
    public void rendersTheViewAndModelFromRequestAttributes() throws Exception {
        final View view = mock(View.class);
        final Map<String, Object> model = Collections.singletonMap("key", "value");
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setAttribute(ViewRendererServlet.VIEW_ATTRIBUTE, view);
        request.setAttribute(ViewRendererServlet.MODEL_ATTRIBUTE, model);
        final MockHttpServletResponse response = new MockHttpServletResponse();

        new ViewRendererServlet().service(request, response);

        verify(view).render(model, request, response);
    }

    @Test
    public void failsWithoutAView() throws Exception {
        try {
            new ViewRendererServlet()
                    .service(
                            new MockHttpServletRequest("POST", "/"), new MockHttpServletResponse());
            fail("expected ServletException");
        } catch (ServletException e) {
            assertTrue(e.getMessage().contains("View is null"));
        }
    }

    @Test
    public void exposesThePortletContextUnderDispatcherServletsAttribute() {
        // Spring's JSP tags find the portlet's application context under this name
        assertEquals(
                DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE,
                ViewRendererServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE);
    }
}
