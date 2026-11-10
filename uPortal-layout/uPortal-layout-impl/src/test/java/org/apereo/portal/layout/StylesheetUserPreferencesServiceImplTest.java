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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.IUserPreferencesManager;
import org.apereo.portal.IUserProfile;
import org.apereo.portal.layout.IStylesheetUserPreferencesService.PreferencesScope;
import org.apereo.portal.layout.dao.IStylesheetDescriptorDao;
import org.apereo.portal.layout.dao.IStylesheetUserPreferencesDao;
import org.apereo.portal.layout.om.ILayoutAttributeDescriptor;
import org.apereo.portal.layout.om.IOutputPropertyDescriptor;
import org.apereo.portal.layout.om.IStylesheetData.Scope;
import org.apereo.portal.layout.om.IStylesheetDescriptor;
import org.apereo.portal.layout.om.IStylesheetParameterDescriptor;
import org.apereo.portal.layout.om.IStylesheetUserPreferences;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.apereo.portal.utils.IFragmentDefinitionUtils;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/** */
public class StylesheetUserPreferencesServiceImplTest {

    /**
     * @throws Exception
     */
    @Test
    public void testThemeStylesheetUserPreferences() throws Exception {
        // Setup mocks
        final HttpServletRequest request = new MockHttpServletRequest();
        request.getSession(); // initialize the session
        final IStylesheetDescriptorDao stylesheetDescriptorDao =
                mock(IStylesheetDescriptorDao.class);
        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);
        final IStylesheetUserPreferencesDao stylesheetUserPreferencesDao =
                mock(IStylesheetUserPreferencesDao.class);
        final IFragmentDefinitionUtils fragmentUtils = mock(IFragmentDefinitionUtils.class);

        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstanceManager.getUserInstance(request)).thenReturn(userInstance);

        final IPerson person = mock(IPerson.class);
        when(userInstance.getPerson()).thenReturn(person);

        final IUserPreferencesManager preferencesManager = mock(IUserPreferencesManager.class);
        when(userInstance.getPreferencesManager()).thenReturn(preferencesManager);

        final IUserProfile userProfile = mock(IUserProfile.class);
        when(preferencesManager.getUserProfile()).thenReturn(userProfile);

        final IUserLayoutManager userLayoutManager = mock(IUserLayoutManager.class);
        when(preferencesManager.getUserLayoutManager()).thenReturn(userLayoutManager);

        final IUserLayout userLayout = mock(IUserLayout.class);
        when(userLayoutManager.getUserLayout()).thenReturn(userLayout);

        when(userProfile.getThemeStylesheetId()).thenReturn(1);

        final IStylesheetDescriptor stylesheetDescriptor = mock(IStylesheetDescriptor.class);
        when(stylesheetDescriptorDao.getStylesheetDescriptor(1)).thenReturn(stylesheetDescriptor);

        final ILayoutAttributeDescriptor skinLayoutAttributeDescriptor =
                mock(ILayoutAttributeDescriptor.class);
        when(stylesheetDescriptor.getLayoutAttributeDescriptor("minimized"))
                .thenReturn(skinLayoutAttributeDescriptor);
        when(skinLayoutAttributeDescriptor.getName()).thenReturn("minimized");
        when(skinLayoutAttributeDescriptor.getScope()).thenReturn(Scope.REQUEST);
        when(skinLayoutAttributeDescriptor.getTargetElementNames())
                .thenReturn(Collections.singleton("folder"));

        final IOutputPropertyDescriptor mediaOutputPropertyDescriptor =
                mock(IOutputPropertyDescriptor.class);
        when(stylesheetDescriptor.getOutputPropertyDescriptor("media"))
                .thenReturn(mediaOutputPropertyDescriptor);
        when(mediaOutputPropertyDescriptor.getName()).thenReturn("media");
        when(mediaOutputPropertyDescriptor.getScope()).thenReturn(Scope.SESSION);

        final IStylesheetParameterDescriptor skinStylesheetParameterDescriptor =
                mock(IStylesheetParameterDescriptor.class);
        when(stylesheetDescriptor.getStylesheetParameterDescriptor("skin"))
                .thenReturn(skinStylesheetParameterDescriptor);
        when(skinStylesheetParameterDescriptor.getName()).thenReturn("media");
        when(skinStylesheetParameterDescriptor.getScope()).thenReturn(Scope.PERSISTENT);

        final IStylesheetUserPreferences persistentStylesheetUserPreferences =
                mock(IStylesheetUserPreferences.class);
        when(stylesheetUserPreferencesDao.createStylesheetUserPreferences(
                        stylesheetDescriptor, person, userProfile))
                .thenReturn(persistentStylesheetUserPreferences);
        when(stylesheetUserPreferencesDao.getStylesheetUserPreferences(
                        stylesheetDescriptor, person, userProfile))
                .thenReturn(persistentStylesheetUserPreferences);
        when(persistentStylesheetUserPreferences.getStylesheetParameter("skin"))
                .thenReturn(null)
                .thenReturn("red");

        // Create and initialize service bean
        final StylesheetUserPreferencesServiceImpl stylesheetUserPreferencesService =
                new StylesheetUserPreferencesServiceImpl();
        stylesheetUserPreferencesService.setStylesheetDescriptorDao(stylesheetDescriptorDao);
        stylesheetUserPreferencesService.setUserInstanceManager(userInstanceManager);
        stylesheetUserPreferencesService.setStylesheetUserPreferencesDao(
                stylesheetUserPreferencesDao);
        stylesheetUserPreferencesService.setFragmentDefinitionUtils(fragmentUtils);
        stylesheetUserPreferencesService.setTransactionOperations(
                TransactionOperations.withoutTransaction());

        // Run test
        String actual;

        actual =
                stylesheetUserPreferencesService.getLayoutAttribute(
                        request, PreferencesScope.THEME, "u1l1n1", "minimized");
        assertNull(actual);
        actual =
                stylesheetUserPreferencesService.setLayoutAttribute(
                        request, PreferencesScope.THEME, "u1l1n1", "minimized", "true");
        assertNull(actual);
        actual =
                stylesheetUserPreferencesService.getLayoutAttribute(
                        request, PreferencesScope.THEME, "u1l1n1", "minimized");
        assertEquals("true", actual);

        actual =
                stylesheetUserPreferencesService.getStylesheetParameter(
                        request, PreferencesScope.THEME, "skin");
        assertNull(actual);
        actual =
                stylesheetUserPreferencesService.setStylesheetParameter(
                        request, PreferencesScope.THEME, "skin", "red");
        verify(persistentStylesheetUserPreferences).setStylesheetParameter("skin", "red");
        assertNull(actual);
        actual =
                stylesheetUserPreferencesService.getStylesheetParameter(
                        request, PreferencesScope.THEME, "skin");
        assertEquals("red", actual);
    }

    /**
     * Setting or removing a layout attribute reads the layout before its transaction starts, and
     * never inside it. A read can reload the layout and save it, and the save's row locks would
     * then outlast the layout manager's monitor: a save on another request of the session holds the
     * monitor while it waits for those rows, and this request would wait for the monitor.
     */
    @Test
    public void testLayoutAttributeWritesReadTheLayoutOutsideTheirTransaction() {
        final HttpServletRequest request = new MockHttpServletRequest();
        final AtomicBoolean inTransaction = new AtomicBoolean();
        final AtomicInteger layoutReads = new AtomicInteger();
        final AtomicInteger layoutReadsInTransaction = new AtomicInteger();
        final AtomicInteger storesOutsideTransaction = new AtomicInteger();

        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);
        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstanceManager.getUserInstance(request)).thenReturn(userInstance);
        final IPerson person = mock(IPerson.class);
        when(userInstance.getPerson()).thenReturn(person);
        final IUserPreferencesManager preferencesManager = mock(IUserPreferencesManager.class);
        when(userInstance.getPreferencesManager()).thenReturn(preferencesManager);
        final IUserProfile userProfile = mock(IUserProfile.class);
        when(preferencesManager.getUserProfile()).thenReturn(userProfile);
        when(userProfile.getThemeStylesheetId()).thenReturn(1);

        // The fragment's value for the attribute, which comes from the layout
        final IStylesheetUserPreferences distributedStylesheetUserPreferences =
                mock(IStylesheetUserPreferences.class);
        when(distributedStylesheetUserPreferences.getLayoutAttribute("u1l1n1", "minimized"))
                .thenReturn("fragment");
        final IUserLayout userLayout = mock(IUserLayout.class);
        when(userLayout.getDistributedThemeStylesheetUserPreferences())
                .thenReturn(distributedStylesheetUserPreferences);
        final IUserLayoutManager userLayoutManager = mock(IUserLayoutManager.class);
        when(preferencesManager.getUserLayoutManager()).thenReturn(userLayoutManager);
        when(userLayoutManager.getUserLayout())
                .thenAnswer(
                        invocation -> {
                            layoutReads.incrementAndGet();
                            if (inTransaction.get()) {
                                layoutReadsInTransaction.incrementAndGet();
                            }
                            return userLayout;
                        });

        final ILayoutAttributeDescriptor layoutAttributeDescriptor =
                mock(ILayoutAttributeDescriptor.class);
        when(layoutAttributeDescriptor.getScope()).thenReturn(Scope.PERSISTENT);
        when(layoutAttributeDescriptor.getDefaultValue()).thenReturn("default");
        final IStylesheetDescriptor stylesheetDescriptor = mock(IStylesheetDescriptor.class);
        when(stylesheetDescriptor.getLayoutAttributeDescriptor("minimized"))
                .thenReturn(layoutAttributeDescriptor);
        final IStylesheetDescriptorDao stylesheetDescriptorDao =
                mock(IStylesheetDescriptorDao.class);
        when(stylesheetDescriptorDao.getStylesheetDescriptor(1)).thenReturn(stylesheetDescriptor);

        final IStylesheetUserPreferences persistentStylesheetUserPreferences =
                mock(IStylesheetUserPreferences.class);
        when(persistentStylesheetUserPreferences.removeLayoutAttribute("u1l1n1", "minimized"))
                .thenReturn("true");
        final IStylesheetUserPreferencesDao stylesheetUserPreferencesDao =
                mock(IStylesheetUserPreferencesDao.class);
        when(stylesheetUserPreferencesDao.getStylesheetUserPreferences(
                        stylesheetDescriptor, person, userProfile))
                .thenReturn(persistentStylesheetUserPreferences);
        doAnswer(
                        invocation -> {
                            if (!inTransaction.get()) {
                                storesOutsideTransaction.incrementAndGet();
                            }
                            return null;
                        })
                .when(stylesheetUserPreferencesDao)
                .storeStylesheetUserPreferences(persistentStylesheetUserPreferences);

        final StylesheetUserPreferencesServiceImpl stylesheetUserPreferencesService =
                new StylesheetUserPreferencesServiceImpl();
        stylesheetUserPreferencesService.setStylesheetDescriptorDao(stylesheetDescriptorDao);
        stylesheetUserPreferencesService.setUserInstanceManager(userInstanceManager);
        stylesheetUserPreferencesService.setStylesheetUserPreferencesDao(
                stylesheetUserPreferencesDao);
        stylesheetUserPreferencesService.setFragmentDefinitionUtils(
                mock(IFragmentDefinitionUtils.class));
        stylesheetUserPreferencesService.setTransactionOperations(
                new TransactionOperations() {
                    @Override
                    public <T> T execute(TransactionCallback<T> action) {
                        inTransaction.set(true);
                        try {
                            return action.doInTransaction(new SimpleTransactionStatus());
                        } finally {
                            inTransaction.set(false);
                        }
                    }
                });

        // A value of the user's own, the stylesheet's default, and the fragment's value
        stylesheetUserPreferencesService.setLayoutAttribute(
                request, PreferencesScope.THEME, "u1l1n1", "minimized", "true");
        stylesheetUserPreferencesService.setLayoutAttribute(
                request, PreferencesScope.THEME, "u1l1n1", "minimized", "default");
        stylesheetUserPreferencesService.setLayoutAttribute(
                request, PreferencesScope.THEME, "u1l1n1", "minimized", "fragment");
        stylesheetUserPreferencesService.removeLayoutAttribute(
                request, PreferencesScope.THEME, "u1l1n1", "minimized");

        verify(persistentStylesheetUserPreferences)
                .setLayoutAttribute("u1l1n1", "minimized", "true");
        verify(persistentStylesheetUserPreferences, times(3))
                .removeLayoutAttribute("u1l1n1", "minimized");
        verify(stylesheetUserPreferencesDao, times(4))
                .storeStylesheetUserPreferences(persistentStylesheetUserPreferences);
        assertEquals(
                "Reads of the layout inside the transaction", 0, layoutReadsInTransaction.get());
        assertEquals("Reads of the layout", 4, layoutReads.get());
        assertEquals("Stores outside the transaction", 0, storesOutsideTransaction.get());
    }
}
