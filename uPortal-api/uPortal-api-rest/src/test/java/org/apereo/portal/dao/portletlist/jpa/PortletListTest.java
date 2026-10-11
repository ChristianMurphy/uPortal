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
package org.apereo.portal.dao.portletlist.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apereo.portal.security.IPerson;
import org.junit.Test;

/**
 * A portlet list's name is text in any script (MOD-D49); its owner and item ids stay identifiers.
 */
public class PortletListTest {

    private static IPerson requester() {
        final IPerson person = mock(IPerson.class);
        when(person.getUserName()).thenReturn("admin");
        return person;
    }

    private static PortletList named(String name) {
        final PortletList list = new PortletList();
        list.setOwnerUsername("admin");
        list.setName(name);
        return list;
    }

    @Test
    public void namesAcceptAnyText() {
        for (final String name :
                new String[] {
                    "Favorites",
                    "Mes cours à l'été 2026",
                    "Lab & Research (fall)",
                    "Zoë Ñandú 李小龍 😀",
                    new String(new char[255]).replace('\0', 'é'),
                }) {
            final PortletList list = named(name);
            list.prepareForPersistence(requester());
            assertEquals(name, list.getName());
        }
    }

    @Test
    public void namesRefuseControlCharactersAndLengths() {
        for (final String name :
                new String[] {"Two\nLines", new String(new char[256]).replace('\0', 'x')}) {
            try {
                named(name).prepareForPersistence(requester());
                fail("Accepted the name \"" + name + "\"");
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
    }

    /** Owners are user names, often email-style with single sign-on (MOD-I270) */
    @Test
    public void ownersAcceptAnyUserName() {
        for (final String owner :
                new String[] {
                    "admin",
                    "jane.doe@example.edu",
                    "Jos\u00e9.N\u00fa\u00f1ez",
                    "\u674e\u5c0f\u9f8d"
                }) {
            final PortletList list = named("Favorites");
            list.setOwnerUsername(owner);
            list.prepareForPersistence(requester());
            assertEquals(owner, list.getOwnerUsername());
        }
    }

    @Test
    public void ownersRefuseSpacesAndTheMessageSaysWhy() {
        for (final String owner :
                new String[] {"bad owner", new String(new char[101]).replace('\0', 'x')}) {
            final PortletList list = named("Favorites");
            list.setOwnerUsername(owner);
            try {
                list.prepareForPersistence(requester());
                fail("Accepted the owner \"" + owner + "\"");
            } catch (IllegalArgumentException expected) {
                assertTrue(
                        expected.getMessage(),
                        expected.getMessage().contains("1 to 100 characters and no spaces"));
            }
        }
    }
}
