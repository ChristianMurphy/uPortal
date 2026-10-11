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
package org.apereo.portal.portlet.dao.trans;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.IUserPreferencesManager;
import org.apereo.portal.layout.IUserLayoutManager;
import org.apereo.portal.layout.node.IUserLayoutChannelDescription;
import org.apereo.portal.portlet.dao.IPortletEntityDao;
import org.apereo.portal.portlet.registry.IPortletDefinitionRegistry;
import org.apereo.portal.url.IPortalRequestUtils;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.junit.Before;
import org.junit.Test;

/**
 * A transient layout node id ("ctf...") that the user's layout no longer has, for example from a
 * page rendered before a guest session expired, finds no portlet entity. Before the fix it threw a
 * NullPointerException, which the portal logged for {@code layout.json} with {@code user=null}.
 */
public class TransientPortletEntityDaoTest {
    private TransientPortletEntityDao dao;
    private IPortletEntityDao delegate;
    private IUserLayoutManager layoutManager;
    private IPortletDefinitionRegistry definitions;

    @Before
    public void setUp() {
        delegate = mock(IPortletEntityDao.class);
        layoutManager = mock(IUserLayoutManager.class);
        definitions = mock(IPortletDefinitionRegistry.class);
        final IPortalRequestUtils requestUtils = mock(IPortalRequestUtils.class);
        final IUserInstanceManager instances = mock(IUserInstanceManager.class);
        final IUserInstance instance = mock(IUserInstance.class);
        final IUserPreferencesManager preferences = mock(IUserPreferencesManager.class);
        when(requestUtils.getCurrentPortalRequest()).thenReturn(mock(HttpServletRequest.class));
        when(instances.getUserInstance(any(HttpServletRequest.class))).thenReturn(instance);
        when(instance.getPreferencesManager()).thenReturn(preferences);
        when(preferences.getUserLayoutManager()).thenReturn(layoutManager);
        dao = new TransientPortletEntityDao();
        dao.setDelegatePortletEntityDao(delegate);
        dao.setPortletDefinitionRegistry(definitions);
        dao.setUserInstanceManager(instances);
        dao.setPortalRequestUtils(requestUtils);
    }

    @Test
    public void aTransientNodeTheLayoutDoesNotHaveFindsNoEntity() {
        when(layoutManager.getNode("ctf3")).thenReturn(null);
        assertNull(dao.getPortletEntity("ctf3", 1));
        verifyNoInteractions(delegate);
    }

    @Test
    public void aTransientNodeOfADeletedPortletFindsNoEntity() {
        final IUserLayoutChannelDescription node = mock(IUserLayoutChannelDescription.class);
        when(node.getChannelPublishId()).thenReturn("42");
        when(layoutManager.getNode("ctf4")).thenReturn(node);
        when(definitions.getPortletDefinition("42")).thenReturn(null);
        assertNull(dao.getPortletEntity("ctf4", 1));
        verifyNoInteractions(delegate);
    }
}
