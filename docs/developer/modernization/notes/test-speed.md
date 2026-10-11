# Test speed: handoff note

Branch `ccr-43ba0785-hzlrj5-test-speed` in uPortal and uPortal-start (MOD-I340 to MOD-I349). The
goal is fewer runner minutes and less wall time for the test runs of both repositories, with every
check that runs today still running. This note gives the phase 1 triage: what each part of the
runs costs now, and a ranked list of speed-ups. I haven't pushed anything yet. Phase 2 starts
with the items the coordinator approves.

The account's runners are the limit. Jobs wait 30 to 120 minutes in the queue, 3 to 10 times their
run time. So the measure that counts is runner minutes, the sum of the job times of a run.

## Measurement

* **CI:** the jobs API (`actions/runs/<id>/jobs`) for the last 15 completed runs of each workflow
  with a pass or a fail, on 2026-10-11, and the four full Integration runs of that day. A job's time
  is `started_at` to `completed_at`. Step times are from the same call. Medians unless said
  otherwise. The phase annotations of the ci-speed scripts gave the parts of the database steps.
* **One CI log in full:** the `Playwright: hsqldb` job 114546465364 (run 38160938037), saved to a
  file and parsed with a script, for the time of every test and every request still in flight.
* **Locally:** uPortal `ccr-43ba0785-hzlrj5` at `7df60017b`, installed into a private Maven
  repository, and uPortal-start `ccr-43ba0785-hzlrj5` with `-ci-speed` merged, on ports 8280, 8205
  and 9087. `portalInit` with `--profile` and an init script (kept outside the repositories) that
  logs each task's start and end. The whole Playwright suite with the JSON reporter, twice. The
  machine is shared, and its load average was 7 to 11 on 4 processors during the runs. Local times
  are slower than CI's, and differences of a minute or less between two local runs mean nothing.
* **uPortal tests:** `./gradlew --no-parallel --continue build -PskipWebComponentTests` locally,
  with the same task log and the JUnit XML reports.

## Runner minutes per push today

Runner minutes per run, medians of 15 runs.

| Workflow | Topic push | Full run on the integration branch |
| --- | --- | --- |
| uPortal-start `Integration` | 65.0 | 176 to 190 (4 runs) |
| uPortal-start `G-index` | 41.7 when it runs (ci-speed now skips it on most topic pushes) | 41 |
| uPortal-start `Migration equivalence` | 20.4 | 44 (push), 102 (manual) |
| uPortal-start `HTTP replay` | 10.6 when it replays, 0.3 when it skips | 11 |
| uPortal-start `CI` | 7.3 | 39 with 5 cells, about 24 with ci-speed's 3 cells |
| uPortal `CI` | 24.3 (the build cell 19.7 with SpotBugs, the Spring 5.3 probe 4.1) | 54.5 |
| uPortal `DB integration` | 12.8 | 30 to 50 |
| uPortal `DB copy tool` | 6.0 | 6.5 |

The jobs of a topic push of uPortal-start `Integration`:

| Job | Minutes | Largest steps |
| --- | --- | --- |
| `Playwright: hsqldb` | 17.1 | tests 606 s, `Initialize portal` 202 s, `npm ci` 137 s, `Start Tomcat` 52 s |
| `Playwright: postgres-18` | 17.1 | the harness step 852 s. In run 38160995495 the suite took 396 s of it and deploy with `dataInit` 173 s |
| `G-migrate: mariadb-11.4 to postgres-18` | 10.8 | fill, copy and verify 499 s |
| `Playwright: hsqldb, CAS` | 8.8 | `Initialize portal` 139 s, `npm ci` 138 s, `Start CAS` 94 s |
| `DB harness: postgres-18` | 6.2 | `dataInit`, snapshot, compare, API specs 225 s |
| `Build uPortal` | 4.2 | `./gradlew --no-parallel install` 240 s |

`npm ci` takes about 137 s in nearly every job. The ci-speed npm cache, now on the integration
branch, brings it to a few seconds, so it isn't on the list below.

## Ranked list

