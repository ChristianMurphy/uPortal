# Handoff: end-to-end migration tests (MOD-D29 items 1, 4, 5 and the flaky-test check)

Everything is on the integration branch of both repositories. Issue block: MOD-I150 to MOD-I159.
The topic branches `ccr-43ba0785-hzlrj5-tests-e2e` (both repositories) and
`ccr-43ba0785-hzlrj5-tests-e2e-r2` (uPortal-start) are merged and can be deleted.

## uPortal

| Commit | What |
| --- | --- |
| `d205646` | fix(portlets): `getServerInfo()` reported `Pluto/${pluto.version}` (MOD-I150) |
| `104858e` | fix(portlets): a removed request attribute stayed in `getAttributeNames()` (MOD-I151) |
| `b1c8ec2` | fix(portlets): `getValues(name, def)` returned `{}` for a preference without a value (MOD-I152) |
| `3e4977d` | fix(portlets): events with a payload were never created when the portlet WAR has no JAXB (MOD-I157) |

Each has a unit test that fails on the old code. SpotBugs on the changed modules is clean.

## uPortal-start

| Commit | What |
| --- | --- |
| `f7a29cd` | fix(build): `dataImport` works again (MOD-I91) |
| `eb9b119` | fix(build): the portal shell classpath holds the jars of one uPortal version only |
| `210a548` | fix(data): the NewsReader overlay adds the missing export script (MOD-I78) |
| `6d14fb0` | test(db): `dataExportNormalize`, `dataExportCompare`, `dataExportRecord` (buildSrc) |
| `d2d0ce7` | test(db): `tests/db/equivalence.sh`, the fixture and the 5.17.9 reference |
| `ea436bb` | ci: `migration-equivalence.yml`, nightly and on demand |
| `b6e757e` | test(portlets): `pluto-testsuite.spec.ts` (42 tests) |
| `a40dec7` | test(portlets): `portlet-modes.spec.ts` (94 portlets) |
| `a76a0d5` | test: `failOnFlakyTests` on CI, and `global-setup.ts` |
| `63c1a79` | docs: `docs/migration-equivalence.md` |
| `04a1994` | test(db): the MySQL and MariaDB vendor diffs (whole seconds, MOD-I95) |
| `42a0b1c` | ci(prototype): the push trigger on `ccr-*` and the workstream set |

## Evidence

* Migration equivalence, locally in place on HSQLDB (5.17.9, then 5.17.10-SNAPSHOT): 5.17's export
  equals the reference in 264 files, and the build under test's export equals 5.17's in 271 files.
  On the topic branch, Integration run 38116679825 passed Playwright on HSQLDB and PostgreSQL 18
  with local login. Migration equivalence run 38116679805 passed in place on PostgreSQL 18 and
  for the copy from MariaDB 11.4 to PostgreSQL 18. The first runs on the integration branch are
  38119552320 (Integration) and 38119552312 (Migration equivalence). The nightly set runs as
  38119582423.
* Seeded faults, each on a copy of the database that run left, each reverted: the MOD-I120 map keys
  (`'_' || ATTR_NAME` in `UP_SS_USER_PREF_LAY_ATTR_VAL`) changed 12 of 271 files, every
  `externalId` turned into `_externalId`; a changed portlet parameter and a user attribute cut
  to 200 characters changed 1 file each. A test that fails once and passes on its retry makes
  the run exit 1 with `CI=1` and 0 without it.
* Pluto test suite: all 42 tests pass on 5.17.10-SNAPSHOT. Before MOD-I157 the event tests failed
  by name. MOD-I153 and MOD-I156 are named exceptions that fail the spec once fixed.
* Portlet modes: 94 portlets in every declared mode and both window states pass, with the named
  exceptions of MOD-I158 and MOD-I159.
* Flakes the flaky-test check found and that are fixed: logins on a just-started portal took
  over 30 seconds (`global-setup.ts` logs each user in once), waits for slow requests in the modes
  spec, and the modes spec changing student's favorites while `favorites.spec.ts` ran (it uses
  staff now).

## Left open

* MOD-I153, MOD-I154, MOD-I155, MOD-I156, MOD-I158 and MOD-I159 are logged, not fixed.
* Vendor diffs exist for MySQL and MariaDB (whole seconds, MOD-I95). The nightly set (38119582423)
  shows whether SQL Server and Oracle need one; record it as
  `tests/db/equivalence/vendors/<vendor>.diff` if 5.17 itself differs there.
* Drop the `ci(prototype)` commit (`42a0b1c`) before upstreaming.

## Overlap with the Pluto in-tree workstream

Its uPortal-start branch had a spec at the same path that pinned the 16 checks that failed before
MOD-I150 to MOD-I152. The coordinator chose this workstream's spec, so that branch drops its copy.
MOD-I150, MOD-I151, MOD-I152 and MOD-I157 were fixed in uPortal classes, outside Pluto.
`EventProviderImplTest` gets the driver API from
`project(':uPortal-pluto:uPortal-pluto-container-driver-api')` in `uPortal-rendering/build.gradle`.
