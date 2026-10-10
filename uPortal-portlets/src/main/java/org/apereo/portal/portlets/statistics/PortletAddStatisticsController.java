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

import java.util.Collections;
import java.util.List;
import org.apereo.portal.events.aggr.portletlayout.PortletLayoutAggregation;
import org.apereo.portal.spring.web.portlet.ModelAndView;
import org.apereo.portal.spring.web.portlet.bind.annotation.RenderMapping;
import org.apereo.portal.spring.web.portlet.bind.annotation.ResourceMapping;
import org.apereo.portal.web.report.ReportValue;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(value = "VIEW")
public class PortletAddStatisticsController
        extends BasePortletLayoutStatisticsController<PortletAddReportForm> {
    private static final String DATA_TABLE_RESOURCE_ID = "portletAddData";
    private static final String REPORT_NAME = "portletAdd.totals";

    @Override
    @RenderMapping(value = "MAXIMIZED", params = "report=" + REPORT_NAME)
    public String getLoginView() {
        return super.getLoginView();
    }

    @Override
    @ResourceMapping(DATA_TABLE_RESOURCE_ID)
    public ModelAndView renderPortletAddAggregationReport(PortletAddReportForm form) {
        return super.renderPortletAddAggregationReport(form);
    }

    @Override
    public String getReportName() {
        return REPORT_NAME;
    }

    @Override
    public String getReportDataResourceId() {
        return DATA_TABLE_RESOURCE_ID;
    }

    @Override
    protected List<ReportValue> createRowValues(
            PortletLayoutAggregation aggr, PortletAddReportForm form) {
        int count = aggr != null ? aggr.getAddCount() : 0;
        return Collections.<ReportValue>singletonList(ReportValue.number(count));
    }
}
