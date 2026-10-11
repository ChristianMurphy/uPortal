# Handoff: WS14 Session Timeout as a web component

Converts the Session Timeout portlet (fname `session-timeout`) to a REST API and a Lit web
component, following "Converting the next portlet" in `docs/developer/web-components/README.md`.
Topic branch `ccr-43ba0785-hzlrj5-wc-session-timeout` in uPortal and uPortal-start, from
`ccr-43ba0785-hzlrj5`.

## The portlet today

* `SessionTimeoutViewController` (uPortal-portlets) renders, in the `RENDER_HEADERS` part, a
  script that starts `up.SessionTimeout` (`scripts/session-timeout.js`), and in the body a
  hidden dialog (`jsp/SessionTimeout/body.jsp`).
* Inputs: the session's max inactive interval, and the preferences `enabled` (true),
  `dialogDisplaySeconds` (60, capped at the session timeout), `logoutURLFragment` (`/Logout`)
  and `resetSessionURLFragment` (`/api/ajax-success`).
* The warning opens `timeout - 30 s - dialogDisplaySeconds` after the page loads, as a modal
  jQuery UI dialog that Escape does not close: "Session expiration", "Your session will expire
  in N seconds.", "Do you want to stay signed in?", "Yes, keep me signed in" and "No, log me
  out".
* Keep-alive sends a GET to the reset URL. When it succeeds, the dialog closes and the timer
  starts again. When it fails, the page calls `alert('Error resetting your session!')`.
* Log out, or the countdown reaching zero: go to the logout URL.
* Tabs share the logout time in `localStorage` (`UportalSessionTimeoutLogoutTime`). A later
  tab's time postpones the warning in earlier tabs.
* uPortal-start places it in the header region of `authenticated-lo` for Authenticated Users.

## Status

| Step | State |
| --- | --- |
| 1. Characterisation spec | Done: uPortal-start `714f196`, `tests/ux/smoke/session-timeout.spec.ts`, 9 tests pass against the portlet |
| 2. REST API under `/api/v6-0/` | Done: uPortal `d2736e6` (`SessionTimeoutRESTController`, 6 MockMvc tests), uPortal-start `1b1875b` (`tests/api/session-timeout.spec.ts`) |
| 3. `<up-session-timeout>` | Done: uPortal `62870ab` and `747c04c`. 161 component tests (machine, browser, model-based); every new file has 100% lines and functions |
| 4. Channel definition | Done: uPortal-start `97e95e9`, Web Component definition, `UP_PORTLET_DEF_PARAM` delta +2 to +5 |
| 5. Spec against the component, seeded faults | Done: uPortal-start `7764e92`. The 9 characterisation tests pass unchanged against the component, and 4 component tests and a guest-with-session API test were added |
| 6. UPGRADING-6.0 | Done: "Session Timeout is a web component" |
| Equivalence reference | uPortal-start `3e544bc`: the Migration equivalence reference holds the converted `session-timeout` definition (run 38130202417 on `a154519` failed without it) |

Local runs on the last commits: the session API and session timeout specs, `favorites.spec.ts`
and `web-component-channel.spec.ts`, 29 of 29; `:uPortal-webcomponents:check` with 385 tests;
the HSQLDB harness passed all four drift checks and found both intended changes (`UP_PORTLET_DEF_PARAM`
403 to 408).

## Owner decisions

On 2026-10-11 the owner accepted the visible changes: the error shown in the dialog,
`resetSessionURLFragment` no longer read, logout URLs limited to portal paths, and 401 for
guests. UPGRADING-6.0 now tells sites that pointed the logout URL at another site to set
`logout.redirect` instead.

## Judgement calls not approved by the owner

Sent to the coordinator on 2026-10-11. The ones not recorded elsewhere:

* Changed at the coordinator's request: a failed `GET /api/v6-0/session` (5xx, network,
  malformed body) now falls back to the portlet script's 30-minute default and still warns. Only
  a 401 leaves the page without a warning (`inactive`). Machine and browser tests cover each
  failure kind, and a seeded fault without the fallback fails all six.
* The storage-key e2e test allows 40 seconds between the page load and the check.
* The model-based tests leave out `disabled` (their input is `enabled=true`). Browser tests
  cover it.
* The definition doesn't add portlet-preference rows. The renderer passes preferences to the
  element only through `webComponent.preferenceAttributes`, and the defaults are in the component.
* Bootstrap 6's dialog partial is in the shared sheet, which every component loads.
* `SessionTimeoutRESTController` is a new class beside the legacy `SessionRESTController`
  (`/api/session.json`), which is unchanged.

## Seeded faults

Each fault was seeded alone and reverted. The e2e faults were edits of the deployed bundle,
with Tomcat restarted so the page cache served them.

