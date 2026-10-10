# P2: Hibernate 4.3.11 → 5.2.18

Branch `ccr-43ba0785-hzlrj5-hibernate52` (local `p2-hibernate-52`), rebased linearly onto ccr `22bdf2b23`. It merges only after it passes the golden tests (MOD-D29) unchanged. The commit-message check passes; P2-5 was skipped.

## Commits
- `892bd4759` refactor(rdbm): hide hibernate schema generation behind a schema tool
- `997bb7f6f` build!: upgrade hibernate to 5.2.18
- `054d24727` refactor(persistence): use jpa index, order column and fetch mappings
- `3a1c55cbb` fix(persistence): pin the pref value and postgresql lob column types

## Freeze settings
- **`dialect_resolvers=PortalDialectResolver`** keeps 4.3's dialects: PostgreSQL 10+ → `PostgreSQL81TextClobDialect` (CLOB stays `text`); MySQL/MariaDB → `MySQL55Dialect` (no datetime(6), sequences or MyISAM); SQL Server → `SQLServer2005Dialect` (one-row id tables).
- **`implicit_naming_strategy`**: legacy-jpa plus the 4.3 `FK_`/`UK_` hash. **`physical_naming_strategy`**: standard. **`id.optimizer.pooled.preferred=pooled`.** **`increment_size_mismatch_strategy=EXCEPTION`** (active from 5.4).

**Jadira** 6.0.1.GA (5.2 SPI, same Joda storage). **jpamodelgen** stays 5.6.3 (5.2 needs JAXB).

## G-unit (JDK 21; tests/failures/skipped)
Base 1137/0/79 → P2-1 1149 → P2-2 1151 → P2-3 1151 → P2-4 1152, all with 0 failures and 79 skipped.

## G-portal (Playwright, 124 tests)
- Rebased HEAD: 119 passed and 1 flaky in each of two runs. Only the 4 sandbox-blocked tests failed.
- Pre-rebase P2-2/P2-4 runs were equally clean (one attempt voided: OOM killer took Tomcat).

## G-db (rebased HEAD)
- **hsqldb, postgres-16, mysql-8.4, mariadb-11.4, mariadb-10.6, sqlserver-2022:** STRUCTURE, DATA and NAMES show zero drift. GENERATORS differs only by the removed `+table(hibernate_sequences…)` declarations.
- **oracle-23:** the same, except one cached sequence value (MOD-I84). The invariants hold.
- **Values:** table dumps after 4.3 and 5.2 dataInits are identical on postgres, mysql and sqlserver.

## Upgrade path (postgres-16)
1. **4.3 dataInit:** zero drift; API specs 36/36.
2. **5.2 on the same database:** structure and names unchanged; API, groups, permissions and user-admin specs pass. 3 failures are environmental (SimpleContentPortlet not deployed; CAS points at a stopped HSQL).
3. **Ids:** all max ids are at or below their sequence values. 5.2 allocates above the 4.3 blocks.
4. Only the expected UK test violations. LOB OIDs are written as before; vacuumlo is a pre-existing, documented hazard.

## Problems (MOD-I candidates, numbered after LOG.md's latest)
1. **Map keys lost their `_` prefix** (found on the upgrade path). 4.3 applied `@Type(nullSafeString)` to map keys too, so rows store `_externalId`. 5.2 applies it to values only, which broke tab externalIds (`/f/welcome` redirected to login). Fixed in P2-2 with `@MapKeyType`, plus tests.
2. **Random test failures from a 5.2 assertion.** The `BatchFetchQueueHelper` assertion fails under `-ea` when `PortletPreferencesImpl` is join-fetched. Tests now disable it, which matches production.
3. **Rollback-only commits fail differently in 5.2.** `JpaClusterLockDao` now rolls back explicitly. The MOD-I82 behaviour is kept.
4. **5.2's defaults would change the schema.** The freeze block prevents this.
5. **Smaller upgrade issues:** `@Index` on one-to-many is rejected, Javassist 3.22 fails on JDK 21, dom4j and Ehcache were duplicated, and dialect lookup failed after the SessionFactory closed.
6. **New log noise:** HHH000112, HHH000482 and `AggrEvents.AggrEvents.*` regions.
7. **Harness gaps:** DATA only counts rows, `--skip-init` snapshots before deploying, the LOB report ignores `-PdbPort`, and `--portal` lacks resource-server.
8. **Unexplained NPE since the rebase.** `TransientPortletEntityDao` throws on `layout.json` when user=null. It appeared in 3 runs after the rebase and in none of 6 before; tests still pass. Compare against a 4.3 portal on the same ccr.
9. **Environment (prototype machine only):** the shared `~/.gradle/gradle.properties` gained `G1PeriodicGCInterval`, which broke JDK 11 Gradle daemons; fixed by the coordinator with `-XX:+IgnoreUnrecognizedVMOptions`. Docker Hub 429s: pulled via mirror.gcr.io.

## Before merging, once the golden tests land
1. Rebase onto ccr (45 commits ahead): expect a CHANGES.md conflict, and review `05f40ce45` (read-committed cluster locks) against problem 3.
2. Without editing goldens, re-run G-unit on all four commits (round-trip, DAO-query, DST-aggregation and ArchUnit goldens), the data-equivalence check, G-db on every vendor, the upgrade path and G-portal. Watch Joda times, `@OrderColumn` lists, `PREF_VALUE` and HQL results.

## Left for 5.3–7
5.3 needs Spring 5, `hibernate.cfg_xml_file`, the cache SPI and Jadira 7. The increment check activates in 5.4. Port the text CLOB dialect for 5.6.2. For 6, replace the legacy dialects, string `@Type`, Jadira/Joda and `MapKeyType`, then move to Jakarta. A migration tool later replaces the freeze block.

## Cleanup
Tomcat and HSQL are stopped, the start-p2 HSQLDB files and Tomcat install are deleted, old traces are pruned, and none of my containers remain. Kept: `postgres:16-alpine`, `/home/user/.m2-p2` and the sandbox-only edits in start-p2.
