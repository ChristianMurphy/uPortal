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
package org.apereo.portal.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class HealthCheckControllerTest {

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        final HealthCheckController controller = new HealthCheckController();
        ReflectionTestUtils.setField(controller, "checkers", Collections.emptyList());
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * web.xml maps the servlet to exactly /health-check, so that is the path the controller sees.
     */
    @Test
    public void answersOnTheServletsExactMapping() throws Exception {
        this.mockMvc
                .perform(get("/health-check").servletPath("/health-check"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{}"));
    }

    @Test
    public void answersWithDetails() throws Exception {
        this.mockMvc
                .perform(get("/health-check").servletPath("/health-check").param("detail", "ALL"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
