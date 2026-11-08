<!-- markdownlint-disable -->

# A permission check racing a permission change kept the old decision (MOD-I403)

Branch `ccr-43ba0785-hzlrj5-permission-race` in uPortal and in uPortal-start, both from the
integration branch. The owner treats it as a cache bug, as with MOD-I310. It made uPortal-start's
Cluster job fail once in 24 runs since the MOD-I212 clear (68125a343), so it is a fix for the red
tip.

## What failed

uPortal-start Integration run 42458398202, Cluster job 128662855202, built uPortal `425884690`,
whose runtime code is the tip `498252acd`. `permission-session` failed: node B's session, denied
before the grant, was still denied 30 s after it and for a further 120 s. The other checks
passed. From both nodes' access logs (artifact `integration-cluster`, read in
`/home/user/wt/cluster-perm-ci/`):

| Time (UTC) | Node | What happened |
| --- | --- | --- |
| 07:29:35 | B | A new student session is denied the Permissions Manager portlet (`/api/portlet/permissionsmanager.json`, 403). The denial is now in node B's decision caches. |
| 07:29:35 | A | The admin grants the student SUBSCRIBE on the portlet (`POST /api/updatePermission`, 200). Node A's student session is allowed in the same second. |
| 07:29:35 to 07:32:05 | B | The session from 07:29:35 asks once a second: 150 requests, all 403. |
| 07:32:06 | B | A new student session is allowed. Its login purged the student's tagged entries on node B. |
| 07:32:07 | A | The admin revokes the permission. A new session on node B is denied 1898 ms later, and the existing sessions on both nodes follow the revocation. |

So node B applied the grant within about a second, as it did the revocation, but one decision
on node B kept the old answer. `PRINCIPAL_HAS_PERMISSION` keeps an entry for 180 s, and the
session gave up after 150 s.

## Cause

`AuthorizationImpl` caches each decision in `PRINCIPAL_HAS_PERMISSION`, and
`AnyUnblockedGrantPermissionPolicy` caches the per-principal answers it computes the decision from
in `HAS_UNBLOCKED_GRANT`. Both answers come from the principal's permission set (the
`IPermissionSet` entity cache, 6 hours). A permission change removes the changed principals'
permission sets, then `clearPermissionDecisions` empties `PRINCIPAL_HAS_PERMISSION` and then
`HAS_UNBLOCKED_GRANT`. All three caches replicate removals only, through ehcache-jgroups'
asynchronous queue.

What the libraries do, read from the jars (ehcache-core 2.6.11, ehcache-jgroups3replication
1.7.4):

* `Cache.removeAll` removes the entries one key after the other (`MemoryStore.removeAll`), then
  calls `notifyRemoveAll`. The JGroups replicator queues a "remove all" event there.
* Every cache of a cache manager with the default 1000 ms interval shares one queue
  (`JGroupsCachePeer.asyncReplicationQueues`, keyed by the interval). A timer sends what the queue
  holds once a second, as one list.
* The receiver applies the list in order: `removeAll(true)` for a "remove all", `remove(key, true)`
  for a removal.

This leaves windows on any node, for grants and for revocations:

1. **Between the two clears.** Node B empties `PRINCIPAL_HAS_PERMISSION` first. A check that runs
   before node B empties `HAS_UNBLOCKED_GRANT` misses the first cache, finds the old answer in the
   second, and stores the old decision in the first again, for 180 s. The window is the time
   between the two removals on node B, and it includes the loop that removes the
   `HAS_UNBLOCKED_GRANT` keys one at a time. The CI runs below measured that loop at up to 26 ms.
   If node A's queue timer fired between its two `notifyRemoveAll` calls, node A would send the
   two removals in two lists a second apart. In the 73 clears of both runs it never did. The
   logs of run 42458398202 can't show which happened there: the replication messages weren't
   logged then (the cluster test now logs them).
2. **A check that read its inputs before the clears.** A check that read the permission set or a
   cached answer before a clear could store its result after the clear. The permission set it
   stores lasts 6 hours.

The likely cause in run 42458398202 is the first window: the denied session's first request after
the grant ran within the second in which node B applied the removals.

## Fix

* `clearPermissionDecisions` empties `HAS_UNBLOCKED_GRANT` before `PRINCIPAL_HAS_PERMISSION`, which
  is computed from it. A check between the two removals then recomputes the decision from the
  permission set, which node B has already removed and reads again.
