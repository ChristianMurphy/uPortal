# The full matrix before a landing (`-ci-prelanding`)

Branch `ccr-43ba0785-hzlrj5-ci-prelanding` in uPortal and uPortal-start. The owner approved the
change on 2026-11-10.

## Why

Landings 8, 9 and 10 each left the integration tip red from a job that a topic push didn't run:

* `JvmTimeZoneCheckTest` on Java 25 (MOD-I429). A topic push ran Java 21 only.
* SQL Server in DB integration (MOD-I430). A topic push ran PostgreSQL and MariaDB only.
* `Query budgets: hsqldb` in uPortal-start's Integration (run 42743207405). A topic push skipped
  the job.

The full matrix ran after the push to the integration branch, so a failure was found on the tip.
It now runs before the push, on a staging branch, and the push itself doesn't repeat it.

## The matrix before this change

Read from the workflows at uPortal `6a9697af39` and uPortal-start `df45e44523`. The minutes are
job times from the runs of 2026-11-09 and 2026-11-10 on the integration branch and on
`-hibernate56`. "Manual" is `workflow_dispatch`. The schedules never fire on the fork, because
its default branch is `master`. The newest runs of a nightly kind are manual ones from 2026-10-11.

uPortal:

| Workflow and job | Topic push | Integration push | Manual only | Minutes |
| --- | --- | --- | --- | --- |
| CI, Linux with Temurin 21 (build, tests, SpotBugs, web components, lint) | yes | yes | | 16 to 17 |
| CI, Linux with Temurin 25 | | yes | | 8 |
| CI, Windows with Temurin 25 | | yes | | 8 |
| CI, macOS with Temurin 25 | | yes | | 8.5 |
| CI, Zulu and Corretto on 21 and 25 | | | with `nightly` | not measured |
| CI, commit messages and prose | yes | yes | | under 1 |
| DB integration, PostgreSQL 18 and MariaDB 12.3 | yes | yes | | 3.4 and 6.2 |
| DB integration, MySQL 9.7, SQL Server 2025, Oracle 23 | | yes | on a topic | 6.2, 8.4 and 7.6 |
| DB integration, canaries (PostgreSQL 19, MySQL 26, MariaDB 13) | | | yes | not measured |
| DB copy tool, PostgreSQL and MariaDB | yes | yes, with MySQL | | 5 to 6 |
| DB copy tool, nightly legs and mutation tests | | | yes | 28 to 107 for the run |
| Workflow lint | when `.github` changes | when `.github` changes | | 1 to 3 |

uPortal-start:

| Workflow and job | Topic push | Integration push | Manual only | Minutes |
| --- | --- | --- | --- | --- |
| CI, Linux with Temurin 11 | yes | yes | | 3 |
| CI, Windows and macOS | | yes | | 6.4 and 4.8 |
| CI, lint, commit messages and prose | yes | yes | | 2 |
| Integration, uPortal build | yes | yes | | 3.7, or seconds when reused |
| Integration, Playwright on HSQLDB, and with CAS | yes | yes | | 17 to 20, and 6 to 8 |
| Integration, Cluster with two nodes | yes | yes | | 10 to 11 |
| Integration, DB harness on PostgreSQL 18 | yes | yes | | 4 to 5 |
| Integration, DB harness on HSQLDB, MySQL, MariaDB, SQL Server, Oracle | | yes | | 4 to 6 each |
| Integration, Playwright on PostgreSQL 18 | yes | yes | | 17 to 21 |
| Integration, Playwright on MariaDB 12.3 | | yes | | 19 |
| Integration, G-migrate from MariaDB 11.4 to PostgreSQL 18 | yes | yes | | 7 |
| Integration, five more G-migrate pairs | | yes | | 7 to 25 each |
| Integration, Query budgets | | yes | | 3.5 to 5 |
| Integration, nightly sets (MySQL Playwright, every G-migrate pair, browsers, load, cluster faults) | | | with `nightly` | not measured |
| G-index, PostgreSQL 18 at 0.01 with the seeded faults | when persistence paths change | yes | | 33 to 40 |
| G-index at 0.05 on PostgreSQL and MariaDB | | | with `nightly` | not measured |
| Migration equivalence, PostgreSQL 18 in place and one copy | yes | yes | | 7 to 10 each |
| Migration equivalence, HSQLDB and MariaDB in place, a second copy | | yes | | 6 to 10 each |
| HTTP replay, rules | yes | yes | | under 1 |
| HTTP replay against 5.17 | when `tests/replay` changes | yes | | 5, plus the build |
| DB upgrade harness (`db-harness.yml`) | | | yes, and on `master` | not measured |

