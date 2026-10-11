---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Deploy uPortal as a WAR on an external Tomcat 11

## Context and Problem Statement

uPortal is a WAR that uPortal-start deploys into an external Tomcat, next to the portlet
applications, which are separate WARs. The portlet container (Apache Pluto) dispatches requests
from the uPortal web application into the portlet web applications across servlet contexts, and
it passes container objects from one context to the other. Spring Framework 7 needs Servlet 6.1,
which Tomcat 11 and Jetty 12.1 provide. Tomcat 11 needs Java 17 or later.

Should 6.0 stay on an external servlet container, move to Spring Boot with an embedded server,
or move to Jetty?

## Decision Drivers

* The portlet WARs that uPortal-start bundles and adopters deploy must keep running in the same
  container as uPortal, with cross-context dispatch.
* Adopters already run uPortal-start on Tomcat.
* The runtime should be one that the Spring and Apereo projects build on.

## Considered Options

* An external Tomcat 11 with uPortal and the portlets as WARs
* Spring Boot with an embedded server, or an executable WAR
* Jetty 12.1, which runs `javax` (ee8) and `jakarta` (ee11) web applications side by side
* A second Tomcat 9 that runs only the portlets

## Decision Outcome

Chosen option: "An external Tomcat 11 with uPortal and the portlets as WARs", because it is the
only option that runs the portlet WARs with cross-context dispatch in a documented way.

* 6.0 is released on Tomcat 11 only. Tomcat 10.1 is used only at the Spring 6.2 test checkpoint
  (ADR 0002).
* uPortal-start provides the Tomcat configuration, with connector limits set explicitly, because
  Tomcat's defaults changed (`maxParameterCount` and `maxPartCount`).
* Spring Boot stays a candidate for 6.x inside the WAR, through `SpringBootServletInitializer`
  and `@ImportResource` for the existing XML contexts.
* Standalone tools that don't host portlets can use Spring Boot. The database copy tool
  (`uPortal-db-migrate`, ADR 0008) is a Spring Boot 4.1 command-line application with its own
  dependency platform, the Spring Boot BOM.

### Consequences

* Good, because uPortal-start and adopters keep the deployment model they know.
* Good, because Tomcat 11 still supports `crossContext` and the `sessionCookiePath="/"` setting
  that portlet containers rely on.
* Good, because Apereo CAS 8.0 and Spring Boot 4 both build on Tomcat 11.
* Bad, because adopters move their Tomcat configuration through Tomcat 9 to Tomcat 11.
* Bad, because uPortal gets none of Spring Boot's packaging, configuration or Actuator in 6.0.

### Confirmation

uPortal-start's CI deploys uPortal and the portlet WARs into Tomcat and runs every Playwright spec
against it. Before the release, those specs have to pass on Tomcat 11 with JDK 25 (ADR 0002).

## Pros and Cons of the Options

### An external Tomcat 11 with uPortal and the portlets as WARs

* Good, because cross-context dispatch is supported and documented.
* Good, because Tomcat 11 can convert `javax` WARs at deploy time, and its Jakarta migration tool
  can convert them at build time (ADR 0011).
* Bad, because adopters still install and configure Tomcat themselves.

### Spring Boot with an embedded server, or an executable WAR

* Good, because it would give uPortal Spring Boot's configuration, packaging and Actuator.
* Bad, because Spring Boot documents no way to host additional WAR contexts in its embedded
  Tomcat. The portlet WARs couldn't run next to uPortal.
* Bad, because JSPs work with Spring Boot only in WAR packaging.

### Jetty 12.1, which runs `javax` (ee8) and `jakarta` (ee11) web applications side by side

* Good, because Jetty 12.1 can dispatch from an ee11 web application into an ee8 one, and has done
  so since 12.0.8. Portlet WARs could then stay on `javax`.
* Bad, because a bug that ran dispatched servlets with the calling context's identity and class
  loader was fixed only on 30 September 2026
  ([jetty/jetty.project#15763](https://github.com/jetty/jetty.project/pull/15763)).
* Bad, because cross-context dispatch in Jetty bypasses the security handler
  ([jetty/jetty.project#11451](https://github.com/jetty/jetty.project/pull/11451)).
* Bad, because Pluto passes container objects between contexts, which would need adapters between
  `javax` and `jakarta` types.

### A second Tomcat 9 that runs only the portlets

* Good, because the portlet WARs would run unchanged.
* Bad, because Pluto's dispatch needs uPortal and the portlets in one container.
* Bad, because it keeps an end-of-life stack and a second session to manage.

## More Information

* Jetty 12.1 can get a time-boxed test if a later release needs mixed `javax` and `jakarta`
  web applications.
* Spring Boot for the whole portal becomes realistic once uPortal no longer hosts portlet WARs.
* Tomcat 9.0 support ends on 31 March 2027. A 9.1 branch without APR gets releases until the end
  of 2030. uPortal-start moved from Tomcat 8.5.100 to 9.0.122 in its build upgrade, with
  `maxParameterCount=10000` and `maxPartCount=50` set explicitly.
* [Tomcat 11 context configuration](https://tomcat.apache.org/tomcat-11.0-doc/config/context.html),
  [Spring Boot traditional deployment](https://docs.spring.io/spring-boot/how-to/deployment/traditional-deployment.html)
* Prototype references: MOD-Q1, MOD-D6, MOD-D22 (packaging row), MOD-D28, MOD-I35, MOD-I46,
  MOD-I77.
