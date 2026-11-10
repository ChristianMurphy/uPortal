# Handoff: second-level cache region names and missing regions (P4-3 step S2)

Branch `ccr-43ba0785-hzlrj5-l2-regions` in uPortal, based on the integration branch at
`7b24794905`. uPortal-start has a branch of the same name at its tip `39ca485901`, without a
change, so that its runs build this uPortal branch.

The step is S2 of `notes/p4-3-plan.md`, with the owner's answer to Q3 (MOD-D56): configure every
region in use, delete the dead entries and annotations, `fail` in tests and `create-warn` in
production.

## Status

| Commit | Subject | State |
| --- | --- | --- |
| 1 | `fix(cache): rename the default query regions to the hibernate 5.6 names` | done |
| 2 | `fix(cache): give every hibernate region its own ehcache entry` | done |
| 3 | `refactor(cache): drop region entries and annotations that nothing uses` | done |
| 4 | `test(cache): fail a test on a region without an ehcache entry` | done |
| 5 | `fix(portlet): mark the portlet lifecycle entries immutable` | done |
| 6 | `docs(upgrading): say what a site's own ehcache.xml needs for 6.0` | done |
| 7 | `test(cache): run a cached aggregation query against its region` | done |

## The regions that the units build

A scratch test booted `PortalDb` and `AggrEventsDb` on the caches of `ehcache.xml` with
`missing_cache_strategy=create` and listed `sessionFactory.getCache().getCacheRegionNames()`.
`PortalDb` and `AggrEventsDb` build 98 entity, collection and natural-id regions between them,
and one default region for query results each. The DAOs that call `createCachedQuery` read 23
named query regions.

The list differs from Appendix A of the plan in three places:

* `@NaturalIdCache` is on each PAGS definition entity, and none of them has a `@NaturalId`.
  Hibernate doesn't build a natural-id region for them, and they don't need an entry. The
  annotations are removed.
* `PortletList.items` is a cached collection (`PortletList.java:72`) and had no entry. The plan
  didn't list it.
* `PortletList`, `PortletListItem` and `PortletLayoutAggregationImpl` do have `@Cache`, written
  with the package name. They aren't `@Cacheable`-only.

So 12 regions lacked an entry, where the plan counted 14. S1's portal log had 11 HHH90001006
lines: the 11 regions built at startup. The twelfth, `PortletList.Query`, is built by its first
query.

## What changed for each region

`ehcache.xml` and `ehcache-no-jgroups.xml` get the same change, and the second file has no
replicators. Both files had 184 caches and now have 182. "inval" means that removals and updates
are replicated as removals (`replicatePuts=false, replicateUpdates=true,
replicateUpdatesViaCopy=false, replicateRemovals=true`), as on the regions beside them. `…` stands
for `org.apereo.portal.` and `A.…` for `AggrEvents.org.apereo.portal.events.aggr.`.

