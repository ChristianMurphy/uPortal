---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Build with Gradle 9 before raising the bytecode level

## Context and Problem Statement

uPortal built with Gradle 6.9.4, which can't run on JDK 17 or later. google-java-format 1.7 and
Error Prone 2.3.4, which the build used, fail on JDK 16 and later. Current google-java-format and
Error Prone 2.43 and later need JDK 21 to run. Gradle 9.8 needs a JVM from 17 to 27 for its daemon,
and Java 11 can run Gradle only up to 8.14. Gradle 7, 8 and 9 each removed APIs that uPortal
or uPortal-start used, and uPortal-start's war overlay plugin called the Convention API that
Gradle 9 removed.

The published uPortal roadmap lists "Maven" (2025) and "Replace Gradle" (2028). Open pull
requests by the same author already covered part of the work:
[#2921](https://github.com/uPortal-Project/uPortal/pull/2921) (Gradle 6.9.4 to 8.5) and
[#2988](https://github.com/uPortal-Project/uPortal/pull/2988) (the unmaintained sherter
google-java-format plugin replaced by Spotless).

Which build tool should 6.0 use, and how can the build move to current JDKs without breaking
adopters before the runtime changes?

## Decision Drivers

* Build and test on current JDKs.
* Make the build upgrade without runtime changes, so it can go upstream before the Java baseline
  rises.
* Reuse the decisions already made in #2921 and #2988.
* Ask for little review time. Since October 2025 one maintainer has done 43 of 45 merges to
  `master`.

## Considered Options

* Gradle 9 on JDK 21 and 25, still compiling for Java 11 with `--release 11`
* Gradle 9 and a higher bytecode level in the same step
* Move the build to Maven
* Gradle 9 with the build scripts converted to Kotlin DSL in the same work

## Decision Outcome

Chosen option: "Gradle 9 on JDK 21 and 25, still compiling for Java 11 with `--release 11`",
because it moves the build to current JDKs without changing the bytecode that adopters run.

The build changed in phase P1, which is done:

* Gradle 9.8.1, with the daemon on JDK 21 (JDK 25 works too). The code compiles with
  `--release 11` and `-parameters`, which Spring 6.1 and later need for parameter names.
* `maven-publish`, with an `install` alias for publishing to the local Maven repository. The
  `jcenter` repository is gone.
* Spotless 8.10.4 with google-java-format 1.36.1 in AOSP style, with the old `verGJF` and `goJF`
  task names kept as aliases. The reformat touched 409 files and is listed in
  `.git-blame-ignore-revs`.
* Error Prone 2.50.0, SpotBugs Gradle plugin 6.5.12, JaCoCo 0.8.15, CodeNarc for Groovy 4,
  nebula.lint 21.3.0.
* Mockito 5.24.0 loaded as a Java agent, and Groovy 4.0.33 with Spock 2.4 for the Groovy tests.
* Coverage goes to Coveralls through its GitHub Action.
* Dependency versions are still declared in `gradle.properties`.

uPortal-start moved from Gradle 6.9.4 through 7.6.6 and 8.14.6 to 9.8.1, with its daemon on JDK 21
and `JAVA_HOME` still on Java 11 for Tomcat and the data tools.

The bytecode level rises later, with the runtime JDK (ADR 0002). After 6.0, the proposed order
is a `build-logic` included build with convention plugins and a `gradle/libs.versions.toml`
version catalog, then the configuration cache, then Kotlin DSL one script at a time.

### Consequences

* Good, because the build upgrade doesn't change what adopters deploy. The test count was the
  same on JDK 11, 21 and 25 (1055 tests), and the published POMs keep their coordinates and
  metadata.
* Good, because Spotless and current Error Prone and SpotBugs run in every build.
* Good, because SLF4J now resolves to 2.0.18, which Logback 1.6.3 needs.
* Bad, because building uPortal needs JDK 21, and uPortal-start needs it for its Gradle daemon.
* Bad, because source code stays at the Java 11 language level until the runtime moves, so records,
  switch expressions and text blocks wait.
* Bad, because the published POMs no longer list test-scope dependencies, and `runtimeOnly`
  dependencies now appear with runtime scope.
* Bad, because the work supersedes #2921 and conflicts with it on the WAR publication, since
  #2921 doesn't publish dependencies.

### Confirmation

CI runs `./gradlew build jacocoAggregateReport` on Ubuntu with Temurin, Zulu and Corretto on JDK
21 and 25, and on Windows and macOS with Temurin 25. `javac --release 11` rejects Java 12 and later
APIs at compile time, on any JDK. Error Prone runs on every compile, and `./gradlew spotlessCheck`
checks formatting.

## Pros and Cons of the Options

### Gradle 9 on JDK 21 and 25, still compiling for Java 11 with `--release 11`

* Good, because the build can go upstream before any breaking runtime change.
* Good, because it reuses #2921's Gradle decisions and #2988's switch to Spotless.
* Bad, because three Gradle major versions in one series need careful review.

### Gradle 9 and a higher bytecode level in the same step

* Good, because it is one change instead of two.
* Bad, because it puts a breaking runtime change into an otherwise non-breaking pull request.

### Move the build to Maven

* Good, because the roadmap lists it.
* Bad, because it ties a build tool change to the platform change, and review capacity is short.
* Bad, because Gradle 9 is needed anyway for current JDKs, and #2921 already did much of that work.

### Gradle 9 with the build scripts converted to Kotlin DSL in the same work

* Good, because Kotlin DSL gives typed accessors and IDE navigation, and it has been Gradle's
  default for new builds since 8.2.
* Bad, because it unblocks nothing on the way to 6.0, and every converted script costs review time.
* Neutral, because Gradle hasn't deprecated the Groovy DSL.

## More Information

* uPortal CI runs 38058739659 and 38058850716 were green after the P1 change, on Ubuntu with
  Temurin, Zulu and Corretto on JDK 21, and on Windows and macOS with JDK 25.
* [Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html),
  [Spotless for Gradle](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
* Prototype references: MOD-D3, MOD-D17, MOD-Q2, MOD-I7, MOD-I20, MOD-I21, MOD-I30, MOD-I34.
