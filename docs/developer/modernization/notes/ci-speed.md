# CI speed: handoff note

Branch `ccr-43ba0785-hzlrj5-ci-speed` in uPortal and uPortal-start, built on
`ccr-43ba0785-hzlrj5-gradle-cache` (the Gradle cache change, below) and `-log-diet`. The goal is
fewer runner minutes for every push, with every test that runs today still running somewhere. The
owner approved items 6 and 7 as MOD-D55. Item 8 was added by the coordinator during the work.

## What changed

| Item | Repository | Commit | Change |
| --- | --- | --- | --- |
| 1 | uPortal-start | `ci: cache npm packages and the Playwright browsers` | `setup-node` caches npm. Each workflow that installs Playwright caches `~/.cache/ms-playwright`, keyed by the Playwright version. `--with-deps` stays, because the system libraries aren't in the cache. |
| 2 | uPortal | `ci: run the web component tests and lint-js in one CI cell` | Only Linux with Temurin 21 installs Chromium, runs the Vitest tests and runs the JavaScript lint. The other cells pass `-PskipWebComponentTests`, a new property of the `vpTest` task. |
| 3 | uPortal | `ci: run SpotBugs in the build cell instead of its own job` | SpotBugs runs in that cell after Build and Test, on the compiled classes. The separate job is gone. A failure still shows the findings and uploads `spotbugs-reports`, and now also writes them as an error annotation. |
| 4 | uPortal-start | `ci: plan the database jobs in the job that builds uPortal` | The `plan` jobs of Integration, Migration equivalence, G-index and HTTP replay are the first steps of `Build uPortal`. The job set is the same, with one exception for HTTP replay (below). |
| 5 | uPortal-start | `ci: show the time of each phase of the database scripts` | `harness.sh`, `migrate.sh`, `equivalence.sh` and `gindex.sh` print each phase as a log group with its seconds, then a table, which is also a job summary and a notice annotation. |
| 6 | both | `ci: run Zulu and Corretto only in the nightly CI run` | `CI.yml` has three matrix sets and a `nightly` input (below). |
| 7 | uPortal-start | `ci: run G-index on a topic push only when it changes what it measures` | `gindex-changes.sh` decides, with a table test of 24 pushes. |
| 8 | uPortal-start | `ci: build the uPortal branch of the same name, else the integration one` | `choose-uportal.sh` replaces four copies of the branch loop, with a table test of 12 cases. |
| | both | `docs: list which CI jobs run on each kind of push` | `docs/developer/other/CI_JOBS.md` (uPortal) and `docs/developer/ci-jobs.md` (uPortal-start). |
| | uPortal-start | `ci: report what a run decided as annotations and in the last log lines` | Notice annotations and a last step in `Build uPortal`, because agents can't read job summaries. |

### Item 6, the matrix sets

| Run | uPortal cells | uPortal-start cells |
| --- | --- | --- |
| Push to a workstream branch | Linux, Temurin 21 | Linux, Temurin 11 |
| Other pushes, pull requests, manual runs | Linux Temurin 21 and 25, Windows and macOS Temurin 25 | Linux, Windows and macOS, Temurin 11 |
| Nightly: a manual run with `nightly` ticked | those, plus Linux Zulu and Corretto on 21 and 25 | those, plus Linux Zulu and Corretto on 11 |

* Neither `CI.yml` has a `schedule`, and none was added. A schedule fires only from the default
  branch's workflow file, so on our branches it does nothing. Upstream it would put a daily run on
  the maintainers' runners. The nightly set runs from a manual run with `nightly` ticked, which the
  coordinator's check-in routine dispatches.
* uPortal-start builds with one JDK for Tomcat and the data tools, 11. Its sets differ in platform
  and vendor, not in Java version.
* Check: a script evaluates the matrix expression of each file for seven kinds of run (a workstream
  push, the integration branch, master, a pull request, a manual run on a workstream branch and on
  master, and a manual run with `nightly`) and lists the cells. The results are in the table above.

### Item 4, the one difference from the old job set

`HTTP replay` skips on a workstream push that doesn't change the replay. Its `plan` job had been the
job that decided this, and the build waited for it. Now `Build uPortal` starts for two steps (the
checkout and the decision) and skips the rest. Moving the decision into the `rules` job would keep
the job set exactly, but then the build would still wait for a short job.

