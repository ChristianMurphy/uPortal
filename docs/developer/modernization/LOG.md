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

### MOD-D7: baseline and sandbox gate

* **Baseline:** master `657741e` + `5007322bc` (MOD-I50), on Java 11 / Gradle
  6.9.4 / Tomcat 8.5.100 / HSQLDB.
* **uPortal unit tests:** all pass offline (uPortal-webapp: 376 run,
  0 failed).
* **Playwright:** 116 of 119 pass in both runs; 1 flaky test (feedback, run 1
  only); 3 failures are caused by the sandbox network (MOD-I55).
* **Sandbox G-portal gate:** every spec green except those 3
  environment-blocked tests, in two consecutive runs. CI (with internet) is
  the authority for those 3.

### MOD-D8: P0 safety nets landed in uPortal-start

* **Ports:** commit `2034439` adds `tomcatHttpPort`, `tomcatShutdownPort` and
  `hsqlPort` (defaults 8080, 8005, 8887), set with `@token@` filtering.
  Playwright reads `PORTAL_BASE_URL`. Parallel portals now work.
* **Flake fixes:** commits `09080d8` and `324f91a` add
  `waitForPortalRequestsToSettle()` after login (see MOD-I56), make the
  calendar test wait for the events response, and make the portlet-list
  `updatedOn` check tolerant of the clock ticking over a second.
* **Web-proxy test:** now asserts on the proxied content.
* **New specs (G-flows):** commits `ec4c20b`..`d5e0dba` cover Portlet Manager,
  Groups Manager, Permissions Manager, User Administration and
  Import/Export, all in `tests/ux/admin/`. They exercise 10+ Web Flow
  definitions and clean up after themselves.
* **Results:** 4 consecutive runs; the only failures are the 3
  sandbox-blocked tests plus the corrected web-proxy JS-errors test.
* **Still open:** filter CKEditor's version-check request by URL in
  visual-resource-server, so the gate is fully green offline.

### MOD-D9: the portlet WAR bridge works; move Tomcat 11 portlet fixes into P5

* **Evidence:** the non-merging spike converted the whole built 5.17
  portal (uPortal, all 12 portlet WARs including pluto-testsuite,
  resource-server, CAS 3.6 and shared/lib) with Tomcat's jakartaee-migration
  tool.
  * It passed the same 116/119 Playwright tests as the javax baseline on
    Tomcat 11.0.26 + JDK 21, Tomcat 11.0.26 + JDK 25, and Tomcat 10.1.60
    + JDK 21.
  * The new admin specs pass too.
  * All 94 registered portlets render, and the Pluto testsuite results
    match Tomcat 8.5 exactly.
* **The hybrid portlet API** is the tool's own output: 4 classes change
  (`Cookie` moves to jakarta, `javax.portlet` keeps its name). Cross-context
  `getCookies()` returns `jakarta.servlet.http.Cookie[]`.
* **Where portlets break:** Tomcat 11 (Pages 4.0 removals: MOD-I60–I62)
  and JDK 25 (XSLT extension functions: MOD-I63) break things. The jakarta
  rename itself does not.
* **Decision:**
  * MOD-Q5's bridge is validated for the bundled portlets.
  * Move the JSTL upgrade, the Spring 3.x WAR upgrades/retirements and the
    Notification Boot fix into P5 as javax-side pre-steps.
  * Fix MOD-I63 in code before the JDK 25 runtime move.
  * The JDK 25 runtime can be decoupled from P6, since the javax-era stack
    ran every exercised path on JDK 25 once MOD-I63 was handled.
  * Reword the risk register: Tomcat 11 dispatch and wrapping are fine;
    JSP verification of EOL tag libraries is the risk.
* **Evidence location:**
  [research/spike-jakarta/README.md](research/spike-jakarta/README.md)
  and its scripts.

### MOD-D10: DB upgrade harness is the G-db gate

* uPortal-start `45b7e4812` and `4765db8e8` add `tests/db/harness.sh`,
  `-PdbVendor`, the `dbSetup`/`dbSnapshot`/`dbVerify`/`dbRecord` tasks, and
  a GitHub Actions workflow.
* 5.17 snapshots are recorded on seven databases: HSQLDB 2.5.1, PostgreSQL
  16, MySQL 8.4, MariaDB 10.6, MariaDB 11.4, SQL Server 2022 and Oracle Free
  23. Each reproduced with zero drift on a fresh database.
