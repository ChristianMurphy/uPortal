# Semantic branch names (`-semantic-branches`)

Branch `ccr-43ba0785-hzlrj5-semantic-branches` in uPortal and uPortal-start. The topic still has
an old name, because the workflows on the tip started runs only for `ccr-*` branches.

## The decision

The owner's decision of 2026-11-10: the fork's branches get names that say what they are. The
owner also approved pushes to them.

| Kind | Old name | New name |
| --- | --- | --- |
| Integration | `ccr-43ba0785-hzlrj5` | `feat/uportal-6` |
| Staging | `ccr-43ba0785-hzlrj5-landing-<n>` | `staging/landing-<n>` |
| Topic | `ccr-43ba0785-hzlrj5-<topic>` | `<type>/<topic>`, with `<type>` one of `feat`, `fix`, `ci`, `docs`, `test`, `build`, `refactor`, `chore`, `perf` |
| Archive | `ccr-43ba0785-hzlrj5-archive-2026-11-08` | `archive/2026-11-08` |

This topic is the first step: both sets of names work at once. It doesn't rename, delete or
create any long-lived branch. The coordinator does the cut-over (below) after this topic is
merged, and a later topic removes the old names.

## Places that had the old name

A search of both repositories for `ccr-` and `hzlrj5` found these places that decide something.
The rest of the hits are prose.

uPortal:

* `CI.yml`, `db-integration.yml`, `db-migrate.yml`, `workflow-lint.yml`: the push trigger
  `'ccr-*'`.
* `CI.yml`, `db-integration.yml`, `db-migrate.yml`: `GRADLE_CACHE_READ_ONLY` (a `ccr-*` branch
  saves its Gradle cache) and `cancel-in-progress` (a newer push to a topic or staging branch
  cancels the older run).
* `CI.yml`: the light matrix of a topic push, and the commit range of a new branch (from the
  merge base with `origin/ccr-43ba0785-hzlrj5`).
* `db-integration.yml`: `db-full` is skipped on a topic push. `db-migrate.yml`: the vendor list
  of a topic push.
* `.github/scripts/staged.sh` and its test: which pushes look for a marker, and which branch a
  marker may come from.
* `.github/scripts/tip-status.sh`: the default branch. `.claude/modernization/land-push.sh`: the
  branch it pushes to.

uPortal-start:

* `CI.yml`, `integration.yml`, `g-index.yml`, `migration-equivalence.yml`, `replay.yml`,
  `workflow-lint.yml`: the push trigger. The first five and `db-harness.yml` also have
  `GRADLE_CACHE_READ_ONLY`, and the first five `cancel-in-progress`.
* `CI.yml`: the light matrix, the commit range, and the released-uPortal Playwright job, which
  is skipped on `ccr-*`.
* `integration.yml`: the `workstream` set. `migration-equivalence.yml` and `replay.yml`: the
  `WORKSTREAM` value. The workflows that build uPortal: the step of the Staging check that
  chooses the uPortal commit on the integration branch or a staging branch.
* `.github/scripts/choose-uportal.sh` (MOD-I307, MOD-I388): the uPortal branch of the same name,
  else the integration branch, else master.
* `.github/scripts/gindex-changes.sh` (MOD-I306): which push is a topic push, and the
  integration branch it is compared with, here and in uPortal.
* `.github/scripts/staged.sh`: as in uPortal, plus the tip of uPortal's integration branch for
  `staged.sh landing`.

No Gradle file, Java test or Playwright spec has the name. `check-commit-messages.sh`,
`check-placeholders.sh`, `vale.sh` and `preflight.sh` take a range or a path and don't have it
either.

Prose that gave the old names as the rule: the agent guide, `.claude/modernization/agent-brief.md`
and `README.md`, `docs/developer/other/CI_JOBS.md` and `WORKSTREAMS.md` in uPortal. In
uPortal-start: `docs/developer/ci-jobs.md`, `docs/g-index.md`, `docs/db-upgrade-harness.md`,
`docs/migration-equivalence.md`, and the descriptions in `tests/db/migrate/matrix.json`,
`tests/db/equivalence/matrix.json` and `matrix.mjs`. They now give the new names. `LOG.md` and
the other notes are records and keep the names they were written with. `notes/ci-prelanding.md`
got a pointer to this note.

## What changed