### Item 7 on this stack

The check compares the topic branch with `origin/ccr-43ba0785-hzlrj5`. The `-log-diet`,
`-gradle-cache` and `-ci-speed` branches are stacked and not merged yet, so every push to
`-ci-speed` still contains the `tests/db` changes of the earlier branches. The check says "run" for
them. The skip path is covered by the table test and by a local run against the real repositories,
and it shows in Actions once the earlier branches are merged.

## Gradle cache (`-gradle-cache`)

* Commit 1 sets `cache-read-only: false` on `ccr-*` branches, so a topic branch saves and restores
  its own Gradle cache. The basic provider is kept (MOD-I75). It has no cleanup and no restore
  keys, so the "cache cleanup on" part of the request can't be met with it. The enhanced provider
  has both and needs the owner's decision.
* Measured in uPortal run 38150884890 (the first push): the DB copy tool job was the first Linux job
  to finish and saved the entry, 415,964,467 bytes, under
  `setup-java-Linux-x64-gradle-ce7de829863bc76c420c9ec44e2aa8a9815370208cd816825b4ee444c1177cbb`.
  The key is the runner OS plus a hash of the build files, so every Linux job of the repository
  has the same key. GitHub doesn't replace an entry, so the CI build job, which downloads far more, couldn't
  add to it.
* Commit 2 fixes that: one job per key saves, the others only read.

  | Repository | Jobs that save | Everything else |
  | --- | --- | --- |
  | uPortal | the Linux Temurin 21 cell of `CI`, and its Windows and macOS cells | reads only |
  | uPortal-start | the `Playwright: <vendor>` job of `Integration` (it deploys every overlay), `Build uPortal` in `Integration`, and the Windows and macOS cells of `CI` | reads only |

* Size against the 10 GB limit: see the measurements below.

## Evidence

Times are runner seconds: a job's `started_at` to `completed_at` from `list_workflow_jobs`, summed
over a run, with skipped jobs at 0. Before is the push run of `-log-diet` (uPortal `3aee5e1a4`,
uPortal-start `3d49ae6b9`). After is the push run of `-ci-speed` (uPortal `3c82080e7`, uPortal-start
`1ae9222b9`). Both are light pushes on one branch. The matrix changes (items 2 and 6) don't show in
them, because a push to a workstream branch runs one Linux cell before and after. A manual full run wasn't started
(`agent-guide.md`). The first batch run after the landing, against batch 1's full runs, gives that
comparison. The runs after used Gradle and npm entries that earlier pushes of the branch had saved.

### uPortal

| Workflow | Before run | Before | After run | After |
| --- | --- | --- | --- | --- |
| `CI` | 38143056177 | 1570 s | 38160993944 | 1347 s |
| `DB integration` | 38143056196 | 574 s | 38160993925 | 411 s |
| `DB copy tool` | 38143056181 | 312 s | 38160993973 | 288 s |
| `Workflow lint` | 38143056178 | 34 s | 38160993957 | 23 s |
| Total | | 2490 s (41.5 min) | | 2069 s (34.5 min) |

Jobs of `CI`:

| Job | Before | After | What changed |
| --- | --- | --- | --- |
| `ubuntu-latest with Java temurin version 21` | 697 s | 1122 s | SpotBugs runs here (482 s). Chromium 82 s to 61 s, Build and Test 559 s to 515 s |
| `Static analysis (SpotBugs and FindSecBugs)` | 598 s | gone | folded into the cell |
| `Spring 5.3 compile probe` | 247 s | 198 s | restored Gradle entry (the compile step 181 s) |
| `Commit messages`, `Prose (Vale)` | 12 s, 16 s | 12 s, 15 s | |

* **Trade-off of item 3.** SpotBugs in the build cell saves 116 runner seconds (598 s gone, 482 s
  added), but the cell's wall time grows from 697 s to 1122 s, because SpotBugs no longer runs in
  parallel with the build. Most of SpotBugs is the analysis, not the compile: 482 s in the cell
  against 584 s as its own job, which also compiled. The fold saves one runner start and one repeat of the compile.
* **Gradle cache.** `DB integration` on postgres-18, step `Run uPortal-webapp's tests`: 306 s (job
  114480677391) to 146 s (job 114533674302), after it restored the entry that the `CI` build cell
  saved. The first run of this branch (`7ab84bf11`, `CI` 38157439312, `DB integration` 38157439409)
  was cold: `CI` 1508 s, `DB integration` 634 s.

