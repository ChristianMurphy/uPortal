# Handoff: end-to-end migration tests (MOD-D29 items 1, 4, 5 and the flaky-test check)

Branches: `ccr-43ba0785-hzlrj5-tests-e2e` in uPortal (this note and the LOG rows) and in
uPortal-start (the tests). The uPortal fixes are already on the integration branch. Issue block:
MOD-I150 to MOD-I159.

## Landed on the integration branch (uPortal)

| Commit | What |
| --- | --- |
| `d205646` | fix(portlets): `getServerInfo()` reported `Pluto/${pluto.version}` (MOD-I150) |
| `104858e` | fix(portlets): a removed request attribute stayed in `getAttributeNames()` (MOD-I151) |
| `b1c8ec2` | fix(portlets): `getValues(name, def)` returned `{}` for a preference without a value (MOD-I152) |
| `3e4977d` | fix(portlets): events with a payload were never created when the portlet WAR has no JAXB (MOD-I157) |

Each has a unit test that fails on the old code. SpotBugs on the changed modules is clean.

## On the uPortal-start topic branch, not yet on the integration branch

Eleven commits, oldest first:

1. `fix(build)`: `dataImport` works again (MOD-I91).
2. `fix(build)`: the portal shell classpath contains the jars of one uPortal version only.
3. `fix(data)`: the overlay adds NewsReader's missing export script (MOD-I78).
4. `test(db)`: `dataExportNormalize`, `dataExportCompare`, `dataExportRecord` (buildSrc).
5. `test(db)`: `tests/db/equivalence.sh`, the fixture and the 5.17.9 reference.
6. `ci`: `migration-equivalence.yml`, nightly and on demand.
7. `test(portlets)`: `pluto-testsuite.spec.ts` (42 tests).
8. `test(portlets)`: `portlet-modes.spec.ts` (94 portlets).
9. `test`: `failOnFlakyTests` on CI, and `global-setup.ts`, which logs each test user in once.
10. `docs`: `docs/migration-equivalence.md`.
11. `ci(prototype)`: the push trigger on `ccr-*` and the workstream set. Drop it before
    upstreaming.

## Local evidence so far

* A seeded test that fails once and passes on its retry makes the run exit
  1 with `CI=1` and 0 without it.
* A full Playwright run on uPortal 5.17.9: 219 passed. The new specs' failures were in the tests
  themselves and are fixed: duplicate wrappers for portlets that are also in a layout region,
  failed loads from other hosts, and a page left with requests in flight. Two cold-portal flakes
  in the API specs led to `global-setup.ts`.
* The Pluto run found MOD-I156 (public render parameters are not shared) and MOD-I157 (fixed).

## Runs in progress

* Playwright on 5.17.10-SNAPSHOT with the four fixes: the API specs and all 42 Pluto tests passed,
  and 165 of 172 tests passed in all. The 7 failures were config-mode console errors (MOD-I159),
  now named exceptions on the topic branch. CI on the topic branch checks the portlets' other modes.
* In the same slot: `tests/db/equivalence.sh in-place hsqldb`, then three seeded faults on the
  database it leaves. The faults are the MOD-I120 map keys (`'_' || ATTR_NAME` in
  `UP_SS_USER_PREF_LAY_ATTR_VAL`), a changed portlet parameter, and a re-encoded and cut user
  attribute. Each must make `dataExportCompare` fail with a readable diff.
* Resources that portlets load from other hosts fail in the sandbox, such as the retired Google image
  chart API in Feedback Administration, Daily Business Cartoon's image and CKEditor 4's version
  check.
  The modes spec records them as annotations.

## Next steps

1. If the specs pass on 5.17.10-SNAPSHOT, rebase the uPortal-start commits onto the integration
   branch and push as a fast-forward (MOD-D32). Otherwise fix the specs first.
2. Watch `Integration` (Playwright with the new specs) and `Migration equivalence` (three
   in-place vendors and two copies).
3. Run the nightly set once with `workflow_dispatch` (`nightly: true`). Record vendor
   differences in `tests/db/equivalence/vendors/<vendor>.diff` if 5.17 itself differs.
4. Fill in the `VENDOR_RESULTS`, `CI_RESULTS` and `SEEDED_FAULTS` placeholders in
   `docs/migration-equivalence.md`.
5. Add a MOD-D29 status line with run IDs. Mark MOD-I78 and MOD-I91 fixed, and note under MOD-I126
   that the equivalence check covers DATA.

## Overlap with the Pluto in-tree workstream

Its uPortal-start branch has a spec at the same path that pins the 16 checks that failed before
MOD-I150 to MOD-I152. The coordinator chose this workstream's spec. When P3-2 is merged, drop
`521c61b` and `f0cfc1a`. Its uPortal note can cite MOD-I150 as the fix for the shadowing
`pluto-configuration.properties`. `uPortal-rendering/build.gradle` gains
`pluto-container-driver-api` for tests (MOD-I157). With the in-tree Pluto it becomes
`project(':uPortal-pluto:uPortal-pluto-container-driver-api')`.
