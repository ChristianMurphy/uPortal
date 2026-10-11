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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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
import org.apereo.portal.security.csrf.PortalCsrfSecurityRequestMatcher;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code PUT /api/v6-0/locale} over HTTP with the real service, helper and locale manager behind
 * the controller, so a refused code is checked where it would be stored.
 */
public class UserLocaleApiTest {

    private static final Locale FRENCH = new Locale("fr", "FR");

    private LocaleManager localeManager;
    private ILocaleStore localeStore;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private CsrfToken csrfToken;

    @Before
    public void setUp() {
        final LocaleManagerFactory factory = new LocaleManagerFactory();
        ReflectionTestUtils.setField(factory, "portalLocalesProperty", "en_US,fr_FR");
        factory.init();

        final IPerson person = mock(IPerson.class);
        when(person.getUserName()).thenReturn("student");
        localeManager = factory.createLocaleManager(person, new ArrayList<>());
        final IUserProfile profile = mock(IUserProfile.class);
        when(profile.getLocaleManager()).thenReturn(localeManager);
        final IUserPreferencesManager preferences = mock(IUserPreferencesManager.class);
        when(preferences.getUserProfile()).thenReturn(profile);
        when(preferences.getUserLayoutManager()).thenReturn(mock(IUserLayoutManager.class));
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
        final UserLocaleService service =
                new UserLocaleService(userInstanceManager, factory, helper);

        final HttpSessionCsrfTokenRepository tokenRepository = new HttpSessionCsrfTokenRepository();
        final PortalCsrfSecurityRequestMatcher csrfMatcher = new PortalCsrfSecurityRequestMatcher();
        csrfMatcher.init();
        final CsrfFilter csrfFilter = new CsrfFilter(tokenRepository);
        csrfFilter.setRequireCsrfProtectionMatcher(csrfMatcher);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new UserLocaleRESTController(service))
                        .addFilters(csrfFilter)
                        .build();

        session = new MockHttpSession();
        final MockHttpServletRequest pageRequest = new MockHttpServletRequest();
        pageRequest.setSession(session);
        csrfToken = tokenRepository.generateToken(pageRequest);
        tokenRepository.saveToken(csrfToken, pageRequest, new MockHttpServletResponse());
    }

    private MockHttpServletRequestBuilder update(String code) {
        return put("/uPortal/api/v6-0/locale")
                .contextPath("/uPortal")
                .servletPath("/api")
                .session(session)
                .header(csrfToken.getHeaderName(), csrfToken.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}");
    }

    @Test
    public void refusesALocaleThePortalDoesNotOfferAndKeepsTheChoice() throws Exception {
        for (final String code : List.of("de_DE", "xx_YY")) {
            mockMvc.perform(update(code)).andExpect(status().isBadRequest());
        }

        assertNull(localeManager.getSessionLocales());
        verify(localeStore, never()).updateUserLocales(any(), any());
    }

    @Test
    public void savesALocaleThePortalOffers() throws Exception {
        mockMvc.perform(update("fr_FR"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .json(
                                        "{\"selected\":\"fr_FR\",\"locales\":["
                                                + "{\"code\":\"en_US\",\"name\":\"anglais\"},"
                                                + "{\"code\":\"fr_FR\",\"name\":\"français\","
                                                + "\"nativeName\":\"Français\"}]}"));

        assertEquals(List.of(FRENCH), localeManager.getSessionLocales());
        verify(localeStore).updateUserLocales(any(), any());
    }
}