### uPortal-start

| Workflow | Before run | Before | After run | After |
| --- | --- | --- | --- | --- |
| `CI` | 38143061745 | 418 s | 38160995511 | 449 s |
| `HTTP replay` | 38143061767 | 564 s | 38160995486 | 14 s |
| `Migration equivalence` | 38143061737 | 1352 s | 38160995502 | 1147 s |
| `G-index` | 38143061735 | 2020 s | 38160995472 | 2283 s |
| `Integration` | 38143061751 | 3585 s | 38160995495 | 3138 s |
| `Workflow lint` | 38143061748 | 19 s | 38160995453 | 24 s |
| Total | | 7958 s (132.6 min) | | 7055 s (117.6 min) |

* **`HTTP replay`:** the earlier push changed `replay.yml`, so it ran the replay (564 s). The later
  push doesn't change anything the replay uses, and it skips. A skipping push cost 14 s before (the rules
  and plan jobs) and costs 14 s now (5 s for the rules plus 9 s for `Build uPortal`). Without that row, the
  totals are 7394 s and 7041 s.
* **`G-index`:** both runs failed on the same two seq-scan findings for `up_person_attr`
  (`user_dir_id`), so the failure is not from these changes. The job took 1790 s before and 2116 s
  now. I didn't find a cause in the changes. Its workload has a fixed length, and the phase table of the
  job (below) puts 1025 s in the Playwright suite.
* **`Integration`:** `Build uPortal` 223 s to 114 s (restored entry, plan steps included), the
  `DB harness: postgres-18` 377 s to 358 s, `G-migrate` 534 s to 478 s, `Playwright: hsqldb` 1180 s to 1039 s.
* **`Migration equivalence`:** `Build uPortal` 227 s to 160 s, and the 7 s `plan` job is gone.
* **Item 1, npm and browsers.** `Install npm dependencies` took 139 s (job 114494330925) and 114 s
  (job 114497461135) before. After, it took 7 s, 8 s and 10 s in the jobs that started once another
  job had saved the entry (114553883760, 114553883774, 114553883833). It took 89 s in the first job
  (114553883737), 128 s in the harness job that started at the same moment (114553883766) and 138 s in
  `G-index`, which failed and so saved nothing. A failed job doesn't save a cache. The Playwright
  browser cache hit (`playwright-Linux-1.64.0`), but `Install Playwright browsers` still takes 11 s to
  28 s because `--with-deps` installs the system libraries.

### Runs on the integration branch

The landings ran as full pushes of `ccr-43ba0785-hzlrj5`, so these runs are not comparable with the
light pushes above. They are the after side for the comparison with batch 1's full runs.

| Repository and commit | Workflow | Run | Runner seconds | Jobs |
| --- | --- | --- | --- | --- |
| uPortal `399bccebc` | `CI` | 38170373669 | 3270 s | Linux Temurin 21 1202 s (Chromium 88 s, Build and Test 576 s, lint-js 37 s, SpotBugs 482 s), Linux 25 505 s, macOS 25 518 s, Windows 25 748 s, probe 265 s, commits 13 s, prose 19 s |
| uPortal `399bccebc` | `DB integration` | 38170373753 | 1818 s | mariadb-12.3 343 s, sqlserver-2025 420 s, mysql-9.7 346 s, postgres-18 329 s, oracle-23 380 s |
| uPortal `399bccebc` | `DB copy tool` | 38170373594 | 453 s | |
| uPortal `399bccebc` | `Workflow lint` | 38170373585 | 20 s | |
| uPortal `399bccebc` | Total | | 5561 s (92.7 min) | |

In the `CI` run only the Linux Temurin 21 cell installs Chromium and runs the web component tests,
lint-js and SpotBugs (item 2). The other three cells run Build and Test alone.

### The new output, as an agent reads it

