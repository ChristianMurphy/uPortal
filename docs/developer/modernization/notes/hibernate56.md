# Handoff: Hibernate 5.6.15 on the Ehcache 2 cache (P4-3 step S1)

Branch `ccr-43ba0785-hzlrj5-hibernate56` in uPortal and in uPortal-start. The uPortal branch was
rebased on 2026-11-10 onto `ccr-43ba0785-hzlrj5-mssql-id-lock` (`1d3e6313da`), which is based on
the integration branch at `a00134ea50` and has to be merged first. uPortal-start is based on
`adbe9ea9d3`. Step S1 of `notes/p4-3-plan.md`, decided in MOD-D56 item 1. The second-level cache
stays on hibernate-ehcache over ehcache-core 2.6.11 with JGroups replication. The schema is frozen
(ADR 0006).

## Status

| Commit | Subject | State |
| --- | --- | --- |
| 1 | `build(deps): upgrade hibernate to 5.6.15` | done |
| 2 | `fix(cache): keep the 60 second lock timeout of the hibernate regions` | done |
| 3 | `fix(persistence): build ddl with hibernate 5.6's generation context` | done |
| 4 | `test(persistence): give the jpa test contexts a jdbc 4 pool` | done |
| 5 | `fix(persistence): keep the date and time dimension columns on 5.6` | done |
| 6 | `test(persistence): answer the sequence query in the ddl case for oracle` | done |
| 7 | `test(persistence): record the hsqldb drop script of hibernate 5.6` | done |
| 8 | `test(events): expect spring's rollback exception on hibernate 5.6` | done |
| 9 | `chore(persistence): name the hibernate config files with the 5.6 setting` | done |
| 10 | `chore(cache): state how hibernate treats a region without a cache` | done |
| 11 | `build(deps): compile uPortal-rdbm against the jpa 2.2 api` | done |
| 12 | `build(deps): replace javassist with byte buddy for hibernate proxies` | done |
| 13 | `docs: describe the hibernate 5.6 upgrade for sites` | done |
| 14 | `docs(notice): drop javassist` | done |
| 15 | `fix(events): make the search request aggregation entity final` | done |
| 16 | `test(persistence): keep the test pool's connection use as it was` | done |
| 17 | `build(deps): keep one jaxb runtime in the web application` | done (review) |
| 18 | `build(test): drop the assertion switch for hibernate 5.2's batch fetch` | done (review) |
| 19 | `build(deps): stop excluding a dom4j 2 jar that nothing brings` | done (review) |
| 20 | `docs: compare the hibernate changes with 5.17, explain oracle sequences` | done (review) |

CI runs and the local portal check are in "Runs" below.

## The spike's leads

| Lead (MOD-I423) | Cause | Change |
| --- | --- | --- |
| `SpringBeanEhCacheRegionFactory` doesn't compile | Hibernate 5.3 removed `AbstractEhcacheRegionFactory` with the old cache SPI | Extends hibernate-ehcache's `EhcacheRegionFactory`. `resolveCacheManager` finds the manager by name, and `releaseFromUse` leaves it running (the parent would shut the shared manager down). Commit 1 |
| `MySQL5InnoDBCompressedDialect` doesn't compile | exporters and `Table.getInitCommands` take a `SqlStringGenerationContext` since 5.5 | the three-argument override. Commit 1 |
| commons-dbcp 1.2.2, `IllegalAccessError` | `AbstractBatchImpl.clearBatch` calls `Statement.isClosed()`, which is protected in that pool | the shared JPA test context uses the Tomcat JDBC pool, as production does. Commit 4. Sites with such a pool: UPGRADING-6.0 |
| `MetadataSchemaTool` throws | the two-argument exporter methods are deprecated defaults that throw | passes the context that Hibernate's schema creator builds. Commit 3 |
| trailing `;` in the DDL | `HibernateSchemaManagementTool` defaults `hibernate.hbm2ddl.delimiter` to `;` | `SchemaExport.setDelimiter("")` for create and drop, and the setting for update. The goldens of every vendor keep their statements. Commit 3 |
| HSQLDB drop script | `HSQLDialect.dropConstraints()` is false in 5.6 | recorded, with a new check that every table and sequence is gone after the drop scripts. Commit 7 |
| the DDL case for Oracle needs `all_sequences` | the increment check reads the database's sequences at boot | the test creates a view of that name over HSQLDB's catalog. Commit 6 |
| the sequence increment check | read from 5.4, default `EXCEPTION`, which `hibernate.properties` already sets | no code change. CI checks it on PostgreSQL and Oracle, new and upgraded from 5.17. UPGRADING-6.0 has a section |
| `testAcademicTermDetails`, `UnexpectedRollbackException` | Hibernate 5.6 rolls a rollback-only transaction back on commit and returns, where 5.2 threw (`JdbcResourceLocalTransactionCoordinatorImpl`). Spring then raises its own exception | the test expects it. Commit 8 |