* `DecisionCacheMarker`, a marker entry (a random UUID) under a fixed key in
  `HAS_UNBLOCKED_GRANT`. `AuthorizationImpl` removes it before each step of a change: before it
  removes the permission sets, before it empties `HAS_UNBLOCKED_GRANT` and before it empties
  `PRINCIPAL_HAS_PERMISSION`. The cache replicates removals. The other nodes remove it too, in the
  order of the steps. Each cache write reads the marker before it reads its inputs, and stores
  only while that marker is still in the cache. If the marker goes between that comparison and the
  put, it takes its entry out again. This covers the decision in
  `AuthorizationImpl.doesPrincipalHavePermission`, the answer in
  `AnyUnblockedGrantPermissionPolicy.hasUnblockedPathToGrantWithCache`, and the permission set in
  `AuthorizationImpl.primGetPermissionsForPrincipal`.

The brief proposed a generation counter, increased by a cache event listener on the three caches
for local and remote removals. The marker closes the same two windows, without a listener:

* A listener is an Ehcache 2 `CacheEventListener`. That's a new `net.sf.ehcache` use,
  which `MigrationFreezeRulesTest` refuses and `check-freeze-store.py` can't record (MOD-D43). The
  `spring-cache.md` plan drops the Ehcache 2 listeners at the provider switch (P4-3).
* The marker is stored in the cache and read through Spring's `Cache` API. A removal made on another
  node removes it here, with any provider that replicates removals in order, which the clear
  already needs.
* A listener sees a "remove all" only after the last key is gone (`notifyRemoveAll` comes after the
  loop). The marker is removed before each step starts.

