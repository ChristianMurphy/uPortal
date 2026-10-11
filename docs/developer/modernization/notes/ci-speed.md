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

The runner queue of the account had about 25 runs waiting during the work, and the runs of this
branch had not started when this note was written. The table is filled in when they finish.

| Run | ID | Result |
| --- | --- | --- |
| pending | | |

## Findings for the coordinator

* The required check `Static analysis (SpotBugs and FindSecBugs)` no longer exists in uPortal. Its
  failures now show as a failed `Run SpotBugs` step in `ubuntu-latest with Java temurin version 21`.
* uPortal has no `npm ci` step in a workflow. npm runs inside Gradle (the node plugin), so the npm
  cache of item 1 applies to uPortal-start only.
* `check-freeze-store.py` and the other checks of the guide were run against
  `origin/ccr-43ba0785-hzlrj5`.
* My brief calls for manual full runs of uPortal `CI` and uPortal-start `Integration` for the
  before and after table. `agent-guide.md` says not to start them. They are not started, and the
  question is with the coordinator.

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
