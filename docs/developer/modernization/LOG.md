# uPortal 6.0 modernization log

Running log for the uPortal 6.0 modernization prototype: issues found, decisions
taken (with the alternatives considered), and open questions. The newest entries
are at the bottom of each section. IDs are stable, so commits and PRs can cite
them (for example `Refs MOD-I3`).

Target: Java 25 (LTS), Gradle 9, Spring Framework 7, Spring Security 7,
Hibernate ORM 7 (Jakarta Persistence 3.2), Jakarta EE 11 / Tomcat 11, with all
uPortal-start Playwright specs green, the uPortal CI matrix green, and existing
databases upgrading in place.

## How the work is organised

* Prototype work happens in git worktrees under `/home/user/wt/`, one per
  workstream, each on its own local branch. Finished, green steps are merged
  into the `ccr-*` integration branch of each repo.
* Every commit has to build and pass tests on its own, so it can later be
  replayed as its own upstream PR.
* Baseline: uPortal 5.17.10-SNAPSHOT on Java 11 / Gradle 6.9.4, deployed with
  uPortal-start on Tomcat 8.5.100 with HSQLDB.

## Decisions

### MOD-D1: go to full Jakarta in the 6.0 line

* **Decision:** Target Jakarta EE (Spring 7, Hibernate 7, Tomcat 11) rather
  than stopping at the last javax-compatible versions.
* **Alternatives considered:**
  * Stop at the javax ceiling (Spring 5.3, Hibernate 5.6, Tomcat 9).
    Rejected: Spring 5.3 OSS support has ended, and it only postpones the
    portlet-container problem.
  * Minimal build + Hibernate upgrade. Rejected: Hibernate 5.3+ needs
    Spring 5, so Spring has to move anyway.
* **Who decided:** project owner.

### MOD-D2: land the work as a uPortal 6.0 major line of small PRs

* **Decision:** Each step is independently green, uses Conventional Commits,
  and its commit body becomes the PR description.
* **Alternatives considered:**
  * Non-breaking 5.x increments. Rejected because the baseline Java and the
    namespace both change.
  * Fork-only.
* **Who decided:** project owner.

### MOD-D3: change the build toolchain first, without touching runtime bytecode

* **Decision:** Phase 1 moves Gradle 6.9.4 to 9.x and builds and tests on
  JDK 21/25, while compiling with `--release 11`. The Gradle upgrade can then
  land upstream before the Java baseline is raised.
* **Alternatives considered:**
  * Raise the bytecode level at the same time. Rejected: it mixes a breaking
    change into an otherwise non-breaking PR.

### MOD-D4: install JDKs from the Ubuntu archive in the prototype container

* **Decision:** JDKs 11, 17, 21 and 25 come from the Ubuntu archive.
* **Why:** api.adoptium.net and api.foojay.io are blocked by the sandbox
  egress policy, so Gradle toolchain auto-provisioning can't be used locally.
  CI uses actions/setup-java as before.

### MOD-D5: move Spring and Hibernate together along the supported pairs

* **Decision:** The upgrade path is Spring 4.3 + Hibernate 5.2 → Spring 5.3 +
  Hibernate 5.6 → Spring 6.2 + Hibernate 6.6 (a test-only checkpoint, not
  released) → Spring 7.0 + Hibernate 7.x. The latest Hibernate is 7.4.12.Final;
  8.0 is still beta. Spring 7.0 officially supports Hibernate 7.1/7.2, and
  Spring Boot 4.1 pairs it with 7.4.
* **Why:** Spring 4.3 tops out at Hibernate 5.2, so Hibernate 4.3 → 5.2 can
  land first on Spring 4.3. That lets the schema-tooling rewrite (MOD-I1)
  happen without a framework change.
* **Alternatives considered:** Jump straight to Spring 7 + Hibernate 7.
  Rejected for the PR series, because it gives no green checkpoints. It is
  still used as the exploratory spike to surface blockers early.

