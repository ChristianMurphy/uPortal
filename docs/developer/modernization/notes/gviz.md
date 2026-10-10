# MOD-I51: remove the jitpack-only google-visualization-java (WS gviz)

Branch `ccr-43ba0785-hzlrj5-gviz` (uPortal). uPortal-start is unchanged.

## Outcome

google-visualization-java (`com.github.ci-rt:google-visualization-java:release-1.1.3`,
jitpack.io only) is gone. A small uPortal-owned table model replaces it, so there is no jitpack
repository, no `googleVisualizationVersion`, and no ICU4J or opencsv coming in through it.

* **Plan change.** I first recommended vendoring the library's classes (option b below). The
  project owner decided, via the coordinator, against both (a) and (b), because both keep an
  unmaintained library. They chose (c): a uPortal table model written out with Jackson. A later
  workstream will move the chart pages to REST plus an Apache ECharts web component.
* **New code**, in `uPortal-web`, package `org.apereo.portal.web.report` (`@NullMarked`):
  * `ReportTable`: typed columns, and rows of `ReportValue`s. It checks types, and short rows
    and new columns are filled with nulls, as `DataTable` did. Plain getters: `getColumns()`,
    and `getRows()` returning one `List<ReportValue>` per row whose `getValue()` is a `Double`,
    `String` or `java.time` value. That is the shape a REST/ECharts serialiser would use.
  * `ReportColumn` (id, type, label), `ReportValueType` (NUMBER, TEXT, DATE, DATETIME,
    TIMEOFDAY), `ReportValue` (static factories and `nullValue(type)`).
  * `ReportTableJsonSerializer`: writes the Google Charts DataTable JSON through Jackson's
    `JsonGenerator`. Custom `CharacterEscapes` reproduce the old escaping byte for byte.
  * `ReportTableRenderer`: CSV and HTML, with `java.text` formats configured to match ICU's
    empty pattern.
  * The views are renamed `AbstractReportTableView`, `ReportTableCsvView` and
    `ReportTableHtmlView`. The bean names in `mvcViews.xml` are unchanged. They now write
    UTF-8 bytes with a byte Content-Length; the old views set the length in characters, which
    cut non-ASCII downloads short. The HTML response declares `charset=UTF-8`.
* **Ported:**
  * all statistics controllers (`throws TypeMismatchException` dropped);
  * `ReportTitleAndColumnDescriptionStrategy`, `DefaultStatisticsReportLabellingStrategy`;
  * the Activity and PopularPortlets controllers;
  * `JsonDataTable` and `GoogleDataTableSerializer` deleted, since `ReportTable` carries its
    own serialiser.
* **Unaffected:** the SqlQuery `googleColumnChart`/`googleLineChart` JSPs and `reportList.jsp`
  use only the `WEB-INF/tags/google-visualization` tag files, not the Java library. The JSPs
  are unchanged.

## Inventory (of the removed library)

* 21 uPortal files used 14 types: `DataTable`, `ColumnDescription`, `TableRow`, `TableCell`,
  `Value`, `ValueType`, `NumberValue`, `DateValue`, `DateTimeValue`, `TimeOfDayValue`,
  `TypeMismatchException`, `JsonRenderer`, `CsvRenderer` and `HtmlRenderer`.
* Their closure (jdeps `-filter:none`) is 29 of the jar's classes plus the `ErrorMessages`
  bundle: 5,900 lines.
* The jar brought ICU4J 63.1, opencsv 2.3, commons-lang 2.6 and Guava. uPortal itself used
  ICU4J only for `ULocale` in the two views, and never used opencsv.

## Options compared

| Option | Verdict |
|---|---|
| (a) Google `visualization-datasource:1.1.1` on Central | Same public API for all 14 types (`javap` diff) and Guava-32 compatible. But its POM pins ICU4J 4.0.1, the cause of uPortal-start#468 and the 2020 move to the fork (a13a5cd29). It also emits `,,` for null cells. Unmaintained since 2012. |
| (b) Vendor the 30 files from ci-rt `release-1.1.3` | Identical behaviour, about 5,900 lines of unmaintained code. Analysis done; the build wiring was refused by the session's permission classifier ("Untrusted Code Integration"). |
| (c) uPortal-owned model **(chosen by the owner)** | About 600 lines of new main code. No third-party chart library, and Jackson is already a dependency. |

What the ci-rt fork changed after Google's release:
* the `,,`→`,{},` JSON fix (96957fa), which only covered middle columns: a null first cell
  still produced `[,`;