`.github/scripts/branches.sh` is new, and it is the same file in both repositories. It holds the
new and the old names, and `branches.sh kind <branch>` prints `integration`, `staging`, `topic`
or `other`. The other scripts read it instead of having a name of their own.

* **Job conditions.** The first job of each workflow (Staging check) has a new step that runs
  `branches.sh kind` and gives the answer as its `kind` output. The matrix of `CI`, `db-full`,
  the vendor list, the `workstream` set and the Playwright job of uPortal-start's `CI` read
  `needs.staged.outputs.kind`. The job also writes a `Branch kind` notice annotation.
* **Push triggers.** A workflow can't run a script in `on:`. Each workflow lists `'ccr-*'`,
  one `'<type>/**'` line for each type and `'staging/landing-*'`. `branches.test.sh` reads those
  lists and fails when one differs from the types in `branches.sh`.
* **`cancel-in-progress` and `GRADLE_CACHE_READ_ONLY`.** GitHub reads these before any job runs,
  so they can't ask the script either. They take a name with a slash as one of our branches.
  `cancel-in-progress` also leaves out `feat/uportal-6`. That is exact for a push, because the
  trigger lets in only our names. It is looser for a manual run on a branch with another name,
  such as `renovate/x`: such a run now saves a Gradle cache and cancels an older run on the same
  branch. `branches.test.sh` fails when a workflow gives a full branch name anywhere else.
* **Markers.** `staged.sh` counts a marker when the run that uploaded it was on a branch of kind
  `integration` or `staging`, under either name. So the markers of
  `ccr-43ba0785-hzlrj5-landing-12` still count, and markers from `feat/uportal-6` and
  `staging/landing-<n>` count for a push to either integration name.
* **The integration branch to use.** A script that needs one integration branch takes the first
  name that exists, the new one first: `tip-status.sh` without an argument, `land-push.sh`,
  `staged.sh landing`, `choose-uportal.sh` (after the branch of the same name) and
  `gindex-changes.sh`. Before the cut-over that is the old branch, and after it the new one,
  without another change.
* **Commit range of a new branch.** `branches.sh base <branch>` gives the commit from which the
  `Commit messages` job checks a new branch. For a topic or staging branch that is where it left
  the integration branch, as before. For the first push of `feat/uportal-6` it is where the
  branch left `ccr-43ba0785-hzlrj5`. Without that, the cut-over would check the whole history and
  fail on the five old commits that failed run 42755635976.

Look-alikes: `feat/uportal-6-x` is a topic. `staging/other`, `staging/landing-` and `style/x` are
`other` and don't start a push run. `archive/2026-11-08` is `other`. The old archive name counts as a
topic, as it did before.

## The owner's other branches in the fork

uPortal's fork has `fix/permission-set-import`, `fix/structure-id-race`,
`fix/counter-store-read-committed`, `fix/layout-save-overlap`, `fix/skin-elements-copy`,
`fix/logout-session-race`, `test/permission-decision-cache-repro`,
`test/verify-fixes-on-databases`, `build/gradle-8`, `docs/readme-description` and
`refactor/spotless-migration`. uPortal-start's has `build/gradle-8`, `docs/readme-description`,
`chore/switch-to-gradle-lite` and `backup/land-b2-start`. Their names match the new triggers.

A push there still doesn't start any of our runs. For a push, GitHub reads the workflow files
from the pushed commit, not from another branch. Those branches start from upstream `master`,
and their `CI.yml` has `push: branches: [master]` only (checked with `git show
origin/<branch>:.github/workflows/CI.yml` for every one of them). The run history shows the same:
the pushes to `test/verify-fixes-on-databases` started only that branch's own three workflows
(runs 42736532643, 42729426390 and 42726749445) and none of ours. `backup/land-b2-start` has our
older workflows with the `ccr-*` trigger alone, so it starts nothing either.

A topic branch cut from the integration branch has the new workflow files, and its pushes start
the runs.

## Checks before the push

* `branches.test.sh`: 72 rows in uPortal and 74 in uPortal-start (more workflows). They cover
  both sets of names, the look-alikes, which integration branch a checkout and a remote have, the
  commit range, the push triggers and stray names in the workflows.
* Seeded faults in `db-migrate.yml`, reverted afterwards: without the `'perf/**'` trigger,
  the row `push trigger of db-migrate.yml` failed. With `startsWith(github.ref_name,
  'ccr-43ba0785-hzlrj5-')` back in the vendor list, the row `branch names in the workflows
  outside cancel-in-progress` failed and printed the line.
