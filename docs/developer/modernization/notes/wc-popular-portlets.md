# Handoff: Popular Portlets as a web component (WS14)

Converts the Popular Portlets portlet ("Most Popular Apps", fname `popular-portlets`) to a REST
API and a web component, following "Converting the next portlet" in
`docs/developer/web-components/README.md` (MOD-D18 to MOD-D21, MOD-D31, MOD-D34). The result is
described in the guide's section on the Popular Portlets conversion and in UPGRADING-6.0.

Branches: `ccr-43ba0785-hzlrj5-wc-popular-portlets` in uPortal and in uPortal-start, both from
`ccr-43ba0785-hzlrj5`.

## The portlet before

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
| 1. Characterisation spec | done: uPortal-start `tests/ux/portlets/popular-portlets.spec.ts`, 8 tests, green on the portlet |
| 2. REST API | done: `GET /api/v6-0/popular-portlets` (uPortal-api-rest, `org.apereo.portal.rest.popular`), MockMvc and unit tests; uPortal-start `tests/api/popular-portlets-v6-0.spec.ts`, 5 tests |
| 3. Component | done: `uPortal-webcomponents/src/components/up-popular-portlets`, 95% or more per file |
| 4. Channel definition | done: the type is now Web Component, and `intended-changes.txt` has `UP_PORTLET_DEF_PARAM rows +4` |
| 5. Spec against the component | done: the 8 tests pass unchanged, plus 3 component tests; seeded faults below |
| 6. UPGRADING-6.0 | done, with the guide sections |
| CI | pushed, results pending (see "Next step") |

Commits, oldest first. uPortal: `22226f3` (this note), `c09eebf` (API), `df1c59c` (component),
`7bbd571` (paging text without surrounding space, found by the spec), `91eaeee` (stronger machine
test, found by a seeded fault), then the docs commit. uPortal-start: `44ae73c` (characterisation
spec), `07f4545` (API spec), `89627e4` (data), `6a97e02` (component tests).

## Local results

* uPortal-api-rest: `./gradlew :uPortal-api:uPortal-api-rest:test spotlessCheck spotbugsMain`
  green.
* uPortal-webcomponents: `./gradlew :uPortal-webcomponents:check` green, 551 tests. The new
  component has 30 browser tests, 236 model-based tests (144 shortest and 90 simple paths, every
  state value reached, axe in each final state), and unit tests for the API client, `table.ts`, the
  messages and the machine.
* uPortal-start on HSQLDB, uPortal built from the topic branch: the popular portlets spec and the
  API spec pass on a fresh database (16 tests, 3.4 min, most of it waiting for the aggregation).
  The whole suite: 145 passed and the two `web-proxy.spec.ts` tests failed. They fetch example.com,
  which the sandbox blocks, as in the WS14 foundation runs.
* Harness `tests/db/harness.sh --no-deploy hsqldb`: both intended changes found (`UP_PORTLET_TYPE`
  10 rows, `UP_PORTLET_DEF_PARAM` 407). It still reported drift, all of it in the bundled portlets'
  tables and sequences that the local `portalInit` had created in the same HSQLDB. CI runs the
  harness on a clean database.

## Seeded faults

Each fault was put in, the check run, and the code restored.

| Fault | Check | Result |
| --- | --- | --- |
| The service ignores the subscribe permission | `PopularPortletsServiceTest` | 1 of 14 failed |
| The controller accepts 366 days | `PopularPortletsRESTControllerTest` | 1 of 14 failed |
| The sort ignores the direction | `table.unit.test.ts` | 2 of 15 failed |
| A load leaves the page number as it was | `up-popular-portlets.machine.unit.test.ts` | passed at first; the test was strengthened (`91eaeee`), then 1 of 21 failed |
| No `aria-sort` on the sorted header | `up-popular-portlets.browser.test.ts` | 2 of 30 failed |
| Next is never disabled | `up-popular-portlets.model.browser.test.ts` | 206 of 236 failed |
| No `aria-sort` (deployed module) | characterisation spec | the sorting test failed |
| The Title filter compares titles (deployed module) | component test "a filter keeps the rows it names" | failed |
| The API counts 7 days by default (deployed jar) | API spec | "30 days by default" failed |

