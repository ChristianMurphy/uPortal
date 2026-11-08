# retro-1-fixes: review fixes for the retrospective 1 branches

The oss-review of the unlanded retro-1 branches (uPortal `e189ef60b`, uPortal-start `414de84b0`)
found 13 problems. This branch, `ccr-43ba0785-hzlrj5-retro-1-fixes` in both repositories, starts
at those heads and fixes them, so that the coordinator can merge both branches together once the
integration tip is green. The retro-1 branches stay as they are.

## Status

Findings 1 to 13 each have a commit, and the re-review found all of them fixed. A follow-up commit
narrows the Workflow lint exception in `tip-status.sh` (section 1). The proposed LOG row at the end
answers the second question the coordinator decided.

These commits leave the runtime code as it is. The uPortal build differs from retro-1 only by a
comment in `build.gradle`. In uPortal-start they change scripts, a Workflow lint step and docs.

Push runs of the first heads, uPortal `425884690` and uPortal-start `73281b071`. The uPortal-start
runs built uPortal `425884690`.

| Repository | Workflow | Run | Result |
| --- | --- | --- | --- |
| uPortal | CI | 42458390231 | passed |
| uPortal | DB integration | 42458390289 | passed |
| uPortal | DB copy tool | 42458390273 | passed |
| uPortal | Workflow lint | 42458390356 | passed, with the placeholder test: 17 rows, 0 failed |
| uPortal-start | CI | 42458398199 | passed, with the lint job |
| uPortal-start | Migration equivalence | 42458398228 | passed |
| uPortal-start | G-index | 42458398208 | passed. The change filter skipped the G-index job |
| uPortal-start | Workflow lint | 42458398439 | passed, with the downloads check and the tests of it and of the placeholder check |
| uPortal-start | HTTP replay | 42458398153 | **failed**: the replay's date mask. The `-replay-date` branch fixes it |
| uPortal-start | Integration | 42458398202 | **failed**, see below |

* Integration's Playwright job on HSQLDB (128662855111) failed because `reset-my-layout.spec.ts:289`
  and `user-locale.spec.ts:121` passed only on a retry.
* Integration's Cluster job (128662855202) failed `permission-session`. On node B, a session that
  was denied the portlet before the grant was still denied 30 s after it, and 120 s after that.
  Another agent is investigating it.

| Finding | Repository | Fix | Evidence |
| --- | --- | --- | --- |
| 1. `tip-status.sh` passes a tip without a passing run of its own | uPortal | Count only runs of the tip, except for Workflow lint | Fake `gh`, section 1 |
| 2. `preflight.sh` can't pass on the prototype machine | uPortal | Ports from `/proc/net/tcp`, JDKs by major version | Section 2 |
| 3. `check-placeholders.sh` can't tell an error from a result | both | Exit 2 on errors, start at the top | Test, section 3 |
| 4. Section 11's call blames the topic | uPortal | Merge base and `origin/` refs | Test rows, section 3 |
| 5. No test for `check-placeholders.sh` | both | `check-placeholders.test.sh`, run by Workflow lint | Section 3 |
| 6. `-Duser.timezone` with `--rerun` tests the default zone | uPortal | The guide's text, from Gradle runs | Section 4 |
| 7. The integration run of landing 1 isn't recorded | uPortal | LOG entry and MOD-I386 | Section 5 |
| 8. `check-uportal-downloads.py` accepts `steps.reuse` anywhere | uPortal-start | Job check, quotes and comments, new rows | Section 6 |
| 9. Section 1 cites the wrong merge | uPortal | `38fe16ec1` | Section 7 |
| 10. `ci-jobs.md` doesn't list the lint job or the new checks | uPortal-start | The lint job and a Workflow lint section | Docs only |
| 11. `tip-status.sh` points to section 1 | uPortal | Section 11, with finding 1 | Comment only |
| 12. LOG details | both | MariaDB Playwright, MOD-I377, MOD-I378, MOD-I373 in the note | Section 5 |
| 13. The `build.gradle` comment on CI cache keys | uPortal | Says that CI doesn't use the build cache | Comment only |

## 1. `tip-status.sh`

A fake `gh` on `PATH` answers `gh api` with a tip SHA and `gh run list` with canned JSON, which
it passes through the script's own `--jq` expression with `jq -r`. Real `gh` gives an empty
`conclusion` for an unfinished run, and the fake does the same. Each case changes the runs of one
workflow. The fake gives each of the others one green run of the tip.

The first fix (a586d2e0d) still counted an older commit's Workflow lint pass when the tip had its
own lint run, queued, running or cancelled. The re-review found it. The script now counts any
commit's lint run only when the tip has none. The column "first fix" shows the cases that changed.

