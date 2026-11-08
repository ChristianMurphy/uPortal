<!-- markdownlint-disable -->

# P4-3 plan: Hibernate 5.6, the JCache second-level cache and the cache provider

Branch `ccr-43ba0785-hzlrj5-p4-3-plan` in uPortal. This note is a plan, and the branch changes no
code. Plan step P4-3 of the research report (MOD-D6) reads "Hibernate 5.6.15 + `hibernate-jcache`
L2 + region renames + Ehcache 3.12 provider". This note breaks it into steps that each fit one
topic branch, and asks the owner to choose the cluster design.

Sources:

* uPortal integration tip `92f31a6aa` and uPortal-start tip `88542e157`. The uPortal tip moved to
  `2cdbc62e5` while this note was written. That landing changes the permission set import and
  doesn't touch cache, Hibernate or region code. The inventory is the same on it.
* The java-time-jpa branch at `7fd9f0291`, which is merged before P4-3 (section 1.6).
* Library facts come from the sources, jars or documentation of the version named, fetched from
  Google's Maven Central mirror (the approved exception in the agent guide) and from the Ehcache
  3 repository at tag `v3.12.0`. Maven Central itself answered HTTP 200 during this session, and
  no request got a 429. Each fact gives the class or file it was read from.
* The spike (section 5) ran in a scratch worktree, `/home/user/wt/uPortal-p43-spike`, which was
  not pushed.

## Summary

* **Hibernate 5.6.15 runs on the current Ehcache 2 cache.** hibernate-ehcache 5.6.15 still
  exists, and it works over ehcache-core 2.6.11 and its JGroups replication. The spike needed
  four code changes and one test change to compile and boot. Afterwards the 1306 tests of the
  other modules ran without a failure, and in uPortal-webapp 400 of 484 passed and 76 were
  skipped, as on the tip. The passing ones include the persistence, query and analytics goldens,
  every cache race test, `PermissionDecisionCacheRaceTest` and the freeze rules. The 8 failures
  are the DDL goldens (a new `;` statement delimiter, a new HSQLDB drop
  script, and an Oracle test fake that lacks `all_sequences`) and one transaction test.
