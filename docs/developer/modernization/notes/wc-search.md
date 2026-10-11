# Handoff: WS14 Search conversion (REST API and web components)

Branches: `ccr-43ba0785-hzlrj5-wc-search` in uPortal and in uPortal-start, both from
`ccr-43ba0785-hzlrj5`. Nothing is on the integration branch yet. The coordinator merges it. The
developer guide's section "The Search conversion" and UPGRADING-6.0's "Search is a web
component" describe the result.

## Decisions (coordinator, 2026-10-11)

* First decision: convert the REST mode only and keep the event mode as a portlet. **Replaced by
  the owner's scope change (2026-10-11, relayed by the coordinator):** in 6.0 the Search portlet
  and event-mode search are removed. All results come through `/api/v6-0/search` from
  `ISearchStrategy` beans, plus remote JSON search providers configured in portal properties
  (URL template with `{query}`, headers or API key, JSON Pointer field mapping, own tab, timeout,
  an error in its own tab). The user's cookies stay in the portal. GSA, the Google CSE service,
  portlet-event search, `searchTabs` and the event-mode JSPs are dropped.
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
| `feat(search): add configurable remote json search providers` | `RemoteJsonSearchStrategy`, `RemoteJsonSearchSettings`, `RemoteJsonSearchProviders`; per-group `failed` and `label`; tests on a loopback HTTP server (mapping, encoding, headers, no cookies, error status, bad JSON, redirects, timeout, refused connection) |
| `feat(search): show provider tabs and failed groups in up-search` | the component reads `label` and `failed`; 377 Vitest tests |
| `refactor(search)!: remove the search portlet and portlet-event search` | the portlet, JSPs, Spring context, `portlet.xml` entries and event definitions, `IPortalSearchService` and its services, `uPortal-api-search`, the Directory portlet's event handler, `PortletUtils.getStringFromPortletUrl`, the search beans and properties |
| `docs: describe remote search providers and the removed portlet` | UPGRADING-6.0 (removed parts, provider settings, examples), guide, this note |

uPortal-start, oldest first:

| Commit subject | What |
| --- | --- |
| `test(e2e): characterise the search and search launcher channels` | `tests/ux/smoke/search.spec.ts`: 6 tests on what users see, with locators that work for the portlet and the components |
| `feat(data): render search as web component channels` | `search` and `search-launcher` as Web Component channels; intended row changes; `search-web-component.spec.ts` (5 tests) and `tests/api/search-v6-0.spec.ts` (4 tests) |

## Running

Check the latest runs on both branches (GitHub Actions: CI in uPortal; Integration, CI and
G-index in uPortal-start). Integration builds the uPortal branch of the same name.

## Next steps

1. Characterisation spec green against the portlet (Integration on uPortal-start `7bacc1dee`,
   run 38115662860).
2. Waiting on the coordinator: search analytics. `SearchRequestAggregator` counts portlet action
   events for fname `search` with a `query` parameter, which only the portlet produced, so the
   Activity portlet's popular searches stop counting new searches. Proposed: a new portal event
   that `/api/v6-0/search` publishes for searches the user submits, counted by the same
   aggregator.
3. Push the channel switch; the same spec must pass against the components, with the new specs.
4. The harness: the intended changes are `UP_PORTLET_DEF_PARAM +7`, `UP_PORTLET_PREF -8` and
   `UP_PORTLET_PREF_VALUES -8` (the last two estimated: eight preferences with one value each).
   If the harness reports other counts, correct `intended-changes.txt` from its report.
5. Seeded faults, as throwaway commits reverted by a new commit: one per new check.
6. Message the coordinator with the CI run numbers and the proposed LOG rows below.

## Seeded faults

To be filled in from the CI runs.

## Proposed LOG rows (the coordinator numbers them)

* Status line for MOD-D18. Search is converted (`/api/v6-0/search`, `<up-search>`,
  `<up-search-launcher>`), and uPortal-start publishes both channels as Web Component channels.
* Issue: typing in the first version of the type-ahead machine reloaded the portlet list on every
  keystroke, because a `reenter: true` transition on a parallel region's state re-entered the
  whole machine. Found by the model-based test, fixed by handling typing in each popup state, with
  a regression test. The guide's state machine rules could say so.
* Decision (MOD-D, owner, 2026-10-11): search in 6.0 is `/api/v6-0/search` over server-side
  strategies only. The Search portlet, portlet-event search (`SearchRequest`/`SearchResults`,
  `uPortal-api-search`), `IPortalSearchService` with the GSA, Google CSE, portlet registry and
  marketplace services, and `searchTabs` are removed. Remote JSON search providers in portal
  properties replace GSA, CSE and portlets that answered search events. Alternatives considered:
  keep the event mode as a portlet (it needs the portlet container, which 6.0 keeps only for
  bundled and adopters' portlets), or give `IPortalSearchService` a servlet-request variant (keeps
  the GSA and CSE code, still loses portlet-event results).
* Issue: removing the portlet stops search analytics (`SearchRequestAggregator` counts the
  portlet's action events). Fix proposed to the coordinator, see "Next steps".
* Open question: the import upgrader and copy-tool rule for converting Search definitions belong
  with MOD-I133 (the same gap as Favorites).