* **Annotations** (`gh api repos/ChristianMurphy/uPortal-start/check-runs/<job>/annotations`), job
  `Build uPortal` of `G-index` 114533692470: `uPortal under test` gives `ChristianMurphy/uPortal
  ccr-43ba0785-hzlrj5-ci-speed at 3c82080e7e5e9374312cf2235dbf9db87d8f1fa3: uPortal has a branch
  named ccr-43ba0785-hzlrj5-ci-speed, like this one`. `G-index change check` lists the 14 and 4 files
  that matched, and `G-index runs` the chosen set. The same lines are in the log, about 60 lines from
  its end.
* **Phase times** (item 5), job 114553883766 (`DB harness: postgres-18`): start the database 5 s,
  deploy, dataInit, snapshot and verify 133 s, start the portal 31 s, the API specs 19 s, stop the
  portal 10 s, the LOB report 8 s. Job 114547143098 (`G-index`): deploy and dataInit 204 s, template
  user 83 s, generate and load 252 s, start the portal 61 s, the Playwright suite 1025 s, the driver
  246 s, audit 25 s `FAILED`.
* **G-index filter** (item 7): on this stack it says `run`, because the branch still holds the
  `tests/db` changes of `-log-diet`. The skip path is covered by the 24-row table test, which
  `Workflow lint` ran green (job `actionlint` of run 38160995453, with the 12-row test of item 8).

## Cache size

The saver of the uPortal-start key, `Playwright: postgres-18` (run 38160995495, job 114553883760),
saved 1,208,282,953 bytes. The saver of the uPortal key, `Build uPortal` of `Integration` (run
38157305156, job 114522826645), saved 486,872,376 bytes. One branch of uPortal-start costs about
1.7 GB. The uPortal build cell saved 549,854,370 bytes (run 38157439312, job 114523221602). Against
the limit of 10 GB per repository, that is about 6 branches of uPortal-start and 18 of uPortal, and
GitHub evicts the entry used least recently when the total is over. With about 20 active branches,
uPortal-start's entries are evicted often, and an entry of `ccr-43ba0785-hzlrj5` can be evicted too.
The cache listing API isn't open through the proxy (403), so the real total is not read.

**Contents of the entry.** The entry can't be opened without a workflow change, so I measured a
Gradle home that I built from nothing here: `tomcatInstall tomcatDeploy dbWait dbSetup dataInit
dbSnapshot dbLobReport` and the portlets' `dataInit` on HSQLDB, with the uPortal 5.17.10-SNAPSHOT
build of the branch. It is the same work as the saver job except the database and the Playwright
run. The home was 1,309 MB (the entry is compressed, 1,208 MB).

| Directory | Size | Content |
| --- | --- | --- |
| `caches/modules-2/files-2.1`, 14 `.war` files | 743 MB | the portlets that the overlays deploy: resource-server-webapp (109 MB and 40 MB, two versions), CalendarPortlet 104, SimpleContentPortlet 88, WebProxyPortlet 86, notification-portlet-webapp 79, NewsReaderPortlet 69, FeedbackPortlet 50, Announcements 47, esup-filemanager 26, BookmarksPortlet 25 |
| `caches/modules-2/files-2.1`, other | about 250 MB | 241 jars (170 MB), the Node tarball (53 MB), Gradle plugin files |
| `caches/9.8.1` | 300 MB | `generated-gradle-jars` 185 MB, `transforms` 67 MB, `javaCompile` 43 MB, all rebuilt by Gradle |
| the rest | about 15 MB | metadata and `jars-9` |

The portlet wars are 57% of the home. They come from Maven Central, as do the jars. The
uPortal jars themselves come from `~/.m2`, so they aren't in the entry.

**Options.** I didn't change any workflow. Each option needs the coordinator's decision.

1. **Leave the rebuildable and the large files out of the entry** with `gradle-home-cache-excludes`.
   Excluding `caches/9.8.1/generated-gradle-jars`, `transforms` and `javaCompile` takes out 295 MB
   (23%), and Gradle rebuilds them in a few seconds. Excluding the wars (`caches/modules-2/files-2.1/org.jasig.*`
   and `org.esupportail.*`) takes out 743 MB more. The entry falls from 1.3 GB to about 0.3 GB, about
   0.8 GB per branch with the uPortal key, so about 12 branches fit. Each job that deploys
   overlays then downloads 743 MB from Maven Central again, and Central answered 403 to a job once
   (uPortal run 38139808912), which was a reason for the cache. I have not timed the download.