The Cluster job already ran on every push. The brief listed it as missing from topic pushes.

The whole set on the integration branch took these minutes per workflow, from the push to the
last job. The longer times include the wait for runners.

| Workflow | Minutes |
| --- | --- |
| uPortal CI | 14 to 35 |
| uPortal DB integration | 7 to 35 |
| uPortal DB copy tool | 6 to 17 |
| uPortal-start CI | 4 to 17 |
| uPortal-start Integration | 24 to 64 |
| uPortal-start G-index | 39 to 82 |
| uPortal-start Migration equivalence | 14 to 24 |
| uPortal-start HTTP replay | 4 to 18 |

## What changed

### Staging branches

A push to `ccr-43ba0785-hzlrj5-landing-<n>` runs the jobs of the "Integration push" column in
both repositories. The workflows told topic branches apart by the prefix
`ccr-43ba0785-hzlrj5-`. They now also ask that the name doesn't start with
`ccr-43ba0785-hzlrj5-landing-`. `gindex-changes.sh` and the replay's change check got the same
rule, so G-index and the replay always run there.

uPortal-start's staging runs build the uPortal branch of the same name, as topic branches do
(`choose-uportal.sh`, MOD-I307). No uPortal branch of that name means the integration tip, which
is right when only uPortal-start changes.

A newer push to a staging branch cancels the runs of the older one, as on a topic branch.

### No second run on the integration branch

Each workflow got two jobs.

`Staging check` runs first. On a push to the integration branch or to a staging branch it works
out a key for the tree and looks for a marker with that key. When it finds one, the slow jobs
are skipped, and its notice annotation gives the run that passed. On any other branch or event it
doesn't look anything up and the jobs run as before.

`Record the pass for this tree` runs last, and only when every slow job passed. It uploads the
marker: an artifact named `staged-<workflow>-<key>`, kept for 30 days.

The slow jobs are the platform cells of both `CI` workflows, the `db` and `db-full` jobs of DB
integration, the copy tool job, and in uPortal-start's four other workflows the uPortal build
with every job after it. The commit message, prose and lint jobs always run, and so does
Workflow lint when `.github` changes. They are cheap, and they read what the key leaves out.

The key is the SHA-256 of `git ls-tree -r` of the commit: every file's path, mode and blob id.
A workflow of uPortal-start that builds uPortal hashes that key together with the key of the
uPortal commit it chose. Its marker stands for both trees.

A marker counts when a push run of the same workflow file uploaded it, in the same repository,
on the integration branch or a staging branch. A full run on the integration branch writes a
marker too. A docs-only push there, such as a later log entry, then skips the slow jobs as well.

`.github/scripts/staged.sh` holds the logic, the same file in both repositories apart from the
list of workflows. `staged.test.sh` is its table test (33 rows), and Workflow lint runs it.

### The log entry on top

The coordinator adds the landing's log entry after the staging runs. The key leaves out
Markdown files under `docs/developer/modernization` and `.claude/modernization`. A landing that
adds only such files on top of the staged tree keeps its key.

The slow jobs don't read those files. In uPortal's build files and tests they appear in comments only.
The jobs that read them (Prose, and the placeholder check of Workflow lint) are never skipped.
A file of another type in those directories, such as `RecordGviz.java` beside the notes, stays
in the key, because ESLint and other tools could pick it up. `docs/UPGRADING-6.0.md` and the
other docs stay in the key too. A change there runs the matrix again.

### Failed or unclear lookups

* GitHub doesn't answer, or the tree can't be listed: the slow jobs run.
* No uPortal commit could be chosen: the slow jobs run.
* The uPortal branch moved between the check and the build: the jobs test the newer commit, and
  no marker is written, with a warning annotation. Push the staging branch again.
* The `Staging check` job itself fails (checkout, runner): the jobs that need it are skipped and
  the run is red. Re-run it. It can't end green without having tested.
* A marker older than 30 days is gone, and the slow jobs run.

### Limits of the marker

* The key covers files, not the commit. Commits with the same files share a marker. A landing
  relies on that: its commit on the integration branch differs from the staged one, and its
  files don't.
* A job's result can also depend on things outside the tree: a container image behind a tag, a
  dependency from Maven Central, the runner image. The marker says the jobs passed for this tree
  within the last 30 days. Images are pinned by digest, so the exposure is small, and the
  integration push had the same exposure a few minutes after a topic run.
