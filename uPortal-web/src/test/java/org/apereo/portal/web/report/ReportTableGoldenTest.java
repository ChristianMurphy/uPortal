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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Compares the report table output with what google-visualization-java produced for the same tables
 * in uPortal 5 (MOD-I51). The golden files were recorded with
 * docs/developer/modernization/notes/gviz-golden/RecordGviz.java, which builds these tables with
 * the old library.
 *
 * <p>Two documented differences: a null value that isn't in the last column is {@code {"v":null}}
 * in the JSON (the library wrote invalid JSON there), and the HTML has characters above ASCII as
 * they are rather than as entities such as {@code &eacute;} (the page is UTF-8).
 */
public class ReportTableGoldenTest {

    private static final String[] LOCALES = {"en-US", "de-DE", "th-TH", "ar-EG"};

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void jsonMatchesTheOldLibrary() throws Exception {
        for (Map.Entry<String, ReportTable> e : tables().entrySet()) {
            final String name = e.getKey();
            String expected = golden(name + ".json");
            if (name.equals("datetime")) {
                // The library's null-cell bug, fixed: nothing at all for a null in the first
                // column, and {} for one in a middle column
                expected =
                        expected.replace("{\"c\":[,", "{\"c\":[{\"v\":null},")
                                .replace(",{},", ",{\"v\":null},");
            }
            assertEquals(name, expected, MAPPER.writeValueAsString(e.getValue()));
        }
    }

    @Test
    public void theOldLibraryWroteInvalidJsonForNullCells() throws Exception {
        try {
            MAPPER.readTree(golden("datetime.json"));
            fail("expected the recorded JSON with a null first cell to be invalid");
        } catch (IOException expected) {
            // google-visualization-java wrote {"c":[,...]} for a null first cell
        }
        assertNotNull(MAPPER.readTree(MAPPER.writeValueAsString(tables().get("datetime"))));
    }

    @Test
    public void csvMatchesTheOldLibrary() throws Exception {
        for (Map.Entry<String, ReportTable> e : tables().entrySet()) {
            for (String tag : LOCALES) {
                final String file = e.getKey() + "." + tag + ".csv";
                assertEquals(
                        file,
                        golden(file),
                        ReportTableRenderer.renderCsv(e.getValue(), Locale.forLanguageTag(tag)));
            }
        }
    }

    @Test
    public void htmlMatchesTheOldLibrary() throws Exception {
        for (Map.Entry<String, ReportTable> e : tables().entrySet()) {
            for (String tag : LOCALES) {
                final String file = e.getKey() + "." + tag + ".html";
                assertEquals(
                        file,
                        decodeNonAsciiEntities(golden(file)),
                        decodeNonAsciiEntities(
                                ReportTableRenderer.renderHtml(
                                        e.getValue(), Locale.forLanguageTag(tag))));
            }
        }
    }

    /** The entities the JDK's HTML serializer wrote for the non-ASCII characters in the tables. */
    private static final Map<String, String> NAMED_ENTITIES = new HashMap<>();

