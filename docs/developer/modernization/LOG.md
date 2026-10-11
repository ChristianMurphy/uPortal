# uPortal 6.0 modernization log

Running log for the uPortal 6.0 modernization prototype: issues found, decisions
taken (with the alternatives considered), and open questions. The newest entries
are at the bottom of each section. IDs are stable, so commits and PRs can cite
them (as in `Refs MOD-I3`).

The target is Java 25 (LTS), Gradle 9, Spring Framework 7, Spring Security 7,
Hibernate ORM 7 (Jakarta Persistence 3.2), Jakarta EE 11 / Tomcat 11, with all
uPortal-start Playwright specs green, the uPortal CI matrix green, and existing
databases upgrading in place.

## Work organisation

* Prototype work happens in git worktrees under `/home/user/wt/`, one per
  workstream, each on its own local branch. Finished, green steps are merged
  into the `ccr-*` integration branch of each repo.
* Each commit has to build and pass tests on its own, so it can later be
  replayed as its own upstream PR.
* Baseline: uPortal 5.17.10-SNAPSHOT on Java 11 / Gradle 6.9.4, deployed with
  uPortal-start on Tomcat 8.5.100 with HSQLDB.

## ADRs

The decisions that matter upstream are architecture decision records in
[`docs/developer/adr`](../adr/README.md) (MOD-D36). Each ADR summarizes its own
evidence, because this log stays on the prototype branch.

| ADR | Title | Log entries |
| --- | --- | --- |
| 0001 | Record architecture decisions with MADR | MOD-D36 |
| 0002 | Target Java 25 and Jakarta EE 11 in 6.0, reached in tested steps | MOD-D1, MOD-D2, MOD-D5, MOD-D6, MOD-D9 |
| 0003 | Build with Gradle 9 before raising the bytecode level | MOD-D3, MOD-D17, MOD-Q2 |
| 0004 | Deploy uPortal as a WAR on an external Tomcat 11 | MOD-Q1, MOD-D6, MOD-D22, MOD-D28 |
| 0005 | Prefer the Spring approach for every migration, and cache through Spring's Cache API | MOD-D22, MOD-Q10 |
| 0006 | Upgrade Hibernate in steps with the 5.17 schema frozen | MOD-D5, MOD-D11, MOD-D33 |
| 0007 | Replace Joda-Time with java.time | MOD-D11, MOD-D22 |
| 0008 | Move to an improved 6.0 schema with a cross-vendor copy tool | MOD-D11, MOD-D25, MOD-D28 |
| 0009 | Run 6.0 on current database servers, with an index set measured under load | MOD-D12, MOD-D16, MOD-D24, MOD-D25 |
| 0010 | Compare each step with 5.17's output, on each supported database | MOD-D10, MOD-D16, MOD-D23, MOD-D29 |
| 0011 | Keep Spring Portlet MVC and Pluto in uPortal's source, and transform portlet WARs for Jakarta | MOD-D9, MOD-D13, MOD-D18, MOD-D26, MOD-Q5 |
| 0012 | Replace uPortal's built-in portlets with web components or Thymeleaf pages before the Jakarta switch | MOD-D18, MOD-D19, MOD-D26 |
| 0013 | Build web components with Lit, Vite+ and Vitest, and model their state with XState | MOD-D18, MOD-D20, MOD-D31 |
| 0014 | Keep Bootstrap as the theme framework and target Bootstrap 6 | MOD-D21 |
| 0015 | Draw statistics charts with Apache ECharts | MOD-D34 |
| 0016 | Make uPortal's local login the uPortal-start default, with CAS as an option | MOD-Q3, MOD-D30 |
| 0017 | Require static analysis in CI, with a SpotBugs baseline and NullAway on null-marked code | MOD-D27 |
| 0018 | Check commit messages and 6.0 docs in CI, and keep history linear | MOD-D32, MOD-D35 |

MOD-D4, MOD-D7, MOD-D8, MOD-D14, MOD-D15, MOD-D30 and MOD-D37 are about how the
prototype runs (its machines, branches, CI runs and history) and have no ADR.
MOD-D30 is listed above only for the local login workstream it started.

## Decisions

### MOD-D1: go to full Jakarta in the 6.0 line

* **Decision:** Target Jakarta EE (Spring 7, Hibernate 7, Tomcat 11) rather
  than stopping at the last javax-compatible versions.
* **Alternatives considered:**
  * Stop at the javax ceiling (Spring 5.3, Hibernate 5.6, Tomcat 9).
    Rejected because Spring 5.3 OSS support has ended, and it only postpones the
    portlet-container problem.
  * Minimal build + Hibernate upgrade. Rejected because Hibernate 5.3+
    needs Spring 5, so Spring has to move anyway.
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
  * Raise the bytecode level in the same step. Rejected: it mixes a breaking
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
* **Alternatives considered:** jump straight to Spring 7 + Hibernate 7.
  Rejected for the PR series, because it doesn't give green checkpoints. It
  is still used as the exploratory spike to find blockers early.

### MOD-D6: adopt the research report's phase plan as the working plan

* **Decision:** Work through phases P0 to P8 as laid out in the research report,
  with four atomic steps:
  * P2-2: the Hibernate 5.2 upgrade.
  * P4-1: the Spring 5.3 bloc.
  * P6-1 + P6-2: the cross-repo Jakarta checkpoint on Spring 6.2 / Hibernate
    6.6 / Tomcat 10.1. It is merged but never tagged.
  * P7-1: the Spring 7 / Hibernate 7.4 / Tomcat 11 step.
* **Required checks:** G-unit, G-portal, G-db (all vendors via Testcontainers) and
  G-flows.
* **Runtime JDK path:** 11 → 21 (P4) → 25 by P6. The first plan tied
  JDK 25 to P6, because Spring 4.3/5.3 don't officially support JDK 25.
  MOD-D9 decoupled it: the javax-era stack ran every exercised path on
  JDK 25 once MOD-I63 was fixed, so the move to 25 can come before P6.
  Since phase 1 (MOD-D17), CI requires the JDK 25 legs to pass.
* **Release on Tomcat 11 only.** Tomcat 10.1 is used only at the P6
  checkpoint.
* **A non-merging spike** runs straight to the end state to find blockers
  early.
* **Alternatives considered:**
  * Collapse P6 and P7-1 into one switch. This stays possible if the spike
    shows the 6.2→7.0 delta is small.
  * Release a javax 6.0. Rejected under MOD-D1.

### MOD-D7: baseline and sandbox check

* **Baseline:** master `657741e` + `5007322bc` (MOD-I50), on Java 11 / Gradle
  6.9.4 / Tomcat 8.5.100 / HSQLDB.
* **uPortal unit tests:** all pass offline (uPortal-webapp: 376 run,
  0 failed).
* **Playwright:** 116 of 119 pass in both runs, with 1 flaky test (feedback,
  run 1 only). The 3 failures are caused by the sandbox network (MOD-I55).
* **Sandbox G-portal check:** every spec green except those 3
  environment-blocked tests, in two consecutive runs. CI (with internet) is
  the authority for those 3.

### MOD-D8: P0 safety nets merged in uPortal-start

* **Ports:** commit `2034439` adds `tomcatHttpPort`, `tomcatShutdownPort` and
  `hsqlPort` (defaults 8080, 8005, 8887), set with `@token@` filtering.
  Playwright reads `PORTAL_BASE_URL`. Parallel portals now work.
* **Flake fixes:** commits `09080d8` and `324f91a` add
  `waitForPortalRequestsToSettle()` after login (see MOD-I56). They also make
  the calendar test wait for the events response and make the portlet-list
  `updatedOn` check tolerant of the clock ticking over a second.
* **Web-proxy test:** now asserts on the proxied content.
* **New specs (G-flows):** commits `ec4c20b`..`d5e0dba` cover Portlet Manager,
  Groups Manager, Permissions Manager, User Administration and
  Import/Export, all in `tests/ux/admin/`. They exercise 10+ Web Flow
  definitions and clean up after themselves.
* **Results:** 4 consecutive runs; the only failures are the 3
  sandbox-blocked tests plus the corrected web-proxy JS-errors test.
* **Still open:** filter CKEditor's version-check request by URL in
  visual-resource-server, so the check is fully green offline.

### MOD-D9: transformed portlet WARs run on Jakarta Tomcat; move Tomcat 11 portlet fixes into P5

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
* **Where portlets break:** Tomcat 11 (Pages 4.0 removals, MOD-I60 to I62)
  and JDK 25 (XSLT extension functions: MOD-I63) break things. The jakarta
  rename itself does not.
* **Decision:**
  * MOD-Q5's build-time WAR transformation is validated for the bundled
    portlets.
  * Move the JSTL upgrade, the Spring 3.x WAR upgrades/retirements and the
    Notification Boot fix into P5 as javax-side pre-steps.
  * Fix MOD-I63 in code before the JDK 25 runtime move.
  * The JDK 25 runtime can be decoupled from P6, since the javax-era stack
    ran every exercised path on JDK 25 once MOD-I63 was handled.
  * Reword the risk register to say that Tomcat 11 dispatch and wrapping
    are fine and that JSP verification of EOL tag libraries is the risk.
* **Evidence location:**
  [research/spike-jakarta/README.md](research/spike-jakarta/README.md)
  and its scripts.

### MOD-D10: DB upgrade harness is the G-db required check

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

* **Decision (project owner):** do not keep the 4.3-era schema forever.
  6.0 provides an improved schema plus a one-command, verified **copy tool**.
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
  * Intermediate phases (P2 to P6) are still held to **zero drift** against
    the 5.17 baselines. That way code regressions aren't mixed up with
    intentional schema changes. The freeze settings are the *source* contract the copy tool
    reads.
  * The 6.0 schema change comes as its own step with Hibernate 7 (after
    P7-1), and the freeze block is dropped there.
  * The copy-tool framework is built early as **5.17 → 5.17 cross-vendor
    copy** (identity mapping). That version is already useful to 5.x
    adopters, and it proves streaming, LOBs, FK ordering, sequence and
    ID-table seeding, and verification before the 6.0 transforms are added.
* **New required check, G-migrate:**
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

### MOD-D12: 6.0 schema has a tested index set for high-traffic sites

* **Requirement (project owner):** the 6.0 schema has to hold up for a
  high-traffic portal with heavy analytics. Tests must prove that every
  needed index exists and that redundant or unused indexes are removed.
* **Plan: a new required check, G-index, on PostgreSQL first, then MySQL/MariaDB.**
  1. **Load:** a synthetic high-volume dataset, built by a seeded,
     repeatable generator, sized for a large campus. Target scale is to be
     confirmed (MOD-Q7). It covers users, layouts and fragments, groups,
     permissions, portlet entities and preferences, millions of raw events,
     and a year of aggregations.
  2. **Workload capture:**
     * Run the full Playwright suite, the JMeter scenarios (uPortal-start
       already has the plugin), and the analytics jobs (raw-event
       aggregation, purge, session cleanup).
     * Collect every distinct SQL statement, using `pg_stat_statements` and
       Hibernate's statement inspector.
  3. **Checks (fail the build unless allowlisted):**
     * No sequential scan in `EXPLAIN` on tables above a size threshold.
     * Each FK column has an index.
     * No duplicate or left-prefix-redundant indexes.
     * Report indexes with `idx_scan = 0` after the workload as removal
       candidates.
     * Report the top statements by total time.
  4. **Where the results go:** the outcome defines the 6.0 index set. That
     set is generated by Hibernate 7 from explicit `@Index` declarations, is
     created by the copy tool on the target, and is snapshotted by the
     harness.
* **Alternatives considered:**
  * Hand-review indexes only. Rejected: not repeatable, and it doesn't
    catch regressions.
  * Production query logs alone. Useful as input if an adopter can share
    anonymised `pg_stat_statements` output (MOD-Q8), but not reproducible in
    CI.

### MOD-D13: fix Tomcat 11 portlet breakage in uPortal-start overlays first

