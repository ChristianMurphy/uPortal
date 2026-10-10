<!--
Draft for uPortal-Project/community Discussions, category "RFC / design proposals".
Announce on uportal-dev. Before posting, point the docs/developer/modernization/...
links at the published branch: relative links will not resolve from a Discussion.
-->

# RFC: uPortal 6.0 on Java 25, Spring 7 and Jakarta EE 11

By Christian Murphy (@ChristianMurphy) · **Status:** open for comment

This RFC proposes the scope of uPortal 6.0, how it would be split into small PRs, and what adopters would need to do to upgrade. I'd like agreement on direction before any large PR opens. The research is in [`docs/developer/modernization/research/REPORT.md`](research/REPORT.md). The issue and decision log, which the `MOD-*` IDs below refer to, is in [`docs/developer/modernization/LOG.md`](LOG.md).

## The problem

uPortal 5.17 runs on components whose support has ended or is ending:

- **Java 11:** free Temurin builds end in October 2027.
- **Spring Framework 4.3 and Spring Security 4.2:** both reached OSS end of life in 2020.
- **Hibernate ORM 4.3:** a Java 6/7-era line that has been out of support for years.
- **Tomcat 8.5:** EOL since March 2024.
- **Apache Pluto:** the Apache Portals project moved to the Attic in 2025. Pluto's last release still uses javax.
- **Ehcache 2:** no release since 2021. Spring 6 and Hibernate 6 both dropped support for it.
- **Vue 2:** end of life since the end of 2023.

