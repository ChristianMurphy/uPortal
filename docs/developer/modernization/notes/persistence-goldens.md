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

## Merged

- Run 38109522840 (tip bc71dbaa6) passed on postgres-18, mariadb-12.3,
  mysql-9.7, sqlserver-2025, oracle-23 and the three canaries. CI
  38109515481 passed. The commits are on `ccr-43ba0785-hzlrj5` as
  linear history, without the merge commit of the topic branch.

## Next steps for other workstreams

1. P2 (Hibernate 5.2) rebases onto the goldens and must pass them
   unchanged, or record each difference in LOG.md and re-record on
   HSQLDB with `-Duportal.golden.record=true`.
2. MOD-I144: the round trip asserts on Oracle which entity types
   `EntityManager#remove` can't delete. If 5.2 deletes those rows, that
   assertion fails and `VendorDifferences.leftByRemove` changes with the
   fix.
3. When a phase removes a frozen Joda-Time, Ehcache 2, Criteria or
   Session use, run `MigrationFreezeRulesTest` with
   `-Darchunit.freeze.store.default.allowStoreUpdate=true` and commit the
   smaller store.