* `staged.test.sh`: 49 rows, 16 of them new. `choose-uportal.test.sh`: 23 rows, 11 new.
  `gindex-changes.test.sh`: 40 rows, 15 new, with a stale old integration branch beside the new
  one.
* actionlint 1.7.12 and zizmor 1.32.0 (`--offline`) without findings in both repositories.
  shellcheck on the changed scripts.
* `vale.sh docs`, `vale.sh commits`, `check-commit-messages.sh`, `check-placeholders.sh` and
  `check-freeze-store.py` pass on the code and docs commits. `check-placeholders.sh` reports the
  proposed rows of this note until the coordinator numbers them.
* Old markers with the new script: at the integration tips (uPortal `7b24794905`, uPortal-start
  `39ca485901`), `staged.sh landing` found the markers of every workflow, from runs on
  `ccr-43ba0785-hzlrj5-landing-12` and `-landing-ci-test`. `staged.sh check` with
  `BRANCH=feat/uportal-6` on the same trees answered `skipped` and gave those runs.
* `tip-status.sh` without an argument chose `ccr-43ba0785-hzlrj5`, and
  `tip-status.sh staging/landing-semantic-proof` listed that branch's runs.

## Proof runs

The topic branch and the two proof branches have the same commits: uPortal `d32551f67a`, uPortal-start `04509db180`. Every
uPortal-start run below built uPortal `d32551f67a` from the uPortal branch of its own name (the
`uPortal under test` notice of each `Build uPortal` job). Each Staging check wrote the kind as a
`Branch kind` notice.

The topic branch `ccr-43ba0785-hzlrj5-semantic-branches` (old name, kind `topic`):

| Repository | Workflow | Run | Result |
| --- | --- | --- | --- |
| uPortal | CI | 42826081005 | passed |
| uPortal | DB integration | 42826081315 | passed |
| uPortal | DB copy tool | 42826081090 | passed, vendors `postgres,mariadb` |
| uPortal | Workflow lint | 42826081034 | passed, with the table tests |
| uPortal-start | CI | 42826113842 | passed |
| uPortal-start | Workflow lint | 42826113747 | passed, with the table tests |
| uPortal-start | HTTP replay | 42826113769 | passed, "Replay against 5.17" ran |
| uPortal-start | Migration equivalence | 42826113985 | passed, set `workstream` |
| uPortal-start | G-index | 42826113777 | passed, built uPortal `ccr-43ba0785-hzlrj5-semantic-branches` |
| uPortal-start | Integration | 42826113950 | red, set `workstream`. One spec passed only on its retry (below). Every other job passed |

Integration run 42826113950 is red from job 129888198852 (`Playwright: hsqldb`): 513 specs
passed, and `tests/ux/smoke/search.spec.ts:146` (type-ahead in the header suggests portlets and
opens one) failed once and passed on its retry. The suggestion `Daily Business Cartoon` didn't
appear within 5 seconds. The job ran while the account's runners were full, and this topic
leaves the specs, the data and the portal's code as they were. The slow `/api/portletList` on HSQLDB runners (MOD-I340) may be
the cause, which this topic didn't look into. The run wasn't started again, because the guide
allows that only for a job that stopped before any test ran.

`ci/semantic-proof` behaved as a topic branch (kind `topic`, and every Staging check said `run:
push on ci/semantic-proof is not a push to the integration branch or a staging branch`):

| Repository | Workflow | Run | What it showed |
| --- | --- | --- | --- |
| uPortal | CI | 42826085211 | the light matrix, Linux with Temurin 21 and 25 only. Commit messages and Prose passed. Cancelled |
| uPortal | DB integration | 42826085179 | passed. PostgreSQL 18, MariaDB 12.3 and SQL Server 2025 ran, and `db-full` was skipped |
| uPortal | DB copy tool | 42826085171 | passed, vendors `postgres,mariadb` |
| uPortal-start | CI | 42826119913 | one Linux cell, the Playwright job skipped. Commit messages, lint and Prose passed. Cancelled |
| uPortal-start | G-index | 42826120095 | built uPortal `ci/semantic-proof`. The change check compared with the integration branch and listed the six G-index files of this topic. Cancelled |
| uPortal-start | HTTP replay | 42826120158 | passed, built uPortal `ci/semantic-proof` |
| uPortal-start | Migration equivalence | 42826120160 | passed, set `workstream`, built uPortal `ci/semantic-proof` |
| uPortal-start | Integration | 42826120277 | set `workstream`. Cancelled |