## Found beyond the leads

### The converters' columns became `TIMESTAMP` (MOD-I413)

The spike ran before java-time-jpa was merged, so it couldn't see this. Hibernate 5.6 takes a
converted attribute's JDBC type from the Java type descriptor of the converter's database type
(`SimpleValue.buildAttributeConverterTypeAdapter`). Its registry holds the date descriptors under
`java.util.Date` only. For `java.sql.Date` and `java.sql.Time` it falls back to the first entry
they can be assigned to (`RegistryHelper.resolveDescriptor`), which recommends `TIMESTAMP`.
Hibernate 5.2 asked `JdbcTypeJavaClassMappings` for the class itself and got `DATE` and `TIME`.

Effect on the first test run of this branch:

* `UP_DATE_DIMENSION.DD_DATE` and `UP_TIME_DIMENSION.TD_TIME` were `timestamp` in the DDL of
  HSQLDB, PostgreSQL and Oracle, and `datetime` on MySQL and MariaDB.
* The date was bound as a timestamp with the UTC calendar. `EntityRoundTripGoldenTest` failed for
  `AggrEventsDb in Australia/Sydney` on 56 lines.

Fix: `JdbcDateTimeTypeContributor` in uPortal-core registers Hibernate's JDBC date and time
descriptors under the two JDBC classes, through `META-INF/services`. The converters stay as they
are. `DateTypeDescriptor` (SQL) is the same file in 5.2.18 and 5.6.15, so a `DATE` is still bound
without the `hibernate.jdbc.time_zone` calendar, and the trap of MOD-I413 doesn't fire.

Evidence: `JavaTimeColumnStorageTest` now asserts the column types. With the services file moved
away, its three zones failed at the `DATE` type assertion. With it, the test passes, the DDL
goldens of every vendor but HSQLDB's drop sections are unchanged, and the persistence goldens pass
in UTC, `TZ=America/New_York` and `TZ=Australia/Sydney`.

### The lock timeout of the regions was 0

`EhcacheRegionFactory.prepareForUse` sets its lock timeout only when
`net.sf.ehcache.hibernate.cache_lock_timeout` is present, and `getTimeout()` returns the field.
uPortal doesn't set it. hibernate-ehcache 5.2 used 60 seconds. With 0, the soft locks of the 15
`READ_WRITE` entity regions and the query cache's pre-invalidation timestamps expire at once.
`SpringBeanEhCacheRegionFactory.getTimeout()` returns 60 seconds unless the setting is present.

Evidence: `SpringBeanEhCacheRegionFactoryTest` (6 cases). With the override returning
`super.getTimeout()`, `keepsLocksForSixtySecondsWhenNoTimeoutIsConfigured` failed. The fault was
reverted.

### Two JPA API jars

