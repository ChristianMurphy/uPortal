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
package org.apereo.portal.layout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.apereo.portal.IUserIdentityStore;
import org.apereo.portal.IUserProfile;
import org.apereo.portal.layout.dao.IStylesheetDescriptorDao;
import org.apereo.portal.layout.dao.IStylesheetUserPreferencesDao;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.utils.cache.UsernameTaggedCacheEntryPurger;
import org.apereo.services.persondir.IPersonAttributes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A reset purges the user's cached layout, so the user's other sessions reload it from the database
 * instead of saving their old copy back (MOD-I363). The purge waits for the commit.
 */
public class UserLayoutHelperImplTest {
    private UserLayoutHelperImpl helper;
    private UsernameTaggedCacheEntryPurger purger;
    private IPersonAttributes student;

    @Before
    public void setUp() {
        final IUserIdentityStore userIdentityStore = mock(IUserIdentityStore.class);
        when(userIdentityStore.getPortalUID(any(IPerson.class), anyBoolean())).thenReturn(20);
        final IUserLayoutStore userLayoutStore = mock(IUserLayoutStore.class);
        when(userLayoutStore.getUserProfileByFname(any(IPerson.class), anyString()))
                .thenReturn(mock(IUserProfile.class));
        purger = mock(UsernameTaggedCacheEntryPurger.class);

        helper = new UserLayoutHelperImpl();
        helper.setUserIdentityStore(userIdentityStore);
        helper.setUserLayoutStore(userLayoutStore);
        helper.setStylesheetDescriptorDao(mock(IStylesheetDescriptorDao.class));
        helper.setStylesheetUserPreferencesDao(mock(IStylesheetUserPreferencesDao.class));
        helper.setUsernameTaggedCacheEntryPurger(purger);

        student = mock(IPersonAttributes.class);
        when(student.getName()).thenReturn("student");
        when(student.getAttributes())
                .thenReturn(Collections.singletonMap("username", List.<Object>of("student")));
    }

    @After
    public void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void testResetPurgesTheCachedLayoutAfterTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        helper.resetUserLayout(student);

        verify(purger, never()).purgeTaggedCacheEntries(anyString());
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(purger).purgeTaggedCacheEntries("student");
    }

    @Test
    public void testResetWithoutATransactionPurgesAtOnce() {
        helper.resetUserLayout(student);

        verify(purger).purgeTaggedCacheEntries("student");
    }
}
