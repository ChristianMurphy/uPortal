# Handoff: Self Edit Account and the password flows (WS14)

**Update (MOD-D46, MOD-D45):** the owner chose the web component for Self Edit Account after
all. The REST API and `<up-self-edit-account>` are back (the two `revert: bring back` commits),
with the password manager change below. The confirmation, current password, neutral page and
token expiry fixes are in, in the API and component as well as the old flow, which stays until MOD-I133. The text from here to "Next
steps" records how the work got here.

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
  * Fixed here (see below), approved by the owner: the form changed the password without the
    current one. `enterPassword.jsp` has a current password field, but no flow state uses it.
  * Fixed here (see below): a confirmation that differed was saved anyway. The script read the
    input's `textLength`, which inputs don't have; `PersonFormValidator` never ran because Web Flow
    looks for `accountFormValidator` and the bean is `personValidator`; and the error block read
    the errors of a model called `person`.
  * The "Add Preference" link adds inputs that saving ignores. The Thymeleaf page should drop it.

## Forgot Password and the reset link today

Forgot Password is the `forgot-password` channel (framework portlet `ForgotPassword`) and a
subflow of the login form ("Forgot your username or password?"). It stores a 20-character token in
the local account's `loginToken` attribute and emails a link to the login portlet with the user
name and token. The link starts the `reset-password` flow, a subflow of `local-login`. It asks
for a new password and its confirmation and answers "Success". Saving removes the token. After
that the link shows "Invalid password reset URL" with a link to send a new email.

Defects found:

* An unknown username fails with the channel error ("An error occurred loading this content."):
  the flow calls `setAttribute` on the null account it found. A known username gets "Reset email
  sent". Anyone can use the form to learn which local accounts exist.
* The page says the email was sent whether or not it was. `EmailPasswordResetNotificationImpl`
  logs a failed send and carries on. The test portal has no mail server.
* Fixed here, approved by the owner: tokens never expired.
* Fixed here: a failed token check logged the account's real token and the submitted one at INFO
  (`UserAccountHelper.validateLoginToken`), and the reset email logged the link at DEBUG.
* The reset page's password inputs have no labels (their captions are table cells), and a
  differing confirmation or a blank password gets only "Password was not updated.".
* The link works only before the login portlet has started its flow in the session: after the
  login form has been shown, the link shows the login form again.
* No portlet maps a `flowId` to the `update-password` flow, so it can't be reached. Its form has
  a current password field, and its `AccountPasswordFormValidator` is unused too.

## Done

uPortal, oldest first:

| Commit subject | What |
| --- | --- |
| `docs: start the self edit account conversion note` | This note |
| `revert: take the self edit account api and component back out` | The REST API and web component built before the scope changed; they stay in the history |
| `docs: rescope the self edit account note to the password flows` | This note, rescoped |
| `fix(account): refuse a password confirmation that differs` | Alias `accountFormValidator` for `personValidator` in `flowsContext.xml`, and the self-edit form's script and error block. Also turns on `PersonFormValidator` in User Administration's edit-account flow |
| `docs: record the password flows' defects and proposed log rows` | This note |
| merge of `ccr-43ba0785-hzlrj5` | The login and CSRF fixes |
| `fix(account): stop logging password reset tokens` | `validateLoginToken` logs the user name only, the reset email the account name; log-capturing unit tests (logback-classic, test scope) |
| `docs: record the token logging fix and the held defects` | This note |
| `docs: record a possible portlet-manager flake` | This note |
| merge of `ccr-43ba0785-hzlrj5` | Hibernate 5.2 |
| `docs: add the password confirmation fix to UPGRADING-6.0` | The confirmation fix's UPGRADING section |
| `fix(account): ask for the current password in self edit account` | `PersonForm.currentPassword`, `PersonFormValidator.validateEditOwnAccount`, the self-edit view-state renamed `editOwnAccount`, the JSP input, unit tests, UPGRADING section |
| `fix(account): expire password reset tokens` | `loginTokenIssued`, `resetTokenLifetime` (default `PT1H`), the expired message, unit tests, UPGRADING section |
| `docs: record the current password and token expiry fixes` | This note |
| merge of `ccr-43ba0785-hzlrj5` | MOD-D45 to D53, `.claude/settings.json` |
| `fix(account): give every forgot password request the same answer` | `requestPasswordReset`, the neutral page, ERROR on a failed send, unit tests, UPGRADING section |
| `revert: bring back the self edit account api and component` | Reverts the revert, e9e5952 |
| `fix(webcomponents): let password managers fill self edit account` | Light-DOM credentials form with `autocomplete` hints, browser tests |

uPortal-start, oldest first:

