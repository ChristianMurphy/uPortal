# Jakarta readiness of uPortal's dependencies (P5 to P7)

Survey date: 2026-11-10. Read-only. Nothing was built, committed or changed.

Checked against: uPortal `6a9697af39` and uPortal-start `df45e44523` on `ccr-43ba0785-hzlrj5`
(Spring 5.3.39, Spring Security 5.8.16, Hibernate 5.6.15, Tomcat 9.0.122), the deployed portal in
`/home/user/wt/start-h56/.gradle/tomcat` for what each WAR really contains, ADRs 0002, 0004, 0006,
0011 and 0012, `LOG.md` (MOD-D6, MOD-D9, MOD-D56), `notes/p4-3-plan.md`, `notes/pluto-in-tree.md`
and `research/spike-jakarta/README.md`.

How to read the sources: "tags" means `git ls-remote --tags` of the named public GitHub
repository on 2026-11-10. "clone" means a depth-1 clone of its default branch, read only.
"jar scan" means a count of class files in a deployed jar that name a `javax` package
(`scratchpad/jr/bin/jarscan.sh`). File counts are `grep` counts of import lines in
`/home/user/uPortal`.

**Not confirmed, and why.** Maven Central answered HTTP 429 after 8 metadata requests, again
after a pause, and a third time on a single request. I stopped there. Versions below therefore
come from GitHub tags and build files, except the eight artifacts Central did answer for (marked
"Central"). Anything I could not confirm is marked "unconfirmed" where it appears.

## 1. Summary: what changes the plan

