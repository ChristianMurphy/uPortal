fix(layout): save one user's layout from two requests in turn

##### Checklist

-   [x] the [individual contributor license agreement](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#individual-contributor-license-agreement) is signed
-   [x] commit message follows [commit guidelines](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#commit)
-   [x] tests are included

##### Description of change

**Problem.** Two saves of one user's layout at the same moment can fail, and the request answers 500. Parallel sign-ins of one user do this, because each saves the merged layout. `RDBMUserLayoutStore.setUserLayout` reads the layout id, deletes the user's layout rows and inserts the new ones, and nothing makes two saves wait for each other. It fails in three ways:

* MariaDB 11.6+: the delete fails with error 1020, "Record has changed since last read".
* PostgreSQL: the second save's delete skips the rows the first save just inserted, and its insert hits a duplicate key on `up_layout_param_pkey`.
* Every database, for a user's first layout: both saves insert the same keys.

**Fix.** Two commits.

1. The layout store's transactions run under READ COMMITTED, so a later save sees what an earlier one committed.
2. A save first locks all of the user's `UP_USER_PROFILE` rows with a no-op update. A second save waits there until the first commits. `resetUserLayoutAllProfiles` takes the same lock first, so it can't deadlock with a save.

I lock the profile rows and not the `UP_USER` row because `getNextStructId` updates `UP_USER` while the layout manager's monitor is held. Locking `UP_USER` here could make two requests wait for each other in a way the database can't detect.

**Test.** `RDBMUserLayoutStoreConcurrencyTest` pauses one save at a chosen statement while a second save runs on another thread. Without the lock, 3 of its 6 tests fail on HSQLDB. With both commits, all pass. `UserLayoutHelperImplTest` covers the reset. Upstream's CI build command passes on this branch on my fork (JDK 11, Ubuntu).

**Verified.** The tests ran in GitHub Actions on PostgreSQL 16 and 18, MariaDB 12.3, MySQL 8.4, SQL Server 2025 and Oracle 23. Without the fix:

* PostgreSQL fails with a duplicate key on `up_layout_param_pkey`.
* MariaDB fails with error 1020 on `UP_LAYOUT_PARAM`.
* A user's first layout fails on every database: a duplicate key on PostgreSQL, SQL Server and Oracle, and a deadlock on MariaDB and MySQL.

With both commits all tests pass on all six. The workflows and scripts for these runs are on my fork's branch [`test/verify-fixes-on-databases`](https://github.com/ChristianMurphy/uPortal/tree/test/verify-fixes-on-databases).

**Not verified.** No portal ran on those databases, only the tests.

**Where this comes from.** I'm working on a fork that upgrades uPortal's dependencies (Spring 5.3 so far, heading for Spring 7, Hibernate 7 and Jakarta EE 11). That fork's CI runs uPortal-start's Playwright suite against HSQLDB, PostgreSQL, MySQL, MariaDB, SQL Server and Oracle, and a two-node cluster test. Sign-ins there answered 500 on PostgreSQL and MariaDB when one test user signed in from two workers at once. The bug isn't caused by the upgrade: the code is the same in `v5.17.9` and `master`, so I ported the fix here on its own.
