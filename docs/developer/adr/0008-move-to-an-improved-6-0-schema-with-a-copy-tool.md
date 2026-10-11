---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Move to an improved 6.0 schema with a cross-vendor copy tool

## Context and Problem Statement

The uPortal 5.17 schema carries choices from Hibernate 4.3 that cause problems today:

* On SQL Server, MySQL and MariaDB, all 49 ID generators are one-row tables. 49 `@TableGenerator`
  declarations are dead, because no vendor creates the table they name.
* On PostgreSQL, `UP_RAW_EVENTS.EVENT_DATA` and `UP_ANALYTICS_EVENTS.EVENT_DATA` are `text`
  columns that hold large-object OIDs. After one portal run, `vacuumlo -n` reported that it would
  remove 720 large objects that were still in use. Notification and SimpleContent portlet tables
  have the same problem.
* Timestamps are stored through Joda-Time and Jadira (ADR 0007), and MySQL and MariaDB columns
  have no fractional seconds (34 columns).
* Constraint and index names are hashes, some foreign keys have no index, and MySQL and MariaDB
  need `lower_case_table_names=1`.

ADR 0006 keeps this schema frozen while Hibernate goes from 4.3 to 7.4. How should a 5.17
database get to a better 6.0 schema without manual SQL on each vendor?

## Decision Drivers

* No manual SQL for adopters, on any supported vendor.
* A failed upgrade must leave the 5.17 database usable.
* Event and analytics history should come along by default.
* Adopters should be able to change database vendor during the upgrade.
* The result must match a fresh 6.0 install exactly.

## Considered Options

* A new 6.0 schema, filled by a tool that copies a 5.17 database into it on any supported vendor
* In-place migrations per vendor, such as Flyway scripts for five vendors
* Both a copy tool and in-place migrations
* uPortal's XML export and import
* Keep the 5.17 schema forever

## Decision Outcome

Chosen option: "A new 6.0 schema, filled by a tool that copies a 5.17 database into it on any
supported vendor", because it is the only option that leaves the old database untouched, works
across vendors, and yields a schema identical to a fresh install.

The 6.0 schema has:

* Native sequences on HSQLDB, PostgreSQL, MariaDB, SQL Server and Oracle. MySQL has no sequences, so it keeps one-row
  generator tables that the copy tool positions, unless the index workload (ADR 0009) shows that
  `AUTO_INCREMENT` works better there.
* LOB and text column types that match each vendor.
* `java.time` timestamp columns with explicit time zone semantics, and fractional seconds on MySQL
  and MariaDB (`DATETIME(6)`).
* Readable constraint and index names, the dead objects dropped, indexes on foreign keys, and
  MySQL without the `lower_case_table_names` requirement.
* Native JSON, UUID and Boolean types where current servers have them (ADR 0009).

The copy tool reads a 5.17 database on any supported vendor and writes the 6.0 schema on any
supported vendor. The old database is never written to, so rolling back means pointing the portal
at it again. Event and analytics history is copied by default and streamed, with an option to
leave out raw events.

The tool came first as a 5.17 to 5.17 copy across vendors, which is already useful to 5.x
adopters and tests the hard parts before the 6.0 changes go in. That version is in the code:

* `uPortal-db-migrate` is a standalone Spring Boot 4.1 command-line application with Spring Batch
  6, Spring JDBC and HikariCP, compiled for Java 21. No other uPortal module is on its class path,
  and the JDBC drivers are supplied from outside the jar.
* uPortal-start runs it as `./gradlew dbMigrate -PsourceDbVendor=… -PtargetDbVendor=…`. Exit code 0
  means copied and verified, 1 means failure, and 2 means a plan error with nothing written.
* Tables load in foreign key order with constraints left on, one parallel Batch step per table at
  each level. Cycles are broken at nullable foreign keys with a second pass.
* PostgreSQL large objects are read with `lo_get` and written with `lo_from_bytea`, and missing
  large objects fail the plan before anything is written.
* Timestamps are copied without a zone, so they don't shift.
* The 49 generators come from a build-time scan of the entity annotations. Each is set to at least
  `max(id)` plus its allocation size, and never below its source value.
