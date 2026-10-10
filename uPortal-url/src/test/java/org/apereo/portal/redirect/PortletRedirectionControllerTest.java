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
package org.apereo.portal.redirect;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Requests to the redirection servlet ({@code /s/*}) go through RequestMappingHandlerMapping, as
 * redirectionServletContext.xml configures it.
 */
public class PortletRedirectionControllerTest {

    private MockMvc mvc;

    @Before
    public void setUp() {
        final ExternalRedirectionUrl single = new ExternalRedirectionUrl();
        single.setUrl("https://example.org/find");
        single.setPathParameters(Collections.singletonList("username"));

        final ExternalRedirectionUrl pair = new ExternalRedirectionUrl();
        pair.setUrl("https://example.org/map");
        pair.setPathParameters(Arrays.asList("building", "room"));

        final PortletRedirectionController controller = new PortletRedirectionController();
        final Map<String, IRedirectionUrl> services = new HashMap<>();
        services.put("person", single);
        services.put("location", pair);
        controller.setServices(services);

        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void pathAfterTheServiceKeyFillsThePathParameter() throws Exception {
        mvc.perform(get("/s/person/jdoe").servletPath("/s"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("https://example.org/find?username=jdoe"));
    }

    @Test
    public void lastPathParameterTakesTheRestOfThePath() throws Exception {
        mvc.perform(get("/s/location/library/2/west").servletPath("/s"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("https://example.org/map?building=library&room=2%2Fwest"));
    }

    @Test
    public void serviceKeyAloneAddsNoPathParameter() throws Exception {
        mvc.perform(get("/s/person").servletPath("/s"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("https://example.org/find?username="));
    }

    @Test
    public void unknownServiceIsNotFound() throws Exception {
        mvc.perform(get("/s/nothing/here").servletPath("/s")).andExpect(status().isNotFound());
    }
}
