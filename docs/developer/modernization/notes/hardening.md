# Handoff: hardening (Tomcat error pages, existing axe violations)

The project owner approved this work on 2026-10-11. Part 1 hides the Tomcat version on error
pages. Part 2 fixes the axe violations recorded in the a11y baseline. Branches:
`ccr-43ba0785-hzlrj5-hardening` in uPortal and in uPortal-start, both from `ccr-43ba0785-hzlrj5`.
LOG numbers: MOD-I250 to MOD-I259.

## Status

| Part | State |
| --- | --- |
| 1. Tomcat error pages without the version | ready to merge. On uPortal-start `b0bc7f7`, Integration 38153992098 passed (Playwright on HSQLDB and PostgreSQL 18, 359 tests each), and so did HTTP replay 38153992140 and Migration equivalence 38153992169. CI 38153992096 failed in the Java 11 job when Maven Central didn't return three of `:buildSrc`'s poms, before any test ran. The same build files passed on the integration tip. G-index 38153992223 also fails on the integration tip |
| 1a. Tomcat's docs, examples and ROOT page removed (MOD-I251) | pushed (uPortal-start `e7b70d3`, UPGRADING-6.0 section "Tomcat's documentation, examples and ROOT page are gone"); green in 38141361903 |
| 1b. HTTP replay registry entry for the new error pages | pushed (uPortal-start `c2f0b0a`, `b0bc7f7`): `tomcat-error-page-no-version` replaces `tomcat-9-error-page-version`, and `tomcat-error-page-no-vary` explains the 400 pages that lost `Vary` (435 bytes, under `compressionMinSize`). Replay 38153992140 passes |
| Layout checks at standard sizes (MOD-D52) | pushed (uPortal-start `2412f7d`, uPortal `bc6eb94`). `b01ddf1` adds the 13 problems CI's fonts show. `23c69f5` removes the 2 entries that Integration 38153992098 no longer sees |
| 2a. Respondr layout fixes (MOD-I284, MOD-I285) | pushed (uPortal `886c73b`, `db1556b`; uPortal-start `94edb5d`): baseline 615 to 230 entries, 243 with CI's |
| 2b. Existing axe violations in the Respondr skin | on its own branch, `ccr-43ba0785-hzlrj5-a11y-fixes` (uPortal `1cdf6a51`), see `notes/a11y-fixes.md` there. The baseline commit waits for uPortal-start's `-test-suites` branch |
| 3. Respondr right to left (MOD-I234, MOD-D51) | not started: the owner set it after 2b |

## Part 1: Tomcat error pages

Commits: uPortal-start `fix(tomcat): leave the report and version off tomcat's error pages`
(server.xml valve and `tests/api/error-pages.spec.ts`). uPortal `docs: describe the tomcat error
page change` (UPGRADING-6.0 section "Tomcat's error pages no longer show the Tomcat version" and
this note).

What Tomcat 9.0.122 answered with uPortal-start's server.xml before the change (local Tomcat,
no portal): the "Apache Tomcat/9.0.122" heading plus Type, Message and Description on
* a 404 outside every webapp (served by ROOT), and in a context without a 404 error-page;
* a 400 from the connector: `%zz` (invalid percent-encoding), `%00`, and `{` in the request
  target (the last one also prints the IllegalArgumentException);
* a 500 from pluto-testsuite `jsp/test_results.jsp`, whose `<portlet:defineObjects/>` throws
  outside a portlet request (line 22).

No response had a `Server` or `X-Powered-By` header. With the valve, each page is the title and
an h1 with the status line only.

The spec requests `/uPortal/e2e-no-such-page` (404), `/e2e-no-such-webapp/` (404),
`/uPortal/%zz` (400) and `/pluto-testsuite/jsp/test_results.jsp` (500). For each it checks the
status, that the body is Tomcat's page (`HTTP Status <code>`), and that neither the body nor any
header contains "Apache Tomcat", "tomcat" (headers) or a dotted version number.

Seeded fault: the same spec against the old server.xml (no valve) fails all four tests on
"Apache Tomcat/9.0.122" in the body. Run locally against Tomcat 9.0.122 with uPortal-start's
server.xml, catalina.properties, context.xml and the shared jars, with the pluto-testsuite war;
not yet against a whole portal (CI does that).

The HTTP replay recordings of these error pages change. The h3 version line goes, and so do
the Type, Message and Description paragraphs. About 20 recorded request paths show Tomcat's page, among them
`/uPortal/does-not-exist.html`, `/uPortal/s/does-not-exist` and several `/uPortal/api/*`. The
coordinator decided that this branch adds the registry entry (`tests/replay/intended-differences.json`,
format in uPortal-start `docs/http-replay.md`) after the http-replay branch is merged. It cites the
UPGRADING-6.0 section above and MOD-I250. The replay check on this branch then shows whether it
matches the changed paths. The existing entries `tomcat-9-error-page-version` and
`api-unmapped-404-page` match only the h3 line and the title or h1.