* Verification compares row counts and an order-independent hash of the normalized rows per table,
  then checks the generator positions.

Still to build: the 6.0 generator profile, splitting and merging tables, restartable runs, and the
6.0 schema itself.

### Consequences

* Good, because the old database is a ready rollback.
* Good, because adopters can switch vendors as part of the upgrade, such as MySQL to PostgreSQL.
* Good, because the 6.0 schema drops the one-row ID tables and the PostgreSQL OIDs in `text`
  columns.
* Bad, because the upgrade needs a second database server and time to copy, which is longest for
  sites with years of events.
* Bad, because portlet tables that live in the portal database need their own copy rules. The
  Bookmarks, Feedback and Notification portlets create their tables on first start, so the target
  needs `dataInit` and one portal start before the copy.

### Confirmation

A required check, G-migrate, runs in uPortal-start's Integration workflow. It fills 5.17
databases on old servers, copies each into a current server with `dbMigrate`, verifies the copy,
and for some pairs runs the whole Playwright suite on the copied portal. Each push runs a small set of
pairs, and the nightly set includes each source version, each target and each pair of vendors.
When the 6.0 schema exists, G-migrate also requires the copied schema to equal a fresh 6.0 install,
row counts and hashes to match after the documented transforms, and one insert per entity to
succeed.

In uPortal, the `DB copy tool` workflow tests the tool on real servers started by Testcontainers. It
runs property tests that copy each kind of value between pairs of servers, fault injection (target
sessions killed, the target server stopped, the JVM killed mid-copy, corrupted target rows) and a
chain from PostgreSQL 16 to MariaDB 12.3 to PostgreSQL 18 that includes a partitioned events table.
Pushes and pull requests use PostgreSQL 18, MariaDB 12.3 and MySQL 9.7, and the pairs with SQL
Server 2025 and Oracle Free 23.26 run nightly. The tool also has 61 unit tests. Measured runs:

| Copy | Rows | Result |
| --- | --- | --- |
| PostgreSQL 16 to MariaDB 12.3 | 14,619 | verified |
| MariaDB 11.4 to PostgreSQL 18 | 24,853 | verified |
| HSQLDB 2.7.4 to PostgreSQL 18 | 9,765 | verified |
| PostgreSQL 16 to MariaDB 12.3, 1M raw events with LOBs | 1,000,000 events | 148.5 s to copy (6.7k rows/s), 108.6 s to verify, 94 MiB peak heap |

Playwright passed on the copied portals, apart from two web-proxy tests that the prototype's network
sandbox blocks, and inserts after the copy didn't hit duplicate keys.

## Pros and Cons of the Options

### A new 6.0 schema, filled by a tool that copies a 5.17 database into it on any supported vendor

* Good, because one tool handles any pair of supported vendors.
* Good, because the rule interface can already express JSON text to native JSON, UUID types,
  zone-aware timestamps and renamed sequences.
* Bad, because large sites need batching, parallel tables and restartable runs.

### In-place migrations per vendor, such as Flyway scripts for five vendors

* Good, because adopters upgrade on the database server they already run.
* Bad, because it needs five vendors' worth of SQL.
* Bad, because it can't switch vendors, and rolling back means restoring a backup.

### Both a copy tool and in-place migrations

* Good, because adopters could choose.
* Bad, because it is the most work, with two paths to test.

### uPortal's XML export and import

* Good, because it is vendor-neutral and exists today.
* Bad, because it is slow and doesn't cover event data.

### Keep the 5.17 schema forever

* Good, because it is the least work.
* Bad, because it keeps the one-row ID tables and the `vacuumlo` data loss risk indefinitely.

## More Information

* Until 6.0, adopters on PostgreSQL should not run `vacuumlo` against a uPortal database, and
  should back up before upgrading.
* [PostgreSQL vacuumlo](https://www.postgresql.org/docs/current/vacuumlo.html),
  [Spring Batch](https://docs.spring.io/spring-batch/reference/)
* Prototype references: MOD-D11, MOD-D25 (MySQL sequences), MOD-D28, MOD-I23, MOD-I24,
  MOD-I73, MOD-I92 to MOD-I96.
