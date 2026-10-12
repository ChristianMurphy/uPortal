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
package org.apereo.portal.portlets.localization;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

public class LocaleBeanTest {

    @Test
    public void givesTheThreeLetterCodes() {
        final LocaleBean bean = new LocaleBean(Locale.forLanguageTag("fr-FR"));
        assertEquals("fra", bean.getIso3Language());
        assertEquals("FRA", bean.getIso3Country());
    }

    @Test
    public void aRegionWithoutAThreeLetterCodeGivesNone() {
        // getISO3Country() throws MissingResourceException for these
        assertEquals("", new LocaleBean(Locale.forLanguageTag("ar-XB")).getIso3Country());
        assertEquals("", new LocaleBean(Locale.forLanguageTag("es-419")).getIso3Country());
        assertEquals("ara", new LocaleBean(Locale.forLanguageTag("ar-XB")).getIso3Language());
    }

    @Test
    public void aLanguageWithoutAThreeLetterCodeGivesNone() {
        assertEquals("", new LocaleBean(new Locale("qx")).getIso3Language());
    }

    @Test
    public void aLocaleWithoutACountryGivesNoCountryCode() {
        assertEquals("", new LocaleBean(Locale.ENGLISH).getIso3Country());
    }
}