| Case | retro-1 script | First fix | This branch |
| --- | --- | --- | --- |
| The tip's run in progress, an older commit's run passed | exit 0, the SHA in the event field | exit 1 | exit 1, "no finished run on the tip" |
| The tip's run queued | exit 0 | exit 1 | exit 1, "no finished run on the tip" |
| The tip's run cancelled | exit 0 | exit 1 | exit 1, "no finished run on the tip" |
| No run of the tip (just pushed, or `[skip ci]`) | exit 0 | exit 1 | exit 1, "no run on the tip" |
| The tip's run passed, an older one failed | exit 0 | exit 0 | exit 0 |
| The tip's run failed | exit 1 | exit 1 | exit 1 |
| The tip passed, then a manual run of it was cancelled | exit 0 | exit 0 | exit 0 |
| The tip failed, then a manual run of it is in progress | exit 1 | exit 1 | exit 1 |
| No run on the branch at all | exit 1 | exit 1 | exit 1 |
| Workflow lint only on an older commit, passed | exit 0 | exit 0 | exit 0 |
| Workflow lint running on the tip, passed on an older commit | exit 0 | **exit 0** | exit 1, "no finished run on the tip" |
| Workflow lint cancelled on the tip, passed on an older commit | exit 0 | **exit 0** | exit 1 |
| Workflow lint queued on the tip, passed on an older commit | exit 0 | **exit 0** | exit 1 |
| Workflow lint passed on the tip, failed on an older commit | exit 0 | exit 0 | exit 0 |
| Workflow lint failed on the tip, passed on an older commit | exit 1 | exit 1 | exit 1 |
| Workflow lint passed on the tip, then a manual run of it is in progress | exit 0 | exit 0 | exit 0 |

The message for a tip whose run is cancelled, queued or still running is "no finished run on the
tip (cancelled, queued or running)".

On the real `ccr-43ba0785-hzlrj5-admin-390` branch, whose uPortal-start Integration run was in
progress, the retro-1 script printed `latest 42455714441 397e40df6… in_progress/push on older
commit ; no finished run in the last 20`. The first fix printed `latest 42455714441 push
in_progress/- on tip; not finished on the tip 397e40df63`. On the integration tip and on this
branch after its push runs, this branch's script gives the same results as the first fix.

## 2. `preflight.sh`

The prototype machine has JDKs 11, 17, 21 and 25 in `/usr/lib/jvm`, and lacks `ss` and SDKMAN.
These runs used a fake `gh` for the dispatch probe, so that a run doesn't send a POST:

| Check | retro-1 script | This branch |
| --- | --- | --- |
| JDK, uPortal wants `25.0.4-tem` | FAIL, "SDKMAN has: none" | ok, JDK 25 in `/usr/lib/jvm` |
| JDK, uPortal-start wants `11.0.30-amzn` | FAIL, "SDKMAN has: none" | ok, JDK 11 in `/usr/lib/jvm` |
| Ports 8080, 8005, 8009 and 8887 | FAIL, "ss: command not found" | ok, free |
| Chromium 1248 and its headless shell | FAIL | FAIL: a real failure, only 1194 is installed |

Seeded faults, each run on this branch's script:

* `PORTS="2024 40297 18767"`: 2024 and 40297 have listeners on this machine, and
  `(echo > /dev/tcp/127.0.0.1/PORT)` agrees. The script reports both in use and 18767 free.
* A Python listener on 127.0.0.1:18767, which exits by itself after 12 seconds: "18767 is in
  use". After it exited: "18767 is free".
* Fixture tables in place of `/proc/net/tcp` and `/proc/net/tcp6` (a fake: the copy of the script
  reads them instead). 8080 listens in `tcp`, 8005 has only an established connection, and 8871
  listens only in `tcp6`. Result: 8080 and 8871 in use, 8005 and 8887 free. Without the `tcp6`
  file, 8871 is free and nothing fails. With neither file, every port fails with "can't read
  /proc/net/tcp". Passing a missing `tcp6` to awk, as an earlier sketch did, makes awk exit 2.
* JDKs, from a scratch repository and a scratch uPortal-start: `.sdkmanrc` wanting `99.0.1-tem`
  fails with "no JDK 99 under …, /usr/lib/jvm or JAVA_HOME". A fake SDKMAN with `23.0.2-tem`
  satisfies `23.0.2-tem`, and a fake `JAVA_HOME` whose release says `1.8.0_462` satisfies
  `8.0.462-tem`.

## 3. `check-placeholders.sh` and its test

`check-placeholders.test.sh` builds a scratch repository with a tip and topic branches. Its 17
rows each check the exit status and a line of the output. A fake `git` on `PATH` fails `git diff`
or `git grep` for the two rows that need a broken repository. The script and the test are the same
bytes in uPortal and uPortal-start, and Workflow lint runs the test in both.

* On the retro-1 script, 9 rows fail: the `++ ` line, the name with a space, both subdirectory
  rows, both revisions that aren't commits (exit 1 with no output), outside a repository (exit
  0), and both git failures (exit 0).
* On this branch all 17 pass, in both repositories.
* 13 mutants of the new script: 11 fail at least one row. Two can't change the result. With the
  one-line `cd "$(git rev-parse --show-toplevel)"`, `git grep` fails outside a repository and the
  script still exits 2. And `grep` can't fail on its own input.
