---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Keep Spring Portlet MVC and Pluto in uPortal's source, and transform portlet WARs for Jakarta

## Context and Problem Statement

uPortal runs JSR-286 portlets in Apache Pluto 2.1.0-M3. Its own 37 portlets use Spring Portlet
MVC, and its 26 admin flows use Spring Web Flow's portlet integration. uPortal-start bundles 12
portlet WARs, and adopters deploy their own. The 6.0 platform (ADR 0002) breaks all of this:

* Spring Framework 5.0 removed Spring Portlet MVC, and Spring Web Flow 2.5 removed its portlet
  integration. Spring's release notes suggest that projects which need them write adapter classes in
  their own code, possibly derived from Spring 4.x.
* Apache Portals moved to the Apache Attic in November 2025, and `apache/portals-pluto` was
  archived in October 2025. Pluto's last release, 3.1.2, uses `javax`.
* The Jakarta Portlet 4.0 specification is unreleased, and Maven Central has no `jakarta.portlet`
  artifacts. The only Jakarta portlet container, Liferay's Plutonium 4.0.0-M1, has one maintainer
  and isn't on Maven Central. PortletMVC4Spring 6.0.0-M1 targets Spring 6.2 and Portlet 4.0, has no
  Spring 7 release, and isn't on Maven Central after 5.3.4.
* Neither Tomcat's Jakarta migration tool nor Eclipse Transformer renames `javax.portlet`. A
  converted portlet WAR links against `javax.portlet` classes compiled against `jakarta.servlet`.
  In the Portlet 2.0 API only `PortletRequest.getCookies()`, which returns servlet `Cookie`
  objects, touches the servlet API.

Sakai, another Apereo project, faced the same problem and merged its answer on 9 October 2026: it
copied Pluto into its own source tree and compiled `javax.portlet` against `jakarta.servlet`.

How do uPortal's own portlets, the bundled portlet WARs and adopters' portlets keep working on
Jakarta EE 11 and Spring 7?

## Decision Drivers

* Bundled and adopters' portlets keep working in 6.0 without changes to their source.
* No dependency on milestone artifacts that one person maintains outside Maven Central.
* uPortal's own portlets keep working at every Spring step until they are replaced (ADR 0012).
* Every part of the solution can be tested in CI, including Pluto's own conformance tests.

## Considered Options

* Copy Spring Portlet MVC and Pluto into uPortal, rebuild the Portlet 2.0 API against
  `jakarta.servlet`, and transform the portlet WARs at build time
* Liferay Plutonium 4 with Jakarta Portlet 4.0 and PortletMVC4Spring
* Keep the portlets on a second Tomcat 9
* Convert portlet WARs at deploy time with Tomcat's `legacyAppBase`
* Drop portlet support in 6.0

## Decision Outcome

Chosen option: "Copy Spring Portlet MVC and Pluto into uPortal, rebuild the Portlet 2.0 API
against `jakarta.servlet`, and transform the portlet WARs at build time", because it keeps
existing portlets running without milestone dependencies, and Sakai already runs the same design.

1. **Spring Portlet MVC in uPortal (done).** The module `uPortal-spring-portlet` holds the classes
   uPortal uses from `spring-webmvc-portlet` 4.3.30 and from Spring Web Flow 2.4.8's portlet
   packages, renamed from `org.springframework` to `org.apereo.portal.spring`. At the Spring 5.3
   step they are adapted, with PortletMVC4Spring 5.3.4 as the reference for the changes. uPortal's
   own portlets need them only until ADR 0012 replaces those portlets, and the copied classes are
   deleted before the `jakarta` switch.
2. **Pluto in uPortal (planned).** Pluto 2.1's container, driver and tag library source is imported
   unchanged while uPortal is still on `javax`, and moves to `jakarta.servlet` with the rest of
   uPortal at the switch. It stays for the bundled portlet WARs and adopters' portlets.
3. **A rebuilt Portlet 2.0 API (planned).** `javax.portlet` 2.0 compiled against
   `jakarta.servlet`, so `getCookies()` returns `jakarta.servlet.http.Cookie[]`. The jar goes in
   Tomcat's `shared/lib`.
4. **Portlet WARs transformed at build time (planned).** uPortal-start's overlay build runs
   Tomcat's Jakarta migration tool on each bundled WAR, with a narrow profile that renames only what
   Tomcat provides (servlet, JSP, EL and WebSocket) and leaves each WAR's own `javax.persistence` and
   `javax.mail` alone. A per-WAR exclude list handles broken jars, and a non-zero exit fails the
   build. A linkage scanner fails the build when a WAR references a class or method that Servlet 6
   removed.
5. **Tomcat 11 fixes in uPortal-start's overlays first (planned).** Thirteen WARs bundle JSTL 1.1.2,
   whose `<c:set>` fails on Tomcat 11, and get a current JSTL. The Spring 3.x WARs (Feedback, CAS
   3.6, ResourceServingWebapp, esup-filemanager) are upgraded or retired. NotificationPortlet's
   Spring Boot auto-configuration for an embedded server is switched off with one property. Proper
   fixes and releases in the portlet repositories follow during 6.x.