Hibernate 5.6 brings `javax.persistence-api` 2.2, and uPortal-rdbm declared
`hibernate-jpa-2.1-api`. The web application's runtime classpath held both. uPortal-rdbm now
declares the 2.2 jar.

### Byte Buddy

Hibernate 5.6.15 asks for Byte Buddy 1.12.18. The test classpath resolved Mockito's 1.18.12, so
no test ran on 1.12.18. uPortal-core now declares 1.18.12 at runtime. Javassist leaves the
classpath.

### A lazy-loading proxy that Hibernate 5.6 refuses (MOD-I125)

`BaseAggregationImpl.getAggregatedGroup` is final. For `SearchRequestAggregationImpl`, the one
aggregation entity that wasn't a final class, Hibernate 5.2 warned (HHH000112) and built the
proxy. Hibernate 5.6 throws, skips the proxy and logs HHH000305 with a stack trace in `dataInit`
and at every portal start. The class is final now, as its five siblings are (commit 15). The local
portal logged HHH000305 once before the change and didn't log it after.

### The test pool ran three vendors out of connections

The first DB integration run (42706503954) failed on PostgreSQL 18 and 19, SQL Server 2025 and
Oracle 23 with refused connections ("sorry, too many clients already", ORA-12516). Commit 4 had
moved the shared test pool to Tomcat's, which opens 10 connections per pool at creation and, with
its sweeper on, keeps returned connections idle up to `maxActive` for a minute. commons-dbcp
opened none in advance and closed a returned connection when 10 were idle. Commit 16 sets
`initialSize` and `minIdle` to 0 and turns the sweeper off, which makes Tomcat's pool close a
returned connection at once when `maxIdle` are idle (`ConnectionPool.returnConnection`,
tomcat-jdbc 7.0.37).

## Local portal on JDK 21

`portalInit`, `tomcatStart`, a sign-in as `admin` through `/uPortal/Login` (HTTP 200, the admin
layout with its portlets), `layout.json` and `portlets.json` (200), then `tomcatStop` and
`hsqlStop`. uPortal `f7252fa621` plus commit 15, uPortal-start `60f2d9f406`. No browser was used.

* `dataInit` ran every create statement without a trailing `;`.
* The web application holds `hibernate-core-5.6.15.Final`, `hibernate-ehcache-5.6.15.Final`,
  `ehcache-core-2.6.11`, `byte-buddy-1.18.12` and `javax.persistence-api-2.2`, and no Javassist
  or `hibernate-jpa-2.1-api` jar.
* Hibernate messages in `portal.log` that 5.2 didn't log:
  * HHH020100, twice: the Ehcache provider is deprecated. Expected until the provider step (S6).
  * HHH90001007, four times: the legacy names of the query and timestamps regions, for `PortalDb`
    and `AggrEventsDb`. S2 renames them.
  * HHH90001006, eleven times, in place of HHH020003: the regions without an `ehcache.xml` entry
    (MOD-I424). S2 configures them.
  * HHH90001003, once: read-only caching for the mutable entity `PortletLifecycleEntryImpl`. A
    mapping question for S2.
* HHH000482 (8) and HHH000207 (27) are as before. HHH000112 is gone.
* No region name with the prefix twice (`AggrEvents.AggrEvents.*`) appeared in `portal.log` or
  `catalina.out`. The portal ran for about ten minutes, and no aggregation query ran in that
  time, so this isn't a full check of MOD-I125.

One local change was needed to build uPortal-start here. It was not committed. The `buildscript`
block of `gradle/tasks/perf.gradle` asks Maven Central directly, and Maven Central answered 429.
An init script can't change the `buildscript` repositories of a script plugin. For the run, the
block pointed at the approved mirror, and the file was restored afterwards.

## After the review of 2026-11-09

