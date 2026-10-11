# Handoff: hardening (Tomcat error pages, existing axe violations)

The project owner approved this work on 2026-10-11. Part 1 hides the Tomcat version on error
pages. Part 2 fixes the axe violations recorded in the a11y baseline. Branches:
`ccr-43ba0785-hzlrj5-hardening` in uPortal and in uPortal-start, both from `ccr-43ba0785-hzlrj5`.
LOG numbers: MOD-I250 to MOD-I259.

## Status

| Part | State |
| --- | --- |
| 1. Tomcat error pages without the version | done locally, pushed; Integration run pending |
| 2. Existing axe violations in the Respondr skin | not started: waits for the coordinator's go-ahead after the cluster branch is merged |

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
registry entry is the replay session's to add, through the coordinator; it can cite the
UPGRADING-6.0 section above. The existing entries `tomcat-9-error-page-version` and
`api-unmapped-404-page` match only the h3 line and the title or h1.

Not changed, because it is outside the task. Tomcat's default webapps stay installed. The ROOT page at `/`
has "Apache Tomcat/9.0.122" in its title and h1, `/docs/` and `/examples/` answer 200, and
`/manager/html` and `/host-manager/html` answer 401. Proposed as MOD-I251.

## Part 2: existing axe violations

Plan for when the go-ahead comes. Read `tests/a11y/baseline.ts` in uPortal-start, from the
integration branch once the cluster branch is merged, and group its entries by rule and by the
skin file that makes the markup. The shared skin and the admin pages come first. Each commit
removes the entries it fixes from the baseline. Check with the coordinator before touching `#webSearchSubmit` (Search conversion).

A message key added for these fixes (labels, alt text, landmark names) gets Spanish and French
text in `Messages_es.properties` and `Messages_fr.properties` in the same commit as the English
(MOD-D50). Each apostrophe is written twice, because the portal formats messages with
MessageFormat. Accents are `\u` escapes until the i18n branch is merged.

## Proposed LOG rows

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I250 | Security | Tomcat's own error pages (a 404 in uPortal or outside every webapp, a 400 from the connector, a 500 in a webapp without an error-page) show "Apache Tomcat/9.0.122" and the exception message. | **Fixed** by uPortal-start "fix(tomcat): leave the report and version off tomcat's error pages", which adds an ErrorReportValve to the Host with `showReport="false"` and `showServerInfo="false"`. No response has a Server header. Test: `tests/api/error-pages.spec.ts` (4 pages, fails on all four without the valve). UPGRADING-6.0: "Tomcat's error pages no longer show the Tomcat version". HTTP replay registers the changed error pages. |
| MOD-I251 | Security | uPortal-start installs Tomcat's default webapps (ROOT, docs, examples, manager, host-manager). The ROOT page at `/` names the Tomcat version, `/docs/` and `/examples/` answer 200, and the managers answer 401. | Proposed: remove the default webapps in `tomcatInstall` (or send `/` to `/uPortal/`). Needs the coordinator's decision. It is not part of the approved task. |

## Next step

Wait for the Integration run on the uPortal-start branch, then tell the coordinator that part 1
is green and wait for the go-ahead for part 2.
