# Handoff: User Locale as a web component (WS14)

Workstream 14 converts the User Locales Selector portlet (`user-locales-selector`, framework
portlet `UserLocale`, the `locale-selector` Web Flow) into `/api/v6-0/locale` and
`<up-user-locale>`. The work is on `ccr-43ba0785-hzlrj5-wc-user-locale` in uPortal and
uPortal-start, cut from `ccr-43ba0785-hzlrj5` (uPortal `d864e63`, uPortal-start `dd3f7e8`). The
steps follow "Converting the next portlet" in `docs/developer/web-components/README.md`.

## How it was verified

This session couldn't build uPortal locally. Maven Central answers 429 from the container, and the
session's permission rules refused a mirror. GitHub Actions did the Gradle and Playwright
verification instead. uPortal CI builds and tests the uPortal branch. uPortal-start's Integration
workflow builds the uPortal branch of the same name and runs Playwright against it. npm worked, so
`vp check`, `vp test` and `vp build` in `uPortal-webcomponents` ran locally, with the sandbox's
Chromium 1194 linked under the names Playwright 1.64 expects.

## Done

uPortal:

| Commit subject | What |
| --- | --- |
| `feat(api): add a user locale rest api` | `UserLocaleRESTController` and `UserLocaleService` (`org.apereo.portal.rest.locale`, `@NullMarked`) on `UserLocaleHelper`, with MockMvc tests behind the real CSRF filter and service tests on the real helper |
| `feat(webcomponents): add up-user-locale web component` | `<up-user-locale>`, its machine, API client, messages and tests. The shared Bootstrap 6 sheet gains `forms/form-field` and `forms/radio`. `Messages.properties` gains `locale.load.fail`, `locale.save.fail` and `locale.try.again` |
| `docs: describe the user locale web component` | UPGRADING-6.0 section, guide sections, this note |

uPortal-start:

| Commit subject | What |
| --- | --- |
| `test(e2e): record what the user locale selector does today` | `tests/ux/smoke/user-locale.spec.ts`, the characterisation spec: 4 tests, role and name locators only |
| `test(e2e): start the user locale spec from a saved english choice` | The portlet doesn't check any radio button for a student who never saved a language. Each signed-in test now saves English first. The guest test checks that a new session has every radio button unchecked |
| `feat(data): render the user locale selector as a web component channel` | `user-locales-selector` as a Web Component channel. `UP_PORTLET_DEF_PARAM rows +4` in the harness's intended changes |
| `test(e2e): cover the user locale web component` | `tests/ux/smoke/user-locale-component.spec.ts`, 3 tests. The element gets the portal's context attributes. The API refuses a PUT without the CSRF token (403) or with an unknown code (400). A failed save shows an alert |

Local results for `uPortal-webcomponents`: `vp check` clean. `vp test`: 328 tests in 13 files, of
which 104 are new in 4 files (14 API, 15 machine, 23 browser, 52 model-based). Every new file has
100% lines, functions and statements, and branches from 94.4% (`up-user-locale.ts`) to 100%.
`vp build` writes `up-user-locale` into `manifest.json` (6.4 kB, 2.3 kB gzipped).

## Running

Integration on uPortal-start `b51fdde`: both specs against the component.

## Next steps

1. Integration on `b51fdde` must pass `user-locale.spec.ts` unchanged and
   `user-locale-component.spec.ts`, with the harness accepting `UP_PORTLET_DEF_PARAM rows +4`.
2. Seed one fault in each new check, show the red run, revert in a new commit.
3. Report to the coordinator with CI run IDs and the proposed LOG rows.

## CI

| Run | Commit | Result |
| --- | --- | --- |
| uPortal CI 38115501518 | `3da8e6c` (REST API and component) | Green |
| uPortal-start Integration 38114907893 | `78647e6` (spec against the portlet) | Red: the order problem fixed in `a95e9cb`. The other 138 tests passed on hsqldb and postgres-18 |
| uPortal-start Integration 38116706216 | `a95e9cb` (spec against the portlet) | Green, all 7 jobs |

## Proposed LOG rows

To be filled in when the work is done.
