# Handoff: Self Edit Account and the password flows (WS14)

Self-Edit Account and the update-password and reset-password flows are Thymeleaf full-page flows in
MOD-D19, and Thymeleaf waits for Spring 5.3 (P4-1). So this workstream records what those
flows and Forgot Password do today, so that the Thymeleaf conversion can be checked against it, and
fixes the dead password confirmation check in the self-edit-account flow. The work is on
`ccr-43ba0785-hzlrj5-wc-self-edit-account` in uPortal and uPortal-start, cut from
`ccr-43ba0785-hzlrj5`. A separate workstream owns Login, so the login flow and `LoginController` stay
untouched.

## Self Edit Account today

The Password Management channel (`passwordmgr`, framework portlet `SelfEditAccount`, the
`self-edit-account` Web Flow). Read from the flow, its JSPs and `UserAccountHelper`, and confirmed
against a running portal:

* The details view lists `directoryDisplayAttributes` (userContext.xml: title, department,
  organization, mail, telephoneNumber, postalAddress) from the user's session, so saved changes show
  from the next login.
* Edit appears only when `UserAccountHelper.canEditUser(person, person.name)`: a local account and
  `UP_USERS EDIT_USER` on `SELF`. The quickstart data grants that to no group, so only admin (a
  superuser) can edit. The form lists the `accountEditAttributes` the user has
  `EDIT_USER_ATTRIBUTE` on, a password and its confirmation.
* Defects:
  * The form has no current password field. `enterPassword.jsp` has one, but no flow state uses
    it. Whether a new password should need the current one is the owner's call.
  * A confirmation that differs is saved anyway. The script reads the input's `textLength`, which
    inputs don't have, and `PersonFormValidator` never runs because Web Flow looks for
    `accountFormValidator` and the bean is `personValidator`.
  * The "Add Preference" link adds inputs that saving ignores. The Thymeleaf page should drop it.

## Done

uPortal:

| Commit subject | What |
| --- | --- |
| `docs: start the self edit account conversion note` | This note |
| `revert: take the self edit account api and component back out` | The REST API and web component built before the scope changed; they stay in the history |

uPortal-start:

| Commit subject | What |
| --- | --- |
| `test(e2e): record what the self edit account portlet does today` | `tests/ux/smoke/self-edit-account.spec.ts`, 10 tests. Each editing test imports its own local account and permission sets through `/api/import` as admin |
| `revert: take the self edit account component and data switch back out` | The component spec, the data switch and the spec changes for the withdrawn proposals |

Local result for the characterisation spec: 10 passed against the portlet on HSQLDB, and 20 of 20
with 4 workers and every test repeated twice.

## Next steps

1. Fix the confirmation check in the self-edit-account flow, in its own commit, with spec tests
   that fail before and pass after, and a seeded fault.
2. Characterisation specs for Forgot Password and the update-password and reset-password flows.
3. When the login and CSRF fix is in the integration branch, merge it here and use its
   `tests/api/utils/csrf-request.ts` helper for the imports.

## Sandbox notes

Local builds work with a Gradle init script (outside the repos) that points Maven Central at
Google's mirror, with the mirror ahead of the Gradle plugin portal in `pluginManagement`.
`portalInit` on JDK 21 needs the `JDK_JAVA_OPTIONS` `--add-opens` from the coordinator's tips.
Playwright 1.64 runs with a symlink tree that links the sandbox's Chromium 1194 under the 1248 names.
In this sandbox `./gradlew dataImport -Dfile=...` fails while configuring the build
(`:overlays:uPortal:prepareSkinResources` not found); importing through `/api/import` as admin
works.