* Anyone who can push to the fork can upload an artifact with a marker's name from a changed
  workflow on a staging branch. The check guards against mistakes, not against the owner's own
  account.
* Workflow lint has a path filter. GitHub doesn't start it for a new branch whose commits
  were all pushed before, so a staging branch that is a fast-forward of a topic doesn't get a
  Workflow lint run. The topic's own run covers the same commit.

### Landing check

`.claude/modernization/land-push.sh` runs `staged.sh landing` before it pushes. That looks for
the marker of every workflow of the repository for `HEAD`, with the same lookup as CI, and stops
when one is missing. For uPortal-start it uses the tip of uPortal's integration branch, so
uPortal goes first. There is no override. A fix for a red tip goes through a staging branch like
any other landing.

`tip-status.sh` already counts a run whose slow jobs were skipped as a pass, because such a run
ends with success. It now prints the `Staging check` notice under such a run, so the reader sees which
run tested the tree. `tip-status.sh ccr-43ba0785-hzlrj5-landing-<n>` lists a staging branch's
runs.

### Topic pushes

| Added to a topic push | Catches | Cost |
| --- | --- | --- |
| CI on Linux with Temurin 25 | MOD-I429 | 8 minutes, one more runner |
| DB integration on SQL Server 2025 (moved from `db-full` to `db`) | MOD-I430 | 8.4 minutes, one more runner |
| Query budgets on HSQLDB | run 42743207405 | 3.5 to 5 minutes, one more runner |

The `Staging check` job adds about 10 seconds and a runner start to every run. The uPortal build
of uPortal-start's workflows now waits for it.

The other jobs wait for the staging branch:

* Windows and macOS in both `CI` workflows
* MySQL and Oracle in DB integration, and MySQL in the copy tool
* the five other database harnesses, the second Playwright database and five G-migrate pairs
* the wider equivalence set
* G-index and the replay, when the topic doesn't touch their paths

### Commit message job on a new branch

Found during the proof runs. On the first push of a new branch the job checked the whole history
and failed in uPortal on five commits from before the prose rules (run 42755635976). A staging
branch would never have shown a green CI run. For a new branch with the integration prefix the
job now checks from the merge base with the integration branch. Both repositories, one commit
each.

## The matrix after this change

| Push to | Runs |
| --- | --- |
| A topic branch | The topic column above, plus Linux with Temurin 25, SQL Server 2025 and the query budgets |
| A staging branch | The integration column above, unless the tree already passed |
| The integration branch | The cheap jobs. The slow jobs only when no marker matches the tree |
| Manual and nightly sets | Unchanged |

## Not on staging branches

The brief asked for everything that is integration-only or manual-only today. The nightly and
manual-only sets stay manual: the canaries, the nightly copy tool legs with their random seed, the
mutation tests, the `nightly` sets of Integration, G-index and Migration equivalence, the Zulu
and Corretto cells, and `db-harness.yml`.

* None of them can turn the tip red today, because no push starts them. The failures behind this
  change were all in the integration push set.
* They last ran on 2026-10-11. Their state on the tip is unknown, so as a landing condition
  they could block every landing on the first day.
* The canaries are allowed to fail, and the copy tool's nightly legs use a random seed.

A manual run on a staging branch still starts them. Whether some of them should join is
the first question below.

## A landing, step by step

1. `tip-status.sh` on the integration branch. On a red tip, only its fix goes in.
2. Review the topics, number their LOG rows and run `check-placeholders.sh`, as before.
3. Replay the topics onto the tip in a worktree of each repository. Up to four reviewed topics
   can go onto one staging branch.
4. Push the result as `ccr-43ba0785-hzlrj5-landing-<n>`, uPortal first, then uPortal-start under
   the same name. Push the uPortal-start branch even when it equals the tip.
5. Wait for the runs. `tip-status.sh ccr-43ba0785-hzlrj5-landing-<n>` lists them.
6. On a failure, fix the topic or drop it from the branch, and push again. The slow jobs run
   again in the repository whose tree changed, and in uPortal-start whenever uPortal's tree
   changed.
7. Add the log entry on top in uPortal (Markdown under `docs/developer/modernization`).
8. `land-push.sh` in uPortal, then in uPortal-start. It refuses a tree without markers.
9. The integration push runs the cheap jobs and skips the rest. `tip-status.sh` shows the tip
   with the staging runs named.

Batching works without more code. When one topic of a batch fails and is dropped, the tree
changes. The slow jobs then run again for the others.

## Evidence

