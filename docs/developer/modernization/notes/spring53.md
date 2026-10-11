# P4-1: the Spring 5.3 bloc

Workstream WS6/WS7, plan step P4-1. Branch `ccr-43ba0785-hzlrj5-spring53` in uPortal, made from the
integration branch. The scope is section 4 of `spring53-prep.md`, plus the Pluto driver errors
below. This note says what is done and what comes next, so the work can resume from the branch.

## State

| Item | State |
| --- | --- |
| Step 1: `PortletEntityRegistryImpl` catches `ObjectOptimisticLockingFailureException` (MOD-I175) | **Done**, branch `ccr-43ba0785-hzlrj5-optimistic-lock` (`e5d75f3`), merged into this branch |
| Versions in `gradle.properties` (Spring 5.3.39, Security 5.8.16, Web Flow 2.5.1, Session 2.7.4, LDAP 2.4.4, Spring-WS 3.1.8) | **Done** |
| The `spring53Probe` task, its baseline and CI job removed | **Done** |
| Pluto driver: `PortalStartupListener` needs `spring-context`, which `spring-web` 5.3 no longer brings | **Done** (dependency added) |
| Spring Session 2 + Spring Data Redis 2.7 with Lettuce 6.1 (MOD-D22) | **Done**, `RedisSessionConfigTest` (5 tests, seeded fault: dropping the standalone database setting fails it) |
| One commons-logging provider: `spring-jcl` excluded build-wide, `jcl-over-slf4j` kept | **Done** |
| Spring-WS 2.1.2 (from portlet-ws-util) → 3.1.8 (MOD-I176) | **Done**, direct `runtimeOnly` in uPortal-webapp |
| `checkWarSpringJars` (part of `check`): every `spring-*` jar in `WEB-INF/lib` at the bloc's version | **Done**. Seeded faults: `spring-aspects` 4.3.30 and the old single `spring` 2.5.6 jar both fail it |
| Unit suite on 5.3, `SpringXmlContextsTest` included | **Done** locally, see below |
| `PortalShell` closes its context on Spring 5 (MOD-I292) | **Done**, `PortalShellTest` (fails with the old check) |
| uPortal-start: removed-API allowlist entry for Spring Session 2's `HttpSessionAdapter` | **Done** on uPortal-start branch `ccr-43ba0785-hzlrj5-spring53` |
| Portal from uPortal-start, Playwright suite | **Done** locally: 324 of 326 pass. The 2 Web Proxy specs fetch `http://example.com/`, which this container can't resolve |
| HTTP replay | **Done** locally: the Spring differences are fixed (health check, `.html` content type) or registered on uPortal-start's branch. 54 exchanges stay unexplained, all from the Background Preference web component on the integration branch, not from Spring |
| SpotBugs: 41 findings that Spring 5's `@Nullable` annotations expose in unchanged code | **Expected red** on this branch. The fixes are on `ccr-43ba0785-hzlrj5-spring53-nullness` (Spring 4.3, one commit per module), which is merged before P4-1. Merge the integration branch after that; the baselines don't grow |
| UPGRADING-6.0.md, "Spring Framework 5.3" | **Done**: versions, URL suffixes under `/api`, Redis sessions, logging |

## Unit suite (local, JDK 21)

`./gradlew --continue test` on the branch ran 1566 tests with 1 failure before the test fix below,
and that test passes after it.

* `GoogleCustomSearchServiceTest` used `MappingJackson2HttpMessageConverter.DEFAULT_CHARSET`,
  which Spring 5.3 sets to null, and stubbed `ClientHttpResponse.getStatusCode()`, which 5.3's
  `DefaultResponseErrorHandler` no longer calls (`getRawStatusCode()` instead). Test-only.
* `:uPortal-webcomponents:vpTest` fails in this container only: the Playwright version it uses
  looks for Chromium build 1248, and the container has 1194. Not related to Spring.