| Region | Before | After |
| --- | --- | --- |
| `org.hibernate.cache.internal.StandardQueryCache` | 1000, TTL 300, not replicated | renamed `default-query-results-region`, same settings |
| `org.hibernate.cache.spi.UpdateTimestampsCache` | 1000, no expiry, not replicated | renamed `default-update-timestamps-region`, same settings |
| `AggrEvents.org.hibernate.cache.internal.StandardQueryCache` | 100, TTI 300, not replicated | renamed `AggrEvents.default-query-results-region`, same settings |
| `AggrEvents.org.hibernate.cache.spi.UpdateTimestampsCache` | 100, no expiry, replicated by copy | renamed `AggrEvents.default-update-timestamps-region`, same settings |
| `…portlet.dao.jpa.PortletLifecycleEntryImpl` | no entry. From the defaultCache: 1000, TTL 600 and no replication | own entry: 1000, TTL 600, inval |
| `…portlet.dao.jpa.PortletDefinitionImpl.lifecycleEntries` | no entry | own entry: 1000, TTL 600, inval |
| `…layout.profile.dao.jpa.ProfileSelection` | no entry | own entry: 1000, TTL 600, inval |
| `…layout.profile.dao.jpa.ProfileSelection##NaturalId` | no entry | named `ProfileSelection-NaturalId` in the annotation, own entry: 1000, TTL 600, inval |
| `…dao.portletlist.jpa.PortletList` | no entry | own entry: 1000, TTL 600, inval |
| `…dao.portletlist.jpa.PortletList.items` | no entry | own entry: 1000, TTL 600, inval |
| `…dao.portletlist.jpa.PortletListItem` | no entry | own entry: 1000, TTL 600, inval |
| `…dao.portletlist.jpa.PortletList.Query` | no entry | own entry: 1000, TTL 600, inval |
| `A.…action.SearchRequestAggregationImpl` and its `-NaturalId` | no entry | own entries: 1000, TTL 600, not replicated |
| `A.…portletlayout.PortletLayoutAggregationImpl` and its `-NaturalId` | no entry | own entries: 1000, TTL 600, not replicated |
| `…layout.dlm.providers.AllUsersEvaluatorFactory`, `AttributeEvaluator`, `GroupMembershipEvaluator`, `GuestUserEvaluatorFactory`, `ProfileEvaluator` | entries that no region used: the subclasses use the region of `Evaluator` | removed |
| `…layout.dlm.FragmentDefinition.evaluators`, `…layout.dlm.providers.Paren.evaluators` | entries that no region used: the role is `EvaluatorGroup.evaluators` | removed |
| `…portlet.dao.jpa.MarketplaceRatingImpl` | entry for an entity without `@Cache` | removed. `MarketplaceRatingImpl.Query` stays |
| `…portlet.dao.jpa.PortalCookieImpl.Query` | entry that no cached query reads | removed |
| `A.…login.LoginAggregationImpl.Query`, `concuser.ConcurrentUserAggregationImpl.Query`, `tabrender.TabRenderAggregationImpl.Query`, `portletexec.PortletExecutionAggregationImpl.Query`, `session.EventSessionImpl.Query` | entries that no cached query reads | removed |
| the other 86 regions and 22 query regions | own entry | unchanged |

The new entries keep the size and expiry the regions had from the defaultCache, so a portal with one node
behaves as before. Whether 1000 entries and ten minutes suit them is a sizing question that this
step leaves alone (see "For the owner"). The new aggregation regions aren't replicated, as the
aggregation regions beside them aren't.

Annotations removed: `@NaturalIdCache` on `PersonAttributesGroupDefinitionImpl`,
`PersonAttributesGroupTestDefinitionImpl` and `PersonAttributesGroupTestGroupDefinitionImpl`, and
`@Cacheable` with `@Cache` on the embeddable `PortletLocalizationData`. The eight `Evaluator`
subclasses lost theirs in `-h56-query-budget` (MOD-I437).

This step leaves the application caches without a reader alone (plan, 1.3). They aren't
Hibernate regions, and step S4 lists every cache.

`cacheContext.xml` gives `mergeViewCacheClearer` the two new timestamps names in
`excludedCacheNames`. `MergeViewCacheClearerTest` uses cache names of its own and needed no
change. No other file in uPortal or uPortal-start lists the old names, apart from two research
notes that describe Hibernate 4.3.

## The tests

`HibernateCacheRegionsTest` (uPortal-webapp) has four tests. None opens a JGroups channel: the
cache manager is built from the parsed file without its peer provider, and each cache keeps its
settings and listeners.

* `everyRegionHasAnEntry` boots `PortalDb` and `AggrEventsDb` with plain JPA on a private
  in-memory HSQLDB, on the caches of `ehcache.xml` and then of `ehcache-no-jgroups.xml`, with
  `hibernate.cache.ehcache.missing_cache_strategy=fail`. Hibernate builds a named query region
  at its first query. For that reason the test also gets `<entity>.Query` from the session factory's
  cache for every entity that a DAO with cached queries selects from. It finds those DAOs in the main sources (`createCachedQuery(` and
  `.from(X.class)`), which is 23 regions today.
