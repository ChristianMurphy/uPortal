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
package org.apereo.portal.web.report;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Writes a {@link ReportTable} in the DataTable JSON format that the report charts (Google Charts'
 * {@code google.visualization.DataTable}) read:
 *
 * <pre>
 * {"cols":[{"id":"date","label":"Date","type":"date","pattern":""}, ...],
 *  "rows":[{"c":[{"v":"Date(2026,0,31)"},{"v":12.0},{"v":null}]}, ...]}
 * </pre>
 *
 * Dates are strings in the {@code Date(year,month,day[,hours,minutes,seconds])} form, with a
 * 0-based month; times of day are {@code [hours,minutes,seconds,milliseconds]}. Strings are escaped
 * as google-visualization-java escaped them, so the output matches what uPortal 5 produced, except
 * that a null value is always written as {@code {"v":null}}: the library wrote nothing for a null
 * that wasn't in the last column, which isn't valid JSON.
 */
public class ReportTableJsonSerializer extends JsonSerializer<ReportTable> {

    private static final CharacterEscapes ESCAPES = new ReportTableCharacterEscapes();

    @Override
    public void serialize(ReportTable table, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        final CharacterEscapes previousEscapes = gen.getCharacterEscapes();
        gen.setCharacterEscapes(ESCAPES);
        try {
            gen.writeStartObject();
            gen.writeArrayFieldStart("cols");
            for (final ReportColumn column : table.getColumns()) {
                gen.writeStartObject();
                gen.writeStringField("id", column.getId());
                gen.writeStringField("label", column.getLabel());
                gen.writeStringField("type", column.getType().getJsonName());
                gen.writeStringField("pattern", "");
                gen.writeEndObject();
            }
            gen.writeEndArray();
            gen.writeArrayFieldStart("rows");
            for (final List<ReportValue> row : table.getRows()) {
                gen.writeStartObject();
                gen.writeArrayFieldStart("c");
                for (final ReportValue value : row) {
                    gen.writeStartObject();
                    gen.writeFieldName("v");
                    writeValue(value, gen);
                    gen.writeEndObject();
                }
                gen.writeEndArray();
                gen.writeEndObject();
            }
            gen.writeEndArray();
            gen.writeEndObject();
        } finally {
            gen.setCharacterEscapes(previousEscapes);
        }
    }

    private static void writeValue(ReportValue value, JsonGenerator gen) throws IOException {
        final Object v = value.getValue();
        if (v == null) {
            gen.writeNull();
            return;
        }
        switch (value.getType()) {
            case NUMBER:
                gen.writeNumber((Double) v);
                break;
            case TEXT:
                gen.writeString((String) v);
                break;
            case DATE:
                {
                    final LocalDate date = (LocalDate) v;
                    gen.writeString(
                            "Date("
                                    + date.getYear()
                                    + ","
                                    + (date.getMonthValue() - 1)
                                    + ","
                                    + date.getDayOfMonth()
                                    + ")");
                    break;
                }
            case DATETIME:
                {
                    final LocalDateTime dateTime = (LocalDateTime) v;
                    gen.writeString(
                            "Date("
                                    + dateTime.getYear()
                                    + ","
                                    + (dateTime.getMonthValue() - 1)
                                    + ","
                                    + dateTime.getDayOfMonth()
                                    + ","
                                    + dateTime.getHour()
                                    + ","
                                    + dateTime.getMinute()
                                    + ","
                                    + dateTime.getSecond()
                                    + ")");
                    break;
                }
            case TIMEOFDAY:
                {
                    final LocalTime time = (LocalTime) v;
                    gen.writeStartArray();
                    gen.writeNumber(time.getHour());
                    gen.writeNumber(time.getMinute());
                    gen.writeNumber(time.getSecond());
                    gen.writeNumber(time.getNano() / 1_000_000);
                    gen.writeEndArray();
                    break;
                }
            default:
                throw new IllegalStateException("Unknown value type " + value.getType());
        }
    }

    /**
     * Escapes what google-visualization-java's EscapeUtil.jsonEscape escaped: the JSON essentials,
     * {@code ' " < >} as Unicode escapes (so the JSON is safe inside HTML), and the C1 controls and
     * U+2000–U+20FF (which include the JavaScript line separators).
     */
    private static final class ReportTableCharacterEscapes extends CharacterEscapes {

        private static final long serialVersionUID = 1L;

        private final int[] asciiEscapes;

        ReportTableCharacterEscapes() {
            asciiEscapes = standardAsciiEscapesForJSON();
            asciiEscapes['\''] = ESCAPE_CUSTOM;
            asciiEscapes['"'] = ESCAPE_CUSTOM;
            asciiEscapes['<'] = ESCAPE_CUSTOM;
            asciiEscapes['>'] = ESCAPE_CUSTOM;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return asciiEscapes;
        }

        @Override
        public @Nullable SerializableString getEscapeSequence(int ch) {
            switch (ch) {
                case '\'':
                    return new SerializedString("\\u0027");
                case '"':
                    return new SerializedString("\\u0022");
                case '<':
                    return new SerializedString("\\u003c");
                case '>':
                    return new SerializedString("\\u003e");
                default:
                    if ((ch >= 0x80 && ch < 0xa0) || (ch >= 0x2000 && ch < 0x2100)) {
                        return new SerializedString(String.format("\\u%04X", ch));
                    }
                    return null;
            }
        }
    }
}
