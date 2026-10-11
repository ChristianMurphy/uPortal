# uPortal 6.0 Jakarta spike: transformed WARs + hybrid portlet API on Tomcat 11 (non-merging)

Date: 2026-10-10. Spike tree: `spike` (scripts in `spike/scripts`, logs in `spike/logs`).
Nothing under `uPortal`, `uPortal-start` or `wt` was modified, committed or pushed.
The spike used ports 9080 (HTTP), 9005 (shutdown) and 9887 (HSQLDB), with AJP off. Heap `-Xmx2g`. Everything is stopped at the end.

## Summary

- **What ran:** the already-built uPortal 5.17.10-SNAPSHOT portal, converted from javax to jakarta at the bytecode level with Apache Tomcat's `jakartaee-migration` 1.0.13. That covers the uPortal webapp, the 12 portlet WARs (incl. pluto-testsuite), `resource-server`, `ResourceServingWebapp`, CAS 3.6 and the `shared/lib` jars.
- **Where it ran:** on Tomcat **11.0.26** (JDK 21 and JDK 25) and Tomcat **10.1.60** (JDK 21). The database was a copy of uPortal-start's HSQLDB.
- **Playwright:** the baseline test set (uPortal-start `2a519ad`, 119 tests) gives **116/119 on every configuration** in two consecutive runs each. The failures are the same 3 sandbox-blocked tests as the baseline. Per spec, the results are identical to the baseline.
- **Wider checks:**
  - The P0 workstream's new admin Web Flow specs also pass. They cover the portlet manager, the groups manager and the permissions manager.
  - All 94 registered portlets render maximized. The only error card comes from the `error` portlet, which exists to render one.
  - The Pluto 2.0 testsuite gives exactly the same results as untransformed Tomcat 8.5. 144 checks pass, and the same 16 checks fail as they already do on 5.17.
- **Fixes needed:**
  - **Tomcat 10.1:** none, apart from one transform exclude (icu4j).
  - **Tomcat 11:** three. All of them come from the removal of the JSP 2.0 `jakarta.servlet.jsp.el` API in Pages 4.0, plus Spring Boot 1.5 reaching into Tomcat internals.
  - **JDK 25:** one JVM property, because XSLT extension functions are now off by default.
- **Verdict on transformed WARs with the hybrid portlet API:** **works, with caveats**. See (f).

## (a) Setup recipe

### Inputs (copied, read-only snapshot)
```bash
mkdir -p spike/{src,dl,scripts,logs,src/tomcat85-lib}
cd uPortal-start/.gradle/tomcat && cp -a webapps shared portal conf bin spike/src/
cp -a uPortal-start/.gradle/hsqldb spike/src/hsqldb        # uPortal.script/.lobs/.properties, modified=no
cp lib/{servlet-api,jsp-api,el-api,websocket-api,annotations-api,jaspic-api}.jar spike/src/tomcat85-lib/   # reference API for the scanner
```
HSQLDB is the 2.5.1 jar from the Gradle cache (the same version as `hsqldbVersion`). `scripts/hsql.sh start|stop|reset` runs `org.hsqldb.server.Server --database.0 file:spike/hsqldb/db/uPortal;hsqldb.tx=mvcc --dbname.0 uPortal --port 9887`.

### Downloads (all checksum-verified)
- `archive.apache.org/dist/tomcat/tomcat-11/v11.0.26/bin/apache-tomcat-11.0.26.tar.gz` (.sha512)
- `archive.apache.org/dist/tomcat/tomcat-10/v10.1.60/bin/apache-tomcat-10.1.60.tar.gz` (.sha512)
- `repo1.maven.org/maven2/org/apache/tomcat/jakartaee-migration/1.0.13/jakartaee-migration-1.0.13-shaded.jar` (.sha1). This is the latest 1.0.x, published 2026-10-05. Tomcat 11.0.26 itself bundles 1.0.12. I also fetched the -sources jar to read `EESpecProfiles`.
- `org.apache.taglibs:taglibs-standard-{impl,spec,jstlel}:1.2.5`:
  - Maven Central returned 429 (MOD-I52) for impl and spec.
  - I took impl and spec from Tomcat 8.5's `examples` webapp instead, and verified their sha1 against Google's Central mirror.

### Tomcat configuration (ported from uPortal-start, `tomcat11/` and `tomcat10/` identical)
- **`conf/server.xml`:**
  - Server port 9005 and one HTTP connector on 9080 with `maxThreads="1200"` and compression. There is no AJP connector.
  - `autoDeploy="false"`, and the access log gains `%D`.
  - The connector attribute must be spelled `compressibleMimeType`. uPortal-start's 8.5 `server.xml` uses `compressableMimeType`, which Tomcat 10.1/11 no longer has a setter for (the scanner also flags `setCompressableMimeType` as removed), so it would be silently dropped.
- **`conf/context.xml`:** `<Context sessionCookiePath="/">` + `<Manager pathname=""/>` + `<Resources cachingAllowed="true" cacheMaxSize="100000"/>`.
- **`conf/catalina.properties`:** `shared.loader=${catalina.base}/shared/lib/*.jar`.
- **Per-webapp `META-INF/context.xml`:** unchanged. uPortal and every portlet keep `crossContext="true"`.
- **`bin/setenv.sh`:**
  - `JAVA_HOME=${SPIKE_JAVA_HOME:-jdk21}`, `-Dportal.home=$CATALINA_BASE/portal`, `-XX:+UseG1GC -Xms256m -Xmx2g`, and the IPv4 and log4j flags from uPortal-start.
  - A `-Dspike.marker=spike` tag that the scripts match on, so they only ever stop spike JVMs.
  - An `add-opens.txt` hook and `SPIKE_EXTRA_OPTS`.