## Running portal (local)

uPortal-start branch `ccr-43ba0785-hzlrj5-spring53` (from its integration branch `ad95212`), with
uPortal installed to `~/.m2` as 5.17.10-SNAPSHOT, on Tomcat 9.0.122. The portal ran on the Ubuntu
OpenJDK 11 package, the Gradle daemon on JDK 21. Playwright used the container's Chromium 1194
through a local config outside the tracked files.

Found on the running portal and fixed:

* `dataInit` hung after importing: `PortalShell` didn't close its context on Spring 5
  (MOD-I292).
* uPortal-start's removed-API scan failed on Spring Session 2's `HttpSessionAdapter` (MOD-I293).
* `/health-check` answered 404: a `@RequestMapping` without a path matches only an empty path in
  Spring 5.3 (MOD-I294).
* `/api/v4-3/portlet/{fname}.html` answered `text/plain`, because Spring 5.3 no longer takes the type from
  the extension (MOD-I295).

Registered as intended on uPortal-start (`tests/replay/intended-differences.json`):

* `spring53-suffix-groups`, `spring53-suffix-people-v50`: suffixed paths under `/api` answer
  404, and a dot in a v5-0 user name is kept. Suffix matching stays off, because Spring 6 removes
  it. UPGRADING-6.0.md, "URL suffixes under `/api`".
* `spring53-security-headers-on-small-images`: four small `/media` images now get the Spring
  Security headers every other `/media` answer had on 5.17.
* `spring53-cors-vary`: `swagger-ui.html` gains Spring 5's CORS `Vary` values.

## SpotBugs findings on Spring 5.3

`./gradlew spotbugsMain` on this branch reports 41 findings outside the baselines, none in code
the branch changes. Spring 5 annotates its API with `@Nullable` (such as
`JdbcOperations.queryForObject`, `TransactionOperations.execute`, `AnnotationUtils.findAnnotation`,
`ApplicationContext.getBean` through `getApplicationContext()`), so SpotBugs now sees results
that could be null on 4.3 as well. The SpotBugs job fails here until the nullness branch
is merged.

| Module | Location | Pattern |
| --- | --- | --- |
| uPortal-api/uPortal-api-rest | `MarketplaceRESTController:134` | `NP_NONNULL_PARAM_VIOLATION` |
| uPortal-api/uPortal-api-rest | `MarketplaceRESTController:151` | `NP_NONNULL_PARAM_VIOLATION` |
| uPortal-events | `JpaAggregatedTabLookupDao:159` | `RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE` |
| uPortal-events | `BatchTinCanAPIProvider:79` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-events | `CachingPersonAttributeDao:118` | `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` |
| uPortal-health | `DatabaseHealthChecker:63` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-layout/uPortal-layout-impl | `RDBMDistributedLayoutStore:351` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-layout/uPortal-layout-impl | `RDBMUserLayoutStore:1495` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-layout/uPortal-layout-impl | `RDBMUserLayoutStore:598` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-layout/uPortal-layout-impl | `RDBMUserLayoutStore:212` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-persondir | `RDBMUserIdentityStore:433` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-persondir | `RDBMUserIdentityStore:157` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-persondir | `RDBMUserIdentityStore:163` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-pluto/uPortal-pluto-portal-driver | `PortalStartupListener:135` | `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` |
| uPortal-portlets | `GoogleCustomSearchService:120` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-portlets | `GsaSearchService:80` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-portlets | `SqlQueryPortletController:129` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-rdbm | `JacksonColumnMapper:71` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-rdbm | `HibernateStyleCounterStore:213` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-rendering | `PortletDefinitionPreferencesImpl:99` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-rendering | `PortletEntityPreferencesAPIImpl:138` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-rendering | `PortletEntityPreferencesImpl:136` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-rendering | `JpaPortletExecutionInterceptor:66` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-rendering | `JpaPortletExecutionInterceptor:48` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-session | `SpringSessionRedisEnabledCondition:35` | `NP_NONNULL_PARAM_VIOLATION` |
| uPortal-spring-portlet | `HandlerMethodInvoker:401` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-spring-portlet | `HandlerMethodInvoker:162` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-spring-portlet | `HandlerMethodInvoker:590` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-spring-portlet | `AbstractHandlerMapping:217` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-spring-portlet | `AbstractMapBasedHandlerMapping:150` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-spring-portlet | `AbstractMapBasedHandlerMapping:151` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-spring-portlet | `DefaultAnnotationHandlerMapping:110` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-spring | `AspectJAroundAdviceFactory:62` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-spring | `DialectAwareTransactionInterceptor:62` | `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` |
| uPortal-spring | `ZeroLeggedOAuthInterceptor:86` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-tools | `DataSourceSchemaExport:107` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-utils/uPortal-utils-core | `MediatingFactoryBean:79` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-utils/uPortal-utils-core | `MediatingFactoryBean:81` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-utils/uPortal-utils-core | `JdbcUtils:65` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-utils/uPortal-utils-core | `CacheKey:59` | `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` |
| uPortal-web | `PortletDelegationView:77` | `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` |

