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

/** One of the portal's languages, as {@code GET /api/v6-0/locale} lists it. */
public final class LocaleOption {

    private final String code;
    private final String tag;
    private final String name;
    private final String nativeName;

    public LocaleOption(String code, String tag, String name, String nativeName) {
        this.code = code;
        this.tag = tag;
        this.name = name;
        this.nativeName = nativeName;
    }

    /** The portal's code for the locale, such as {@code fr_FR}; what a client sends back. */
    public String getCode() {
        return code;
    }

    /** The BCP 47 language tag, such as {@code fr-FR}, for the {@code lang} attribute. */
    public String getTag() {
        return tag;
    }

    /** The language's name in the user's current language, such as {@code français}. */
    public String getName() {
        return name;
    }

    /** The language's name in that language, as a list item starts, such as {@code Français}. */
    public String getNativeName() {
        return nativeName;
    }
}
