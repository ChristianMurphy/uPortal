# Migration path for uPortal's Spring ecosystem (Spring 4.3 / Security 4.2 / Web Flow 2.4 / Session 1.3 / LDAP 2.3 to the Jakarta-era releases current in October 2026)

Research date: 2026-10-10. Method: I read primary sources directly where possible. These included the Spring Framework and Spring Security GitHub wikis (cloned), the reference-doc sources of spring-security, spring-session, spring-ldap and spring-webflow (cloned at release branches and tags), the OpenRewrite recipe YAML, the Apereo CAS release notes, the PortletMVC4Spring git history, and Maven Central directory listings for release dates. I also checked class presence across the git trees of Spring Framework tags v4.3.30.RELEASE, v5.0.0.RELEASE, v5.3.39, v6.0.0, v6.2.0 and v7.0.0. The proxy blocked docs.spring.io, docs.openrewrite.org, springdoc.org, apereo.github.io and endoflife.date in this environment, so doc citations point at the GitHub source of the same pages. The spring.io support-timeline tables are rendered by JavaScript and could not be read. The commercial end-of-support dates below are hedged for that reason.

## Q1. Latest versions, support/EOL status, and Java / Jakarta EE baselines (as of Oct 2026)

### Takeaway
As of October 2026 the current lines are Spring Framework 7.0.x (7.0.9 GA, 7.1 due Nov 2026), Spring Security 7.1.x (7.1.1), Spring Web Flow 4.0.x (4.0.1), Spring Session 4.1.x (4.1.1) and Spring LDAP 4.1.x (4.1.1). Security 7.1, Web Flow 4.0, Session 4.1 and LDAP 4.1 build on Spring Framework 7, which requires JDK 17+ (JDK 25 recommended) and Jakarta EE 11, meaning Servlet 6.1, Tomcat 11 and JPA 3.2 with Hibernate 7.1/7.2. Open-source support ended for the whole 6.x / 5.x generation, including Spring Framework 6.2 (June 2026). So an OSS-supported end state means moving to the 7.x line.

