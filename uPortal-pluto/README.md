# uPortal-pluto: Apache Pluto 2.1.0-M3 in uPortal's tree

uPortal runs portlets in [Apache Pluto](https://portals.apache.org/pluto/)
2.1.0-M3, a JSR-286 (Portlet 2.0) container, and compiles against the Java
Portlet API 2.1.0. Apache Portals moved to the Attic in 2025, so there will be
no further Pluto release, and none for Jakarta EE. uPortal therefore keeps the
source of the Pluto artifacts it uses here, so that it can move them to
`jakarta.servlet` itself (steps P5-5 and P6-1 of the 6.0 modernization).

## What is here

| Module | Imported from (Maven Central) | Ships in |
| --- | --- | --- |
| `uPortal-pluto-portlet-api` | `org.apache.portals:portlet-api_2.1.0_spec:1.0` | Tomcat `shared/lib` |
| `uPortal-pluto-container-api` | `org.apache.portals.pluto:pluto-container-api:2.1.0-M3` | Tomcat `shared/lib` |
| `uPortal-pluto-container-driver-api` | `org.apache.portals.pluto:pluto-container-driver-api:2.1.0-M3` | Tomcat `shared/lib` |
| `uPortal-pluto-taglib` | `org.apache.portals.pluto:pluto-taglib:2.1.0-M3` | Tomcat `shared/lib` |
| `uPortal-pluto-container` | `org.apache.portals.pluto:pluto-container:2.1.0-M3` | uPortal's `WEB-INF/lib` |
| `uPortal-pluto-portal-driver` | `org.apache.portals.pluto:pluto-portal-driver:2.1.0-M3` | uPortal's `WEB-INF/lib` |

These are all the Pluto artifacts uPortal and uPortal-start use, apart from
`pluto-util` (uPortal-start's build only, to rewrite portlet `web.xml` files)
and the `pluto-testsuite` WAR.

## Provenance

Each module holds the contents of the artifact's official `-sources` jar,
unchanged: `.java` files and `package.html` in `src/main/java`, everything
else (including the upstream `META-INF/LICENSE` and `META-INF/NOTICE`) in
`src/main/resources`. Left out: `META-INF/MANIFEST.MF` and
`META-INF/DEPENDENCIES`, which Maven generated, and the portlet API's
`META-INF/NOTICE.vm`, a Maven template. `.gitattributes` stores the files byte
for byte (the portlet API sources have CRLF line endings).

| `-sources` jar | SHA-1 | SHA-256 |
| --- | --- | --- |
| `portlet-api_2.1.0_spec-1.0-sources.jar` | `889fe77cb728c3b78939d52d9214e7b04c49daac` | `139b4e6d1cb944553a06dca2119566f13cd2dcc09282bce54ce9041a2b451d1b` |
| `pluto-container-api-2.1.0-M3-sources.jar` | `75bc9540f4a689122e0c1d9fd45b4128d4b04324` | `b5d91c25c0bab73f428fec172dd9dff74b40659828ba9358c9fa53b96e0508c9` |
| `pluto-container-driver-api-2.1.0-M3-sources.jar` | `8bc24be42ba27dc0e5ead1220b8a941c25389c2d` | `521de17a87268750e8bff2b3c3135a8a2cb7e06f194eb8504f4ded4e880105a2` |
| `pluto-container-2.1.0-M3-sources.jar` | `dfee63bcc1375394a7f2e4afde24728ad0cc5109` | `94a6c6768e6537e845e5403922763cff359f3454d36e61b4075b5e953e1d8301` |
| `pluto-portal-driver-2.1.0-M3-sources.jar` | `e04c8342ee148dbf6088d33f272a47d6fb9397e0` | `fa921c32fa1ae7ef06691ed01f4aab400f03c5717986badf444ba48bc6a08986` |
| `pluto-taglib-2.1.0-M3-sources.jar` | `88f2a60d99b3c20e1402d9a632f77b0c112f4eb6` | `7ec3161dc7f1b5699ef97b9ac00e46c1f13e5d4d6229bca96717c20783ad09a7` |

The SHA-1 values match the `.sha1` files on repo1.maven.org. The jars'
PGP signatures verify against the
[Apache Pluto KEYS](https://archive.apache.org/dist/portals/pluto/KEYS)
(Eric Dalquist for Pluto, David Sean Taylor for the portlet API), except
`pluto-portal-driver-2.1.0-M3-sources.jar`, which Central has no signature for
(its binary jar's signature verifies).

`./verify-import.sh` downloads the six jars again, checks their SHA-256, and
diffs them against this tree. It prints nothing while the code is unchanged,
and lists uPortal's changes once there are some.

## How it is built

* **Dependencies:** each module declares the dependencies, scopes and
  exclusions of its upstream POM (`provided` → `compileOnly`, `compile` →
  `api`), with uPortal's version where uPortal has one (Spring, SLF4J,
  Commons IO, CC/PP, JSP API) and the upstream version otherwise
  (`plutoJaxbApiVersion`, `plutoJaxbImplVersion`, `plutoJstlVersion`,
  `plutoStaxVersion` and `plutoServletApiDependency` in `gradle.properties`;
  `jaxb-api` keeps upstream's 2.1 because uPortal's 2.3.1 would change one
  compile classpath). Every resolved configuration of the build is the same as
  with the Maven Central artifacts.
* **Servlet 2.4:** the code compiles against `javax.servlet:servlet-api:2.4`,
  as upstream did. Its `ServletOutputStream` subclasses don't implement the
  abstract methods that Servlet 3.1 added.
* **Java 8:** it compiles with `--release 8`. The code predates JDK 9, which
  changed `NamespaceContext.getPrefixes` to return `Iterator<String>`, so it
  doesn't compile against the Java 11 API. Upstream shipped Java 5 bytecode.
* **Static analysis:** Spotless and Error Prone (with NullAway) skip these
  modules, so the code keeps its upstream formatting and stays diffable. SpotBugs
  runs with one baseline per module in `config/spotbugs/baseline/`, as for the
  rest of uPortal, so changes can't add findings.
* **Published as** `org.jasig.portal:uPortal-pluto-*` with uPortal's version.
  uPortal's own POMs now name these instead of the Apache artifacts.

## Changing this code

The import commit holds only upstream files, so a reviewer can check it with
`verify-import.sh`. Later changes follow these rules:

1. Change the code only for what uPortal needs: the move to `jakarta.servlet`
   (P5-5, P6-1), and bug or security fixes. Make each change its own commit,
   and say in it that it changes imported Pluto code.
2. Keep the `org.apache.pluto` and `javax.portlet` package names. Portlet
   WARs and uPortal-start's `shared/lib` rely on them.
3. Keep the Apache license headers. The Apache License 2.0 (section 4(b))
   requires each modified file to carry a prominent notice that it was
   changed, so add a comment below the header saying uPortal changed it.
4. Keep the upstream formatting, so `verify-import.sh` shows only the real
   changes.