* **One JAXB runtime (commit 17).** The runtime classpath of uPortal-webapp lists `jaxb-impl`
  2.3.3 and no `jaxb-runtime`, `txw2`, `istack-commons-runtime`, `stax-ex` or `FastInfoset`. The
  deployed `WEB-INF/lib` has `jaxb-api-2.3.1`, `jaxb-impl-2.3.3` and `jaxb2-basics-runtime-1.11.1`.
  NOTICE doesn't list the removed jars.
* **dom4j (commit 19).** With the exclusion lifted, `dependencyInsight --dependency
  org.dom4j:dom4j` found nothing in `runtimeClasspath` or `testRuntimeClasspath` of
  uPortal-webapp.
* **JDK 21, after commits 17 to 19:** `./gradlew build spotbugsMain --continue` without the web
  component tasks passed.
* **JDK 25.0.5** (Gradle and the test JVMs, `JAVA_HOME`): the tests of uPortal-core (86),
  uPortal-events (27), uPortal-rdbm (3), uPortal-hibernate (6) and uPortal-webapp (492, of which
  76 skipped) passed.
* **Portal on JDK 11.0.33** (Gradle on 21): `portalInit`, `tomcatStart`, a sign-in as `admin`
  with curl (200, the admin layout), `layout.json` (200), then `tomcatStop` and `hsqlStop`.
  `portal.log` has the Hibernate messages of the JDK 21 run and no exception. HHH000305 isn't
  among them.

## Local evidence

JDK 21, Gradle 9.8.1, dependencies through the approved mirror init script.

* `./gradlew build spotbugsMain --continue` without the web component tasks (Chromium 1243 is
  installed here, and the pinned Playwright version downloads 1248): every test task, SpotBugs
  and Spotless passed after the two formatting fixes. uPortal-webapp ran 492 tests, none failed
  and 76 were skipped, as on the tip.
* `TZ=America/New_York` and `TZ=Australia/Sydney`: uPortal-webapp (492), uPortal-core (86) and
  uPortal-events (27) passed in both.
* Passing among them: `EntityRoundTripGoldenTest`, `AggregationGoldenTest`, `DaoQueryGoldenTest`,
  `PermissionDecisionCacheRaceTest`, the race tests of the entity caches, the layout, locale and importer
  concurrency tests, `MigrationFreezeRulesTest` and `CacheConfigurationCoverageTest`.
* `check-freeze-store.py`: 335, 0 and 14 recorded uses, unchanged.

## Runs

### After the rebase and the review fixes

uPortal `cf52b454d5`, uPortal-start `df45e44523`. The push runs of the commit that adds this
section are in the report to the coordinator.

| Repository | Workflow | Run | Result |
| --- | --- | --- | --- |
| uPortal | CI (push) | 42723868771 | Build and Prose passed. Commit messages failed: after the forced push the job couldn't find the previous head and checked the whole history, and the findings are in three older commits. The 21 commits of this branch pass both checks |
| uPortal | DB copy tool (push) | 42723869181 | passed |
| uPortal | DB integration (manual) | 42723886969 | seven of eight jobs passed. SQL Server 2025 failed in `JpaClusterLockDaoTest.testConcurrentCreation` (job 129524473140), see the next paragraph |
| uPortal-start | CI, Workflow lint | 42723874175, 42723874192 | passed |
| uPortal-start | HTTP replay (push) | 42723874264 | passed without "Replay against 5.17": the push didn't change `tests/replay` |

`testConcurrentCreation` failed on SQL Server in both manual runs after the review round
(42723886969, job 129524473140, and 42726717925, job 129534120643, on the commit after
`cf52b454d5`, which changes this note only). It had passed there once, in run 42710461224 on
commit 16. In each failure one of the test's three threads was the deadlock victim inside
`persist`, in the isolated transaction that reads the next id of `ClusterMutex`
(`LockAcquisitionException: error performing isolated work`).

From the sources:

* On SQL Server the sequences are one-row tables without an index. `UP_MUTEX_SEQ` has
  `allocationSize = 1`, so every new `ClusterMutex` reads and updates it.