Ehcache 2 refuses `putIfAbsent` and the other compare-and-set operations on a cache with a
replicator ("You have configured the cache with a replication scheme that cannot properly support
CAS operation guarantees"). The marker is created with a plain `put`. When two checks create one at
once, the check whose marker is replaced doesn't store its result. That costs one recomputation.

The marker stays in `HAS_UNBLOCKED_GRANT` and doesn't get a cache of its own. A new cache name
would stop every portal whose own `ehcache.xml` lacks it at startup (`CacheLookup.requireCache`),
until the site adds it with the same replication settings. `HAS_UNBLOCKED_GRANT` is already
required and replicates its removals in the same queue. With 250000 entries and LRU it keeps the
marker, which every miss reads. In return `PRINCIPAL_HAS_PERMISSION` and `IPermissionSet` don't
cache anything while `HAS_UNBLOCKED_GRANT` is disabled, and little when it stores only a few
entries (with 1 or 2 entries the review's probe kept `PRINCIPAL_HAS_PERMISSION` empty). The comments of both
`ehcache.xml` files and `UPGRADING-6.0.md` say so.

What it costs:

* Each marker read is a full Ehcache `get` on `HAS_UNBLOCKED_GRANT`. It updates the marker
  element's statistics and access time, and counts as a hit of that cache in its statistics and in
  JMX.
* Each cache layer that misses reads the marker three times (one before its inputs, two around
  its put). A decision that misses `PRINCIPAL_HAS_PERMISSION`, computes N answers and loads one
  permission set reads it 3 × (N + 2) times. Hits read nothing more.
* After each permission change one check puts a new marker. Each change sends three more removals
  of one key each.
* When a marker expires with the cache's 300 s TTL, a check running at that moment doesn't store
  its result.

What it doesn't close (open, for the owner): the marker changes before a "remove all" starts, and a
check can still read an old entry of the same cache while the loop runs.

* Within one "remove all" of `HAS_UNBLOCKED_GRANT`, a principal's answer is computed from its
  parent groups' answers in the same cache. A check that reads a group's old answer before the loop
  removes it, and stores the member's answer after the loop removed that key, keeps the old answer
  until the entry expires (300 s). This needs a change to a group's permission.
* `AuthorizationImpl.doesPrincipalHavePermission` answers an activity that the database doesn't
  define with the cached administrator decision (`UP_SYSTEM`, `ALL_PERMISSIONS`, `ALL`) from
  `PRINCIPAL_HAS_PERMISSION`. A check that reads it before the loop of
  `PRINCIPAL_HAS_PERMISSION` removes it, and stores its own decision after the loop removed that
  key, keeps it for 180 s.

The proposed listener has the same gaps, because it hears of a "remove all" after the loop.

## Evidence

### The race test (uPortal)

`PermissionDecisionCacheRaceTest` (uPortal-webapp, in the style of `PortletDefinitionCacheRaceTest`)
builds two nodes from `properties/ehcache.xml` with JGroups taken out, each with the real
`AuthorizationImpl` and `AnyUnblockedGrantPermissionPolicy` and one shared permission store. In
place of each JGroups replicator, a test replicator records the removals that `ehcache.xml` has
JGroups send (removals, and updates sent as removals). The test applies them to node B one at a
time, in order, as the receiver does. Because each replicated cache has a replicator, Ehcache
refuses `putIfAbsent` on it, as in the portal. Every test runs for a grant and for a revocation:

* `aCheckBetweenTheReplicatedRemovalsDoesNotKeepTheOldDecision` (case 1): node B's session checks
  before the change, node A makes it, and node B checks again after each removal it applies. The
  last check must see the change.
* `aCheckOnTheChangingNodeThatReadThePermissionsBeforeTheChangeIsNotCached` and
  `aCheckOnTheOtherNodeThatReadThePermissionsBeforeTheChangeIsNotCached` (case 2): a check is held
  in the permission store's `select`, after it read the rows, while node A makes the change and
  both nodes clear their caches. Then it goes on. The next check on that node must see the change.

Local runs, Gradle 9.8.1 on JDK 25, `--offline`:
`./gradlew :uPortal-webapp:test --tests '*PermissionDecisionCacheRaceTest'`

| Code | Result |
| --- | --- |
| Integration tip `1d485034e` (no fix) | 6 of 6 fail. Case 1: "node B after the removals and checks \[remove 2.student from IPermissionSet -> false, removeAll PRINCIPAL_HAS_PERMISSION -> false, removeAll HAS_UNBLOCKED_GRANT -> false] expected:<true> but was:<false>" (revocation: the reverse). Case 2: "the next check on node A (the paused check decided false) expected:<true> but was:<false>", and the same on node B and for the revocation |
| The fix | 6 of 6 pass |
| The fix, with the clears in the old order | case 1 fails for the grant and the revocation (2 of 6), case 2 passes |
| The fix, with the marker comparison taken out of `DecisionCacheMarker.store` | case 2 fails on both nodes for both changes (4 of 6), case 1 passes |
| The fix, with the permission set stored without the marker | case 2 fails, 4 of 6 |
| The fix, with `putIfAbsent` for a new marker | 6 of 6 fail with Ehcache's "cannot properly support CAS operation guarantees" |

The seeded faults were reverted after each run.

The review of `490187844` found that no test checked that caching works again after a change, and
that nothing tested the step that takes a put out again. The branch now also has:

* In `AuthorizationImplCacheTest`, the grant, revocation and update tests check twice after the
  change and expect one more policy call. `checksAreCachedAgainOnceTheMarkerIsGone` removes the
  marker, as its TTL does, then checks twice and expects one policy call.
* In `PermissionDecisionCacheRaceTest`, every case asserts that the check after the first one that
  saw the change is answered from `PRINCIPAL_HAS_PERMISSION`. The test counts the hits through a
  wrapper of that cache, which `AuthorizationImpl.setDoesPrincipalHavePermissionCache` installs.
* `aCheckOnTheChangingNodeHeldInItsPutDoesNotKeepItsDecision` and
  `aCheckOnTheOtherNodeHeldInItsPutDoesNotKeepItsDecision` (case 3): a check computes the old
  decision, passes the marker comparison and is held inside its put into
  `PRINCIPAL_HAS_PERMISSION`, while node A makes the change and both nodes clear their caches. The
  put then comes after the clear. The next check must see the change, and the one after it must be
  a hit.
* `takeOutJGroups` asserts that `ehcache.xml` gives the three caches `replicateRemovals=true`, and
  the same `replicateAsynchronously` (default true) and `asynchronousReplicationIntervalMillis`
  (default 1000), as the test's in-order replay of the removals needs.

The cases now run 10 times (5 cases, grant and revocation). Seeded faults, each reverted after its
run, against the 8 tests of `AuthorizationImplCacheTest` and the 10 of the race test:

| Fault | `AuthorizationImplCacheTest` | Race test |
| --- | --- | --- |
| None | 8 of 8 pass | 10 of 10 pass |
| Each `DecisionCacheMarker` puts a marker only the first time it finds none | 4 fail (the three change tests and `checksAreCachedAgainOnceTheMarkerIsGone`) | 4 fail (case 3: the next check isn't a hit) |
| The marker is never put | 7 fail | 10 fail |
| `store` without the second read and the undo | pass | 4 fail (case 3: "the next check on node A expected:<true> but was:<false>", and on node B, and for the revocation) |

### Checks before the push

Run locally on the final code (Gradle 9.8.1 on JDK 25, `--offline`):

* `:uPortal-security:uPortal-security-permissions:test`: `AuthorizationImplCacheTest`, 7 of 7 pass,
  unchanged.
* `:uPortal-webapp:test`: all 471 run pass or are skipped (76), among them
  `MigrationFreezeRulesTest` and `CacheConfigurationCoverageTest`.
* `spotbugsMain` (every module), `spotlessCheck` of both changed modules: pass.
* `check-freeze-store.py <merge base> HEAD`: no rule grew (Ehcache 2: 335 recorded uses before and
  after).

### The cluster test (uPortal-start)

`tests/cluster/cluster.sh` on the uPortal-start branch:

* A `permission-session` failure also asks `/api/session.json` with the session's cookie jar and
  reports the user name. A session that is no longer the student's then shows another name.
* Both nodes log `net.sf.ehcache.distribution.jgroups` at `DEBUG` in the portal log: what each node
  queues and sends ("Sending N JGroupEventMessages from the asynchronous queue") and each removal it
  applies ("received remove all: cache=..."), with milliseconds.
* `permission-rounds` (`--permission-rounds N`, 10 by default, 0 with `--fault`): 4 existing student
  sessions on node B, which checked the permission before, and node A grants and revokes it N times.
  After each change the sessions ask node B one request after the other until each has seen it,
  then each must still see it. A miss is listed in `build/cluster/permission-rounds.txt` with the
  session's user from `/api/session.json`, and the sessions log in again.

### CI runs

**Before the fix.** uPortal-start `ccd93137d` was pushed before the uPortal branch existed, so its
Integration run 42465456850 built the uPortal integration tip `1d485034e` ("uPortal under test"
annotation of job 128683811307). Cluster job 128683896914 passed in 10 min 50 s. Passing Cluster
jobs earlier today took 10.5 to 11.5 min (jobs 128653803604, 128654963387, 128655530942,
128658780276). `permission-rounds`: the 4 sessions followed each of the 20 changes, the slowest
in 938 ms. The run's other jobs failed while downloading the
Gradle distribution (timeouts from services.gradle.org), before any test ran.

What the replication log of that run shows (scripts in the session scratchpad, logs in the
`integration-cluster` artifact):

* On node A, the two `Remove all elements called on` lines of each change
  (`PRINCIPAL_HAS_PERMISSION`, then `HAS_UNBLOCKED_GRANT`) were at most 1 ms apart, and the queue
  was never sent between them (37 clears).
* Node B received each pair in one list and applied the two removals at most 3 ms apart.

So in this run window 1 was at most a few milliseconds wide on node B. Node B's access log has
1944 requests for the portlet, about 95 for each change, and none of them came inside it. The
logs of run 42458398202 have no replication lines, so they can't show the width of its window.
The race test shows both windows and that the fix closes them, and each Cluster run now logs the
replication messages that a lost change would follow.

**After the fix.** uPortal-start `afb27f197`, Integration run 42466487884, attempt 3, built the
uPortal branch at `4f1c2a325` (annotation of job 128709759746). Attempts 1 and 2 failed in
`Build uPortal` while downloading `gradle-9.8.1-bin.zip` (HTTP 504 and timeouts, from GitHub's
release downloads, between 09:03 and 10:50 UTC). Cluster job 128711224056 passed in 11 min 36 s,
with `permission-session` and `permission-rounds` (the 4 sessions followed each of the 20 changes,
the slowest in 984 ms). On node A the two clears of each change, now `HAS_UNBLOCKED_GRANT` then
`PRINCIPAL_HAS_PERMISSION`, were at most 1 ms apart and never split by a queue send (36 clears).
Node B received the marker removals, the permission set removal and the two clears in that order.

One list in that run shows how wide window 1 can get: during the portlet import
(`invalidation-portlet`), node B spent 26 ms in one "remove all" of `HAS_UNBLOCKED_GRANT` before
it went on to `PRINCIPAL_HAS_PERMISSION`. In the rounds the same removal took at most 1 ms. With
the old order, a check during such a loop missed `PRINCIPAL_HAS_PERMISSION` and could still find
the old answer for its key. A session that asks once a second asks inside a 26 ms window about
once in 40 changes, the same order as one failure in 24 runs with two changes each. The logs of
run 42458398202 don't show how long node B's removals took there.

## Proposed LOG rows

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I403 | Security | **Cache bug.** A permission check that raced a permission change could keep the old decision after the change, on the node that made the change or on another. It lasted until the entry expired (180 s in `PRINCIPAL_HAS_PERMISSION`, 300 s in `HAS_UNBLOCKED_GRANT`, 6 hours for the principal's cached permission set). On another node a check between the replicated clears of `PRINCIPAL_HAS_PERMISSION` and `HAS_UNBLOCKED_GRANT` (emptied in that order) recomputed the decision from the old answer, and on any node a check that read the permissions before the clears stored its result after them. Found by uPortal-start's cluster test (run 42458398202, job 128662855202, `permission-session`: a session on node B denied 150 s after the grant), once in 24 runs since MOD-I212. `PermissionDecisionCacheRaceTest` fails 6 of 6 on the integration branch. | **Fixed** (`permission-race` 352aec7d7): `HAS_UNBLOCKED_GRANT` is emptied first, and a marker entry in it (`DecisionCacheMarker`), removed before each step of a change and replicated, keeps a check's result out of the three caches when a change happened while it ran. It uses Spring's `Cache` API, without an Ehcache listener (MOD-D43). uPortal-start's cluster test adds `permission-rounds` and logs the cache replication messages. |
| MOD-I404 | Caching | Ehcache 2 refuses `putIfAbsent` and the other compare-and-set operations on a cache with a replicator, so `Cache.putIfAbsent` through Spring throws `CacheException` on the 112 caches `ehcache.xml` gives a JGroups replicator. Unit tests that build caches without a replicator don't see it. | Open, for the provider switch (P4-3). No uPortal code calls it on a replicated cache today. `DecisionCacheMarker` uses `put`, and `PermissionDecisionCacheRaceTest` gives its caches a replicator, so it fails if `putIfAbsent` comes back. |
| MOD-I405 | Security | The decision marker leaves an old answer cached in these cases, both while a node empties one of the decision caches with a "remove all". A member's answer in `HAS_UNBLOCKED_GRANT` computed from a parent group's old answer, read before the loop removed it and stored after the loop removed the member's key, stays for 300 s. It needs a change to a group's permission. `AuthorizationImpl` answers an activity the database doesn't define with the cached administrator decision from `PRINCIPAL_HAS_PERMISSION`, and the same order of reads and stores keeps that answer for 180 s. The window is one loop, measured in CI at up to 26 ms. | Open, for the owner (`notes/permission-race.md`). A second removal of the marker and a second clear after each loop would close both, and so would having the fallback call the policy directly. |

## Status and next step

The coordinator accepted the marker in place of a listener, and the in-loop cases above stay an
open row for the owner. The review of `490187844` asked for the tests and documentation in
"Evidence" and in `UPGRADING-6.0.md`, which are now on the branch, and merged with landing 3
(uPortal `280a638dd`, uPortal-start `09ccc9cbb`).

Push runs of that round, all green on the first attempt:

* On uPortal `81b797067`, CI 42481092562 and DB integration 42481092533 passed, and so did DB
  copy tool 42481092542.
* On uPortal-start `b796c1f01`, Integration 42481094541 built the uPortal branch at `81b797067`
  (job 128732971575). Its Cluster job 128733840833 passed in 10 min 55 s, `permission-rounds` with the
  4 sessions following each of the 20 changes, the slowest in 1029 ms. CI 42481094558, Migration
  equivalence 42481094591, G-index 42481094552, Workflow lint 42481094532 and HTTP replay
  42481094623 passed too.
