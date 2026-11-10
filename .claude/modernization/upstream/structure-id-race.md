fix(layout): give each structure id to one request only

##### Checklist

-   [x] the [individual contributor license agreement](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#individual-contributor-license-agreement) is signed
-   [x] commit message follows [commit guidelines](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#commit)
-   [x] tests are included

##### Description of change

**Problem.** Two requests of one user that add to the layout at the same time, for example from two browser tabs, can get the same structure id. `RDBMUserLayoutStore.getNextStructId` reads `UP_USER.NEXT_STRUCT_ID`, updates it if it is unchanged, and returns the new id without checking whether the update matched a row. If another request took an id in between, both return the same id. On MariaDB 11.6 and later the update fails with error 1020 instead.

**Fix.** Increment `NEXT_STRUCT_ID` first, then read it, in the same transaction. The update locks the user's row until commit, so a second request waits and gets the next id.

**Test.** `RDBMUserLayoutStoreStructIdTest` pauses one request before its write while another takes an id. On `master` it fails with "A and B got the same id". With the fix it passes. Upstream's CI build command passes on this branch on my fork (JDK 11, Ubuntu).

**Verified.** The test ran in GitHub Actions on PostgreSQL 16 and 18, MariaDB 12.3, MySQL 8.4, SQL Server 2025 and Oracle 23. Without the fix, both requests got the same id on every database except MariaDB, which failed with error 1020. With the fix it passes on all of them. The workflows and scripts for these runs are on my fork's branch [`test/verify-fixes-on-databases`](https://github.com/ChristianMurphy/uPortal/tree/test/verify-fixes-on-databases).

**Not verified.** No portal ran on those databases, only the test.

**Where this comes from.** I'm working on a fork that upgrades uPortal's dependencies (Spring 5.3 so far, heading for Spring 7, Hibernate 7 and Jakarta EE 11). That fork's CI runs uPortal-start's Playwright suite against HSQLDB, PostgreSQL, MySQL, MariaDB, SQL Server and Oracle, and a two-node cluster test. A layout test there failed on MariaDB 12.3 with error 1020, and this race was one of the causes I found. The bug isn't caused by the upgrade: the code is the same in `v5.17.9` and `master`, so I ported the fix here on its own.

`fix/layout-save-overlap` changes other parts of the same file. The two merge cleanly in either order.