2. **Save only on the integration branch and on the first run of a branch.** That keeps the
   total small, but a topic branch reads only its own caches and the default branch's, never
   `ccr-43ba0785-hzlrj5`'s. So a topic branch would get nothing from the integration branch's
   entry, and the saving would apply to batch runs only.
3. **One shared key for all branches.** A key shared by every branch has to be saved on the default
   branch (`master`), because only that cache is visible from other branches. That needs a workflow
   in `master`, which is for the maintainers to accept, and `schedule` fires only from there too.
   It is the way to make one entry serve all branches, and it needs an upstream change.
4. **The enhanced provider** (`cache-provider: enhanced`) deduplicates and cleans entries. It is
   under a commercial license, and it was left out for that reason (MOD-I75). The owner decides.

My reading: option 1 for the generated directories costs almost nothing and removes a quarter of
the entry. The wars are the decision, a trade of 743 MB of storage against 743 MB of downloads per
job.

## Findings for the coordinator

* The required check `Static analysis (SpotBugs and FindSecBugs)` no longer exists in uPortal. Its
  failures now show as a failed `Run SpotBugs` step in `ubuntu-latest with Java temurin version 21`.
* SpotBugs in the build cell saves 116 runner seconds and adds 425 s to the cell's wall time
  (697 s to 1122 s). Under a shortage of runner minutes that is the right way round. If wall time
  starts to matter, the separate job comes back with one line.
* uPortal has no `npm ci` step in a workflow. npm runs inside Gradle (the node plugin), so the npm
  cache of item 1 applies to uPortal-start only.
* A failed job doesn't save a cache. `G-index` fails on its audit (the seq-scan findings for
  `up_person_attr`), so it never saves its npm entry and each of its runs starts cold.
* The cache entry of uPortal-start is large (see "Cache size"). The listing of the repository's
  caches is closed through the proxy, so nobody here can read the real total.
* The brief called for manual full runs of uPortal `CI` and uPortal-start `Integration` for the
  before and after table. `agent-guide.md` says not to start them, and the coordinator agreed. The
  full comparison is the first batch run after `-ci-speed` against batch 1's full runs.
* `check-freeze-store.py` and the other checks of the guide were run against
  `origin/ccr-43ba0785-hzlrj5` before each push.

## Proposed LOG rows (MOD-I300 to MOD-I307)

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I300 | CI | Every uPortal-start job that runs Playwright installed the npm packages and a Chromium build from scratch. | `setup-node` caches npm, and `~/.cache/ms-playwright` is cached by Playwright version in the workflows that install it. `--with-deps` stays. uPortal has no `npm ci` step to cache. |
| MOD-I301 | CI | Every cell of uPortal's CI matrix installed Chromium and ran the web component tests and the JavaScript lint, with the same result in each. | Only Linux Temurin 21 does. The other cells pass `-PskipWebComponentTests`. |
| MOD-I302 | CI | The separate SpotBugs job set up Java and Gradle and compiled every module again (9 min 44 s in run 38143056177). | SpotBugs runs in the Linux Temurin 21 cell after Build and Test, on the compiled classes. The required check `Static analysis (SpotBugs and FindSecBugs)` is gone. |
| MOD-I303 | CI | The four `plan` jobs of uPortal-start's database workflows were separate runner starts that the build waited for. | They are the first steps of `Build uPortal`. HTTP replay on a workstream push without replay changes now starts that job for two steps. |
| MOD-I304 | CI | The database scripts run as one workflow step, so the step list showed only their total time. | Each phase is a log group with its seconds. The table is a job summary and a notice annotation. |
| MOD-I305 | CI | A full CI run built Zulu and Corretto cells on every push to the integration branch and on every pull request. | They run only in a manual run with the new `nightly` input ticked. MOD-D55. |
| MOD-I306 | CI | G-index ran on every workstream push, about an hour of a runner each time. | It runs when the push changes persistence, SQL, schema, data files or G-index itself, in uPortal-start or in the uPortal branch of the same name. MOD-D55. |
| MOD-I307 | CI | The uPortal that uPortal-start's workflows built fell back to the branch name with its last `-suffix` dropped. `-tests-e2e-r3` tested an older `-tests-e2e` from before Hibernate 5.2 (run 38147009440). | The order is the same-name branch, the integration branch, master. The job writes repository, ref and commit as a notice annotation and in the last log lines, and the checkout uses that commit. |
