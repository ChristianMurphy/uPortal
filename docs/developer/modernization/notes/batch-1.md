# Landing batch 1 after the merge of 2026-11-08

Branch `ccr-43ba0785-hzlrj5-batch-1` in uPortal and uPortal-start. The owner's earlier session
wrote it on 2026-10-11 and 2026-10-14. It holds the follow-ups of three landed topics: CodeNarc
(MOD-D53), the Background Preference rulings (MOD-D49 to MOD-D51, `wc-background-preference.md`)
and the search guide text (`wc-search.md`, `preference-cache.md`). It wasn't merged before the
other landings, so on 2026-11-08 it was 41 commits behind the uPortal tip and 62 behind the
uPortal-start tip.

## Commits

| Repository | Commit | Change |
| --- | --- | --- |
| uPortal | `656815f78` build: give CodeNarc the compiled classes so its enhanced rules run | Each CodeNarc task gets the compile classpath and the classes of its source set |
| uPortal | `93df49aca` test(webapp): fix the six assertEquals calls CodeNarc flagged | The expected and actual values of six calls in `AnyUnblockedGrantPermissionPolicyTest` |
| uPortal | `d8eb6b791` fix(webcomponents): leave composing keys to the input method | `<up-background-preference>` ignores keys while `isComposing`, uses logical properties and wraps long captions |
| uPortal | `8ca0effc6` style(respondr): place the background preference with logical insets | `regions.scss` uses `inset-inline-end` and `inset-block-start` |
| uPortal | `510140fac`, `4bac07307`, `5feae87a4`, `47a2ce8b6`, `e612af3c3` | Docs. They update the background preference note and the landing state of three notes, and reword the search guide |
| uPortal-start | `a9f879428` test(e2e): check background preference in spanish and french | The tests render the channel for a user in es_ES and for one in fr_FR |
| uPortal-start | `0096b2304` ci: print the exceptions after tomcat's severe lines | `tomcat-log-summary.sh` prints the exception and `Caused by:` lines after each SEVERE line |

## The merge

Merge commits uPortal `a8a2a0f73` (tip `280a638dd`) and uPortal-start `0beeb6a1d` (tip
`09ccc9cbb`). Landing 4 then came in through uPortal `4b5d266ad` (tip `0cf02679f`) and
uPortal-start `e47c4f3de` (tip `9c20dbaf6`). None of the four had a conflict, and
`git show --remerge-diff` of each is empty.

## Checked against what landed since

| Landed change | Effect on this branch |
| --- | --- |
| test-users (MOD-I401): a spec that changes a user's language or layout signs in as a user of its own | The Spanish and French tests saved the language of the shared developer, whom the Reset My Layout language tests sign in as and change too. They now sign in as `ownUser("background")`, see below |
| MOD-I391: one user's language saved from two sessions at once answered 500 | Fixed in uPortal on the tip. With the own user, these tests don't save the developer's language at all |
| Replay masks (MOD-I395, MOD-I396) | Unrelated. The branch changes the Respondr CSS and the component's module. The replay compares the skin CSS through `skin-css-pinned-compilers` and the module's name through `background-preference-web-component-page`. The local replay is below |
| Gradle wrapper retries and the cache fix (MOD-I397, MOD-I398) | uPortal's `build.gradle` changes, so uPortal's first push run has no Gradle cache entry and downloads the distribution, with the new retries. uPortal-start's build files don't change |
| `agent-guide.md` names merge `38fe16ec1` for the lost docs change (retro-1 finding 9) | `wc-search.md` named `1c1b39734b`. It now names `38fe16ec1`. The message of `47a2ce8b6` keeps the wrong merge, since it is pushed |
| MOD-I375 logs the request-level portlet entity cache finding | `wc-background-preference.md` names the row |
| MOD-I192 is fixed (MOD-I350, MOD-I351) | The summary helps any job whose web application fails to start. The job log is the cheapest place to read the cause (guide, section 4) |
| CodeNarc on the tip | No new Groovy file since October. `codenarcMain` and `codenarcTest` scan the same 8 files |

## Changes beyond the merge

* uPortal-start `600593474` test(e2e): give the background preference language tests an own user.
  `own-users.ts` adds `background` (Nina), and the two tests sign in as it. global-setup imports
  it for every worker slot, as it does the other own users. The comment above the tests cites
  MOD-D50, the language decision, where it cited MOD-D49.
* uPortal `f60c75dfb`: `wc-search.md` gives merge `38fe16ec1`.
* uPortal docs: this note, and `wc-background-preference.md` (`20948f634`), whose rulings section
  had the developer and swapped the labels MOD-D49 and MOD-D50.

## Evidence

### uPortal, local

JDK 21 runs Gradle, with the uPortal build of `4b5d266ad` or of `a8a2a0f73` before it.

