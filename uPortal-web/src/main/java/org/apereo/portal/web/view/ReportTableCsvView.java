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

import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apereo.portal.web.report.ReportTable;
import org.apereo.portal.web.report.ReportTableRenderer;

/** Renders a {@link ReportTable} to a CSV */
public class ReportTableCsvView extends AbstractReportTableView {
    public static final String OUT_FILE_NAME_ATTRIBUTE = "outFileName";

    @Override
    protected void renderReportTable(
            Map<String, Object> model,
            ReportTable data,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        final String csvDataTable = ReportTableRenderer.renderCsv(data, request.getLocale());

        response.setContentType("text/csv; charset=UTF-8");

        String outFileName = (String) model.get(OUT_FILE_NAME_ATTRIBUTE);
        if (outFileName == null) {
            outFileName = "dataTable.csv";
        }
        // For security reasons, make sure the file extension is ".csv".
        else if (!outFileName.toLowerCase().endsWith(".csv")) {
            outFileName = outFileName + ".csv";
        }
        response.setHeader("Content-Disposition", "attachment; filename=" + outFileName);
        writeUtf8(response, csvDataTable);
    }
}
