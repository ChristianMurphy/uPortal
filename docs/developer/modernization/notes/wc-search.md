# Handoff: WS14 Search conversion (REST API and web components)

Branches: `ccr-43ba0785-hzlrj5-wc-search` in uPortal and in uPortal-start, both from
`ccr-43ba0785-hzlrj5`. Nothing is on the integration branch yet. The coordinator merges it. The
developer guide's section "The Search conversion" and UPGRADING-6.0's "Search is a web
component" describe the result.

## Decisions (coordinator, 2026-10-11)

* Convert the Search portlet's REST mode only, the one uPortal-start uses (`RESTSearch=true`).
  The event mode (`RESTSearch=false`: `searchServices` beans, `searchTabs`, portlet events) stays
  a portlet, unchanged; `IPortalSearchService` is not changed.
* The header launcher is in scope: `<up-search-launcher>` in the same module, sending a GET to
  `/p/search/max?query=...`, which `<up-search>` reads.
* Keep type-ahead over `/api/portletList` (one character, 200 ms pause, a choice opens
  `/p/<fname>/max`), in both elements.
* Verification runs in GitHub Actions, because Maven Central answers 429 to Gradle in this
  container, and Gradle allow rules are not added for cloud sessions. npm and Vitest run locally.

## Done

uPortal, oldest first:

| Commit subject | What |
| --- | --- |
| `feat(search): add a search rest api for web components` | `GET /api/v6-0/search` (`SearchResultsRESTController`, `SearchService`) in the null-marked package `org.apereo.portal.rest.search.v6`, over the existing `ISearchStrategy` beans; MockMvc tests behind the CSRF filter and unit tests |
| `feat(search): add up-search and up-search-launcher web components` | `<up-search>`, `<up-search-launcher>`, the shared `<up-search-box>` with type-ahead; two XState machines; 373 Vitest tests (machine, browser, model-based), every file at 90% or more; form, input group and nav partials in the shared sheet; a `shared` chunk for Lit and the base library; nine new messages |
| `style(search): apply google-java-format to the search api` | the Spotless output CI asked for |
| `docs: record the search conversion` | guide, UPGRADING-6.0, this note |

uPortal-start, oldest first:

| Commit subject | What |
| --- | --- |
| `test(e2e): characterise the search and search launcher channels` | `tests/ux/smoke/search.spec.ts`: 6 tests on what users see, with locators that work for the portlet and the components |
| `feat(data): render search as web component channels` | `search` and `search-launcher` as Web Component channels; intended row changes; `search-web-component.spec.ts` (5 tests) and `tests/api/search-v6-0.spec.ts` (4 tests) |

## Running

Check the latest runs on both branches (GitHub Actions: CI in uPortal; Integration, CI and
G-index in uPortal-start). Integration builds the uPortal branch of the same name.

## Next steps

1. Characterisation spec green against the portlet (Integration on uPortal-start `7bacc1dee`).
2. Push the channel switch; the same spec must pass against the components, with the new specs.
3. The harness: the intended changes are `UP_PORTLET_DEF_PARAM +7`, `UP_PORTLET_PREF -8` and
   `UP_PORTLET_PREF_VALUES -8` (the last two estimated: eight preferences with one value each).
   If the harness reports other counts, correct `intended-changes.txt` from its report.
4. Seeded faults, as throwaway commits reverted by a new commit: one per new check.
5. Message the coordinator with the CI run numbers and the proposed LOG rows below.

## Seeded faults

To be filled in from the CI runs.

## Proposed LOG rows (the coordinator numbers them)

* Status line for MOD-D18. Search is converted (`/api/v6-0/search`, `<up-search>`,
  `<up-search-launcher>`), and uPortal-start publishes both channels as Web Component channels.
* Issue: typing in the first version of the type-ahead machine reloaded the portlet list on every
  keystroke, because a `reenter: true` transition on a parallel region's state re-entered the
  whole machine. Found by the model-based test, fixed by handling typing in each popup state, with
  a regression test. The guide's state machine rules could say so.
* Open question: the future of event-mode search. A portal with `RESTSearch=false` keeps the
  Search portlet, which needs the portlet container, Spring Portlet MVC and portlet events, so it
  cannot reach 6.0's Jakarta switch as it is. Options: drop it in 6.0 (results from other
  portlets go away), or give `IPortalSearchService` a servlet-request variant and run the
  services from `/api/v6-0/search`, leaving portlet-event results behind.
* Open question: the import upgrader and copy-tool rule for converting Search definitions belong
  with MOD-I133 (the same gap as Favorites).
