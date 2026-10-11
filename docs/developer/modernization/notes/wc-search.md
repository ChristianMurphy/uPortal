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
| `feat(search): add spanish and french search messages` | MOD-D49: es and fr text for the ten new keys and `search.score` |
| `fix(search): count the query limit in characters, not utf-16 units` | MOD-D50: code points; "Zoë Ñandú 李小龍 😀" through the service, controller, a remote provider, the API client, the launcher and the URL |
| `fix(search): read remote providers from the propertyResolver bean` | the startup bug below; `RemoteJsonSearchProvidersTest` in a root and a servlet context |
| `fix(search): leave input method keys alone and use logical css` | MOD-D51: `event.isComposing`; logical margins; long words wrap |
| `fix(search): read the query from the render url the portal redirects to` | the `pP_query` bug below |

uPortal-start, oldest first:

| Commit subject | What |
| --- | --- |
| `test(e2e): characterise the search and search launcher channels` | `tests/ux/smoke/search.spec.ts`: 6 tests on what users see, with locators that work for the portlet and the components |
| `feat(data): render search as web component channels` | `search` and `search-launcher` as Web Component channels; intended row changes; `search-web-component.spec.ts` (5 tests) and `tests/api/search-v6-0.spec.ts` (4 tests) |
| `test(e2e): check search in spanish, french and any unicode text` | es_ES and fr_FR users (through the locale selector, set back to en_US), the Unicode query from both boxes, the API's limit in characters |
| `test(e2e): read the search query from the portlet parameter` | the specs read `pP_query`, as the component does |

## Running

Check the latest runs on both branches (GitHub Actions: CI in uPortal; Integration, CI and
G-index in uPortal-start). Integration builds the uPortal branch of the same name.

## Next steps

1. Done: the characterisation spec passes against the Search portlet. Integration run
   38122396831 on uPortal-start `202e6ff`, dispatched with `uportal-ref` set to uPortal
   `1aa8d008c` (the API and the components, with the portlet still in place): all 6 search tests
   passed on hsqldb (141 of 141) and on postgres-18. The postgres-18 job failed on 8
   Announcements tests, which this branch does not touch.
2. Search analytics (coordinator's option a): `/api/v6-0/search?submitted=true` publishes a
   `SearchRequestEvent`; `SearchRequestAggregator` counts it and the stored 5.17 portlet action
   events (`SearchRequestAggregatorTest` reads both from stored JSON). The aggregation golden
   doesn't change: its event stream has no search events.
3. Done: the channel switch is pushed. The database harness on postgres-18 (Integration run
   38130101463) matched the baseline (result "ZERO DRIFT") with every intended change found: `UP_PORTLET_TYPE +1`,
   `UP_PORTLET_DEF_PARAM +9` (403 to 412), `UP_PORTLET_PREF -8` (121 to 113),
   `UP_PORTLET_PREF_VALUES -8` (132 to 124), and the removed `hibernate_sequences` generators.
4. Done, locally (MOD-D53): the portal built from this branch, started with uPortal-start's
   `portalInit` and `tomcatStart`, passes all 21 tests of `search.spec.ts`,
   `search-web-component.spec.ts` and `search-v6-0.spec.ts` (see "Seeded faults" for the setup).
5. Landing evidence: the push runs on the branch heads (uPortal CI, and uPortal-start CI,
   Integration, equivalence and G-index).

## Seeded faults

Each fault was seeded alone, its check run, and the source restored.

Vitest, run in the session container on uPortal `79bbff8ef` (each fault failed its file):

| Fault | Check | Result |
| --- | --- | --- |
| `search.try.again` default reads "Try once more" | `messages.unit.test.ts` | fails: the defaults differ from `Messages.properties` |
| `fetchSearchResults` drops `submitted=true` | `search-api.unit.test.ts` | fails: "asks for the query and each type" |
| type-ahead pause 0 ms instead of 200 ms | `up-search-box.machine.unit.test.ts` | fails: "waits 200 ms after typing" |
| a 401 goes to `failed.retryable` | `up-search.machine.unit.test.ts` | fails: "asks the user to sign in again after a 401" |
| the submit button's text is `aria-hidden`, so it has no name | `up-search.browser.test.ts` | fails: every test that finds the button named "Search" |
| results tabs always `aria-selected="false"` | `up-search.model.browser.test.ts` | fails: every path that ends in results |
| suggestions always `aria-selected="false"` | `up-search-box.model.browser.test.ts` | fails: 6 paths with an active suggestion |

JUnit, run by the coordinator in its container (JDK 21), one fault at a time:

| Fault | Check | Result |
| --- | --- | --- |
| J1: the aggregator drops its `SearchRequestEvent` branch | `SearchRequestAggregatorTest` | fails: `countsTheSearchesTheApiPublishes` |
| J2: the remote client follows redirects (`Redirect.NORMAL`) | `RemoteJsonSearchStrategyTest` | fails: `doesNotFollowRedirects` |
| J3: settings accept a URL without `{query}` | `RemoteJsonSearchSettingsTest` | fails: `refusesUnusableSettings` |
| J4: the controller publishes every search, submitted or not | `SearchResultsRESTControllerTest` | fails: `publishesOnlySubmittedSearchesForTheStatistics` |
| J5: `MAX_QUERY_LENGTH` raised to 2000 | `SearchServiceTest` | passed on `79bbff8ef`, a gap: the test built its queries from the constant. On `2e46e8936` the test uses a literal 200 and fails: `refusesBlankAndOverlongQueries` |

Playwright, against a portal started in the session container from this branch (MOD-D53: no CI
runners). The setup, kept outside the repositories: uPortal `./gradlew install` with a Maven
Central mirror init script; uPortal-start `gradle.properties` from the example with
`uPortalVersion=5.17.10-SNAPSHOT`; `portalInit` and `tomcatStart` with `JAVA_HOME` on a JDK 11 (the
portal's JDK, from `.sdkmanrc`; on JDK 21 the portlets' data import fails) and Gradle's daemon on
JDK 21. Each fault was applied alone, Tomcat restarted (it keeps serving a changed bundle until
then), the spec run with `--retries=0`, and the change undone. Afterwards all 21 tests passed
again.

| Fault | Check | Result |
| --- | --- | --- |
| the search field's `maxlength` 100 in the deployed `up-search` bundle | `search.spec.ts` | fails: "the header offers a search box" (expected "200", received "100") |
| `ArrowRight` no longer moves the results tabs, in the deployed bundle | `search-web-component.spec.ts` | fails: "the results tabs follow the keyboard" |
| the blank-query message reads "Type something to search for", in a rebuilt `uPortal-api-rest` jar | `search-v6-0.spec.ts` | fails: "refuses a blank query" |

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
* Decision (search statistics, coordinator): `SearchRequestEvent(searchTerms)`, published by
  `/api/v6-0/search` for searches the components submit (`submitted=true`), counted by
  `SearchRequestAggregator` with the 5.17 action events of the `search` portlet. The aggregation
  tables don't change. Alternatives: a synthetic portlet action event (needs a portlet window
  for a channel that is no longer a portlet), or no statistics.