| Commit subject | What |
| --- | --- |
| `test(e2e): record what the self edit account portlet does today` | `self-edit-account.spec.ts` |
| `revert: take the self edit account component and data switch back out` | The component spec, the data switch and the spec changes for the withdrawn proposals |
| `test(e2e): expect a differing password confirmation to be refused` | Pairs with the uPortal fix: two tests (form script, and portal with scripts off) replace the "saved anyway" test |
| `test(e2e): share the local account helpers of the self edit spec` | `tests/ux/utils/local-account-utils.ts` |
| `test(e2e): record what forgot password and the reset link do today` | `forgot-password.spec.ts` (5 tests), `reset-password.spec.ts` (6 tests). The commit body enumerates a local test result, which the Vale commit check refuses; when landing, replace its last paragraph with "Each test imports its own account. The specs pass in any order and in parallel." |
| `test(e2e): refuse creating a user whose name is taken` | MOD-I195: a `user-administration.spec.ts` test, with the list of `PersonFormValidator`'s checks in the body |
| merge of `ccr-43ba0785-hzlrj5` | The login and CSRF fixes, with `tests/api/utils/csrf-request.ts` |
| `test(e2e): send the csrf token with the account imports` | `importXml` sends `sessionCsrfHeaders()` |
| `test(e2e): note that the reset flow no longer logs tokens` | The reset spec's header |
| merges of `ccr-43ba0785-hzlrj5` | Integration branch updates |
| `test(e2e): expect self edit account to ask for the current password` | Pairs with the current password fix |
| `test(e2e): expect password reset links to expire` | Pairs with the token expiry fix; `loginTokenIssued` helper |
| merge of `ccr-43ba0785-hzlrj5` | `.claude/settings.json` |
| `test(e2e): expect the same forgot password page for every name` | Pairs with the neutral page fix |
| `revert: bring back the self edit account component and data switch` | Reverts the revert, 26d722a73; the self-edit spec is left as it was |
| `test(e2e): check self edit account's component and password manager` | The specs against the component, CSRF in its helpers, API password checks, the password manager test |

### The confirmation fix

The new self-edit tests for the form and the portal failed against the portlet before the fix
and pass after it. Each was
then run with one fault seeded into the deployed portal: the script reading `textLength` again
failed only "the form refuses a confirmation that differs"; the alias removed failed only "the
portal refuses a confirmation that differs". `user-administration.spec.ts` still passes with the
alias. Revert the uPortal fix and its uPortal-start spec commit together to undo it.

### Local results

Against uPortal built from this branch, on HSQLDB: the three specs together (11 self edit, 5
Forgot Password, 6 reset), 44 of 44 with 4 workers, fully parallel, every test twice. Seeded faults
in the deployed flows (Forgot Password not saving the account; the reset flow accepting any token)
failed "stores a reset token…", "a second request replaces the token", "sets the password…" and "a
wrong token is invalid…".

### MOD-I195 and the token logging fix

MOD-I195 ("Create a User" with an existing name saved over that account) is fixed by the same
alias, uPortal `fix(account): refuse a password confirmation that differs`, and tested by
uPortal-start `test(e2e): refuse creating a user whose name is taken`. That test failed without
the alias, when the portal logged that it had updated the existing account, and failed with the
edit-account error block seeded to read another model.

`fix(account): stop logging password reset tokens`: each new log-capturing unit test failed with
the old log line put back.

### The current password fix

The owner approved requiring the current password on the existing Web Flow form. Web Flow calls
`validate<StateId>` on the `accountFormValidator` bean, so the self-edit view-state is now
`editOwnAccount` (view still `editLocalAccount`), and only Self Edit Account calls
`validateEditOwnAccount`. User Administration's edit-account flow keeps `editLocalAccount` and
doesn't ask for the current password. Self Edit Account asks for it only with a new password.

Local runs of the four account specs passed 25 of 25. A deployed view-state renamed back to
`editLocalAccount` failed only "refuses a new password without the right current one". The unit
test `ownAccountRefusesAWrongCurrentPassword` alone failed once the hash check was removed from
the validator.

### The token expiry fix

`UserAccountHelper.issueLoginToken` stores the token and `loginTokenIssued`; a token older than
`org.apereo.portal.portlets.account.resetTokenLifetime` (default `PT1H`), or one without an issue
time (sent by 5.x), is refused, and the reset link shows "This password reset URL has expired".
The token stays on the account until a new email replaces it. A token that expires while the
form is open gets the generic "Password was not updated." on submit.

Local runs: the four specs, 27 of 27. With the deployed page showing the old text and the flow
storing an undated token, the three new or changed tests failed and no others. With the age check
always passing, four of the new unit tests failed. The existing `UserAccountHelperTest.setup`
called `initMocks` last, which replaced the mocks given to the helper. It now runs first.

### The neutral Forgot Password page

Option (a) of MOD-D45: every user name gets "Check your email" and the same text. Only a local
account with an email address gets a token and an email. A failed send is logged at ERROR with
the account name; the unknown name is not logged, since people sometimes type a password there.
Every bundle loses `reset.email.sent` and the "instructions have been sent" key. Unit tests cover a send that works
(the test portal has no mail server) and each branch of `requestPasswordReset`. With the email
check or the catch removed, one unit test failed each. With the deployed flow put back to the
5.x transition, only the identical page test failed.

### Password managers and the component