* Hibernate reads the row with `with (updlock, holdlock, rowlock)` and then updates it
  (`TableStructure`, `SQLServer2005Dialect.appendLockHint`). Both are the same in 5.2.18 and
  5.6.15. Hibernate 4.3 didn't add `holdlock`.
* The LOG has no earlier failure of this test on SQL Server.

Likely cause, not shown by a test: `holdlock` on a table without an index makes two
transactions that read at the same moment wait for each other when they update. Until commit 4
the test pool was commons-dbcp 1.2, which opens new connections one at a time, so the three
threads seldom read at the same moment. Tomcat's pool opens them in parallel, as it does in
production. If that is right, the deadlock exists on the integration branch since the Hibernate
5.2 step, and this branch's test pool made the test show it. The other explanation, a change in
Hibernate 5.6, has no support in the sources read.

The decision (coordinator, 2026-11-10) was a fix in the product, shown first on the Hibernate
5.2 code. That is the topic `-mssql-id-lock` (`notes/mssql-id-lock.md`). Its test deadlocks on
SQL Server on the integration branch's code: 25 of 60 inserts in run 42733496148. Hibernate 5.6
didn't bring the deadlock. The topic's dialect returns to the lock hint of Hibernate 4.3, and
this branch is rebased onto it.

### Before the rebase

uPortal `5ffc5a7369` (commit 16) and uPortal-start `60f2d9f406`.

| Repository | Workflow | Run | Result |
| --- | --- | --- | --- |
| uPortal | CI (push) | 42710456666 | Build and Commit messages passed. Prose (Vale) failed on `agent-guide.md:214`, a line of the integration branch at `18dd1d3c16` that the tip has since changed. This branch's docs pass `vale.sh docs` |
| uPortal | DB copy tool (push) | 42710456180 | passed |
| uPortal | DB integration (manual: six vendors and three canaries) | 42710461224 | passed |
| uPortal | DB integration (manual, on commit 14) | 42706503954 | failed on PostgreSQL 18 and 19, SQL Server 2025 and Oracle 23: the test pool, fixed by commit 16 |
| uPortal-start | Integration (push, attempt 2) | 42706549755 | passed. Its jobs were Playwright (HSQLDB, HSQLDB with CAS, PostgreSQL 18), the DB harness on PostgreSQL 18, G-migrate and the Cluster job |
| uPortal-start | HTTP replay (push, attempt 2) | 42706549496 | passed. "Replay against 5.17" ran (job 129489833604) |
| uPortal-start | G-index | 42706549434 | passed |
| uPortal-start | Migration equivalence | 42706549418 | passed |
| uPortal-start | CI | 42706549443 | passed |

* Attempt 1 of Integration and of HTTP replay, and the G-index, Migration equivalence and CI
  runs, started before commits 15 and 16 were pushed. Their `Build uPortal` jobs ran at 21:59 UTC,
  when the uPortal branch head was `f7252fa621` (commit 14). They passed.
* Integration and HTTP replay were then re-run with "Re-run all jobs" (agent guide, section 10,
  a run that built an older commit). The `Build uPortal` job of attempt 2 found no earlier build
  and built the branch head at 22:35 UTC, `5ffc5a7369`. The `uPortal under test` annotation
  wasn't read: `gh` has no valid token on this machine, and the end of the job log doesn't
  include that line.
* A topic push runs the DB harness and Playwright on PostgreSQL 18 only. The other vendors of
  the DB harness, and G-migrate from 5.17 on them, run in the coordinator's batch.
* The first CI run (42706492080) also failed its Commit messages job. A new branch checks the
  whole history, and the findings were in three older commits.

## Open

