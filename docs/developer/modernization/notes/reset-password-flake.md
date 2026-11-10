<!-- markdownlint-disable -->

# A request with the session id that a sign-in replaced gets a session of its own (MOD-I439)

Branch `ccr-43ba0785-hzlrj5-reset-password-flake` in uPortal, from the integration branch at
`7b24794905`. It has this note and no code. uPortal-start has no branch of that name: nothing
changed there, and a push would only start a full set of runs.

The cause is found and reproduced. It is a defect of the portal, it is in upstream master too,
and it isn't fixed here. "No code change" gives the reasons.

## What failed

uPortal-start Integration run 42753109471, job 129622410669 (Playwright on HSQLDB).
`tests/ux/smoke/reset-password.spec.ts:134` "sets the password, after which the link is
invalid" passed only on its retry. After the reset the test signs in with the new password,
logs out, and submits the login form with the old password. The portal refused the sign-in and
showed the login form again, without "Invalid username or password".

From the trace of the failed attempt and `portal.log` in the artifact
`integration-playwright-hsqldb`:

| Time (UTC) | What happened |
| --- | --- |
| 04:19:37.156 | The test clicks Sign In. `GET /p/login` and the form page arrive in the guest session `5F34BE26`. |
| 04:19:37.281 | A script of the form page sends `GET /api/v4-3/dlm/layout.json` with the cookie `5F34BE26`. Earlier requests for it took 6 to 50 ms. This one gets its answer after 427 ms. |
| 04:19:37.539 | The test has filled the form in and submits it: `POST /Login` with the cookie `5F34BE26`. |
| 04:19:37.707 | `portal.log` has the refused attempt for the test's user. |
| 04:19:37.708 | Both answers arrive in the same millisecond. `POST /Login` answers 302 to `/p/login`. `layout.json` answers with `Set-Cookie: JSESSIONID=683E5180…`. |
| 04:19:37.712 | The browser follows the redirect to `/p/login` with the cookie `683E5180`, the one from `layout.json`. The form it gets has no message. |

## Cause

Every request to `/Login` replaces the session.
`PortalPreAuthenticatedProcessingFilter.doPortalAuthentication` ends the session the request
came with and starts a new one, whether the sign-in then succeeds or not. `LoginController`
records a refused sign-in in that new session (`up_attemptedUserName`) and redirects to the
login form, whose flow reads it from the session (`local-login.xml`).

The old session id is dead from then on, and the portal gives any request that still has it
in its cookie a new guest session with a `Set-Cookie` of its own. The browser now gets two session
cookies, one from `/Login` and one from the other request, and keeps the one that arrives
last. When that is the other request's cookie, the redirect after the sign-in runs in a guest
session without any record of the sign-in.

In the failed run the other request was the form page's own `layout.json`, still unanswered
when the test submitted the form 380 ms after it had opened the page.

The trace doesn't show the cookie that `/Login` set. It misses the session cookie of other 302
answers in the same file too. It also can't show whether `layout.json` reached the portal's
code before or after `/Login` ended the session. The reproduction below covers the second case.

## Reproduction

On a local portal built from the tip (uPortal `7b24794905` as CI built it, artifact
`uportal-m2-7b2479490501076f77bebc3de746c59bac81fe7a`, with uPortal-start `39ca485901`: HSQLDB,
Tomcat on JDK 11, the uPortal overlay and NotificationPortlet). The script plays one browser
with curl. It loads the guest page, posts a wrong password to `/Login` without following the
redirect, and then sends `layout.json` with the session id from before the sign-in.

```sh
B=http://localhost:8080/uPortal
sid() { awk '$6=="JSESSIONID"{print $7}' "$1" | tail -1; }
mkjar() { printf '#HttpOnly_localhost\tFALSE\t/\tFALSE\t0\tJSESSIONID\t%s\n' "$1" > "$2"; }
curl -s -c jar -b jar -L -o /dev/null $B/
T=$(sid jar)
curl -s -c jar -b jar -o /dev/null -D h2.txt -d 'userName=nobody&password=wrong' $B/Login
N=$(sid jar)
curl -s -o /dev/null -D h3.txt -H "Cookie: JSESSIONID=$T" $B/api/v4-3/dlm/layout.json
X=$(grep -i '^set-cookie: JSESSIONID' h3.txt | sed 's/.*JSESSIONID=\([^;]*\).*/\1/' | tr -d '\r')
mkjar "$N" jarN; mkjar "$X" jarX
curl -s -c jarN -b jarN -L $B/p/login | grep -c 'Invalid username or password'
curl -s -c jarX -b jarX -L $B/p/login | grep -c 'Invalid username or password'
```

Steps 1 to 3 gave the same result on four runs, and steps 4 and 5 on the two runs that had
them. One run, shortened:

```
1 guest page: session T=778A7DDB
2 POST /Login (wrong password): HTTP/1.1 302 Location=/uPortal/p/login; Set-Cookie: JSESSIONID=2E91175E…; browser now has N=2E91175E
3 GET layout.json with the old id T: HTTP/1.1 200; Set-Cookie: JSESSIONID=D4A3F7D5…
4 login form with Login's cookie N=2E91175E: 'Invalid username or password' x1
5 login form with the stale request's cookie X=D4A3F7D5: 'Invalid username or password' x0
```

