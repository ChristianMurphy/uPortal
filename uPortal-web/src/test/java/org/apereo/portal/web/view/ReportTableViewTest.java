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
package org.apereo.portal.web.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apereo.portal.web.report.ReportColumn;
import org.apereo.portal.web.report.ReportTable;
import org.apereo.portal.web.report.ReportValue;
import org.apereo.portal.web.report.ReportValueType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class ReportTableViewTest {

    private final Map<String, Object> model = new HashMap<>();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Before
    public void setUp() {
        final ReportTable table = new ReportTable();
        table.addColumn(new ReportColumn("g", ReportValueType.TEXT, "Große Gruppe 日本"));
        table.addColumn(new ReportColumn("n", ReportValueType.NUMBER, "Logins"));
        table.addRow(Arrays.asList(ReportValue.text("été"), ReportValue.number(2.5)));
        model.put("table", table);
        request.addPreferredLocale(Locale.GERMANY);
    }

    @Test
    public void csvIsUtf8WithItsLengthInBytes() throws Exception {
        model.put(ReportTableCsvView.OUT_FILE_NAME_ATTRIBUTE, "logins");
        new ReportTableCsvView().render(model, request, response);

        final String expected = "\"Große Gruppe 日本\",\"Logins\"\n\"été\",\"2,5\"\n";
        assertEquals("text/csv;charset=UTF-8", response.getContentType().replace(" ", ""));
        assertEquals("attachment; filename=logins.csv", response.getHeader("Content-Disposition"));
        assertEquals(expected, response.getContentAsString());
        assertEquals(expected.getBytes(StandardCharsets.UTF_8).length, response.getContentLength());
    }

    @Test
    public void htmlIsUtf8WithItsLengthInBytes() throws Exception {
        new ReportTableHtmlView().render(model, request, response);

        final String body = response.getContentAsString();
        assertEquals("text/html;charset=UTF-8", response.getContentType().replace(" ", ""));
        assertTrue(body, body.contains("<td>Große Gruppe 日本</td><td>Logins</td>"));
        assertTrue(body, body.contains("<td>été</td><td align=\"right\">2,5</td>"));
        assertEquals(body.getBytes(StandardCharsets.UTF_8).length, response.getContentLength());
    }

    @Test
    public void modelWithoutATableIsAServerError() throws Exception {
        model.clear();
        new ReportTableCsvView().render(model, request, response);
        assertEquals(500, response.getStatus());
    }
}
