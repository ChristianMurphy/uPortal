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

uPortal, oldest first:

| Commit subject | What |
| --- | --- |
| `docs: start the self edit account conversion note` | This note |
| `feat(api): add a self edit account rest api` | `org.apereo.portal.rest.account` (`@NullMarked`): `SelfEditAccountService` on `UserAccountHelper`, `SelfEditAccountRESTController`, MockMvc tests behind the real CSRF filter |
| `feat(webcomponents): add up-self-edit-account web component` | `<up-self-edit-account>`, its machine, API client, messages and tests; tables, labels and form controls in the shared sheet; four `account.*` keys in `Messages.properties` |
| `feat(account): refuse a password confirmation that differs` | Proposed behaviour change 1 (API, component, tests) |
| `feat(account): require the current password for a new one` | Proposed behaviour change 2 (API, component, tests); `please.enter.current.password` and `current.password.doesnt.match` in `Messages.properties` |
| `docs: describe the self edit account web component` | UPGRADING-6.0 section, guide section, this note |

uPortal-start, oldest first:

| Commit subject | What |
| --- | --- |
| `test(e2e): record what the self edit account portlet does today` | `tests/ux/smoke/self-edit-account.spec.ts`, the characterisation spec, 10 tests. Each editing test imports its own local account and permission sets through `/api/import` as admin |
| `feat(data): render password management as a web component channel` | `passwordmgr` as a Web Component channel; `UP_PORTLET_DEF_PARAM rows +4` in `intended-changes.txt` |
| `test(e2e): cover the self edit account web component` | `self-edit-account-component.spec.ts`, 8 tests; the import helpers move to `tests/ux/utils/self-edit-account-utils.ts` |
| `test(e2e): expect a differing password confirmation to be refused` | Pairs with proposed change 1 |
| `test(e2e): expect the current password for a password change` | Pairs with proposed change 2 |

### Proposed behaviour changes (owner to decide)

Each is one uPortal commit plus one uPortal-start commit. Revert both to restore the old behaviour.

1. **A differing confirmation is refused** with "Passwords must match" (the existing key and its
   translations). The API answers 400 with `code: passwords.must.match`; the component checks before
   sending.
