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
| Portal from uPortal-start, Playwright suite, HTTP replay | **Next** |
| UPGRADING-6.0.md, "Spring Framework 5.3" | **Half done**: versions, Redis sessions, logging. Behaviour seen on the running portal still to add |

## Unit suite (local, JDK 21)

`./gradlew --continue test` on the branch ran 1566 tests with 1 failure before the test fix below,
and that test passes after it.

* `GoogleCustomSearchServiceTest` used `MappingJackson2HttpMessageConverter.DEFAULT_CHARSET`,
  which Spring 5.3 sets to null, and stubbed `ClientHttpResponse.getStatusCode()`, which 5.3's
  `DefaultResponseErrorHandler` no longer calls (`getRawStatusCode()` instead). Test-only.
* `:uPortal-webcomponents:vpTest` fails in this container only: the Playwright version it uses
  looks for Chromium build 1248, and the container has 1194. Not related to Spring.

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

## Environment

Builds run with a Gradle init script outside the repositories that points Maven Central at
Google's mirror (`https://maven-central.storage-download.googleapis.com/maven2`), which the owner
allowed for this session. Without it, Central answered 429.
