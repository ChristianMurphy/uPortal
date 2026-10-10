/*
 * Records the JSON, CSV and HTML that uPortal 5.17.x produced through
 * google-visualization-java (com.github.ci-rt release-1.1.3) for the tables in
 * ReportTableGoldenTest, so the report table model that replaced the library can be
 * compared against it (MOD-I51). Not part of the build.
 *
 * Run with the jars from WEB-INF/lib of uPortal-webapp-5.17.9.war:
 *   google-visualization-java-release-1.1.3.jar (SHA-256 eca09172...bad63), icu4j-63.1,
 *   guava-32.0.0-android, commons-lang-2.6, jcl-over-slf4j-1.7.36, slf4j-api-2.0.17
 *
 *   javac -cp "$CP" RecordGviz.java && java -cp ".:$CP" RecordGviz <outDir>
 *
 * JSON is rendered the way GoogleDataTableSerializer did:
 * JsonRenderer.renderDataTable(table, true, true, false). CSV and HTML are rendered the
 * way GoogleCsvDataTableView and GoogleHtmlDataTableView did, for each locale.
 */
import com.google.visualization.datasource.datatable.ColumnDescription;
import com.google.visualization.datasource.datatable.DataTable;
import com.google.visualization.datasource.datatable.TableCell;
import com.google.visualization.datasource.datatable.TableRow;
import com.google.visualization.datasource.datatable.value.DateTimeValue;
import com.google.visualization.datasource.datatable.value.DateValue;
import com.google.visualization.datasource.datatable.value.NumberValue;
import com.google.visualization.datasource.datatable.value.TextValue;
import com.google.visualization.datasource.datatable.value.TimeOfDayValue;
import com.google.visualization.datasource.datatable.value.Value;
import com.google.visualization.datasource.datatable.value.ValueType;
import com.google.visualization.datasource.render.CsvRenderer;
import com.google.visualization.datasource.render.HtmlRenderer;
import com.google.visualization.datasource.render.JsonRenderer;
import com.ibm.icu.util.ULocale;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class RecordGviz {

    static final String[] LOCALES = {"en-US", "de-DE", "th-TH", "ar-EG"};

    public static void main(String[] args) throws Exception {
        Path out = Paths.get(args[0]);
        Files.createDirectories(out);
        for (Map.Entry<String, DataTable> e : tables().entrySet()) {
            String name = e.getKey();
            DataTable t = e.getValue();
            write(out.resolve(name + ".json"), JsonRenderer.renderDataTable(t, true, true, false));
            for (String tag : LOCALES) {
                ULocale locale = ULocale.forLocale(Locale.forLanguageTag(tag));
                write(out.resolve(name + "." + tag + ".csv"), CsvRenderer.renderDataTable(t, locale, ","));
                write(out.resolve(name + "." + tag + ".html"), HtmlRenderer.renderDataTable(t, locale));
            }
        }
    }

    static void write(Path p, CharSequence s) throws Exception {
        Files.write(p, s.toString().getBytes(StandardCharsets.UTF_8));
    }

    static Value nul(ValueType type) {
        return Value.getNullValueFromValueType(type);
    }

    static void row(DataTable t, Value... values) throws Exception {
        TableRow r = new TableRow();
        for (Value v : values) {
            r.addCell(new TableCell(v));
        }
        t.addRow(r);
    }

    static Map<String, DataTable> tables() throws Exception {
        Map<String, DataTable> tables = new LinkedHashMap<>();

        // A daily report, as BaseStatisticsReportController builds it (months are 0-based)
        DataTable daily = new DataTable();
        daily.addColumn(new ColumnDescription("date", ValueType.DATE, "Date"));
        daily.addColumn(new ColumnDescription("Everyone", ValueType.NUMBER, "Everyone"));
        String odd = "Students, \"Faculty\" & <Staff> 'all'";
        daily.addColumn(new ColumnDescription(odd, ValueType.NUMBER, odd));
        row(daily, new DateValue(2026, 0, 31), new NumberValue(0), new NumberValue(12));
        row(daily, new DateValue(2026, 1, 1), new NumberValue(1234567.891), new NumberValue(0.5));
        row(daily, new DateValue(2026, 11, 31), new NumberValue(1.0 / 3), new NumberValue(-12.5));
        tables.put("daily", daily);

        // Date-time intervals, with null cells first, in the middle and last
        DataTable dateTime = new DataTable();
        dateTime.addColumn(new ColumnDescription("date", ValueType.DATETIME, "Date"));
        dateTime.addColumn(new ColumnDescription("logins", ValueType.NUMBER, "Logins"));
        dateTime.addColumn(new ColumnDescription("unique", ValueType.NUMBER, "Unique"));
        dateTime.addColumn(new ColumnDescription("note", ValueType.TEXT, "Note"));
        row(
                dateTime,
                new DateTimeValue(2026, 9, 10, 13, 5, 7, 0),
                new NumberValue(42),
                nul(ValueType.NUMBER),
                new TextValue("a,\"b\"<c>&'d'"));
        row(
                dateTime,
                new DateTimeValue(2026, 9, 11, 0, 0, 0, 0),
                nul(ValueType.NUMBER),
                new NumberValue(1e10),
                nul(ValueType.TEXT));
        row(
                dateTime,
                nul(ValueType.DATETIME),
                new NumberValue(2.0 / 3 * 1000),
                new NumberValue(1e-7),
                new TextValue(""));
        tables.put("datetime", dateTime);

        // Time-of-day intervals (the minute and hour reports)
        DataTable timeOfDay = new DataTable();
        timeOfDay.addColumn(new ColumnDescription("time", ValueType.TIMEOFDAY, "Time"));
        timeOfDay.addColumn(
                new ColumnDescription("Concurrent users", ValueType.NUMBER, "Concurrent users"));
        row(timeOfDay, new TimeOfDayValue(0, 0, 0), new NumberValue(5));
        row(timeOfDay, new TimeOfDayValue(23, 59, 0), new NumberValue(123456789012345.0));
        row(timeOfDay, new TimeOfDayValue(13, 5, 7), nul(ValueType.NUMBER));
        tables.put("timeofday", timeOfDay);

        // Text that needs escaping in JSON, CSV and HTML
        DataTable text = new DataTable();
        text.addColumn(new ColumnDescription("text", ValueType.TEXT, "Text"));
        text.addColumn(new ColumnDescription("n", ValueType.NUMBER, "N"));
        row(text, new TextValue("Café — Ünïcödé 日本"), new NumberValue(1));
        row(text, new TextValue("line1\nline2\ttab\r"), new NumberValue(2));
        row(text, new TextValue("back\\slash /"), new NumberValue(3));
        row(text, new TextValue("</script><script>alert(1)</script>"), new NumberValue(4));
        // (A C0 control character such as \u0001 made HtmlRenderer throw, so it isn't here.)
        row(text, new TextValue(" sep\u0085x "), new NumberValue(5));
        row(text, new TextValue("  spaces  "), new NumberValue(6));
        tables.put("text", text);

        // Columns but no rows
        DataTable empty = new DataTable();
        empty.addColumn(new ColumnDescription("date", ValueType.DATE, "Date"));
        empty.addColumn(new ColumnDescription("count", ValueType.NUMBER, "Count"));
        tables.put("empty", empty);

        return tables;
    }
}
