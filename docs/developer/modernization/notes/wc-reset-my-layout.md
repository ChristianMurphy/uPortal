# Handoff: WS14 Reset My Layout as a web component

Converts the Reset My Layout portlet (fname `reset-my-layout`) to a REST API and a Lit web
component, following "Converting the next portlet" in `docs/developer/web-components/README.md`.
Topic branch `ccr-43ba0785-hzlrj5-wc-reset-my-layout` in uPortal and uPortal-start, from
`ccr-43ba0785-hzlrj5`.

## The portlet today

* Framework portlet `ResetMyLayout` in `WEB-INF/portlet.xml`, a `DispatcherPortlet` with
  `properties/contexts/portlet/ResetMyLayout-portlet.xml`, which maps VIEW to the Spring Web Flow
  `reset-my-layout` (`WEB-INF/flows/reset-my-layout/`).
* The flow's view `reset-begin.jsp` shows the description and the note from
  `reset.my.layout.description` and `reset.my.layout.note` (that the user must sign in again to
  see the change), and a Reset Now button.
* Reset Now looks the user up (`personLookupHelper.getSelf`) and starts the subflow
  `reset-user-layout`, which the admin User Manager flow also uses. Its `reset-confirm.jsp` shows
  "Reset User Layout", "Resetting a user's layout is irreversible. Are you sure you want to reset
  the layout for student?", Reset Layout and Cancel. Reset Layout calls
  `userLayoutHelper.resetUserLayout(person)` and shows `reset-result.jsp`: "Success", "Layout for
  student has been reset" and Continue. Cancel and Continue end the flow, which starts again.
* `UserLayoutHelperImpl.resetUserLayout` sets the default profile's layout ID to 0 and deletes the
  user's structure and theme stylesheet preferences. The session keeps the layout it loaded, so
  the default layout shows from the next sign-in.
* uPortal-start publishes it to Authenticated Users and places it in no layout. Guests who open
  `/p/reset-my-layout` get the login portlet.

## Status

| Step | State |
| --- | --- |
| 1. Characterisation spec | Done: uPortal-start `b138960`, `tests/ux/smoke/reset-my-layout.spec.ts`, 7 tests pass against the portlet (two runs in a row, then `per-role-pages.spec.ts` still passes) |
| 2. REST API under `/api/v6-0/` | Done: uPortal `31f57f4` (`ResetMyLayoutRESTController`, 7 MockMvc tests), uPortal-start `f2db0a9` (`tests/api/reset-my-layout.spec.ts`, 5 tests pass on the deployed build) |
| 3. `<up-reset-my-layout>` | Done: uPortal `034f94e`. 80 component tests (37 unit cases, 25 browser cases, 18 model cases). Every new file has 100% lines. `vp check`, `vp test --coverage` (301 tests) and `vp build` pass |
| 4. Channel definition | Done: uPortal-start `e3c7803`, Web Component definition; `UP_PORTLET_DEF_PARAM` delta +2 to +4 on the existing line |
| 5. Spec against the component, seeded faults | Done: uPortal-start `ec3c105`. The 7 characterisation tests pass unchanged against the component, and 4 component tests were added. The API spec reads the channel's element. A new test found the 403 case, fixed in uPortal `2f8f0f0` |
| 6. UPGRADING-6.0 | Done: "Reset My Layout is a web component", with the portlet, its context and its flow deleted |

## Running locally

* Maven Central answers 429. A Gradle init script in `~/.gradle/init.d/`, outside both
  repositories, puts Google's mirror first. uPortal-start's `gradle/tasks/perf.gradle` resolves
  jasypt from its own `buildscript` block, which the init script does not reach; it also reads
  `mavenLocal()`, so `org/jasypt/jasypt/1.9.3` was copied into `~/.m2` from the mirror.
* uPortal: `./gradlew install -x test -x javadoc`. uPortal-start: `gradle.properties` from the
  example with `uPortalVersion=5.17.10-SNAPSHOT`, then `portalInit` and `tomcatStart` with
  `JDK_JAVA_OPTIONS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED"`.
* Playwright 1.64 wants Chromium 1248 in `chrome-linux64` and `chrome-headless-shell-linux64`
  directories. A symlink tree in the scratchpad maps them to the 1194 build in `/opt/pw-browsers`
  (`headless_shell` linked as `chrome-headless-shell`), passed as `PLAYWRIGHT_BROWSERS_PATH`.
* Tab names in the layout hold at most 35 characters (`UP_LAYOUT_STRUCT.NAME`); a longer name
  makes `/api/layout?action=addTab` answer 500.

## Seeded faults

Each fault was seeded alone and reverted.