## Commons-logging

`spring-jcl` and `jcl-over-slf4j` both define `org.apache.commons.logging`. The build keeps
`jcl-over-slf4j`: it is the full commons-logging 1.2 API, four modules already declare it, and
`uPortal-db-migrate` does the same with Spring 7. `spring-jcl` is excluded next to the existing
`commons-logging` exclusion in the root `build.gradle`. The WAR holds `jcl-over-slf4j` only.

## Proposed LOG rows (block MOD-I290..MOD-I299)

* **MOD-I175 (update):** fixed by `e5d75f3`. The two `PortletEntityRegistryImplTest` tests ignored
  as "hopelessly arcane issues troubleshooting StaleObjectStateException" were this bug, and run
  again.
* **MOD-I176 (update):** fixed on this branch, Spring-WS 3.1.8 declared in uPortal-webapp.
* **MOD-I290:** `PortalStartupListener` in the copied Pluto driver needs `spring-context`, which
  `spring-web` 4.3 brought along and 5.3 doesn't. Fixed with a direct dependency.
* **MOD-I291:** the default Redis timeout goes from 2 s (Jedis) to 60 s (Lettuce's command
  timeout). The `org.apereo.portal.session.redis.timeout` setting keeps its name. Documented in
  UPGRADING-6.0.md.

* **MOD-I292:** `PortalShell` closed its context only when it was a `DisposableBean`, which Spring 5's
  application contexts are not. Ehcache's JGroups threads then kept the JVM alive, so
  uPortal-start's `dataInit` imported everything and hung. Fixed by closing it as a
  `ConfigurableApplicationContext`.
* **MOD-I293:** uPortal-start's removed-API allowlist said Spring Session 2 drops the
  `getSessionContext()` override; 2.7's `HttpSessionAdapter` still has it. The entry now names
  that class. Spring Session 3 (P6) drops it.

* **MOD-I294:** `HealthCheckController` had a `@RequestMapping` without a path. Spring 5.3 matches
  that only against an empty path, so `/health-check` answered 404. Fixed by mapping
  `/health-check`.
* **MOD-I295:** `/api/v4-3/portlet/{fname}.html` became `text/plain` on Spring 5.3. Fixed with
  `produces = text/html`.
* **MOD-I296:** suffix pattern matching is off from Spring 5.3. `/api/groups.json` and
  `/api/v5-0/people/*.json` answer 404 and are documented, not restored. A dot in a v5-0 user
  name now reaches the controller.

## Environment

Builds run with a Gradle init script outside the repositories that points Maven Central at
Google's mirror (`https://maven-central.storage-download.googleapis.com/maven2`), which the owner
allowed for this session. Without it, Central answered 429.
