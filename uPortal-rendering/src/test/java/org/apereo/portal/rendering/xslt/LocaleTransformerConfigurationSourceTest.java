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
package org.apereo.portal.rendering.xslt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.junit.Test;

public class LocaleTransformerConfigurationSourceTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    private Map<String, Object> parametersFor(List<Locale> locales) {
        final LocaleManager localeManager = mock(LocaleManager.class);
        when(localeManager.getLocales()).thenReturn(locales);
        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstance.getLocaleManager()).thenReturn(localeManager);
        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);
        when(userInstanceManager.getUserInstance(request)).thenReturn(userInstance);

        final LocaleTransformerConfigurationSource source =
                new LocaleTransformerConfigurationSource();
        source.setUserInstanceManager(userInstanceManager);
        return source.getParameters(request, response);
    }

    @Test
    public void anArabicUserGetsRightToLeft() {
        final Map<String, Object> parameters =
                parametersFor(Collections.singletonList(new Locale("ar", "SA")));
        assertEquals("ar-SA", parameters.get("USER_LANG"));
        assertEquals("rtl", parameters.get("USER_DIR"));
    }

    @Test
    public void anEnglishUserGetsLeftToRight() {
        final Map<String, Object> parameters = parametersFor(List.of(Locale.US, Locale.FRANCE));
        assertEquals("en-US", parameters.get("USER_LANG"));
        assertEquals("ltr", parameters.get("USER_DIR"));
    }

    @Test
    public void theFirstLocaleDecides() {
        final Map<String, Object> parameters =
                parametersFor(List.of(new Locale("he", "IL"), Locale.US));
        assertEquals("rtl", parameters.get("USER_DIR"));
    }

    @Test
    public void noLocaleGivesNoParameters() {
        assertNull(parametersFor(Collections.emptyList()));
    }
}
