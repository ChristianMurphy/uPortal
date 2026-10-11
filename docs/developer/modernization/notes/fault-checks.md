# Seeded-fault checks: does each one check its reason?

Branch `ccr-43ba0785-hzlrj5-fault-checks` in uPortal and uPortal-start. The cluster session found a
self-check that accepted any non-zero exit: its fixture files were git-ignored, so the check
crashed with "no such file" and still passed in every CI run. This note lists every check in both
repositories whose job is "this must fail", with whether it checks the expected reason or accepts
any failure. The last sections record what this branch changed. The list was taken on the integration
branch at uPortal-start `d230c6a` and uPortal `2a9f6b0`.

The fixtures these checks read are all tracked. The only ignored path they touch is
`build/g-index/<vendor>/findings.json`, which each G-index run writes and deletes at its start.

## The checks

"Reason" means the check requires a specific message, report line, finding or exit code. "Any"
means any failure passes, a crash included.

| # | Check | Before | Now |
| --- | --- | --- | --- |
| 1 | uPortal-start `g-index.yml`, "Seeded faults must fail the audit" | Any non-zero exit, then reason: it requires the fk-no-index and duplicate-index findings in `findings.json`. A crash doesn't write the file. The check then fails | Unchanged |
| 2 | uPortal-start `integration.yml`, "Seeded fault: the auth spec fails with CAS stopped" | Any: any Playwright failure passed, including no tests found and a global setup that couldn't reach the portal | Reason (fixed) |
| 3 | uPortal-start `tests/replay/evaluate.check.mjs` | Reason, except three cases that checked only `failed` | Reason (fixed) |
| 4 | uPortal-start `RemovedApiScannerTest` | Reason, except the allowlist case, which accepted any `IOException`, a missing file included | Reason (fixed) |
| 5 | uPortal-start `SnapshotComparisonTest` | Reason: specific drift flags | Unchanged |
| 6 | uPortal-start `test_code_page.py` | Reason: `ValueError` matching "both become" | Unchanged |
| 7 | uPortal `CopyFaultIT` (copy tool on real servers) | Mixed. Corruption, generator and killed-session cases check their reason. The NOT NULL, narrow column, unique key and foreign key refusals matched a table or column name that the report prints anyway. The stopped-server case accepted any failure | Reason (fixed) |
| 8 | uPortal `HsqldbFailClosedTest` | Exit code, with the same four weak messages as #7 | Reason (fixed) |
| 9 | uPortal `SpringXmlContextValidatorTest` | Reason: one problem with a specific text | Unchanged |
| 10 | uPortal PIT mutation testing (`db-migrate.yml`, nightly) | Any failure kills a mutant, by PIT's design; the job fails on its thresholds | Unchanged |
| 11 | uPortal-start `pluto-testsuite.spec.ts`, the known bug MOD-I156 | Reason: the bug's exact symptom | Unchanged |
| 12 | uPortal copy-tool unit tests that expect an exception (`GeneratorCatalogTest`, `RuleSetTest`, `DialectTest`, `ValueCodecTest`) | Exception type only | Unchanged: tests of the code, not of a checker |

These checks aren't must-fail checks, and are listed so nobody looks for them. The replay's
self-check against the reference must pass. The coverage checks in `tests/db/*/matrix.mjs` must
pass. The replay's
Playwright run reports failures as warnings. The database canaries may fail by design.
No Playwright `test.fail` exists, and no harness script other than `gindex.sh` has a fault option.
The replay and equivalence "fault legs" are manual evidence tables in their docs.

## What changed

* **CAS (2).** `.github/scripts/check-expected-failure.py` reads Playwright's JSON report. It passes
  only when the run had no errors outside the tests, every failed test is under the spec
  path it's given, and the test it's given failed with an error matching the expected reason. The step first
  checks that CAS refuses connections and the portal answers. Then "Sign In opens the login form
  of the portal's authMode" must fail on `chrome-error://chromewebdata`, the page Chromium shows
  when the redirect to CAS's login is refused.
* **Replay (3).** The entry without `whereReason` must fail with that problem. The CSRF entry
  without an UPGRADING-6.0.md section must leave the change unregistered. The entry that explains
  only some lines must leave the unexplained line as the failure.
* **Removed-API allowlist (4).** The test requires the line-numbered "with a reason" message.
* **Copy tool (7, 8).** Each plan refusal requires its own ERROR line: the NOT NULL column, the
  narrow column, the unique key and the foreign key. `HsqldbFailClosedTest` pins the whole line.
  `CopyFaultIT` pins the vendor-neutral part, because identifiers and row counts can differ by
  vendor. In the stopped-server case the table being copied (`UP_SEQUENCE`) must have the error,
  and the error must mention the lost connection.

## Evidence

Each check was shown to pass for its reason and to fail otherwise, with every seeded change
reverted afterwards.

| Check | Passes | Fails |
| --- | --- | --- |
| CAS (2), local portal with `-PauthMode=cas` and CAS stopped | 5 auth tests failed, all at CAS's login form; the check passed | wrong spec path ("No tests found"); missing report; portal down (global setup refused); another reason expected |
| Replay (3) | 13 tests pass | the whereReason problem text changed: 1 test fails |
| Allowlist (4) | `:buildSrc:test` passes | the refusal message changed: 1 of 5 tests fails |
| HSQLDB refusals (8) | 6 tests pass | expected text that can't match: all 4 refusals fail |
| `CopyFaultIT` (7), PostgreSQL 18 | 15 tests, 2 skipped (vendor-specific) | another reason expected for the stopped server: fails |

## Proposed LOG rows (MOD-I241 to MOD-I242)

* **MOD-I241 Seeded-fault checks that accepted any failure.** The CAS seeded fault, three replay
  rule checks, the removed-API allowlist test, four copy-tool plan refusals and the stopped-server
  copy case passed on any failure, so a crash or a missing file could pass as the fault caught.
  Each now requires its reason, and was shown to fail on a crash and on another reason.
* **MOD-I242 Rule for new checks.** A check that must fail states the expected reason (message,
  report line or exit code) and fails on a crash. A check that only needs a non-zero exit isn't
  accepted.
