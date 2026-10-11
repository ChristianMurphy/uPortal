---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Make uPortal's local login the uPortal-start default, with CAS as an option

## Context and Problem Statement

uPortal-start deploys a CAS 3.6.0 server overlay, built on Spring 3.2 and Servlet 2.4, into the
same Tomcat as uPortal, and logs users in through it by default. CAS 3.6 is end of life. Current
CAS releases (7.3 and 8.0) are Jakarta-only, and the CAS 8.0 overlay targets Spring Boot 4.1 on
Java 25, so no current CAS release can run in the same Tomcat as a `javax` uPortal. Transformed with the
Jakarta migration tool, CAS 3.6 runs on Tomcat 10.1 as is, and on Tomcat 11 with a jar in
`shared/lib` that supplies the removed `jsp.el` classes. CAS 3.6 can serve until the Jakarta checkpoint,
but it remains end of life.

The CAS overlay also hard-codes HSQLDB in `deployerConfigContext.xml`, so adopters who switch
databases have to edit it by hand. uPortal-start already enables uPortal's local login
(`SimpleSecurityContextFactory`), and the Playwright `auth` spec exercises CAS.

How should a fresh uPortal-start portal log users in during and after the move to Jakarta?

## Decision Drivers

* A login method that works at every step of ADR 0002, on every runtime JDK.
* CAS remains the tested production integration, since many adopters use it.
* uPortal's Java and Jakarta level shouldn't be tied to the CAS server's.
* The quick start should need as little setup as possible.

## Considered Options

* uPortal's local login by default, with CAS 8 as an opt-in container that CI also runs
* Upgrade the CAS overlay in uPortal-start
* Keycloak in place of CAS
* Keep CAS 3.6, transformed for Jakarta

## Decision Outcome

Chosen option: "uPortal's local login by default, with CAS 8 as an opt-in container that CI also
runs", because local login already exists and works on every step, and a separate CAS container
keeps CAS tested without coupling its Java and Jakarta level to uPortal's.

* A fresh uPortal-start portal logs users in with uPortal's local login.
* CAS 8 runs as a separate service, from the `apereo/cas` image if a CAS 8.0 tag is published, or
  from a CAS Initializr overlay otherwise. Adopters opt in to it.
* CI starts the CAS service and runs the `auth` spec against it.
* uPortal talks to CAS through java-cas-client 4.x once it is on Jakarta: 4.0 at the Spring 6.2
  checkpoint, and 4.1, which needs Java 21 and Servlet 6.1, with Spring 7.
* The change goes in before uPortal-start's runtime JDK moves past 11, because CAS 3.6 doesn't
  run after that move.

A workstream for this change has started and isn't merged yet.

### Consequences

* Good, because the quick start no longer depends on an end-of-life CAS server.
* Good, because CAS can be upgraded on its own schedule.
* Good, because the CAS overlay's hard-coded HSQLDB setting goes away.
* Bad, because adopters who use the bundled CAS overlay have to switch to the CAS container or to
  their own CAS server.
* Bad, because whether Apereo publishes a CAS 8.0 image tag hasn't been confirmed. The overlay is
  the fallback.

### Confirmation

uPortal-start's CI runs the Playwright login specs against local login, and runs the `auth` spec
against the CAS container.

## Pros and Cons of the Options

### uPortal's local login by default, with CAS 8 as an opt-in container that CI also runs

* Good, because local login is already in uPortal and enabled in uPortal-start.
* Good, because the CAS server's platform and uPortal's are independent.
* Bad, because the quick start no longer shows a CAS login out of the box.

### Upgrade the CAS overlay in uPortal-start

* Good, because the setup stays the same for adopters.
* Bad, because current CAS needs its own Jakarta and Spring Boot level, which would have to match
  uPortal's at every step, in the same Tomcat.

### Keycloak in place of CAS

* Good, because Keycloak is an open-source identity server that runs as its own container.
* Bad, because Keycloak speaks the CAS protocol only through a third-party extension that lacks
  proxy tickets.

### Keep CAS 3.6, transformed for Jakarta

* Good, because it runs on Tomcat 10.1 and, with the extra jar, on Tomcat 11.
* Bad, because CAS 3.6 is end of life and depends on classes that Tomcat 11 removed.

## More Information

* Apereo describes its `apereo/cas` images as meant for quick starts and demos.
* [CAS overlay template 8.0](https://github.com/apereo/cas-overlay-template/tree/8.0),
  [cas-webapp-docker](https://github.com/apereo/cas-webapp-docker),
  [java-cas-client](https://github.com/apereo/java-cas-client)
* Prototype references: MOD-Q3, MOD-D30 (workstream), MOD-I9, MOD-I36, MOD-I44, MOD-I67, MOD-I85.