* **Oracle reads `all_sequences` without a schema.** Hibernate 5.6's extractor for Oracle returns
  no schema, and the increment check takes the first sequence with the name. A portal user that
  has access to a same-named sequence in another schema can get that one's increment.
  The review of 2026-11-09 decided for documentation. UPGRADING-6.0 says what a site sees and
  gives the query that finds the second sequence. The code for Oracle stays as it is.
* **S2** (`-l2-regions`) renames the default regions and sets the missing cache strategy to
  `fail` in tests. This branch only states `create-warn`.

## Proposed LOG rows

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I423 (update) | Persistence | The spike's leads, as the table in this note resolves them | **Done** in `-hibernate56` |
| MOD-I413 (update) | Persistence | Rechecked on 5.6.15: `DateTypeDescriptor` is unchanged, so `DATE` is bound without the calendar. But 5.6 maps an `AttributeConverter` to `java.sql.Date` or `java.sql.Time` as `TIMESTAMP`, because its Java type registry has no entry for the two classes. The dimension columns changed type on every vendor and the Sydney golden failed | **Fixed** by `JdbcDateTimeTypeContributor`. `JavaTimeColumnStorageTest` asserts the column types. Recheck at P6 |
| MOD-I? | Caching | hibernate-ehcache 5.6's `EhcacheRegionFactory.getTimeout()` is 0 unless `net.sf.ehcache.hibernate.cache_lock_timeout` is set, where 5.2 used 60 seconds. Soft locks and query cache pre-invalidation would expire at once | **Fixed** in `SpringBeanEhCacheRegionFactory`, with a test |
| MOD-I? | Build | With Hibernate 5.6 the web application held `hibernate-jpa-2.1-api` and `javax.persistence-api` 2.2 together, and Byte Buddy 1.12.18 (Java 20 at most) where the tests ran on Mockito's 1.18.12 | **Fixed**: one JPA jar, Byte Buddy 1.18.12 declared, Javassist removed |
| MOD-I? | Persistence | On Oracle, Hibernate 5.6's increment check reads `all_sequences` and compares by name only, across the schemas the user has access to | Documented in UPGRADING-6.0, with the query that finds the cause (review of 2026-11-09) |
| (in `notes/mssql-id-lock.md`) | Persistence | `JpaClusterLockDaoTest.testConcurrentCreation` deadlocked on SQL Server 2025 in two of three manual runs of this branch, after its test pool moved to Tomcat's (42723886969 and 42726717925 failed, 42710461224 passed) | Cause and fix are in `-mssql-id-lock`. Hibernate 5.2 has the same deadlock |
| MOD-I121 (update) | Tests | Rechecked on 5.6.15: `BatchFetchQueueHelper` has no `assert` statement any more | The `-da` switch is removed from the test JVMs |
| MOD-I? | Build | hibernate-core 5.6.15 brings `org.glassfish.jaxb:jaxb-runtime` 2.3.1 with four more jars, next to the `jaxb-impl` 2.3.3 the web application has. The root build still excluded `org.dom4j:dom4j`, which no dependency brings since 5.6 | **Fixed**: `jaxb-runtime` excluded where Hibernate is declared, and the dom4j exclusion removed |
| MOD-I125 (update) | Runtime | On 5.6 a portal logs HHH020100, HHH90001006 and HHH90001007 (the cache provider and region names, for S2 and S6) and HHH90001003 for `PortletLifecycleEntryImpl`. HHH000112 became HHH000305 with a stack trace for `SearchRequestAggregationImpl`. No `AggrEvents.AggrEvents.*` region appeared in a ten-minute local run in which no aggregation query ran | HHH000305 **fixed** (the class is final). The doubled prefix needs a run with aggregation. The rest stays for S2 |
| MOD-I? | Testing | Tomcat's JDBC pool with default settings opens 10 connections per pool and keeps returned ones idle. The shared JPA test context then used more connections than PostgreSQL, SQL Server and Oracle allow in the DB integration jobs | **Fixed**: `initialSize=0`, `minIdle=0`, sweeper off |
