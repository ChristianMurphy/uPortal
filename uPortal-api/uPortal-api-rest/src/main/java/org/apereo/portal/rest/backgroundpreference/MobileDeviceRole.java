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
package org.apereo.portal.rest.backgroundpreference;

import org.apereo.portal.groups.IEntityGroup;
import org.apereo.portal.groups.IGroupMember;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.services.GroupService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Whether a user is in the "Mobile Device Access" group, who get the {@code mobile*} background
 * set. Portlets ask {@code isUserInRole("Mobile Device Access")}, which the portal answers with the
 * group of that name (PortalHttpServletRequestWrapper); REST requests are not wrapped that way, so
 * this asks the group service the same way.
 */
@Component
public class MobileDeviceRole {

    public static final String GROUP_NAME = "Mobile Device Access";

    public boolean includes(IPerson person) {
        final @Nullable IEntityGroup group = findGroup();
        if (group == null) {
            return false;
        }
        final IGroupMember member = GroupService.getGroupMember(person.getEntityIdentifier());
        return member != null && member.isDeepMemberOf(group);
    }

    private static @Nullable IEntityGroup findGroup() {
        final @Nullable IEntityGroup group = GroupService.findGroup(GROUP_NAME);
        if (group != null) {
            return group;
        }
        final org.apereo.portal.EntityIdentifier[] results =
                GroupService.searchForGroups(
                        GROUP_NAME, GroupService.SearchMethod.DISCRETE, IPerson.class);
        if (results == null || results.length == 0) {
            return null;
        }
        final @Nullable IGroupMember member = GroupService.getGroupMember(results[0]);
        return member == null || !member.isGroup() ? null : member.asGroup();
    }
}
