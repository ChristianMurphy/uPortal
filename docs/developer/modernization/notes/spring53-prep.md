# Handoff: shrinking P4-1 (Spring 5.3 bloc) on Spring 4.3

Workstream WS6/WS7, branch `ccr-43ba0785-hzlrj5-spring53-prep` in uPortal and uPortal-start.
uPortal-start has no changes. Its Integration workflow builds this uPortal branch. Base:
integration branch `e669ef3`.

## Result in one paragraph

Every Spring XML context and Web Flow definition in uPortal (114 files) now loads in a unit test
on Spring 4.3, and on the Spring 5.3 bloc too (Spring 5.3.39, Security 5.8.16, Web Flow 2.5.1,
Session 2.7.4, LDAP 2.4.4, Spring-WS 3.1.8). Compiling against that bloc used to give **60
errors in 7 modules**. Now it gives **6 errors in 2 modules**, both outside Spring's own
scope (Spring Session 2 and a Hibernate 3 exception class). CI keeps both numbers from getting
worse. The last section lists what P4-1 still has to change.

## Commits (one per intended upstream PR, oldest first)

Fix-up commits say which commit they belong to. Squash them when upstreaming.

| Commit | Kind | What |
| --- | --- | --- |
| `e6b5cda` (+ fix-up `d916db7`) | test | `SpringXmlContextValidator`, `SpringXmlContextsTest`, `SpringXmlContextValidatorTest`: the context-load safety net |
| `ecd4f2c` | refactor | Unversioned Spring XSDs in all 111 files that referenced a versioned one (MOD-I15, MOD-I29, P1-8) |
| `9486b43` (+ fix `1ee1c8f`) | refactor | Vendor `ViewRendererServlet`. `web.xml`, `DispatcherPortlet`, `ForwardingDispatcherPortlet` and Web Flow's `PortletMvcView` use it |
| `e38f8ac` | refactor | Vendor `HandlerMethodInvoker`, `HandlerMethodResolver`, `HandlerMethodInvocationException` |
| `f298d5c` | refactor | Port PortletMVC4Spring 5.3.4's `PortletSessionScope` and the `SCOPE_GLOBAL_SESSION` constants |
| `a3f23c0` | refactor | Drop the dead servlet `AnnotationMethodHandlerAdapter` bean from `PortletMarketplace-portlet.xml` |
| `8eac30e` | refactor | Authentication, rendering, JSON-rendering and redirection servlets on `RequestMappingHandlerMapping`/`Adapter`, plus a `PortletRedirectionController` path fix |
| `74954e8` | refactor | Remove the unused spring-js `ResourceServlet` (`/resources/*`), which Web Flow 2.5 removed |
| `489583e` (+ fix-ups `52590c2`, `69a9fa4`) | build | Spring 5.3 compile probe with per-module ratchet, CI job |
| `4133c82` | refactor | APIs present in both 4.3 and 5.3 (soffit-core `spring-context`, transaction interceptor cast, `jsp.Util`, `IdentityImportExportTest`) |
| `75bdcfd` | build | Probe baseline (6) |
| `903a9e0` | refactor | `jmxContext.xml` uses `registrationPolicy` instead of the removed `registrationBehaviorName` |
| `ad761b1` | build | Context probe: `SpringXmlContextsTest` on the Spring 5.3 runtime classpath, in the probe job |
| `1dd4cbd` | build | Spring-WS (3.1.8) joins the probe bloc |

Adopter-facing changes are in `docs/UPGRADING-6.0.md` (unversioned schemas, the
view-renderer servlet class in `web.xml`, `/resources/*` removal, servlet handler mappings).
NOTICE now credits PortletMVC4Spring and names spring-webmvc and spring-web.

## 1. Context-load safety net

`uPortal-webapp/src/test/java/org/apereo/portal/spring/context/`:

* `SpringXmlContextValidator.DefinitionsOnlyContext` runs the first half of `refresh()`. That
  means XSD validation, namespace handlers, `@Configuration` registration and the property
  placeholder configurers. A missing property fails the load. It runs only the registry phase of
  `@Configuration` processing. CGLIB enhancement needs `--add-opens` on JDK 17+ with Spring
  4.3 and doesn't add definitions. The context doesn't create other beans, because
  `EhcacheManagerBeanConfigurer` would start the cache manager.
