# Build and runtime toolchain upgrade path for uPortal / uPortal-start (Java, Gradle, Tomcat, test and infra libraries), as of 10 October 2026

Research notes (as of 2026-10-10). Method: version facts come from primary registries (Maven Central `maven-metadata.xml`, Gradle Plugin Portal `m2` metadata, `services.gradle.org`, npm registry, and Maven Central jar/pom inspection) and from upstream docs read as source on raw.githubusercontent.com. docs.gradle.org, tomcat.apache.org, openjdk.org, adoptium.net and similar hosts could not be fetched from this sandbox (proxy 403 / DNS failure). Their contents were read either from the upstream source repo (Gradle docs `.adoc` and Tomcat site `xdocs`) or from web-search snippets. Each item says which. "Current" in uPortal means the uPortal `master` values in [uPortal gradle.properties](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/gradle.properties) and [uPortal build.gradle](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/build.gradle), plus the local uPortal-start checkout (`/home/user/uPortal-start`).

## Q1. Java: latest GA and LTS (Oct 2026), and vendor support windows

### Takeaway
As of October 2026 the latest GA is **JDK 27** (non-LTS, GA mid-September 2026) and the latest LTS is **JDK 25** (GA 16 September 2025). The next LTS is JDK 29 (September 2027). JDK 25 is the right target. Free vendor support runs to at least 2031 to 2033 for 25. Java 11, the current baseline, ends free community support at Temurin in **October 2027** (at Corretto, January 2032).