Local checks, all passing on the heads below: actionlint 1.7.12 with ShellCheck 0.11.0, zizmor
1.32.0 `--offline`, `staged.test.sh`, `gindex-changes.test.sh` (26 rows, two new),
`choose-uportal.test.sh`, `find-uportal-build.test.sh`, `check-uportal-downloads.py` and its
test, `check-placeholders.test.sh`, `check-commit-messages.sh`, `vale.sh commits`, `vale.sh docs`
and `check-freeze-store.py`.

Seeded faults in `staged.sh`, each reverted after the test failed:

| Fault | Rows that failed |
| --- | --- |
| A marker from any branch counts | "marker from a topic branch" |
| Every file under `docs` and `.claude` is left out of the key | "a coordinator script", "a file beside the notes that isn't Markdown", "another doc" |
| A failed call to GitHub counts as a found marker | "the repository can't be read" |
| The run that uploaded the marker isn't checked | "marker from a manual run", "marker from another workflow" |

## Proof runs on the fork

Topic heads when the runs started: uPortal `e12546998e`, uPortal-start `a02112a8be`. The staging
branch `ccr-43ba0785-hzlrj5-landing-ci-test` had the same commits.

Topic push (`-ci-prelanding`):

| Run | Result | What it shows |
| --- | --- | --- |
| uPortal CI 42755844918 | passed | Linux with Temurin 21 (11.4 minutes) and 25 (5.8). `Staging check` looked nothing up |
| uPortal DB integration 42755844921 | passed | PostgreSQL, MariaDB and SQL Server 2025 (7.5 minutes) in the `db` job, `db-full` skipped |
| uPortal DB copy tool 42755844942, Workflow lint 42755844810 | passed | Workflow lint ran `staged.test.sh` |
| uPortal-start CI 42755851732, Migration equivalence 42755851595, HTTP replay 42755851696, G-index 42755851814, Workflow lint 42755851855 | passed | The light profile, as before |
| uPortal-start Integration 42755851990 | failed | `Query budgets: hsqldb` ran on a topic push and failed with the tip's counts from before landing 11. Every other job passed. It built uPortal `e12546998e`, which doesn't have the fix |

Staging push (`-landing-ci-test`):

| Run | Result | What it shows |
| --- | --- | --- |
| uPortal CI 42755847932 | passed | Linux with Temurin 21 and 25, Windows and macOS ran. Marker written |
| uPortal DB integration 42755848172 | passed | Five vendors. Marker written |
| uPortal DB copy tool 42755848021 | passed | Marker written |
| uPortal-start CI 42755854777 | passed | Linux, Windows and macOS. Marker written |
| uPortal-start Migration equivalence 42755854723, HTTP replay 42755854692 | passed | The integration sets, built from uPortal's `-landing-ci-test`. Markers written |
| uPortal-start G-index 42755854633 | passed | G-index ran (40 minutes). Marker written |
| uPortal-start Integration 42755854627, first attempt | failed | The full set ran, with six harnesses, three Playwright jobs, six G-migrate pairs, Cluster and Query budgets. Only Query budgets failed, and the marker job was skipped |

Same tree plus a docs-only commit in each repository (`-landing-ci-test-docs`, uPortal
`019fc44593`, uPortal-start `e93bb92505`):

| Run | Result | What it shows |
| --- | --- | --- |
| uPortal CI 42758162065, DB integration 42758162243, DB copy tool 42758161938 | passed | Slow jobs skipped. Each notice gives the staging run. Commit messages and Prose ran. The commit message job passed on a new branch |
| uPortal-start CI 42758167795, Migration equivalence 42758168106, HTTP replay 42758167962 | passed | Slow jobs skipped, with a docs commit in both trees |
| uPortal-start G-index 42758167860 | passed on the second attempt | The first attempt came before the staging G-index had finished. It didn't find a marker, started G-index and was cancelled. Re-run after the marker existed, it skipped |
| uPortal-start Integration 42758168136 | cancelled | No marker, so the full set started |

One-line code change in uPortal (`-landing-ci-test-code`, uPortal `f8d54ce6c2`, uPortal-start
`a02112a8be`):

| Run | Result | What it shows |
| --- | --- | --- |
| uPortal CI 42758165168, DB integration 42758164972, DB copy tool 42758164953 | cancelled | No marker for the new key. The slow jobs started |
| uPortal-start Integration 42758170536, G-index 42758170679, Migration equivalence 42758170746, HTTP replay 42758170685 | cancelled | The key changed with the uPortal commit alone. The uPortal build and the jobs after it started |
| uPortal-start CI 42758170650 | passed | Skipped, as designed: this workflow doesn't build uPortal, and uPortal-start's own tree hadn't changed |