2. **A new password needs the current one.** The API answers 400 with
   `please.enter.current.password` or `current.password.doesnt.match` (the codes and English text of
   the update-password flow's validator). The component adds a Current Password field.

The intermediate state, with change 1 but not change 2, was not run in a portal. The final state was.

### Security rules and their tests

| Rule | Unit or MockMvc test | Playwright test (component spec) |
| --- | --- | --- |
| Only the session's user; no user name from the request | `savesTheSessionUsersAccount` | returns the session user's account (`?username=admin` is ignored) |
| Today's password rules, plus the two proposed changes | `refusesAConfirmationThatDiffers`, `refusesANewPasswordWithoutTheCurrentOne`, `refusesANewPasswordWithAWrongCurrentOne` | characterisation spec |
| No attribute becomes editable | `refusesAnAttributeTheUserMayNotEdit` | refuses an attribute the user may not edit |
| No password logged, returned or in error details | `expectRefusal` checks every message, `returnsTheAccount` (strict JSON), `refusesAMalformedBodyWithoutQuotingIt` | returns the account without a password, never echoes a password |
| Guests get 401 | `answersGuestsWith401` (service and controller) | answers guests with 401 |
| CSRF: missing, forged and another session's token refused | `refusesAChangeWithoutTheCsrfToken`, `refusesTheTokenOfAnotherUsersSession` | needs the CSRF token of the user's own session |

After the Playwright runs, the portal's and Tomcat's logs held none of the passwords sent to the
API. The only matches were Tomcat access log lines for the specs' own sign-ins. The shared
`loginViaUrl` helper signs in with `GET /Login?userName=...&password=...`, which puts the password
in the URL.

### Seeded faults

Each check was run once with one fault in place, then the fault was reverted. Every run failed:

* Java: the service following `?username` (several service tests); no guest check
  (`answersGuestsWith401`); no editability check (`refusesAnAttributeTheUserMayNotEdit`); no
  match check (`refusesAConfirmationThatDiffers`); any current password accepted
  (`refusesANewPasswordWithAWrongCurrentOne`); a refusal that quotes the password
  (`refusesAConfirmationThatDiffers`); the CSRF matcher not protecting `/api/v6-0/`
  (`refusesAChangeWithoutTheCsrfToken`, `refusesTheTokenOfAnotherUsersSession`); the unreadable-body
  answer quoting the parser (`refusesAMalformedBodyWithoutQuotingIt`). A password getter on
  `Account` failed `returnsTheAccount`.
* Vitest: `canEdit` not checked (API client test); PUT to another path (API client test); differing
  passwords sent and Cancel keeping the passwords (machine tests); no `aria-busy` while saving
  (browser test); Edit shown to a user who may not edit (model-based test); a password field
  keeping its value after a failed save (browser tests).
* Playwright, one deployed build with two faults: the component never showing Edit failed 9
  characterisation tests and the focus test; the API following `?username` failed "returns the
  session user's account".

### Local results

* uPortal-api-rest: 26 account tests (10 controller, 16 service). Spotless and SpotBugs are clean.
* uPortal-webcomponents: `vp check` clean; `vp test` 439 tests in 13 files, of which 215 are in the
  4 new files (15 API client, 25 machine, 24 browser, 151 model-based). Every new file has 100%
  lines, functions and statements. Branch coverage is 95.5% for `up-self-edit-account.ts`, and 97.6% for
  the component's directory as a whole. `vp build` writes `up-self-edit-account` into `manifest.json`.
* Playwright on HSQLDB with both branches built from source: the characterisation spec, 10 of 10
  against the portlet (and 20 of 20 with 4 workers, each test twice), unchanged 10 of 10 against the
  component, and with the proposed changes 11 of 11, plus 8 of 8 in the component spec.

## Not done, and why

* **The portlet is not deleted.** Like Favorites and User Locale, `SelfEditAccount` stays so that
  portals whose data still names it keep working until they convert (MOD-I133). Deleting it means
  removing `WEB-INF/flows/self-edit-account/` (four JSPs and the flow),
  `properties/contexts/portlet/SelfEditAccount-portlet.xml` and the `SelfEditAccount` entry of
  `WEB-INF/portlet.xml`, which only the portlet uses. Shared pieces that stay: `UserAccountHelper` (the
  API, the edit-account, user-manager, forgot-password and reset-password flows, the tenant
  manager), `directoryDisplayAttributes` (`DirectoryPortletController`), `accountEditAttributes`,
  `ModernParameterEditor` and the `editPortlet:preferenceInput` tag (edit-account), and
  `PersonFormValidator` (registered as `personValidator`, used by no flow).
* The database harness was not run locally for the data change. CI's harness job covers it.

## Running

See CI below.

## Next step

Wait for the owner's decision on the two proposed changes, and for CI. Deleting the portlet now
would be one commit with the list above.

## CI

To be filled in when the manual runs finish.

## Proposed LOG rows

For the coordinator to number:

* MOD-D18 status line: "Self Edit Account (2026-10-11): `/api/v6-0/account` and
  `<up-self-edit-account>`; uPortal-start publishes `passwordmgr` as a Web Component channel. Two
  proposed behaviour changes await the owner (current password required, differing confirmation
  refused). Note: `docs/developer/modernization/notes/wc-self-edit-account.md`."
* MOD-D19 lists Self-Edit Account among the Thymeleaf full-page flows. The coordinator had it
  converted to a web component instead, because users open it as a channel.
* Issue (Portlets): the self-edit-account flow changed the password without the current password.
  Fixed in the component by proposed change 2.
* Issue (Portlets): `PersonFormValidator` never runs. Web Flow looks for `accountFormValidator`
  and the bean is `personValidator`, so the self-edit-account and edit-account flows skip the password match
  check and, in edit-account, the `username.in.use` check. From the code (not reproduced),
  "Create a User" with an existing user name then updates that account, password included, because
  `UserAccountHelper.updateAccount` looks up an account by name when the form's id is below 0.
  The component fixes the first part (proposed change 1); User Administration still has the
  defect until its conversion.
* Issue (uPortal-start): the self edit account specs create local accounts through `/api/import`
  and can't delete them (no REST delete for local accounts). Harmless on CI's fresh databases;
  local databases collect `selfedit-*` accounts. When MOD-I130 puts the legacy `/api` under CSRF,
  the import helper needs the token.