* `theDefaultRegionsUseTheCurrentNames` fails when a file lacks a new default name or still has
  an old one. With `fail`, Hibernate still accepts the old names (HHH90001007).
* `aMergeKeepsTheTimestampsRegions` reads `cacheContext.xml` and fails when a timestamps region
  isn't in `excludedCacheNames`.
* `aCachedAggregationQueryUsesItsOwnRegion` is the check of MOD-I125 below.

Every test JVM gets `hibernate.cache.ehcache.missing_cache_strategy=fail` as a system property
(`build.gradle`). Hibernate's `Environment` puts system properties over `hibernate.properties`,
so the portal keeps `create-warn`. A persistence unit's own properties win over both.
`jpaPortalCacheRaceTestContext.xml` and `jpaPortalSecondLevelCacheTestContext.xml` use an Ehcache
file with only a defaultCache and set `create` for themselves.

### Before the fix and with seeded faults

On the integration tip, the first three tests failed with these messages:

```text
ehcache.xml lacks default regions expected:<[]> but was:<[AggrEvents.default-query-results-region,
  AggrEvents.default-update-timestamps-region, default-query-results-region,
  default-update-timestamps-region]>
AggrEventsDb doesn't start with the caches of ehcache.xml. Give the region its own <cache> entry
  in both Ehcache files: On-the-fly creation of Ehcache Cache objects is not supported
  [AggrEvents.org.apereo.portal.events.aggr.portletlayout.PortletLayoutAggregationImpl]
cacheContext.xml must list AggrEvents.default-update-timestamps-region in excludedCacheNames
```

Faults seeded on the finished branch and reverted, never committed:

| Fault | Result |
| --- | --- |
| `PortletLifecycleEntryImpl` removed from `ehcache.xml` | `everyRegionHasAnEntry` fails: "PortalDb doesn't start with the caches of ehcache.xml. … On-the-fly creation of Ehcache Cache objects is not supported `[org.apereo.portal.portlet.dao.jpa.PortletLifecycleEntryImpl]`" |
| `ProfileSelection` removed from `ehcache-no-jgroups.xml` | Both tests of `PortletPreferencesCacheTest` fail with "… not supported `[org.apereo.portal.layout.profile.dao.jpa.ProfileSelection]`". That context doesn't set a strategy of its own, which shows that the test JVM had the system property |
| `PortletList.Query` removed from `ehcache.xml` | `everyRegionHasAnEntry` fails: "Cached queries read these regions, and ehcache.xml has no <cache> entry for them expected:<[]> but was:<`[org.apereo.portal.dao.portletlist.jpa.PortletList.Query]`>" |
| `default-query-results-region` renamed back in `ehcache.xml` | `theDefaultRegionsUseTheCurrentNames` fails: "ehcache.xml lacks default regions … `[default-query-results-region]`" |
| The old aggregation timestamps name in `cacheContext.xml` | `aMergeKeepsTheTimestampsRegions` fails with the missing name and the list it found |
| `AggrEvents.…QuarterDetailImpl.Query` removed from `ehcache.xml` | `aCachedAggregationQueryUsesItsOwnRegion` fails: "… not supported `[AggrEvents.org.apereo.portal.events.aggr.dao.jpa.QuarterDetailImpl.Query]`" |

The scan for query regions has a limit. It reads `.from(X.class)` in the DAO's source. It doesn't
find a cached query whose root is written another way, and that query's missing region would only
warn in the portal.

## HHH90001003

`PortletLifecycleEntryImpl` is cached read-only and wasn't marked immutable, which Hibernate 5.6
reports once at startup. The class has no setter, and `PortletDefinitionImpl.updateLifecycleState`
only adds and removes entries. It is now marked `@Immutable`. `PortletLifecycleCacheTest` saves a
lifecycle with the second-level cache on, changes the state so that a scheduled entry is removed,
and reads both results twice. The DDL and persistence goldens didn't change.