- **`bin/catalina.sh`:** a copy was made switchable (`SPIKE_NO_TOMCAT_OPENS=1` drops Tomcat's six built-in `--add-opens`), for the minimisation experiment only.

### Transform (exact options)
```bash
J=/usr/lib/jvm/java-21-openjdk-amd64/bin/java; T=dl/jakartaee-migration-1.0.13-shaded.jar
# shared/lib, one jar at a time
$J -jar $T -profile=TOMCAT -logLevel=FINE src/shared/lib/<jar> transformed/shared-lib/<jar>
# every webapp directory (scripts/transform-all.sh runs 2 in parallel, -Xmx1g each)
$J -Xmx1g -jar $T -profile=TOMCAT -logLevel=FINE src/webapps/<app> transformed/webapps/<app>
# CalendarPortlet and NewsReaderPortlet additionally need (blocker B1):
$J -Xmx1g -jar $T -profile=TOMCAT -exclude=icu4j-*.jar -logLevel=FINE src/webapps/<app> transformed/webapps/<app>
```
- **Profile:** `-profile=TOMCAT`, the default. It is also what Tomcat's `legacyAppBase` uses.
- **Archive mode:** streaming. `-zipInMemory` was never needed.
- **Default excludes:** left on, so jars such as spring-aop, spring-expression, jackson-core, commons-codec and slf4j-api are copied unchanged. That is 1 to 14 jars per webapp.
- **Wall time:** about 5 minutes for all 16 webapps, on 4 loaded CPUs.
- **Narrow variant:** `scripts/NarrowMigrate.java.txt` drives the same tool as a library with a custom profile. That profile covers servlet+jsp, el, websocket, jaspic and the Tomcat-provided `javax.annotation` classes, and leaves `javax.persistence`, `javax.mail`, `javax.ejb` and `javax.transaction` alone. The CLI cannot express this.
  ```bash
  $J -cp $T scripts/NarrowMigrate.java.txt src/webapps/<app> transformed-narrow/webapps/<app> 'icu4j-*.jar'
  ```
- **Hybrid API:** the TOMCAT-profile output of `portlet-api_2.1.0_spec-1.0.jar` is the hybrid API. Exactly 4 classes change (`PortletRequest`, `PortletResponse`, `filter.PortletRequestWrapper`, `filter.PortletResponseWrapper`), and the only change is that `Cookie` now comes from `jakarta.servlet.http`. `javax.portlet` keeps its name.
- **Pluto:** `pluto-container-api` (8 classes), `pluto-container-driver-api` (4) and `pluto-taglib` (19) are moved to jakarta.servlet the same way. `pluto-container`/`pluto-portal-driver` 2.1.0-M3 inside uPortal's WEB-INF/lib are transformed along with uPortal. `ccpp` changes 1 class and person-directory-api changes 0.

### Deploy, fixups, run
```bash
scripts/deploy.sh spike/tomcat11 spike/transformed   # copies shared-lib + webapps, patches 8080->9080 / 8887->9887 in 13 files
scripts/fixups.sh spike/tomcat11                                # Tomcat 11 only: F1 JSTL swap, F2 Boot exclude, F3 jsp.el shim
scripts/hsql.sh start
SPIKE_JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 SPIKE_EXTRA_OPTS="-Djdk.xml.enableExtensionFunctions=true" scripts/tc.sh start   # JDK 25
scripts/tc.sh start                                                        # JDK 21 (default)
scripts/smoke.sh; scripts/crawl.sh <base> <out>; scripts/pluto-testsuite.sh http://localhost:9080 <out>; python3 -I scripts/pluto-parse.py <dir> <out>
PWDIR=spike/pw-base scripts/pw-run.sh <name>                    # Playwright, base URL http://localhost:9080/uPortal/
scripts/tc.sh stop   # = catalina.sh stop 45 -force (MOD-I53 hang, see B6)
scripts/scan-removed-api.sh <tomcat lib> transformed/shared-lib transformed/webapps   # removed-API / linkage scanner (section c)
```

**Playwright test sets.** The uPortal-start test tree is changing under the P0 workstream: commits `09080d8` … `474c66f` landed during the spike, including new `ux/admin` specs and a tightened web-proxy wait (MOD-I54). So I exported two pinned copies read-only with `git archive`:
- `pw-base` = the baseline test set at `2a519ad`: 19 specs, 119 tests.
- `pw-head` = `474c66f`: 22 specs, 122 tests.

In both copies `tests/general-config.ts` is changed to `http://localhost:9080/uPortal/`. `node_modules` is a symlink to uPortal-start's. `PLAYWRIGHT_BROWSERS_PATH=$SP/pw-browsers-1208-shim`.

**The 8.5 reference.** `tomcat85/` is a copy of uPortal-start's own Tomcat 8.5.100, with untransformed webapps, on JDK 11 and the same ports and DB copy. I used it to get like-for-like reference numbers (Pluto testsuite, crawl, log noise) in this sandbox.

**Disk.** The deployed webapp copies and the working DB (about 6.5 GB) were deleted at the end, because the disk had reached 99% (601 MB free). They can be regenerated with `deploy.sh`, `fixups.sh` and `hsql.sh reset`. `src/`, `transformed/`, `transformed-narrow/` and the logs are kept.

### Transform record
| webapp | classes rewritten | archives processed / copied unchanged | notes |
|---|---|---|---|
| uPortal | 2712 | 267 / 14 | |
| resource-server | 1251 | 77 / 9 | |
| ResourceServingWebapp | 707 | 27 / 5 | |
| Announcements | 1743 | 75 / 8 | |
| BookmarksPortlet | 1075 | 39 / 5 | |
| CalendarPortlet | 2250 | 168 / 14 | **aborted rc=1 without `-exclude=icu4j-*.jar`** (B1) |
| FeedbackPortlet | 967 | 55 / 8 | |
| NewsReaderPortlet | 1446 | 104 / 11 | **aborted rc=1 without `-exclude=icu4j-*.jar`** (B1) |
| NotificationPortlet | 2012 | 114 / 12 | |
| SimpleContentPortlet | 1734 | 131 / 7 | |
| WebProxyPortlet | 1991 | 126 / 12 | |
| basiclti-portlet | 341 | 14 / 5 | |
| cas (CAS 3.6) | 1295 | 92 / 11 | |
| cas-proxy-test-portlet | 680 | 16 / 5 | |
| esup-filemanager | 785 | 68 / 12 | |
| pluto-testsuite | 162 | 6 / 1 | |

**What the TOMCAT profile renamed** (namespace census over every constant pool, before and after; `logs/census-*.txt`, `scripts/census-diff.py`):
- **Renamed in every WAR:** `javax.servlet` (incl. `jsp`) and `javax.el`, plus `javax.websocket` where present.
- **Also renamed:** `javax.annotation.{PostConstruct,PreDestroy,Resource,Generated,…}` and, contrary to the report's assumption, **`javax.persistence`, `javax.transaction` (except `.xa`), `javax.ejb` and `javax.mail`**. In uPortal, 884 classes reference `javax.persistence` and 147 reference `javax.transaction`.
  - The tool renames zip entry paths as well as references. So `hibernate-jpa-2.1-api` becomes a self-consistent "`jakarta.persistence` 2.1" jar inside each WAR, and each WAR is internally consistent. Nothing broke because of it.
- **Kept, correctly:**
  - `javax.portlet` (348 classes in uPortal), `javax.ccpp`, `javax.cache` (26), `javax.xml.*` (incl. `javax.xml.bind`, 757), `javax.inject` and `javax.validation`.
  - JSR-305 `javax.annotation.{Nonnull,concurrent,meta}`, and all JDK `javax.*` packages (`sql`, `naming`, `crypto`, `management`, …).
- **Left behind:** none. No class file in any of the 16 webapps still contains a `javax.servlet`, `javax.el` or `javax.websocket` string after the transform.
- **Wrongly renamed:** none observed. The only jakarta namespaces with no provider are the optional ones that also had no javax provider before (e.g. `jakarta.mail` referenced by logback's SMTP appender).
- **Narrow custom profile:** keeps `javax.persistence` (e.g. 291 classes in Bookmarks), `javax.transaction`, `javax.ejb` and `javax.mail`.

## (b) Blockers

Severity levels:
- **Critical** = portal or most portlets down.
- **High** = a portlet or a login flow down.
- **Medium** = latent, will fail on a rarely used path.
- **Low** = cosmetic or operational.

| # | Symptom | Root cause | Category | Spike workaround | Real fix and plan phase | Severity |
|---|---|---|---|---|---|---|
| B1 | Migration of CalendarPortlet and NewsReaderPortlet stops with `Error performing migration: Invalid class file [com/ibm/icu/impl/data/LocaleElements_zh__PINYIN.class]` (rc=1) and leaves a half-written webapp | `icu4j-2.6.1.jar` (2003) contains a corrupt class (javap: "unexpected tag at #41: 60"). The tool's BCEL parser throws, and the tool aborts the whole archive instead of copying the entry through. `legacyAppBase` uses default settings, so (by inference; not run) it could never deploy these two WARs | Transform tool + portlet WAR | `-exclude=icu4j-*.jar` | **P5-5:** build-time transform step with a per-WAR exclude list, failing the build on a non-zero exit. Drop the 2003 icu4j from Calendar/NewsReader (it has no EE references). Do not rely on `legacyAppBase` | High for deploy-time conversion; Low for build-time |
| B2 | Tomcat 11: NotificationPortlet fails to deploy. `BeanCreationException 'tomcatEmbeddedServletContainerFactory'` → `NoClassDefFoundError: org/apache/coyote/http11/AbstractHttp11JsseProtocol` | NotificationPortlet is a **Spring Boot 1.5.22** WAR. Tomcat's internals are visible to webapps through the common loader, so Boot's `EmbeddedServletContainerAutoConfiguration` sees `Tomcat.class` and introspects `TomcatEmbeddedServletContainerFactory`. That class references `AbstractHttp11JsseProtocol`, which Tomcat 11 removed (10.1 still has it, so 10.1 deploys it) | Portlet WAR (Boot 1.5) + Tomcat 11 change | F2: add `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration` to the WAR's `application.properties` | **P5:** overlay property in uPortal-start (or move NotificationPortlet off Boot 1.5). The scanner's Tomcat-internals layer reports it (`REMOVED-CLASS org.apache.coyote.http11.AbstractHttp11JsseProtocol`) | High (portlet absent); trivial fix |
| B3 | Tomcat 11: every JSP that uses `<c:set>` fails to compile with `NoClassDefFoundError: jakarta/servlet/jsp/el/VariableResolver` in `org.apache.jasper.compiler.Generator$TagHandlerInfo`. This hits search-launcher, portal-activity, uPortal's own error portlet (`PortletError/generic.jsp`) and most portlet JSPs | 13 of 16 WARs, uPortal included, bundle **JSTL 1.1.2** (`taglibs:standard:1.1.2`, 2004). Its `SetSupport` has a method signature and an exception handler that use the JSP 2.0 `jsp.el` API (`ExpressionEvaluator`, `VariableResolver`, `ELException`). Jakarta Pages 4.0 (Tomcat 11) removed that API. Pages 3.1 (Tomcat 10.1) still has it, deprecated. Jasper's tag introspection makes the verifier load it | uPortal WAR + portlet WARs (dependency) + Tomcat 11 change | F1: delete `standard-1.1.2.jar`, `jstl-1.1.2.jar` and `jstl-1.2.jar`, then add migrated Apache Taglibs 1.2.5 `impl`+`spec` (+`jstlel` for pluto-testsuite's JSTL 1.0 URIs). The TLD URIs are unchanged, so no JSP edits are needed | **P5 (while still javax):** move uPortal and every bundled portlet to taglibs-standard 1.2.5. This is a no-op on Tomcat 8.5/9, and it also removes the duplicate JSTL impls of B13. Failing that, the uPortal-start transform step substitutes the jar. It is a hard requirement before **P7-1** (Tomcat 11) | **Critical on Tomcat 11** (most portlets error out), none on 10.1 |
| B4 | Tomcat 11: the Feedback form and the CAS login and logout pages fail with `NoClassDefFoundError: jakarta/servlet/jsp/el/ELException` from `AbstractFormTag.evaluate`/`MessageTag` → `org.springframework.web.util.ExpressionEvaluationUtils`. `/cas/login` and `/cas/logout` return 500, so uportal-auth, 7 portlet-list specs (through `logoutViaApi`) and all 3 feedback specs fail | **Spring 3.1/3.2** `ExpressionEvaluationUtils` references the removed `jsp.el` classes. They are only *used* when `springJspExpressionSupport` is on, and it is off by default on Servlet 3+. Class verification still needs them. Affected are FeedbackPortlet (Spring 3.2.18) and CAS 3.6 (3.1.1). The scanner also flags ResourceServingWebapp (3.1.4) and esup-filemanager (3.1.1) | Portlet WARs (EOL Spring 3.x) + Tomcat 11 change | F3: a jar in `shared/lib` that restores the six removed `jakarta.servlet.jsp.el` classes, made by migrating them out of Tomcat 8.5's `jsp-api.jar`. Because webapp loaders delegate `jakarta.servlet.*` to the parents, the shared loader supplies them. Classes are enough; no removed *methods* are called | **P5:** upgrade Feedback to Spring 4.3+ (or re-platform it in 6.x), and retire CAS 3.6 from uPortal-start (MOD-I36/Q3). Move ResourceServingWebapp and esup-filemanager off Spring 3.1. Keep the F3 jar only as a documented emergency fallback. Needed before **P7-1** | High on Tomcat 11, none on 10.1 |
| B5 | JDK 25: every page returns 500 with `TransformerConfigurationException: The first argument to the non-static Java function 'getUrlString' is not a valid object reference` (`layout/theme/urlTemplates.xsl`) | JDK 25's default `conf/jaxp.properties` sets **`jdk.xml.enableExtensionFunctions=false`**. This is upstream text: "the default value is false (disallowing extension functions)". uPortal's theme pipeline calls Java through Xalan-Java extension namespaces on the JDK's built-in XSLTC. The same file also tightens limits: `entityExpansionLimit=2500`, `totalEntitySizeLimit=100000`, `maxGeneralEntitySizeLimit=100000`, `elementAttributeLimit=200`, `maxOccurLimit=5000`, `maxElementDepth=100` | JDK 25 behaviour change (uPortal code) | `-Djdk.xml.enableExtensionFunctions=true` | **P6-2** (JDK 25 runtime), or earlier if the runtime moves first. Set the property in uPortal-start's `setenv` and also on uPortal's `TransformerFactory` in code, so embedded and test JVMs work too. Add a JDK 25 portal smoke test. Check data import/export of large entity files against the new limits (not tested here) | **Critical** (portal down); trivial fix |
| B6 | `catalina.sh stop` leaves the JVM running once requests have been served. The first stop, before any traffic, exited cleanly | MOD-I53 reproduced on Tomcat 11: the non-daemon `pool-20-thread-1` is held in `DestroyJavaVM` (`logs/runs/run2-hang-jstack.txt`) | uPortal code | `stop 45 -force` | MOD-I53 fix (shut the executor down on context close) | Low |
| B7 | `IllegalStateException: Cannot create a session after the response has been committed` from `PortalEventFactoryImpl.getPortalEventSessionId` during `emergency-alert` resource requests: 3 per suite run on 10.1 and on 11, 0 in one 8.5 run | The analytics event is published after the resource response was flushed, with `getSession(true)`. It looks timing-related, around login session replacement. Not confirmed as a Tomcat 10+ behaviour change | uPortal code (possibly a Tomcat 10+ change) | none. The specs don't see it; the event is lost | **P6:** `getSession(false)` or publish before the commit. Re-check on Tomcat 10.1 at the P6 checkpoint | Low |
| B8 | Latent removed-API references that no spec exercises: `encodeUrl`/`encodeRedirectUrl`/`setStatus(int,String)`/`isRequestedSessionIdFromUrl`/`getRealPath` in uPortal's `GuardingHttpServletRequest/Response` (uPortal-rendering) and `AbstractHttpServletResponseWrapper` (uPortal-utils-core), spring-security-web 3.1/4.2 `OnCommittedResponseWrapper`, spring-web `ContentCachingResponseWrapper`, esapi, jackrabbit-webdav and java-support. Also spring-session 1.3.5 `HttpSessionContext` and spring-webmvc `EvalTag` (`<spring:eval>`, which no bundled JSP uses) | Servlet 6.0 / Pages 4.0 removals | Mixed (uPortal, Spring 3.x/4.x, libs) | none needed | **P6-1:** the source migration deletes uPortal's pass-through overrides (the compiler forces it). The **P5-5** scanner checks the WARs. It fails on class-level hits and warns on member-level ones. Forbid `<spring:eval>` in bundled JSPs until Spring 5+ | Medium |
| B9 | uPortal's direct `HttpServletRequest`/`Response` implementations (`PortalHttpServletRequestWrapper`, `PortletHttpServletRequestWrapper`, `PortletHttpServletResponseWrapper`, `PortletResourceHttpServletResponseWrapper`, `PortletMimeHttpServletResponseWrapper`) lack the new abstract methods `getRequestId`, `getProtocolRequestId`, `getServletConnection` and `sendRedirect(String,int,boolean)` | Compiled against Servlet 3.x | uPortal code | none (never invoked during the specs) | **P6-1:** compiling against Servlet 6.x forces delegating implementations; caught automatically | Low (`AbstractMethodError` if a filter or valve calls them) |
| B10 | JDK 17+ strong encapsulation: without Tomcat's built-in opens, uPortal, Announcements, NewsReader, Calendar and NotificationPortlet fail with `InaccessibleObjectException … ClassLoader.defineClass`, and CAS fails on `ArrayList.hashCodeRange` | Spring 4.3's repackaged cglib and Hibernate 4.3's javassist need `java.lang`. CAS 3.6/Spring 3.1 bean introspection needs `java.util` | Spring 4.3 / Hibernate 4.3 / Spring 3.1 on a new JDK | none, because Tomcat 10.1/11's `catalina.sh` already adds both | **P4-2:** document it. Non-Tomcat JVMs (dataInit, Gradle test JVMs on JDK 21/25) need `--add-opens java.base/java.lang=ALL-UNNAMED` | Low under Tomcat; Medium for tooling JVMs |
| B11 | JDK 25 warns about terminally deprecated `sun.misc.Unsafe` memory-access calls (JEP 471/498), from guava 32.0.0-**android** (uPortal, about 65k calls), lucene-core 8.11.4 `MMapDirectory.invokeCleaner`, xstream 1.4.21 and classgraph 4.8.173 (resource-server) | These will be denied by default in a future JDK | Libraries | none (warn only); found with `--sun-misc-unsafe-memory-access=debug` | Lucene 10 (P5-3), a Guava bump to the jre flavour (VarHandles), and xstream; add a P7-4 JDK 27 canary with `=deny` | Low today |
| B12 | `WARNING … failed to set property [disableURLRewriting]` (FeedbackPortlet `context.xml`) | Context attribute removed in Tomcat 10 | Config | none | **P6-2:** drop it and rely on `<tracking-mode>COOKIE</tracking-mode>` | Low |
| B13 | The scanner reports 116 unresolvable intra-WAR references in Calendar, ResourceServingWebapp and basiclti (75 in NewsReader, 24 in SimpleContent) | Two JSTL implementations in one WAR (`jstl-1.2.jar` bundles the GlassFish impl, alongside `standard-1.1.2.jar`), plus similar duplicate-version jars. This predates the spike | Portlet WAR packaging | removed by F1 | **P5:** dependency hygiene in the portlet builds | Low |
| B14 | uPortal-start's `server.xml` uses `compressableMimeType`, but Tomcat 10.1/11 only have `setCompressibleMimeType` | Attribute renamed (old spelling gone) | Config | used `compressibleMimeType` | **P6-2** `server.xml` template | Low (compression MIME list silently reverts) |

**Concerns that did not materialise**, with their evidence:
- `crossContext` and `sessionCookiePath="/"` work unchanged on 10.1 and 11.
- Pluto 2.1.0-M3 runs after bytecode transformation, both in uPortal and in shared/lib.
- Spring Portlet MVC 4.3 runs, and so do Spring 3.x and PortletMVC4Spring 5.2.0 in SimpleContent.
- Spring Web Flow 2.4.8 portlet admin flows work: the 3 new `ux/admin` specs pass on Tomcat 11, JDK 21 and 25.
- Hibernate 4.3 with the renamed `jakarta.persistence` 2.1 runs.
- Spring 4.3, Hibernate 4.3 and Spring 3.x run on **JDK 25**, with no failures beyond B5.
- No extra `--add-opens` are needed.
- **CAS 3.6 runs on Jakarta Tomcat** once transformed. On 10.1 it doesn't need any fixup, and on 11 it needs only F3. That contradicts MOD-I36 as worded.
- The Tomcat 11 risk-register item, "Hybrid portlet API / in-tree Pluto fails under Tomcat 11 dispatch or wrapping rules", did not happen. Dispatch and wrapping are fine. The real Tomcat 11 risk is JSP-level (B3/B4).

### Path to a working portal (Tomcat 11, JDK 21)
1. **First start (no fixups):** 173 s. Everything deployed except NotificationPortlet (B2). `/uPortal/` returned 302 → guest page with 14 portlets. JSPs using `<c:set>` failed (B3).
2. **After F1 + F2:** 222 s, every context up. curl smoke clean except Feedback (B4). Playwright gave **104/120**:
   - CAS/jsp.el (B4): 7 portlet-list tests + uportal-auth.
   - Feedback (B4): 3.
   - Sandbox: 4.
   - One work-in-progress admin spec from uPortal-start's working tree.
3. **After F3:** 116/120, and then the pinned baseline set gave **116/119 twice**.

## (c) Removed-Servlet-API scan (prototype of the P5-5 scanner)

### The script
- **Script:** `scripts/JakartaLinkScan.java.txt` (single file, run with **JDK 25**, uses the `java.lang.classfile` API, no ASM), plus the wrapper `scripts/scan-removed-api.sh <tomcat lib> <shared-lib> <webapps>`.
- **Grep was not enough.** A grep for method names finds the simple cases but misses most of what actually broke. So the scanner is a small **linkage checker**:
  - It indexes every class in each webapp (`WEB-INF/classes` and every jar), the transformed `shared/lib`, the target Tomcat's whole `lib/` (spec APIs and also the Tomcat internals webapps can see), and, lazily, the JDK itself through `jrt:/`.
  - It resolves every `Fieldref`, `Methodref`, `InterfaceMethodref` and `Class` constant by JVM rules, with exact descriptors and walking the class hierarchy. That catches `super.encodeUrl(..)` in a webapp subclass.
  - The resolution is limited to references that touch `jakarta.servlet|el|websocket|annotation|security.auth.message`, `javax.portlet`, `org.apache.pluto.container` or the Tomcat internals (`org.apache.catalina|coyote|tomcat|jasper|juli|naming`).
  - Any reference that does not resolve is a hit. It is labelled **REMOVED** when it does resolve against Tomcat 8.5's API with javax renamed to jakarta.
  - It also reports:
    - concrete classes that lack methods which are abstract in Tomcat 11 but were not in 8.5 (`AbstractMethodError` risk);
    - leftover `javax.servlet/el/websocket` strings;
    - and a table of every `jakarta.*` package referenced, marked by whether Tomcat or the WAR provides it.
- **The removed-API list** is computed by diffing the APIs rather than typed in by hand (`logs/api-diff.txt`). Tomcat 8.5's spec jars, renamed, against Tomcat 11's give:
  - `SingleThreadModel`, `HttpSessionContext`, `HttpUtils`.
  - `ServletContext.getServlet/getServlets/getServletNames/log(Exception,String)`, `ServletRequest(Wrapper).getRealPath`.
  - `UnavailableException(Servlet,…)`/`getServlet`, `HttpServletRequest(Wrapper).isRequestedSessionIdFromUrl`, `HttpServletResponse(Wrapper).encodeUrl/encodeRedirectUrl/setStatus(int,String)`.
  - `HttpSession.getSessionContext/getValue/getValueNames/putValue/removeValue`.
  - The whole `jakarta.servlet.jsp.el.*` package (6 classes), with `JspContext.getExpressionEvaluator/getVariableResolver`, `JspException.getRootCause` and `BodyTag.EVAL_BODY_TAG`.
  - `ELResolver.getFeatureDescriptors` (and its overrides), `ELResolver.TYPE`/`RESOLVABLE_AT_DESIGN_TIME`, `MethodExpression.isParmetersProvided`, `jakarta.annotation.ManagedBean`, and the JASPIC permission constants.
  - Against Tomcat 10.1, the `jsp.el` package, `JspContext.getExpressionEvaluator/getVariableResolver`, `JspException.getRootCause`, `BodyTag.EVAL_BODY_TAG`, `ELResolver.getFeatureDescriptors` and `ManagedBean` are still present. The Servlet 6.0 removals and `MethodExpression.isParmetersProvided` are already gone.
- **Raw results:** `logs/scan-tomcat11.txt` and `logs/scan-tomcat10.txt` (about 2000 lines each). Matrices: `logs/scan-matrix-t11.md` and `logs/scan-matrix-t10.md` (`scripts/scan-matrix.py`).

### Hits per webapp
Counts are distinct removed members or classes referenced. The 10.1 counts are in brackets.

| webapp | removed-API refs T11 \[T10.1] | jars carrying them on Tomcat 11 |
|---|---|---|
| uPortal | 18 \[11] | spring-security-web 4.2.20, spring-session 1.3.5, spring-web/webmvc 4.3.30, standard 1.1.2, **uPortal-rendering**, **uPortal-utils-core** |
| Announcements | 12 \[1] | freemarker 2.3.31, spring-test 4.3.30 (a test jar in a production WAR), spring-web/webmvc 4.3.30, standard 1.1.2 |
| BookmarksPortlet | 8 \[1] | spring-web/webmvc 4.3.30, standard 1.1.2 |
| CalendarPortlet | 17 \[6] | freemarker 2.3.8, java-support 7.1.1, spring-test, spring-web/webmvc 4.3.30, standard 1.1.2 |
| FeedbackPortlet | 8 \[1] | **spring-web/webmvc 3.2.18**, standard 1.1.2 |
| NewsReaderPortlet | 10 \[1] | spring-test, spring-web/webmvc 4.3.30 |
| NotificationPortlet | 28 \[20] | **spring-boot / -autoconfigure 1.5.22** (Tomcat internals), spring-security-web 4.2.13, spring-web/webmvc 4.3.25 |
| ResourceServingWebapp | 8 \[1] | **spring-web/webmvc 3.1.4**, standard 1.1.2 |
| SimpleContentPortlet | 9 \[1] | freemarker 2.3.31, spring-web/webmvc 5.3.39, standard 1.1.2 |
| WebProxyPortlet | 21 \[10] | spring-security-web 4.2.20, spring-test, spring-web/webmvc 4.3.30, standard 1.1.2, uPortal-rendering/utils-core 5.17.3 |
| basiclti-portlet | 5 \[0] | standard 1.1.2 |
| cas (3.6) | 12 \[5] | esapi 2.0GA, **spring-security-web 3.1.0, spring-web/webmvc 3.1.1**, standard 1.1.2 |
| cas-proxy-test-portlet | 5 \[0] | spring-webmvc 4.2.5, standard 1.1.2 |
| esup-filemanager | 12 \[5] | jackrabbit-webdav 1.5.2, **spring-web/webmvc 3.1.1**, standard 1.1.2 |
| pluto-testsuite | 5 \[0] | standard 1.1.2 |
| resource-server | 3 \[0] | spring-webmvc 5.3.31 |

### Removed member → jars (Tomcat 11; the number is how many webapps include it)
| removed API | jars |
|---|---|
| `jakarta.servlet.jsp.el.{ExpressionEvaluator,VariableResolver,ELException,Expression}` (classes) | standard-1.1.2 (13), spring-webmvc (EvalTag, 14), spring-web 3.x `ExpressionEvaluationUtils` (4), freemarker (3), spring-test (4) |
| `PageContext.getExpressionEvaluator/getVariableResolver` | standard (12), spring-web 3.x (4), spring-webmvc (13) |
| `org.apache.coyote.http11.AbstractHttp11JsseProtocol` (class) + 17 members of it, `AbstractProtocol.setBacklog`, `WebappLoader(ClassLoader)`, `Connector.setAttribute`, `setCompressableMimeType` | spring-boot / spring-boot-autoconfigure 1.5.22 (NotificationPortlet) |
| `HttpSessionContext` (class) | spring-session 1.3.5 (uPortal) |
| `HttpServletResponseWrapper.setStatus(int,String)` | spring-web (12), uPortal-rendering (2) |
| `HttpServletResponse(Wrapper).encodeUrl/encodeRedirectUrl` | uPortal-utils-core, uPortal-rendering, spring-security-web 3.1/4.2 (`OnCommittedResponseWrapper`, 3), java-support, jackrabbit-webdav |
| `HttpServletRequest(Wrapper).isRequestedSessionIdFromUrl/getRealPath` | uPortal-rendering, esapi, jackrabbit-webdav, java-support |

### Concrete classes lacking new Servlet 6 abstract methods
- uPortal's own wrappers (B9).
- `uPortal-url`/`uPortal-utils-core` 5.17.3 inside WebProxyPortlet.
- spring-test mocks (in 4 WARs).
- spring-security-web 3.1 (CAS), `java-support` 7.1.1 (Calendar), jackrabbit-webdav (esup-filemanager), portlet-servlet-adapter 3.1.0 (SimpleContent).

### How well the scan predicted runtime
- **Class-level hits predicted every hard failure:**
  - On Tomcat 11, class-level REMOVED hits exist in exactly the jars that broke at runtime: JSTL 1.1.2 (B3), Spring 3.x `ExpressionEvaluationUtils` (B4) and Spring Boot 1.5 (B2). They break at verification or reflective introspection time, so any JSP or bean that touches the class fails.
  - On 10.1 the only class-level hit is spring-session's `HttpSessionContext`, which only matters if `getSessionContext()` is called. That matches 10.1, which didn't need any fixups.
- **Member-level hits stayed latent:** 0 of them failed during the 119 specs, the 94-portlet crawl or the Pluto testsuite.
- **Recommendation for P5-5:** fail the overlay build on class-level hits, and warn (with an allow-list) on member-level hits.
- **Not covered:** JSPs. They compile at runtime, so a runtime crawl is still needed. Reflection by string name is not covered either.

## (d) Playwright results vs baseline

Baseline test set `2a519ad` (19 specs, 119 tests), `retries: 2`. "1f" means passed on retry. All runs used the same HSQLDB copy, without re-initialising it between runs.

| spec | baseline doc (T8.5/J11) | T8.5/J11 spike ref | T11/J21 r1 | T11/J21 r2 | T11/J25 r1 | T11/J25 r2 | T11/J25 min-opens | T10.1/J21 r1 | T10.1/J21 r2 |
|---|---|---|---|---|---|---|---|---|---|
| api/analytics | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 |
| api/portlet-list | 31/31 | 31/31 | 31/31 | 31/31 | 31/31 (1f) | 31/31 | 31/31 | 31/31 (1f) | 31/31 |
| api/search-v5-0 | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 |
| ux/auth/uportal-auth | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 |
| ux/portlets/announcements | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 |
| ux/portlets/bookmarks | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 |
| ux/portlets/calendar | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 |
| ux/portlets/feedback | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 |
| ux/portlets/news-reader | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 |
| ux/portlets/notifications | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 | 3/3 |
| ux/portlets/simple-content | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 (1f) | 5/5 |
| ux/portlets/web-proxy | 2/3 | 2/3 | 2/3 | 2/3 | 2/3 | 2/3 | 2/3 | 2/3 | 2/3 |
| ux/skin/custom-skin | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 | 1/1 |
| ux/smoke/favorites | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 |
| ux/smoke/guest-page | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 |
| ux/smoke/per-role-pages | 19/19 | 19/19 | 19/19 | 19/19 | 19/19 | 19/19 | 19/19 | 19/19 | 19/19 |
| ux/smoke/portlet-options | 10/10 | 10/10 | 10/10 | 10/10 | 10/10 | 10/10 | 10/10 | 10/10 | 10/10 |
| ux/smoke/visual-resource-server | 4/6 | 4/6 | 4/6 | 4/6 | 4/6 | 4/6 | 4/6 | 4/6 | 4/6 |
| ux/smoke/web-components | 6/6 | 6/6 | 6/6 | 6/6 | 6/6 | 6/6 | 6/6 | 6/6 | 6/6 |
| **total** | **116/119** | **116/119** | **116/119** | **116/119** | **116/119** (1 flaky) | **116/119** | **116/119** | **116/119** (2 flaky) | **116/119** |

**The configurations:**
- **T11** = Tomcat 11.0.26 with fixups F1 to F3.
- **J25** adds `-Djdk.xml.enableExtensionFunctions=true` (B5).
- **min-opens** = all six Tomcat default `--add-opens` removed and only `java.lang` + `java.util` given (section e).
- **T10.1** = Tomcat 10.1.60 with **no fixups at all**.

**The 3 failures** are the same sandbox-blocked tests in every column:
- web-proxy "proxies http://example.com/": `UnknownHostException: example.com`.
- CKEditor 4 and uportal-links: `ERR_TUNNEL_CONNECTION_FAILED` to `cke4.ckeditor.com`, a proxy policy denial. The CKEditor console error message is identical to the baseline's.

**The flakes:**
- portlet-list "PUT confirm noop resave" is the known `updatedOn` one-second-boundary race. The P0 workstream fixed it upstream in uPortal-start `324f91a`.
- simple-content "config mode loads CKEditor" hit a 10 s attach timeout on a loaded machine.

Neither is Jakarta-related.

**Beyond the baseline set:**
- **uPortal-start HEAD `474c66f` (22 specs, 122 tests) on T11/JDK25:** **118/122**.
  - The 4 failures are sandbox-blocked: web-proxy ×2, because MOD-I54's tightened spec now also fails properly offline, and CKEditor ×2.
  - The 3 new **admin Web Flow specs, for portlet manager registration, the groups manager and the permissions manager, pass on Tomcat 11 with both JDK 21 and JDK 25**.
- **Narrow-profile WARs** (Bookmarks, Announcements, SimpleContent, Calendar, NewsReader, Feedback, all transformed by `NarrowMigrate.java.txt`) on T11/J21: `ux/portlets` + `ux/smoke` = 78/81, with the same 3 sandbox failures.
- **Crawl of all 94 registered portlets**, maximized as admin (`scripts/crawl.sh`): 94 × HTTP 200 and 0 error cards. The only exception is the `error` test portlet, which exists to render one.
  - The result is identical on T8.5 ref, T11/J21, T11/J25 min-opens, T11 narrow and T10.1.
  - The portlet markup doesn't contain any exception text.
- **Pluto 2.0 testsuite** (test-portlet-1/2, 32 tests driven by POSTing each action; `scripts/pluto-testsuite.sh`):
  - **12 PASSED, 10 FAILED, 8 MANUAL, 2 WARNING; 144 checks pass and 16 fail, identically on Tomcat 8.5 (javax, untransformed), Tomcat 10.1 and Tomcat 11**, per test and per check.
  - The 16 failing checks already fail on 5.17: `checkInvalidParameters`, `checkEnumerateAttributes`, `checkPreferenceValidator`, `checkGetNoValuePreference` ×2, `checkPortalInfo`, `checkContextServerInfo` and `checkContextMajorVersion`, per portlet.
  - The bytecode transform and the hybrid API don't add any Portlet 2.0 conformance regressions.
- **Hybrid-API Cookie signature, exercised directly.** I added a probe to the deployed pluto-testsuite JSP. Then:
  - `javax.portlet.RenderRequest.getCookies()` (uPortal's `ExtendedRenderRequestImpl`) and Pluto's `HttpServletPortletRequestWrapper.getCookies()` both returned `jakarta.servlet.http.Cookie[]`, containing `JSESSIONID` and a test cookie set by the client.
  - That was after a cross-context dispatch from `/uPortal` into `/pluto-testsuite`. The probe was then removed.
  - Pluto, Spring `PortletUtils` (3.1, 3.2, 4.2, 4.3), PortletMVC4Spring 5.2.0 and portlet-servlet-adapter 3.1.0 all link against the hybrid `getCookies()[Ljakarta/servlet/http/Cookie;`.
- **Log comparison:** the ERROR classes in `portal.log` after a full suite run match between 8.5 and 11: broken-pipe aborts on `/api/portletList`, two Hibernate metamodel warnings, and the 409-test constraint failures. The one exception is B7. No `NoSuchMethodError`, `NoClassDefFoundError`, `AbstractMethodError` or `InaccessibleObjectException` appears in any log of the final configurations.
- **Startup times:** these vary a lot, because other agents shared the 4 CPUs.
  - T8.5/J11 ref: 103 s.
  - T11/J21: 117 to 222 s.
  - T11/J25: 60 to 67 s (217 s once under load).
  - T10.1/J21: 74 s.

## (e) Final `--add-opens` list

- **Tomcat 11.0.26 / 10.1.60 `catalina.sh` already adds:**
  - `--add-opens=java.base/java.lang=ALL-UNNAMED`
  - `--add-opens=java.base/java.lang.reflect=ALL-UNNAMED`
  - `--add-opens=java.base/java.io=ALL-UNNAMED`
  - `--add-opens=java.base/java.util=ALL-UNNAMED`
  - `--add-opens=java.base/java.util.concurrent=ALL-UNNAMED`
  - `--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED`
  - `--enable-native-access=ALL-UNNAMED`
- **Extra opens needed by uPortal plus all bundled WARs on JDK 21 and JDK 25: none.** Across all runs, no `InaccessibleObjectException` or "does not opens" message appeared.
- **Minimum the application really needs.** I proved this on JDK 25 by disabling all six Tomcat opens: uPortal, CAS, NewsReader, Announcements and NotificationPortlet then fail to start. With only these two re-added, the result is 116/119 and a clean crawl:
  ```
  --add-opens=java.base/java.lang=ALL-UNNAMED   # Spring 4.3 repackaged cglib + Hibernate 4.3 javassist: ClassLoader.defineClass
  --add-opens=java.base/java.util=ALL-UNNAMED   # CAS 3.6 / Spring 3.1 bean introspection (ArrayList.hashCodeRange); CAS only
  ```
  Keep Tomcat's other four, because Tomcat's own memory-leak protection uses them.
- **Non-Tomcat JVMs that boot uPortal's Spring/Hibernate context** (dataInit, Gradle test JVMs on JDK 21/25) need `java.base/java.lang`.
- **Other JVM flags this spike needed:**
  - `-Djdk.xml.enableExtensionFunctions=true` on JDK 25 (B5).
  - Optionally `--sun-misc-unsafe-memory-access=debug`, to inventory the Unsafe users listed in B11.

## (f) Verdict on "transform bundled WARs + hybrid portlet API": **works, with caveats**

### Evidence that it works
1. **The full picture held.** Bytecode-transformed uPortal and Pluto 2.1.0-M3 run through the shared-loader hybrid `javax.portlet` 2.0 API on Tomcat 10.1 and 11, on JDK 21 and 25. They dispatch cross-context into 12 transformed JSR-286 WARs running Spring Portlet MVC 3.1 to 4.3, PortletMVC4Spring 5.2 and Spring Web Flow 2.4 portlet flows.
2. **The results match 5.17 on Tomcat 8.5.** I compared four ways:
   - Playwright: 116/119, every spec identical.
   - The 94-portlet crawl.
   - Pluto testsuite: 144/160 checks, the same 16 failing.
   - The `portal.log` error classes.
3. **The hybrid API is tiny and mechanical.** It is the migration tool's output for `portlet-api_2.1.0_spec-1.0.jar`, where exactly 4 classes change. Its only servlet-typed signature (`Cookie`) works end to end across contexts.
4. **Tomcat 10.1 (the P6 checkpoint target) didn't need any fixups.** That leaves only the build-time exclude for a corrupt icu4j class.

### Caveats
- **Tomcat 11 needs three fixes,** all from Jakarta Pages 4.0 and Tomcat-internal removals, and all fixable cheaply before the switch:
  - JSTL 1.1.2 → 1.2.5 (B3);
  - retire the Spring 3.x WARs (B4), namely Feedback, CAS 3.6, ResourceServingWebapp and esup-filemanager, with the F3 `jsp.el` jar in the meantime;
  - a Spring Boot 1.5 auto-config exclude for NotificationPortlet (B2).
- **JDK 25 needs the XSLT extension-function property (B5).**
- **Latent removed-Servlet-API calls remain** in EOL Spring 3.x/4.x and a few libraries (B8). None was reached by the specs, the crawl or the testsuite, but the scanner has to catch them.
- **Deploy-time conversion (`legacyAppBase`) is not viable.** One corrupt class aborts a whole WAR (B1), so the transform must be the deterministic build-time step the plan already proposes.
- **Untested in this spike:**
  - It does not prove the *source-level* in-tree Pluto, or uPortal compiled against Servlet 6 / Spring 6 to 7. Here uPortal itself was transformed bytecode on Spring 4.3.
  - Clustering, large imports under the JDK 25 XML limits, and the 8 Pluto *manual* tests were not exercised.

## (g) Surprises that should change the plan

1. **The step that breaks portlets is Tomcat 11, not javax→jakarta.**
   - Every WAR-breaking blocker (B2, B3, B4) comes from Pages 4.0 / Tomcat 11 removals. Tomcat 10.1 ran the transformed WARs unmodified.
   - So the **P6 checkpoint on Tomcat 10.1 is low-risk for portlets**, and **P7-1 (Tomcat 11) is where portlets break**.
   - **Change:** add required pre-steps on javax to P5. They are the JSTL 1.2.5 upgrade in uPortal and every bundled portlet, retirement or upgrade of every Spring 3.x WAR (Feedback, CAS 3.6, ResourceServingWebapp, esup-filemanager), and the NotificationPortlet Boot fix. Then P7-1 is not also a portlet migration.
   - **Change:** reword the risk-register row "Hybrid portlet API fails under Tomcat 11 dispatch or wrapping rules". Dispatch and wrapping are fine. The real risk is JSP-time verification of EOL tag libraries.
2. **JDK 25 turns off XSLT extension functions by default, and uPortal's whole theme depends on them (B5).**
   - The research didn't cover this. It is a P6-2 (JDK 25 runtime) blocker that a one-line property fixes. It should also be fixed in code, and tested in the JDK 25 CI leg.
   - The same JDK change tightens XML entity and depth limits, which could affect large data imports.
3. **The migration tool's default TOMCAT profile is wider than the report assumes.**
   - It also renames `javax.persistence`, `javax.transaction`, `javax.ejb`, `javax.mail` and the Common Annotations classes, and it renames entry paths. Each WAR ends up with private "jakarta" copies of those APIs. That works, because the rename is consistent within the WAR.
   - The narrow rule set the report proposes cannot be expressed on the CLI. It needs about 20 lines of Java against the tool's `EESpecProfile` (`scripts/NarrowMigrate.java.txt`). Both rule sets passed.
   - **Change:** P5-5 should pick one explicitly. I recommend the narrow profile: fewer rewritten classes, and `javax.persistence.*` property keys don't change. The pipeline also needs a per-WAR `-exclude` list and must fail the build on a non-zero exit.
4. **The P5-5 "removed-Servlet-API scanner" has to be a linkage checker rather than a list of method names.**
   - The failures came from removed *classes* that appear in exception tables and method signatures, hit through Jasper's tag introspection. They also came from Tomcat-internal classes reached by Spring Boot, and from `super.` calls in webapp subclasses.
   - The prototype needs JDK class models (`jrt:/`) to avoid false negatives, and the whole Tomcat `lib/` as the container layer.
   - Severity tiering works: class-level hits matched every hard failure, and member-level hits were all latent.
5. **CAS 3.6 does run on Jakarta Tomcat once transformed** (MOD-I36 says it cannot). It needs nothing on 10.1 and the F3 jar with the jsp.el classes on 11. So CAS 3.6 can remain a stop-gap through P6, and the CAS login and uportal-auth specs keep passing. MOD-Q3, making CAS opt-in or external, is still right because CAS 3.6 is EOL. It is no longer a hard blocker for P6-2.
6. **No `--add-opens` work is needed for Tomcat runtimes, and JDK 25 already runs the javax-era stack.**
   - Tomcat 10.1/11's built-in opens cover uPortal's real needs (`java.lang`, plus `java.util` for CAS).
   - Spring 4.3, Hibernate 4.3 and Spring 3.x ran every exercised path on JDK 25 once B5 was set. So the JDK 25 *runtime* could be decoupled from P6, while staying "unsupported/informational" for Spring 4.3/5.3.
   - Tooling JVMs still need `java.lang` opens (P4-2).
7. **"Rebuild the API jar deliberately" overstates the hybrid API work.** It is exactly the tool's output, with 4 classes changed. P5-5's "hybrid portlet API module" can be a migrated artifact with a checksum test. A forked source tree isn't needed until P6-1 recompiles Pluto from source.
8. **Smaller items for the plan:**
   - uPortal-start's `server.xml` `compressableMimeType` (B14) and Feedback's `disableURLRewriting` (B12) are silently ignored on 10.1/11, so the P6-2 overlays need them fixed.
   - Several WARs bundle `spring-test` (mock classes) and two JSTL implementations (B13). That is P5 dependency-hygiene work.
   - MOD-I53's shutdown hang reproduces on Tomcat 11.
9. **The Playwright baseline is moving.** The P0 workstream changed specs during this spike (web-proxy now fails offline as it should, and there are new admin specs). Checkpoint comparisons need a pinned spec commit, as done here with `git archive 2a519ad`.

## Files
- Scripts, in `spike/scripts/`:
  - `transform-all.sh`, `NarrowMigrate.java.txt`, `deploy.sh`, `fixups.sh`, `hsql.sh`, `tc.sh`.
  - `JakartaLinkScan.java.txt` (scanner, census and signature search), `scan-removed-api.sh`, `scan-matrix.py`, `census-diff.py`, `FindRefs.java.txt`.
  - `smoke.sh`, `crawl.sh`, `pluto-testsuite.sh`, `pluto-parse.py`, `pw-run.sh`, `logsum.py`.
- Logs, in `spike/logs/`:
  - `transform/` (per-webapp migration logs) and `api-diff.txt`.
  - `scan-tomcat11.txt`, `scan-tomcat10.txt`, `scan-matrix-t{11,10}.md` and `census-*.txt`.
  - `pw-<run>.json` / `-summary.txt` and `crawl-*.txt`.
  - `pluto-testsuite-t{85,10,11}.txt` (+ `pluto-t85-pages/`, `pluto-t10-pages/`).
  - `runs/<config>/` (full Tomcat, portal and portlet logs per configuration) and `runs/run2-hang-jstack.txt`.
- Playwright traces and screenshots: `$SP/spike-pw-<run>-results/`.

> The Java single-file programs in `scripts/` are stored as `*.java.txt` so the
> repository's Java format check skips them (they use JDK 25 syntax). Rename them back
> to `*.java` to run them with `java <file>.java`.
