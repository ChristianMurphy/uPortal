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
package org.apereo.portal.security.provider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apereo.portal.persondir.ILocalAccountDao;
import org.apereo.portal.persondir.ILocalAccountPerson;
import org.apereo.portal.security.IPortalPasswordService;
import org.apereo.portal.security.PortalPasswordServiceImpl;
import org.apereo.portal.spring.locator.LocalAccountDaoLocator;
import org.apereo.portal.spring.locator.PortalPasswordServiceLocator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A local account whose password has accented letters, a non-Latin script and a character outside
 * the Basic Multilingual Plane signs in (MOD-D49). The password is hashed as UTF-8 and has to reach
 * the check as the same text, whatever the JVM's default charset.
 */
public class SimpleSecurityContextTest {
    private static final String PASSWORD = "Zoë Ñandú 李小龍 😀";

    private LocalAccountDaoLocator accounts;
    private PortalPasswordServiceLocator passwords;

    @Before
    public void setUp() throws Exception {
        final IPortalPasswordService passwordService = new PortalPasswordServiceImpl();
        final ILocalAccountPerson zoe = mock(ILocalAccountPerson.class);
        when(zoe.getPassword()).thenReturn(passwordService.encryptPassword(PASSWORD));
        final ILocalAccountDao dao = mock(ILocalAccountDao.class);
        when(dao.getPerson("zoe")).thenReturn(zoe);
        accounts = new LocalAccountDaoLocator(dao);
        accounts.afterPropertiesSet();
        passwords = new PortalPasswordServiceLocator(passwordService);
        passwords.afterPropertiesSet();
    }

    @After
    public void tearDown() throws Exception {
        accounts.destroy();
        passwords.destroy();
    }

    private static boolean signIn(String password) throws Exception {
        final SimpleSecurityContext context = new SimpleSecurityContext();
        context.getPrincipalInstance().setUID("zoe");
        context.getOpaqueCredentialsInstance().setCredentials(password);
        context.authenticate();
        return context.isAuthenticated();
    }

    @Test
    public void aUserWithANonAsciiPasswordSignsIn() throws Exception {
        assertTrue(signIn(PASSWORD));
    }

    @Test
    public void anotherPasswordDoesNotSignIn() throws Exception {
        assertFalse(signIn("Zoe Nandu"));
    }
}
