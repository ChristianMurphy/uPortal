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
package org.apereo.portal.i18n;

import java.util.Locale;
import java.util.Set;

/**
 * The direction in which a locale's text is written, for the page's {@code dir} attribute and the
 * skin's stylesheet.
 *
 * @since 6.0
 */
public final class LocaleDirection {

    /** The value of {@code dir} for text written right to left. */
    public static final String RTL = "rtl";

    /** The value of {@code dir} for text written left to right. */
    public static final String LTR = "ltr";

    /**
     * Languages written right to left. Java gives Hebrew and Yiddish as "iw" and "ji" before JDK
     * 17, so both forms are listed. The web components use the same languages (lib/i18n.ts).
     */
    private static final Set<String> RIGHT_TO_LEFT_LANGUAGES =
            Set.of("ar", "ckb", "dv", "fa", "he", "iw", "ji", "ps", "sd", "ug", "ur", "yi");

    /** Scripts written right to left, for a language written in more than one script. */
    private static final Set<String> RIGHT_TO_LEFT_SCRIPTS =
            Set.of("Adlm", "Arab", "Hebr", "Nkoo", "Rohg", "Syrc", "Thaa");

    private LocaleDirection() {}

    /**
     * Whether text in the locale is written right to left. A script subtag decides when there is
     * one (pa-Arab is right to left, az-Latn is not); otherwise the language does.
     */
    public static boolean isRightToLeft(Locale locale) {
        if (locale == null) {
            return false;
        }
        final String script = locale.getScript();
        if (!script.isEmpty()) {
            return RIGHT_TO_LEFT_SCRIPTS.contains(script);
        }
        return RIGHT_TO_LEFT_LANGUAGES.contains(locale.getLanguage());
    }

    /** {@link #RTL} or {@link #LTR} for the locale. */
    public static String of(Locale locale) {
        return isRightToLeft(locale) ? RTL : LTR;
    }
}
