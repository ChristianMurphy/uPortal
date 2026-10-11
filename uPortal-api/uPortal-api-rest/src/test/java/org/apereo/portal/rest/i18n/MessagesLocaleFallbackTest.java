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
package org.apereo.portal.rest.i18n;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MOD-D51: a site adds a language with a bundle and a portal_locales entry. A bundle for a language
 * alone ({@code _zh}) serves its regions and scripts, however the component writes the locale, and
 * a language without a bundle gets English. The message source is configured like the portal's
 * (mvcContext.xml) over test-only bundles.
 */
public class MessagesLocaleFallbackTest {

    private static final String URI = "/v6-0/messages";

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        final ReloadableResourceBundleMessageSource messageSource =
                new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:/org/apereo/portal/rest/i18n/FallbackMessages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setAlwaysUseMessageFormat(true);
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.setFallbackToSystemLocale(false);

        final LocaleManager localeManager = mock(LocaleManager.class);
        when(localeManager.getLocales()).thenReturn(Collections.singletonList(Locale.US));
        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstance.getLocaleManager()).thenReturn(localeManager);
        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);
        when(userInstanceManager.getUserInstance(any(HttpServletRequest.class)))
                .thenReturn(userInstance);

        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new MessagesRESTController(messageSource, userInstanceManager))
                        .build();
    }

    private void expectChinese(String locale, String resolved) throws Exception {
        mockMvc.perform(get(URI).param("keys", "favorites,favorites.added").param("locale", locale))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .json(
                                        "{\"locale\":\""
                                                + resolved
                                                + "\",\"messages\":{"
                                                + "\"favorites\":\"收藏\","
                                                + "\"favorites.added\":\"您已添加 {0}。\"}}",
                                        true));
    }

    @Test
    public void servesTheLanguageBundleForARegion() throws Exception {
        expectChinese("zh-TW", "zh-TW");
    }

    @Test
    public void servesTheLanguageBundleForAScriptAndRegion() throws Exception {
        expectChinese("zh-Hant-TW", "zh-Hant-TW");
    }

    @Test
    public void readsALocaleWrittenWithAnUnderscore() throws Exception {
        // As Java and portal_locales write it
        expectChinese("zh_TW", "zh-TW");
    }

    @Test
    public void servesEnglishForALanguageWithoutABundle() throws Exception {
        mockMvc.perform(get(URI).param("keys", "favorites").param("locale", "pl-PL"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .json(
                                        "{\"locale\":\"pl-PL\","
                                                + "\"messages\":{\"favorites\":\"Favorites\"}}",
                                        true));
    }
}