`staging/landing-semantic-proof` behaved as a staging branch (kind `staging`, and every Staging
check said `run: no earlier full run passed for this tree`):

| Repository | Workflow | Run | What it showed |
| --- | --- | --- | --- |
| uPortal | CI | 42826090801 | the full matrix, where Windows and macOS with Temurin 25 passed beside Linux 25. Cancelled while Linux 21 ran |
| uPortal | DB integration | 42826090807 | passed with MySQL 9.7 and Oracle 23 too, and uploaded the marker |
| uPortal | DB copy tool | 42826090909 | passed, vendors `postgres,mariadb,mysql`, and uploaded the marker |
| uPortal-start | CI | 42826131426 | passed on Linux, Windows and macOS, and uploaded the marker |
| uPortal-start | G-index | 42826131527 | built uPortal `staging/landing-semantic-proof`. `staging/landing-semantic-proof is a staging branch, which always runs G-index`. Cancelled |
| uPortal-start | HTTP replay | 42826131409 | built uPortal `staging/landing-semantic-proof`, `replay=true`. Cancelled |
| uPortal-start | Integration | 42826131301 | set `push`. Cancelled |
| uPortal-start | Migration equivalence | 42826131348 | set `push`. Cancelled |

Markers from the new staging name count. With the topic's tree checked out, `staged.sh find`
gave run 42826090807 for `db-integration` in uPortal and run 42826131426 for `CI` in
uPortal-start, both on `staging/landing-semantic-proof`. `staged.sh check` with
`BRANCH=feat/uportal-6`, and again with `BRANCH=ccr-43ba0785-hzlrj5`, answered `skipped: the
slow jobs passed for this tree in run 42826090807 on staging/landing-semantic-proof`.

`Workflow lint` didn't start on the two proof branches (see Limits).

## The cut-over, for the coordinator

Do this after the topic is on the integration branch and `tip-status.sh` passes.

1. Tell the agents with open topics that the names change. Their branches keep working under the
   old names.
2. Push the new name at the tip, uPortal first:
   `git push origin origin/ccr-43ba0785-hzlrj5:refs/heads/feat/uportal-6` in uPortal, then the
   same in uPortal-start. Push the same commit as the old tip, with nothing on top.
3. Read the first runs on `feat/uportal-6`. The trees are the ones the landing's staging runs
   tested, and in uPortal-start the uPortal commit is the same too. So every Staging check should
   say `skipped` and give the staging run, and the slow jobs don't run. `Commit messages` checks
   an empty range. `Prose`, `lint` and the short jobs run. `Workflow lint` doesn't start, because
   the push doesn't add a commit that changes `.github`, and `tip-status.sh` accepts that. If a Staging
   check says `run` instead, the slow jobs run in full and upload a marker. That costs time and
   doesn't break anything.
4. Run `.github/scripts/tip-status.sh`. It now picks `feat/uportal-6` by itself. So do
   `land-push.sh`, `staged.sh landing`, `choose-uportal.sh` and `gindex-changes.sh`.
5. From then on, land on `feat/uportal-6` and stage on `staging/landing-<n>`. Don't push to
   `ccr-43ba0785-hzlrj5` again. It stays where it was.
6. Ask each agent with an open topic to merge `origin/feat/uportal-6` before its next push. A
   topic that hasn't merged this change still runs the old workflow files. Those compare with the
   old integration branch, and in uPortal-start they build uPortal's old integration branch when
   uPortal has no branch of the topic's name. After the cut-over that branch no longer moves.
7. Dispatch the nightly sets on `feat/uportal-6`.

Steps for the owner:

* Delete the old topic, staging and proof branches when their topics are merged, and
  `ccr-43ba0785-hzlrj5-archive-2026-11-08` after pushing its commit as `archive/2026-11-08`.
  Deleting through the git proxy returns 403, so this is done on GitHub.
* Delete `ccr-43ba0785-hzlrj5` itself last, when no open topic depends on it (step 6) and the
  coordinator session no longer needs it (below).
