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
package org.apereo.portal.portlets.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import org.junit.Test;

/** The report dates parse and print as Joda-Time's {@code M/d/yyyy} pattern did. */
public class CustomLocalDateEditorTest {
    private final CustomLocalDateEditor editor =
            new CustomLocalDateEditor(CustomLocalDateEditor.REPORT_DATE_FORMAT, false);

    private LocalDate parse(String text) {
        editor.setAsText(text);
        return (LocalDate) editor.getValue();
    }

    @Test
    public void parsesOneOrTwoDigitMonthsAndDays() {
        assertEquals(LocalDate.of(2026, 1, 2), parse("1/2/2026"));
        assertEquals(LocalDate.of(2026, 12, 31), parse("12/31/2026"));
        assertEquals(LocalDate.of(2026, 3, 8), parse("03/08/2026"));
    }

    @Test
    public void parsesShortYearsAsTheYearGiven() {
        assertEquals(LocalDate.of(26, 10, 1), parse("10/1/26"));
    }

    @Test
    public void printsTheYearWithFourDigits() {
        editor.setValue(LocalDate.of(2026, 3, 8));
        assertEquals("3/8/2026", editor.getAsText());
        editor.setValue(LocalDate.of(26, 10, 1));
        assertEquals("10/1/0026", editor.getAsText());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnInvalidDate() {
        parse("2/30/2026");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAThreeDigitMonth() {
        parse("012/1/2026");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnEmptyDateUnlessAllowed() {
        parse("");
    }

    @Test
    public void readsAnEmptyDateAsNullWhenAllowed() {
        final CustomLocalDateEditor lenient =
                new CustomLocalDateEditor(CustomLocalDateEditor.REPORT_DATE_FORMAT, true);
        lenient.setAsText("");
        assertNull(lenient.getValue());
    }
}