    static {
        NAMED_ENTITIES.put("nbsp", " ");
        NAMED_ENTITIES.put("eacute", "é");
        NAMED_ENTITIES.put("Uuml", "Ü");
        NAMED_ENTITIES.put("iuml", "ï");
        NAMED_ENTITIES.put("ouml", "ö");
        NAMED_ENTITIES.put("mdash", "—");
    }

    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|[A-Za-z]+);");

    /**
     * Replaces character references for non-ASCII characters with the characters, leaving {@code
     * &amp; &lt; &gt;} and references to ASCII characters (such as {@code &#13;}) as they are.
     */
    private static String decodeNonAsciiEntities(String html) {
        final Matcher m = ENTITY.matcher(html);
        final StringBuffer sb = new StringBuffer();
        while (m.find()) {
            final String ref = m.group(1);
            String replacement = m.group();
            if (ref.startsWith("#")) {
                final int c = Integer.parseInt(ref.substring(1));
                if (c >= 0x80) {
                    replacement = new String(Character.toChars(c));
                }
            } else if (NAMED_ENTITIES.containsKey(ref)) {
                replacement = NAMED_ENTITIES.get(ref);
            } else if (!Arrays.asList("amp", "lt", "gt").contains(ref)) {
                fail("unexpected entity " + m.group());
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String golden(String file) throws IOException {
        try (InputStream in = ReportTableGoldenTest.class.getResourceAsStream("golden/" + file)) {
            assertNotNull(file, in);
            final String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // A checkout may have converted line endings
            return s.replace("\r\n", "\n");
        }
    }

    private static ReportTable table(ReportColumn... columns) {
        final ReportTable table = new ReportTable();
        table.addColumns(Arrays.asList(columns));
        return table;
    }

    private static void row(ReportTable table, ReportValue... values) {
        table.addRow(Arrays.asList(values));
    }

    private static ReportValue number(double v) {
        return ReportValue.number(v);
    }

    private static ReportValue text(String v) {
        return ReportValue.text(v);
    }

    /** The same tables RecordGviz builds with google-visualization-java. */
    static Map<String, ReportTable> tables() {
        final Map<String, ReportTable> tables = new LinkedHashMap<>();

        final String odd = "Students, \"Faculty\" & <Staff> 'all'";
        final ReportTable daily =
                table(
                        new ReportColumn("date", ReportValueType.DATE, "Date"),
                        new ReportColumn("Everyone", ReportValueType.NUMBER, "Everyone"),
                        new ReportColumn(odd, ReportValueType.NUMBER, odd));
        row(daily, ReportValue.date(LocalDate.of(2026, 1, 31)), number(0), number(12));
        row(daily, ReportValue.date(LocalDate.of(2026, 2, 1)), number(1234567.891), number(0.5));
        row(daily, ReportValue.date(LocalDate.of(2026, 12, 31)), number(1.0 / 3), number(-12.5));
        tables.put("daily", daily);

        final ReportTable dateTime =
                table(
                        new ReportColumn("date", ReportValueType.DATETIME, "Date"),
                        new ReportColumn("logins", ReportValueType.NUMBER, "Logins"),
                        new ReportColumn("unique", ReportValueType.NUMBER, "Unique"),
                        new ReportColumn("note", ReportValueType.TEXT, "Note"));
        row(
                dateTime,
                ReportValue.dateTime(LocalDateTime.of(2026, 10, 10, 13, 5, 7)),
                number(42),
                ReportValue.nullValue(ReportValueType.NUMBER),
                text("a,\"b\"<c>&'d'"));
        row(
                dateTime,
                ReportValue.dateTime(LocalDateTime.of(2026, 10, 11, 0, 0, 0)),
                ReportValue.nullValue(ReportValueType.NUMBER),
                number(1e10),
                ReportValue.nullValue(ReportValueType.TEXT));
        row(
                dateTime,
                ReportValue.nullValue(ReportValueType.DATETIME),
                number(2.0 / 3 * 1000),
                number(1e-7),
                text(""));
        tables.put("datetime", dateTime);

        final ReportTable timeOfDay =
                table(
                        new ReportColumn("time", ReportValueType.TIMEOFDAY, "Time"),
                        new ReportColumn(
                                "Concurrent users", ReportValueType.NUMBER, "Concurrent users"));
        row(timeOfDay, ReportValue.timeOfDay(LocalTime.of(0, 0, 0)), number(5));
        row(timeOfDay, ReportValue.timeOfDay(LocalTime.of(23, 59, 0)), number(123456789012345.0));
        row(
                timeOfDay,
                ReportValue.timeOfDay(LocalTime.of(13, 5, 7)),
                ReportValue.nullValue(ReportValueType.NUMBER));
        tables.put("timeofday", timeOfDay);

        final ReportTable text =
                table(
                        new ReportColumn("text", ReportValueType.TEXT, "Text"),
                        new ReportColumn("n", ReportValueType.NUMBER, "N"));
        row(text, text("Café — Ünïcödé 日本"), number(1));
        row(text, text("line1\nline2\ttab\r"), number(2));
        row(text, text("back\\slash /"), number(3));
        row(text, text("</script><script>alert(1)</script>"), number(4));
        row(text, text(" sep\u0085x "), number(5));
        row(text, text("  spaces  "), number(6));
        tables.put("text", text);

        tables.put(
                "empty",
                table(
                        new ReportColumn("date", ReportValueType.DATE, "Date"),
                        new ReportColumn("count", ReportValueType.NUMBER, "Count")));

        return tables;
    }
}
