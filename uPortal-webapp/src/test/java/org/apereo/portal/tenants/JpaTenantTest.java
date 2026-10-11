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
package org.apereo.portal.tenants;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.List;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaPortalTestApplicationContext.xml")
public class JpaTenantTest extends BasePortalJpaDaoTest {

    /**
     * Attribute names are stored with the "_" prefix of NullSafeStringType, like the values, as
     * Hibernate 4.3 stored them in existing uPortal databases.
     */
    @Test
    public void attributeKeysKeepNullSafePrefix() {
        final long id =
                this.executeInTransaction(
                        () -> {
                            final JpaTenant tenant = new JpaTenant();
                            tenant.setName("Tenant One");
                            tenant.setFname("tenant-one");
                            tenant.setAttribute("adminContactEmail", "admin@example.org");
                            getEntityManager().persist(tenant);
                            return tenant.getId();
                        });

        this.execute(
                () -> {
                    final List<?> rows =
                            getEntityManager()
                                    .createNativeQuery(
                                            "SELECT ATTR_NAME, ATTR_VALUE FROM UP_TENANT_ATTRIBUTES")
                                    .getResultList();
                    assertEquals(1, rows.size());
                    assertArrayEquals(
                            new Object[] {"_adminContactEmail", "_admin@example.org"},
                            (Object[]) rows.get(0));

                    getEntityManager().clear();
                    final JpaTenant tenant = getEntityManager().find(JpaTenant.class, id);
                    assertEquals("admin@example.org", tenant.getAttribute("adminContactEmail"));
                    return null;
                });
    }
}
