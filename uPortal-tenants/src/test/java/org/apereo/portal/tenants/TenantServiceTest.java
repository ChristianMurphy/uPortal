/**
 * Licensed to Jasig under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Jasig
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.tenants;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * The following methods are excluded from unit testing.
 *
 * <p>1. createTenant 2. updateTenant
 */
public class TenantServiceTest {

    private static final Map<String, String> attributes;

    private static final Set<ITenant> tenants = new HashSet<>();

    @InjectMocks private TenantService tenantService;

    @Mock private ITenantDao tenantDao;

    @Mock private ITenant tenant;

    static {
        Map<String, String> attributeMap = new HashMap<>();
        attributeMap.put("tenant_attr_1", "value_1");
        attributeMap.put("tenant_attr_2", "value_2");
        attributeMap.put("tenant_attr_3", "value_3");
        attributes = Collections.unmodifiableMap(attributeMap);
    }

    @Before
    public void setup() throws Exception {
        tenants.clear();
        MockitoAnnotations.initMocks(this);
        when(tenant.getFname()).thenReturn("Tenant1");
        when(tenant.getName()).thenReturn("Tenant1 Tenant1");
        when(tenant.getAttributesMap()).thenReturn(attributes);
        tenants.add(tenant);
        when(tenantDao.getTenantByFName("Tenant1")).thenReturn(tenant);
        when(tenantDao.getTenantByName("Tenant1 Tenant1")).thenReturn(tenant);
        when(tenantDao.getAllTenants()).thenReturn(tenants);
    }

    /**
     * Tenant names are text: letters, combining marks, digits and spaces in any script, 5 to 32
     * characters counted as code points (MOD-I233, MOD-D49).
     */
    @Test
    public void tenantNamesAcceptLettersOfAnyScript() {
        for (final String name :
                new String[] {
                    "Universit\u00e9 Laval",
                    "Universite\u0301 Laval", // the accent as a combining mark
                    "\u5317\u6d77\u9053\u5927\u5b66",
                    "\u041c\u0413\u0423 \u0438\u043c\u0435\u043d\u0438 1755",
                    "\u062c\u0627\u0645\u0639\u0629 \u0627\u0644\u0642\u0627\u0647\u0631\u0629",
                    "\u00c9cole 42",
                    "Tenant Name 1",
                    // five letters outside the Basic Multilingual Plane: ten UTF-16 chars
                    "\ud840\udc00\ud840\udc01\ud840\udc02\ud840\udc03\ud840\udc04",
                    new String(new char[32]).replace('\0', '\u00e9'),
                }) {
            tenantService.validateName(name);
        }
    }

    @Test
    public void tenantNamesRefuseOtherCharactersAndLengths() {
        for (final String name :
                new String[] {
                    "abcd",
                    new String(new char[33]).replace('\0', '\u00e9'),
                    "Bad/Name",
                    "Tenant <b>",
                    "Tenant\tName",
                    "Smile \ud83d\ude00\ud83d\ude00",
                }) {
            try {
                tenantService.validateName(name);
                fail("Accepted the tenant name \"" + name + "\"");
            } catch (IllegalStateException expected) {
                // refused
            }
        }
    }

    @Test
    public void tenantFnamesStayLowerCaseAsciiIdentifiers() {
        tenantService.validateFname("universite_laval");
        for (final String fname :
                new String[] {"universit\u00e9_laval", "Upper_Case", "a b c d e"}) {
            try {
                tenantService.validateFname(fname);
                fail("Accepted the tenant fname \"" + fname + "\"");
            } catch (IllegalStateException expected) {
                // refused
            }
        }
    }

    @Test
    public void testGetTenantsList() {
        List<ITenant> tenants = tenantService.getTenantsList();
        assertEquals(tenants.size(), 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTenantByBlankFName() {
        tenantService.getTenantByFName("");
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateTenantNameSmall() {
        tenantService.validateName("5FF0");
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateTenantNameLarge() {
        tenantService.validateName("5FF09MU3KMR7UWMU3KREI37U0Q9KHUEG4MU3KMU3KWMU3KWMU3K");
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateFName() {
        tenantService.validateFname("W5FF?0");
    }

    @Test
    public void testNotExistsTenantName() {
        boolean exists = tenantService.fnameExists("");
        assertFalse(exists);
        exists = tenantService.fnameExists("Tenant1");
        assertTrue(exists);
    }
}