* Removals (for the LOG): `uPortal-api-search` and `portal-search-4.0.xsd`, the Search portlet
  and its JSPs and Spring context, `IPortalSearchService`, `PortalSearchResults`, the GSA,
  Google CSE, portlet registry and marketplace search services, the search event definitions and
  global event, the Directory portlet's search event handler, `portletUrl.tld` and
  `PortletUtils.getStringFromPortletUrl`, `autosuggestHandler.js` and its CSS. UPGRADING-6.0
  lists each with its replacement.
* Finding: the Search portlet's header button had no accessible name (the skin hides its
  "Search" text and leaves an icon). The components' button is named "Search" (message
  `search.submit`), and a browser test checks it.
* Finding: the Search portlet left the People panel showing after a switch to another tab. Its
  script marked the first tab active on the `li`, where Bootstrap does not look, so Bootstrap
  never hid that panel. The characterisation spec checks only that the chosen tab's panel shows
  its message. The components render only the chosen tab's panel.
* Finding: axe in the model test sometimes measured a tab's color halfway through Bootstrap's
  color transition and reported a contrast failure. `axeViolations` now waits for running
  animations.
* Finding: a seeded fault that raised the search query limit from 200 to 2000 passed
  `SearchServiceTest`, which built its queries from `MAX_QUERY_LENGTH`. A test that takes its
  input from the constant it checks follows any change to that constant. The test now uses a
  literal 200. The testing rules could say so.
* Finding: uPortal did not start with the remote providers in it (MVCDispatcherServlet failed in
  init, in uPortal-start Integration run 38130101463). `RemoteJsonSearchProviders` had two
  constructors without `@Autowired`, and the one it needed took a
  `PortalPropertySourcesPlaceholderConfigurer` by type, of which the MVC servlet's context sees two.
  The unit tests built the class directly. The fix takes the root `propertyResolver` bean by name,
  and a test builds the bean in contexts laid out as the portal's. Proposed guide rule: each new
  Spring bean gets a test that wires it the way the portal does.
* Finding: the launcher's `/p/search/max?query=...` reaches the channel as
  `/p/search.ctf2/max/render.uP?pP_query=...`: the portal's redirect turns the parameter into a
  portlet parameter. The component read only `query`, so a search from the header showed an empty
  search. Unit and browser tests replace the navigation, so only a portal run found it. The
  component now reads `pP_query`, else `query`.
* Finding: MockMvc does not decode `+` in a query string as a space, and Tomcat does. A MockMvc test
  that sends `URLEncoder` output must write spaces as `%20`.
* Leftover: the Respondr skin still styles `#webSearchContainer` and `#webSearchSubmit`
  (`header.scss`, `search.scss`), which nothing renders now. They go with the skin migration
  (MOD-D21).
* Open question: the import upgrader and copy-tool rule for converting Search definitions belong
  with MOD-I133 (the same gap as Favorites).