* The report separates STRUCTURE, DATA, NAMES and GENERATORS drift.
* **Still to build:** the one-insert-per-entity check, and coverage of
  portlet tables.

### MOD-D11: 6.0 gets an improved schema and a cross-vendor copy migration

This supersedes the "freeze forever" and Flyway parts of MOD-Q4 and the
research report.

* **Decision (project owner):** do not carry the 4.3-era schema forever.
  6.0 ships an improved schema plus a one-command, verified **copy tool**.
  The tool reads a 5.17 database on any supported vendor and writes a
  fresh 6.0 schema on any supported vendor (HSQLDB, PostgreSQL, MySQL,
  MariaDB, SQL Server, Oracle).
  * The old database is left untouched, which gives free rollback.
  * Switching vendors during the upgrade comes for free (e.g. MySQL →
    PostgreSQL).
  * Event and analytics history is migrated by default, streamed, with an
    opt-out flag for raw events.
* **Schema improvements in scope:**
  1. Native sequences on every vendor. This ends the one-row ID tables on
     SQL Server/MySQL/MariaDB (MOD-I23) and drops the 49 dead
     `@TableGenerator`s.
  2. Proper LOB/text types per vendor. This ends the PostgreSQL OIDs stored
     in `text` columns and their `vacuumlo` data loss (MOD-I24/I25).
  3. `java.time`-native timestamp columns with explicit time-zone semantics,
     replacing Joda/Jadira storage (MOD-I5).
  4. Clean, consistent constraint and index names; dead objects dropped;
     missing FK indexes added; no MySQL `lower_case_table_names`
     requirement (MOD-I73).
* **How it fits the phases:**
  * Intermediate phases (P2–P6) still keep **zero drift** against the 5.17
    baselines, so code regressions stay separate from intentional schema
    changes. The freeze settings are the *source* contract the copy tool
    reads.
  * The 6.0 schema change lands as its own step with Hibernate 7 (after
    P7-1), and the freeze block is dropped there.
  * The copy-tool framework is built early as **5.17 → 5.17 cross-vendor
    copy** (identity mapping). That version is already useful to 5.x
    adopters, and it proves streaming, LOBs, FK ordering, sequence and
    ID-table seeding, and verification before the 6.0 transforms are added.
* **New gate, G-migrate:**
  * For each source vendor, migrate a 5.17 dump to the same vendor and to
    at least one other vendor.
  * The target schema must equal a fresh 6.0 install (zero drift against
    the fresh-install snapshot).
  * Per-table row counts and content hashes must match after the
    documented transforms.
  * Every ID generator must be seeded above `max(id)`, and one insert per
    entity must succeed.
  * The full Playwright suite must pass against the migrated database.