| Fault | Caught by |
| --- | --- |
| Controller answers guests as users (no `isGuest()` check) | MockMvc `refusesGuests` |
| Machine doesn't set a notice when a reset answers 500 | 1 machine test, 1 browser test (before the machine tests covered each error kind, only the browser test) |
| Confirmation heading not focused | 6 browser tests, 10 model tests (none before the model test listed the focus target of each state) |
| Reset sent as PUT instead of POST | 1 API client test, 10 browser tests, 8 model tests |
| Deployed bundle shows `admin` in the confirmation | e2e "asks for confirmation, naming the user" |
| Deployed bundle resets on Cancel | e2e "Cancel goes back and keeps the layout", e2e keyboard test |
| Deployed bundle never moves focus | e2e keyboard test |
| Deployed controller answers guests as users | API spec, both guest tests |
| Deployed controller resets the staff user's layout | e2e "the default tabs and portlets come back", e2e "leaves other users' layouts alone", API spec reset test |

The e2e faults were edits of the deployed bundle or of the controller class in the deployed
`uPortal-api-rest` jar, with Tomcat restarted for each.

## Design

* `GET /api/v6-0/layout/reset` answers `{"userName": "student"}`, which the confirmation names.
  `POST /api/v6-0/layout/reset` (CSRF) calls `IUserLayoutHelper.resetUserLayout` with the session
  user's attributes and answers the same body. Guests get 401, answers are `no-store`.
* `<up-reset-my-layout>` loads `GET` first. The confirmation shows the user name it returns. States:
  `loading`, `failed` (retry), `signedOut` (401 or 403), and `ready` with `start`, `confirming`,
  `resetting` and `done`. A failed reset goes back to `confirming` with a notice. It emits
  `up-layout-reset`. Focus moves to the new step's heading or button after a step, never on the
  first load.
* The confirmation drops the portlet's breadcrumb heading (a link with the user's name, which
  cancelled) and keeps one "Reset User Layout" heading. Reset Layout is a danger button.
* New keys `reset.my.layout.load.fail`, `.reset.fail`, `.session.expired` and `.try.again`, in
  English, French and Spanish.
* A 403 from the API is Spring Security refusing the CSRF token. The controller never refuses a
  signed-in user, and a session that ended gets a new token. The component treats 403 like 401.
* Deleted: the `ResetMyLayout` entry of `WEB-INF/portlet.xml`,
  `properties/contexts/portlet/ResetMyLayout-portlet.xml` and `WEB-INF/flows/reset-my-layout/`.
  Nothing else used them. Checked on the running portal: with the definition set back to the
  `ResetMyLayout` descriptor, `/p/reset-my-layout` shows the Welcome tab and the log warns "No
  portlet descriptor could be found for the portlet definition".
* Kept, because other code uses them: the `reset-user-layout` subflow and its two JSPs (User
  Administration's `user-manager` flow), the `userLayoutHelper` bean in `flowsContext.xml`, and the
  message keys, which the component reads.
* Now unused: `IPersonLookupHelper.getSelf(ExternalContext)` in uPortal-api-internal, which only
  the deleted flow called. It takes a Web Flow type. It is a public interface method, so it stays
  for the Spring 5.3 step (P4-1) to remove.

## Findings

* When the session ends while a component is open, its next unsafe request gets 403. The CSRF
  filter refuses the old token before the controller can answer 401. Other components that treat 403 as
  "not allowed" show the wrong message in that case.
* `/api/layout?action=addTab` answers 500 for a tab name longer than 35 characters.
* A reset doesn't change the session's layout. If the user changes their layout before signing
  in again (adds a tab, moves a portlet), the old layout is saved again and the reset is lost.
  This is the portlet's behaviour too, and the note "You must sign on again" is the only warning.

## Proposed LOG rows

* MOD-D18 status line, Reset My Layout converted. `/api/v6-0/layout/reset` and
  `<up-reset-my-layout>` in uPortal, the Web Component definition in uPortal-start. The
  characterisation spec passes on the portlet and on the component. The `reset-my-layout` flow,
  its portlet context and the `ResetMyLayout` portlet are deleted. `reset-user-layout` stays.
* Issue: a session that ends while a component is open makes its next unsafe request fail with
  403 (CSRF), not 401. Components should say "sign in again" for a 403 from an API that never
  refuses signed-in users.
* Issue: a layout reset is lost if the user changes the layout before signing in again (the
  session saves its old layout). Clearing the session's layout after the reset would fix it.
* Issue: `IPersonLookupHelper.getSelf(ExternalContext)` is unused. Remove it with P4-1.

## Next step

Run CI.yml by hand on the uPortal topic branch and integration.yml on the uPortal-start topic
branch, then report the run IDs and head SHAs to the coordinator.