* In the main uPortal clone, which has only `origin/` refs for topics,
  `check-placeholders.sh ccr-43ba0785-hzlrj5 ccr-43ba0785-hzlrj5-retro-1` exited 1 with no output.
  Now it exits 2 with "check-placeholders: not a commit: ccr-43ba0785-hzlrj5-retro-1". The call
  from section 11, with the merge base and `origin/` refs, finds 0.
* For finding 4, with the tip as base the check reports a row that the tip numbered after the
  topic branched. With the merge base, it passes.
* With no arguments, the check finds 19 placeholders in uPortal and 18 in uPortal-start, all in
  the notes of topics that landed earlier. The coordinator decided not to number them here. The
  script's header says that a landing uses the merge-base form.

## 4. Time zones in uPortal's tests

The runs used a probe test in `uPortal-hibernate3-dialects` that prints
`TimeZone.getDefault()`, never committed, with `./gradlew --no-daemon --offline`. The machine's
zone is `Etc/UTC`.

| Run | Test task | Zone the test saw |
| --- | --- | --- |
| No setting | ran | `Etc/UTC` |
| The same again | up to date | (`Etc/UTC`) |
| `TZ=Australia/Sydney` | ran | `Australia/Sydney` |
| The same again | up to date | (`Australia/Sydney`) |
| No setting | ran | `Etc/UTC` |
| `-Duser.timezone=America/Denver --rerun` | ran | `Etc/UTC` |
| `JAVA_TOOL_OPTIONS` with `-Duser.timezone=America/Denver` | up to date | (`Etc/UTC`) |
| The same with `--rerun` | ran | `America/Denver` |
| No setting | up to date | (`America/Denver`) |
| No setting, `--rerun` | ran | `Etc/UTC` |
| `TZ=Australia/Sydney`, with the zone pinned as in `uPortal-db-migrate` | ran | `America/New_York` |
| `JAVA_TOOL_OPTIONS` Denver and `--rerun`, zone pinned | ran | `America/New_York` |

The last two rows pin the zone with an init script outside the repository, which sets
`systemProperty 'user.timezone', 'America/New_York'` on the probe module's tests, as
`uPortal-db-migrate/build.gradle:190` does. The test JVM keeps the pinned zone with `TZ` and
with `JAVA_TOOL_OPTIONS`.

## 5. LOG corrections

* Integration 38775913145 on uPortal-start `d1326f514` built uPortal `498252acd1` (the
  `uPortal under test` annotation of job 116563376166) and finished red at 17:48:59Z. Job
  116563567769 failed `pages.spec.ts:121`, and the other four red jobs show `1 flaky` and no
  failure in their logs. The other eight integration push runs passed (`gh api
  repos/ChristianMurphy/<repo>/actions/runs/<id>`).
* Run 38203657042 exists only in uPortal-start. Its Windows job 114662281469 failed with
  `Java heap space` in `:overlays:resource-server:removedApiScan`.
* Job 116224145491 (layout-calendar, Playwright on HSQLDB) ended with 2 failed, both
  `rtl-theme.spec.ts:123`, and 2 flaky, one of them `visual-resource-server.spec.ts:153`.
* uPortal-start `notes/layout-calendar.md` now gives MOD-I373 the LOG's status, "Local only".

## 6. `check-uportal-downloads.py`

The test has 11 rows now. On the retro-1 script, the `steps.reuse` row outside the `uportal` job
and the row with quoted names and a comment fail. Mutants, each a change to this branch's
script:

| Mutant | Rows that fail |
| --- | --- |
| Drop the match on `uportal` in the name | a `uportal` name outside `~/.m2` |
| Accept any `${{ … }}` name | another job's output, `steps.reuse` outside the `uportal` job |
| Accept any name with `outputs.artifact` | the same two |
| Read `name` from above `with:` | a step whose own `name:` comes before `with:` |
| Accept `steps.reuse` in any job | `steps.reuse` outside the `uportal` job |
| Keep the quotes, or keep the comment | quoted names and a comment |

On the repository's workflows the script finds the same 17 downloads as before, with no problem,
and gives each one its job.

## 7. Merge commit `38fe16ec1`

`git show --remerge-diff 38fe16ec1 -- docs/developer/web-components/README.md` has the `pP_query`
text, and `git show --remerge-diff 1c1b39734b` doesn't touch any doc. Commit 47a2ce8b6 on the
`-batch-1` branch names 1c1b39734b too. That is for the batch-1 branch to fix.

## Proposed LOG rows (numbers to be set by the coordinator)

| ID | Area | Finding | Status |
| --- | --- | --- | --- |
| MOD-I388 | CI | `tip-status.sh` counts a uPortal-start run on the uPortal-start tip, but the run doesn't say which uPortal commit it built. When the coordinator merges a change to uPortal alone, uPortal-start's tip doesn't move. Its runs still count as green although they tested the older uPortal tip. | **Open.** Read the commit from the `uPortal under test` annotation of the `Build uPortal` job and compare it with the uPortal tip, or start a uPortal-start run after each merge that changes only uPortal. |