* Optional: make `feat/uportal-6` the default branch of both forks. Schedules fire only on the
  default branch, so the nightly sets would then start by themselves and the manual dispatch
  stops. The default branch also saves Gradle caches that every other branch can read. Pull
  requests and the fork's landing page would show the 6.0 work instead of upstream `master`.

The follow-up topic, once the old branches are gone: in `branches.sh` remove the `OLD_` names
and their uses, in each workflow the `'ccr-*'` trigger and the `ccr-` parts of
`cancel-in-progress`, `GRADLE_CACHE_READ_ONLY` and the Playwright job's condition, and the rows
with old names in the four table tests. `branches.test.sh` then needs its expected trigger list
without `'ccr-*'`. The header of `branches.sh` lists the same steps.

What in the coordinator's setup may still expect the old name:

* The cloud session's own record gives `ccr-43ba0785-hzlrj5` as its branch in both repositories
  (`get_session`, `outcomes`). Pushes to other branches work, as this topic's pushes show. What
  a replaced container checks out isn't known, so keep that branch until the session ends or
  the owner has seen a fresh container start without it.
* The main clones `/home/user/uPortal` and `/home/user/uPortal-start` have the local branch
  `ccr-43ba0785-hzlrj5` checked out, and it is stale. Agents' worktrees track
  `origin/ccr-43ba0785-hzlrj5`. Use `origin/feat/uportal-6` after a fetch.
* The session's scheduled tasks give the old name in their prompts. All three are
  disabled. `Team check-in: unblock agents and land green work` says to replay onto
  `ccr-43ba0785-hzlrj5` and to dispatch the nightly sets there. Change the prompt before it is
  enabled again.
* The coordinator's own files outside the repositories, such as its scratchpad roster and the
  briefs it has sent, give the old names. The agents that are running now were told to
  merge `origin/ccr-43ba0785-hzlrj5`.
* `gh workflow run ... --ref ccr-43ba0785-hzlrj5` in the coordinator's habits for nightly and
  manual runs.

## Limits

* Between the merge of this topic and the cut-over, the guide and the brief give
  `origin/feat/uportal-6`, which doesn't exist yet. Do the cut-over straight after the landing.
* While both integration branches exist, a script takes the new one. The old one stays at the commit
  it had at the cut-over.
* The first runs on a new branch name start with an empty Gradle cache, because a branch reads
  only its own caches and the default branch's.
* A new branch whose commits are already in the repository doesn't start `Workflow lint`. Its
  `paths` filter found nothing new on the two proof branches, which were pushed after the topic
  branch with the same commit.

## Proposed LOG rows

| ID | Area | Entry |
| --- | --- | --- |
| MOD-D? | CI | The fork's branches get semantic names (owner, 2026-11-10): `feat/uportal-6` for the integration branch, `staging/landing-<n>` for a staging branch, `<type>/<topic>` with a Conventional Commits type for a topic, and `archive/<date>` for an archive. The names are in `.github/scripts/branches.sh` in both repositories. The old `ccr-43ba0785-hzlrj5` names are accepted until the old branches are deleted. |
| MOD-I? | CI | The workflows and scripts had the old branch name in push triggers, job conditions, the staging markers, the commit range, the choice of the uPortal branch and the G-index change check, and a branch with a new name didn't start any run. `branches.sh` now says which kind a branch is, the Staging check job gives that to the other jobs, and `branches.test.sh` checks the push triggers against it. Proof runs are in `notes/semantic-branches.md`. |
| MOD-I? | CI | The first push of a new integration branch name would check every commit message since the scripts were added, and five old ones fail (run 42755635976). `branches.sh base` gives the commit where the new name left the old one. The cut-over then checks an empty range. |
| MOD-I? | Tests | Open: `tests/ux/smoke/search.spec.ts:146` (type-ahead in the header) passed only on its retry in Integration run 42826113950, job 129888198852, on HSQLDB. The suggestion didn't appear within 5 seconds. Possibly the slow `/api/portletList` of MOD-I340. Not looked into. |
| MOD-I? | CI | Open: the cut-over. Push `feat/uportal-6` at the tip in both repositories, uPortal first, move the open topics onto it, then remove the old names in a follow-up topic. The owner deletes the old branches and may make `feat/uportal-6` the default branch. Steps are in `notes/semantic-branches.md`. |

## Branches for the owner to delete

In both repositories: `ci/semantic-proof`, `staging/landing-semantic-proof` and, after the
merge, `ccr-43ba0785-hzlrj5-semantic-branches`.
