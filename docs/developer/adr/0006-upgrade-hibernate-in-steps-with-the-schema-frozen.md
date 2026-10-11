---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Upgrade Hibernate in steps with the 5.17 schema frozen

## Context and Problem Statement

uPortal 5.17 uses Hibernate ORM 4.3.11 and creates its schema with Hibernate's DDL generation. The
6.0 target is Hibernate 7.4 (ADR 0002). Spring 4.3 supports Hibernate up to 5.2, Spring 5.3 runs
5.6, and Spring 6 needs 6.1 or later, so Hibernate moves in the same steps as Spring.

Every Hibernate major version changes defaults that affect existing databases:

* The schema tooling behind `dataInit` (`DataSourceSchemaExport`, `HibernateDbLoader`,
  `FixedDatabaseMetadata`) uses Hibernate 4 APIs that 5.0 removed.
* Newer dialects use native sequences on SQL Server and MariaDB, where 5.17 databases hold IDs in
  one-row tables. 5.17's generators on SQL Server, MySQL and MariaDB are all 49 such tables.
* Hibernate 5.x hashes foreign key names differently from 4.3, and Hibernate users report that
  `hbm2ddl update` on a 4.x schema created duplicate foreign keys under new names.
* On PostgreSQL, 4.3 stored large-object OIDs in `text` columns, while 5.6.2 and later create `oid`
  columns. Hibernate 5.2.9 and 5.2.10 stored PostgreSQL LOB strings in a format no other version
  reads.
* Hibernate 6.2 changed time zone storage, 6.0 removed string `@Type` names and `@TypeDef`, and
  7.0 removed Hibernate's `@Index`, `@IndexColumn` and `@LazyCollection`.

How can uPortal move through Hibernate 5.2, 5.6, 6.6 and 7.4 without changing the schema of an
existing database by accident?

## Decision Drivers

* An existing 5.17 database must keep working at every step, with no manual SQL.
* A code regression must not be mixed up with an intended schema change.
* Each step should be one pull request that passes the database checks on every vendor.

## Considered Options

* Step through Hibernate 5.2, 5.6, 6.6 and 7.4 with the 5.17 schema frozen, then change the schema
  once with Hibernate 7
* Accept each version's new defaults and let `hbm2ddl update` adjust existing databases
* Freeze the 5.17 schema forever
* Jump straight to Hibernate 7.4

## Decision Outcome

Chosen option: "Step through Hibernate 5.2, 5.6, 6.6 and 7.4 with the 5.17 schema frozen, then
change the schema once with Hibernate 7", because it is the only option that keeps every step
comparable with 5.17 and still ends with a cleaner schema.

* Until the Hibernate 7 step, every intermediate version produces exactly the 5.17 schema, which
  the database checks measure as zero drift (ADR 0010). A block of settings in
  `hibernate.properties` keeps 4.3's choices. The plan for each later Hibernate version is to add
  the legacy setting for every default it changes.
* uPortal never runs `hbm2ddl update` against an upgraded database.
* The improved 6.0 schema comes in its own step after Spring 7 and Hibernate 7.4, and the freeze
  settings are removed there. The copy tool moves existing databases to it (ADR 0008).
* The first step targets Hibernate 5.2.18 or later, never 5.2.9 or 5.2.10.

The Hibernate 5.2 step (P2) is built on a branch in four commits:

1. `refactor(rdbm)`: schema generation goes behind a uPortal schema tool interface, still on 4.3.
2. `build!`: upgrade to Hibernate 5.2.18 with the freeze block. A `PortalDialectResolver` keeps 4.3's
   dialects (PostgreSQL 10 and later get `PostgreSQL81TextClobDialect`, MySQL and MariaDB get
   `MySQL55Dialect`, SQL Server gets `SQLServer2005Dialect`). A `PortalImplicitNamingStrategy`
   reproduces 4.3's `FK_` and `UK_` hashes, with the standard physical naming and the pooled ID
   optimizer. Jadira 6.0.1.GA stores Joda-Time values as before.