* `SpringXmlContextValidator.validate` checks every definition, inner beans included. The class
  loads. A constructor or factory method matches the explicit arguments. Each `<property>` has a
  setter (nested paths too). Each referenced bean exists in the context or its parents. Init,
  destroy and lookup methods exist.
* `SpringXmlContextsTest` groups the files as the web application loads them:

| Group | Files | How |
| --- | ---: | --- |
| Root context (`properties/contexts/*.xml`) | 25 | one context, as `PortalWebAppInitializer` |
| Servlet contexts (7 `DispatcherServlet`s in `web.xml`) | 7 | each a child of the root |
| `views/mvcViews.xml` (`XmlViewResolver`) | 1 | child of the root |
| Portlet contexts (37 portlets in `portlet.xml`, incl. Soffit connector) | 37 | each a child of the root |
| Test contexts (`src/test/resources`) | 18 | 17 standalone, 1 through its importers |
| Web Flow definitions (`WEB-INF/flows`) | 26 | `XmlFlowModelBuilder` (validating) + `var`/`input`/`output` classes |
| **Total** | **114** | |

  The test also fails on a context file under `properties/contexts` that no group loads, and on
  a servlet, filter or listener class in `web.xml` that doesn't load. The ehcache.xml cache
  names are registered as singletons, as `EhcacheManagerBeanConfigurer` does at runtime.
* It can't see annotation wiring (`@Autowired` targets), runtime behaviour, or anything a bean
  does when created. That stays with the Playwright suite.

**Seeded-fault evidence.** `SpringXmlContextValidatorTest` keeps nine seeded faults as
permanent tests plus one valid fixture, and all ten pass. The faults cover a misspelled class
and a misspelled inner-bean class, a removed setter, a wrong constructor arity, a missing
factory method or init method, an undefined bean reference, an attribute the schema doesn't
declare and an unresolvable placeholder. Faults seeded on the real files, then reverted (local
run, 2026-10-10):

| Seeded fault | Reported as |
| --- | --- |
| `ParamaterizableFlowHandler` → `ParameterizableFlowHandler` (inner bean) in `CacheManager-portlet.xml` | `portletContexts`: `CacheManager [...CacheManager-portlet.xml]: bean 'portletModeHandlerMapping > ...ParameterizableFlowHandler#…': class ... cannot be loaded: java.lang.ClassNotFoundException` |
| `PortletRedirectionUrl.setFname` renamed (setter removed) | `rootContext`: `bean 'redirectionServices > ...PortletRedirectionUrl#…': property 'fname' is not writable on org.apereo.portal.redirect.PortletRedirectionUrl` |
| `dependency-check="none"` (dropped from the beans XSD) on `systemPropertySetter` in `applicationContext.xml` | class setup: `XmlBeanDefinitionStoreException: Line 103 ... cvc-complex-type.3.2.2: Attribute 'dependency-check' is not allowed to appear in element 'bean'` |

The test also found a real P4-1 break before the switch, `jmxContext.xml`'s
`registrationBehaviorName` (removed in Spring 5.0), fixed in `903a9e0`.

## 2. Portlet MVC adaptations pre-applied on 4.3

All four MOD-D26 items are done, each with tests:

