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
package org.apereo.portal.events.aggr.golden;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import javax.naming.CompositeName;
import javax.naming.InvalidNameException;
import org.apereo.portal.groups.ICompositeGroupService;
import org.apereo.portal.groups.IEntityGroup;
import org.apereo.portal.groups.IGroupMember;
import org.springframework.beans.factory.FactoryBean;

/**
 * The groups service for the aggregation goldens. Login events carry group keys such as {@code
 * local.Students}; {@code findGroup} returns null, so {@code JpaAggregatedGroupLookupDao} maps them
 * by parsing the key, as it does for a group that no longer exists. A user without a login event
 * (the guest) is a member of local.Everyone.
 */
public class GoldenGroupServiceFactory implements FactoryBean<ICompositeGroupService> {
    @Override
    public ICompositeGroupService getObject() throws InvalidNameException {
        final IEntityGroup everyone = mock(IEntityGroup.class);
        when(everyone.getServiceName()).thenReturn(new CompositeName("local"));
        when(everyone.getName()).thenReturn("Everyone");
        final IGroupMember member = mock(IGroupMember.class);
        final ICompositeGroupService groupService = mock(ICompositeGroupService.class);
        when(groupService.getGroupMember(anyString(), any())).thenReturn(member);
        when(groupService.findParentGroups(member))
                .thenAnswer(invocation -> Collections.singletonList(everyone).iterator());
        return groupService;
    }

    @Override
    public Class<?> getObjectType() {
        return ICompositeGroupService.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