3. `refactor(persistence)`: JPA `@Table(indexes)` with the same index names, `@OrderColumn`, and
   JPA fetch settings replace the Hibernate annotations that 7.0 removes.
4. `fix(persistence)`: `PREF_VALUE` (portlet preferences) and the PostgreSQL LOB columns get
   explicit column types.

The step merges only after it also passes the data checks that compare its output with 5.17's
(ADR 0010).

### Consequences

* Good, because any schema difference during P2 to P6 is a bug, which the database checks catch.
* Good, because the dangerous defaults (sequence support, naming, LOB types, time zones) are
  pinned and tested before they could change data.
* Bad, because the freeze block grows with every Hibernate version and has to be removed with care
  in the Hibernate 7 step.
* Bad, because databases keep the 5.17 cruft (one-row ID tables, OIDs in `text` columns) until the
  6.0 schema.

### Confirmation

The database upgrade harness (G-db) records the schema of each vendor and compares structure,
data, constraint names and ID generators with 5.17's (ADR 0010). On the Hibernate 5.2 branch:

* G-unit went from 1137 to 1152 tests, with no failures.
* G-portal passed 119 of 124 Playwright tests twice. The 5 failures were tests that the prototype's
  network sandbox blocks.
* G-db didn't report drift in structure, data or names on HSQLDB, PostgreSQL 16, MySQL 8.4, MariaDB 10.6
  and 11.4, SQL Server 2022 and Oracle 23. The only generator change was the dead
  `hibernate_sequences` declarations, which were never created on any database.
* Table dumps after `dataInit` on 4.3 and on 5.2 were identical on PostgreSQL, MySQL and SQL
  Server.
* A PostgreSQL 16 database filled by 4.3 ran the 5.2 portal with no drift, and new IDs stayed above
  4.3's.

## Pros and Cons of the Options

### Step through Hibernate 5.2, 5.6, 6.6 and 7.4 with the 5.17 schema frozen, then change the schema once with Hibernate 7

* Good, because every step has the same reference schema.
* Good, because the schema tooling rewrite goes in on Spring 4.3, without a framework change.
* Bad, because each step needs its own legacy settings, found and tested per vendor.

### Accept each version's new defaults and let `hbm2ddl update` adjust existing databases

* Good, because it doesn't need freeze settings.
* Bad, because new dialects would switch SQL Server and MariaDB to sequences, and inserts would
  then fail or reuse IDs.
* Bad, because `hbm2ddl update` adds foreign keys under new names next to the old ones.

### Freeze the 5.17 schema forever

* Good, because it is the least work in 6.0.
* Bad, because uPortal keeps the one-row ID tables and the PostgreSQL OIDs in `text` columns, which
  `vacuumlo` can delete.

### Jump straight to Hibernate 7.4

* Good, because it skips the intermediate versions.
* Bad, because Spring would have to jump to 7 in the same change, and no checkpoint separates the
  causes of a failure.

## More Information

* Upgrading the 4.3 portal to 5.2 found one bug that fresh-install tests missed. Hibernate 4.3
  applied `@Type(nullSafeString)` to map keys as well as values, and 5.2 applies it to values only.
  Upgraded portals lost their tab external IDs until `@MapKeyType` was added to the four affected
  collections, with tests on the stored format. The row-count check of the harness didn't see
  it, so the step now also has to pass the value comparison in ADR 0010.
* Hibernate 7.4 needs at least PostgreSQL 14, MariaDB 10.6, SQL Server 2014, Oracle 19, MySQL 8 and
  HSQLDB 2.6.1. uPortal 6.0 requires newer servers anyway (ADR 0009).
* Which tool handles schema changes after 6.0 (Flyway or Liquibase) is still open. Liquibase 5.0
  moved to the Functional Source License, and Flyway's open-source edition is Apache-2.0.
* [Hibernate 5.2 migration guide](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc),
  [Hibernate 6.0 migration guide](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc),
  [Hibernate 7.0 migration guide](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
* Prototype references: MOD-D5, MOD-D11, MOD-D33, MOD-Q4, MOD-I1, MOD-I14, MOD-I23 to MOD-I26,
  MOD-I43, MOD-I120 to MOD-I127.
