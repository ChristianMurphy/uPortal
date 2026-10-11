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
| 2. REST API under `/api/v6-0/` | Next |
| 3. `<up-reset-my-layout>` | Not started |
| 4. Channel definition | Not started |
| 5. Spec against the component, seeded faults | Not started |
| 6. UPGRADING-6.0 | Not started |

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

## Next step

Commit the REST API (`ResetMyLayoutRESTController`, MockMvc tests) and an API spec in
uPortal-start.
