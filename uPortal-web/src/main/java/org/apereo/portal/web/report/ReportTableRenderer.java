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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Writes a {@link ReportTable} as CSV or as an HTML page, formatting values for a locale. The
 * output matches what google-visualization-java's CsvRenderer and HtmlRenderer produced for uPortal
 * 5: numbers without grouping and with at most six decimals, dates as {@code yyyy-MM-dd},
 * date-times as {@code yyyy-MM-dd HH:mm:ss}, times as {@code HH:mm:ss}, in the locale's digits and
 * calendar.
 */
public final class ReportTableRenderer {

    private static final String SEPARATOR = ",";

    private ReportTableRenderer() {}

    /**
     * The table as CSV: a header line of quoted labels, then one line per row. Text values, and
     * other values whose formatted form contains a comma, are quoted; a null value is written as
     * {@code null}. Lines end with {@code \n}.
     */
    public static String renderCsv(ReportTable table, Locale locale) {
        final List<ReportColumn> columns = table.getColumns();
        if (columns.isEmpty()) {
            return "";
        }
        final ValueFormatter formatter = new ValueFormatter(locale);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(quoteCsv(columns.get(i).getLabel()));
        }
        sb.append('\n');
        for (final List<ReportValue> row : table.getRows()) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(SEPARATOR);
                }
                final ReportValue value = row.get(i);
                if (value.isNull()) {
                    sb.append("null");
                } else {
                    final String formatted = formatter.format(value);
                    if (value.getType() == ReportValueType.TEXT || formatted.contains(SEPARATOR)) {
                        sb.append(quoteCsv(formatted));
                    } else {
                        sb.append(formatted);
                    }
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** The table as an HTML 4.01 page with one table: a header row of labels, then the rows. */
    public static String renderHtml(ReportTable table, Locale locale) {
        final ValueFormatter formatter = new ValueFormatter(locale);
        final StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n")
                .append("<html>\n")
                .append("    <head>\n")
                .append(
                        "        <META http-equiv=\"Content-Type\" content=\"text/html;"
                                + " charset=UTF-8\">\n")
                .append("        <title>Google Visualization</title>\n")
                .append("    </head>\n")
                .append("    <body>\n")
                .append("        <table border=\"1\" cellpadding=\"2\" cellspacing=\"0\">\n")
                .append(
                        "            <tr style=\"font-weight: bold; background-color:"
                                + " #aaa;\">\n")
                .append("                ");
        for (final ReportColumn column : table.getColumns()) {
            sb.append("<td>").append(escapeHtml(column.getLabel())).append("</td>");
        }
        sb.append("\n            </tr>\n");
        int rowCount = 0;
        for (final List<ReportValue> row : table.getRows()) {
            rowCount++;
            sb.append("            <tr style=\"background-color: ")
                    .append(rowCount % 2 != 0 ? "#f0f0f0" : "#ffffff")
                    .append("\">\n                ");
            for (final ReportValue value : row) {
                if (value.isNull()) {
                    sb.append("<td>&nbsp;</td>");
                } else if (value.getType() == ReportValueType.NUMBER) {
                    sb.append("<td align=\"right\">")
                            .append(escapeHtml(formatter.format(value)))
                            .append("</td>");
                } else {
                    final String formatted = formatter.format(value);
                    sb.append("<td>")
                            .append(formatted.isEmpty() ? "&nbsp;" : escapeHtml(formatted))
                            .append("</td>");
                }
            }
            sb.append("\n            </tr>\n");
        }
        sb.append("        </table>\n").append("    </body>\n").append("</html>\n");
        return sb.toString();
    }

    private static String quoteCsv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Escapes text for HTML element content; other characters are written as they are. */
    private static String escapeHtml(String value) {
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                default:
                    if (c < ' ' && c != '\t' && c != '\n') {
                        // Carriage return and the other C0 controls, as numeric references
                        sb.append("&#").append((int) c).append(';');
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Formats values for one locale. Not thread-safe (it holds java.text formats), so each render
     * makes its own.
     */
    private static final class ValueFormatter {

        private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

        private final DecimalFormat numberFormat;
        private final SimpleDateFormat dateFormat;
        private final SimpleDateFormat dateTimeFormat;
        private final SimpleDateFormat timeFormat;

        ValueFormatter(Locale locale) {
            numberFormat = new DecimalFormat("0.######", DecimalFormatSymbols.getInstance(locale));
            numberFormat.setGroupingUsed(false);
            dateFormat = utcFormat("yyyy-MM-dd", locale);
            dateTimeFormat = utcFormat("yyyy-MM-dd HH:mm:ss", locale);
            timeFormat = utcFormat("HH:mm:ss", locale);
        }

        private static SimpleDateFormat utcFormat(String pattern, Locale locale) {
            final SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
            format.setTimeZone(UTC);
            return format;
        }

        /** Formats a non-null value. */
        String format(ReportValue value) {
            final Object v = Objects.requireNonNull(value.getValue());
            switch (value.getType()) {
                case NUMBER:
                    return numberFormat.format(v);
                case TEXT:
                    return String.valueOf(v);
                case DATE:
                    return dateFormat.format(
                            Date.from(((LocalDate) v).atStartOfDay(ZoneOffset.UTC).toInstant()));
                case DATETIME:
                    return dateTimeFormat.format(
                            Date.from(((LocalDateTime) v).toInstant(ZoneOffset.UTC)));
                case TIMEOFDAY:
                    return timeFormat.format(
                            Date.from(
                                    ((LocalTime) v)
                                            .atDate(LocalDate.of(1970, 1, 1))
                                            .toInstant(ZoneOffset.UTC)));
                default:
                    throw new IllegalStateException("Unknown value type " + value.getType());
            }
        }
    }
}
