fix(persistence): fetch counter blocks under read committed

##### Checklist

-   [x] the [individual contributor license agreement](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#individual-contributor-license-agreement) is signed
-   [x] commit message follows [commit guidelines](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#commit)
-   [x] tests are included

##### Description of change

**Problem.** On MariaDB 11.6 and later, two portal servers that fetch a block of the same counter at the same time can fail with "Record has changed since last read in table 'UP_SEQUENCE'" (error 1020). The request that needed the id fails, for example when a user or group is created. `HibernateStyleCounterStore` reads the counter, updates it if it is unchanged, and retries when the update matches no row. MariaDB 11.6 turned `innodb_snapshot_isolation` on by default, so under REPEATABLE READ the update throws and the retry never runs.

**Fix.** `counterStoreTransactionOperations` in `persistenceContext.xml` runs under READ COMMITTED. A lost race is then an update that matches no row again, and the store retries. It is one line of XML.

**Test.** A new test in `CounterStoreTest` builds the bean from `persistenceContext.xml` and pauses one server between its read and its write while another takes a block. It uses HSQLDB with SERIALIZABLE connections, which refuses the update as MariaDB does. It fails with the old definition and passes with the fix. Upstream's CI build command passes on this branch on my fork (JDK 11, Ubuntu).

**Verified.** The new test ran in GitHub Actions on MariaDB 12.3 with `innodb_snapshot_isolation` on. Without the fix it fails with error 1020 on `UP_SEQUENCE`, and with the fix it passes. It passes both ways on PostgreSQL 16 and 18, MySQL 8.4, SQL Server 2025 and Oracle 23. The workflows and scripts for these runs are on my fork's branch [`test/verify-fixes-on-databases`](https://github.com/ChristianMurphy/uPortal/tree/test/verify-fixes-on-databases).

**Not verified.** No portal ran on MariaDB with this branch, only the test.

**Where this comes from.** I'm working on a fork that upgrades uPortal's dependencies (Spring 5.3 so far, heading for Spring 7, Hibernate 7 and Jakarta EE 11). That fork's CI runs uPortal-start's Playwright suite against HSQLDB, PostgreSQL, MySQL, MariaDB, SQL Server and Oracle, and a two-node cluster test. I found this while fixing layout saves that failed on MariaDB 12.3. The bug isn't caused by the upgrade: the code is the same in `v5.17.9` and `master`, so I ported the fix here on its own.
