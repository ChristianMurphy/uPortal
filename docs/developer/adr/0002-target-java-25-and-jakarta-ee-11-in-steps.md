---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Target Java 25 and Jakarta EE 11 in 6.0, reached in tested steps

## Context and Problem Statement

uPortal 5.17 runs on Java 11, Spring Framework 4.3, Spring Security 4.2, Hibernate ORM 4.3 and
Tomcat 8.5. Open-source support has ended for Spring 4.3, for Spring 5.3 (August 2024) and for
Spring 6.2 (June 2026), which leaves Spring 7 as the only Spring line with open-source support.
Tomcat 8.5 reached end of life in March 2024, and Tomcat 9.0 support ends on 31 March 2027.

Spring Framework 7 moves to Jakarta EE 11, with Servlet 6.1 (Tomcat 11) and Jakarta Persistence
3.2 (Hibernate ORM 7). Each Spring generation also fixes the Java level, the `javax` or `jakarta`
namespace and the Jackson generation, and Hibernate 5.3 and later need Spring 5. So the
framework versions move together, not one at a time.

The published uPortal roadmap splits this work: uP6 is Java 21 with a Tomcat upgrade and
external portlets converted to web components, and uP7 drops portlets. An April 2025 discussion
([uportal-user](https://groups.google.com/a/apereo.org/g/uportal-user/c/d9ktfsL_f6E)) moved uP6 to
Java 17 on Tomcat 9, which keeps `javax`. Renovate in uPortal blocks Spring 5 and
later and Hibernate major versions
([uPortal #2948](https://github.com/uPortal-Project/uPortal/pull/2948)).

Which platform should uPortal 6.0 target, and how does the code get there in steps that reviewers
can merge one at a time?

## Decision Drivers

* Open-source support for every framework in the release.
* One breaking upgrade for adopters instead of two.
* A step builds and passes its tests on its own, so it can be its own pull request.
* Portlets keep working (ADR 0011), and existing databases upgrade (ADR 0008).
* A failure in a step should point to one cause, such as the namespace change or a Hibernate
  version.

## Considered Options

* Full Jakarta, reached through Spring and Hibernate versions that support each other
* The last `javax` versions, which are Spring 5.3, Hibernate 5.6 and Tomcat 9
* Upgrade only the build and Hibernate
* One jump straight to Spring 7 and Hibernate 7
* Non-breaking 5.x releases

## Decision Outcome

Chosen option: "Full Jakarta, reached through Spring and Hibernate versions that support each
other", because it is the only option in which every framework still has open-source support at
release, and adopters take one breaking upgrade. The 6.0 versions are:

| Component | uPortal 5.17 | uPortal 6.0 |
| --- | --- | --- |
| Java runtime | 11 | 25 (LTS) |
| Gradle | 6.9.4 | 9 (ADR 0003) |
| Spring Framework | 4.3.30 | 7.0 |
| Spring Security | 4.2 | 7 |
| Hibernate ORM | 4.3.11 | 7.4 (Jakarta Persistence 3.2) |
| Servlet container | Tomcat 8.5.100 | Tomcat 11 (Jakarta EE 11, ADR 0004) |

The code moves along Spring and Hibernate versions that support each other:

1. Spring 4.3 with Hibernate 5.2 (Spring 4.3 supports Hibernate up to 5.2).
2. Spring 5.3 with Hibernate 5.6.
3. Spring 6.2 with Hibernate 6.6 on Tomcat 10.1. This is a test checkpoint. It is merged so the
   next step starts from green code. It is never tagged or released, because Spring 6.2 has no
   open-source support.
4. Spring 7.0 with Hibernate 7.4 on Tomcat 11.

The work is split into phases P0 to P8, about fifty pull requests. Each one passes the required
checks below. Everything that can happen while uPortal still uses `javax` happens first. Four
steps can't be split further:

* P2-2, the Hibernate 5.2 upgrade (ADR 0006).
* P4-1, Spring 5.3 together with Spring Security, Spring Session and Spring Web Flow.
* P6-1 in uPortal and P6-2 in uPortal-start, the `javax` to `jakarta` switch on Spring 6.2,
  Hibernate 6.6 and Tomcat 10.1, which has to happen in both repositories and in every deployed
  portlet WAR at once.
* P7-1, Spring 7, Hibernate 7.4 and Tomcat 11.

The runtime JDK goes from 11 to 21 at P4 and to 25 by P6. 6.0 is released on Tomcat 11 only.
6.0 is a major release made of small pull requests with Conventional Commit messages (ADR 0018),
and each commit body becomes its pull request description. A separate prototype branch that is
never merged goes straight to the end state to find blockers early.

### Consequences

* Good, because adopters upgrade once, to frameworks with open-source support.
* Good, because each checkpoint separates the causes of a failure: the namespace change, Hibernate
  7, Servlet 6.1 and Jackson 3 each arrive in a different step.
* Good, because most changes happen on `javax` code that is already tested, which makes the
  `jakarta` switch mostly a scripted rename.
* Bad, because the Spring 6.2 checkpoint is work on a version that is never released.
* Bad, because 6.0 combines the uP6 and uP7 roadmap scopes and departs from the April 2025 plan and
  the Renovate pins. The community has to agree to that.
* Bad, because the namespace switch is one change across uPortal, uPortal-start's `shared/lib` and
  every portlet WAR, since a shared class loader can't mix `javax` and `jakarta`.
* Bad, because adopters need Java 25, Tomcat 11 and current database servers (ADR 0009).

### Confirmation

Each step has to pass four required checks:

* G-unit: `./gradlew build` in CI on Linux with Temurin, Zulu and Corretto, and on Windows and
  macOS.
* G-portal: every Playwright spec in uPortal-start against a running portal.
* G-db: the database upgrade harness on every supported database (ADR 0010).
* G-flows: the admin and import/export Playwright specs.

The release step P7 has to pass every Playwright spec on Tomcat 11 and JDK 25, the full CI matrix,
and G-db on every vendor. When this ADR was written, P0 (tests) and P1 (build toolchain) were done,
the copied Spring Portlet MVC from P3 was merged, and the Hibernate 5.2 step was built and passed
G-unit, G-portal and G-db on a branch.

## Pros and Cons of the Options

### Full Jakarta, reached through Spring and Hibernate versions that support each other

* Good, because Spring 7, Hibernate 7.4 and Tomcat 11 have open-source support.
* Good, because Apereo CAS 8.0 already runs Spring 7.0.8, Hibernate 7.4.5, Tomcat 11.0.26,
  Spring Security 7.1.0 and Spring Web Flow 4.0.1 on Java 25.
* Good, because the namespace change and the portlet container problem are solved once.
* Bad, because uPortal has to provide its own portlet container on `jakarta.servlet` (ADR 0011).
* Bad, because it is the most work.

### The last `javax` versions, which are Spring 5.3, Hibernate 5.6 and Tomcat 9

* Good, because it avoids the namespace change and keeps the portlet WARs as they are.
* Bad, because Spring 5.3 open-source support ended in August 2024.
* Bad, because it postpones the portlet container problem and gives adopters a second breaking
  upgrade later.

### Upgrade only the build and Hibernate

* Good, because it is the smallest change.
* Bad, because Hibernate 5.3 and later need Spring 5, so Spring has to move anyway.

### One jump straight to Spring 7 and Hibernate 7

* Good, because it has the fewest intermediate versions.
* Bad, because a failure can come from any of the changes at once, and there is no green
  checkpoint in between.
* Neutral, because the prototype runs this jump on a branch that is never merged, to find
  blockers early.

### Non-breaking 5.x releases

* Good, because adopters would get the changes gradually.
* Bad, because the Java baseline and the namespace both change, which breaks compatibility.

## More Information

* Hibernate's latest stable release when this was written was 7.4.12.Final. Hibernate 8.0 was in
  beta. Spring Framework 7.0 names Hibernate 7.1 and 7.2 as supported, and Spring Boot 4.1 pairs
  Spring 7.0 with Hibernate 7.4.
* The P6 checkpoint and P7-1 can be combined if the prototype branch shows that the step from
  Spring 6.2 to 7.0 is small.
* A prototype converted the whole built 5.17 portal, including its 12 portlet WARs, with Tomcat's
  Jakarta migration tool. It passed the same 116 of 119 Playwright tests as the `javax` portal on
  Tomcat 11.0.26 with JDK 21 and JDK 25, and on Tomcat 10.1.60 with JDK 21. The 3 failures were
  caused by the prototype's network sandbox.
* [Spring Framework versions](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions),
  [Spring Framework 7.0 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes),
  [CAS 8.0.x library versions](https://raw.githubusercontent.com/apereo/cas/8.0.x/gradle/libs.versions.toml),
  [uPortal roadmap](https://github.com/uPortal-Project/uportal-project.github.io/blob/master/roadmap.md)
* Prototype references: MOD-D1, MOD-D2, MOD-D5, MOD-D6, MOD-D9, MOD-I20, MOD-I49.
