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
package org.apereo.portal.portlet.dao.jpa;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.persistence.EntityManagerFactory;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao;
import org.apereo.portal.portlet.dao.IPortletTypeDao;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionId;
import org.apereo.portal.portlet.om.IPortletLifecycleEntry;
import org.apereo.portal.portlet.om.IPortletType;
import org.apereo.portal.portlet.om.PortletLifecycleState;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * A portlet definition's lifecycle with the portal's second-level cache turned on. The entries are
 * immutable and cached read-only: a change of state adds an entry and removes the scheduled ones it
 * overtakes, and never updates one.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaPortletPreferencesCacheTestContext.xml")
public class PortletLifecycleCacheTest extends BasePortalJpaDaoTest {
    private static final long HOUR = 3600_000L;

    @Autowired private IPortletTypeDao jpaChannelTypeDao;
    @Autowired private IPortletDefinitionDao jpaPortletDefinitionDao;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @After
    public void deleteDefinitions() {
        execute(
                () -> {
                    for (final IPortletDefinition definition :
                            jpaPortletDefinitionDao.getPortletDefinitions()) {
                        jpaPortletDefinitionDao.deletePortletDefinition(definition);
                    }
                    for (final IPortletType type : jpaChannelTypeDao.getPortletTypes()) {
                        jpaChannelTypeDao.deletePortletType(type);
                    }
                    return null;
                });
        this.entityManagerFactory
                .unwrap(SessionFactoryImplementor.class)
                .getCache()
                .evictAllRegions();
    }

    @Test
    public void aChangeOfStateReplacesTheScheduledEntries() {
        final IPerson user = mock(IPerson.class);
        when(user.getID()).thenReturn(7);
        final long now = System.currentTimeMillis();

        final IPortletDefinitionId id =
                executeInTransaction(
                        () -> {
                            final IPortletType type =
                                    jpaChannelTypeDao.createPortletType("BaseType", "foobar");
                            final IPortletDefinition created =
                                    new PortletDefinitionImpl(
                                            type,
                                            "lifecycle-cache",
                                            "Lifecycle Cache",
                                            "Lifecycle Cache",
                                            "/uPortal",
                                            "lifecycleCache",
                                            true);
                            created.clearLifecycle();
                            created.updateLifecycleState(
                                    PortletLifecycleState.PUBLISHED,
                                    user,
                                    new Date(now - 2 * HOUR));
                            created.updateLifecycleState(
                                    PortletLifecycleState.EXPIRED, user, new Date(now + 2 * HOUR));
                            jpaPortletDefinitionDao.savePortletDefinition(created);
                            return created.getPortletDefinitionId();
                        });

        final List<PortletLifecycleState> scheduled =
                Arrays.asList(PortletLifecycleState.PUBLISHED, PortletLifecycleState.EXPIRED);
        assertEquals(scheduled, lifecycle(id));
        assertEquals("Read again, from the cache", scheduled, lifecycle(id));

        // Maintenance from now on, which cancels the expiry scheduled for later
        executeInTransaction(
                () -> {
                    final IPortletDefinition definition =
                            jpaPortletDefinitionDao.getPortletDefinition(id);
                    definition.updateLifecycleState(
                            PortletLifecycleState.MAINTENANCE, user, new Date(now - HOUR));
                    jpaPortletDefinitionDao.savePortletDefinition(definition);
                    return null;
                });

        final List<PortletLifecycleState> changed =
                Arrays.asList(PortletLifecycleState.PUBLISHED, PortletLifecycleState.MAINTENANCE);
        assertEquals(changed, lifecycle(id));
        assertEquals("Read again, from the cache", changed, lifecycle(id));
    }

    /** The states of the definition's lifecycle entries, as a new session loads them. */
    private List<PortletLifecycleState> lifecycle(IPortletDefinitionId id) {
        return executeInTransaction(
                () -> {
                    final List<PortletLifecycleState> states = new ArrayList<>();
                    for (final IPortletLifecycleEntry entry :
                            jpaPortletDefinitionDao.getPortletDefinition(id).getLifecycle()) {
                        states.add(entry.getLifecycleState());
                    }
                    return states;
                });
    }
}