## MOD-I125: the prefix twice

`aCachedAggregationQueryUsesItsOwnRegion` runs a query in `AggrEventsDb` with the two hints of
`BaseJpaDao.createCachedQuery`, on the caches of `ehcache.xml` with `fail`. The result is stored
in `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.QuarterDetailImpl.Query` (one entry after
two runs), and the cache manager has no cache whose name starts with `AggrEvents.AggrEvents.`.
With `fail`, Hibernate would also have refused to build a region with the prefix twice, since no
such entry exists. The portal log check is under "Portal log".

## Portal log

A local portal from this branch (uPortal `bc1acd07c4`, uPortal-start `39ca485901`, HSQLDB, ports
8480, 8405 and 8487), started once on JDK 21.0.12 and once on JDK 11. Each time: `portalInit` or
`dataInit`, `tomcatStart`, a sign-in as `admin` and as `student`, `GET /api/portlet-list/` (the
first query of `PortletList.Query`), and a wait until `UP_EVENT_AGGR_STATUS` showed a finished
aggregation pass (14:11:27 on JDK 21, 14:25:46 on JDK 11). No browser was used.

| Message | S1's log | JDK 21 | JDK 11 |
| --- | --- | --- | --- |
| HHH90001007 (old default region names) | 4 | 0 | 0 |
| HHH90001006 (region built from the defaultCache) | 11 | 0 | 0 |
| HHH90001003 (read-only cache, mutable entity) | 1 | 0 | 0 |
| HHH020100 (the Ehcache provider is deprecated) | 2 | 2 | 2 |
| a cache or log line with `AggrEvents.AggrEvents.` | 0 | 0 | 0 |

The counts are for `portal.log` and `catalina.out`. `dataInit` logged HHH020100 twice and none
of the other three, on both JDKs. After the aggregation pass, the Cache Manager portlet listed 49
caches whose name starts with `AggrEvents.`, the same 49 names as `ehcache.xml`, and the eight
new `PortalDb` regions under their own names.

MOD-I125's doubled prefix is settled: the aggregation ran its cached queries in a portal on both
JDKs and built no extra region, and the test above shows where a cached query's result goes.

## uPortal-start

No file in uPortal-start overrides `ehcache.xml` or lists a region name. `etc/portal/uPortal.properties`
only offers `org.apereo.portal.ehcache.filename`, and the cluster test's `replication-off` profile
sets it to `ehcache-no-jgroups.xml`. The branch of the same name is pushed without a change.

## Evidence

Local, with Maven Central replaced by the approved mirror through an init script outside the
repositories (Maven Central answered 429):

* JDK 21: `spotlessCheck` and `test` of every module. 1884 tests ran, none failed, 77 were skipped
  (76 vendor tests in uPortal-webapp, as on the tip). `:uPortal-webcomponents:vpTest` failed
  because Chromium 1248 isn't installed here (MOD-I373). After commit 7, the four tests of
  `HibernateCacheRegionsTest` passed.
* JDK 25: `test` of every module without `vpTest`. 1885 ran, none failed, 77 skipped.
* SpotBugs (`spotbugsMain`) on uPortal-content-portlet, uPortal-groups-pags and
  uPortal-layout-core passed. The check of commit messages, Vale on the commits and on
  UPGRADING-6.0, remark and the freeze store check passed (335, 0 and 14 recorded uses, unchanged).
* The seeded faults in the table above, each with uPortal-webapp's tests on JDK 21.
* `tests/query-budget/run.sh` with the portal on JDK 11, after a new `dataInit`: all 14 requests
  within their budgets (student-login 100 and 12, admin-login 60 and 12, guest-welcome 7 and 4).
* The portal log checks under "Portal log".

