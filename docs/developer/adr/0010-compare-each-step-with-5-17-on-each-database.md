---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Compare each step with 5.17's output, on each supported database

## Context and Problem Statement

Before this work, uPortal's CI ran its JPA and DAO tests only on in-memory HSQLDB, and
uPortal-start ran on HSQLDB. Adopters run PostgreSQL, MySQL, MariaDB, SQL Server and Oracle. Dialect
problems, isolation level differences and vendor-specific DDL could only show up in production.

The test suites (124 Playwright tests in 24 specs, 244 unit test classes) show that the portal
still works on the paths they click. They miss silent changes: values that read back differently,
JSON formats, routes, security defaults and analytics buckets. Playwright ran with `retries: 2`,
which hides flaky tests.

How can each 6.0 step prove that it didn't change what 5.17 does, on every database adopters use?

## Decision Drivers

* Each step of ADR 0002 needs an objective pass or fail, on every supported vendor.
* A difference from 5.17 must be either a bug or an intended change that is written down.
* Database runs should be cheap enough for every push, with the slowest sets nightly.

## Considered Options

* Record 5.17's output once, keep it as test fixtures, and compare each step with it on every
  supported database in CI
* Rely on the existing unit tests and Playwright specs on HSQLDB
* Test other databases by hand before each release

## Decision Outcome

Chosen option: "Record 5.17's output once, keep it as test fixtures, and compare each step with it
on every supported database in CI", because only a stored reference can show a silent change, and
only real databases show vendor differences.

What runs today:

* **G-db, the database upgrade harness in uPortal-start** (`tests/db/harness.sh`). It records the
  schema of a 5.17 portal on each vendor and compares a later build with it, reporting structure,
  data, constraint names and ID generators separately. 5.17 snapshots were recorded on HSQLDB,
  PostgreSQL, MySQL, MariaDB, SQL Server and Oracle, and each reproduced with zero drift on a fresh
  database.
* **uPortal's `DB integration` workflow.** The JPA and DAO tests read their database from system
  properties, with HSQLDB as the default, and run on each 6.0 runtime database (ADR 0009). No test
  is skipped on any vendor.
* **uPortal-start's Integration workflow.** It builds uPortal from source, runs the full Playwright
  suite on HSQLDB, PostgreSQL 18 and MariaDB 12.3 (MySQL 9.7 nightly), runs the harness on each
  runtime database, and runs G-migrate (ADR 0008).

Planned, and approved for the 6.0 work:

1. Data equivalence. Export every entity type with `dataExport` on 5.17, upgrade in place and
   through the copy tool, export again with the new code, and compare the normalized XML.
2. HTTP replay. Record the requests that Playwright and an API crawl make against 5.17, replay them
   against each step, and compare status codes, redirects, headers, cookie attributes and
   normalized JSON and HTML.
3. Analytics equivalence. Run one generated event stream that crosses daylight saving, month and
   year boundaries in several time zones through 5.17 and the new build, and compare the
   aggregation tables.
4. Portlet conformance. Run Apache Pluto's JSR-286 test suite and require it to pass, now and on
   the transformed portlet WARs on Tomcat 11 (ADR 0011).
5. Smoke tests of each bundled portlet in each mode and window state, and characterization specs
   for each built-in portlet and admin flow before it is replaced (ADR 0012).
6. Entity round trips with edge values on each vendor, and the results of the HQL and Criteria
   queries recorded on 5.17 and compared through Hibernate 5.2 to 7.4.
7. A security matrix (role and route to status code), legacy password hashes, CSRF and a snapshot
   of response headers.
8. A two-node cluster test that checks that each scheduled job runs on one node only and that a
   cache eviction on one node also evicts on the other. It also stops one node to check that the
   cluster lock recovers.
9. Playwright with `retries: 0`, or with retried tests counted as failures.

A step merges only after it passes these checks unchanged, or with each difference explained.

### Consequences

* Good, because the checks found real bugs that HSQLDB hid. Under InnoDB's `REPEATABLE READ` on
  MySQL and MariaDB, the cluster lock code couldn't see the row it had just created, and an exception logging aspect hid
  Oracle's constraint errors so two API calls returned 500 instead of 400. Both are fixed with
  regression tests.
* Good, because the Hibernate 5.2 step showed why values matter. Upgraded portals lost their tab
  external IDs while fresh installs and row counts looked fine (ADR 0006).
* Bad, because SQL Server and Oracle containers are slow and large, and the full set of vendors,
  pairs and the campus-scale data has to run nightly.
* Bad, because 5.17 bugs are recorded too, and fixing one means updating the recording on purpose.

### Confirmation

uPortal's `DB integration` workflow runs on pushes to `master` and on pull requests, with its
canaries nightly. uPortal-start's Integration workflow runs nightly and on demand. Its push trigger
is still limited to the prototype branches. A failed test or a drift report fails the job, and each finding is reported as a GitHub
annotation. No database job may be marked allowed-to-fail except the declared canaries for
unreleased server versions (ADR 0009).

## Pros and Cons of the Options

### Record 5.17's output once, keep it as test fixtures, and compare each step with it on every supported database in CI

* Good, because it catches changes that a click-through test can't see.
* Good, because it reuses tools that already exist: `dataExport`, Playwright and the Pluto test
  suite.
* Bad, because the recordings need normalizing (generated IDs, timestamps, hashed names) before
  they compare cleanly.

### Rely on the existing unit tests and Playwright specs on HSQLDB

* Good, because it costs nothing more.
* Bad, because the cluster lock and Oracle bugs above passed on HSQLDB.

### Test other databases by hand before each release

* Good, because it doesn't use CI capacity.
* Bad, because a problem shows up long after the change that caused it.

## More Information

* The harness snapshots in uPortal-start are recorded from 5.17 and are not re-recorded, with one
  documented exception: HSQLDB 2.7.4 names constraint-backing indexes differently from 2.5.1, and
  those 111 names were re-recorded.
* [Testcontainers](https://java.testcontainers.org/),
  [Apache Pluto portlet test suite](https://github.com/apache/portals-pluto)
* Prototype references: MOD-D10, MOD-D16, MOD-D23, MOD-D29, MOD-I82, MOD-I83, MOD-I86, MOD-I97,
  MOD-I120, MOD-I126.