The deployed module and jar were patched in `.gradle/tomcat/webapps/uPortal` and Tomcat
restarted. Tomcat kept serving the old module until the restart.

## Behaviour that changed on purpose

* A filter keeps the rows it names. The portlet's search function read `col.data()[dataIndex]`:
  `col.data()` is in the current sort order, `dataIndex` is the row's load position, so choosing
  `courses` showed "Who's Online". The characterisation spec checks only the number of rows there.
* No `[no description available]` tooltip for a portlet without a description.
* Choosing other days keeps the sort, the filters and the page size. The portlet re-created the
  DataTable on the same element.
* `days` outside 1 to 365, or missing, is a 400 or the default 30. The portlet's resource request
  failed with a `NullPointerException` without it.

The Title column still sorts and filters by fname. The # Times filter still lists the counts as
text in code-unit order. The info line, the paging buttons and the note have the same texts.

## Proposed LOG rows

For the coordinator to number:

* Issue: the Popular Portlets portlet's column filters show the wrong rows once the table is
  sorted (the DataTables search function indexes `col.data()`, which is in display order, with
  the row's load index). Fixed by `<up-popular-portlets>`. The portlet itself keeps the bug.
* Issue: `PopularPortletsController.getEventCounts` throws a `NullPointerException` when `days` is
  missing. `/api/v6-0/popular-portlets` answers 400 or uses 30.
* Issue: in uPortal-start on the integration branch, `./gradlew dataImport -Dfile=...` fails with
  "Task with path ':overlays:uPortal:prepareSkinResources' not found" (MOD-I91, fixed on the
  tests-e2e topic branch). The conversion was tested with `dataInit` instead.
* Issue: uPortal-start commit `07f4545` on this topic branch has the phrase "the 403 answer to",
  which Vale's commit rule refuses. Its push's commit-message job fails. Fix the wording when the
  branch lands (MOD-D37).
* Status line for MOD-D18. Popular Portlets is converted to `/api/v6-0/popular-portlets` and
  `<up-popular-portlets>`, which uPortal-start publishes as a Web Component channel. The
  characterisation spec passed on the portlet and then, unchanged, on the component. The table has
  no chart, so MOD-D34 does not apply. The import upgrader and copy-tool rule are not built (as
  MOD-I133).
* Status line for MOD-D21. The shared Bootstrap 6 sheet now also has pagination, tables, labels and
  form-control. Bootstrap 6 has no `.form-select`, and `select.form-control` takes its place.

## Running it locally

* uPortal: `./gradlew --no-parallel install -x test -x javadoc`. Maven Central answered 429 to
  parallel downloads in this sandbox. A retry loop got through, because each try keeps what it
  fetched.
* uPortal-start: `gradle.properties` from the example with `uPortalVersion=5.17.10-SNAPSHOT`,
  `JAVA_HOME` on a JDK 11 (`apt-get install openjdk-11-jdk-headless`), because the import tools
  fail on 21, then `portalInit` and `tomcatStart`. After `tests/db/harness.sh`, run `hsqlStart`
  again, because the harness stops HSQLDB.
* Playwright 1.64 wants Chromium revision 1248, and the sandbox has 1194 under `/opt/pw-browsers`.
  Symlinks `chromium_headless_shell-1248/chrome-headless-shell-linux64` and
  `chromium-1248/chrome-linux64` to the 1194 builds work, for uPortal-start and for the Vitest
  browser tests.

## Next step

Watch CI on both branches. In uPortal that is CI on `91eaeee`. In uPortal-start it is CI and
Integration on `6a97e02`. Integration builds the uPortal topic branch, then runs the specs on
HSQLDB and PostgreSQL 18 as well as the harness. Fix what fails, then hand off to the coordinator.
