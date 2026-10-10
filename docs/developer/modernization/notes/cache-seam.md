# P3-3 cache seam: handoff note

Workstream WS5, plan step P3-3 (MOD-D6). The goal is to put uPortal's own caching behind
Spring's cache abstraction (MOD-D22, MOD-Q10) and remove spring-modules-cache, while still on
Ehcache 2 and with no change in behaviour. Branch: `ccr-43ba0785-hzlrj5-cache-seam` in uPortal
and in uPortal-start (no uPortal-start changes).

## What changed

| Commit | Module(s) | Change |
| --- | --- | --- |
| `refactor(cache): replace spring-modules-cache with Spring's KeyGenerator` | build, uPortal-events, uPortal-utils-core, uPortal-webapp | spring-modules-cache 0.8 removed. `PersonDirectoryCacheKeyGenerator` implements Spring's `KeyGenerator`. The new `CachingPersonAttributeDao` replaces Person Directory's `CachingPersonAttributeDaoImpl`, whose API needs the spring-modules type. Adds the `CacheLookup` seam helper. |
| `refactor(cache): look up the SpEL expression caches through Spring` | uPortal-utils-core | `PortalSpELServiceImpl`, `PortletSpELServiceImpl` |
| `refactor(cache): move the layout cache to Spring's cache API` | uPortal-layout-impl | `LayoutCachingService` |
| `refactor(cache): move the permission caches to Spring's cache API` | uPortal-security-permissions | `AuthorizationImpl` (3 caches, `SelfPopulatingCache` → `Cache.get(key, loader)`), `AnyUnblockedGrantPermissionPolicy` |
| `refactor(cache): move the LRS actor cache to Spring's cache API` | uPortal-events | `LrsActorService` |
| `refactor(cache): move the user lock cache to Spring's cache API` | uPortal-persondir | `RDBMUserIdentityStore` (`SelfPopulatingCache` → `Cache.get(key, loader)`) |
| `test(cache): check that every cache name the code uses is configured` | uPortal-webapp | `CacheConfigurationCoverageTest` |
| `refactor(cache): move the portlet entity id parse cache to Spring's cache API` | uPortal-rendering | `PortletEntityRegistryImpl` |
| `refactor(cache): move the supported event cache to Spring's cache API` | uPortal-web | `PortletEventCoordinationHelper`, `PortletEventCoordinatationService` |
| `refactor(cache): make the cache health report independent of Ehcache` | uPortal-utils-core | `CacheHealthReporterService` takes cache names. `InsufficientSizeCacheEventListener` is the Ehcache 2 adapter. |
| `refactor(cache): move the tab name resolution cache to Spring's cache API` | uPortal-events | `JpaAggregatedTabLookupDao`. Its test context uses a real Ehcache, because the DAO caches a null key. |
| `refactor(cache): move the dynamic skin failure cache to Spring's cache API` | uPortal-portlets | `AbstractDynamicSkinService` and its two subclasses. `getKeysWithExpiryCheck().contains(k)` → `get(k) != null`. |

Each commit is meant to become one upstream PR. They are in dependency order: the first one
adds `CacheLookup`, and the others use it.

### The seam

* **Spring `CacheManager`:** the existing `ehCacheManager` bean in `cacheContext.xml`, an
  `EhCacheCacheManager` over the Ehcache 2 `cacheManager` (an `EhCacheManagerFactoryBean` with
  `shared=true`, reading `${org.apereo.portal.ehcache.filename:ehcache.xml}`). No XML changed.
  The Spring caches wrap the same `net.sf.ehcache.Cache` objects, so sizes, TTLs, listeners and
  JGroups replication stay exactly as configured. `<cache:annotation-driven>` already used it
  for the existing `@Cacheable` users (`EntityTypes`, `RDBMLocaleStore`,
  `PersistentPortletEntityWrapper`).
* **`CacheLookup.requireCache(CacheManager, name)`:** the one way moved code gets a cache. It
  throws `IllegalStateException` at startup when the name isn't configured. That matches
  today's behaviour: a missing `@Qualifier` Ehcache bean also failed startup. Moved classes take
  a `CacheManager` through an `@Autowired setCacheManager` and keep a plain `setXCache(Cache)`
  setter for tests and XML.
* **Operation mapping:**

  | Ehcache 2 | Spring `Cache` |
  | --- | --- |
  | `get(k)` returning an `Element` | `get(k)` returning a `ValueWrapper`, or `get(k, Type.class)` |
  | `put(new Element(k, v))` | `put(k, v)` |
  | `remove(k)` | `evict(k)` |
  | `SelfPopulatingCache` | `get(k, Callable)`. `EhCacheCache` loads under the cache's per-key write lock (`acquireWriteLockOnKey`), as `BlockingCache` did. |

  Cached `null` values behave the same, because `EhCacheCache` stores them as Elements.

### Behaviour notes

* **Person attributes:** `CachingPersonAttributeDao` generates the same `CacheKey` (method
  name `getPeopleWithMultivaluedAttributes`, the seed attributes, the `username` tag), so
  entries and the logout purge (`UsernameTaggedCacheEntryPurger` → `TagTrackingCacheEventListener`)
  work as before. Two things changed:
  * The "null results" marker is now a uPortal class.
  * The bean no longer counts queries and misses; it had only logged them at debug level.

  MOD-I57 (a null lookup is cached for 15 minutes) is unchanged on purpose.
* **commons-collections:** this jar reached `uPortal-utils-core`'s compile classpath only
  through spring-modules (3.1). It is now declared at 3.2.2, the version the war already ships.
  spring-modules' other transitive jars (`concurrent` 1.3.4, `cglib-nodep` 2.1_3, `oro` 2.0.8)
  have no users in the code. The integration run's portal start is what shows they aren't
  needed at runtime.

## Cache inventory

`ehcache.xml` and `ehcache-no-jgroups.xml` define the same **184 caches** with the same
settings, plus `defaultCache` (1000 entries, TTL 600 s, not replicated). The no-jgroups file
only drops the replicators and the peer provider. Peer provider:
`JGroupsCacheManagerPeerProviderFactory`, `file=properties/jgroups.xml`.

* **58 application caches and 126 Hibernate regions.** The Hibernate regions are 63 entity
  regions (18 of them `-NaturalId`), 31 collection regions and 32 query regions (28 `.Query`
  and 4 `org.hibernate.cache.*`).