### Consequences

* Good, because existing portlet WARs keep running on Tomcat 11 without changes to their source.
* Good, because adopters' custom portlet WARs can go through the same transformation.
* Good, because copying Spring Portlet MVC on Spring 4.3 was a refactoring that left the build
  green, which splits the Spring 5 step into two smaller ones.
* Bad, because uPortal maintains its own copy of a retired portlet container and a portlet API jar
  that no specification defines.
* Bad, because transformed WARs contain end-of-life Spring 3.2, Spring 4.3 and Hibernate 3 code that
  may call removed Servlet APIs on rarely used paths. The scanner and per-portlet specs are the
  defence.
* Bad, because overlays that name the moved classes need the renames listed in
  [`docs/UPGRADING-6.0.md`](../../UPGRADING-6.0.md).
* Bad, because the `javax` to `jakarta` switch has to happen in uPortal, uPortal-start's
  `shared/lib` and every portlet WAR at once.

### Confirmation

* uPortal-start already deploys Apache Pluto's JSR-286 test suite (the `pluto-testsuite` overlay).
  A spec that requires it to pass, now on `javax` and later on the transformed WARs on Tomcat 11,
  is in progress (ADR 0010).
* The admin Playwright specs go through the Web Flow admin flows in a browser. Smoke tests for
  each bundled portlet in each mode and window state are planned (ADR 0010).
* When Spring Portlet MVC moved into uPortal, 1069 unit tests passed (10 of them new), all 37
  portlets rendered as before with unchanged logs, and every admin and portlet-options spec passed.
  The copied files are byte-identical to Spring's apart from the package rename.

## Pros and Cons of the Options

### Copy Spring Portlet MVC and Pluto into uPortal, rebuild the Portlet 2.0 API against `jakarta.servlet`, and transform the portlet WARs at build time

* Good, because Sakai merged the same design for Pluto.
* Good, because only the classes uPortal uses were copied: 69 classes and 12,462 lines, found with
  `jdeps` from every reference in uPortal's code and configuration. 15 unused Portlet MVC classes
  were left out.
* Good, because a prototype ran the whole converted 5.17 portal on Tomcat 11. It passed the same
  116 of 119 Playwright tests as the `javax` portal, all 94 registered portlets rendered, and the
  Pluto test suite results matched Tomcat 8.5 exactly. The 3 failures were caused by the
  prototype's network sandbox.
* Bad, because uPortal takes on maintenance of the copied code.

### Liferay Plutonium 4 with Jakarta Portlet 4.0 and PortletMVC4Spring

* Good, because it follows the newer portlet specification.
* Bad, because the artifacts are milestones outside Maven Central with one maintainer, and
  PortletMVC4Spring has no Spring 7 line.
* Bad, because every portlet would have to be rebuilt against Portlet 4.0, and uPortal's Pluto 2.1
  interfaces ported to Pluto 3.

### Keep the portlets on a second Tomcat 9

* Good, because the portlet WARs would run unchanged.
* Bad, because Pluto's dispatch needs the portal and the portlets in one container.
* Bad, because Tomcat 9 support ends on 31 March 2027.

### Convert portlet WARs at deploy time with Tomcat's `legacyAppBase`

* Good, because Tomcat does it automatically.
* Bad, because it uses the default settings only. A corrupt class in `icu4j-2.6.1.jar` (in the
  Calendar and NewsReader WARs) aborted the conversion of those whole WARs.
* Bad, because the result isn't a build artifact that CI can test.

### Drop portlet support in 6.0

* Good, because it removes the container problem entirely.
* Bad, because adopters' portlets would stop working, and the bundled portlets aren't replaced yet.

## More Information

* In the prototype, every failure traced back to the Jakarta Pages 4.0 removals in Tomcat 11, or to
  JDK 25 turning off XSLT extension functions. The namespace rename itself caused none. CAS 3.6, once transformed, runs on Tomcat 10.1 as is
  and on Tomcat 11 with a jar in `shared/lib` that supplies the removed `jsp.el` classes.
* `ScopingPortletSessionImpl` matched Spring's `DispatcherPortlet` class name for a workaround, and
  now matches both the copied class and Spring's class inside external portlet WARs.
* [Sakai PR #15141](https://github.com/sakaiproject/sakai/pull/15141),
  [Spring Framework 5.0 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.0-Release-Notes),
  [Apache Portals in the Attic](https://attic.apache.org/projects/portals.html),
  [Tomcat Jakarta EE migration tool](https://github.com/apache/tomcat-jakartaee-migration)
* Prototype references: MOD-D9, MOD-D13, MOD-D18 (what stays), MOD-D26, MOD-Q5, MOD-Q9, MOD-I2,
  MOD-I3, MOD-I6, MOD-I16 to MOD-I19, MOD-I48, MOD-I49, MOD-I60 to MOD-I67, MOD-I87.