* JSONP callback sanitising;
* ANSI-SQL query changes (unused here);
* dependency bumps.

## Evidence

* **Golden files**, recorded with the old library: 45 files under
  `uPortal-web/src/test/resources/org/apereo/portal/web/report/golden/`.
  * Recorded by `notes/gviz-golden/RecordGviz.java`, run with plain `javac`/`java` against the
    jars in `uPortal-webapp-5.17.9.war` from Central. The gviz jar's SHA-256 is
    eca091720c6f98906c53aa8157fc9458a49920759bd149074351221770bbad63, verified.
  * Five tables (daily DATE/NUMBER; DATETIME with nulls first, middle and last; TIMEOFDAY;
    TEXT that needs escaping; no rows), each as JSON, plus CSV and HTML in `en-US`, `de-DE`,
    `th-TH` and `ar-EG`.
* **`ReportTableGoldenTest`:** the new code reproduces every golden file.
  * JSON: byte-identical, except the null-cell fix. The test applies exactly that substitution
    and asserts that the recorded JSON fails to parse.
  * CSV: byte-identical in all four locales, including Buddhist years for `th-TH` and
    Arabic-Indic digits for `ar-EG`.
  * HTML: identical once the old serialiser's entities for non-ASCII characters
    (`&eacute;`, `&mdash;`, `&#133;`…) are decoded. The new page writes those characters as
    UTF-8; `& < >`, `&nbsp;` and `&#13;` are byte-identical.
* **`ReportTableTest`:** type and size checks, null padding, valid JSON for nulls in every
  position, an empty table, milliseconds, and control characters (the old `HtmlRenderer`
  threw on U+0001).
* **`ReportTableViewTest`:** content types, UTF-8 byte lengths, the CSV filename, and a 500
  when the model has no table.
* **`LoginTotalsStatisticsControllerTest`**, end to end: mocked DAOs → controller →
  `MappingJackson2JsonView` → exact JSON, and → `ReportTableCsvView` → exact CSV, including a
  gap day.
* **Formatting check:** `java.text` against ICU's formatting, over 31 locales × 12 numbers,
  plus a date-time per locale.
  * Numbers matched in every locale.
  * Dates differ only where ICU's default calendar is Islamic or Persian (`ar-SA`, `fa-IR`,
    `ps-AF`), or where the tag asks for one explicitly (`-u-ca-japanese`). The JDK has no such
    calendars, so those dates are now Gregorian. This is documented in UPGRADING-6.0.

### How the tests were run

* Maven Central rate-limited this sandbox (HTTP 429) for most of the session. Gradle compiled
  `uPortal-web` and `uPortal-portlets` once, through Google's Central mirror, before the
  permission classifier flagged the mirror init script ("Package Registry Bypass"). I removed
  it, and Gradle could not resolve the test classpaths after that.
* So the 17 `uPortal-web` tests ran with plain `javac`/`java` against the jars in the Gradle
  cache, and passed (17/17).
* `LoginTotalsStatisticsControllerTest`, the full suite, `spotlessCheck` and `spotbugsMain`
  rely on CI.
* Formatting was applied with google-java-format 1.35.0 (AOSP, Google import order); the
  build pins 1.36.1, which CI checks.

CI runs: see the bottom of this note.

## Other changes

* `config/spotbugs/baseline/uPortal-web.xml`: dropped the two XSS_SERVLET entries of the
  deleted Google views.
* `NOTICE`: dropped the Google Visualization Data Source Library line. The opencsv and ICU4J
  lines stay until a dependency report confirms nothing else brings them in.
* `docs/UPGRADING-6.0.md`: new section with the class map for overlays.

## Proposed LOG updates

* **MOD-I51:** resolved. google-visualization-java was replaced by a uPortal report table
  model (`org.apereo.portal.web.report`). The jitpack repository, `googleVisualizationVersion`
  and the ICU4J/opencsv it brought are removed. Golden tests prove the JSON, CSV and HTML match
  5.17, except the null-cell JSON fix.
* **New decision (MOD-D?):**
  * Own small formats instead of keeping unmaintained libraries, when uPortal uses only a
    sliver of them.
  * The chart pages move to REST plus an Apache ECharts web component; Google Charts goes then.
  * The `WEB-INF/tags/google-visualization` tags and the "Google Visualization" HTML title stay
    until that move.
* **Follow-ups:**
  * Regenerate NOTICE from the dependency report (opencsv, ICU4J).
  * The SqlQuery chart JSPs still load `www.google.com/jsapi` over plain http.

## CI
