# Testing the Database Copy Tool

The copy tool (`uPortal-db-migrate`, MOD-D11/MOD-D28 in
`docs/developer/modernization/LOG.md`) moves an adopter's whole database,
often to another vendor. These tests are there so that adopters can trust
it with production data (MOD-D29). Every value must arrive exactly, or
converted as documented and reported. Any other outcome must end the run
with a non-zero exit status and without a SUCCESS line.

| Suite | Run | Needs | What |
| --- | --- | --- | --- |
| Unit tests and properties | `./gradlew :uPortal-db-migrate:test` (part of `check`) | nothing | Planning, load order, value conversion, verification hash, generator formula; HSQLDB-to-HSQLDB copies, including the fail-closed plan checks |
| Integration tests | `./gradlew :uPortal-db-migrate:integrationTest` | Docker | Copies between real servers (type and generator properties, fault injection) and the PostgreSQL 16 → MariaDB 12.3 → PostgreSQL 18 chain |
| Mutation testing | `./gradlew :uPortal-db-migrate:pitest`, `./gradlew :uPortal-webapp:pitest` | nothing | PIT on the tool's core and on `JpaClusterLockDao` |

CI: the `DB copy tool` workflow (`.github/workflows/db-migrate.yml`) runs the
integration tests on every push and pull request, and nightly the deep run,
the SQL Server and Oracle pairs, and PIT.

## Integration tests

Testcontainers starts the servers once per run; each test creates its own
databases on them. The images are the 6.0 runtime servers (MOD-D25), pinned
by digest in
`uPortal-db-migrate/src/integrationTest/resources/db-images.properties`
(Renovate updates them). HSQLDB, in memory, always takes part as a source.

```sh
# The servers to use (default postgres,mariadb): postgres, mariadb, mysql, sqlserver, oracle
./gradlew :uPortal-db-migrate:integrationTest -PdbMigrateVendors=postgres,mysql
# One class, with the tool's console output in the log
./gradlew :uPortal-db-migrate:integrationTest --tests '*CopyFaultIT' -PdbMigrateShowOutput
```

| Property | Default | Effect |
| --- | --- | --- |
| `-PdbMigrateVendors` | `postgres,mariadb` | The servers; every ordered pair of them is tested (same vendor: PostgreSQL only) |
| `-PdbMigrateSeed` | `20261010` | The property tests' random seed. `random` picks a new one each run. A failing property prints its seed: pass it here to repeat the run |
| `-PdbMigrateTriesFactor` | `1` | Multiplies every property's tries (nightly: 10) |
| `-PdbMigrateMaxLob` | `20000` | Largest generated CLOB/BLOB value, in characters or bytes (nightly: 1,000,000) |

Each server takes 0.3 to 2 GB of memory; on a small machine test one or two
vendors at a time and leave the rest to CI.

### Type properties (`CopyTypeProperties`)

Rows of random values, with listed edge cases mixed in, are written into a
new source database, copied with the tool, and read back from both sides.
The test compares them by its own rules (`PairCopy`), not through the
tool's verification, and also compares dates and timestamps as the servers
print them, so a driver that shifts a value on the way in and back on the
way out cannot hide it. A second test copies every edge case at least once
on every run.

