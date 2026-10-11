# Running JSR-286/JSR-362 portlets and an Apache Pluto-based portlet container on a Jakarta EE (jakarta.servlet) stack: feasibility for uPortal (state as of 2026-10-10)

Research notes. Research method: web search plus direct reads of GitHub repos and source files and of Maven Central directory listings. Many primary sites (projects.eclipse.org, jakarta.ee, learn.liferay.com, liferay.dev, tomcat.apache.org, portals.apache.org, attic.apache.org) could not be fetched directly from the research environment because of DNS failures. Claims about those sites come from search-result snippets and are marked "(via search snippet)". Treat those as lower confidence and re-verify before relying on them.

## Q1. Apache Pluto: latest release, activity, Attic status, and any jakarta.servlet port or fork

### Takeaway
Apache Pluto is dead upstream. Apache Portals (Pluto and Jetspeed) retired to the Apache Attic in late 2025, and the last release was Pluto 3.1.2 in December 2023, which is still javax. The working Jakarta port is **Liferay "Plutonium" 4.0**. It is an Apache-2.0 fork of Pluto 3, renamed to `com.liferay.plutonium`, and is the <!-- vale ai-tells.FormalRegister = NO --><!-- Jakarta EE designation -->Compatible Implementation<!-- vale ai-tells.FormalRegister = YES --> of Jakarta Portlet 4.0. It is milestone-quality (4.0.0-M1) and is not published to Maven Central. Sakai took a different route: it vendored and jakarta-ized the much older Pluto 1.1.7 (Portlet 1.0) in its own tree.