Tomcat's default webapps (MOD-I251, approved by the coordinator). The ROOT page at `/` had
"Apache Tomcat/9.0.122" in its title and h1, `/docs/` and `/examples/` answered 200, and
`/manager/html` and `/host-manager/html` answer 401. uPortal-start `e7b70d3` makes `tomcatConfig`
delete docs, examples and ROOT, and installs a ROOT that redirects `/` to `/uPortal/`. The
manager webapps stay. `tests/api/tomcat-webapps.spec.ts` failed all three tests on the old install
and passes after `tomcatConfig`.

## Layout checks at standard screen sizes (MOD-D52)

The project owner asked for these before part 2, so that parts 2 and 3 are measured against
them. The coordinator gave the number block MOD-I280 to MOD-I289.

uPortal-start commits: `41883a6` (the measuring code, `tests/layout/measure.ts`, the helper
`tests/layout/layout-checks.ts` and its fixture spec `layout-checks.spec.ts`) and `2412f7d` (the
page spec `pages.spec.ts`, `baseline.ts` and `docs/layout-tests.md`). uPortal commit `bc6eb94`
(`uPortal-webcomponents/src/test-support/layout.ts`, a copy of the checks, with its fixture tests
and a test of Favorites and Popular Portlets at container widths of 280, 400, 800 and 1200
pixels). A change to the checks goes into both copies. `docs/layout-tests.md` describes the
checks and what they leave out.

Local results, all on HSQLDB with uPortal 5.17.10-SNAPSHOT built from this branch:

* `tests/layout` passes, 45 tests in 3.2 minutes. Once the spec waited for the page to stop changing,
  two runs in a row observed the same problems. Before that wait, the Portlet Manager's table
  loaded at different times in different runs.
* The fixture spec passes, 15 tests. With the measuring code returning nothing, its 12 fault
  tests fail.
* On the student's welcome tab at 1366 pixels, a fault seeded for each check fails that
  check. The faults and results are in the body of `2412f7d`.
* `./gradlew :uPortal-webcomponents:check` passes, 583 tests. With the table of Popular
  Portlets made 1000 pixels wide, its tests fail at 280, 400 and 800 pixels.

Before it was right, the measuring code reported several things that aren't layout problems:
the skip link moved to (-1000, -1000), script and style text counted as portlet content, visually
hidden label text counted as extra lines, a badge with `position: absolute` counted as spill, and the
closed tab bar under the page at 320 pixels counted as overlapping the logo. Each now has a case
in the fixture spec, or a rule in `measure.ts` with a comment.

Open:

* CI runners have other fonts than this container, so CI may observe problems that the local
  baseline lacks. If Integration fails on them, add what CI observed (the `observed=` JSON in
  the failure) to the baseline, and say so in the commit.
* WebKit and Firefox at 390 and 1366 pixels: the browser projects are on the cluster branch.
  Add them once it is merged.
* The i18n pseudo-locales (longer text, right to left): run the checks with them once that
  branch is merged.

The baseline lists these problems, the to-do list for part 2, in short:

* every page: the tab bar's list is wider than its box, by 4 pixels at 1366 and 136 at 320
  (`#portalNavigationList`). The feedback portlet's wrapper is 0 pixels high with its link
  floated out of it. At 320 and 390 pixels, the header icons (waffle menu, notifications)
  overlap the logo, and the header row is 10 pixels wider than its box;
* at 320 pixels the menu toggle ends at 330 pixels, past the edge of the screen, which the
  body's `overflow-x: hidden` cuts off;
* from 768 pixels, each tab's link overlaps its drag handle and delete button by 3 to 4 pixels;
* the calendar's event list on the welcome tabs, the file uploader button on the welcome tabs,
  the sign-in links at 320 and 390, the Portlet Manager's table and paging, and the card titles of
  the admin's content grid.

## Part 2a: Respondr layout fixes

The coordinator's go-ahead (2026-10-11) split part 2: the layout problems first, the axe fixes
once the cluster branch is merged. uPortal `886c73b` makes `prepareSkinResources` take the skin's
sources as inputs; before, an incremental build kept the old CSS after an SCSS edit. uPortal
`db1556b` fixes what Bootstrap 5's `width: 100%` on `.row > *` and `.container-fluid` broke:
the menu toggle (now `width: auto`), the phone menu's sidebar (270px, no padding, tabs in a
column), the first tab's -4px margin, and floats in the pre-content region. UPGRADING-6.0 has
"Respondr's tab bar, header and phone menu fit their boxes again". uPortal-start `94edb5d` removes
385 baseline entries. Screenshots before and after differ only in the header strip at 390px and
the tab bar strip at 1366px.