Both forms were reached through the redirect to `/uPortal/p/login.ctf1/max/render.uP`, as in
the failed run. The order of steps 4 and 5 doesn't matter.

The same happens to a sign-in that succeeds. With `admin` and the right password, the home
page with the cookie from `/Login` says "You are signed in as", and the home page with the
cookie from the `layout.json` request is the guest page with the Sign In link.

A second way to lose the session showed up in the same portal. A page request with the old id,
such as `GET /f/welcome/normal/render.uP`, doesn't get a session. `RequireValidSessionFilter`
answers 302 to `/Login?refUrl=…`. A browser follows that with the cookie it has by then, the
signed-in one, and `/Login` ends that session too:

```
signed in as admin: T=2ACCC0F0 N=9432C232
GET /f/welcome/normal/render.uP with the old id T: HTTP/1.1 302 Location=/uPortal/Login?refUrl=%2FuPortal%2Ff%2Fwelcome%2Fnormal%2Frender.uP
GET /uPortal/Login?refUrl=… with N: HTTP/1.1 302 Set-Cookie: JSESSIONID=4D2A41F0…
home page afterwards: signed in x0, Sign In link x1
```

The local portal had only curl as its client, and these are requests in sequence. They show what the portal
answers to a request with a replaced id. They don't show a browser losing the race.

## Who can hit it

* Any test that submits the login form while a request of the form page is unanswered. The
  form-based checks are `expectLoginRefused` in `reset-password.spec.ts` (two),
  `self-edit-account.spec.ts` (three) and `uportal-auth.spec.ts` (two), and every
  `loginViaPage`. The helper `waitForPortalRequestsToSettle` counts `*.uP` requests only, so it
  wouldn't have waited for `layout.json`.
* A person whose browser fills the form in and submits it at once, as a password manager does,
  on a portal that is slow with the page's own requests. A correct sign-in then ends on the
  guest page.

## No code change

The spec isn't at fault. It submits the form as soon as the form is there. A change that makes
it wait for the page's requests would hide the defect. Such a wait isn't reliable either,
because the page starts those requests after it has loaded: "nothing in flight" can be true a
moment before `layout.json` goes out.

The portal is wrong, and the fix isn't small. It has to keep a request with a just-replaced
session id from handing the browser a session cookie. The ways I see:

* **Refuse such a request.** `/Login` records the id it replaces for a short time. A filter in
  front of the others gives a request with a recorded id and without a valid session an error
  instead of a new session. Through a request wrapper it has to do the same for a request that
  was already running when its session ended. `RequireValidSessionFilter` must not send
  such a request to `/Login`. This changes what a late request gets (an error where it now
  gets guest data), and on a cluster without sticky sessions the list of replaced ids has to be
  shared.
* **Let it use the session that replaced its own.** This brings session fixation back: whoever
  planted the old id gets the signed-in session. Not an option.
* **Don't replace the session on a refused sign-in.** That covers the lost message and leaves
  the lost sign-in.

The first one is a design decision for the owner, with a status code that clients can see. It
needs the full Playwright suites on every database and a portal with a browser to test the race
itself. This machine had 4.7 GB of disk left for all agents, enough for the small portal above
and not for that.

## Upstream

The code is the same in upstream master (`657741e9b3`):
`PortalPreAuthenticatedProcessingFilter` ends and restarts the session at lines 286 to 314 and
406, and `RequireValidSessionFilter` lets `/api` requests through without a session since 5.4.
It is an upstream candidate once there is a fix.

## Runs

None. The branch has only this note, and its commit message has `[skip ci]`. Vale and remark passed
on the note locally.

## Proposed LOG rows

| ID | Area | Finding |
| --- | --- | --- |
| MOD-I439 (update) | Tests | `reset-password.spec.ts:134` passed only on a retry in Integration run 42753109471 (job 129622410669). Cause found: the form page's own `GET /api/v4-3/dlm/layout.json` was unanswered when the test submitted the form. `/Login` replaced the session, the `layout.json` answer had a `Set-Cookie` for a new guest session and arrived in the same millisecond as the 302, and the browser followed the redirect with that cookie. The form then had no record of the refused sign-in. The spec is right. The defect is the portal's (MOD-I?). Open until that is fixed: the spec can fail again, and so can the other form-based sign-in checks (`notes/reset-password-flake.md`). |
| MOD-I? | Security | A request with the session id that a sign-in has just replaced gets a new guest session and a `Set-Cookie` for it. The browser keeps whichever cookie arrives last. If that is the stale request's, a refused sign-in loses its message and a correct sign-in ends on the guest page. A page request with the old id is sent to `/Login?refUrl=…` instead, and when the browser follows that with its new cookie, `/Login` ends the signed-in session. Reproduced with curl on a portal built from `7b24794905`, the same on every run. `PortalPreAuthenticatedProcessingFilter` replaces the session on every `/Login`, and `RequireValidSessionFilter` lets `/api` requests start one. The same code is in upstream master. **Open**, for the owner: the fix refuses a request with a just-replaced id, which changes what such a request gets (`notes/reset-password-flake.md`, "No code change"). |
