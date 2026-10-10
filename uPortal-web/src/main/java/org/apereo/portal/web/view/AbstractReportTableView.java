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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apereo.portal.web.report.ReportTable;
import org.springframework.web.servlet.view.AbstractView;

/** Base class for views that serialize {@link ReportTable} objects */
public abstract class AbstractReportTableView extends AbstractView {
    public static final String DATA_TABLE_MODEL_ATTRIBUTE = "dataTable";

    @Override
    protected final void renderMergedOutputModel(
            Map<String, Object> model, HttpServletRequest request, HttpServletResponse response)
            throws Exception {

        try {
            ReportTable data = (ReportTable) model.get(DATA_TABLE_MODEL_ATTRIBUTE);
            if (data == null) {
                for (final Object value : model.values()) {
                    if (value instanceof ReportTable) {
                        if (data == null) {
                            data = (ReportTable) value;
                        } else {
                            throw new IllegalArgumentException(
                                    "Multiple ReportTable objects exist in the model but none of"
                                            + " them are stored under the key: "
                                            + DATA_TABLE_MODEL_ATTRIBUTE);
                        }
                    }
                }
            }

            if (data == null) {
                throw new IllegalArgumentException("No ReportTable could be found in the model");
            }

            renderReportTable(model, data, request, response);
        } catch (RuntimeException rte) {
            this.logger.error(
                    "Failed to generate response for ReportTable view serialization", rte);
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to generate response for ReportTable view serialization");
        }
    }

    protected abstract void renderReportTable(
            Map<String, Object> model,
            ReportTable data,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException;

    /** Writes the body as UTF-8, with its length in bytes. */
    protected static void writeUtf8(HttpServletResponse response, String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }
}
