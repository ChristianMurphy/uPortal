# Copy tool tests (MOD-D29): handoff note

Branch: `ccr-43ba0785-hzlrj5-dbmig-tests`. The first 17 commits are on the
integration branch (`55defd7e5..6a014728c`). How to run everything:
`docs/developer/other/DB_COPY_TOOL_TESTING.md`.

## Done

* Property tests (jqwik): load order, value conversions against the
  verification, table hash and generator formula as unit properties;
  type and generator properties between real servers in
  `uPortal-db-migrate/src/integrationTest`.
* Fault injection (`CopyFaultIT`): lost target, corrupted rows, plans the
  target cannot take, generators behind the data.
* Product bugs MOD-I160 to MOD-I164 are fixed, each with a test that
  failed before the fix (rows not yet in LOG.md, see below). The SQL Server
  datetime mismatch the properties also found was already fixed as
  MOD-I109.
* CI: `.github/workflows/db-migrate.yml`. Push run 38097849648 passed 45
  tests on PostgreSQL 18, MariaDB 12.3 and MySQL 9.7.
* PIT on the copy tool, unit tests and properties only: 55 % to 65 % of
  all mutants, 94 % of the covered ones. Thresholds: 60 % and 90 %.

## On this branch, not yet on the integration branch

* Tests for the PIT survivors and the PIT scoping (four commits).
* The fault tests stop the copy at `COUNTER_15000` before the fault. That
  fixes the race in `killedTargetSessionsFailTheCopyAndARerunRecovers`
  (run 38099695386). A run of the four lost-target tests, 20 times each on
  MariaDB, was in progress when this note was written.

## Next

1. When the repeat run passes, rebase onto the integration branch (the
   jqwik notice filter, MOD-I179) and push with `ccr-push.sh`.
2. Dispatch `db-migrate.yml` once on the integration branch, for the SQL
   Server and Oracle legs and PIT in CI. Record the run ID in MOD-D28.
3. Run `./gradlew :uPortal-webapp:pitest` (JpaClusterLockDao) and set its
   threshold from the result; fill in the JpaClusterLockDao section of
   the testing doc.
4. Seeded faults for the integration tests (generator formula one lower,
   binary values verified on 64 bytes only) and revert runs for the five
   fixes (patches under the agent's scratch `dbt/seeds/`, rebuilt with
   `git show <fix> -- <main files>`).
5. LOG.md: rows MOD-I160 to MOD-I164 and a MOD-D29 status line, with the
   owner's decision to keep jqwik.