| Column | Values (edge cases besides random ones) | Arrives |
| --- | --- | --- |
| SMALLINT, INTEGER, BIGINT | full range; 0, -1, min, max, 2^53+1 | exactly |
| DECIMAL(38,10) | ±(10^28 - 10^-10), ±10^-10, 0, trailing zeros | exactly (by value) |
| DOUBLE | any finite bit pattern, with ±0.0, ±MAX_VALUE, MIN_NORMAL, subnormals, 0.1+0.2 | exactly (bits) |
| BOOLEAN (BIT(1), NUMBER(1)) | true, false | exactly |
| CHAR(10) | as VARCHAR | without trailing padding |
| VARCHAR(1), (255), (4000) | empty, spaces, control characters (no U+0000), combining marks, RTL, CJK, emoji and ZWJ sequences, U+FEFF, U+FFFF, SQL quotes and backslashes, maximal length in 1-, 2-, 3- and 4-byte characters | exactly; '' as NULL on Oracle (reported) |
| CLOB / text, BLOB / bytea | up to `MaxLob`; empty, one NUL byte, all 256 byte values | exactly |
| PostgreSQL `oid` and OIDs in `text` (5.17's `@Lob String`, MOD-I24) | as CLOB / BLOB | exactly, re-created as large objects on PostgreSQL |
| DATE | year 1000 (MySQL's minimum) to 9999; 1582-10-04/15, 1900-01-01, 1969-12-31, DST days | exactly |
| TIME | to the microsecond; midnight, 23:59:59.999999, the DST gap's hour | truncated to the lower precision (reported) |
| TIMESTAMP(0), (3), (6) | to the microsecond, year 1000 to 9999; New York's DST gap and overlap (the tests run in America/New_York), its last LMT second, pre-1970, 2038-01-19, 9999-12-31 23:59:59.999999 | exactly |
| 5.17's timestamp (MySQL/MariaDB `DATETIME`, SQL Server `datetime`) | as TIMESTAMP | truncated (MOD-I95) or on SQL Server's 1/300 s grid (reported) |
| TIMESTAMP WITH TIME ZONE | offsets -14:00 to +14:00 | same instant |
| uuid (PostgreSQL to PostgreSQL) | nil, max, v7 | exactly |

A conversion that changes a value must be in the report's notes for the
column and on the console; the tool's exit status must be 0 and its own
verification must agree.

Values a server cannot store at all are left out of these rows (U+0000 in
PostgreSQL text, NaN and infinities in MySQL/MariaDB, dates before 1753 in
SQL Server `datetime`, and so on): copying such a value must fail, which the
fault tests check.

### Generator properties (`GeneratorProperties`)

Random ids and random source generator positions (behind the data, as after
an import with explicit ids, or far ahead) are copied; then two of
Hibernate's next allocations are taken from each target generator,
sequence or one-row table, and the lowest and highest ids they hand out
under POOLED and POOLED_LO are inserted. None may collide.

### Fault injection (`CopyFaultIT`)

Run for each target server of the run, with the 5.17-shaped fixture as
source:

| Fault | Expected |
| --- | --- |
| Target sessions killed mid-copy | exit 1, the table named, no SUCCESS |
| Target server stopped mid-copy (a container of its own) | exit 1 within minutes, no SUCCESS |
| Copy thread interrupted | never SUCCESS unless every row is there and verified |
| JVM killed (SIGKILL) mid-copy | non-zero exit, no SUCCESS |
| One target row changed, deleted or added; one BLOB byte, one CLOB character, a timestamp one second off, values swapped between rows, a self-reference re-pointed, a boolean flipped, '' made NULL, a large object's byte (PostgreSQL) | `--verify-only` exits 1 and names that table, and only that table |
| A generator set below the copied ids | `--verify-only` exits 1 and names the generator |
| A missing target table (MOD-I92), an unexpected source column, a target NOT NULL column nothing fills | exit 2, nothing written |
| A target NOT NULL, length, unique key or foreign key the source rows violate | exit 2, nothing written |

#### After a failed copy

The tool is not restartable yet. A copy that stops part-way (exit 1, or a
killed JVM) leaves in the target the chunks it had committed: the tables of
the finished foreign-key levels, part of the table it was loading, nothing
of the later levels, deferred foreign keys not filled in, and the ID
generators not positioned. The source is only read. Run it again with
`--clear-target`, which deletes the rows of every table the run loads (in
one transaction, children first) and copies everything again; without it the
plan refuses the non-empty target (exit 2). A copy that stopped during the
verification has copied and positioned everything: `--verify-only` checks it
again.

## Mutation testing

[PIT](https://pitest.org) changes the compiled code one small mutation at a
time (a flipped condition, a removed call, a changed constant) and runs the
tests against each mutant; a mutant no test notices is a gap in the tests.

```sh
./gradlew :uPortal-db-migrate:pitest   # planning, load order, conversions, LOBs, verification, generators
./gradlew :uPortal-webapp:pitest       # JpaClusterLockDao (MOD-I82, MOD-I99)
```

Neither runs in `build` or `check`; the `DB copy tool` workflow runs both
nightly. The reports are in each module's `build/reports/pitest/`. A run
fails below its threshold (`mutationThreshold` in the module's
`build.gradle`; `-PpitestThreshold=N` overrides it).
