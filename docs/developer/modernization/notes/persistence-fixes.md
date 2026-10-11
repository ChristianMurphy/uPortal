# Persistence fixes found by the goldens (MOD-I140, MOD-I141, MOD-I142, MOD-I191, G-index R4)

Branch `ccr-43ba0785-hzlrj5-persistence-fixes` in uPortal, made from `ccr-43ba0785-hzlrj5` at
`c8000b9dd` (Hibernate 5.2 merged). The coordinator merges it into
`ccr-43ba0785-hzlrj5`. LOG row numbers for new findings: MOD-I220 to MOD-I229.

Order of work: MOD-I142, MOD-I191, MOD-I141, MOD-I140 (design note to the coordinator first),
G-index R4. Each fix shows the bug in a failing test, fixes it, then re-records the affected
golden on HSQLDB with `-Duportal.golden.record=true` and lists the changed golden lines in the
commit body.

## Status

| Fix | State | Commits | CI |
| --- | --- | --- | --- |
| MOD-I142 portlet search | pushed; light CI | `3e7e375` | |
| MOD-I191 order tie-breakers | pushed; light CI | `98f0524` | |
| MOD-I141 timestamps in UTC | done locally; uPortal-start part waits for access | see `git log` | |
| MOD-I140 analytics repeated hour | design approved (option B), built in the 6.0 schema step; characterisation committed | see `git log` | |
| G-index R4 | index plan written for the 6.0 schema step; allowlist entries stay | | |
| 6: pre-delete in `PersistenceUnits` | removed; passes on HSQLDB, needs the six-vendor run | see `git log` | |
| 7: `TransientPortletEntityDao` NPE | reproduced and fixed | see `git log` | |

## Full-matrix runs (head `bcf049d`)

Started 2026-10-11 11:04 UTC with workflow_dispatch:

| Workflow | Run | Why |
| --- | --- | --- |
| CI | 38134202155 | full CI matrix |
| DB integration | 38134204060 | the goldens on the six databases |
| DB copy tool | 38134205546 | this branch changes the tool |

Next step:
read their results. A red golden on a vendor other than HSQLDB is this branch's to fix. If Oracle
fails on the removed pre-delete (fix 6), revert `502d3e2` and record why.

## Items for the 6.0 schema step (with Hibernate 7, after P7-1)

Each one changes the schema and waits for that step. The sections below give the details.

