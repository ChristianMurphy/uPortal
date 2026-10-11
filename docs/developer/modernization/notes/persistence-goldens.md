# Handoff: persistence, query and analytics goldens (MOD-D29 items 3 and 7)

Branch: `ccr-43ba0785-hzlrj5-goldens`, a topic branch. It is merged into
`ccr-43ba0785-hzlrj5` as a fast-forward once all five databases pass.
Issue IDs: MOD-I140 to MOD-I149 (I140 to I143 are used).

## Done

- `EntityRoundTripGoldenTest`, `DaoQueryGoldenTest`,
  `AggregationGoldenTest` and `MigrationFreezeRulesTest` in
  uPortal-webapp, with goldens recorded on HSQLDB under
  `uPortal-webapp/src/test/resources/golden`. LOG.md has the MOD-D29
  status and MOD-I140 to MOD-I143.
- Local runs: all goldens pass on HSQLDB and PostgreSQL 16. The query
  golden passes on MySQL 9.7 and MariaDB 12.3, and the aggregation golden
  passed on both before the last rebase.
- Seeded faults on HSQLDB, none committed:
  - `@MapKeyType(@Type(type = "string"))` on `JpaTenant.attributes`
    (the MOD-I120 class): the round trip fails on the
    `UP_TENANT_ATTRIBUTES` rows, keys without the `_` prefix.
  - `@Temporal(DATE)` on `PortletLifecycleEntryImpl.date`: the round
    trip fails with 74 (UTC) and 88 (New York) differing lines.
  - A new Joda-Time method in `JpaTenantDao`: `noNewJodaTime` fails and
    names the method.
  - The locale order of `JpaMessageDao.getMessagesByCode` reversed: the
    query golden fails, after commit "pin the order of the two ordered
    message queries".
  - Interval starts and ends computed in UTC in
    `AggregationInterval.determineStart` and `determineEnd`: the New
    York aggregation golden fails with 1,263 differing lines (interval
    bounds, then the aggregate rows); UTC passes, as expected. With only
    `determineStart` changed, the New York case loops in
    `doCloseAggregations` until the 10-minute timeout, which reports that
    stack.
- Manual run 38097192745 (all five databases, tip c2bfe4fdc):
  postgres-18 and mariadb-12.3 passed. mysql-9.7 failed on bag order
  and sqlserver-2025 on best-fit characters; both are fixed in the
  later commits. oracle-23 ran into the 45-minute job limit.

- Manual run 38103140369 (tip c475017f3): postgres-18, mysql-9.7 and
  mariadb-12.3 passed, and the three canaries passed. CI run 38103161766
  (unit tests, SpotBugs, commit messages) passed.

## Half done

- Run 38107283955 (old base): sqlserver-2025, postgres-18, mysql-9.7
  and mariadb-12.3 passed. On oracle-23 the query and aggregation
  goldens passed, and the round trip failed: @OrderBy ties came back in
  another order, and '' values in element collections became NULL rows
  that Hibernate 4.3 doesn't read or delete (MOD-I144), which blocked
  the clean-up. Both are handled in "expect oracle's dropped values and
  order-by ties".
- The topic branch now merges ccr a7e40a329 (186 commits, including
  java.time outside JPA, the Spring cache API and the Pluto copy). All
  goldens pass unchanged on HSQLDB on that base. The aggregation context
  got a Spring CacheManager, and the freeze store was re-recorded: it
  dropped 76 Joda-Time and 92 Ehcache uses and records 12 uses that came
  with those batches.
- Run 38109271584 (tip 44ded6c29) was cancelled before it started, to
  include the MOD-I144 assertion: on Oracle the round trip now asserts
  which entity types `EntityManager#remove` can't delete, before its
  SQL clean-up.

## Next steps

1. Wait for the newest manual run. Read the logs of failed jobs with the
   GitHub MCP `get_job_logs` tool; the CI log prints the full golden
   diff.
2. Fix what they show, on the topic branch. Push first, wait until the
   push runs are listed, then dispatch the manual run. A push run in the
   same concurrency group cancels a queued dispatched run.
3. When all five databases pass, replay the non-merge commits onto
   `ccr-43ba0785-hzlrj5` (MOD-D32), run
   `.github/scripts/check-commit-messages.sh` and
   `.github/scripts/vale.sh commits` on the range, push as a
   fast-forward, and add the run IDs to the MOD-D29 status in LOG.md.
