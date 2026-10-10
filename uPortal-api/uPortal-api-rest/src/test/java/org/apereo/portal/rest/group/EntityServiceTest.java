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
package org.apereo.portal.rest.group;

import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Tests {@link EntityService}'s handling of entities it can't look up a name for (MOD-I100). */
public class EntityServiceTest {

    private final EntityService entityService = EntityService.instance();

    @Test
    public void lookupEntityNameReturnsNullForAnUnknownTypeWithAnId() {
        assertNull(entityService.lookupEntityName(entity("no-such-type", "admin")));
    }

    @Test
    public void lookupEntityNameReturnsNullForANullTypeWithAnId() {
        assertNull(entityService.lookupEntityName(entity(null, "admin")));
    }

    @Test
    public void lookupEntityNameReturnsNullForAKnownTypeWithABlankId() {
        assertNull(entityService.lookupEntityName(entity("person", " ")));
    }

    @Test
    public void lookupEntityNameReturnsNullForANullEntity() {
        assertNull(entityService.lookupEntityName(null));
    }

    private static Entity entity(String type, String id) {
        final Entity result = new EntityImpl();
        result.setEntityType(type);
        result.setId(id);
        return result;
    }
}
