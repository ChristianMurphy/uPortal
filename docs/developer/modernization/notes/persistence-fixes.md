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
| MOD-I142 portlet search | done locally (HSQLDB); waiting for the six-vendor run | see `git log` | |
| MOD-I191 order tie-breakers | in progress | | |
| MOD-I141 timestamps in UTC | not started | | |
| MOD-I140 analytics repeated hour | not started | | |
| G-index R4 | not started | | |

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
