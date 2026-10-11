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

- Oracle and SQL Server jobs of run 38103140369 are still running. The
  cause of the first Oracle run's slowness is unknown. The commit "time
  out stuck golden test cases with a stack" makes the job log print the
  stuck test and its stack.

## Next steps

1. Dispatch "DB integration" (`db-integration.yml`) on
   `ccr-43ba0785-hzlrj5-goldens` and read the oracle-23 and
   sqlserver-2025 job logs with the GitHub MCP `get_job_logs` tool.
2. Fix what they show, on the topic branch.
3. When all five databases pass, rebase onto `ccr-43ba0785-hzlrj5`, run
   `.github/scripts/check-commit-messages.sh` and
   `.github/scripts/vale.sh commits` on the range, push as a
   fast-forward, delete the topic branch, and add the run IDs to the
   MOD-D29 status in LOG.md.