| Check | Result |
| --- | --- |
| `codenarcMain codenarcTest` | passed. CodeNarc scans 8 files and finds 0 violations, and the log has 0 "unable to resolve class" lines |
| Seeded fault: one `assertEquals` in `testContainsType` swapped back | `codenarcTest` fails: `JUnitAssertEqualsConstantActualValue` at `AnyUnblockedGrantPermissionPolicyTest.groovy:96`. Reverted |
| `:uPortal-webapp:test` | 469 tests, 0 failed (76 skipped). `AnyUnblockedGrantPermissionPolicyTest` 2 of 2 |
| `:uPortal-webcomponents:test` (Vitest, Chromium 1194 through the 1248 symlink tree) | 50 files, 1532 tests passed, coverage thresholds met |
| `spotbugsMain` | passed, no report with a finding |
| The compiled Respondr CSS | `defaultSkin.css` and `defaultSkin-rtl.css` place `up-background-preference` with `inset-inline-end: 12px` and `inset-block-start: 0` |
| `overflow-block` in Chromium 141 (1194) | `CSS.supports('overflow-block', 'auto')` is true |

### uPortal-start, local

uPortal `4b5d266ad` installed as 5.17.10-SNAPSHOT into a private Maven repository, uPortal-start
`e47c4f3de` on HSQLDB, ports 8680, 8605 and 8687, the portal on JDK 11. Chromium 1194, two
workers, `--retries=0`, 11.9 to 12.7 GB of memory available. The deployed module of the
component contains the `isComposing` check, and the deployed skin CSS has the logical insets.

| Run | Result |
| --- | --- |
| `npm run lint` | passed (tsc, type coverage 100%, ESLint without errors) |
| HTTP replay against the 5.17 recording (`replay.mjs replay`, before any test) | 2387 exchanges, 0 unexplained differences. `skin-css-pinned-compilers` applied 12 times, `background-preference-web-component-page` 36 and `-registry` 18 |
| `background-preference.spec.ts`, `--repeat-each=3` | 35 of 36 passed. "the choice belongs to the user" failed once, in a copy that ran next to another copy of the same test. Both sign in as the shared student, and the other copy's `afterEach` chose None. The test signs in as the student on the tip too, and CI runs one copy |
| The language tests of `background-preference`, `search-web-component`, `session-timeout` and `reset-my-layout`, `--repeat-each=10` | 80 of 80 passed |
| The same set with the Spanish and French tests as the branch had them before `600593474`, as the developer | 80 of 80 passed |
| Seeded fault: the two tests without `setUserLocale` | both fail: the element's `locale` is `en-US` where `^es` and `^fr` were expected. The fault was in a copy of the spec, removed afterwards |

The developer version didn't fail here. The change follows the rule of MOD-I401, and doesn't fix
a failure seen on this branch: since MOD-I391 two saves of one language don't answer 500, and the
Reset My Layout language tests set their own session's language before they open a page.

`tomcat-log-summary.sh` on a seeded log (a SEVERE line in `localhost.*.log`, an exception, two
`Caused by:` lines and stack frames between them) printed the SEVERE line, the exception and both
causes without the frames, in the job log section and in the summary, and exited 0. On this
session's real logs (167 SEVERE lines, most of them from Tomcat's shutdown) it printed 300 lines,
the limit. actionlint 1.7.12 and `zizmor --offline` find nothing in either repository. The script
runs only in a job that failed (`if: failure()`), so a green run doesn't run it.

### CI

Push runs of uPortal `4b5d266ad` and uPortal-start `e47c4f3de`. Every uPortal-start run built
uPortal `ccr-43ba0785-hzlrj5-batch-1` at `4b5d266ad` (the `uPortal under test` annotation of its
`Build uPortal` job).

| Repository | Workflow | Run | Result |
| --- | --- | --- | --- |
| uPortal | CI | 42485726268 | passed. CodeNarc scans 8 files with 0 violations, and SpotBugs passes |
| uPortal | DB integration | 42485726273 | passed |
| uPortal | DB copy tool | 42485726167 | passed |
| uPortal | Workflow lint | 42485094969 | passed, on `20948f634`, the head before landing 4 |
| uPortal-start | Integration | 42486906203 | passed. Playwright on HSQLDB 514 passed (job 128753193828), on PostgreSQL 18 514 passed (128753193806), on HSQLDB with CAS 6 passed. Cluster, DB harness on PostgreSQL 18 and G-migrate MariaDB 11.4 to PostgreSQL 18 passed |
| uPortal-start | CI | 42486906083 | passed, with the lint job |
| uPortal-start | HTTP replay | 42486906249 | passed. "Replay against 5.17" ran (job 128753777911), because the merge brought changes to `tests/replay` |
| uPortal-start | Migration equivalence | 42486906367 | passed |
| uPortal-start | G-index | 42486906112 | passed |
| uPortal-start | Workflow lint | 42486906222 | passed |

The October Integration run of this branch, 38672648246 on `0096b2304`, failed on
`user-locale.spec.ts:121` (MOD-I379) and `pages.spec.ts:121` at 390 pixels (MOD-I386). The tip
has the fixes of both, and this run passed without a retry.

## Open items

* The describes of `background-preference.spec.ts` that sign in as the shared student collide with
  their own copies under `--repeat-each` with two workers, as seen above. CI runs one copy, and
  test-users kept the student for this spec because no other spec reads the student's background.
