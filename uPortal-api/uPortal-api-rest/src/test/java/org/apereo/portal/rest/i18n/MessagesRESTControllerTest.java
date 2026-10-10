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
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class MessagesRESTControllerTest {

    private static final String URI = "/v6-0/messages";

    private MockMvc mockMvc;
    private LocaleManager localeManager;

    @Before
    public void setUp() {
        // Configured like the portal's message source (mvcContext.xml)
        final StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.setAlwaysUseMessageFormat(true);
        // StaticMessageSource has no locale fallback: each test locale gets its messages
        messageSource.addMessage("favorites", Locale.US, "Favorites");
        messageSource.addMessage("favorites.added", Locale.US, "You''ve added {0}.");
        messageSource.addMessage("remove", Locale.US, "Remove");
        messageSource.addMessage("favorites", Locale.FRENCH, "Favoris");
        messageSource.addMessage("favorites", Locale.GERMANY, "Favoriten");

        localeManager = mock(LocaleManager.class);
        when(localeManager.getLocales()).thenReturn(Collections.singletonList(Locale.FRENCH));
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

    @Test
    public void returnsTheKnownMessagesInTheRequestedLocale() throws Exception {
        mockMvc.perform(
                        get(URI).param("keys", "remove, favorites,favorites.added,no.such.key")
                                .param("locale", "en-US"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        "{\"locale\":\"en-US\",\"messages\":{"
                                                + "\"favorites\":\"Favorites\","
                                                + "\"favorites.added\":\"You've added {0}.\","
                                                + "\"remove\":\"Remove\"}}",
                                        true));
    }

    @Test
    public void usesTheUsersLocaleWithoutAValidOne() throws Exception {
        mockMvc.perform(get(URI).param("keys", "favorites"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .json(
                                        "{\"locale\":\"fr\",\"messages\":{\"favorites\":\"Favoris\"}}"));

        mockMvc.perform(get(URI).param("keys", "favorites").param("locale", "!!"))
                .andExpect(content().json("{\"locale\":\"fr\"}"));
    }

    @Test
    public void fallsBackToTheRequestLocale() throws Exception {
        when(localeManager.getLocales()).thenReturn(Collections.emptyList());

        mockMvc.perform(get(URI).param("keys", "favorites").locale(Locale.GERMANY))
                .andExpect(
                        content()
                                .json(
                                        "{\"locale\":\"de-DE\",\"messages\":{\"favorites\":\"Favoriten\"}}"));
    }

    @Test
    public void refusesInvalidKeys() throws Exception {
        mockMvc.perform(get(URI).param("keys", " , ")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URI).param("keys", "favorites,<script>"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"message\":\"Invalid message key\"}"));
        mockMvc.perform(get(URI)).andExpect(status().isBadRequest());

        final StringBuilder tooMany = new StringBuilder();
        for (int i = 0; i <= MessagesRESTController.MAX_KEYS; i++) {
            tooMany.append("key").append(i).append(',');
        }
        mockMvc.perform(get(URI).param("keys", tooMany.toString()))
                .andExpect(status().isBadRequest());
    }
}
