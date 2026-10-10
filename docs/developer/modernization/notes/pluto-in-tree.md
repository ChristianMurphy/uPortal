# P3-2: Apache Pluto 2.1.0-M3 in uPortal's tree

Handoff note for plan step P3-2 (research report, WS8b): import the source of
Apache Pluto 2.1.0-M3 and of the `javax.portlet` API unchanged, and build
uPortal against it, with no change in behaviour. It prepares P5-5 (a hybrid
`javax.portlet` API on `jakarta.servlet`) and P6-1 (the in-tree Pluto on
jakarta).

Branches: `ccr-43ba0785-hzlrj5-pluto` in uPortal and in uPortal-start.

## What changed

### uPortal

| Commit | What |
| --- | --- |
| `49e81c5` `build: import apache pluto 2.1.0-M3 and portlet api 2.1.0 sources` | The six `-sources` jars, unchanged, in `uPortal-pluto/*/src/main/{java,resources}`. Upstream files only (330 files). |
| `0b31bcd` `build: build the in-tree apache pluto modules` | Six subprojects, README, NOTICE, `verify-import.sh`, SpotBugs baselines, root NOTICE credit, STATIC_ANALYSIS note. |
| `97463b0` `build: build uportal against the in-tree pluto and portlet api` | 24 build scripts switch from the Maven Central artifacts to the projects; `plutoVersion` and `portletApiDependency` go. |
| `a27321a` | Merge of the integration branch (one docs commit). |
| (this note) | `docs:` handoff note and `compare-classes.py`. |

### uPortal-start

| Commit | What |
| --- | --- |
| `521c61b` `test: run the pluto 2.0 test suite through uportal` | `tests/ux/portlets/pluto-testsuite.spec.ts`: drives test-portlet-1 and test-portlet-2 like the spike's `pluto-testsuite.sh` and pins the 5.17 result. |

No other uPortal-start change: `shared/lib` keeps Apache's jars (see below).

## Inventory: which Pluto artifacts are used where

| Artifact | uPortal | uPortal-start |
| --- | --- | --- |
| `org.apache.portals:portlet-api_2.1.0_spec:1.0` | `compileOnly`/`testImplementation` in 20 modules; db-migrate entity scan (21 build scripts) | Tomcat `shared/lib`; overlays' `shell` classpath; 4 overlays' `impexp` |
| `pluto-container-api` | `compileOnly`/`testImplementation` in 18 modules; db-migrate entity scan | `shared/lib` |
| `pluto-container-driver-api` | `compileOnly` in uPortal-web, -portlets, -rendering; webapp tests | `shared/lib`; `shell` classpath |
| `pluto-taglib` | `compileOnly` in uPortal-web | `shared/lib` |
| `pluto-container` | `api` of uPortal-rendering (WAR), `compileOnly` in -url, -utils-url | in the uPortal WAR |
| `pluto-portal-driver` | `api` of uPortal-rendering (WAR) | in the uPortal WAR |
| `pluto-util` | none | `buildSrc` (GradlePlutoPlugin rewrites portlet `web.xml`) |
| `pluto-testsuite` WAR | none | `overlays/pluto-testsuite` |

All six of the first rows are imported: four are what the spike transformed
in `shared/lib`, two are in the WAR. `pluto-util` and the testsuite WAR stay
external. They are build-time tooling and a test fixture, and P6 will
transform them like any other WAR or plugin dependency.

