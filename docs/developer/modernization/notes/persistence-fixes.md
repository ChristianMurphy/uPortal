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
| MOD-I191 order tie-breakers | done locally (HSQLDB, full webapp suite) | see `git log` | |
| MOD-I141 timestamps in UTC | waiting on the coordinator: `lockEnd`, the 6.0 copy profile, the in-place path | | |
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

