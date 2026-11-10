# Handoff: parallel inserts deadlock on SQL Server

Branch `ccr-43ba0785-hzlrj5-mssql-id-lock` in uPortal, based on the integration branch at
`a00134ea50` (Hibernate 5.2.18). uPortal-start has a branch of the same name at its tip
`adbe9ea9d3`, without a change, so that its runs build this uPortal branch. The branch stands on
its own and is meant to be merged before `-hibernate56`.

## Status

| Commit | Subject | State |
| --- | --- | --- |
| 1 | `test(persistence): insert rows from parallel threads and check their ids` | done |
| 2 | `fix(persistence): read sql server id tables without holdlock` | done |

## Cause

Found in the `-hibernate56` review round: `JpaClusterLockDaoTest.testConcurrentCreation`
deadlocked on SQL Server 2025 in two of three manual DB integration runs, after that branch moved
the JPA test pool from commons-dbcp 1.2 to Tomcat's (`notes/hibernate56.md`).

* On SQL Server the portal uses `SQLServer2005Dialect`, which has no sequences. Each id generator
  is a one-row table without an index (`UP_MUTEX_SEQ` and the other `UP_…_SEQ` tables). uPortal
  5.17 had the same tables: its three `hibernate*.cfg.xml` files set
  `hibernate.id.new_generator_mappings=true`, and Hibernate 4.3's `SQLServer2005Dialect` has no
  sequences either.
* Hibernate reads the row with the dialect's write lock hint and then updates it, in a
  transaction of its own (`TableStructure`, `dialect.appendLockHint(LockMode.PESSIMISTIC_WRITE,
  …)`).
* Hibernate 4.3.11's `SQLServer2005Dialect.appendLockHint` returns `with (updlock, rowlock )` for
  write locks. Hibernate 5.2.18 and 5.6.15 return `with (updlock, holdlock, rowlock)`.
* The portal's own code takes no pessimistic lock: no `LockModeType` or `LockMode` use outside
  the tests. The id tables are the one place where the hint is used.

## Shown on the integration branch's code

`ParallelIdAllocationTest` (commit 1) has six threads insert ten `ClusterMutex` rows each through
`JpaClusterLockDao`. A barrier starts every round, and the data source is a Tomcat JDBC pool,
which opens connections in parallel as the portal's does. `UP_MUTEX_SEQ` has an allocation size
of 1. Each of the 60 inserts reads the id table.

On commit 1 alone, with Hibernate 5.2.18 and no fix, the manual DB integration run 42733496148
failed on SQL Server 2025 only (job 129557041695): "25 of 60 parallel inserts failed", each with
"Transaction … was deadlocked on lock resources with another process and has been chosen as the
deadlock victim". The other seven jobs of that run passed, PostgreSQL, MySQL, MariaDB and Oracle
with their canaries. So the deadlock is in the integration branch today, and Hibernate 5.6 didn't
bring it. `JpaClusterLockDaoTest` passed in the same job, on the commons-dbcp pool.

## Fix

`SQLServer2005NoHoldLockDialect` (uPortal-hibernate4-dialects) extends `SQLServer2005Dialect` and
returns the 4.3 hint, `with (updlock, rowlock)`, for the write lock modes. Read locks and
skip-locked reads keep Hibernate 5's hints. `PortalDialectResolver` returns it for SQL Server from
version 9 on. For version 9 (SQL Server 2005) Hibernate's own resolver used to answer, and it
picked `SQLServer2005Dialect` too.

Ids stay unique: the update lock on the row is kept until the id transaction commits, so the next
reader waits and then reads the new value. The test asserts 60 distinct ids.

`PortalDialectResolverTest` checks the dialect for versions 9, 10, 16 and 17 and the hints.
UPGRADING-6.0 has a section "SQL Server: parallel inserts". A site that sets `hibernate.dialect`
for the portal's units itself has to change it. The portal's own contexts don't read the
`hibernate.dialect` of uPortal-start's `global.properties`, so its SQL Server configuration needs
no change. That reading comes from the contexts. No uPortal-start run on SQL Server checked it.

### Sequences

`SQLServer2012Dialect` has sequences, and every SQL Server version the project tests (2019, 2022,
2025) supports them. Using them would move each site's id storage from the one-row tables to new
sequences, which have to start above the tables' values. That is a schema change and out of
scope here (ADR 0006).

## Evidence

All manual DB integration runs cover PostgreSQL 18, MySQL 9.7, MariaDB 12.3, SQL Server 2025 and
Oracle 23, and the canaries PostgreSQL 19, MySQL 26 and MariaDB 13.

| Commit | Run | Result |
| --- | --- | --- |
| 1 (`3a89d982dc`, test only, Hibernate 5.2.18) | DB integration, manual, 42733496148 | SQL Server 2025 failed in `ParallelIdAllocationTest`: 25 of 60 inserts were deadlock victims (job 129557041695). The other seven jobs passed |
| 2 (`acc7fed88c`, with the fix) | DB integration, manual, 42735260301 | passed, all eight jobs |
| 2 | DB integration, manual, 42737280243 | passed, all eight jobs |
| 2 | DB integration, manual, 42738685420 | passed, all eight jobs. The SQL Server report lists `ParallelIdAllocationTest` with one test, not skipped |
| 2 | CI (push), 42735253319 | passed |

The runs with the fix were started one after the other, and none was re-run. There was no
red run after the fix.

Local, JDK 21, HSQLDB: `./gradlew spotlessCheck :uPortal-utils:uPortal-utils-core:test
:uPortal-webapp:test` and SpotBugs on the two changed modules passed. No SQL Server is available
on this machine, so every SQL Server result is from CI.

The uPortal-start runs of the branch of the same name are in the report to the coordinator. A
topic push there runs the DB harness and Playwright on PostgreSQL 18 only, so they don't reach
the SQL Server dialect.

## Proposed LOG rows

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I? | Persistence | On SQL Server, inserts of one entity that run at the same moment deadlock in Hibernate's id transaction. The id generators are one-row tables, and Hibernate 5 reads them with `updlock, holdlock, rowlock`, where 4.3 (uPortal 5.17) used `updlock, rowlock`. On the integration branch since the Hibernate 5.2 step. The tests didn't show it, because their commons-dbcp pool opens connections one at a time. 25 of 60 parallel inserts failed in run 42733496148 | **Fixed** in `-mssql-id-lock`: `SQLServer2005NoHoldLockDialect` with the 4.3 hint, chosen by `PortalDialectResolver`. `ParallelIdAllocationTest` on a Tomcat pool, every vendor |
| MOD-I? | Persistence | SQL Server sites could use real sequences (`SQLServer2012Dialect`), which every tested version supports | Left for the 6.0 schema step, because it moves the id storage of existing sites (ADR 0006) |
