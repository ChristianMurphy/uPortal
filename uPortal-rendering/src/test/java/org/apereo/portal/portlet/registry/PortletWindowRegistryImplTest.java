/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.portlet.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.portlet.om.AbstractObjectId;
import org.apereo.portal.portlet.om.IPortletEntityId;
import org.apereo.portal.url.IPortalRequestUtils;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class PortletWindowRegistryImplTest {

    private static final class TestPortletEntityId extends AbstractObjectId
            implements IPortletEntityId {
        private static final long serialVersionUID = 1L;

        TestPortletEntityId(String id) {
            super(id);
        }
    }

    /** The error for a missing entity names the entity ID, not "null" (MOD-I105). */
    @Test
    public void statelessWindowForAMissingEntityNamesTheEntityId() {
        final HttpServletRequest request = new MockHttpServletRequest();
        final IPortalRequestUtils portalRequestUtils = mock(IPortalRequestUtils.class);
        when(portalRequestUtils.getOriginalPortletOrPortalRequest(request)).thenReturn(request);
        when(portalRequestUtils.getOriginalPortalRequest(request)).thenReturn(request);

        final PortletWindowRegistryImpl registry = new PortletWindowRegistryImpl();
        registry.setPortalRequestUtils(portalRequestUtils);
        // Knows no entities
        registry.setPortletEntityRegistry(mock(IPortletEntityRegistry.class));

        final PortletWindowIdImpl baseWindowId =
                new PortletWindowIdImpl(new TestPortletEntityId("u12l1n34"), null);
        try {
            registry.getOrCreateStatelessPortletWindow(request, baseWindowId);
            fail("Expected an IllegalArgumentException for the missing entity");
        } catch (IllegalArgumentException e) {
            assertEquals(
                    "No IPortletEntity could be found for u12l1n34"
                            + " while creating stateless portlet window for u12l1n34",
                    e.getMessage());
        }
    }
}
