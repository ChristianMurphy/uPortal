<!-- markdownlint-disable -->

# A request racing a logout kept the user's layout in a new session (MOD-I?)

Branch `ccr-43ba0785-hzlrj5-session-timeout-flake` in uPortal and in uPortal-start, both from the
integration branch (uPortal `1d5fac532`, uPortal-start `2a7d891ee`). It is the fix for the red
uPortal-start tip. The integration history was rewritten on 2026-11-09 without a change to any
tree: the failed run's uPortal-start `6021af529` is now `2a7d891ee`, and the uPortal `287617ca3`
it built is now `1d5fac532`.

## What failed

uPortal-start Integration run 42516129147, job 128840231513 (G-migrate postgres-16 to
postgres-18), built uPortal `287617ca3`. `tests/ux/smoke/session-timeout.spec.ts:150` "logs the
user out when the countdown reaches zero" passed only on its retry. The first attempt found one
`div.user-name` on the page it loaded after the logout, where it expected none.

From the trace, the access log and `portal.log` of the artifact
`integration-migrate-postgres-16-to-postgres-18` (read in `/home/user/wt/stf-ci/`):

| Time (UTC) | What happened |
| --- | --- |
| 20:00:11.3 | The test signs in as `faculty`. `GET /api/v6-0/session` answers 200 with 1800 seconds. |
| 20:00:13.023 | The page's clock reaches zero and the component sends the browser to `/uPortal/Logout`. The test waited only for this request to start. |
| 20:00:13.029 | The test's `page.goto()` cancels that navigation (`net::ERR_ABORTED` after 5.9 ms). The portal had the request already: its access log has the `/Logout` with a 302. |
| 20:00:13.031 | `GET /uPortal/` arrives with the cookie of the session that the logout is ending (`6C8DEE…`). `portal.log` shows this request loading faculty's layout fragments at 20:00:13.040 on a thread named for `faculty`, which happens when a user instance is built. It answers 302 to faculty's `/f/welcome/normal/render.uP`. |
| 20:00:13.055 | `render.uP` arrives with a new session (`E450A1…`) and renders "You are signed in as Felicia Faculty", with faculty's layout and a new CSRF token. |
| 20:00:13.3 | In the same new session `GET /api/v5-1/userinfo` returns a token for `guest`, and `GET /api/v6-0/session` answers 401. |
| 20:00:18.6 | The test's `afterEach` logs out. The page is the guest page from then on. |

The brief's questions:

* **The element that remained** was the greeting of the `faculty` user, rendered by the portal
  after the logout. It wasn't a stale page.
* **The session's real expiry** played no part. The session was 2 seconds old, and the test moved
  only the page's clock. The countdown and the server agreed (1800 seconds). The portal ended the
  session at the logout, as it should.
* **No parallel spec** shared the session. Each session cookie in the trace was issued to this
  test's own browser context. Other specs sign in as `faculty` too, each with its own session,
  and the user was the right one here: the fault is that it was still shown.
* **java-time-jpa** isn't involved. The code below doesn't read a timestamp, and it is unchanged since
  the commit this clone's history starts at (`a951c7838`, upstream, 2026-04-28).

This spec had no earlier retry. Of the last 60 Integration runs on `ccr-43ba0785-hzlrj5*`
(uPortal-start, up to 2026-11-08), 32 failed. This run is the only one of them with a
`session-timeout.spec.ts` annotation.

## Cause

`UserInstanceManagerImpl.getUserInstance` read the person from the request's session
(`personManager.getPerson`, which uses `getSession(false)`), and then called
`request.getSession()` to find or keep the user instance. A logout on another request can end the
session between the two calls. Tomcat still returns a session and its attributes while the
session's listeners run during `invalidate()`, so the time for the race is the time those
listeners take, not the few instructions between the two lines. (That part comes from reading
Tomcat's `StandardSession`, not from a measurement.) After that `getSession()` starts a new
session, and the method stored a `UserInstance` built for the logged-out person in it.

The new session has no person of its own, so `personManager` makes a guest for it. The rendering
pipeline takes the person from the session's `UserInstance`. So the REST APIs saw a guest, and
every page of the new session rendered the logged-out user's name, layout and portlets until the
session ended. A browser gets into this state when any request of the old session is in flight
during the logout: a second tab, or a poll by a component.

The spec made the race likely: it waited for the `/Logout` request to start, and its next
`page.goto()` then cancelled the navigation and sent a request of its own 8 ms later. That is
also a fault in the spec. Had the cancel come before the browser sent `/Logout`, the session
would have stayed signed in and the test would have failed without any fault in the portal.

## Fix

* uPortal, `UserInstanceManagerImpl`: remember the session the person was read from. When
  `getSession()` then returns another session, read the person again, so the instance stored in
  the new session is the new session's own (a guest). The sessions are compared by identity:
  with the session cookie on `/`, Tomcat can give a new session the ID the browser sent while a
  portlet application still has a session with that ID.
* uPortal-start, `session-timeout.spec.ts`: the two logout tests wait for the portal's answer to
  `/Logout` (`waitForResponse`) before they load the next page. No timeout changed.

## Evidence

* `UserInstanceManagerImplTest.logoutDuringTheRequestLeavesNoUserInstanceInTheNewSession` ends
  the session right after the person is read. Without the fix it fails at its first person
  assertion with `expected same:<Mock for IPerson …> was not:<Mock for IPerson …>`: the instance
  for the new session belongs to the logged-out user. With the fix both tests of the class pass.
* Both results are from JDK 21 with `javac` and JUnit run manually against the Gradle cache's
  jars and the classes of another worktree, because Maven Central answered 429 to two Gradle
  attempts and this session's brief rules out a mirror. The uPortal push runs are the first Gradle
  run of the test, and of Spotless and SpotBugs on the change.
* No local portal run and no local Playwright run: the trace and the logs settle the cause, and
  the race needs a request inside a window of a few milliseconds.
* HTTP replay: the change alters a response only for a request that races a logout. The replay
  sends its requests one after another, so it has nothing to register.
* Push runs: RUNS_PLACEHOLDER

## Not done

* Other code that reads the person and then calls `getSession()` wasn't surveyed. This method is
  the one that decides whose layout a session renders.
* A request that overlaps the logout by less (it reads the person and the session before the
  logout ends, and writes to the session after) gets Tomcat's `IllegalStateException`, as before.
* The spec has no test that sends a request during a logout. In a browser test that is a race
  again. The unit test covers the order of events.

## Proposed LOG rows

| ID | Area | Finding |
| --- | --- | --- |
| MOD-I? | Security | A request in flight during a logout could leave the browser with a new session that showed the logged-out user's name, layout and portlets. The REST APIs treated that session as a guest. `UserInstanceManagerImpl.getUserInstance` read the person from the session, then called `getSession()`, which starts a new session once the logout has ended the old one, and stored the old person's `UserInstance` there. The code predates the modernization (unchanged since `a951c7838`). Fixed: the person is read again when the session changed. Found through uPortal-start Integration run 42516129147 (job 128840231513), where `session-timeout.spec.ts:150` passed only on a retry. |
| MOD-I? | Tests | `session-timeout.spec.ts` waited for the `/Logout` request to start and then loaded the next page, which cancelled the logout navigation. Both logout tests now wait for the answer to `/Logout`. |