### Cited Findings
**Spring Framework**
- The official versions page says: "7.0.x is the start of a new framework generation and the current production line (November 2025), to be followed up by the 7.1.x feature branch (November 2026)." It also says 6.2.x is "the final feature branch of the 6th generation. Open source support ends in June 2026; commercial long-term support options are available," and that 5.3.x OSS <!-- vale ai-tells.SemicolonUsage = NO --><!-- direct quote of the Spring versions wiki -->"ended in August 2024; commercial long-term support options remain available."<!-- vale ai-tells.SemicolonUsage = YES --> ([Spring Framework Versions wiki](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions))
- JDK ranges are 7.x: JDK 17-25+, 6.2: JDK 17-25, and 5.3: JDK 8-21. The wiki adds: "We recommend JDK 25 or higher for production use with Spring Framework 7.x." ([Spring Framework Versions wiki](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions))
- EE ranges are 7.x: "Jakarta EE 11-12", 6.2: "Jakarta EE 9-10", and 5.3: "Java EE 7-8 (javax namespace)". 7.x uses "an EE 11 baseline (Servlet 6.1, JPA 3.2, BV 3.1) and provides early support for EE 12." ([Spring Framework Versions wiki](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions))
- <!-- vale ai-tells.FormalTransitions = NO --><!-- direct quote of the Spring 5.0 release notes -->"Spring Framework 4.3.x and therefore Spring Framework 4 overall reached its EOL cut-off on December 31st, 2020."<!-- vale ai-tells.FormalTransitions = YES --> The same page says the 5.x generation "will reach its end of OSS support on August 31st, 2024." ([Spring Framework 5.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.0-Release-Notes))
- 5.3.x "keeps being supported under commercial support terms for another couple of years." No exact commercial end date is given. ([spring.io blog, Mar 2024](https://spring.io/blog/2024/03/01/support-timeline-announcement-for-spring-framework-6-0-x-and-5-3-x))
- On Maven Central, the latest spring-core GA is 7.0.9 (2026-08-20), and 7.1.0-M1 (2026-08-20) and 7.1.0-M2 (2026-09-24) are published. ([Maven Central: spring-core](https://repo1.maven.org/maven2/org/springframework/spring-core/))
- The 7.1 preview notes say it is "scheduled for November 2026" and raise the baselines to "Jackson 3.1 for LTS support" and <!-- vale ai-tells.SemicolonUsage = NO --><!-- direct quote of the Spring 7.1 release notes -->"Hibernate ORM 7.3 support; runtime compatibility with Hibernate ORM 7.1 and 7.2 is retained for the time being."<!-- vale ai-tells.SemicolonUsage = YES --> ([Spring Framework 7.1 Release Notes (preview)](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.1-Release-Notes))
- Aggregators give these dates (unofficial, and they conflict). 6.2 OSS end is June 30, 2026 (consistent with the official "June 2026"). 6.2 enterprise end is June 2032 per versionlog. 7.0 OSS end is June 30, 2027 per eosl.date versus 2027-07-31 per endoflife.ai. ([versionlog 6.2](https://versionlog.com/spring-framework/6.2/), [eosl.date](https://eosl.date/eol/product/spring-framework/), [endoflife.ai 6.2](https://endoflife.ai/spring-framework/6.2))

**Spring Security**
- Support policy: "Major versions will be supported for at least 3 years from the release date (but you must run a supported minor version). Minor versions will be supported for at least 12 months." A minor that is the last to align with a Framework minor is EOL when that Framework minor is EOL; otherwise it is EOL when the corresponding Boot minor is. Note that the release table on this wiki page is stale (it stops at 5.5). ([Spring Security Versions wiki](https://github.com/spring-projects/spring-security/wiki/Spring-Security-Versions))
- On Maven Central, the latest GA is 7.1.1 (2026-08-20), 7.0.7 was released 2026-08-20, and 7.2.0-M1/M2 were released 2026-08-20 and 2026-09-24. The newest 6.5.x listed is 6.5.11. ([Maven Central: spring-security-core](https://repo1.maven.org/maven2/org/springframework/security/spring-security-core/))
- Spring Security 7.1.x builds against Spring Framework 7.0.9, Servlet API 6.1.0, Apereo CAS client 4.1.1, Spring LDAP 4.1.1, and both Jackson 2.22.2 and Jackson 3.1.6. ([spring-security 7.1.x libs.versions.toml](https://github.com/spring-projects/spring-security/blob/7.1.x/gradle/libs.versions.toml))
- "Spring Security 6.5 is the last release in the 6.x generation." ([Migrating to 7.0 (7.0.x docs source)](https://github.com/spring-projects/spring-security/blob/7.0.x/docs/modules/ROOT/pages/migration/index.adoc))
- An aggregator says OSS support for Spring Boot 3.5, Framework 6.2, Security 6.5 and Cloud 2025.0 all ended on June 30, 2026. This is secondary and unverified against spring.io. ([endoflife.ai article](https://endoflife.ai/article-spring-june-30-eol-wave))

**Spring Web Flow**
- Maven Central dates: 2.4.8.RELEASE on 2018-09-12, 2.5.1.RELEASE on 2018-09-12, 3.0.0 on 2023-07-12, 3.0.1 on 2024-11-21, 4.0.0 on 2025-12-09, and 3.0.2 and 4.0.1 both on 2026-06-10. ([Maven Central: spring-webflow](https://repo1.maven.org/maven2/org/springframework/webflow/spring-webflow/))
- Per the GitHub releases page, 4.0.0-RC1 targets <!-- vale ai-tells.SemicolonUsage = NO --><!-- direct quote of the Web Flow release page -->"Jakarta EE 11; Jakarta Faces 4.1; Spring Framework 7.0.0; Spring Security 7.0.0"<!-- vale ai-tells.SemicolonUsage = YES -->. 4.0.1 targets Spring Framework 7.0.8 and Security 7.0.6, and 3.0.2 targets Framework 6.2.19 and Security 6.5.11. 3.0.0-M1 was described as "Adopt Java 17, Spring Framework 6, Jakarta EE 9 baseline." ([spring-webflow releases](https://github.com/spring-projects/spring-webflow/releases))
- 3.0.2 and 4.0.1 fixed <!-- vale ai-tells.ColonUsage = NO --><!-- CVE titles quoted verbatim -->"CVE-2026-40985: Data Binding Vulnerability in Spring Web Flow with Unified EL Parser" and "CVE-2026-40986: Spring Web Flow JS RemotingHandler renders non-HTML Response as HTML"<!-- vale ai-tells.ColonUsage = YES -->. A commercial release also exists. I saw this via a search snippet of the author listing, not the post itself. ([spring.io author page (R. Stoyanchev)](https://spring.io/authors/rstoyanchev))

**Spring Session**
- Maven Central: 4.0.0 on 2025-11-18, 4.0.5 on 2026-08-20, 4.1.0 on 2026-06-09, 4.1.1 on 2026-08-20, and 4.2.0-M1 on 2026-09-24. The last 3.x is 3.5.7. ([Maven Central: spring-session-core](https://repo1.maven.org/maven2/org/springframework/session/spring-session-core/))
- Spring Session 4.0.0 builds against spring-framework-bom 7.0.0, spring-security-bom 7.0.0 and Jackson 3 (`tools.jackson.core:jackson-databind:3.0.0`). ([spring-session 4.0.0 libs.versions.toml](https://github.com/spring-projects/spring-session/blob/4.0.0/gradle/libs.versions.toml))

**Spring LDAP**
- Maven Central: 4.0.0-RC1 on 2025-10-16, 4.0.5 on 2026-08-20, 4.1.0 on 2026-06-08, 4.1.1 on 2026-08-20, and 4.2.0-M1 on 2026-09-24. The last 3.x is 3.3.8. ([Maven Central: spring-ldap-core](https://repo1.maven.org/maven2/org/springframework/ldap/spring-ldap-core/))
- The 4.0.0 release notes list "Update to Spring Framework 7.0.0 \[#1284\]". The 3.0.0 notes list "Update to Spring Framework 6.0.0 #716". ([spring-ldap 4.0.0 release](https://github.com/spring-projects/spring-ldap/releases/tag/4.0.0), [spring-ldap 3.0.0 release](https://github.com/spring-projects/spring-ldap/releases/tag/3.0.0))

### Inferences
- uPortal's current stack is entirely EOL in OSS terms. Spring 4.3 has been EOL since 2020-12-31, and Security 4.2.x was marked EOL in Oct 2020 by the Security versions wiki.
- The 7.x line is the only OSS-supported landing zone in Oct 2026. Targeting 7.0.x now and following 7.1 in Nov 2026 avoids a second major hop.
- Release alignment: the Spring Security 7.1, Session 4.1 and LDAP 4.1 releases (June 2026) are the "Boot 4.1" train on Framework 7.0.x. The 7.2 / 4.2 milestones (Sep 2026) are presumably the train for Framework 7.1 in Nov 2026. This is inferred from release dates and the 7.1.x build file.

### Gaps
- I could not retrieve the official spring.io commercial (Tanzu) end-of-support dates for 5.3, 6.2 and 7.0, nor OSS end dates for 7.0, Security 7.0/7.1, Web Flow 3.0/4.0, Session 3.5/4.x or LDAP 3.3/4.x. The tables are rendered by JavaScript, and api.spring.io and endoflife.date were blocked. The aggregator dates above conflict and should be checked on https://spring.io/projects/*#support.
- I didn't find an official statement putting Spring Web Flow in "maintenance mode". It continues to receive CVE fixes and major versions that track the Framework.

## Q2. Compatibility matrix that forces lockstep upgrades

### Takeaway
Each Spring Framework generation fixes the Java level, the javax/jakarta namespace, the Servlet level (and so the Tomcat version), the JPA/Hibernate level, and the Jackson generation. Security, Session, LDAP, Web Flow and springdoc must be upgraded together with the Framework. The hard walls are three hops: 5.3 to 6.x (javax to jakarta, Java 17, Tomcat 10.1, Hibernate 5.6-jakarta or 6.x), 6.2 to 7.0 (Servlet 6.1/Tomcat 11, JPA 3.2/Hibernate 7.1+, Jackson 3 default), and 7.1 to 7.2 (Jackson 2 support removed).

### Cited Findings
- Spring 5.0 baseline: JDK 8, Java EE 7 API level with runtime support for EE 8 (Servlet 3.1/4.0, JPA 2.1/2.2, Bean Validation 1.1/2.0). Servers are Tomcat 8.5+ and Jetty 9.4+. Libraries are Jackson 2.9+, EhCache 2.10+ and Hibernate 5.0+. ([Spring Framework 5.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.0-Release-Notes))
- Spring 5.3: "Hibernate ORM 5.2+ baseline, with a focus on ORM 5.4.x" and "Jackson support covers Jackson 2.9 to 2.12." ([Spring Framework 5.3 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.3-Release-Notes))
- Spring 6.0 "raises the minimum requirements to Java 17+ and Jakarta EE 9+". It requires "Hibernate ORM 5.6.x with the `hibernate-core-jakarta` artifact" or Hibernate ORM 6.1, Hibernate Validator 7.0/8.0, and "Tomcat 10, Jetty 11, or Undertow 2.2.19 ... For the latest server generations, consider Tomcat 10.1." ([Spring Framework 6.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes))
- Spring 6.1 minimums are SnakeYAML 2.0, Jackson 2.14 and others. Spring 6.2 recommends "Jackson 2.18/2.19 while preserving runtime compatibility with Jackson 2.15+." ([Spring Framework 6.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes), [Spring Framework 6.2 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes))
- Spring 7.0 minimums: "Servlet 6.1 (Tomcat 11.0, Jetty 12.1)", "JPA 3.2 (Hibernate ORM 7.1/7.2)", "Bean Validation 3.1 (Hibernate Validator 9.0/9.1)", JUnit 6, and the JDK 17 baseline retained. Undertow support was removed because Undertow lacks Servlet 6.1. ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- Spring 7.0 makes Jackson 3.x the default with fallback to 2.x. Jackson 2 is deprecated, "our current plan is to disable its auto-detection in 7.1 and remove its support entirely in 7.2." ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- Spring 7.1 raises the minimum to "Jackson 3.1 for LTS support" and adds Hibernate ORM 7.3 support. ([Spring Framework 7.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.1-Release-Notes))
- Spring Session lockstep: 2.0 "requires Java 8 and Spring Framework 5.0 as a baseline." 3.0 "requires Java 17 and Spring Framework 6.0 as a baseline." 4.0 builds on Framework 7.0.0. ([Spring Session 2.0 docs](https://github.com/spring-projects/spring-session/blob/2.0.0.RELEASE/docs/src/docs/asciidoc/index.adoc), [Spring Session 3.x upgrading](https://github.com/spring-projects/spring-session/blob/3.0.0/spring-session-docs/modules/ROOT/pages/upgrading.adoc), [spring-session 4.0.0 libs](https://github.com/spring-projects/spring-session/blob/4.0.0/gradle/libs.versions.toml))
- Spring Web Flow lockstep: 2.5 <!-- vale ai-tells.FormalTransitions = NO --><!-- direct quote of the Web Flow 2.5 docs -->"provides an upgrade path to Spring Framework 5 that in turn requires Java 8+, Servlet 3.1, Hibernate 5, Tiles 3."<!-- vale ai-tells.FormalTransitions = YES --> 3.x is for Framework 6 / Java 17 / Jakarta EE 9. 4.x is for Framework 7 / Jakarta EE 11. ([Web Flow 2.5 whatsnew](https://github.com/spring-projects/spring-webflow/blob/v2.5.0.RELEASE/src/reference/whatsnew.xml), [spring-webflow releases](https://github.com/spring-projects/spring-webflow/releases))
- Spring LDAP lockstep: 3.0.0 moved to Framework 6.0.0 and 4.0.0 moved to Framework 7.0.0. ([spring-ldap 3.0.0](https://github.com/spring-projects/spring-ldap/releases/tag/3.0.0), [spring-ldap 4.0.0](https://github.com/spring-projects/spring-ldap/releases/tag/4.0.0))
- springdoc lockstep: <!-- vale ai-tells.FigurativeIdioms = NO --><!-- direct quote of the springdoc README -->"MAJOR version increments are released in lockstep with Spring Boot MAJOR releases."<!-- vale ai-tells.FigurativeIdioms = YES --> The current README targets Spring Boot 4.x. 3.0.0 was released 2025-11-21 and 3.1.1 on 2026-09-06, while 2.9.x is still maintained (2.9.1 on 2026-09-06). ([springdoc README](https://github.com/springdoc/springdoc-openapi/blob/main/README.md), [Maven Central: springdoc-openapi-starter-webmvc-ui](https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/))
- Current third-party versions on Maven Central are Jackson 2.x 2.22.3, Jackson 3 `tools.jackson.core:jackson-databind` 3.2.3 and 3.1.7 (2026-09-22), Hibernate ORM 7.4.12.Final (2026-10-04) with 6.6.58.Final still patched, Ehcache 3 `org.ehcache:ehcache` 3.12.0 (2026-04-03), and Apereo CAS Java client `org.apereo.cas.client:cas-client-core` 4.1.1 (2026-05-20). ([Maven Central: jackson-databind 2.x](https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/), [tools.jackson databind](https://repo1.maven.org/maven2/tools/jackson/core/jackson-databind/), [hibernate-core](https://repo1.maven.org/maven2/org/hibernate/orm/hibernate-core/), [ehcache](https://repo1.maven.org/maven2/org/ehcache/ehcache/), [cas-client-core](https://repo1.maven.org/maven2/org/apereo/cas/client/cas-client-core/))

**Matrix.** Rows are synthesized from the citations above. Cells marked (inf.) are inferred from release dates or build files rather than from an official matrix.

| Spring FW | Java | EE namespace / level | Servlet → Tomcat | JPA → Hibernate ORM | Jackson | Security | Session | LDAP | Web Flow | API docs |
|---|---|---|---|---|---|---|---|---|---|---|
| 4.3 (EOL 2020) | (not re-verified) | javax, Java EE 6/7 | ≤3.1 → Tomcat 8.5 used by uPortal-start | Hibernate 3/4/5 | 2.x | 4.2 | 1.3 | 2.3 | 2.4 (has portlet support) | springfox 2.9 |
| 5.3 (OSS ended 2024-08) | 8 to 21 | javax, Java EE 7 to 8 | 3.1/4.0 → Tomcat 8.5/9 | 2.1/2.2 → 5.2+ (5.4 focus) | 2.9 to 2.12 at GA | 5.8 | 2.x (inf.) | 2.x (inf.) | 2.5 (no portlet) | springfox 3.0 (dead) |
| 6.2 (OSS ended 2026-06) | 17 to 25 | jakarta, EE 9 to 10 | 5.0/6.0 → Tomcat 10.1 | 3.1 → 5.6-jakarta or 6.x | 2.15+ (2.18/2.19 rec.) | 6.5 | 3.x (3.5 inf.) | 3.x (3.3 inf.) | 3.0 | springdoc 2.x |
| 7.0 (current) | 17+ (25 rec.) | jakarta, EE 11 (early EE 12) | 6.1 → Tomcat 11 / Jetty 12.1; no Undertow | 3.2 → 7.1/7.2 | 3.x default, 2.x deprecated | 7.0 / 7.1 | 4.0 / 4.1 | 4.0 / 4.1 | 4.0 | springdoc 3.x |
| 7.1 (Nov 2026) | 17+ | EE 11 to 12 | 6.1+ | adds 7.3 | Jackson 3.1 min; Jackson 2 auto-detection off | 7.2 (M2 now, inf.) | 4.2 (M1, inf.) | 4.2 (M1, inf.) | ? | ? |
| 7.2 (future) | n/a | n/a | n/a | n/a | Jackson 2 support removed | n/a | n/a | n/a | n/a | n/a |

### Inferences
- uPortal-start currently pins Tomcat 8.5 / Servlet 3.0.1 and allows only Java 8 and 11 (local inventory: `codebase_inventory_platform.md`, section F/G). So Spring 6 forces Java 17 and Tomcat 10.1 at the same moment as javax→jakarta. Spring 7 forces Tomcat 11 and Hibernate 7.1+.
- Beyond 5.3, which is commercial-only, Spring has no supported generation on javax with Java 17+. An "intermediate stop" on 5.3 is useful only as a stepping stone, because Spring Security 5.8's "prepare for 6" toggles exist only there.
- Hibernate is tied to the Framework. Spring 7 needs JPA 3.2 / Hibernate ORM 7.1+, and Spring 7 removed `org.springframework.orm.hibernate5` (see Q4). So uPortal's Hibernate/JPA upgrade is part of the 6.2→7.0 hop, not an independent task.

### Gaps
- I didn't find an official compatibility table for Hibernate ORM 7.4 against Spring Framework 7.0/7.1. 7.1 lists 7.3 support and 7.0 lists 7.1/7.2.
- I did not verify which Spring Session 2.x/3.x and Spring LDAP 2.x/3.x minor exactly corresponds to Framework 5.3 or 6.2.

## Q3. Spring Portlet MVC removal and replacements (PortletMVC4Spring and others)

### Takeaway
Spring Framework 5.0 removed `org.springframework.web.portlet`, and Web Flow 2.5 removed its portlet integration. The only maintained fork is Liferay's PortletMVC4Spring. Its 5.x line (last release 5.3.4, April 2024) runs on javax Spring 5.1 to 5.3 and requires Portlet 3.0, which uPortal does not have: it uses Pluto 2.1.0-M3 / Portlet 2.0. A Jakarta line exists only as 6.0.0-M1 (tagged 2025-09-24). It targets Spring 6.2.10, Jakarta EE 10 and Jakarta Portlet 4.0 via Liferay-specific GAVs, it is published to Liferay's own Nexus rather than Maven Central, and no Spring 7 version exists. For uPortal's 37 DispatcherPortlet-based internal portlets and 17 portlet Web Flow contexts, this is the biggest blocker in the Spring migration.

### Cited Findings
- Spring 5.0 says: <!-- vale ai-tells.ColonUsage = NO --><!-- direct quote of the Spring 5.0 release notes -->"The Spring Framework no longer supports: Portlet, Velocity, JasperReports, XMLBeans, JDO, Guava (replaced by the Caffeine support). If those are critical to your project, you should stay on Spring Framework 4.3.x ... Alternatively, you may create custom adapter classes in your own project (possibly derived from Spring Framework 4.x)."<!-- vale ai-tells.ColonUsage = YES --> ([Spring Framework 5.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.0-Release-Notes))
- Git tree check: `web/portlet/DispatcherPortlet.java` and `web/servlet/ViewRendererServlet.java` are present in v4.3.30.RELEASE and absent from v5.0.0.RELEASE onward. ([spring-framework repo, tags v4.3.30.RELEASE vs v5.0.0.RELEASE](https://github.com/spring-projects/spring-framework/tree/v5.0.0.RELEASE))
- Spring Web Flow 2.4.8 contains 42 portlet-related source files, including `AbstractFacesPortletFlowConfiguration` and the `context/portlet` package. 2.5.0 and 3.0.0 contain none. ([spring-webflow v2.4.8.RELEASE](https://github.com/spring-projects/spring-webflow/tree/v2.4.8.RELEASE), [spring-webflow v2.5.0.RELEASE](https://github.com/spring-projects/spring-webflow/tree/v2.5.0.RELEASE))
- PortletMVC4Spring is "forked from Spring Portlet MVC". Liferay adopted it in March 2019. It added a "webflow" module with the portlet-related Spring Web Flow source in June 2021, plus an optional Spring Security-based CSRF module. Supported portals are "Liferay Portal 7.1+ and Apache Pluto 3.0+" ("Portlet 3.0 compliant portals"), and the demos were tested on both. ([liferay/portletmvc4spring README](https://github.com/liferay/portletmvc4spring))
- Migration from Spring Portlet MVC, per the README: upgrade to Spring 5.1.x, then replace `spring-webmvc-portlet` with `com.liferay.portletmvc4spring.framework`. Set DispatcherPortlet to `com.liferay.portletmvc4spring.DispatcherPortlet`. Replace `AnnotationMethodHandlerAdapter` with `PortletRequestMappingHandlerAdapter` (`customModelAndViewResolver`/`customArgumentResolver` are gone, and `customArgumentResolvers` takes `HandlerMethodArgumentResolver`). Switch multipart to `CommonsPortletMultipartResolver` or `StandardPortletMultipartResolver` (native Portlet 3.0). Globally rename `org.springframework.web.portlet` to `com.liferay.portletmvc4spring`. Artifacts are `...framework`, `...security` and `...webflow`. ([liferay/portletmvc4spring README](https://github.com/liferay/portletmvc4spring))
- Maven Central has releases only up to 5.3.4 (2024-04-26). The 5.1.x/5.2.x/5.3.x lines map to Spring 5.1/5.2/5.3, with patch releases on 2023-12-12 and 2024-04-26. ([Maven Central: com.liferay.portletmvc4spring.framework](https://repo1.maven.org/maven2/com/liferay/portletmvc4spring/com.liferay.portletmvc4spring.framework/))
- Git history (cloned 2026-10-10): tag `6.0.0-M1` was made on 2025-09-24 with commits "MVCS-77 Upgrade to Jakarta EE 10, Jakarta Portlet 4.0, and Spring 6.2.10". Other commits include "Specify the liferay-third-party repository ahead of 6.0.0-M1 release, since not releasing to Central", "temporarily rely on the Non-Official/Liferay GAV for the Portlet API" and "Liferay version of pluto, renamed to plutonium - temporarily depend on 4.0.0-M1". The last commit on master is 2025-09-24, and the branches are 4.1.x, 5.1.x, 5.2.x, 5.3.x and master. ([liferay/portletmvc4spring commits](https://github.com/liferay/portletmvc4spring/commits/master))
- The 6.0.0-M1 POM sets `spring.framework.version` 6.2.10 and `spring.security.version` 6.5.3. It uses `com.liferay.jakarta.portlet:com.liferay.jakarta.portlet-api:4.0.0` (provided), `jakarta.servlet-api` 6.0.0, `com.liferay.plutonium:*` 4.0.0-M1, and distribution repo `https://repository.liferay.com/nexus/content/repositories/third-party`. ([portletmvc4spring 6.0.0-M1 pom.xml](https://github.com/liferay/portletmvc4spring/blob/6.0.0-M1/pom.xml))
- A Liferay KB article from Sep 2025 (community-contributed) said the library "has not been updated to Spring 6 (because of the Jakarta namespace issues)". It said Spring 6 can only be used in Liferay Client Extensions, and that javax→jakarta portlet packages may cause ClassCastException. This predates or coincides with the 6.0.0-M1 tag, so treat it as superseded but indicative of the low maturity. ([Liferay Learn KB](https://learn.liferay.com/kb-article/spring-framework-6-and-jdk-21-compatibility-with-liferay-dxp))
- A 2019 Liferay blog announced the fork: JDK 8+, Portlet API 3.0, refactored and tested with Spring 5.1.x. ([liferay.dev blog](https://liferay.dev/b/what-does-the-future-hold-for-spring-portlet-mvc-long-live-portletmvc4spring-))
- uPortal-start pins `plutoVersion=2.1.0-M3` and `portletApiDependency=org.apache.portals:portlet-api_2.1.0_spec:1.0` (Portlet 2.0). (local file `uPortal-start/gradle.properties`)
- Local inventory: "ALL 37 internal portlets in uPortal's portlet.xml are DispatcherPortlet-based" and "portlet FlowHandlerAdapter in 17 portlet contexts". (local note `codebase_inventory_platform.md` (section C))

### Inferences
- **PortletMVC4Spring 5.3.x (javax)** would let uPortal reach Spring 5.3 / Security 5.8 only if the portal's container moves to Portlet 3.0 (Pluto 3.x or equivalent), because the fork "requires" Portlet 3.0. Staying on Pluto 2.1 / Portlet 2.0 makes even the Spring 5 hop impossible without a custom fork.
- **PortletMVC4Spring 6.0.0-M1** is a milestone, not GA. It is outside Central, depends on non-standard Liferay GAVs (`com.liferay.jakarta.portlet-api`, "plutonium"), and covers Spring 6.2 only, whose OSS support has ended. No Spring 7 / Jakarta EE 11 build exists. A uPortal Spring 7 target would need one of the following:
  - forking PortletMVC4Spring and porting it to Spring 7 (APIs such as `PathMatcher`, `HandlerMappingIntrospector` and the Jackson 2 classes are changing);
  - vendoring the 4.3 `web.portlet` code into uPortal under its own package (what Spring 5.0 suggests); or
  - re-platforming the internal admin portlets away from Portlet MVC.
- Web Flow's portlet integration classes are not in Web Flow 3/4 at all. Only PortletMVC4Spring's `webflow` module contains it, so the 26 admin flows inherit the same blocker.
- Any blanket javax→jakarta rewrite must leave `javax.portlet` alone unless the container moves to Jakarta Portlet 4.0. The `jakarta.portlet` namespace exists only in Portlet 4.0 (Liferay's GAV here).

### Gaps
- I didn't find another maintained Spring Portlet MVC fork (Spring 6/7 or jakarta) besides PortletMVC4Spring.
- I did not establish whether a Jakarta Portlet 4.0 API has been published under an official Eclipse/Jakarta GAV. Liferay's commit message calls its GAV "Non-Official".
- I could not confirm whether PortletMVC4Spring 6.0.0-M1 has actually been tested on (non-Liferay) Apache Pluto. Its POM uses Liferay's "plutonium" fork. Apache Pluto's own project status is covered by the portlets/Jakarta notes, not here.

## Q4. Spring Framework per-hop breaking changes for a legacy XML-configured WAR (4.3 → 5.3 → 6.2 → 7.x)

### Takeaway
Most of the cost in each hop comes from removed integration packages:
- **4.3→5.0:** support for Portlet, Velocity, Tiles 2, Hibernate 3/4, Guava cache, Log4jConfigurer and the old `AnnotationMethodHandlerAdapter`.
- **5.3→6.0:** javax, Commons FileUpload, Ehcache 2, Joda-Time, HTTP Invoker/Hessian, Tiles 3, `HandlerInterceptorAdapter`.
- **6.x:** the `-parameters` requirement (6.1), a stricter placeholder parser and autowiring (6.2), and default-behaviour flips (trailing slash, PathPatternParser).
- **6.2→7.0:** `javax.annotation`/`javax.inject` support, `orm.hibernate5`, `spring-jcl`, ListenableFuture, Theme support, the suffix/trailing-slash options, plus Jackson 3 and Servlet 6.1.

Versioned XSD references (`spring-beans-3.1.xsd`) still resolve in 7.x. Versioned Spring Security XSDs do not (see Q5).

### Cited Findings
**4.3 → 5.x**
- 5.0 removed packages `beans.factory.access` (BeanFactoryLocator), `jdbc.support.nativejdbc`, `mock.staticmock`, `web.view.tiles2` and `orm.hibernate3/hibernate4` <!-- vale ai-tells.ColonUsage = NO --><!-- direct quote of the Spring 5.0 release notes -->("Minimum requirement: Tiles 3 and Hibernate 5 now")<!-- vale ai-tells.ColonUsage = YES -->, plus JSP tag attributes such as FormTag `commandName` (use `modelAttribute`). ([Spring Framework 5.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.0-Release-Notes))
- 5.0 replaced the commons-logging dependency with `spring-jcl`, its own jar that provides the Commons Logging API. It says "you might have to declare explicit excludes for 'commons-logging' and/or 'jcl-over-slf4j'". ([Spring Framework 5.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.0-Release-Notes))
- 5.0 CORS: "`allowCredentials` default value has been changed to `false`". ([Spring Framework 5.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.0-Release-Notes))
- Git tree check, v4.3.30 vs v5.0.0. Absent from 5.0.0 onward: `util/Log4jConfigurer`, `web/util/Log4jConfigListener`, `view/velocity/*`, `view/tiles2/*`, `orm/hibernate3|4/*` (including `HibernateJdbcException`), `view/jasperreports/*`, `cache/guava/GuavaCacheManager`, `beans/factory/access/SingletonBeanFactoryLocator`, and the servlet `annotation/DefaultAnnotationHandlerMapping` and `annotation/AnnotationMethodHandlerAdapter`. ([spring-framework tags v4.3.30.RELEASE / v5.0.0.RELEASE](https://github.com/spring-projects/spring-framework/tree/v4.3.30.RELEASE))
- 5.1: nested configuration classes are only detected on `@Configuration`/`@Component` classes. `Forwarded`/`X-Forwarded-*` handling is expected via `ForwardedHeaderFilter`. `DefaultUriBuilderFactory` encodes URI variables more strictly. ([Spring Framework 5.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.1-Release-Notes))
- 5.2: annotation lookup was rewritten ("all annotations must now be annotated with `@Retention(RetentionPolicy.RUNTIME)`"). `@RequestMapping()` with no path now matches only "". Suffix pattern matching was deprecated. Jackson 2.9.7+ is required. ([Spring Framework 5.2 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.2-Release-Notes))
- 5.3: <!-- vale ai-tells.FormalTransitions = NO --><!-- direct quote of the Spring 5.3 release notes -->"Spring MVC no longer performs `.*` suffix pattern matching by default, and likewise path extensions are no longer used by default to interpret the requested content type (e.g. `/person.pdf`...)"<!-- vale ai-tells.FormalTransitions = YES -->. `allowCredentials=true` requires explicit origins or `allowedOriginPatterns`. `PathPatternParser` rejects `"/path/**/other"`. HTTP Invoker, Hessian, RMI and JMS Invoker are deprecated. `@RequestParam` values that convert to null are treated as missing. ([Spring Framework 5.3 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.3-Release-Notes))

**5.3 → 6.x**
- 6.0 removed "Hessian, HTTP Invoker, JMS Invoker, and JAX-WS support" and EJB access (use `JndiObjectFactoryBean` or `<jee:jndi-lookup>`). "Joda-Time support in `org.springframework.format.datetime` has been removed. Please migrate to the `java.time` types." It also removed `org.springframework.cache.ehcache` (Ehcache 2.x): "Ehcache 3 is the direct replacement ... use `org.ehcache:ehcache` (with the `jakarta` classifier) ... using Ehcache through the JCache API or its new native API is preferred." ([Spring Framework 6.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes))
- 6.0 on JSR-330/250: `@Inject` is now in `jakarta.inject` and `@PostConstruct`/`@PreDestroy` in `jakarta.annotation`. "For the time being, Spring keeps detecting their `javax` equivalents as well." ([Spring Framework 6.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes))
- 6.0 bean property introspection no longer uses `java.beans.Introspector` by default. For 5.3-style behaviour, add `org.springframework.beans.BeanInfoFactory=org.springframework.beans.ExtendedBeanInfoFactory` to `META-INF/spring.factories`. ([Spring Framework 6.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes))
- 6.0 web: the `CommonsMultipartResolver` from Commons FileUpload, Tiles and FreeMarker JSP support were dropped ("We recommend `StandardServletMultipartResolver`"). Trailing slash matching "has been deprecated and its default value set to `false`" (restore with `configurer.setUseTrailingSlashMatch(true)`). Controllers are no longer detected by type-level `@RequestMapping` alone, so interface-based proxies break. `HttpMethod` is a class. `HttpComponentsClientHttpRequestFactory` requires Apache HttpClient 5. Servlet mocks need the Servlet 6.0 API. `SourceHttpMessageConverter` is no longer registered by default. ([Spring Framework 6.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes))
- 6.0 Spring MVC: "`PathPatternParser` used by default (with the ability to opt into `PathMatcher`)." ([Spring Framework 6.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes))
- 6.0 deprecated `LocalVariableTableParameterNameDiscoverer`. 6.1 removed it: <!-- vale ai-tells.VerbTricolon = NO --><!-- direct quote of the Spring 6.1 release notes -->"If you experience issues with dependency injection, property binding, SpEL expressions, or other use cases that depend on the names of parameters, you should compile your Java sources with the common Java 8+ `-parameters` flag"<!-- vale ai-tells.VerbTricolon = YES -->. The notes include the Maven `<parameters>true</parameters>` and Gradle `options.compilerArgs.add("-parameters")` snippets, and warn that Java agents can strip parameter info (fixed in JDK 19). ([Spring Framework 6.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes))
- Runtime symptom for `@RequestParam`/`@PathVariable` without explicit names: `IllegalArgumentException: Name for argument of type [...] not specified, and parameter name information not available via reflection. Ensure that the compiler uses the '-parameters' flag.` ([AbstractNamedValueMethodArgumentResolver.java (main)](https://github.com/spring-projects/spring-framework/blob/main/spring-web/src/main/java/org/springframework/web/method/annotation/AbstractNamedValueMethodArgumentResolver.java))
- 6.1 also changes the following:
  - `ThreadPoolTaskExecutor`/`Scheduler` reject tasks after context close starts.
  - `getMessage` after close throws.
  - `@RequestParam` `defaultValue` now applies to blank strings.
  - `throwExceptionIfNoHandlerFound` is true by default.
  - Most `ClientHttpRequestFactory` implementations no longer buffer, so there is no `Content-Length`.
  - `ParameterNamesModule` is auto-registered in `Jackson2ObjectMapperBuilder`.

  Source: the [Spring Framework 6.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes)
- 6.2:
  - Revised autowiring: parameter-name and `@Qualifier` matches overrule `@Priority`, and generic matching is stricter.
  - Invalid `@Configuration` classes (void `@Bean`) are rejected.
  - The container now logs "each override at `INFO` level".
  - The placeholder parser was "completely rewritten": keys containing `:` must be escaped (`${sub\://host}`), and escaped `\${...}` is now literal.
  - SpEL accessor ordering changed.
  - Since 6.2.19, SpEL is capped at 10,000 operations by default (`spring.expression.maxOperations`).

  Source: the [Spring Framework 6.2 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes)
- Git tree check, v5.3.39 vs v6.0.0. Absent from 6.0.0 onward: `cache/ehcache/EhCacheCacheManager`, `multipart/commons/CommonsMultipartResolver`, `remoting/httpinvoker/*`, `view/tiles3/*`, `format/datetime/joda/*` and `servlet/handler/HandlerInterceptorAdapter`. `LocalVariableTableParameterNameDiscoverer` is present in 6.0.0 and absent in 6.2.0. ([spring-framework tag v6.0.0](https://github.com/spring-projects/spring-framework/tree/v6.0.0))
- The same tree check shows these still present in v7.0.0: `PropertyPlaceholderConfigurer`, `XStreamMarshaller`, `HttpComponentsClientHttpRequestFactory` (HttpClient 5 since 6.0), `JCacheCacheManager`, `CaffeineCacheManager`, `MappingJackson2JsonView`/`MappingJackson2HttpMessageConverter` (Jackson 2, deprecated), `RequestContextListener`, `JndiObjectFactoryBean`, `SchedulerFactoryBean`, `MBeanExporter`, `XsltView`, `FreeMarkerConfigurer` and `AntPathMatcher`. ([spring-framework tag v7.0.0](https://github.com/spring-projects/spring-framework/tree/v7.0.0))

**6.2 → 7.x**
- 7.0 removed `spring-jcl` "in favor of Apache Commons Logging 1.3.0". It removed support for `javax.annotation` and `javax.inject` annotations: "`@javax.annotation.Resource`, `@javax.annotation.PostConstruct`, `@javax.inject.Inject`, etc ... you will need to migrate to their equivalents in the `jakarta.annotation` and `jakarta.inject` packages." ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- 7.0 removed the deprecated path options `suffixPatternMatch`/`registeredSuffixPatternMatch`, `trailingSlashMatch`, `favorPathExtension`/`ignoreUnknownPathExtensions` (including `PathExtensionContentNegotiationStrategy`) and `matchOptionalTrailingSeparator`. It also removed `ListenableFuture`, `webjars-locator-core` support (use `webjars-locator-lite`), OkHttp3 and `Theme` support. ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- 7.0 breaking changes: `HttpHeaders` no longer extends `MultiValueMap`. Native Hibernate support moved from `orm.hibernate5` to `orm.jpa.hibernate`, keeping only `LocalSessionFactoryBean/Builder`, `HibernateTransactionManager`, `SpringBeanContainer` and `SpringSessionContext`. `MutablePersistenceUnitInfo` is no longer a `PersistenceUnitInfo`. The JMS default `DestinationResolver` changed. CORS pre-flight is no longer rejected for empty config. CGLIB proxy defaulting now applies to all proxy processors. ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- 7.0 deprecations:
  - `RestTemplate` (formally `@Deprecated` in 7.1, removal planned for 8.0).
  - "The `<mvc:*` XML configuration namespace for Spring MVC is now deprecated ... no plans yet for removing it ... Other namespaces (like `<bean>`) are NOT deprecated."
  - The `<lang:*>` namespace and BeanShell.
  - JUnit 4 support (`SpringRunner`, `AbstractJUnit4SpringContextTests`, ...).
  - Jackson 2.x support.
  - "The use of `PathMatcher` in Spring MVC".
  - The `HandlerMappingIntrospector` SPI.
  - The `view.document`/`view.feed` packages.

  Source: the [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
- 7.0 null safety: "Spring nullness annotations with JSR 305 semantics are deprecated in favor of JSpecify annotations." `MethodParameter#isOptional` now checks only local `@Nullable` annotations, not inherited ones. ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- 7.0 PathPattern: "the legacy `AntPathMatcher` variant for matching HTTP request mappings is being deprecated". The previously missing feature of `**` at the start of a pattern ("`/**/pages/index.html`") is now supported. ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- 7.0.8: SpEL is capped at 10,000 operations. 7.0.9: `SimpleEvaluationContext` no longer compiles expressions by default. ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- 7.1 preview:
  - JAXB converters are no longer auto-detected for server apps.
  - `HandlerMappingIntrospector` is removed ("applications may now experience the absence of a `CorsConfigurationSource` bean").
  - `ProblemDetail.instance` is no longer set automatically.
  - `RestTemplate` is `@Deprecated`.
  - `ForwardedHeaderFilter` default constructors and `DataBinder#disallowedFields` are deprecated.
  - Derby support is deprecated.

  Source: the [Spring Framework 7.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.1-Release-Notes)
- Git tree check: present in v6.2.0 and absent in v7.0.0 are `orm/hibernate5/*`, `util/concurrent/ListenableFuture`, `servlet/theme/*` and the `spring-jcl` module. ([spring-framework tag v7.0.0](https://github.com/spring-projects/spring-framework/tree/v7.0.0))

**XML schemas**
- `spring.schemas` on main still maps versioned XSD URLs for 2.0 through 4.3 (such as `spring-beans-4.3.xsd`, `spring-mvc-3.0.xsd`, `spring-cache-3.1.xsd`) all to the current unversioned XSD. ([spring-beans spring.schemas (main)](https://github.com/spring-projects/spring-framework/blob/main/spring-beans/src/main/resources/META-INF/spring.schemas), [spring-context spring.schemas](https://github.com/spring-projects/spring-framework/blob/main/spring-context/src/main/resources/META-INF/spring.schemas), [spring-webmvc spring.schemas](https://github.com/spring-projects/spring-framework/blob/main/spring-webmvc/src/main/resources/META-INF/spring.schemas))

### Inferences
- **Versioned XSDs.** uPortal's 110 XML files with versioned Spring XSDs (local inventory) will still load on 7.x, because the versioned URLs are aliases of the current schema. The schema validated against is the current one, though, so attributes removed from current XSDs will fail validation. Rewriting to unversioned `https://www.springframework.org/schema/...xsd` is low-risk hygiene. This is not true for Spring Security's namespace (see Q5).
- **`-parameters` is the most insidious 6.1 gotcha for an XML app.** Besides `@RequestParam`/`@PathVariable`/`@PreAuthorize("#id")`, XML `<constructor-arg name="...">` resolution relies on parameter names (it falls under the "dependency injection" use case in the 6.1 note). Classes in third-party jars compiled without `-parameters` cannot be fixed by uPortal's compiler flags. Those bean definitions must switch to `index=`/`type=`, or the classes must have `@ConstructorProperties`.
- **Cache.** uPortal uses Ehcache 2.6 via the Spring cache abstraction, and `EhCacheCacheManager` is gone in 6.0. Mitigations:
  - Option A: use Spring's `JCacheCacheManager` over Ehcache 3 (`org.ehcache:ehcache:3.12.0`, `jakarta` classifier per the 6.0 notes). This requires converting `ehcache.xml` to Ehcache 3 XML.
  - Option B: use Caffeine via `CaffeineCacheManager`.

  The migration can start on 5.3, where `JCacheCacheManager` already exists (tree check), which de-risks the 6.0 hop.
- **Joda-Time.** Joda itself is still maintained (2.15.0 released 2026-10-01 per [Maven Central](https://repo1.maven.org/maven2/joda-time/joda-time/)), but Spring 6 dropped its formatting/`@DateTimeFormat` integration. The 86 Joda-importing files only break where Spring binding or formatting is involved. A full move to `java.time` is still advisable.
- **Inventory correction.** The local inventory lists `XStreamMarshaller` as removed in Spring 6. The tree check shows it is still present in 6.0.0, 6.2.0 and 7.0.0. `PropertyPlaceholderConfigurer` (47 files) is also still present in 7.0.0, though long deprecated in favour of `PropertySourcesPlaceholderConfigurer`. The 6.2 placeholder-parser rewrite (escaping `:` in keys) applies to both.
- **Suffix patterns.** If any uPortal REST endpoints rely on suffix patterns or path-extension content negotiation (`*.json`), they stop matching implicitly in 5.3 and lose the opt-in completely in 7.0. Explicit mappings are needed.
- **spring-jcl.** The 7.0 replacement of `spring-jcl` by commons-logging 1.3.0 can collide with `jcl-over-slf4j` on an SLF4J/Logback classpath. Exclude one of them.

### Gaps
- "`@Autowired` on non-public" members: I didn't find a release-note change restricting `@Autowired` on non-public fields or methods in 5.x to 7.x. If this concern came from a specific source, it remains unverified.
- I did not obtain the Spring 4.3 JDK range from a primary source in this session.

## Q5. Spring Security per-hop changes (4.2 → 5.8 → 6.x → 7.x)

### Takeaway
For an XML-namespace app the path is 4.2 → 5.8, flipping each "6.0 default" toggle one at a time, → 6.5, applying the "Preparing for 7.0" toggles, → 7.0/7.1. The non-obvious XML gotchas are:
- Every Security major/minor rejects older versioned `spring-security-X.Y.xsd` declarations (7.0 is fatal for anything older than 7.0), so use the unversioned XSD.
- Spring Security 7's XML `request-matcher` accepts only `path|regex|ciRegex`; `ant` and `mvc` are gone, and PathPattern syntax applies to `<intercept-url pattern>`.
- `spring-security-cas` does not exist for 6.0.x (removed in 6.0, re-added in 6.1) and moves to the `org.apereo.cas.client` 4.x package.
- The legacy Access API (`access-decision-manager-ref`, voters) moves to the separate `spring-security-access` module in 7.0.
- `WebSecurityConfigurerAdapter`, `authorizeRequests` and `.and()` are gone, but they matter only for Java config.

### Cited Findings
**4.2 → 5.x (password storage, CSRF)**
- "Prior to Spring Security 5.0 the default `PasswordEncoder` was `NoOpPasswordEncoder`." `DelegatingPasswordEncoder` stores `{id}encoded` values (`{bcrypt}`, `{noop}`, `{sha256}`, ...). Unprefixed hashes fail with `IllegalArgumentException: There is no PasswordEncoder mapped for the id "null"`, unless `setDefaultPasswordEncoderForMatches(...)` is set or a `NoOpPasswordEncoder` bean is exposed ("If you are migrating from Spring Security 4.2.x you can revert to the previous behavior..."). ([password-storage.adoc (5.8.x)](https://github.com/spring-projects/spring-security/blob/5.8.x/docs/modules/ROOT/pages/features/authentication/password-storage.adoc))
- 5.8 "Preparing for 6.0" step 1 says to update to latest 5.8 ("fully compatible with Spring Security 5.7"). Password-encoding minimums for PBKDF2/SCrypt/Argon2 change in 6.0: constructors are replaced by `defaultsForSpringSecurity_v5_5()`/`_v5_8()` factories, and `DelegatingPasswordEncoder` handles the upgrade. Also stop using `Encryptors.queryableText`. ([Preparing for 6.0 (5.8.x source)](https://github.com/spring-projects/spring-security/blob/5.8.x/docs/modules/ROOT/pages/migration/index.adoc))
- 5.8 exploit protections:
  - "Defer Loading CsrfToken", with opt-outs for SPAs using `CookieCsrfTokenRepository`/`HttpSessionCsrfTokenRepository`.
  - "Protect against CSRF BREACH" (masked tokens), with opt-outs for AngularJS/JS frameworks.
  - "CSRF BREACH with WebSocket support".

  Source: [exploits.adoc (5.8.x)](https://github.com/spring-projects/spring-security/blob/5.8.x/docs/modules/ROOT/pages/migration/servlet/exploits.adoc)
- 5.8 session management: "Require Explicit Saving of SecurityContextRepository". In 6, `SecurityContextHolderFilter` only reads and "Users now must explicitly save the `SecurityContext`" (also on logout). The default repository becomes `DelegatingSecurityContextRepository(RequestAttribute..., HttpSession...)`. `SessionAuthenticationStrategy` must be invoked explicitly by authentication mechanisms (`requireExplicitAuthenticationStrategy`). `RequestCache` querying is optimized. ([session-management.adoc (5.8.x)](https://github.com/spring-projects/spring-security/blob/5.8.x/docs/modules/ROOT/pages/migration/servlet/session-management.adoc))
- 5.8 authorization:
  - Method security: replace `<global-method-security pre-post-enabled="true"/>` with `<method-security/>`.
  - Change the `@EnableTransactionManagement` order.
  - Replace custom `AccessDecisionManager`/voters, `AfterInvocationProvider` and `RunAsManager`.
  - Request security: XML examples use `<http use-authorization-manager="true" filter-all-dispatcher-types="true">` and `once-per-request`.
  - "Ensure that all requests have defined authorization rules".
  - "Migrate `hasIpAddress`".
  - "Permit `FORWARD` when using Spring MVC".
  - "Replace `hasRole` with `hasAuthority` if using `GrantedAuthorityDefaults`".

  Source: [authorization.adoc (5.8.x)](https://github.com/spring-projects/spring-security/blob/5.8.x/docs/modules/ROOT/pages/migration/servlet/authorization.adoc)
- 5.8 config: add `@Configuration` to `@Enable*`; use the new `requestMatchers`/`securityMatchers`; "Stop Using `WebSecurityConfigurerAdapter`" (publish `SecurityFilterChain`, `WebSecurityCustomizer` and `AuthenticationManager` beans, with LDAP/JDBC/in-memory examples). ([config.adoc (5.8.x)](https://github.com/spring-projects/spring-security/blob/5.8.x/docs/modules/ROOT/pages/migration/servlet/config.adoc))
- 5.8 authentication: SHA-256 Remember-Me (`encodingAlgorithm`/`matchingAlgorithm`), and propagate `AuthenticationServiceException`. ([authentication.adoc (5.8.x)](https://github.com/spring-projects/spring-security/blob/5.8.x/docs/modules/ROOT/pages/migration/servlet/authentication.adoc))

**6.0**
- "In 6.0, `<http>` defaults `once-per-request` to `false`, `filter-all-dispatcher-types` to `true`, and `use-authorization-manager` to `true`." `<websocket-message-broker>` defaults `use-authorization-manager` to true. ([Migrating to 6.0 authorization (6.5.x source)](https://github.com/spring-projects/spring-security/blob/6.5.x/docs/modules/ROOT/pages/migration/servlet/authorization.adoc))
- 6.0: "change your `javax` imports to `jakarta` imports", and "Compile With `--parameters`" if method-security annotations use parameter names (such as `@PreAuthorize("@authz.checkPermission(#id, authentication)")`). ([Migrating to 6.0 (6.5.x source)](https://github.com/spring-projects/spring-security/blob/6.5.x/docs/modules/ROOT/pages/migration/index.adoc), [migration-7 authorization (6.5.x)](https://github.com/spring-projects/spring-security/blob/6.5.x/docs/modules/ROOT/pages/migration-7/authorization.adoc))
- CAS: "In Spring Security 6.0, the CAS support has been removed ... There is no direct replacement," with a suggestion to use CAS as an OAuth2 provider. ([cas.adoc (5.8.x)](https://github.com/spring-projects/spring-security/blob/5.8.x/docs/modules/ROOT/pages/migration/servlet/cas.adoc))
- CAS re-add: issue #11674 "Re-add support for CAS" was closed as completed on 2023-03-02 in milestone 6.1.0-M2, unblocked by the Java CAS Client v4 release (Jakarta, JDK 17) on 2022-11-25. A 2024-01-17 comment describes problems validating tickets with Security 6.2 + CAS 7, and it has no reply. ([spring-security#11674](https://github.com/spring-projects/spring-security/issues/11674))
- Maven Central has `spring-security-cas` 5.8.0 to 5.8.16 and then 6.1.0 (2023-05-15) onward. There is no 6.0.x, and 7.1.1 is current. ([Maven Central: spring-security-cas](https://repo1.maven.org/maven2/org/springframework/security/spring-security-cas/))
- The 7.0 CAS reference uses `org.apereo.cas.client.validation.Cas20ServiceTicketValidator`, `Cas20ProxyTicketValidator`, `org.apereo.cas.client.session.SingleSignOutFilter`/`SingleSignOutHttpSessionListener` and `org.apereo.cas.client.proxy.ProxyGrantingTicketStorageImpl`. These are the Apereo CAS client 4.x packages, replacing the 3.x `org.jasig.cas.client`. ([cas.adoc (7.0.x)](https://github.com/spring-projects/spring-security/blob/7.0.x/docs/modules/ROOT/pages/servlet/authentication/cas.adoc))

**Preparing for 7.0 (in 6.5)**
- "The Lambda DSL ... the prior configuration style will not be valid in Spring Security 7." `.and()` is removed, `HttpSecurity#apply` is replaced by `.with(...)`, and `shouldFilterAllDispatcherTypes` is replaced by `dispatcherTypeMatchers`. ([migration-7/configuration.adoc (6.5.x)](https://github.com/spring-projects/spring-security/blob/6.5.x/docs/modules/ROOT/pages/migration-7/configuration.adoc))
- Web changes in 6.5 preparing for 7.0:
  - Login redirects become relative URIs (opt out with `favorRelativeUris=false`).
  - `PortResolver` is removed (expose `PortResolver.NO_OP`).
  - "In Spring Security 7, `AntPathRequestMatcher` and `MvcRequestMatcher` are no longer supported and the Java DSL requires that all URIs be absolute." In XML, use `request-matcher="path"`.
  - `SwitchUserFilter` and `setFilterProcessingUrl` move to `PathPatternRequestMatcher`.
  - CAS `proxyReceptorUrl` was previously matched as `/**/proxy/receptor` and must become an absolute `PathPatternRequestMatcher` (`setProxyReceptorRequestMatcher`).
  - JSP taglibs / `WebInvocationPrivilegeEvaluator` move to `AuthorizationManagerWebInvocationPrivilegeEvaluator` ("allows the authentication to be `null` if the authorization rule is `permitAll`").
  - Servlet-path prefixes must be supplied explicitly (`basePath("/mvc")`). JSP-servlet patterns like `/**/*.jsp` have "not yet a general-purpose replacement" (use `RegexRequestMatcher`).
  - `requires-channel` is replaced by `redirect-to-https-request-matcher-ref`.

  Source: [migration-7/web.adoc (6.5.x)](https://github.com/spring-projects/spring-security/blob/6.5.x/docs/modules/ROOT/pages/migration-7/web.adoc)
- LDAP: "support for ApacheDS will be discontinued in version 7.0 ... migrating to UnboundId". ([migration-7/ldap.adoc (6.5.x)](https://github.com/spring-projects/spring-security/blob/6.5.x/docs/modules/ROOT/pages/migration-7/ldap.adoc))
- Method security: `AnnotationTemplateExpressionDefaults` becomes the default in 7, and `AuthorizationAdvisor` beans replace `AuthorizationAdvisorProxyFactory#addAdvisor`. ([migration-7/authorization.adoc (6.5.x)](https://github.com/spring-projects/spring-security/blob/6.5.x/docs/modules/ROOT/pages/migration-7/authorization.adoc))

**7.0 / 7.1**
- 7.0 removals and moves:
  - "Removed `and()` from the `HttpSecurity` DSL"
  - "Removed `authorizeRequests` in favor of `authorizeHttpRequests`"
  - "Removed `MvcRequestMatcher` and `AntPathRequestMatcher` in favor of `PathPatternRequestMatcher`"
  - "Moved Access API (`AccessDecisionManager`, `AccessDecisionVoter`, etc.) to a new module, `spring-security-access`"
  - "Removed `AuthorizationManager#check`"
  - "Removed `ApacheDsContainer`"
  - "Removed support for password grant"
  - "Removed Open SAML 4 support"
  - "Changed LoginUrlAuthenticationEntryPoint to favor relative redirects by default"

  Added: MFA, Spring Authorization Server and Kerberos merged in, and Password4j encoders. ([What's New in 7.0 (7.0.x source)](https://github.com/spring-projects/spring-security/blob/7.0.x/docs/modules/ROOT/pages/whats-new.adoc))
- 7.0 says: "If Using Access API, Add `spring-security-access`." Also replace `SecurityJackson2Modules` with `SecurityJacksonModules` (Jackson 3). "The Jackson 3 support uses a format compatible with the now deprecated Jackson 2 one, so class instances serialized with Jackson 2 should be deserializable." ([Migrating to 7.0 (7.0.x)](https://github.com/spring-projects/spring-security/blob/7.0.x/docs/modules/ROOT/pages/migration/index.adoc), [migration/servlet/authorization.adoc (7.0.x)](https://github.com/spring-projects/spring-security/blob/7.0.x/docs/modules/ROOT/pages/migration/servlet/authorization.adoc))
- The 7.0 XML schema restricts `request-matcher` to `{"path" | "regex" | "ciRegex"}` ("'path' (for PathPatternRequestMatcher)"). It still contains `use-expressions`, `access-decision-manager-ref`, `use-authorization-manager`, `authorization-manager-ref`, `global-method-security`, `redirect-to-https-request-matcher-ref` and `request-matcher-ref`. ([spring-security-7.0.rnc](https://github.com/spring-projects/spring-security/blob/7.0.x/config/src/main/resources/org/springframework/security/config/spring-security-7.0.rnc))
- The 7.0 `SecurityNamespaceHandler` fails fatally with "You cannot use any XSD older than spring-security-7.0.xsd. Either change to spring-security.xsd or spring-security-7.0.xsd". It accepts only schema locations matching `spring-security-7\.0.*.xsd` or `spring-security.xsd`, even though `spring.schemas` still lists old URLs such as `spring-security-4.2.xsd`. ([SecurityNamespaceHandler.java (7.0.x)](https://github.com/spring-projects/spring-security/blob/7.0.x/config/src/main/java/org/springframework/security/config/SecurityNamespaceHandler.java), [spring.schemas (7.0.x)](https://github.com/spring-projects/spring-security/blob/7.0.x/config/src/main/resources/META-INF/spring.schemas))
- 7.1 is a feature release: `InetAddressMatcher`, `ConditionalAuthorizationManager`, MFA conditions and `PreFlightRequestFilter` support. ([What's New in 7.1](https://github.com/spring-projects/spring-security/blob/7.1.x/docs/modules/ROOT/pages/whats-new.adoc))
- A "Preparing for 8.0" guide exists, with no release date for 8.0. ([migration-8 (7.0.x)](https://github.com/spring-projects/spring-security/blob/7.0.x/docs/modules/ROOT/pages/migration-8/index.adoc))

### Inferences
- **XSD declarations.** uPortal declares `spring-security-4.2.xsd` in 3 files (local inventory). These must be changed to the unversioned `spring-security.xsd` before 5.x, because the namespace handler's version check is fatal. Doing it once avoids the same failure at every subsequent hop.
- **Intercept-url patterns.** With `ant` matchers gone in 7.0, every `<intercept-url pattern=...>` is parsed as a `PathPattern`. Patterns with `**` in the middle (`/a/**/b`) are invalid under PathPattern (5.3 notes), although Framework 7.0 adds support for a leading `/**/`. These sources differ on whether leading-`**` JSP patterns work in Security. The 6.5 doc predates Framework 7's change. A custom `request-matcher-ref`/`regex` matcher is the fallback.
- **CSRF.** The `csrf request-matcher-ref` customization (local inventory) carries over, but 6.0's deferred token plus BREACH masking changes what JavaScript clients must send. Any uPortal JS that reads the raw token from a cookie or meta tag must be re-tested.
- **CAS.** uPortal does not use `spring-security-cas`. It uses Jasig cas-client 3.6.2 servlet filters (local inventory). So the CAS-module gap in 6.0 is irrelevant. What matters is moving from Jasig cas-client 3.x (`org.jasig.cas.client`, javax) to Apereo cas-client 4.x (`org.apereo.cas.client`, jakarta), which is also a filter-class and package rename in web.xml / Spring XML.
- **Passwords.** uPortal hashes passwords with jasypt and its own code, not Spring's `PasswordEncoder` (local inventory). The 5.0 `DelegatingPasswordEncoder` change affects it only where a namespace `<authentication-provider>` without an explicit encoder is used.
- **Method security.** `<global-method-security pre-post-enabled="true">` (2 contexts) still parses in 7.0. The supported path is `<method-security/>` (`AuthorizationManager`-based), with `-parameters` for `#param` SpEL.
- **Pre-auth filters.** The custom `AbstractPreAuthenticatedProcessingFilter` subclasses need review under explicit `SecurityContextRepository` saving. The pre-auth filter sets the context, and something must persist it to the session or the user re-authenticates on every request. Unit-test this at the 5.8 opt-in stage.

### Gaps
- I could not fetch the Spring Security 5.0 "what's new"/migration notes (4.2→5.0) beyond the password-storage doc, so other 5.0-era XML namespace removals are not enumerated here.
- I did not verify the 6.0 XML attribute name for explicit save (the 5.8 doc includes it via a partial, `security-context-explicit.adoc`, which I did not read).

## Q6. Spring Web Flow, Session and LDAP, springfox→springdoc, and the legacy Spring add-ons

### Takeaway
Spring Web Flow is alive (3.0.x for Spring 6, 4.0.x for Spring 7, with CVE fixes in June 2026) but has had no portlet support since 2.5.
- **Spring Session:** goes 1.3 → 2.x (module split, `ExpiringSession` merged, JDBC schema change requiring a table rebuild) → 3.x (Java 17/Spring 6) → 4.x (Spring 7, Jackson 3, Hazelcast/MongoDB handed to their vendors).
- **Spring LDAP:** 2.3 → 3.x → 4.x is mostly a baseline bump, since JNDI stays `javax.naming`.
- **springfox:** dead since 2020. The replacement is springdoc 2.x (Spring 6) or 3.x (Spring 7), but springdoc is Spring Boot-oriented.
- **Legacy add-ons:** spring-modules-cache (2000s), json-lib-ext-spring (2008) and Ehcache 2 have no Jakarta successors and must be replaced by Spring's own cache abstraction, Jackson views and JCache/Ehcache 3.

### Cited Findings
**Web Flow**
- 2.5: "As of 2.5 there is no longer a spring-js module. The classes from that module have been kept but moved to new packages in the spring-webflow module. The spring-js-resources module is available as an optional module that must be included explicitly." 2.5 also requires JSF 2.2+. ([Web Flow 2.5 whatsnew](https://github.com/spring-projects/spring-webflow/blob/v2.5.0.RELEASE/src/reference/whatsnew.xml))
- 3.0.0-M1 adopted "Java 17, Spring Framework 6, Jakarta EE 9", and 3.0.0-RC1 <!-- vale ai-tells.VerbTricolon = NO --><!-- direct quote of the Web Flow release page -->"Migrate\[d\] Spring Faces to Spring Framework 6, Jakarta EE, and JSF 4"<!-- vale ai-tells.VerbTricolon = YES -->. 3.0.1 added <!-- vale ai-tells.SemicolonUsage = NO --><!-- direct quote of the Web Flow release page -->"Compatibility fixes for Spring Framework 6.2 and 6.1; AuthorizationManager support"<!-- vale ai-tells.SemicolonUsage = YES -->. 4.0.0 targets Jakarta EE 11, Faces 4.1, Framework 7.0 and Security 7.0. ([spring-webflow releases](https://github.com/spring-projects/spring-webflow/releases))
- Web Flow 4.0.0 still includes `persistence/HibernateFlowExecutionListener` and `JpaFlowExecutionListener` plus the `spring-binding` EL/expression packages. The repo still contains the `spring-js-resources` and `spring-faces` modules. ([spring-webflow v4.0.0 tree](https://github.com/spring-projects/spring-webflow/tree/v4.0.0))

**Spring Session**
- 2.0 split `spring-session` into `spring-session-core` plus store modules (Data Redis, JDBC, Hazelcast), and moved MongoDB/GemFire to separate repos. It also made these changes:
  - "`ExpiringSession` API has been merged into `Session` API".
  - The `SessionRepository` API was renamed to Spring Data conventions.
  - The Redis namespace is "fully configurable".
  - Spring Session no longer registers its own `RedisTemplate`/`JdbcTemplate` beans.
  - <!-- vale ai-tells.FillerIntensifier = NO --><!-- direct quote of the Spring Session 2.0 docs -->"the support for managing multiple users' sessions in a single browser instance has been removed"<!-- vale ai-tells.FillerIntensifier = YES --> as part of "the changes to `HttpSessionStrategy`".

  Source: the [Spring Session 2.0 docs, "Upgrading to 2.x"](https://github.com/spring-projects/spring-session/blob/2.0.0.RELEASE/docs/src/docs/asciidoc/index.adoc)
- The JDBC schema differs between 1.3.5 and 2.0.0. 1.3.5 has `SPRING_SESSION(SESSION_ID PK, ...)` and `SPRING_SESSION_ATTRIBUTES(SESSION_ID FK, ...)`. 2.0.0 has `SPRING_SESSION(PRIMARY_ID PK, SESSION_ID unique, ..., EXPIRY_TIME NOT NULL, ...)` and `SPRING_SESSION_ATTRIBUTES(SESSION_PRIMARY_ID FK, ...)`. No migration script was found in the 2.0.0 tree. ([1.3.5 schema-postgresql.sql](https://github.com/spring-projects/spring-session/blob/1.3.5.RELEASE/spring-session/src/main/resources/org/springframework/session/jdbc/schema-postgresql.sql), [2.0.0 schema-postgresql.sql](https://github.com/spring-projects/spring-session/blob/2.0.0.RELEASE/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-postgresql.sql))
- 3.0 "requires Java 17 and Spring Framework 6.0 as a baseline". ([Spring Session 3.x upgrading](https://github.com/spring-projects/spring-session/blob/3.0.0/spring-session-docs/modules/ROOT/pages/upgrading.adoc))
- 4.0: <!-- vale ai-tells.ColonUsage = NO --><!-- release-note titles quoted verbatim -->"Spring Session Hazelcast: Now Led by Hazelcast Team" and "Spring Session MongoDB: Now Led by MongoDB Team"<!-- vale ai-tells.ColonUsage = YES -->. The main repo now contains only `spring-session-core`, `spring-session-data-redis` and `spring-session-jdbc`. ([What's New in 4.0](https://github.com/spring-projects/spring-session/blob/4.0.0/spring-session-docs/modules/ROOT/pages/whats-new.adoc), [spring-session repo](https://github.com/spring-projects/spring-session))

**Spring LDAP**
- The 3.0.0 and 4.0.0 GitHub releases list only baseline updates ("Update to Spring Framework 6.0.0", "Update to Spring Framework 7.0.0"). The 4.2 what's-new lists one breaking change: `AttributeSchema` validates that LDAP names map to Java identifiers. ([spring-ldap 3.0.0](https://github.com/spring-projects/spring-ldap/releases/tag/3.0.0), [spring-ldap 4.0.0](https://github.com/spring-projects/spring-ldap/releases/tag/4.0.0), [What's New in Spring LDAP 4.2 (main)](https://github.com/spring-projects/spring-ldap/blob/main/modules/ROOT/pages/whats-new.adoc))
- Spring Security 7 removed ApacheDS embedded LDAP in favour of UnboundID (`unboundid-ldapsdk` 7.0.5 in Security 7.1's build). ([What's New in 7.0](https://github.com/spring-projects/spring-security/blob/7.0.x/docs/modules/ROOT/pages/whats-new.adoc), [7.1.x libs](https://github.com/spring-projects/spring-security/blob/7.1.x/gradle/libs.versions.toml))

**springfox → springdoc**
- springfox's last release is 3.0.0 (2020-07-14). uPortal's 2.9.2 dates from 2018-06-23. ([Maven Central: springfox-core](https://repo1.maven.org/maven2/io/springfox/springfox-core/))
- springdoc says it "helps automating the generation of API documentation using Spring Boot projects". The current README targets Spring Boot 4.x, and majors are released together with Boot majors. ([springdoc README](https://github.com/springdoc/springdoc-openapi/blob/main/README.md))
- OpenRewrite has `org.openrewrite.java.springdoc.SpringFoxToSpringDoc`, which replaces dependencies and migrates Swagger 2 annotations to OpenAPI 3 (`SwaggerToOpenAPI`). It also converts `Docket` beans to `GroupedOpenApi` and `ApiInfoBuilder` to `Info`, and cleans up remaining springfox. `UpgradeSpringDoc_3_0` also exists. ([rewrite-spring springdoc.yml](https://github.com/openrewrite/rewrite-spring/blob/main/src/main/resources/META-INF/rewrite/springdoc.yml))

**Legacy add-ons**
- json-lib-ext-spring's last release is 1.0.2 (2008-06-11). ([Maven Central: json-lib-ext-spring](https://repo1.maven.org/maven2/net/sf/json-lib/json-lib-ext-spring/))
- Ehcache 2's last releases are `net.sf.ehcache:ehcache` 2.10.9.2 (2021-04-24) and `ehcache-core` 2.6.11 (2015-04-22). Ehcache 3 is at 3.12.0 (2026-04-03). ([Maven Central: net.sf.ehcache](https://repo1.maven.org/maven2/net/sf/ehcache/ehcache/), [ehcache-core](https://repo1.maven.org/maven2/net/sf/ehcache/ehcache-core/), [org.ehcache](https://repo1.maven.org/maven2/org/ehcache/ehcache/))
- Spring 6.0 notes: "`net.sf.ehcache` is using Java EE APIs and is about to be End Of Life'd." ([Spring Framework 6.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes))
- Spring 7.0 adds `JacksonJsonView`/`JacksonJsonHttpMessageConverter` (Jackson 3). `MappingJackson2JsonView` remains but is deprecated with Jackson 2 support. ([spring-framework tag v7.0.0](https://github.com/spring-projects/spring-framework/tree/v7.0.0), [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))

### Inferences
- **Web Flow.** For uPortal's 26 admin flows the servlet-side Web Flow is fine through 4.0.x. The portlet `FlowHandlerAdapter` is the problem and only exists in PortletMVC4Spring's `webflow` module (see Q3). Flow XML definitions (`spring-webflow-2.4.xsd` etc.) should be checked against the 4.0 schema. Web Flow's Unified EL / SpEL binding had 2026 CVEs, so stay on the latest patch.
- **Spring Session.** uPortal is on 1.3.5 + data-redis (local inventory). That makes the Redis path more important than JDBC. Redis key layout and serializers changed across 2.x/3.x/4.x: `RedisOperationsSessionRepository` became `RedisIndexedSessionRepository` in 2.2 (from memory, not verified in this session). Because the Java serialization of `jakarta.*`-typed or changed-class session attributes breaks across the 6.0 hop anyway, plan a session flush (log everyone out) at the 5→6 cutover rather than a live migration. If JDBC were used, the 1.3→2.0 table restructure also forces drop/recreate.
- **spring-modules-cache 0.8.** It is a 2000s project built on Spring 2.x-era APIs. Replace its annotations and interceptors with Spring's `@Cacheable`/`<cache:annotation-driven>` (the cache namespace still exists, per `spring.schemas`) backed by `JCacheCacheManager`. This can and should be done on Spring 4.3/5.3 before the jakarta hop.
- **json-lib-ext-spring.** Its `JsonView` extends 2008-era Spring view classes on `javax.servlet`. Replace it with `MappingJackson2JsonView` on 5.x/6.x and then `JacksonJsonView` on 7.x.
- **springdoc without Boot.** The README positions springdoc as Spring Boot-based, so a non-Boot WAR would probably need spring-boot-autoconfigure on the classpath or manual bean wiring. Alternatives are to generate OpenAPI at build time, or hand-wire swagger-core's `OpenApiResource`. Validate this with a spike.

### Gaps
- I could not confirm the springdoc FAQ answer on plain Spring MVC (non-Boot) support (springdoc.org was blocked). This needs verification.
- I did not enumerate Spring Session 2.x→3.x→4.x Redis key-format and serializer changes (such as JSON serializers on Jackson 3 in 4.x) from primary docs.
- I did not verify Spring LDAP 2.3→2.4 or 3.x API removals beyond the release pages (the 3.0.0/4.0.0 release pages list only dependency upgrades).
- I did not check the Web Flow 3.x/4.x flow-definition XSD changes or removed attributes.

## Q7. Jakarta EE namespace migration tooling (OpenRewrite, Eclipse Transformer, IDE)

### Takeaway
OpenRewrite has a chained, mostly free-to-run recipe ladder: `UpgradeSpringFramework_7_0` → `_6_2` → `_6_1` → `_6_0` → `_5_3` plus `JakartaEE10`/`JakartaEE11`, and Spring Security 5.8/6.0/7.0. It handles Java imports, build dependencies, `web.xml`, `persistence.xml`/`orm.xml` and the `-parameters` flag. The recipe sources are under the Moderne Source Available License, and they do not understand Spring XML bean `class="javax..."` strings, JSPs or TLDs as code. Eclipse Transformer works on binaries and text files (class, properties, XML, TLD, HTML, JSP, JAR/WAR) using rename rules. It is the right tool for third-party javax jars or bulk text rewrites, with a careful exclusion list (`javax.portlet`, `javax.sql`, `javax.naming`, `javax.xml.*` JDK packages).

### Cited Findings
- `UpgradeSpringFramework_6_0` chains `UpgradeSpringFramework_5_3` and `org.openrewrite.java.migrate.jakarta.JakartaEE10`. It also covers:
  - dependency bumps;
  - the Apache HttpClient 5 migration;
  - `MigrateSpringAssert`;
  - `ResponseStatusException`/`HttpStatusCode` changes;
  - `UpgradeSpringData_3_0`;
  - `ListenableToCompletableFuture`.

  Source: [rewrite-spring spring-framework-60.yml](https://github.com/openrewrite/rewrite-spring/blob/main/src/main/resources/META-INF/rewrite/spring-framework-60.yml)
- `UpgradeSpringFramework_6_1` includes `AddParametersCompilerFlagToMaven` and `AddParametersCompilerFlagToGradle`. ([spring-framework-61.yml](https://github.com/openrewrite/rewrite-spring/blob/main/src/main/resources/META-INF/rewrite/spring-framework-61.yml))
- `UpgradeSpringFramework_7_0` chains `_6_2` and upgrades `org.springframework:*` to 7.0.x. It also:
  - renames `HttpStatus.UNPROCESSABLE_ENTITY`→`UNPROCESSABLE_CONTENT` and `PAYLOAD_TOO_LARGE`→`CONTENT_TOO_LARGE`;
  - applies `JUnit5to6Migration`;
  - applies `org.openrewrite.java.jackson.UpgradeJackson_2_3`;
  - applies `UpgradeSpringKafka_4_0`;
  - applies `org.openrewrite.java.jspecify.MigrateFromSpringFrameworkAnnotations`.

  Source: [spring-framework-70.yml](https://github.com/openrewrite/rewrite-spring/blob/main/src/main/resources/META-INF/rewrite/spring-framework-70.yml)
- `UpgradeSpringSecurity_5_8` covers:
  - `HttpSecurityLambdaDsl`;
  - `AuthorizeHttpRequests`;
  - `UseNewRequestMatchers`/`UseNewSecurityMatchers`;
  - Pbkdf2/SCrypt/Argon2 encoder updates;
  - `ReplaceGlobalMethodSecurityWithMethodSecurity`;
  - `ReplaceGlobalMethodSecurityWithMethodSecurityXml`, an XML `ChangeTagName`/`ChangeTagAttribute`.

  `UpgradeSpringSecurity_6_0` adds `UseSha256InRememberMe`, `PropagateAuthenticationServiceExceptions`, `RequireExplicitSavingOfSecurityContextRepository`, `UpdateRequestCache`, `RemoveUseAuthorizationManager` (XML) and `RemoveFilterSecurityInterceptorOncePerRequest`. `UpgradeSpringSecurity_7_0` is thin: a version bump, `SecurityConfigurerRemoveThrowsException` and a method rename. ([spring-security-58.yml](https://github.com/openrewrite/rewrite-spring/blob/main/src/main/resources/META-INF/rewrite/spring-security-58.yml), [spring-security-60.yml](https://github.com/openrewrite/rewrite-spring/blob/main/src/main/resources/META-INF/rewrite/spring-security-60.yml), [spring-security-70.yml](https://github.com/openrewrite/rewrite-spring/blob/main/src/main/resources/META-INF/rewrite/spring-security-70.yml))
- The rewrite-spring YAML header reads "Licensed under the Moderne Source Available License", and the build applies the `org.openrewrite.build.moderne-source-available-license` plugin. rewrite-migrate-java's jakarta YAML has the same license header. ([spring-framework-70.yml header](https://github.com/openrewrite/rewrite-spring/blob/main/src/main/resources/META-INF/rewrite/spring-framework-70.yml), [rewrite-migrate-java jakarta-ee-9.yml](https://github.com/openrewrite/rewrite-migrate-java/blob/main/src/main/resources/META-INF/rewrite/jakarta-ee-9.yml))
- `JavaxMigrationToJakarta` chains per-spec recipes (Servlet, JSP, EL, annotation, inject, persistence, mail, JMS, XML bind/WS/SOAP, ...) plus `FileuploadToFileUpload2`. `JavaxJspToJakartaJsp` changes the `jsp-api` dependency and `ChangePackage javax.servlet.jsp → jakarta.servlet.jsp` (Java sources). Dedicated XML recipes exist for `persistence.xml` (`JavaxPersistenceXmlToJakartaPersistenceXml`), `orm.xml`, `beans.xml`, `ejb-jar.xml`, `validation.xml` and `web.xml`. ([jakarta-ee-9.yml](https://github.com/openrewrite/rewrite-migrate-java/blob/main/src/main/resources/META-INF/rewrite/jakarta-ee-9.yml), [jakarta-ee-10.yml](https://github.com/openrewrite/rewrite-migrate-java/blob/main/src/main/resources/META-INF/rewrite/jakarta-ee-10.yml))
- `JavaxWebXmlToJakartaWebXml` (filePattern `**/web.xml`) sets `web-app version` to 5.0, sets the jakarta xmlns and schemaLocation, and regex-replaces every tag value `javax.` → `jakarta.`, then reverts `jakarta.sql.` → `javax.sql.`. ([jakarta-faces-3.yml](https://github.com/openrewrite/rewrite-migrate-java/blob/main/src/main/resources/META-INF/rewrite/jakarta-faces-3.yml))
- `JakartaEE11` = `JakartaEE10` + Faces 4.1 + `UpdateJakartaPlatform11` + `MigratePluginsForJakarta11`. ([jakarta-ee-11.yml](https://github.com/openrewrite/rewrite-migrate-java/blob/main/src/main/resources/META-INF/rewrite/jakarta-ee-11.yml))
- Eclipse Transformer <!-- vale ai-tells.VerbTricolon = NO --><!-- direct quote of the Eclipse Transformer README -->"transform\[s\] Java binaries, such as individual class files and complete JARs and WARs, mapping changes to Java packages, type names, and related resource names"<!-- vale ai-tells.VerbTricolon = YES -->. It handles "Java class files", "Properties files", "Text files (of several types: java source, XML, TLD, HTML, and JSP)" and "Java archives (JAR, WAR, RAR, and EAR files)", recursing into nested archives. Rules files are `jakarta-renames.properties`, `jakarta-selection.properties`, `jakarta-text-master.properties` and others, and it includes a `JakartaTransformerCLI` with built-in rules. ([eclipse-transformer README](https://github.com/eclipse-transformer/transformer/blob/main/README.md))

### Inferences
- **Recommended tool split for uPortal.** These are inferences rather than documented guidance:
  - OpenRewrite ladder for Java sources and Gradle dependencies, run one hop at a time (5.3 → 6.0 → 6.1 → 6.2 → 7.0), plus `-parameters`.
  - Text-level rewrite, either sed-like or Eclipse Transformer text rules, for the 113 Spring XML files (`class="javax..."`), 111 JSPs (taglib URIs `java.sun.com/jsp/jstl/*` → `jakarta.tags.*` (JSTL 3.0 URI form from memory, unverified)) and 2 TLDs.
  - Eclipse Transformer for any third-party javax-only jars that must run on Tomcat 10.1/11 (Tomcat also provides a migration tool. This is from memory, not verified here and outside this note's scope.).
- **Blanket-replace hazards.** The web.xml recipe's blanket `javax.`→`jakarta.` regex (reverting only `javax.sql`) would wrongly rewrite `javax.portlet.*` and JDK packages (`javax.naming`, `javax.xml.transform`, `javax.crypto`) if they appear in web.xml or other XML. uPortal needs an explicit exclusion list. The local inventory notes 120 files using JAXP `javax.xml.*` that must remain javax.
- **Licensing.** The Moderne Source Available License permits running the recipes on one's own code but restricts offering them as a service. This is my reading and should be confirmed on the license page. Apereo should confirm it is acceptable for CI use.
- **OpenRewrite parsing.** OpenRewrite parses Java, XML, properties and YAML, but not JSP as code.

### Gaps
- I could not read docs.openrewrite.org (blocked) to confirm recipe-catalog status, or whether any OpenRewrite recipe handles Spring XML `class=` attributes or JSP taglib URIs. I found none in the YAML I inspected.
- I did not research IntelliJ IDEA's "Migrate Packages and Classes → Java EE to Jakarta EE" capabilities or limits.

## Q8. Jackson 3 (`tools.jackson`) and Spring

### Takeaway
Spring Framework 7.0 (Nov 2025) defaults to Jackson 3 across the stack and falls back to Jackson 2. Jackson 2 is deprecated in 7.0, its auto-detection is planned to be disabled in 7.1 (Nov 2026, which also requires Jackson 3.1), and support is planned for removal in 7.2. So uPortal can run Jackson 2.22 on Spring 7.0 temporarily, but the 7.x end state is Jackson 3. Only `jackson-annotations` stays in `com.fasterxml.jackson`.

### Cited Findings
- "As of #33798, we default to supporting Jackson 3.x in our entire stack, falling back to Jackson 2.x. Support for the Jackson 2.x generation has been deprecated ... our current plan is to disable its auto-detection in 7.1 and remove its support entirely in 7.2." Also: "Jackson 3.x uses a new `tools.jackson` package ... Classes from the 'jackson-annotation' artifact (such as `@JsonView` and `@JsonTypeInfo`) remain in the `com.fasterxml.jackson` package," and "There is no Jackson 3.x equivalent for `Jackson2ObjectMapperBuilder`. We now recommend using Jackson's `JsonMapper.builder()`." ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- 7.1 baseline: "Jackson 3.1 for LTS support". ([Spring Framework 7.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.1-Release-Notes))
- The Spring blog adds:
  - `MappingJackson2HttpMessageConverter` is "now deprecated", and the replacement is `JacksonJsonHttpMessageConverter`.
  - Jackson 3 changes defaults: `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` is now true, and `WRITE_DATES_AS_TIMESTAMPS` is false (ISO-8601 dates).
  - Spring Security 7.0 Jackson 3 support uses a `PolymorphicTypeValidator` that allows only Spring Security types by default.
  - Boot users can use `spring-boot-jackson2` temporarily.

  Source: the spring.io post [Introducing Jackson 3 support in Spring (spring.io, 2025-10-07)](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring)
- 7.0 introduces `HttpMessageConverters` and `WebMvcConfigurer#configureMessageConverters(HttpMessageConverters.ServerBuilder)`, with `JacksonJsonHttpMessageConverter(jsonMapper)` as the example. ([Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes))
- Spring Security 7: replace `SecurityJackson2Modules` with `SecurityJacksonModules`. The formats are compatible ("class instances serialized with Jackson 2 should be deserializable with the Jackson 3 support"). ([Migrating to 7.0 (7.0.x)](https://github.com/spring-projects/spring-security/blob/7.0.x/docs/modules/ROOT/pages/migration/index.adoc))
- The OpenRewrite `UpgradeSpringFramework_7_0` recipe includes `org.openrewrite.java.jackson.UpgradeJackson_2_3`. ([spring-framework-70.yml](https://github.com/openrewrite/rewrite-spring/blob/main/src/main/resources/META-INF/rewrite/spring-framework-70.yml))
- Current Maven Central versions are Jackson 2.22.3 (the 2.x line is still released) and Jackson 3.2.3 / 3.1.7 (2026-09-22). ([Maven Central jackson-databind 2.x](https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/), [tools.jackson databind](https://repo1.maven.org/maven2/tools/jackson/core/jackson-databind/))

### Inferences
- Spring 6.2 accepts uPortal's Jackson 2.22 (runtime compatibility with 2.15+). The Jackson 3 move can be postponed to after the jakarta hop but must happen within 7.x.
- Changed Jackson 3 defaults (alphabetical property order, ISO dates) can silently change uPortal REST/JSON payloads consumed by front-end web components and portlets. Contract-test JSON outputs, or explicitly reconfigure the `JsonMapper` with `use-jackson2-defaults`-style settings.
- Any persisted Jackson-serialized data must be checked for format compatibility. This includes Spring Session JSON serializers and Spring Security objects in sessions.

### Gaps
- I did not verify the Jackson 2.x maintenance/EOL horizon on the FasterXML side.

## Q9. Migration reports from large production Spring apps (Apereo CAS and others)

### Takeaway
Apereo CAS is the closest public analogue: same community, a heavy Spring and Spring Web Flow user. It went javax → Jakarta in CAS 7.0 (Spring Boot 3 / Jakarta EE 10 / Servlet 6.0, Tomcat 10.1 minimum, JDK 21 baseline) and to Spring Boot 4 / Spring 7 / Jackson 3 / JDK 25 in CAS 8.0. Its notes stress that the jakarta hop "affects almost every CAS module and dependency" and that third-party libraries lacking Jakarta releases were the long pole.

### Cited Findings
- CAS 7.0 RC3: "The migration of the entire codebase to Spring Boot `3.0.0` and Jakarta APIs is now complete. This is a major change and upgrade across the board that affects almost every CAS module and dependency." CAS now supports "Jakarta EE 10 and Servlet specification `6.0.0`... minimum supported version is now required to be `10.1.x`" for Tomcat. The same notes say:
  - <!-- vale ai-tells.FigurativeQuantities = NO --><!-- direct quote of the CAS release notes -->"A handful of dependencies and libraries (i.e. OpenSAML, Pac4j, Spring Retry,) have yet to provide a final release version compatible with Spring Boot 3 and/or Jakarta APIs."<!-- vale ai-tells.FigurativeQuantities = YES -->
  - <!-- vale ai-tells.NegatedObject = NO --><!-- direct quote of the CAS release notes -->"Apache BVal has been replaced with Hibernate Validator ... The former provides no support for Jakarta APIs."<!-- vale ai-tells.NegatedObject = YES -->
  - Sleuth was removed, SCIM2 was replaced, and Jetty lacked Servlet 6.0 at the time.

  Source: [CAS 7.0 RC3 release notes](https://github.com/apereo/cas/blob/7.0.x/docs/cas-server-documentation/release_notes/RC3.md)
- CAS 7.0 RC8: <!-- vale uPortal.Jargon = NO --><!-- direct quote of the CAS 7.0 RC8 release notes -->"Spring Framework `6.1` provides a first-class experience on JDK `21` and Jakarta EE 10 ... it is likely that CAS `7` would switch to using JDK `21` as its baseline."<!-- vale uPortal.Jargon = YES --> ([CAS 7.0 RC8 release notes](https://github.com/apereo/cas/blob/7.0.x/docs/cas-server-documentation/release_notes/RC8.md))
- CAS 8.0 RC1:
  - "CAS is now built with Spring Boot `4.0.x`."
  - "The JDK baseline requirement for this CAS release is and **MUST be JDK `25`**."
  - `spring-retry` was replaced by Spring Framework's native retry, with semantic change: retries no longer count the initial attempt.
  - Undertow was dropped "until Undertow adds support for Servlet 6.1".
  - Spring Cloud GCP and Spring Boot Admin are not yet compatible.
  - "Jackson ... is upgraded to its `3.x` version ... Almost all such changes are internal and SHOULD NOT affect how CAS configuration, application records, etc are processed."

  Source: [CAS 8.0 RC1 release notes](https://github.com/apereo/cas/blob/8.0.x/docs/cas-server-documentation/release_notes/RC1.md)
- Maven Central: `org.apereo.cas:cas-server-core-api` 8.0.2 is latest GA, with 8.1.0-RC1/RC2 published. ([Maven Central: cas-server-core-api](https://repo1.maven.org/maven2/org/apereo/cas/cas-server-core-api/))
- Liferay's PortletMVC4Spring 6.0.0-M1 commit trail (see Q3) is a real-world report of porting Spring Portlet MVC to Jakarta. It needed:
  - a non-official Jakarta Portlet 4.0 GAV;
  - a forked Pluto ("plutonium");
  - JSTL taglib reference removal;
  - `org.glassfish.expressly` to fix `ClassNotFoundException: com.sun.el.ExpressionFactoryImpl` in the webflow demo;
  - Thymeleaf compatibility fixes for Spring 6.

  Source: [liferay/portletmvc4spring commits](https://github.com/liferay/portletmvc4spring/commits/master)

### Inferences
- **What CAS's experience implies for uPortal.**
  - Expect the long pole to be the third-party and portlet ecosystem rather than Spring itself. For uPortal this means Pluto/portlet API, PortletMVC4Spring, cas-client, person-directory and the 14 portlet overlays.
  - Expect the Spring 7 hop to be smaller than the jakarta hop, but to bring Jackson 3 and Servlet 6.1 (Tomcat 11) changes.
  - CAS used Spring Boot, so its notes do not cover XML-namespace pitfalls.
- **uPortal-start's bundled CAS overlay (CAS 3.6.0 on Spring 3.2.18)** is a separate application. Moving it to a current CAS (7.x/8.x) is a Spring Boot 3/4 + JDK 21/25 decision on its own track.

### Gaps
- I didn't find a public migration write-up from a large XML-namespace-configured, non-Boot Spring 4.x WAR (such as another portal or Sakai-like LMS) moving to Spring 6/7. I recommend a targeted search of the Sakai, Kuali and Jasig/uPortal mailing lists.