1. **Spring Framework 7.1 arrives this month and moves two baselines.** 7.1.0-RC2 was published
   on 2026-11-05, and the release notes preview says 7.1.0 is "scheduled for November 2026". It
   raises the minimum to Jackson 3.1 and to Hibernate 7.4 (7.1 and 7.2 still run "for the time
   being"). Spring 7.0 planned to switch off Jackson 2 auto-detection in 7.1 and remove Jackson 2
   in 7.2. ADR 0002 targets "Spring 7.0" and puts Jackson 3 in a later step (P7-2). If 6.0 ships
   on 7.1, Jackson 3 has to be part of P7-1, or P7-1 has to register the Jackson 2 converters by
   hand. Hibernate is at 7.4.15.Final now (ADR 0002 says 7.4.12), and 8.0 is at Beta4.
2. **No bundled portlet has a Jakarta branch, and every upstream repository pins itself to
   `javax` on purpose.** The Renovate rules in the portlet repositories and in resource-server
   block Spring 6, Hibernate 6, Servlet 5 and JAXB 3, with the reason written out. So "upgrade
   each portlet upstream" is not on offer for 6.0. Transforming the WARs in uPortal-start
   (ADR 0011) is the only strategy that needs nothing from upstream.
3. **esup-filemanager is no longer a portlet upstream.** Its README says that since April 2026 it
   is a standalone Spring Boot 3.5 application on Java 21 and "cannot be deployed in a portlet
   container". ADR 0011 item 5 lists it as "upgraded or retired". Upgrading is no longer
   possible. The bundled 4.0.0 (Spring 3.1.1) is the last portlet release.
4. **Three libraries inside the uPortal WAR have no Jakarta release and are not in any ADR's
   step list:** resource-server (`resource-server-utils`, `-api`, `-content`), person-directory
   1.8.16 and Cernunnos 1.4.0. The uPortal WAR is migrated in source (P6-1), so nothing
   transforms the jars inside it. Each needs a decision before P6: a Jakarta release upstream, a
   copy in uPortal's tree, or a transformed artifact that uPortal publishes. resource-server is
   the largest: 10 of its 12 `utils` classes use `javax.servlet`, and its page-cache filter
   extends Ehcache 2's `ehcache-web`, which the cache plan removes.
5. **person-directory is less of a blocker than the research report says, and blocked in a
   different place.** Upstream master (3.0.2-SNAPSHOT, last commit 2024-03-29) has no EE imports
   at all, only `javax.sql` and `javax.naming` from the JDK. But it dropped 5 classes uPortal
   uses, one of them the only servlet class (`RequestAttributeSourceFilter`). In 1.8.16 that one
   class is also the only one that uses `javax.servlet`. Copying those 5 classes into uPortal
   removes the `javax.servlet` link whichever version is kept.
6. **Hibernate 6 cannot run on `javax`, but the Jakarta switch can run on Hibernate 5.6.**
   Hibernate 6.0 is `jakarta.persistence` only. `org.hibernate:hibernate-core-jakarta`
   5.6.15.Final exists on Central, and Spring 6.0's release notes name it as the supported way
   to move first. That allows P6 to be split: the namespace switch on Spring 6 with Hibernate
   5.6 Jakarta, then Hibernate 6.6 as its own step. Whether Spring 6.2 (not only 6.0) still runs
   5.6 is unconfirmed and needs a one-day spike.
7. **ZooKeeper: found nothing.** Neither repository mentions it, and neither do JGroups' manual,
   the jgroups-extras organization, Tomcat's clustering docs or the uPortal searches I ran.
   uPortal's JGroups stack finds members through `JDBC_PING` in the portal database. The one
   Apereo product with documented ZooKeeper support is the CAS server. See section 5.

Smaller corrections to the ADRs are in section 7.

## 2. Library table

Size is the number of files in uPortal that import the library (all source sets, in-tree Pluto
and the copied Portlet MVC counted separately where it matters). Risk is my judgement.

### 2.1 Platform and Spring

| Library | Used where | Jakarta version | What to do | Size | Risk | Source |
| --- | --- | --- | --- | --- | --- | --- |
| Servlet, JSP, EL API (`javax.servlet` 3.1/4.0) | whole portal; 107 JSPs, 4 tag files, 5 TLDs | Servlet 6.1, Pages 4.0, EL 6.0 (EE 11) in Tomcat 11.0.29 | Source rename in P6-1. The removed-API scanner already runs in uPortal-start. Wrappers need the new Servlet 6 methods (spike B8, B9). | 438 files outside Pluto and Portlet MVC, 73 in `uPortal-pluto`, 8 in `uPortal-spring-portlet` | Medium | grep; tags `apache/tomcat`; `research/spike-jakarta/README.md` |
| Spring Framework 5.3.39 | everywhere | 6.2 is EE 9 to 10, JDK 17 to 25. 7.x is EE 11 to 12, JDK 17 to 25+. 7.0.10 current, 7.1.0-RC2 | 6.2 at the checkpoint, 7.x at P7. Decide 7.0 or 7.1 (summary 1). Removed APIs still used: `HandlerInterceptorAdapter` 13, `CommonsMultipartResolver` 1, `org.springframework.cache.ehcache` 16 references, `MappingJackson2*` 7, `webjars-locator-core` 1 (7.0 supports only `webjars-locator-lite`). | about 60 hand-written files for `javax.annotation` alone | Medium | [versions](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions), [7.0 notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes), [7.1 notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.1-Release-Notes), Central |
| `javax.annotation` (`@PostConstruct` 33, `@Resource` 28, `@PreDestroy` 3 imports) | Spring beans | `jakarta.annotation` 3.0. Spring 7.0 removed support for the `javax` ones | Rename in P6-1. Spring 5.3 does not read the `jakarta` ones, so it cannot go earlier. The 48 `@Generated` imports are XJC output and change with JAXB 4. | 57 files | Low, but silent if missed: an unrenamed `@PostConstruct` is ignored on Spring 7 | grep; 7.0 notes |
| Spring Security 5.8.16 | filters, XML config | 6.5 (Spring 6.2), 7.1.2 current, 7.2.0-RC1 | Moves with Spring. No SAML or OAuth module of Spring Security is used (0 imports). | config plus about 20 files | Medium | grep; Central |
| Spring Web Flow 2.5.1 | 26 admin flows | 3.0.2 (Spring 6), 4.0.1 (built on Spring 7.0.8, Servlet 6.1) | ADR 0012 replaces the flows before P6. If some remain, servlet-mode 4.0.1 exists. The jar scan shows 24 servlet and 14 EL classes, so 2.5.1 cannot cross the switch. | 26 files | Medium, tied to the portlet rewrite | tags `spring-projects/spring-webflow`; [4.0.1 build](https://github.com/spring-projects/spring-webflow/blob/v4.0.1/build.gradle) |
| Spring LDAP 2.4.4 (`core` and `core-tiger`) | groups, person lookup | 3.3 (Spring 6), 4.1.2 (Spring 7), 4.2.0-RC1 | Moves with Spring. No `javax` EE classes in the jar. Whether `spring-ldap-core-tiger` still exists after 2.x is unconfirmed. | 5 files, 2 XML | Low | Central; jar scan |
| Spring Session 2.7.4, Spring Data Redis 2.7.17, Lettuce 6.1.10 | `uPortal-session` only | Session 4.1.1, Data Redis 4.1.2, Lettuce 7.5.2 in Boot 4.1.1's set | Moves with Spring. Sessions are dropped at each major step. | 2 files | Low | Central; [Boot 4.1.1 versions](https://github.com/spring-projects/spring-boot/blob/v4.1.1/platform/spring-boot-dependencies/build.gradle) |
| Spring WS 3.1.8 (`runtimeOnly`) | one bean, `HttpComponentsMessageSender` in `servicesContext.xml` | 5.0.3, 5.1.0-RC1 | Check whether anything still calls it. If yes, 5.0.x with the HttpClient 5 sender. If no, drop it on `javax` now. | 1 XML | Low | Central; grep |
| Thymeleaf (planned by ADR 0012, not in the build yet) | new admin pages | 3.1.6 is current. Modules are `thymeleaf-spring5` and `thymeleaf-spring6`. There is no `spring7` module. Boot 4.1.1 pairs `thymeleaf-spring6` 3.1.5 with Spring 7 | As ADR 0012 says. Templates do not change at the switch. | 0 today | Low | tags and [3.1 pom](https://github.com/thymeleaf/thymeleaf/blob/3.1-master/lib/pom.xml); Boot 4.1.1 versions |
| JSTL (`taglibs-standard` 1.2.5) | 107 JSPs | Jakarta Tags 3.0.2 API, GlassFish implementation 3.0.1 (Boot 4.1.1's set) | Swap in P6-1. Whether the old `java.sun.com/jsp/jstl` URIs still resolve in Tags 3.0 is unconfirmed. Test one JSP first. | 107 JSPs | Low to medium | Boot 4.1.1 versions |
| Tiles, Velocity, Quartz, Hibernate Search, JMS, JAX-RS, `javax.cache`, `javax.validation`, `javax.el` (direct) | not used by uPortal (0 imports each) | n/a | Nothing. Some portlet WARs carry Bean Validation, JCache or FreeMarker inside the WAR. They go through the WAR transform. | 0 | None | grep; WAR listing |

### 2.2 Persistence and cache

| Library | Used where | Jakarta version | What to do | Size | Risk | Source |
| --- | --- | --- | --- | --- | --- | --- |
| Hibernate ORM 5.6.15 (`javax.persistence` 2.2) | all entities | `hibernate-core-jakarta` 5.6.15 (EE 9). 6.x is Jakarta only, Java 11. 7.x is Persistence 3.2, Java 17. 7.4.15 current | See section 3. 6.6 at the checkpoint, 7.4 at P7. | 142 files `javax.persistence`, 102 `org.hibernate` | High (data) | [6.0 guide](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc), [7.0 guide](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc), Central |
| `hibernate-ehcache`, Ehcache 2.6.11, `ehcache-jgroups3replication` 1.7.4, `ehcache-web` 2.0.4 | second-level cache, 184 regions, page cache | none. No Hibernate 6 version | Decided: Caffeine and a uPortal channel (`notes/p4-3-plan.md` S6). All of S6 must be merged before P6. `ehcache-web` comes in through resource-server (next table). | 46 files, 8 XML | High, already planned | `notes/p4-3-plan.md` |
| Caffeine | target | 3.3.1, Java 11 class files, no EE API | S6b. | new | Low | tags `ben-manes/caffeine`; p4-3 plan |
| JGroups 3.6.20 | cache replication, `jgroups.xml` | no EE API (1 optional JAXB class). 5.2.30 is Java 11, 5.5.8 is Java 17 | Not tied to Jakarta. S6c moves to 5.x. | 4 files, 2 XML | Medium (site files) | tags `belaban/JGroups`; jar scan |
| Jadira usertype 6.0.1 | Joda types | none for Hibernate 6 | Already leaving with the java.time work (ADR 0007). Must be gone before Hibernate 6. | 11 files | Low | grep |
| Tomcat JDBC pool 7.0.37 through `portlet-jdbc-util` 1.1.3 (`TomcatDataSourceFactory`) | portal data sources | no EE API | The factory class has no `javax` references. Raise `tomcat-jdbc` to the Tomcat in use at any time. The status of the `uportal-portlet-utils` repository is unconfirmed (the name I tried does not exist). | 2 references | Low | jar scan; grep |
| HikariCP | `uPortal-db-migrate` only | 7.1.0 | Managed by the Boot BOM there. Nothing for the portal. | 3 files | None | tags `brettwooldridge/HikariCP` |
| HSQLDB 2.7.4, JDBC drivers | all | no EE API | Nothing. | n/a | None | jar scan |

### 2.3 Portlet container and Apereo or Jasig libraries

| Library | Used where | Jakarta version | What to do | Size | Risk | Source |
| --- | --- | --- | --- | --- | --- | --- |
| Portlet API 2.1.0 and Pluto 2.1.0-M3 (in tree as `uPortal-pluto`, 304 files) | container, `shared/lib` | none. Pluto 3.1.2 is still `javax.portlet` on `javax.servlet` 3.1. No `jakarta.portlet` release found: `jakartaee/portlet` has a `main` branch and no tags | As ADR 0011: keep `javax.portlet`, compile it against `jakarta.servlet`, rename the rest of Pluto in P6-1. Pluto's own JAXB use (43 files) moves to JAXB 4 in the same step. | 206 files use `javax.portlet` outside Pluto and Portlet MVC, 99 in Pluto, 63 in Portlet MVC | High, but proven by the spike | grep; [Pluto 3.1.2 pom](https://github.com/apache/portals-pluto/blob/pluto-3.1.2/pom.xml); `notes/pluto-in-tree.md` |
| `pluto-util` 2.1.0-M3 | uPortal-start `buildSrc` (rewrites portlet `web.xml`) | none | Build-time only. Keep. It writes a servlet class name in `org.apache.pluto`, which the in-tree Pluto keeps. | 1 plugin | Low | `buildSrc/build.gradle` |
| CAS client 3.6.2 (`org.jasig.cas.client`) | 3 Java files, 6 XML or properties files, 1 filter in `web.xml` | 4.0.4: Spring 6.1, Servlet 6.0, Java 17. 4.1.1: Spring 7.0.7, Servlet 6.1, Java 21. Package is now `org.apereo.cas.client` | 4.0.x at the checkpoint, 4.1.x at P7, as planned. Also drop `cas-client-support-distributed-ehcache`: it is declared, its cache region is "not wired" (p4-3 plan, appendix B), and in 4.x it needs Ehcache 3. | 9 files | Low | [4.0.4 pom](https://github.com/apereo/java-cas-client/blob/cas-client-4.0.4/pom.xml), [4.1.1 pom](https://github.com/apereo/java-cas-client/blob/cas-client-4.1.1/pom.xml); clone |
| person-directory 1.8.16 (`api` in `shared/lib`, `impl` in the WAR) | person attributes | 3.0.1 is the last release (Spring 6, Java 17). Master: no EE imports, 54 classes, last commit 2024-03-29 | Copy the 5 classes that 3.x dropped into uPortal (`AdditionalDescriptors`, its DAO and interface, `MediatingAdditionalDescriptors`, `support.web.RequestAttributeSourceFilter`). Then either stay on 1.8.16 or move to 3.0.1. Whether 1.8.16 runs on Spring 6 and 7, and whether 3.0.1's method signatures match, is unconfirmed. Spike both in P5. The `api` jar has 4 classes and no `javax`, so `shared/lib` is unaffected. | 51 files, 25 distinct classes | Medium | clone `apereo/person-directory`; jar scan |
| resource-server 1.5.3 (`utils`, `api`, `core`, `content`) | skin aggregation, cache filters, JSP tag, 11 JSP or XSL files | none. Renovate pins it to Spring 5.3 and `javax` ("consumers ... are still on javax.servlet and Java 11"). Active: last commit 2026-10-09 | Needs a decision (open question 3). It is 21 main classes. uPortal uses 8. Either a 2.0 Jakarta release upstream (uPortal-Project owns it), or copy the classes in, as with Pluto. `ConfigurablePageCachingFilter` extends Ehcache 2's `PageCachingFilter`, so it also has to change for S6b. | 12 files, 2 XML | Medium to high: no owner yet | clone `uPortal-Project/resource-server`; jar scan |
| Cernunnos 1.4.0 | import and export: 26 `.crn` scripts, 15 Java files (12 in `uPortal-io-types`) | none. Project is dormant (`com.googlecode.cernunnos`) | The jar has 19 classes that use `javax.servlet`, 2 `javax.portlet`, 1 `javax.mail`. uPortal's imports are core types only (`TaskRequest`, `Phrase`, `Reagent`). Whether Cernunnos loads its servlet task classes at start-up is unconfirmed. Test the import and export specs on the checkpoint first. If it fails, publish a transformed jar (Eclipse Transformer) or replace the 26 scripts. | 15 files, 26 scripts | Medium | grep; jar scan |
| dom4j 1.6.1 | XML handling | no EE need. 2.2.0 is current (`org.dom4j:dom4j`) | Not a Jakarta blocker: the 6 JAXB classes are optional. A move to 2.x can happen any time on `javax`. Cernunnos is compiled against 1.6.1, so test them together. | 40 files | Low | tags `dom4j/dom4j`; jar scan |
| `portlet-ws-util` 1.1.3 | declared in one build file | none | 0 imports found for its classes. Remove the dependency if nothing loads it by name. | 0 | Low | grep |
| json-lib-ext-spring 1.0.2 | 2 Java files, 1 XML | none, abandoned | Replace with a Jackson view on `javax` now (report step P5-2). | 3 files | Low | grep; jar scan |
| springfox 2.9.2 | 1 Java file, 1 XML | none, dead. springdoc 3.2.0 is the Spring 7 line | Remove on `javax` now. Add springdoc after P7 if wanted. | 2 files | Low | tags `springdoc/springdoc-openapi` |
| jasypt 1.9.3 | 4 Java files | none needed | uPortal uses only the encryptor and digester classes. The 4 servlet classes in the jar are not used. Not a Jakarta blocker. Replacing it stays a P5 option. | 4 files | Low | grep; jar scan |
| `net.oauth.core` 20100527 | 1 file (OAuth 1 signing) | no EE API | Nothing for Jakarta. | 1 file | Low | jar scan |
| Grouper client 2.6.19, Tika 3.3.2, AWS SDK, XStream 1.4.21, Guava, AspectJ | various | no EE API in use | Nothing for Jakarta. | n/a | None | jar scan |
| ldaptive 1.2.4, OkHttp 3.12, Retrofit 2.6 | transitive from person-directory 1.8.16 | n/a | 0 imports in uPortal. They leave if person-directory moves to 3.x. | 0 | Low | grep; jar scan |

### 2.4 Other libraries

| Library | Used where | Jakarta version | What to do | Size | Risk | Source |
| --- | --- | --- | --- | --- | --- | --- |
| JAXB (`jaxb-api` 2.3.1, `jaxb-impl` 2.3.3, XJC through Ant, jaxb2-basics 1.11.1) | import and export, `uPortal-io-jaxb` | `jakarta.xml.bind` 4.0.5, GlassFish JAXB 4.0.9 (Boot 4.1.1's set) | P6-1. XJC 4 regenerates the classes. jaxb2-basics 1.11.1 is `javax` only. Its successor's coordinates and version are unconfirmed (Central 429). Spring 7.1 no longer auto-detects the JAXB message converter on the server side. | 7 hand-written files, 43 in Pluto, plus generated code | Medium | Boot 4.1.1 versions; 7.1 notes |
| JavaMail 1.4.7 | password reset mail | Jakarta Mail 2.1.5 with Angus Mail 2.0.5 | P6-1 rename. | 3 files | Low | Boot 4.1.1 versions |
| Jackson 2.22.2 | REST, JSON columns | no EE API. Jackson 3.3.0 is current | Fine on Jakarta. The pressure comes from Spring 7.1 (summary 1). | 62 files | Medium (stored JSON) | tags `FasterXML/jackson-databind`; 7.0 and 7.1 notes |
| Groovy 3.0.25 (runtime), 4.0 (tests) | PAGS scripts, Cernunnos scripts | no EE API. 5.1.3 is current, Boot 4.1.1 manages 5.0.8 | Not tied to Jakarta. Can move on `javax`. Spring 7 deprecates the `<lang:*>` XML namespace. | 5 files | Low | tags `apache/groovy`; 7.0 notes |
| commons-fileupload 1.2.1 (transitive, not declared) | `CommonsMultipartResolver` bean in `mvcContext.xml` | 2.0.0-M5 is still a milestone | Switch to `StandardServletMultipartResolver` on `javax` now (Spring 6 removed the Commons resolver), then exclude the jar. | 1 XML, `web.xml`, `NoMultipartDispatcherServlet` | Low | tags `apache/commons-fileupload`; grep |
| HttpComponents 4.5.14 | 10 files, 1 Spring request factory, the Spring WS sender | HttpClient 5.6.4 (Boot 4.1.1's set). No EE API | Direct uses can move to 5 on `javax`. The Spring factory changes at P6, because Spring 5.3's factory is HttpClient 4 only. | 10 files | Low | grep; Boot 4.1.1 versions |
| Lucene 8.11.4 | `uPortal-index` | no EE API. 10.6.0 is current and needs Java 21 | After the JDK 21 runtime step, on `javax`. No Hibernate Search. | 3 files | Low | tags `apache/lucene` |
| Soffit: jjwt 0.11.5 | `uPortal-soffit`, security | no EE API. 0.13.0 is current | Nothing for Jakarta. `jjwt-jackson` uses Jackson 2. Whether jjwt has a Jackson 3 module is unconfirmed, so keep Jackson 2 on the class path beside Jackson 3. The soffit modules have 5 files that use `javax.servlet`. Soffit applications that embed `uPortal-soffit-renderer` on Spring Boot 2 cannot take the 6.0 jar (open question 6). | 10 files | Medium for adopters | [jjwt changelog](https://github.com/jwtk/jjwt/blob/0.13.0/CHANGELOG.md); grep |
| SLF4J, Logback 1.6.3, log4j bridges | logging | no EE classes in the jars deployed | Nothing. | n/a | None | jar scan |
| StAX (`stax`, StaxMate, Woodstox) | XML streaming, 79 files use `javax.xml.stream` | `javax.xml.stream` is part of the JDK and is not renamed | Make sure the rename script excludes it, with `javax.sql`, `javax.naming`, `javax.xml.*` (except `bind`), `javax.crypto` and `javax.portlet`. | 0 to change | Low, if the exclusion list is right | grep |
| Web components (`uPortal-webcomponents`, Vite) | static files in the WAR | n/a | No Java-side change. In uPortal-start the Vue components are WebJars inside the `resource-server` WAR, which is a Spring Boot 2.7 WAR and goes through the transform. | 0 | Low | `overlays/resource-server/build.gradle` |

### 2.5 Build plugins

None of these touches `javax` at run time, and all run on Gradle 9.8.1 today.

| Plugin | Version | Note for P6 and P7 | Source |
| --- | --- | --- | --- |
| `com.github.node-gradle.node` | 7.1.0 (both repositories) | none | `build.gradle` |
| `com.github.spotbugs`, FindSecBugs 1.14.0 | 6.5.12 | Whether FindSecBugs' servlet detectors know `jakarta.servlet` is unconfirmed. The baseline may shift at P6-1. | `build.gradle` |
| `com.diffplug.spotless`, `net.ltgt.errorprone`, `nebula.lint`, `net.researchgate.release`, `info.solidsoft.pitest` | 8.10.4, 5.1.1, 21.3.0, 3.2.0, 1.19.0 | none | `build.gradle` |
| `org.springframework.boot` | 4.1.1, for `uPortal-db-migrate` only | Its BOM is a ready list of Spring 7 compatible versions (used above). | `build.gradle` |
| XJC through Ant (`jaxb-xjc` 2.x) | n/a | Moves to `jaxb-xjc` 4 at P6-1. | `uPortal-io-jaxb/build.gradle` |
| War overlay plugin (source in `buildSrc`), `OverlayLibrariesPlugin`, `RemovedApiScanPlugin`, `GradlePlutoPlugin` | in tree | The transform step (section 4) is a new plugin beside these. The scanner's rules then read `jakarta` names. | uPortal-start `buildSrc` |
| Tomcat install | `org.apache.tomcat:tomcat:${tomcatVersion}@tar.gz` from Central | Change `tomcatVersion` (10.1.61, then 11.0.29) and the `server.xml` template (spike B12, B14). | `gradle/tasks/tomcat.gradle` |
| `com.bmuschko:gradle-docker-plugin` | 10.0.0 | Base images are `eclipse-temurin:11-jdk`. They move with the runtime JDK. | `gradle/tasks/docker.gradle`, `docker/` |

### 2.6 Portlet WARs in uPortal-start

From `overlays/*/build.gradle` and `gradle.properties.example`. The library versions are what
the deployed WARs contain after uPortal-start's overlay rules (JSTL is already 1.2.5 in every
WAR). "Jakarta branch" was checked by listing the branches of each repository.

| Overlay | Artifact and version | Spring | Servlet baseline | Persistence | Upstream | Jakarta branch |
| --- | --- | --- | --- | --- | --- | --- |
| Announcements | `org.jasig.portlet:Announcements` 2.5.3 | 4.3.30, Portlet MVC | compiles against 3.1 (per Renovate note) | Hibernate 5.6.15, Ehcache 2.10.6 | active, 2026-10-23 | none. Pinned to Spring 4.3 |
| BookmarksPortlet | `org.jasig.portlet:BookmarksPortlet` 1.3.1 | 4.3.30, Portlet MVC | `web.xml` 2.4 | Hibernate 3.6.10 | active, 2026-09-20 | none. Pinned |
| CalendarPortlet | `org.jasig.portlet:CalendarPortlet` 2.7.4 | 4.3.30, Portlet MVC, Spring Security 4.0.4, Spring WS 2.4.7 | `web.xml` 3.0, compiles against 4.0.1 | Hibernate 3.6.10 | active, 2026-10-09 | none. Pinned |
| FeedbackPortlet | `org.jasig.portlet:FeedbackPortlet` 1.3.2 | 3.2.18, Portlet MVC | 2.5 | Hibernate 3.2.7 | active, 2026-09-20 | none. Pinned |
| NewsReaderPortlet | `org.jasig.portlet:NewsReaderPortlet` 5.1.5 | 4.3.30, Portlet MVC | `web.xml` 3.0, compiles against 3.1.0 | Hibernate 3.6.10 (Atlassian build) | active, 2026-09-20 | none. Pinned |
| NotificationPortlet | `notification-portlet-webapp` 4.8.4 | Spring Boot 1.5.22, Spring 4.3.25, Security 4.2, `spring-security-oauth2` 2.5.2 | `web.xml` 2.5 | Hibernate 5.6.15, Ehcache 2 and 3 | active, 2026-11-06 | none. Pinned, and to Gradle 8 |
| SimpleContentPortlet | `SimpleContentPortlet` 3.4.4 | 5.3.39 with Liferay PortletMVC4Spring 5.2.0 | `web.xml` 2.4 | Hibernate 5.6.15 | active, 2026-10-09 | none. Pinned to Spring 5.3 |
| WebProxyPortlet | `WebProxyPortlet` 2.4.1 | 4.3.30, Portlet MVC, Web Flow 2.4.8, Security 4.2.20; bundles Pluto 2.1.0-M3 jars, person-directory 1.8.16, JGroups 3.6.20 | `web.xml` 2.5 | Hibernate 4.2.21 | active, 2026-09-20 | none. Pinned |
| basiclti-portlet | `basiclti-portlet` 1.5.1 | none (plain portlet) | `web.xml` 2.4 | none | active, 2026-10-23 | none |
| cas-proxy-test-portlet | `cas-proxy-test-portlet` 1.0.2 | 4.2.5, CAS client 3.1.10, log4j 1.2.17 | `web.xml` 3.0 | none | `Jasig/cas-proxy-test-portlet`, activity unconfirmed | none |
| esup-filemanager | `esup-filemanager` 4.0.0 | 3.1.1, Portlet MVC | `web.xml` 3.0 | none | upstream is now a Spring Boot 3.5 application and no longer a portlet | n/a: upstream left the portlet API |
| pluto-testsuite | `pluto-testsuite` 2.1.0-M3 | none | n/a | none | Apache Attic | none. Test fixture |
| ResourceServingWebapp | `resource-server-webapp` 1.0.48 | 3.1.4 | n/a | Ehcache 2.6.5 | old line of resource-server | none |
| resource-server | `resource-server-webapp` 1.5.3 plus WebJars | Spring Boot 2.7.18, Spring 5.3.31 | n/a | Ehcache 2.6.11 | active, 2026-10-09 | none. Pinned |
| uPortal | `uPortal-webapp` | 5.3.39 | `web.xml` 3.0 | Hibernate 5.6.15 | this work | source migration in P6-1 |

Sources: `/home/user/uPortal-start/overlays`, the WAR listing in
`/home/user/wt/start-h56/.gradle/tomcat/webapps`, clones of each repository's default branch
(`pom.xml`, `gradle.properties`, `renovate.json`), and the two local clones
`/home/user/newsreaderportlet` and `/home/user/calendarportlet`.

## 3. Order of the upgrades

### What must move together

* **The namespace, per class loader.** uPortal's WAR, Tomcat's `shared/lib` (portlet API, the
  three Pluto API jars, `ccpp`, `person-directory-api`) and every portlet WAR dispatch into each
  other, so they switch in one deployment (ADR 0002). Inside a portlet WAR, `javax.persistence`,
  `javax.mail` and `javax.xml.bind` can stay, because the WAR carries its own copies.
* **Spring 6 needs Jakarta EE 9 or later and Java 17.** Spring 5.3 is `javax` only. There is no
  Spring version that reads both servlet namespaces
  ([versions](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions)).
* **Spring Security, Session, LDAP, Web Flow, WS and the CAS client follow Spring's major.** CAS
  client 4.0 needs Java 17, 4.1 needs Java 21.
* **Hibernate.** 6.0 and later are `jakarta.persistence` only
  ([6.0 guide](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)), so
  Hibernate 6 cannot come before the switch. Hibernate 5.6 exists in both namespaces
  (`hibernate-core` and `hibernate-core-jakarta`, both 5.6.15.Final). Spring 6.0's notes say to
  use the `-jakarta` artifact or go to 6.1
  ([6.0 notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes)).
  So Hibernate can lag the switch by one step. It cannot lead it.
* **Spring 7 needs Servlet 6.1 (Tomcat 11) and Persistence 3.2 (Hibernate 7.1 or later).** These
  three stay one step, as ADR 0002 says.
* **Ehcache 2 must be gone before Hibernate 6**, because `hibernate-ehcache` ends at 5.6. A
  Jakarta build of `hibernate-ehcache` for the 5.6 Jakarta option is unconfirmed, so the
  optional step P6-0 below also assumes S6 is done.

### What can be done now, on `javax`

Each row stands alone and passes the four required checks on the current stack.

| Step | What | Needs | Size |
| --- | --- | --- | --- |
| P5-a | `StandardServletMultipartResolver`, exclude commons-fileupload | nothing | 1 bean, `web.xml` |
| P5-b | `HandlerInterceptorAdapter` to `HandlerInterceptor` | nothing | 13 uses |
| P5-c | json-lib views to Jackson views; remove springfox | nothing | 5 files |
| P5-d | Direct HttpClient 4 uses to HttpClient 5 | nothing | up to 10 files |
| P5-e | Copy the 5 person-directory classes into uPortal; spike 1.8.16 and 3.0.1 on Spring 6.2 | nothing | 5 classes, 1 spike |
| P5-f | resource-server: decide, then either release 2.0 on Jakarta upstream or copy the 21 classes in. Replace `ConfigurablePageCachingFilter` | owner decision; S6b for the filter | 12 files |
| P5-g | Cernunnos: run import and export against a transformed uPortal WAR on Tomcat 10.1 (the spike's method) and read the log for class loading errors | nothing | 1 spike |
| P5-h | Drop unused dependencies: `cas-client-support-distributed-ehcache`, `portlet-ws-util`, Spring WS if unused | nothing | build files |
| P5-i | dom4j 2.x, Groovy 4 or 5 at run time, `tomcat-jdbc` current | nothing | version bumps |
| P5-j | Runtime JDK 21 (report step P4-2), then Lucene 10 | P4-2 | 3 files |
| P5-k | Cache: S2 to S6 of `notes/p4-3-plan.md`, JGroups 5 included | owner's Q1 is decided | planned |
| P5-l | Replace the built-in portlets and flows, delete `uPortal-spring-portlet` and Web Flow (ADR 0012) | long | 37 portlets, 26 flows |
| P5-m | uPortal-start: the WAR transform step with the narrow profile, per-WAR excludes, and the hybrid portlet API jar, proven on a Tomcat 10.1 branch (report step P5-5) | nothing | 1 plugin |
| P5-n | uPortal-start: retire or freeze esup-filemanager, cas-proxy-test-portlet and ResourceServingWebapp 1.0.48 | owner decision | overlays |

### Proposed steps P6 and P7

| Step | What | Needs | Atomic |
| --- | --- | --- | --- |
| P6-0 (optional split) | Spike: uPortal on Spring 6.2 with `hibernate-core-jakarta` 5.6.15. If it works, P6-1 keeps Hibernate 5.6 and Hibernate 6.6 becomes P6-3. If it does not, P6-1 stays as ADR 0002 describes it. | P5-k done | no |
| P6-1 (uPortal) | Scripted rename of `javax.servlet`, `persistence`, `annotation`, `xml.bind`, `mail` (with the JDK exclusion list). Spring 6.2, Security 6.5, Session 3.5, LDAP 3.3, CAS client 4.0.4, JAXB 4, Jakarta Mail, Jakarta Tags, `web.xml` 6.0. In-tree Pluto on `jakarta.servlet`, portlet API compiled against it. Servlet 6 methods on the wrappers. | all of P5 except P5-i and P5-j | yes, with P6-2 |
| P6-2 (uPortal-start) | Tomcat 10.1.61, transformed overlays, `shared/lib` from the Jakarta build, `server.xml` fixes, scanner rules on `jakarta` names | P5-m | yes, with P6-1 |
| P6-3 | Hibernate 6.6: type system, `SQLServer` and `MariaDB` dialects without sequences (section 6), freeze block additions | P6-1 | yes |
| P7-1 | Spring 7.0 or 7.1, Security 7.1 or 7.2, Session 4.1, LDAP 4.1, CAS client 4.1.x, Hibernate 7.4.x, Tomcat 11.0.x, `webjars-locator-lite`, runtime JDK 25 (CAS client 4.1 needs 21 at least) | P6-3; owner decision on 7.0 or 7.1 | yes |
| P7-2 | Jackson 3. Part of P7-1 if the target is Spring 7.1 | golden files for stored JSON | no on 7.0, yes on 7.1 |
| P7-3 | Hibernate 7 clean-up, 6.0 schema, real sequences on SQL Server if decided (section 6) | P7-1, copy tool | no |

The optional split costs one more checkpoint that is never released. It buys a P6-1 in which a
failure can only come from the namespace or from Spring 6, and never from Hibernate 6. I would
take it only if the spike is clean in a day.

## 4. Portlet strategy

Three strategies, as asked.

**A. Transform the portlet WARs when uPortal-start builds its overlays (ADR 0011).**
Cost: one Gradle plugin in uPortal-start (about 20 lines of Java drive the Tomcat tool with a
narrow profile, per the spike), a per-WAR exclude list, the removed-API scanner (already there),
and the hybrid portlet API jar, which the spike showed is the tool's own output with 4 changed
classes. Adopters run the same step on their own WARs.
Risk: old Spring 3 and 4 code inside the WARs may call removed Servlet methods on paths no test
reaches. The scanner and the per-portlet specs cover that. A corrupt class in `icu4j-2.6.1`
aborts the tool for two WARs unless excluded. The spike passed 116 of 119 specs on Tomcat 11
with this strategy, with the same 3 sandbox failures as on `javax`.
Known tool limits, from the spike and the tool's README: it rewrites package names in classes,
string constants, configuration files, JSPs and TLDs, and it strips jar signatures. It does not
rename `javax.portlet` (wanted here). Reflection on class names built at run time is not seen
by any bytecode tool. Eclipse Transformer 1.0.0 does the same job with rule files and also
lists service loader configuration files (`META-INF/services`) among the file types it
rewrites ([README](https://github.com/eclipse-transformer/transformer/blob/main/README.md)).
Either works. The spike used Tomcat's tool
1.0.13, which is still the latest tag.

**B. Upgrade each portlet upstream to Jakarta.**
Cost: 9 portlet repositories and resource-server, each on Spring 3.2 to 5.3 with Hibernate 3.2
to 5.6, each needing Spring 6 or 7, Hibernate 6 or 7 and a replacement for Spring Portlet MVC
(Spring removed it in 5.0, and PortletMVC4Spring's Jakarta line is a 6.0.0-M1 tag from one
maintainer). Every repository's Renovate configuration blocks these upgrades today.
Risk: high and long. It would also force every adopter-built portlet to do the same. It cannot
finish before 6.0.

**C. Keep a `javax` portlet bridge** (portlets stay `javax`, an adapter or a second container
sits between them and the portal).
Cost: adapters for every servlet type Pluto passes across contexts (request, response, session,
context, cookies), or a Jetty 12.1 deployment with mixed ee8 and ee11 contexts, or a second
Tomcat 9.
Risk: ADR 0004 already rejects each form: Pluto needs one container, Jetty's cross-context
dispatch bypassed security handling and had an identity bug until September 2026, and Tomcat 9
support ends on 31 March 2027.

**Recommendation: A, as decided, with three additions.**

1. Treat the transform as the long-term contract for portlets, not as a stopgap until upstream
   moves. Upstream has said in its Renovate rules that it is staying on `javax`.
2. Decide now which overlays 6.0 stops bundling: esup-filemanager (no portlet upstream any
   more), cas-proxy-test-portlet (CAS client 3.1.10, log4j 1.2.17, a test tool) and
   ResourceServingWebapp 1.0.48 (Spring 3.1.4, the old line of the same project as
   `resource-server`). Each one dropped removes a Spring 3 WAR and the `spring3-jsp-el` patch
   that goes with it.
3. Ship the transform as a task adopters can call on their own WARs, and say in
   `UPGRADING-6.0.md` that a portlet built for 6.0 still compiles against `javax.portlet` and
   `javax.servlet`.

## 5. ZooKeeper

**What I looked at and what I found.**

* uPortal and uPortal-start: a search for `zookeeper`, `ZOOKEEPER_PING`, `kafka` and `solr` over
  code, configuration, build files and docs finds nothing. The only hits are a Kafka recipe
  name in `research/spring_stack_migration.md` and a binary.
* uPortal's clustering today: `uPortal-webapp/src/main/resources/properties/jgroups.xml` is a
  TCP stack that finds members with `JDBC_PING`, a table in the portal database. Sessions can be
  replicated through `uPortal-session`, which is Spring Session on Redis. Search is an embedded
  Lucene index per node. There is no Solr and no Kafka.
* JGroups: the 5.x manual does not contain the word ZooKeeper, and the `jgroups-extras`
  organization lists discovery add-ons for AWS, Azure, Google, Kubernetes and MongoDB, and none
  for ZooKeeper ([manual](http://www.jgroups.org/manual5/index.html),
  [jgroups-extras](https://github.com/orgs/jgroups-extras/repositories)). A `ZOOKEEPER_PING`
  protocol does not exist in either place.
* Tomcat session clustering: membership is multicast or static
  ([cluster how-to](https://tomcat.apache.org/tomcat-11.0-doc/cluster-howto.html)). No ZooKeeper.
* Spring Session: the reference index does not mention ZooKeeper
  ([reference](https://docs.spring.io/spring-session/reference/index.html)).
* Apereo CAS server 8.0: documents ZooKeeper as a configuration source through Spring Cloud
  ([CAS docs](https://apereo.github.io/cas/8.0.x/configuration/Configuration-Server-Management-SpringCloud-ZooKeeper.html)).
* Web searches for uPortal with ZooKeeper, including the Apereo wiki and the Google Groups
  archives as search targets, returned nothing relevant. I did not read the mailing list
  archives page by page.

**So, plainly:** I found no use of ZooKeeper by uPortal, by uPortal-start, or in any deployment
guide I could reach. The likely meanings of "a ZooKeeper cluster at a site" are the site's CAS
server configuration, or Solr or Kafka run for other systems.

**What it means for the invalidation channel.** Nothing has to change. The planned channel
(p4-3 plan, S6a) runs on the existing JGroups stack, and that stack needs the portal database
for discovery and nothing else. If a site does want ZooKeeper to coordinate uPortal nodes, there
is no ready JGroups protocol for it, so it would mean either a custom discovery protocol or a
second transport for the channel. A cheap way to keep that door open: put the channel's
transport behind a small interface in S6a (send an ordered message to all, receive, be told
about a merge), with JGroups as the only implementation in 6.0. The ordering and merge
guarantees G2 and G7 of the plan then become the contract any other transport has to meet.

## 6. SQL Server ids on Hibernate 6 and 7

Today on SQL Server, `PortalDialectResolver` picks `SQLServer2005Dialect`, which has no
sequences, so each of the 50 `@SequenceGenerator` entities gets a one-row table named like a
sequence (`UP_MUTEX_SEQ` and so on), as in 5.17 (ADR 0006, `notes/mssql-id-lock.md`).

What Hibernate 6 and 7 do, from the 7.4 sources:

* `SQLServer2005Dialect` is gone from `hibernate-core` (404 on the 6.6 branch).
  `SQLServerDialect` has a minimum version of 12 (SQL Server 2014) and returns a real
  `SequenceSupport`
  ([SQLServerDialect](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/dialect/SQLServerDialect.java)).
* `SequenceStyleGenerator` uses a real sequence when the dialect supports sequences and the
  generator's `force_table_use` parameter is not set. Otherwise it builds a `TableStructure`
  ([SequenceStyleGenerator](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/id/enhanced/SequenceStyleGenerator.java),
  lines 205 to 230 and 511 to 513).
* So by default Hibernate 6 and 7 would ask SQL Server for `next value for UP_MUTEX_SEQ`, and
  on an upgraded database that name is a table. Inserts fail. MariaDB is the same case
  (`MariaDBDialect` returns `MariaDBSequenceSupport`). MySQL has no sequences and keeps tables.

Do the one-row tables survive the upgrade anyway? Yes, but only if uPortal says so. Two ways:

* A uPortal dialect that extends `SQLServerDialect` and returns `NoSequenceSupport.INSTANCE`
  (the class is still in 7.4). This is the report's "no-sequence dialects" item, and the least
  change. `TableStructure` is still in 7.4.
* `force_table_use` on each generator. `@SequenceGenerator` cannot carry it, so it means 50
  entity changes. Not recommended.

One thing to carry over: `TableStructure` in 7.4 still reads the row through
`dialect.appendLockHint(new LockOptions(PESSIMISTIC_WRITE), …)`, and 7.4's `SQLServerDialect`
still answers `updlock,holdlock`. That is the hint behind the parallel-insert deadlock of
`notes/mssql-id-lock.md`. The fix there has to be ported to the new method signature in the
uPortal dialect at P6-3 and again at P7-1.

For real sequences in 6.0: the generator names already match, so the switch is "drop the
no-sequence override, and have the copy tool create a sequence where the table was". Each
sequence has to start above the table's current value and increment by the entity's
`allocationSize`, because Hibernate reads a sequence value as the top of a pooled block (6.0
guide, "Defaults for implicit sequence generators"). Hibernate 7.0 also rejects mixed
generator kinds (`GenerationType.SEQUENCE` with anything but `@SequenceGenerator`), which
uPortal's entities do not do: they use `@GeneratedValue(generator = …)` with a matching
`@SequenceGenerator`, and one `@TableGenerator`. How Hibernate 7 reacts to an increment that
differs from `allocationSize` is unconfirmed. The prototype should check it.

## 7. Corrections and changes since the ADRs

* **ADR 0002:** "Hibernate's latest stable release ... was 7.4.12.Final. Hibernate 8.0 was in
  beta": now 7.4.15.Final and 8.0.0.Beta4 (Central). Spring 7.1 names Hibernate 7.4 as its
  baseline, so the ADR's choice of 7.4 is now the official pairing, not an inference from Boot.
* **ADR 0002:** P7-1 is written for Spring 7.0. See summary 1.
* **ADR 0011, item 5:** esup-filemanager cannot be "upgraded". See summary 3.
* **ADR 0011:** "Pluto's last release, 3.1.2, uses `javax`" is right
  ([pom](https://github.com/apache/portals-pluto/blob/pluto-3.1.2/pom.xml)). The Renovate notes
  in several portlet repositories say Pluto 3.x is `jakarta.portlet`, which is wrong. Worth a
  line in the upstream pull requests the owner already plans.
* **ADR 0011 and the report's atomic step P6-1** do not list resource-server, person-directory's
  servlet filter or Cernunnos. See summary 4.
* **ADR 0012:** Thymeleaf 3.1.6 is out (the ADR says 3.1.5). No change in substance.
* **Research report, person-directory:** "the one Apereo dependency that can block the Spring 7
  step" overstates it. See summary 5.
* **Research report, cache row:** "Ehcache 3.12 local and Infinispan clustered" is superseded
  by MOD-D56 (Caffeine and a uPortal channel). Tomcat versions in the report (10.1.60, 11.0.26)
  are now 10.1.61 and 11.0.29.

## 8. Open questions for the owner

1. **Spring 7.0 or 7.1 for 6.0?** 7.1 is due this month and makes Jackson 3.1 the baseline. On
   7.1, Jackson 3 joins the atomic step P7-1.
2. **Split P6 with Hibernate 5.6 Jakarta?** Worth a one-day spike (P6-0), or keep the single
   step of ADR 0002?
3. **resource-server:** a 2.0 Jakarta release upstream (and who makes it), or copy the 21
   classes into uPortal as was done for Pluto? Either way the page-cache filter needs a
   replacement for S6b.
4. **person-directory:** copy the 5 classes in and stay on 1.8.16, or move to 3.0.1? Upstream
   has had no commit since March 2024. Is an Apereo release realistic?
5. **Which overlays does 6.0 stop bundling?** Candidates: esup-filemanager,
   cas-proxy-test-portlet, ResourceServingWebapp 1.0.48.
6. **Soffit applications:** `uPortal-soffit-renderer` 6.0 will be Jakarta only. Is a `javax`
   build of that one module wanted for Soffit applications still on Spring Boot 2?
7. **ZooKeeper:** which system at those sites uses it (CAS, Solr, Kafka, something else), and do
   they expect uPortal itself to join it? If yes, the channel gets a transport interface in S6a.
8. **SQL Server sequences:** if they come in 6.0, they belong in the copy tool's 6.0 schema step
   (P7-3). Until then both Hibernate steps need the no-sequence dialect. Is MariaDB to get real
   sequences in the same step?
9. **Cernunnos:** if the checkpoint spike shows it loads its servlet classes, is a transformed
   jar published by uPortal acceptable, or should the 26 import and export scripts be rewritten?
10. **Maven Central:** the versions marked unconfirmed (jaxb2-basics successor, a Jakarta
    `hibernate-ehcache`, `spring-ldap-core-tiger`, `jakarta.portlet` on Central) need a second
    look when Central stops answering 429.
