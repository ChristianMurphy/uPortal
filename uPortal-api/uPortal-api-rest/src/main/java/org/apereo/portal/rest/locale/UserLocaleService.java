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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.i18n.LocaleManagerFactory;
import org.apereo.portal.portlets.localization.LocaleBean;
import org.apereo.portal.portlets.localization.UserLocaleHelper;
import org.apereo.portal.user.IUserInstanceManager;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads and sets the current user's locale the way the User Locales Selector portlet did, through
 * {@link UserLocaleHelper}: the choice goes into the session, and for a signed-in user also into
 * their stored locales.
 */
@Service
public class UserLocaleService {

    private final IUserInstanceManager userInstanceManager;
    private final LocaleManagerFactory localeManagerFactory;
    private final UserLocaleHelper userLocaleHelper;

    @Autowired
    public UserLocaleService(
            IUserInstanceManager userInstanceManager,
            LocaleManagerFactory localeManagerFactory,
            UserLocaleHelper userLocaleHelper) {
        this.userInstanceManager = userInstanceManager;
        this.localeManagerFactory = localeManagerFactory;
        this.userLocaleHelper = userLocaleHelper;
    }

    /** The portal's locales, named in the user's language and in their own, and the choice. */
    public UserLocale getUserLocale(HttpServletRequest request) {
        final LocaleManager localeManager = getLocaleManager(request);
        final @Nullable Locale current = getCurrentLocale(localeManager);
        final List<LocaleOption> options = new ArrayList<>();
        for (final LocaleBean bean : userLocaleHelper.getLocales(current)) {
            options.add(
                    new LocaleOption(
                            bean.getCode(),
                            bean.getLocale().toLanguageTag(),
                            bean.getDisplayLanguage(),
                            nativeName(bean.getLocale())));
        }
        final @Nullable Locale rendered = getRenderedLocale(localeManager);
        // A stored choice the portal no longer offers is no choice among these locales
        final @Nullable String selected =
                current != null && localeManagerFactory.getPortalLocales().contains(current)
                        ? current.toString()
                        : null;
        return new UserLocale(selected, rendered == null ? null : rendered.toString(), options);
    }

    /**
     * Makes one of the portal's locales the user's locale.
     *
     * @throws IllegalArgumentException when the code is not one of the portal's locales
     */
    public UserLocale setUserLocale(HttpServletRequest request, @Nullable String code) {
        if (code == null || !isPortalLocale(code)) {
            throw new IllegalArgumentException("Not one of the portal's locales: " + code);
        }
        userLocaleHelper.updateUserLocale(request, code);
        return getUserLocale(request);
    }

    /**
     * The language's name in that language, with a capital first letter as it starts a list item:
     * Java names French "français" in French, and the list shows "Français".
     */
    static String nativeName(Locale locale) {
        final String name = locale.getDisplayLanguage(locale);
        if (name.isEmpty()) {
            return locale.toString();
        }
        final int first = name.codePointAt(0);
        final int end = Character.charCount(first);
        return name.substring(0, end).toUpperCase(locale) + name.substring(end);
    }

    private boolean isPortalLocale(String code) {
        for (final Locale locale : localeManagerFactory.getPortalLocales()) {
            if (locale.toString().equals(code)) {
                return true;
            }
        }
        return false;
    }

    private LocaleManager getLocaleManager(HttpServletRequest request) {
        return userInstanceManager
                .getUserInstance(request)
                .getPreferencesManager()
                .getUserProfile()
                .getLocaleManager();
    }

    /**
     * The locale the portal renders the page in: the first of the manager's locales (session, user,
     * then {@code portal_locales}) that the portal offers.
     */
    private @Nullable Locale getRenderedLocale(LocaleManager localeManager) {
        final List<Locale> portalLocales = localeManagerFactory.getPortalLocales();
        for (final Locale locale : localeManager.getLocales()) {
            if (portalLocales.contains(locale)) {
                return locale;
            }
        }
        return null;
    }

    /** The session's locale, else the user's stored one, else null (as the portlet did). */
    private @Nullable Locale getCurrentLocale(LocaleManager localeManager) {
        final @Nullable List<Locale> sessionLocales = localeManager.getSessionLocales();
        if (sessionLocales != null && !sessionLocales.isEmpty()) {
            return sessionLocales.get(0);
        }
        final @Nullable List<Locale> userLocales = localeManager.getUserLocales();
        if (userLocales != null && !userLocales.isEmpty()) {
            return userLocales.get(0);
        }
        return null;
    }
}
