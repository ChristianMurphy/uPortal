# Handoff: Popular Portlets as a web component (WS14)

Converts the Popular Portlets portlet ("Most Popular Apps", fname `popular-portlets`) to a REST
API and a web component, following "Converting the next portlet" in
`docs/developer/web-components/README.md` (MOD-D18 to MOD-D21, MOD-D31, MOD-D34).

Branches: `ccr-43ba0785-hzlrj5-wc-popular-portlets` in uPortal and in uPortal-start, both from
`ccr-43ba0785-hzlrj5`.

## The portlet today

* `PopularPortletsController` (uPortal-portlets, `org.apereo.portal.portlets.popular`), VIEW mode
  only, and `WEB-INF/jsp/PopularPortlets/listPortlets.jsp`, a DataTables table.
* Data: `PortletLayoutAggregation` rows (interval DAY, group Everyone) of the last N days, summed
  per fname: the number of times anyone added the portlet to a layout. Portlets the viewer may not
  subscribe to, or that no longer exist, are left out.
* uPortal-start publishes it in `data/base/portlet-definition/popular-portlets.portlet-definition.xml`.
  No layout places it. Users reach it at `/p/popular-portlets`.

## Status

| Step | State |
| --- | --- |
| 1. Characterisation spec (uPortal-start) | done: `tests/ux/portlets/popular-portlets.spec.ts`, 8 tests, green locally on a fresh HSQLDB portal (3.3 min, most of it waiting for the aggregation) |
| 2. REST API under `/api/v6-0/` | written, tests green locally, not yet committed |
| 3. `<up-popular-portlets>` component | not started |
| 4. Channel definition as Web Component | not started |
| 5. Spec against the component, seeded faults | not started |
| 6. UPGRADING-6.0 section | not started |

## Found while characterising

* The column filters keep the right number of rows but not always the right rows. Their search
  function reads `col.data()[dataIndex]`: `col.data()` is in the current sort order, `dataIndex` is
  the row's load position. Choosing `courses` in the Title filter showed "Who's Online". The spec
  checks only the counts there. Proposed issue row for the LOG.
* The Title filter lists fnames, not titles, and the first sort is by fname (the column's data),
  so "Welcome to uPortal" (`what-is-uportal`) sorts after "uPortal Links".
* `days` missing from the resource request is a `NullPointerException` (`minusDays(null)`). The
  JSP always sends it.

## Running it locally

* uPortal: `./gradlew --no-parallel install -x test -x javadoc`. Maven Central answered 429 to
  parallel downloads in this sandbox. A retry loop got through, because each try keeps what it
  fetched.
* uPortal-start: `gradle.properties` from the example with `uPortalVersion=5.17.10-SNAPSHOT`,
  `JAVA_HOME` on a JDK 11 (`apt-get install openjdk-11-jdk-headless`), because the import tools fail on 21,
  then `portalInit` and `tomcatStart`.
* Playwright 1.64 wants Chromium revision 1248, and the sandbox has 1194 under `/opt/pw-browsers`.
  Symlinks `chromium_headless_shell-1248/chrome-headless-shell-linux64` and
  `chromium-1248/chrome-linux64` to the 1194 builds work.

## Next step

Commit and push the REST API (`org.apereo.portal.rest.popular` in uPortal-api-rest).