Pluto itself compiles against `portlet-api_2.0_spec`; uPortal deploys the
2.1.0 API jar (Apache Portals' 2015 Portlet 2.1 API). The 2.1.0 jar is the one
imported, because it is the one that runs.

## Provenance and faithfulness

* **Source:** the official `-sources` jars from Maven Central. SHA-1 matches
  repo1.maven.org's `.sha1` for all six. PGP signatures verify against
  [Apache Pluto's KEYS](https://archive.apache.org/dist/portals/pluto/KEYS)
  for 11 of 12 jars (sources and binaries). Central has no `.asc` for
  `pluto-portal-driver-2.1.0-M3-sources.jar`; its SHA-1 matches and its binary
  jar's signature verifies. SHA-256 values are in `uPortal-pluto/README.md`.
* **Tree vs jars:** `uPortal-pluto/verify-import.sh` re-downloads the jars,
  checks SHA-256, and diffs them against the tree. It is silent (exit 0); the
  only files not imported are `META-INF/MANIFEST.MF`, `META-INF/DEPENDENCIES`
  and portlet-api's `META-INF/NOTICE.vm`. All 302 `.java` files carry their
  ASF header. The portlet API sources are CRLF; `uPortal-pluto/.gitattributes`
  (`* -text`) keeps them byte for byte.
* **Built classes vs Apache's jars** (`notes/pluto-in-tree/compare-classes.py`,
  javap from JDK 21):

  | Module | Classes | `javap -protected -s` diffs | Methods | Bytecode identical | Differ, explained by javac | Unexplained |
  | --- | --- | --- | --- | --- | --- | --- |
  | portlet-api | 61 = 61 | 0 | 615 | 614 | 1 | 0 |
  | container-api | 56 = 56 | 0 | 472 | 471 | 1 | 0 |
  | container-driver-api | 19 = 19 | 0 | 114 | 113 | 1 | 0 |
  | container | 87 = 87 | 0 | 1148 | 1095 | 53 | 0 |
  | portal-driver | 69 = 69 | 0 (1 anonymous class header) | 660 | 645 | 15 | 0 |
  | taglib | 30 = 30 | 0 | 164 | 152 | 12 | 0 |

  **The public and protected API is identical.** The only member-level
  difference is a synthetic `private static $values()` in each of the three
  enums (javac 15+). `ToolTips$1`, an anonymous class, is no longer `final`
  (javac 9+); it isn't API.
* **Compiler-related bytecode differences** (the 83 differing methods):
  * Class files are version 52 (Java 8), not 49 (Java 5). See "Findings".
  * Enum static initialisers call `$values()`.
  * A `checkcast` after calls that return a type variable (modern javac
    casts at the call site).
  * `toString()`/`getClass()` called through the receiver's static type
    (`invokeinterface X.toString`) instead of `Object`.
  * Adjacent string literals folded into one constant; `ldc_w` vs `ldc`.
  * Branch targets and exception-table offsets that moved because of the
    above.

  None changes behaviour. The script classifies each differing method and
  exits 1 if any difference is outside these rules.
* **Dependency resolution:** I dumped every resolvable configuration of every
  project before and after (810 configurations in 62 projects, 27,937
  entries). After mapping each Apache artifact to its in-tree project, **there
  are zero differences**. The WAR's `WEB-INF/lib` swaps `pluto-container` and
  `pluto-portal-driver` for `uPortal-pluto-container` and
  `uPortal-pluto-portal-driver`, and nothing else changes. Getting there took
  mirroring Pluto's parent-POM exclusion of `stax-api` from `stax`, and
  keeping Pluto's `jaxb-api` 2.1 (uPortal's 2.3.1 changed
  uPortal-utils-url's compile classpath).
* **Published POMs:** uPortal's POMs name
  `org.jasig.portal:uPortal-pluto-container` and `-portal-driver` (compile, with
  the same exclusions on uPortal-rendering) where they named the Apache
  artifacts. The `uPortal-pluto-*` POMs carry the scopes and exclusions of
  Pluto's POMs.

## Build decisions

* **Layout:** `uPortal-pluto/uPortal-pluto-{portlet-api,container-api,
  container-driver-api,container,portal-driver,taglib}`, a grouping parent that
  isn't published (like `uPortal-api`). Plain `src/main` rather than P3-1's
  `src/vendored`, because whole modules are imported, not a subset mixed with
  uPortal code.
* **Servlet 2.4 at compile time** (`plutoServletApiDependency`), as Pluto's POMs
  declare: `DummyServletOutputStream`, `PrintWriterServletOutputStream` and
  an anonymous class in `HttpServletPortletResponseWrapper` don't implement
  the abstract `isReady()`/`setWriteListener()` Servlet 3.1 added. That was
  already true of Apache's jars running on Tomcat 9.
* **`--release 8`** for these modules only. `PortletAppDescriptorServiceImpl`
  implements `NamespaceContext.getPrefixes` returning `Iterator<?>`, which no
  longer compiles against the JDK 9+ signature (`Iterator<String>`). JDK 21
  and 25 javac both accept `--release 8` (`-Xlint:-options` silences the
  obsolescence warning).
* **Static analysis** (following P3-1, which skipped Spotless for vendored
  code):
  * Spotless: skipped.
  * Error Prone and NullAway: off for these modules (`options.errorprone.enabled
    = false`). NullAway's `OnlyNullMarked` would ignore the code anyway, but
    Error Prone upgrades shouldn't force edits to imported code.
  * SpotBugs: runs, with its own baseline per module (84 findings: portlet-api
    4, container-api 1, container-driver-api 12, container 26, portal-driver
    32, taglib 9). P5-5/P6-1 changes to this code then can't add findings,
    the same ratchet as everywhere else.
* **Versions:** uPortal's variables where it has them (Spring, SLF4J, Commons
  IO, CC/PP, JSP API); new `plutoJaxbApiVersion`, `plutoJaxbImplVersion`,
  `plutoJstlVersion`, `plutoStaxVersion` otherwise.

## uPortal-start `shared/lib`

**Decision: `shared/lib` keeps Apache's `portlet-api_2.1.0_spec`,
`pluto-container-api`, `-driver-api` and `-taglib` jars for now.**

* The in-tree classes have the same API (above), and uPortal compiles against
  them, so both sides of the shared classloader agree.