| Item | Change | Copy tool, 5.17 to 6.0 |
| --- | --- | --- |
| MOD-I140 | each aggregation row's UTC offset, in its natural id, in the six aggregation tables | fills the offset with `--source-zone` |
| G-index R4 | indexes on the three foreign keys to `UP_UNIQUE_STR` | creates them with the 6.0 schema |
| MOD-I141 marker | `dataInit` and the copy tool write a marker, and a 6.0 portal refuses to start on an unconverted database | writes the marker |
| MOD-D44 | SQL Server text columns become `nvarchar` (Hibernate's nationalized strings) | converts the text |

## Running tests in this container

Gradle runs here with JDK 21 and an init script outside the repos
(`~/.gradle/init.d/mirror.gradle`) that points Maven Central at Google's mirror. For "before the
fix" evidence, a second worktree under the session scratchpad holds only the new test.

## MOD-I142: portlet search

- `IPortletDefinitionDao` and `IPortletDefinitionRegistry` get
  `searchForPortlets(String term, SearchMatch match)` with `EXACT`, `STARTS_WITH`, `ENDS_WITH` and
  `CONTAINS`. The old `searchForPortlets(term, allowPartial)` stays as a default method
  (`CONTAINS` or `EXACT`).
- The partial matches compare `upper(name)` and `upper(title)` with `upper(?)`, and the pattern is
  escaped with `!` (`ESCAPE '!'`). Only `%`, `_` and `!` are escaped, because HSQLDB and Oracle
  reject an escape character followed by anything else.
- `PortletDefinitionSearcher` (groups) passes the search method through instead of adding `%`
  itself, so starts-with and ends-with no longer behave as contains.
- Before the fix, `JpaPortletDaoTest.searchMatchesTheTermLiterallyAndIgnoresCase` failed on HSQLDB
  with `CONTAINS "cal" expected:<[calendar]> but was:<[]>`.
- After the fix: `JpaPortletDaoTest`, `JpaPortletDefinitionDaoLikePatternTest` (table test of the
  pattern) and the re-recorded query golden pass on HSQLDB, as do the tests of uPortal-web,
  uPortal-rendering, uPortal-content-portlet and uPortal-groups-local.
- `QueryVendorDifferences` loses the MOD-I142 entries (the partial searches and `"über"`). The
  exact search `"calendar", false` stays listed: that is collation, not MOD-I142.
- Open question for the coordinator: on SQL Server, `[` in a LIKE pattern starts a character
  class. `!` can't escape it on the other vendors (see above), so a term with `[` may still
  differ on SQL Server. The task did not ask for it, and no test covers it yet.

### Proposed UPGRADING-6.0.md text

> **Portlet search.** The partial portlet searches (the groups and permissions pickers, through
> `PortletDefinitionSearcher`) now ignore case on every database, honour starts-with and
> ends-with, and treat `%` and `_` in the search term as ordinary characters. Code that calls
> `IPortletDefinitionRegistry.searchForPortlets(term, true)` and put SQL wildcards in `term` must
> use `searchForPortlets(term, SearchMatch.STARTS_WITH)` or `ENDS_WITH` instead. The exact
> search (`false`, or `SearchMatch.EXACT`) is unchanged and compares as the database does.

## MOD-I191: a final tie-breaker on every sort

Every sorted query in uPortal's persistence code, and what it sorts by now:

| Where | Query | Sort before | Change |
| --- | --- | --- | --- |
| `JpaPortalEventStore` | raw events in a time range (two HQL queries) | `timestamp` | adds `id` |
| `JpaBaseAggregationDao` | `getAggregations` of every aggregation type | date desc, time desc | adds the entity id (from the metamodel) |
| `JpaPortletLayoutAggregationDao`, `JpaSearchRequestAggregationDao` | their own `getAggregations` | date desc, time desc | adds `id` |
| `JpaEventAggregationManagementDao` | quarters | `quarterId` | adds `id` (the natural id is start, end and number together) |
| `JpaEventAggregationManagementDao` | academic terms | `start` | adds `id` |
| `PortletDefinitionImpl.lifecycleEntries` | `@OrderBy` | `ENTRY_DATE` | adds `ENTRY_ID` |
| `PortletList.items` | `@OrderBy` | `LIST_ORDER` | adds `ID` (a UUID string: hex digits and dashes in fixed places, which sort alike on every database) |
| `RDBMUserLayoutStore` | layout rows joined with their parameters | `STRUCT_ID` | adds `STRUCT_PARM_NM` (part of the parameter table's key) |
| `RDBMUserLayoutStore` | parameters of the channel rows | `STRUCT_ID` | adds `STRUCT_PARM_NM` |
| `RDBMLocaleStore` | a user's locales | `PRIORITY` | adds `LOCALE` (part of the key) |
| `RDBMUserLayoutStore` | struct ids of the channel rows | `STRUCT_ID` | none: unique for a user and layout |
| `JpaDateDimensionDao` (two), `JpaTimeDimensionDao` | dimensions | `date`, `time` | none: the only natural id, so unique |
| `JpaTenantDao` | tenants | `name` | none: unique column |
| `JpaMessageDao` (three) | messages | code and locale | none: unique together, and each query fixes one of them |

Search used: `order by`, `@OrderBy`, `orderBy(`, `cb.asc`/`cb.desc` and `addOrder` over all main
Java, XML, SQL and Groovy files. The copy tool (uPortal-db-migrate) has no ORDER BY.

Goldens:

- The round trip compared `@OrderBy` lists as multisets and checked only the first sort key. It
  now sorts the expected list by every `@OrderBy` item, reports elements left tied, and compares
  in order. Before the tie-breakers it failed on `lifecycleEntries` (MAX and UNICODE variants) and
  `PortletList.items` (MAX).
- The aggregation golden sorted the raw events of one second and numbered the aggregation rows by
  distinct time. It now writes both in the order the DAO returns them. The re-recorded files hold
  the same rows. Only the order inside ties and the numbering changed.
- Seeded fault: `PortletList.items` back to `@OrderBy("LIST_ORDER ASC")` fails the round trip with
  `PortletList[MAX].items: @OrderBy("LIST_ORDER ASC") leaves elements tied`.
- Not covered by a golden: the two `RDBMUserLayoutStore` queries and `RDBMLocaleStore`. HSQLDB
  returns their ties in key order anyway, so a test there can't fail without the change.

## MOD-I141: timestamps in UTC

### Every temporal column uPortal maps

| Table.column | Field | Mapping | 5.17 stores | With `hibernate.jdbc.time_zone=UTC` alone | After this fix |
| --- | --- | --- | --- | --- | --- |
| `UP_PORTLET_LIFECYCLE.ENTRY_DATE` | `PortletLifecycleEntryImpl.date` | `java.util.Date` | JVM local | UTC | `Instant`, UTC |
| `UP_MUTEX.LOCK_START`, `LOCK_UPDATE` | `ClusterMutex.lockStart`, `lastUpdate` | `java.util.Date` | JVM local | UTC | `Instant`, UTC |
| `UP_MUTEX.LOCK_END` | `ClusterMutex.lockEnd` | `java.util.Date` | JVM local | UTC | `Instant`, UTC (not in MOD-I141's list, see below) |
| `UP_PERSON_DIR.LST_PSWD_CGH_DT` | `LocalAccountPersonImpl.lastPasswordChange` | `java.util.Date` | JVM local | UTC | `Instant`, UTC |
| `UP_PORTLET_LIST.CREATED_ON`, `UPDATED_ON` | `PortletList.createdOn`, `updatedOn` | `java.sql.Timestamp` | JVM local | UTC | `Instant`, UTC |
| `UP_TIME_DIMENSION.TD_TIME` | `TimeDimensionImpl.time` | Jadira `localTime` | JVM local time of day | **moves by the JVM offset** | unchanged (`JvmZoneLocalTimeType`) |
| `UP_DATE_DIMENSION.DD_DATE` | `DateDimensionImpl.date` | Jadira `localDate`, `databaseZone=jvm` | local date | unchanged | unchanged |
| `UP_RAW_EVENTS.TIMESTAMP`, `UP_ANALYTICS_EVENTS.TIMESTAMP` | `PersistentPortalEvent`, `PersistentAnalyticsEvent` | Jadira `dateTime` | UTC | unchanged | unchanged |
| `UP_EVENT_AGGR_STATUS.LAST_START`, `LAST_END`, `LAST_EVENT_DATE` | `EventAggregatorStatusImpl` | Jadira `dateTime` | UTC | unchanged | unchanged |
| `UP_ACADEMIC_TERM_DETAIL.TERM_START`, `TERM_END` | `AcademicTermDetailImpl` | Jadira `dateTime` | UTC | unchanged | unchanged |
| `UP_EVENT_SESSION.LAST_ACCESSED` | `EventSessionImpl.lastAccessed` | Jadira `dateTime` | UTC | unchanged | unchanged |
| `UP_PORTAL_COOKIES.CREATED`, `EXPIRES`; `UP_PORTLET_COOKIES.EXPIRES` | cookie entities | Jadira `dateTime` | UTC | unchanged | unchanged |
| `UP_QUARTER_DETAIL.QUARTER_START`, `QUARTER_END` | `QuarterDetailImpl` | Jadira `monthDay` (a string) | text | unchanged | unchanged |

No entity maps `Calendar` or a java.time type before this fix. Plain JDBC code
(`RDBMEntityLockStore` on `UP_ENTITY_LOCK.EXPIRATION_TIME`, `RDBMPermissionImpl` on
`UP_PERMISSION.EFFECTIVE`/`EXPIRES`, `RDBMUserLayoutStore` on `UP_USER.LST_CHAN_UPDT_DT`) doesn't
go through Hibernate and keeps storing JVM-local time.

Evidence: two recordings of the America/New_York round-trip golden, one with only the property
and one with the whole fix. The UTC golden is the reference.

- With only the property, `TD_TIME` moved: 00:00 was stored as 05:00. Jadira's `localTime` type
  without a zone binds through Hibernate's own time descriptor, which applies
  `hibernate.jdbc.time_zone`. `databaseZone=jvm` means "no zone" to Jadira, so it doesn't help.
  `JvmZoneLocalTimeType` passes the JVM zone's ID as `databaseZone`, and Jadira then binds with that
  zone's calendar, as 5.17 effectively did.
- After the fix, the New York rows of the four tables equal the UTC rows, the AggrEventsDb and
  RawEventsDb goldens don't change, and the "lossy" lines for the repeated hour are gone.
- Under a UTC JVM every stored value stays the same. The UTC golden loses only the five `class ... :
  java.sql.Timestamp` notes, because `Instant` fields read back as `Instant`.
- Before the fix, `JpaLocalAccountDaoImplTest.aPasswordChangeIsStoredInUtc` failed with
  `expected:<2026-11-01T05:30> but was:<2026-11-01T01:30>`.

Public API: the getters and setters keep their `Date` and `Timestamp` types. `PortletList`'s JSON
getters build a `Timestamp` from the `Instant`, so the REST output doesn't change.

Decisions (coordinator, 2026-10-11): `LOCK_END` joins the list. `JvmZoneLocalTimeType` is
approved. The global property beats per-column converters: a `java.sql.Timestamp` built from a
local time in the JVM zone's spring gap moves by an hour. 6.0 has no in-place upgrade path
(MOD-D11), so there is no in-place command.

### Copy tool

- `--target-profile 6.0` is a new profile with the 5.17 tables and generators (the build writes
  the same `generators.tsv` into both profile directories until the 6.0 schema step).
- `profiles/6.0/from-5.17.rules` applies to a 5.17 to 6.0 copy only. It converts the seven
  columns with `local-time-to-utc zone=${source-zone}`, so a later 6.0 to 6.0 copy never converts
  twice.
- `--source-zone ZONE` gives the zone. Without it, a 5.17 to 6.0 copy whose source holds a value
  in one of those columns stops with exit 2 before writing anything and names each column. With
  it, a copy into a profile that doesn't convert stops the same way.
- The default target profile stays 5.17, because uPortal-start's G-migrate and equivalence jobs
  call the tool without `--source-zone`.
- Tests: `LocalTimeToUtcTransformTest` (table test of the transform) and
  `HsqldbLocalTimeConversionTest`, end to end on HSQLDB in a New York JVM. Its five cases are the
  conversion with the repeated hour, the refusal without the zone, an empty source that needs no
  zone, an unchanged 5.17 copy, and the refusal of a zone without the 6.0 profile.
- Seeded fault: without the `UP_MUTEX.LOCK_END` line in `from-5.17.rules`, the conversion test
  fails with `expected:<2026-11-01T07:00> but was:<2026-11-01T02:00>`.

### Follow-ups

- **uPortal-start side (owner, 2026-10-11):** the end-to-end agent makes it after this branch
  lands. The coordinator hands over the section "Handover: MOD-I141 in uPortal-start" below.
- **6.0 schema step (with Hibernate 7):** `dataInit` and the copy tool write a marker (a row in
  `UP_VERSIONS`), and a 6.0 portal refuses to start on a database without the marker whose
  converted columns hold values. The message gives the copy command. There is no override
  property, since starting would read wrong times.

### Handover: MOD-I141 in uPortal-start

For the end-to-end agent, once this branch is on `ccr-43ba0785-hzlrj5`:

1. Every uPortal-start job that copies a 5.17 database into a 6.0 one passes two options to the
   copy tool (uPortal's `db-migrate`, run by `./gradlew dbMigrate -PsourceDbVendor=...
   -PtargetDbVendor=...`):
   - `--target-profile 6.0`
   - `--source-zone <zone>`, where `<zone>` is the IANA id of the time zone the 5.17 portal's JVM
     ran in when it wrote the source database (its `user.timezone`, else the container's zone).
     In a CI container without a zone setting that is `UTC`.

   Change G-migrate (the 5.17 to 6.0 copy on every vendor) and the migration-equivalence jobs, and
   check the other callers of `dbMigrate` too.
2. Without `--source-zone`, a copy with `--target-profile 6.0` stops with exit 2 and writes
   nothing when any of the seven converted columns holds a value, and lists the columns that do.
   They are `UP_PORTLET_LIFECYCLE.ENTRY_DATE`, `UP_MUTEX.LOCK_START`, `LOCK_UPDATE`,
   `LOCK_END`, `UP_PERSON_DIR.LST_PSWD_CGH_DT`, `UP_PORTLET_LIST.CREATED_ON` and `UPDATED_ON`. A
   `--source-zone` without `--target-profile 6.0` also stops with exit 2.
3. Expected data differences in the equivalence check: those seven columns hold UTC clock time in
   the 6.0 database, shifted from the 5.17 values by the source zone's offset. With
   `--source-zone UTC` they are equal. Every other column copies unchanged.
4. When the harness passes both options everywhere, switch the tool's default target profile
   from 5.17 to 6.0 in uPortal (`MigrationOptions.targetProfile` and the `--target-profile` line
   of `DbMigrateCli.USAGE`). That uPortal commit lands with the uPortal-start change. A job that
   copies between two 5.17 databases then passes `--target-profile 5.17`.
5. The in-place harness legs start intermediate builds on 5.17 databases on purpose and need no
   change: 6.0 has no in-place upgrade path (MOD-D11). The 6.0 schema step adds the marker and the
   start check (see the table above).

### Proposed LOG text

MOD-I141 resolution column:

> **Fixed (2026-10-11, persistence-fixes branch).** The columns listed above, and
> `ClusterMutex.lockEnd` (`UP_MUTEX.LOCK_END`, found by the round trip), are `Instant` with
> `hibernate.jdbc.time_zone=UTC`. `TimeDimensionImpl.time` binds with the JVM zone's calendar
> (`JvmZoneLocalTimeType`). The time dimension stores what it stored before. The copy tool's
> `--target-profile 6.0` converts 5.17's values with `--source-zone`. A value in the repeated
> autumn hour takes the earlier offset (`UPGRADING-6.0.md`). The marker and start refusal belong
> to the 6.0 schema step.

## MOD-I140: analytics in the repeated hour (6.0 schema step)

Decision (coordinator, 2026-10-11): option B, local time plus each row's UTC offset. The column is
a schema change, so it comes with the 6.0 schema step (Hibernate 7, after P7-1). Until then the
schema stays at zero drift against 5.17 (MOD-D33).

Design:

- Each aggregation row stores the UTC offset of its interval start (in minutes: -240 for EDT) in a new column. The column joins the natural id with date, time, interval and group in
  the six aggregation tables: login, concurrent user, tab render, portlet execution, portlet
  layout and search request.
- Interval lookup and closing use (date, time, offset). `BaseAggregationImpl.getDateTime()`
  rebuilds the start with the stored offset instead of the JVM zone's default.
- Reports: the Statistics and ECharts controllers show the offset on sub-day rows only where a
  local time repeats. In New York on 2025-11-02 the hourly report has 25 rows, the two 01:00 rows
  labelled -04:00 and -05:00. The spring day has 23 rows. Day and longer rows keep their local days.
- Copy tool, 5.17 to 6.0: fill the offset from date, time and `--source-zone`. A row in the
  repeated hour gets the earlier offset. That is exact, because 5.17 dropped the second hour. The
  spring gap has no rows.
- Tests then: re-record `aggregation/America_New_York.txt` on purpose, so that the HOUR rows of
  2025-11-02 sum to the DAY row, and seed a fault (offset left out of the natural id).

Now: for each day of each burst, the aggregation golden has a line with the sum of Everyone's
HOUR rows and the count of the DAY row. In New York, 2025-11-02 reads `HOUR rows sum to 6, DAY row counts 8`. Every
other day agrees, and all days agree under UTC. The fix changes that line on purpose.

Proposed LOG text, MOD-I140 resolution column:

> **Design approved (2026-10-11): option B.** Keep local time and store each aggregation row's
> UTC offset, as part of its natural id. Built in the 6.0 schema step. The copy tool fills the
> offset with `--source-zone`. The aggregation golden shows the bug as a per-day line (New York,
> 2025-11-02: HOUR rows 6, DAY row 8).

## G-index R4 (6.0 schema step)

Decision (coordinator, 2026-10-11): an index is a schema change, so it waits for the 6.0 schema
step. The "6.0: R4" allowlist entries in uPortal-start stay until then.

Every foreign key to `UP_UNIQUE_STR`, none with an index today. Check each against the G-index
audit when the step runs:

| Table.column | Proposed `@Index` | Where |
| --- | --- | --- |
| `UP_LOGIN_EVENT_AGGR.UNIQUE_STRINGS_ID` | `IDX_UP_LOGIN_EVENT_USTR` | `LoginAggregationImpl` `@Table(indexes)` |
| `UP_CONCURRENT_USER_AGGR.UNIQUE_STRINGS_ID` | `IDX_UP_CONC_USER_USTR` | `ConcurrentUserAggregationImpl` `@Table(indexes)` |
| `UP_UNIQUE_STR_SEGMENT.UNIQUE_STR_ID` | `IDX_UP_USTR_SEGMENT_USTR` | `UniqueStringsSegment` `@Table(indexes)` |

The third column is the join column of `UniqueStrings`' one-to-many, used whenever segments load or
are deleted. Hibernate 5.2 rejects `@Index` on the one-to-many itself (hibernate-52 note), so it
belongs on the segment's `@Table`. The names follow the tables' existing `IDX_UP_..._*` pattern.
Check them against the G-index note's naming rules, which this session couldn't read.

Expected audit result: no `fk-no-index` finding for these columns, and the two "6.0: R4" entries
removed with the G-index check still passing.

## 6: the pre-delete in `PersistenceUnits.deleteAll`

Removed. `ElementCollectionDeleteIntegrator` deletes NULL element rows with their owner (MOD-I144),
so the goldens clean up with `EntityManager#remove` alone. The round-trip, query and aggregation
goldens pass on HSQLDB. The six-vendor run, Oracle included, decides. If Oracle fails, revert that
commit and record why.

## 7: `TransientPortletEntityDao` NullPointerException

`getPortletEntity` threw for a transient node id ("ctf...") the user's layout no longer has (a page
from before a guest session expired), and for one whose portlet definition is gone.
`TransientPortletEntityDaoTest` reproduced both. Now it returns null, as the JPA DAO does for an
unknown node. The original stack trace from the first P2 runs is in uPortal-start's runs, which
this session can't read. The test reproduces the case from the code.

## Proposed new LOG rows (MOD-I220 to MOD-I229)

| ID | Area | Issue | Resolution |
| --- | --- | --- | --- |
| MOD-I220 | Persistence | **Found by the entity round trip.** `UP_MUTEX.LOCK_END` (`ClusterMutex.lockEnd`) is stored as JVM-local time too, but MOD-I141's list left it out. | **Fixed** with MOD-I141: `Instant` in UTC, converted by the copy tool. |
| MOD-I221 | Persistence | `hibernate.jdbc.time_zone=UTC` alone moves `UP_TIME_DIMENSION.TD_TIME` by the JVM's offset (00:00 stored as 05:00 in New York). Without a zone, Jadira's `localTime` binds through Hibernate's time descriptor. | **Fixed:** `JvmZoneLocalTimeType` binds with the JVM zone's calendar, as before. |
| MOD-I222 | Portlets | `TransientPortletEntityDao.getPortletEntity` threw a NullPointerException for a transient node the layout lacks or whose portlet is gone (seen on `layout.json` with `user=null` in the first P2 runs). | **Fixed:** returns null. |
| MOD-I223 | Portlets | On SQL Server, `[` in a portlet search term starts a LIKE character class. HSQLDB and Oracle reject escaping it with the `!` escape, so it isn't escaped. | **Open**, not covered by a test. Needs a decision: SQL Server-only escaping, or accept. |