* **110 caches are replicated.** 109 replicate by invalidation (`replicatePuts=false`,
  `replicateUpdatesViaCopy=false`, removals and updates sent as invalidations). One,
  `AggrEvents.org.hibernate.cache.spi.UpdateTimestampsCache`, replicates by copy.
* **29 caches have a `SpringCacheEventListenerFactory` listener:** 13
  `tagTrackingCacheEventListener` and 16 `insufficientSizeCacheEventListener`, all
  `listenFor=local`.
* **1 cache is `<searchable>`:** `EntityPersonAttributesGroupStore.membership`.

### Application caches

Column key: size is `maxElementsInMemory`. TTL and TTI are in seconds, and 0 means none.
Repl is `inval` for invalidation over JGroups, `no` for not replicated. Listener is `tag` for
TagTracking and `size` for InsufficientSize. **Access** is how the code reaches the cache
after this branch:

* **Spring:** moved to Spring's `Cache` API through `CacheLookup`.
* **@Cacheable:** Spring cache annotations.
* **Ehcache:** still uses the Ehcache 2 API.
* **Map:** through `CacheFactory`/`MapCacheProvider`.
* **3rd party:** a library takes the Ehcache object.

| Cache | Key → value | Size | TTL | TTI | Repl | Listener | Callers | Access |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `org.apereo.services.persondir.USER_INFO.merged` | `CacheKey` (tagged username, seed) → `Set<IPersonAttributes>` | 1000 | 900 | 0 | no | tag | PersonDirectoryConfiguration / CachingPersonAttributeDao | Spring |
| `org.apereo.portal.layout.dlm.LAYOUT_CACHE` | `CacheKey` (tagged username, profile id) → `DistributedUserLayout` | 1000 | 7200 | 900 | inval | tag | LayoutCachingService | Spring |
| `...AuthorizationImpl.AUTH_PRINCIPAL_CACHE` | `Tuple<String, Class>` → `IAuthorizationPrincipal` | 1000 | 0 | 900 | no | tag | AuthorizationImpl | Spring |
| `...AuthorizationImpl.ENTITY_PARENTS_CACHE` | target `String` → `Set<String>` group keys | 1000 | 180 | 0 | no | — | AuthorizationImpl | Spring |
| `...AuthorizationImpl.PRINCIPAL_HAS_PERMISSION` | `CacheKey` (tagged username, policy, type, owner, activity, target) → `Boolean` | 50000 | 180 | 0 | no | tag | AuthorizationImpl | Spring |
| `...AnyUnblockedGrantPermissionPolicy.HAS_UNBLOCKED_GRANT` | `CacheKey` → `Boolean` | 250000 | 300 | 0 | no | tag | AnyUnblockedGrantPermissionPolicy | Spring |
| `SpELExpressionCache` | expression `String` → `Expression` | 5000 | 0 | 900 | no | — | PortalSpELServiceImpl | Spring |
| `portletSpELExpressionCache` | expression `String` → `Expression` | 100 | 0 | 900 | no | — | PortletSpELServiceImpl | Spring |
| `org.apereo.portal.events.tincan.LrsActorCache` | username → `LrsActor` | 10000 | 0 | 600 | no | — | LrsActorService | Spring |
| `org.apereo.portal.RDBMUserIdentityStore.userLockCache` | username → `SerializableObject` (lock) | 10000 | 0 | 600 | no | — | RDBMUserIdentityStore | Spring |
| `org.apereo.portal.portlet.dao.jpa.PortletEntityImpl.idParseCache` | consistent id `String` → `IPortletEntityId` | 15000 | 60 | 0 | no | — | PortletEntityRegistryImpl | Spring |
| `org.apereo.portal.portlet.rendering.SupportedEventCache` | `Tuple<IPortletDefinitionId, QName>` → `Boolean` | 1000 | 0 | 300 | no | — | PortletEventCoordinationHelper (+ Service) | Spring |
| `org.apereo.portal.EntityTypes.CLASS_BY_ID` / `ID_BY_CLASS` / `ALL` | id ↔ entity class | 50/50/1 | 28800 | 0 | inval | size (two of three) | EntityTypes | @Cacheable |
| `org.apereo.portal.i18n.RDBMLocaleStore.userLocales` | person id → `Locale[]` | 2000 | 600 | 0 | no | — | RDBMLocaleStore | @Cacheable |
| `org.apereo.portal.portlet.registry.PersistentPortletEntityWrapper.windowStates` | `"windowStates"` → window state map | 50 | 1800 | 0 | no | — | PersistentPortletEntityWrapper | @Cacheable |
| `org.apereo.portal.layout.dlm.FragmentActivator.userViews` / `userViewErrors` | fragment owner + locale → `UserView` / `Exception` | 100 | 600/30 | 0 | inval/no | size/— | FragmentActivator (`SelfPopulatingCache` with an error cache) | Ehcache |
| `org.apereo.portal.layout.dlm.RDBMDistributedLayoutStore.fragmentNodeInfoCache` | node id → `FragmentNodeInfo` | 1000 | 300 | 0 | no | size | RDBMDistributedLayoutStore | Ehcache |
| `org.apereo.portal.events.aggr.tabrender.TabRenderAggregator.layoutNodeIdNameResolver` | layout node id (may be `null`) → `Tuple<String, String>` | 1000 | 300 | 0 | no | — | JpaAggregatedTabLookupDao | Spring |
| `org.apereo.portal.jpa.cache.EntityManagerCache` | `CacheKey` → entity | 1000 | 0 | 300 | no | tag | EntityManagerCacheImpl | Ehcache |
| `org.apereo.portal.groups.IEntityGroup` / `IEntity` / `org.apereo.portal.security.IPermissionSet` | entity key → entity | 1000/2500/1000 | 21600 | 0 | inval | size/—/size | CacheFactoryEntityCachingService (name built from the entity class) | Map |
| `org.apereo.portal.groups.GroupMemberImpl.parentGroups` | member key → parent group keys | 5000 | 300 | 0 | inval | tag | GroupMemberImpl, LocalGroupsCacheAuthenticationListener (looked up through `ApplicationContextLocator`) | Ehcache |
| `org.apereo.portal.groups.EntityGroupImpl.children` | group key → child keys | 1000 | 300 | 0 | inval | size | EntityGroupImpl, LocalGroupsCacheAuthenticationListener | Ehcache |
| `org.apereo.portal.groups.RDBMEntityGroupStore.search` / `parentGroupEntity` / `parentGroupEntityGroup` | query or member → group keys | 500 | 300 | 0 | no | size | RDBMEntityGroupStore | Ehcache |
| `...pags.dao.EntityPersonAttributesGroupStore.entityGroup` / `pagsGroup` | group key → group | 625 | 300 | 0 | no | size | EntityPersonAttributesGroupStore | Ehcache |
| `...pags.dao.EntityPersonAttributesGroupStore.membership` | `MembershipCacheKey` → `Boolean`, searchable on `memberId` | 312500 | 300 | 0 | no | — | EntityPersonAttributesGroupStore, PagsMembershipCacheAuthenticationListener (search API) | Ehcache |
| `org.apereo.portal.groups.pags.testers.AdHocGroupTester` | person hash → test in progress | 1000 | 60 | 0 | no | — | AdHocGroupTester (`getQuiet`) | Ehcache |
| `...PortletCacheControlServiceImpl.{private,public}Scope{PortletRenderHeaderOutput,PortletRenderOutput,PortletResourceOutput}Cache` (6) | `PublicPortletCacheKey`/`PrivatePortletCacheKey` → `CachedPortletData` | 2500 | 0 | 300 (private) / 600 (public) | no | tag | PortletCacheControlServiceImpl (per-element TTL from portlet `expiration-cache`) | Ehcache |
| `org.apereo.portal.rendering.STRUCTURE_TRANSFORM` / `THEME_TRANSFORM` | `CacheKey` → `CachedEventReader` / `CachedCharacterEvents` | 500/10000 | 0 | 300 | no | — | RenderingPipelineConfiguration → CachingPipelineComponent, jsonRenderingPipelineContext.xml | Ehcache |
| `org.apereo.portal.utils.cache.resource.CachingResourceLoader` | resource → `CachedResource` | 500 | 0 | 900 | no | — | CachingResourceLoaderImpl (`SelfPopulatingCache`) | Ehcache |
| `org.apereo.portal.utils.ResourceLoader.RESOURCE_URL_CACHE` / `RESOURCE_URL_NOT_FOUND_CACHE` | resource name → URL / not-found marker | 250 | 28800/600 | 0 | no | — | ResourceLoader (cacheContext.xml) | Map |
| `org.apereo.portal.portlets.portletadmin.PortletAdministrationHelper.CPD_CACHE` | portlet type → `PortletPublishingDefinition` | 250 | 28800 | 0 | no | — | channelContainerContext.xml | Map |
| `org.apereo.portal.utils.cache.ConfigurablePageCachingFilter.PAGE_CACHE` | request → page | 250 | 0 | 900 | no | — | resource-server-utils `ConfigurablePageCachingFilter` (cacheContext.xml) | 3rd party |
| `...MarketplaceService.marketplacePortletDefinitionCache` / `marketplaceUserPortletDefinitionCache` / `marketplaceCategoryCache` | fname / username / categories → definition / `Future<Set<MarketplaceEntry>>` / list | 1000/1000/20 | 300/300/600 | 0 | no | — | MarketplaceService, PortletMarketplaceController | Ehcache |
| `org.apereo.portal.skinManager.failureCache` | css instance key → failure | 100 | 60 | 60 | no | — | Abstract/FileSystem/AwsS3DynamicSkinService | Spring |
| `org.apereo.portal.portlets.sqlquery.SqlQueryPortletController.queryResults` | query → results | 2000 | 300 | 0 | no | — | SqlQueryPortletController (`CacheManager.getInstance()`) | Ehcache |
| `org.apereo.portlet.soffit.connector.SoffitConnectorController.RESPONSE_CACHE` | `ResponseCacheTuple` → `ResponseWrapper` | 2000 | 0 | 0 | no | — | SoffitConnectorController (per-element TTL from the soffit's Cache-Control) | Ehcache |
| `org.apereo.portal.web.skin.Resources` / `Resources_DocumentFragment` | — | 50 | 28800 | 0 | no | — | no reference in this repository | unused? |
| `org.apereo.portal.layout.dlm.NODEREF_CACHE` | — | 2000 | 0 | 180 | no | — | no reference (ehcache.xml: "only used by a command line tool") | unused? |
| `org.jasig.cas.client.proxy.EhcacheBackedProxyGrantingTicketStorageImpl.cache` | PGT id → PGT | 10000 | 0 | 600 | no | — | CAS client, but `securityContext.xml` wires the non-Ehcache storage | unused? |
| `org.apereo.portal.utils.cache.DEFAULT_CACHE` (not configured) | — | defaultCache | | | | | `CacheFactory.getCache()` | Map |

### Hibernate second-level cache regions

These are owned by the Hibernate 5.2 workstream (MOD-D5) and are not touched here. The full
list is in the appendix, because P4-3 renames them. Findings from the inventory:

* **Regions used by mappings with no `<cache>` entry, so they get `defaultCache`:**
  `AggrEvents...SearchRequestAggregationImpl` and its `-NaturalId`,
  `AggrEvents...PortletLayoutAggregationImpl-NaturalId`, the three PAGS definition
  `-NaturalId` regions, `ProfileSelection` (plus its natural-id region),
  `PortletLifecycleEntryImpl`, and `org.apereo.portal.dao.portletlist.jpa.PortletList.Query`.
  `PortletList`/`PortletListItem` have `@Cacheable` but no `@Cache`. Lower confidence.
* **Probably dead entries:** `FragmentDefinition.evaluators` and `Paren.evaluators`. The
  collection role is `EvaluatorGroup.evaluators`, because the field is inherited. The
  `Evaluator` subclass entity regions are probably dead too, because a single-table hierarchy
  shares the root region. Not confirmed at runtime.
* **Four regions carry the Hibernate 4.3 names that 5.3 renamed:** `StandardQueryCache` and
  `UpdateTimestampsCache`, each with and without the `AggrEvents.` prefix.

## Callers: moved and remaining

The `net.sf.ehcache` API was used in 47 main source files. After this branch it is used in 32,
counting the Javadoc-only reference in `CacheHealthReporterService`. **Moved (16 files):**
PersonDirectoryConfiguration, PortalSpELServiceImpl, PortletSpELServiceImpl,
LayoutCachingService, AuthorizationImpl, AnyUnblockedGrantPermissionPolicy, LrsActorService,
RDBMUserIdentityStore, PortletEntityRegistryImpl, PortletEventCoordinationHelper,
PortletEventCoordinatationService, CacheHealthReporterService, JpaAggregatedTabLookupDao and
the three dynamic skin services (Abstract, FileSystem and AwsS3). spring-modules-cache is
gone (2 files).

**Remaining, grouped by what each needs:**

1. **Plain get/put/remove, ready to move the same way (next PRs):**
   * EntityManagerCacheImpl. MOD-I102 is now fixed on the integration branch.
     `sharedJpaTestContext.xml` gives it a Mockito `Ehcache`; replace that with a real Ehcache
     behind an `EhCacheCacheManager`, as `jpaAggrEventsTestContext.xml` now does.
   * RDBMDistributedLayoutStore (`fragmentNodeInfoCache`).
   * CachingPipelineComponent (structure and theme transform caches).
   * MarketplaceService and PortletMarketplaceController.
   * SqlQueryPortletController. Its `CacheManager.getInstance()` fallback relies on the
     `shared=true` singleton.
   * The groups classes (GroupMemberImpl, EntityGroupImpl, LocalGroupsCacheAuthenticationListener,
     RDBMEntityGroupStore, EntityPersonAttributesGroupStore entityGroup and pagsGroup,
     AdHocGroupTester). They look caches up through the static `ApplicationContextLocator`, so
     the change is to fetch the Spring `CacheManager` the same way.
2. **Self-populating with extra semantics:**
   * FragmentActivator: the user view loader stores failures in `userViewErrors` and rethrows
     them. That maps to `get(key, loader)` plus an explicit error cache.
   * CachingResourceLoaderImpl: a `SelfPopulatingCache` with a `ThreadLocalCacheEntryFactory`.
3. **Exceptions: things Spring's `Cache` cannot express.** Each needs a thin uPortal interface
   or a provider-specific adapter:
   * **Per-entry TTL:** PortletCacheControlServiceImpl (the portlet's `expiration-cache` and
     the cache's TTI) and SoffitConnectorController (Cache-Control `max-age`). Spring's `Cache`
     has no per-entry expiry, and neither does JCache's put. Proposal: store the expiry time in
     the value and check it on read, or use Caffeine's `Expiry` behind a small `ExpiringCache`
     interface.
   * **Search API:** `PagsMembershipCacheAuthenticationListener` searches `membership` by the
     `memberId` attribute (`EhcacheMemberIdAttributeExtractor`, `<searchable>`) to evict a
     user's entries. Proposal: tag the `MembershipCacheKey` with the username
     (`TaggedCacheEntry`) and purge by tag, as the layout and permission caches do. That
     removes the only search use.
   * **Statistics and administration:** `CacheManagementHelper`, used by the Cache Manager
     portlet, reads Ehcache `Statistics` and `CacheConfiguration`. Proposal: a
     `CacheStatisticsProvider` interface with an Ehcache 2 implementation now, and JCache
     (`CacheStatisticsMXBean`) or Caffeine (`stats()`) later. `clearCache` and `clearAllCaches`
     can already go through Spring (`getCacheNames()` and `clear()`).
   * **Event listeners:** `TagTrackingCacheEventListener` (tag purge),
     `InsufficientSizeCacheEventListener` (health report) and `SpringCacheEventListenerFactory`
     (wiring from ehcache.xml) are Ehcache 2 listener SPI. JCache has `CacheEntryListener`
     (created, updated, removed, expired, but no "evicted"), and Caffeine has `RemovalListener`.
     The tag tracking can move into a `TaggingCache` decorator around Spring's `Cache`; that
     decorator is provider-neutral and records tags on `put`. The health report is already
     provider-neutral (this branch).
   * **Expired-element eviction job:** `ExpiredElementEvictor` (`schedulerContext.xml`,
     every ~62 s) calls `evictExpiredElements()` on every cache. Ehcache 3 and Caffeine expire
     entries themselves, so P4-3 deletes the job rather than porting it. MOD-Q11 should record
     that.
   * **Map adapter:** `CacheProviderFactory` and `MapCacheProvider` (`MapCacheFactoryBean`,
     `CacheFactoryEntityCachingService`) expose caches as `Map`s and **create unconfigured
     caches on demand from `defaultCache`**, logging "Created new default EhCache". That is the
     opposite of `missing_cache_strategy=fail`. Proposal: back `MapCacheProvider` with Spring's
     `Cache` (`containsKey` → `get != null`; `size`/`containsValue` aren't supported and have
     no callers) and make an unknown name fail, once the coverage test covers the built names
     (`IEntityGroup`, `IEntity`, `IPermissionSet`).
   * **Bean registration:** `EhcacheManagerBeanConfigurer` registers every Ehcache as a bean
     named after the cache, for the remaining `@Qualifier`/`@Resource` injections. It goes away
     once the last of those has moved.
   * **Third-party users of Ehcache 2:**
     * resource-server-utils `ConfigurablePageCachingFilter` (`PAGE_CACHE`) takes the Ehcache
       `CacheManager`. It needs a replacement filter or a release of resource-server built on
       Spring's `Cache`.
     * The Hibernate L2 `SpringBeanEhCacheRegionFactory` is owned by the Hibernate
       workstream.
     * `RequestCacheAspect` only uses `EhcacheHibernateMbeanNames.mbeanSafe` (a string
       escaper), which is trivial to inline.

## Tests

| Test | Module | What it shows |
| --- | --- | --- |
| `CachingPersonAttributeDaoTest` (8) | uPortal-events | User attributes on a real Ehcache with the tag listener: miss then hit for equal seeds, users cached separately, null results cached as a marker (or not, when disabled), seeds without key attributes not cached, `removeUserAttributes` eviction, the logout purge by username tag, and key equality (equal seeds, empty attributes ignored, username tag present). |
| `LayoutCachingServiceTest` (4) | uPortal-layout-impl | Hit for an equal user and profile and miss otherwise, eviction on `removeCachedLayout` (the layout update path), the logout purge of only that user's layouts, and fail-fast on a missing cache. |
| `AuthorizationImplCacheTest` (3) | uPortal-security-permissions | Permission checks cached per principal and target, and purged per user at logout. |
| `PortalSpELServiceImplTest` (+2) | uPortal-utils-core | Parsed expressions are cached; a missing cache fails fast. |
| `LrsActorServiceTest` (2) | uPortal-events | An actor is resolved once per user; a missing cache fails fast. |
| `InsufficientSizeCacheEventListenerTest` (2) | uPortal-utils-core | A too-small cache reports its name on eviction; entries that fit are not reported. |
| `CacheConfigurationCoverageTest` (2) | uPortal-webapp | Every cache name used in main sources is in both ehcache files. It finds 45 names: `requireCache` arguments, `@Qualifier`/`@Resource` on Ehcache or Spring Cache injection points, `getCache("…")`, Spring cache annotations and XML `cacheName` properties. |

That is 23 new tests. No existing tests were removed. The Ehcache mocks in
`PortletEntityRegistryImplTest` and `PortletEventCoordinatationServiceTest` became Spring
`Cache` mocks.

**Not covered by unit tests:**

* **Portlet-definition invalidation.** The marketplace and portlet-definition caches are still
  on Ehcache, and the publishing flow is covered by Playwright.
* **Invalidation of the `IPermissionSet` entity cache on permission updates.**
  `AuthorizationImpl` goes through the static `EntityCachingService`.
* **The principal cache.** Creating an `AuthorizationPrincipalImpl` needs the portal's
  application context.

The integration run (Playwright, three databases) covers these paths end to end.

### Seeded faults

Each fault was applied to the source, the module's tests were run, and the change was
reverted; the logs are in the session scratchpad. Every fault made the tests fail:

| Fault | Where | Result |
| --- | --- | --- |
| Invalidation removed: `removeUserAttributesMultivaluedSeed` no longer evicts | CachingPersonAttributeDao | 1 of 8 failed |
| Wrong key: seed values dropped from the user attribute key | PersonDirectoryCacheKeyGenerator | 6 of 8 failed |
| Username tag dropped from multivalued seeds | PersonDirectoryCacheKeyGenerator | 2 of 8 failed |
| Invalidation removed: `removeCachedLayout` no longer evicts | LayoutCachingService | 1 of 6 failed |
| Wrong key: profile id dropped from the layout key | LayoutCachingService | 3 of 6 failed |
| Wrong key: target dropped from the permission key | AuthorizationImpl | 1 of 3 failed |
| Username tag dropped from the permission key (logout purge) | AuthorizationImpl | 1 of 3 failed |
| Missing cache tolerated: `CacheLookup` returns null instead of failing | CacheLookup | 1 failed |
| Missing cache configuration: `LAYOUT_CACHE` renamed in ehcache-no-jgroups.xml | ehcache-no-jgroups.xml | coverage test failed and named `LayoutCachingService.java` |

One fault survived at first: the tag was dropped only from the single-valued (`String`) seed
branch of the key generator. `CachingPersonAttributeDao` always passes multivalued seeds, so
that branch is unreachable through it. The fault was re-seeded in the list branch that
production uses (third row).

## Gates

GATE-RESULTS-PLACEHOLDER

* **Local runs (JDK 21):** all green.
  * Tests and SpotBugs for utils-core, events, layout-impl, security-permissions, persondir,
    rendering, rdbm, groups-core, groups-pags and core, plus `spotlessCheck`.
  * Tests for `uPortal-web`, `uPortal-portlets`, `uPortal-soffit-connector` and
    `uPortal-webapp`, and SpotBugs for web and webapp.
  * Altogether 1044 tests ran with 0 failures, and no test files were deleted.
* **jitpack.io workaround:** jitpack.io (`google-visualization-java`) is denied by this
  environment's network policy. The webapp-side modules ran after the official jar was
  installed into `~/.m2` from the released uPortal 5.17.9 WAR on Maven Central (sha256
  `eca09172…bad63`, the coordinator's workaround, not committed).

## Proposed LOG entries

* **MOD-D? (P3-3 seam):** uPortal code gets caches only through Spring's `CacheManager` and
  `CacheLookup.requireCache`, which fails on an unconfigured name. Spring's `Cache` API is
  used where it fits, and `get(key, loader)` replaces `SelfPopulatingCache`. The exceptions
  are listed in `notes/cache-seam.md`: per-entry TTL, search API, statistics, Ehcache
  listeners, the expired-element eviction job, the `Map` adapter, and third-party Ehcache
  users. They get thin uPortal interfaces in later PRs. spring-modules-cache is removed.
  Behaviour, sizes, TTLs and replication are unchanged.
* **MOD-I? (Caching):** `CacheProviderFactory` (`CacheFactory`/`MapCacheFactoryBean`) creates
  any unconfigured cache from `defaultCache` (1000 entries, 600 s TTL, not replicated) with
  only a warning. Several Hibernate regions also have no entry and get the same defaults; they
  are listed in the note. Impact: a typo or rename silently changes size, TTL and replication.
  Plan: `CacheConfigurationCoverageTest` for the application names now, and P4-3 sets
  `missing_cache_strategy=fail` for Hibernate and makes `MapCacheProvider` fail on unknown
  names.
* **MOD-I? (Caching):** `ehcache.xml` has entries that nothing references:
  * `web.skin.Resources` and `web.skin.Resources_DocumentFragment`;
  * `layout.dlm.NODEREF_CACHE`;
  * the CAS `EhcacheBackedProxyGrantingTicketStorageImpl.cache`;
  * `FragmentDefinition.evaluators` and `Paren.evaluators`.

  Plan: confirm on a running portal (Cache Manager portlet hit counts), then drop them when the
  provider configuration is generated (P4-3).
* **MOD-I51 (existing):** jitpack.io is blocked. Nothing new to log: this workstream used the
  coordinator's workaround, which installs the jar from the 5.17.9 WAR.
* **MOD-Q11 update:** the Ehcache expired-element eviction job is Ehcache 2-specific and is
  deleted at the provider switch (Ehcache 3 and Caffeine expire entries themselves). The cache
  health report job stays and is fed by provider listeners.

## Plan for P4-3 and the 6.0 provider profiles

P4-3 is the Hibernate 5.6.15 step, with `hibernate-jcache` for the L2 cache, the region renames
and the provider switch. With the seam in place, it changes configuration rather than callers.

### What's left before or during P4-3

1. **Finish moving callers** (the remaining list above). Then delete
   `EhcacheManagerBeanConfigurer`, and the only `net.sf.ehcache` imports left are the
   adapters: listeners, statistics, the eviction job and the Hibernate region factory.
2. **Add the three interfaces from the exceptions list,** each with an Ehcache 2
   implementation first:
   * `ExpiringCache` for per-entry TTL;
   * a tagging decorator that replaces `TagTrackingCacheEventListener`;
   * `CacheStatisticsProvider`.

   Replace the PAGS search with tags.
3. **One region list as the source of truth.** Generate `ehcache.xml` (Ehcache 2) today, and
   the JCache, Infinispan or Caffeine configuration later, from one region file holding name,
   size, TTL, TTI, replication mode and listeners. The coverage test then checks the code
   against that file.
4. **Rename the Hibernate regions** for Hibernate 5.3+ naming (owned by the Hibernate
   workstream):
   * `StandardQueryCache` → `default-query-results-region` and `UpdateTimestampsCache` →
     `default-update-timestamps-region`, with and without the `AggrEvents.` prefix;
   * the entity, collection and natural-id regions keep their class-based names, with the
     region prefix set per persistence unit (`AggrEvents.` for the aggregation unit).

   The timestamps region must not expire. Set `hibernate.javax.cache.missing_cache_strategy=fail`
   in every test JVM. The appendix lists all 126 regions.
5. **Switch the Spring cache manager bean.** Keep its name (`ehCacheManager`, perhaps aliased
   to `cacheManager` once the Ehcache 2 `cacheManager` bean is gone) and pick its class by
   profile:
   * **(a) single node:** `CaffeineCacheManager` with per-cache specs from the region file.
     Caffeine is Java 11-compatible, so it works before the runtime JDK bump. Or
     `JCacheCacheManager` over Ehcache 3.12, which needs Java 17. Hibernate L2 uses
     `hibernate-jcache` on the same provider.
   * **(b) embedded cluster:** `JCacheCacheManager` over Infinispan embedded, with JGroups.
     Application caches in invalidation mode match today's `replicatePuts=false`. Hibernate L2
     uses `infinispan-hibernate-cache-v66` (MOD-I39) for Hibernate 6.6 and 7.1+, and the
     Infinispan JCache region factory before that.
     * The one copy-replicated region today, `AggrEvents.UpdateTimestampsCache`, maps to
       Infinispan's replicated timestamps cache.
     * Ehcache 3 is not an option for clusters: it clusters only through Terracotta.
   * **(c) Valkey/Redis:** `RedisCacheManager` (Spring Data Redis, Lettuce), or Redisson's
     JCache, with a local near-cache for the hot regions (layout, permissions, groups, XSLT).
     The Hibernate L2 story for Redisson against Hibernate 7.4 is unverified (MOD-Q10).
6. **Handle the provider-specific pieces per profile:**
   * Per-entry TTL: Caffeine `Expiry`, Infinispan `put(k, v, lifespan)`, Redis per-key TTL.
   * Listeners:
     * Tag purge moves into the decorator, so it needs nothing from the provider.
     * Size reports: Caffeine `RemovalCause.SIZE`, Infinispan
       `@CacheEntriesEvicted`, Redis none.
   * Statistics: Caffeine `recordStats`, JCache MXBeans.
   * Delete `ExpiredElementEvictor`.
7. **`javax.cache` stays `javax`** (research report). Exclude it from the jakarta rewrite.

### What the two-node cluster test must show for the switch

Today, on `ccr-43ba0785-hzlrj5-cluster`, with Ehcache 2 and JGroups, record each check below.
Then require the same results on profile (b), and on (c) where it applies:

1. **Layout invalidation:** user A changes a layout on node 1 (adds a tab); node 2 serves the
   new layout on A's next request (`LAYOUT_CACHE`, inval).
2. **Group membership:** an admin adds a user to a group on node 1; permission and
   group-dependent content changes on node 2 within the cache TTL (`IEntityGroup`,
   `GroupMemberImpl.parentGroups`, `EntityGroupImpl.children`, inval).
3. **Portlet definitions:** an admin edits or publishes a portlet on node 1; node 2 shows the
   change. This covers the Hibernate L2 entity regions and the `.Query` regions.
4. **Entity types:** `EntityTypes` caches (inval) stay consistent.
5. **Logout purge stays local:** the username tag purge (`listenFor=local`) does not need to
   cross nodes. A user's cached attributes and permissions on node 2 expire by TTL, as today.
   Record this, so a new provider doesn't "fix" it by surprise.
6. **No copies of non-replicated caches:** for example, `USER_INFO.merged` and
   `PRINCIPAL_HAS_PERMISSION` entries exist only on the node that computed them.
7. **Timestamps:** query-cache invalidation through the timestamps region works across nodes
   (the aggregation unit replicates it by copy).
8. **Restart and rejoin:** a node restarts and rejoins the cluster; it starts cold and
   receives invalidations.

## Appendix: Hibernate second-level cache regions in ehcache.xml

All 126 regions. Columns: region, kind, maxElementsInMemory, TTL (s), TTI (s), replication.

| Region | Kind | Size | TTL | TTI | Repl |
| --- | --- | --- | --- | --- | --- |
| `AggrEvents.org.apereo.portal.events.aggr.UniqueStrings.uniqueStringSegments` | hibernate-collection | 10000 | 0 | 600 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.UniqueStringsSegment.uniqueStrings` | hibernate-collection | 100000 | 0 | 600 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.UniqueStringsSegment` | hibernate-entity | 100000 | 0 | 600 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.UniqueStrings` | hibernate-entity | 10000 | 0 | 600 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.concuser.ConcurrentUserAggregationImpl-NaturalId` | hibernate-entity (natural id) | 5000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.concuser.ConcurrentUserAggregationImpl.Query` | hibernate-query | 5000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.concuser.ConcurrentUserAggregationImpl` | hibernate-entity | 5000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AcademicTermDetailImpl.Query` | hibernate-query | 100 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AcademicTermDetailImpl` | hibernate-entity | 100 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AggregatedGroupConfigImpl.Query` | hibernate-query | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AggregatedGroupConfigImpl.excludedGroups` | hibernate-collection | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AggregatedGroupConfigImpl.includedGroups` | hibernate-collection | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AggregatedGroupConfigImpl` | hibernate-entity | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AggregatedIntervalConfigImpl.Query` | hibernate-query | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AggregatedIntervalConfigImpl.excludedIntervals` | hibernate-collection | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AggregatedIntervalConfigImpl.includedIntervals` | hibernate-collection | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.AggregatedIntervalConfigImpl` | hibernate-entity | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.DateDimensionImpl-NaturalId` | hibernate-entity (natural id) | 3660 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.DateDimensionImpl.Query` | hibernate-query | 3660 | 0 | 600 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.DateDimensionImpl` | hibernate-entity | 3660 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.EventAggregatorStatusImpl-NaturalId` | hibernate-entity (natural id) | 10 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.QuarterDetailImpl.Query` | hibernate-query | 5 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.QuarterDetailImpl` | hibernate-entity | 4 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.TimeDimensionImpl-NaturalId` | hibernate-entity (natural id) | 1440 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.TimeDimensionImpl.Query` | hibernate-query | 1440 | 0 | 600 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.dao.jpa.TimeDimensionImpl` | hibernate-entity | 1440 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.groups.AggregatedGroupMappingImpl-NaturalId` | hibernate-entity (natural id) | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.groups.AggregatedGroupMappingImpl.Query` | hibernate-query | 1000 | 0 | 600 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.groups.AggregatedGroupMappingImpl` | hibernate-entity | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.login.LoginAggregationImpl-NaturalId` | hibernate-entity (natural id) | 5000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.login.LoginAggregationImpl.Query` | hibernate-query | 5000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.login.LoginAggregationImpl` | hibernate-entity | 5000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.portletexec.PortletExecutionAggregationImpl-NaturalId` | hibernate-entity (natural id) | 100000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.portletexec.PortletExecutionAggregationImpl.Query` | hibernate-query | 100000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.portletexec.PortletExecutionAggregationImpl` | hibernate-entity | 100000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.portlets.AggregatedPortletMappingImpl-NaturalId` | hibernate-entity (natural id) | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.portlets.AggregatedPortletMappingImpl.Query` | hibernate-query | 1000 | 0 | 600 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.portlets.AggregatedPortletMappingImpl` | hibernate-entity | 1000 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.session.EventSessionImpl-NaturalId` | hibernate-entity (natural id) | 100000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.session.EventSessionImpl.Query` | hibernate-query | 100000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.session.EventSessionImpl.groupMappings` | hibernate-collection | 100000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.session.EventSessionImpl` | hibernate-entity | 100000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.tabrender.TabRenderAggregationImpl-NaturalId` | hibernate-entity (natural id) | 10000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.tabrender.TabRenderAggregationImpl.Query` | hibernate-query | 10000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.tabrender.TabRenderAggregationImpl` | hibernate-entity | 10000 | 0 | 600 | no |
| `AggrEvents.org.apereo.portal.events.aggr.tabs.AggregatedTabMappingImpl-NaturalId` | hibernate-entity (natural id) | 500 | 0 | 0 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.tabs.AggregatedTabMappingImpl.Query` | hibernate-query | 500 | 0 | 600 | inval |
| `AggrEvents.org.apereo.portal.events.aggr.tabs.AggregatedTabMappingImpl` | hibernate-entity | 500 | 0 | 0 | inval |
| `AggrEvents.org.hibernate.cache.internal.StandardQueryCache` | hibernate-query | 100 | 0 | 300 | no |
| `AggrEvents.org.hibernate.cache.spi.UpdateTimestampsCache` | hibernate-query | 100 | 0 | 0 | copy |
| `org.apereo.portal.concurrency.locking.ClusterMutex-NaturalId` | hibernate-entity (natural id) | 100 | 0 | 0 | inval |
| `org.apereo.portal.groups.pags.dao.jpa.PersonAttributesGroupDefinitionImpl.Query` | hibernate-query | 625 | 300 | 0 | no |
| `org.apereo.portal.groups.pags.dao.jpa.PersonAttributesGroupDefinitionImpl.members` | hibernate-collection | 625 | 300 | 0 | no |
| `org.apereo.portal.groups.pags.dao.jpa.PersonAttributesGroupDefinitionImpl.testGroups` | hibernate-collection | 1000 | 300 | 0 | no |
| `org.apereo.portal.groups.pags.dao.jpa.PersonAttributesGroupDefinitionImpl` | hibernate-entity | 625 | 300 | 0 | no |
| `org.apereo.portal.groups.pags.dao.jpa.PersonAttributesGroupTestDefinitionImpl` | hibernate-entity | 1000 | 300 | 0 | no |
| `org.apereo.portal.groups.pags.dao.jpa.PersonAttributesGroupTestGroupDefinitionImpl.tests` | hibernate-collection | 1000 | 300 | 0 | no |
| `org.apereo.portal.groups.pags.dao.jpa.PersonAttributesGroupTestGroupDefinitionImpl` | hibernate-entity | 1000 | 300 | 0 | no |
| `org.apereo.portal.i18n.dao.jpa.MessageImpl.Query` | hibernate-query | 1000 | 0 | 60 | inval |
| `org.apereo.portal.i18n.dao.jpa.MessageImpl` | hibernate-entity | 1000 | 0 | 60 | inval |
| `org.apereo.portal.layout.dao.jpa.LayoutAttributeDescriptorImpl.targetElementNames` | hibernate-collection | 1000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.LayoutAttributeDescriptorImpl` | hibernate-entity | 1000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.LayoutNodeAttributesImpl.attributes` | hibernate-collection | 10000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.LayoutNodeAttributesImpl` | hibernate-entity | 1000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.OutputPropertyDescriptorImpl` | hibernate-entity | 10000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetDescriptorImpl-NaturalId` | hibernate-entity (natural id) | 1000 | 0 | 0 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetDescriptorImpl.Query` | hibernate-query | 1000 | 0 | 300 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetDescriptorImpl.layoutAttributes` | hibernate-collection | 10000 | 0 | 300 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetDescriptorImpl.outputProperties` | hibernate-collection | 10000 | 0 | 300 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetDescriptorImpl.stylesheetParameters` | hibernate-collection | 10000 | 0 | 300 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetDescriptorImpl` | hibernate-entity | 1000 | 0 | 300 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetParameterDescriptorImpl` | hibernate-entity | 1000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetUserPreferencesImpl.Query` | hibernate-query | 1000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetUserPreferencesImpl.layoutAttributes` | hibernate-collection | 1000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetUserPreferencesImpl.outputProperties` | hibernate-collection | 1000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetUserPreferencesImpl.parameters` | hibernate-collection | 1000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dao.jpa.StylesheetUserPreferencesImpl` | hibernate-entity | 1000 | 0 | 3600 | inval |
| `org.apereo.portal.layout.dlm.Evaluator` | hibernate-entity | 100 | 21600 | 0 | inval |
| `org.apereo.portal.layout.dlm.FragmentDefinition.Query` | hibernate-query | 100 | 21600 | 0 | inval |
| `org.apereo.portal.layout.dlm.FragmentDefinition.evaluators` | hibernate-collection (field `evaluators` is inherited from EvaluatorGroup (SINGLE_TABLE hierarchy rooted at Evaluator); Hibernate collection role is EvaluatorGroup.evaluators, so this region is probably never used) | 100 | 21600 | 0 | inval |
| `org.apereo.portal.layout.dlm.providers.AllUsersEvaluatorFactory` | hibernate-entity | 500 | 21600 | 0 | inval |
| `org.apereo.portal.layout.dlm.providers.AttributeEvaluator` | hibernate-entity | 500 | 21600 | 0 | inval |
| `org.apereo.portal.layout.dlm.providers.EvaluatorGroup.evaluators` | hibernate-collection | 1000 | 21600 | 0 | inval |
| `org.apereo.portal.layout.dlm.providers.GroupMembershipEvaluator` | hibernate-entity | 500 | 21600 | 0 | inval |
| `org.apereo.portal.layout.dlm.providers.GuestUserEvaluatorFactory` | hibernate-entity | 500 | 21600 | 0 | inval |
| `org.apereo.portal.layout.dlm.providers.Paren.evaluators` | hibernate-collection (same as FragmentDefinition.evaluators: inherited from EvaluatorGroup; region probably never used) | 1000 | 21600 | 0 | inval |
| `org.apereo.portal.layout.dlm.providers.ProfileEvaluator` | hibernate-entity | 500 | 21600 | 0 | inval |
| `org.apereo.portal.permission.dao.jpa.PermissionActivityImpl` | hibernate-entity | 500 | 21600 | 0 | inval |
| `org.apereo.portal.permission.dao.jpa.PermissionOwnerImpl-NaturalId` | hibernate-entity (natural id) | 100 | 0 | 0 | inval |
| `org.apereo.portal.permission.dao.jpa.PermissionOwnerImpl.Query` | hibernate-query | 100 | 21600 | 0 | inval |
| `org.apereo.portal.permission.dao.jpa.PermissionOwnerImpl.activities` | hibernate-collection | 100 | 21600 | 0 | inval |
| `org.apereo.portal.permission.dao.jpa.PermissionOwnerImpl` | hibernate-entity | 100 | 21600 | 0 | inval |
| `org.apereo.portal.persondir.dao.jpa.LocalAccountPersonAttributeImpl.Query` | hibernate-query | 500 | 1800 | 900 | inval |
| `org.apereo.portal.persondir.dao.jpa.LocalAccountPersonAttributeImpl.values` | hibernate-collection | 500 | 1800 | 900 | inval |
| `org.apereo.portal.persondir.dao.jpa.LocalAccountPersonAttributeImpl` | hibernate-entity | 500 | 1800 | 900 | inval |
| `org.apereo.portal.persondir.dao.jpa.LocalAccountPersonImpl.Query` | hibernate-query | 500 | 1800 | 900 | inval |
| `org.apereo.portal.persondir.dao.jpa.LocalAccountPersonImpl.attributes` | hibernate-collection | 500 | 1800 | 900 | inval |
| `org.apereo.portal.persondir.dao.jpa.LocalAccountPersonImpl` | hibernate-entity | 500 | 1800 | 900 | inval |
| `org.apereo.portal.portlet.dao.jpa.MarketplaceRatingImpl.Query` | hibernate-query | 250 | 21600 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.MarketplaceRatingImpl` | hibernate-entity | 250 | 21600 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortalCookieImpl-NaturalId` | hibernate-entity (natural id) | 2500 | 0 | 900 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortalCookieImpl.Query` | hibernate-query | 2500 | 900 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortalCookieImpl.portletCookies` | hibernate-collection | 2500 | 900 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortalCookieImpl` | hibernate-entity | 2500 | 900 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletCookieImpl` | hibernate-entity | 25000 | 900 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletDefinitionImpl-NaturalId` | hibernate-entity (natural id) | 1000 | 21600 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletDefinitionImpl.Query` | hibernate-query | 1000 | 21600 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletDefinitionImpl.localizations` | hibernate-collection | 10000 | 21600 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletDefinitionImpl.parameters` | hibernate-collection | 10000 | 21600 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletDefinitionImpl` | hibernate-entity | 1000 | 21600 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletDefinitionParameterImpl` | hibernate-entity | 10000 | 21600 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletEntityImpl.Query` | hibernate-query | 1500 | 1800 | 900 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletEntityImpl.windowStates` | hibernate-collection | 15000 | 1800 | 900 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletEntityImpl` | hibernate-entity | 15000 | 1800 | 900 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletPreferenceImpl.values` | hibernate-collection | 2200 | 1800 | 900 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletPreferenceImpl` | hibernate-entity | 2200 | 1800 | 900 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletPreferencesImpl.portletPreferences` | hibernate-collection | 15500 | 1800 | 900 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletPreferencesImpl` | hibernate-entity | 15500 | 1800 | 900 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletTypeImpl-NaturalId` | hibernate-entity (natural id) | 250 | 0 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletTypeImpl.Query` | hibernate-query | 250 | 21600 | 0 | inval |
| `org.apereo.portal.portlet.dao.jpa.PortletTypeImpl` | hibernate-entity | 250 | 21600 | 0 | inval |
| `org.apereo.portal.tenants.JpaTenant.Query` | hibernate-query | 10 | 300 | 0 | no |
| `org.apereo.portal.tenants.JpaTenant.attributes` | hibernate-collection | 250 | 900 | 0 | inval |
| `org.apereo.portal.tenants.JpaTenant` | hibernate-entity | 25 | 900 | 0 | inval |
| `org.apereo.portal.version.dao.jpa.VersionImpl-NaturalId` | hibernate-entity (natural id) | 100 | 0 | 0 | inval |
| `org.hibernate.cache.internal.StandardQueryCache` | hibernate-query | 1000 | 300 | 0 | no |
| `org.hibernate.cache.spi.UpdateTimestampsCache` | hibernate-query | 1000 | 0 | 0 | no |