One result needs a word. A first budget run on a database in which an aggregation pass had
already run put guest-welcome at 12 and 10 statements, over its budget of 6 and 4. The ten
statements of a warm request all come from the Portal Activity portlet on the guest page, which
reads the login, concurrent user and search aggregations through `JpaBaseAggregationDao`'s
queries. Those use `createQuery` and were never cached, on this branch or before. On a new
database the dimensions don't exist yet and the portlet doesn't run a query, which is the state CI
measures. See the proposed row.

`run.sh` leaves its p6spy settings in the deployed `portal/uPortal.properties`. A `dataInit`
after it fails with `ClassNotFoundException: com.p6spy.engine.spy.P6SpyDriver` until those lines
are removed or the portal is deployed again, as the script's header says.

The CI runs are in the report to the coordinator.

## For the owner

* **Sizes of the twelve new entries.** They keep the defaultCache's 1000 entries and ten minutes.
  `PortletLifecycleEntryImpl` and `lifecycleEntries` belong to `PortletDefinitionImpl`, which is
  cached for six hours. A definition that is read from the cache then still loads its lifecycle
  entries from the database once they are older than ten minutes. Giving them the definition's
  lifetime would save those statements. It is a change in behaviour, so it isn't made here.
* **`create-warn` in production** stays, as decided. With it, a new cached query whose region has
  no entry warns once, at the first query, and runs unreplicated.

* **The guest page after an aggregation pass.** See the last proposed row. The budget job
  doesn't see it, because it measures a portal that hasn't aggregated yet.

## Proposed LOG rows

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I? | Caching | Step S2 of the P4-3 plan. The default query regions of both units had their Hibernate 4 names (HHH90001007), and `mergeViewCacheClearer` excluded the timestamps regions by those names. Of the regions in use, 12 had no `ehcache.xml` entry (HHH90001006) and didn't replicate: the portlet lifecycle entries and their collection, `ProfileSelection` and its natural id, `PortletList` with its items, item entity and query region, and two aggregation entities with their natural ids. The plan counted 14: the three PAGS natural-id regions aren't built, and `PortletList.items` was missed | **Fixed** in `-l2-regions`: renamed, twelve entries added with the defaultCache's size and expiry and the replication of their neighbours, `HibernateCacheRegionsTest`, and `missing_cache_strategy=fail` in every test JVM. Production keeps `create-warn` |
| MOD-I? | Caching | A named query region is built at the first query that uses it. A missing entry doesn't show at startup, and with `fail` it fails the query | `HibernateCacheRegionsTest` asks for the query region of every entity that a DAO with cached queries selects from. UPGRADING-6.0 tells sites to sign in before they read the log |
| MOD-I424 (update) | Caching | 14 dead Hibernate entries are removed from both Ehcache files, with `@NaturalIdCache` on the three PAGS entities (they have no `@NaturalId`) and the cache annotations on the embeddable `PortletLocalizationData`. The unused application caches stay for step S4 | Done for the Hibernate regions |
| MOD-I125 (update) | Runtime | HHH90001006 and HHH90001007 are gone. HHH90001003 is gone: `PortletLifecycleEntryImpl` is `@Immutable`. A cached aggregation query stores its result in the region with the prefix once, and no `AggrEvents.AggrEvents.*` cache exists (`HibernateCacheRegionsTest`). HHH020100 stays until the provider step | Done, apart from HHH020100 (S6) |
| MOD-I? | Performance | Once an aggregation pass has filled the date and time dimensions, each render of the guest welcome page runs 10 statements, where the budget is 4: the Portal Activity portlet reads the login, concurrent user and search aggregations with uncached queries (`JpaBaseAggregationDao`). Seen locally on `-l2-regions` with `tests/query-budget/run.sh` on a database that had aggregated (12 first, 10 warm). Not caused by that branch: the queries were never cached. The "Query budgets" job measures a new portal, before any aggregation, and passes | Open. Not checked on the tip. Options: cache the portlet's report, or let the budget job wait for one aggregation pass |
