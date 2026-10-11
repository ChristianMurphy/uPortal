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
| MOD-I141 timestamps in UTC | half done: mappings and UTC storage committed; the upgrade conversion waits on the coordinator | see `git log` | |
| MOD-I140 analytics repeated hour | design note sent (option B recommended), waiting for the decision | | |
| G-index R4 | not started, needs uPortal-start access and the 6.0 schema-change ruling | | |

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

Waiting on the coordinator (sent 2026-10-11): whether `LOCK_END` joins the list (it moves with
the property either way), the copy tool's 6.0 profile and `--source-zone`, and the in-place
upgrade path.