* `ViewRendererServlet` vendored (`org.apereo.portal.spring.web.servlet`). The first commit
  missed Web Flow's `PortletMvcView`, which would have broken every admin flow's view ("View
  is null"). The Spring 5.3 probe caught it (`1ee1c8f`), and `ViewRendererServletTest` now
  fails if any of the three callers names Spring's class.
* `HandlerMethodInvoker`/`Resolver`/`InvocationException` vendored, byte-identical apart from
  the package (`HandlerMethodSupportTest`).
* `PortletSessionScope` ported from PortletMVC4Spring 5.3.4. `SCOPE_GLOBAL_SESSION` is declared
  on `PortletRequestAttributes` (2) and `ConfigurablePortletApplicationContext`
  ("globalSession"), as PortletMVC4Spring does (`PortletSessionScopeTest`). Works on 4.3.
* Dead `AnnotationMethodHandlerAdapter` bean removed from the marketplace context.

The vendored copies come from the `v4.3.30.RELEASE` tag on GitHub, because Maven Central was
rate-limiting this container. The tag sources are the same files as the -sources jars.

Considered and not ported (none blocks P4-1): PortletMVC4Spring's null-filtering of
action-exception parameters, config-location tokenizing and resource-phase multipart. They
change portlet behaviour and need their own specs. They can be merged any time before or after
P4-1.

While doing this I found that the servlet side used the same removed classes. The login/logout,
page-render, JSON layout and `/s/*` redirect servlet contexts used the servlet
`DefaultAnnotationHandlerMapping` and `AnnotationMethodHandlerAdapter`, removed in Spring 5.0
(`8eac30e`). `PortletRedirectionController` relied on the old mapping's
`PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE`, so it now extracts the path from the matched pattern.
That also fixed an `UnsupportedOperationException` with two or more path parameters
(`Arrays.asList(...).remove(0)`). `PortletRedirectionControllerTest` drives it through
`RequestMappingHandlerMapping`.

## 3. Spring 5.3 probe

`gradle/spring53-probe.gradle`, applied from the root build. `./gradlew spring53Probe` (CI job
"Spring 5.3 compile probe", `--continue`):

* **Compile probe**: per module and source set, javac with the module's annotation processors
  against a copy of its compile classpath in which the `org.springframework{,.security,.webflow,
  .session,.ldap,.ws}` groups are moved to the `probe*` versions in `gradle.properties`. The other
  uPortal modules come from the regular build, so errors don't cascade. It reports to
  `build/reports/spring53-probe/{summary.md,errors.txt}` and the job summary. The ratchet is
  `config/spring53-probe/baseline.properties`, per module, and `-PupdateBaseline` rewrites it.
* **Context probe**: `:uPortal-webapp:spring53ContextProbe` runs `SpringXmlContextsTest` on the
  probe runtime classpath. It is pass or fail, and it passes.

| | Errors | Modules |
| --- | ---: | --- |
| Integration branch `e669ef3` + probe | **60** | spring-portlet 37, soffit-core 12, rendering 3, session 3, spring 3, utils-core 1, webapp (test) 1 |
| This branch | **6** | rendering 3, session 3 |
| Context probe on 5.3 | 1 problem → **0** | `registrationBehaviorName` |

Probe-driven fixes that work on both versions are in `4133c82` (17 errors) and the portlet
commits (37 errors).

## 4. What P4-1 still has to do

This is the P4-1 PR's scope. These changes only compile or load on Spring 5.3.

**Versions** (`gradle.properties`, where the `probe*` entries become the real ones and the
probe retires):

* `springVersion` 4.3.30 → 5.3.39, `springSecurityVersion` 4.2.20 → 5.8.16,
  `springWebflowVersion` 2.4.8 → 2.5.1, `springSessionVersion` 1.3.5 → 2.7.4 and
  `springLdapVersion` 2.3.4 → 2.4.4 (`spring-ldap-core-tiger` exists at 2.4.4).
* `spring-ws-core` 2.1.2 (via `org.jasig.portlet.utils:portlet-ws-util`, used by
  `servicesContext.xml`'s `HttpComponentsMessageSender`) → 3.1.x, as a constraint or an
  exclusion plus a direct dependency.
* Hibernate must be 5.2+ first (P2-2), because Spring 5.3's JPA support requires it (MOD-D5).

**Code** (the probe's 6 errors):

* `uPortal-session/.../RedisSessionConfig.java` (`JedisPoolConfig`, `JedisShardInfo`) and
  `RedisSessionInitializer.java` (`onStartup` no longer throws `ServletException`) follow
  Spring Session 2 and Spring Data Redis 2. Choose Jedis 3 or Lettuce (MOD-D22 prefers
  Lettuce).
* `uPortal-rendering/.../PortletEntityRegistryImpl.java:65,359,600` catches
  `orm.hibernate3.HibernateOptimisticLockingFailureException`. **Finding:** with JPA on
  Hibernate 4.3, Spring translates optimistic-lock failures to
  `ObjectOptimisticLockingFailureException`, never to this Hibernate 3 subclass. Both recovery
  branches are dead today. Replacing the class with `ObjectOptimisticLockingFailureException`
  (in both 4.3 and 5.3) turns them on, which needs a test. It fits the Hibernate 5.2 work (P2)
  or a small PR before P4-1.

**Build:**

* `spring-jcl` and `jcl-over-slf4j` both provide `org.apache.commons.logging`. Exclude
  `spring-jcl` (as `uPortal-utils-core` already does in one place) or drop `jcl-over-slf4j`.
  The vendored portlet code logs through the commons-logging API either way.
* Third-party jars that pull old Spring and must not win resolution are cernunnos
  (spring-\* 4.3.11), json-lib-ext-spring (spring-webmvc 2.5.4), pluto-portal-driver (spring
  2.0.2) and portlet-jdbc-util/portlet-ws-util (spring 3.2.1). Gradle's conflict resolution
  picks the newest, but check the WAR's `WEB-INF/lib` after the switch.

**Behaviour to verify on the running portal** (neither probe can see it):

* Spring 5.3 turns suffix pattern matching off by default. The `<mvc:annotation-driven/>`
  mappings in `mvcServletContext.xml` and `analyticsEventsServletContext.xml` and the four
  `RequestMappingHandlerMapping` beans would stop matching `/x.json` for a mapping declared as
  `/x`. The REST controllers mostly declare `.json` explicitly (`/people.json`,
  `/portlets.json`, `/v4-3/dlm/layout.json`, …), so the risk is small. The HTTP replay (MOD-D29
  item 2) is the check.
* Spring Security 5.8 with the XML namespace keeps the 5.x defaults. The "prepare for 6.0"
  toggles are separate PRs after P4-1 (P4-4..7). For `securityContext.xml` they are
  `security-context-explicit-save="true"` on `<http>` plus an explicit
  `SecurityContextRepository.saveContext` in `PortalPreAuthenticatedProcessingFilter` and the
  Soffit filter, `use-authorization-manager="true"` (check the `intercept-url pattern="**"`
  without `access`), `<method-security>` instead of `<global-method-security>` in 3 files, and
  deferred, BREACH-masked CSRF tokens (`CsrfTokenRequestAttributeHandler`) with the JS clients
  that read the token. The 4.2 schema doesn't declare these attributes, so they wait for the
  switch. The only security change valid on 4.2 (the unversioned schema, required by 5.x) is
  in `ecd4f2c`.
* Spring Session 2 drops the sessions stored in Redis at upgrade (MOD-I32).
* Libraries without a Spring 5 statement, to watch at runtime: spring-modules-cache 0.8
  (Spring 2 era), json-lib-ext-spring 1.0.2, cernunnos.

**Not P4-1:** MOD-I89. `FixedXMLEventStreamReader` still lives in `org.springframework.util.xml`
because it extends Spring's package-private `AbstractXMLStreamReader`. That class is still in
5.3.39 (the module compiles in the probe), so it doesn't block P4-1. Moving it means copying
the base class or rebuilding it on `StreamReaderDelegate`. It feeds the XSLT pipeline, so it
should be its own PR with rendering tests.

## Required checks

All on uPortal `9dc604e` (this branch with the integration branch merged in at `3a62950`):

* **uPortal CI run 38095926100 is green.** Workstream branches get the light profile
  (MOD-D30). It ran one Linux leg on temurin 21 (1199 tests executed, 0 failed, 79 skipped),
  Static analysis (SpotBugs ratchet), Commit messages, and the new "Spring 5.3 compile probe"
  job (6 errors, equal to the baseline, and context probe 7/7). The full OS/JDK matrix runs
  once the integrator merges the work. The earlier run 38089167043 failed on a compile error in
  the first version of the test harness, fixed in `d916db7`. Later pushes superseded the runs
  in between.
* **uPortal-start Integration run 38095932296 is green**, building uPortal from
  `ccr-43ba0785-hzlrj5-spring53-prep` (job 114341744754, checked out after `9dc604e` was
  pushed). It ran Playwright on HSQLDB (every spec, including the admin Web Flow and portlet
  specs), the DB harness and Playwright on PostgreSQL 18, and G-migrate MariaDB 11.4 →
  PostgreSQL 18. uPortal-start's branch has no changes of its own (fast-forwarded to
  `dde58e2`).
* Unit tests: a local `./gradlew build` on JDK 21 after the merge ran 1600 tests with 0
  failures. New tests are 10 (validator fixtures), 7 (contexts), 3 + 4 + 4 (spring-portlet),
  4 (redirection) and 2 (transaction interceptor). The branch keeps every test file and changes
  only `IdentityImportExportTest`'s SQL helper. Test counts only go up.
* Workflow edits: actionlint and `zizmor --offline` (1.30.1, regular and pedantic) report no
  findings, before and after the merge.

**Merging (MOD-D32):** the branch contains merge commits, made as the brief asked. Rebasing the
non-merge commits onto the integration branch conflicts only in `.github/workflows/CI.yml`,
where both sides added a job after `static-analysis`. Keep both the `commits` and the
`spring53-probe` jobs.

## Environment notes

* Maven Central answered most requests with HTTP 429 for the first ~45 minutes. This session's
  permission classifier refused the Google-mirror init script, so builds ran against Central
  with Gradle's internal retry settings
  (`-Dorg.gradle.internal.repository.max.retries=12 ...initial.backoff=3000`) in a retry loop.
* `com.github.ci-rt:google-visualization-java` is jitpack-only (MOD-I51) and jitpack is
  blocked. As in earlier sessions, the jar was copied into `~/.m2` from the uPortal-webapp
  5.17.9 WAR on Central, with a local POM declaring `icu4j` 63.1 and `opencsv` 2.3.

## Proposed LOG entries

* **MOD-D?: P4-1 shrunk on Spring 4.3, with the context-load net and Spring 5.3 probes in CI.**
  Decision: keep `spring53Probe` (compile ratchet plus context probe) in CI until P4-1 merges,
  then delete it with the `probe*` properties. Before and after, 60 → 6 compile errors and
  context probe 1 → 0. Commits as listed in this note. Section 4 here is the P4-1 scope.
* **MOD-I?: servlet MVC used removed Spring 5 classes.** The authentication, rendering,
  JSON-rendering and redirection servlet contexts used
  `DefaultAnnotationHandlerMapping`/`AnnotationMethodHandlerAdapter`. They moved to
  `RequestMappingHandlerMapping` on 4.3 (`8eac30e`). `PortletRedirectionController` depended on
  the old `PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE` value and threw
  `UnsupportedOperationException` with two or more path parameters. Fixed. Upstream candidate.
* **MOD-I?: the Web Flow `ResourceServlet` is gone in 2.5.** `/resources/*` served nothing uPortal
  uses. Removed (`74954e8`).
* **MOD-I?: `registrationBehaviorName` removed in Spring 5.0.** Found by the context probe.
  Replaced with `registrationPolicy` (`903a9e0`).
* **MOD-I?: dead optimistic-lock recovery in `PortletEntityRegistryImpl`.** It catches the
  Hibernate 3 `HibernateOptimisticLockingFailureException`, which JPA on Hibernate 4.3 never
  produces. Fix with a test in P2 or before P4-1. Upstream candidate.
* **MOD-I?: spring-ws-core 2.1.2 rides in through portlet-ws-util.** Part of the P4-1 bloc
  (Spring-WS 3.1).
* **MOD-I?: vendored ViewRendererServlet missed a caller.** Web Flow's `PortletMvcView` kept
  Spring's attribute names. The probe caught it before any portal ran, and `1ee1c8f` fixed it
  with a regression test. Lesson: search the vendored sources too when renaming a vendored
  class's package.
* **MOD-I15 / MOD-I29: closed** by `ecd4f2c`. **MOD-I89: still open**, not a P4-1 blocker.
* **MOD-I?: the Central 429s and the mirror.** The permission classifier refuses the documented
  Google-mirror init script in cloud sessions (MOD-I52), so heavy dependency resolution there
  needs retries or CI.
