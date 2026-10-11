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
package org.apereo.portal.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apereo.portal.portlet.registry.IPortletDefinitionRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** /api/v4-3/portlet/{fname}.html answers HTML whatever the request's Accept header says. */
@RunWith(MockitoJUnitRunner.class)
public class PortletsRESTControllerHtmlTest {

    @InjectMocks private PortletsRESTController portletsRESTController;

    @Mock private IPortletDefinitionRegistry portletDefinitionRegistry;

    @Test
    public void renderedPortletIsHtml() throws Exception {
        MockMvcBuilders.standaloneSetup(this.portletsRESTController)
                .build()
                .perform(get("/v4-3/portlet/does-not-exist.html"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("text/html;charset=ISO-8859-1"))
                .andExpect(content().string("Portlet not found"));
    }
}
