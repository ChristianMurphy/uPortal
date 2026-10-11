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
package org.apereo.portal.portlets.tenantmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Map;
import org.apereo.portal.tenants.TenantService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.portlet.MockActionRequest;
import org.springframework.mock.web.portlet.MockActionResponse;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A tenant name in any script gets an fname made of a-z, 0-9 and _ (MOD-I233). When the name has no
 * letters to make one from, the form asks for the fname.
 */
public class TenantManagerControllerTest {
    @InjectMocks private TenantManagerController controller;
    @Mock private TenantService tenantService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(
                controller, "tenantManagerAttributes", Collections.<String, String>emptyMap());
        doCallRealMethod().when(tenantService).validateName(anyString());
        doCallRealMethod().when(tenantService).validateFname(anyString());
    }

    private MockActionRequest add(String name, String fname) {
        final MockActionRequest request = new MockActionRequest();
        if (fname != null) {
            request.setParameter("fname", fname);
        }
        controller.doAddTenant(
                request, new MockActionResponse(), request.getPortletSession(), name, fname);
        return request;
    }

    private void createdWithFname(String name, String fname) {
        verify(tenantService).createTenant(eq(name), eq(fname), anyMap(), any(), any());
    }

    @Test
    public void makesTheFnameFromALatinNameWithAccents() {
        add("Université Laval", null);
        createdWithFname("Université Laval", "universite_laval");
    }

    @Test
    public void foldsLatinLettersWithoutADecomposition() {
        add("Universität Göttingen", null);
        createdWithFname("Universität Göttingen", "universitat_gottingen");
        add("Łódź Tech", null);
        createdWithFname("Łódź Tech", "lodz_tech");
        add("Ærø Øst Straße", null);
        createdWithFname("Ærø Øst Straße", "aero_ost_strasse");
    }

    @Test
    public void usesTheFnameTheAdministratorTyped() {
        add("北海道大学", "hokkaido_u");
        createdWithFname("北海道大学", "hokkaido_u");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void asksForAnFnameWhenTheNameHasNoLatinLetters() {
        final MockActionRequest request = add("北海道大学", null);
        verify(tenantService, never()).createTenant(any(), any(), anyMap(), any(), any());
        final Map<String, Object> invalid =
                (Map<String, Object>) request.getPortletSession().getAttribute("invalidFields");
        assertNotNull(invalid);
        assertEquals(Collections.singleton("fname"), invalid.keySet());
    }
}
