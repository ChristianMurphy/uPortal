---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Prefer the Spring approach for every migration, and cache through Spring's Cache API

## Context and Problem Statement

uPortal is a Spring application, but many of its libraries are older than the Spring features
that now do the same jobs. Several of them stop working on the 6.0 platform (ADR 0002):

* Ehcache 2 is used directly in 58 files (`Element`, `SelfPopulatingCache` and the search API),
  with 184 cache regions and JGroups replication. Spring 6 dropped its Ehcache 2 support, and
  Hibernate 6 dropped `hibernate-ehcache`.
* Joda-Time is imported in 86 files, and Spring 6 dropped its Joda-Time formatting.
* REST calls from web components authenticate with custom JWT code on jjwt (10 files).
* JSON views use json-lib (`net.sf.json`, 3 files) through json-lib-ext-spring, last released in
  2008.
* Passwords are hashed with jasypt (MD5 and SHA-256), which has been unmaintained since 2019.

When something has to be replaced, which replacement should uPortal pick? And which API should
caching use?

## Decision Drivers

* Fewer libraries outside the Spring ecosystem to keep current.
* Replacements that Spring keeps compatible across its own major versions.
* Clustered portals must keep cache invalidation across nodes.
* Adopters see breaking changes only where they gain something from them.

## Considered Options

* Prefer the Spring approach wherever one exists, and cache through Spring's Cache API
* Choose a replacement library case by case, and cache through the JCache API (JSR-107)
* Keep each existing library until it blocks an upgrade

## Decision Outcome

Chosen option: "Prefer the Spring approach wherever one exists, and cache through Spring's Cache
API", because it gives one consistent rule and the smallest set of outside libraries.

| Area | uPortal 5.17 | Spring approach |
| --- | --- | --- |
| Caching | Ehcache 2 API in 58 files, spring-modules-cache | Spring's Cache API (`@Cacheable`, `@CacheEvict`, `CacheManager`) |
| Sessions | Spring Session 1.3 with Redis (Jedis) | Spring Session 4.x with Lettuce, which also works with Valkey |
| Built-in portlets | Spring Portlet MVC and Web Flow's portlet integration | Spring MVC controllers with Thymeleaf, and `@RestController` with Jackson (ADR 0012) |
| REST authentication for web components | jjwt | Spring Security OAuth2 Resource Server (JWT). uPortal keeps issuing the tokens. |
| CAS client | cas-client filters wired by hand (8 files) | `spring-security-cas`, to be evaluated with Spring 7 |
| HTTP clients | `RestTemplate` (9 files) on HttpClient 4 | `RestClient` on the JDK or HttpClient 5 request factory |
| Password hashing | jasypt MD5 and SHA-256 | Spring Security's `DelegatingPasswordEncoder`, rehashing on login |
| Dates | Joda-Time (86 files) | `java.time` with Spring's JSR-310 formatting (ADR 0007) |
| JSON views | json-lib (3 files) | Spring MVC's Jackson message converters |
| Scheduling | `task:scheduled`, `@Scheduled`, a custom cluster lock | `@Scheduled` and `TaskScheduler`, and Spring Integration's `JdbcLockRegistry` if it proves simpler than the custom lock |
| Retry | none, or written by hand | Spring Framework 7's `@Retryable` |
| Database copy (ADR 0008) | none in 5.17 | Spring Batch and Spring JDBC in a Spring Boot command-line tool |
| Configuration | 113 XML contexts | `@Configuration` classes for new code. XML is converted when a context is touched. |
| Observability | custom JMX statistics (3 files) | Micrometer through Spring's observation API |

Where no Spring option exists, or where the Spring option would break adopters without a benefit,
uPortal keeps its existing choice. The portlet container for third-party portlets, Pluto, and
the XSLT rendering pipeline are such cases (ADR 0011).

For caching, uPortal code calls Spring's Cache API: `@Cacheable`, `@CacheEvict` and `@CachePut`,
`@Cacheable(sync = true)` where `SelfPopulatingCache` is used today, and `CacheManager` and `Cache`
for the rest. The cache provider is configuration:

* One node uses Caffeine or Ehcache 3.
* An embedded cluster uses Infinispan with JGroups invalidation, the closest match to Ehcache 2
  with JGroups.
* An external Valkey or Redis server uses Spring Data Redis's `RedisCacheManager` or Redisson, as
  proposed and still to be tested.

Hibernate's second-level cache keeps Hibernate's own interface (`hibernate-jcache`, or
Infinispan's region factory) and shares the provider, so a clustered portal also invalidates
entities across nodes.

### Consequences

* Good, because Spring 4.3 already includes `CaffeineCacheManager` and `JCacheCacheManager`, so
  the caching change can go in before any Spring upgrade.
* Good, because the `javax.cache` package never moves to `jakarta`, so caching can be finished
  before the namespace switch.
* Good, because one rule settles many small choices without a separate debate for each.
* Bad, because Spring's Cache API doesn't cover Ehcache 2's search API, its 29 cache event
  listeners or per-cache size and expiry settings. Searches become database queries, listeners
  become explicit evictions or application events, and limits move to provider configuration
  generated from one list of regions.
* Bad, because Ehcache 3 has no JGroups replication and clusters only through a Terracotta
  server, so clustered adopters move to Infinispan and rewrite their cache configuration.
* Bad, because Ehcache 3.12 needs Java 17, so a single-node portal on Java 11 uses Caffeine until
  the runtime JDK rises.

### Confirmation

Code review checks that new code uses the Spring approach from the table. When the caching change
is done, a search of uPortal's sources for `net.sf.ehcache` imports finds none. Tests set Hibernate's
`missing_cache_strategy=fail` so an unconfigured cache region fails the build, and a two-node
test checks that an edit on one node is visible on the other. The caching change, the two-node
test and the other rows of the table are planned and not yet done.

## Pros and Cons of the Options

### Prefer the Spring approach wherever one exists, and cache through Spring's Cache API

* Good, because Spring keeps these features working across its versions, and uPortal moves
  with Spring anyway.
* Good, because Spring's Cache API works with every provider listed above.
* Bad, because a few Spring options, such as `spring-security-cas`, still need evaluation.

### Choose a replacement library case by case, and cache through the JCache API (JSR-107)

The first caching proposal used JCache directly.

* Good, because each choice can pick the best library for its job.
* Good, because JCache is a standard with several providers.
* Bad, because uPortal would call a second caching API next to Spring's, and Spring 4.3 already
  covers JCache through `JCacheCacheManager`.
* Bad, because every replacement needs its own discussion.

### Keep each existing library until it blocks an upgrade

* Good, because it changes the least now.
* Bad, because Ehcache 2, Joda-Time formatting and Web Flow's portlet integration block the
  Spring 6 step anyway, and jasypt and json-lib are unmaintained.

## More Information

* Infinispan publishes `infinispan-hibernate-cache-v66` for Hibernate 6.6 and 7.1 and later.
* A Valkey or Redis provider needs a test of Redisson with Hibernate 7.4, of Spring Session 4 with
  Lettuce against Valkey, and of latency for the 184 cache regions before it is offered.
* [Spring Framework cache abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html),
  [Ehcache 3 clustered caches](https://github.com/ehcache/ehcache3/blob/master/docs/src/docs/asciidoc/user/clustered-cache.adoc)
* Prototype references: MOD-D22, MOD-Q10, MOD-Q11, MOD-I4, MOD-I39.