### Cited Findings
**Upstream Apache Pluto status**
- Maven Central `org.apache.portals.pluto:pluto-container` versions are 2.0.0 to 2.0.3, 2.1.0-M1/M2/M3, 3.0.0, 3.0.1, 3.1.0, 3.1.1 and 3.1.2. `latest`/`release` = 3.1.2 and the metadata's `lastUpdated` is 2023-12-21. uPortal's 2.1.0-M3 is a 2.x milestone that never went GA. See [Maven Central metadata](https://repo1.maven.org/maven2/org/apache/portals/pluto/pluto-container/maven-metadata.xml).
- The Pluto status page reads "This project has retired. For details please refer to its Attic page" and gives 3.1.2 as the current stable release (via search snippet). See [Pluto status](https://portals.apache.org/pluto/status.html).
- The Apache Attic page says Apache Portals was a TLP from February 2004, **retired in September 2025**, and the move to the Attic **completed in November 2025**. A staged copy of the page still says "In Progress" (via search snippet). See <!-- vale ai-tells.ColonUsage = NO --><!-- citation link text kept verbatim -->[Attic: Apache Portals](https://attic.apache.org/projects/portals.html)<!-- vale ai-tells.ColonUsage = YES --> and [staged copy](https://attic-retire-portals.staged.apache.org/projects/portals.html).
- The GitHub mirror `apache/portals-pluto` was **archived (read-only) on Oct 29, 2025**. It has 2,991 commits on master. Its README calls Pluto 3.0 <!-- vale ai-tells.FormalRegister = NO --><!-- direct quote from the Pluto README -->"the third version of the JSR-362 Reference Implementation"<!-- vale ai-tells.FormalRegister = YES --> (includes the TCK). There is no mention of Jakarta, and its dependencies are still javax (e.g., `javax.ws.rs` 2.1.1). See [apache/portals-pluto](https://github.com/apache/portals-pluto).
- Jetspeed-2 API docs show the same "This project has retired" notice. See [Jetspeed-2 apidocs](https://portals.apache.org/jetspeed-2/apidocs/org/apache/jetspeed/om/portlet/DisplayName.html).
- Pluto's architecture: it is "a thin layer on top of the servlet container". The Pluto assembler adds `org.apache.pluto.container.driver.PortletServlet` to each portlet WAR's web.xml, and that servlet receives the portal's dispatches. The design depends on cross-context dispatch inside one servlet container (via search snippet). See [Pluto architecture](https://portals.apache.org/pluto/architecture.html) and [Deploying to Pluto 2.0](https://portals.apache.org/pluto/v20/deploying.html).

**Liferay Plutonium: a Jakarta port of Pluto 3**
- The `liferay/plutonium` README says it <!-- vale ai-tells.FormalRegister = NO --><!-- direct quote from the Plutonium README -->"contains the source code for the Liferay Plutonium 4.0 project, the Compatible Implementation (CI) of Jakarta Portlet 4.0"<!-- vale ai-tells.FormalRegister = YES -->. License is Apache-2.0. It has 3,105 commits and no GitHub releases. See [liferay/plutonium](https://github.com/liferay/plutonium).
- The Plutonium root pom (`com.liferay.plutonium:plutonium-parent:4.0.0-SNAPSHOT`, `SPDX-License-Identifier: Apache-2.0`) declares:
  - modules: plutonium-container-api, plutonium-container, plutonium-taglib, plutonium-container-driver-api, plutonium-portal-driver(-impl), plutonium-portal, plutonium-testsuite, portlet-servlet-adapter, templating, maven-plutonium-plugin, archetypes
  - dependencies: `jakarta.servlet-api` 6.0.0, `jakarta.portlet:jakarta.portlet-api:4.0.0-SNAPSHOT`, CDI API 4.0.1, Weld `weld-servlet-core` 5.1.5.Final, Spring 6.2.2, Tomcat servlet/jsp API 10.1.36
  - Java 11 target
  - no distributionManagement

  See [plutonium pom.xml](https://raw.githubusercontent.com/liferay/plutonium/master/pom.xml).
- Plutonium commit history: by ngriffin7a (Neil Griffin, Liferay), Sep 9 to 11, 2025. It includes commits titled "PLUTO-808 Migrate to Jakarta EE", "Remove dependency on ASF parent POM" and a series of "Rename Pluto > Plutonium" commits. The package moved off `org.apache.pluto`. Those are the latest commits on the first page. See [plutonium commits](https://github.com/liferay/plutonium/commits/master).
- PortletMVC4Spring commits refer to the "Liferay version of Jakarta Pluto" and "Liferay version of pluto, renamed to plutonium, temporarily depend on 4.0.0-M1". The pom property is `plutonium.version` = 4.0.0-M1. A further commit is "Specify the liferay-third-party repository ahead of 6.0.0-M1 release, since not releasing to Central". This implies Plutonium 4.0.0-M1 lives in Liferay's third-party Nexus, not Maven Central. See [portletmvc4spring commits](https://github.com/liferay/portletmvc4spring/commits/master) and [code search hit, pom.xml](https://github.com/liferay/portletmvc4spring/blob/master/pom.xml).
- The `liferay/portals-pluto` fork has branches `tck3-jakarta-chrome` (May 17, 2025) and `tck3-5-16` (May 16, 2025), which show that Liferay's Jakarta TCK work happened on Pluto first. See [liferay/portals-pluto branches](https://github.com/liferay/portals-pluto/branches/all).
- The Eclipse "Jakarta Portlet" proposal says migrating the Portlet API and TCK is "already complete" and <!-- vale ai-tells.FormalRegister = NO --><!-- direct quote from the Eclipse proposal -->"the work of migrating Apache Pluto (the compatible implementation) to these new APIs is also complete"<!-- vale ai-tells.FormalRegister = YES --> (via search snippet, proposal ~513 days old). See [Jakarta Portlet proposal](https://projects.eclipse.org/proposals/jakarta-portlet) and [creation review](https://projects.eclipse.org/projects/ee4j.portlet/reviews/creation-review).

**Other forks and precedents**
- Sakai (Apereo higher-ed LMS) PR #15141, "SAK-52757 Jakarta consolidate migration for master", was merged Oct 9, 2026. It moves Sakai to Tomcat 10.1 (10.1.60), Java 21, Spring 6 and Hibernate 6. It includes the commit <!-- vale ai-tells.ColonUsage = NO --><!-- upstream commit title -->"SAK-52804 Portal: Import Pluto 1.1.7 into the Sakai Source Tree"<!-- vale ai-tells.ColonUsage = YES --> and later "Revert pluto package rename back to org.apache.pluto", plus "align portlet-api to 1.0". See [sakaiproject/sakai PR #15141](https://github.com/sakaiproject/sakai/pull/15141).
- Sakai's vendored `libs/pluto/pluto-container/.../core/PortletServlet.java` now imports `javax.portlet.Portlet`/`PortletException` **together with** `jakarta.servlet.ServletException`/`jakarta.servlet.http.HttpServlet(Request|Response)`. That is a hybrid: the Portlet API keeps the javax namespace while the servlet layer is Jakarta. GitHub code search finds 39 files in the Sakai repo that contain both `import javax.portlet` and `jakarta.servlet`, including Sakai's own portlets (`lti/lti-portlet/.../IMSLTIPortlet.java`, `lti/web-ifp/.../SakaiIFrame.java`). See [Sakai PortletServlet.java](https://raw.githubusercontent.com/sakaiproject/sakai/master/libs/pluto/pluto-container/src/main/java/org/apache/pluto/core/PortletServlet.java).
- GitHub forks named `sustain/portals-pluto-jakarta` (and `mcastro-sustain/portals-pluto-jakarta`), both "Mirror of Apache Pluto - Jakarta", were created Dec 1, 2025. `sustain` has 3 open PRs and its README is unchanged (still Pluto 3.0/javax). A `sakaicontrib/portals-pluto` fork was created Aug 5, 2026, with no visible Jakarta changes on its landing page. See [sustain/portals-pluto-jakarta](https://github.com/sustain/portals-pluto-jakarta) and [sakaicontrib/portals-pluto](https://github.com/sakaicontrib/portals-pluto).

### Inferences
- uPortal cannot get a Jakarta Pluto from the ASF. There are three realistic bases:
  1. **Liferay Plutonium 4** (Pluto 3 lineage). It provides Portlet 3.0 semantics and the `jakarta.portlet` namespace, and it is Apache-2.0, so it is compatible with uPortal's license. It is a milestone from one developer, with artifacts hosted outside Maven Central.
  2. **A self-made jakarta-ization of Pluto 2.1.0-M3**, uPortal's current base. This is the Sakai-style approach: keep `javax.portlet` and switch the servlet layer to Jakarta.
  3. **Self-port of upstream Pluto 3.1.2.** This would largely duplicate Plutonium.
- Moving uPortal from Pluto 2.1 to Plutonium 4 is not a drop-in change. Pluto 3 brought Portlet 3.0 (render state, bean portlets via CDI/Weld, new SPI pieces). uPortal's Pluto SPI implementations (portal driver services, URL/state handling, request/response contexts) would need reworking against the Pluto 3 container SPI. This is inferred from the Plutonium module list and its CDI/Weld dependencies. The size of the SPI delta was not verified.
- Sakai's choice is informative. It vendored the tiny Portlet 1.0-era Pluto 1.1.7 rather than adopting Plutonium. A higher-ed project with few portlets found maintaining a small container of its own cheaper than tracking Liferay's milestone fork.

### Gaps
- Whether Plutonium 4.0.0-M1 (or a GA) is downloadable from `repository.liferay.com` could not be verified: the proxy blocked access. Its exact contents and TCK pass status are also unknown.
- Whether Plutonium runs on Tomcat 11 / Servlet 6.1 is not stated. It was built against Tomcat 10.1.36 APIs.
- The content of the 3 open PRs on `sustain/portals-pluto-jakarta` was not inspected.
- The size of the Pluto 2.1 → 3.x container SPI change for a portal like uPortal was not measured.

## Q2. Portlet specification status: Jakarta Portlet 4.0 / jakarta.portlet API, Liferay's Jakarta move, and reusability or licensing

### Takeaway
There is a **Jakarta Portlet 4.0** spec project at the Eclipse Foundation (`ee4j.portlet`). It is a pure namespace migration of Portlet 3.0 (`javax.portlet` → `jakarta.portlet`, aligned to Jakarta EE 10 / Servlet 6.0, Java SE 11+). As of Oct 2026 it still shows as "under development". Its GitHub repo has no tags, and Maven Central has no `jakarta.portlet` artifacts. Liferay released it anyway under a non-official GAV, `com.liferay.jakarta.portlet:com.liferay.jakarta.portlet-api:4.0.0`. Liferay DXP moved to Jakarta in **2025.Q3** and requires all portlets to become Portlet 4.0. Javax portlets do not run there. The reusable pieces (the API source, Plutonium and PortletMVC4Spring) are Apache-2.0, not LGPL.

### Cited Findings
**Spec project and API**
- Jakarta Portlet scope: migrate JSR 362 (Portlet 3.0) to Jakarta EE. Package namespaces change from `javax.portlet` to `jakarta.portlet`, the XML namespace moves to jakarta.ee, and dependencies align with Jakarta EE 10. "Initial scope is strictly limited" to namespace/dependency migration. The initial contribution is the spec document, Java API, JavaScript API and TCK (via search snippets). See [Jakarta Portlet proposal](https://projects.eclipse.org/proposals/jakarta-portlet) and [creation review](https://projects.eclipse.org/projects/ee4j.portlet/reviews/creation-review).
- jakarta.ee spec page: "Jakarta Portlet 4.0 (Under Development)". It notes the rename "will require source code changes and recompilation", that 4.0 is not binary compatible with earlier versions, and a minimum of Java SE 11 (via search snippets, possibly stale). See [Jakarta Portlet 4.0](https://jakarta.ee/specifications/portlet/4.0/).
- The GitHub repo `jakartaee/portlet` contains `api`, `spec` and `tck`. It has 68 commits and **no tags or releases**, and requires JDK 17+ and Maven 3.9 to build. See [jakartaee/portlet](https://github.com/jakartaee/portlet).
- API pom details:
  - coordinates are `jakarta.portlet:jakarta.portlet-api:4.0.0-SNAPSHOT`, packaging bundle, parent `org.eclipse.ee4j:project:1.0.9`
  - provided dependencies on `jakarta.jakartaee-api` 10.0.0 and CDI API 4.0.1
  - Apache License 2.0 header (ASF), inherited from the ASF portlet-api code

  See [api/pom.xml](https://raw.githubusercontent.com/jakartaee/portlet/main/api/pom.xml) and [PortletRequestDispatcher.java (Apache-2.0 header)](https://raw.githubusercontent.com/jakartaee/portlet/master/api/src/main/java/jakarta/portlet/PortletRequestDispatcher.java).
- `https://repo1.maven.org/maven2/jakarta/portlet/` returned **HTTP 404**, so no official `jakarta.portlet` group was on Maven Central when checked (2026-10-10). `.../com/liferay/jakarta/portlet/com.liferay.jakarta.portlet-api/` also returned 404. See [Maven Central jakarta/portlet](https://repo1.maven.org/maven2/jakarta/portlet/).

**Liferay's adoption and artifacts**
- liferay-portal build files use `compileOnly group: "com.liferay.jakarta.portlet", name: "com.liferay.jakarta.portlet-api", version: "4.0.0"` alongside `jakarta.servlet-api` 6.0.0. GitHub code search finds 894 hits, e.g. `modules/apps/rss/rss-api/build.gradle`. See [liferay-portal rss-api build.gradle](https://github.com/liferay/liferay-portal/blob/master/modules/apps/rss/rss-api/build.gradle).
- PortletMVC4Spring commit: "temporarily rely on the Non-Official/Liferay GAV for the Portlet API". See [portletmvc4spring commits](https://github.com/liferay/portletmvc4spring/commits/master).

**Liferay's Jakarta transition (DXP/Portal)**
- Liferay DXP 2025.Q3 breaking changes:
  - "Java EE libraries (javax.*) are no longer compatible and must be replaced with updated Jakarta EE (jakarta.*) versions"
  - Tomcat 9.0.x is no longer supported. Tomcat 10.1.x, JBoss EAP 8.0 or WildFly 30 are recommended

  See [2025.Q3 Breaking Changes](https://learn.liferay.com/w/dxp/self-hosted-installation-and-upgrades/upgrading-liferay/deprecations-and-breaking-changes-reference/2025-deprecations-and-breaking-changes/2025-q3-breaking-changes) (via search snippet).
- Liferay Jakarta 2025 FAQ (via search snippets, see [Jakarta 2025 FAQ](https://learn.liferay.com/w/reference/jakarta-2025-faq)):
  - `blade gw upgradeJakarta` "Replaces javax with jakarta in custom code, including javax.faces.*, javax.portlet.*, javax.inject.*, and javax.enterprise.* ... Converts portlets to Portlet 4.0". The FAQ says this is not a simple find-and-replace.
  - Portlet 4.0 "has the same behavior as Portlet 3.0, but with the Jakarta packages and alignment with Servlet 6.0".
  - PortletMVC4Spring is planned "as part of the 2025.Q3 release".
  - For JSF, Liferay reversed an earlier deprecation ("we are no longer deprecating JSF support").
- One aggregator summary says Liferay Portal CE followed with the 2026 Q1 releases. (secondary, unverified). See <!-- vale ai-tells.ColonUsage = NO --><!-- citation link text kept verbatim -->[Liferay Learn: Upgrading to Jakarta](https://learn.liferay.com/w/dxp/development/tooling/liferay-workspace/upgrading-to-jakarta)<!-- vale ai-tells.ColonUsage = YES -->.
- The Liferay migration docs show where `javax.portlet` must change: imports, portlet action names, `web.xml` schema declarations (Servlet 6.0) and `Language.properties` portlet properties (via search snippet). See [Migrating to PortletMVC4Spring and Jakarta](https://learn.liferay.com/w/dxp/development/traditional-java-based-development/developing-a-web-application/using-spring/migrating-to-portletmvc4spring).
- Liferay also proposed a <!-- vale uPortal.Jargon = NO --><!-- Eclipse project and JSR names -->"Jakarta Portlet Bridge" (Faces Bridge, JSR 378)<!-- vale uPortal.Jargon = YES --> project at Eclipse. It was at proposal stage when last seen (via search snippet). See <!-- vale uPortal.Jargon = NO --><!-- Eclipse project name in link text -->[Jakarta Portlet Bridge proposal](https://projects.eclipse.org/proposals/jakarta-portlet-bridge)<!-- vale uPortal.Jargon = YES -->.
- Liferay's own portal does not run on Pluto. PortletMVC4Spring's container-detection enum distinguishes `LIFERAY("com.liferay")`, `PLUTO("com.liferay.plutonium")` and `WEBSPHERE("com.ibm")`. See [PortletContainer.java](https://github.com/liferay/portletmvc4spring/blob/master/framework/src/main/java/com/liferay/portletmvc4spring/util/PortletContainer.java).

### Inferences
- Licensing is not a blocker. Every component uPortal would reuse is Apache-2.0: the `jakartaee/portlet` API source, Liferay Plutonium and Liferay PortletMVC4Spring. Liferay Portal CE's LGPL applies to the portal product, not to these separate repos. The license of the binary `com.liferay.jakarta.portlet-api:4.0.0` jar was not verified, but uPortal could build `jakarta.portlet-api` itself from `jakartaee/portlet`.
- The supply-chain risk is real:
  - no official spec release
  - no Maven Central artifacts for the API, Plutonium or PortletMVC4Spring 6
  - all three are maintained essentially by one Liferay engineer, Neil Griffin
  uPortal would likely need to republish the API, Plutonium and PortletMVC4Spring under Apereo coordinates. The alternative is to depend on Liferay's Nexus.
- Liferay's Portlet 4.0 transition shows the only "blessed" path. It is **recompiling portlets against jakarta.portlet**, not running javax portlets on Jakarta. Major vendors don't offer a javax-portlet-on-Jakarta compatibility layer.

### Gaps
- The exact Liferay Portal CE GA version that went Jakarta, and its Tomcat 11 support, were not verified (learn.liferay.com was not fetchable).
- The Jakarta Portlet 4.0 ballot or release timeline is unknown. The status page may be stale.
- The license metadata of `com.liferay.jakarta.portlet-api:4.0.0` is unknown.

## Q3. Deploy-time conversion (Tomcat migration tool, `legacyAppBase`/`webapps-javaee`, Eclipse Transformer): can it convert javax portlet WARs, and what happens to javax.portlet?

### Takeaway
The tools convert `javax.servlet` → `jakarta.servlet` in a portlet WAR's classes, JSPs, TLDs and nested JARs. **None of them touch `javax.portlet`**, because it is not a Java EE package. So a transformed portlet WAR ends up referencing `javax.portlet.*` types whose signatures now mix with `jakarta.servlet` types. That only links if the container provides a **hybrid `javax.portlet` API jar compiled against jakarta.servlet**, together with a jakarta-ized Pluto 2.1 built for it. Sakai shows the hybrid works for Portlet 1.0 when compiled from source. Bytecode-transforming whole Spring-4 portlet WARs adds a second risk: libraries calling Servlet APIs removed in 6.0.

### Cited Findings
**What the tools convert**
- Tomcat Host `legacyAppBase`: "Java EE applications packaged as WAR files or directories placed in this directory will be converted to Jakarta EE using the Apache Tomcat Migration Tool for Jakarta EE. The conversion will be performed using default settings. The resulting WAR or directory will be placed in the appBase ... If the default settings are not appropriate ... performing the migration manually. The migrate.\[sh|bat] scripts are provided in the $CATALINA_HOME/bin directory". The default directory is `webapps-javaee`. This is from the Tomcat main-branch docs. See [Tomcat host.xml docs source](https://raw.githubusercontent.com/apache/tomcat/main/webapps/docs/config/host.xml).
- Tomcat's changelog fixes a bug where a failed legacyAppBase migration lost the previous app version, which shows the feature is maintained. See [Tomcat changelog.xml](https://github.com/apache/tomcat/blob/main/webapps/docs/changelog.xml).
- Migration tool README:
  - converts "package references in classes, String constants, configuration files, JSPs, TLDs etc."
  - "Only those defined by Java EE have moved to the jakarta.* namespace"
  - "will remove cryptographic signatures from JAR files"
  - users should check third-party licenses before distributing converted output

  See [apache/tomcat-jakartaee-migration README](https://raw.githubusercontent.com/apache/tomcat-jakartaee-migration/main/README.md).
- Migration tool profiles (`EESpecProfiles.java`):
  - `SERVLET` (javax.servlet only)
  - `TOMCAT` (annotation subset, ejb, el, mail, persistence, security.auth.message, servlet, transaction, websocket)
  - `EE` (all Java EE 8 packages incl. faces, ws.rs, xml.bind etc.)
  - `JEE8` (reverse)
  **No profile includes `javax.portlet`.** See [EESpecProfiles.java](https://raw.githubusercontent.com/apache/tomcat-jakartaee-migration/main/src/main/java/org/apache/tomcat/jakartaee/EESpecProfiles.java).
- Eclipse Transformer's default `jakarta-renames.properties` covers activation, annotation, batch, decorator, ejb, el, enterprise, faces, inject, interceptor, jms, json, jws, mail, persistence, resource, security, servlet, transaction, validation, websocket, ws.rs and xml.bind/soap/ws. It has **no `javax.portlet` entry**. See [jakarta-renames.properties](https://raw.githubusercontent.com/eclipse-transformer/transformer/main/org.eclipse.transformer.jakarta/src/main/resources/org/eclipse/transformer/jakarta/jakarta-renames.properties).

**Where the Portlet API references servlet types**
- Portlet 2.0+ `javax.portlet.PortletRequest` declares `public javax.servlet.http.Cookie[] getCookies();` (`@since 2.0`, `@see PortletResponse#addProperty(Cookie)`). So the JSR-286 API itself has servlet types in its signatures. See [Pluto portlet-api PortletRequest.java](https://raw.githubusercontent.com/apache/portals-pluto/master/portlet-api/src/main/java/javax/portlet/PortletRequest.java).
- The Jakarta Portlet 4.0 API imports these servlet types:
  - `jakarta.servlet.http.Part` (`ClientDataRequest`, `ClientDataRequestWrapper`)
  - `jakarta.servlet.DispatcherType` (`ResourceRequest`, `ResourceRequestWrapper`)
  - `jakarta.servlet.http.Cookie` (`PortletRequestWrapper`, `PortletResponseWrapper`)

  `PortletRequestDispatcher` itself has no servlet types in its signatures. See [code search in jakartaee/portlet](https://github.com/jakartaee/portlet/blob/main/api/src/main/java/jakarta/portlet/ClientDataRequest.java) and [PortletRequestDispatcher.java](https://raw.githubusercontent.com/jakartaee/portlet/master/api/src/main/java/jakarta/portlet/PortletRequestDispatcher.java).

**Precedent and residual risks**
- Sakai is a precedent for the hybrid. Its vendored Pluto 1.1.7 compiles `javax.portlet.*` against `jakarta.servlet.*`, and Sakai's own portlets do the same. See [Sakai PortletServlet.java](https://raw.githubusercontent.com/sakaiproject/sakai/master/libs/pluto/pluto-container/src/main/java/org/apache/pluto/core/PortletServlet.java) and [Sakai PR #15141](https://github.com/sakaiproject/sakai/pull/15141).
- Servlet 6.0 removed APIs that were deprecated in Servlet 5.0 and earlier (Issue 418): `SingleThreadModel`, `HttpSessionContext`, `HttpUtils` and "various deprecated methods". The migration tools don't supply the removed classes or methods for bytecode that calls these. See [Jakarta Servlet spec source, "Changes Since Jakarta Servlet 5.0"](https://raw.githubusercontent.com/jakartaee/servlet/master/spec/src/main/asciidoc/servlet-spec-body.adoc).
- Cargo documents routing legacy `javaee`/`j2ee` WARs into `legacyAppBase` for automatic conversion on Tomcat 10+. See <!-- vale ai-tells.ColonUsage = NO --><!-- citation link text kept verbatim -->[Cargo: Deploying legacy WARs to Tomcat 10.x onwards](https://codehaus-cargo.atlassian.net/wiki/spaces/CARGO/pages/2520383491/Deploying+legacy+WARs+to+Tomcat+10.x+onwards)<!-- vale ai-tells.ColonUsage = YES -->.

### Inferences
- **The automatic conversion alone (option C) cannot work out of the box.** A converted portlet WAR calls, e.g., `javax.portlet.PortletRequest.getCookies()` with return type `jakarta.servlet.http.Cookie[]`. The stock `portlet-api_2.1.0_spec` jar declares `javax.servlet.http.Cookie[]`, so this causes `NoSuchMethodError`/linkage errors, or a jar that won't load at all on a container that has no `javax.servlet`.
- Making C work needs three pieces:
  1. A **hybrid `portlet-api` 2.0 jar**: same `javax.portlet` package, servlet types switched to `jakarta.servlet`. It can be produced by running the Tomcat tool or Eclipse Transformer over `portlet-api_2.1.0_spec-1.0.jar`, because its only servlet references are rewritable `javax.servlet` ones.
  2. A **jakarta-transformed Pluto 2.1.0-M3** (container + driver + taglib) plus uPortal's own portlet integration.
  3. Every portlet WAR transformed at build or deploy time.
- The alternative (custom Transformer rules that also rename `javax.portlet` → `jakarta.portlet`) only makes sense with a Portlet 4.0 container. A Portlet 2.0 WAR can't simply be renamed onto the 4.0 API, which has Portlet 3.0 semantics and signatures.
- Bytecode-transformed Spring 4.3 Portlet MVC is the weakest link:
  - Spring 4's servlet/portlet code compiled against Servlet 3.x may call methods removed in Servlet 6.0. Candidates such as `HttpServletResponse.setStatus(int,String)`, `encodeUrl`/`encodeRedirectUrl`, `HttpSession.getValue/putValue`, `isRequestedSessionIdFromUrl` and `ServletRequest.getRealPath` are from general knowledge. The spec source only states "various deprecated methods".
  - Any old taglibs or commons libraries have this problem too. Such failures appear only at runtime, on specific code paths.
  - Spring 4.3 is also long EOL. Transforming it doesn't fix its CVEs.
- JAR signatures are stripped during conversion. That is harmless for most libs but breaks signed-jar checks if any portlet relies on them.
- Sakai's case is easier than uPortal's. Portlet 1.0 has no servlet types in its signatures, and Sakai recompiled from source rather than transforming binaries. uPortal's Portlet 2.0 API does have them (Cookie), so the hybrid must be a deliberately rebuilt API jar.

### Gaps
- No public precedent was found of anyone running **unmodified javax JSR-286 WARs** on Tomcat 10/11 via `legacyAppBase` with a hybrid API.
- The exact list of Servlet 6.0 removed methods was not retrieved from a primary source in this session. The spec section says "various deprecated methods". Check the Servlet 6.0 API diff or Tomcat's migration-10.1 page (tomcat.apache.org was not fetchable here).
- The default profile used by `legacyAppBase` ("default settings") is not stated in the docs read. The README shows `profile="tomcat"` in its Ant example.

## Q4. Cross-context dispatch, session sharing, and Servlet 6.0/6.1 changes affecting portal containers

### Takeaway
Cross-context dispatch (`crossContext="true"` + `ServletContext.getContext()`) and shared session cookies (`sessionCookiePath="/"`) are still documented Tomcat Context attributes on the current main branch. No evidence was found of their removal or of behavior changes in 10.1/11. Servlet 6.0 removed long-deprecated APIs. Servlet 6.1 (Tomcat 11) removed SecurityManager references and added a few clarifications. The Pluto dispatch model still works on both releases.

### Cited Findings
- Tomcat `crossContext` attribute: "Set to true if you want calls within this application to ServletContext.getContext() to successfully return a request dispatcher ... Set to false (the default) in security conscious environments, to make getContext() always return null." See [Tomcat context.xml docs source (main)](https://raw.githubusercontent.com/apache/tomcat/main/webapps/docs/config/context.xml).
- Tomcat `sessionCookiePath`: "Once one web application using sessionCookiePath="/" obtains a session, all subsequent sessions for any other web application in the same host ... will always use the same session ID." See [Tomcat context.xml docs source](https://raw.githubusercontent.com/apache/tomcat/main/webapps/docs/config/context.xml).
- Other dispatch-related attributes still exist:
  - `dispatcherWrapsSameObject` checks that wrapped request/response objects passed to a dispatcher wrap the originals. Default true only under STRICT_SERVLET_COMPLIANCE.
  - `dispatchersUseEncodedPaths` defaults to true.

  See [Tomcat context.xml docs source](https://raw.githubusercontent.com/apache/tomcat/main/webapps/docs/config/context.xml).
- Servlet 6.0 changes:
  - Issue 418 removes deprecated API classes/methods (`SingleThreadModel`, `HttpSessionContext`, `HttpUtils` and other deprecated methods).
  - Issue 411 relaxes wrapping rules so containers can wrap requests/responses for `RequestDispatcher`.
  - Issue 225 deprecates wrapped-response `doHead` handling.

  See [Servlet spec source](https://raw.githubusercontent.com/jakartaee/servlet/master/spec/src/main/asciidoc/servlet-spec-body.adoc).
- Servlet 6.1 changes:
  - Issue 443 removes references to the SecurityManager and its APIs.
  - Issue 538 deprecates HTTP/2 server push.
  - Issue 544 makes error dispatches use GET.
  - Issue 74 adds `HttpServletResponse` methods to choose the redirect status code/body.
  - Issue 453 adds a security warning on `ServletContext` path methods.
  - Issue 546 lets apps use `HttpSession` outside requests (e.g., WebSocket).

  See [Servlet spec source](https://raw.githubusercontent.com/jakartaee/servlet/master/spec/src/main/asciidoc/servlet-spec-body.adoc).
- Spring Framework 7.0 requires Jakarta EE 11 with "Servlet 6.1 (Tomcat 11.0, Jetty 12.1)" as minimum, JDK 17 baseline and JPA 3.2 (Hibernate ORM 7.1/7.2). "Annotations in the javax.annotation and javax.inject packages are no longer supported." See [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes).

### Inferences
- uPortal's existing Tomcat configuration pattern (crossContext on the uPortal context, shared session cookie path) should carry over to Tomcat 11.
- Issue 411 (container wrapping in dispatch) and `dispatcherWrapsSameObject` matter for a portal that passes wrapped requests across contexts. They need testing but are not show-stoppers.
- Spring 7 → Tomcat 11 is a hard floor. A portlet stack must run on Servlet 6.1. Plutonium and PortletMVC4Spring 6.0.0-M1 were built against Servlet 6.0 / Spring 6.2, which is binary-compatible in most cases but untested for 6.1/Spring 7.
- Pluto 2.1/3.x code paths that used `SecurityManager`/`AccessController` privileged blocks need cleanup for Tomcat 11, which removed SecurityManager support.

### Gaps
- tomcat.apache.org/migration-11.0.html and migration-10.1.html could not be fetched. Tomcat-specific behavior changes to `getContext()` or session handling in 11.x were not confirmed or ruled out from the migration guides.
- No benchmark or bug report was found about cross-context dispatch regressions on Tomcat 11.

## Q5. How other portals and higher-ed projects handled Jakarta

### Takeaway
The open-source Java portal ecosystem has shrunk to Liferay and project-private forks:
- Apache Jetspeed and Pluto are in the Attic.
- JBoss Portal/GateIn and eXo don't show any Jakarta portlet work.
- Liferay forced a recompile to Portlet 4.0.
- Sakai (Apereo) vendored a tiny Pluto and jakarta-ized it in-tree.

Apereo CAS moved to Spring Boot 3 / jakarta but has no portlets, so it is a framework-migration precedent only.

### Cited Findings
- Apache Portals (Pluto and Jetspeed) retired in September 2025, and the Attic move completed in November 2025 (via search snippet). See <!-- vale ai-tells.ColonUsage = NO --><!-- citation link text kept verbatim -->[Attic: Apache Portals](https://attic.apache.org/projects/portals.html)<!-- vale ai-tells.ColonUsage = YES -->.
- In Liferay DXP 2025.Q3, javax libraries are no longer compatible and Tomcat 9 is dropped. Portlets are converted to Portlet 4.0 via `blade gw upgradeJakarta` (via search snippets). See [2025.Q3 Breaking Changes](https://learn.liferay.com/w/dxp/self-hosted-installation-and-upgrades/upgrading-liferay/deprecations-and-breaking-changes-reference/2025-deprecations-and-breaking-changes/2025-q3-breaking-changes) and [Jakarta 2025 FAQ](https://learn.liferay.com/w/reference/jakarta-2025-faq).
- Sakai's move to Tomcat 10.1 + Spring 6 + Hibernate 6 + Java 21 was merged Oct 9, 2026. Pluto 1.1.7 was vendored into the source tree and Tomahawk was vendored and migrated to jakarta. See [sakaiproject/sakai PR #15141](https://github.com/sakaiproject/sakai/pull/15141).
- eXo/GateIn: the search found only old docs (EPP 5.2 → eXo 4.2, Tomcat 7 era) and nothing on Jakarta or Tomcat 10. See [eXo blog: JBoss Portal migration](https://exoplatform.com/blog/jboss-portal-migration-guide-from-jpp-5-2-to-exo-platform-4-2-in-a-blink) and [GateIn docs](https://docs.jboss.org/gatein/uxp/latest/developer/html/DEV.eXoArchitecturePremer.defaultPortalContainer.html).
- Vaadin had an open issue to "Migrate Vaadin Portlet add-on to Jakarta Servlet 5". It shows third-party portlet frameworks stuck on the namespace issue. See [vaadin/flow #14386](https://github.com/vaadin/flow/issues/14386).
- Apereo CAS 7.2 (May 2025): "The entire codebase has been migrated to Spring Boot 3.4" (jakarta-based). See [Apereo CAS 7.2 announcement](https://www.apereo.org/news/2025/apereo-cas-72-release-announcement).
- uPortal Soffit docs:
  - Soffit is "intended as an alternative to JSR-286 portlet development"
  - "an alternative approach to producing content for uPortal that is not based on JSR-286 or the portlet container"
  - "the Java Portlet APIs are large, obtuse, and actively interfere with contemporary web development practices"

  See <!-- vale ai-tells.ColonUsage = NO --><!-- citation link text kept verbatim -->[uPortal docs: Soffits README](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/docs/developer/soffits/README.md)<!-- vale ai-tells.ColonUsage = YES -->.
- **The Soffit connector is itself a portlet inside uPortal.** `SoffitConnectorController` is a Spring Portlet MVC `@Controller` with `@RequestMapping(value = {"VIEW", "EDIT", "HELP"})`. Publishing a soffit means choosing type "Portlet" and selecting "/uPortal" and "Soffit Connector". It also means setting the `...SoffitConnectorController.serviceUrl` preference. See [SoffitConnectorController.java](https://github.com/uPortal-Project/uPortal/blob/master/uPortal-soffit/uPortal-soffit-connector/src/main/java/org/apereo/portal/soffit/connector/SoffitConnectorController.java) and [publishing_a_soffit.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/developer/soffits/publishing_a_soffit.md).
- GitHub issue search on `uPortal-Project/uPortal` for "jakarta" returned 0 results. No public uPortal Jakarta plan is tracked there. See [uPortal issues](https://github.com/uPortal-Project/uPortal/issues).

### Inferences
- Even "drop JSR-286" (option D) does not remove the need for a portlet container inside uPortal unless uPortal also rewrites its rendering pipeline. The Soffit connector is rendered through Pluto as a portlet in the `/uPortal` context. uPortal's own built-in admin/UI channels are most likely portlets as well (not verified here). Option D realistically means keeping a minimal in-webapp portlet container for uPortal-internal portlets, or replacing the portlet-pipeline integration with a native rendering-pipeline component type.
- Sakai, the closest peer (Apereo, higher-ed, Tomcat, Spring), chose "own a minimal container in-tree" over adopting Liferay's stack.

### Gaps
- No academic or higher-ed portal was found that publicly documented abandoning portlets entirely during a Jakarta migration.
- JBoss Portal is long discontinued, and no 2025 to 2026 source was found. eXo Platform's current Jakarta status was not found.
- The CAS 7.0 Jakarta details (first jakarta release) were not retrieved. Only the 7.2 announcement was.

## Q6. Spring Portlet MVC replacement on Jakarta: Liferay PortletMVC4Spring

### Takeaway
Spring Portlet MVC was removed in Spring 5.0, so uPortal's Spring-4 portlets must move to PortletMVC4Spring or be rewritten in any case. PortletMVC4Spring **6.0.0-M1** (tagged Sep 24, 2025) supports Jakarta EE 10, Jakarta Portlet 4.0 and **Spring 6.2.10**. It is milestone-only, not on Maven Central, has no Spring 7 version, and requires a Portlet 3.0/4.0 container (Plutonium or Liferay), not Pluto 2.1.

### Cited Findings
- The Spring Framework 5.0 release notes say <!-- vale ai-tells.ColonUsage = NO --><!-- direct quote from the Spring release notes -->"The Spring Framework no longer supports: Portlet, Velocity, JasperReports, XMLBeans, JDO, Guava"<!-- vale ai-tells.ColonUsage = YES -->. Users needing these should stay on 4.3.x. See [Spring Framework 5.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.0-Release-Notes).
- PortletMVC4Spring README:
  - "The PortletMVC4Spring master branch is for compatibility with Spring Framework 6.x, which requires Jakarta EE and JDK 17+"
  - coordinates `com.liferay.portletmvc4spring:com.liferay.portletmvc4spring.framework:6.0.0-M1`
  - "has been upgraded from version 2.0 of the Portlet API to version 3.0"
  - supported portals: "Liferay Portal 7.1+", "Apache Pluto 3.0+"
  - Apache-2.0

  See [liferay/portletmvc4spring](https://github.com/liferay/portletmvc4spring) and [README](https://raw.githubusercontent.com/liferay/portletmvc4spring/master/README.md).
- PortletMVC4Spring tags:
  - 6.0.0-M1 (Sep 24, 2025)
  - 5.3.4 and 5.2.5 (Apr 26, 2024)
  - 5.3.3 and 5.2.4 (Dec 12, 2023)

  See [portletmvc4spring tags](https://github.com/liferay/portletmvc4spring/tags).
- Master pom:
  - version `6.0.0-M2-SNAPSHOT`, with the comment `<!-- CURRENTLY UNUSED BRANCH: SEE README -->` (ambiguous)
  - `spring.framework.version` 6.2.10, `spring.security.version` 6.5.3
  - portlet API `com.liferay.jakarta.portlet:com.liferay.jakarta.portlet-api:4.0.0`, `jakarta.servlet-api` 6.0.0
  - Java 17
  - distributionManagement to `https://repository.liferay.com/nexus/content/repositories/third-party`

  See [portletmvc4spring pom.xml](https://raw.githubusercontent.com/liferay/portletmvc4spring/master/pom.xml).
- Key commits: "MVCS-77 Upgrade to Jakarta EE 10, Jakarta Portlet 4.0, and Spring 6.2.10" and "Specify the liferay-third-party repository ahead of 6.0.0-M1 release, since not releasing to Central". All recent commits are by one author (ngriffin7a). See [portletmvc4spring commits](https://github.com/liferay/portletmvc4spring/commits/master).
- Maven Central has `com.liferay.portletmvc4spring.framework` only up to **5.3.4 (2024-04-26)**. There is no 6.x on Central. See [Maven Central listing](https://repo1.maven.org/maven2/com/liferay/portletmvc4spring/com.liferay.portletmvc4spring.framework/).
- The security and webflow modules depend on `com.liferay.plutonium:portlet-servlet-adapter` (e.g., `com.liferay.plutonium.portlet.servlet.adapter.HttpServletRequestAdapter` in `SpringSecurityRequestAdapter`). So PortletMVC4Spring 6 pulls in a piece of Plutonium even when it runs on other containers. See [SpringSecurityRequestAdapter.java](https://github.com/liferay/portletmvc4spring/blob/master/security/src/main/java/com/liferay/portletmvc4spring/security/SpringSecurityRequestAdapter.java) and [security/pom.xml](https://github.com/liferay/portletmvc4spring/blob/master/security/pom.xml).
- Liferay's migration guide steps for moving Spring Portlet MVC → PortletMVC4Spring (via search snippet, see [Migrating to PortletMVC4Spring and Jakarta](https://learn.liferay.com/w/dxp/development/traditional-java-based-development/developing-a-web-application/using-spring/migrating-to-portletmvc4spring)):
  - swap the artifact
  - change `DispatcherPortlet` class in portlet.xml
  - replace `AnnotationMethodHandlerAdapter` with `PortletRequestMappingHandlerAdapter`
  - then rename `javax.portlet` → `jakarta.portlet` for Jakarta

### Inferences
- The required path for each uPortal portlet built on Spring Portlet MVC 4.x has four steps:
  1. Spring Portlet MVC → PortletMVC4Spring (package `org.springframework.web.portlet` → `com.liferay.portletmvc4spring`).
  2. Portlet 2.0 → 3.0 semantics.
  3. `javax.*` → `jakarta.*`.
  4. Spring 4 → 6.2/7, including Spring Security and Spring Web Flow if used.

  That is effectively a re-platforming of each portlet.
- **For Spring 7 a fork is needed.** PortletMVC4Spring tracks Spring's major.minor in its version. A Spring 7.0 port would mainly mean:
  - adapting to Spring 7 API removals (e.g., `javax.annotation`/`javax.inject` support gone, deprecated MVC internals removed)
  - Servlet 6.1
  - Spring Security 7 changes

  The codebase is a fork of Spring 4.3's portlet module (~a few hundred classes). Rough guess: a few person-weeks for a Spring-experienced developer, plus ongoing maintenance as Spring evolves. This is an estimate, not sourced.
- Alternatively, portlets could run Spring 6.2 internally (each portlet WAR has its own classloader) while uPortal itself uses Spring 7. Spring 6.2 OSS support ends in 2026 (not verified in this session), which limits that option.

### Gaps
- Whether Liferay has a Spring 7 branch or plan for PortletMVC4Spring was not found (no mention on the README or commits pages).
- No GA (non-milestone) 6.0.0 release was found.
- The meaning of the "CURRENTLY UNUSED BRANCH" pom comment is unclear.

## Q7. Effort/risk comparison of options A to D

### Takeaway
- **D (move content to Soffit/web components) is the strategic end-state.** It still needs a small in-webapp portlet container for the Soffit connector and uPortal-internal portlets.
- **A** is the only path that runs JSR portlets on Jakarta against a standard portlet API (Jakarta Portlet 4.0). It means adopting Liferay Plutonium 4 + Jakarta Portlet 4.0 + PortletMVC4Spring 6 (forked for Spring 7). The stack rests on unreleased, single-maintainer, non-Central artifacts.
- **C** cannot work without a hybrid portlet API plus a jakarta-ized Pluto 2.1, and it leaves EOL Spring 4 bytecode in production.
- **B** only keeps javax portlets running on a separate Tomcat 9 until they are replaced.

The most pragmatic plan is a hybrid of D with a minimal A or Sakai-style container:
1. Jakarta-ize (or replace with Plutonium) the container only as far as needed for the Soffit connector and the in-webapp portlets.
2. Re-platform the high-value bundled portlets (Announcements, Calendar, News, Notification, Bookmarks, SimpleContent) as soffits/web components.
3. Retire low-value ones (CAS proxy test, Pluto testsuite, possibly WebProxy/Feedback).

### Cited Findings
- No javax→jakarta tool renames `javax.portlet`. See [EESpecProfiles.java](https://raw.githubusercontent.com/apache/tomcat-jakartaee-migration/main/src/main/java/org/apache/tomcat/jakartaee/EESpecProfiles.java) and [jakarta-renames.properties](https://raw.githubusercontent.com/eclipse-transformer/transformer/main/org.eclipse.transformer.jakarta/src/main/resources/org/eclipse/transformer/jakarta/jakarta-renames.properties).
- The Portlet 2.0 API exposes `javax.servlet.http.Cookie` in `PortletRequest.getCookies()`. See [PortletRequest.java](https://raw.githubusercontent.com/apache/portals-pluto/master/portlet-api/src/main/java/javax/portlet/PortletRequest.java).
- Plutonium 4 (Apache-2.0) is the Jakarta Portlet 4.0 CI. Its artifacts are 4.0.0-M1 on Liferay's third-party repo. See [liferay/plutonium](https://github.com/liferay/plutonium) and [portletmvc4spring commits](https://github.com/liferay/portletmvc4spring/commits/master).
- PortletMVC4Spring 6.0.0-M1 supports Spring 6.2.10 only and is not on Central. See [pom.xml](https://raw.githubusercontent.com/liferay/portletmvc4spring/master/pom.xml) and [Maven Central](https://repo1.maven.org/maven2/com/liferay/portletmvc4spring/com.liferay.portletmvc4spring.framework/).
- Spring 7 requires Servlet 6.1 / Tomcat 11. See [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes).
- Precedent for in-tree hybrid javax.portlet on jakarta.servlet (Sakai, Pluto 1.1.7). See [Sakai PR #15141](https://github.com/sakaiproject/sakai/pull/15141).
- The Soffit connector is a Spring Portlet MVC portlet in `/uPortal`. See [SoffitConnectorController.java](https://github.com/uPortal-Project/uPortal/blob/master/uPortal-soffit/uPortal-soffit-connector/src/main/java/org/apereo/portal/soffit/connector/SoffitConnectorController.java).
- Pluto relies on in-container dispatch to a `PortletServlet` in each portlet WAR. See [Pluto deploying 2.0](https://portals.apache.org/pluto/v20/deploying.html) (via search snippet).

### Inferences

Effort/risk matrix. These are analyst estimates based on the findings above. Sizes are relative (S < M < L < XL).

| Option | What it entails | Effort | Technical risk | Sustainability risk | Notes |
|---|---|---|---|---|---|
| **A1. Adopt Liferay Plutonium 4 + Jakarta Portlet 4.0 + PortletMVC4Spring 6 (fork to Spring 7)** | Replace Pluto 2.1 with Plutonium (Pluto 3 SPI) in uPortal. Rebuild every portlet (Spring Portlet MVC 4 → PortletMVC4Spring, Portlet 2.0 → 4.0, javax → jakarta, Spring 4 → 7). Republish API/Plutonium/PMVC4S artifacts (not on Central). | **XL**: container SPI port in uPortal (L) + PMVC4S Spring 7 fork (M) + ~10 portlets × (M each) | Med to high, from Pluto 3 SPI differences, Portlet 3 semantics (render state, CDI/Weld) and no testing on Servlet 6.1 | **High**: milestone-only, single Liferay maintainer, spec "under development" and no Central artifacts. uPortal would effectively co-own Plutonium | Keeps the portlet ecosystem and the ESUP/other community portlets possible. Aligns with the only maintained Jakarta portlet line. |
| **A2. Sakai-style hybrid: jakarta-ize Pluto 2.1.0-M3 in-tree, keep `javax.portlet` 2.0 API rebuilt against jakarta.servlet** | Vendor Pluto 2.1 container/driver/taglib into uPortal and source-convert it to jakarta.servlet. Rebuild a hybrid `portlet-api` 2.0 jar (Cookie → jakarta). Portlets still need Spring Portlet MVC replaced (PMVC4S 5.x targets Portlet 3.0 and javax, so it would need its own hybrid fork) or rewritten. | **L** for container (Pluto 2.1 is smaller, uPortal already integrates it) + **L to XL** for portlets | Medium: well-trodden by Sakai for 1.0. The Cookie signature change is the main API wrinkle | Medium to high: uPortal alone maintains a non-standard "javax.portlet on jakarta" API, and no third-party portlets will target it | Fastest way to keep uPortal's **internal** portlets (incl. Soffit connector) running on Tomcat 11. |
| **B. Keep portlets on javax in a separate Tomcat 9** | Run a legacy portlet host (an old uPortal/Pluto 2.1 instance) in Tomcat 9. Integrate into the Jakarta uPortal via iframe, reverse proxy, WSRP, or a "portlet-to-soffit" adapter. | **M** (iframe/proxy) to **L** (adapter/WSRP) | Medium. Pluto needs cross-context dispatch inside one container, so portlets cannot be dispatched from the Tomcat 11 uPortal. There would be two portals and two sessions/SSO, and inter-portlet events are lost | **High**. Tomcat 9 / Spring 4 / JDK compatibility age out, and an EOL stack brings security exposure | Only for keeping portlets running during a migration window. |
| **C. Automatic deploy-time WAR transformation (`legacyAppBase` / Transformer)** | Transform each javax portlet WAR to jakarta.servlet. Requires A2's hybrid API + jakarta Pluto anyway. Transformed Spring 4.3 inside each WAR. | **M** on top of A2's container work | **High**: runtime `NoSuchMethodError` from Servlet-6.0-removed APIs in old libs; signed jars stripped; no precedent found; debugging transformed bytecode | **High**: EOL Spring 4 bytecode in production with unpatched CVEs | Only viable as a short-term crutch layered on A2. Tools do nothing for `javax.portlet`. |
| **D. Drop JSR-286, content → Soffit / web components** | Rewrite each bundled portlet as a soffit (Spring Boot 3/4 app) or web component. Retire low-value portlets. Keep or replace the in-uPortal Soffit connector. | **L** overall (≈ M per portlet kept, and several can be retired) | Low to medium: modern stack. Data migration is needed for portlet preferences/content (Announcements, Bookmarks, Calendar configs) | **Low**: no dependence on a dead spec | Still needs a portlet container for the Soffit connector and uPortal-internal portlets (→ A2 or A1-lite), or a rewrite of the connector as a native rendering-pipeline component. |

- **Recommended direction (inference):** use D for content portlets, combined with a minimal A2 (or a Plutonium-based A1 if Portlet 3/4 conformance is a community requirement) for the portlet pipeline that remains inside uPortal. Use B or C only for a fixed period, to keep institutions' custom javax portlets running until they are replaced.
- Signals that would change the recommendation:
  - Jakarta Portlet 4.0 gets an official Eclipse release with Central artifacts.
  - Plutonium reaches GA on Central with an active community.
  - Liferay publishes a Spring 7 PortletMVC4Spring.

  Any of these would lower A1's sustainability risk considerably.
- Downstream deployers (e.g., ESUP-Portail institutions with esup-filemanager and custom portlets) face the same choice per portlet. A documented "how to port a portlet to soffit" path, plus a time-boxed legacy Tomcat 9 for the remaining javax portlets (B), would reduce adoption risk.

### Gaps
- No quantitative effort data (LOC, number of Spring Portlet MVC controllers per portlet) was collected for the bundled portlets. Sizing per portlet is a guess.
- uPortal's use of the Pluto 2.1 SPI was not measured: the number of classes in uPortal written against `org.apache.pluto.container` SPIs, which drives the cost of A1 vs A2.
- Tomcat 9's end-of-support date (relevant to B) was not verified in this session.
- No information was found on whether uPortal ever had a working WSRP consumer usable for B.
