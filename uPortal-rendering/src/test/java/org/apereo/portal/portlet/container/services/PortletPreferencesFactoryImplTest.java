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
package org.apereo.portal.portlet.container.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.portal.security.ISecurityContext;
import org.junit.Before;
import org.junit.Test;

/** Tests which users {@link PortletPreferencesFactoryImpl} treats as guests. */
public class PortletPreferencesFactoryImplTest {

    private final IPersonManager personManager = mock(IPersonManager.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final PortletPreferencesFactoryImpl factory = new PortletPreferencesFactoryImpl();

    @Before
    public void setUp() {
        factory.setPersonManager(personManager);
    }

    @Test
    public void authenticatedUserIsNotGuest() {
        final IPerson person = personWithSecurityContext(true);
        when(personManager.getPerson(request)).thenReturn(person);

        assertFalse(factory.isGuestUser(request));
        assertFalse(factory.isStoreInMemory(request));
    }

    @Test
    public void unauthenticatedUserIsGuest() {
        final IPerson person = personWithSecurityContext(false);
        when(personManager.getPerson(request)).thenReturn(person);

        assertTrue(factory.isGuestUser(request));
        assertTrue(factory.isStoreInMemory(request));
    }

    /**
     * MOD-I90: the person manager returns null when it can't create a person, for example while a
     * re-login invalidates the session that a render is still using.
     */
    @Test
    public void missingPersonIsGuest() {
        when(personManager.getPerson(request)).thenReturn(null);

        assertTrue(factory.isGuestUser(request));
        assertTrue(factory.isStoreInMemory(request));
    }

    /** A person can have no security context, for example one created from an OIDC ID token. */
    @Test
    public void personWithoutSecurityContextIsGuest() {
        final IPerson person = mock(IPerson.class);
        when(person.getSecurityContext()).thenReturn(null);
        when(personManager.getPerson(request)).thenReturn(person);

        assertTrue(factory.isGuestUser(request));
        assertTrue(factory.isStoreInMemory(request));
    }

    private static IPerson personWithSecurityContext(boolean authenticated) {
        final ISecurityContext securityContext = mock(ISecurityContext.class);
        when(securityContext.isAuthenticated()).thenReturn(authenticated);
        final IPerson person = mock(IPerson.class);
        when(person.getSecurityContext()).thenReturn(securityContext);
        return person;
    }
}
