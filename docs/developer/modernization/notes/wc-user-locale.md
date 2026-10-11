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

uPortal CI on `794cdf0`, the seeded fault (see below). It was still queued at 08:15.

## Next steps

1. When uPortal CI on `794cdf0` is red in `UserLocaleServiceTest`, push the revert (`007cc1a`
   locally, `[skip ci]`, the same files as `5ce0ee9`).
2. Then dispatch the full-matrix CI on the uPortal branch and `integration.yml` on the
   uPortal-start branch, and send the coordinator the run IDs and both head SHAs.
3. When the login and CSRF fix is merged into `ccr-43ba0785-hzlrj5`, merge it into both topic branches
   and run them again: it tightens CSRF on `/api`.

## Seeded faults

| Fault | Check | Result |
| --- | --- | --- |
| The machine doesn't emit `locale.changed`, so the page doesn't reload | Vitest, local | 18 of 104 tests failed |
| The radio buttons are enabled while saving | Vitest, local | 34 of 104 tests failed |
| The validator accepts a selected code that isn't offered | Vitest, local | 2 of 104 tests failed |
| `isPortalLocale` accepts every code (`794cdf0`) | uPortal CI, `UserLocaleServiceTest` | Pending |

Each local fault was undone before the next one, and the suite passed again (104 of 104). The
Playwright component spec checks the same 400 answer as the CI fault, through the running portal.
Runners were short, so it wasn't seeded separately.

## CI

| Run | Commit | Result |
| --- | --- | --- |
| uPortal CI 38115501518 | `3da8e6c` (REST API and component) | Green |
| uPortal-start Integration 38114907893 | `78647e6` (spec against the portlet) | Red: the order problem fixed in `a95e9cb`. The other 138 tests passed on hsqldb and postgres-18 |
| uPortal-start Integration 38116706216 | `a95e9cb` (spec against the portlet) | Green, all 7 jobs |
| uPortal-start Integration 38119557843 | `b51fdde` (both specs against the component) | Green, all 7 jobs, with the harness accepting `UP_PORTLET_DEF_PARAM rows +4` |
| uPortal CI 38122658784 | `794cdf0` (seeded fault) | Pending |

## Proposed LOG rows

The coordinator numbers them.

* MOD-D18 status: "User Locale: `/api/v6-0/locale` (GET, PUT) and `<up-user-locale>`. uPortal-start
  publishes `user-locales-selector` as a Web Component channel. The `UserLocale` portlet and its
  `locale-selector` flow stay until the 6.0 data migration converts existing definitions
  (MOD-I133). The web components guide describes the conversion."
* New issue: "The `UserLocale` portlet's flow passed any code the form sent to
  `UserLocaleHelper.updateUserLocale`, which parses any string into a locale and stores it.
  `/api/v6-0/locale` accepts only the locales in `portal_locales` and answers 400 to others.
  UPGRADING-6.0 says so."
* New issue: "The user locale selector checks no language for a user who never saved one, and
  the theme then renders in the portal's default locale. The component keeps this. A later change
  could check the language the page is rendered in."
