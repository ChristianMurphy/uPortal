# Copy tool tests (MOD-D29): handoff note

The work is on the integration branch; `docs/developer/other/DB_COPY_TOOL_TESTING.md`
says how to run each suite, and LOG.md MOD-D29 has the status.

## Done

* Property tests (jqwik): load order, value conversions against the
  verification, table hash and generator formula as unit properties;
  type and generator properties between real servers in
  `uPortal-db-migrate/src/integrationTest`.
* Fault injection (`CopyFaultIT`): lost target (the copy is stopped at a
  known row first, MOD-I165), corrupted rows, plans the target cannot
  take, generators behind the data.
* Product bugs MOD-I160 to MOD-I164 are fixed, each with a test that
  failed before the fix.
* PIT: the copy tool's core and `JpaClusterLockDao`, with thresholds.
* CI: `.github/workflows/db-migrate.yml`, push job and nightly legs.

## Left

* Fill in the results of DB copy tool run 38106391935 (the nightly legs
  run by hand) in MOD-D29, or rerun it if a leg fails.
* The SQL Server `smalldatetime` type keeps minutes only, but the tool
  treats it as whole seconds. Neither 5.17 nor 6.0 uses it, and a copy
  into it fails verification instead of passing.
