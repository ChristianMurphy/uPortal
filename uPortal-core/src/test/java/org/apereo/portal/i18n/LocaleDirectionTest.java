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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import org.junit.Test;

public class LocaleDirectionTest {

    @Test
    public void arabicHebrewPersianAndUrduAreRightToLeft() {
        for (String tag : new String[] {"ar", "ar-SA", "ar-XB", "he", "he-IL", "fa-IR", "ur"}) {
            assertTrue(tag, LocaleDirection.isRightToLeft(Locale.forLanguageTag(tag)));
        }
    }

    @Test
    public void hebrewFromTheLegacyConstructorIsRightToLeft() {
        // Before JDK 17, new Locale("he") reports its language as "iw"
        assertTrue(LocaleDirection.isRightToLeft(new Locale("iw")));
        assertTrue(LocaleDirection.isRightToLeft(new Locale("he", "IL")));
    }

    @Test
    public void portalLocalesAreLeftToRight() {
        for (String tag : new String[] {"en-US", "fr-FR", "es-ES", "ja-JP", "sv-SE", "de-DE"}) {
            assertFalse(tag, LocaleDirection.isRightToLeft(Locale.forLanguageTag(tag)));
        }
        assertFalse(LocaleDirection.isRightToLeft(Locale.forLanguageTag("en-XA")));
    }

    @Test
    public void theScriptDecidesWhenThereIsOne() {
        assertTrue(LocaleDirection.isRightToLeft(Locale.forLanguageTag("pa-Arab-PK")));
        assertFalse(LocaleDirection.isRightToLeft(Locale.forLanguageTag("pa-Guru-IN")));
        assertFalse(LocaleDirection.isRightToLeft(Locale.forLanguageTag("ar-Latn")));
    }

    @Test
    public void noLocaleIsLeftToRight() {
        assertFalse(LocaleDirection.isRightToLeft(null));
        assertEquals(LocaleDirection.LTR, LocaleDirection.of(null));
    }

    @Test
    public void ofGivesTheDirAttributeValue() {
        assertEquals("rtl", LocaleDirection.of(Locale.forLanguageTag("ar-EG")));
        assertEquals("ltr", LocaleDirection.of(Locale.US));
    }
}
