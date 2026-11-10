fix(layout): keep a logged-out user's instance out of a new session

##### Checklist

-   [x] the [individual contributor license agreement](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#individual-contributor-license-agreement) is signed
-   [x] commit message follows [commit guidelines](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#commit)
-   [x] tests are included

##### Description of change

**Problem.** A request that overlaps a logout can leave the logged-out user's layout in a new session. `UserInstanceManagerImpl.getUserInstance` reads the person from the session and then calls `request.getSession()`. If the logout ends the session between those two calls, `getSession()` starts a new session, and the method stores the logged-out person's `UserInstance` in it. Pages in that session then show the user's name, layout and portlets, while the REST APIs treat the session as a guest. Only the browser that logged out sees this, but on a shared computer the next person could.

**Fix.** `getUserInstance` remembers which session the person came from, and reads the person again if `getSession()` returns a different one. Sessions are compared by identity, because the container can reuse the session id.

**Test.** `UserInstanceManagerImplTest` ends the session right after the person is read and checks that the new session gets the guest's instance. It fails on `master` and passes with the fix. The test mocks `IPortalRequestUtils`, so `uPortal-layout-impl` gets the portlet API as a test dependency. Upstream's CI build command passes on this branch on my fork (JDK 11, Ubuntu).

**Verified.** In a portal built from `master`, I sent page requests within a few milliseconds of `/Logout` in the same session. 11 of 24,000 such requests left a new session that showed the logged-out user's page while the REST API called it a guest. With this change, none of 30,000 did: every new session showed the guest page. The workflows and scripts for these runs are on my fork's branch [`test/verify-fixes-on-databases`](https://github.com/ChristianMurphy/uPortal/tree/test/verify-fixes-on-databases).

**Not verified.** The overlapping requests came from an HTTP client, not a browser.

**Where this comes from.** I'm working on a fork that upgrades uPortal's dependencies (Spring 5.3 so far, heading for Spring 7, Hibernate 7 and Jakarta EE 11). That fork's CI runs uPortal-start's Playwright suite against HSQLDB, PostgreSQL, MySQL, MariaDB, SQL Server and Oracle, and a two-node cluster test. A Playwright test there loaded a page 8 ms after `/Logout` and got the logged-out user's page back. The bug isn't caused by the upgrade: the code is the same in `v5.17.9` and `master`, so I ported the fix here on its own.
