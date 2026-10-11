# Handoff: Self Edit Account as a web component (WS14)

Workstream 14 converts the Password Management channel (`passwordmgr`, framework portlet
`SelfEditAccount`, the `self-edit-account` Web Flow) into `/api/v6-0/account` and
`<up-self-edit-account>`. The work is on `ccr-43ba0785-hzlrj5-wc-self-edit-account` in uPortal and
uPortal-start, cut from `ccr-43ba0785-hzlrj5` (uPortal `d9889d4`, uPortal-start `5ef0dfc`). The steps
follow "Converting the next portlet" in `docs/developer/web-components/README.md`.

## The portlet today

Read from the flow, its JSPs and `UserAccountHelper`, and confirmed against a running portal:

* The details view lists `directoryDisplayAttributes` (userContext.xml: title, department,
  organization, mail, telephoneNumber, postalAddress) from the user's session, so saved changes show
  from the next login.
* Edit appears only when `UserAccountHelper.canEditUser(person, person.name)`: a local account and
  `UP_USERS EDIT_USER` on `SELF`. The quickstart data grants that to no group, so only admin (a
  superuser) can edit. The form lists the `accountEditAttributes` the user has
  `EDIT_USER_ATTRIBUTE` on (all single-text inputs in the default configuration), a password and
  its confirmation.
* Defects: the form has no current password field (`enterPassword.jsp` has the field, no
  flow state uses it); a confirmation that differs is saved anyway (the script reads the input's
  `textLength`, which inputs don't have, and `PersonFormValidator` never runs because the bean is
  `personValidator`, not `accountFormValidator`); the "Add Preference" link adds inputs that saving
  ignores.

## Done

uPortal-start:

| Commit subject | What |
| --- | --- |
| `test(e2e): record what the self edit account portlet does today` | `tests/ux/smoke/self-edit-account.spec.ts`, 10 tests. Each editing test imports its own local account and permission sets through `/api/import` as admin, so it never changes a shared password |

Local result: 10 passed against the portlet on HSQLDB with uPortal `d9889d4` built from source, and
20 of 20 with 4 workers and every test repeated twice.

## Running

Nothing.

## Next step

Step 2, the REST API: `org.apereo.portal.rest.account` (`SelfEditAccountService` on
`UserAccountHelper`, `SelfEditAccountRESTController`), with MockMvc tests behind the real CSRF
filter. Then two separate commits, each a proposed behaviour change the owner may revert: require
the current password, and refuse a confirmation that differs.

## Sandbox notes

Local builds work with a Gradle init script (outside the repos) that points Maven Central at
Google's mirror, with the mirror ahead of the Gradle plugin portal in `pluginManagement`.
`portalInit` on JDK 21 needs the `JDK_JAVA_OPTIONS` `--add-opens` from the coordinator's tips.
Playwright 1.64 runs with a symlink tree that links the sandbox's Chromium 1194 under the 1248 names.
