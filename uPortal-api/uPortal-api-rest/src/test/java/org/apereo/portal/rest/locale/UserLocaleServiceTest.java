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
package org.apereo.portal.rest.locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.IUserPreferencesManager;
import org.apereo.portal.IUserProfile;
import org.apereo.portal.i18n.ILocaleStore;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.i18n.LocaleManagerFactory;
import org.apereo.portal.layout.IUserLayoutManager;
import org.apereo.portal.portlets.localization.UserLocaleHelper;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** The service with the real {@link UserLocaleHelper} and locale manager behind it. */
public class UserLocaleServiceTest {

    private static final Locale FRENCH = new Locale("fr", "FR");

    private MockHttpServletRequest request;
    private IPerson person;
    private LocaleManager localeManager;
    private ILocaleStore localeStore;
    private IUserLayoutManager layoutManager;
    private UserLocaleService service;

    @Before
    public void setUp() {
        final LocaleManagerFactory factory = new LocaleManagerFactory();
        ReflectionTestUtils.setField(factory, "portalLocalesProperty", "en_US,fr_FR,ja_JP");
        factory.init();

        request = new MockHttpServletRequest();
        person = mock(IPerson.class);
        when(person.getUserName()).thenReturn("student");
        localeManager = factory.createLocaleManager(person, new ArrayList<>());
        final IUserProfile profile = mock(IUserProfile.class);
        when(profile.getLocaleManager()).thenReturn(localeManager);
        layoutManager = mock(IUserLayoutManager.class);
        final IUserPreferencesManager preferences = mock(IUserPreferencesManager.class);
        when(preferences.getUserProfile()).thenReturn(profile);
        when(preferences.getUserLayoutManager()).thenReturn(layoutManager);
        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstance.getPreferencesManager()).thenReturn(preferences);
        when(userInstance.getPerson()).thenReturn(person);
        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);
        when(userInstanceManager.getUserInstance(any(HttpServletRequest.class)))
                .thenReturn(userInstance);
        localeStore = mock(ILocaleStore.class);

        final UserLocaleHelper helper = new UserLocaleHelper();
        helper.setUserInstanceManager(userInstanceManager);
        helper.setLocaleManagerFactory(factory);
        helper.setLocaleStore(localeStore);
        service = new UserLocaleService(userInstanceManager, factory, helper);
    }

    private static List<String> codes(UserLocale userLocale) {
        final List<String> codes = new ArrayList<>();
        for (final LocaleOption option : userLocale.getLocales()) {
            codes.add(option.getCode());
        }
        return codes;
    }

    @Test
    public void listsThePortalLocalesWithNoChoice() {
        final UserLocale userLocale = service.getUserLocale(request);

        assertNull(userLocale.getSelected());
        // The page is rendered in the first portal locale, so that one is current
        assertEquals("en_US", userLocale.getCurrent());
        assertEquals(List.of("en_US", "fr_FR", "ja_JP"), codes(userLocale));
        assertEquals("fr-FR", userLocale.getLocales().get(1).getTag());
    }

    @Test
    public void namesTheLanguagesInTheSessionLanguage() {
        localeManager.setSessionLocales(Collections.singletonList(FRENCH));

        final UserLocale userLocale = service.getUserLocale(request);

        assertEquals("fr_FR", userLocale.getSelected());
        assertEquals("fr_FR", userLocale.getCurrent());
        assertEquals("anglais", userLocale.getLocales().get(0).getName());
        assertEquals("français", userLocale.getLocales().get(1).getName());
        assertEquals("English", userLocale.getLocales().get(0).getNativeName());
        assertEquals("Français", userLocale.getLocales().get(1).getNativeName());
        assertEquals("日本語", userLocale.getLocales().get(2).getNativeName());
    }

    @Test
    public void namesEachLanguageInItsOwnLanguage() {
        assertEquals("Español", UserLocaleService.nativeName(new Locale("es", "ES")));
        assertEquals("Français", UserLocaleService.nativeName(FRENCH));
        assertEquals("Deutsch", UserLocaleService.nativeName(Locale.GERMANY));
        assertEquals("Македонски", UserLocaleService.nativeName(new Locale("mk", "MK")));
        assertEquals("日本語", UserLocaleService.nativeName(Locale.JAPAN));
        assertEquals("", UserLocaleService.nativeName(Locale.ROOT));
    }

    @Test
    public void fallsBackToTheStoredLocale() {
        localeManager.setUserLocales(Collections.singletonList(Locale.JAPAN));

        assertEquals("ja_JP", service.getUserLocale(request).getSelected());
        assertEquals("ja_JP", service.getUserLocale(request).getCurrent());
    }

    @Test
    public void ignoresAStoredLocaleThePortalNoLongerOffers() {
        localeManager.setUserLocales(Collections.singletonList(Locale.GERMANY));

        final UserLocale userLocale = service.getUserLocale(request);

        assertNull(userLocale.getSelected());
        assertEquals("en_US", userLocale.getCurrent());
    }

    @Test
    public void storesASignedInUsersChoice() {
        final UserLocale userLocale = service.setUserLocale(request, "fr_FR");

        assertEquals("fr_FR", userLocale.getSelected());
        assertEquals("français", userLocale.getLocales().get(1).getName());
        assertEquals(List.of(FRENCH), localeManager.getSessionLocales());
        assertEquals(List.of(FRENCH), localeManager.getUserLocales());
        verify(localeStore).updateUserLocales(person, new Locale[] {FRENCH});
        verify(layoutManager).loadUserLayout(true);
    }

    @Test
    public void keepsAGuestsChoiceInTheSessionOnly() {
        when(person.isGuest()).thenReturn(true);

        assertEquals("fr_FR", service.setUserLocale(request, "fr_FR").getSelected());

        assertEquals(List.of(FRENCH), localeManager.getSessionLocales());
        verify(localeStore, never()).updateUserLocales(any(), any());
    }

    @Test
    public void refusesALocaleThePortalDoesNotOffer() {
        assertThrows(IllegalArgumentException.class, () -> service.setUserLocale(request, "de_DE"));
        assertThrows(IllegalArgumentException.class, () -> service.setUserLocale(request, null));
        assertNull(localeManager.getSessionLocales());
        verify(localeStore, never()).updateUserLocales(any(), any());
    }
}