Password managers search the document, not shadow roots. While the form shows, the component
puts a `<form>` in its light DOM, slotted into the Password section, with a read-only Username
input (`autocomplete="username"`) and the three password inputs (`current-password`,
`new-password`). The attribute inputs stay in the shadow root. A browser test and a Playwright
test look for the inputs from the document, as a password manager does. With the hints removed
from the deployed bundle, only the Playwright test failed; in the browser tests, it and two
others failed. Removing the API's confirmation check failed only `refusesAConfirmationThatDiffers`,
and removing its current password check failed the three current password tests.

A change to a deployed web component asset needs a Tomcat restart before the portal serves it.

Local runs: the self-edit, component, Forgot Password, reset and User Administration specs, 36 of
36; the web components suite, 770 of 770.

### A possible parallel-run flake

On 2026-10-11 at about 09:02 UTC, a local run of every admin spec and the three account specs
with 4 workers (uPortal `c763c7d` on HSQLDB) failed `portlet-manager.spec.ts`, "register, render
and delete a CMS portlet", at a `toContainText` check. It passed when run alone right after. Its
edit-portlet flow uses the `portlet` model, which the `accountFormValidator` alias does not reach,
so this looks like a flake under parallel load.

## Next steps

The password fixes, the neutral page and the password manager check are done. MOD-D53 leaves
these to the follow-up branches, Spanish and French text for the new keys (-i18n), the Unicode
input tests (-utf8-inputs) and `isComposing` handling (-i18n). The portlet, its flow and their
fixes are removed with MOD-I133.

## Proposed LOG rows

For the coordinator to number. Held for the owner, each with how a fix could look:

* Issue (Portlets, security): the Forgot Password flow answers an unknown user name with the channel error
  (the flow calls `setAttribute` on a null account) and a known one with "Reset email sent", so it
  tells anyone which local accounts exist. Fix: show the same "if an account exists, we have
  emailed it" page for every user name. Send nothing for an unknown one.
* Issue (Portlets): the Forgot Password flow says the email was sent when sending failed
  (`EmailPasswordResetNotificationImpl` logs the failure and returns). Fix: return the outcome and
  show "The email could not be sent. Contact the help desk." on failure. With the enumeration fix,
  show it only for a failed send to an existing account, so it does not reveal accounts.
* Issue (Portlets, accessibility): the reset form's password inputs have no labels (their captions
  are table cells), and a differing confirmation or a blank password gets only "Password was not
  updated.". Fix: `<label for>` on both inputs, and the `passwords.must.match` and a new
  `please.enter.new.password` message in place of the generic one.
* Issue (Portlets): the reset link is ignored once the login portlet has started its flow in the
  session, because the portlet resumes that flow. Fix: in the Thymeleaf version, make the reset
  page its own URL, such as `/uPortal/password/reset?user=...&token=...`, not a parameter of the
  login portlet. Until then, end the login flow when a request has a `loginToken` parameter.
* Issue (Portlets): the update-password flow is unreachable (no portlet maps a `flowId` to it),
  and so is its `AccountPasswordFormValidator`. Fix: delete both with the Web Flow removal.

Decided or fixed:

* MOD-D19 is confirmed for these flows. Self-Edit Account, Forgot Password and the reset-password
  flow stay Thymeleaf full-page flows, to be converted with Spring 5.3 (P4-1). Their
  characterisation specs are `self-edit-account.spec.ts`, `forgot-password.spec.ts` and
  `reset-password.spec.ts` in uPortal-start.
* Issue (Portlets): `PersonFormValidator` never ran, because Web Flow looks for
  `accountFormValidator`, and the self-edit form's script check was dead too, so differing
  confirmations were saved. **Fixed** by `fix(account): refuse a password confirmation that
  differs`, approved by the owner.
* MOD-I195: **fixed** by the same commit, tested by uPortal-start `test(e2e): refuse creating a
  user whose name is taken`.
* Issue (Security): a failed reset-token check logged the account's real token at INFO, and the
  reset email logged its link. **Fixed** by `fix(account): stop logging password reset tokens`.
* Issue (Portlets, security): the Self-Edit Account flow changed the password without the
  current password. **Fixed** by `fix(account): ask for the current password in self edit
  account`, approved by the owner.
* Issue (Portlets, security): reset tokens never expired. **Fixed** by `fix(account): expire
  password reset tokens` (`resetTokenLifetime`, default `PT1H`), approved by the owner.
* Issue (Portlets, Thymeleaf conversion): drop Self-Edit Account's "Add Preference" link, whose
  inputs saving ignores.
* Issue (uPortal-start): the account specs create local accounts through `/api/import` and can't
  delete them (no REST delete for local accounts). Harmless on CI's fresh databases.

## Sandbox notes

Local builds work with a Gradle init script (outside the repos) that points Maven Central at
Google's mirror, with the mirror ahead of the Gradle plugin portal in `pluginManagement`.
`portalInit` on JDK 21 needs the `JDK_JAVA_OPTIONS` `--add-opens` from the coordinator's tips.
Playwright 1.64 runs with a symlink tree that links the sandbox's Chromium 1194 under the 1248 names.
In this sandbox `./gradlew dataImport -Dfile=...` fails while configuring the build
(`:overlays:uPortal:prepareSkinResources` not found); importing through `/api/import` as admin
works.