* **So P4-3 splits in two.** The Hibernate upgrade (with the region renames) doesn't need a new
  cache provider. The provider switch is a cluster design decision. It needs the owner's choice
  first (question Q1), and moving the 27 caches still on the Ehcache 2 API (P3-3's list) comes
  before it whatever the choice.
* **No JCache provider keeps today's cluster guarantees unchanged.** Ehcache 3 clusters only
  through a Terracotta server. Infinispan's invalidation mode sends asynchronous invalidations
  without ordering, invalidates other nodes on every `put`, and its JCache `removeAll()` (which
  Spring's `JCacheCache.clear()` calls) removes only the keys of the local node. The design that
  keeps the permission fix's guarantees as they are is an ordered invalidation channel that
  uPortal owns, over the JGroups stack and `jgroups.xml` it already has, with a local store
  under it (option (d), Caffeine, or (a), Ehcache 3).
* **Recommended first topic:** `ccr-43ba0785-hzlrj5-hibernate56`, step S1 below. It moves to Hibernate
  5.6.15 on hibernate-ehcache 5.6.15 over Ehcache 2.6.11, with the spike's changes, the delimiter
  and DDL test fixes, after java-time-jpa is merged.

## 1. Inventory

### 1.1 Hibernate second-level cache regions

`PortalDb` (`properties/db/hibernate.cfg.xml:33-35`) and
`AggrEventsDb` (`hibernate-aggr-events.cfg.xml:33-35`) turn on the second-level cache, the query
cache and minimal puts. `RawEventsDb` turns both caches off
(`hibernate-raw-events.cfg.xml:34-35`). Both cached units use
`org.hibernate.cache.ehcache.SpringBeanEhCacheRegionFactory` (`persistenceContext.xml:107`,
`eventPersistenceContext.xml:68`) over the Ehcache 2 manager `uPortal.cacheManager`. The
aggregation unit sets `hibernate.cache.region_prefix=AggrEvents` (`eventPersistenceContext.xml:69`).

Appendix A lists every region with its strategy, the line that declares it and its
`ehcache.xml` entry. In numbers:

| Kind | Declared | Strategy |
| --- | --- | --- |
| Entity regions (`@Cache` on the class) | 51, of which 8 sit on subclasses of the `Evaluator` hierarchy and are ignored (HHH000482) | 30 `NONSTRICT_READ_WRITE`, 15 `READ_WRITE`, 6 `READ_ONLY` |
| Entities with `@Cacheable` and no `@Cache` | 3: `PortletList`, `PortletListItem`, `PortletLayoutAggregationImpl` | the region factory's default, `READ_WRITE` in both 5.2 and 5.6 |
| Collection regions (`@Cache` on the field) | 30 | 17 `NONSTRICT_READ_WRITE`, 13 `READ_WRITE` |
| Natural-id regions (`@NaturalIdCache`) | 24, 23 with an explicit `-NaturalId` region name | the entity's |
| `@Cache` on an `@Embeddable` | 1, `PortletLocalizationData.java:33`, which Hibernate doesn't use | |

Findings:

* **Regions without an `ehcache.xml` entry** get `defaultCache` (1000 entries, 600 s TTL, not
  replicated), with warning HHH020003 from hibernate-ehcache 5.2.18 (`EhCacheMessageLogger`): `SearchRequestAggregationImpl` and its
  natural id, the natural ids of `PortletLayoutAggregationImpl` and of the three PAGS definition
  entities, `ProfileSelection` and its natural id (`ProfileSelection##NaturalId`, the default
  name), `PortletLifecycleEntryImpl`, `PortletDefinitionImpl.lifecycleEntries`, the three
  `@Cacheable`-only entities, and the query region `PortletList.Query`. On a cluster these 14
  regions don't replicate anything.
* **Entries no region uses:** 5 of the subclass entity regions (the other 3 have no entry),
  `FragmentDefinition.evaluators` and `Paren.evaluators` (the role is
  `EvaluatorGroup.evaluators`), `MarketplaceRatingImpl` (the entity has no `@Cache`), and 6
  `.Query` regions that no cached query reads (Appendix A.2).
* `ehcache.xml` and `ehcache-no-jgroups.xml` define the same 184 caches with the same sizes and
  expiry. The second file only drops the replicators and the peer provider.

### 1.2 Query cache regions

`BaseJpaDao.createCachedQuery` (`BaseJpaDao.java:116-122`) sets the hints
`org.hibernate.cacheable` and `org.hibernate.cacheRegion`, and `getCacheRegionName`
(`BaseJpaDao.java:148-165`) names the region after the query's first root, `<entity
class>.Query`. 23 regions are read this way, from 19 DAOs. The default query and timestamps
regions use the Hibernate 4.3 names in both units. Appendix A.2 lists them with their DAO call
lines.

* Hibernate 5.6 names the defaults `default-query-results-region` and
  `default-update-timestamps-region` (`RegionFactory.java:36-37`, hibernate-core 5.6.15). Both
  hibernate-ehcache 5.6.15 (`EhcacheRegionFactory.defaultRegionName`) and hibernate-jcache 5.6.15
  (`JCacheRegionFactory.defaultRegionName`) fall back to the old names when no cache has the new
  one, with warning HHH90001007 (`SecondLevelCacheLogger`, `NAMESPACE + 7`). The legacy lists
  are in `AbstractRegionFactory.java:32-45`.
* `cacheContext.xml:60-61` names the two timestamps regions in `MergeViewCacheClearer`'s
  `excludedCacheNames`. They must be renamed with the regions. Otherwise a merge after a split
  empties the timestamps region and leaves the query results, which Hibernate then treats as
  current: a missing timestamp counts as no change (`TimestampsCacheEnabledImpl.isUpToDate`,
  hibernate-core 5.6.15).
* **MOD-I125 (`AggrEvents.AggrEvents.*` regions).** A 5.2 portal logs empty query regions with the
  prefix twice in their name. Some 5.2 path qualifies a name that is already qualified, and this
  note didn't trace which one. In 5.6, `RegionNameQualifier.qualify` (hibernate-core 5.6.15)
  returns a name that already starts with the prefix unchanged, which should end them. S1 checks
  the regions a 5.6 portal creates.
* **The `PortalDb` timestamps region isn't replicated** (`ehcache.xml:636`, "not replicated -
  used to track local modifications to tables"), and the `AggrEventsDb` one replicates by copy
  (`ehcache.xml:1442`). On a cluster, a cached `PortalDb` query result on node B is checked
  against node B's own table timestamps only. A change made on node A then doesn't invalidate
  node B's cached query results, such as the list of portlet definitions (TTL 21600 s), until
  they expire. This wasn't checked at runtime, and it is question Q5.

### 1.3 Spring caches and their `ehcache.xml` settings

Since P3-3 (MOD-D43), uPortal's own code reaches a cache through Spring's `CacheManager`, the
`ehCacheManager` bean (`EhCacheCacheManager` over the Ehcache 2 `cacheManager`,
`cacheContext.xml:40-43` and `:79-80`), except where the Ehcache 2 API is still used. Appendix B
lists the 57 application caches with their settings and the code that gets them:

| Access | Caches |
| --- | --- |
| Spring `Cache` through `CacheLookup.requireCache` | 14 |
| Spring `@Cacheable`, `@CachePut`, `@CacheEvict` | 5 (`EntityTypes` 3, `RDBMLocaleStore`, `PersistentPortletEntityWrapper`) |
| Ehcache 2 API | 27, one of them through the search API |
| `Map` through `CacheFactory`/`MapCacheProvider` | 6 (`IEntityGroup`, `IEntity`, `IPermissionSet`, the two `ResourceLoader` caches, `CPD_CACHE`) |
| Third-party library | 1 (`ConfigurablePageCachingFilter.PAGE_CACHE`, resource-server-utils) |
| No reference | 4 (`web.skin.Resources`, `Resources_DocumentFragment`, `NODEREF_CACHE`, the CAS proxy ticket cache) |

Replication over the whole file: 111 caches replicate by invalidation and 1 by copy (MOD-I404
counts 112). Of the application caches, 12 replicate by invalidation. The other 100 replicated
caches are Hibernate regions. The settings in use:

* `replicateAsynchronously=true, replicatePuts=false, replicateUpdates=true,
  replicateUpdatesViaCopy=false`: an update of an existing key goes out as a removal. Most
  caches.
* `replicatePuts=false, replicateUpdates=false, replicateRemovals=true`: removals only.
  `PRINCIPAL_HAS_PERMISSION` (`ehcache.xml:187`) and `HAS_UNBLOCKED_GRANT` (`ehcache.xml:211`).
* `replicatePuts=true, replicateUpdatesViaCopy=true`: copies.
  `AggrEvents.org.hibernate.cache.spi.UpdateTimestampsCache` only.

29 caches have a `SpringCacheEventListenerFactory` listener (13 tag tracking, 16 insufficient
size), all `listenFor="local"`. One cache, `EntityPersonAttributesGroupStore.membership`
(`ehcache.xml:1997`), is `<searchable>`. 15 caches set both a TTL and a TTI, which matters for Ehcache 3: `LAYOUT_CACHE`,
`skinManager.failureCache`, the six `LocalAccountPerson*` regions (entities, collections and
queries), the three `PortletEntityImpl` regions and the four `PortletPreference*` regions. 31
caches set neither.

### 1.4 Remaining Ehcache 2 uses and the freeze store

`MigrationFreezeRulesTest.noNewEhcache2` (`MigrationFreezeRulesTest.java:134-149`) freezes any
dependency of `org.apereo.portal..` classes on `net.sf.ehcache..`,
`org.springframework.cache.ehcache..` and `org.hibernate.cache.ehcache..`. Its store
(`archunit_store/bc2ac94a-…`) records **335 uses** at the tip, the same count as after P3-3. The
java-time-jpa branch doesn't change it. Per file, with the line of the first Ehcache import:

| Module | File | Store entries | Ehcache 2 types |
| --- | --- | --- | --- |
| utils-core | `SpringCacheEventListenerFactory.java:20` | 31 | listener factory SPI |
| rendering | `PortletCacheControlServiceImpl.java:24` | 31 | `Ehcache`, `Element`, `CacheConfiguration` (per-entry TTL) |
| utils-core | `TagTrackingCacheEventListener.java:24` | 30 | `CacheEventListenerAdapter` |
| utils-core | `CacheManagementHelper.java:20` | 26 | `Statistics`, `CacheConfiguration` (Cache Manager portlet) |
| groups-core | `RDBMEntityGroupStore.java:27` | 20 | `Ehcache`, `Element` |
| groups-pags | `EntityPersonAttributesGroupStore.java:25` | 20 | `Cache`, `Element` |
| utils-core | `MapCacheProvider.java:22` | 16 | `Ehcache`, `Element` |
| web | `MarketplaceService.java:25` | 15 | `Cache`, `Element` |
| groups-core | `EntityGroupImpl.java:23` | 15 | `Cache`, `Element` |
| layout-impl | `FragmentActivator.java:26` | 13 | `SelfPopulatingCache`, `CacheEntryFactory` |
| groups-pags | `PagsMembershipCacheAuthenticationListener.java:19` | 12 | search API (`Attribute`, `Query`, `Result`) |
| portlets | `SqlQueryPortletController.java:25` | 9 | `CacheManager.getInstance()` |
| groups-core | `LocalGroupsCacheAuthenticationListener.java:18` | 8 | `Cache`, `Element` |
| groups-core | `GroupMemberImpl.java:21` | 8 | `Cache`, `Element` |
| utils-core | `ExpiredElementEvictor.java:19` | 8 | `evictExpiredElements()` job |
| utils-core | `CachingResourceLoaderImpl.java:22` | 7 | `SelfPopulatingCache` |
| utils-core | `CacheProviderFactory.java:19` | 7 | creates unconfigured caches from `defaultCache` |
| groups-pags | `AdHocGroupTester.java:17` | 7 | `getQuiet` |
| soffit-connector | `SoffitConnectorController.java:26` | 6 | per-entry TTL from Cache-Control |
| layout-impl | `RDBMDistributedLayoutStore.java:35` | 6 | `Ehcache`, `Element` |
| utils-core | `InsufficientSizeCacheEventListener.java:17` | 6 | eviction listener |
| rdbm | `EntityManagerCacheImpl.java:28` | 6 | `Ehcache`, `Element` |
| utils-core | `EhcacheManagerBeanConfigurer.java:17` | 6 | registers each cache as a bean |
| rendering | `CachingPipelineComponent.java:24` | 6 | `Ehcache`, `Element` |
| portlets | `PortletMarketplaceController.java:40` | 5 | `Cache`, `Element` |
| utils-core | `ThreadLocalCacheEntryFactory.java:17` | 4 | `CacheEntryFactory` |
| groups-pags | `EhcacheMemberIdAttributeExtractor.java:17` | 4 | search `AttributeExtractor` |
| webapp | `RenderingPipelineConfiguration.java:30` | 2 | `Cache` beans for the transform caches |
| utils-core | `RequestCacheAspect.java:27` | 1 | `EhcacheHibernateMbeanNames.mbeanSafe` |
| utils-core | `CacheFactory.java:19`, `CacheHealthReporterService.java` | 0 | Javadoc only |
| hibernate | `org/hibernate/cache/ehcache/SpringBeanEhCacheRegionFactory.java:18` | not counted | in `org.hibernate`, outside the rule's packages |

Outside Java:

* `cacheContext.xml`: `EhcacheManagerBeanConfigurer` (`:31`), `EhCacheManagerFactoryBean`
  (`:40`), `ExpiredElementEvictor` (`:65`), `EhCacheCacheManager` (`:79`), and resource-server's
  `ConfigurablePageCachingFilter`, which takes the Ehcache `CacheManager` (`:99-102`).
* `schedulerContext.xml:83` runs the evictor every 61.9 s. `jmxContext.xml:111` registers
  Ehcache's `ManagementService`. `logback.xml:139` logs `net.sf.ehcache.distribution`.
* `ehcache.xml` and `ehcache-no-jgroups.xml` (2098 lines each), `jgroups.xml` with
  `MergeViewNotifier`, `portal.properties:158-172`, and uPortal-start's
  `etc/portal/uPortal.properties:188-191`.
* Dependencies: `ehcache-core` 2.6.11 (utils-core, hibernate, soffit-connector),
  `ehcache-jgroups3replication` 1.7.4 and JGroups 3.6.20 (webapp, utils-core),
  `cas-client-support-distributed-ehcache` 3.6.2 and `ehcache-web` 2.0.4 at runtime.

The legacy Hibernate `Criteria` rule records **0 uses** at the tip (`archunit_store/500a02c1-…`
is empty). The rule for `org.hibernate.Session` outside persistence packages records **14 uses**
(`b40bb80f-…`), and java-time-jpa keeps 14.

### 1.5 Hibernate APIs that change from 5.2.18 to 5.6.15

Each row says what uPortal uses, what changes, and what the spike saw.

| Area | uPortal at the tip | 5.2.18 to 5.6.15 | Effect |
| --- | --- | --- | --- |
| Legacy `Criteria` | none (freeze store empty, research inventory) | deprecated in 5.2, removed in 6.0 | none for P4-3 |
| `@Type` and typedefs | `global.hbm.xml` declares 6 typedefs on Jadira's SPI (`fname`, `nullSafeString`, `qname`, `json`, `stats_json`, `windowState`) and, on the tip, 5 Jadira Joda typedefs. Mappings use `fname` 7 times and `nullSafeString` 10 times (4 in `@MapKeyType`). No mapping uses `qname`, `json`, `stats_json` or `windowState`. `@Type(type = "class")` twice | `UserType` has the same methods in both versions (`org/hibernate/usertype/UserType.java`), with `SharedSessionContractImplementor` since 5.2 | Jadira `usertype.spi` 6.0.1.GA ran on 5.6.15 in the spike, and the round-trip golden, which stores every entity, passed. String `@Type` names leave in 6.0 (an open row in java-time-jpa's note) |
| Dialect resolution | `PortalDialectResolver` (`uPortal-utils-core`, `DialectResolver`) picks `MySQL5InnoDBCompressedDialect`, `MySQL55Dialect`, `PostgreSQL81Dialect`, `PostgreSQL9Dialect`, `PostgreSQL81TextClobDialect`, `Oracle12ForceClobDialect`, `SQLServer2005Dialect` | `DialectResolver` unchanged. `MySQL5InnoDBDialect` is deprecated in 5.6. `PostgreSQL81Dialect` declares CLOB as `oid` from 5.6.2 (`PostgreSQL81Dialect.java:117`, 5.6.15) | `PostgreSQL81TextClobDialect` already keeps `text`. `MySQL5InnoDBCompressedDialect` doesn't compile: `Table.getInitCommands()` and `StandardTableExporter.getSqlCreateStrings` take a `SqlStringGenerationContext` since 5.5 |
| Schema export through `MetadataSources` | `MetadataSchemaTool` (`uPortal-rdbm`) calls `SchemaExport.doExecution` and the dialect's exporters with `(object, metadata)` (`MetadataSchemaTool.java:116-161`) | the two-argument `Exporter` methods are deprecated defaults that throw `IllegalStateException` (`Exporter.java`, 5.6.15). `HibernateSchemaManagementTool` defaults `hibernate.hbm2ddl.delimiter` to `;` (5.6.15 line 139, where 5.2.18 line 115 has no default) | compiles, then fails at run time. With the context the DDL is the same on PostgreSQL, MySQL, MariaDB and SQL Server apart from the trailing `;`. `DataSourceSchemaExport` runs these statements through JDBC in `dataInit`, so the delimiter must be set to none |
| `Integrator` | `ElementCollectionDeleteIntegrator` and `DelegatingHibernateIntegrator` (`uPortal-rdbm`), listed in `META-INF/services` | `Integrator` has the same methods in both versions. The three-argument `integrate` goes in 7.0 | none. The MOD-I144 delete test passed |
| Region factory | `SpringBeanEhCacheRegionFactory extends AbstractEhcacheRegionFactory` in Hibernate's package | 5.3 replaced the cache SPI. hibernate-ehcache 5.6.15 has `org.hibernate.cache.ehcache.internal.EhcacheRegionFactory` (`RegionFactoryTemplate`), and hibernate-jcache 5.6.15 has `JCacheRegionFactory` (short name `jcache`) | doesn't compile. Section 3 lists what the 5.6 factories do |
| Configuration file | `persistence.xml` sets `hibernate.ejb.cfgfile` | still read in 5.6.15, with a deprecation warning (`EntityManagerFactoryBuilderImpl.java:511-523`). `hibernate.cfg_xml_file` replaces it | rename in S1 to quiet the warning |
| Bytecode | Javassist 3.30.2 at runtime for 5.2's proxies (`uPortal-core/build.gradle:12-14`) | 5.6 removed Javassist and uses ByteBuddy 1.12.18 (hibernate-core 5.6.15 POM) | drop the Javassist line. Tests ran with Mockito's newer ByteBuddy, so the portal's own ByteBuddy version needs a run on JDK 21 and 25 |
| Sequences at boot | `hibernate.id.sequence.increment_size_mismatch_strategy=EXCEPTION` in `hibernate.properties` | read from 5.4. At boot, Hibernate reads every sequence's increment from the database | an upgraded 5.17 database whose sequence increments differ from the mappings refuses to start. `PersistenceUnitDdlTest`'s Oracle case boots against an HSQLDB fake, which lacks `all_sequences` |
| Batch statements | test contexts use commons-dbcp 1.2.2 (from cernunnos) | `AbstractBatchImpl.clearBatch` calls `Statement.isClosed()` (5.6.15 line 183) | 85 tests failed with `IllegalAccessError` on dbcp 1.2.2's protected `isClosed()`. Production uses Tomcat's pool. A site with a pre-JDBC 4 pool breaks the same way |
| Deprecated calls | `PortalRawEventsAggregatorImpl` uses `SessionFactory.getClassMetadata` and `Cache.evictEntity`, `evictCollection`, `evictEntityRegions` | deprecated, still present in 5.6 | warnings only |
| `DATE` binding | java-time-jpa's `LocalDateColumnConverter` relies on `DATE` being bound without `hibernate.jdbc.time_zone` | `DateTypeDescriptor` is the same in 5.2.18 and 5.6.15 (only whitespace differs in the `TIME` and `TIMESTAMP` descriptors) | the trap java-time-jpa describes doesn't fire in 5.6. `InstantJavaDescriptor.unwrap` changed for years before 1905 only (HHH-13266) |

### 1.6 What java-time-jpa changes in these areas

java-time-jpa (`7fd9f0291`, 22 commits and 4 merges of the tip) is merged before P4-3. Against the tip it:

* replaces the 15 `@Type` uses of the Jadira Joda typedefs (`dateTime` 11, `monthDay` 2,
  `localTime`, `localDate`) with 7 `@Convert` uses and three `AttributeConverter`s in
  `uPortal-core` (`LocalDateColumnConverter`, `LocalTimeColumnConverter`,
  `MonthDayColumnConverter`), and removes those typedefs from `global.hbm.xml` and
  `JvmZoneLocalTimeType`. Hibernate 5.6, 6 and 7 accept JPA converters unchanged;
* drops `joda-time` and Jadira's `usertype.core` (and its Integrator). `usertype.spi` 6.0.1.GA
  stays for the six SPI user types, and its removal before Hibernate 6 is an open row of that
  branch;
* adds `JavaTimeColumnStorageTest` (uPortal-core), which stores through Hibernate into HSQLDB and
  pins the stored literals. It must pass unchanged on 5.6, and `DateTypeDescriptor` (above) says
  it will;
* doesn't change a cache annotation, a region, `ehcache.xml`, the region factory, the dialects,
  `MetadataSchemaTool` or the Ehcache freeze store (335). It removes the Joda rule from
  `stored.rules` and rewrites the 14 entries of the `Session` rule.

S1 is written for the tree after java-time-jpa. The spike ran on the tip, so S1 repeats the test
run on the merged tree.

## 2. Guarantees a provider must keep

The permission fix (MOD-I403, `DecisionCacheMarker`), MOD-I212 and MOD-D54 rest on what Ehcache
2.6.11 and ehcache-jgroups3replication 1.7.4 do today:

| ID | Guarantee | Where it comes from |
| --- | --- | --- |
| G1 | Removals and clears go to the other nodes, and puts don't (`replicatePuts=false`). Each node computes its own entries | `ehcache.xml` replicator settings |
| G2 | The removals of `HAS_UNBLOCKED_GRANT`, `IPermissionSet` and `PRINCIPAL_HAS_PERMISSION` reach another node in the order they were made, across the three caches | one asynchronous queue per interval and cache manager, applied in order by the receiver (`notes/permission-race.md`, read from the 1.7.4 jar) |
| G3 | A removal is sent even when the key isn't in the local cache. The marker goes on every node, also when the sender had none | Ehcache 2.6.11 `Cache.notifyRemoveInternalListeners` builds `new Element(key, null)` and notifies the listeners when nothing was removed (disassembled from `ehcache-core-2.6.11.jar`) |
| G4 | `clear()` (Ehcache `removeAll`) empties the cache on every node, whatever keys each node holds | the replicator's "remove all" event |
| G5 | A clear runs key by key. A check can read an old entry of the same cache while the loop runs (MOD-I405) | `MemoryStore.removeAll`. This one is a gap to close, and a provider doesn't have to keep it |
| G6 | `putIfAbsent` and the other compare-and-set calls throw on a replicated cache (MOD-I404). `DecisionCacheMarker.current()` uses `put` for that reason | Ehcache 2's CAS check |
| G7 | After a split heals, each node empties its caches, apart from the excluded ones (MOD-D54) | `MergeViewNotifier` at the top of `jgroups.xml`, `MergeViewCacheClearer` |
| G8 | Writers don't wait for other nodes. A replication problem never fails a permission change | asynchronous replication |

The tests that hold them: `PermissionDecisionCacheRaceTest` (10 cases, builds two nodes from
`ehcache.xml` and replays the removals in order, and refuses `putIfAbsent`),
`AuthorizationImplCacheTest`, `MergeViewCacheClearerTest`, `CacheConfigurationCoverageTest`, and
uPortal-start's `tests/cluster/cluster.sh`: `cluster-join`, `invalidation-portlet`,
`invalidation-layout`, `invalidation-permission`, `permission-session`, `permission-rounds`,
`split-heal`, `jobs-once`, `failover-takeover`, `failover-jobs-once`, `events-aggregated` and
`no-overlap`, with the seeded faults `replication-off`, `join-gives-up` and `merge-clear-off`.

## 3. Cluster design options

Library behaviour, by version:

* **Ehcache 3.12.0** is compiled for Java 17 (class file 61 in `ehcache-3.12.0.jar`), and 3.11.1
  for Java 8 (52). `javac --release 11` on JDK 21 compiles against the 3.12 jar (tried here), so
  only the runtime needs JDK 17 or later, after P4-2. Its clustering needs a Terracotta server
  (`docs/src/docs/asciidoc/user/clustered-cache.adoc` at `v3.12.0`: "To enable clustering with
  Terracotta, you will have to deploy a Terracotta server"), and the 3.12.0 jar doesn't contain JGroups or RMI replication classes.
  The XML `expiry-type` is a choice of one of `class`, `tti`, `ttl` or `none`
  (`ehcache-core.xsd` in the jar), so the 15 caches with both a TTL and a TTI need an
  `ExpiryPolicy` class or configuration in code. Its native API reports `EventType.EVICTED`.
* **Infinispan.** The last line with a Hibernate 5.3 to 5.6 cache module
  (`infinispan-hibernate-cache-v53`) is 13.0.22.Final (April 2024, Java 8 class files,
  JGroups 4.2.18). 14.0.35.Final (August 2025, Java 11, JGroups 5.2.28) publishes only the
  Hibernate 6 modules. 15 and 16 need Java 17, and `-v66` (Hibernate 6.6 and 7.1+) starts at
  16.0. From the 14.0.35 sources:
  * `InvalidationInterceptor.visitPutKeyValueCommand` invalidates the key on the other nodes for
    every write except `putForExternalRead`. A plain `put` evicts the other nodes' entries, so
    `replicatePuts=false` has no equal unless the caller uses `Flag.CACHE_MODE_LOCAL` or
    `putForExternalRead`.
  * An unconditional `remove` of an absent key counts as successful
    (`CallInterceptor.visitRemoveCommand`), and `handleInvalidate` sends it. That keeps G3.
  * `visitClearCommand` broadcasts the clear synchronously. That keeps G4 for the native
    `clear()`.
  * Asynchronous invalidation goes out with `DeliverOrder.NONE`
    (`InvalidationInterceptor.invalidateAcrossCluster`), with no ordering between messages, which
    breaks G2. Synchronous invalidation waits for every member before the call returns
    (`remote-timeout`, 15 s by default, `ClusteringConfiguration.REMOTE_TIMEOUT`), which keeps G2
    for one thread and breaks G8.
  * An invalidation cache accepts only `when-split=ALLOW_READ_WRITES`
    (`PartitionHandlingConfigurationBuilder.validate`, ISPN000550 in `Log.java`). A split isn't
    reconciled, and a `@Merged` cache manager listener reports the merge.
  * Its JCache `removeAll()` removes the keys of `cache.keySet()` one by one
    (`infinispan-jcache` 14.0.35, `JCache.java:507-520`), and Spring 5.3.39's `JCacheCache.clear()`
    calls `removeAll()` (`JCacheCache.java:116-118`). In invalidation mode that clears only the
    keys this node holds, which breaks G4 for `clearPermissionDecisions` and the logout purges.
    Infinispan's own Spring module (`infinispan-spring5-common` 14.0.35, `SpringCache`) maps
    `clear()` to the native `clear()` and offers `put(key, value, lifespan, unit)`.
* **hibernate-jcache 5.6.15** (`JCacheRegionFactory`): `hibernate.javax.cache.provider`,
  `.uri`, `.cache_manager` (an instance is accepted) and `.missing_cache_strategy` (default
  `create-warn` in `MissingCacheStrategy.interpretSetting`). It clears a region with JCache
  `clear()`, which doesn't notify listeners, and its `releaseFromUse` closes the cache manager, also one
  it was given.
* **Caffeine 3.3.1** and its JCache adapter are Java 11 class files (55). Caffeine has no
  replication.
* **JGroups.** 3.6.20.Final (April 2020) is the last 3.x. The 5.x jars (5.2.30 to 5.5.8) have no
  `org.jgroups.auth.MD5Token` and no `FD`, both used by uPortal's `jgroups.xml`. 5.2 to 5.4 are
  Java 11 class files, 5.5 is Java 17.

### (a) Ehcache 3 locally, with an invalidation channel that uPortal owns

uPortal sends its own removal and clear messages over JGroups, in order, and applies them to
local Ehcache 3 caches. The channel is a decorator around the JCache `CacheManager` that both
Spring's `JCacheCacheManager` and Hibernate's `JCacheRegionFactory` use. Hibernate takes it as an
instance through `hibernate.javax.cache.cache_manager`. For each replicated cache the decorator
sends `remove(key)` (also for an absent key), `removeAll()`, `clear()`, and a `put` that replaced
an entry (`getAndPut`, to match `replicateUpdates=true`), all on one channel in one FIFO stream.
In JGroups 3.6.20, `NAKACK2` assigns each sender's messages a sequence number and "Receivers
deliver messages ordered according to seqno" (`NAKACK2.java` Javadoc), and `UNICAST3` does the
same for unicasts. ehcache-jgroups relies on that order today. Receivers apply them to the local cache without sending them on.

* **Site configuration.** `jgroups.xml` doesn't change, `MergeViewNotifier` included, as long
  as JGroups stays 3.6. The cache sizes and expiry come from uPortal's region list (S4), with
  overrides per cache in `uPortal.properties`, and an Ehcache 3 XML for the local store. A
  property such as `org.apereo.portal.cache.cluster.enabled` replaces the choice between
  `ehcache.xml` and `ehcache-no-jgroups.xml`.
* **Split and heal.** As today. During the split each side sends to its own view only. On the
  MergeView each node empties its caches, Hibernate regions included, because they are in the
  same `CacheManager` (G7).
* **Sites with their own `ehcache.xml`.** The file is no longer read. A startup check fails when
  `org.apereo.portal.ehcache.filename` is set, and names the properties that replace it. A small
  converter in uPortal-start reads an old `ehcache.xml` and prints the overrides.
* **`DecisionCacheMarker`.** No change in behaviour: G1 to G4 and G7 hold by construction, and G8
  too (the channel sends asynchronously). G6 goes, because a local `putIfAbsent` is allowed. The
  race test keeps its two nodes and replaces its recording replicator with an in-memory channel.
  The `putIfAbsent` refusal case is replaced by a test that the marker is created once.
* **Size.** Main code: the JCache decorator (the JCache `Cache` interface has about 30 methods)
  400 to 500 lines, channel, messages and receiver 250, configuration from the region list 150.
  Tests: 600 to 800. Removed: `SpringCacheEventListenerFactory`, the two Ehcache listeners,
  `ExpiredElementEvictor`, `EhcacheManagerBeanConfigurer`, `CacheProviderFactory` and the
  ehcache-jgroups dependency, about 700 lines, plus the two 2098-line XML files.
* **Costs.** uPortal owns replication code, as the Jasig project owned ehcache-jgroups3replication
  (`org.jasig.ehcache`). Ehcache 3.12 has to wait for the JDK 21 runtime (P4-2). The 15 caches
  with both a TTL and a TTI need an expiry class. JGroups 3.6 is from 2020.

### (b) Infinispan as the provider, in invalidation mode

Infinispan embedded stores every cache. Clustered sites run the replicated caches as invalidation
caches over Infinispan's JGroups transport, and single servers use local caches.

* **Site configuration.** An `infinispan.xml` replaces `ehcache.xml`, with one
  `<invalidation-cache>` or `<local-cache>` per cache (`memory max-count`, `expiration lifespan`
  and `max-idle`, both allowed together), and a JGroups 5.2 stack in place of `jgroups.xml`. That
  stack needs a new `AUTH` token (no `MD5Token`) and `FD_ALL3` in place of `FD`, and
  `JDBC_PING` still exists in 5.2.
* **Spring caches.** Not through `JCacheCacheManager` (its `clear()` is node-local, see above).
  A uPortal `Cache` adapter over Infinispan's `AdvancedCache`, or a subclass of Infinispan's
  `SpringCache`, maps `put` to `withFlags(CACHE_MODE_LOCAL).put` (G1), `evict` to `remove` (G3)
  and `clear` to the native `clear()` (G4).
* **Hibernate.** On Hibernate 5.6 either Infinispan 13.0.22 with `infinispan-hibernate-cache-v53`
  (JGroups 4.2, a line that ended in 2024), or Infinispan 14 through hibernate-jcache. Through
  JCache every load into a region is a `put` that evicts the entry on the other nodes. On
  Hibernate 6.6 and 7 (P6, P7) the native `-v66` module needs Infinispan 16 and Java 17.
* **Split and heal.** Writers block: a synchronous invalidation to an unreachable member waits up
  to `remote-timeout` (15 s) until JGroups removes the member from the view, then the call
  completes on the members left. A permission change or a portlet import can fail with a
  timeout during that time. On the merge, a `@Merged` listener empties the caches (G7), and it
  must also evict the Hibernate regions if they live in another cache manager.
* **Sites with their own `ehcache.xml` and `jgroups.xml`.** Both files are rewritten. The
  converter has to produce Infinispan XML and a JGroups 5 stack.
* **`DecisionCacheMarker`.** `HAS_UNBLOCKED_GRANT`, `IPermissionSet` and `PRINCIPAL_HAS_PERMISSION` must be `INVALIDATION_SYNC` to keep G2,
  which costs G8. With asynchronous invalidation the marker loses its order guarantee and the
  race test's case 1 returns. The race test changes from an in-order replay of a queue to
  removals applied before the call returns. `putIfAbsent` becomes allowed (G6 goes).
* **Size.** Main code about 580 lines (the Spring adapter about 200, the Infinispan configuration
  from the region list about 250, and the merge listener and Hibernate setup together about 130).
  Tests about 500. uPortal-start's cluster
  harness changes more than in (a): it reads JGroups views and `net.sf.ehcache` replication lines
  today (`cluster.sh:239-249`, `:294-361`), and its `join-gives-up` fault edits `jgroups.xml`.
* **What it gives.** A maintained cluster product with per-entry lifespan, statistics, merge
  events and a path to Hibernate 7 (`-v66`), as P5-6 assumes.

### (c) Keep Ehcache 2 and JGroups for the Spring caches until P5-6, and move only Hibernate's second-level cache to JCache

Hibernate's regions are 100 of the 112 replicated caches. Moving them to a JCache provider that
doesn't replicate (Ehcache 3, Caffeine) loses their cluster invalidation, and
`invalidation-portlet` and `split-heal` would fail. The JCache wrapper for Ehcache 2
(`org.ehcache:jcache` 1.0.1, December 2014, built on Ehcache 2.8.3) could replicate the regions over
JGroups through JCache, but nobody maintains it. So (c) amounts to keeping Hibernate 5.6 on
hibernate-ehcache 5.6.15 over Ehcache 2 (steps S1 and S2) and moving the second-level cache to
JCache together with the provider, at the latest before Hibernate 6 (P6), which has no
hibernate-ehcache.

* **Site configuration.** Unchanged until the provider step.
* **Split and heal.** Unchanged (G7 as today).
* **Sites with their own `ehcache.xml`.** Unchanged in S1. S2 renames 4 entries, and the old
  names keep working with warning HHH90001007.
* **`DecisionCacheMarker`.** Unchanged.
* **Size.** S1 is about 60 lines of main code and the DDL test changes, S2 about 40 lines and the
  XML entries.
* **Cost.** Ehcache 2 and JGroups 3.6 stay longer. Spring 6 (P6) removes `EhCacheCacheManager`,
  and Hibernate 6 removes hibernate-ehcache, so the provider switch must be merged before P6.

### (d) Caffeine locally, with the invalidation channel of (a)

As (a), with Caffeine as the local store. Spring 5.3 has `CaffeineCacheManager`, and Hibernate
reads the regions through hibernate-jcache over Caffeine's JCache adapter, or through a region
factory of uPortal's own on `RegionFactoryTemplate` and a `DomainDataStorageAccess` over a
Caffeine cache (about 150 lines, and no JCache in between).

* It runs on Java 11, so it doesn't wait for P4-2.
* Caffeine takes `expireAfterWrite` and `expireAfterAccess` together (the 15 caches), and a
  per-entry `Expiry` for the portlet output caches and Soffit (the per-entry TTL that Spring's
  `Cache` lacks).
* Its `RemovalListener` reports `RemovalCause.SIZE`, which `InsufficientSizeCacheEventListener`
  needs (MOD-D43). JCache doesn't report evictions.
* Its statistics (`recordStats`) feed the Cache Manager portlet.
* Configuration, split and heal, site migration, marker and size: as (a).

### Other designs looked at

* **Infinispan in local mode everywhere, and invalidation mode for clusters** (one provider for
  both profiles). The same costs as (b) for clusters.
* **Valkey or Redis** (MOD-Q10). A removal is a network call to the server, ordered per
  connection. It needs a server per site and a near cache for the hot regions, and the Hibernate
  7 support of Redisson is unverified. It fits as a later profile behind the same region list.
* **A database table of invalidations polled by each node.** It works without multicast, but
  adds a delay of one polling interval to every invalidation and a write per removal.

### Comparison

| | (a) Ehcache 3 + own channel | (b) Infinispan | (c) defer the provider | (d) Caffeine + own channel |
| --- | --- | --- | --- | --- |
| G1 puts are local | yes | with a uPortal adapter | yes (Ehcache 2) | yes |
| G2 ordered removals | yes, one FIFO stream | only with synchronous invalidation | yes | yes |
| G3 removal of an absent key | yes | yes | yes | yes |
| G4 cluster-wide clear | yes | native `clear()` only, not through Spring's JCache adapter | yes | yes |
| G7 clear after a merge | yes, `MergeViewNotifier` unchanged | `@Merged` listener | yes | yes |
| G8 writers never wait | yes | no | yes | yes |
| Runtime JDK | 17+ (after P4-2) | 11 (Infinispan 14), 17+ (15, 16) | 11 | 11 |
| Site files rewritten | `ehcache.xml` | `ehcache.xml`, `jgroups.xml` | none until the switch | `ehcache.xml` |
| uPortal main code, roughly | +900, −700 | +600, −700 | +100 | +900 (+150 for its own region factory), −700 |
| Who maintains the clustering | uPortal | Infinispan | ehcache-jgroups (unmaintained) | uPortal |

## 4. Steps

Each step is one topic branch with its own tests. The provider steps (S6) follow the owner's
answer to Q1, and S3 to S5 come first whatever the answer.

### S1 `-hibernate56`: Hibernate 5.6.15 on the Ehcache 2 cache

* **Change.** `hibernateVersion=5.6.15.Final`. `SpringBeanEhCacheRegionFactory` extends
  hibernate-ehcache's `EhcacheRegionFactory`: it finds the cache manager by name in
  `resolveCacheManager`, and its `releaseFromUse` doesn't shut it down. The class keeps its name,
  so `persistenceContext.xml` and site overrides don't change. hibernate-ehcache's own setting
  `hibernate.cache.ehcache.cache_manager` takes a manager instance, but its `releaseFromUse` then
  shuts the shared manager down ("todo (5.3)" in `EhcacheRegionFactory`). Set
  `hibernate.cache.ehcache.missing_cache_strategy=create-warn` explicitly. Port
  `MySQL5InnoDBCompressedDialect` and `MetadataSchemaTool` to `SqlStringGenerationContext`, and
  set the script delimiter to none in `MetadataSchemaTool`. Rename `hibernate.ejb.cfgfile` to
  `hibernate.cfg_xml_file`. Drop the Javassist runtime dependency and pin ByteBuddy. Give the JPA
  test contexts a JDBC 4 pool. Fix `PersistenceUnitDdlTest`'s Oracle case (its fake must
  answer `all_sequences`, or the test sets the increment check to `NONE` with a comment), record
  the HSQLDB drop script as an intended change, and find why
  `JpaEventAggregationManagementDaoTest.testAcademicTermDetails` ends in
  `UnexpectedRollbackException`.
* **Checks.** All module tests on JDK 21 and 25, with the persistence goldens in UTC, New York and
  Sydney. A `db-integration.yml` run started by hand (the six vendors and the canaries).
  uPortal-start `Integration` on the branch of the same name: the DB harness on every vendor and
  G-index, G-migrate from 5.17 (the increment check on upgraded databases), Playwright, HTTP
  replay and the Cluster job with `permission-session`, `permission-rounds`, `split-heal` and the
  failover checks. In the portal log on JDK 11 and 21: HHH90001006, HHH90001007, HHH020100 (the
  hibernate-ehcache deprecation), HHH000482, and whether the `AggrEvents.AggrEvents.*` regions of
  MOD-I125 are gone. `dataInit` on Oracle (the delimiter).
* **Risks.** The boot-time sequence check on upgraded databases. ByteBuddy proxies in place of
  Javassist on JDK 11, 21 and 25. The aggregation query regions start to use their configured
  entries (MOD-I125). Sites whose own context uses a pre-JDBC 4 pool (UPGRADING-6.0).
* **Rollback.** Revert the topic. Nothing in the schema or the data changes: the DDL is the same
  apart from the HSQLDB drop script.

### S2 `-l2-regions`: region names and missing regions

* **Change.** Rename the 4 default regions in both Ehcache files to
  `default-query-results-region` and `default-update-timestamps-region` (with `AggrEvents.` for
  the aggregation unit), and the 2 names in `cacheContext.xml:60-61`. Configure the 14 regions of
  1.1 that have no entry, and drop the dead entries and annotations (Q3). Set
  `hibernate.cache.ehcache.missing_cache_strategy=fail` in every test JVM, and add a test that
  boots both cached units against the real `ehcache.xml` with `fail`.
* **Checks.** The new test, shown to fail with a seeded fault (an entry removed), with the
  region in its message. `MergeViewCacheClearerTest` with the new excluded names. The Cluster job (`split-heal`).
  uPortal-start `Integration`.
* **Risks.** A site's own `ehcache.xml` with the old names keeps working with HHH90001007. One
  that lacks a newly required region gets `create-warn` in production (Q3).
* **Rollback.** Revert. The old names work with 5.6.

### S3 `-cache-api-<module>`: the remaining Ehcache 2 callers onto Spring's cache API

Several topics, one per module group, in the order of `notes/spring-cache.md` ("Remaining"):
plain get, put and remove first (groups, layout store, transform caches, marketplace, SQL query
portlet, entity manager cache), then the self-populating caches (`FragmentActivator`,
`CachingResourceLoaderImpl`), then interfaces for what Spring's `Cache` can't express: per-entry
expiry for the portlet output caches and Soffit, a tag decorator in place of
`TagTrackingCacheEventListener`, a statistics interface for the Cache Manager portlet, the PAGS
membership purge by tag in place of the search API, `MapCacheProvider` over Spring's `Cache` that
fails on an unknown name, and a replacement for resource-server's page caching filter.

* **Checks per topic.** The module's tests and a seeded fault for each moved cache, the freeze
  store count going down (`check-freeze-store.py`), Playwright for the portlets touched, and the
  Cluster job for the replicated caches moved.
* **Risk.** The tag purge fires at another moment than the Ehcache listener did.
* **Rollback.** Per topic.

At the end, the Ehcache freeze store holds the adapters only (listener factory, evictor, bean
configurer, statistics), about 70 entries.

### S4 `-cache-regions`: one region list

One file lists each cache with its name, size, TTL, TTI, replication mode and listeners. `ehcache.xml`
is checked against it (or generated from it), and `CacheConfigurationCoverageTest` checks the
code against it. Still Ehcache 2. **Checks:** a test that each entry of the list matches
`ehcache.xml`, with a seeded fault. **Rollback:** revert.

### S5 `-decision-marker-loops`: MOD-I405

Independent of the provider: remove the marker again and clear again after each loop, or let the
fallback call the policy (the owner's choice in MOD-I405). Doing it before S6 means the provider
switch doesn't have to keep G5. **Checks:** two new cases in `PermissionDecisionCacheRaceTest`
for the in-loop reads, failing before the change.

### S6 the provider (after Q1)

With (d) or (a):

1. **S6a `-cache-channel`.** The uPortal invalidation channel, on JGroups 3.6 and `jgroups.xml`,
   while the local store is still Ehcache 2: the replicators leave `ehcache.xml`, and the channel
   wraps both the Spring caches and the Hibernate regions (a `DomainDataStorageAccess` wrapper in
   `SpringBeanEhCacheRegionFactory`). This step changes the clustering and nothing else.
   **Checks:** `PermissionDecisionCacheRaceTest` on the channel, unit tests for ordering, absent
   keys and clears, and the full Cluster job, `permission-rounds` with 50 rounds
   (`--permission-rounds 50`) and every seeded fault. **Rollback:** revert, `ehcache.xml` gets its
   replicators back.
2. **S6b `-cache-provider`.** Caffeine (or Ehcache 3.12 after P4-2) under Spring's
   `CacheManager` and under Hibernate (hibernate-jcache or uPortal's region factory), built from
   the region list. Ehcache 2, `ehcache.xml` and `ehcache-jgroups3replication` go, the Ehcache
   freeze store reaches 0 and the rule becomes a plain rule. Hibernate's missing cache strategy
   becomes `fail`.
   **Checks:** S1's checks and the Cache Manager portlet in Playwright. The converter for
   site files gets a test on uPortal 5's `ehcache.xml`. **Rollback:** revert S6b, S6a keeps
   working on Ehcache 2.
3. **S6c `-jgroups5`** (or with P5-6): JGroups 5.2, a `jgroups.xml` without `MD5Token` and `FD`,
   `MergeViewNotifier` on the JGroups 5 `Protocol` API, and `HashedDaoAuthToken` checked against JGroups 5's `AuthToken`. **Checks:** the Cluster job with `cluster-join`, `join-gives-up` and
   `split-heal`.

With (b): S6a moves the local caches to Infinispan local caches behind the uPortal Spring adapter,
S6b turns on invalidation caches with a JGroups 5.2 stack and the `@Merged` listener, and S6c
moves the Hibernate regions. The cluster harness changes in S6b.

### Order and dependencies

S1 follows java-time-jpa. S2 follows S1. S3's topics and S5 don't depend on S1 and can run in
parallel with it. S4 follows S3. S6 follows S2 to S5 and the owner's Q1, and S6b with Ehcache 3
also follows P4-2. All of S6 must be merged before P6 (Spring 6, Hibernate 6.6).

## 5. Spike: Hibernate 5.6.15 with the current Ehcache 2 region factory

In `/home/user/wt/uPortal-p43-spike`, detached at `92f31a6aa`, two local commits, not pushed.
JDK 21, Gradle 9.8.1, dependencies through the approved Google mirror init script and tests with
`--offline` after that. Maven Central didn't refuse any request.

1. `hibernateVersion=5.6.15.Final`. The compile stopped at two modules:
   * `SpringBeanEhCacheRegionFactory.java:26`: `AbstractEhcacheRegionFactory` doesn't exist.
   * `MySQL5InnoDBCompressedDialect.java:49`: `Table.getInitCommands()` needs a
     `SqlStringGenerationContext`.
2. Commit `26ebbb060`: the region factory as a subclass of `EhcacheRegionFactory` (by name, no
   shutdown), and the dialect on the three-argument exporter. Everything compiles, main and test.
   `uPortal-webapp`'s runtime classpath has hibernate-core 5.6.15, ByteBuddy 1.12.18,
   ehcache-core 2.6.11, ehcache-jgroups3replication 1.7.4 and JGroups 3.6.20, and no ehcache
   2.10.6 (uPortal-hibernate's exclusion still applies).
3. All tests: 1790 ran. Every module but uPortal-webapp passed (1306 tests, 1 skipped as on the
   tip). uPortal-webapp: 92 of 484 failed.
   * 85 with `IllegalAccessError: 'boolean
     org.apache.commons.dbcp.DelegatingPreparedStatement.isClosed()'`. The JPA test contexts'
     `BasicDataSource` is commons-dbcp 1.2.2 (through cernunnos), which predates JDBC 4.
   * 7 `PersistenceUnitDdlTest` cases with `IllegalStateException: getSqlCreateStrings() was not
     implemented!` from `MetadataSchemaTool.getCreateScript` (`MetadataSchemaTool.java:138`).
   * The web component `vpTest` needs Chromium 1248, which this machine doesn't have. It was
     excluded.
4. Commit `c4bc7dba4`: `MetadataSchemaTool` builds a `SqlStringGenerationContext`
   (`SqlStringGenerationContextImpl.fromConfigurationMap`), and the test configurations force
   commons-dbcp 1.4. In uPortal-webapp 400 of the 484 tests then passed, 8 failed, and 76 were skipped.
   * Passing: `EntityRoundTripGoldenTest` (6), `AggregationGoldenTest` (2),
     `DaoQueryGoldenTest` (1), `PermissionDecisionCacheRaceTest` (10), the 4 race tests of the
     entity caches, `MigrationFreezeRulesTest` (5), `CacheConfigurationCoverageTest` (2).
   * `PersistenceUnitDdlTest`, 7 cases. PostgreSQL 16, MySQL 8.4, MariaDB 10.6 and 11.4 and SQL
     Server 2022 differ only by a `;` at the end of each statement (5.6's default delimiter). On
     HSQLDB the drop script uses `drop table if exists … CASCADE` in place of dropping the foreign
     keys first (151 lines out, 84 in). Oracle 23 doesn't boot in the test: "Could not fetch the
     SequenceInformation from the database", `select * from all_sequences` against the HSQLDB
     fake.
   * `JpaEventAggregationManagementDaoTest.testAcademicTermDetails`: `UnexpectedRollbackException`
     after the expected `IllegalArgumentException`. The spike didn't find the cause.

The spike didn't start a portal, so the portal's runtime on 5.6 (ByteBuddy proxies, the region
factory's warnings, `dataInit`) and the other vendors are S1's checks.

## 6. Questions for the owner

**Q1. Which cluster design replaces Ehcache 2 and ehcache-jgroups?**

* (a) Ehcache 3.12 locally with an invalidation channel of uPortal's own. Keeps G1 to G4, G7
  and G8, needs JDK 17 at runtime.
* (b) Infinispan, invalidation mode. Keeps G2 only with synchronous invalidation (writers wait,
  up to 15 s on a split), needs a uPortal Spring adapter for G1 and G4, and rewrites `jgroups.xml`.
* (c) No provider switch in P4-3. Hibernate 5.6 runs on Ehcache 2, and the provider moves in
  P5-6, before P6.
* (d) Caffeine locally with the channel of (a). Keeps the same guarantees as (a) on Java 11, with
  both TTL and TTI, per-entry expiry and eviction causes.

**Recommendation:** (c) for P4-3 itself (S1, S2), then (d) for S6. The channel keeps every
guarantee the permission fix tested, without change to `DecisionCacheMarker`, `jgroups.xml` or
the cluster test's JGroups checks. Infinispan or Valkey (MOD-Q10) can come later as a second profile
behind the same region list, if a site needs one. The report's "Ehcache 3.12
provider" then becomes Caffeine. If the owner prefers a provider that the project doesn't
maintain itself, (b) with synchronous invalidation on the three permission caches is the
alternative, and G8 has to go.

**Q2. Does P4-3 split as proposed?** The phase table bundles the Hibernate upgrade, the
`hibernate-jcache` second-level cache, the renames and the provider. Kept together, they
form one large step with two kinds of risk at once. Split, S1 and S2 form P4-3, and the provider
moves to S6 under P5-6. **Recommendation:** split. The spike shows that S1 doesn't need the provider.

**Q3. What happens to a region without configuration?** Options: `fail` in production as well
as in tests, `create-warn` in production and `fail` in tests, or `create` (silent). The 14
regions without an entry can get one. The annotations of the 8 ignored subclass entities and of
the embeddable can go, with the entries nothing uses. **Recommendation:**
configure every region in use, delete the dead annotations and entries, `fail` in tests,
`create-warn` in production for 6.0 because of sites' own `ehcache.xml`, and `fail` when the
provider changes (S6b).

**Q4. Where do sites set cache sizes after S6?** Either a provider file (Ehcache 3 XML or
`infinispan.xml`) replaces `ehcache.xml`, or properties per cache in `uPortal.properties`
override uPortal's region list. A site could also use both. **Recommendation:** properties over the region list, with a
converter for uPortal 5 `ehcache.xml` files. The region list stays the one source.

**Q5. Should `PortalDb`'s query results follow changes made on another node?** The timestamps
region isn't replicated (1.2), so a cached query on node B can miss a portlet published on node
A until the region's TTL (6 hours for portlet definitions). Not checked at runtime. Options:
replicate the timestamps region by copy, as `AggrEventsDb` does, or leave it and document it.
**Recommendation:** first a cluster check in uPortal-start (node A publishes a new portlet, node
B's portlet list must show it within 30 s), then decide on its result. Replicating timestamps by
copy is a one-line change in `ehcache.xml` today and a channel message type in S6.

**Q6. Does MOD-I405 get its fix before the provider switch (S5)?** **Recommendation:** yes. The
fix doesn't depend on the provider, and S6 then needn't keep G5.

**Q7. When does JGroups move from 3.6 to 5?** Options: with S6 (one rewrite of site files), with
P5-6, or never if (b) is chosen (Infinispan brings its own). **Recommendation:** after S6b, as
its own step S6c, so that a site rewrites `jgroups.xml` once and the provider switch doesn't
depend on it.

## 7. Proposed LOG rows

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I423 | Persistence | Hibernate 5.6.15 spike on the integration branch (`92f31a6aa`), with hibernate-ehcache 5.6.15 over ehcache-core 2.6.11. Compile breaks: `SpringBeanEhCacheRegionFactory` (no `AbstractEhcacheRegionFactory` since 5.3) and `MySQL5InnoDBCompressedDialect` (exporters take a `SqlStringGenerationContext` since 5.5). Runtime breaks: `MetadataSchemaTool` calls the two-argument exporter methods, which throw in 5.6. The JPA test pool, commons-dbcp 1.2.2, has a protected `Statement.isClosed()` that 5.6 calls (85 tests). After fixes for those: the DDL gains a `;` per statement (5.6's default `hibernate.hbm2ddl.delimiter`), HSQLDB's drop script changes, `PersistenceUnitDdlTest`'s Oracle case can't read `all_sequences` from its fake, and `testAcademicTermDetails` ends in `UnexpectedRollbackException`. The persistence, query and analytics goldens pass, and so do the race tests of every cache | Open, for P4-3 step S1 (`notes/p4-3-plan.md`) |
| MOD-I424 | Caching | 14 Hibernate regions in use have no `ehcache.xml` entry and get `defaultCache` (1000 entries, 600 s, not replicated), among them `PortletLifecycleEntryImpl`, `ProfileSelection`, three PAGS natural ids and `PortletList.Query`. 8 `@Cache` annotations on subclasses of `Evaluator` and one on an `@Embeddable` are ignored. 18 entries are used by nothing: 5 subclass regions, 2 inherited collection roles, `MarketplaceRatingImpl`, 6 `.Query` regions and 4 application caches | Open, P4-3 step S2 (Q3) |
| MOD-I425 | Caching | Infinispan 14.0.35's JCache `removeAll()` removes only the keys of the local node in invalidation mode, and Spring 5.3's `JCacheCache.clear()` calls it. A permission change would then leave other nodes' decisions cached. Its asynchronous invalidations are unordered (`DeliverOrder.NONE`), and a plain `put` invalidates the key on the other nodes | Design input for Q1. With Infinispan, the Spring caches need an adapter over the native API, and the three permission caches need synchronous invalidation |
| MOD-I426 | Caching | `PortalDb`'s timestamps region isn't replicated, so another node's cached query results may not see a change until they expire (6 hours for portlet definitions). Not checked at runtime | Open (Q5): a cluster check first |
| MOD-I125 (update) | Runtime | Hibernate 5.6's `RegionNameQualifier` doesn't add a prefix that a region name already has, which should end the `AggrEvents.AggrEvents.*` regions | Check in S1 |

## 8. Status and next step

The plan is complete and pushed for review. No code changed on this branch. The next step is the
owner's answers to Q1 to Q7, then S1 on a new topic branch once java-time-jpa is merged. The
spike's worktree, `/home/user/wt/uPortal-p43-spike` (commits `26ebbb060` and `c4bc7dba4` on
`92f31a6aa`), keeps the four changes S1 starts from.

## Appendix A: Hibernate regions

### A.1 Entity, collection and natural-id regions

Strategy: NRW is `NONSTRICT_READ_WRITE`, RW `READ_WRITE` and RO `READ_ONLY`. The `ehcache.xml`
column gives the line of the `<cache name=…>` element, then `maxElementsInMemory`, the TTL and
TTI in seconds, and the replication (`inval` for removals, `copy`, `no`). `…` stands for
`org.apereo.portal.`. Rows marked "ignored, subclass" are annotations Hibernate drops with
HHH000482.

| Unit | Region (as Hibernate 5.2 names it) | Kind | Strategy | Declared at | ehcache.xml | Size | TTL | TTI | Repl |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Aggr | `AggrEvents.…events.aggr.UniqueStrings.uniqueStringSegments` | collection | RW | `UniqueStrings.java:73` | L1797 | 10000 | 0 | 600 | inval |
| Aggr | `AggrEvents.…events.aggr.UniqueStringsSegment.uniqueStrings` | collection | RW | `UniqueStringsSegment.java:66` | L1817 | 100000 | 0 | 600 | inval |
| Aggr | `AggrEvents.…events.aggr.UniqueStringsSegment` | entity | RW | `UniqueStringsSegment.java:54` | L1807 | 100000 | 0 | 600 | inval |
| Aggr | `AggrEvents.…events.aggr.UniqueStrings` | entity | RW | `UniqueStrings.java:55` | L1787 | 10000 | 0 | 600 | inval |
| Aggr | `AggrEvents.…events.aggr.action.SearchRequestAggregationImpl-NaturalId` | natural-id | entity's | `SearchRequestAggregationImpl.java:56` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Aggr | `AggrEvents.…events.aggr.action.SearchRequestAggregationImpl` | entity | RW | `SearchRequestAggregationImpl.java:59` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Aggr | `AggrEvents.…events.aggr.concuser.ConcurrentUserAggregationImpl-NaturalId` | natural-id | entity's | `ConcurrentUserAggregationImpl.java:60` | L1850 | 5000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.concuser.ConcurrentUserAggregationImpl` | entity | RW | `ConcurrentUserAggregationImpl.java:63` | L1847 | 5000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.AcademicTermDetailImpl` | entity | RW | `AcademicTermDetailImpl.java:49` | L1457 | 100 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.AggregatedGroupConfigImpl.excludedGroups` | collection | NRW | `AggregatedGroupConfigImpl.java:84` | L1537 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.AggregatedGroupConfigImpl.includedGroups` | collection | NRW | `AggregatedGroupConfigImpl.java:75` | L1527 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.AggregatedGroupConfigImpl` | entity | NRW | `AggregatedGroupConfigImpl.java:50` | L1507 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.AggregatedIntervalConfigImpl.excludedIntervals` | collection | NRW | `AggregatedIntervalConfigImpl.java:86` | L1572 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.AggregatedIntervalConfigImpl.includedIntervals` | collection | NRW | `AggregatedIntervalConfigImpl.java:76` | L1562 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.AggregatedIntervalConfigImpl` | entity | NRW | `AggregatedIntervalConfigImpl.java:51` | L1552 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.DateDimensionImpl-NaturalId` | natural-id | entity's | `DateDimensionImpl.java:56` | L1607 | 3660 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.DateDimensionImpl` | entity | RW | `DateDimensionImpl.java:58` | L1597 | 3660 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.EventAggregatorStatusImpl-NaturalId` | natural-id | entity's | `EventAggregatorStatusImpl.java:39` | L1664 | 10 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.QuarterDetailImpl` | entity | RO | `QuarterDetailImpl.java:47` | L1482 | 4 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.TimeDimensionImpl-NaturalId` | natural-id | entity's | `TimeDimensionImpl.java:54` | L1642 | 1440 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.dao.jpa.TimeDimensionImpl` | entity | RO | `TimeDimensionImpl.java:56` | L1632 | 1440 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.groups.AggregatedGroupMappingImpl-NaturalId` | natural-id | entity's | `AggregatedGroupMappingImpl.java:53` | L1690 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.groups.AggregatedGroupMappingImpl` | entity | RO | `AggregatedGroupMappingImpl.java:56` | L1680 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.login.LoginAggregationImpl-NaturalId` | natural-id | entity's | `LoginAggregationImpl.java:60` | L1836 | 5000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.login.LoginAggregationImpl` | entity | RW | `LoginAggregationImpl.java:62` | L1833 | 5000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.portletexec.PortletExecutionAggregationImpl-NaturalId` | natural-id | entity's | `PortletExecutionAggregationImpl.java:66` | L1878 | 100000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.portletexec.PortletExecutionAggregationImpl` | entity | RW | `PortletExecutionAggregationImpl.java:70` | L1875 | 100000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.portletlayout.PortletLayoutAggregationImpl-NaturalId` | natural-id | entity's | `PortletLayoutAggregationImpl.java:58` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Aggr | `AggrEvents.…events.aggr.portletlayout.PortletLayoutAggregationImpl` | entity (`@Cacheable` only) | RW (default) | `PortletLayoutAggregationImpl.java:61` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Aggr | `AggrEvents.…events.aggr.portlets.AggregatedPortletMappingImpl-NaturalId` | natural-id | entity's | `AggregatedPortletMappingImpl.java:40` | L1726 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.portlets.AggregatedPortletMappingImpl` | entity | RO | `AggregatedPortletMappingImpl.java:43` | L1716 | 1000 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.session.EventSessionImpl-NaturalId` | natural-id | entity's | `EventSessionImpl.java:57` | L1892 | 100000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.session.EventSessionImpl.groupMappings` | collection | RW | `EventSessionImpl.java:76` | L1895 | 100000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.session.EventSessionImpl` | entity | RW | `EventSessionImpl.java:59` | L1889 | 100000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.tabrender.TabRenderAggregationImpl-NaturalId` | natural-id | entity's | `TabRenderAggregationImpl.java:63` | L1864 | 10000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.tabrender.TabRenderAggregationImpl` | entity | RW | `TabRenderAggregationImpl.java:66` | L1861 | 10000 | 0 | 600 | no |
| Aggr | `AggrEvents.…events.aggr.tabs.AggregatedTabMappingImpl-NaturalId` | natural-id | entity's | `AggregatedTabMappingImpl.java:39` | L1762 | 500 | 0 | 0 | inval |
| Aggr | `AggrEvents.…events.aggr.tabs.AggregatedTabMappingImpl` | entity | RO | `AggregatedTabMappingImpl.java:41` | L1752 | 500 | 0 | 0 | inval |
| Portal | `…concurrency.locking.ClusterMutex-NaturalId` | natural-id | entity's | `ClusterMutex.java:39` | L649 | 100 | 0 | 0 | inval |
| Portal | `…dao.portletlist.jpa.PortletListItem` | entity (`@Cacheable` only) | RW (default) | `PortletListItem.java:29` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…dao.portletlist.jpa.PortletList` | entity (`@Cacheable` only) | RW (default) | `PortletList.java:33` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…groups.pags.dao.jpa.PersonAttributesGroupDefinitionImpl-NaturalId` | natural-id | entity's | `PersonAttributesGroupDefinitionImpl.java:62` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…groups.pags.dao.jpa.PersonAttributesGroupDefinitionImpl.members` | collection | RW | `PersonAttributesGroupDefinitionImpl.java:109` | L2024 | 625 | 300 | 0 | no |
| Portal | `…groups.pags.dao.jpa.PersonAttributesGroupDefinitionImpl.testGroups` | collection | NRW | `PersonAttributesGroupDefinitionImpl.java:126` | L2033 | 1000 | 300 | 0 | no |
| Portal | `…groups.pags.dao.jpa.PersonAttributesGroupDefinitionImpl` | entity | NRW | `PersonAttributesGroupDefinitionImpl.java:66` | L2011 | 625 | 300 | 0 | no |
| Portal | `…groups.pags.dao.jpa.PersonAttributesGroupTestDefinitionImpl-NaturalId` | natural-id | entity's | `PersonAttributesGroupTestDefinitionImpl.java:45` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…groups.pags.dao.jpa.PersonAttributesGroupTestDefinitionImpl` | entity | NRW | `PersonAttributesGroupTestDefinitionImpl.java:49` | L2042 | 1000 | 300 | 0 | no |
| Portal | `…groups.pags.dao.jpa.PersonAttributesGroupTestGroupDefinitionImpl-NaturalId` | natural-id | entity's | `PersonAttributesGroupTestGroupDefinitionImpl.java:54` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…groups.pags.dao.jpa.PersonAttributesGroupTestGroupDefinitionImpl.tests` | collection | RW | `PersonAttributesGroupTestGroupDefinitionImpl.java:96` | L2060 | 1000 | 300 | 0 | no |
| Portal | `…groups.pags.dao.jpa.PersonAttributesGroupTestGroupDefinitionImpl` | entity | NRW | `PersonAttributesGroupTestGroupDefinitionImpl.java:58` | L2051 | 1000 | 300 | 0 | no |
| Portal | `…i18n.dao.jpa.MessageImpl` | entity | NRW | `MessageImpl.java:42` | L682 | 1000 | 0 | 60 | inval |
| Portal | `…layout.dao.jpa.LayoutAttributeDescriptorImpl.targetElementNames` | collection | NRW | `LayoutAttributeDescriptorImpl.java:59` | L873 | 1000 | 0 | 3600 | inval |
| Portal | `…layout.dao.jpa.LayoutAttributeDescriptorImpl` | entity | NRW | `LayoutAttributeDescriptorImpl.java:44` | L863 | 1000 | 0 | 3600 | inval |
| Portal | `…layout.dao.jpa.LayoutNodeAttributesImpl.attributes` | collection | RW | `LayoutNodeAttributesImpl.java:78` | L853 | 10000 | 0 | 3600 | inval |
| Portal | `…layout.dao.jpa.LayoutNodeAttributesImpl` | entity | NRW | `LayoutNodeAttributesImpl.java:53` | L843 | 1000 | 0 | 3600 | inval |
| Portal | `…layout.dao.jpa.OutputPropertyDescriptorImpl` | entity | NRW | `OutputPropertyDescriptorImpl.java:37` | L823 | 10000 | 0 | 3600 | inval |
| Portal | `…layout.dao.jpa.StylesheetDescriptorImpl-NaturalId` | natural-id | entity's | `StylesheetDescriptorImpl.java:56` | L717 | 1000 | 0 | 0 | inval |
| Portal | `…layout.dao.jpa.StylesheetDescriptorImpl.layoutAttributes` | collection | NRW | `StylesheetDescriptorImpl.java:127` | L757 | 10000 | 0 | 300 | inval |
| Portal | `…layout.dao.jpa.StylesheetDescriptorImpl.outputProperties` | collection | NRW | `StylesheetDescriptorImpl.java:103` | L737 | 10000 | 0 | 300 | inval |
| Portal | `…layout.dao.jpa.StylesheetDescriptorImpl.stylesheetParameters` | collection | NRW | `StylesheetDescriptorImpl.java:115` | L747 | 10000 | 0 | 300 | inval |
| Portal | `…layout.dao.jpa.StylesheetDescriptorImpl` | entity | NRW | `StylesheetDescriptorImpl.java:58` | L707 | 1000 | 0 | 300 | inval |
| Portal | `…layout.dao.jpa.StylesheetParameterDescriptorImpl` | entity | NRW | `StylesheetParameterDescriptorImpl.java:37` | L833 | 1000 | 0 | 3600 | inval |
| Portal | `…layout.dao.jpa.StylesheetUserPreferencesImpl.layoutAttributes` | collection | RW | `StylesheetUserPreferencesImpl.java:124` | L793 | 1000 | 0 | 3600 | inval |
| Portal | `…layout.dao.jpa.StylesheetUserPreferencesImpl.outputProperties` | collection | NRW | `StylesheetUserPreferencesImpl.java:100` | L803 | 1000 | 0 | 3600 | inval |
| Portal | `…layout.dao.jpa.StylesheetUserPreferencesImpl.parameters` | collection | RW | `StylesheetUserPreferencesImpl.java:113` | L813 | 1000 | 0 | 3600 | inval |
| Portal | `…layout.dao.jpa.StylesheetUserPreferencesImpl` | entity | RW | `StylesheetUserPreferencesImpl.java:63` | L773 | 1000 | 0 | 3600 | inval |
| Portal | `…layout.dlm.Evaluator` | entity | NRW | `Evaluator.java:43` | L889 | 100 | 21600 | 0 | inval |
| Portal | `…layout.dlm.FragmentDefinition` | entity (ignored, subclass) | NRW | `FragmentDefinition.java:43` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…layout.dlm.providers.AllUsersEvaluatorFactory` | entity (ignored, subclass) | NRW | `AllUsersEvaluatorFactory.java:37` | L940 | 500 | 21600 | 0 | inval |
| Portal | `…layout.dlm.providers.AttributeEvaluator` | entity (ignored, subclass) | NRW | `AttributeEvaluator.java:36` | L950 | 500 | 21600 | 0 | inval |
| Portal | `…layout.dlm.providers.EvaluatorGroup.evaluators` | collection | NRW | `EvaluatorGroup.java:50` | L930 | 1000 | 21600 | 0 | inval |
| Portal | `…layout.dlm.providers.EvaluatorGroup` | entity (ignored, subclass) | NRW | `EvaluatorGroup.java:38` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…layout.dlm.providers.GroupMembershipEvaluator` | entity (ignored, subclass) | NRW | `GroupMembershipEvaluator.java:45` | L960 | 500 | 21600 | 0 | inval |
| Portal | `…layout.dlm.providers.GuestUserEvaluatorFactory` | entity (ignored, subclass) | NRW | `GuestUserEvaluatorFactory.java:40` | L970 | 500 | 21600 | 0 | inval |
| Portal | `…layout.dlm.providers.Paren` | entity (ignored, subclass) | NRW | `Paren.java:37` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…layout.dlm.providers.ProfileEvaluator` | entity (ignored, subclass) | NRW | `ProfileEvaluator.java:35` | L980 | 500 | 21600 | 0 | inval |
| Portal | `…layout.profile.dao.jpa.ProfileSelection##NaturalId` | natural-id | entity's | `ProfileSelection.java:43` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…layout.profile.dao.jpa.ProfileSelection` | entity | NRW | `ProfileSelection.java:45` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…permission.dao.jpa.PermissionActivityImpl` | entity | NRW | `PermissionActivityImpl.java:45` | L1037 | 500 | 21600 | 0 | inval |
| Portal | `…permission.dao.jpa.PermissionOwnerImpl-NaturalId` | natural-id | entity's | `PermissionOwnerImpl.java:53` | L1007 | 100 | 0 | 0 | inval |
| Portal | `…permission.dao.jpa.PermissionOwnerImpl.activities` | collection | NRW | `PermissionOwnerImpl.java:86` | L1027 | 100 | 21600 | 0 | inval |
| Portal | `…permission.dao.jpa.PermissionOwnerImpl` | entity | NRW | `PermissionOwnerImpl.java:55` | L996 | 100 | 21600 | 0 | inval |
| Portal | `…persondir.dao.jpa.LocalAccountPersonAttributeImpl.values` | collection | RW | `LocalAccountPersonAttributeImpl.java:66` | L1103 | 500 | 1800 | 900 | inval |
| Portal | `…persondir.dao.jpa.LocalAccountPersonAttributeImpl` | entity | NRW | `LocalAccountPersonAttributeImpl.java:46` | L1083 | 500 | 1800 | 900 | inval |
| Portal | `…persondir.dao.jpa.LocalAccountPersonImpl.attributes` | collection | RW | `LocalAccountPersonImpl.java:84` | L1073 | 500 | 1800 | 900 | inval |
| Portal | `…persondir.dao.jpa.LocalAccountPersonImpl` | entity | RW | `LocalAccountPersonImpl.java:55` | L1053 | 500 | 1800 | 900 | inval |
| Portal | `…portlet.dao.jpa.PortalCookieImpl-NaturalId` | natural-id | entity's | `PortalCookieImpl.java:52` | L1128 | 2500 | 0 | 900 | inval |
| Portal | `…portlet.dao.jpa.PortalCookieImpl.portletCookies` | collection | NRW | `PortalCookieImpl.java:83` | L1148 | 2500 | 900 | 0 | inval |
| Portal | `…portlet.dao.jpa.PortalCookieImpl` | entity | NRW | `PortalCookieImpl.java:54` | L1118 | 2500 | 900 | 0 | inval |
| Portal | `…portlet.dao.jpa.PortletCookieImpl` | entity | NRW | `PortletCookieImpl.java:50` | L1158 | 25000 | 900 | 0 | inval |
| Portal | `…portlet.dao.jpa.PortletDefinitionImpl-NaturalId` | natural-id | entity's | `PortletDefinitionImpl.java:84` | L1185 | 1000 | 21600 | 0 | inval |
| Portal | `…portlet.dao.jpa.PortletDefinitionImpl.lifecycleEntries` | collection | NRW | `PortletDefinitionImpl.java:179` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…portlet.dao.jpa.PortletDefinitionImpl.localizations` | collection | RW | `PortletDefinitionImpl.java:202` | L1205 | 10000 | 21600 | 0 | inval |
| Portal | `…portlet.dao.jpa.PortletDefinitionImpl.parameters` | collection | NRW | `PortletDefinitionImpl.java:195` | L1215 | 10000 | 21600 | 0 | inval |
| Portal | `…portlet.dao.jpa.PortletDefinitionImpl` | entity | RW | `PortletDefinitionImpl.java:86` | L1174 | 1000 | 21600 | 0 | inval |
| Portal | `…portlet.dao.jpa.PortletDefinitionParameterImpl` | entity | RW | `PortletDefinitionParameterImpl.java:37` | L1225 | 10000 | 21600 | 0 | inval |
| Portal | `…portlet.dao.jpa.PortletEntityImpl.windowStates` | collection | NRW | `PortletEntityImpl.java:105` | L1261 | 15000 | 1800 | 900 | inval |
| Portal | `…portlet.dao.jpa.PortletEntityImpl` | entity | NRW | `PortletEntityImpl.java:69` | L1241 | 15000 | 1800 | 900 | inval |
| Portal | `…portlet.dao.jpa.PortletLifecycleEntryImpl` | entity | RO | `PortletLifecycleEntryImpl.java:40` | none |  |  |  | defaultCache: 1000, 600, 0, no |
| Portal | `…portlet.dao.jpa.PortletPreferenceImpl.values` | collection | RW | `PortletPreferenceImpl.java:89` | L1308 | 2200 | 1800 | 900 | inval |
| Portal | `…portlet.dao.jpa.PortletPreferenceImpl` | entity | NRW | `PortletPreferenceImpl.java:57` | L1298 | 2200 | 1800 | 900 | inval |
| Portal | `…portlet.dao.jpa.PortletPreferencesImpl.portletPreferences` | collection | RW | `PortletPreferencesImpl.java:80` | L1288 | 15500 | 1800 | 900 | inval |
| Portal | `…portlet.dao.jpa.PortletPreferencesImpl` | entity | RW | `PortletPreferencesImpl.java:56` | L1278 | 15500 | 1800 | 900 | inval |
| Portal | `…portlet.dao.jpa.PortletTypeImpl-NaturalId` | natural-id | entity's | `PortletTypeImpl.java:44` | L1335 | 250 | 0 | 0 | inval |
| Portal | `…portlet.dao.jpa.PortletTypeImpl` | entity | NRW | `PortletTypeImpl.java:46` | L1324 | 250 | 21600 | 0 | inval |
| Portal | `…tenants.JpaTenant.attributes` | collection | NRW | `JpaTenant.java:71` | L1369 | 250 | 900 | 0 | inval |
| Portal | `…tenants.JpaTenant` | entity | NRW | `JpaTenant.java:44` | L1359 | 25 | 900 | 0 | inval |
| Portal | `…version.dao.jpa.VersionImpl-NaturalId` | natural-id | entity's | `VersionImpl.java:32` | L659 | 100 | 0 | 0 | inval |

### A.2 Query regions

From `BaseJpaDao.createCachedQuery`, named after the query's first root, and the 5.2 default
regions. The last column gives the lines of the `createCachedQuery` calls in the DAO.

| Query region | ehcache.xml | Size | TTL | TTI | Repl | Cached queries in |
| --- | --- | --- | --- | --- | --- | --- |
| `AggrEvents.…events.aggr.concuser.ConcurrentUserAggregationImpl.Query` | L1853 | 5000 | 0 | 600 | no | no cached query |
| `AggrEvents.…events.aggr.dao.jpa.AcademicTermDetailImpl.Query` | L1467 | 100 | 0 | 0 | inval | `JpaEventAggregationManagementDao.java:270,279,309,340,370,397,429,439` |
| `AggrEvents.…events.aggr.dao.jpa.AggregatedGroupConfigImpl.Query` | L1517 | 1000 | 0 | 0 | inval | `JpaEventAggregationManagementDao.java:270,279,309,340,370,397,429,439` |
| `AggrEvents.…events.aggr.dao.jpa.AggregatedIntervalConfigImpl.Query` | L1582 | 1000 | 0 | 0 | inval | `JpaEventAggregationManagementDao.java:270,279,309,340,370,397,429,439` |
| `AggrEvents.…events.aggr.dao.jpa.DateDimensionImpl.Query` | L1617 | 3660 | 0 | 600 | inval | `JpaDateDimensionDao.java:175,183,207,217` |
| `AggrEvents.…events.aggr.dao.jpa.QuarterDetailImpl.Query` | L1492 | 5 | 0 | 0 | inval | `JpaEventAggregationManagementDao.java:270,279,309,340,370,397,429,439` |
| `AggrEvents.…events.aggr.dao.jpa.TimeDimensionImpl.Query` | L1652 | 1440 | 0 | 600 | inval | `JpaTimeDimensionDao.java:69` |
| `AggrEvents.…events.aggr.groups.AggregatedGroupMappingImpl.Query` | L1700 | 1000 | 0 | 600 | inval | `JpaAggregatedGroupLookupDao.java:152` |
| `AggrEvents.…events.aggr.login.LoginAggregationImpl.Query` | L1839 | 5000 | 0 | 600 | no | no cached query |
| `AggrEvents.…events.aggr.portletexec.PortletExecutionAggregationImpl.Query` | L1881 | 100000 | 0 | 600 | no | no cached query |
| `AggrEvents.…events.aggr.portlets.AggregatedPortletMappingImpl.Query` | L1736 | 1000 | 0 | 600 | inval | `JpaAggregatedPortletLookupDao.java:129` |
| `AggrEvents.…events.aggr.session.EventSessionImpl.Query` | L1898 | 100000 | 0 | 600 | no | no cached query |
| `AggrEvents.…events.aggr.tabrender.TabRenderAggregationImpl.Query` | L1867 | 10000 | 0 | 600 | no | no cached query |
| `AggrEvents.…events.aggr.tabs.AggregatedTabMappingImpl.Query` | L1772 | 500 | 0 | 600 | inval | `JpaAggregatedTabLookupDao.java:151` |
| `…dao.portletlist.jpa.PortletList.Query` | none | defaultCache | | | | `PortletListDao.java:84,93,101` |
| `…groups.pags.dao.jpa.PersonAttributesGroupDefinitionImpl.Query` | L2015 | 625 | 300 | 0 | no | `JpaPersonAttributesGroupDefinitionDao.java:160,175,186` |
| `…i18n.dao.jpa.MessageImpl.Query` | L692 | 1000 | 0 | 60 | inval | `JpaMessageDao.java:166,176,184,192` |
| `…layout.dao.jpa.StylesheetDescriptorImpl.Query` | L727 | 1000 | 0 | 300 | inval | `JpaStylesheetDescriptorDao.java:69` |
| `…layout.dao.jpa.StylesheetUserPreferencesImpl.Query` | L783 | 1000 | 0 | 3600 | inval | `JpaStylesheetUserPreferencesDao.java:142,177,198` |
| `…layout.dlm.FragmentDefinition.Query` | L910 | 100 | 21600 | 0 | inval | `FragmentDefinitionDao.java:97,105,116` |
| `…permission.dao.jpa.PermissionOwnerImpl.Query` | L1017 | 100 | 21600 | 0 | inval | `JpaPermissionOwnerDao.java:67` |
| `…persondir.dao.jpa.LocalAccountPersonAttributeImpl.Query` | L1093 | 500 | 1800 | 900 | inval | `JpaLocalAccountDaoImpl.java:136,146,249,264` |
| `…persondir.dao.jpa.LocalAccountPersonImpl.Query` | L1063 | 500 | 1800 | 900 | inval | `JpaLocalAccountDaoImpl.java:136,146,249,264` |
| `…portlet.dao.jpa.MarketplaceRatingImpl.Query` | L1395 | 250 | 21600 | 0 | inval | `JpaMarketplaceRatingDao.java:134` |
| `…portlet.dao.jpa.PortalCookieImpl.Query` | L1138 | 2500 | 900 | 0 | inval | no cached query |
| `…portlet.dao.jpa.PortletDefinitionImpl.Query` | L1195 | 1000 | 21600 | 0 | inval | `JpaPortletDefinitionDao.java:257,277,327` |
| `…portlet.dao.jpa.PortletEntityImpl.Query` | L1251 | 1500 | 1800 | 900 | inval | `JpaPortletEntityDao.java:215,229` |
| `…portlet.dao.jpa.PortletTypeImpl.Query` | L1345 | 250 | 21600 | 0 | inval | `JpaPortletTypeDao.java:98` |
| `…tenants.JpaTenant.Query` | L1379 | 10 | 300 | 0 | no | `JpaTenantDao.java:84,91,103` |
| `org.hibernate.cache.internal.StandardQueryCache` (5.2 default results) | L644 | 1000 | 300 | 0 | no | queries without a region |
| `org.hibernate.cache.spi.UpdateTimestampsCache` (5.2 timestamps) | L636 | 1000 | 0 | 0 | no | every cached query of PortalDb |
| `AggrEvents.org.hibernate.cache.internal.StandardQueryCache` | L1433 | 100 | 0 | 300 | no | queries without a region |
| `AggrEvents.org.hibernate.cache.spi.UpdateTimestampsCache` | L1442 | 100 | 0 | 0 | copy | every cached query of AggrEventsDb |

## Appendix B: application caches

Columns as in Appendix A. Listener: `tag` is `tagTrackingCacheEventListener`, `size`
`insufficientSizeCacheEventListener`. Access: "Spring" through `CacheLookup.requireCache`,
"Ehcache" through the Ehcache 2 API, "Map" through `CacheFactory` and `MapCacheProvider`. The
last column gives where the code gets the cache (for "Ehcache", the first Ehcache import of the
file).

| Cache | ehcache.xml | Size | TTL | TTI | Repl | Listener | Access | Where |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `…events.tincan.LrsActorCache` | L101 | 10000 | 0 | 600 | no |  | Spring | `LrsActorService.java:45` |
| `…events.aggr.tabrender.TabRenderAggregator.layoutNodeIdNameResolver` | L109 | 1000 | 300 | 0 | no |  | Spring | `JpaAggregatedTabLookupDao.java:65` |
| `…jpa.cache.EntityManagerCache` | L117 | 1000 | 0 | 300 | no | tag | Ehcache | `EntityManagerCacheImpl.java:28` |
| `…RDBMUserIdentityStore.userLockCache` | L128 | 10000 | 0 | 600 | no |  | Spring | `RDBMUserIdentityStore.java:109` |
| `…web.skin.Resources` | L137 | 50 | 28800 | 0 | no |  | none | `no reference` |
| `…portlet.registry.PersistentPortletEntityWrapper.windowStates` | L146 | 50 | 1800 | 0 | no |  | @Cacheable | `PersistentPortletEntityWrapper.java:76` |
| `…web.skin.Resources_DocumentFragment` | L155 | 50 | 28800 | 0 | no |  | none | `no reference` |
| `…security.provider.AuthorizationImpl.AUTH_PRINCIPAL_CACHE` | L166 | 1000 | 0 | 900 | no | tag | Spring | `AuthorizationImpl.java:152` |
| `…security.provider.AuthorizationImpl.ENTITY_PARENTS_CACHE` | L177 | 1000 | 180 | 0 | no |  | Spring | `AuthorizationImpl.java:154` |
| `…security.provider.AuthorizationImpl.PRINCIPAL_HAS_PERMISSION` | L187 | 50000 | 180 | 0 | inval | tag | Spring | `AuthorizationImpl.java:156` |
| `…security.provider.AnyUnblockedGrantPermissionPolicy.HAS_UNBLOCKED_GRANT` | L211 | 250000 | 300 | 0 | inval | tag | Spring | `AnyUnblockedGrantPermissionPolicy.java:88, AuthorizationImpl.java:158` |
| `…layout.dlm.FragmentActivator.userViews` | L228 | 100 | 600 | 0 | inval | size | Ehcache | `FragmentActivator.java:26` |
| `…layout.dlm.FragmentActivator.userViewErrors` | L246 | 100 | 30 | 0 | no |  | Ehcache | `FragmentActivator.java:26` |
| `…layout.dlm.RDBMDistributedLayoutStore.fragmentNodeInfoCache` | L255 | 1000 | 300 | 0 | no | size | Ehcache | `RDBMDistributedLayoutStore.java:35` |
| `…layout.dlm.LAYOUT_CACHE` | L266 | 1000 | 7200 | 900 | inval | tag | Spring | `LayoutCachingService.java:40` |
| `…layout.dlm.NODEREF_CACHE` | L283 | 2000 | 0 | 180 | no |  | none | `no reference` |
| `…utils.ResourceLoader.RESOURCE_URL_CACHE` | L292 | 250 | 28800 | 0 | no |  | Map | `cacheContext.xml:83` |
| `…utils.ResourceLoader.RESOURCE_URL_NOT_FOUND_CACHE` | L301 | 250 | 600 | 0 | no |  | Map | `cacheContext.xml:89` |
| `…portlets.portletadmin.PortletAdministrationHelper.CPD_CACHE` | L310 | 250 | 28800 | 0 | no |  | Map | `channelContainerContext.xml:27` |
| `…EntityTypes.CLASS_BY_ID` | L319 | 50 | 28800 | 0 | inval | size | @Cacheable | `EntityTypes.java:82` |
| `…EntityTypes.ID_BY_CLASS` | L330 | 50 | 28800 | 0 | inval | size | @Cacheable | `EntityTypes.java:102` |
| `…EntityTypes.ALL` | L341 | 1 | 28800 | 0 | inval |  | @Cacheable | `EntityTypes.java:111` |
| `…services.persondir.USER_INFO.merged` | L361 | 1000 | 900 | 0 | no | tag | Spring | `PersonDirectoryConfiguration.java:230` |
| `…groups.IEntityGroup` | L376 | 1000 | 21600 | 0 | inval | size | Map | `CacheFactoryEntityCachingService` |
| `…groups.IEntity` | L393 | 2500 | 21600 | 0 | inval |  | Map | `CacheFactoryEntityCachingService` |
| `…groups.GroupMemberImpl.parentGroups` | L412 | 5000 | 300 | 0 | inval | tag | Ehcache | `GroupMemberImpl.java:21` |
| `…groups.EntityGroupImpl.children` | L433 | 1000 | 300 | 0 | inval | size | Ehcache | `EntityGroupImpl.java:23` |
| `…groups.RDBMEntityGroupStore.search` | L449 | 500 | 300 | 0 | no | size | Ehcache | `RDBMEntityGroupStore.java:27` |
| `…groups.RDBMEntityGroupStore.parentGroupEntity` | L459 | 500 | 300 | 0 | no | size | Ehcache | `RDBMEntityGroupStore.java:27` |
| `…groups.RDBMEntityGroupStore.parentGroupEntityGroup` | L469 | 500 | 300 | 0 | no | size | Ehcache | `RDBMEntityGroupStore.java:27` |
| `…security.IPermissionSet` | L480 | 1000 | 21600 | 0 | inval | size | Map | `CacheFactoryEntityCachingService, AuthorizationImpl.java:215` |
| `…portlet.container.cache.PortletCacheControlServiceImpl.privateScopePortletRenderHeaderOutputCache` | L498 | 2500 | 0 | 300 | no | tag | Ehcache | `PortletCacheControlServiceImpl.java:24` |
| `…portlet.container.cache.PortletCacheControlServiceImpl.privateScopePortletRenderOutputCache` | L508 | 2500 | 0 | 300 | no | tag | Ehcache | `PortletCacheControlServiceImpl.java:24` |
| `…portlet.container.cache.PortletCacheControlServiceImpl.privateScopePortletResourceOutputCache` | L518 | 2500 | 0 | 300 | no | tag | Ehcache | `PortletCacheControlServiceImpl.java:24` |
| `…portlet.container.cache.PortletCacheControlServiceImpl.publicScopePortletRenderHeaderOutputCache` | L528 | 2500 | 0 | 600 | no | tag | Ehcache | `PortletCacheControlServiceImpl.java:24` |
| `…portlet.container.cache.PortletCacheControlServiceImpl.publicScopePortletRenderOutputCache` | L538 | 2500 | 0 | 600 | no | tag | Ehcache | `PortletCacheControlServiceImpl.java:24` |
| `…portlet.container.cache.PortletCacheControlServiceImpl.publicScopePortletResourceOutputCache` | L548 | 2500 | 0 | 600 | no | tag | Ehcache | `PortletCacheControlServiceImpl.java:24` |
| `…utils.cache.ConfigurablePageCachingFilter.PAGE_CACHE` | L567 | 250 | 0 | 900 | no |  | 3rd party | `cacheContext.xml:99 (resource-server-utils)` |
| `…utils.cache.resource.CachingResourceLoader` | L576 | 500 | 0 | 900 | no |  | Ehcache | `CachingResourceLoaderImpl.java:22` |
| `SpELExpressionCache` | L585 | 5000 | 0 | 900 | no |  | Spring | `PortalSpELServiceImpl.java:74` |
| `portletSpELExpressionCache` | L594 | 100 | 0 | 900 | no |  | Spring | `PortletSpELServiceImpl.java:64` |
| `…rendering.STRUCTURE_TRANSFORM` | L603 | 500 | 0 | 300 | no |  | Ehcache | `RenderingPipelineConfiguration.java:30` |
| `…rendering.THEME_TRANSFORM` | L612 | 10000 | 0 | 300 | no |  | Ehcache | `RenderingPipelineConfiguration.java:30` |
| `…portlet.rendering.SupportedEventCache` | L620 | 1000 | 0 | 300 | no |  | Spring | `PortletEventCoordinationHelper.java:47` |
| `…i18n.RDBMLocaleStore.userLocales` | L674 | 2000 | 600 | 0 | no |  | @Cacheable | `RDBMLocaleStore.java:83` |
| `…portlet.marketplace.MarketplaceService.marketplacePortletDefinitionCache` | L1407 | 1000 | 300 | 0 | no |  | Ehcache | `MarketplaceService.java:25` |
| `…portlet.marketplace.MarketplaceService.marketplaceUserPortletDefinitionCache` | L1413 | 1000 | 300 | 0 | no |  | Ehcache | `MarketplaceService.java:25` |
| `…portlet.marketplace.MarketplaceService.marketplaceCategoryCache` | L1419 | 20 | 600 | 0 | no |  | Ehcache | `MarketplaceService.java:25, PortletMarketplaceController.java:40` |
| `org.jasig.cas.client.proxy.EhcacheBackedProxyGrantingTicketStorageImpl.cache` | L1928 | 10000 | 0 | 600 | no |  | none | `not wired (securityContext.xml uses another storage)` |
| `…skinManager.failureCache` | L1944 | 100 | 60 | 60 | no |  | Spring | `FileSystemDynamicSkinService.java:44` |
| `…groups.pags.dao.EntityPersonAttributesGroupStore.entityGroup` | L1973 | 625 | 300 | 0 | no | size | Ehcache | `EntityPersonAttributesGroupStore.java:25` |
| `…groups.pags.dao.EntityPersonAttributesGroupStore.pagsGroup` | L1986 | 625 | 300 | 0 | no | size | Ehcache | `EntityPersonAttributesGroupStore.java:25` |
| `…groups.pags.dao.EntityPersonAttributesGroupStore.membership` | L1997 | 312500 | 300 | 0 | no | searchable | Ehcache (search API) | `PagsMembershipCacheAuthenticationListener.java:19` |
| `…groups.pags.testers.AdHocGroupTester` | L2069 | 1000 | 60 | 0 | no |  | Ehcache | `AdHocGroupTester.java:17` |
| `…portlets.sqlquery.SqlQueryPortletController.queryResults` | L2084 | 2000 | 300 | 0 | no |  | Ehcache | `SqlQueryPortletController.java:25` |
| `…portlet.soffit.connector.SoffitConnectorController.RESPONSE_CACHE` | L2094 | 2000 | 0 | 0 | no |  | Ehcache | `SoffitConnectorController.java:26` |
| `…portlet.dao.jpa.PortletEntityImpl.idParseCache` | L560 | 15000 | 60 | 0 | no |  | Spring | `PortletEntityRegistryImpl.java:107` |