Old stacks also hide breakage. Since the Hibernate 4.3 upgrade ([#3012](https://github.com/uPortal-Project/uPortal/pull/3012)), every startup has fetched a DTD from www.hibernate.org, because the Woodstox parser on our classpath ignores Hibernate's local resolver. A portal that can't connect to hibernate.org won't start (MOD-I50). The prototype already fixes this, and I'll offer the fix to 5.17.x on its own whatever happens here.

## The proposal

I propose that uPortal 6.0 target **Java 25, Gradle 9, Spring Framework 7, Spring Security 7, Hibernate ORM 7.4, and Jakarta EE 11 on Tomcat 11**. Apereo CAS 8.0 already runs Spring 7, Hibernate 7.4 and Tomcat 11 on Java 25 in production.

This departs from current direction in three ways:

- It **merges the published uP6 and uP7 roadmap scopes**. Portlet support has held the platform back, and the roadmap leaves the portlet question to uP7. Here, the platform moves in 6.0 and portlets are bridged instead of dropped.
- It departs from the **April 2025 uP6 update**, which chose Java 17 and Tomcat 9 so the project could stay on javax. I've only read summaries of that thread, so please correct me if I've misread it.
- It conflicts with the **Renovate pins** in [#2948](https://github.com/uPortal-Project/uPortal/pull/2948), which block Spring 5+, Spring Security 5+ and Hibernate majors.

The main reason is support dates. OSS support for Spring 5.3 ended in August 2024, and for Spring 6.2 in June 2026. That leaves Spring 7 as the only line with open-source support, so any javax landing zone is already unsupported. Tomcat 9.0 support also ends in March 2027, although a 9.1.x branch continues to 2030. A javax 6.0 would leave the portlet-container problem unsolved, and it would give adopters two breaking upgrades instead of one.

The build stays on Gradle. We need Gradle 9 anyway, since Gradle 6.9 can't run on JDK 17+, and [#2921](https://github.com/uPortal-Project/uPortal/pull/2921) already did much of the groundwork. The roadmap lists Maven (2025) and "Replace Gradle" (2028). I'd rather not tie a build-tool change to the platform switch. If you want Maven, please say so before the build PRs are reviewed.

## Portlets stay supported through 6.x

This proposal does not drop portlets. There's also no usable Jakarta portlet container to adopt. Jakarta Portlet 4.0 has no artifacts on Maven Central, and Liferay's Plutonium is a single-maintainer milestone outside Central that would mean rebuilding every portlet. So uPortal would own a small hybrid:

1. **In-tree Pluto on jakarta.servlet.** We import the Pluto 2.1 source unchanged while still on javax, then switch it along with everything else.
2. **A hybrid javax.portlet 2.0 API, recompiled against jakarta.servlet.** In Portlet 2.0, only the `Cookie` type touches the servlet API. The jar goes in Tomcat's `shared/lib`.
3. **A vendored Spring Portlet MVC.** We copy Spring 4.3.30's `web.portlet` package and the Web Flow portlet glue into `org.apereo.portal` while still on Spring 4.3, then adapt them at each Spring step. This keeps the 37 internal portlets and the Soffit connector working.
4. **Build-time transformation of the bundled portlet WARs** in uPortal-start. A scanner fails the build if a WAR calls a Servlet API that Servlet 6 removed. This is a bridge that lets 6.0 be released without changes to the portlet repositories. It is also the least proven piece. There's no public precedent for running unmodified JSR-286 WARs this way, so a spike in P5 decides whether it holds up.
5. **In 6.x, a native web-component channel and a pipeline-level Soffit connector**, so content no longer needs the portlet container. Then the bundled portlets move, most valuable first, to REST services plus custom elements. The Vue 2 components move to Lit or plain custom elements.

Sakai took the same route. Its [PR #15141](https://github.com/sakaiproject/sakai/pull/15141), merged on 9 October 2026, imported Pluto into Sakai's tree and compiled `javax.portlet` against `jakarta.servlet`.

Where they line up, this plan carries out the "Recommended First Moves" from the maintainers' [subsystem analysis](https://github.com/uPortal-Project/uPortal/blob/master/docs/architecture/subsystem-analysis.md). The web-component channel and Soffit connector are moves #2, #3 and #7, and Flyway is part of #5. The other moves aren't part of this plan and can go ahead on their own.

## What adopters would need to do

- **Upgrade from 5.17.x only.**
- **Run a current database server.** 6.0 runs on, and is tuned for, PostgreSQL 18, MySQL 9.7 LTS, MariaDB 12.3 LTS, SQL Server 2025 and Oracle 23.26 or later. Older servers are migration sources only: the copy tool moves 5.17 data from them into a new server, across vendors if you like.
- **Run Java 25 on Tomcat 11.** uPortal-start will provide the Tomcat configuration, including explicit connector limits.
- **Expect a session flush** (every user logs in again) and **a Lucene reindex** on first start.
- **Take care with PostgreSQL LOBs.** Our current mappings keep large-object OIDs in `text` columns, and `vacuumlo` can delete large objects referenced only that way. Don't run `vacuumlo` against a uPortal database, and back up before upgrading. 6.0 pins these column types, and the upgrade test runs `vacuumlo` and re-reads every LOB.
- **Migrate clustered caching.** Ehcache 2 with JGroups replication goes away. Single-node portals use JCache with Ehcache 3, and clusters use Infinispan. Custom cache and JGroups configuration will need migrating.
- **Note the new uPortal-start default: local login.** CAS 8 becomes an opt-in Compose service that CI still runs for the login spec. I haven't confirmed that an official CAS 8.0 image exists. A CAS Initializr overlay is the fallback.
- **Plan for custom code.** Custom portlet WARs go through the same transformation, and custom Java code moves to jakarta.

Databases should upgrade in place with no manual SQL. 6.0 freezes Hibernate's 4.3-era DDL behaviour with documented legacy settings, and never runs `hbm2ddl update` on an upgraded database.

## Delivery plan

The plan is about 50 small PRs over phases P0 to P7, each green on its own, followed by P8 as ordinary 6.x work. Everything that can happen on javax happens first, which leaves four atomic steps. Each phase must pass four required checks. G-unit is the build on Linux, Windows and macOS. G-portal is every Playwright spec, three runs in a row. G-db upgrades a 5.17 dump on every supported database, and G-flows runs new admin and import/export specs.

| Phase | Main changes | Atomic step | Tag |
|---|---|---|---|
| P0 Safety nets | Green Playwright baseline, admin-flow specs, DB upgrade harness | | |
| P1 Toolchain | Spotless, Gradle 7.6 → 8.14 → 9.8, Tomcat 9, local login; still Java 11 bytecode | | |
| P2 Hibernate 5.2 | New schema tooling, frozen DDL behaviour, Flyway baseline | P2-2 | |
| P3 Groundwork | Vendored Portlet MVC, in-tree Pluto, cache facade, Joda → `java.time` | | |
| P4 Spring 5.3 | Spring/Security/Session/Web Flow bloc, JDK 21, Hibernate 5.6 | P4-1 | 6.0.0-M1 |
| P5 Shrink the switch | Spring 6 removals done early, Lucene 10, portlet bridge spike, Infinispan | | |
| P6 Jakarta checkpoint | Scripted javax → jakarta, Spring 6.2, Hibernate 6.6, Tomcat 10.1, JDK 25 | P6-1 + P6-2, across both repos | none |
| P7 Release line | Spring 7, Security 7.1, Hibernate 7.4, Tomcat 11, Jackson 3 | P7-1 | M2, RCs, GA |
| P8 6.x | Web-component channel, Soffit in the pipeline, portlet ports | | 6.1+ |

For the release, I commit to three things: zero DDL drift on all five production database vendors (PostgreSQL, MySQL, MariaDB, SQL Server and Oracle), every Playwright spec green on Tomcat 11 and JDK 25, and the full CI matrix green. The Playwright baseline isn't green today ([uPortal-start #692](https://github.com/uPortal-Project/uPortal-start/pull/692) reported 7 failures in May 2026), so P0 fixes that first.

A checked-in script generates the javax → jakarta part of P6, so reviewers read the script and the manual fix-ups instead of hundreds of changed files. P6 is merged but never tagged, because Spring 6.2 is out of support. I'm not putting dates on the plan, because review capacity will set the pace.

The milestone train starts with 6.0.0-M1 after the Spring 5.3 bloc, a javax build adopters can use to test the database and cache changes. M2 follows the Spring 7 step, then RCs and GA.

## Branches and releases

- `master` becomes 6.0 development once this RFC is accepted. Until then, I'd only ask for work that doesn't depend on the direction, which means the P0 tests, the CI fix (MOD-I33), Spotless and the MOD-I50 fix.
- `rel-5-17-patches` gets 5.x fixes for 12 months after 6.0.0 GA. That period is a suggestion and open to discussion.
- uPortal-start gets a `6.x` branch. Adopters fork `master` and the repository has no tags, so `master` stays on 5.17 until 6.0 is released.
- Renovate pins are lifted phase by phase. The portlet repositories can keep theirs.
- PRs follow the usual conventions and the PR template, and each adds notes to `docs/UPGRADING-6.0.md`.

## Review capacity and authorship

Since October 2025, one maintainer has done 43 of 45 merges to `master`, and about 50 PRs can't rest on one person. I'm asking for **a second reviewer for each phase**. I commit to **co-maintaining the vendored Spring Portlet MVC and the Pluto fork**, and I'm glad to review other people's PRs.

**Much of the prototype is produced with AI coding agents under my direction and review.** I review every change before it goes upstream, and I'm responsible for it. Commits list me as the human author, with a `Co-Authored-By` trailer for the agent. I'd like the steering committee to confirm that this is acceptable under my ICLA before the first large PR.

## How my open PRs fit

- **[#2988](https://github.com/uPortal-Project/uPortal/pull/2988) (Spotless)** lands first, nearly as it is. The google-java-format version bump follows as a separate format-only PR.
- **[#2921](https://github.com/uPortal-Project/uPortal/pull/2921) (Gradle 6.9.4 → 8.5)** is retargeted to the Gradle 7.6 hop and keeps its decisions (Ant XJC, `maven-publish`, plugin versions). The 8.14 and 9.8 hops follow as separate PRs.
- **[uPortal-start #681](https://github.com/uPortal-Project/uPortal-start/pull/681)** follows the same split.

## Questions for the community

1. Is the combined uP6 + uP7 scope acceptable, or would you rather have a javax uP6 first, knowing Spring 5.3 is already unsupported?
2. Is 12 months of 5.17.x patches after 6.0 GA long enough?
3. Which database (vendor and version), Java and Tomcat versions do you run in production? Does anyone run a cluster with JGroups cache replication?
4. Do you have custom portlets in production that the bridge must cover?
5. Flyway (Apache-2.0) or Liquibase, whose Community edition moved to the FSL in 5.0? I lean towards Flyway.
6. Should the build stay on Gradle for 6.0?
7. Who can review, and which phase would you take?
8. For the steering committee: is the AI-assisted authorship described above acceptable under my ICLA?
