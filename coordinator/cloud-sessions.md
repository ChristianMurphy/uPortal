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

## 02:10 status
- Owner rulings: DB versions are hard minimums (no workarounds for older servers, no version check): landed 16080b035 (MOD-D25, ADR 0009, UPGRADING). Flyway vs Liquibase: owner asked which is better; recommended Flyway, awaiting their answer. Playwright error-context.md: owner asked whether it injects instructions; answered (Playwright's own prompt header, benign); no filter unless asked.
- Integration branch red on SpotBugs since 7691dadc3 (TargetConstraintChecks.scalar, SQL_INJECTION_SPRING_JDBC). Sent to copy-tool agent a3f910cda1ef31f61 as top priority (suppress with justification, not baseline).
- Manual runs failed Commit messages on 2dd6334ce (later jargon rule): exception added, ae9c14ac7.
- Spring 5.3 probe: hard-coded --release 11 broke on Pluto (release 8). Fix 0b695896d in batch 1 (probe uses module's release; baseline uPortal-pluto/uPortal-pluto-portal-driver=3).
- Batch 1 = land-pluto 0b695896d (on ae9c14ac7) pushed to -landing (push CI starts). Batch 2 = 6e57f78d2 on -landing2 ([skip ci] tip). Re-dispatch both after the SpotBugs fix lands (rebase first).
- jqwik: first check counted nothing because Gradle hides test stdout; rerun with show-streams.init.gradle (jqwik-filter-check2.sh).
- Agent rules updated: topic branches and backup/ branches allowed.

## 02:20 (usage limit near)
- Owner chose Flyway (prefers Apache 2.0): MOD-Q4 closed on the integration branch.
- Batch 3 = batch 2 + gviz (9 commits) = land-batch3 b1753d43b on ccr-43ba0785-hzlrj5-landing2. Batch 1 = 0b695896d on -landing (push CI running).
- Next after reset: copy-tool SpotBugs fix lands -> rebase both landings -> dispatch CI.yml + db-integration.yml (uPortal) and integration.yml (start) on -landing2 -> land via ccr-push.sh. jqwik: read tasks/b0it0rw5t.output (control count must be > 0).

## 02:35 after the reset
- Landed: 9f564f893 SpotBugs fix (copy tool), 7e77dacb9 jqwik filter (MOD-I179; verified: notice 1 without filter, 0 with), WS14 foundation (agent, 48ad07ccf), Flyway decision 7c0fd896f + 90c92b7ef.
- Batch 3 (pluto + uPortal-rendering test dep fix + probe fix + javatime + cache + gviz) = 4b3a91b77 on uPortal -landing2; overlays c29999696 on start -landing2. Dispatched uPortal CI + DB integration, start CI + Integration at ~02:33.
- Resumed all 4 local agents after the limit. Deleted backups: jqwik, ws14-foundation, ws14-verify (landed).

## 03:10 batch landed
- uPortal 7efd1c6d1: 58 commits (Pluto + probe fix + rendering test dep fix, javatime, cache, gviz), tree == merge(tip d7e6f88cf, tested 5e15b123c). uPortal-start c29999696: overlays (15). Landing CI green: uPortal CI 38105448139, DB 38105277559 (5 vendors), start CI 38105279144, Integration 38105280624.
- Dispatched integration-branch CI + DB integration (uPortal) and CI + Integration (start) for the [skip ci] tips.
- Told pluto/overlays/javatime/cache/gviz sessions they're done; told local agents about portletApiDependency removal.
- Topic and backup branch deletes are 403 at the git proxy: leave them; owner can delete on GitHub.
- Still to land: local-login (start; apply its uPortal UPGRADING text), cluster, http-replay, gindex, goldens (+ Hibernate 5.2 after), e2e.

## 03:15 WS14 foundation done; portlet conversions started
- WS14 agent a6d1213feb127f2e1 finished (report: scratchpad/ws14-foundation.md). Worktrees cleaned.
- New cloud sessions (prompt: scratchpad/wc-prompt.md), web-component conversions only; Thymeleaf pages wait for Spring 5.3 (P4-1), which waits on Hibernate 5.2, which waits on the goldens:
| session_01QytzZZ4r6LrVQfxXm7ZArf | Popular Portlets -> web component | both repos | ccr-43ba0785-hzlrj5-wc-popular-portlets | 03:15 |
| session_01E2HJKpeJKrMNR1oGJ21PUv | Session Timeout -> web component | both repos | ccr-43ba0785-hzlrj5-wc-session-timeout | 03:15 |
- Next candidates when these land: User Locale, background preference, Search, Directory, Portlet Marketplace.

## 03:30 landing lesson
- 7efd1c6d1 broke uPortal-webapp compileTestJava: d7e6f88cf (copy tool, landed after the batch's CI) used setAbandonedLockAge(joda Duration); the java.time batch changed the setter. Text merge was clean. Fixed efba9bc17 (compile verified; lock test run in background bbxqtal59).
- New landing rule: when the integration tip moved after the batch's CI, run `./gradlew compileTestJava` (or at least on the modules both sides touch) on the rebased tree before ccr-push.sh.

## 04:20 local-login landing in progress
- uPortal-start: land-login worktree (branch land-login, 12 commits, tree == merge(ccr, local-login tip)), reworded 3 messages (msgfix-login.py). Pushed to start -landing a1d41bf20; dispatched CI + Integration ~04:20.
- uPortal: MOD-D40 + MOD-I180..I183 + UPGRADING section + REPORT tag fix committed locally in uPortal-coord (wip-coord), NOT pushed yet: push with ccr-push.sh after start CI is green, then land-login via ccr-push.sh from /home/user/wt/land-login, then dispatch start CI + Integration on ccr (tip is [skip ci]).
- uPortal -landing reset to the ccr tip so start's landing CI builds current uPortal.
- 04:35 gindex (19 commits, 3 reworded: msgfix-gindex.py) stacked on land-login as land-start2 in /home/user/wt/land-login; pushed start -landing dd3f7e8af; dispatched CI, Integration, g-index on it (cancelled the login-only runs). uPortal docs: 2c4718301 (D40, I180-I183) + ceeea6539 (D41, I184-I190) in uPortal-coord, backed up on uPortal -landing2. Land order after green: uPortal docs via ccr-push.sh, then start land-start2 via ccr-push.sh, then dispatch start CI + Integration + g-index on ccr.
- wc-popular-portlets: reword uPortal-start 07f4545 ('the 403 answer to' -> plain wording) at landing.
- 04:40 goldens agent done (report scratchpad/tests-persistence.md). It may have killed another agent's read-only du (pkill -f); agent-rules now forbid pattern kills.

## 05:00 landed
- uPortal 0f25d5387: MOD-D40 (local login) + MOD-D41 (G-index), MOD-I180..I190, UPGRADING section, REPORT tag fix. Also 4782e0227/4326e9fac (cache test Windows paths; full CI matrix on manual workstream runs).
- uPortal-start dd3f7e8af: local login (12) + G-index (19), fast-forward of the tested landing commit. Dispatched start CI, Integration, g-index on ccr.
- Goldens done (a75820d7a; agent finished). P2 cloud session session_01HMe4gQBfaXjQDQFVTvZJgL (branch ccr-43ba0785-hzlrj5-hibernate52-r2 in both repos).
- Branches for owner to delete add: uPortal -goldens, start -local-login, -gindex.
- Disk 7.2 GB: asked copy-tool (oracle/mssql images) and e2e (start-tst 4.7 GB) to clean.
- 05:10 copy-tool agent a3f910cda1ef31f61 finished (report scratchpad/dbmig-tests.md); landed through d864e63b6; DB copy tool run 38111315873 all green incl. SQL Server + Oracle nightly legs. Branches for owner: uPortal -dbmig-tests, backup/ccr-43ba0785-hzlrj5-dbmig-tests, backup/ccr-43ba0785-hzlrj5-tests-persistence. Disk 13 GB.

## 05:22 more parallel work (owner asked)
| session_01EELW3HUB1Xwprs4mAubg4u | User Locale -> web component | both | ccr-43ba0785-hzlrj5-wc-user-locale | 05:22 |
| session_01Mq8kzpodmqTJS3TMfCQzxm | Search -> web component | both | ccr-43ba0785-hzlrj5-wc-search | 05:22 |
- Local agent ad9710bd7c066b122: login and CSRF fixes MOD-I180, I181, I130 (worktrees uPortal-sec, start-sec; topic ccr-43ba0785-hzlrj5-login-fixes).
- Queued until Hibernate 5.2 lands (they touch persistence): G-index R1-R15, golden bugs MOD-I140..I143, P3-5 converters, MOD-I144 is with P2. After P2: P4-1 Spring 5.3 bloc, then Thymeleaf admin pages and P4-2 JDK 21 runtime.
- Could start now if wanted: Directory and Marketplace web components; json-lib -> Jackson (P5-2 part); jasypt -> in-tree verifier (P5-4); MOD-I133 Favorites import upgrader; cluster bugs (start-up event loss, per-session permission cache).
- 05:25 wc-search session blocked: its permission check denied add_repo uPortal-start and a read-only grep. Told it to do uPortal-only steps 2-3; owner asked to approve in that session.
- 05:35 Owner decision (MOD-I130): protect all legacy unsafe /api endpoints with CSRF incl. /api/layout, marketplace rating, /api/preferences; accept breaking uPortal-home (useOldLayout layout calls, saveRating) and uportal-app-framework (chooseSkin, addToHome) until they send a token or Bearer. No XSRF cookie. Sent to login-fixes agent.
- 05:45 owner asked for unconfirmed assumptions; asked all active agents/sessions to list theirs.

## 2026-10-11 06:21 UTC
- Landed uPortal 2b8e80e5f (LOG: MOD-D40 demo default, MOD-D42 search, MOD-D43 freeze, I140/I141/I142 notes, I191).
- Landed uPortal 885c8f9c6 + 206a8da55: check-freeze-store.py in the Commit messages job; net count per rule fails, added entries are warnings. Cache listener can't move to Spring Cache (no eviction events); stays until P4-3.
- Landed uPortal-start 5ef0dfced: authentication.md says local login is the demo default; production uses SSO.
- Answered: Search -> analytics option (a) SearchRequestEvent; UPGRADING lists removed public pieces; never log provider headers.
- Answered: Session Timeout list accepted except #4 -> fall back to 30 min on 5xx/network/malformed; 401 means no warning.
- Answered: P2 a19ebddc6 lock-test wait accepted (MySQL DATETIME rounding exists on 4.3 too).
- New sessions: wc-self-edit-account session_01WCgEpUoVfzwp1CAcqSUhgp; wc-reset-my-layout session_01Enrc6m1hU6m7twAjCR9CpX.
- Owner question pending: Self Edit Account changes the password without the current password today (to confirm); session builds "require current password" as its own revertible commit.
