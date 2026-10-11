---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Replace Joda-Time with java.time

## Context and Problem Statement

uPortal imports Joda-Time in 86 files. The event and portlet cookie entities store Joda values
through Jadira usertype, whose last release is a 2018 candidate release without
Hibernate 6 support. Spring Framework 6 dropped its Joda-Time formatting. Joda-Time itself is
still maintained, but the libraries around it block the Spring 6 and Hibernate 6 steps
(ADR 0002, ADR 0006).

How should uPortal handle dates and times in 6.0, and how does it keep the stored values of
existing databases unchanged?

## Decision Drivers

* Hibernate 6 and Spring 6 need a replacement for Jadira and for Joda-Time formatting.
* Stored timestamps and dates must read back the same after the change, on every database and in
  every JVM time zone.
* The replacement should keep working through Hibernate 7 and later.

## Considered Options

* `java.time` with JPA `AttributeConverter`s that reproduce Jadira's storage
* Keep Joda-Time and write uPortal's own Hibernate types for it
* Keep Joda-Time and Jadira

## Decision Outcome

Chosen option: "`java.time` with JPA `AttributeConverter`s that reproduce Jadira's storage",
because it removes both blockers and uses the date API that Java, Spring and Hibernate support
natively.

* The change happens while uPortal is on Hibernate 5.2 and Spring 4.3 (phase P3), where
  `java.time` support is native, and is split by module, with the event code first.
* `AttributeConverter`s reproduce Jadira's column values exactly, including its `databaseZone=jvm`
  handling of `LocalDate`. Converters are the persistence API least likely to change across
  Hibernate 6, 7 and 8.
* Spring MVC formats dates with Spring's JSR-310 support.
* The improved 6.0 schema (ADR 0008) then uses columns made for `java.time`, with explicit time
  zone semantics.

The change is planned and not yet done.

### Consequences

* Good, because Jadira and Joda-Time leave the dependency list, and Hibernate 6 is no longer
  blocked.
* Good, because `java.time` is part of the JDK.
* Bad, because 86 files change, and differences such as `withZone` handling need fixes by hand
  after any automated rewrite.
* Bad, because a mistake in a converter would shift stored times silently, so the tests have to
  cover several time zones.

### Confirmation

Round-trip tests write and read every date and time column under a JVM time zone with daylight
saving time (such as `America/Chicago`), across the daylight saving boundaries, and compare the
stored values with Jadira's. The analytics comparison in ADR 0010 runs one event stream that
crosses daylight saving, month and year boundaries in several time zones through 5.17 and the new
build, and compares the aggregation tables. When the change is done, a search of uPortal's sources
for `org.joda.time` imports finds none.

## Pros and Cons of the Options

### `java.time` with JPA `AttributeConverter`s that reproduce Jadira's storage

* Good, because converters are a JPA API, stable across Hibernate versions.
* Good, because OpenRewrite's `NoJodaTime` recipe can do most of the rewrite locally, and its diff
  gets reviewed.
* Bad, because every converter needs tests that prove it stores what Jadira stored.

### Keep Joda-Time and write uPortal's own Hibernate types for it

* Good, because the Java code would change less.
* Bad, because Hibernate's `UserType` interface changes again in 7.0, so the types would need
  rewriting twice.
* Bad, because Spring 6 no longer formats Joda values, so the web layer would need its own
  formatters too.

### Keep Joda-Time and Jadira

* Good, because nothing changes now.
* Bad, because Jadira doesn't support Hibernate 6, so the Hibernate 6 step can't be taken.

## More Information

* [Hibernate 5.2 migration guide](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc)
  on `java.time` support, and
  [OpenRewrite NoJodaTime](https://docs.openrewrite.org/recipes/java/joda/time/nojodatime).
* Prototype references: MOD-D11 (6.0 timestamp columns), MOD-D22 (dates row), MOD-I5.
