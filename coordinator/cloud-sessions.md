# Cloud sessions (MOD-D30), max 3 at a time
| Session | Task | Repo | Branch | Started |
|---|---|---|---|---|
| session_01D3kfgKzSSqxUWZ5Ni7LWq8 | HTTP replay + security matrix (#16) | uPortal-start | ccr-43ba0785-hzlrj5-http-replay | 21:22 |
| session_01RvbdUKREMw4s1ZjS24Fjkd | cluster, browsers/a11y, query+load budgets (#18) | uPortal-start | ccr-43ba0785-hzlrj5-cluster | 21:22 |
| session_01Ujj36LshKbdxBQWUVDSwzL | P3-2 Pluto in-tree | uPortal (+start same branch) | ccr-43ba0785-hzlrj5-pluto | 21:22 |
Check: get_session (status_bucket), list_events kinds [assistant,result], branch CI runs. Handoff notes in docs/developer/modernization/notes/.
| session_01FMSq3zJZh5g9ihzwthDXuG | P4 prep on Spring 4.3: context-load test, PMVC adaptations, 5.3 compile probe | uPortal (+start same branch) | ccr-43ba0785-hzlrj5-spring53-prep | 21:25 |
| session_01CPt6NctSYup7rdRgBnPBcu | overlay fixes MOD-I60–I62 on javax + removed-API scanner + Tomcat 11 evidence | uPortal-start | ccr-43ba0785-hzlrj5-overlays | 21:25 |
| session_01VfHhiWt3WRJKLCsZz1rH56 | P1-13 local login default + CAS 7/8 opt-in | uPortal-start | ccr-43ba0785-hzlrj5-local-login | 21:25 |
Cap raised to 6 after "go ham" (owner reset usage).
Queued for local slots (light): G-index workload + index audit (MOD-D12) [or cloud when a slot frees]; P3-4 Joda→java.time non-entity; UPGRADING-6.0 draft.
| session_01AaE15VXcEeTFrjTa5fKzhW | G-index workload + index audit (MOD-D12) | uPortal-start | ccr-43ba0785-hzlrj5-gindex | 21:28 |
| session_01BczdVSXCFLv2boYJrRWu4e | P3-4 Joda→java.time outside JPA | uPortal (+start same branch) | ccr-43ba0785-hzlrj5-javatime | 21:28 |
| session_01LS6ayx2s3MDdf1Arhrjnhy | P3-3 Spring cache seam | uPortal (+start same branch) | ccr-43ba0785-hzlrj5-cache-seam | 21:28 |
"Full usage" (owner): no cloud cap; limit is my merge/review capacity and conflict overlap.
Next when WS14 foundation merges: fan out portlet conversions (characterisation spec first) as parallel cloud sessions, low-overlap portlets first.
Portlet conversions must follow MOD-D31 (XState v5 machines via the Lit actor-controller adapter, model-based tests with @xstate/graph).
| session_01QjPzV1qqbij6fXp1pUrG1p | MOD-I51 remove jitpack-only google-visualization-java | uPortal | ccr-43ba0785-hzlrj5-gviz | 22:50 |
Hibernate 5.2 branch pushed: uPortal ccr-43ba0785-hzlrj5-hibernate52 (waits for goldens; then rebase + full gates, ideally in a cloud session).
- Pluto landing: reword e1bb823 body (quotes the jargon word; uPortal.Jargon). Resolve Pluto testsuite spec overlap with the local e2e agent's Pluto checks. Also STATIC_ANALYSIS.md line 37 heading is owned by prose agent c.

## Local agent roster (as of 23:52 UTC, after the restart)
| Agent id | Work | Worktree |
| a6d1213feb127f2e1 | Web components foundation (WS14) | uPortal-wc, start-wc |
| a6a7605e5abcdcfc9 | Persistence, query and analytics golden tests | uPortal-tst |
| a3dc5b4068fc845ab | End-to-end equivalence tests (dataExport, Pluto suite, portlet modes, flaky-test check) | start-tst, uPortal-tst-e2e |
| a3f910cda1ef31f61 | Copy tool property, fault and mutation tests | uPortal-dbt |
| a4d49f9b71c658f39 | Prose rewrite A (research: hibernate, toolchain, spring, inventories) | uPortal-prose-a |
| ae5e435cdd47ccf49 | Prose rewrite B (research: webcomponents, REPORT, platform, portlets, spike, README) | uPortal-prose-b |
| aaac211e686e68322 | Prose rewrite C (WORKSTREAMS, RFC, UPGRADING, STATIC_ANALYSIS, notes, LOG; uPortal-start harness doc) | uPortal-prose-c, start-prose-c |
| (done) ad1d1d88871b78c84 | DB matrix: only the LOG entry is left, for the coordinator | uPortal-dbm, start-dbm |
Decided: Pluto spec overlap goes to the e2e agent's spec; the cloud Pluto session drops its uPortal-start spec.
Hourly check-in routine: trig_01CXTm9dZGeia9okTQddSNLe (cron :51).
- LOG row to add (MOD-I171): uPortal d66bdc5a5 (coordinator, "by hand") and cd841c8c3 (prose agent c, quotes the jargon word) fail vale.sh commits; both pushed because the check was piped into tail in an && chain. Not rewritten (integration branch). Fix: scratchpad/ccr-push.sh runs both checks before pushing; agent rules warn against piped checks.
- Landing rewords (topic commits whose messages fail vale.sh commits): javatime 750571c, cluster (commit "name the cluster test a required check"), pluto e1bb823.
- local-login: its handoff note carries uPortal UPGRADING-6.0.md text and uPortal follow-ups (uPortal push was declined for that session): apply them in uPortal when landing.
- cache-seam: it is pushing a [skip ci] docs commit fixing 83 Vale errors in cache-seam.md + its UPGRADING sections: re-replay its commits before landing to include it.
- cluster: dl.k6.io and github.com releases are 403 through the proxy for that session (network policy); k6 runs in CI only.
- gviz: 3 pushed commits may fail vale.sh commits; reword at landing. Run land-gate.sh on it and check the statistics + SqlQuery chart pages render (Google Charts JSON unchanged).
| a5fd220a462bf99ed | ADRs in MADR format replacing the RFC (MOD-D36) | uPortal-adr |
- History rewrite (MOD-D37): after the in-flight work is merged. Steps are in LOG.md MOD-D37. Before it: announce the freeze to all local agents and cloud sessions, tag archive/ccr-43ba0785-hzlrj5-<date> in both repos, rebuild, check the trees are identical, push --force-with-lease, send everyone the old and new SHAs, and empty commit-exceptions.txt.
- After the ADR agent: fix the research files that mention the RFC (prose agent b's files) once b has finished.
- Landing renames: cache work note notes/cache-seam.md -> notes/spring-cache.md (and fix links to it); rename any other note/file names with banned terms. Topic branch names are deleted at landing, so they don't matter.
- javatime: waiting on its rename JodaTimeBridge -> JodaTimeConversions (requested 00:31). Then replay again (gradle.properties conflict: keep jqwikVersion, drop jodaTimeJsptagsVersion), reword: subject cf562c14b (73 chars), bodies with slice/bridge/gate/seam, then land-gate and land.
| aa9c3d6f37df7fcb0 | Log and ADR accuracy fixes (11 items: D22, D23, D28, D36, D6/D9, D27, D17, AGENTS.md Bootstrap, Q9/D18, RFC mentions in research) | uPortal-logfix |

## Landing via CI (01:30, machine too loaded for two local portals)
- Replays go to ccr-43ba0785-hzlrj5-landing in both repos (my branch; force-push allowed). Tip commits with [skip ci] suppress push runs: dispatch CI.yml + db-integration.yml (uPortal) and CI.yml + integration.yml (uPortal-start) on the landing branch. After landing a tip with [skip ci], dispatch the same on ccr-43ba0785-hzlrj5.
- Batch 1: uPortal pluto replay b319dedb0 (land-pluto worktree) + uPortal-start overlays replay 80b5a262a (land-overlays worktree). Dispatched 01:31.
- DB matrix entry landed fd52921db; worktrees and wip-db-matrix branches removed.
- Copy-tool fault test race (run 38099695386 at 0be70c49f, exit 0 expected nonzero): sent to a3f910cda1ef31f61 with the db-migrate nightly dispatch ask.
- Open owner decisions to raise: DB minimums hard vs recommended; Flyway vs Liquibase after 6.0 (MOD-D22/Q4).
- Batch 2: uPortal landing2 8598f9238 = pluto replay + javatime (12) + cache (20); uPortal-start landing2 = 80b5a262a. Dispatched CI, db-integration (uPortal) and integration (start) 01:40. Local cache gate stopped (load; multi-login tests timed out, page showed the right user).

## Checkpoint before the usage limit (01:40)
- Owner asked that all work be safe in case containers pause. Asked the 4 local agents and the 4 active cloud sessions (http-replay, cluster, gindex, gviz) to commit, push, and update their handoff notes, then push after every step.
- Backups of committed but unpushed local work, on branches CI ignores (the workflows run only on master and ccr-*):
  uPortal backup/ccr-43ba0785-hzlrj5-{jqwik,ws14-foundation,tests-persistence,dbmig-tests};
  uPortal-start backup/ccr-43ba0785-hzlrj5-{ws14-verify,tests-e2e}. Delete them once the work lands.
- Coordinator notes and scripts: uPortal backup/ccr-43ba0785-hzlrj5-coordinator (coordinator/ directory). Never land it.
- jqwik filter: commit 42f5817cd (backup branch above). With filter: 0 notices, 95 tests pass. Control run (no filter) was still going; if the container was lost, rerun scratchpad jqwik-filter-check.sh from coordinator/ and push with ccr-push.sh.
- Pluto session confirmed the replay matches its branch (verify-import.sh passes on b319ded). Done.
- Landing CI dispatched (01:28 and 01:33): uPortal CI 38101924741, DB 38101926236 (landing); CI 38102214490, DB 38102216331 (landing2). uPortal-start CI 38101927617, Integration 38101929051 (landing); Integration 38102217848 (landing2).
