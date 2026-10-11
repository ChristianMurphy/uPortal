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

import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The portal's languages and the one the user chose, as {@code /api/v6-0/locale} returns them. */
public final class UserLocale {

    private final @Nullable String selected;
    private final List<LocaleOption> locales;

    public UserLocale(@Nullable String selected, List<LocaleOption> locales) {
        this.selected = selected;
        this.locales = Collections.unmodifiableList(locales);
    }

    /** The code of the user's chosen locale, or null when the user has not chosen one. */
    public @Nullable String getSelected() {
        return selected;
    }

    /** The portal's locales, in the order of {@code portal_locales}. */
    public List<LocaleOption> getLocales() {
        return locales;
    }
}
