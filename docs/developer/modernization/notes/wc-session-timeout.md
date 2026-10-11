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
| 1. Characterisation spec (uPortal-start `tests/ux/smoke/session-timeout.spec.ts`) | Done: 9 tests pass against the portlet (uPortal-start `714f196`) |
| 2. REST API under `/api/v6-0/` | Drafted: `SessionTimeoutRESTController`, `GET /api/v6-0/session`, `POST /api/v6-0/session/keep-alive` |
| 3. `<up-session-timeout>` component | Written, not committed: machine tests (24), browser tests (38) and model-based tests (63) pass |
| 4. Channel definition as Web Component, intended changes | Not started |
| 5. Spec against the component, seeded faults | Not started |
| 6. UPGRADING-6.0 section | Not started |

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

## Next step

Commit the REST API with its tests, then the component.
