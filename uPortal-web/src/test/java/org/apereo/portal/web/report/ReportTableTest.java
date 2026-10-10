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
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;

public class ReportTableTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ReportTable table;

    @Before
    public void setUp() {
        table = new ReportTable();
        table.addColumn(new ReportColumn("a", ReportValueType.NUMBER, "A"));
        table.addColumn(new ReportColumn("b", ReportValueType.TEXT, "B"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateColumnIdIsRejected() {
        table.addColumn(new ReportColumn("a", ReportValueType.NUMBER, "Another A"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rowWithTooManyValuesIsRejected() {
        table.addRow(
                Arrays.asList(ReportValue.number(1), ReportValue.text("x"), ReportValue.number(2)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void valueOfTheWrongTypeIsRejected() {
        table.addRow(Arrays.asList(ReportValue.text("x"), ReportValue.text("y")));
    }

    @Test
    public void shortRowsAndNewColumnsAreFilledWithNulls() {
        table.addRow(Collections.singletonList(ReportValue.number(1)));
        table.addColumn(new ReportColumn("c", ReportValueType.TIMEOFDAY, "C"));
        assertEquals(
                Arrays.asList(
                        ReportValue.number(1),
                        ReportValue.nullValue(ReportValueType.TEXT),
                        ReportValue.nullValue(ReportValueType.TIMEOFDAY)),
                table.getRows().get(0));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void rowsCannotBeChangedFromOutside() {
        table.addRow(Collections.singletonList(ReportValue.number(1)));
        table.getRows().get(0).set(0, ReportValue.number(2));
    }

    @Test
    public void nullValuesAreValidJsonInEveryPosition() throws Exception {
        table.addColumn(new ReportColumn("c", ReportValueType.NUMBER, "C"));
        table.addRow(
                Arrays.asList(
                        ReportValue.nullValue(ReportValueType.NUMBER),
                        ReportValue.nullValue(ReportValueType.TEXT),
                        ReportValue.nullValue(ReportValueType.NUMBER)));
        final String json = mapper.writeValueAsString(table);
        assertTrue(
                json,
                json.endsWith("\"rows\":[{\"c\":[{\"v\":null},{\"v\":null},{\"v\":null}]}]}"));
        mapper.readTree(json);
    }

    @Test
    public void tableWithoutColumnsIsValidJson() throws Exception {
        assertEquals("{\"cols\":[],\"rows\":[]}", mapper.writeValueAsString(new ReportTable()));
    }

    @Test
    public void timeOfDayKeepsMilliseconds() throws Exception {
        final ReportTable t = new ReportTable();
        t.addColumn(new ReportColumn("t", ReportValueType.TIMEOFDAY, "T"));
        t.addRow(
                Collections.singletonList(
                        ReportValue.timeOfDay(LocalTime.of(1, 2, 3, 456_000_000))));
        assertTrue(mapper.writeValueAsString(t).contains("{\"v\":[1,2,3,456]}"));
    }

    @Test
    public void controlCharactersAreEscapedInJsonAndHtml() throws Exception {
        table.addRow(Arrays.asList(ReportValue.number(1), ReportValue.text("a\u0001b\u001bc")));
        // google-visualization-java failed to render this table as HTML
        final String html = ReportTableRenderer.renderHtml(table, Locale.US);
        assertTrue(html, html.contains("<td>a&#1;b&#27;c</td>"));
        assertTrue(mapper.writeValueAsString(table).contains("\"a\\u0001b\\u001Bc\""));
    }

    @Test
    public void tableWithoutColumnsIsEmptyCsv() {
        assertEquals("", ReportTableRenderer.renderCsv(new ReportTable(), Locale.US));
    }
}