* **Alternatives considered:**
  * In-place per-vendor migrations (Flyway, 5 vendors' worth of SQL). No
    vendor switching, and rollback only by restoring a backup.
  * Both a copy tool and in-place migrations: the most work.
  * The XML export/import pipeline: vendor-neutral, but slow, and it
    doesn't cover event data.
  * Freeze forever: carries the MOD-I23/I24 cruft indefinitely.
* **Open details:**
  * Portlet-owned tables (Announcements, Calendar, …) share the portal
    database in uPortal-start. The plan is a generic same-structure copy for
    them, because their schemas don't change in 6.0.
  * Large-site performance: batching, parallel tables, resumability.
  * Rehearsal and dry-run reporting.

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
| MOD-I23 | Persistence | IDs on SQL Server / MariaDB: `PortalDialectResolver` forces `SQLServer2005Dialect`, and MariaDB is detected as MySQL, so neither supports sequences. The `@SequenceGenerator`-named generators therefore probably fall back to tables there (inferred from dialect behaviour, not yet checked on a real database). Hibernate 7.4's `SQLServerDialect` / `MariaDBDialect` always use native sequences. | **Confirmed by the P0-4 harness.** On SQL Server 2022, MySQL 8.4 and MariaDB 10.6/11.4, all 49 IDs are backed by one-row `UP_*_SEQ(next_val)` tables. HSQLDB, PostgreSQL and Oracle use 49 real sequences. All 49 `@TableGenerator`s are dead (no vendor has `hibernate_sequences`), and every sequence's increment equals its allocationSize. Fix: no-sequence dialect subclasses for SQL Server and MariaDB in P2/P6. |
| MOD-I24 | Persistence | PostgreSQL `@Lob`: Hibernate 4.3 stored large-object OIDs in `text` columns, while 5.6.2+ generates `oid` columns. `vacuumlo` can delete large objects referenced only from `text` columns, which is data loss. | **Confirmed by the P0-4 harness.** `UP_RAW_EVENTS.EVENT_DATA` and `UP_ANALYTICS_EVENTS.EVENT_DATA` are `text` columns that hold LOB OIDs. After one portal run, `vacuumlo -n` reported it would remove 720 large objects that are still in use. `PREF_VALUE` is `varchar(100000)`, so it is unaffected today. Pin explicit column types, and warn adopters now (this already affects 5.17). |
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
| MOD-I50 | Persistence | **Found by the prototype.** Since the Hibernate 4.3 upgrade, `hibernate*.cfg.xml` (via `hibernate.ejb.cfgfile`) is parsed through StAX. On the portal classpath that is Woodstox, which ignores Hibernate's local DTD resolver, so every startup downloads `http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd`. Portals without access to hibernate.org fail to start, and 60 JPA tests fail offline. | **Fixed** in commit `5007322bc` (removed the DOCTYPEs; Woodstox offline-parse regression test). Upstream candidate for 5.17.x. |
| MOD-I51 | Build | `com.github.ci-rt:google-visualization-java:release-1.1.3` exists only on jitpack, which is a supply-chain and availability risk. The sandbox blocks jitpack, so the prototype installed the jar from the official uPortal-webapp 5.17.9 WAR into `~/.m2`. | Replace it with a Central artifact, or vendor it. Track in WS12. |
| MOD-I52 | Environment | The sandbox gets HTTP 429 from Maven Central under load, and Gradle 6.9 doesn't retry. | Prototype-only workaround: an init script pointing to Google's Central mirror (scratchpad, not committed). |
| MOD-I53 | Runtime | `tomcatStop` leaves Tomcat running: two uPortal-owned worker threads (`pool-19-thread-1/2`) are never shut down, and Tomcat logs leak warnings naming the uPortal webapp. CI never notices. | **Fixed** in `0745364ca`. `PersonLookupHelperImpl` started up to 10 unnamed worker threads and never shut them down; one people search was enough to hang shutdown. The pool now closes with the Spring context, and its threads are named. Verified on a real portal with JDK 11 and 25. Upstream candidate for 5.17.x. |
| MOD-I54 | Tests | The web-proxy "no critical JavaScript errors" test passes by accident. It waits for "Example Domain", which matches the portlet title "Example Domain (test proxy)", not the proxied page. | Tighten the selector (P0-1). |
| MOD-I55 | Tests | The sandbox blocks three Playwright tests: web-proxy can't reach `example.com`, and two visual-resource-server tests fail because CKEditor 4.22.1's version check calls `cke4.ckeditor.com`. Feedback "renders feedback form" is flaky (post-login navigation race, 1 of 2 runs). | Sandbox gate = all specs except those 3 environment-blocked tests. Consider `versionCheck: false` for CKEditor (it also stops nagging adopters). Fix the feedback race in P0-1. |
| MOD-I56 | Rendering | **Found by the new specs.** A focused transient portlet (`/p/<fname>`) is kept in session state, and every `*.uP` request resets it (`UserLayoutParameterProcessor` → `TransientUserLayoutManagerWrapper.setFocusedId(null)`). A late AJAX request from the previous page (emergency alert, calendar, file manager) therefore makes the next render come back without the portlet. This caused the feedback/calendar Playwright flakes. | The tests now wait for in-flight `*.uP` requests to settle. The underlying race is a product bug: upstream candidate, fix after 6.0. |
| MOD-I57 | Admin UI | **Found by the new specs.** User Administration "Create a User" saves, but the next page throws an NPE. A null lookup for the new username is cached for 15 minutes, so the account can't be opened and edited attributes look stale. | The spec flushes that cache through Cache Manager. Upstream bug. |
| MOD-I58 | Admin UI | **Found by the new specs.** The editPermission breadcrumbs are action URLs used as plain links, so following one returns HTTP 405. Import/Export `delete.jsp` has broken markup that leaves the form covered and unclickable. | The specs work around both (keyboard submit). Upstream bugs. |
| MOD-I59 | API | `/api/entity` cannot delete groups or local accounts. | Spec cleanup goes through the UI. Possible API gap. |
| MOD-I60 | Portlets | **Spike B3 (Tomcat 11 only, critical).** 13 of 16 WARs ship JSTL 1.1.2, which uses `javax/jakarta.servlet.jsp.el`, removed in Pages 4.0. Any JSP using `<c:set>` fails on Tomcat 11. Tomcat 10.1 is fine. | P5: upgrade JSTL to Apache Taglibs 1.2.5 / Jakarta Tags in uPortal and every bundled portlet before P7-1. |
| MOD-I61 | Portlets | **Spike B4 (Tomcat 11 only).** Spring 3.x WARs (Feedback, CAS 3.6, ResourceServingWebapp, esup-filemanager) fail on Tomcat 11 for the same reason. | P5: upgrade or retire them. A shared/lib shim jar worked in the spike. |
| MOD-I62 | Portlets | **Spike B2 (Tomcat 11 only).** NotificationPortlet (Spring Boot 1.5) fails to deploy: Boot references `AbstractHttp11JsseProtocol`, which Tomcat 11 removed. | P5: one property that excludes Boot's embedded-container auto-config. |
| MOD-I63 | Runtime | **Spike B5 (JDK 25, critical).** JDK 25 disables XSLT extension functions by default, and uPortal's theme pipeline depends on them, so the portal is down on JDK 25. JDK 25 also tightens XML entity and depth limits. | **Fixed** in `9a2eea868`. `TemplatesBuilder` now enables `http://www.oracle.com/xml/jaxp/properties/enableExtensionFunctions` on its factory only; the processor is the JDK's built-in XSLTC (`xalan/java` extensions). Verified on JDK 11/17/21/25, and the guest and admin pages render on JDK 25 without the JVM flag. The JDK 25 XML limits don't affect uPortal's XSL or the Woodstox-based import. Upstream candidate. |
| MOD-I64 | Tooling | **Spike B1.** `icu4j-2.6.1.jar` (in Calendar and NewsReader) contains a corrupt class, so the migration tool aborts the whole WAR. Tomcat's `legacyAppBase` deploy-time conversion therefore can't handle these two WARs. | The transform pipeline needs a per-WAR `-exclude` list and must fail the build on a non-zero exit. |
| MOD-I65 | Tooling | The jakartaee-migration default TOMCAT profile also renames `javax.persistence`, `javax.transaction`, `javax.ejb`, `javax.mail` and the annotation classes. Each WAR stays self-consistent, so it works. A narrow profile needs about 20 lines of Java (`NarrowMigrate.java`), and it passed too. | P5-5: use the narrow profile. It rewrites fewer classes and leaves `javax.persistence.*` property keys unchanged. |
| MOD-I66 | Tooling | A removed-Servlet-API scanner must check linkage, not grep method names. The hard failures came from removed *classes* in signatures and exception tables, reached through Jasper tag introspection and Tomcat internals. | Prototype `JakartaLinkScan.java` (in research/spike-jakarta/scripts). Class-level hits predicted every hard failure. |
| MOD-I67 | Correction | MOD-I36 said CAS 3.6 cannot run on Jakarta Tomcat. In fact, once transformed it runs on 10.1 as-is, and on 11 with the jsp.el shim. | CAS 3.6 can be a stop-gap through P6. Making CAS opt-in or external (MOD-Q3) is still right because CAS 3.6 is EOL. |
| MOD-I68 | Runtime | Tomcat 10.1/11 silently ignore `compressableMimeType` (uPortal-start `server.xml`) and `disableURLRewriting` (Feedback). Several WARs ship `spring-test` and two JSTL implementations. | Fix in the P6-2 overlays. Dependency hygiene in P5. |
| MOD-I69 | Runtime | `DatabaseHealthChecker` creates a new thread pool on every health check and never shuts it down. | Small upstream fix. |
| MOD-I70 | Tests | Six uPortal-webapp tests create their own TransformerFactory (e.g. `XsltPortalUrlProviderTest`, `PortletDataUpgradeTest`), so they still fail on a JDK 25 test JVM. | Route them through `TemplatesBuilder` or enable the feature in test setup when the CI test JDK moves to 25. |
| MOD-I71 | Runtime | The person lookup workers never clear the request data they are handed, so idle threads keep a reference to the last request. | Minor leak. Upstream fix later. |
| MOD-I72 | Persistence | **Found by the P0-4 harness.** Master (Hibernate 4.3, unreleased 5.17.10) already differs from released 5.17.9 (Hibernate 4.2) on the same schema. The structure and constraint names are the same, but `UP_SEQUENCE` counters come out at 110 vs 60 (POOLED became POOLED_LO in #3012), `UP_PORTLET_PREF_SEQ` at 131 vs 121, and the PostgreSQL dialect changed from 82 to 81. | Check that 5.17.9 → 5.17.10 is safe for existing counters before 5.17.10 ships. Re-record the baselines from 5.17.9 if the harness should gate the upgrade from the last release. |
| MOD-I73 | Persistence | On MySQL and MariaDB, 5.17 needs `lower_case_table_names=1` (the import scripts use `up_group`). MariaDB Connector/J needs `useMysqlMetadata=true`. On MariaDB, the dialect depends on the driver: through MySQL Connector/J, 10.6 gets `MySQL5InnoDBCompressedDialect` and `ROW_FORMAT=COMPRESSED` tables, while 11.4 doesn't. | Document this as adopter configuration. Hibernate 6/7's MariaDB dialect detection changes it again, so the harness has to cover both drivers. |

## Open questions

| ID | Question | Status / proposed answer |
| --- | --- | --- |
| MOD-Q1 | Keep an external Tomcat, move to Spring Boot, or move to Jetty 12? | **Proposed:** keep an external Tomcat 11 for 6.0. Spring Boot 4 can come in *inside* the WAR (`SpringBootServletInitializer`, `@ImportResource` for the XML contexts, the Boot BOM), but Boot cannot host the portlet WARs, and embedded Tomcat with extra webapps is undocumented. Jetty 12.1 does support cross-context dispatch from ee11 into ee8 webapps (since 12.0.8). However, a context/classloader bug was only fixed on 2026-09-30, and Pluto passes container objects between contexts, which would need a javax↔jakarta adapter. Treat Jetty as a time-boxed spike, not the plan. |
| MOD-Q2 | Kotlin DSL / version catalog / convention plugins? | **Proposed:** order is Gradle 9 in Groovy → a `build-logic` included build with convention plugins plus `gradle/libs.versions.toml` (Renovate supports it) → configuration cache (Gradle 9's preferred mode) → Kotlin DSL file by file, last. Kotlin DSL has been the default since 8.2; Groovy is not deprecated. No OpenRewrite recipe exists for Groovy→Kotlin. |
| MOD-Q3 | Local SSO in uPortal-start? | **Proposed:** default to uPortal local login (`SimpleSecurityContextFactory`, already on). CAS becomes opt-in as a separate CAS 7.3/8.0 Initializr overlay or Docker container. java-cas-client 4.x is used once uPortal is on Jakarta. |
| MOD-Q4 | Schema management after hbm2ddl? (**Superseded by MOD-D11** for the 5.17 → 6.0 path; Flyway vs Liquibase is still open for changes *after* 6.0) | **Open (licence issue):** Liquibase 5.0 moved to the FSL (not OSI), which Apereo may not accept. Flyway OSS is Apache-2.0 with `baselineOnMigrate`, but needs per-vendor SQL. Candidate: Hibernate 7 `SchemaManager` for fresh installs, plus Flyway versioned migrations (baseline = 5.17 schema) for upgrades, tested on all vendors with Testcontainers. Needs community input. |
| MOD-Q5 | Portlets: transform the WARs, port them, or replace them with web components / Soffit? | **Proposed:** follow Sakai's approach and keep Pluto in-tree on `jakarta.servlet`, with the `javax.portlet` 2.0 API rebuilt against `jakarta.servlet`. Fork Spring Portlet MVC for the 37 built-in portlets. Use build-time conversion of the bundled portlet WARs as a *bridge* only. Move content portlets to web components (Vue 2 is EOL, so Lit or Vue 3) and Soffit over time. The prototype will verify the bridge. |
| MOD-Q6 | Propose 6.0 as an RFC in `uPortal-Project/community` Discussions before opening the large PRs? | **Recommended** |
