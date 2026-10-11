# P2: Hibernate 4.3.11 → 5.2.18

Branch `ccr-43ba0785-hzlrj5-hibernate52-r2`, made from `ccr-43ba0785-hzlrj5` at `a75820d7a`. It
carries the 5 commits of the first attempt (`ccr-43ba0785-hzlrj5-hibernate52`, left untouched),
cherry-picked, and the fixes below. It merges only after the golden tests (MOD-D29) pass
unchanged on all five databases. Flyway (P2-5) and Spring 5.3 are not part of it.

## Commits

| r2 | First attempt | Change |
| --- | --- | --- |
| `bd8fa3448` | `892bd4759` | P2-1: the `SchemaTool` interface for the DDL tooling |
| `4523661ef` | `997bb7f6f` | P2-2: the move to Hibernate 5.2.18, with the freeze block |
| `ae9299f2c` | `054d24727` | P2-3: JPA index, order column and fetch mappings |
| `be5dfb1b5` | `3a1c55cbb` | P2-4: `PREF_VALUE` and PostgreSQL LOB column types |
| `f2ea3e8fa` | `ff3e9c103` | The first version of this note |
| `9c8d43b58` | (new) | MOD-I144: delete NULL element rows with their owner |

I reworded a few words in the cherry-picked commit messages for the commit prose check.

Cherry-pick conflicts: `docs/CHANGES.md` and `docs/UPGRADING-6.0.md`, both resolved by keeping
both sides. The read-committed cluster locks (`05f40ce45`, MOD-I99) don't conflict with the 5.2
change to `JpaClusterLockDao`. The isolation level is set on `newTransactionTemplate`, and the 5.2
commit marks the same template's transaction rollback-only inside the callback, so both apply.

## Freeze settings (unchanged from the first attempt)

- `hibernate.dialect_resolvers=PortalDialectResolver` keeps the 4.3 dialects. PostgreSQL 10+ gets
  `PostgreSQL81TextClobDialect` (CLOB stays `text`). MySQL and MariaDB get `MySQL55Dialect` (no
  `datetime(6)`, sequences or MyISAM). SQL Server gets `SQLServer2005Dialect` (one-row id tables).
- `implicit_naming_strategy` is legacy-jpa plus the 4.3 `FK_`/`UK_` hash, and
  `physical_naming_strategy` is the standard one. `id.optimizer.pooled.preferred=pooled`.
  `increment_size_mismatch_strategy=EXCEPTION` (active from 5.4).
- Jadira 6.0.1.GA (5.2 SPI, same Joda storage). jpamodelgen stays 5.6.3 (5.2 needs JAXB).

## MOD-I144 on Hibernate 5.2

5.2.18 behaves like 4.3. `PersistentSet.readFrom` and `PersistentMap.readFrom` drop an element
whose value reads as NULL, and `CollectionRemoveAction` skips the delete-all statement when the
loaded collection was empty. On Oracle, an owner whose element collection stores only '' values
still couldn't be deleted.

Fix (`9c8d43b58`): `ElementCollectionDeleteIntegrator` (uPortal-rdbm, listed in uPortal-webapp's
`META-INF/services/org.hibernate.integrator.spi.Integrator`) adds a pre-delete listener. For each
element collection of the entity being deleted that was loaded and is empty, it runs that
collection's delete-all statement by the owner's id. Hibernate runs collection removals before
entity deletions in a flush. The listener's statement runs between Hibernate's own collection
deletes and the owner's delete. The change leaves the mappings and the DDL as they were.

Tests:
- `JpaStylesheetDescriptorDaoTest.testDeleteRemovesNullTargetElementNames` inserts a NULL
  `UP_SS_DESC_LAY_ATTR_ELMS` row and deletes the descriptor. Without the listener it fails on
  the foreign key on HSQLDB.
- `EntityRoundTripGoldenTest` now expects `EntityManager#remove` to delete every entity on every
  database. `VendorDifferences.leftByRemove` (6 Oracle types with ORA-02292) is removed. The
  reload still misses the '' entries on Oracle, as the goldens expect, and no golden file changes.

## Fixes made on r2 after the cherry-picks

| Commit | Change |
| --- | --- |
| `377e5e28c` | `JpaPortalEventStore` sets `FlushModeType.COMMIT` on its `EntityManager` instead of calling `Session.setHibernateFlushMode`. The frozen `Session.setFlushMode` use leaves the ArchUnit store, and the store doesn't gain one. |
| `24a283a72` | The `org.dom4j:dom4j` exclusion skips the `spotbugs*` configurations, so SpotBugs starts again. The MD5 in `PortalImplicitNamingStrategy.hashedName` gets `@SuppressFBWarnings`: it rebuilds the 4.3 `FK_`/`UK_` names. |
| `7412afdfb` | `.gitattributes`: `*.sql` has LF line endings. The DDL golden files then match on Windows. |
| `a19ebddc6` | `JpaClusterLockDaoTest.anAbandonedLockIsTakenOver` waits 1.1 s instead of 50 ms. MySQL rounds `DATETIME` to whole seconds, so a lock taken at .5 s or later is stored up to half a second ahead. The test failed 2 of 6 local runs on MySQL 9.7 against ccr `d864e63b6` (Hibernate 4.3) as well, so this is not a 5.2 change. MOD-I141 moves `ClusterMutex.lastUpdate` to `Instant` after P2 and looks at the column precision again. Check the 1.1 s wait then. |
| `4a60a9f26`, `b917cc86e` | The MOD-I144 test inserts its NULL row in a transaction and without a cast, which MySQL rejects. |

