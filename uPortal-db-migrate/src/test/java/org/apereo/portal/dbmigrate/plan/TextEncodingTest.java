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
package org.apereo.portal.dbmigrate.plan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What each database's character set can hold, as the code page check sees it (MOD-I202). */
public class TextEncodingTest {

    private static final TextEncoding CP1252 = TextEncoding.forSqlServerCodePage(1252);

    @Test
    public void windows1252HoldsWesternEuropeanText() {
        assertEquals("windows-1252", CP1252.name());
        assertFalse(CP1252.isUnicode());
        assertEquals(-1, CP1252.firstUnencodable("Thing 7 é — «Ünïcødé» € Œ ™"));
    }

    @Test
    public void windows1252NamesTheFirstCharacterItCannotHold() {
        assertEquals(0x1F331, CP1252.firstUnencodable("PAGS Fixture Child 🌱"));
        assertEquals('研', CP1252.firstUnencodable("Équipe 研究グループ"));
        assertEquals('ł', CP1252.firstUnencodable("ünïcødé-łøwęr"));
    }

    @Test
    public void utf8mb3HoldsTheBasicPlaneButNoEmoji() {
        TextEncoding utf8mb3 = TextEncoding.forMySql("utf8mb3");
        assertSame(TextEncoding.BMP_ONLY, utf8mb3);
        assertSame(utf8mb3, TextEncoding.forMySql("utf8"));
        assertEquals(-1, utf8mb3.firstUnencodable("研究グループ ☕ Исследователь"));
        assertEquals(0x1F393, utf8mb3.firstUnencodable("Zoë 🎓"));
    }

    @Test
    public void unicodeCharacterSetsHoldEverything() {
        for (TextEncoding e :
                new TextEncoding[] {
                    TextEncoding.forSqlServerCodePage(65001),
                    TextEncoding.forSqlServerCodePage(0),
                    TextEncoding.forMySql("utf8mb4"),
                    TextEncoding.forPostgres("UTF8"),
                    TextEncoding.forOracle("AL32UTF8"),
                    TextEncoding.forOracle("AL16UTF16")
                }) {
            assertTrue(e.toString(), e.isUnicode());
            assertEquals(-1, e.firstUnencodable("🌱 研究 ☕"));
        }
    }

    @Test
    public void eachDatabaseNamesWindows1252ItsOwnWay() {
        assertEquals("windows-1252", TextEncoding.forMySql("latin1").name());
        assertEquals("windows-1252", TextEncoding.forPostgres("WIN1252").name());
        assertEquals("windows-1252", TextEncoding.forOracle("WE8MSWIN1252").name());
        assertEquals("ISO-8859-1", TextEncoding.forPostgres("LATIN1").name());
        // MySQL's latin1 is windows-1252: it holds the euro sign, ISO-8859-1 does not
        assertEquals(-1, TextEncoding.forMySql("latin1").firstUnencodable("€"));
        assertEquals('€', TextEncoding.forPostgres("LATIN1").firstUnencodable("€"));
    }

    @Test
    public void unknownCharacterSetsAreNull() {
        assertNull(TextEncoding.forMySql("armscii8"));
        assertNull(TextEncoding.forPostgres("MULE_INTERNAL"));
        assertNull(TextEncoding.forOracle("WE8DEC"));
    }
}