| Fault | Caught by |
| --- | --- |
| Buffer 60 s instead of 30 | e2e "opens 90 seconds", e2e storage key test; 18 Vitest tests |
| Escape closes the warning (`closedby="any"`, an empty cancel handler, the dialog shown once) | Vitest Escape test and cancel test. The e2e Escape test misses it, see findings |
| Keep-alive on a wrong path | e2e keep-alive tests (2); 51 Vitest tests after `747c04c` (none before it) |
| Another tab's later time ignored | e2e "a tab opened later"; 19 Vitest tests |
| Logout time under another storage key | e2e storage key test |
| Dialog not named by its title | e2e "names the warning dialog" |
| `logout-url` not checked | 3 Vitest tests |
| Countdown ends one second early | 1 Vitest test |
| Controller answers 200 to guests | MockMvc `refusesGuests`; API spec "a guest who has a session" (the cookieless guest test passed, so the second test was added) |

## Findings

* In the portal page a keydown listener calls `preventDefault()` on Escape after a modal
  `<dialog>` sees it. Chromium then keeps the dialog open whatever the component does. The e2e
  Escape test records the user-visible behaviour, but it cannot catch a component that lets
  Escape close the warning. The listener was not identified. The Vitest test on a bare page
  covers the component.
* `/webcomponents/assets/*` is answered with `Cache-Control: max-age=0` and `Expires` equal to
  `Last-Modified`, not the year that `cacheContext.xml` sets for the path. The page caching
  filter also keeps a copy in memory until Tomcat restarts.
* `dataImport` from the root fails on the integration branch (`prepareSkinResources` not
  found, MOD-I91, fixed on the tests-e2e branch), so the converted definition was loaded with
  `portalInit`.
* A cookieless GET to `/api/v6-0/session` reaches the controller without a session, so the
  CSRF filter does not create one for it.

## Proposed LOG rows

* MOD-D18 status line, Session Timeout converted. `/api/v6-0/session` and
  `<up-session-timeout>` (uPortal), Web Component definition in uPortal-start. The
  characterisation spec passes on the portlet and on the component.
* Issue: a portal-page keydown listener cancels Escape for modal dialogs in web components
  (first finding above). Locate it before a component relies on Escape.
* Issue: content-hashed web component modules are served with `max-age=0`, and the page caching
  filter keeps stale copies until Tomcat restarts (second finding). It affects every component.
* Issue: `session-timeout.keep-alive.failed` exists only in English (`Messages.properties`);
  the French and Spanish files have the other session-timeout keys.
* Sandbox notes for other workers. Maven Central answers 429 to most requests, and Google's
  mirror works. Playwright 1.64 needs Chromium 1248, linked from 1194.

## Running locally

* Maven Central (repo.maven.apache.org and repo1) answered 429 to most requests. Google's mirror
  `maven-central.storage-download.googleapis.com/maven2` worked. A Gradle init script in
  `~/.gradle/init.d/`, outside both repositories, points Gradle there.
* Only JDK 21 is installed, and the proxy refuses connections to api.adoptium.net. The data tools of the
  bundled portlets need `JDK_JAVA_OPTIONS="--add-opens=java.base/java.lang=ALL-UNNAMED
  --add-opens=java.base/java.util=ALL-UNNAMED"` (Jakarta spike B10). Tomcat adds its own.
* Playwright 1.64 expects Chromium revision 1248, and `/opt/pw-browsers` has 1194. A symlink tree
  in the scratchpad with the expected names, passed as `PLAYWRIGHT_BROWSERS_PATH`, works for
  Vitest and for the uPortal-start specs.
* uPortal: `./gradlew install -x test -x javadoc`. uPortal-start: copy
  `gradle.properties.example`, set `uPortalVersion=5.17.10-SNAPSHOT`, run `portalInit` and
  `tomcatStart`.

## Design

* `GET /api/v6-0/session` answers `{"maxInactiveIntervalSeconds": n}`. `POST
  /api/v6-0/session/keep-alive` (CSRF) extends the session and returns the same body. Guests and
  requests without a session get 401. The controller reads the session with `getSession(false)`.
* `<up-session-timeout>` gets the preferences as the attributes `enabled`,
  `dialog-display-seconds` and `logout-url` through `webComponent.preferenceAttributes`. The
  keep-alive API takes the place of `resetSessionURLFragment`. The timing rules and the
  `localStorage` key stay as in the portlet, so portlet and component tabs agree during an
  upgrade. The warning is a native `<dialog>` shown with `showModal()` (Bootstrap 6 Dialog).
* A failed keep-alive shows "Error resetting your session!" in the dialog instead of
  `window.alert`. The characterisation spec accepts either.
* The timers take a `clock` property (`now`, `after`, `every`), and tests set a
  `ManualClock`. Vitest's fake timers would also stop the browser runner's timers.

## Next step

Wait for CI on both topic branches, fix what it finds, then report to the coordinator. The
portlet's Java, JSP and script stay until the 6.0 data migration converts the definition
(MOD-I133), like Favorites.
