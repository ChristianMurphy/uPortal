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

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import org.springframework.util.StringUtils;

/**
 * Property editor for {@link LocalDate}, using a {@link DateTimeFormatter} for parsing and
 * rendering. This class is modeled on Spring's CustomDateEditor.
 *
 * <p>This is not meant to be used as system PropertyEditor but rather as locale-specific date
 * editor within custom controller code, parsing user-entered strings into date properties of beans
 * and rendering them in the UI form.
 */
public class CustomLocalDateEditor extends PropertyEditorSupport {
    /**
     * The statistics reports' {@code M/d/yyyy} dates, parsed as Joda-Time's pattern parsed them: a
     * month and a day of one or two digits, a year of one or more digits, and no invalid dates.
     * Years print with at least four digits.
     */
    public static final DateTimeFormatter REPORT_DATE_FORMAT =
            new DateTimeFormatterBuilder()
                    .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
                    .appendLiteral('/')
                    .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                    .appendLiteral('/')
                    .parseLenient()
                    .appendValue(ChronoField.YEAR, 4, 9, SignStyle.NORMAL)
                    .parseStrict()
                    .toFormatter()
                    .withResolverStyle(ResolverStyle.STRICT);

    private final DateTimeFormatter dateFormat;

    private final boolean allowEmpty;

    /**
     * @param dateFormat the formatter to parse and render with
     * @param allowEmpty if empty strings should be allowed, and read as null
     */
    public CustomLocalDateEditor(DateTimeFormatter dateFormat, boolean allowEmpty) {
        this.dateFormat = dateFormat;
        this.allowEmpty = allowEmpty;
    }

    /** Parse the date from the given text, using the formatter. */
    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        if (this.allowEmpty && !StringUtils.hasText(text)) {
            // Treat empty String as null value.
            setValue(null);
            return;
        }
        if (text == null) {
            throw new IllegalArgumentException("Could not parse a null date");
        }
        try {
            setValue(LocalDate.parse(text, this.dateFormat));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Could not parse date: " + e.getMessage(), e);
        }
    }

    /** Format the date as text, using the formatter. */
    @Override
    public String getAsText() {
        final LocalDate value = (LocalDate) getValue();
        return value != null ? this.dateFormat.format(value) : "";
    }
}