## Test results

- CI 38111358789 on `f2ea3e8fa` (the cherry-picks only): 907 of 908 tests passed, with the
  persistence, query and analytics goldens on HSQLDB among them. The failure was the freeze rule
  fixed in `377e5e28c`. SpotBugs could not start (`24a283a72`). Vale flagged the docs.
- DB integration 38111412577 on `f2ea3e8fa`: the round-trip golden passed on Oracle with the old
  assertion (six types left with ORA-02292), which shows that 5.2.18 still leaves the rows. On
  Oracle, MariaDB and SQL Server the only failure was the freeze rule. MySQL also failed
  `anAbandonedLockIsTakenOver` (`a19ebddc6`).
- DB integration 38114017644 on `7eb70db29` (with the MOD-I144 fix): PostgreSQL 18, MariaDB 12.3,
  SQL Server 2025 and Oracle 23 passed, with the goldens unchanged and Oracle deleting every
  entity. MySQL 9.7 failed only on `anAbandonedLockIsTakenOver` and on the cast in the new test.
  Canaries: PostgreSQL 19 and MariaDB 13 passed. MySQL 26 failed, and its failed tests
  weren't checked (canaries may fail).
- CI 38114016099 on `7eb70db29`: SpotBugs, Vale, the commit checks and the Spring 5.3 compile check
  passed. Windows failed `PersistenceUnitDdlTest` (`7412afdfb`), and the other legs were
  cancelled.
- DB copy tool 38114019284 on `7eb70db29`: passed.
- Pending on `b917cc86e`: CI 38116123147 and DB integration 38116124775. uPortal-start
  Integration 38116235009 on uPortal-start `7943371`, with the full sets.

## Open points

- `PersistenceUnits.deleteAll`, the golden tests' clean-up, still deletes element-collection rows
  with SQL before it removes entities. The MOD-I144 fix makes that step unnecessary. It was left
  as it is.
- uPortal-start: a manual Integration run on a workstream branch used the light profile until
  uPortal-start `7943371`. The harness then covered PostgreSQL 18 only, and `db-harness.yml`
  builds released 5.17, not this branch. Run 38116235009 is the first with the harness on all six
  6.0 servers.
- Upgrade path (5.17 data, then the 5.2 portal on the same database): this round relies on
  uPortal-start's harness and G-migrate in CI. The first attempt's manual in-place upgrade on
  PostgreSQL 16 was not repeated.

## Proposed LOG rows

The coordinator numbers them.

- **MOD-I144 (update):** on Hibernate 5.2.18, Oracle leaves the rows too (run 38111412577). Fixed
  in 6.0 by `ElementCollectionDeleteIntegrator`, and the round trip now asserts a clean delete on
  Oracle (run 38114017644).
- **New, Testing:** `JpaClusterLockDaoTest.anAbandonedLockIsTakenOver` failed at random on MySQL
  because `DATETIME` rounds the lock time to whole seconds, also on 4.3. The test now waits 1.1 s
  (`a19ebddc6`). Revisit with MOD-I141.
- **New, Build:** the dom4j 2 exclusion from the 5.2 upgrade stopped SpotBugs from starting.
  Fixed in `24a283a72`.
- **New, Build:** `.sql` files had no line-ending rule, so the DDL golden files could get CRLF on
  Windows. Fixed in `7412afdfb`.

## Findings carried over from the first attempt

1. Map keys lost their `_` prefix. 4.3 applied `@Type(nullSafeString)` to map keys too, so rows
   store `_externalId`. Fixed with `@MapKeyType`, with tests.
2. The `BatchFetchQueueHelper` assertion fails under `-ea` when `PortletPreferencesImpl` is
   join-fetched. Tests disable it, which matches production.
3. Rollback-only commits fail differently in 5.2. `JpaClusterLockDao` now rolls back explicitly.
4. 5.2's defaults would change the schema. The freeze block prevents this.
5. Smaller upgrade issues: `@Index` on one-to-many is rejected, Javassist 3.22 fails on JDK 21,
   dom4j and Ehcache were duplicated, and dialect lookup failed after the SessionFactory closed.
6. New log noise: HHH000112, HHH000482 and `AggrEvents.AggrEvents.*` regions.
7. An NPE in `TransientPortletEntityDao` on `layout.json` when user=null appeared in 3 runs after
   the first rebase. Tests still pass. Not yet compared with a 4.3 portal.

## Left for 5.3 to 7

5.3 needs Spring 5, `hibernate.cfg_xml_file`, the cache SPI and Jadira 7. The increment check
activates in 5.4. Port the text CLOB dialect for 5.6.2. For 6, replace the legacy dialects,
string `@Type`, Jadira/Joda and `MapKeyType`, then move to Jakarta. Flyway replaces the freeze
block after 6.0.

## Resuming

Read this note and `git log ccr-43ba0785-hzlrj5..ccr-43ba0785-hzlrj5-hibernate52-r2`. The local
Gradle build gets 429s from Maven Central in this container. A loop that retries with a pause
fills the cache over several attempts.