Left for later: the tab links overlapping their drag and delete handles from 768px, the
calendar's event list, the file uploader button, the Portlet Manager's table and paging, the
content grid's card titles, and the guest search form.

## CI fonts and the report artifact

CI's fonts show problems this container's don't. The report artifact contains the full observed
lists, but its host (`productionresultssa5.blob.core.windows.net`) is refused by the proxy, so
only the failing pages' lists, from the job log, could be read. `b01ddf1` makes the spec print
every recorded entry a run no longer sees. Remove those entries after the next CI run.

## Part 2: existing axe violations

Plan for when the go-ahead comes. Read `tests/a11y/baseline.ts` in uPortal-start, from the
integration branch once the cluster branch is merged, and group its entries by rule and by the
skin file that makes the markup. The shared skin and the admin pages come first. Each commit
removes the entries it fixes from the baseline. Check with the coordinator before touching `#webSearchSubmit` (Search conversion).

A message key added for these fixes (labels, alt text, landmark names) gets Spanish and French
text in `Messages_es.properties` and `Messages_fr.properties` in the same commit as the English
(MOD-D50). Each apostrophe is written twice, because the portal formats messages with
MessageFormat. Accents are `\u` escapes until the i18n branch is merged.

## Part 3: right to left

Added by the project owner (MOD-I234, MOD-D51), after part 2. For a right-to-left locale (ar,
he, fa, ur) the page gets `dir` and `lang` from the user's locale, and the skin's stylesheets
mirror, with logical properties where the CSS is touched or with an RTL build of the skin. A
Playwright run in a right-to-left test locale shows that the navigation, the portlet chrome and a
web component mirror and that axe still passes. The i18n branch adds pseudo-locales; the test
reuses them once that branch is merged.

## Proposed LOG rows

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I250 | Security | Tomcat's own error pages (a 404 in uPortal or outside every webapp, a 400 from the connector, a 500 in a webapp without an error-page) show "Apache Tomcat/9.0.122" and the exception message. | **Fixed** by uPortal-start "fix(tomcat): leave the report and version off tomcat's error pages", which adds an ErrorReportValve to the Host with `showReport="false"` and `showServerInfo="false"`. No response has a Server header. Test: `tests/api/error-pages.spec.ts` (4 pages, fails on all four without the valve). UPGRADING-6.0: "Tomcat's error pages no longer show the Tomcat version". HTTP replay registers the changed error pages. |
| MOD-I251 | Security | uPortal-start installs Tomcat's default webapps (ROOT, docs, examples, manager, host-manager). The ROOT page at `/` names the Tomcat version, `/docs/` and `/examples/` answer 200, and the managers answer 401. | **Fixed** (coordinator, 2026-10-11) by uPortal-start `e7b70d3`. `tomcatConfig` now deletes the documentation and examples webapps along with Tomcat's ROOT page, and a new ROOT redirects `/` to `/uPortal/`. manager and host-manager stay. UPGRADING-6.0: "Tomcat's documentation, examples and ROOT page are gone". Test: `tests/api/tomcat-webapps.spec.ts`. |
| MOD-I280 | Tests | No test checked that pages fit the standard screen sizes (MOD-D52). | **Done** by uPortal-start `41883a6`. `tests/layout/measure.ts` measures the six checks of MOD-D52 from element boxes, a helper compares the result with a baseline, and a fixture spec seeds 12 faults. |
| MOD-I281 | Tests | The portal's pages already have layout problems at the standard sizes (the list in uPortal-start `2412f7d`). | **Recorded** in `tests/layout/baseline.ts` by uPortal-start `2412f7d`: 21 pages at 6 widths. A new problem fails. Fixing them is part 2, and each fix removes its entries. |
| MOD-I282 | Tests | The web components were not checked in a narrow portlet column. | **Done** by uPortal `bc6eb94`, which tests Favorites and Popular Portlets at container widths of 280, 400, 800 and 1200 pixels, with the same checks (`src/test-support/layout.ts`). Other components add theirs when the coordinator announces the helper. |
| MOD-I283 | Theme | Respondr's body has `overflow-x: hidden`, so a page wider than the screen doesn't scroll sideways: the part past the edge is cut off without any sign. At 320 pixels this cuts off the menu toggle. | The layout check counts it as a scroll problem. Part 2 fixes the width. Whether the body keeps `overflow-x: hidden` is for the skin owner to decide. |

## Working rules (MOD-D53, 2026-10-11)

Gradle runs in the session (with the Maven mirror init script outside the repos). Push once a
step passes locally, since each push cancels the branch's running checks. The push run (light
profile) on the head commit is the evidence for landing; there are no full-matrix runs per
branch, and queued runs stay where they are. This branch is the follow-up branch for MOD-D52:
any component on ccr that fails the layout checks goes into the baseline, or gets fixed if the
fix is small.

## Next step

The coordinator merges part 1. Part 2b continues on `-a11y-fixes`. Part 3 starts after 2b.