Also checked from a shell: `staged.sh landing` found the three uPortal markers for the topic head
with the note commit on top, and `land-push.sh` stopped with "Not staged" on the code-change
commit, with the push address pointed at a path that doesn't exist. `tip-status.sh` on the
`-docs` branch printed each skipped run with its staging run.

Not shown by a run:

* Integration never wrote a marker, so its skip wasn't seen. After landing 11 fixed the query
  budgets, a second attempt of run 42755854627 built a merge of the topic and the new tip.
  Query budgets and 18 other jobs passed. `Playwright: postgres-18` then hung in
  `npx playwright install` and the attempt was cancelled. Integration's two new jobs are the
  same text as in the three workflows above, with a longer `needs` list.
* A push to the integration branch itself. The lookup treats it like a staging branch, and the
  table test covers that row.
* `land-push.sh` up to the push.

`npx playwright install --with-deps chromium` also hung until the job's time limit in two jobs
of the coordinator's manual Integration run 42757831859 on the integration branch (05:04 and
05:05 UTC). The cause is open, and the proof runs didn't cancel that run.

Proof branches for the owner to delete, in both repositories:
`ccr-43ba0785-hzlrj5-landing-ci-test`, `ccr-43ba0785-hzlrj5-landing-ci-test-docs` and
`ccr-43ba0785-hzlrj5-landing-ci-test-code`.

## Proposed LOG rows

| ID | Area | Finding | Status |
| --- | --- | --- | --- |
| MOD-D57 | CI | A landing passes the full matrix on a staging branch (`ccr-43ba0785-hzlrj5-landing-<n>`) in both repositories before it is pushed, and several reviewed topics can share one staging branch (owner, 2026-11-10). The push to the integration branch skips the slow jobs that passed for the same tree. `land-push.sh` refuses a tree without that pass. | Decided. Prototype only, like MOD-D30 |
| MOD-I440 | CI | The integration push ran the full matrix after the landing, so landings 8 to 10 each left the tip red from a job that topic pushes didn't run (MOD-I429, MOD-I430, MOD-I438). | **Fixed** by the staging branches. Topic pushes also run Java 25 on Linux, SQL Server 2025 and the query budgets now, which closes MOD-I438 |
| MOD-I441 | CI | The skip is keyed by a hash of the tree, in uPortal-start together with the uPortal commit's tree. Markdown files under `docs/developer/modernization` and `.claude/modernization` are left out, so the landing's log entry doesn't repeat the matrix. A full run on the integration branch writes a marker too, so a docs-only push there skips the slow jobs. | Done. Limits in `notes/ci-prelanding.md` |
| MOD-I442 | CI | On the first push of a new branch the commit message job checked the whole history. In uPortal it failed on five commits from before the prose rules, on every new branch (run 42755635976). | **Fixed**: a new branch with the integration prefix is checked from its merge base |
| MOD-I443 | CI | The nightly and manual-only sets don't run on staging branches. The schedules never fire on the fork, and those sets last ran on 2026-10-11. | **Open**, for the owner |
| MOD-I444 | CI | `npx playwright install --with-deps chromium` hung until the job's time limit in three Integration jobs on 2026-11-10 between 05:04 and 05:22 UTC (runs 42757831859 and 42755854627). Other jobs of the same runs installed the browsers in seconds. | **Open**, cause unknown |
| MOD-I445 | CI | Workflow lint doesn't start for a new branch without new commits, because of its path filter. | Open. The topic's run covers the same commit |

MOD-I388 is still open. The `Staging check` notice of an Integration run now gives the key that
covers the uPortal tree, and `staged.sh landing` in uPortal-start tells whether the two tips
passed together. `tip-status.sh` doesn't use that yet.

## Questions for the owner

1. Should a staging branch also run some of the manual-only sets? Candidates by value: the copy
   tool pairs with SQL Server and Oracle on the fixed seed, and the mutation tests. A manual run
   of each on the tip would show their state and cost first.
2. Is one more runner per topic push acceptable for each of Java 25, SQL Server and the query
   budgets? Each can go back to staging only by reverting its commit.
3. `land-push.sh` has no override. Is that right for an emergency such as an expired marker?
   The cost of the strict rule is one more staging run.
4. The first real landing through a staging branch is also the first live test of Integration's
   marker. Is that acceptable, or should one more proof run come first?
5. The proof branches listed above can be deleted.
