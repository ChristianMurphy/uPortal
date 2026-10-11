# Handoff: WS14 foundation (web component channel type and Favorites)

Workstream 14 replaces uPortal's built-in portlets with REST APIs and web components (MOD-D18 to
MOD-D21, MOD-D31, MOD-D34). This part is pushed to the integration branch `ccr-43ba0785-hzlrj5`
in uPortal and uPortal-start. The developer guide is `docs/developer/web-components/README.md`.

## Done

uPortal, oldest first:

| Commit subject | What |
| --- | --- |
| `feat(rendering): add a web component channel type` | `WebComponentRenderExecutionWorker`, `WebComponentChannelRenderer`, `WebComponentModuleResolver`, `WebComponentConfig`; `PortletWorkerFactoryImpl` returns the worker for the Web Component type; the `WebComponent` descriptor in `portlet.xml` and `WebComponent.cpd.xml` |
| `feat(api): add a messages rest api for web components` | `/api/v6-0/messages` from `Messages.properties` |
| `build(webcomponents): add vite+ lit workspace with vitest browser tests` | `uPortal-webcomponents/` with Gradle tasks `vpCheck`, `vpTest`, `vpBuild`, `installChromium` |
| `feat(favorites): add favorites rest api` | `/api/v6-0/favorites` on `FavoritesUtils` and the layout manager; CSRF on `/api/v6-0/` |
| `feat(favorites): add up-favorites web component` | `<up-favorites>` with Bootstrap 6 in a shared constructable stylesheet |
| `fix: null-mark the web component packages and clear spotbugs findings` | `@NullMarked` packages, SpotBugs clean |
| `fix(favorites): compare fnames by value when unfavoriting` | MOD-I131, with a regression test |
| `build(webcomponents): enforce the coverage thresholds in every file` | 90% lines, branches, functions and statements per file |
| `feat(webcomponents): add an xstate actor controller for lit elements` and `refactor(favorites): run up-favorites on a state machine` | MOD-D31 |
| `docs: record the ws14 foundation and the issues it found` | LOG status lines, MOD-I130 to MOD-I137 |
| `docs(webcomponents): draw charts with apache echarts` | MOD-D34 rule in the guide |

uPortal-start, oldest first:

| Commit subject | What |
| --- | --- |
| `ci(prototype): skip the released-uportal playwright run on ccr branches` | MOD-I135 |
| `test(db): let the harness accept listed data changes` | `tests/db/snapshots/<baseline>/intended-changes.txt`, MOD-I134 |
| `feat(data): render favorites as a web component channel` | Web Component portlet type, converted `favorites` definition, the two intended row deltas |
| `test(e2e): cover the favorites web component and the channel type` | 4 new tests in `favorites.spec.ts`, 3 in `web-component-channel.spec.ts` |

Local results before the push were Vitest with 224 tests in 9 files (100% lines, 97.8% branches); the
Gradle build, SpotBugs and Spring 5.3 probe of the changed modules; two full Playwright runs on
HSQLDB against uPortal built from source, where only the two sandbox web-proxy tests and one
portlet-list test that times out under load failed; the hsqldb harness found both intended
changes.

## Open

* MOD-I133: the import upgrader and the copy-tool rule for converting Favorites definitions are
  documented, not built.
* MOD-I137: harness drift from aggregation jobs that write rows during a slow `dataInit`.
* MOD-I130: the legacy `/api` endpoints are still exempt from CSRF.
* The Favorites portlet class stays in uPortal, unused by the quickstart data. The guide's
  "Deleting the Favorites portlet" section lists what removing it needs.

## Pushed and running

uPortal `76636e0cf..9c996809b`. Its last commit is docs-only (`[skip ci]`), so CI ran by
dispatch: CI 38104024957 and Workflow lint 38104026344. uPortal-start `defd95dd4..4bddf44a7`: CI
38104005708, Integration 38104005683 and Workflow lint 38104005697. Integration is the first CI
run of the WS14 specs and of `intended-changes.txt` on every 6.0 database.

## Next step

Check those runs. A harness job that fails on `UP_PORTLET_TYPE` or `UP_PORTLET_DEF_PARAM`
means a vendor moved its generator differently. The drift report shows the line that differs. Then record the
conclusions in the MOD-D29 status line. The next conversion follows "Converting the next portlet"
in the guide.