### MOD-D6: adopt the research report's phase plan as the working plan

* **Decision:** Work through phases P0–P8 as laid out in the research report,
  with four atomic steps:
  * P2-2: Hibernate 5.2.
  * P4-1: the Spring 5.3 bloc.
  * P6-1 + P6-2: the cross-repo Jakarta checkpoint on Spring 6.2 / Hibernate
    6.6 / Tomcat 10.1. It is merged but never tagged.
  * P7-1: Spring 7 / Hibernate 7.4 / Tomcat 11.
* **Gates:** G-unit, G-portal, G-db (all vendors via Testcontainers) and
  G-flows.
* **Runtime JDK path:** 11 → 21 (P4) → 25 (P6). Spring 4.3/5.3 are not
  supported on JDK 25, so JDK 25 test legs stay informational until P6.
* **Release on Tomcat 11 only.** Tomcat 10.1 is used only at the P6
  checkpoint.
* **A non-merging spike** runs straight to the end state to surface blockers
  early.
* **Alternatives considered:**
  * Collapse P6 and P7-1 into one switch. This stays possible if the spike
    shows the 6.2→7.0 delta is small.
  * Release a javax 6.0. Rejected under MOD-D1.

## Issues

| ID | Area | Issue | Impact / plan |
| --- | --- | --- | --- |
| MOD-I1 | Persistence | Schema creation (`DataSourceSchemaExport`, `HibernateDbLoader`, `TableXmlHandler`, `FixedDatabaseMetadata`) uses Hibernate 4 APIs removed in 5.0: `Configuration.generateSchemaCreationScript`, `buildMapping`, `createMappings`, `Table.sqlCreateString`. `dataInit` depends on it. | Highest-risk persistence item. Rewrite on the `Metadata`/`SchemaManager` APIs, or move to Liquibase/Flyway (see MOD-Q4). |
| MOD-I2 | Portlets | All 37 built-in uPortal portlets use Spring Portlet MVC (`DispatcherPortlet`, `@RenderMapping`), which was removed in Spring 5.0. | Needs a forked or replacement Portlet MVC (for example PortletMVC4Spring), or a rewrite. |
| MOD-I3 | Portlets | Spring Web Flow portlet integration (17 portlet contexts, 26 admin flows) was removed in Web Flow 2.5. No Playwright spec exercises these flows. | Add Playwright coverage before touching them. Then fork the portlet integration or replace the flows. |
| MOD-I4 | Caching | Ehcache 2 is used directly in 58 files (`Element`, `SelfPopulatingCache`, search API), with JGroups replication and 184 cache regions. Spring 6 dropped Ehcache 2 support, and Hibernate 6 dropped hibernate-ehcache. | Move to JCache: Ehcache 3, or Caffeine plus a clustered option. |
| MOD-I5 | Persistence | Jadira usertype (Joda-Time mappings in events and cookies) does not support Hibernate 6. Joda-Time is imported in 86 files. | Move to `java.time` plus explicit column definitions that keep the existing column types. |
| MOD-I6 | Portlets | Apache Pluto 2.1.0-M3 and the portlet API are javax-only. The Portlet 2.0 API jar refers to `javax.servlet` only through `javax.servlet.http.Cookie`. | A namespace-transformed portlet API and Pluto may be feasible. Prototype on Tomcat 11. |
| MOD-I7 | Build | Gradle 6.9.4 cannot run on JDK 17+. google-java-format 1.7 and error-prone 2.3.4 fail on JDK 16+. | Phase 1. |
| MOD-I8 | uPortal-start | `javaVersionCheck` only accepts Java 1.8 and 11. Docker images use Java 8. | Phase 1 for uPortal-start. |
| MOD-I9 | uPortal-start | Tomcat 8.5 is end of life. The bundled CAS server is 3.6.0 on Spring 3.2 / Servlet 2.4. | Upgrade Tomcat. Replace or upgrade CAS (see MOD-Q3). |
| MOD-I10 | Environment | jitpack.io, api.adoptium.net, api.foojay.io, dlcdn.apache.org and the Playwright CDN are blocked in the prototype container. | Use the Ubuntu JDKs, archive.apache.org, and the pre-installed Chromium. |
| MOD-I11 | Docs | `AGENTS.md` says Hibernate 4.2.21, but the build uses 4.3.11.Final. | Fixed in phase 1. |
| MOD-I12 | Spring | uPortal-spring defines classes inside `org.springframework.*` packages, such as `ForwardingDispatcherPortlet` and `PortalPropertySourcesPlaceholderConfigurer`. | Split packages are fragile across Spring upgrades. Move them into `org.apereo.portal.*`. |
| MOD-I13 | Build | `uPortal-hibernate3-dialects` has no dependents in uPortal. Some portlets (Announcements, Bookmarks, Calendar, NewsReader) still declare it. | Keep publishing it until those portlets move, or tell adopters it is being dropped. |
| MOD-I14 | Persistence | 49 entities declare both `@SequenceGenerator` and `@TableGenerator` under the same name, with allocationSize from 1 to 10000. | Hibernate 6/7 resolve generators differently and validate increment sizes. Verify against real databases before changing anything. |
| MOD-I15 | Spring | 110 of 113 Spring XML contexts reference versioned XSDs (`beans-3.1` etc.), and 3 use `spring-security-4.2.xsd`, which Spring Security 5+ rejects. | Mechanical change to unversioned XSDs. Can land early. |
| MOD-I16 | Portlets | Apache Pluto is retired: Apache Portals went to the Attic in Nov 2025, `apache/portals-pluto` was archived Oct 2025, and the last release, 3.1.2, is javax. The Jakarta Portlet 4.0 spec is unreleased, with no `jakarta.portlet` artifacts on Central. The only Jakarta port is Liferay "Plutonium" 4.0.0-M1, which has one maintainer and is not on Central. | Don't depend on upstream Pluto. Precedent: Sakai (PR #15141, merged 2026-10-09) copied Pluto into its own tree and runs the `javax.portlet` API on `jakarta.servlet`. |
| MOD-I17 | Portlets | PortletMVC4Spring 6.0.0-M1 targets Spring 6.2 and Portlet 4.0 (which needs a Portlet 3+ container). There is no Spring 7 release, and it is not on Central past 5.3.4. | Not a drop-in replacement for the internal portlets on Pluto 2.1. Forking Spring Portlet MVC 4.3 (Apache-2.0) into uPortal is the likely path. |
| MOD-I18 | Portlets | Neither the Tomcat jakartaee-migration tool nor Eclipse Transformer renames `javax.portlet`. A converted portlet WAR therefore only links against a hybrid portlet API: `javax.portlet` compiled against `jakarta.servlet` (only `Cookie` is affected in Portlet 2.0). | The prototype spike tests deploy-time or build-time conversion of the bundled portlet WARs against a hybrid API + in-tree Pluto. |
| MOD-I19 | Soffit | `SoffitConnectorController` is itself a Spring Portlet MVC portlet. | Moving content to Soffit still needs the in-uPortal portlet container, or a rewritten connector. |
| MOD-I20 | Upstream | The published roadmap defines uP6 as Java 21 + Tomcat upgrade + converting external portlets to web components, and uP7 as dropping portlets. It also lists "Maven" (2025) and "Replace Gradle" (2028). An April 2025 uportal-dev thread (seen only via search snippets) revised uP6 to Java 17 on Tomcat 9 / javax. Renovate pins Spring < 5 and blocks Hibernate majors. A Spring 7 / Jakarta / Tomcat 11 6.0 merges uP6 and uP7 scope. | Needs an RFC in `uPortal-Project/community` Discussions before large PRs land (MOD-Q6). |
| MOD-I21 | Upstream | The owner has open upstream PRs that overlap phase 1: uPortal #2921 (Gradle 6.9.4 → 8.5, branch `build/gradle-8`), #2988 (sherter → Spotless, branch `refactor/spotless-migration`), and uPortal-start #681. | Phase 1 reuses their decisions. Then either update those PRs or replace them with the finer-grained series. |
| MOD-I22 | Upstream | `docs/architecture/subsystem-analysis.md` (PR #2917) already recommends: decomposing `uPortal-utils-core`; splitting `core-api` / `core-portlet`; extracting the rendering pipeline from Pluto; adopting Liquibase or Flyway; turning the Soffit connector into a servlet filter. It estimates about 21k portlet-specific LOC. | Align workstreams with these "first moves" so the work reads as executing the agreed analysis. |
| MOD-I23 | Persistence | IDs on SQL Server / MariaDB: `PortalDialectResolver` forces `SQLServer2005Dialect`, and MariaDB is detected as MySQL, so neither supports sequences. The `@SequenceGenerator`-named generators therefore probably fall back to tables there (inferred from dialect behaviour, not yet checked on a real database). Hibernate 7.4's `SQLServerDialect` / `MariaDBDialect` always use native sequences. | Data-compat risk. Either add a dialect subclass that turns sequence support off, or convert those tables to sequences once and run `SchemaManager.resynchronizeGenerators()` (7.2+). Verify on real databases with Testcontainers (WS3). |
| MOD-I24 | Persistence | PostgreSQL `@Lob`: Hibernate 4.3 stored large-object OIDs in `text` columns, while 5.6.2+ generates `oid` columns. `vacuumlo` can delete large objects referenced only from `text` columns, which is data loss. | Pin explicit column types for `@Lob` on PostgreSQL. Document this for adopters. |
| MOD-I25 | Persistence | `PortletPreferenceImpl.PREF_VALUE` is mapped as `@Lob` plus `@Type(StringType)`. Hibernate 6 removes that override, so the column would be treated as a CLOB. | Map it explicitly (`@JdbcTypeCode` / `columnDefinition`) so the existing column type stays the same. |
| MOD-I26 | Persistence | Hibernate 6.x/7.x change several defaults: native timezone storage (6.2), native booleans on Oracle 23, sequence naming (6.0), and the increment-size check (5.4). Each has a legacy setting. | Ship a "freeze 5.x behaviour" settings block in `hibernate.cfg.xml`, then remove settings one at a time behind tests. |
| MOD-I27 | uPortal-start | HSQLDB 2.5.1 (uPortal-start) is too old for Hibernate 6.2+, which needs 2.6.1+. uPortal itself uses 2.7.4. | Upgrade the uPortal-start HSQLDB with the toolchain work (WS2). |
| MOD-I28 | Tooling | The OpenRewrite Spring and Hibernate recipes are under the Moderne Source Available License, not Apache. They cover only mechanical renames, with nothing for 4.x→5.x or 7.2+. The `web.xml` recipe regex-replaces every `javax.`, including `javax.portlet`. | Use them as local tools only, and review every diff. Use Eclipse Transformer with custom rules for the namespace switch. |
| MOD-I29 | Spring | Spring Security 7's namespace handler rejects versioned XSDs older than 7.0. 3 files use `spring-security-4.2.xsd`. XML `request-matcher` only accepts `path` / `regex` / `ciRegex`. | Switch to the unversioned XSD early; this is safe on 4.2. |
| MOD-I30 | Spring | From Spring 6.1, `-parameters` is required, or name-based XML `<constructor-arg name=...>` and implicit `@RequestParam` / `@PathVariable` names fail at runtime. | Add `-parameters` to the compiler options in phase 1; it is harmless on Spring 4.3. |
| MOD-I31 | Spring | Spring 7 makes Jackson 3 (`tools.jackson`) the default. Jackson 2 support is deprecated in 7.0 and slated for removal in 7.2. uPortal uses Jackson 2 in UserTypes, REST and JSON views. | Move to Jackson 3 in the Spring 7 step. Keep the persisted JSON format byte-compatible (`JacksonType` columns). |
| MOD-I32 | Spring | Spring Session 1.3 → 2.0 changes the JDBC schema and the module layout. uPortal uses the data-redis store. | Sessions are transient; document that they are dropped at upgrade. |
| MOD-I33 | CI | `actions/setup-java@v6` removed the `adopt-hotspot` distribution that both CI matrices use. | Switch to temurin, zulu and corretto in the phase 1 CI change. |
| MOD-I34 | Logging | Possible latent bug, still to verify: SLF4J 1.7.36 is declared, but Logback 1.6.3 needs SLF4J 2.0.x. If 1.7.36 wins resolution, logging can silently do nothing. | Check with `dependencyInsight`, then fix in its own PR. |
| MOD-I35 | Runtime | Tomcat 9.0.x support ends 2027-03-31. A 9.1.x branch (no APR) gets releases until 2030-12-31. Tomcat 11.0.x needs Java 17. | uPortal-start moves to 9.0.x now (javax), then straight to 11 together with the Jakarta switch. |
| MOD-I36 | uPortal-start | No CAS server can share a Tomcat with javax uPortal: CAS 3.6 can't run on Jakarta Tomcat, and current CAS (7.3/8.0) is Jakarta-only. | Candidate: default to uPortal's local login (`SimpleSecurityContextFactory`, already enabled) and make CAS opt-in, run as a separate CAS Initializr overlay or container (MOD-Q3). |
| MOD-I37 | Search | Lucene 8 → 10.5 needs Java 21 and a reindex; Lucene 11 will need Java 25. | Rebuild the index on first start after upgrade. Document it. |
| MOD-I38 | Libraries | person-directory 3.0.1 (Spring 6, Java 17) has been dormant since 2022. jasypt has been unmaintained since 2019. commons-fileupload2 is still milestone-only. | Watch list. Plan Apereo-side releases or replacements. |
| MOD-I39 | Caching | Ehcache 3 has no JGroups/RMI replication; it clusters only via Terracotta. Infinispan (`infinispan-hibernate-cache-v66`, which covers Hibernate 6.6 and 7.1+) uses JGroups with invalidation, so it is the closest match to Ehcache 2 + JGroups. Caffeine is local-only. | Proposed: Hibernate L2 on Infinispan in clustered mode (Caffeine or JCache for single-node), and Spring cache through JCache. Needs a spike. |
| MOD-I40 | Web Flow | Spring Web Flow 4.0.x (Spring 7) is maintained, but has had no portlet integration since 2.5. Liferay PortletMVC4Spring has a `webflow` module for Spring 6.2 only. | The 26 admin portlet flows need a forked portlet integration or a rewrite. Write Playwright specs for them first (WS3). |
| MOD-I41 | Tests | uPortal-start PR #692 (May 2026) reported "99 passed / 7 failed" for Playwright. | P0 has to make the baseline green before it can serve as a gate. This session's baseline run is checking it. |
| MOD-I42 | Spring | Jackson 3 changes default property ordering and date output. | Use a mapper configured with Jackson 2 defaults for `JacksonType` columns and the REST payloads. Add golden-file tests. |
| MOD-I43 | Persistence | Hibernate 7.4 needs at least PostgreSQL 14, MariaDB 10.6 and SQL Server 2014. | Adopter prerequisite. State it in the RFC and in UPGRADING-6.0. |
| MOD-I44 | Security | cas-client 4.1 needs Java 21 and Servlet 6.1. | Use 4.0 at the Spring 6.2 checkpoint and 4.1 with Spring 7. |
| MOD-I45 | Logging | Spring 7 replaces `spring-jcl` with commons-logging 1.3, and uPortal bans commons-logging. | Revisit the exclusions and `jcl-over-slf4j` in the Spring 7 PR. |
| MOD-I46 | Runtime | Tomcat's newer defaults are lower: `maxParameterCount` is 1000 and `maxPartCount` is 50. | Set the connector values explicitly in uPortal-start. Add a large-form permissions spec. |
| MOD-I47 | Correction | The inventory said `XStreamMarshaller` and `PropertyPlaceholderConfigurer` were removed in Spring 6. That is wrong: both still exist in Spring 7.0. | Not blockers. Still handle the placeholder escaping in 6.2 and the XStream `--add-opens`. |
| MOD-I48 | Portlets | PortletMVC4Spring 5.2.0 already runs on Pluto 2.1 in uPortal-start (SimpleContentPortlet PR #550). So "Spring 5 + Portlet MVC replacement" splits into two steps: first vendor Spring Portlet MVC 4.3.30 on Spring 4.3, then adapt it inside the Spring 5.3 bloc. | Supersedes the second atomic step in WORKSTREAMS.md. |
| MOD-I49 | Portlets | The javax→jakarta switch is atomic across repos. It covers uPortal, uPortal-start `shared/lib`, and every deployed portlet WAR, because a mixed shared classloader cannot work. | The WAR transform pipeline (WS9) is on the critical path before the switch. |

## Open questions

| ID | Question | Status / proposed answer |
| --- | --- | --- |
| MOD-Q1 | Keep an external Tomcat, move to Spring Boot, or move to Jetty 12? | **Proposed:** keep an external Tomcat 11 for 6.0. Spring Boot 4 can come in *inside* the WAR (`SpringBootServletInitializer`, `@ImportResource` for the XML contexts, the Boot BOM), but Boot cannot host the portlet WARs, and embedded Tomcat with extra webapps is undocumented. Jetty 12.1 does support cross-context dispatch from ee11 into ee8 webapps (since 12.0.8). However, a context/classloader bug was only fixed on 2026-09-30, and Pluto passes container objects between contexts, which would need a javax↔jakarta adapter. Treat Jetty as a time-boxed spike, not the plan. |
| MOD-Q2 | Kotlin DSL / version catalog / convention plugins? | **Proposed:** order is Gradle 9 in Groovy → a `build-logic` included build with convention plugins plus `gradle/libs.versions.toml` (Renovate supports it) → configuration cache (Gradle 9's preferred mode) → Kotlin DSL file by file, last. Kotlin DSL has been the default since 8.2; Groovy is not deprecated. No OpenRewrite recipe exists for Groovy→Kotlin. |
| MOD-Q3 | Local SSO in uPortal-start? | **Proposed:** default to uPortal local login (`SimpleSecurityContextFactory`, already on). CAS becomes opt-in as a separate CAS 7.3/8.0 Initializr overlay or Docker container. java-cas-client 4.x is used once uPortal is on Jakarta. |
| MOD-Q4 | Schema management after hbm2ddl? | **Open (licence issue):** Liquibase 5.0 moved to the FSL (not OSI), which Apereo may not accept. Flyway OSS is Apache-2.0 with `baselineOnMigrate`, but needs per-vendor SQL. Candidate: Hibernate 7 `SchemaManager` for fresh installs, plus Flyway versioned migrations (baseline = 5.17 schema) for upgrades, tested on all vendors with Testcontainers. Needs community input. |
| MOD-Q5 | Portlets: transform the WARs, port them, or replace them with web components / Soffit? | **Proposed:** follow Sakai's approach and keep Pluto in-tree on `jakarta.servlet`, with the `javax.portlet` 2.0 API rebuilt against `jakarta.servlet`. Fork Spring Portlet MVC for the 37 built-in portlets. Use build-time conversion of the bundled portlet WARs as a *bridge* only. Move content portlets to web components (Vue 2 is EOL, so Lit or Vue 3) and Soffit over time. The prototype will verify the bridge. |
| MOD-Q6 | Propose 6.0 as an RFC in `uPortal-Project/community` Discussions before opening the large PRs? | **Recommended** |