* `up-search.ts` and `up-search-box.ts` still check `event.isComposing` alone, as the background
  preference did before the review. They belong to the search conversion, outside this batch.
* MOD-I375, the request-level portlet entity cache, is still open. This branch doesn't change it.

## Review of 2026-11-08

The oss-review of uPortal `779599f56` and uPortal-start `e47c4f3de` against the tips found these.
Each has a commit after those heads.

| Finding | Commit | Evidence |
| --- | --- | --- |
| B1. `tomcat-log-summary.sh` printed every SEVERE line, read the catalina logs first and stopped at 300 lines in the log and 60 in the summary | uPortal-start `3959acd97` | With 167 plain SEVERE lines in the catalina log the old script left the cause out of the summary, and with 400 out of the job log too. The new script keeps it in both. `tomcat-log-summary.test.sh` (7 cases, in Workflow lint) passes, and 2 of its 7 cases pass on the old script |
| B2. Escape with keyCode 229, which Safari sends at the end of a composition, closed the list | uPortal `e1d0955a8` | The browser test's new keyCode 229 case fails on `event.isComposing` alone and passes with `isComposing()` |
| B3. Without `overflow-block` the list doesn't scroll and its choices spill out of it | uPortal `432df2944` | A local probe in Chromium 1194 with 12 choices: `clientHeight` 515 and `scrollHeight` 548 in each case. Computed `overflow-y` is `auto` with both properties, `auto` with `overflow-y` alone (a browser without `overflow-block`), and `visible` with neither |
| B4. The wrap test's seeded fault no longer held, since `hostStyles` sets `overflow-wrap: anywhere` too (`a608d5efd`) | uPortal `c090f29a7` | The component's own line is gone. With the line removed from `hostStyles` the test fails (2061 px in a 200 px element). `wc-background-preference.md`'s fault table says so |
| B5. RTLCSS already mirrored the old rule | this note | RTLCSS 4.3.0 turns `right: 12px` into `left: 12px`, so `defaultSkin-rtl.css` placed the channel at the same place before `8ca0effc6`. The logical insets help skins that aren't mirrored, such as the Dynamic Respondr Skin the review names |
| B6. `assertEquals(msg, true, ...)` in `testContainsType` | uPortal `29a723f8c` | `assertTrue` and `assertFalse`. The class's 2 tests pass, and `codenarcTest` finds 0 violations in 8 files |
| `g-index.md` cited the branch `-tomcat-causes`, and `test-users.md` didn't list `background` | uPortal-start `21ffca18a` | Docs |

After the review, `:uPortal-webcomponents:test` passed 1533 of 1533 tests on Chromium 1194 with
Gradle on JDK 21, and `uPortal-webapp`'s CodeNarc tasks and `AnyUnblockedGrantPermissionPolicyTest`
passed. In uPortal-start actionlint, `zizmor --offline` and `tomcat-log-summary.test.sh` passed.
The commits don't change Java code, and `spotbugsMain` wasn't run again.

## Next step

None on this branch. The coordinator reviews it against the tip.

## Proposed LOG rows

| ID | Area | Finding | Status |
| --- | --- | --- | --- |
| MOD-I409 | Build | Since the Gradle 9 upgrade (`798d0c098`) uPortal's `build.gradle` gave every CodeNarc task an empty compilation classpath. CodeNarc's enhanced rules (`CloneWithoutCloneable`, `JUnitAssertEqualsConstantActualValue`, `MissingOverrideAnnotation`, `UnsafeImplementationAsMap`) didn't run, and each build printed 571 "unable to resolve class" lines. With the classpath, the rules found six `assertEquals` calls in `AnyUnblockedGrantPermissionPolicyTest` with the expected and actual values swapped | **Fixed** (uPortal `656815f78`, `93df49aca`): each CodeNarc task gets its source set's classes and compile classpath. 8 files, 0 violations, and a swapped call fails `codenarcTest` |
| MOD-I410 | CI | When a web application failed to start, uPortal-start's job log showed only Tomcat's SEVERE line ("Exception sending context initialized event"). The exception and its causes were only in the logs inside the artifact (MOD-I192) | **Fixed** (uPortal-start `0096b2304`, `3959acd97`): `tomcat-log-summary.sh` prints the exception and `Caused by:` lines after a SEVERE line, without stack frames, in the job log and the summary. It reads the localhost logs first and prints a SEVERE line only when an exception follows it, so plain SEVERE lines don't push the cause past its limits. `tomcat-log-summary.test.sh` checks 0, 167 and 400 of them |

Status lines for rows that exist:

* MOD-D50, Background Preference: uPortal-start has tests in Spanish and French (`a9f879428`, with a user of its own since `600593474`).
* MOD-D51, Background Preference: the component ignores keys during composition (`isComposing`), and it places and sizes its parts with logical properties (uPortal `d8eb6b791`, `8ca0effc6`). Long captions wrap.
* MOD-I401: batch 1's Spanish and French Background Preference tests saved the shared developer's language until `600593474`. They sign in as `ownUser("background")` now.