* uPortal-start's `master` builds against released uPortal versions, which
  don't publish `uPortal-pluto-*` yet. Switching `gradle/tasks/tomcat.gradle`
  (and the overlays' `shell`/`impexp` classpaths) to
  `org.jasig.portal:uPortal-pluto-*:${uPortalVersion}` has to wait for a uPortal
  build that publishes them, i.e. the 6.0 line.
* **Switch them in P5-5**, when the API jar first differs (the hybrid
  `Cookie`), in the same cross-repo change. Until then the WAR's
  `uPortal-pluto-container`/`-portal-driver` are the in-tree code that actually
  runs.

## Gates

| Gate | Result |
| --- | --- |
| uPortal CI (`ccr-43ba0785-hzlrj5-pluto`) | run 38089044859: _pending_ |
| uPortal DB integration | run 38089044904: _pending_ |
| uPortal-start CI | run 38089219745: _pending_ |
| uPortal-start Integration, uPortal from this branch (after) | run 38089219769: _pending_ |
| uPortal-start Integration, uPortal from the integration branch (before) | run 38089226181 (workflow_dispatch, `uportal-ref=ccr-43ba0785-hzlrj5`): _pending_ |
| Pluto testsuite before / after | _pending_ |

**Local unit tests (sandbox, JDK 21, `./gradlew test --rerun` in both trees):**
identical per module before and after, in all 32 test tasks: 1,148 run, 1,069
passed, 0 failed, 79 skipped (78 of the skips are in uPortal-webapp, the same
on both sides). Also run locally: `build` (green in both trees),
`spotlessCheck`, `spotbugsMain` on the six new modules plus uPortal-rendering
and uPortal-url, markdown lint, and uPortal-start `npm run lint` (tsc,
type-coverage 100%, eslint on the new spec).

The proxy refuses jitpack.io (organisation policy, MOD-I51), where
`com.github.ci-rt:google-visualization-java:release-1.1.3` lives, so
uPortal-web and everything after it don't build in a cloud session as is.
Following the coordinator's workaround, the official jar was taken from
uPortal's released 5.17.9 WAR on Central (SHA-256 `eca09172…bad63`) and
installed into mavenLocal, not committed. Its embedded POM is
`com.github.ci-rt:visualization-datasource:1.1.3`, so it was installed with
the coordinates the build asks for.

## Findings

* **F1 (Servlet 3.1 gap):** Pluto's `ServletOutputStream` subclasses don't
  implement `isReady()`/`setWriteListener()`. They work today because nothing
  calls those methods. On `jakarta.servlet` (P6-1) they must be implemented,
  since Servlet 6 compiles them as abstract. That is a real source change to
  Pluto for P6-1 that the bytecode transform hid.
* **F2 (JDK 9 API):** `PortletAppDescriptorServiceImpl.XPathNamespaceContext.getPrefixes`
  returns `Iterator<?>`. It only compiles with `--release 8`. P6-1 (or P5-5) can
  fix it with a one-line `Iterator<String>` change and move the modules to
  `--release 11`, recorded as a uPortal change per the README's policy.
* **F3 (API version mismatch):** Pluto 2.1.0-M3 was built against the 2.0
  portlet API; uPortal ships the 2.1.0 API jar. Harmless (2.1.0 is a
  superset), but it is why the in-tree container compiles against
  `uPortal-pluto-portlet-api`, not a 2.0 jar.
* **F4 (sandbox, known as MOD-I51):** jitpack.io is refused by the proxy, so
  uPortal-web can't compile in a cloud session until the jar is installed
  into mavenLocal (see Gates). The embedded POM has different coordinates
  from the ones uPortal asks for, which the workaround has to correct.
* **F5 (upstream data):** portlet-api's upstream `META-INF/NOTICE` has a
  corrupted copyright sign (U+FFFD). The imported copy keeps it; uPortal's
  combined `uPortal-pluto/NOTICE` writes "©".

## Proposed LOG entries

* **MOD-D?: P3-2 delivered — Pluto 2.1.0-M3 and the portlet API in tree.**
  * Six `uPortal-pluto-*` modules, imported unchanged from the signed
    `-sources` jars on Central (`verify-import.sh`).
  * Public/protected API identical. 83 of 3,173 methods differ, all
    explained by javac (`compare-classes.py`).
  * 810 resolved configurations unchanged.
  * Built with Servlet 2.4 and `--release 8`; Spotless and Error Prone off;
    SpotBugs baselines with 84 findings.
  * `shared/lib` keeps Apache's jars until P5-5.
  * Gates: (run IDs).
* **MOD-I?: Pluto doesn't implement Servlet 3.1's `ServletOutputStream`
  methods** (F1). P6-1 must add `isReady`/`setWriteListener` to three classes.
* **MOD-I?: Pluto needs `--release 8`** (F2). Fix `getPrefixes` and move to 11
  when the code is first changed.
* Add to **MOD-I51**: the jar in the 5.17.9 WAR carries a
  `visualization-datasource:1.1.3` POM, so mavenLocal needs one written with
  `google-visualization-java:release-1.1.3` (F4).

## What's left

* **P5-5:** the hybrid API (`Cookie` from `jakarta.servlet` in 4 classes of
  `uPortal-pluto-portlet-api`), and switching uPortal-start's `shared/lib` to
  `uPortal-pluto-*`.
* **P6-1:** move `uPortal-pluto-*` to `jakarta.servlet` (F1, F2).
* If the other workstream's Pluto testsuite spec lands, keep one of the two
  specs.
