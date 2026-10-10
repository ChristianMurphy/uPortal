# MOD-I51: remove the jitpack-only google-visualization-java (WS gviz)

Status: **analysis done, implementation paused** waiting for approval (see "Blocked").

## Inventory

* Declared in `uPortal-web/build.gradle` as
  `api "com.github.ci-rt:google-visualization-java:release-1.1.3"`, and the
  only reason for `maven { url = "https://jitpack.io" }` in `build.gradle`.
* Used by 21 files in `uPortal-web` (the `dataTableCsvView` and
  `dataTableHtmlView` views: `AbstractGoogleDataTableView`,
  `GoogleCsvDataTableView`, `GoogleHtmlDataTableView`) and
  `uPortal-portlets` (all statistics report controllers,
  `JsonDataTable` + `GoogleDataTableSerializer` for the JSON the
  `reportGraph.jsp` charts read, plus `TypeMismatchException` in the
  activity and popular-portlets controllers).
* 14 API types: `DataTable`, `ColumnDescription`, `TableRow`, `TableCell`,
  `Value`, `ValueType`, `NumberValue`, `DateValue`, `DateTimeValue`,
  `TimeOfDayValue`, `TypeMismatchException`, `JsonRenderer`, `CsvRenderer`,
  `HtmlRenderer`. The query language, the servlet data-source helpers and
  the SQL helpers are unused.
* Transitive closure (`jdeps -verbose:class -filter:none`, nested classes
  included): 29 of the jar's top-level classes, plus the `ErrorMessages`
  bundle that `ReasonType` loads by name. 30 files, 5,900 lines.
* External needs of that closure: Guava (collections only), ICU4J
  (`ULocale`, formats, `Collator`), commons-lang 2 (`StringUtils`,
  `StringEscapeUtils`, `StrBuilder`), the Commons Logging API (served by
  jcl-over-slf4j), and JAXP/DOM from the JDK. opencsv (a declared dependency
  of the jar) is not used by these classes or by uPortal.

## Options compared

| Option | Change | Behaviour vs today | Notes |
|---|---|---|---|
| (a) `com.google.visualization:visualization-datasource:1.1.1` (Central, 2012) | ~3 lines + explicit `icu4j` pin | **Regresses** one fix: a null cell in a non-last column renders `,,` (invalid JSON), see below | Public API of all 14 types identical (`javap -public` diff). All 21 Guava members it calls exist in Guava 32. Its POM asks for icu4j 4.0.1, the cause of uPortal-start#468 (ICU crash on JDK 8u>255) that made uPortal switch to the fork in a13a5cd29, so an explicit icu4j 63.1 would be required. Also pulls guava r07 / commons-lang 2.4 / opencsv 1.8 (resolved upward, except opencsv). |
| (b) vendor the 30 files from ci-rt `release-1.1.3` (commit 68edca5d) | new module `uPortal-gviz`, `src/vendored` like P3-1 (MOD-D26) | identical (same source as the jar shipped today) | Apache-2.0, Google copyright headers kept. No package rename needed: nothing else provides these packages once the jar is gone, so adopters' custom reports keep compiling. |
| (c) rewrite in uPortal code | DataTable model + 3 renderers, change 21 consumers | must re-create JSON/CSV/HTML formats exactly | not a sliver: 29 classes; breaks adopter report controllers |

What the fork changed after Google's last release (git diff 05bf25e..68edca5,
main sources): the `,,` → `,{},` JSON fix in `JsonRenderer` (96957fa),
JSONP callback sanitising in `DataSourceParameters`, ANSI-SQL changes in
the query/SQL packages (unused here), and newer dependency versions
(guava 27.1, icu4j 63.1, commons-lang 2.6, opencsv 2.3).

### Rendering evidence (both jars, same harness)

A harness built a uPortal-shaped table (DATETIME/DATE/TIMEOFDAY first
column, NUMBER series, TEXT with `,"<>`, and null cells) and rendered it
with `JsonRenderer.renderDataTable(t, true, true, false)` (what
`GoogleDataTableSerializer` calls), `CsvRenderer` and `HtmlRenderer`.
CSV and HTML output are byte-identical between ci-rt 1.1.3 and Google
1.1.1. JSON differs only on the null row:

```
ci-rt 1.1.3:  {"c":[{"v":"Date(2026,9,11,0,0,0)"},{},{"v":0.0},{"v":null}]}
google 1.1.1: {"c":[{"v":"Date(2026,9,11,0,0,0)"},,{"v":0.0},{"v":null}]}
```

uPortal's own controllers fill gaps with zero `NumberValue`s, so they never
emit null cells today; a custom report that does would break
`reportGraph.jsp` under (a).

## Recommendation

(b): vendor the 30 files unchanged. It is the only option that keeps
today's behaviour exactly, removes the third-party gviz artifact entirely,
and needs no adopter change. (a) is smaller, but it swaps a jitpack
artifact for an unmaintained 2012 one, needs an icu4j override to avoid a
known crash, and reintroduces the invalid-JSON bug.

Planned shape: `uPortal-gviz` (`src/vendored/java`, excluded from
Spotless; `src/vendored/SHA256SUMS` lists each file's upstream SHA-256),
`api` icu4j (`icu4jVersion=63.1`, new in gradle.properties; uPortal-web uses
`ULocale` directly), Guava, commons-lang 2.6, jcl-over-slf4j;
`uPortal-web` depends on it instead of the jar; jitpack repo and
`googleVisualizationVersion` removed; NOTICE credits Google and the ci-rt
fork; a SpotBugs baseline generated for the new module; UPGRADING-6.0
notes the artifact swap (same packages, opencsv no longer in the WAR).
Tests planned: a characterisation test pinning the JSON/CSV/HTML output
above (including the null-cell case), a test for the report controller →
`JsonDataTable` → Jackson path, then a fresh build with an empty Gradle
cache and no gviz jar in mavenLocal.

## Blocked

The session's permission classifier refused (1) installing the
5.17.9-WAR jar into `~/.m2` and routing Central through the Google mirror
(“Package Registry Bypass”), and (2) wiring the vendored source into the
build (“Untrusted Code Integration”). Neither was retried another way.
The harness above ran with plain `javac`/`java` against jars taken from the
official uPortal-webapp 5.17.9 WAR on Maven Central (gviz jar SHA-256
eca091720c6f98906c53aa8157fc9458a49920759bd149074351221770bbad63,
verified).

## Proposed LOG updates

* MOD-I51: resolution “vendored the 30 files uPortal uses from ci-rt
  google-visualization-java release-1.1.3 into `uPortal-gviz`; jitpack
  removed” once implemented.
* MOD-D26 follow-on: note the second vendored module and that it keeps
  upstream package names (with the reason).