* **Decision (project owner):** handle MOD-I60 to I62 (JSTL 1.1.2 in 13 WARs,
  the Spring 3.x WARs, and NotificationPortlet's Boot auto-config) in
  uPortal-start's overlay build for 6.0. That means swapping JSTL jars and
  applying small fixes as each overlay is built. Proper fixes and releases
  in the individual portlet repositories follow during 6.x.

### MOD-D14: hold upstream; review on branches

* **Decision (project owner):**
  * Don't send anything upstream for now: no PRs and no RFC post yet.
  * Review happens on the `ccr-*` branches, without draft PRs.
  * Upstream candidates keep accumulating in this log, such as the
    5.17.x fixes MOD-I50/I53/I63 and the P0 test assets.

### MOD-D15: every integration push must pass real CI and workflow lint

* **G-ci:** after each merge into a `ccr-*` branch, the GitHub Actions CI
  run on that push must be green. For uPortal that is the build matrix on
  Linux/Windows/macOS × temurin/zulu/corretto. For uPortal-start it is the
  build matrix plus Playwright. The Workflow lint run (zizmor + actionlint)
  must also be green.
* A phase isn't "done" until G-ci is green as well as the local checks.
* Any workflow change gets checked with zizmor (regular and pedantic
  personas) and actionlint locally before it is pushed.

### MOD-D16: run multiple databases in GitHub Actions

* **Today:**
  * uPortal CI runs its JPA/DAO tests only on in-memory HSQLDB.
  * uPortal-start CI runs on HSQLDB.
  * `db-harness.yml` (uPortal-start) can run 7 databases. Postgres 16,
    SQL Server 2022 and Oracle Free 23 run as service containers. MySQL 8.4
    and MariaDB 10.6/11.4 are started with `docker run`. It covers
    `dataInit`, a schema snapshot diff and the API specs. It is not
    triggered on the integration branches yet, because of MOD-I72.
* **Plan:**
  1. Add a 5.17.9 baseline, so CI (which builds against the released
     uPortal) can compare. The baseline is picked automatically from
     `uPortalVersion`. Enable `db-harness.yml` on `ccr-*` pushes and PRs.
  2. uPortal: make the JPA test context's JDBC URL and dialect
     configurable, and add a `db-integration` CI job. It runs the DAO/JPA
     tests on PostgreSQL, MariaDB and MySQL on every push, and on SQL
     Server and Oracle nightly and on demand. Dialect and query problems
     then show up in uPortal itself rather than only in uPortal-start.
  3. uPortal-start: run the full Playwright suite on PostgreSQL and
     MariaDB on every push. SQL Server and Oracle run nightly.
  4. G-migrate (MOD-D11): a matrix of source and target vendors, with every vendor →
     PostgreSQL and every vendor → itself, nightly.
  5. G-index (MOD-D12): a 1/100-scale dataset on every push (Postgres),
     and the full large-campus dataset on demand. Full scale is about 150M
     raw events, which is too heavy for standard runners.
* **Prototype constraint:** this container only has room to run SQL Server
  and Oracle one at a time (disk/RAM). Recording baselines for those
  vendors happens in CI, with the snapshots uploaded as artifacts and then
  committed.

### MOD-D17: phase 1 toolchain merged in both repos

* **uPortal (`31bdb3a98`, merge of `p1-gradle-toolchain`, 20 commits):**
  * Gradle 9.8.1, run on JDK 21 (25 also works). Bytecode is still
    `--release 11`, compiled with `-parameters`.
  * `maven-publish` with an `install` alias. `jcenter` dropped.
  * Spotless 8.10.4 + gjf 1.36.1 (1.37.0 fails inside Spotless) with
    `verGJF`/`goJF` aliases. The reformat (`246b5e05f`, 409 files) ran
    with gjf 1.24.0, the newest release that runs on JDK 11. 1.36.1 gives
    the same output, and moving to it (`9dc3ca7ad`) didn't change any
    file. The reformat is listed in `.git-blame-ignore-revs`.
  * Error Prone 2.50.0, SpotBugs plugin 6.5.12, JaCoCo 0.8.15, CodeNarc
    groovy-4 build, nebula.lint 21.3.0.
  * Mockito 5.24.0 loaded as a javaagent. Groovy 4.0.33 + Spock 2.4.
  * Coveralls through the GitHub Action.
  * CI on JDK 21 (ubuntu) and 25 (windows/macos).
  * Test counts are identical on JDK 11, 21 and 25 (1055 tests).
  * POMs: same coordinates and metadata. Test-scope dependencies are no
    longer listed; `runtimeOnly` now appears with runtime scope.
  * SLF4J resolves to 2.0.18 with Logback 1.6.3, which works (closes
    MOD-I34).
  * #2921 is superseded and extended, but conflicts with it on the war
    publication (#2921 doesn't publish dependencies). #2988 is extended.
* **uPortal-start (`0a73bcde2`, merge of `p1-start-toolchain`, 10 commits):**
  * Gradle 6.9.4 → 7.6.6 → 8.14.6 → 9.8.1, with the daemon on JDK 21
    (`gradle/gradle-daemon-jvm.properties`); `JAVA_HOME` stays Java 11 for
    Tomcat, HSQLDB and the data tools.
  * `javaVersionCheck` accepts 11, 17, 21 and 25.
  * Tomcat 9.0.122 (connector limits explicit, `compressibleMimeType`).
  * HSQLDB 2.7.4: upgrades a 2.5.1 database in place and can't be
    downgraded.
  * Playwright 1.64.0 (type arguments added to keep type-coverage at
    100%).
  * The CKEditor version-check request is filtered by URL only, so the
    sandbox check is now fully green except the web-proxy internet tests.
  * gradle-docker-plugin 10.0.0. JMeter plugin removed (dead since 2018).
  * Overlay WARs are byte-identical across the Gradle hops. On Gradle 9,
    only 4 CAS classes differ, and they have the same bytecode level and
    instructions.
* **Adopter changes** (for UPGRADING-6.0):
  * New `gradle.properties` values: `tomcatVersion`, `servletApiDependency`
    4.0.1, `hsqldbVersion`, `gradleDockerPluginVersion`.
  * A JDK 21 is needed for Gradle.
  * Rebase `etc/tomcat` customisations onto Tomcat 9.
  * Rename the overlay build-script configurations.
* **G-ci result:**
  * uPortal: phase 1 merge runs 38058739659 and 38058850716 are green
    (ubuntu temurin/zulu/corretto on JDK 21, windows/macos on JDK 25).
  * uPortal-start: the first post-merge run failed on windows-latest.
    setup-gradle moves `GRADLE_USER_HOME` to `D:\\a\\.gradle`, so the
    daemon-JDK hint written to `~/.gradle` was never read and Gradle
    tried to auto-provision a JDK. Fixed in `9dbed695b`, which writes to
    `${GRADLE_USER_HOME}` and sets `fail-fast: false`.
  * CI run 38060775616 and Integration run 38060775604 (uPortal built
    from source, DB harness and Playwright) are green on all legs.
  * Docs-only pushes now include `[skip ci]`, so they don't queue (and
    cancel) full matrix runs.

### MOD-D18: replace uPortal's own portlets with REST + web components before Jakarta

* **Decision (project owner):** the copied Spring Portlet MVC / Web Flow
  portlet integration classes (P3-1) are kept only through the Spring 5.3
  step (P4), until they are replaced.
  PortletMVC4Spring 5.3.4 shows the Spring 5 adaptations needed there.
  All 37 portlets in uPortal's own `portlet.xml`, plus the 26 admin flows,
  are replaced during P4/P5 by **REST APIs + Lit-based web components**,
  placed through a native web-component channel type. The copied code is
  deleted before the Jakarta switch (P6), so it is never ported to Spring
  6/7 or Jakarta.
* **What stays:** the Pluto container remains as a compatibility layer
  for the bundled external portlet WARs (each carries its own Portlet MVC;
  MOD-D9/D13) and for adopters' portlets. Soffit moves to a pipeline
  component (MOD-Q9).
* **New workstream, WS14:**
  1. **Foundation:** a web-component channel type (renders the custom
     element and its module script, passes context such as user, locale,
     window state and preferences, and authenticates REST calls with the
     existing JWT/OIDC userinfo token); a shared Lit base library (Bootstrap
     5 tokens, i18n from `Messages.properties`, accessibility); build and
     packaging in uPortal; a data upgrade that turns portlet definitions
     referencing the built-in portlets into web-component channel
     definitions (XML import upgrader plus a copy-tool rule).
  2. **Reference conversion:** Groups Manager, with its REST API, its web
     component, and its Playwright spec retargeted.
  3. **Fan-out per portlet,** in parallel by sub-agents. Admin domains:
     portlets, groups, permissions, users, fragments, import/export, cache,
     tenants, analytics. User-facing: login, account, favorites,
     marketplace, search, directory, locale, layout reset, skin, session
     timeout. Trivial portlets become built-in channel types or are
     dropped, documented in UPGRADING-6.0.
* **Required check:** `tests/ux/admin` and the related specs keep asserting the
  same behaviour against the new UI. Each converted portlet removes its
  Portlet MVC / Web Flow dependency, and P6 requires that the copied code
  has been deleted.
* **Alternatives considered:**
  * Replace first, without keeping the copied code in between. That stalls
    every framework upgrade on the rewrite.
  * Keep the copied code through 6.0 and replace it in 6.x. That means
    porting dead code through Spring 6/7 and Jakarta.
  * Servlet Web Flow 4 with server-side views. That keeps a second UI
    stack.

* **Status (2026-10-11, WS14 foundation, on `ccr-43ba0785-hzlrj5` in both
  repositories):**
  * Channel type: a definition of the "Web Component" portlet type
    (framework portlet `WebComponent`; parameters `webComponent.tagName`,
    `moduleUrl`, `attributes`, `preferenceAttributes`) gets a
    `WebComponentRenderExecutionWorker` from `PortletWorkerFactoryImpl`
    instead of a Pluto worker. It renders on the request thread in page
    order, so a module script is emitted once per page, with no XSL change.
    The element gets `locale`, `window-state`, `context-path`, `fname`,
    `csrf-header` and `csrf-token`.
  * REST calls use the session cookie and that CSRF token; `/api/v6-0/`
    is CSRF-protected, bearer requests exempt. The JWT/OIDC userinfo
    route is documented, not wired.
  * Reference conversion (MOD-D19): `/api/v6-0/favorites` and
    `<up-favorites>`. uPortal-start publishes Favorites as a Web Component
    channel. The import upgrader and copy-tool rule are not built
    (MOD-I133). Guide: `docs/developer/web-components/README.md`.
  * Popular Portlets: `/api/v6-0/popular-portlets` and
    `<up-popular-portlets>`, published by uPortal-start as a Web Component
    channel. The characterisation spec passed on the portlet and then,
    unchanged, on the component. The conversion fixes MOD-I193 rather than
    matching it. Notes: `notes/wc-popular-portlets.md`.

### MOD-D19: per-portlet choice between REST + web components and server-rendered Spring pages

This refines MOD-D18 at the project owner's direction: replacements may be
either REST + web components or native Spring MVC pages with Thymeleaf,
chosen per portlet.

* **Rule of thumb:**
  * **Web component + REST** when the thing is *inside* portal pages
    next to other content: a widget in a layout region, used by end users,
    benefits from client-side interactivity, or is reused by other clients
    such as mobile or uPortal-home.
  * **Spring MVC + Thymeleaf page** when it is a *full-page tool or form
    flow*: an admin console, a multi-step wizard, or a security-sensitive
    form (login, password, account). Spring Security's CSRF and auth
    integrate directly, it doesn't need a JS build, it is accessible by default
    (progressive enhancement, optionally with htmx), and JSP → Thymeleaf
    conversion is mostly mechanical. Multi-step wizards use plain
    controllers with session-scoped wizard state. Servlet-mode Spring Web
    Flow 4 is allowed where a flow is complex enough to need it.
* **Versions:**
  * Thymeleaf 3.1.5 with `thymeleaf-spring5` while on Spring 5.3 (javax).
  * Switch to `thymeleaf-spring6` 3.1.5 at P6. Spring Boot 4.2 manages
    exactly this for Spring 7.
  * Templates don't change across the Jakarta switch.
  * Layout dialect 4.0.1 shares the Respondr header and footer, so admin
    pages keep the portal chrome.
* **Default mapping** (to be confirmed per portlet by the reference
  conversions):
  * **Thymeleaf admin console under `/uPortal/admin/...`:**
    * Portlet / Groups / Permissions / User / Portal / Fragment
      Administration, Exit Fragment Administration, Import/Export.
    * Cache Manager, Attribute Swapper, Fragment Audit, Statistics and
      Portal Activity, SQL Query.
    * Tenant Manager, Toggle Resources Aggregation, Translator,
      Dynamic Respondr Skin.
  * **Thymeleaf full-page user flows:** Login, Forgot Password,
    Self-Edit Account (with update-password / reset-password), Reset My
    Layout.
  * **Web components + REST, placeable in layouts:** Favorites, Portlet
    Marketplace, Search, Directory, Popular Portlets, User Locale,
    background preference, Session Timeout.
  * **Built-in channel types or dropped:** IFrame, Image, Error,
    Snooper, JspInvoker, Google Analytics (becomes skin/config).
  * **Soffit Connector:** becomes a pipeline component (MOD-Q9).
* **Reference conversions** (WS14 step 2): one of each kind, so both
  patterns get their effort measured.
  * Groups Manager → Thymeleaf admin page.
  * Favorites → web component + REST.
* **Alternatives considered:**
  * Web components everywhere. That gives one UI stack, but it means a
    heavier rewrite for form-heavy admin wizards, more client-side
    code to secure and a JS build for every tool.
  * Thymeleaf everywhere: widgets couldn't sit inside portal page regions
    without a server-fragment channel.
  * Keep JSP: works on Tomcat 11, but it's the legacy stack this work is
    retiring (111 JSPs).

### MOD-D20: web components are built with Vite+ and tested extensively with Vitest

* **Decision (project owner):** the WS14 web components (MOD-D18/D19)
  use **Vite+** as the build toolchain and get **extensive Vitest** test
  suites.
* **Versions (npm, 2026-10-10):**
  * `vite-plus` 1.1.0 (MIT, released 2026-10-07, CLI `vp`). It bundles
    Vite, Vitest, Oxlint, Oxfmt, Rolldown and tsdown.
  * Vite 8.3.4, Vitest 5.0.3 with `@vitest/browser` 5.0.3, Lit 3.3.3
    (BSD-3-Clause).
* **How it is wired in:**
  * One web-components workspace inside uPortal. The components then
    version with the REST APIs they call.
  * `vite-plus` is a pinned devDependency. It is not installed globally
    with `curl | bash`, to avoid the supply-chain risk and keep CI
    reproducible.
  * Node stays managed by the existing node-gradle plugin, not `vp env`,
    so there is one Node source of truth.
  * Gradle tasks call the npm scripts: `vp check` (format, lint,
    types), `vp test` and `vp build`. The built ES modules are packaged
    into the uPortal webapp (or a webjar) as static resources, served
    with cache-busting.
  * Renovate groups the Vite+ / Vite / Vitest updates.
* **Testing bar for every component:**
  * Vitest in **browser mode** (Playwright provider, Chromium) for real
    custom-element and shadow-DOM behaviour, plus fast unit tests for
    pure logic.
  * Coverage of rendering states (loading, empty, error, populated),
    user interactions and emitted events, attribute and property
    reflection, i18n keys, keyboard navigation and focus, an automated
    accessibility check (axe-core) per state, and REST calls with mocked
    responses (MSW or fetch stubs), including 401, 403, 500 and
    malformed payloads.
  * Coverage thresholds start at 90% lines/branches per package and are
    enforced in CI. Snapshot tests are only allowed for small, stable
    markup.
  * Playwright end-to-end specs are still the required check for the
    integrated behaviour in the portal.
* **Risks:**
  * Vite+ 1.x is days old. Pin exact versions, and keep the configuration
    portable (plain `vite.config.ts` + Vitest config) so it can fall back
    to plain Vite + Vitest if needed.
  * Browser-mode tests in this sandbox need the pre-installed Chromium
    linked under the revision Playwright expects; CI uses the
    Playwright-installed browsers.

* **Status (2026-10-10, WS14 foundation):** `uPortal-webcomponents`
  pins vite-plus 1.1.0, Vitest 5.0.3 (a Node unit project and a Chromium
  browser project), Lit 3.3.3 and bootstrap 6.0.0-alpha.1. `vp check`,
  `vp test` (90% of lines, branches, functions and statements in every file)
  and `vp build` run in Gradle's `check`, `test` and `jar`; the WAR serves
  the content-hashed modules from `/webcomponents/`. CI installs Chromium
  with `:uPortal-webcomponents:installChromium`.

### MOD-D21: Bootstrap stays the theme; target Bootstrap 6

* **Decision (project owner):** keep Bootstrap as uPortal's theme
  framework and aim for **Bootstrap 6**. Today uPortal bundles Bootstrap
  5.3.8 as a webjar, with the Respondr skin in SCSS plus a legacy
  `common.less`.
* **What Bootstrap 6 is today** (from the npm package, since getbootstrap
  and the GitHub API are blocked in the sandbox):
  * `6.0.0-alpha.1`, published 2026-10-08, MIT, npm dist-tag `next`.
    Peer deps: `@floating-ui/dom` (replaces Popper) and
    `vanilla-calendar-pro`.
  * The SCSS uses Sass modules only (`@use`/`@forward`, zero `@import`).
  * The CSS uses cascade layers: `colors, config, root, reboot, layout,
    content, forms, components, custom, helpers, utilities`. The
    `custom` layer is where skins belong.
  * The JS is TypeScript. New components: avatar, banner, chip,
    combobox, datepicker, drawer, menu, dialog, nav-overflow, otp-input,
    range, stepper, strength, toggler. There is no `dropdown.ts`,
    `modal.ts` or `offcanvas.ts` any more. They appear to be replaced by
    menu, dialog and drawer. That is inferred from file names and
    still to be confirmed against the v6 migration guide.
  * **Confirmed by Bootstrap's own migration skill** (`npx skills add
    twbs/bootstrap`, installed in `~/.claude/skills`: 11 markdown-only
    skills, reviewed before use):
    * Modal → **Dialog**: a native `<dialog>`. The `.modal-dialog` and
      `.modal-content` wrappers are removed, and `.dialog-open` goes on
      `<html>`.
    * Offcanvas → **Drawer**; Dropdown → **Menu** (no `.dropdown`
      wrapper or `.dropdown-toggle`).
    * `.btn-primary` → `btn-solid theme-primary`, and every variant moves
      to theme tokens.
    * Responsive prefixes become `md:`-style (`.navbar-expand-md` →
      `.md:navbar-expand`).
    * Utility, font-size and spacer-scale renames; form validation
      becomes `data-bs-validate`.
    * The JS is ESM-only, Popper is replaced by Floating UI, and
      lifecycle methods return promises.
    * Sass uses `@use` only, with renamed variables and functions.
  * Every WS14 and skin-migration task should use the
    `bootstrap-v5-v6-migration`, `bootstrap-vite`,
    `bootstrap-color-system` and `bootstrap-component` skills.
* **Impact on uPortal** (file counts in uPortal-webapp and
  uPortal-portlets):
  * `dropdown` 20, `modal` 15, `offcanvas` 7, `data-bs-` 15, `tooltip` 3,
    `btn-` 48, `form-control` 22.
  * 4 skin files use `@import`.
  * Adopters' custom skins (SCSS `@import`) and uPortal-start's
    `skinGenerate`/SCSS compile (#709) need to move to Sass modules.
* **Plan:**
  1. New UI work (WS14 Thymeleaf pages and web components) is written
     against Bootstrap 6 markup from day one, with the alpha version
     pinned and upgraded deliberately through beta to GA.
  2. Port the Respondr skin to Sass modules and put skin overrides in
     the `custom` layer.
  3. Migrate existing JSP/XSL markup (menu, dialog, drawer, Floating UI)
     as WS14 replaces those views. Whatever remains gets a focused
     markup PR.
  4. Bundled external portlets still emit Bootstrap 3/5 markup. Add a
     small compatibility stylesheet in its own cascade layer, so they
     keep rendering until each one is ported (MOD-D13). The visual smoke
     specs check this.
  5. Web components: the Bootstrap CSS doesn't reach into shadow DOM.
     Either style through Bootstrap 6 CSS custom properties (design
     tokens do cross shadow roots) plus a shared constructable
     stylesheet, or render simple components in light DOM. The choice is
     made in the WS14 foundation and covered by the Vitest browser-mode
     tests. **Chosen (2026-10-10):** shadow DOM, with one shared
     constructable stylesheet compiled from the Bootstrap 6 Sass modules
     the components use (tokens unprefixed on `:host`,
     `color-scheme: inherit`), so components keep their markup private
     and follow the page's light or dark mode. axe checks every state in
     the browser tests; it found the alpha's contrast failures
     (MOD-I132).
* **Status (2026-10-11):** the shared Bootstrap 6 sheet now also has the
  pagination, table, label and form-control parts, for Popular Portlets.
  Bootstrap 6 has no `.form-select`, and `select.form-control` takes its place.
* **Risks:**
  * Alpha API churn. Mitigation: pin it, and keep Bootstrap-specific
    markup in Thymeleaf fragments and a small set of Lit base classes.
  * Bootstrap 6 GA may land after uPortal 6.0. Fallback: release 6.0 on
    Bootstrap 5.3.x with the same layer structure and move to 6 in
    6.x, decided at the RC.

### MOD-D22: prefer the Spring approach for every migration

* **Decision (project owner):** when something has to be migrated or
  replaced, prefer the Spring-ecosystem way wherever one exists. Applied
  across the plan:

| Area | Today | Spring approach |
| --- | --- | --- |
| Caching | Ehcache 2 API in 58 files, spring-modules-cache | Spring Cache abstraction (`@Cacheable`/`@CacheEvict`, `CacheManager`); providers Caffeine, JCache/Infinispan, Redis/Valkey (MOD-Q10) |
| Sessions | Spring Session 1.3 (Redis, Jedis) | Spring Session 4.x (Lettuce; works with Valkey) |
| Built-in portlets | Spring Portlet MVC + Web Flow portlet integration classes | Spring MVC: `@Controller` + Thymeleaf for pages, `@RestController` + Jackson for the web components' APIs (MOD-D18/D19) |
| REST auth for web components | Custom JWT (jjwt, 10 files) | Spring Security OAuth2 Resource Server (JWT); uPortal keeps issuing the tokens |
| CAS integration | Jasig/Apereo cas-client filters wired by hand (8 files) | Evaluate `spring-security-cas` (re-added in Security 6.1) in P7 |
| HTTP clients | `RestTemplate` (9 files) on HttpClient 4 | `RestClient` (Spring 6.1+) on the JDK or HttpClient 5 request factory |
| Password hashing | jasypt MD5 / SHA-256 | Spring Security Crypto `DelegatingPasswordEncoder`, upgraded on login |
| Dates | Joda-Time (86 files) | `java.time`, using Spring's JSR-310 formatting and conversion |
| JSON views | json-lib (`net.sf.json`, 3 files) | Spring MVC's Jackson message converters |
| Scheduling | `task:scheduled` (2), `@Scheduled` (1), custom cluster lock | Spring `@Scheduled`/`TaskScheduler`; Spring Integration `JdbcLockRegistry` for cluster-wide locks, if replacing `JpaClusterLockDao` pays off |
| Retry | none or ad hoc | Spring Framework 7 core retry (`@Retryable`) |
| Bulk DB copy (MOD-D11) | plain JDBC (v0 in progress) | Spring JDBC; Spring Batch chunk steps (restartable, partitioned) in a standalone Spring Boot CLI module |
| Schema migrations after 6.0 | hbm2ddl | Open between Flyway and Liquibase (MOD-Q4). Spring Boot integrates both. |
| Configuration | 113 XML contexts | New code uses `@Configuration` Java config; XML is converted opportunistically when a context is touched |
| Observability | custom JMX/statistics (3 files) | Micrometer, via Spring's observation API (6.x) |
| Packaging | external Tomcat WAR | Spring Boot's `SpringBootServletInitializer` WAR stays the 6.x candidate (MOD-Q1). Only `uPortal-db-migrate` imports Boot's BOM (MOD-D28). The portal's versions stay in `gradle.properties`. |

* **Limits:** where no Spring option exists, or the Spring option would
  break adopters without benefit (the portlet container for third-party
  portlets, the XSLT rendering pipeline, Pluto), the plan keeps the
  existing choice. Each such exception is recorded in this log.

### MOD-D23: multi-database CI is live (MOD-D16 delivered)

* **uPortal `db-integration.yml`:** the JPA/DAO tests read their database
  from system properties, defaulting to HSQLDB. As first delivered, they ran
  on PostgreSQL 16, MariaDB 11.4 and MySQL 8.4 on every push, and on SQL
  Server 2022 and Oracle Free 23 nightly. All five were proven on push once
  (run 38061900729).
  * **Now (MOD-D25, MOD-D30):** since `36c35ecdf`, every push and pull
    request runs PostgreSQL 18, MySQL 9.7, MariaDB 12.3, SQL Server 2025
    and Oracle Free 23.26. The canaries run nightly. On workstream
    branches a push runs PostgreSQL 18 and MariaDB 12.3 (`4900748e3`), and
    a manual run adds MySQL, SQL Server and Oracle (`d66bdc5a5`). Run
    38095664677 (`247d76b87`) passed on all five.
* **uPortal-start `integration.yml`:** builds uPortal from source on the
  same branch, then runs the full Playwright suite (124 tests) on HSQLDB,
  PostgreSQL 16 and MariaDB 11.4, and the DB harness on all 7 vendors.
  Run 38061904020 was green apart from the known drift MOD-I83/I84.
  * **Now (MOD-D25, MOD-D30):** since uPortal-start `a811eb3b8`, the
    harness runs on HSQLDB 2.7.4, PostgreSQL 18, MySQL 9.7, MariaDB 12.3,
    SQL Server 2025 and Oracle Free 23.26. The Playwright suite runs on
    HSQLDB, PostgreSQL 18 and MariaDB 12.3 on every push, and on MySQL 9.7
    in the nightly set. Workstream branches run Playwright on HSQLDB, the
    harness and Playwright on PostgreSQL 18, and one G-migrate pair
    (`3ebe1a52b`). Run 38087260340 (`5cce78e2d`) passed every one of
    these jobs.
* **Harness changes:**
  * The baseline is picked from `uPortalVersion`.
  * Drift and failed specs are published as GitHub annotations, because
    artifacts can't be downloaded through the sandbox proxy.
  * CAS now follows the harness database.
* **Real bugs found:** MOD-I82 and MOD-I97 (product), MOD-I86 (tests).
* **Done (2026-10-10):** MOD-I82 to I85 are fixed, and both repos'
  workflows no longer carry `continue-on-error`, `known-failure` or
  `known-drift`. With drift fixed, the harness on Oracle ran the API
  specs for the first time and found MOD-I97.
  * uPortal 7ded7362d: CI 38072747439 and DB integration 38072747374
    (PostgreSQL, MariaDB, MySQL) green. DB integration 38068704548 green on
    all 5 vendors including SQL Server and Oracle (a one-off push run,
    then back to nightly). Workflow lint 38069434024 green.
  * uPortal-start 3695a5927: CI 38072765561 green. Integration
    38072765632 green: harness on all 7 vendors, Playwright on HSQLDB,
    PostgreSQL and MariaDB.

### MOD-D24: database version matrix covers oldest supported, latest, and next

* **Finding (2026-10-10, Docker Hub tags):** the current CI matrix tests
  mid-range versions (PostgreSQL 16, MySQL 8.4, MariaDB 10.6/11.4, SQL
  Server 2022, Oracle Free 23, HSQLDB 2.7.4). Latest available:
  * **PostgreSQL:** 18.6 is the latest major, and 19 is at beta4.
  * **MySQL:** 9.7.2 is the current LTS (`lts` tag). 26.7 is the newest
    Innovation release (`latest`), and 8.4 is the previous LTS.
  * **MariaDB:** 12.3 is the current LTS (`lts`), and 13.0 is the newest
    release (`latest`). 10.6 has passed its July 2026 end of life.
  * **SQL Server:** 2025.
  * **Oracle:** Free 23.26. Oracle 19c, still common on campuses, has no
    free container image; `gvenzl/oracle-xe:21` is the closest available
    older version.
* **Policy:**
  * Test the **oldest supported** and the **latest stable** version of
    each vendor on every push. Run a **next/canary** version nightly as
    allowed-to-fail, so breakage shows early.
  * The oldest supported version follows Hibernate 7.4's floors
    (MOD-I43) and vendor end-of-life dates.
* **Proposed matrix:**

| Vendor | Every push | Nightly |
| --- | --- | --- |
| PostgreSQL | 15 (floor; 14 reaches EOL Nov 2026) and 18 | 19 beta/rc (canary) |
| MySQL | 8.4 (LTS floor) and 9.7 (LTS) | 26.x Innovation (canary) |
| MariaDB | 10.11 (LTS floor, 10.6 is EOL) and 12.3 (LTS) | 13.x (canary) |
| SQL Server | 2022 and 2025 | 2019 |
| Oracle | Free 23.26 | XE 21 (older-engine proxy) |
| HSQLDB | 2.7.4 | none |

* **How it gets wired in:**
  * The `tests/db/vendors/*.properties` files gain the new versions.
  * Each new version needs a 5.17 baseline, recorded through the
    CI-annotation flow (MOD-D23).
  * uPortal `db-integration` and uPortal-start `integration` adopt the
    matrix, with images pinned by digest and Renovate updating digests
    and tags.
  * The copy-tool check (G-migrate) uses *old* versions as sources and
    *latest* versions as targets, which mirrors how adopters upgrade.
* **Status:** this is a proposed default; the project owner can adjust
  the floors. It gets implemented after the MOD-I82 to I84 fixes land, so
  workflow edits don't collide. (Those fixes were merged on 2026-10-10;
  MOD-D25 is now being implemented.)

### MOD-D25: old database versions are migration sources only, and 6.0 runs on and is tuned for current servers

This supersedes MOD-D24's runtime matrix.

* **Decision (project owner):** older database versions stay in the plan
  only as **upgrade sources** for the copy tool (MOD-D11). 5.17
  deployments run on them, and the tool reads from them. uPortal 6.0
  itself targets and is tuned for **current servers**.
* **6.0 runtime matrix, every push:**

| Vendor | Version |
| --- | --- |
| PostgreSQL | 18 |
| MySQL | 9.7 LTS |
| MariaDB | 12.3 LTS |
| SQL Server | 2025 |
| Oracle | Free 23.26 |
| HSQLDB | 2.7.4 (dev only) |

  The versions in this table are also the minimum versions for 6.0. The
  project owner ruled on 2026-10-11 that they are hard minimums: 6.0
  supports these versions and newer, and its code and configuration carry
  no workarounds for older servers. uPortal does not check the server
  version or refuse to start on an older server; it only needs a newer
  server where it uses a feature the older one lacks. Nightly canaries,
  allowed to fail, run PostgreSQL 19, MySQL 26.x Innovation and MariaDB
  13.x.
* **Migration-source matrix** (G-migrate and the 5.17 harness baselines;
  5.17 code on old servers):

| Vendor | Source versions |
| --- | --- |
| PostgreSQL | 14, 15, 16 |
| MySQL | 8.0, 8.4 |
| MariaDB | 10.6, 10.11, 11.4 |
| SQL Server | 2019, 2022 |
| Oracle | XE 21, as the closest freely available stand-in for 19c |
| HSQLDB | 2.5.1, 2.7.4 |

  The copy tool must read every source and write every 6.0 target,
  including cross-vendor pairs.
* **Tune 6.0 for newer servers** (feeds the 6.0 schema, MOD-D11, and the
  index audit, MOD-D12):
  * **Native JSON columns** for JSON stored as text today (the
    `JacksonType` and `StatisticsJacksonType` columns, event data):
    `jsonb` on PostgreSQL, `JSON` on MySQL/MariaDB, native `json` on SQL
    Server 2025, `JSON` on Oracle 23. Mapped with Hibernate 7
    `@JdbcTypeCode(SqlTypes.JSON)`.
  * **Native types:** the `BOOLEAN` type on Oracle 23; UUID types (`uuid`,
    `uniqueidentifier`, MariaDB `UUID`) for UUID ids, considering
    time-ordered UUIDv7 (PostgreSQL 18 `uuidv7()`) for insert locality.
  * **Time partitioning for high-volume analytics:** raw events, event
    sessions and aggregations partitioned by time, using PostgreSQL
    declarative partitioning, MySQL/MariaDB RANGE partitions, SQL Server
    partition schemes and Oracle interval partitioning. Purges then
    become partition drops instead of multi-million-row DELETEs. Test it
    at large-campus scale (MOD-Q7).
  * **Index features:** BRIN on PostgreSQL time columns, covering
    `INCLUDE` indexes on PostgreSQL and SQL Server, decided by the
    G-index workload.
  * **Exact dialect versions:** configure Hibernate with the actual
    server version, so it uses the newest SQL features.
* **Correction to MOD-D11:** "native sequences everywhere" can't apply
  to **MySQL**, which still has no sequences (MariaDB has them since
  10.3). The 6.0 MySQL options are:
  * (a) keep table-backed generators, which the copy tool positions;
  * (b) IDENTITY / AUTO_INCREMENT, which turns off Hibernate's JDBC batch
    inserts for those entities.

  The default is (a), unless the G-index workload shows (b) is better.
  Every other vendor uses native sequences.
* **Status (2026-10-10): implemented.**
  * **uPortal `db-integration.yml`:** every push runs the JPA/DAO tests on
    the five runtime servers. SQL Server and Oracle moved from nightly to
    push: in run 38068704548 their jobs took 5.3 and 5.4 min, against
    4.3 to 5.0 for the others. A separate nightly `db-canary` job runs
    PostgreSQL 19 beta 4, MySQL 26.7 and MariaDB 13.0; its entries set
    `canary: true`, which drives `continue-on-error`, the only
    allowed failure in either repo. Failed tests are annotated with their
    causes.
  * **uPortal-start `integration.yml`:** every push runs the harness
    (G-db) on the six runtime databases and the whole Playwright suite on
    PostgreSQL 18 and MariaDB 12.3. The nightly set adds MySQL 9.7. The
    scripts start every database from the digest-pinned image in its
    vendor file.
    * New 5.17 baselines for PostgreSQL 18.6, MySQL 9.7.2, MariaDB 12.3.3
      and SQL Server 2025, recorded in run 38078328181 and read back from
      its annotations (`tests/db/fetch-ci-snapshot.sh`). Apart from their
      version headers they equal the PostgreSQL 16, MySQL 8.4, MariaDB
      11.4 and SQL Server 2022 baselines line for line, dialects included.
      Oracle Free 23.26 and HSQLDB 2.7.4 already had baselines. No
      existing baseline changed, and `dbRecord` now refuses to overwrite
      one without `-PdbRecordReplace`.
  * **G-migrate** (`tests/db/migrate.sh`): the source is filled by
    `dataInit` on the old server plus a portal start with the API specs;
    the target gets `dataInit` and one start (MOD-I92); then `dbMigrate`
    must exit 0. `tests/db/migrate/matrix.json` and `matrix.mjs` give the
    sets and check their coverage.
    * Per push: 6 pairs, including MariaDB 11.4 → PostgreSQL 18 and
      PostgreSQL 15 → MariaDB 12.3 across vendors. The PostgreSQL 15 and
      PostgreSQL 16 pairs run the whole Playwright suite on the migrated
      portal afterwards.
    * Nightly: all 36 (source vendor, target vendor) pairs. The set reads
      each source version at least once and writes each target 6 times.
      Playwright runs once per target.
    * HSQLDB 2.5.1 stays a source: uPortal-start's own server runs at
      2.5.1 on its own port and directory (new `-PhsqlDatabaseDir`), so
      2.7.4 never opens, and converts, the files.
  * **Images:** every database image is pinned as `name:tag@sha256`, with
    tags and digests checked against Docker Hub and MCR
    (`gvenzl/oracle-free:23.26` does not exist; `23.26.3-slim` does).
    Renovate regex managers read workflow matrices and vendor files and
    update digests and patch versions only. A dry run proposed exactly
    those.
  * **Proof:**
    * uPortal 4c28a7f81: DB integration 38084658479 (5 runtime servers)
      and CI 38084658432 green. The canaries ran on push once in run
      38075062798 (07d9d4edb, reverted by f26640b22), and all three
      passed.
    * uPortal-start: the nightly sets ran on push once (7d81ec108,
      reverted by 4b667662d). Run 38080746162 passed 29 of 36 pairs; the
      7 failures were MOD-I108 and MOD-I109. After the fixes, run
      38087260340 (5cce78e2d) passed all 48 jobs. Besides the 36 pairs,
      it ran the harness on the six runtime databases and Playwright on
      HSQLDB, PostgreSQL 18, MariaDB 12.3 and MySQL 9.7. It also ran Playwright on a
      migrated SQL Server 2025 portal (from Oracle XE 21). The earlier
      run 38084663287 failed only that pair: its SQL Server container
      never accepted a connection. SQL Server is now capped at 2 GB
      (94287fd6b), and a failed pair reports its containers.
    * Final heads, per push, with the light profile in place (uPortal
      4900748e3, uPortal-start 11034ab9c): uPortal 247d76b87 DB
      integration 38095664677 (all 5 runtime servers) and CI 38095664624
      green. uPortal-start dde58e2c0 Integration 38095696299 and CI
      38095696297 green.
  * **Light CI profile (MOD-D30):** a workstream branch runs DB
    integration on PostgreSQL 18 and MariaDB 12.3 (2 jobs instead of 5).
    The integration workflow's light profile runs 6 jobs instead of 17:
    the build, Playwright on HSQLDB, the harness and Playwright on
    PostgreSQL 18, and MariaDB 11.4 → PostgreSQL 18. The integration
    branch keeps 5 and 17 jobs, and the schedule 8 and 48.
  * **Findings:** MOD-I99 (product), MOD-I107 (uPortal-start), MOD-I108
    (uPortal-start), MOD-I109 (copy tool), MOD-I110 (CI tooling). One
    flake: 8 Announcements specs failed once on PostgreSQL 16 (run
    38078328181, attempt 1) and passed on the re-run.
  * **Left:** both sides of a G-migrate pair are created by the same
    build (5.17.10-SNAPSHOT from source). Once the 6.0 schema is merged, the
    source must come from a released 5.17 (`-PuPortalVersion`). SQL
    Server and Oracle have no full Playwright run in CI.

### MOD-D26: P3-1 merged, with Spring Portlet MVC and Web Flow's portlet integration classes vendored

* **Commits** (merge `a5c27ca20`):
  * `65a04d252`: uPortal classes move out of Spring's packages (MOD-I12).
  * `1eaa654d4`: new module `uPortal-spring-portlet`. Vendored code
    lives in `src/vendored` (Spring formatting and Apache-2.0 headers,
    skipped by Spotless), and uPortal's two DispatcherPortlet subclasses
    in `src/main`.
  * `272a417fb`: switches uPortal's portlets to it. BREAKING:
    `org.springframework.` → `org.apereo.portal.spring.`; the full rename
    map is in `docs/UPGRADING-6.0.md`, and NOTICE credits Spring.
* **What was vendored:** 69 classes, 12,462 lines, chosen with jdeps
  starting from every reference in uPortal's code and config. All 81
  vendored files are byte-identical to upstream apart from the package
  rename, and 15 unused Portlet MVC classes were left out.
  spring-webmvc-portlet is gone from all 244 resolved configurations;
  the WAR differs by exactly one jar.
* **Required checks:**
  * Unit tests: 1069 run, 0 failed (10 new).
  * Playwright, two runs: only the two sandbox web-proxy tests fail. All
    admin specs (Web Flow), portlet-options and per-role specs pass.
  * All 37 portlets render as before (`render_portlets.py`), and the
    logs are unchanged.
* **P4-1 (Spring 5.3) needs, measured by compiling against Spring 5.3.34
  / Web Flow 2.5.1 (37 errors in 6 files):**
  * vendor `ViewRendererServlet` and update `web.xml`;
  * vendor `HandlerMethodInvoker`, `HandlerMethodResolver` and
    `HandlerMethodInvocationException` (they compile unchanged on 5.3);
  * port PortletMVC4Spring's `PortletSessionScope` to replace
    `SCOPE_GLOBAL_SESSION`;
  * delete the dead servlet `AnnotationMethodHandlerAdapter` bean in
    `PortletMarketplace-portlet.xml`.
* **PortletMVC4Spring 5.3.4 vs the vendored code:** 59 of 69 classes
  differ only in formatting. The changes to port are `PortletSessionScope`,
  null-filtering of action-exception parameters, config-location
  tokenizing, and resource-phase multipart. Portlet 3.0-only APIs appear
  only in classes we didn't vendor.

### MOD-D27: static analysis as a required CI check (SpotBugs ratchet plus NullAway with JSpecify)

* **Finding:**
  * SpotBugs 4.10.4 with FindSecBugs is applied, but only runs when a
    SpotBugs task is named explicitly, with `ignoreFailures = true`, so it
    never runs in CI.
  * Error Prone 2.50.0 runs on every compile, but 4 ERROR-level checks
    are disabled, including the security check `BanJNDI`.
  * The codebase has **zero** nullness annotations.
* **Decision:**
  1. **SpotBugs + FindSecBugs as a ratchet in CI.** Record today's
     findings in a committed exclusion baseline, and fail the build on
     any NEW finding. Focus on CORRECTNESS (including the `NP_*` null
     dereference detectors), MT_CORRECTNESS (inconsistent
     synchronisation, races), SECURITY, and BAD_PRACTICE. Shrink the
     baseline over time.
  2. **NullAway (an Error Prone plugin) in JSpecify mode** with
     `OnlyNullMarked`. Adopt it package by package with `@NullMarked`,
     starting with new code (WS14 controllers and services, the copy
     tool) and the hot spots (portlet rendering, preferences, security,
     where MOD-I90's NPE lives). This follows Spring Framework 7, which
     annotates its APIs with JSpecify and builds with NullAway (MOD-D22),
     so after P7 NullAway also understands Spring's nullness.
  3. **Re-enable the disabled Error Prone checks** (`BanJNDI`,
     `BoxedPrimitiveEquality`, `DoubleBraceInitialization`,
     `SelfAssertion`) by fixing their findings, one PR per check.
* **Limits:** static analysis flags missing null checks, but races like
  MOD-I90 (a session swapped mid-render) also need a code fix and a
  regression test. SpotBugs' MT detectors catch only some of them.
* **Status (2026-10-10): delivered.** Commits 5615a7973 (SpotBugs ratchet),
  e395b1ccb (NullAway), 75af908cf (MOD-I90 fix), ff040e1c1
  (DoubleBraceInitialization re-enabled) and c68c04509 (baselines). CI
  run 38081288582 is green on all 8 legs plus the new Static analysis
  job.
  * SpotBugs runs in its own JDK 21 CI job (effort max, threshold
    medium), not in `build`. Per-module baselines are in
    `config/spotbugs/baseline/`, and `./gradlew spotbugsBaseline`
    regenerates them. A planted null dereference failed the check.
  * The baseline holds 1051 findings in 43 modules: STYLE 421, SECURITY
    306, BAD_PRACTICE 211, MT_CORRECTNESS 52, PERFORMANCE 43, CORRECTNESS
    18. About 40 of the 48 NP-on-return-value findings are callers of the
    now-`@Nullable` `getPerson()`/`getSecurityContext()`.
  * NullAway runs at error level on `@NullMarked` packages:
    `security.oauth`, `security.firewall` and `portlet.session`.
  * MOD-I90 is fixed: a missing person or security context counts as a
    guest. The same null was also fixed in
    `CachedPasswordUserInfoService`.
  * Still disabled: BanJNDI (5 findings), SelfAssertion (3, tests) and
    BoxedPrimitiveEquality (2).
  * Real bugs found by SpotBugs: MOD-I100 to I103 and MOD-I105, each
    fixed with a regression test that fails on the old code (8dcab6efd,
    20b19918e, 5ab591f23, 044e79e0f, f90ebac9e). d9774f02d drops their 7
    entries from the baselines.
  * Fixed in the same series, but not found by SpotBugs: MOD-I104, found
    while tracing MOD-I90 (f213dcad5; its test also passes on the old
    code, since the race can't be forced), and MOD-I106, a build fix for
    the webapp's npm install (bddc4f315).

### MOD-D28: copy tool v0 merged (MOD-D11, 5.17 → 5.17 across vendors)

* **Commits:** uPortal `1abff7f67` (merge `179e11d66`) and uPortal-start
  `55a2df094` (merge `d3b98e271`). The uPortal-start branch's CAS fix
  (`596bab6fb`) duplicated `36ed5df1d`, so the integration version was
  kept.
* **Shape (MOD-D22):** subproject `uPortal-db-migrate`, a standalone
  Spring Boot 4.1.1 CLI.
  * Spring Batch 6.0.5 + Spring JDBC + HikariCP; Boot's BOM; compiled
    with `--release 21` (this module only); no other uPortal modules on
    its classpath.
  * Executable jar, with JDBC drivers supplied externally.
  * uPortal-start runs it as `./gradlew dbMigrate -PsourceDbVendor=…
    -PtargetDbVendor=…`.
  * Exit codes: 0 = copied and verified, 1 = failure, 2 = plan error
    (nothing written).
* **Design:**
  * Tables load in FK-level order with constraints left on; each level
    runs one partitioned Batch step per table, in parallel. Cycles are
    broken at nullable FKs with a second pass.
  * Batch metadata is kept in in-memory H2.
  * PostgreSQL OID LOBs are read with `lo_get` and written with
    `lo_from_bytea`. Missing LOBs fail the plan before anything is
    written.
  * Timestamps are copied without a zone, so they don't shift (tested
    in America/New_York).
  * The 49 generators come from a build-time annotation scan. Each is
    positioned at ≥ max(id) + allocationSize, and never below its source
    value. MySQL uses one-row tables.
  * Verification: per-table row count plus an order-independent hash of
    normalised rows, then the generator positions.
* **Demos (old → new):**
  * PostgreSQL 16 → MariaDB 12.3 (14,619 rows), MariaDB 11.4 → PostgreSQL
    18 (24,853 rows) and HSQLDB 2.7.4 → PostgreSQL 18 (9,765 rows), all
    verified.
  * Playwright on the migrated portals: everything passed except the
    sandbox web-proxy tests (2 flaky).
  * Post-migration inserts didn't hit duplicate keys.
  * 1M raw events with LOBs, PostgreSQL 16 → MariaDB 12.3: 148.5 s
    (6.7k rows/s), verified in 108.6 s, peak heap 94 MiB.
  * 61 unit tests, plus a Testcontainers chain PostgreSQL 16 → MariaDB
    12.3 → PostgreSQL 18 that includes a partitioned events table.
* **Ready for 6.0:** the rule SPI can express JSON text → native JSON,
  UUID types, zone-aware timestamps and renamed sequences, and loading a
  partitioned PostgreSQL table through its parent works.
* **Still to build:** a 6.0 generator profile; table split/merge;
  restartable runs.
* **SQL Server and Oracle in CI (G-migrate matrix, MOD-D25): done in
  uPortal-start.**
  * uPortal-start's `integration.yml` runs G-migrate from
    `tests/db/migrate/matrix.json` (`8d4c48acb`). Each integration-branch
    push runs six pairs, SQL Server 2022 → 2025 and Oracle XE 21 → Free
    23.26 among them. The nightly set has 36 pairs.
  * Run 38087260340 (uPortal-start `5cce78e2d`, the nightly set run once
    on push) passed all 36 pairs, including every pair with SQL Server or
    Oracle as source or target.
  * uPortal's `db-migrate.yml` (`6a014728c`) runs the copy tool's
    integration tests on PostgreSQL, MariaDB and MySQL on every push. Its
    SQL Server and Oracle legs run nightly or by hand and haven't run yet
    (run 38097849648 ran the push job only).
  * Neither fork's `master` has these workflows. GitHub fires a schedule
    only on the default branch, and none has run. The nightly sets run on
    the fork only when started by hand.
* **CI after the merges:** uPortal CI and DB integration are green from
  7ded7362d (runs 38072747439, 38072747374), and uPortal-start CI and
  Integration from 3695a5927 (38072765561, 38072765632). The merge commit
  itself (179e11d66) had one red Windows job, caused by a Maven Central
  403 (MOD-I98), not by the merge.

### MOD-D29: test expansion that uses 5.17's output as the reference

* **Status (2026-10-10):** approved by the project owner ("Please do
  improve the tests to give more confidence in the migration"). Items 3,
  7 and the ArchUnit rules (uPortal), and items 1, 4, 5 and the
  flaky-test check (uPortal-start) are in progress. Items 2 and 8, the
  copy-tool strength tests, and item 9 with the non-functional checks
  are queued until memory frees up. P2 (Hibernate 5.2) merges only after
  it passes the new goldens unchanged, or with each difference explained
  here.
* **Status (2026-10-11, WS14, item 6 for Favorites):** uPortal-start's
  `favorites.spec.ts` keeps its four Options-menu tests unchanged against
  the Web Component channel and adds four for `<up-favorites>` (list,
  unfavorite, reorder, refusal without the CSRF token).
  `web-component-channel.spec.ts` adds three for the channel type. Two
  local full-suite runs on HSQLDB against uPortal from source passed all
  of them; the only failures were the two sandbox web-proxy tests and,
  in one run, a portlet-list test that times out under load. On
  uPortal-start 4bddf44a7 both CI 38104005708 and Integration 38104005683
  are green. Integration ran Playwright on HSQLDB, PostgreSQL 18 and
  MariaDB 12.3, and the harness on all six 6.0 servers found both
  intended changes. uPortal CI 38104024957 failed on Windows
  because `.ts` files were checked out with CRLF. CI 38105388258 on the
  fix (95493437d) passed on every leg.
* **Status of items 3 and 7 and the ArchUnit rules (2026-10-11):**
  delivered as uPortal-webapp tests. They run on HSQLDB in
  `:uPortal-webapp:test` and on the five databases of `db-integration.yml`
  through the existing `uportal.test.db.*` properties. The goldens under
  `uPortal-webapp/src/test/resources/golden` were recorded on HSQLDB with
  Hibernate 4.3, Spring 4.3 and Joda-Time. `-Duportal.golden.record=true`
  re-records them, on HSQLDB only. CI never sets it. Each vendor
  difference is a documented rule in `VendorDifferences` or
  `QueryVendorDifferences` that maps the expected value. No test is
  skipped on any database.
  * `EntityRoundTripGoldenTest`: all 61 entity types of the PortalDb,
    RawEventsDb and AggrEventsDb units (2 abstract types are covered by
    their subclasses), in four variants: maximum lengths and values,
    minimum and empty values, multi-byte text with emoji and RTL scripts
    plus instants on the New York and London DST changes, and nulls. Each
    entity is reloaded in a new persistence context and compared field by
    field. The stored rows are compared too, so the `_` prefix that 4.3
    writes on the keys of the four `nullSafeString` maps is pinned
    (MOD-I120). Runs under the JVM zones UTC and America/New_York.
    Lists with `@OrderBy` are compared as multisets and checked to be
    sorted by their key: 5.17 never guaranteed the order among equal
    keys, and Oracle returns those ties in another order than HSQLDB.
  * `DaoQueryGoldenTest`: 45 query methods of 17 PortalDb DAOs on a fixed
    data set, with result classes, order, nulls and exceptions.
  * `AggregationGoldenTest`: 462 generated raw events of 10 types (logins
    and logouts, page renders, portlet executions, layout changes) in
    three windows that cross the November and March DST changes, a month
    end and the year end, aggregated by the real pipeline in both zones.
    It pins the 1,153 (UTC) and 1,131 (New York) aggregation, event
    session and status rows, and the read-side DAO queries.
  * `MigrationFreezeRulesTest` (ArchUnit 1.5.1, test scope of
    uPortal-webapp): a new use of `org.joda.time`, Ehcache 2, the legacy
    Hibernate `Criteria` API, or `org.hibernate.Session` outside the DAO
    packages fails. The existing uses are frozen in
    `src/test/resources/archunit_store`. The store also holds 12 uses
    that the java.time and Spring cache batches added: 11 in
    `JodaTimeConversions`, which converts between Joda-Time and java.time
    until the JPA mappings move (P2, P3-5), and 1 in the Ehcache 2
    listener `InsufficientSizeCacheEventListener`, which goes with JCache
    (P4-3).
  * Findings: MOD-I140 to MOD-I144.
  * Runs: the manual DB integration run 38109522840 on the topic branch
    `ccr-43ba0785-hzlrj5-goldens` passed on all five databases and the
    three canaries, and CI 38109515481 passed. The earlier topic runs
    38097192745, 38103140369 and 38107283955 found the MySQL bag order,
    the SQL Server best-fit characters in values, keys and ids, and the
    Oracle findings above. The commits handle each of them.
  * All goldens passed unchanged after the java.time and Spring cache
    batches; only the aggregation test context needed a Spring
    CacheManager.

* **Status (2026-10-11, copy-tool strength tests):** in
  `uPortal-db-migrate`, described in
  `docs/developer/other/DB_COPY_TOOL_TESTING.md`. jqwik properties check
  the load order, the conversions against the verification, the table
  hash and the generator formula. Integration tests copy random values of
  every column type between each pair of PostgreSQL 18, MariaDB 12.3 and
  MySQL 9.7 (SQL Server 2025 and Oracle Free 23.26 nightly), take ids
  from the copied generators, and inject faults: lost target, corrupted
  rows, plans the target can't take. They found MOD-I160 to MOD-I164 and
  MOD-I166, MOD-I167 and MOD-I169, each fixed with a test that failed
  before the fix, and MOD-I109 on their own. MOD-I165 and MOD-I168 record
  limits of the tests. PIT, opt-in and nightly: the copy tool's core went
  from 55 % to 65 % of mutants killed (94 % of the mutants some test
  covers), and `JpaClusterLockDao` from 58 % to 75 %. The owner kept jqwik
  after its notice to AI agents. Its output is filtered (MOD-I179). CI: DB
  copy tool push runs 38097849648 and 38106385464; every job, the SQL
  Server and Oracle legs and PIT included, passed in run 38111315873
  (8a214d35a), started by hand.
* **Status (2026-10-11, items 1, 4, 5 and the flaky-test check):**
  landed on uPortal-start (f7a29cdf1 to 42a0b1c15), described in
  `docs/migration-equivalence.md`. `tests/db/equivalence.sh` has uPortal
  5.17.9 write the quickstart data and a fixture of edge values, and
  requires the build under test to export exactly what 5.17.9 exported,
  in place or after a `dbMigrate` copy. The reference was recorded by
  5.17.9 on HSQLDB; MySQL and MariaDB differ from it by a recorded diff
  (whole seconds, MOD-I95). A seeded MOD-I120 fault (`'_' || ATTR_NAME`
  in `UP_SS_USER_PREF_LAY_ATTR_VAL`) changed 12 of 271 exported files.
  `pluto-testsuite.spec.ts` runs the 42 tests of Apache Pluto's test
  suite by name, and `portlet-modes.spec.ts` renders the 94 bundled
  portlets in every declared mode and both window states. CI fails on
  flaky tests, and `global-setup.ts` logs each test user in once.
  Findings: MOD-I150 to MOD-I159; four fixed in uPortal (d20564691,
  104858ea0, b1c8ec22e, 3e4977d18), and MOD-I78 and MOD-I91 fixed in
  uPortal-start. Runs: on the topic branch at the merged state,
  Integration 38116679825 (Playwright on HSQLDB and PostgreSQL 18 with
  local login, and the CAS job) and Migration equivalence 38116679805
  (in place on PostgreSQL 18, MariaDB 11.4 copied to PostgreSQL 18)
  passed; the first integration push runs are 38119552320 and
  38119552312, and the nightly set runs as 38119582423.
* **Gap:** today's suites (124 Playwright tests in 24 specs, 244 unit-test
  classes, the DB harness on 7 databases, copy-tool row hashes) show the
  portal still works on the paths they click. They miss silent changes:
  values read back differently, JSON formats, routes, security defaults,
  analytics buckets. The harness compares schema and row counts, not
  values. Playwright runs Chromium only, at one desktop size, with
  `retries: 2`, which hides flaky tests.
* **Principle:** record what 5.17 does once, keep the recordings as
  fixtures, and require every phase to match them or log why a
  difference is intended.
* **Old vs new:**
  1. *Data equivalence:* `dataExport` of every entity type on 5.17,
     upgrade (in place and through the copy tool), `dataExport` with
     the new code, then diff the normalised XML. Needs MOD-I78 fixed or
     excluded.
  2. *HTTP differential replay:* record the requests made by Playwright
     and by an API crawl against 5.17, replay them against each phase,
     and diff status, redirects, headers, cookie attributes, normalised
     JSON and HTML. Dump the handler mappings so every route is
     exercised. Catches Spring 6 path matching, Jackson 3 defaults and
     Tomcat 11 behaviour.
  3. *Analytics equivalence:* one generated raw-event stream (crossing
     DST, month and year ends, several time zones) through 5.17 and the
     new build, then compare the aggregation tables.
* **Coverage:**
  4. *Portlet conformance after the WAR transform:* run the
     already-deployed Pluto JSR-286 test suite (`overlays/pluto-testsuite`,
     test-portlet-1/2) and require PASS, both now and on the transformed
     WAR on Tomcat 11.
  5. Smoke every bundled portlet in every mode and window state.
  6. *Characterisation specs* for each WS14 portlet and flow before it
     is replaced. The replacement passes the same spec.
  7. *Entity round-trip* with edge values on every vendor, plus
     characterisation of HQL/Criteria results through Hibernate 5.2 →
     7.4.
  8. *Security matrix* (role × route → status), legacy password hashes,
     CSRF, and a header snapshot.
  9. *Two-node cluster:* jobs run once, cache invalidation propagates,
     and the lock recovers when a node fails.
* **Strength and non-functional:**
  * Query-count and latency budgets, and a nightly campus-scale load
    test.
  * PIT mutation testing and copy-tool fault injection.
  * jqwik property tests for the copy tool.
  * Firefox, WebKit and phone viewports, with axe and visual
    snapshots.
  * ArchUnit end-state rules, and japicmp on the extension APIs.
  * An anonymised production database, plus a large-campus data
    generator.
* **Order:** 1, 2 and 4 first. They reuse existing tools, and P2
  (Hibernate 5.2) should merge only after it passes 1 and 7. Then 7, 8 and 9. Run a
  small set of vendor pairs per push and the rest nightly. For the
  G-portal check, run with `retries: 0` or count retried tests as
  failures.

### MOD-D30: divide work by resource use, with workstream CI branches and cloud sessions

* **Finding:** the one 16 GB prototype machine was the bottleneck.
  * 7 Gradle daemons held 8.2 GB. The project's
    `-XX:MaxHeapSize=32g` (from #2960) overrides its own `-Xmx2048M`,
    and idle daemons lived for 3 hours.
  * Portal and multi-database runs had no machine-wide limit.
  * Every agent pushed to the one integration branch, so unfinished
    work turned CI red for everyone: SpotBugs twice, DB integration
    three times.
  * Tasks were phase-sized. The P2 agent has run for about 11 hours.
* **Changes on the machine (environment only, not committed):**
  * `~/.gradle/gradle.properties` caps daemons at a 2 GB heap, G1
    periodic GC, a 15-minute idle timeout and 2 workers. Stopping the
    idle old daemons raised available memory from 2.0 to 6.9 GB.
  * A machine-wide `portal-slot.sh` allows at most two portal or
    multi-database runs at once.
* **Decisions (project owner, 2026-10-10):**
  * Workstreams validate on their own branches,
    `ccr-43ba0785-hzlrj5-<topic>`, in both forks. The integrator merges
    only green work and deletes merged branches.
  * Up to three cloud sessions, each on its own machine, take the
    portal-heavy workstreams.
* **Working rules:** these are recorded in WORKSTREAMS.md.
  * Split work by resource use. Light tasks (edit, compile, one module's
    tests) run in parallel; heavy tasks go through the slots, to cloud
    sessions, or to CI.
  * One upstream PR-sized task of about 1 to 3 hours each, ending with a
    handoff note.
  * Workstreams propose log entries with placeholder IDs; the integrator
    assigns the numbers.
* **Upstream note:** the `MaxHeapSize=32g` override should be raised
  upstream. It makes `-Xmx2048M` meaningless on developer machines.
* **Update (21:25 UTC):** after a usage reset the owner said to go full
  speed ("go ham"), so the cloud cap rose to six. There are six sessions running.
  * HTTP replay and security matrix (`-http-replay`).
  * Cluster, browsers, accessibility and budgets (`-cluster`).
  * P3-2 Pluto in-tree (`-pluto`).
  * P4 preparation on Spring 4.3 (`-spring53-prep`).
  * Overlay fixes for the Tomcat 11 blockers and the removed-API scanner
    (`-overlays`).
  * Local login default with CAS opt-in (`-local-login`).
* **Update (22:15 UTC): the bottleneck moved to GitHub Actions.** 24 runs
  were queued with only 3 jobs running. Each workstream push started 20 to 40
  jobs (the full OS/JDK matrix, every database, full Playwright).
  * **Decision (owner):** workstream branches
    (`ccr-43ba0785-hzlrj5-<topic>`) run a light profile, and a newer push
    cancels the older run.
  * master, pull requests and the integration branch keep the full
    matrices, so every merge is still checked in full. Heavier sets run
    nightly or on dispatch.
  * CI.yml has it in both repos (uPortal 87e0b98a5, uPortal-start
    06cc883f0): one Linux leg plus static analysis, and on uPortal-start
    no released-uPortal Playwright run, since Integration covers it.
  * The DB-matrix workstream applies it to `db-integration.yml`,
    `integration.yml` and `db-harness.yml`. Every new workflow follows
    the same rule.
  * How it works: the branch test is
    `startsWith(github.ref_name, 'ccr-43ba0785-hzlrj5-')`, and the
    matrices switch with an inline `fromJSON(cond && light || full)`,
    because `env` isn't available in `strategy`. Prototype only, like
    the `ccr-*` trigger (MOD-I74).
* **Shared egress limits in the cloud sessions:** Maven Central (429)
  and Docker Hub pull limits, and jitpack.io is policy-blocked
  (MOD-I51).
  * Each session uses Google's Central mirror and Docker Hub mirror
    (`mirror.gcr.io`), and installs the jitpack-only
    `google-visualization-java` jar from the 5.17.9 uPortal WAR, checked
    by SHA-256. These steps aren't committed.
  * The proper MOD-I51 fix, removing the jitpack dependency, is queued.
* **Maven Central mirror (owner decision, 22:58 UTC):** one cloud
  session's permission check refused the local-only Google mirror init
  script as a "package registry bypass", and the session correctly did
  not work around it. Asked whether to continue, the owner chose to allow
  it: workers may point Central lookups at Google's public, byte-identical
  mirror (`maven-central.storage-download.googleapis.com`) to get past
  Central's 429s. It is never committed. Where a session's permission
  system still refuses, that session validates through its topic
  branch's GitHub CI instead.

### MOD-D31: Lit components model their state explicitly with XState

* **Decision (project owner, 2026-10-10):** WS14's Lit web components
  model their UI state as state machines in XState v5.
* **How:**
  * A small Lit `ReactiveController` adapter in uPortal-webcomponents.
    It starts the actor when the component connects, subscribes through
    a selector, calls `requestUpdate()` only when the selected value
    changes, and stops the actor when the component disconnects.
  * There is no official `@xstate/lit`; npm returns 404 (checked
    2026-10-10).
  * Machines are pure modules built with `setup(...).createMachine(...)`.
    Side effects such as REST calls are `fromPromise` actors declared in
    `setup`, so tests can replace them.
  * Templates render from `matches()` and `hasTag()` rather than from
    boolean properties added ad hoc.
  * Any component with more than trivial state uses a machine, including
    every portlet conversion that follows.
* **Testing:**
  * Pure machine tests in Vitest.
  * Model-based tests with `@xstate/graph`: shortest and simple paths
    drive the real component in Vitest browser mode through every
    reachable state.
  * The existing per-file coverage thresholds still apply.
* **Versions:** `xstate` 5.33.2 and `@xstate/graph` 3.0.4 (peer
  dependency `xstate` ^5.19.4), pinned and updated by Renovate. The build
  puts XState in one shared chunk, loaded once for all components.
* **Alternatives considered:**
  * Robot3 with lit-robot: smaller (robot3 1.4.0, lit-robot 3.0.1), but a
    second state vocabulary next to XState.
  * `@xstate/store` 4.3: event-driven stores, not statecharts.
  * Plain Lit reactive properties: states are implicit and can't be tested
    as a model.
  * Lit's template compiler (`@lit-labs/compiler` 1.1.2) stays out; it is
    still a Labs prototype.
* **Clean integration (owner: "it should be able to integrate cleanly").**
  The adapter's acceptance criteria, each covered by a test:
  * Lit's public `ReactiveController` API only, used by composition: no
    base class, mixin or patching.
  * Types inferred from the machine, so components need no `any` or
    casts and an unknown event is a type error.
  * Moving an element in the DOM keeps its state: the persisted snapshot
    is restored on reconnect. In-flight invocations are defined and
    tested. There are no updates after disconnect and no leaks.
  * Host properties set the actor's input at first connect, and later
    changes become explicit events. Machine `emit`s become DOM
    `CustomEvent`s.
  * A selector plus equality check, so `requestUpdate()` runs only on
    real changes.
  * Generic: no uPortal code, only `lit` and `xstate` as dependencies,
    so it can be published on its own or proposed upstream later.
* **Also rejected:** the community `xstate-lit` 2.0.5. It was last
  published in January 2024 and doesn't list a repository or licence.
* **Status:** the WS14 foundation agent adds the adapter, converts
  `<up-favorites>` and adds the model-based tests before its merge.

### MOD-D32: Conventional Commits are checked in CI, and history is linear

* **Context (project owner):** "We use conventional commits on uportal."
  An audit of the integration branches since `master` found:
  * all 181 non-merge commits had valid types and scopes;
  * 13 subjects exceeded AGENTS.md's 72-character limit, mostly because
    `(MOD-I…)` references went in the subject;
  * there were 21 git merge commits ("Merge branch 'wip-…' into ccr-…");
  * one unpushed `wip:` commit sat in an agent's worktree.
* **Changes:**
  * `.github/scripts/check-commit-messages.sh`, in both repos, checks
    every non-merge commit a push or pull request adds:
    `type(scope)!: description`, a lowercase description, no trailing
    period, at most 72 characters. A Commit messages job in CI runs it
    (uPortal 8dee16c72, uPortal-start 12fe42e97), and AGENTS.md lists
    the command.
  * Commits older than the script aren't checked, and pushed history is
    not rewritten.
  * Issue IDs go in a `Refs MOD-…` footer.
  * uPortal upstream has no commit linter and squash-merges PRs with
    Conventional titles, so the script is an upstream candidate. A PR
    title check would complement it there.
* **Decision (owner): linear history.** Workstreams land by rebasing
  their non-merge commits onto the integration branch and
  fast-forwarding, with no merge commits. Existing merge commits stay,
  because rewriting the shared branch would break every worker's
  branch.
  * Cloud workstreams push topic branches, and the integrator lands
    them.
  * Local agents rebase their unpushed branch and push as a
    fast-forward.
* **Alternatives considered:**
  * Squash per workstream: loses the one-commit-per-future-PR split.
  * Keep merge commits: commitlint ignores them, but the history doesn't
    read as a series of upstream PRs.

### MOD-D33: P2 (Hibernate 4.3.11 → 5.2.18) is built and checked; it merges after the goldens

* **Branch:** `ccr-43ba0785-hzlrj5-hibernate52` in uPortal, four commits
  on ccr `22bdf2b23`. The handoff note is
  `docs/developer/modernization/notes/hibernate-52.md`.
  * `892bd4759` `refactor(rdbm): hide hibernate schema generation behind a
    schema tool` (P2-1).
  * `997bb7f6f` `build!: upgrade hibernate to 5.2.18` (P2-2).
  * `054d24727` `refactor(persistence): use jpa index, order column and
    fetch mappings` (P2-3).
  * `3a1c55cbb` `fix(persistence): pin the pref value and postgresql lob
    column types` (P2-4).
  * P2-5 (Flyway) is not started.
* **Freeze block** (`hibernate.properties`, MOD-D5): 5.2 keeps 4.3's
  schema.
  * A `PortalDialectResolver` keeps 4.3's dialect choices: PostgreSQL 10+
    → `PostgreSQL81TextClobDialect`, MySQL/MariaDB → `MySQL55Dialect`,
    SQL Server → `SQLServer2005Dialect`.
  * A `PortalImplicitNamingStrategy` (legacy-jpa plus 4.3's `FK_`/`UK_`
    hash), the standard physical naming, and the pooled optimizer.
  * Jadira 6.0.1.GA, which stores Joda values the same way. jpamodelgen
    stays at 5.6.3.
* **Required checks on the branch:**
  * G-unit: 1137 → 1152 tests, 0 failures.
  * G-portal: 119/124 twice. Only the sandbox-blocked tests failed.
  * G-db on hsqldb, postgres-16, mysql-8.4, mariadb-10.6/11.4,
    sqlserver-2022 and oracle-23: zero STRUCTURE, DATA and NAMES drift.
    GENERATORS lost only the dead `hibernate_sequences` declarations.
    Oracle has the known cached-sequence noise (MOD-I84).
  * Table dumps after 4.3 and after 5.2 `dataInit` are identical on
    PostgreSQL, MySQL and SQL Server.
  * Upgrade path on postgres-16: 4.3 data, then the 5.2 portal on the
    same database, with no drift. The id allocations stay above 4.3's.
* **Before merging (MOD-D29, MOD-D32):**
  * Rebase onto ccr. Expect a `CHANGES.md` conflict, and reconcile with
    `05f40ce45` (read-committed cluster locks, MOD-I99).
  * Then pass, unchanged, the persistence, query, analytics and ArchUnit
    goldens and the dataExport data-equivalence check. Rerun G-db on
    every vendor, the upgrade path and G-portal.
* **Findings:** MOD-I120 to MOD-I127.

### MOD-D34: Apache ECharts replaces Google Charts, in two steps

* **Context:** uPortal draws statistics charts with Google Charts and builds the chart data with
  `google-visualization-java`, a fork published only on jitpack.io (MOD-I51). The fork is
  unmaintained, and Google's original (1.1.1 on Maven Central, 2013) has a bug that writes invalid
  JSON for null cells. Google Charts itself is closed source, and its terms don't allow hosting it,
  so every viewer's browser loads it from Google. That conflicts with a strict Content Security
  Policy and with student-privacy reviews.
* **Users:** the Statistics, Activity, Popular Portlets and SQL Query portlets, the CSV and HTML
  table views, and `reportGraph.jsp`, `reportList.jsp` and the SQL Query chart JSPs.
* **Options compared (npm registry, 2026-10-10):**
  * Apache ECharts (Apache-2.0) is an Apache Software Foundation project, with 6.0 in July 2025 and
    6.1 in May 2026. It draws SVG or canvas and has screen-reader descriptions, decal patterns for
    colour-blind users and dark mode. Chart types import one by one.
  * Vega-Lite (BSD-3) is very active, with 6.5.0 on 8 October 2026, and its charts are JSON specs.
    The download is larger. Its expression engine needs `unsafe-eval` unless the interpreter package
    is added.
  * Plotly.js (MIT) is active, with 4.1.2 in October 2026. It has the largest download and its own
    look.
* **Not chosen:**
  * Chart.js, by the owner's call. It had one release in the past year.
  * Observable Plot, with no release since February 2025.
  * uPlot, with one maintainer and time series only.
  * D3, last released in March 2024. We would write our own charts on it.
  * Highcharts, under a commercial licence.
  * ApexCharts, whose v8 uses its own licence file.
  * IBM Carbon Charts, built for the Carbon design system rather than Bootstrap.
* **Decision (owner, 2026-10-10):** use Apache ECharts, in two steps.
  1. Now, uPortal gets its own small table model in place of `google-visualization-java`. Jackson
     writes its JSON and plain RFC 4180 escaping writes its CSV. The JSON is unchanged from what Google Charts
     reads, so the pages don't change, and the null-cell bug is fixed. jitpack.io leaves the build
     (MOD-I51, cloud session `gviz`).
  2. With the portlet replacement (workstream 14, MOD-D18 and MOD-D19), REST endpoints return plain
     JSON rows and web components draw them with ECharts imported from `echarts/core`. Google Charts
     then leaves the pages.

### MOD-D35: prose and commit messages are checked with Vale and the ai-tells rules

* **Asked by the owner:** validate prose with Vale and the AI-tells rule set. The owner chose the
  scope, which is the docs written for 6.0 plus commit messages.
* **Tool:** `.github/scripts/vale.sh` in both repositories downloads Vale 3.24.0 and the `ai-tells`
  and `ai-tells-commits` styles 1.37.0 from tbhb/vale-ai-tells, checks their SHA-256, and runs
  `docs [paths]` or `commits <range>`. The release zips match the v1.37.0 tag, and all 151 rules are
  YAML patterns with no scripts. Every rule is at error level. Any finding fails the check.
* **Docs in scope:** `docs/UPGRADING-6.0.md`, `docs/developer/modernization/`,
  `docs/developer/other/STATIC_ANALYSIS.md` (uPortal) and `docs/db-upgrade-harness.md`
  (uPortal-start). Upstream docs join when they are rewritten.
* **Status:**
  * Commit messages: checked in CI from uPortal `247d76b87` and uPortal-start `dde58e2c0` on. A
    seeded bad message failed with four findings, and 3 of the previous 60 commits would have failed.
  * Docs: about 2,000 findings at the start (785 em dashes, 537 semicolons, 146 capitals after a
    colon, then smaller groups), mostly in the research reports. Agents rewrote the files in
    parallel and reached zero on 2026-10-11, keeping facts, numbers, links and citations. About 70 spans
    stay verbatim under an inline suppression with a reason: direct quotes from upstream docs,
    error messages and cited page titles. The Prose (Vale) CI job checks every doc in scope from
    uPortal `55defd7e5` and uPortal-start `bda2e6072` on. A planted em dash and jargon word in the
    upgrade guide failed it with three annotations.
  * A committed uPortal style adds what ai-tells lacks. Jargon.yml lists six planning terms, and
    NotButContrast.yml catches a contrast that defines a thing by what it isn't. Plain technical
    negation passes.
* **Alternative considered:** krishnasunkam/vale-ai-tells, a smaller package with 17 rules. The tbhb
  package is the one Vale's catalog lists as `ai-tells`, and it has the commit-message rules.

### MOD-D36: ADRs in MADR format replace the RFC

* **Asked by the owner:** record decisions as ADRs instead of one RFC. uPortal's code repositories
  have no written proposal process. The `uPortal-Project/community` README names that repository's
  Discussions as the place for design proposals and RFCs (MOD-Q6). Neither uPortal nor
  uPortal-start had an ADR folder; the nearest documents are `docs/developer/other/ARCHITECTURE.md`
  and `docs/architecture/subsystem-analysis.md`.
* **Decision (owner, 2026-10-11):**
  * Architecture decision records go in `docs/developer/adr/NNNN-title.md`, using the MADR template
    (context and problem, decision drivers, considered options, outcome, consequences, pros and
    cons). One ADR per decision that matters upstream. Each starts with status `proposed`, because
    the uPortal community hasn't reviewed them.
  * `RFC-uPortal-6.0.md` is deleted. LOG.md maps each ADR to the MOD-D entries behind it.
  * Upstream pull requests contain the ADRs, `docs/UPGRADING-6.0.md` and user-facing docs. LOG.md,
    the research reports and the workstream notes stay on the prototype branch as evidence. So ADRs
    summarize their evidence instead of linking into `docs/developer/modernization/`.

### MOD-D37: the integration branch history is rewritten once, after the current work is merged

* **Asked by the owner:** clean up the git history. In uPortal, the integration branch had 166
  commits beyond master: 13 merges, 80 docs or log updates and 12 prototype-only CI changes. In
  uPortal-start it had 77: 8 merges and 11 prototype-only CI changes.
* **Decision (owner, 2026-10-11):** rewrite `ccr-43ba0785-hzlrj5` in place in both repositories,
  after the workstreams in flight have landed, rather than now or on a copy branch.
* **Plan:**
  1. Announce a push freeze to every agent and session, and wait for in-flight pushes.
  2. Tag the old head (`archive/ccr-43ba0785-hzlrj5-<date>`) in both repositories before anything
     is rewritten.
  3. Rebuild the history without merge commits. Commits are grouped into the units that become
     upstream pull requests, and messages pass both commit checks, so
     `.github/vale/commit-exceptions.txt` empties. Log and note updates fold into fewer commits.
     Prototype-only changes (the `ccr-*` CI conditions, `docs/developer/modernization/`) sit in
     their own commits, never mixed with upstream content.
  4. Check that the final tree is byte-identical to the old head, then push with
     `--force-with-lease`.
  5. Every agent and session rebases its unlanded work with
     `git rebase --onto <new head> <old head>`.
* **Until then:** keep each commit self-contained, keep prototype-only files out of commits that
  change upstream content, and don't create merge commits.

### MOD-D38: P4-1 shrinks on Spring 4.3, with a context-load test and Spring 5.3 probes in CI

* **What landed** (21 commits, workstream `spring53-prep`, 2026-10-11): every Spring XML context
  loads in a test (`SpringXmlContextsTest`), with seeded faults caught. All four MOD-D26 changes to
  the copied Portlet MVC classes are made on Spring 4.3. The authentication, rendering, JSON-rendering
  and redirection servlet contexts moved from the
  `DefaultAnnotationHandlerMapping` that Spring 5 removed to `RequestMappingHandlerMapping`.
  Contexts refer to the unversioned Spring schemas.
* **Measured:** compiling against Spring 5.3 went from 60 errors to 6, and the context probe on
  5.3 from 1 failure to 0. The workstream's last code commit passed CI and uPortal-start
  Integration (HSQLDB Playwright, PostgreSQL 18 harness and Playwright, G-migrate). The combined
  branch built here with 86 tests passing in the changed modules.
* **Decision:** the `spring53Probe` CI job (a compile ratchet plus the context probe) stays until
  P4-1 merges, then goes with its `probe*` properties. The handoff note,
  `notes/spring53-prep.md`, lists the remaining P4-1 scope.

### MOD-D39: jqwik stays for property tests

* **Context:** jqwik 1.10.1, which the copy-tool tests use for property tests (8 classes in
  `uPortal-db-migrate`), prints a notice from its maintainer saying AI agents must not use the
  library. Its EPL-2.0 licence doesn't restrict use. The copy-tool agent flagged it.
* **Decision (owner, 2026-10-11):** keep jqwik. Its test results count like any other test
  results. The alternatives were porting the properties to JUnit 5 parameterized tests with
  seeded random inputs, or keeping jqwik for human contributors only.

### MOD-D40: uPortal-start signs users in locally, and CAS 8 is opt-in

This answers MOD-Q3 as proposed. The project owner confirmed on 2026-10-11 that local login is
the demo default: it is what a fresh uPortal-start checkout does without any configuration.
Production sites are expected to set `authMode=cas` (or their own SSO). uPortal's own
`security.properties` defaults are unchanged.

* **Decision:** uPortal-start no longer deploys the CAS 3.6 overlay. A portal built from
  uPortal-start signs users in with uPortal's own login form against the portal database.
  `authMode=cas` with `casServerUrl` switches to CAS, either an institution's server or the
  development `apereo/cas:8.0.2` container (by digest, with static quickstart users and a JSON
  service registry) that uPortal-start starts with Docker Compose, using the stock
  image without an Initializr overlay.
* **Why:** CAS 3.6 is end of life, runs on Spring 3.2 and the javax Servlet API, and cannot move
  to Tomcat 10.1 or 11 (MOD-I9, MOD-I36, MOD-I67). Its own data source also had to be changed
  separately when a portal left HSQLDB (MOD-I85).
* **Checks:** CI runs the whole Playwright suite with local login. The Integration workflow runs
  the auth and proxy-ticket specs against CAS 8 on every push, and once more with CAS stopped,
  which must fail.
* **Status (2026-10-11): landed** in uPortal-start from the local-login session's branch, with
  the upgrade notes in `docs/UPGRADING-6.0.md`. The handoff note is uPortal-start
  `docs/developer/modernization/notes/local-login.md`.
* **Later:** the portal's own CAS client moves to Apereo cas-client 4.x in P6/P7, tested by the
  CAS 8 job.

### MOD-D41: the G-index workload and index audit (MOD-D12) is in uPortal-start CI

* **What it is:** a generator for large-campus data, a workload driver, a statement and plan
  audit with an allowlist, and CI jobs, in uPortal-start (`docs/developer/modernization/notes/g-index.md`
  there). Every push runs PostgreSQL 18 at 1/100 scale with seeded faults. The nightly set runs
  PostgreSQL 18 and MariaDB 12.3 at 1/20.
* **Result:** the recommended 6.0 index set is R1 to R15 in that note; R7 is a code change, the
  rest are DDL. At 100,000 accounts the proposed set cut total database time by 41 % and the
  login lookup from 67 ms to 0.19 ms.
* **Status (2026-10-11): landed** in uPortal-start with the local-login batch. The R1 to R15
  changes go into the 6.0 schema (MOD-D11, MOD-D12). R1 is also a 5.17.x candidate, since it only
  adds an index.

### MOD-D42: 6.0 search uses server-side providers, and event-mode search is removed

* **Decision (project owner, 2026-10-11):** the Search portlet becomes the `<up-search>` and
  `<up-search-launcher>` web components over `/api/v6-0/search`. All results come from
  server-side providers (`ISearchStrategy` beans): the existing portlet and people providers,
  plus a generic remote JSON provider that an admin configures with a URL template, headers or an
  API key, and a field mapping. It covers Google Programmable Search, a campus directory or a
  CMS search API.
* **Removed in 6.0:** event-mode search (the `SearchRequest`/`SearchResults` portlet events),
  `searchTabs`, the Google Search Appliance service (Google ended the product in 2019), the
  Google CSE portlet-mode service and the old Search portlet. Portlets can no longer add results
  through events; their owners expose a search URL for the remote provider instead.
  `UPGRADING-6.0.md` explains the move.
* **Kept:** the type-ahead over the portlet list, which works in 5.17's default REST mode.
* **Work:** the WS14 Search conversion (cloud session, branch `ccr-43ba0785-hzlrj5-wc-search`).

### MOD-D43: the ArchUnit freeze store never grows

* **Decision (project owner, 2026-10-11):** "Prefer using modern where possible, don't make
  things worse." For each of MOD-D29's freeze rules, a change may not record more uses of the
  retired API than there were before it. Code that has to cross between an old and a new API uses
  the new one wherever it can.
* **Check:** `.github/scripts/check-freeze-store.py`, run by the CI workflow's `Commit messages`
  job, compares `uPortal-webapp/src/test/resources/archunit_store` before and after a push. It
  fails when a rule's count of recorded uses goes up, and lists each added entry as a warning.
  It ignores line numbers the way ArchUnit does. A new rule's entries are listed but not counted,
  because they record uses that already exist.
* **Counts, not entries:** a change that takes a retired type out of a service can leave one
  call in the adapter that calls the service. 394fd8018 did this: the cache health report stopped
  taking Ehcache objects, and the Ehcache 2 listener now calls `getName()` itself. That adds one
  entry while the Ehcache rule as a whole shrinks. Failing on each entry would block a change
  that reduces Ehcache use.
* **Status:** the java.time and Spring cache batches (bc955a7bd) added 12 entries and removed
  156: the Joda-Time rule went from 583 to 518 uses and the Ehcache 2 rule from 426 to 335. Of
  the 12, the 11 in `JodaTimeConversions` stay until the JPA mappings leave Joda-Time (P3-5). The
  one in `InsufficientSizeCacheEventListener` stays until the cache provider changes (P4-3).
  Spring's Cache API has no eviction events, and JCache does not report evictions either, so the
  listener becomes an Ehcache 3 event listener or a Caffeine removal listener (cause `SIZE`) then.

### MOD-D44: 6.0 stores text on SQL Server as nvarchar

* **Decision (project owner, 2026-10-11):** SQL Server text columns become `nvarchar`, through
  Hibernate's nationalized strings, in the 6.0 schema step (MOD-D11). Lengths count characters,
  and every supported SQL Server version accepts it.
* **Why:** 5.17's and 6.0's `varchar` columns on the default collation keep only windows-1252
  (MOD-I196, MOD-I201). Text such as emoji or CJK in group names, portlet titles or user
  attributes can't be stored, and a copy from another vendor is refused (MOD-I202).
* **Considered:** `varchar` with a UTF-8 collation. Its lengths count bytes, and every text column
  would need up to four times its length for the same number of characters. It also depends on
  each site's database setup.
* **Cost:** text that fits windows-1252 takes twice the space, and the copy tool converts the
  columns as part of the 6.0 copy.

### MOD-D45: the password flows get four fixes in 6.0

* **Decision (project owner, 2026-10-11):** all four, each in its own commit with tests:
  1. Self-Edit Account and User Administration refuse a password confirmation that differs.
     This also stops "Create a User" from overwriting an existing account (MOD-I195).
  2. Self-Edit Account asks for the current password before a change.
  3. Forgot Password gives the same answer for known and unknown user names.
  4. Password reset links expire (default one hour, as a property).
* **Resolved conflict (coordinator, 2026-10-11):** fixes 3 and 4 first asked for the page to say
  when the email couldn't be sent. For an unknown user name nothing is sent, so a "could not be
  sent" page would show that an account exists. Known and unknown user names get the same neutral page ("if that
  user name has a local account with an email address, reset instructions were sent; contact the
  help desk if nothing arrives"), and a failed send is logged at ERROR for administrators,
  without the token.
* **Found by:** the Self-Edit Account characterisation specs.

### MOD-D46: Self-Edit Account and Reset My Layout are web components

* **Decision (project owner, 2026-10-11):** both become REST APIs plus web components, which
  changes MOD-D19's default mapping for these two (it listed them as Thymeleaf full-page flows).
* **Condition.** Self-Edit Account's password fields work with browser password managers. They
  carry `autocomplete` hints (`username`, `current-password`, `new-password`), and a browser
  test checks that the fields can be found, since shadow DOM can hide fields from autofill.
* **Why:** both conversions were built and tested before the mapping was checked. The API is
  CSRF-protected, no log line contains a password, and a Thymeleaf page would have to wait for
  Spring 5.3. Forgot Password and the reset-password flow keep MOD-D19's mapping.

### MOD-D47: hide the Tomcat version and fix the existing accessibility violations

* **Decision (project owner, 2026-10-11):** Tomcat's error pages and headers stop showing the
  server version. The accessibility violations in the axe baseline (`tests/a11y/baseline.ts`
  in uPortal-start) are fixed, starting with the Respondr skin, and the baseline only shrinks.
* **Work:** `ccr-43ba0785-hzlrj5-hardening`.

### MOD-D48: fix how uPortal holds database connections before changing pool defaults

* **Decision (project owner, 2026-10-11):** the pool defaults stay as they are for now. First,
  uPortal stops taking a connection per portlet render to read a cached portlet definition,
  and stops holding the request's connection while the page waits for its portlets. The
  budget (MOD-I190) is then set from new measurements.
* **Why:** with 5.x defaults, 16 users reach about 127 PostgreSQL connections, more than the
  default limit of 100. A pool of 60 avoids errors, but most of its connections are idle.

### MOD-D49: text inputs accept any Unicode text

* **Decision (project owner, 2026-10-11):** the portal has international users, so a field where
  people type text accepts any Unicode text, sent as UTF-8. That covers names, titles,
  descriptions, search terms, email addresses, passwords and tenant names. A field limits its
  characters only when its value is an identifier that the portal puts in URLs or keys, such as
  fnames, tenant fnames and event types. Its error message then lists the characters it allows.
* **Tests:** each new API and component sends a value with accented letters, a non-Latin script
  and a character outside the Basic Multilingual Plane (four bytes in UTF-8), and reads it back.
  The persistence goldens do the same on all six vendors.
* **Storage:** SQL Server follows MOD-D44. A MySQL or MariaDB column must use `utf8mb4` to hold
  four-byte characters, and any change it needs joins the 6.0 schema step (MOD-D11).
* **Work:** `ccr-43ba0785-hzlrj5-utf8-inputs` (audit, MOD-I233); each conversion tests its own
  fields.

### MOD-D50: web components come in English, Spanish and French

* **Decision (project owner, 2026-10-11):** each message a web component uses has English,
  Spanish and French text (`Messages.properties`, `Messages_es.properties`,
  `Messages_fr.properties`) in the commit that adds it. A check fails the build when a
  component's key is missing from one of the three. The other bundles (de, ja, lv, mk, sv) show
  English for new keys until someone translates them, as today.
* **Format:** messages go through `MessageFormat` (`alwaysUseMessageFormat`), so an apostrophe
  is written twice (`l''élément`) and placeholders stay `{0}`, `{1}`.
* **Tests:** a Playwright test renders each component for a user whose locale is Spanish and
  one whose locale is French, and finds a translated text.
* **Work:** `ccr-43ba0785-hzlrj5-i18n` (MOD-I231, MOD-I232, the check); each open conversion
  adds its own translations.

### MOD-D51: web components work in any language a site adds

* **Decision (project owner, 2026-10-11):** beyond English, Spanish and French (MOD-D50), a site
  can run the components in other languages, such as German, Polish, Arabic or Chinese. It adds
  a message bundle and lists the locale in `portal_locales`, with no code changes. So the
  components:
  1. pick plural forms by the language's rules (`Intl.PluralRules`). Polish needs three forms
     and Arabic six. A message key has one entry per form (`.one`, `.few`, `.many`, `.other`
     and so on), and a missing form falls back to `.other`;
  2. format numbers and dates in the user's locale (`Intl.NumberFormat`,
     `Intl.DateTimeFormat`), so Arabic digits and German separators appear where those locales
     use them;
  3. set `lang` and `dir` on the element from the locale, use CSS logical properties only, and
     mirror icons that point left or right, so Arabic reads right to left;
  4. ignore keys while an input method is composing text (`isComposing`), so pressing Enter to
     choose a Chinese or Japanese character doesn't submit a form;
  5. wrap long words, such as German compounds, instead of clipping them;
  6. fall back from region to language to English (`zh-TW`, then `zh`, then English), and
     accept script subtags (`zh-Hant`).
* **Tests:** two pseudo-locales built from `Messages.properties` at test time: one with
  accented letters and text 40% longer, one right to left. They find English text written into
  the code, clipped text and layouts that don't mirror. Unit tests check the Polish and Arabic
  plural forms, and a Playwright test runs a component with test bundles for `pl`, `ar` and
  `zh-TW`.
* **Languages in the release (project owner, 2026-10-11):** the 6.0 release has English,
  Spanish and French text for the components. A site can add other languages. The tests use
  test bundles for them. The release doesn't contain translations that nobody has reviewed.
* **Theme:** the Respondr theme around the components has no right-to-left layout (MOD-I234).
  The owner chose to make it right to left after the accessibility fixes (MOD-D47), which
  change the same skin files.
* **Work:** `ccr-43ba0785-hzlrj5-i18n` builds the shared code in `lib/i18n.ts` and updates the
  landed components. Each later conversion uses that code.

### MOD-D52: pages and components fit standard screen sizes

* **Request (project owner, 2026-10-11):** check that the layout fits on standard screen sizes:
  nothing spills out of its box or gets cut off, nothing collapses, and nothing wraps where it
  shouldn't.
* **Sizes:** widths of 320 (the WCAG reflow minimum, a 1280-pixel screen at 400% zoom), 390
  (phone), 768 (tablet), 1024, 1366 (the most common laptop) and 1920 pixels.
* **Checks,** measured from element boxes in the browser rather than compared screenshots:
  1. The page never scrolls sideways.
  2. No text or control is cut off. A box that hides overflow shows all of its content, unless
     it scrolls on purpose, or it ends in an ellipsis and the full text is available to screen
     readers and on hover.
  3. An element doesn't spill over its neighbors, and controls don't overlap.
  4. Buttons, tabs, menu items, table headers, labels and badges stay on one line from 768 pixels
     up. The tab bar fits on one row from 1024 pixels up, unless it turns into a menu by design.
  5. A region, portlet or component with content keeps a usable size instead of collapsing, and
     columns stack instead of getting narrower than 240 pixels. Images keep their proportions.
  6. With the WCAG text spacing applied (line height 1.5, letter spacing 0.12em, word spacing
     0.16em), nothing is cut off.
* **Where:** each page of the quickstart users (guest, student, staff, faculty, admin), the
  login page, each web component maximized, and the main admin pages, in Chromium at all six
  widths. WebKit and Firefox run at 390 and 1366 pixels. Each web component also runs at
  container widths of 280, 400, 800 and 1200 pixels in its own browser tests, since a portlet
  column can be narrow. Once the -i18n pseudo-locales land, the checks also run with the
  longer text and right to left.
* **Existing problems:** recorded in a baseline that only shrinks, like the axe baseline
  (MOD-D47). A new problem fails the build.
* **Work:** `ccr-43ba0785-hzlrj5-hardening`, before the accessibility fixes, so those fixes and
  the right-to-left theme (MOD-I234) are measured against it. The checks run inside the
  existing Playwright jobs, not new ones.

### MOD-D53: faster landing with the same checks

* **Measured (2026-10-11, 11:55 UTC):** of 24 pending workflow runs, 22 were waiting for a runner
  or running, one for close to two hours. A branch needs about 40 jobs before it is merged, and
  about 20 runner slots serve both repositories. Pushes cancelled running checks: one branch
  started 32 runs. Cloud sessions couldn't run Gradle, so each compile check went through the
  queue or the coordinator.
* **Decision (project owner, 2026-10-11):** the runner pool stays as it is, and:
  1. Cloud sessions may run Gradle. `.claude/settings.json` in both repositories allows
     `./gradlew`. The init script that points Gradle at a Maven mirror is kept outside the
     repositories. A session
     compiles and tests its change before it pushes.
  2. A session pushes when a step has passed locally, not after every commit, since each push
     cancels its branch's running checks.
  3. Before it is merged, a branch runs the checks for what it changes: the light profile on push,
     plus the six-vendor database workflows when it changes persistence, SQL, the schema or
     the copy tool. Branches are then merged in batches of up to four, and the full matrix runs once
     on the result. If that run fails, the coordinator finds the branch that broke it and
     reverts its commits with revert commits. The other branches stay.
  4. A rule the owner adds for every component goes into one follow-up branch that applies it
     to every component, which also keeps the wording consistent. Branches that pass are
     merged without it. A security or data-loss rule still goes into each branch before it is
     merged.
* **Unchanged:** seeded faults, characterisation specs, zero schema drift until the 6.0 schema
  step, no re-runs for flakes, and a full matrix on the integration branch after each batch.
  This refines MOD-D32's landing steps.
* **Batches in practice (coordinator, 2026-10-11):** a push to the integration branch cancels the
  workflows still running there, so a batch is pushed in quick succession and then nothing is
  pushed until its full run finishes. A push whose head commit carries `[skip ci]` doesn't start a
  run, so it cancels none. A landing can go out under a `[skip ci]` LOG commit while a
  batch's run is in progress, and the next batch's run covers it. Clean-ups of superseded runs
  leave runs started by hand alone.
* **Lesson (coordinator, 2026-10-11):** a uPortal change to persistence or SQL also needs one
  Integration run on uPortal-start with that uPortal build. The six-vendor DB integration
  workflow runs uPortal's own tests, which didn't load a layout through the path that lost its
  table alias. The portal's PostgreSQL run would have shown it before the merge.

### MOD-D54: servers clear their replicated caches after a healed network split

* **Decision (project owner, 2026-10-11):** when the JGroups channel reports a merge view (the two
  halves of a split rejoining), each server clears its replicated caches and reloads from the
  database. A normal join doesn't clear anything.
* **Why:** during a split each server misses the other's invalidations, so a portlet definition
  or a permission decision could stay out of date until its cache entry expires, up to six hours.
  The join fix (MOD-I210, more join attempts) covers restarts, not a real network split.
* **Cost:** a short burst of database load after a split heals.
* **Work:** `ccr-43ba0785-hzlrj5-cluster`, with a harness leg that splits and heals the two
  nodes and a seeded fault.

### MOD-D55: fewer CI jobs on each push and batch

* **Measured (2026-10-11):** six full runs took 391 runner-minutes. Jobs waited 16 to 123 minutes
  for a runner and then ran for 3 to 40. Each job waits on its own, so every job a push starts
  adds a wait. `npm ci` downloaded every package again in each of 16 Integration jobs
  (34 minutes). uPortal CI compiled the same code 10 times and installed Chromium 8 times. A
  3-second planning job held back the replay workflow's uPortal build for 44 minutes. G-index,
  at 39 minutes, is the longest job, and it ran on every uPortal-start topic push.
* **Changes that keep every test (coordinator):** cache npm and the Playwright browsers; run the
  web component tests, the JavaScript lint and SpotBugs in one uPortal CI job instead of each
  OS and Java job; make each planning job the first step of the job that needs its answer;
  split the long harness steps into timed phases so the next savings are measured.
* **Decision (project owner, 2026-10-11):**
  1. The Zulu and Corretto jobs run in the nightly run only. Each batch keeps Temurin 21 and 25
     on Linux, and Temurin 25 on macOS and Windows.
  2. A uPortal-start topic push runs G-index only when it, or the uPortal branch of the same
     name, changes persistence, SQL, the schema, the data files or G-index itself. A batch, a
     run started by hand and the nightly run always run it.
* **Cost:** a problem with one vendor's JDK shows up in the next nightly run instead of at
  landing. A G-index failure that a web component change causes shows up in its batch run,
  which then reverts that branch. The Session Timeout workload failures were found this way
  on a topic push.

## Issues

| ID | Area | Issue | Impact / plan |
| --- | --- | --- | --- |
| MOD-I1 | Persistence | Schema creation (`DataSourceSchemaExport`, `HibernateDbLoader`, `TableXmlHandler`, `FixedDatabaseMetadata`) uses Hibernate 4 APIs removed in 5.0: `Configuration.generateSchemaCreationScript`, `buildMapping`, `createMappings`, `Table.sqlCreateString`. `dataInit` depends on it. | Highest-risk persistence item. Rewrite on the `Metadata`/`SchemaManager` APIs, or move to Liquibase/Flyway (see MOD-Q4). |
| MOD-I2 | Portlets | All 37 built-in uPortal portlets use Spring Portlet MVC (`DispatcherPortlet`, `@RenderMapping`), which was removed in Spring 5.0. | Needs a forked or replacement Portlet MVC (such as PortletMVC4Spring), or a rewrite. |
| MOD-I3 | Portlets | Spring Web Flow portlet integration (17 portlet contexts, 26 admin flows) was removed in Web Flow 2.5. No Playwright spec exercises these flows. | Add Playwright coverage before touching them. Then fork the portlet integration or replace the flows. |
| MOD-I4 | Caching | Ehcache 2 is used directly in 58 files (`Element`, `SelfPopulatingCache`, search API), with JGroups replication and 184 cache regions. Spring 6 dropped Ehcache 2 support, and Hibernate 6 dropped hibernate-ehcache. | Move to JCache (Ehcache 3, or Caffeine plus a clustered option). |
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
| MOD-I15 | Spring | 110 of 113 Spring XML contexts reference versioned XSDs (`beans-3.1` etc.), and 3 use `spring-security-4.2.xsd`, which Spring Security 5+ rejects. | **Fixed** on Spring 4.3: every context refers to the unversioned schemas ("refactor(spring): reference the unversioned spring xml schemas", MOD-D38). Mechanical change to unversioned XSDs. Can be merged early. |
| MOD-I16 | Portlets | Apache Pluto is retired. Apache Portals went to the Attic in Nov 2025, `apache/portals-pluto` was archived Oct 2025, and the last release, 3.1.2, is javax. The Jakarta Portlet 4.0 spec is unreleased, with no `jakarta.portlet` artifacts on Central. The only Jakarta port is Liferay "Plutonium" 4.0.0-M1, which has one maintainer and is not on Central. | Don't depend on upstream Pluto. Sakai is a precedent (PR #15141, merged 2026-10-09): it copied Pluto into its own tree and runs the `javax.portlet` API on `jakarta.servlet`. |
| MOD-I17 | Portlets | PortletMVC4Spring 6.0.0-M1 targets Spring 6.2 and Portlet 4.0 (which needs a Portlet 3+ container). There is no Spring 7 release, and it is not on Central past 5.3.4. | Not a drop-in replacement for the internal portlets on Pluto 2.1. Forking Spring Portlet MVC 4.3 (Apache-2.0) into uPortal is the likely path. |
| MOD-I18 | Portlets | Neither the Tomcat jakartaee-migration tool nor Eclipse Transformer renames `javax.portlet`. So a converted portlet WAR only links against a hybrid portlet API: `javax.portlet` compiled against `jakarta.servlet` (only `Cookie` is affected in Portlet 2.0). | The prototype spike tests deploy-time or build-time conversion of the bundled portlet WARs against a hybrid API + in-tree Pluto. |
| MOD-I19 | Soffit | `SoffitConnectorController` is itself a Spring Portlet MVC portlet. | Moving content to Soffit still needs the in-uPortal portlet container, or a rewritten connector. |
| MOD-I20 | Upstream | The published roadmap defines uP6 as Java 21 + Tomcat upgrade + converting external portlets to web components, and uP7 as dropping portlets. It also lists "Maven" (2025) and "Replace Gradle" (2028). An April 2025 uportal-dev thread (seen only via search snippets) revised uP6 to Java 17 on Tomcat 9 / javax. Renovate pins Spring < 5 and blocks Hibernate majors. A Spring 7 / Jakarta / Tomcat 11 6.0 merges uP6 and uP7 scope. | Needs community agreement before large PRs are merged. The decisions are proposed as ADRs, indexed in [`docs/developer/adr/README.md`](../adr/README.md) (MOD-D36). |
| MOD-I21 | Upstream | The owner has open upstream PRs that overlap phase 1: uPortal #2921 (Gradle 6.9.4 → 8.5, branch `build/gradle-8`), #2988 (sherter → Spotless, branch `refactor/spotless-migration`), and uPortal-start #681. | Phase 1 reuses their decisions. Then either update those PRs or replace them with the finer-grained series. |
| MOD-I22 | Upstream | `docs/architecture/subsystem-analysis.md` (PR #2917) already recommends: decomposing `uPortal-utils-core`; splitting `core-api` / `core-portlet`; extracting the rendering pipeline from Pluto; adopting Liquibase or Flyway, and turning the Soffit connector into a servlet filter. It estimates about 21k portlet-specific LOC. | Align workstreams with these "first moves" so the work reads as executing the agreed analysis. |
| MOD-I23 | Persistence | IDs on SQL Server / MariaDB: `PortalDialectResolver` forces `SQLServer2005Dialect`, and MariaDB is detected as MySQL, so neither supports sequences. So the `@SequenceGenerator`-named generators probably fall back to tables there (inferred from dialect behaviour, not yet checked on a real database). Hibernate 7.4's `SQLServerDialect` / `MariaDBDialect` always use native sequences. | **Confirmed by the P0-4 harness.** On SQL Server 2022, MySQL 8.4 and MariaDB 10.6/11.4, all 49 IDs are backed by one-row `UP_*_SEQ(next_val)` tables. HSQLDB, PostgreSQL and Oracle use 49 real sequences. All 49 `@TableGenerator`s are dead (no vendor has `hibernate_sequences`), and every sequence's increment equals its allocationSize. Fix: no-sequence dialect subclasses for SQL Server and MariaDB in P2/P6. |
| MOD-I24 | Persistence | For PostgreSQL `@Lob`, Hibernate 4.3 stored large-object OIDs in `text` columns, while 5.6.2+ generates `oid` columns. `vacuumlo` can delete large objects referenced only from `text` columns, which is data loss. | **Confirmed by the P0-4 harness.** `UP_RAW_EVENTS.EVENT_DATA` and `UP_ANALYTICS_EVENTS.EVENT_DATA` are `text` columns that hold LOB OIDs. After one portal run, `vacuumlo -n` reported it would remove 720 large objects that are still in use. `PREF_VALUE` is `varchar(100000)`, so it is unaffected today. Pin explicit column types, and warn adopters now (this already affects 5.17). |
| MOD-I25 | Persistence | `PortletPreferenceImpl.PREF_VALUE` is mapped as `@Lob` plus `@Type(StringType)`. Hibernate 6 removes that override, so the column would be treated as a CLOB. | Map it explicitly (`@JdbcTypeCode` / `columnDefinition`) so the existing column type stays the same. |
| MOD-I26 | Persistence | Hibernate 6.x/7.x change several defaults: native timezone storage (6.2), native booleans on Oracle 23, sequence naming (6.0), and the increment-size check (5.4). Each has a legacy setting. | Add a settings block in `hibernate.cfg.xml` that freezes 5.x behaviour, then remove settings one at a time behind tests. |
| MOD-I27 | uPortal-start | HSQLDB 2.5.1 (uPortal-start) is too old for Hibernate 6.2+, which needs 2.6.1+. uPortal itself uses 2.7.4. | Upgrade the uPortal-start HSQLDB with the toolchain work (WS2). |
| MOD-I28 | Tooling | The OpenRewrite Spring and Hibernate recipes are under the Moderne Source Available License, not Apache. They cover only mechanical renames, with nothing for 4.x→5.x or 7.2+. The `web.xml` recipe regex-replaces every `javax.`, including `javax.portlet`. | Use them as local tools only, and review every diff. Use Eclipse Transformer with custom rules for the namespace switch. |
| MOD-I29 | Spring | Spring Security 7's namespace handler rejects versioned XSDs older than 7.0. 3 files use `spring-security-4.2.xsd`. XML `request-matcher` only accepts `path` / `regex` / `ciRegex`. | **Fixed** on Spring 4.3: every context refers to the unversioned schemas ("refactor(spring): reference the unversioned spring xml schemas", MOD-D38). Switch to the unversioned XSD early. This is safe on 4.2. |
| MOD-I30 | Spring | From Spring 6.1, `-parameters` is required, or name-based XML `<constructor-arg name=...>` and implicit `@RequestParam` / `@PathVariable` names fail at runtime. | Add `-parameters` to the compiler options in phase 1. It is harmless on Spring 4.3. |
| MOD-I31 | Spring | Spring 7 makes Jackson 3 (`tools.jackson`) the default. Jackson 2 support is deprecated in 7.0 and slated for removal in 7.2. uPortal uses Jackson 2 in UserTypes, REST and JSON views. | Move to Jackson 3 in the Spring 7 step. Keep the persisted JSON format byte-compatible (`JacksonType` columns). |
| MOD-I32 | Spring | Spring Session 1.3 → 2.0 changes the JDBC schema and the module layout. uPortal uses the data-redis store. | Sessions are transient. Document that they are dropped at upgrade. |
| MOD-I33 | CI | `actions/setup-java@v6` removed the `adopt-hotspot` distribution that both CI matrices use. | Switch to temurin, zulu and corretto in the phase 1 CI change. |
| MOD-I34 | Logging | Possible latent bug, still to verify: SLF4J 1.7.36 is declared, but Logback 1.6.3 needs SLF4J 2.0.x. If 1.7.36 wins resolution, logging can silently do nothing. | Check with `dependencyInsight`, then fix in its own PR. |
| MOD-I35 | Runtime | Tomcat 9.0.x support ends 2027-03-31. A 9.1.x branch (no APR) gets releases until 2030-12-31. Tomcat 11.0.x needs Java 17. | uPortal-start moves to 9.0.x now (javax), then straight to 11 together with the Jakarta switch. |
| MOD-I36 | uPortal-start | No CAS server can share a Tomcat with javax uPortal: CAS 3.6 can't run on Jakarta Tomcat, and current CAS (7.3/8.0) is Jakarta-only. | Candidate: default to uPortal's local login (`SimpleSecurityContextFactory`, already enabled) and make CAS opt-in. CAS would run as a separate CAS Initializr overlay or container (MOD-Q3). |
| MOD-I37 | Search | Lucene 8 → 10.5 needs Java 21 and a reindex. Lucene 11 will need Java 25. | Rebuild the index on first start after upgrade. Document it. |
| MOD-I38 | Libraries | person-directory 3.0.1 (Spring 6, Java 17) has been dormant since 2022. jasypt has been unmaintained since 2019. commons-fileupload2 is still milestone-only. | Watch list. Plan Apereo-side releases or replacements. |
| MOD-I39 | Caching | Ehcache 3 has no JGroups/RMI replication. It clusters only via Terracotta. Infinispan (`infinispan-hibernate-cache-v66`, which covers Hibernate 6.6 and 7.1+) uses JGroups with invalidation, so it is the closest match to Ehcache 2 + JGroups. Caffeine is local-only. | Proposed: use Infinispan in clustered mode for Hibernate L2 (Caffeine or JCache for single-node), and JCache for Spring cache. Needs a spike. |
| MOD-I40 | Web Flow | Spring Web Flow 4.0.x (Spring 7) is maintained, but has had no portlet integration since 2.5. Liferay PortletMVC4Spring has a `webflow` module for Spring 6.2 only. | The 26 admin portlet flows need a forked portlet integration or a rewrite. Write Playwright specs for them first (WS3). |
| MOD-I41 | Tests | uPortal-start PR #692 (May 2026) reported "99 passed / 7 failed" for Playwright. | P0 has to make the baseline green before it can be a required check. This session's baseline run is checking it. |
| MOD-I42 | Spring | Jackson 3 changes default property ordering and date output. | Use a mapper configured with Jackson 2 defaults for `JacksonType` columns and the REST payloads. Add golden-file tests. |
| MOD-I43 | Persistence | Hibernate 7.4 needs at least PostgreSQL 14, MariaDB 10.6 and SQL Server 2014. | Adopter prerequisite. State it in the ADRs ([`docs/developer/adr/README.md`](../adr/README.md), ADR 0009) and in UPGRADING-6.0. |
| MOD-I44 | Security | cas-client 4.1 needs Java 21 and Servlet 6.1. | Use 4.0 at the Spring 6.2 checkpoint and 4.1 with Spring 7. |
| MOD-I45 | Logging | Spring 7 replaces `spring-jcl` with commons-logging 1.3, and uPortal bans commons-logging. | Revisit the exclusions and `jcl-over-slf4j` in the Spring 7 PR. |
| MOD-I46 | Runtime | Tomcat's newer defaults are lower: `maxParameterCount` is 1000 and `maxPartCount` is 50. | Set the connector values explicitly in uPortal-start. Add a large-form permissions spec. |
| MOD-I47 | Correction | The inventory said `XStreamMarshaller` and `PropertyPlaceholderConfigurer` were removed in Spring 6. That is wrong: both still exist in Spring 7.0. | Neither is a blocker, but still handle the placeholder escaping in 6.2 and the XStream `--add-opens`. |
| MOD-I48 | Portlets | PortletMVC4Spring 5.2.0 already runs on Pluto 2.1 in uPortal-start (SimpleContentPortlet PR #550). So the "Spring 5 + Portlet MVC replacement" step is split: first vendor Spring Portlet MVC 4.3.30 on Spring 4.3, then adapt it inside the Spring 5.3 bloc. | Supersedes the second atomic step in WORKSTREAMS.md. |
| MOD-I49 | Portlets | The javax→jakarta switch is atomic across repos. It covers uPortal, uPortal-start `shared/lib`, and every deployed portlet WAR, because a mixed shared classloader cannot work. | The WAR transform pipeline (WS9) is on the critical path before the switch. |
| MOD-I50 | Persistence | **Found by the prototype.** Since the Hibernate 4.3 upgrade, `hibernate*.cfg.xml` (via `hibernate.ejb.cfgfile`) is parsed through StAX. On the portal classpath that is Woodstox, which ignores Hibernate's local DTD resolver, and every startup then downloads `http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd`. Portals without access to hibernate.org fail to start, and 60 JPA tests fail offline. | **Fixed** in commit `5007322bc` (removed the DOCTYPEs, plus a Woodstox offline-parse regression test). Upstream candidate for 5.17.x. |
| MOD-I51 | Build | `com.github.ci-rt:google-visualization-java:release-1.1.3` exists only on jitpack, which is a supply-chain and availability risk. The sandbox blocks jitpack, so the prototype installed the jar from the official uPortal-webapp 5.17.9 WAR into `~/.m2`. | Replace it with a Central artifact, or vendor it. Track in WS12. |
| MOD-I52 | Environment | The sandbox gets HTTP 429 from Maven Central under load, and Gradle 6.9 doesn't retry. | Prototype-only workaround: an init script pointing to Google's Central mirror (scratchpad, not committed). |
| MOD-I53 | Runtime | `tomcatStop` leaves Tomcat running: two uPortal-owned worker threads (`pool-19-thread-1/2`) are never shut down, and Tomcat logs leak warnings naming the uPortal webapp. CI never notices. | **Fixed** in `0745364ca`. `PersonLookupHelperImpl` started up to 10 unnamed worker threads and never shut them down. One people search was enough to hang shutdown. The pool now closes with the Spring context, and its threads are named. Verified on a real portal with JDK 11 and 25. Upstream candidate for 5.17.x. |
| MOD-I54 | Tests | The web-proxy "no critical JavaScript errors" test passes by accident. It waits for "Example Domain", which matches the portlet title "Example Domain (test proxy)", not the proxied page. | Tighten the selector (P0-1). |
| MOD-I55 | Tests | The sandbox blocks three Playwright tests: web-proxy can't reach `example.com`, and two visual-resource-server tests fail because CKEditor 4.22.1's version check calls `cke4.ckeditor.com`. Feedback "renders feedback form" is flaky (post-login navigation race, 1 of 2 runs). | Sandbox check = all specs except those 3 environment-blocked tests. Consider `versionCheck: false` for CKEditor (it also stops nagging adopters). Fix the feedback race in P0-1. |
| MOD-I56 | Rendering | **Found by the new specs.** A focused transient portlet (`/p/<fname>`) is kept in session state, and every `*.uP` request resets it (`UserLayoutParameterProcessor` → `TransientUserLayoutManagerWrapper.setFocusedId(null)`). So a late AJAX request from the previous page (emergency alert, calendar, file manager) makes the next render come back without the portlet. This caused the feedback/calendar Playwright flakes. | The tests now wait for in-flight `*.uP` requests to settle. `loginViaPage` did not until uPortal-start 1c616b75e, and `cas-proxy-test.spec.ts` failed once that way (Integration job 114444272171). The underlying race is a product bug: upstream candidate, fix after 6.0. |
| MOD-I57 | Admin UI | **Found by the new specs.** User Administration "Create a User" saves, but the next page throws an NPE. A null lookup for the new username is cached for 15 minutes, so the account can't be opened and edited attributes look stale. | The spec flushes that cache through Cache Manager. Upstream bug. |
| MOD-I58 | Admin UI | **Found by the new specs.** The editPermission breadcrumbs are action URLs used as plain links, so following one returns HTTP 405. Import/Export `delete.jsp` has broken markup that leaves the form covered and unclickable. | The specs work around both (keyboard submit). Upstream bugs. |
| MOD-I59 | API | `/api/entity` cannot delete groups or local accounts. | Spec cleanup goes through the UI. Possible API gap. |
| MOD-I60 | Portlets | **Spike B3 (Tomcat 11 only, critical).** 13 of 16 WARs bundle JSTL 1.1.2, which uses `javax/jakarta.servlet.jsp.el`, removed in Pages 4.0. Any JSP using `<c:set>` fails on Tomcat 11. Tomcat 10.1 is fine. | P5: upgrade JSTL to Apache Taglibs 1.2.5 / Jakarta Tags in uPortal and every bundled portlet before P7-1. |
| MOD-I61 | Portlets | **Spike B4 (Tomcat 11 only).** Spring 3.x WARs (Feedback, CAS 3.6, ResourceServingWebapp, esup-filemanager) fail on Tomcat 11 for the same reason. | P5: upgrade or retire them. A shared/lib jar that restores the removed `jakarta.servlet.jsp.el` classes worked in the spike. |
| MOD-I62 | Portlets | **Spike B2 (Tomcat 11 only).** NotificationPortlet (Spring Boot 1.5) fails to deploy, because Boot references `AbstractHttp11JsseProtocol`, which Tomcat 11 removed. | P5: one property that excludes Boot's embedded-container auto-config. |
| MOD-I63 | Runtime | **Spike B5 (JDK 25, critical).** JDK 25 disables XSLT extension functions by default, and uPortal's theme pipeline depends on them, so the portal is down on JDK 25. JDK 25 also tightens XML entity and depth limits. | **Fixed** in `9a2eea868`. `TemplatesBuilder` now enables `http://www.oracle.com/xml/jaxp/properties/enableExtensionFunctions` on its factory only, and the processor is the JDK's built-in XSLTC (`xalan/java` extensions). The fix was verified on JDK 11/17/21/25, and the guest and admin pages render on JDK 25 without the JVM flag. uPortal's XSL and the Woodstox-based import aren't affected by the JDK 25 XML limits. Upstream candidate. |
| MOD-I64 | Tooling | **Spike B1.** `icu4j-2.6.1.jar` (in Calendar and NewsReader) contains a corrupt class. The migration tool aborts the whole WAR because of it, so Tomcat's `legacyAppBase` deploy-time conversion can't handle these two WARs. | The transform pipeline needs a per-WAR `-exclude` list and must fail the build on a non-zero exit. |
| MOD-I65 | Tooling | The jakartaee-migration default TOMCAT profile also renames `javax.persistence`, `javax.transaction`, `javax.ejb`, `javax.mail` and the annotation classes. Each WAR is still self-consistent, so it works. A narrow profile needs about 20 lines of Java (`NarrowMigrate.java`), and it passed too. | P5-5: use the narrow profile. It rewrites fewer classes and leaves `javax.persistence.*` property keys unchanged. |
| MOD-I66 | Tooling | A removed-Servlet-API scanner must check linkage, not grep method names. The hard failures came from removed *classes* in signatures and exception tables, reached through Jasper tag introspection and Tomcat internals. | Prototype `JakartaLinkScan.java` (in research/spike-jakarta/scripts). Class-level hits predicted every hard failure. |
| MOD-I67 | Correction | MOD-I36 said CAS 3.6 cannot run on Jakarta Tomcat. In fact, once transformed it runs on 10.1 as-is, and on 11 with the shared/lib jar that restores the jsp.el classes. | CAS 3.6 can be a stop-gap through P6. Making CAS opt-in or external (MOD-Q3) is still right because CAS 3.6 is EOL. |
| MOD-I68 | Runtime | Tomcat 10.1/11 silently ignore `compressableMimeType` (uPortal-start `server.xml`) and `disableURLRewriting` (Feedback). Several WARs bundle `spring-test` and two JSTL implementations. | Fix in the P6-2 overlays. Dependency hygiene in P5. |
| MOD-I69 | Runtime | `DatabaseHealthChecker` creates a new thread pool on every health check and never shuts it down. | Small upstream fix. |
| MOD-I70 | Tests | Six uPortal-webapp tests create their own TransformerFactory (e.g. `XsltPortalUrlProviderTest`, `PortletDataUpgradeTest`), so they still fail on a JDK 25 test JVM. | Route them through `TemplatesBuilder` or enable the feature in test setup when the CI test JDK moves to 25. |
| MOD-I71 | Runtime | The person lookup workers never clear the request data they are handed, so idle threads keep a reference to the last request. | Minor leak. Upstream fix later. |
| MOD-I72 | Persistence | **Found by the P0-4 harness.** Master (Hibernate 4.3, unreleased 5.17.10) already differs from released 5.17.9 (Hibernate 4.2) on the same schema. The structure and constraint names are the same, but `UP_SEQUENCE` counters come out at 110 vs 60 (POOLED became POOLED_LO in #3012), `UP_PORTLET_PREF_SEQ` at 131 vs 121, and the PostgreSQL dialect changed from 82 to 81. | Check that 5.17.9 → 5.17.10 is safe for existing counters before 5.17.10 is released. Re-record the baselines from 5.17.9 if the harness should check the upgrade from the last release. |
| MOD-I73 | Persistence | On MySQL and MariaDB, 5.17 needs `lower_case_table_names=1` (the import scripts use `up_group`). MariaDB Connector/J needs `useMysqlMetadata=true`. On MariaDB, the dialect depends on the driver: through MySQL Connector/J, 10.6 gets `MySQL5InnoDBCompressedDialect` and `ROW_FORMAT=COMPRESSED` tables, while 11.4 doesn't. | Document this as adopter configuration. Hibernate 6/7's MariaDB dialect detection changes it again, so the harness has to cover both drivers. |
| MOD-I74 | CI | No GitHub Actions run has validated any modernization change yet. Both repos' `CI.yml` only trigger on `push`/`pull_request` to `master`, so pushes to the `ccr-*` branches don't run CI. Local required checks and static checks (actionlint and a YAML parse for `db-harness.yml`) are all that has run. | **Addressed:** commits `2a2289c3e` (uPortal) and `77d75ab28` (uPortal-start) add a prototype-only `ccr-*` push trigger. Both commits are marked to be dropped before upstreaming. CI now runs on every integration push. The DB harness workflow is left out until MOD-I72's baseline question is answered. **First real runs green (2026-10-10):** uPortal CI run 38055220426 passed all 5 matrix jobs (ubuntu temurin/zulu/corretto, windows, macos) with the DTD, XSLT and shutdown fixes, and so did run 38055604902. uPortal-start CI run 38055325542 passed (5 build jobs plus Playwright, including the new admin specs and the port changes against released 5.17.9). Note: superseded queued push runs get cancelled by the concurrency group, which is expected. |
| MOD-I75 | CI security | zizmor 1.30.1 (offline) flags every workflow in both repos: `unpinned-uses` (high) on every action, `artipacked` (checkout persists credentials), and `excessive-permissions` (top-level `contents: write` in uPortal-start `CI.yml`). The new `db-harness.yml` has the same findings. | CI-hardening commit per repo: pin actions to commit SHAs with version comments (Renovate updates the digests), `persist-credentials: false`, least-privilege `permissions:` (top-level `contents: read`; `contents: write` only on the dependency-submission job), and a zizmor + actionlint workflow so findings stay at zero. **Done:** `ebb11e0e2` (uPortal) and `ba38035e9` (uPortal-start). zizmor doesn't report any findings with the regular and pedantic personas (service images pinned by digest too). Workflow lint (zizmor online audits + actionlint) passed on GitHub for both repos. Also found: gradle/actions v6 defaults to a commercial cache provider, so both repos now set `cache-provider: basic`. Upstream candidate. |
| MOD-I76 | Environment | The prototype container has about 40 GB of writable allowance. Each uPortal-start worktree with a deployed portal takes 2 to 6 GB (Tomcat with ~16 exploded webapps, overlay WAR builds, HSQLDB, node_modules), and the shared Gradle home with four Gradle versions about 4.6 GB. The disk hit 99% twice. | Worktrees are removed right after merge. Unused Gradle distributions, DB images and volumes, and spike copies get pruned. Agents remove the containers and images they started, and `scratchpad/disk-guard.sh` must pass (≥ 6 GB free) before heavy steps. At most two portal-running agents at a time. |
| MOD-I77 | Correction | The research report says Tomcat's `maxParameterCount` default is 1000. Tomcat 9.0.122's code default is still 10000. The 1000 is set by the `server.xml` in its distribution. FeedbackPortlet's `disableURLRewriting` is already ignored on 8.5. | uPortal-start now sets `maxParameterCount=10000` and `maxPartCount=50` explicitly. |
| MOD-I78 | Data | NewsReader `dataExport` fails because a `.crn` file is missing from the portlet WAR. Pre-existing. | **Fixed in uPortal-start** (210a54842): the NewsReader overlay adds the missing export script. The fix in the portlet repository stays for 6.x (MOD-D13). |
| MOD-I79 | Dev DB | The HSQLDB `uPortal.lobs` file grows past 3 GB over repeated `dataInit` runs. | Have `dataInit` (or a clean task) remove the HSQLDB files. Small uPortal-start fix. |
| MOD-I80 | Runtime | Since JDK 24, `jaxp.properties` disables XSLT extension functions. `TemplatesBuilder` is fixed in code (MOD-I63), but 7 tests that build their own TransformerFactory need `-Djdk.xml.enableExtensionFunctions=true`, which Phase 1 sets on every test JVM. | Before the runtime moves to JDK 25 (by P6, MOD-D6), route those through `TemplatesBuilder` (MOD-I70) so adopters don't need the JVM flag. |
| MOD-I81 | Environment | The sandbox egress policy blocks `getbootstrap.com` (and `blog.getbootstrap.com`), including the LLM docs at `https://getbootstrap.com/llms-full.txt`. | The block wasn't bypassed. The same content was rebuilt from the official source repo on GitHub (`twbs/bootstrap` main @ `5c8346d`, package version 6.0.0-alpha.1): `site/src/pages/llms-full.txt.ts` concatenates the sidebar-ordered MDX docs, and a scratchpad script mirrors that by reading the files without running repo code. Result: 140 pages, 1.3 MB, at `scratchpad/bootstrap-docs/llms-full.txt`. WS14 and skin agents use it together with the Bootstrap skills. CI and developers can fetch the canonical URL directly. |
| MOD-I82 | Concurrency | **Found by multi-DB CI.** `JpaClusterLockDao.getClusterMutex` reads in the caller's transaction, creates the mutex in REQUIRES_NEW, then re-reads in the caller's snapshot. Under InnoDB REPEATABLE READ (the MySQL/MariaDB default) the new row isn't visible, so it throws "Failed to find or create ClusterMutex". 5 tests fail on mysql-8.4 and mariadb-11.4 but pass with READ COMMITTED. Real portals on MySQL/MariaDB hit this the first time each mutex is used. | **Fixed** (e9d76ba87): `getClusterMutex` finds or creates the row in its own REQUIRES_NEW transaction (and looks again in a new one after losing a creation race). `getLock`/`updateLock`/`releaseLock` call it before opening their own transaction and only read inside it. A locking read was rejected because PostgreSQL REPEATABLE READ and Oracle SERIALIZABLE still read from the snapshot. If an insert loses the race, it now rolls back explicitly; before, the DAO could return an id that was never committed. Tests: an HSQLDB MVCC/REPEATABLE READ regression test (all 4 cases fail on the old code) and `testLostCreationRace`. `testConcurrentLocking` now uses the default 60 s abandonment window, because MySQL stores lock timestamps in whole seconds (978fe4168). Upstream candidate. |
| MOD-I83 | DB harness | HSQLDB 2.7.4 (uPortal-start P1) names constraint-backing indexes after the constraint, where 2.5.1 used `SYS_IDX_*`. That shows up as 111 NAMES drift; STRUCTURE, DATA and GENERATORS are clean. | **Fixed** (uPortal-start 95ab621df): re-recorded; only names changed (111 constraint-backing indexes: 67 FK, 42 UK and 2 named). The harness doc records this as the one exception to "never re-record 5.17". |
| MOD-I84 | DB harness | Because Oracle sequences are cached, the current values read from metadata vary between runs (e.g. 1201/546/931 against a max id of 403). | **Fixed** (uPortal-start ac964c280). Only Oracle checks value > max(id) and increment = allocationSize. The other vendors are still compared exactly (no other vendor showed changing values). |
| MOD-I85 | uPortal-start | The CAS overlay hard-coded HSQLDB in `deployerConfigContext.xml`, so CAS login failed on other databases. The harness now drives it from `-PdbVendor`, but adopters switching databases still have to edit it by hand. | **Closed** by MOD-D40: uPortal-start no longer has a CAS data source. |
| MOD-I86 | Tests | `JpaVersionDaoTest` used `ALTER TABLE` forms that SQL Server and Oracle reject. | **Fixed** (portable DDL). No test is vendor-skipped. |
| MOD-I87 | Portlets | `ScopingPortletSessionImpl`'s SPR-9287 workaround matched on Spring's `DispatcherPortlet` class name. It serves every portlet, so after vendoring it has to match both names (uPortal's vendored class and Spring's class inside external portlet WARs). | **Fixed** in P3-1. Without the fix it would have stopped working silently for uPortal's own portlets. |
| MOD-I88 | Portlets | Web Flow 2.4.8 recognises portlet contexts only by Spring's own context class. Harmless today, because the flow executor is in the root servlet context. | Goes away when the flows are replaced (MOD-D18). |
| MOD-I89 | Spring | `FixedXMLEventStreamReader` has to stay in `org.springframework.util.xml`, because it extends a package-private Spring class, so MOD-I12 isn't fully closed. | Revisit at P4-1/P6. Replace with a composition-based reader if Spring changes the base class. |
| MOD-I90 | Runtime | Race: when a re-login swaps the session mid-render, `PortletPreferencesFactoryImpl.isGuestUser` can throw an NPE. Seen once. The baseline hit a similar race in a different class. | Related to MOD-I56 (transient focus state). Track it, and add a test when the rendering code is reworked. |
| MOD-I91 | uPortal-start | `dataImport` always fails, because `playwright.gradle` looks up a task that the overlay skips for that task name. The workaround is to invoke it as `dataImp`. | **Fixed in uPortal-start** (f7a29cdf1): `playwright.gradle` looks the skin task up with `findByPath`, so `dataImport` works under its own name. |
| MOD-I92 | Portlets/data | Bookmarks, Feedback and Notification create their tables on first start (hbm2ddl). That means a target database needs `dataInit` plus one portal start before the copy tool can fill it. | Document this in the copy-tool runbook. Long term, provide portlet DDL or let the tool create the portlet schema. |
| MOD-I93 | Portlets/data | The portlets share a `hibernate_sequence`, which is an auto-increment column on MariaDB. | Handled by copy-tool rules in uPortal-start. |
| MOD-I94 | Portlets/data | Notification and SimpleContent also store data in PostgreSQL OID LOB columns, so the `vacuumlo` risk (MOD-I24) extends to portlet tables. | Include them in the adopter warning. The copy tool handles them. |
| MOD-I95 | Persistence | On MySQL/MariaDB, 5.17 timestamp columns have no fractional seconds (34 columns), so PostgreSQL → MariaDB loses sub-second precision. The tool reports it. | The 6.0 schema uses `DATETIME(6)` / `TIMESTAMP(6)` on MySQL/MariaDB. |
| MOD-I96 | Tooling | With externally supplied drivers, a driver also bundled in the jar caused a sealed-package error, and `DriverManager` rejected the class-loader-loaded drivers. | **Fixed:** drivers stay out of the jar and are instantiated directly. |
| MOD-I97 | Persistence | **Found by the harness on Oracle.** `SQLNextExceptionLoggerAspect` called `initCause` on Oracle's `BatchUpdateException`, whose cause is already set (to null). That threw `IllegalStateException` and hid the ORA-00001 constraint error, so two duplicate-name portlet-list API specs got 500 instead of 400. | **Fixed** (7ded7362d): the aspect logs the next exception instead of chaining it. A unit test reproduces the exact CI error on the old code. Upstream candidate. |
| MOD-I98 | CI | uPortal CI run 38072243202 (179e11d66) failed on Windows only, because Maven Central returned HTTP 403 for `spring-core-7.0.9.pom` while resolving the copy tool's Spring Boot 4 dependencies. The next run, with the same build files, was green on all 8 jobs. On the prototype branch, setup-gradle's cache is read-only (it writes only on the default branch, which the fork never builds). Every job then downloads everything from Central and is exposed to its rate limits. | The cause is external and doesn't need a code change. If it recurs, let setup-gradle write the cache on push builds of the integration branch (push events only, never PRs, to avoid cache poisoning), and recheck with zizmor. Upstream runs on `master` get a written cache and are unaffected. |
| MOD-I99 | Concurrency | **Found by the runtime matrix.** MariaDB 11.6+ defaults to `innodb_snapshot_isolation=ON`, so under REPEATABLE READ an update of a row that changed after the transaction read it fails with "Record has changed since last read in table 'UP_MUTEX'". `JpaClusterLockDao` locks optimistically (read, then update where the version is unchanged), so a server that lost the race for a mutex got that error instead of no lock. `testConcurrentLocking` failed on MariaDB 12.3, the 6.0 runtime (runs 38075062798, 38076433637). | **Fixed** (05f40ce45): the DAO's own transactions run under READ COMMITTED. A lost race is then a version mismatch again. `testLockTakenBetweenReadAndUpdate` fails on the old code on MariaDB 12.3 (run 38079129765) and passes on all five servers (run 38079947750). Upstream candidate. Other read-then-update code on MariaDB 11.6+ needs the same review. |
| MOD-I100 | api-rest | **Found by SpotBugs.** `EntityService.java:221` uses `&&` where `\|\|` is meant, so a null entity type with an ID set throws an NPE at :225. | **Fixed** (8dcab6efd): returns null when the type is unknown or the ID is blank (`\|\|`). `EntityServiceTest`: the unknown-type and null-type cases threw the NPE at :225 on the old code. Upstream candidate. |
| MOD-I101 | io-types | **Found by SpotBugs.** `ExternalPortletDefinitionUnmarshaller.java:243` compares `Calendar.equals(Date)`, which is always false, so lifecycle entries meant to get the current time keep the placeholder date on import. | **Fixed** (20b19918e): compares the Calendar's instant (`getTime()`) with the placeholder. `ExternalPortletDefinitionUnmarshallerTest` imports a MAINTENANCE entry with the placeholder. On the old code the entry kept 1999-12-31 and sorted before PUBLISHED, and the portlet left maintenance mode. Upstream candidate. |
| MOD-I102 | rdbm | **Found by SpotBugs.** `EntityManagerCacheImpl.java:213–232` logs that its per-thread map is missing, then dereferences it anyway (NPE). | **Fixed** (5ab591f23): returns after logging (the cache purge still runs first). `EntityManagerCacheImplTest`: all 3 cases threw the NPE on the old code. |
| MOD-I103 | core | **Found by SpotBugs.** `PropertiesManager.java:85–91` publishes an empty `Properties` before loading it, while `getProperty` (:110, :129) initialises lazily without a lock. Concurrent first calls can get a spurious `MissingPropertyException`. | **Fixed** (044e79e0f): loads into a local `Properties` and publishes it into a volatile field under a private lock, read through a double-checked `getProps()`. `setProperties` uses the private lock too. `PropertiesManagerConcurrencyTest` holds the first caller inside the load (its own class loader serves a blocking portal.properties stream): the old code failed 20 of 20 runs with the spurious `MissingPropertyException`, the fix passes 20 of 20. Upstream candidate. |
| MOD-I104 | core | **Found while tracing MOD-I90.** `BasePersonManager.java:158` reads the `guestUserIds` HashMap without the lock its writer holds. | **Fixed** (f213dcad5): a `ConcurrentHashMap`, and writes are still made under the lock. `BasePersonManagerTest` (8 threads, 40 guests, 25 fresh managers; one lookup per guest; a missing guest account isn't cached) guards the behaviour, but the race needs a read during a resize and can't be forced, so it also passes on the old code. Related to MOD-I90. |
| MOD-I105 | utils | **Found by SpotBugs.** `ComparableExtractingComparator.java:53` throws an NPE when only the first value is null. `PortletWindowRegistryImpl.java:554`'s error message prints the null entity instead of its ID. | **Fixed** (f90ebac9e). A null comparable sorts first, in both argument orders, and the message prints `portletEntityId`. `ComparableExtractingComparatorTest` (3 of 5 cases threw the NPE on the old code) and `PortletWindowRegistryImplTest` (the old message said "for null"). |
| MOD-I106 | Build | uPortal-webapp's `npmInstall` still rewrites `package-lock.json`, because c0d9c16ca switched only the root build to `npm ci`. | **Fixed** (bddc4f315). The webapp is an npm workspace of the root, so its npmInstall is disabled and depends on the root's `npm ci`, which installs the webapp's packages too. (`npm ci` in the webapp directory would empty the root's node_modules and install 19 of 805 packages.) Before: `:uPortal-webapp:npmInstall` added 10 lines to package-lock.json. After: a clean `:uPortal-webapp:build -x test :npm_run_lint-js` leaves `git status` clean. uPortal-start's root node block still runs `npm install`, which rewrites its lockfile too (8 `peer` flags under npm 11). It is not changed here. |
| MOD-I107 | uPortal-start | With `org.gradle.parallel=true` (the default), `dataInit` ran the overlays' data tasks in parallel. On SQL Server, the portlets' concurrent Hibernate schema updates deadlock on the catalog. Calendar and Announcements were "chosen as the deadlock victim" (G-migrate run 38076314198). Any `portalInit` on SQL Server can hit this. Pre-existing. | **Fixed** (uPortal-start 015cbbc2f, comment fixed in 74721a63d): a shared build service with `maxParallelUsages = 1` runs the data tasks one at a time. |
| MOD-I108 | uPortal-start | On SQL Server the Feedback portlet never starts. It bundles Hibernate 3.2.7, which has `SQLServerDialect` but not the `SQLServer2005Dialect` that `global.properties` names for every portlet, so it never creates `FEEDBACK_STORE`. Every SQL Server database had 113 tables instead of 114, and 5 G-migrate pairs into SQL Server 2025 failed planning (run 38080746162). Pre-existing. | **Fixed** (uPortal-start b06d565f1): vendor files can set `portlet.<file>.<key>`, which `tomcatConfig` writes to `${portal.home}/<file>.properties`. The SQL Server vendors give Feedback `SQLServerDialect`. README tells adopters to do the same. |
| MOD-I109 | Copy tool | Copies from SQL Server into MySQL or MariaDB failed verification on 5 tables with timestamps (run 38080746162). The copy truncates a `datetime` such as `.997` to the target's whole seconds, but when a side was SQL Server `datetime` the hash put values on SQL Server's 1/300 s grid instead of the pair's lower precision. | **Fixed** (7710f8699): grid first, then the pair's precision. A unit test fails on the old code. |
| MOD-I110 | CI | GitHub keeps only the first 4096 characters of a workflow-command annotation, and 10 per type per step. Property values also end at `,` and `:`. A re-run attempt copies the jobs it does not repeat, without their annotations. | The harness splits snapshots into 4000-character notices (about 8 per vendor). `fetch-ci-snapshot.sh` reads every attempt, and titles are escaped. |
| MOD-I120 | Persistence | **Found by P2 on the upgrade path.** Hibernate 4.3 applied `@Type(nullSafeString)` to map keys as well as values, so existing rows store keys such as `_externalId`. 5.2 applies it to values only, so upgraded portals lost their tab externalIds (`/f/welcome` redirected to login). Fresh-install tests didn't notice, and the harness DATA check counts rows, not values (MOD-I126). | **Fixed in P2-2:** `@MapKeyType` on the four affected collections, plus stored-format tests. The dataExport data-equivalence check (MOD-D29 item 1) is the required check that catches this class of bug. |
| MOD-I121 | Tests | Hibernate 5.2's `BatchFetchQueueHelper` assertion fails at random under `-ea` when `PortletPreferencesImpl` is join-fetched. | Tests run with that assertion disabled, as production does. Recheck on 5.6/6. |
| MOD-I122 | Persistence | Rollback-only commits fail differently in 5.2. | `JpaClusterLockDao` rolls back explicitly. Keep MOD-I82 and reconcile with MOD-I99 (`05f40ce45`) at rebase. |
| MOD-I123 | Persistence | 5.2's defaults (dialects, naming, id optimizers) would change the schema. | Frozen in `hibernate.properties` (MOD-D33). The 6.0 schema and copy tool (MOD-D11) replace the freeze block later. |
| MOD-I124 | Build | Smaller 5.2 upgrade issues: `@Index` on a one-to-many is rejected, Javassist 3.22 fails on JDK 21, dom4j and Ehcache were each on the classpath twice, and the dialect lookup failed after the SessionFactory closed. | **Fixed in P2-2.** |
| MOD-I125 | Runtime | New 5.2 log noise: HHH000112, HHH000482, and empty `AggrEvents.AggrEvents.*` query-cache regions. | Quiet or fix in P4-3 (Hibernate 5.6 + JCache regions). |
| MOD-I126 | DB harness | Harness gaps: DATA counts rows only, `--skip-init` snapshots before deploying, the post-portal LOB report ignores `-PdbPort`, and `--portal` doesn't deploy resource-server. | DATA: `tests/db/equivalence.sh` (uPortal-start d2d0ce7fd, MOD-D29 item 1) compares exported values, in place and through the copy tool. The other three are small uPortal-start fixes. |
| MOD-I127 | Runtime | `TransientPortletEntityDao` throws an NPE on `layout.json` requests with user=null. It appeared in 3 runs after P2 was rebased onto `22bdf2b23` and in none of 6 before. The tests still pass. | Compare with a 4.3 portal on the same ccr commit to find whether P2 or a ccr change causes it (candidates: MOD-I82, MOD-I97). The HTTP replay workstream should also show it. |
| MOD-I130 | Security | **Found by WS14.** Every legacy endpoint under `/api` is exempt from CSRF (`PortalCsrfSecurityRequestMatcher` ignores `/api.*`), although browsers call them with the session cookie: `/api/layout` (add, move and remove layout nodes, favorites), portlet lists, permissions. The web-component channel spec adds and removes channels through `/api/layout` without a token. | **Fixed** (owner's decision, 2026-10-11: protect every endpoint and accept the break) by uPortal "fix(security)!: require the csrf token on the legacy rest apis". Every POST, PUT, PATCH and DELETE under `/api` needs the token. Exempt: requests with `Authorization: Bearer`, `/api/v5-5/oauth/token`, `/api/v5-1/userinfo`, the Swagger UI, `/Login` and `/Logout`. `/api/updatePermission` changed permissions on a GET and is POST now. Respondr pages have `_csrf` and `_csrf_header` meta tags (part of the theme cache key), and `up.csrfHeaders()` sends the header from modern-favorite.js, modern-layout-preferences-persistence.js, modern-fragment-permissions-manager.js, modern-uploader.js, modern-entity-selector.js, portal-analytics.js and the editPermission, deletePermission, ImportExport delete and Marketplace entry JSPs. Outside clients that now fail, both archived, so nobody will update them: uPortal-home `web/src/main/webapp/my-app/layout/services.js` (addPortlet, removeByFName, movePortletAjax with `useOldLayout` on) and `my-app/marketplace/services.js` (saveRating), and uportal-app-framework `components/portal/settings/services.js` (chooseSkin) and `components/portal/misc/services.js` (addToHome). Adopters still running them must change their copies to send the token or a Bearer token, or stop using those calls (UPGRADING-6.0.md). uPortal-web-components send Bearer tokens and keep working. Tests: `PortalCsrfSecurityRequestMatcherTest` runs Spring's CsrfFilter over 20 endpoint requests, and uPortal-start `tests/api/csrf.spec.ts` checks 15 endpoints (403 without the token, another status with it), another session's token, a Bearer token and the userinfo exemption. CI: uPortal CI 38118470010 (full matrix), uPortal-start CI 38118477579 and Integration 38118476392 (attempt 2; attempt 1's postgres-18 Playwright job 114413716154 failed with MOD-I192 and its re-run passed), all green. |
| MOD-I131 | Favorites | `FavoritesUtils.getDuplicateFavoritesByFNameToDelete`, behind the Options menu's "Remove from my favorites", compared fnames with `==` (also in the SpotBugs baseline as ES_COMPARING_PARAMETER_STRING_WITH_EQ). | **Fixed** ("fix(favorites): compare fnames by value when unfavoriting") with a regression test that fails on the old code. The baseline entry is dropped ("build(spotbugs): drop the fixed FavoritesUtils finding from the baseline"). Upstream candidate. |
| MOD-I132 | UI | **Found by axe in the WS14 Vitest browser tests.** Bootstrap 6.0.0-alpha.1's default tokens fail WCAG AA in light mode: links and primary buttons 3.56:1, danger buttons 4.41:1, subtle alerts 3.12:1 to 3.65:1. | The web components' shared stylesheet overrides `--link-color`, `--primary-bg`, `--danger-bg` and the alert text colour, with a contrast regression test ("feat(favorites): add up-favorites web component"). The skin migration (MOD-D21) needs the same overrides. Report it upstream to twbs. |
| MOD-I133 | Data upgrade | The XML import upgraders (`XsltDataUpgrader`) only run for data versions without an importer, so converting portlet-definition 4.0 to 4.0 can't be an import upgrader. The copy tool's rules (MOD-D28) rename, skip and transform tables and columns, but can't add rows or rewrite rows picked by content (a portlet type, a definition's descriptor and parameters). The MOD-D18 "import upgrader plus copy-tool rule" for Favorites isn't built. | Documented instead: import the Web Component type and then the converted definition. `dataImport` updates the definition by fname and keeps its ID and layout placements. Add a 6.0 portlet-definition data version with an upgrader, and a `convert-portlet` copy-tool rule, with the first batch of conversions. |
| MOD-I134 | DB harness | **Found by the hsqldb harness.** A quickstart data change moves DATA and GENERATORS. Publishing Favorites as a Web Component channel (uPortal-start "feat(data): render favorites as a web component channel") adds one `UP_PORTLET_TYPE` row (and sequence value) and two `UP_PORTLET_DEF_PARAM` rows. STRUCTURE and NAMES don't drift. Every later portlet conversion moves data the same way. | **Addressed (coordinator decision, 2026-10-11):** the 5.17 baselines stay as recorded. `tests/db/snapshots/5.17/intended-changes.txt` lists `UP_PORTLET_TYPE rows +1` and `UP_PORTLET_DEF_PARAM rows +2`, and `dbVerify` adds the deltas to every vendor's baseline before comparing (uPortal-start "test(db): let the harness accept listed data changes"). A found entry is a notice. Any other drift fails, and so does a listed change that a run doesn't show. Each later conversion adds its rows to the list. |
| MOD-I135 | CI | uPortal-start `CI.yml` runs the full Playwright suite against released uPortal 5.17.9. Data and specs that need unreleased uPortal fail there: with the WS14 data and specs (uPortal-start "feat(data): render favorites as a web component channel" and "test(e2e): cover the favorites web component and the channel type"), 5.17.9 has no `WebComponent` portlet, `/api/v6-0/` or `/webcomponents/`. There the Favorites channel shows an error and the seven new tests fail. `integration.yml` (uPortal built from source) runs them on the matching uPortal. | **Decided (coordinator, 2026-10-11):** CI.yml skips its released-uPortal Playwright job on every `ccr-*` branch (uPortal-start "ci(prototype): skip the released-uportal playwright run on ccr branches"). Integration runs the suite there against uPortal built from source. Ordering rule: a uPortal-start change that needs a new uPortal lands after that uPortal release, so upstream the job keeps running on master and pull requests. |
| MOD-I136 | Tests | uPortal-start `npm run lint` fails on `tests/db/migrate/matrix.mjs` (8 `no-undef` errors for `console` and `process`): `eslint.config.js` declares globals for TypeScript files only. The linter runs in `./gradlew playwrightRun`, which no workflow calls. | Add Node globals for `*.mjs` in `eslint.config.js`. |
| MOD-I137 | DB harness | **Found by the WS14 hsqldb harness run.** `dataInit` runs the import in a Spring context that also starts uPortal's scheduled event-aggregation jobs. On this loaded machine the context took 2 min 47 s to start, and the jobs ran after the tables existed. Before the snapshot they filled the date and time dimensions (365 and 1440 rows) and wrote rows to `UP_MUTEX` (4) and `UP_EVENT_AGGR_STATUS` (2). The harness reported these as DATA and GENERATORS drift. Faster runs (CI, the earlier local run) take the snapshot first. | Turn the aggregation jobs off in the import context, or snapshot right after the import ends. Harness owners (DB matrix). |
| MOD-I140 | Analytics | **Found by the analytics goldens.** With the portal JVM in a zone with DST, events in the repeated autumn hour are dropped from the sub-day aggregations. The date and time dimensions hold local wall-clock time, so 01:00 to 02:00 EST maps to the same aggregation row as 01:00 to 02:00 EDT, which is already closed. `LoginAggregationImpl` and the other aggregations log "is already closed ... will be ignored". In `aggregation/America_New_York.txt` the HOUR rows of 2025-11-02 sum to 6 logins while the DAY row counts 8. A UTC JVM is unaffected. | **Owner (2026-10-11): fix in 6.0.** Queued until P2 is merged, because it changes the event aggregation code that P2 touches. The fix stores the time dimension in UTC or with its offset, and re-records the New York golden on purpose. Characterised, not fixed: the goldens pin 5.17's behaviour. The fix belongs with the java.time move (UTC dimensions, or the offset in the time dimension) and re-records the New York golden on purpose. **Design chosen (option B, the UTC offset in each aggregation row's natural id). The schema part waits for the 6.0 schema step (MOD-D11).** A test that shows the repeated-hour gap is on the integration branch (`notes/persistence-fixes.md`). |
| MOD-I141 | Persistence | **Found by the entity round trip.** `java.util.Date` columns mapped as timestamps are written as JVM-local wall-clock time. The stored value depends on the JVM zone, and an instant in the repeated autumn hour reads back an hour late under America/New_York (2026-11-01T05:30Z, 01:30 EDT, comes back as 06:30Z). Affected: `ClusterMutex.lastUpdate` and `lockStart`, `PortletLifecycleEntryImpl.date`, `PortletList.createdOn` and `updatedOn`, `LocalAccountPersonImpl.lastPasswordChange`. The Jadira `DateTime` columns (`databaseZone=UTC`) are not affected. | **Owner (2026-10-11): fix in 6.0.** The columns listed above become `Instant` with `hibernate.jdbc.time_zone=UTC`, so they hold UTC clock time. The copy tool converts 5.17's local clock values using the source server's zone, given as an input. Values written in an autumn repeated hour are already ambiguous in 5.17. The conversion picks one reading, and `UPGRADING-6.0.md` documents it. Queued after P2. Characterised in `roundtrip/*-America_New_York.txt`. From P2 on, keep `hibernate.jdbc.time_zone` unset until these columns move to `Instant` stored in UTC, and re-record on purpose then. **Fixed (persistence-fixes branch).** The listed columns and `ClusterMutex.lockEnd` (MOD-I220) are `Instant` with `hibernate.jdbc.time_zone=UTC`. `TimeDimensionImpl.time` binds with the JVM zone (MOD-I221). The copy tool's `--target-profile 6.0` converts 5.17's values with `--source-zone`. A value in the repeated autumn hour takes the earlier offset (`UPGRADING-6.0.md`). The uPortal-start side (G-migrate and equivalence pass `--source-zone`) is the e2e agent's. The start marker waits for the 6.0 schema step. |
| MOD-I142 | Portlets | **Found by the query goldens.** `JpaPortletDefinitionDao.searchForPortlets(term, true)` upper-cases the term but not the name and title it is compared with, and does not escape `%` or `_`. The partial search (the groups `PortletDefinitionSearcher`) finds only upper-case names on HSQLDB, PostgreSQL and Oracle, and matches case-insensitively on MySQL, MariaDB and SQL Server. `STARTS_WITH` and `ENDS_WITH` searches also behave as `CONTAINS`, because the DAO wraps the term in `%` on both sides. | **Owner (2026-10-11): fix.** Compare `upper()` on both sides, honour starts-with and ends-with, and escape `%` and `_`. Re-record the query golden on purpose. Characterised per vendor (`QueryVendorDifferences`). Fix with `upper()` on both sides and an escape character when the portlet search is reworked, and re-record then. **Fixed (persistence-fixes branch).** `upper()` on both sides, starts-with and ends-with honoured, `%` and `_` escaped with `!`, `[` escaped on SQL Server only (MOD-I223). An exact search is case-sensitive on every vendor (an `upper()` query, then an exact filter in Java). The vendors agree in the query golden, and the `QueryVendorDifferences` entries are gone. |
| MOD-I143 | Persistence | **Found by the query goldens and the round trip.** `LocalAccountPersonImpl.attributes` is an eager `FetchMode.JOIN` bag over the indexed `values` list. A loaded person repeats each attribute once per value, and `JpaLocalAccountDaoImpl.getAllAccounts()` returns one row per attribute value (7 for 3 accounts). `getAttributes()` builds a map, so callers of the person API don't see the duplicates. Hibernate 6 de-duplicates root entities, so `getAllAccounts()` changes at P4. | Characterised. Production code doesn't call `getAllAccounts()`. At P4, re-record the golden with the de-duplicated list and note it here. |
| MOD-I144 | Persistence | **Found by the entity round trip on Oracle** (run 38107283955). Adopter-facing bug in 5.17 on Oracle: an owner whose element collection holds an empty string can't be deleted. Oracle stores `''` as NULL. In an element collection without uPortal's `nullSafeString` type, Hibernate 4.3 doesn't read an element or map entry whose value is NULL, so the value is missing after a reload. Hibernate then treats the collection as empty and doesn't delete its rows. Deleting the owner fails with ORA-02292. It affects `UP_SS_DESC_LAY_ATTR_ELMS` (a stylesheet descriptor with an empty target element name) and `UP_PORTLET_ENT__STATES` (a portlet entity with an empty window state). It also affects `UP_PORTLET_DEF_MDATA`, for a portlet definition with a localization whose name, title and description are all empty. Their portlet preferences and portlet types can't be deleted either while they remain. The other databases keep `''`. | The 5.17 mapping keeps the bug. On Oracle `EntityRoundTripGoldenTest` asserts which entity types `EntityManager#remove` can't delete after the round trip, and on the other databases that it deletes all of them. It also expects the missing entries. Its clean-up then deletes the element-collection rows with SQL. Not checked on Hibernate 5.2 yet: P2's Oracle run of the goldens shows it, because the assertion fails if 5.2 deletes the rows. Fix with `nullSafeString` or NOT NULL value columns plus a data migration, decided with the 6.0 schema (MOD-D11). **Update with P2 (Hibernate 5.2.18).** Oracle left the rows on 5.2 too (run 38111412577). Fixed in 6.0 by `ElementCollectionDeleteIntegrator`, landed with P2 (uPortal 632a3ee94). The round trip now asserts a clean delete on Oracle (run 38114017644). |
| MOD-I150 | Portlets | **Found by the Pluto test suite spec** (uPortal-start `pluto-testsuite.spec.ts`, MiscTest.checkContextServerInfo). `PortletContext.getServerInfo()` returned `Pluto/${pluto.version}`. uPortal-webapp's own `pluto-configuration.properties`, which shadows the jar's, kept the Maven placeholder, which the Gradle build did not fill in. | **Fixed** (d20564691): processResources fills in `plutoVersion`. `PlutoConfigurationTest` fails on the old file. Upstream candidate. |
| MOD-I151 | Portlets | **Found by the Pluto test suite spec** (SimpleAttributeTest.checkEnumerateAttributes). A removed portlet request attribute stayed in `getAttributeNames()`. Pluto removes with `setAttribute(name, null)`, and `PortletRequestContextImpl` stored the null. The check failed only when it ran after the removal, and the suite's reflection order changes with each JVM start. | **Fixed** (104858ea0): a null value removes the attribute. `PortletRequestContextImplTest` covers it. Upstream candidate. |
| MOD-I152 | Portlets | **Found by the Pluto test suite spec** (PreferenceCommonTest.checkGetNoValuePreference, in action and render). `PortletPreferences.getValues(name, def)` returned `{}` instead of `def` for a portlet.xml `<preference>` without `<value>` (PLT.17.1). | **Fixed** (b1c8ec22e): `getValues` answers the default for a descriptor preference without values, as Pluto's own container does. `PortletPreferenceImplTest` and `AbstractPortletPreferencesImplTest` cover it. Upstream candidate. |
| MOD-I153 | Portlets | **Found by the Pluto test suite spec** (PreferenceInActionTest.checkPreferenceValidator). `PortletPreferences.store()` never calls the portlet's `PreferencesValidator` (PLT.17.4). uPortal's preference classes bypass Pluto's `PortletPreferencesService`. | Open. The spec expects the check to fail and names this issue. Fix with the in-tree Pluto (P3-2, P6-1). Load one validator per portlet definition from the portlet application's class loader and call it before `storeInternal()`. |
| MOD-I154 | Data | **Found by the migration-equivalence check.** `dataExport` of the `layout` type writes to the database. `LayoutExporter` calls `getUserProfileByFname(person, "default")`, which creates a `default` profile for each user without one (seven `UP_USER_PROFILE` rows on the quickstart data). 5.17.9 and the current build both do it. A second export then lists profiles that the first did not. | The equivalence check exports twice with 5.17 and compares the build under test with the second export. Fix: look the profile up without creating it. An export must not write. |
| MOD-I155 | Data | **Found by the migration-equivalence fixture.** Importing a `portlet-definition` drops its `<user>` subscribers. `PortletDefinitionImporterExporter` imports `<group>` elements only, although it exports `<user>` elements. 5.17.9 does the same. | Open. The reference records 5.17's behaviour: the fixture's `<user>fixture-zoe</user>` is not exported. After a fix, re-record the reference. |
| MOD-I156 | Portlets | **Found by the Pluto test suite spec** (PublicRenderParameterTest, PLT.11.1.2). uPortal does not share public render parameters between portlet windows. The test portlet sets `public-render-param1` in its action, and both it and its companion declare that parameter. The redirect after the action has it as a private parameter of the test portlet (`pP_public-render-param1=Passed`), so it doesn't reach the companion portlet. `IPortletWindow.setPublicRenderParameters` has no caller, and `UrlSyntaxProviderImpl` encodes private parameters only. | Open, for P3/P4. A fix needs URL syntax for shared parameters and has to apply them to each window on the page whose portlet declares the same QName before rendering. The spec expects the miss by name and fails once uPortal shares them. |
| MOD-I157 | Portlets | **Found by the Pluto test suite spec** (Event Filter, Simple and Complex Payload Event tests). On Java 11 and later a portlet could not publish a JSR-286 event with a payload unless its WAR contained JAXB. `EventProviderImpl` created the `JAXBContext` under the portlet application's class loader, and the JDK no longer includes JAXB. uPortal dropped the event and logged "Event handling failed". The event phase never ran, and the companion portlet received nothing. | **Fixed** (3e4977d18): uPortal tries the portlet application's class loader first and then its own JAXB. `EventProviderImplTest` fails on the old code. Upstream candidate. |
| MOD-I158 | Portlets | **Found by the portlet modes spec.** cas-proxy-test-portlet declares EDIT in its portlet.xml, but its Spring `DispatcherPortlet` has no handler for edit mode. `/p/portlet-cas-test/max/render.uP?pCm=edit` shows uPortal's error portlet (`NoHandlerFoundException`). | A bug in the portlet WAR, not in uPortal. The spec expects the error portlet for that render by name. Fix it in the portlet's repository, or drop EDIT from its descriptor. |
| MOD-I159 | Portlets | **Found by the portlet modes spec.** Console errors in bundled portlets. (1) The portlets that are also in a region of admin's layout (feedback-navigation, legal-footer, notification-icon, portal-logo, waffle-menu) render twice on their `/p/` page with one window namespace. In config mode the second CKEditor finds its element taken (`editor-element-conflict`), and the second `customElements.define` of the portlet's web component fails. (2) `_googleAnalytics.scss` points at `icons/destroy.png`, which uPortal lacks (404 in google-analytics-config). (3) js-demo's quickstart content calls `up.fluid.reorderList`, and the portal does not load `up.fluid`. (4) The resource server bundles CKEditor 4.22.1, and CKEditor's online version check reports it as not secure wherever an editor loads. (5) uportal-home-page frames `www.apereo.org`, which sends `X-Frame-Options: sameorigin`. (6) Portlet Administration's table callback reads `api.page.info()` of a table that its `beforeunload` handler already destroyed, when a user leaves while the table loads. | Open. The spec lists (1) to (4) as exceptions, in the wording of Chromium, WebKit and Firefox (uPortal-start 966327fe0, after the WebKit job of the cluster branch failed on WebKit's wording), counts (5) as a message from another host, and ignores errors raised while a page unloads. Render a focused portlet once, or give the second copy its own namespace. The region portlets' CMS content loads its webjar with a classic `<script defer>`, which the second copy runs again. As a safety net for (1), load the webjar as a module script, which a page runs once per URL, or guard the define with `customElements.get(name)`. Add or drop the icon. Fix or drop js-demo's script. Upgrade CKEditor or set `versionCheck: false` (MOD-I55). Replace the framed site. Guard the table callback. |
| MOD-I160 | Copy tool | **Found by the copy tool's property tests (MOD-D29).** The load order breaks a foreign-key cycle one nullable key per round, but stopped after as many rounds as there are tables. A pair of tables that reference each other through three nullable keys each was refused as an unbreakable cycle, a plan error for a schema that loads. | **Fixed** (b09875d19): the bound is the number of keys. `LoadOrderProperties` checks random graphs of up to 12 tables. |
| MOD-I161 | Copy tool | **Found by the copy tool's property tests.** Fractional-second digits came from the driver's display size on MySQL and MariaDB, and MariaDB Connector/J sizes TIME(0) as 10 characters, read as TIME(1): a PostgreSQL to MariaDB copy with a TIME column failed verification. HSQLDB reports no DECIMAL_DIGITS for TIMESTAMP(0), which the tool read as 6 digits, so a copy into it was not reported as truncating. | **Fixed** (2e2276a3e): `information_schema.COLUMNS.DATETIME_PRECISION` on MySQL and MariaDB. The display size is the fallback on every vendor. |
| MOD-I162 | Copy tool | **Found by the copy tool's property tests.** `lob-oid` rules counted only from the schema profiles. A rule given with `--rules`, which the plan error for an undeclared OID column asks for, had no effect: from a PostgreSQL source the plan error came back, and into a PostgreSQL target the text went into the column instead of a large object. | **Fixed** (3487db560): a rule from `--rules` now applies to the source column, and to the target column under the source or the target table's name. |
| MOD-I163 | Copy tool | **Found by the copy tool's property tests**, which require every conversion that changes a value to be reported. The plan's notes (fewer fractional digits, MOD-I95; SQL Server's 1/300 s) reached only the JSON report, and two conversions had no note at all: '' into Oracle (stored as NULL) and DECIMAL into a binary double (about 15 digits). | **Fixed** (ce2133d0d, 137053530): the console summary lists the notes, and both conversions have one. |
| MOD-I164 | Copy tool | **Found by the fault-injection tests.** A target NOT NULL, length, unique or foreign key that the source rows violate failed the copy half-way with status 1, after the earlier levels and part of the table were committed, although status 2 promises a refused plan with nothing written. | **Fixed** (7691dadc3): the plan counts the violating source rows for each target constraint the source does not declare, and refuses with status 2. Lengths in bytes, collation-only duplicates and CHECK constraints are still found by the copy. |
| MOD-I165 | Tests | **Found by CI** (DB copy tool run 38099695386). `CopyFaultIT.killedTargetSessionsFailTheCopyAndARerunRecovers` injected its fault once the target had 200 rows. On MariaDB the copy finished first and the test saw exit 0. Killing sessions idle in the tool's pool also lets the pool reconnect. | **Fixed** (53be8c0f6): the four lost-target tests hold the copy on an uncommitted row with one of the source's keys and inject the fault once the copy waits for it. |
| MOD-I166 | Copy tool | **Found by the nightly SQL Server and Oracle legs** (DB copy tool runs 38106391935, 38108904235). Into an Oracle CLOB the tool wrote text with `setCharacterStream`. Oracle's driver converts it, as it does a `String`, in chunks of 32,768 characters, and a surrogate pair split between two chunks arrives as two U+FFFD: text over 32 KB with an emoji or another supplementary character in the wrong place. The verification caught it, so the copy failed instead of passing. The test fixture's own CLOB inserts were affected the same way. | **Fixed** (ea0bf5bb8): CLOB and NCLOB values go to Oracle with `setClob`/`setNClob`, which a probe on Oracle Free 23.26 showed to store every character as written. 5.17's own Hibernate writes to Oracle CLOBs were not checked. |
| MOD-I167 | Copy tool | **Found by the nightly legs.** Oracle stores an empty BLOB written with `setBytes` or `setBinaryStream` as NULL, as 5.17 does through Hibernate. The tool did not note it, and its verification failed every copy of an empty binary value into Oracle. The plan also called an Oracle CLOB "narrower" than its source, since the driver reports a CLOB as 4,000 wide. | **Fixed** (ea0bf5bb8): empty binary values into Oracle are a noted conversion and verify as NULL, as '' does. The narrower-column note skips CLOBs. |
| MOD-I168 | Tests | **Found by the nightly leg with a random seed.** HSQLDB 2.7 keeps a TIMESTAMP WITH TIME ZONE before 1582-10-15, written with `setObject`, shifted by the Julian calendar's days (1000-01-02 read back as 0999-12-28). No setter avoids it. | The type properties' zoned values start in 1583 (d1587816d). The copy tool is not affected by this: it copies what the source returns. Zoned values before 1583 in an HSQLDB source are not tested. 5.17 has no zoned columns. |
| MOD-I169 | Tests | **Found by the nightly legs.** mssql-jdbc sends a time as a datetime by default (`sendTimeAsDatetime=true`), which rounds 14:48:52.718967 to .720 and 23:59:59.999999 to midnight. The test's SQL Server source rows lost their TIME values before the copy. | The fixture writes SQL Server TIME values as text (d1587816d). **The copy tool was affected too.** It wrote times with `setObject`, so a PostgreSQL to SQL Server 2025 copy changed 17 values. The verification caught them. **Fixed** (8a214d35a): times go into SQL Server TIME columns as text. |
| MOD-I170 | Environment | **Prototype machine only.** The session worker restarted at about 23:06 UTC on 2026-10-10. It stopped the five local agents, the Docker daemon and the local landing run. Their worktrees kept all files. | Docker was started again by hand (`dockerd`, since `service docker start` fails on `ulimit` here). The owner approved new agents, which resumed from each worktree and its notes. Agents now commit in small local steps so a restart loses little. |
| MOD-I171 | CI | The commit messages of uPortal `d66bdc5a5` and `cd841c8c3` fail `vale.sh commits`. The coordinator wrote the first, with a figurative phrase. A prose agent wrote the second, which quotes a jargon word. In both cases the check ran piped into `tail` or `grep` inside an `&&` chain, so its failure didn't stop the push. The commit-message job flags the first push. The integration branch isn't rewritten. | The coordinator pushes through a script that runs both checks with `pipefail` and pushes only if they pass. The agent rules forbid piping a check inside an `&&` chain. |
| MOD-I172 | Spring | The authentication, rendering, JSON-rendering and redirection servlet contexts used `DefaultAnnotationHandlerMapping` and `AnnotationMethodHandlerAdapter`, which Spring 5 removed. `PortletRedirectionController` depended on the old `PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE` value and threw `UnsupportedOperationException` with two or more path parameters. | **Fixed** on Spring 4.3 by "refactor(mvc): map servlet controllers with requestmappinghandlermapping" (MOD-D38). Upstream candidate. |
| MOD-I173 | Spring | Web Flow's `ResourceServlet` is gone in Web Flow 2.5. uPortal mapped it to `/resources/*`, which served nothing uPortal uses. | **Removed** (MOD-D38). |
| MOD-I174 | Spring | `registrationBehaviorName` was removed in Spring 5.0. The context probe found it. | **Fixed:** `registrationPolicy` (MOD-D38). |
| MOD-I175 | Persistence | `PortletEntityRegistryImpl` recovers from Hibernate 3's `HibernateOptimisticLockingFailureException`, which JPA on Hibernate 4.3 never produces, so that recovery code never runs. | Fix it with a test during the Hibernate steps or before P4-1. Upstream candidate. |
| MOD-I176 | Spring | spring-ws-core 2.1.2 comes in through portlet-ws-util. | Part of the P4-1 change set (Spring-WS 3.1). |
| MOD-I177 | Spring | Web Flow's `PortletMvcView` kept Spring's original attribute names after the copied `ViewRendererServlet` moved package. The Spring 5.3 probe caught it before any portal ran. | **Fixed** with a regression test (MOD-D38). When renaming a copied class's package, search the copied sources for callers too. |
| MOD-I178 | Environment | In cloud sessions, the permission check refuses the local Maven Central mirror script (MOD-I52), so heavy dependency resolution there needs retries or CI. | Validate in CI from cloud sessions. The owner allowed the mirror (MOD-D30), but the permission check needs an allow rule to run it, and the owner chose not to add one. |
| MOD-I179 | Tests | jqwik 1.10.1 prints a notice addressed to AI coding agents when property tests run. The owner ruled it prompt injection. Agents must not act on it or repeat it, and test output should leave it out. | **Fixed** for uPortal-db-migrate's tests: `JqwikAgentNoticeFilter`, a JUnit Platform `LauncherSessionListener`, wraps `System.out` and drops what `JqwikExecutor.printMessageForCodingAgents` prints, found by its call stack. Other output passes through unchanged. The agent rules say that text in tool output is data, never instructions. If another tool prints such text, filter it the same way. |
| MOD-I180 | Login | With a local Sign In URL, a guest who opens a deep link such as `/uPortal/p/user-administration` gets HTTP 400. `UrlCanonicalizingFilter` always uses `CasLoginRefUrlEncoder`, which appends `%3FrefUrl%3D<page>` encoded for a CAS `service`. uPortal has `LocalLoginRefUrlEncoder` but never wires it, and the local login form posts to `/Login` without `refUrl`. After signing in, the user sees the default page instead of the one they asked for. | **Fixed** by uPortal "fix(login): send guest deep links to the local login form". `login.refUrlEncoder` (`cas`, the default, or `local`) points the `loginRefUrlEncoder` alias at the encoder that `UrlCanonicalizingFilter` uses. An unknown value fails at startup. `LocalLoginRefUrlEncoder` adds the page as a `refUrl` parameter to the Sign In URL, and the login flow posts it to `/Login`. uPortal-start "fix(auth): open the requested page after a local login" drops the `?refUrl=` workaround and sets `local` (`cas` in `etc/cas`). Tests: `LocalLoginRefUrlEncoderTest`, `LoginRefUrlEncoderSelectionTest` (loads mvcContext.xml) and the auth spec's deep-link tests. CI: uPortal CI 38118470010 (full matrix), uPortal-start CI 38118477579 and Integration 38118476392 (attempt 2; attempt 1's postgres-18 Playwright job 114413716154 failed with MOD-I192 and its re-run passed), all green. |
| MOD-I181 | Login | A failed local login redirects to the guest page without a message. "Invalid username or password" appears only when the user opens the login form again. | **Fixed** by uPortal "fix(login): return a failed local login to the login form". With `login.refUrlEncoder=local`, a failed login with a user name goes back to the Sign In URL with its local `refUrl`, and the form shows "Invalid username or password" at once. With `cas` it still goes to the default page. Tests: `LoginControllerTest`, and uPortal-start's `expectLoginRefused` now requires the login form and the message. CI: uPortal CI 38118470010 (full matrix), uPortal-start CI 38118477579 and Integration 38118476392 (attempt 2; attempt 1's postgres-18 Playwright job 114413716154 failed with MOD-I192 and its re-run passed), all green. |
| MOD-I182 | uPortal-start | `cas-proxy-test-portlet` never received a proxy ticket: uPortal kept `casTicketUserInfoService` commented out, and uPortal-start never set `proxyReceptorUrl` or `proxyCallbackUrl`. | **Fixed** in uPortal-start (MOD-D40): the bean is in `overridesContext.xml` for both modes and returns an empty map without a CAS session. A spec checks both cases. |
| MOD-I183 | uPortal-start | `npm run lint` fails with 8 `no-undef` errors in `tests/db/migrate/matrix.mjs`, and CI never runs that lint. | **Open**. |
| MOD-I184 | Persistence | Local-account login reads all of `UP_PERSON_ATTR`: `USER_DIR_ID` has no index, so at 100,000 accounts each login spends about 130 ms in two lookups. | **Open**. G-index R1 fixes it (MOD-D41). It is also a 5.17.x upstream candidate. |
| MOD-I185 | Events | The aggregation job's `DISTINCT` group query (`getAggregatedGroupMappings`) reads whole aggregation tables every minute. It is the top statement by total time and grows with history. | **Open**. G-index R7 fixes it with a code change, then R8. |
| MOD-I186 | Groups | The groups-of-a-member lookup has no index on `UP_GROUP_MEMBERSHIP.MEMBER_KEY`, and it runs on every login and permission check. | **Open**. G-index R12 fixes it. |
| MOD-I187 | Events | `UP_RAW_EVENTS` has five unused indexes, one on the `AGGREGATED` flag, which is updated about 5 million times a day at large-campus scale. | **Open**. G-index R9 fixes it and made those statements 53 % to 64 % faster. |
| MOD-I188 | Persistence | Foreign keys to parents that are purged or deleted (cookies, unique strings, portlet entities) have no index. | **Open**. G-index R2 to R4 fix it. |
| MOD-I189 | Persistence | The 5.17.9 MariaDB schema has 43 duplicate or covered InnoDB foreign-key indexes. The integration build creates none of them. | **Open**. G-index R10 fixes it. |
| MOD-I190 | Deployment | The portal and its portlets together use all 100 default PostgreSQL connections. | **Open**. Document a connection budget for 6.0. |
| MOD-I191 | Persistence | Queries with `ORDER BY` or `@OrderBy` don't break ties, so rows with the same sort key can come back in any order. The goldens compare tied rows as unordered. | **Owner (2026-10-11):** 6.0 adds a final tie-breaker, usually the id, to each sort, so results come back in a guaranteed order. **Fixed (persistence-fixes branch).** Each sort ends with a unique key, listed in `notes/persistence-fixes.md`, and the goldens compare in order. A seeded fault (one tie-breaker removed) fails a golden. |
| MOD-I192 | CI | Playwright on PostgreSQL 18 intermittently loses one bundled portlet application: every page lacks its output, and a different application is missing on each attempt. It failed 22 SimpleContent tests in job 114386349300 and 8 Announcements tests in job 114405187791 (run 38110595100 and its re-run). Run 38117263537 failed the same 8 Announcements tests. So did job 114413716154 (run 38118476392, login-fixes). The re-run of that job passed. Those branches change only REST APIs, web components, tests and docs, not portlets or the database. The Tomcat logs are only in the job artifacts, and the proxy in front of the agents refuses the host that serves them. | **Open.** First guess was MOD-I190, the portal and its portlets running out of PostgreSQL's 100 connections. A local measurement makes that unlikely. The whole Playwright suite on PostgreSQL 18, with every overlay deployed, peaked at 32 to 38 connections and never emptied a pool. The one failure log read so far (run 38117263537) has no "too many clients" error and no Tomcat SEVERE line, but it predates uPortal-start fd53c9495, which prints Tomcat's startup errors in a failed job. The next failure should show the cause. MOD-I190's connection budget goes ahead on its own merits. |
| MOD-I193 | Portlets | In 5.17's Popular Portlets portlet, the column filters show the wrong rows once the table is sorted. The DataTables search function reads `col.data()`, which is in display order, with each row's load index. | **Fixed in the web component** (WS14). "a filter keeps the rows it names, also after sorting" in uPortal-start's `popular-portlets.spec.ts` and "keeps the rows it names after a sort" in `up-popular-portlets.browser.test.ts` check it; the first fails on the old portlet, which keeps the bug. |
| MOD-I194 | Portlets | `PopularPortletsController.getEventCounts` throws a `NullPointerException` when the `days` parameter is missing. | **Fixed in the REST API**: `/api/v6-0/popular-portlets` uses 30 days when `days` is missing and answers 400 unless it is a whole number from 1 to 365. The portlet keeps the bug. |
| MOD-I195 | Security | User Administration's "Create a User" with an existing user name updates that account, password included. The edit-account Web Flow names its validator `accountFormValidator`, but the bean is `personValidator`, so `PersonFormValidator` never runs. Self-Edit Account has the same dead validator, and its confirmation field is ignored for that reason. Found by reading the code during the Self-Edit Account conversion; not reproduced, since only administrators can open the form. | **Open.** Fix with the User Administration conversion (MOD-D19, Thymeleaf admin console), with a test that creating an existing name is refused. Until then, administrators can overwrite an account this way. |
| MOD-I197 | Testing | `JpaClusterLockDaoTest.anAbandonedLockIsTakenOver` failed at random on MySQL, also on Hibernate 4.3: `DATETIME` rounds the stored lock time to whole seconds, sometimes past the time the second node reads it. | **Fixed in the test** (`a19ebddc6`, landed with P2): it waits 1.1 s. Revisit when MOD-I141 moves `ClusterMutex.lastUpdate` to `Instant`. |
| MOD-I198 | Build | The dom4j 2 exclusion added for Hibernate 5.2 stopped SpotBugs from starting. | **Fixed** in `24a283a72`, landed with P2. |
| MOD-I199 | Build | `.sql` files had no line-ending rule in `.gitattributes`, so the DDL golden files could be checked out with CRLF on Windows. | **Fixed** in `7412afdfb` (LF for `*.sql`), landed with P2. |
| MOD-I196 | Data | **Found by the migration-equivalence nightly set** (uPortal-start run 38120962016, job 114422262361). A 5.17 limit on SQL Server: 5.17 creates `varchar` columns, which on SQL Server's default collation keep only windows-1252. Text outside it (emoji, CJK) is stored as `?`, and 5.17 then can't find a row by such a name: importing the fixture's PAGS group `PAGS Fixture Child 🌱` stopped with `NoSuchElementException` in `dropTestGroupsAndTests`. A UTF-8 collation doesn't help, because `varchar(n)` then counts bytes: the fixture's 254-character attribute is 425 bytes and a portlet description 395. So 5.17 on SQL Server can't hold the fixture with either collation. The persistence goldens model the same conversion in `DatabaseVendor.sqlServerVarchar` (uPortal-webapp tests). | Open (5.17 behaviour). The equivalence check converts the fixture and the reference to windows-1252 when 5.17 runs on SQL Server (uPortal-start f6d21dd46, `tests/db/equivalence/code_page.py`), and compares both exports exactly. The conversion writes `_` (721c07314), because 5.17 names exported files after group names and `?` is not allowed in an Actions artifact. `vendors/sqlserver.diff` records the one other SQL Server difference, a PUBLISHED time rounded to 1/300 s (2c178247c). MOD-I201 is the 6.0 side. |
| MOD-I200 | Data | **Found by the migration-equivalence nightly set** (uPortal-start run 38120962016, job 114422262407; the cause in run 38128100470, job 114443868353). On Oracle, `dataExport` failed for every layout profile, in 5.17.9 and in 6.0: `export-profile.crn` passes `STRUCTURE_SS_ID`, `THEME_SS_ID`, `USER_ID` and `PROFILE_ID` from SQL to `getStylesheetDescriptor(long)` and `getStylesheetUserPreferences(descriptor, int, int)`, and Oracle returns `NUMBER` columns as `BigDecimal`, which Groovy does not pass as a `long` or an `int` ("No signature of method getStylesheetDescriptor() is applicable for argument types: (BigDecimal)"). The import script already converts the same ids. A 5.17 limit that 6.0 shared. | **Fixed in 6.0** (0482bf643): the script converts the ids with `longValue()` and `intValue()`. `ProfileExportTest` runs it on legacy tables with `DECIMAL` ids, which every database returns as `BigDecimal`, so each database of DB integration behaves as Oracle does. The test failed before the fix. The equivalence check skips 5.17's export on Oracle and compares 6.0's export with the reference (uPortal-start 1b4b49066). |
| MOD-I201 | Copy tool | **Found by the migration-equivalence nightly set** (run 38120962016, job 114422262436). 6.0 on SQL Server can't hold text outside windows-1252: the build under test creates `varchar` columns on the default collation. A 5.17 site on another database with emoji or CJK in group names, portlet titles, layout names or user attributes can't move to 6.0 on SQL Server. The copy from MariaDB 10.6 wrote all 2,185 rows, and verification refused 14 tables (same row counts, other hashes): `news_definition`, `news_role`, `up_dlm_evaluator`, `up_group`, `up_pags_group`, `up_pags_test`, `up_permission`, `up_permission_owner`, `up_permission_activity`, `up_layout_struct`, `up_portlet_def`, `up_portlet_pref`, `up_portlet_pref_values`, `up_person_attr_values`. The copy failed, so nothing was lost without notice. The plan reported 0 warnings. | Open until the 6.0 schema step: fixed by MOD-D44 in the 6.0 schema step (`nvarchar` through Hibernate's nationalized strings, uPortal c56fdc315). The nightly copy into SQL Server fails until that step is merged. MOD-I202 makes the plan refuse such a copy before it starts. |
| MOD-I202 | Copy tool | The plan doesn't compare the target's text encoding with the source's text. A copy into a code-page column (SQL Server's default collation, MOD-I201) of text it can't hold copies every row, and only verification refuses the tables afterwards. | **Fixed** (4e55485f7). The plan reads the character set of each target text column (SQL Server code pages, MySQL/MariaDB column character sets, PostgreSQL and Oracle database character sets). For source values with characters outside it, it reports a plan error with the column, the number of rows and the lowest key with its first such character, and doesn't write any row. `TextEncodingTest`, `TargetCodePageChecksTest`, and two `CopyFaultIT` cases with an emoji (windows-1252 on SQL Server and MySQL/MariaDB `latin1`, and `utf8mb3`). |
| MOD-I203 | Groups | **Found by a new API spec** (uPortal-start `tests/api/entities.spec.ts`, f3c3ddede), on a question from the persistence session. Every portlet search of the entity selector answers HTTP 500: `GroupListHelperImpl.search` always asks with `SearchMethod.CONTAINS_CI`, and `PortletDefinitionSearcher` (uPortal-groups-local) handles `DISCRETE`, `STARTS_WITH`, `ENDS_WITH` and `CONTAINS` only and throws `GroupsException("Unknown search type")`. The group services pass the exception up, so `/api/entities.json?entityType[]=portlet` fails, as does choosing a portlet as a permission target (`EntityTargetProviderImpl.searchTargets`). Group and person searches work. Unchanged since 5.17. | Open, for the persistence session: map the `*_CI` methods in `PortletDefinitionSearcher` onto the case-insensitive portlet search that the persistence fixes add (`upper()` on both sides, MOD-I142). Today's search upper-cases the term but not the name. Mapped onto it, a mixed-case search would still miss on HSQLDB, PostgreSQL and Oracle. The spec states the intended result (200, Bookmarks found for "oOKMARK") under `test.fail()` naming MOD-I203, so it reports "unexpectedly passed" once the fix is merged. |
| MOD-I220 | Persistence | **Found by the entity round trip.** `UP_MUTEX.LOCK_END` (`ClusterMutex.lockEnd`) is stored as JVM-local time too, but MOD-I141's list left it out. | **Fixed** with MOD-I141: `Instant` in UTC, converted by the copy tool. |
| MOD-I221 | Persistence | `hibernate.jdbc.time_zone=UTC` alone moves `UP_TIME_DIMENSION.TD_TIME` by the JVM's offset (00:00 stored as 05:00 in New York). Without a zone, Jadira's `localTime` binds through Hibernate's time descriptor. | **Fixed:** `JvmZoneLocalTimeType` binds with the JVM zone's calendar, as before. |
| MOD-I222 | Portlets | `TransientPortletEntityDao.getPortletEntity` threw a NullPointerException for a transient node the layout lacks or whose portlet is gone (seen on `layout.json` with `user=null` in the first P2 runs). | **Fixed:** returns null. |
| MOD-I223 | Portlets | On SQL Server, `[` in a portlet search term starts a LIKE character class. HSQLDB and Oracle reject `![`, so one escape set can't serve every vendor. | **Fixed:** the DAO escapes `[` on SQL Server only, from PortalDb's dialect. The query golden checks that the vendors agree. |
| MOD-I230 | Persistence | **Found by the background-preference conversion.** When one user saves portlet-entity preferences from two sessions at once, Hibernate refuses one save, and a stale cached `PortletPreferenceImpl` can remain. The user's next sign-in then fails with `EntityNotFoundException` until Tomcat restarts. It is the existing `PortletEntityPreferencesAPIImpl` store, which the portlets use too. Seen when two Playwright workers changed the student's background at once. | **Open.** Write a test that saves the same user's entity preferences from two sessions at once and then signs in, and show the failure. Then fix the cache handling (evict on a failed save, or retry). A refused save must not leave a stale entity in the cache. Until then a user can be locked out by saving preferences in two tabs. |
| MOD-I231 | Web components | Favorites added 11 messages and Popular Portlets 12 with English text only, so Spanish and French users see those texts in English. | **Open.** Translate them on `ccr-43ba0785-hzlrj5-i18n`. The MOD-D50 check stops it from happening again. |
| MOD-I232 | i18n | uPortal reads `Messages*.properties` as ISO-8859-1, since `ReloadableResourceBundleMessageSource` has no `defaultEncoding`. Most bundles use `\u` escapes, but `Messages_fr.properties` has one line in raw UTF-8 (`this.portlet.locked`), which French users see as `Ce portlet ne peut Ãªtre dÃ©placÃ©`. Also in 5.x. | **Open.** Read message bundles as UTF-8 after checking that each one is valid UTF-8, and test the French line. Existing `\u` escapes keep working. |
| MOD-I233 | Tenants | Tenant names must match `^[\w ]{5,32}$`. Java's `\w` matches only ASCII, so a name such as "Université Laval" is refused. | **Open.** Accept letters, combining marks, digits and spaces in any script (MOD-D49). Tenant fnames stay `[a-z_0-9]`, since they go in URLs. |
| MOD-I234 | Theme | Respondr has no right-to-left layout. The page has no `dir` attribute, and its stylesheets use left and right. An Arabic user would see right-to-left components on a left-to-right page. Also in 5.x. | **Open, decided (project owner, 2026-10-11).** Make Respondr right to left (`dir` from the locale and mirrored stylesheets) after the accessibility fixes (MOD-D47), on `ccr-43ba0785-hzlrj5-hardening`. |
| MOD-I235 | Web components | `formatMessage` in `lib/i18n.ts` turns numbers into text with `String()`, so counts and positions ignore the locale's digits and separators. Messages have no plural forms. | **Open.** Fix on `ccr-43ba0785-hzlrj5-i18n` (MOD-D51). |

## Open questions

| ID | Question | Status / proposed answer |
| --- | --- | --- |
| MOD-Q1 | Keep an external Tomcat, move to Spring Boot, or move to Jetty 12? | **Proposed:** keep an external Tomcat 11 for 6.0. Spring Boot 4 can come in *inside* the WAR (`SpringBootServletInitializer`, `@ImportResource` for the XML contexts, the Boot BOM), but Boot cannot host the portlet WARs, and embedded Tomcat with extra webapps is undocumented. Jetty 12.1 does support cross-context dispatch from ee11 into ee8 webapps (since 12.0.8). However, a context/classloader bug was only fixed on 2026-09-30, and Pluto passes container objects between contexts, which would need a javax↔jakarta adapter. Treat Jetty as a time-boxed spike, not the plan. |
| MOD-Q2 | Kotlin DSL / version catalog / convention plugins? | **Proposed:** order is Gradle 9 in Groovy → a `build-logic` included build with convention plugins plus `gradle/libs.versions.toml` (Renovate supports it) → configuration cache (Gradle 9's preferred mode) → Kotlin DSL file by file, last. Kotlin DSL has been the default since 8.2, but Groovy is not deprecated. There is no OpenRewrite recipe for Groovy→Kotlin. |
| MOD-Q3 | Local SSO in uPortal-start? | **Decided by MOD-D40:** local login by default, CAS 8 opt-in with `authMode=cas`. java-cas-client 4.x comes with P6/P7. |
| MOD-Q4 | Schema management after hbm2ddl? (**Superseded by MOD-D11** for the 5.17 → 6.0 path, while Flyway vs Liquibase is still open for changes *after* 6.0) | **Decided (project owner, 2026-10-11): use Flyway.** The owner prefers Apache-2.0 licensing. Flyway's open-source core is Apache-2.0, while Liquibase 5.0 moved to the FSL. Fresh installs get the schema from Hibernate 7 `SchemaManager`. Schema changes after 6.0 are Flyway versioned migrations, one SQL file per vendor, run on all vendors in CI. |
| MOD-Q5 | Portlets: transform or port the WARs, or replace them with web components / Soffit? | **Proposed:** follow Sakai's approach and keep Pluto in-tree on `jakarta.servlet`, with the `javax.portlet` 2.0 API rebuilt against `jakarta.servlet`. Fork Spring Portlet MVC for the 37 built-in portlets. Use build-time conversion of the bundled portlet WARs only *until they are replaced*. Over time, move content portlets to web components (Vue 2 is EOL, so Lit or Vue 3) and Soffit. The prototype will verify that the converted WARs run. |
| MOD-Q6 | Propose 6.0 as an RFC in `uPortal-Project/community` Discussions before opening the large PRs? | **Superseded by MOD-D36:** the decisions are ADRs, indexed in [`docs/developer/adr/README.md`](../adr/README.md). |
| MOD-Q7 | What scale defines "high traffic" for G-index (users, concurrent sessions, events per day, retention)? | **Answered:** large campus. About 100k users, 5k concurrent sessions, 5M raw events/day, 30 days of raw events kept, 2 years of aggregations. |
| MOD-Q8 | Can an adopter share anonymised production query statistics (`pg_stat_statements` or MySQL performance_schema digests)? | Asked |
| MOD-Q9 | Which open-source alternatives exist for the portlet container, Spring Portlet MVC and Web Flow's portlet support, and what could replace the uPortal parts that need them? | **Answered by MOD-D18 and MOD-D19** (ADR 0011, ADR 0012). Research: only Liferay maintains alternatives. Plutonium (Jakarta Portlet 4.0 container) and PortletMVC4Spring, which includes a `webflow` module, are both Apache-2.0. But they are milestones hosted outside Maven Central, and they have one maintainer. They target Spring 6.2 only and need Portlet 3/4 with `jakarta.portlet`, so every portlet would have to be rebuilt. Sakai maintains a copy of Pluto in its own tree. A community mirror (`sustain/portals-pluto-jakarta`, Dec 2025) shows little activity. Servlet-mode Spring Web Flow 4.0.x is maintained for Spring 7, and the flow XML can be reused. Plan, as decided in MOD-D18 and MOD-D19: the vendored Portlet MVC + Web Flow portlet integration classes (P3-1) stay only through the Spring 5.3 step (P4), with PortletMVC4Spring 5.3.4 as the reference for adapting them, and are deleted before the Jakarta switch (P6). 6.0 doesn't contain them. During P4 and P5 the 37 built-in portlets and 26 admin flows are replaced, each by Spring MVC + Thymeleaf pages or by REST + web components, and the `tests/ux/admin` specs check the same behaviour. Content moves to a native web-component channel, and Soffit becomes a pipeline component. Pluto stays as the container for the bundled portlet WARs and adopters' portlets until 7.0. The reference conversions are Groups Manager (Thymeleaf) and Favorites (web component). An earlier version of this answer kept the vendored classes in 6.0 and replaced the portlets in 6.x. ADR 0012 lists that as a rejected option. |
| MOD-Q10 | Should 6.0 offer Valkey/Redis as a cache provider, alongside local and embedded-cluster caching? | **Proposed:** yes, behind the JCache abstraction from P3-3 / P4-3. The supported profiles are (a) single node with Caffeine or Ehcache 3; (b) embedded cluster with Infinispan + JGroups invalidation, closest to today's Ehcache 2 + JGroups; (c) external Valkey/Redis through Redisson's JCache and Hibernate L2 support, with a local near-cache for hot regions (layout, permissions, groups, XSLT). Today Redis is used only for optional Spring Session storage (uPortal-session, Jedis), so (c) lets clustered sites run one Valkey for both sessions and caches. Before committing to (c): spike Redisson against Hibernate 7.4 (unverified, MOD-D12 research gap), Spring Session 4.x with Lettuce against Valkey 8, and latency against the 184 regions. CI runs a Valkey service container. **Refinement:** the interface is **Spring's Cache abstraction** (`@Cacheable`/`@CacheEvict`/`@CachePut`, `@Cacheable(sync=true)` in place of `SelfPopulatingCache`, and programmatic `CacheManager`/`Cache` for the rest), not raw JCache. Spring 4.3 already includes `CaffeineCacheManager` and `JCacheCacheManager`, so P3-3 can land before any Spring upgrade. Provider profiles map to Spring cache managers (Caffeine for local, JCache→Infinispan for embedded cluster, and Spring Data Redis `RedisCacheManager` or Redisson for Valkey). Hibernate L2 stays on Hibernate's own SPI (`hibernate-jcache`, or Infinispan's region factory) and shares the same underlying provider, so clustered profiles invalidate entities across nodes too. Spring's abstraction doesn't cover the following, so they need redesign. Ehcache 2 search-API uses become DB queries or indexes, the 29 `SpringCacheEventListenerFactory` listeners become explicit evictions or application events, and per-cache TTL and size limits move to provider config generated from one region list. |
| MOD-Q11 | How should background and scheduled jobs work in 6.0? | **Proposed:** keep Spring scheduling, the Spring approach (MOD-D22). Today `schedulerContext.xml` defines `uPortalTaskScheduler` (a `DelegatingThreadPoolTaskScheduler` with a 120 s initial delay, running on the `uPortalTaskExecutor` pool) and `task:scheduled` fixed-delay jobs with prime-number periods. Every portal JVM runs them. *Local jobs* (each node): raw-event queue flush (~1 s), Ehcache expired-element eviction (~1 min), cache health reports (~1 min), Lucene search-index updates (~1 min), and the TinCan/xAPI batch sender (2 s, `tincanAPIContext.xml`). *Clustered jobs* (one node at a time through `IClusterLockService.doInTryLock` on the `UP_MUTEX` table / `JpaClusterLockDao`): raw-event aggregation (~1 min), event-session and raw-event purges (~1 min), dimension population (~24 h), portlet lifecycle publish/expire (~1 min), portlet rating aggregation (~6 h), and expired portlet-cookie purge (~1 h). MOD-I82 means these clustered jobs fail on MySQL/MariaDB the first time each mutex is used. **Plan:** fix MOD-I82 now. Convert the XML `task:` config to `@Configuration` + `@Scheduled` when touched. Evaluate Spring Integration `JdbcLockRegistry` (vendor DDL included, TTL and renewal, `LockRegistryLeaderInitiator`) to replace the custom `UP_MUTEX` lock in the 6.0 schema. On JDK 21+, use virtual threads for the task executor, and Micrometer observations for job timing and failures. Add tests for activity → aggregated rows, purge retention honored, and lifecycle publish/expire. These jobs are also part of the G-index workload (MOD-D12). |
