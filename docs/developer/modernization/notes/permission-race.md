<!-- markdownlint-disable -->

# A permission check racing a permission change kept the old decision (MOD-I?)

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

So node B applied the grant within about a second, as it did the revocation, but one decision on
node B kept the old answer. `PRINCIPAL_HAS_PERMISSION` keeps an entry for 180 s, and the session gave up
after 150 s.

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
   between the two removals on node B. If node A's queue timer fires between its two
   `notifyRemoveAll` calls (node A removes every `HAS_UNBLOCKED_GRANT` key between them), node A
   sends the two removals in two lists about a second apart. That is the likely way the
   window got wide enough for a session that asks once a second. The logs of run 42458398202
   can't show it: the replication messages weren't logged then (the cluster test now logs them).
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

What it costs: on a miss, three more reads of the marker in `HAS_UNBLOCKED_GRANT` (one before, two
around the put), and after each permission change one `put` of a new marker. Hits cost the same as
before. Each permission change sends three more removals of one key each. When a marker expires
with the cache's 300 s TTL, a check running at that moment doesn't store its result.

What it doesn't close: within one "remove all" of `HAS_UNBLOCKED_GRANT`, a principal's answer is
computed from its parent groups' answers in the same cache. A check that reads a group's old answer
before the loop removes it, and stores the member's answer after the loop removed that key, keeps
the old answer until the entry expires. Both happen while one loop runs, and only when the change
was to a group's permission. The proposed listener has the same gap, because it hears of the
removal after the loop.

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

### Checks before the push

Run locally on the final code (Gradle 9.8.1 on JDK 25, `--offline`):

* `:uPortal-security:uPortal-security-permissions:test`: `AuthorizationImplCacheTest`, 7 of 7 pass,
  unchanged.
* `:uPortal-webapp:test`: all 471 run pass or are skipped (76), among them `MigrationFreezeRulesTest`
  and `CacheConfigurationCoverageTest`.
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

CI runs: in progress, see the next step.

## Proposed LOG rows

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I? | Security | **Cache bug.** A permission check that raced a permission change could keep the old decision after the change, on the node that made the change or on another. It lasted until the entry expired (180 s in `PRINCIPAL_HAS_PERMISSION`, 300 s in `HAS_UNBLOCKED_GRANT`, 6 hours for the principal's cached permission set). On another node a check between the replicated clears of `PRINCIPAL_HAS_PERMISSION` and `HAS_UNBLOCKED_GRANT` (emptied in that order) recomputed the decision from the old answer, and on any node a check that read the permissions before the clears stored its result after them. Found by uPortal-start's cluster test (run 42458398202, job 128662855202, `permission-session`: a session on node B denied 150 s after the grant), once in 24 runs since MOD-I212. `PermissionDecisionCacheRaceTest` fails 6 of 6 on the integration branch. | **Fixed** (`permission-race` 352aec7d7): `HAS_UNBLOCKED_GRANT` is emptied first, and a marker entry in it (`DecisionCacheMarker`), removed before each step of a change and replicated, keeps a check's result out of the three caches when a change happened while it ran. It uses Spring's `Cache` API, without an Ehcache listener (MOD-D43). uPortal-start's cluster test adds `permission-rounds` and logs the cache replication messages. |
| MOD-I? | Caching | Ehcache 2 refuses `putIfAbsent` and the other compare-and-set operations on a cache with a replicator, so `Cache.putIfAbsent` through Spring throws `CacheException` on the 112 caches `ehcache.xml` gives a JGroups replicator. Unit tests that build caches without a replicator don't see it. | Open, for the provider switch (P4-3). No uPortal code calls it on a replicated cache today. `DecisionCacheMarker` uses `put`, and `PermissionDecisionCacheRaceTest` gives its caches a replicator, so it fails if `putIfAbsent` comes back. |

## Status and next step

1. Push uPortal-start `ccd93137d` (the cluster test change) before the uPortal branch exists. Its
   Integration run builds the integration tip, without the fix, and records `permission-rounds`
   and the replication log before the fix.
2. Once that run's `Build uPortal` job has chosen the tip, push the uPortal branch.
3. Push uPortal-start `afb27f197` (the docs). Its Integration run builds the uPortal branch. Its
   Cluster job must pass, with `permission-rounds`.
4. Record both runs here, then report to the coordinator.
