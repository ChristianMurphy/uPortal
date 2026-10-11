---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Run 6.0 on current database servers, with an index set measured under load

## Context and Problem Statement

Before this decision, CI tested mid-range database versions: PostgreSQL 16, MySQL 8.4, MariaDB
10.6 and 11.4, SQL Server 2022, Oracle Free 23 and HSQLDB 2.7.4. On 10 October 2026 the newest
releases were PostgreSQL 18 (19 in beta), MySQL 9.7 LTS (26.7 as the newest Innovation release),
MariaDB 12.3 LTS (13.0 as the newest release), SQL Server 2025 and Oracle Free 23.26. MariaDB 10.6
passed its end of life in July 2026. Oracle 19c, still common on campuses, has no free container
image. Hibernate 7.4 itself needs at least PostgreSQL 14, MariaDB 10.6 and SQL Server 2014.

uPortal also has to hold up on large, busy campuses with heavy analytics. The 5.17 indexes were
never measured against a real workload, and the raw event tables grow by millions of rows a day.

Which database server versions should 6.0 run on, and how should the 6.0 schema (ADR 0008) choose
its indexes?

## Decision Drivers

* Use the features of current servers (JSON, UUID, partitioning) in the 6.0 schema.
* Test what adopters will run, without testing every old version against every new feature.
* Let adopters on old servers upgrade without a separate server upgrade first.
* Every needed index exists, and redundant or unused indexes are gone, proven by tests.

## Considered Options

* 6.0 runs on current servers only, and older servers are sources for the copy tool
* 6.0 runs on the oldest supported and the latest version of each vendor
* Choose indexes by hand review
* Choose indexes from production query logs alone

## Decision Outcome

Chosen option: "6.0 runs on current servers only, and older servers are sources for the copy
tool", together with an index set chosen by a measured workload, because the copy tool (ADR 0008)
already moves data off old servers, and testing one current version per vendor lets the 6.0 schema
use their features.

These are the minimum versions for 6.0, and the versions CI tests on every push:

| Vendor | Minimum version |
| --- | --- |
| PostgreSQL | 18 |
| MySQL | 9.7 LTS |
| MariaDB | 12.3 LTS |
| SQL Server | 2025 |
| Oracle | 23.26 |
| HSQLDB | 2.7.4, for development only |

The next releases (PostgreSQL 19, MySQL 26 Innovation, MariaDB 13) run nightly as canaries, which
may fail. The copy tool reads 5.17 databases from these older servers:

| Vendor | Source versions |
| --- | --- |
| PostgreSQL | 14, 15, 16 |
| MySQL | 8.0, 8.4 |
| MariaDB | 10.6, 10.11, 11.4 |
| SQL Server | 2019, 2022 |
| Oracle | XE 21, the closest free stand-in for 19c |
| HSQLDB | 2.5.1, 2.7.4 |

The 6.0 schema is planned to use these server features:

* Native JSON columns for the JSON stored as text today, mapped with Hibernate 7's
  `@JdbcTypeCode(SqlTypes.JSON)`.
* Oracle 23's `BOOLEAN` type, and native UUID types, possibly time-ordered UUIDv7 for insert
  locality.
* Raw events, event sessions and aggregations partitioned by time. A purge then drops a partition
  instead of deleting millions of rows.
* BRIN indexes on PostgreSQL time columns and covering `INCLUDE` indexes on PostgreSQL and SQL
  Server, where the workload shows a gain.
* Hibernate dialects configured with the actual server version.

The index set comes from a required check, G-index, that is planned:

1. Load a synthetic dataset from a seeded generator, sized for a large campus: about 100,000 users,
   5,000 concurrent sessions, 5 million raw events a day kept for 30 days, and 2 years of
   aggregations.
2. Run the Playwright suite, the JMeter scenarios and the analytics jobs (aggregation, purges,
   session cleanup), and collect every distinct SQL statement with `pg_stat_statements` and
   Hibernate's statement inspector.
3. Fail the build, unless a case is allowlisted, on a sequential scan of a large table in `EXPLAIN`,
   a foreign key column without an index, or a duplicate or left-prefix redundant index. Report
   indexes never used in the workload as removal candidates, and the statements with the most
   total time.
4. Declare the resulting indexes with explicit `@Index` annotations, so Hibernate 7 generates them,
   the copy tool creates them and the harness records them.

G-index runs on PostgreSQL first, then MySQL and MariaDB.

### Consequences

* Good, because 6.0 can use JSON, UUID, partitioning and newer index types.
* Good, because CI tests one runtime version per vendor instead of a range.
* Good, because the index set is reproducible and catches regressions.
* Bad, because adopters on older servers need a new server for 6.0, and the copy tool to fill it.
* Bad, because MariaDB 12.3 and SQL Server 2025 are newer than many campuses run today.
* Bad, because the full large-campus dataset, about 150 million raw events, is too big for standard
  CI runners. Pushes run a 1/100 scale dataset and the full one runs on demand.

### Confirmation

uPortal's `DB integration` workflow runs the JPA and DAO tests on PostgreSQL 18 and MariaDB 12.3,
and on MySQL 9.7, SQL Server 2025 and Oracle Free 23.26, for pushes to `master` and pull requests.
The canaries run nightly and are the only jobs allowed to fail. Database images are pinned by
digest, and Renovate updates the digests. uPortal-start runs its upgrade harness on the same
runtime versions and copies from the source versions in G-migrate (ADR 0008).

## Pros and Cons of the Options

### 6.0 runs on current servers only, and older servers are sources for the copy tool

* Good, because each vendor has one version to tune for.
* Good, because old servers stay supported as a starting point for the upgrade.
* Bad, because the minimums are high. The project can lower them if adopters need it.

### 6.0 runs on the oldest supported and the latest version of each vendor

This was the first proposal: PostgreSQL 15 and 18, MySQL 8.4 and 9.7, MariaDB 10.11 and 12.3, SQL
Server 2022 and 2025, and Oracle Free 23.26, each on every push.

* Good, because adopters could stay on older servers.
* Bad, because the schema could only use features of the oldest version.
* Bad, because it doubles the database jobs on every push.

### Choose indexes by hand review

* Good, because it doesn't need a test setup.
* Bad, because it isn't repeatable and doesn't catch regressions.

### Choose indexes from production query logs alone

* Good, because it reflects real use.
* Bad, because CI can't reproduce it. Anonymised `pg_stat_statements` output from an adopter would
  still be useful input.

## More Information

* MySQL still has no sequences (MariaDB has had them since 10.3), which affects the 6.0 ID
  generators (ADR 0008).
* [PostgreSQL versioning policy](https://www.postgresql.org/support/versioning/)
* Prototype references: MOD-D12, MOD-D16 (G-index scale), MOD-D24, MOD-D25, MOD-Q7, MOD-Q8,
  MOD-I43.