### Cited Findings
- JDK 25 GA was 16 September 2025. It is an LTS release, and Oracle's roadmap names **Java 29 (September 2027)** as the next planned LTS. Oracle JDK 25 has been under a free-use license for all users since September 2025. ([InfoQ "Java 25 so far"](https://www.infoq.com/news/2025/08/java-25-so-far), [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html) (via search snippet; the page's table was cut off, so Oracle Premier/Extended end dates for 25 were **not** confirmed))
- JDK 26 GA was 17 March 2026, with 10 JEPs. It is a short-term release with six months of updates. ([Oracle press release, 2026-03-17](https://www.oracle.com/news/announcement/oracle-releases-java-26-2026-03-17/))
- JDK 26 JEPs include **500 (Prepare to Make Final Mean Final)**, **504 (Remove the Applet API)**, 516 (AOT Object Caching with Any GC), 517 (HTTP/3 for HTTP Client), 522 (G1 throughput) and 524 (PEM, 2nd preview). ([AspectJ 1.9.26 release notes (lists the JDK 26 JEPs)](https://raw.githubusercontent.com/eclipse-aspectj/aspectj/master/docs/release/README-1.9.26.adoc), [Inside Java podcast 51](https://inside.java/2026/03/12/podcast-051/))
- JDK 27 is out. Its GA was scheduled for 14 September 2026, and later reports and Oracle's download page say 15 September 2026, so sources differ by one day. It is non-LTS and gets free Oracle updates until March 2027, when JDK 28 supersedes it. ([Oracle Java downloads page ("Java 27 Livestream on September 15, 2026")](https://www.oracle.com/java/technologies/downloads/), [InfoQ Java news roundup Sep 14 2026](https://www.infoq.com/news/2026/09/java-news-roundup-sep14-2026/), [InfoQ "Java 27 released"](https://infoq.com/news/2026/09/java27-released))
- JDK 27 has nine JEPs: 523 G1 default in all environments, 527 post-quantum hybrid key exchange for TLS 1.3, 531 Lazy Constants (3rd preview), 532 primitive patterns (5th preview), 533 Structured Concurrency (7th preview), **534 Compact Object Headers by default**, 536 JFR in-process redaction, 537 Vector API (<!-- vale ai-tells.GrowthMetaphors = NO --><!-- JDK release-stage name (JEP 11 incubator modules) -->12th incubator<!-- vale ai-tells.GrowthMetaphors = YES -->), 538 PEM (3rd preview). ([InfoQ "Java 27 released"](https://infoq.com/news/2026/09/java27-released), [ADTmag, feature set locked](https://adtmag.com/articles/2026/06/24/feature-set-locked-as-oracle-enters-stabilization-phase.aspx))
- **Temurin (Adoptium)** commits to at least four years per LTS. Its support page says Java 11 and Java 17 run to at least **Oct 2027** and Java 21 to at least **Dec 2029**. Java 8 runs to at least Nov 2026. A July 2026 third-party article says Temurin 8 runs to Dec 2030, which conflicts with that page. The page snapshot may be stale. No Temurin end date for Java 25 was found. ([Adoptium Temurin Support](https://adoptium.net/support) (search snippet), [HeroDevs Java EOL by vendor](https://www.herodevs.com/blog-posts/java-8-11-17-eol-dates-by-openjdk-vendor-temurin-to-red-hat))
- **Amazon Corretto** EOL months are 25 → Oct 2032, 21 → Oct 2030, 17 → Oct 2029, **11 → Jan 2032**, 8 → Dec 2030, 26 (feature release) → Oct 2026. ([Corretto FAQ](https://aws.amazon.com/corretto/faqs/), [endoflife.date/amazon-corretto](https://endoflife.date/amazon-corretto))
- **Azul Zulu** supports Java 25 LTS until **September 2033**, and its policy is at least eight years of production support plus two extended years per LTS. No current Azul Java 11 date was confirmed. ([Azul Zulu 25 GA release notes (PDF)](https://docs.azul.com/core/pdfs/25-ga/azul-zulu-ca-release-notes-25-ga-rev1.0.pdf), [Azul support roadmap (Oct 2024 archive)](https://www.azul.com/products/azul-java-support-roadmap-20241001/))
- The uPortal and uPortal-start CI matrices still use `java-distribution: adopt-hotspot` with Java 11 on Windows and macOS via `actions/setup-java@v4`. ([uPortal CI.yml](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/.github/workflows/CI.yml), [uPortal-start CI.yml](https://raw.githubusercontent.com/uPortal-Project/uPortal-start/master/.github/workflows/CI.yml))
- `actions/setup-java` **v6 removed the legacy AdoptOpenJDK distributions** ("Use `temurin` instead of `adopt` or `adopt-hotspot`"), and v1 to v4 are deprecated. ([actions/setup-java README](https://raw.githubusercontent.com/actions/setup-java/main/README.md))

### Inferences
- Target **JDK 25 LTS** for both build and runtime. Optionally test on 27 in CI as a canary for JDK 28/29. Do not target 26 or 27 for production, because they get only six months of updates.
- The CI matrix must switch from `adopt-hotspot` to `temurin` (plus `zulu`/`corretto` if wanted) before moving to `setup-java@v6`. Otherwise CI breaks as soon as Dependabot or Renovate bumps the action.
- Java 11 still has community builds until about Oct 2027 (Temurin), so a staged approach (Gradle upgrade first on 11 or 17, then the runtime bump) does not face a hard deadline. Tomcat 8.5 is already EOL, though (Q6).

### Gaps
- Oracle's Premier and Extended support end dates for JDK 25 could not be read. The roadmap page snippet was cut off.
- No Temurin end date for 25 was found. The adoptium.net page was not fetchable.
- The exact JDK 27 GA day (14 vs 15 September 2026) is inconsistent across sources.

## Q2. Java per-hop gotchas 11 → 17 → 21 → 25 (→ 26/27)

### Takeaway
The breaking hop for uPortal's 2015-era stack (Spring 4.3, Hibernate 4.3, cglib/Javassist, XStream, Groovy 3, Ehcache 2) is **11 → 17**. JEP 403 makes illegal reflective access fatal, which forces `--add-opens` for old libraries. After that come quieter but real changes:
- JDK 18: UTF-8 becomes the default charset.
- JDK 20/23: CLDR and locale changes break date-format tests.
- JDK 21: warnings when an agent is loaded into a running JVM (Mockito/ByteBuddy need `-javaagent`).
- JDK 23: javac no longer runs annotation processors implicitly.
- JDK 24: the Security Manager is permanently disabled, and `sun.misc.Unsafe` memory methods start warning.
- JDK 25: the 32-bit x86 port is removed.
- JDK 26: final-field mutation warnings start, `Thread.stop` is removed, and Unsafe "deny" is planned.

### Cited Findings
- **Strong encapsulation.** JDK 16 denies illegal access by default (JEP 396), and JDK 17 strongly encapsulates JDK internals with no `--illegal-access` escape (JEP 403). Tools such as google-java-format need `--add-exports jdk.compiler/...=ALL-UNNAMED` on JDK 16+. ([google-java-format README (cites JEP 396)](https://raw.githubusercontent.com/google/google-java-format/master/README.md), [JEP 403 integration, core-libs-dev](https://mail.openjdk.org/pipermail/core-libs-dev/2021-May/078171.html), [JEP 403](https://openjdk.org/jeps/403))
- **XStream (uPortal pins 1.4.21).** "XStream fails since Java 17, because types in modules cannot be accessed from the unnamed module… You have to open all packages of the individual modules… with the option `--add-opens`, e.g. `--add-opens java.base/java.util=ALL-UNNAMED`." No JPMS support is planned soon ([XStream FAQ (source)](https://raw.githubusercontent.com/x-stream/xstream/master/xstream-distribution/src/content/faq.html), [x-stream.github.io/faq.html](https://x-stream.github.io/faq.html)). Maven Central shows **1.4.21 (Nov 2024) as the latest XStream**. ([xstream metadata](https://repo1.maven.org/maven2/com/thoughtworks/xstream/xstream/maven-metadata.xml))
- **Gradle test workers.** Since Gradle 7.5, Gradle no longer adds implicit `--add-opens java.base/java.util` and `java.base/java.lang` to test JVMs. Tests that relied on deep reflection now fail on JDK 16+ unless the build adds `jvmArgs`. (<!-- vale ai-tells.DoubleHyphen = NO --><!-- citation link text kept verbatim -->[Gradle upgrading_version_7 ("Removes implicit --add-opens for test workers")](https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_version_7.adoc)<!-- vale ai-tells.DoubleHyphen = YES -->)
- **Nashorn** was removed in JDK 15 (JEP 372). ([JEP 372](https://openjdk.org/jeps/372) (not fetched; well-known))
- **Security Manager.** It was deprecated for removal in 17 (JEP 411) and **permanently disabled in JDK 24 (JEP 486)**. It cannot be enabled at startup, and the API is kept only for migration ([JEP 486](https://openjdk.org/jeps/486), <!-- vale ai-tells.ColonUsage = NO --><!-- citation link text kept verbatim -->[Oracle JDK 25 docs: Security Manager permanently disabled](https://docs.oracle.com/en/java/javase/25/security/security-manager-is-permanently-disabled.html)<!-- vale ai-tells.ColonUsage = YES -->). Tomcat 11 removed SecurityManager support entirely. ([Tomcat 11 migration guide (source)](https://raw.githubusercontent.com/apache/tomcat-site/trunk/xdocs/migration-11.0.xml))
- **Dynamic agent loading (JEP 451, JDK 21).** Mockito: "Starting from Java 21, the JDK restricts the ability of libraries to attach a Java agent to their own JVM… the inline-mock-maker might not be able to function without an explicit setup… and the JVM will always display a warning." Mockito documents a Gradle `CommandLineArgumentProvider` that passes `-javaagent:<mockito-core.jar>` (from a non-transitive `mockitoAgent` configuration). `-XX:+EnableDynamicAgentLoading` is the non-standard fallback. Since Mockito 5.0 the inline mock maker is the default. ([Mockito javadoc §0.3 (source)](https://raw.githubusercontent.com/mockito/mockito/main/mockito-core/src/main/java/org/mockito/Mockito.java))
- **`sun.misc.Unsafe` memory-access methods.** JEP 471 deprecated them for removal in JDK 23. JEP 498 makes the first use warn at run time in JDK 24. The announced plan is **deny (throw) by default in JDK 26** (`--sun-misc-unsafe-memory-access=deny`) and removal after that. This is a plan statement. A JDK 26 release note confirming it was not found. ([JEP 471](https://openjdk.org/jeps/471), [JEP 498](https://openjdk.org/jeps/498), [Inside Java "Evolving default integrity"](https://inside.java/2025/01/03/evolving-default-integrity/), [JVM Advent "Java integrity by default"](https://www.javaadvent.com/2024/12/java-integrity-by-default.html))
  - ByteBuddy 1.18.9 (June 2026) disables use of Unsafe by default on Java 25+. 1.18.10 delayed the default change to Java 26. ([ByteBuddy release notes](https://raw.githubusercontent.com/raphw/byte-buddy/master/release-notes.md))
  - SpotBugs 4.10.0 added a detector for `sun.misc.Unsafe` / `jdk.internal.misc.Unsafe` use. ([SpotBugs CHANGELOG](https://raw.githubusercontent.com/spotbugs/spotbugs/master/CHANGELOG.md))
- **Final-field mutation (JEP 500, JDK 26).** Deep-reflective mutation of `final` fields now warns by default, as a step toward denying it. This affects serializers and DI frameworks that set finals reflectively. ([AspectJ 1.9.26 notes listing JEP 500](https://raw.githubusercontent.com/eclipse-aspectj/aspectj/master/docs/release/README-1.9.26.adoc), search summary citing JDK-8349536 (secondary))
- **`Thread.stop`.** JDK 20 re-specified it to always throw `UnsupportedOperationException`, and **JDK 26 removed it (JDK-8368226)**. Code calling it no longer compiles. ([JDK 26 release-notes draft (shipilev backports monitor)](https://builds.shipilev.net/backports-monitor/release-notes-26.html) (secondary; verify against the official JDK 26 notes))
- **UTF-8 by default** (JEP 400, JDK 18) and **deprecation of finalization for removal** (JEP 421, JDK 18). ([OpenJDK JEP index](https://openjdk.org/jeps) (lists 421 under 18), [JEP 400](https://openjdk.org/jeps/400) (JEP text not fetched))
- **CLDR and locale data.** JDK 20 adopted CLDR 42. The space before AM/PM in en-US time formats became U+202F (narrow no-break space), which breaks strict parsing and string-equality tests. `-Djava.locale.providers=COMPAT` was a JDK 20 to 22 workaround, but **COMPAT was removed in JDK 23**. JDK 23 added loose space matching only in lenient parsing mode. Spring recommends ISO formats. (<!-- vale ai-tells.ColonUsage = NO --><!-- citation link text kept verbatim -->[Spring Framework wiki: Date and Time Formatting with JDK 20+](https://github.com/spring-projects/spring-framework/wiki/Date-and-Time-Formatting-with-JDK-20-and-higher)<!-- vale ai-tells.ColonUsage = YES -->, [JDK-8324665 RFR](https://mail.openjdk.org/pipermail/i18n-dev/2024-February/008674.html))
- **Annotation processing (JDK 23, JDK-8321314).** javac no longer discovers processors on the classpath implicitly. Processing runs only with `-processor` / `--processor-path` / `--processor-module-path`, or with `-proc:only` / `-proc:full`. JDK 21 and 22 print a note when they detect implicit processing ([Oracle JDK 23 release notes](https://www.oracle.com/java/technologies/javase/23-relnote-issues.html), <!-- vale ai-tells.NounString = NO --><!-- citation link text kept verbatim -->[nipafx "JDK 23 changes default annotation processing policy"](https://nipafx.dev/annotation-processing-off/)<!-- vale ai-tells.NounString = YES -->). uPortal already wires Lombok and hibernate-jpamodelgen through the Gradle `annotationProcessor` configuration (explicit processor path). One example is `uPortal-utils-core`. ([uPortal-utils-core build.gradle](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/uPortal-utils/uPortal-utils-core/build.gradle))
- **32-bit.** The Windows 32-bit x86 port was removed in JDK 24 (JEP 479) and the remaining 32-bit x86 port in JDK 25 (JEP 503). ([JEP 503](https://openjdk.org/jeps/503) (not fetched; well-known; verify))
- **Virtual threads (JEP 491, JDK 24).** `synchronized` no longer pins virtual threads. This only matters if virtual threads are adopted. ([JEP 491](https://openjdk.org/jeps/491) (not fetched))
- **GC flags.** uPortal-start's `etc/tomcat/bin/setenv.bat` still probes for CMS flags (`-XX:+UseConcMarkSweepGC`, `-XX:+UseParNewGC`, `CMS*`) before defaulting to G1. CMS was removed in JDK 14 (JEP 363). A user-supplied CMS flag makes the JVM refuse to start. JDK 27 makes G1 the default in all environments (JEP 523). (local file `uPortal-start/etc/tomcat/bin/setenv.bat`, [JEP 363](https://openjdk.org/jeps/363) (not fetched), [InfoQ Java 27](https://infoq.com/news/2026/09/java27-released))

### Inferences
- Expect to maintain an explicit `--add-opens` list in Tomcat `setenv` and in Gradle `Test.jvmArgs`, at minimum: `java.base/java.lang`, `java.lang.reflect`, `java.util`, `java.util.concurrent`, `java.text`, `java.io`, `java.net`, `sun.nio.ch`, plus `java.xml`/`java.desktop` packages for XStream converters. Do this until Spring/Hibernate/cglib/XStream are upgraded. The list must be derived empirically by running uPortal on 17/21/25 with `--illegal-access` warnings gone. Prefer `jdk.module.illegalAccess`-style logging under JDK 16 (`--illegal-access=warn`) on 11 first to enumerate the call sites.
- Spring 4.3 (uPortal's current version) officially supports up to Java 8 (with "best effort" on later JDKs). Its embedded ASM/cglib cannot read class files newer than about Java 9 to 11. So compiling uPortal itself to `--release 17+` while staying on Spring 4.3 is risky. The runtime JDK can move first if the bytecode target stays at 11 (`options.release = 11`), which leaves more time before the bytecode target has to move. (Inference. Spring/Hibernate compatibility belongs to the parallel framework research.)
- Groovy 3 (used by uPortal's Spock tests and CodeNarc 3.7) has no documented JDK 25 support, so Spock/Groovy must move to Groovy 4 or 5 together with the JDK 25 bump (see Q3).
- Date/time assertions in tests and XSLT/JSP formatting (uPortal uses Joda-Time and the JSTL fmt tags) should be checked for U+202F on JDK 20+.

### Gaps
- Official JDK 26 release notes were not fetched, so the JDK 26 `Thread.stop` removal and the Unsafe `deny` default are from secondary sources.
- JEP pages 372/400/421/479/491/503/363 could not be fetched (openjdk.org blocked). They are cited from well-established knowledge and the JEP index and should be spot-checked.
- No empirical `--add-opens` list for uPortal 5.17 on JDK 17/21/25 was produced. This needs a test run.

## Q3. Minimum versions of build and test libraries to compile or run on Java 25 (and latest as of Oct 2026)

### Takeaway
Almost every build-time component in uPortal must move:
- **Groovy 3 → 4.0.28+ or 5.x** (Groovy 6.0 needs JDK 17+).
- **Spock 2.1-groovy-3.0 → 2.4/2.5-groovy-4.0 or -5.0**.
- **Mockito 4.11 → 5.x** (with the `-javaagent` setup).
- **JaCoCo → 0.8.14+** (official Java 25 support).
- **Lombok → 1.18.40+** (uPortal is already on 1.18.48, which supports JDK 27).
- **AspectJ → 1.9.25.x** (already done, and 1.9.26 for Java 26 is not yet on Central).
- **SpotBugs 4.10.x**, already current. It needs Gradle plugin 6.x.
- **CodeNarc 3.7 → 4.0.0** with the Groovy 4 or 5 variant.
- **google-java-format 1.7 → 1.2x/1.3x**, which needs JDK 21 to run (switch to Spotless).
- **JUnit 4.13.2** still works, but JUnit 6 deprecates Vintage.

### Cited Findings
Latest versions as of 2026-10-10 come from Maven Central metadata unless noted.
- **Groovy.** Latest is **6.0.0** (pom dated 19 Sep 2026). The 5.x line is at **5.1.3** / 5.0.8, and 4.0.x is at 4.0.33 (bundled in Gradle 9.8). Groovy 5 has <!-- vale ai-tells.EmDashUsage = NO --><!-- direct quote of the Groovy release notes -->"full compatibility with JDK 11–25"<!-- vale ai-tells.EmDashUsage = YES --> (GA August 2025, groovy 5.0.0 pom dated 21 Aug 2025). Groovy 6 is <!-- vale ai-tells.EmDashUsage = NO --><!-- direct quote of the Groovy release notes --><!-- vale ai-tells.SemicolonUsage = NO --><!-- direct quote of the Groovy release notes -->"tested across JDK 17–27; **JDK 17 is now the minimum** supported runtime"<!-- vale ai-tells.SemicolonUsage = YES --><!-- vale ai-tells.EmDashUsage = YES -->. Groovy 3.0.25 (27 May 2025) is the last 3.0 release, and no JDK 24/25 support statement was found for it. Jar inspection shows groovy-5.0.8 classes target Java 11 and groovy-6.0.0 classes target Java 17. ([groovy metadata](https://repo1.maven.org/maven2/org/apache/groovy/groovy/maven-metadata.xml), [Groovy 5.0 release notes (source)](https://raw.githubusercontent.com/apache/groovy-website/asf-site/site/src/site/releasenotes/groovy-5.0.adoc), [Groovy 6.0 release notes (source)](https://raw.githubusercontent.com/apache/groovy-website/asf-site/site/src/site/releasenotes/groovy-6.0.adoc), [ASF release catalog 3.0.25](https://release-catalog.apache.org/groovy/3.0.25/index.html))
  - Groovy 5 breaking changes include tightened visibility for package-private members (GROOVY-11357), anonymous inner classes becoming package-private, `getProperty` semantics, AST-transform annotation changes and others. ([Groovy 5.0 release notes §Breaking changes](https://raw.githubusercontent.com/apache/groovy-website/asf-site/site/src/site/releasenotes/groovy-5.0.adoc))
- **Spock.** Latest is **2.5 (released 2026-10-06)**, with artifacts `2.5-groovy-2.5/3.0/4.0/5.0/6.0`. Spock **2.4 (2025-12-11)** added Groovy 5.0 support and states "Spock is also tested to run correctly on Java 25 LTS". It also added the Mockito mock maker (requires `mockito-core` ≥ 4.11) and support for JUnit 6 (OSGi fix).
  - 2.4 breaking change: global `GroovyMock`/`GroovySpy` in parallel mode requires `@Isolated` or `@ResourceLock(META_CLASS_REGISTRY)`.
  - 2.4 breaking change: fixes that "will break tests using `.with {}` in assertions".
  - 2.5 breaking change: `Comparable<T>` mocks compare by identity hash code.
  - 2.5 breaking change: under Groovy 6, a top-level arrow-`switch` in `expect:`/`then:` is no longer an implicit condition.
  Sources: the [Spock release notes (source)](https://raw.githubusercontent.com/spockframework/spock/master/docs/release_notes.adoc), [spock-core metadata](https://repo1.maven.org/maven2/org/spockframework/spock-core/maven-metadata.xml)
- **JUnit.** JUnit 4 is still at **4.13.2 (Feb 2021, final)**. The JUnit 5/6 BOM latest is **6.1.3** (Aug 2026). JUnit 6.0.0 went GA on 30 Sep 2025 with a **Java 17 baseline**, removed `junit-platform-runner`, and **deprecated the Vintage engine** (still published, so JUnit 4 tests can run on the JUnit Platform). ([junit metadata](https://repo1.maven.org/maven2/junit/junit/maven-metadata.xml), [junit-bom metadata](https://repo1.maven.org/maven2/org/junit/junit-bom/maven-metadata.xml), [JUnit 6.0.0 release notes](https://docs.junit.org/6.0.0/release-notes/), [InfoQ JUnit 6](https://www.infoq.com/news/2025/10/junit6-java17-kotlin))
- **Mockito / ByteBuddy.** Mockito latest is **5.24.0** (Sept 2026, jar classes target Java 11). No Mockito 6 is on Central. ByteBuddy latest is **1.18.14** (14 Sep 2026). Since 1.18.7 it publishes `-jdk5` variants and uses a Java 8 baseline for the regular jar. ByteBuddy Java support: 1.17.5 updated ASM to 9.8 for Java 25, and 1.17.8 added ASM 9.9 for Java 26 ([mockito-core metadata](https://repo1.maven.org/maven2/org/mockito/mockito-core/maven-metadata.xml), [ByteBuddy release notes](https://raw.githubusercontent.com/raphw/byte-buddy/master/release-notes.md)). Mockito 4.11 (uPortal's version) bundles a pre-Java-25 ByteBuddy (inference, not verified).
- **EasyMock.** Latest is **5.7.0** (Aug 2026; classes target Java 8; built with JDK 25), which uPortal already uses. ([easymock metadata](https://repo1.maven.org/maven2/org/easymock/easymock/maven-metadata.xml) and jar manifest inspection)
- **Lombok.** 1.18.40 (4 Sep 2025) added JDK 25 support. 1.18.44 fixed JDK 25 `val`/`@ExtensionMethod` errors. 1.18.46 (Apr 2026) added JDK 26. **1.18.48 (1 Sep 2026) added JDK 27**. 1.18.48 is uPortal's current version. ([Lombok changelog (source)](https://raw.githubusercontent.com/projectlombok/lombok/master/doc/changelog.markdown))
- **AspectJ.** The compatibility table maps 1.9.25 → Java 25 and 1.9.26 → Java 26. Since 1.9.21 the `ajc` compiler needs JDK 17+ at build time, while `aspectjrt` and load-time weaving still need only Java 8. **Maven Central's latest is 1.9.25.1 (Dec 2025).** The 1.9.26 release notes say "available Sep-2026", but `aspectjweaver-1.9.26.pom` returned 404 on Central on 2026-10-10 ([AspectJ JavaVersionCompatibility](https://raw.githubusercontent.com/eclipse-aspectj/aspectj/master/docs/release/JavaVersionCompatibility.adoc), [README-1.9.25](https://raw.githubusercontent.com/eclipse-aspectj/aspectj/master/docs/release/README-1.9.25.adoc), [README-1.9.26](https://raw.githubusercontent.com/eclipse-aspectj/aspectj/master/docs/release/README-1.9.26.adoc), [aspectjweaver metadata](https://repo1.maven.org/maven2/org/aspectj/aspectjweaver/maven-metadata.xml)). uPortal-start's CAS overlay still pins `aspectjrt`/`aspectjweaver:1.9.7` (local `overlays/cas/build.gradle`).
- **JaCoCo.** 0.8.13 (2025-04-02) gave official 23/24 support and experimental 25. **0.8.14 (2025-10-11) gave official Java 25** and experimental 26. **0.8.15 (2026-06-04) gave official Java 26** and experimental 27. Gradle 9.8 made 0.8.15 its default `toolVersion`, and 9.3 had made it 0.8.14. ([JaCoCo changes (source)](https://raw.githubusercontent.com/jacoco/jacoco/master/org.jacoco.doc/docroot/doc/changes.html), [Gradle upgrading_version_9](https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_version_9.adoc))
- **SpotBugs.** Latest is **4.10.4** (2026-08-19), which uPortal already uses as `toolVersion`.
  - 4.9.7 "allow our GA builds to work with JDK 25".
  - 4.10.0 fixed an `IllegalArgumentException` initializing inside a fat jar on Java 25.
  - 4.10.0 switched internal nullness annotations from `javax.annotation` to `jakarta.annotation`.
  - The SpotBugs Gradle plugin (`com.github.spotbugs`) latest is **6.5.12** (Sep 2026), and its README examples "require Gradle 8.2+". uPortal uses plugin **4.8.0**. findsecbugs-plugin 1.14.0 is used by uPortal.
  Sources: [SpotBugs CHANGELOG](https://raw.githubusercontent.com/spotbugs/spotbugs/master/CHANGELOG.md), [spotbugs-gradle-plugin README](https://raw.githubusercontent.com/spotbugs/spotbugs-gradle-plugin/master/README.md), [plugin metadata](https://plugins.gradle.org/m2/com/github/spotbugs/com.github.spotbugs.gradle.plugin/maven-metadata.xml)
- **CodeNarc.** Latest is **4.0.0 (Jul 2026)**. It dropped Groovy 3.x. The default artifact works with Groovy 5.x and 4.x and requires Java 11+. A Groovy-4 variant is now published as the separate artifact **`CodeNarc-Groovy4`**, no longer the `-groovy-4.0` version suffix. 3.1.0 to 3.7.0 had `x.y.z-groovy-4.0` versions. Gradle's built-in `codenarc` plugin defaults to CodeNarc 3.6.0 in 9.0 and 3.7.0 from 9.6. Since 9.4 the CodeNarc compilation classpath is set by default. ([CodeNarc CHANGELOG](https://raw.githubusercontent.com/CodeNarc/CodeNarc/master/CHANGELOG.md), [CodeNarc metadata](https://repo1.maven.org/maven2/org/codenarc/CodeNarc/maven-metadata.xml), [Gradle upgrading_major_version_9](https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_major_version_9.adoc))
- **google-java-format.** Latest is **1.37.0** (1 Oct 2026). It uses internal javac (`jdk.compiler`) APIs and needs `--add-exports jdk.compiler/com.sun.tools.javac.{api,code,file,parser,tree,util}=ALL-UNNAMED` on JDK 16+ when used as a library. The running JDK must be ≥ the language level being formatted. The **current minimum JDK to run it is 21** (per README; per third-party mirrors of release notes, 1.29.0 raised the minimum to JDK 21). The `--aosp` style (4-space indent) is still supported. ([google-java-format README](https://raw.githubusercontent.com/google/google-java-format/master/README.md), [gjf metadata](https://repo1.maven.org/maven2/com/google/googlejavaformat/google-java-format/maven-metadata.xml), [dependabot mirror of 1.29 notes](https://dependabot.ecosyste.ms/hosts/GitHub/repositories/rabbitmq%2Frabbitmq-java-client/issues/1755) (secondary))
- **Error Prone.** uPortal applies `net.ltgt.errorprone` 4.4.0, and the latest plugin is 5.1.1. Error Prone 2.32 to 2.42 needs JDK 17 to run, and **2.43+ needs JDK 21**. Compiling for older targets via `--release` is still supported. ([errorprone.info installation](https://errorprone.info/docs/installation), search summary of the Gradle plugin's version table (secondary), [plugin metadata](https://plugins.gradle.org/m2/net/ltgt/errorprone/net.ltgt.errorprone.gradle.plugin/maven-metadata.xml))

### Inferences
- Smallest working set for JDK 25:
  - Groovy 4.0.28+ (or 5.0.x)
  - Spock 2.4-groovy-4.0 (or 2.4/2.5-groovy-5.0)
  - Mockito 5.x, with the Mockito agent configured as `-javaagent` on Test tasks
  - JaCoCo 0.8.14+
  - Lombok ≥ 1.18.40 (already met)
  - AspectJ 1.9.25.x (already met)
  - SpotBugs 4.9.7+ (already met) with Gradle plugin 6.x
  - CodeNarc 4.0.0 (or 3.7.0-groovy-4.0)
  - google-java-format ≥ 1.2x, run on JDK 21+
- **Groovy 4 vs 5 for tests:** while Gradle 9 itself embeds Groovy 4.0.x for build scripts, the project's test Groovy is independent. Choosing **Groovy 4.0.x + Spock 2.4/2.5-groovy-4.0 + CodeNarc 4.0 (or CodeNarc-Groovy4)** minimises churn. Groovy 5 is fine too, and is supported to JDK 25. Groovy 6 forces JDK 17+, which is fine at runtime but newer.
- Groovy 3 → 4 changes the Maven group from `org.codehaus.groovy` to `org.apache.groovy`, so the `groovyVersion` dependency coordinates in uPortal must change. This is a Groovy 4 fact from its release notes and is not re-verified here.
- **Formatting churn:** moving from google-java-format 1.7 to 1.37 will reformat code. The gjf output changes between versions. Do it as one isolated "format-only" commit (or use Spotless `ratchetFrom`) to keep the history reviewable. Because gjf now needs JDK 21 to run, the Gradle daemon must run on JDK 21+ once gjf is upgraded, or the formatter must run in a forked toolchain JVM.
- JUnit 4 can stay, using `useJUnit()` or Vintage on the JUnit Platform. Spock 2 already requires the JUnit Platform, so uPortal's test tasks must be `useJUnitPlatform()` with Vintage for the JUnit 4 tests. In Gradle 9 this also means declaring `junit-platform-launcher` (Q4). Because Vintage is deprecated in JUnit 6, plan a later JUnit 4 → Jupiter migration. OpenRewrite has recipes for it.

### Gaps
- Exact Groovy 3.0.25 behaviour on JDK 25 is unknown (no statement found). Treat it as unsupported.
- The JDK 25 support status of Mockito 4.11's bundled ByteBuddy was not verified.
- Whether AspectJ 1.9.26 is actually published elsewhere (e.g., a staging repo) is unknown. On Central it is absent as of 2026-10-10.

## Q4. Gradle core upgrade path 6.9.4 → 9.8.1 (per-hop breaking changes)

### Takeaway
The current Gradle is **9.8.1 (7 Oct 2026)**. Gradle 9 needs **JVM 17 through 27 to run**, and toolchains support compiling and testing on Java 25 from 9.1.0 and on 27 from 9.8.0. No Gradle 10 release, RC or milestone exists. Both uPortal and uPortal-start use APIs removed in 7.0, 8.0 and 9.0:
- `compile`/`runtime`/`testCompile`, the `maven` plugin and `uploadArchives` (all removed in 7.0)
- `archiveName`/`classifier` (removed in 8.0)
- the Convention API (removed in 9.0); uPortal-start's vendored `WarOverlayPlugin` uses it
- `buildDir`, which is deprecated

A hop-by-hop path (6.9.4 → 7.6.x → 8.14.x → 9.x) with OpenRewrite assistance is the safe route.

### Cited Findings
- **Versions.** `services.gradle.org/versions/current` reports **9.8.1** (built 2026-10-07). The 9.x line: 9.0.0 (2025-07-31), 9.1.0 (2025-09-18), 9.2.0 (2025-10-29), 9.3.0 (2026-01-16), 9.4.0 (2026-03-04), 9.5.0 (2026-04-28), 9.6.0 (2026-06-18), 9.7.0 (2026-08-06), 9.8.0 (2026-09-24). The list has no 10.x RC or milestone, and the RC endpoint is empty. ([services.gradle.org/versions/current](https://services.gradle.org/versions/current), [services.gradle.org/versions/all](https://services.gradle.org/versions/all))
- **Compatibility matrix (master source).** "A JVM version between 17 and 27 is required to execute Gradle." The wrapper, Tooling API and TestKit clients still run on JVM 8. Java 25: toolchain and run support from **9.1.0**. Java 26: from 9.4.0. Java 27: from 9.8.0. Java 17: running from 7.3. Java 21: toolchain from 8.4, running from 8.5. Java 11 can run Gradle only up to **8.14.x**.
  - Embedded Groovy: 3.0.25 in 8.14.4, **4.0.27 in 9.0.0** and 4.0.33 in 9.8.0.
  - "Gradle plugins written in Groovy must use Groovy 4.x."
  - Embedded Kotlin: 2.2.0 in 9.0 and 2.4.10 in 9.8, with Kotlin DSL language level 2.2.
  Source: [compatibility.adoc](https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/compatibility.adoc) ([published](https://docs.gradle.org/current/userguide/compatibility.html))
- **Gradle 9.0 main changes.** It requires JVM 17+ for the daemon (toolchains can target older JDKs). The Configuration Cache is the "preferred execution mode", with automatic fallback when unsupported features are hit. ([Gradle 9.0.0 release notes](https://docs.gradle.org/9.0.0/release-notes.html) (search snippet))
- **6.9 → 7.0 removals.**
  - `compile` and `runtime` configurations removed. Use `implementation`/`api` (`java-library`) and `runtimeOnly`. `testCompile` → `testImplementation` follows from the same removal.
  - Legacy `maven` plugin removed. Use `maven-publish`.
  - `uploadArchives` task removed.
  - Copy specs now fail on duplicate entries unless `duplicatesStrategy` is set.
  - Missing task dependencies between producer and consumer deprecated.
  - Groovy 3 parser in build scripts.
  - `jcenter()` deprecated.
  Source: [upgrading_version_6.adoc](https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_version_6.adoc) ([published](https://docs.gradle.org/current/userguide/upgrading_version_6.html))
- **7.x → 8.0 removals.**
  - `JavaExec.main` removed. Use `mainClass`.
  - `AbstractArchiveTask` `appendix`/`archiveName`/`archivePath`/`baseName`/`classifier`/`destinationDir`/`extension`/`version` removed. Use `archiveAppendix`/`archiveFileName`/`archiveFile`/`archiveBaseName`/`archiveClassifier`/`destinationDirectory`/`archiveExtension`/`archiveVersion`.
  - `JacocoMerge` task removed.
  - Implicit `--add-opens` for test workers removed (since 7.5).
  - Signing plugin defaults to `gpg` rather than `gpg2` (7.5).
  - AdoptOpenJDK toolchain vendor deprecated in favour of `ADOPTIUM`.
  Source: [upgrading_version_7.adoc](https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_version_7.adoc) ([published](https://docs.gradle.org/current/userguide/upgrading_version_7.html))
- **8.x deprecations that 9.0 enforces.**
  - Plugin conventions deprecated (8.2), including `java` (`sourceCompatibility` via `JavaPluginConvention`), `war`, `base` (`archivesBaseName`) and `project-report`.
  - `Project.buildDir` deprecated in favour of `layout.buildDirectory` (8.3, with deprecation warnings deferred).
  - Groovy "space assignment" syntax (`prop value`) deprecated (8.12).
  - `Project#exec`/`javaexec` deprecated (8.11).
  - `is`-prefixed `Boolean` properties deprecated (8.13).
  - Relying on automatic loading of test-framework runtime dependencies deprecated (8.x). Without test suites, **JUnit 5 needs an explicit `runtimeOnly 'org.junit.platform:junit-platform-launcher'`**. JUnit 4 needs only `junit` on the classpath.
  - 8.14: the wrapper is an executable JAR, JaCoCo is 0.8.13, and `JavaExec` uses the `java` extension toolchain by default.
  Source: [upgrading_version_8.adoc](https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_version_8.adoc) ([published](https://docs.gradle.org/current/userguide/upgrading_version_8.html))
- **9.0 breaking changes.**
  - JVM 17 is required to run Gradle.
  - Upgrade to Groovy 4.0.27 for build scripts: `is`-prefixed `Boolean` getters are no longer Groovy properties (Gradle still recognises them until 10). `DELEGATE_FIRST` closure lookup order changes. Private members may be inaccessible in closures from subclasses (GROOVY-11568). Code compiled with Groovy 3 may not reach `super` methods.
  - **Convention API removed**: `Project.getConvention()`, `Task.getConvention()`, `org.gradle.api.plugins.Convention`, `HasConvention`, and the `war` (`WarPluginConvention`), `base` (`archivesBaseName`, `libsDirName`, `distDirName`) and `ear` conventions.
  - `jcenter()` removed.
  - `Project#exec`/`javaexec` and script-level `exec`/`javaexec` removed.
  - `groovy-test`, `groovy-console` and `groovy-sql` removed from the distribution and `localGroovy()`.
  - `Test` tasks with no explicit classpath silently run nothing.
  - **The `test` task fails if test sources exist but no tests are discovered** (`failOnNoDiscoveredTests = false` to opt out).
  - Archive tasks are reproducible by default.
  - With `war` plus `java`, `assemble` now builds both the WAR and the JAR, and both go into `archives`.
  - Plugins written with the Groovy DSL must have been built with Gradle ≥ 7.0.
  - Defaults are JUnit Jupiter 5.12.2 / Spock 2.3 for test suites, and CodeNarc 3.6.0, Checkstyle 10.24 and PMD 7.13.
  Source: [upgrading_major_version_9.adoc](https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_major_version_9.adoc) ([published](https://docs.gradle.org/current/userguide/upgrading_major_version_9.html))
- **9.1 to 9.8 deprecations heading for Gradle 10.**
  - Multi-string/map dependency notation (`group:, name:, version:`) is deprecated (9.1) and will not be permitted in Gradle 10.
  - The `archives` configuration is deprecated (9.1).
  - Project properties for toolchain configuration are deprecated (9.1).
  - `Closure` listener methods on `Test` tasks (`beforeTest`/`afterTest`/`beforeSuite`/`onOutput`…) are deprecated (9.4). Use `addTestListener`.
  - **Implicit lookup of properties and methods in parent projects** is deprecated (9.6), and so is `Project.getProperties()` / script `properties` (removed in 10). Use `providers.gradleProperty()` or `ext` instead.
  - Accessing `Task`/extensions/`Project` from task actions, and using Project objects as dependency notation, are deprecated (9.6).
  - `flatDir(Map)` and `mavenCentral(Map)` are deprecated (9.6).
  - Starting external processes at configuration time via `ExecOperations`, `ProcessBuilder`, `Runtime.exec` or Groovy `String.execute()` is deprecated (9.8).
  - Signing files with a classifier and the `Signature` artifact APIs are deprecated (9.8).
  - macOS on AMD64 is deprecated (9.8).
  - 9.8: `Copy`/`Sync` destination becomes a lazy property, JaCoCo 0.8.15, Groovy 4.0.33. (Version attribution: the guide's "Upgrading from X and earlier" headings describe changes in the release after X, cross-checked against the embedded Groovy/Kotlin table, e.g. Groovy 4.0.33 → 9.8.0, 4.0.32 → 9.6.0.)
  Source: [upgrading_version_9.adoc](https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_version_9.adoc) ([published](https://docs.gradle.org/current/userguide/upgrading_version_9.html))
- **OpenRewrite.** `org.openrewrite.gradle.MigrateToGradle9` is a composite: `MigrateToGradle8`, then `UpdateGradleWrapper` to 9.x, then `DependencyUseStringNotation`. A wider `gradle9` recipe family includes "one dependency declaration per statement", "use Action overloads of flatDir/mavenCentral" and others. It runs via the `org.openrewrite.rewrite` Gradle plugin (latest **7.41.0**) or an init script. `rewrite-gradle` is at 8.90.4 and `rewrite-migrate-java` at 3.42.1. ([OpenRewrite MigrateToGradle9](https://docs.openrewrite.org/recipes/gradle/migratetogradle9), [gradle9 recipe family](https://docs.openrewrite.org/recipes/gradle/gradle9), [rewrite plugin metadata](https://plugins.gradle.org/m2/org/openrewrite/rewrite/org.openrewrite.rewrite.gradle.plugin/maven-metadata.xml), [rewrite-migrate-java metadata](https://repo1.maven.org/maven2/org/openrewrite/recipe/rewrite-migrate-java/maven-metadata.xml))
- **Concrete uPortal-start usages that break** (local `/home/user/uPortal-start` grep):
  - `buildSrc/build.gradle` uses `compile`.
  - `overlays/*/build.gradle` use `runtime "…@war"`, `compile configurations.jdbc`, `archiveName '…war'`, and `testCompile group:, name:, version:` (the cas overlay). The last is both a 7.0 removal and the 9.1-deprecated map notation.
  - `overlays/build.gradle` has `apply plugin: 'maven'` (removed in 7.0) and `maven { url "https://jitpack.io" }`.
  - `buildSrc/.../scaldingspoon/gradle/WarOverlayPlugin.groovy` calls `project.convention.plugins.warOverlay = new WarOverlayPluginConvention()` (Convention API, removed in 9.0).
  - `buildDir` is used in `gradle/tasks/portlet.gradle`, `overlays/uPortal/build.gradle` and buildSrc plugins.
  - Many `ant.exec`/`ant.java` calls in `gradle/tasks/{tomcat,hsql}.gradle` and the overlay import tasks.
  - `wrapper` is 6.9.4.
  - Plugins: `com.github.node-gradle.node` 7.0.2 and `net.foragerr.jmeter` 1.1.0-4.0, which was last published 2018-08-22 per [portal metadata](https://plugins.gradle.org/m2/net/foragerr/jmeter/net.foragerr.jmeter.gradle.plugin/maven-metadata.xml).
- **Concrete uPortal (portal repo) usages.**
  - `apply plugin: 'maven'` with `uploadArchives { mavenDeployer { beforeDeployment { signing.signPom } } }`, and `afterReleaseBuild.dependsOn uploadArchives`.
  - `classifier 'sources'` / `classifier = 'javadoc'`.
  - `sourceCompatibility = 11` set on the project (convention).
  - `"$buildDir/..."` and `fileTree(buildDir)`.
  - `tasks.withType(Test) { afterSuite { … rootProject.ext.testModuleSummaries << … } }`, i.e. a Closure listener plus cross-project mutable state.
  - `googleJavaFormat { toolVersion '1.7'; options style: 'AOSP' }` (sherter plugin 0.9).
  Source: [uPortal build.gradle](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/build.gradle)

### Inferences
- Recommended hop sequence:
  1. **6.9.4 → 7.6.x on Java 11**: fix `compile`/`runtime`/`testCompile`, `maven`/`uploadArchives` → `maven-publish`, duplicates strategy, and `jcenter`.
  2. **→ 8.14.x on Java 11 or 17**: archive property renames, `mainClass`, `layout.buildDirectory`, the `java { toolchain { languageVersion = JavaLanguageVersion.of(N) } }` or `options.release` instead of project `sourceCompatibility`, an explicit `junit-platform-launcher`, removal of convention usage, and `--warning-mode all` clean.
  3. **→ 9.8.x with the daemon on JDK 17+ (ideally 25)**: the Convention API gone, Groovy 4 script semantics, `failOnNoDiscoveredTests`, and the `war` + `java` `assemble` change.
  4. Then clean up the 9.x deprecations (map notation, parent-project property lookup, Closure test listeners, configuration-time process execution) so a future Gradle 10 is a small hop. Running OpenRewrite `MigrateToGradle9` after the manual 7.x fixes can automate much of the 8 → 9 mechanical work, but buildSrc Groovy plugins (`WarOverlayPlugin`, `GradleTomcatDeployPlugin`, `GradlePlutoPlugin`, `PortalShellInvoker`) need hand edits.
- `WarOverlayPlugin` should be rewritten to use `project.extensions.create('warOverlay', WarOverlayExtension)`. The DSL `warOverlay { includeWarJars true }` uses Gradle-specific space-assignment syntax, which is deprecated, so write `includeWarJars = true`.
- uPortal-start resolves properties such as `${tomcatVersion}` and `${uPortalVersion}` from `gradle.properties` inside subproject scripts and buildSrc. These remain valid as Gradle properties, but implicit parent-project lookup of `ext` values is deprecated in 9.6+. Use `providers.gradleProperty('tomcatVersion')` or a version catalog (`gradle/libs.versions.toml`) instead.
- Gradle 9 makes the configuration cache "preferred" but **does not force it**. uPortal-start's heavy use of `ant.*` and `doLast` closures referencing `project` will not be CC-compatible. That is acceptable, because Gradle falls back. Enable it only later.
- Running the Gradle daemon on JDK 25 and compiling via toolchain to 11 or 17 is a good intermediate step while the framework stack (Spring 4.3/Hibernate 4.3) cannot yet run on newer bytecode.

### Gaps
- The Gradle 9.8 release notes themselves were not fetched (docs.gradle.org is blocked). Version dates come from services.gradle.org; behaviour comes from the master-branch upgrade guides, which may include 9.9-in-progress content (e.g., "Upgrading from 9.8.0 and earlier" items that may not be released until 9.9).
- No public Gradle 10 timeline was found.

## Q5. Third-party Gradle plugins and Maven Central publishing

### Takeaway
Several plugins in uPortal and uPortal-start are **unmaintained**:
- sherter google-java-format (last 0.9, June 2020)
- kt3k coveralls (last 2.12.2, March 2023)
- foragerr jmeter (2018)
- node-gradle (last 7.1.0, Sept 2024, and CI tests only up to Gradle 8.10.2)

Replace sherter with **Spotless 8.x** (Java 17+, Gradle 8.1+, supports `googleJavaFormat().aosp()`). Replace coveralls with the **Coveralls GitHub Action / coverage-reporter (native JaCoCo XML)**. Upgrade SpotBugs to 6.5.x and bmuschko docker to 9.4/10.0. Replace legacy `maven` + `uploadArchives` with `maven-publish` plus a Central Portal publisher (**gradle-nexus publish-plugin 2.0 against the OSSRH Staging API**, **vanniktech 0.37**, or JReleaser 1.26). OSSRH reached end-of-life on 30 June 2025.

### Cited Findings
- **Spotless.** `spotless-plugin-gradle` latest is **8.10.4** (2026-10-08). 8.0.0 (2025-09-24) requires Java 17 and Gradle 7.3, and 8.1.0 bumped the minimum Gradle to 8.1. Stuck on JRE 11 → Spotless 7.2.1, and on JRE 8 → 6.13.0. The README shows `googleJavaFormat('1.8').aosp().reflowLongStrings().skipJavadocFormatting()`. Recent releases fix Gradle 9 issues, e.g. not calling the deprecated `Configuration.setVisible` (8.10.3) and `predeclareDepsFromBuildscript()` on Gradle 9 (8.5.0). ([Spotless plugin-gradle README](https://raw.githubusercontent.com/diffplug/spotless/main/plugin-gradle/README.md), [CHANGES.md](https://raw.githubusercontent.com/diffplug/spotless/main/plugin-gradle/CHANGES.md), [metadata](https://repo1.maven.org/maven2/com/diffplug/spotless/spotless-plugin-gradle/maven-metadata.xml))
- **sherter google-java-format plugin.** Last published version 0.9 (portal metadata `lastUpdated` 2020-06-08). The upstream README was not found on master/develop. ([portal metadata](https://plugins.gradle.org/m2/com/github/sherter/google-java-format/com.github.sherter.google-java-format.gradle.plugin/maven-metadata.xml))
- **kt3k coveralls.** Last published version 2.12.2 (portal `lastUpdated` 2023-03-07). Alternatives: `com.github.nbaztec.coveralls-jacoco` (last 1.2.20, May 2024) and the Coveralls universal **coverage-reporter**, which supports `jacoco` format natively and is used by `coverallsapp/github-action` v2. ([kt3k metadata](https://plugins.gradle.org/m2/com/github/kt3k/coveralls/com.github.kt3k.coveralls.gradle.plugin/maven-metadata.xml), [nbaztec metadata](https://plugins.gradle.org/m2/com/github/nbaztec/coveralls-jacoco/com.github.nbaztec.coveralls-jacoco.gradle.plugin/maven-metadata.xml), [coverage-reporter README](https://raw.githubusercontent.com/coverallsapp/coverage-reporter/master/README.md), [coverallsapp/github-action README](https://raw.githubusercontent.com/coverallsapp/github-action/main/README.md))
- **node-gradle (`com.github.node-gradle.node`).** Latest is **7.1.0 (2024-09-27)**. The unreleased 8.x drops npm < 7. The README claims support for "Gradle 6.6 and newer". The CI matrix tests only Gradle 6.9.4, 7.6.3 and 8.10.2, so Gradle 9 is not tested upstream. A Gradle-9 compatibility statement was not found ([node-gradle CHANGELOG](https://raw.githubusercontent.com/node-gradle/gradle-node-plugin/main/CHANGELOG.md), [README](https://raw.githubusercontent.com/node-gradle/gradle-node-plugin/main/README.md), [CI build.yml](https://raw.githubusercontent.com/node-gradle/gradle-node-plugin/main/.github/workflows/build.yml), [portal metadata](https://plugins.gradle.org/m2/com/github/node-gradle/node/com.github.node-gradle.node.gradle.plugin/maven-metadata.xml)). uPortal uses 7.1.0, and uPortal-start uses 7.0.2 with `nodejsVersion=24.11.1`.
- **SpotBugs Gradle plugin.** 6.5.12 latest (Sep 2026). The README requires Gradle 8.2+. uPortal is on 4.8.0. (See Q3 sources.)
- **bmuschko gradle-docker-plugin.** Latest **9.4.0**, plus a `com.bmuschko.docker-remote-api` **10.0.0** marker (metadata `lastUpdated` 2025-10-30). uPortal-start pins `gradleDockerPluginVersion=3.2.4`, and the CAS 8.1 overlay uses 9.4.0. ([gradle-docker-plugin metadata](https://plugins.gradle.org/m2/com/bmuschko/gradle-docker-plugin/maven-metadata.xml), [docker-remote-api marker metadata](https://plugins.gradle.org/m2/com/bmuschko/docker-remote-api/com.bmuschko.docker-remote-api.gradle.plugin/maven-metadata.xml), [CAS overlay gradle.properties](https://raw.githubusercontent.com/apereo/cas-overlay-template/master/gradle.properties))
- **Gretty** (a maintained replacement for the dead bmuschko Tomcat plugin, if an embedded run task is wanted). **5.0.2 (2026-03-13)** supports Jetty 12 and **Tomcat 11**. The 3.x branch is for older Gradle and javax. ([Gretty README](https://raw.githubusercontent.com/gretty-gradle-plugin/gretty/master/README.md), [metadata](https://plugins.gradle.org/m2/org/gretty/gretty/maven-metadata.xml))
- **Other uPortal plugins.** `nebula.lint` 17.8.0 → latest 21.3.0. `net.researchgate.release` 3.1.0 → 3.2.0 (2026-09-30). `net.ltgt.errorprone` 4.4.0 → 5.1.1. `io.freefair.lombok` / `io.freefair.aspectj.post-compile-weaving` latest 9.8.0, as used by the CAS overlay. ([nebula.lint](https://plugins.gradle.org/m2/nebula/lint/nebula.lint.gradle.plugin/maven-metadata.xml), [researchgate release](https://plugins.gradle.org/m2/net/researchgate/release/net.researchgate.release.gradle.plugin/maven-metadata.xml), [freefair lombok](https://plugins.gradle.org/m2/io/freefair/lombok/io.freefair.lombok.gradle.plugin/maven-metadata.xml))
- **Maven Central publishing.** "**OSSRH reaches end-of-life on June 30, 2025!** You should migrate to Sonatype Central." The gradle-nexus publish-plugin (latest **2.0.0**, requires Gradle 6.2+) works with Central via the **OSSRH Staging API**: `nexusUrl = https://ossrh-staging-api.central.sonatype.com/service/local/`, `snapshotRepositoryUrl = https://central.sonatype.com/repository/maven-snapshots/`, with Central Portal user tokens rather than legacy OSSRH credentials. ([gradle-nexus/publish-plugin README](https://raw.githubusercontent.com/gradle-nexus/publish-plugin/master/README.md), [Sonatype OSSRH sunset notice (linked from README)](https://central.sonatype.org/news/20250326_ossrh_sunset/))
- uPortal's build already points `publishingRepositoryUrl` at `https://ossrh-staging-api.central.sonatype.com/...` but still deploys through the legacy `maven` plugin's `uploadArchives`/`mavenDeployer`. ([uPortal build.gradle](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/build.gradle))
- **vanniktech `com.vanniktech.maven.publish`.** Latest **0.37.0** (Central artifact `com.vanniktech:gradle-maven-publish-plugin`, Jun 2026). It publishes natively to the Central Portal (`publishToMavenCentral`, optional automatic release, portal user tokens, and snapshots to `central.sonatype.com/repository/maven-snapshots/`). ([vanniktech central.md](https://raw.githubusercontent.com/vanniktech/gradle-maven-publish-plugin/main/docs/central.md), [metadata](https://repo1.maven.org/maven2/com/vanniktech/gradle-maven-publish-plugin/maven-metadata.xml))
- **JReleaser** Gradle plugin latest **1.26.0** (Aug 2026). ([portal metadata](https://plugins.gradle.org/m2/org/jreleaser/org.jreleaser.gradle.plugin/maven-metadata.xml))
- uPortal and uPortal-start still declare `maven { url "https://jitpack.io" }`, and the proxy here denied jitpack.io. ([uPortal build.gradle](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/build.gradle), local `overlays/build.gradle`)

### Inferences
- **Lowest-risk publishing path:** convert to `maven-publish` + `signing` (`signing { sign publishing.publications.mavenJava }`) + `io.github.gradle-nexus.publish-plugin` 2.0.0 configured with the OSSRH Staging API URLs above, since uPortal already uses them. vanniktech is the more modern alternative and handles sources, javadoc and POM defaults. Note the Gradle 9.8 deprecation of signing files with a classifier and of `Signature` artifact APIs. Use the publication-based signing DSL.
- **Formatting:** using Spotless with `googleJavaFormat('1.37.0').aosp()` (or `palantirJavaFormat`) preserves the AOSP style. The Gradle daemon must be on JDK 21+ because of gjf's runtime minimum.
- **node-gradle:** test 7.1.0 on Gradle 9 early. If it fails (e.g., removed APIs), the fallback is plain `Exec` tasks using a Node toolchain installed in CI (`actions/setup-node`), or the frontend-gradle-plugin. These are not researched here.
- **jmeter plugin (2018):** drop it or replace it. It is unlikely to survive Gradle 7+ removals (inference, not tested).
- Remove the `jitpack.io` repositories if nothing resolves from them. They slow resolution and are a supply-chain risk.

### Gaps
- Gradle 9 compatibility of node-gradle 7.1.0, kt3k coveralls 2.12.2, nebula.lint 21.x and researchgate release 3.2.0 was not verified. No upstream statements were found.
- bmuschko docker 10.0.0 release notes (breaking changes vs 9.x) were not found.
- The Sonatype Central docs pages were not fetchable. OSSRH facts come via the gradle-nexus README.

## Q6. Tomcat: latest 9.0/10.1/11.0 versions, EOL dates, and migration notes 8.5 → 9 → 10.1 → 11

### Takeaway
**Tomcat 8.5 has been EOL since 31 March 2024.** Latest releases (Sept 2026): **9.0.122**, **10.1.60**, **11.0.26**. Supported Java: 9.0 → Java 8+, 10.1 → Java 11+, 11.0 → **Java 17+**. **Tomcat 9.0.x support ends 31 March 2027.** A 9.1.x branch (APR/native connectors removed) will receive releases until **31 December 2030** as a javax lifeline. Moving to 10.1/11 is the javax → jakarta break, the same as for all application code. Since uPortal stays on javax until the Jakarta work is merged, the immediate step is **8.5 → 9.0.x (later 9.1.x)**, then **11.0** after Jakarta.

### Cited Findings
- whichversion table: 11.0.x = Servlet 6.1 / Pages 4.0 / EL 6.0 / WebSocket 2.2 / Auth 3.1, latest 11.0.26, Java 17+. 10.1.x = Servlet 6.0 / JSP 3.1 / EL 5.0 / WS 2.1, latest 10.1.60, Java 11+. 9.0.x = Servlet 4.0 / JSP 2.3 / EL 3.0 / WS 1.1, latest 9.0.122, Java 8+. 8.5.x is archived: last release 8.5.100, EOL 2024-03-31. 10.0.x was superseded with EOL 2022-10-31. ([Tomcat whichversion (source)](https://raw.githubusercontent.com/apache/tomcat-site/trunk/xdocs/whichversion.xml) ([published](https://tomcat.apache.org/whichversion.html)))
- Maven Central confirms the latest `tomcat-catalina` per branch is 9.0.122 / 10.1.60 / 11.0.26, and that `org.apache.tomcat:tomcat:<v>@tar.gz` for 10.1.60 and 11.0.26 (published 9 Sept 2026) is resolvable. uPortal-start installs Tomcat through exactly that coordinate (`tomcat "org.apache.tomcat:tomcat:${tomcatVersion}@tar.gz"`). ([tomcat-catalina metadata](https://repo1.maven.org/maven2/org/apache/tomcat/tomcat-catalina/maven-metadata.xml), [tomcat 11.0.26 tar.gz](https://repo1.maven.org/maven2/org/apache/tomcat/tomcat/11.0.26/tomcat-11.0.26.tar.gz), local `gradle/tasks/tomcat.gradle`)
- **9.0.x end of support (announced 2026).** "Support for Apache Tomcat 9.0.x will end on 31 March 2027". After that date there will be no releases, 9.0-only bug fixes or security checks, and download links are removed after 30 June 2027. "Shortly before 9.0.x support ends, releases from a new 9.1.x branch will be made available… until 31 December 2030". The 9.0 → 9.1 differences are minimal: the APR/native HTTP, HTTPS and AJP connectors are removed and Tomcat Native 1.3.x is unsupported. Users are "strongly encouraged to consider upgrading… making use of tools like the Tomcat Migration Tool for Jakarta EE". ([Tomcat 9.0.x EOS (source)](https://raw.githubusercontent.com/apache/tomcat-site/trunk/xdocs/tomcat-9.0.x-eos.xml) ([published](https://tomcat.apache.org/tomcat-9.0.x-eos.html)), corroborated by [Tomitribe](https://tomitribe.com/blog/tomcat-9-end-of-life/) and [OpenLogic](https://www.openlogic.com/blog/prepare-for-tomcat-9-eol))
- **8.5 EOL** was 31 March 2024. All releases remain only in the archive. ([Tomcat 8.5 EOL (source)](https://raw.githubusercontent.com/apache/tomcat-site/trunk/xdocs/tomcat-85-eol.xml))
- **8.5 → 9.0 migration.**
  - Servlet 4.0 adds classes such as `PushBuilder`. JSPs using wildcard imports that also bring in an application class with the same name fail to compile. Use explicit imports.
  - Default `CookieProcessor` is `Rfc6265CookieProcessor` (already in 8.5). `LegacyCookieProcessor` restores 8.0 behaviour.
  - **9.0.74+: the Connector `maxParameterCount` default reduced from 10,000 to 1,000.**
  - **9.0.31+ AJP:** loopback-only default address, and `secretRequired=true` (the AJP Connector won't start without a secret).
  - 9.0.51+: no `Expires` header for CONFIDENTIAL `Cache-Control: private`.
  - 9.0.53+: multipart uploads use NIO `Files`, so size direct memory to match.
  - 9.0.96 changed pooled JSP tag release (fixed in 9.0.97).
  - 9.0.114+: the Tomcat Native minimum is 1.3.4 / 2.0.12.
  Source: the [Tomcat 9 migration guide (source)](https://raw.githubusercontent.com/apache/tomcat-site/trunk/xdocs/migration-9.xml) ([published](https://tomcat.apache.org/migration-9.html))
- **9.0 → 10.0.** Package rename `javax.servlet`, `javax.servlet.jsp`, `javax.el`, `javax.websocket` and `javax.security.auth.message` → `jakarta.*`. Web apps must be recompiled. Tomcat can convert Java EE 8 WARs at deploy time from `webapps-javaee` (the `legacyAppBase`), or ahead of time with the Apache Tomcat migration tool for Jakarta EE (also an Ant task `javax2jakarta`, `profile="tomcat"`). Other changes:
  - `conf/web.xml` defaults request/response encoding to UTF-8.
  - Session persistence on restart is disabled by default.
  - Many Tomcat system properties were replaced by component attributes.
  - Access-log `%D` is now microseconds. Use `%{ms}T` for milliseconds.
  - The HTTP/2 connector inherits settings from HTTP/1.1.
  Sources: the [Tomcat 10 migration guide (source)](https://raw.githubusercontent.com/apache/tomcat-site/trunk/xdocs/migration-10.xml), [tomcat-jakartaee-migration README](https://raw.githubusercontent.com/apache/tomcat-jakartaee-migration/main/README.md)
- **10.0 → 10.1.** Requires Java 11. Servlet 6.0 removes all 5.0-deprecated APIs and **all cookie specs except RFC 6265** (adds `Cookie.setAttribute`). URI decoding/normalisation is clarified. EL uses generics, and `MethodExpression.isParmetersProvided()` is removed. **APR connector removed (10.1.0-M5)**. 10.1.8+: `maxParameterCount` = 1000. 10.1.56+: the EncryptInterceptor cluster change needs a full cluster restart. ([Tomcat 10.1 migration guide (source)](https://raw.githubusercontent.com/apache/tomcat-site/trunk/xdocs/migration-10.1.xml))
- **10.1 → 11.0.**
  - Requires **Java 17**.
  - **SecurityManager support removed**.
  - Servlet 6.1: header semantics with null or empty values clarified; `getParameter()` may throw on parse errors; **HTTP/2 server push removed**; `Cookie` attribute handling for value-less attributes (`HttpOnly`, `Secure`, `Partitioned`).
  - All deprecated 10.1 and 3.1/5.0 APIs removed.
  - Byte-to-char conversion failures now throw.
  - The `^` WAR URL separator is removed.
  - **Quoted cookie values keep their quotes (RFC 6265)**.
  - `FailedRequestFilter` removed.
  - `maxParameterCount` default 1000 (11.0.0-M5).
  - 11.0.23+: EncryptInterceptor change.
  Source: the [Tomcat 11 migration guide (source)](https://raw.githubusercontent.com/apache/tomcat-site/trunk/xdocs/migration-11.0.xml) ([published](https://tomcat.apache.org/migration-11.0.html))
- **Current Tomcat 11 connector defaults:** `maxParameterCount` 1000 (includes uploaded files), **`maxPartCount` 50**, `maxPartHeaderSize` 512 bytes. Multipart memory guide: `maxPartHeaderSize × maxPartCount × maxConnections × 2`. ([Tomcat 11 http.xml config doc (source)](https://raw.githubusercontent.com/apache/tomcat/11.0.x/webapps/docs/config/http.xml))
- **Context attributes uPortal relies on.** `crossContext` defaults to false. uPortal/Pluto need it true for portlet dispatch. **`sessionCookiePath="/"`** in global `context.xml` is the documented approach "useful for portlet specification implementations". uPortal-start sets `<Context sessionCookiePath="/">`. `useHttpOnly` defaults to true. uPortal-start also sets `shared.loader=${catalina.base}/shared/lib/*.jar` (Pluto shared libs). ([Tomcat 11 context.xml config doc (source)](https://raw.githubusercontent.com/apache/tomcat/11.0.x/webapps/docs/config/context.xml), local `etc/tomcat/conf/context.xml`, `etc/tomcat/conf/catalina.properties`)

### Inferences
- **Near term (still javax): 8.5.100 → 9.0.122.** This is low risk: the same Servlet 4 / javax API and same `shared.loader` mechanism, provided `server.xml`/`context.xml` are re-based on the 9.0 defaults. Check `maxParameterCount=1000`, which matters for large portal admin forms and the uPortal import/export UI, and the AJP `secretRequired` default if AJP is used behind httpd. Plan **9.1.x** before 31 Mar 2027 if the Jakarta migration is not done by then.
- **After Jakarta: 9 → 11.0 directly** (skip 10.1, because 11 needs Java 17, which we exceed anyway). The Pluto portlet container is javax-only (covered elsewhere). The `webapps-javaee` auto-conversion can convert third-party portlet WARs in `overlays/` as an interim step. But uPortal's own WAR plus `shared/lib` (Pluto API jars loaded by the shared classloader) must be converted consistently, because a mixed javax/jakarta shared classloader will not work. (Inference based on the Tomcat 10 guide's description of per-WAR conversion.)
- uPortal-start's buildSrc `GradleTomcatDeployPlugin` and the `tomcat*` tasks are custom and download through Maven coordinates, so the Tomcat bump is mostly a `tomcatVersion` change plus a config-file rebase (`etc/tomcat/conf/*`).

### Gaps
- No date was found for the first 9.1.x release ("shortly before 31 March 2027").
- The JULI/access-log differences between 8.5 and 9 beyond the migration-guide items were not enumerated.
- No Tomcat 12 / Jakarta EE 12 timeline was researched (no 12.0 artifacts on Central yet).

## Q7. Ecosystem libraries needing Jakarta or major bumps (versions and baselines, Oct 2026)

### Takeaway
Most of uPortal's pinned infrastructure libraries are years behind, and their Jakarta successors raise the Java floor to 17 or 21:
- **Ehcache 2.6.11 (2015)** → Ehcache 3.12 (Java 17, `jakarta` classifier) or Caffeine/JCache.
- **JGroups 3.6.20 (2020)** → 5.5.x (Java 17).
- **Lucene 8.11** → 10.x (Java 21, reindex). Lucene 11 is coming and requires Java 25.
- **CAS client 3.6** → 4.1 (Jakarta Servlet 6.1, Spring 7, **Java 21**).
- **person-directory 1.8** → 3.0.1 (Spring 6, Java 17, dormant since 2022).
- **Jackson 2.22** stays fine; Jackson 3 is optional (Java 17, new packages).

The Jakarta EE 11 API set is available on Central.

### Cited Findings
Latest versions and dates are from Maven Central metadata or jar/pom inspection on 2026-10-10.
- **Ehcache.** 2.x: `net.sf.ehcache:ehcache-core` last 2.6.11 (2015-04), `net.sf.ehcache:ehcache` last 2.10.9.2 (2021-04-24). Ehcache 3 latest is **3.12.0** (Apr 2026). Jar inspection shows most classes in both the default and **`jakarta`-classifier** jars compiled for **Java 17**. The Jakarta variant must be requested via `<classifier>jakarta</classifier>`. Default artifacts remain javax. `ehcache-xml` historically depends on `javax.xml.bind` JAXB `[2.2,3)`. ([ehcache metadata](https://repo1.maven.org/maven2/org/ehcache/ehcache/maven-metadata.xml), [ehcache 3.12.0-jakarta jar](https://repo1.maven.org/maven2/org/ehcache/ehcache/3.12.0/ehcache-3.12.0-jakarta.jar), [WSS-696 (jakarta classifier)](https://issues.apache.org/jira/browse/WSS-696), [Spring Boot PR #30002 (default artifacts still javax)](https://github.com/spring-projects/spring-boot/pull/30002), [ehcache-2 metadata](https://repo1.maven.org/maven2/net/sf/ehcache/ehcache/maven-metadata.xml))
- **Caffeine** latest 3.3.0 (Sept 2026, classes target Java 11). The JCache API `javax.cache:cache-api` is still 1.1.1 (2019, and JSR-107 keeps the `javax.cache` package). ([caffeine metadata](https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/caffeine/maven-metadata.xml), [cache-api metadata](https://repo1.maven.org/maven2/javax/cache/cache-api/maven-metadata.xml))
- **JGroups.** uPortal is on 3.6.20.Final (pom 2020-04-29) via `ehcache-jgroups3replication` 1.7.4. Latest is **5.5.7.Final** (Sept 2026), compiled for **Java 17**. ([jgroups metadata](https://repo1.maven.org/maven2/org/jgroups/jgroups/maven-metadata.xml), [uPortal gradle.properties](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/gradle.properties))
- **Logging.** SLF4J latest is **2.0.20** (2.1.0-alpha1 exists). Logback latest is **1.6.5**. Log4j2 latest is 2.26.1 (3.0.0-beta2). uPortal declares `slf4jVersion=1.7.36` but `logbackVersion=1.6.3`. **Logback 1.6.3's parent POM targets Java 11 (`maven.compiler.release=11`), depends on `slf4j.version` 2.0.18, and uses `jakarta.servlet` 5.0 / optional `jakarta.mail`.** Logback 1.3 is javax and JDK 8. 1.4/1.5 use Jakarta for optional components (`SMTPAppender`, `LogbackServletContextListener`), and 1.5 needs SLF4J 2.0.x and JDK 11 at runtime. ([logback-parent 1.6.3 pom](https://repo1.maven.org/maven2/ch/qos/logback/logback-parent/1.6.3/logback-parent-1.6.3.pom), [logback download page](https://logback.qos.ch/download.html) (search snippet), [slf4j metadata](https://repo1.maven.org/maven2/org/slf4j/slf4j-api/maven-metadata.xml), [log4j-api metadata](https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/maven-metadata.xml))
- **Lucene.** Latest is **10.5.2** (Oct 2026). **Lucene 10 requires Java 21** (Elastic/Lucene committers). Lucene `main` (11.0) requires **Java 25** and removed Security Manager support. The 8 → 9 hop renamed `lucene-analyzers-*` artifacts to `lucene-analysis-*`, renamed packages in misc, sandbox and backward-codecs, and made the Directory API little-endian. The 9 → 10 hop removed `IndexSearcher.doc` / `IndexReader.document` (use `storedFields()`) and more. ([lucene-core metadata](https://repo1.maven.org/maven2/org/apache/lucene/lucene-core/maven-metadata.xml), <!-- vale ai-tells.ColonUsage = NO --><!-- citation link text kept verbatim -->[Elastic: Lucene and Java moving forward together](https://www.elastic.co/search-labs/blog/lucene-and-java-moving-forward-together)<!-- vale ai-tells.ColonUsage = YES -->, [Lucene MIGRATE.md](https://raw.githubusercontent.com/apache/lucene/main/lucene/MIGRATE.md))
- **JAXB → Jakarta XML Binding.** `jakarta.xml.bind-api` latest 4.0.5 (4.1.0-M1). `org.glassfish.jaxb:jaxb-runtime` latest **4.0.9** (classes target Java 11). ([jakarta.xml.bind-api metadata](https://repo1.maven.org/maven2/jakarta/xml/bind/jakarta.xml.bind-api/maven-metadata.xml), [jaxb-runtime metadata](https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-runtime/maven-metadata.xml))
- **Mail.** `jakarta.mail-api` 2.1.5 (2.2.0-M1). `org.eclipse.angus:angus-mail` **2.0.5** (classes target Java 8). uPortal uses `javax.mail` 1.4.7. ([jakarta.mail-api metadata](https://repo1.maven.org/maven2/jakarta/mail/jakarta.mail-api/maven-metadata.xml), [angus-mail metadata](https://repo1.maven.org/maven2/org/eclipse/angus/angus-mail/maven-metadata.xml))
- **Jakarta EE 11 APIs on Central:**
  - `jakarta.annotation-api` 3.0.0
  - `jakarta.validation-api` 3.1.1 (4.0.0-M1 exists), with `hibernate-validator` **9.1.4.Final** (classes target Java 17)
  - `jakarta.servlet-api` **6.1.0** (6.2.0-M2 exists)
  - `jakarta.servlet.jsp-api` **4.0.0**
  - `jakarta.el-api` **6.0.1**
  - `jakarta.servlet.jsp.jstl-api` 3.0.2 (3.1.0-M2), with the GlassFish impl `org.glassfish.web:jakarta.servlet.jsp.jstl` 3.0.1 (last 2023-01)
  - `jakarta.platform:jakarta.jakartaee-api` 11.0.0 (Jun 2025)
  Sources: [jakarta.servlet-api metadata](https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/maven-metadata.xml), [jsp-api metadata](https://repo1.maven.org/maven2/jakarta/servlet/jsp/jakarta.servlet.jsp-api/maven-metadata.xml), [el-api metadata](https://repo1.maven.org/maven2/jakarta/el/jakarta.el-api/maven-metadata.xml), [jstl-api metadata](https://repo1.maven.org/maven2/jakarta/servlet/jsp/jstl/jakarta.servlet.jsp.jstl-api/maven-metadata.xml), [glassfish jstl metadata](https://repo1.maven.org/maven2/org/glassfish/web/jakarta.servlet.jsp.jstl/maven-metadata.xml), [hibernate-validator metadata](https://repo1.maven.org/maven2/org/hibernate/validator/hibernate-validator/maven-metadata.xml), [jakartaee-api metadata](https://repo1.maven.org/maven2/jakarta/platform/jakarta.jakartaee-api/maven-metadata.xml)
- **Jackson.** 2.x latest **2.22.3**, which uPortal (2.22.2) tracks. **Jackson 3** latest is **3.2.3**. It has a Java 17 baseline and the new group and package `tools.jackson`, except `jackson-annotations`, which stays at `com.fasterxml.jackson.annotation` 2.x. `@JsonSerialize`/`@JsonDeserialize` move to `tools.jackson.databind.annotation`, and Java 8 modules are built in. ([Jackson 3 migration guide](https://raw.githubusercontent.com/FasterXML/jackson/main/jackson3/MIGRATING_TO_JACKSON_3.md), [jackson-databind 2 metadata](https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/maven-metadata.xml), [tools.jackson metadata](https://repo1.maven.org/maven2/tools/jackson/core/jackson-databind/maven-metadata.xml))
- **commons-fileupload2-jakarta-servlet6** is still milestone-only, **2.0.0-M5** (Feb 2026). There is no GA. ([metadata](https://repo1.maven.org/maven2/org/apache/commons/commons-fileupload2-jakarta-servlet6/maven-metadata.xml))
- **jasypt** last release 1.9.3 (May 2019) and unmaintained. uPortal and the uPortal-start CAS overlay use it. ([jasypt metadata](https://repo1.maven.org/maven2/org/jasypt/jasypt/maven-metadata.xml))
- **Apereo CAS Java client.** **4.1.1** (pom 2026-05-20) depends on `jakarta.servlet-api` **6.1.0**, uses Spring **7.0.7** (optional integration), and compiles to **Java 21** (class files major 65). uPortal is on `casClientVersion=3.6.2` (javax). ([cas-client 4.1.1 pom](https://repo1.maven.org/maven2/org/apereo/cas/client/cas-client/4.1.1/cas-client-4.1.1.pom), [cas-client-core metadata](https://repo1.maven.org/maven2/org/apereo/cas/client/cas-client-core/maven-metadata.xml))
- **HSQLDB.** Latest **2.7.4** (Nov 2024). Its main jar targets Java 11 (class major 55), and 2.7.x publishes Java 8 alternative jars. uPortal is on 2.7.4, and uPortal-start pins `hsqldbVersion=2.5.1`. ([hsqldb metadata](https://repo1.maven.org/maven2/org/hsqldb/hsqldb/maven-metadata.xml), [hsqldb.org](https://hsqldb.org/) (search snippet), [KNOX-2853 (jdk8 classifier)](https://jira.apache.org/jira/browse/KNOX-2853))
- **Apereo person-directory.** Latest **3.0.1** (pom 2022-11-24) is built on Spring **6.0.1** and Java **17**. The 2.0.x line runs to 2.0.13. uPortal and uPortal-start use 1.8.16. ([person-directory-parent 3.0.1 pom](https://repo1.maven.org/maven2/org/apereo/service/persondir/person-directory-parent/3.0.1/person-directory-parent-3.0.1.pom), [person-directory-impl metadata](https://repo1.maven.org/maven2/org/apereo/service/persondir/person-directory-impl/maven-metadata.xml))
- **Tika.** Latest **4.1.0** (Sept 2026, classes target Java 17). The 3.x line is at 3.3.2, which uPortal uses. ([tika-core metadata](https://repo1.maven.org/maven2/org/apache/tika/tika-core/maven-metadata.xml))
- **XStream** is still 1.4.21 with no JPMS plans (see Q2).
- **Pluto / Portlet API** (`portletApiDependency=org.apache.portals:portlet-api_2.1.0_spec:1.0`, `plutoVersion=2.1.0-M3`) is javax-only. It is out of scope here and covered elsewhere. ([uPortal gradle.properties](https://raw.githubusercontent.com/uPortal-Project/uPortal/master/gradle.properties))

### Inferences
- **SLF4J/Logback mismatch:** with `slf4j-api:1.7.36` declared and `logback-classic:1.6.3` pulling `slf4j-api:2.0.18`, Gradle's default conflict resolution picks 2.0.18 unless something forces 1.7.36. If 1.7.36 is forced or strict, logback 1.6 will not bind (SLF4J 1.7 uses `StaticLoggerBinder`, while Logback 1.3+ uses the SLF4J 2 `ServiceLoader` provider), and logging silently falls back to NOP. Verify with `./gradlew :uPortal-webapp:dependencyInsight --dependency slf4j-api` and align `slf4jVersion` to 2.0.x. Logback 1.6's optional Jakarta deps (`SMTPAppender`, servlet listener) mean any `LogbackServletContextListener` usage in a javax web.xml will not work on Tomcat 9.
- **Ehcache 2 + JGroups 3 replication is the hardest infra hop:** there is no drop-in path. Ehcache 3 has a different API and config XML, and clustering is Terracotta-based, not JGroups. Options include Ehcache 3 standalone (jakarta classifier) or Caffeine via Spring's `CacheManager`/JCache for local caches, with replication or invalidation re-designed (e.g., JGroups 5 directly, or rely on DB/Hazelcast). The Hibernate 2nd-level cache region factory must also change (Hibernate 4.3 → newer Hibernate uses JCache). Framework scope belongs to another researcher.
- **Lucene 8 → 10** requires Java 21 and a reindex (a Lucene major reads only N-1 indices, so 10 cannot read 8.x indices). For uPortal's portal search index, rebuilding at startup or via import is simplest.
- **CAS client 4.1** requires Java 21 and Jakarta Servlet 6.1, so it can only be adopted after the portal is on Tomcat 11 / Jakarta. On javax/Tomcat 9, stay on 3.6.x.
- **person-directory** has had no release since Nov 2022. Moving to 3.0.1 implies Spring 6 / Jakarta. The alternative is to vendor the needed DAOs. A note from outside this research: CAS 7+ absorbed person-directory into its own codebase (unverified here).

### Gaps
- Ehcache 3 + JAXB 4 (Jakarta) XML configuration compatibility was not confirmed. In older releases `ehcache-xml` pins JAXB `[2.2,3)`.
- Lucene index backward-compatibility rules were not fetched from the official docs. The N-1 rule is general knowledge.
- No current Apereo statement on person-directory's future was found.

## Q8. CAS server for local SSO in uPortal-start: 3.6.0 overlay → current CAS

### Takeaway
The bundled CAS server is **CAS 3.6.0 (Spring 3.2.18)**, deployed as a WAR overlay into the same Tomcat. Current CAS is **8.0.2 (Spring Boot 4.1, JDK 25)** and **7.3.8.x (Spring Boot 3.5, JDK 21)**, with 8.1.0-RC2 in progress. These are Jakarta and Spring Boot applications and cannot share a javax Tomcat 9 with uPortal. The practical options are:
- (a) run a generated CAS overlay as a **separate embedded-Tomcat executable WAR or Docker container**;
- (b) drop local SSO in the default dev setup and use uPortal's built-in **SimpleSecurityContext (local DB login)**, which uPortal-start already enables;
- (c) keep CAS 3.6 only until the move to a newer Tomcat.

### Cited Findings
- uPortal-start pins `casServerVersion=3.6.0`, `casSpringVersion=3.2.18.RELEASE`, `casEhcacheVersion=2.6.11`, `casSlf4jVersion=1.7.36`, `jasyptVersion=1.9.3`. `overlays/cas/build.gradle` overlays `org.jasig.cas:cas-server-webapp:${casServerVersion}@war` with `aspectjrt/weaver 1.9.7` and uses `compile`/`testCompile`, which Gradle 7 removed. (local `gradle.properties.example`, `overlays/cas/build.gradle`)
- **CAS overlay template (generated by CAS Initializr):**
  - branch **8.0**: `cas.version=8.0.2`, `springBootVersion=4.1.0`, `sourceCompatibility=25`, `baseDockerImage=azul/zulu-openjdk:25`, `appServer=-tomcat`
  - branch **7.3**: `cas.version=7.3.8.3`, Spring Boot 3.5.6, Java 21
  - branch **7.2**: 7.2.7, Spring Boot 3.4.4, Java 21
  - **master**: `8.1.0-RC2`, Spring Boot `4.2.0-M2`, Java 25, gradle-docker-plugin 9.4.0, Jib 3.5.4, freefair 9.8.0, Lombok 1.18.48
  Sources: [overlay 8.0 gradle.properties](https://raw.githubusercontent.com/apereo/cas-overlay-template/8.0/gradle.properties), [overlay 7.3](https://raw.githubusercontent.com/apereo/cas-overlay-template/7.3/gradle.properties), [overlay 7.2](https://raw.githubusercontent.com/apereo/cas-overlay-template/7.2/gradle.properties), [overlay master](https://raw.githubusercontent.com/apereo/cas-overlay-template/master/gradle.properties)
- Maven Central CAS server versions: 8.0.2 (pom 2026-09-26), 8.1.0-RC2 (2026-09-26), 7.3.8.3, 7.2.7.1. ([cas-server-core-api metadata](https://repo1.maven.org/maven2/org/apereo/cas/cas-server-core-api/maven-metadata.xml))
- The overlay README says it runs as an executable artifact with an embedded container (`./gradlew run` or `java -jar build/libs/cas.war`). It builds Docker images with Jib or Spring Boot buildpacks (`docker-compose.yml` provided) and supports AOT cache training runs. It also provides OpenRewrite recipes (`org.apereo.cas…`) for CAS version upgrades. ([cas-overlay-template README](https://raw.githubusercontent.com/apereo/cas-overlay-template/master/README.md))
- CAS v7.3.3 and 7.3.4 were released in Jan/Feb 2026, with 8.0.0 announced as upcoming. ([Apereo news: CAS 7.3.3/7.3.4](https://www.apereo.org/news/2026/apereo-cas-release-v-733-and-v734))
- uPortal-start's `etc/portal/uPortal.properties` enables both `CasAssertionSecurityContextFactory.enabled=true` and **`org.apereo.portal.security.provider.SimpleSecurityContextFactory.enabled=true`** (database-backed local accounts, `principalToken=userName`, `credentialToken=password`). CAS URLs (`cas.server=localhost:8080`, `cas.context=/cas`) are commented defaults, and `cas.ticketValidationFilter.encodeServiceUrl=false` is a workaround specific to the bundled CAS server. (local `etc/portal/uPortal.properties`)

### Inferences
- CAS 3.6 on Spring 3.2 is javax and very old. It may keep working on Tomcat 9 with Java 17+ only with extensive `--add-opens`, and it cannot run on Tomcat 10.1/11 without bytecode conversion. Spring 3.2 plus a javax→jakarta conversion is untested territory. Treat CAS 3.6 as a blocker to remove, not to upgrade.
- **Recommended for uPortal-start dev SSO:** generate a CAS 7.3.x (Java 21) or 8.0.x (Java 25) overlay with CAS Initializr, using JSON/in-memory service registry and static or JDBC (HSQLDB) authentication. Run it as a **separate process** (`./gradlew run` / `java -jar cas.war` on port 8443 or 8081), or as a Docker container via the existing `docker/` setup and the bmuschko or Jib plugin. Then point `cas.server`/`cas.context` at it. This decouples CAS's Java and Jakarta level from uPortal's. A pre-built official image is a cheaper alternative if one exists. Availability was not verified.
- **Lightest option:** make local SSO optional and default to the SimpleSecurityContext login (already enabled) for quick-start. Keep a `-PwithCas` path for the external CAS. This removes the CAS overlay subproject (and its `compile`/`testCompile`, Ehcache 2, jasypt and Spring 3 dependencies) from the default Gradle build, which simplifies the Gradle 7/8/9 hop considerably.
- Whichever option is chosen, the uPortal-side CAS client must stay 3.6.x on javax and move to 4.1.x (Java 21) after the Jakarta migration (Q7). Both talk to any CAS protocol 2/3 server, which means a CAS 7/8 server works with the 3.6 client. That rests on protocol compatibility, which is a general CAS fact not re-verified here.

### Gaps
- Official CAS 8 requirements documentation could not be fetched. The paths tried on the apereo/cas repo returned 404. Java and Spring Boot levels come from the overlay template branches.
- No lightweight official "CAS for development" Docker image or tag list was verified (Docker Hub not reachable).
- Exact uPortal docs on disabling CAS in uPortal-start were not found in the local `docs/` folder.

## Q9. Playwright (@playwright/test) latest version, browser revisions, and breaking changes since 1.58

### Takeaway
The latest **@playwright/test is 1.64.0 (2026-10-07)** (next: 1.65.0-alpha), and it requires Node ≥ 20. It bundles **Chromium 156.0.8078.4 (revision 1248)**, Firefox 157.0 and WebKit 27.2. uPortal-start's `^1.58.2` range already allows it. The breaking changes since 1.58 are small:
- **1.59:** WebKit dropped macOS 14, and `experimental-ct-svelte` was removed.
- **1.60:** long-deprecated APIs removed (`videosPath`/`videoSize`, `logger` on connect, `Locator.ariaRef`, `handle` on `exposeBinding`).
- **1.62:** dropped Debian 11 and isolated the headless clipboard.
- **1.63:** dropped Ubuntu 20.04 and froze the experimental-ct packages.
- **1.64:** device descriptors now forward `screen`.

### Cited Findings
- npm dist-tags: `latest` 1.64.0, `next` 1.65.0-alpha-2026-10-09, `beta` 1.64.0-beta. Release dates: 1.58.0 (2026-01-23), 1.58.2 (2026-02-06), 1.59.0 (2026-04-01), 1.60.0 (2026-05-11), 1.61.0 (2026-06-15), 1.62.0 (2026-07-24), 1.63.0 (2026-09-04), 1.64.0 (2026-10-07). `playwright-core` 1.64.0 `engines.node` is `>=20`. ([npm registry @playwright/test](https://registry.npmjs.org/@playwright/test), [npm playwright-core latest](https://registry.npmjs.org/playwright-core/latest))
- `browsers.json` in the published packages:
  - 1.58.2: chromium **145.0.7632.6 (r1208)**, firefox 146.0.1 (r1509), webkit 26.0 (r2248)
  - **1.64.0: chromium 156.0.8078.4 (r1248)**, chromium-headless-shell same, firefox 157.0 (r1555), webkit 27.2 (r2370)
  - The main branch is already at chromium 156.0.8078.12 (r1249).
  Sources: [playwright-core 1.64.0 tarball](https://registry.npmjs.org/playwright-core/-/playwright-core-1.64.0.tgz), [playwright-core 1.58.2 tarball](https://registry.npmjs.org/playwright-core/-/playwright-core-1.58.2.tgz), [browsers.json (main)](https://raw.githubusercontent.com/microsoft/playwright/main/packages/playwright-core/browsers.json)
- Release notes, browser versions and breaking changes per version:
  - **1.58:** removed `_react`/`_vue` selectors, the `:light` suffix and the `devtools` launch option. WebKit dropped macOS 13. Chromium 145.
  - **1.59:** WebKit dropped macOS 14, and `@playwright/experimental-ct-svelte` was removed. Chromium 147.0.7727.15.
  - **1.60:** `Locator.ariaRef()`, the `handle` option on `exposeBinding`, the `logger` option on `connect`/`connectOverCDP`, and context options `videosPath`/`videoSize` (use `recordVideo`) were removed. Deprecated `ConsoleMessage.location` `lineNumber`/`columnNumber`. Chromium 148.0.7778.96.
  - **1.61:** added Ubuntu 26.04 support. Chromium 149.
  - **1.62:** new component-testing (stories) model and headless clipboard isolated from the OS, with **Debian 11 unsupported**. Chromium 151.0.7922.34.
  - **1.63:** left **Ubuntu 20.04 unsupported**. The experimental-ct-react/vue packages are no longer updated, and Linux arm64 now uses Chrome for Testing. Chromium 153.0.8010.12.
  - **1.64:** **`screen` is now forwarded from device descriptors**, so `...devices['Desktop Chrome']` changes `window.screen` and media queries; opt out with `screen: undefined`. The `fontSize` screencast option is deprecated. Chromium 156.0.8078.4, Firefox 157.0, WebKit 27.2. Also tested against Google Chrome 155 and Microsoft Edge 155.
  Source: the [Playwright JS release notes (source)](https://raw.githubusercontent.com/microsoft/playwright/main/docs/src/release-notes-js.md) ([published](https://playwright.dev/docs/release-notes))
- uPortal-start's `package.json` pins `"@playwright/test": "^1.58.2"` and `eslint-plugin-playwright ~2.10.0`. (local `uPortal-start/package.json`)

### Inferences
- Bumping to 1.64.0 is low risk for uPortal-start's E2E tests unless they use removed selectors, `videosPath`, or rely on default screen size with device descriptors. Check `playwright.config.ts` for `...devices[...]`, and run `npx playwright install --with-deps` in CI on a supported runner (Ubuntu 22.04/24.04/26.04, not 20.04).
- Node 24.11.1 (uPortal-start's `nodejsVersion`) satisfies Playwright's Node ≥ 20 requirement.

### Gaps
- Playwright's own claim about which Chrome-for-Testing milestone r1248 maps to beyond the `browserVersion` string was not cross-checked against Chrome's release schedule. Chromium 156 by October 2026 suggests a faster Chrome cadence than the historic four weeks, which was not investigated.