Savings are runner minutes per run unless the row says otherwise. "Topic" is a push to a topic
branch, "full" a run on the integration branch.

| Rank | Item | Repository | Cost now | Expected saving | Risk to the tests | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Run `portlet-modes.spec.ts` in parallel mode | uPortal-start | the file is 581.5 s of the 786 s HSQLDB suite, on one worker | 3 to 5 topic, about 10 full | low, each test is on its own (to prove with repeated runs) | one line, then evidence runs |
| 2 | `/api/portletList` over 3 s on CI HSQLDB | uPortal (and the theme) | 100 renders wait the full 3 s, 300 s, in 9 of 20 runs | about 4.75 per slow HSQLDB job, about 2 per topic push on average | depends on the fix | medium to high |
| 3 | Reuse the uPortal build of the same commit | uPortal-start | 4.2 per build, up to 4 builds per push | 3.5 per reuse, 3.5 to 10 per topic push | low | medium |
| 4 | Start the uPortal overlay first in `portalInit` and the deploys | uPortal-start | the uPortal chain starts at 86 s of 175 s | 1 per full deploy, 5 to 8 topic | low, same tasks in another order | small |
| 5 | Drop `DB harness: <vendor>` where `Playwright: <vendor>` runs | uPortal-start | 6.2 topic, 12.6 full | all of it | medium, see the item | small, needs a decision |
| 6 | `tomcatStart` waits 3 minutes for a portal that isn't deployed | uPortal-start | 182 to 185 s in every `CI` cell | 3 topic, 9 full | low | small |
| 7 | SpotBugs after the tests instead of beside them | uPortal | 482 s after 555 s in the build cell | 4 to 7 topic and full | low to medium | small to medium |
| 8 | Tests in Gradle's parallel mode | uPortal | `--no-parallel`, test tasks 327 s in a row | pending, see the item | medium | small |
| 9 | Gradle cache entry size (coordinator's addition) | both | 1.7 GB per uPortal-start branch, about 6 branches fit | see the section | none to the tests | small |
| 10 | Keep the 5.17 side of the equivalence check | uPortal-start | about 300 s per job, 2 jobs topic, 12 manual | about 10 topic, about 55 manual | medium | high, deferred |
| 11 | Fixed waits in `announcements.spec.ts` and `visual-resource-server.spec.ts` | uPortal-start | 5 s per suite run | under 1 | none | small |

### 1. `portlet-modes.spec.ts` runs on one worker

The suite runs with Playwright's default of 2 workers. Files run in parallel, and the tests of a
file run one after another. `portlet-modes.spec.ts` renders every bundled portlet in each mode and
window state, 94 tests in one `describe`.

* CI (job 114546465364): the suite took 786 s. `portlet-modes.spec.ts` took 581.5 s, and every other
  file together 538.5 s. Playwright printed `Slow test file: tests/ux/portlets/portlet-modes.spec.ts
  (9.7m)`. The other worker finished its files and then waited.
* Locally: 487 s of a 779 s suite, `Slow test file ... (8.1m)`.
* I'd add `test.describe.configure({ mode: "parallel" })` to that `describe`. Each test opens its
  own page and logs in itself to render one portlet. A test doesn't depend on what another one
  does. The spec already renders as staff, not student, to stay out of `favorites.spec.ts`'s
  way.
* Expected: with both workers sharing the 1,120 s of tests, the HSQLDB suite takes about 560 s
  instead of 786 s (3.8 minutes less). On a run without item 2's waits, about 1.5 minutes less.
  The PostgreSQL 18 suite (396 s) gains about a minute. The G-index workload runs the same suite
  (1,025 s in run 38160995472) and gains more.
* The local check didn't settle it. With the change the suite took 821 s against 779 s, at a load
  average of 10, and two `web-proxy.spec.ts` tests that fail here (example.com is blocked,
  MOD-I55) restarted their worker six times. CI runs on my branch decide, three runs at least, with
  `failOnFlakyTests` on as always.
* A third worker is a second change to try in CI. It isn't measured.

### 2. `/api/portletList` takes over 3 seconds in CI on HSQLDB

`portlet-modes.spec.ts` waits up to 3 seconds after each render for the page's requests to the
portal, then notes the ones still in flight and goes on (`settle()`). In job 114546465364, 100
renders hit the 3 seconds, and every one of them was waiting for `/api/portletList`. That is 300 s
of fixed waits. The renders were those of staff and of guest, never of admin.

* The `Run Playwright tests` step on HSQLDB has two groups of times: 413 to 606 s in 11 runs
  (median 504 s) and 636 to 832 s in 9 runs (median 789 s). The 285 s between the medians match
  about 95 of those waits.
* Locally, no render waited, and `/api/portletList` answered in 1.3 s the first time in a session
  and in 0.2 s after that.
* PostgreSQL 18 runs the whole suite in 396 s, so its renders don't wait either.
* The handler, `ChannelListController.listChannels`, builds the whole portlet registry for the user
  with a permission check for each portlet. Respondr's layout preferences script asks for it on
  every page an authenticated user opens. So this is a cost of every page view in production as
  well.
* Next step: find what makes the call slow on CI runners (time the call on the server, from the
  Tomcat access log, printed at the end of the job log), then make the call cheaper, or fetch the
  registry when the user opens the customize panel. Either one is a uPortal change. The spec keeps
  its 3-second wait.
* Item 1 halves the effect of these waits, because they then spread over both workers.

### 3. Several builds of the same uPortal commit per push

`Integration`, `Migration equivalence`, `G-index` and `HTTP replay` each start with a `Build uPortal`
job that builds the same uPortal commit (`./gradlew --no-parallel install`, 4.2 minutes).

* In 10 pushes with more than one build, the builds of one push started 2 to 90 minutes apart, and
  only 1 or 2 of them overlapped. The first build was nearly always finished when the next started.
* Each `Build uPortal` would upload its `~/.m2` artifact under a name with the uPortal commit.
  Before building, it would look for such an artifact from an earlier run of this repository's own
  pushes or manual runs. A job that finds one passes that build on. The job still prints which
  commit it tested.
* An artifact is visible to every branch of the repository. Branches that build the same commit,
  such as the integration branch tip, share it, and artifacts don't count against the 10 GB cache
  limit (item 9).
* Expected: about 30 s for a job that finds the build, 3.5 minutes less each time. With 2 to 4
  builds per push, 3.5 to 10 runner minutes per push. Each downstream job also starts its queue
  wait 3.5 minutes earlier.
* Dropping `--no-parallel` from the build is a second change, for the builds that don't find one.
  I haven't measured it.

### 4. The uPortal overlay is built last in `portalInit`

The task log of `portalInit` (`par2` run) shows the overlays built roughly in the order of their
project names. The uPortal overlay comes near the end, and its chain is the longest: `war` 12 s,
`plutoAssemble` 11.5 s, `removedApiScan` 10.7 s, `tomcatDeploy` 9.5 s, `dataInit` 33.4 s. It
started at 86 s and ended `portalInit` at 175 s. From 120 s on, only that chain ran.

* I'd order the tasks so that the uPortal overlay's chain starts first, and the portlets fill the
  other workers.
* Expected: `portalInit` about 100 to 110 s instead of 175 s locally, about a minute less on each
  full deploy. A topic push has 6 to 8 of them (`Playwright: hsqldb`, the CAS job, `Playwright:
  postgres-18`, G-migrate's source and target, `HTTP replay`, `G-index`, the equivalence deploys).
  To measure with the same task log before and after.
* Where it doesn't help: `DB harness` deploys the uPortal overlay alone.
* Adopters' `portalInit` gets faster too.

### 5. `DB harness` for a vendor that `Playwright` also runs

`tests/db/harness.sh --portal` (the `DB harness` job) and `--full-suite` (the `Playwright: <vendor>`
job) run the same `dbWait dbSetup :overlays:uPortal:dataInit dbSnapshot dbLobReport dbVerify`, the
same `vacuumlo` dry run and the same LOB report after the portal. `--portal` then runs the API specs
with the uPortal overlay alone. `--full-suite` deploys every overlay and runs the whole suite, which
includes the API specs.

* On a topic push both jobs run for PostgreSQL 18, and on a full run for PostgreSQL 18 and MariaDB
  12.3 (MySQL 9.7 too at night).
* Dropping `DB harness` for those vendors saves 6.2 runner minutes per topic push and 12.6 per full
  run. The other vendors keep it.
* What changes: the API specs no longer run against a portal without the bundled portlets, and the
  job named `DB harness: postgres-18` goes away. This needs the coordinator's decision.

### 6. `tomcatStart` in uPortal-start's `CI` waits 3 minutes for nothing

The `CI` job runs `tomcatInstall`, then `tomcatStart`, without deploying uPortal. `tomcatStart` waits
with Ant's `waitfor` for `/uPortal` to answer below 400, and `waitfor` gives up after its default 3
minutes without failing. The step took 181 to 185 s in 14 of 15 topic runs and in each of the 5
cells of full run 38145142415, and then the task logged "local uPortal server is live".

* Tomcat opens its HTTP connector after it has deployed every webapp. So any answer means Tomcat
  has finished, and `/uPortal`'s status then says whether uPortal started.
* `tomcatStart` would wait for any answer, then log the status of `/uPortal`. The jobs that start a portal
  still wait for 200 or 302 on their own, as now.
* Saving: 3 minutes per cell, 3 per topic push and 9 per full run.

### 7. SpotBugs runs after the tests in uPortal's build cell

ci-speed moved SpotBugs into the Linux Temurin 21 cell (MOD-I302). It runs in a second Gradle call
after `Build and Test`. The cell takes 555 s for the build, then 482 s for SpotBugs. The SpotBugs tasks already run 4 at
a time.

* I'd run `spotbugsMain` in the same Gradle call as `build`. The SpotBugs tasks then run while the
  tests run, and the findings step still reads the same reports.
* Expected: the cell from about 19.7 to 13 to 15 minutes. To measure in CI.

### 8. uPortal's tests run one task at a time

`Build and Test` runs `--no-parallel`. Locally its test tasks took 327 s in a row: `uPortal-webapp`
149 s, `uPortal-db-migrate` 40 s, and about 45 modules of 3 to 13 s each, mostly JVM and Spring
start-up (the classes themselves sum to 272 s).

* The slowest classes: `AggregationGoldenTest` 35.3 s, `ValueCodecProperties` 26.7 s,
  `JpaConcurrentUserAggregationDaoTest` 10.9 s, `EntityRoundTripGoldenTest` 8.5 s,
  `PersistenceUnitDdlTest` 7.9 s, `JpaClusterLockDaoSnapshotIsolationTest` 7.9 s,
  `MigrationFreezeRulesTest` 7.6 s, `HsqldbCopyTest` 7.1 s.
* With `--parallel` here the same test tasks took 408 s (683 s in all) at a load average of 10.
  Each task ran about twice as slow as in the sequential run, and `uPortal-webapp:test` started
  85 s in, behind smaller tasks. The local result tells nothing. On a 4-processor runner of its own
  the floor is that task, about 150 s against 327 s in a row, so up to 3 minutes per cell. To
  measure in CI.
* `maxParallelForks` for `uPortal-webapp` would split its 149 s, at the cost of a second Spring
  context per fork. I haven't measured it.

### 9. The Gradle cache entry size

The ci-speed note ("Cache size") measured a uPortal-start entry of 1.2 GB plus 0.49 GB for the
uPortal key, about 1.7 GB per branch. With the 10 GB limit, about 6 of some 20 active branches fit.
GitHub evicts the entry used least recently, so a branch's next push often finds nothing. As a rough
guide, when pushes spread evenly over the branches, the share of pushes that find their entry is
about the number of branches that fit divided by 20: about 30% today.

A hit saved 55 s on `Build uPortal` and 45 s on the copy tool (gradle-cache note). The uPortal-start jobs download about 1 GB without a cache. I haven't timed that.

| Option | Entry per branch | Branches that fit | Cost | Notes |
| --- | --- | --- | --- | --- |
| a. Leave out `generated-gradle-jars`, `transforms`, `javaCompile` | 1.4 GB | about 7 | Gradle rebuilds them in each job, a few seconds (not timed by me) | no risk |
| b. Leave out the portlet WARs too | about 0.8 GB | about 12 | each job that deploys every overlay downloads 743 MB from Maven Central (5 to 8 jobs per push, not timed). Central refused a job once | more downloads, more exposure to 403 and 429 |
| c. Leave out the uPortal key, after item 3 | 1.2 GB, 0.9 GB with a | 8, 11 with a | a `Build uPortal` that finds no earlier build loses its 55 s | needs item 3 |
| d. Save on the integration branch only | small | | a topic branch reads only its own entries and the default branch's, so topic pushes gain nothing | not useful here |
| e. One shared key | one entry | all | needs a workflow on `master` that saves it | upstream decision |
| f. The enhanced provider | smaller | more | a commercial license (MOD-I75) | owner's decision |

My order: a now and c with item 3. Option b waits until a CI job has timed the WAR download.

### 10. The 5.17 side of the equivalence check

Each `Migration equivalence` job first builds a 5.17 database: deploy and `dataInit` (127 to
156 s), the fixture (30 s), a start (40 s) and two exports (65 s). Those depend on the released
5.17.9, the fixture, the data files and the pinned image, not on the change under test. A dump of
that database per vendor, kept with those inputs as the key, would save about 5 minutes per job.
Restoring a dump on six vendors faithfully is real work, and the second export, which checks that
5.17's export is stable, would run only when the key changes. Deferred.

### 11. Fixed waits in specs

`announcements.spec.ts` waits 2 s for TinyMCE and `visual-resource-server.spec.ts` 3 s for the news
feed. Both can wait for the thing itself. `pluto-testsuite.spec.ts` waits 6 s on purpose, because
the session timeout is what that test measures. Less than a minute per push. Low priority.

## Ideas checked and left out

* **A database snapshot instead of `dataInit` for Playwright on HSQLDB:** on a topic push that job
  is the only `dataInit` on HSQLDB, so the check would go. Also the key would include the uPortal
  commit, which changes with nearly every push. The import itself is short: the quickstart entities
  took 4.8 s in all. The rest of `dataInit` is JVM and Spring start-up and the schema.
* **JVM flags for Tomcat:** garbage collection took 6.5 s in the first five minutes of a suite run.
  Tomcat already deploys the webapps in parallel (`startStopThreads="0"`). uPortal's own start is
  48.5 s of the 59.8 s.
* **Sharding Playwright over more jobs:** each job pays 5 to 7 minutes of set-up (Java, Gradle,
  `portalInit`, Tomcat), so shards cost more runner minutes than they save in wall time.
* **The CAS job inside the HSQLDB job:** saves about 4 runner minutes but adds about 3 minutes to
  the longest job of the run. I'd leave it for later.
* **`removedApiScan` once per push:** the scans run one at a time (MOD-D9 heap limit), 87 s in all,
  but after item 4 only the uPortal overlay's scan is on the critical path.

## Proposed LOG rows (MOD-I340 to MOD-I349)

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I340 | Tests | `portlet-modes.spec.ts` runs its 94 tests on one worker, 581.5 s of the 786 s HSQLDB suite in job 114546465364. | Phase 2, item 1. |
| MOD-I341 | Portal | On CI runners with HSQLDB, `/api/portletList` took over 3 s on 100 renders of staff and guest in job 114546465364, and on 9 of 20 runs. Locally it takes 0.2 to 1.3 s. Respondr asks for it on every page. | Phase 2, item 2. |
| MOD-I342 | CI | Four uPortal-start workflows build the same uPortal commit on each push, 4.2 minutes each. | Phase 2, item 3. |
| MOD-I343 | Build | `portalInit` builds the uPortal overlay last, so its 89 s chain ends the task at 175 s. | Phase 2, item 4. |
| MOD-I344 | CI | `tomcatStart` waited Ant's default 3 minutes in every uPortal-start `CI` cell, where uPortal isn't deployed, and then logged that uPortal was live. | Phase 2, item 6. |
