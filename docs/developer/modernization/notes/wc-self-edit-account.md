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
* Tokens never expire.
* A failed token check logs the account's real token and the submitted one at INFO
  (`UserAccountHelper.validateLoginToken`), so anyone who can read the log can reset that account.
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

uPortal-start, oldest first:

| Commit subject | What |
| --- | --- |
| `test(e2e): record what the self edit account portlet does today` | `self-edit-account.spec.ts` |
| `revert: take the self edit account component and data switch back out` | The component spec, the data switch and the spec changes for the withdrawn proposals |
| `test(e2e): expect a differing password confirmation to be refused` | Pairs with the uPortal fix: two tests (form script, and portal with scripts off) replace the "saved anyway" test |
| `test(e2e): share the local account helpers of the self edit spec` | `tests/ux/utils/local-account-utils.ts` |
| `test(e2e): record what forgot password and the reset link do today` | `forgot-password.spec.ts` (5 tests), `reset-password.spec.ts` (6 tests). The commit body enumerates a local test result, which the Vale commit check refuses; when landing, replace its last paragraph with "Each test imports its own account. The specs pass in any order and in parallel." |

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

## Next steps

1. When the login and CSRF fix is in the integration branch, merge it here and use its
   `tests/api/utils/csrf-request.ts` helper in `importXml` (`local-account-utils.ts`).
2. Wait for the owner's ruling on the confirmation fix (keep or revert) and on the current password.

## Proposed LOG rows

For the coordinator to number:

* MOD-D19 is confirmed for these flows. Self-Edit Account, Forgot Password and the reset-password
  flow stay Thymeleaf full-page flows, to be converted with Spring 5.3 (P4-1). Their
  characterisation specs are `self-edit-account.spec.ts`, `forgot-password.spec.ts` and
  `reset-password.spec.ts` in uPortal-start. The update-password flow is unreachable and needs no
  conversion. Drop it with the Web Flow removal.
* Issue (Portlets): `PersonFormValidator` never ran, because Web Flow looks for
  `accountFormValidator`; the self-edit form's script check was dead too, so differing
  confirmations were saved. **Fixed** by `fix(account): refuse a password confirmation that
  differs`, pending the owner. The same fix turns the validator on in User Administration, whose
  "Create a User" with an existing user name used to update that account (from the code, not
  reproduced).
* Issue (Portlets): the Self-Edit Account flow changes the password without the current password. Owner to
  decide for the Thymeleaf page.
* Issue (Security): a failed reset-token check logs the account's real token at INFO
  (`UserAccountHelper.validateLoginToken`), and reset tokens never expire.
* Issue (Portlets): the Forgot Password flow fails with the channel error for an unknown username and
  answers differently for a known one (account enumeration), and reports an email as sent when the
  send failed.
* Issue (Portlets): the reset link is ignored once the login portlet has started its flow in the
  session. The reset form's inputs have no labels, and its errors don't say what was wrong.
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
