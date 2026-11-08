# Handoff: Joda-Time out of the JPA mappings (P3-5)

Branch `ccr-43ba0785-hzlrj5-java-time-jpa` in uPortal, based on the integration branch at
`47d6333e2c`, with the integration branch at `280a638dd`, `0cf02679f`, `b7c7269e0` and
`92f31a6aa` merged on 2026-11-08 (section "Merges of 2026-11-08"). The fixes for the oss-review of
2026-11-08 come between the third and the fourth merge (section "Review of 2026-11-08"). Plan step P3-5 in the LOG: `notes/java-time.md`
plan steps 1 to 3, ADR 0007, MOD-I5 and MOD-D22. The schema is frozen (ADR 0006). Column types,
names and stored values stay as they are, and a golden difference is a bug, never a re-record.

## Status

| Commit | Subject | State |
| --- | --- | --- |
| 0 | test(persistence): run the persistence goldens east of utc | done |
| 1 | refactor(persistence): add java.time converters for the jadira columns | done |
| 2 | refactor(events)!: map raw events, sessions and status with java.time | done |
| 3 | refactor(events)!: move the event aggregation engine to java.time | done |
| 4 | refactor(portlets)!: map the portal and portlet cookies with java.time | done |
| 5 | refactor(portlets)!: use java.time in the statistics report form | done |
| 5a | fix(io): read the quarter dates that joda-time's monthday read | done |
| 6 | build!: remove joda-time, jadira and jackson-datatype-joda | done |
| 7 | docs: list the java.time api breaks in UPGRADING-6.0 | done |
| 7a | fix(events): check the term dates before converting them | done |
| merge | Merge origin/ccr-43ba0785-hzlrj5 (280a638dd) into java-time-jpa | done, no conflicts |
| 8 | docs: list the six user types that keep jadira's spi | done |
| merge | Merge origin/ccr-43ba0785-hzlrj5 (0cf02679f) into java-time-jpa | done, no conflicts |
| 9 | docs(notice): drop joda-time and jadira's usertype core | done |
| merge | Merge origin/ccr-43ba0785-hzlrj5 (b7c7269e0) into java-time-jpa | done, no conflicts |
| 10 | fix(events): floor intervals, quarters and terms as joda-time did | done |
| 11 | feat(events): warn when the jvm zone is one joda-time read another way | done |
| 12 | test(web): check expired portlet cookies and the cookie date precision | done |
| 13 | test(portlets): check the date range of the statistics reports | done |
| 14 | test(events): check the term of a date dimension in new york | done |
| 15 | test(events): check the stored precision of event and status times | done |
| 16 | docs: correct the java.time sections of UPGRADING-6.0 and CHANGES | done |
| merge | Merge origin/ccr-43ba0785-hzlrj5 (92f31a6aa) into java-time-jpa | done, no conflicts |
| 17 | docs: record the review fixes in the branch note | done |

Each commit builds and passes the tests of the modules it changes on its own. The subjects of
commits 0, 2, 3 and 5 are shorter than in the brief, which gave them over 72 characters.

Differences from the brief's sequence:

* The aggregation import and export moved in commit 3, because the engine's constructors changed
  there. Commit 5 holds the statistics form and editor.
* Commit 5a is new. Without it, an event aggregation file with `--1-1` or a full date as a
  quarter's start would fail to import. Joda-Time's `MonthDay.parse` read those forms.
* Commit 6 keeps Jadira's `usertype.spi`: `FunctionalNameType`, the `fname` typedef, extends
  its `AbstractSingleColumnUserType`. The brief puts `fname` out of scope, and the coordinator
  chose to keep the SPI for it (option A, 2026-10-14). The open row below tracks its removal.
  Five more user types extend the same SPI classes (commit 8 and the Jadira SPI row below).

## Merges of 2026-11-08

The branch merged `origin/ccr-43ba0785-hzlrj5` at `280a638dd`, 41 commits after `47d6333e2c`, in
merge commit `b2942c243`. Git didn't find any conflict, and the merge commit contains only the
merge. `git diff 280a638dd b2942c243` lists the same 124 files as the branch's diff against
`47d6333e2c`.

What was merged since 2026-10-14, read against this branch (LOG rows MOD-I360 to MOD-I402):

| Merged change | Rows | Overlap with this branch |
| --- | --- | --- |
| The layout store and the counter store run under READ COMMITTED, and a structure id is incremented before it is read | MOD-I360 to MOD-I362 | None. `RDBMUserLayoutStore`, `HibernateStyleCounterStore` and `persistenceContext.xml` don't use a Joda-Time or Jadira type, and this branch doesn't change them. Their new concurrency tests load the whole `PortalDb` unit with this branch's mappings |
| The layout reload and the purge after a reset | MOD-I363 | None. `DistributedLayoutManager` and `UserLayoutHelperImpl` don't use a date type |
| The user import runs under READ COMMITTED | MOD-I394 | None. `UserImporterExporter` changed its isolation only. This branch changes the event aggregation import, a different class |
| A locale save locks the user's row | MOD-I391 | None |
| The entity type cache uses the class as its key | MOD-I392 | None |
| `TZ` is an input of every test task (retro-1) | none | Gradle no longer reuses a test result from another zone. "Running the checks again" below says so now |
| The Gradle wrapper retries the download | MOD-I397 | None |

The 41 commits don't use `org.joda`, Jadira's `usertype.core` or a typedef this branch removes
(`git grep` on the merged tree). The other rows in that range are about uPortal-start
specs, CI and portlets this branch doesn't change.

MOD-I382 (open) lists portlet cookies among the code that reads a row and then updates it, which
may fail with 1020 on MariaDB 11.6+. This branch changes the type of the cookie fields from
Joda-Time's `DateTime` to `Instant` and keeps the same reads and writes. It doesn't change that
risk.

Landing 4 (`0cf02679f`, 9 commits after `280a638dd`) brought the permission cache work
(`-permission-race`): `AuthorizationImpl`, `AnyUnblockedGrantPermissionPolicy`, the new
`DecisionCacheMarker`, comments in the two Ehcache files, a test in uPortal-webapp and a section
in UPGRADING-6.0. It doesn't change a file of this branch apart from UPGRADING-6.0, and none of
its code uses a date type. The second merge, `f7bbeee38`, had no conflicts either. In
UPGRADING-6.0 the permission section ("Clusters: permission changes and portal events") comes
after this branch's section, and both links to this branch's heading still resolve. That section
names `PortalRawEventsAggregatorImpl.aggregationDelay` (default `PT30S`). This branch keeps the
setting and its `TemporalAmount` type, and floors the newest event time to the second as
Joda-Time's `roundFloorCopy` did. After the second merge, `git diff 0cf02679f f7bbeee38` lists the
same 124 files.

Landing 5 (`b7c7269e0`, 10 commits after `0cf02679f`) brought the layout save lock (MOD-I399,
`RDBMUserLayoutStore` and `UserLayoutHelperImpl`) and a render fix for the skin elements in
uPortal-web. They change other files than this branch, and their code doesn't use a date type. The third merge,
`973b955fd`, had no conflicts.

Landing 6 (`92f31a6aa`, 18 commits after `b7c7269e0`) brought batch-1 (the CodeNarc classpath in
the root `build.gradle`, the background preference web component and its tests) and, in
uPortal-start, jdk21-opens. It doesn't change a file of this branch. The fourth merge,
`e05f685b8`, had no conflicts, and uPortal-start's branch merged its tip `88542e157` in
`f0c27518c`.

While re-reading the branch, commit 8 corrected one of its claims. The build comment in
uPortal-core, UPGRADING-6.0 and the open row named `FunctionalNameType` as the only user of
Jadira's SPI. `NullSafeStringType`, `QNameType`, `JacksonType` and `StatisticsJacksonType` in
uPortal-rdbm, and `WindowStateType` in uPortal-rendering, extend it too. All six
were there before this branch, and none uses Joda-Time.

## Review of 2026-11-08

The coordinator's oss-review of `9e30c9751` against `0cf02679f` found nothing that blocks: the
stored values equal 5.17's. Its findings and the coordinator's decisions are in the scratch file
`review-jtj/findings.md`. Each item below has a commit and a test that fails before it. For a
test-only item, the seeded fault (the reviewer's mutant) is listed, applied alone, then reverted.

| Finding | Change | Commit | Test, and its result before the change |
| --- | --- | --- | --- |
| D1, the JVM zone ids `EST`, `MST`, `HST` and `SystemV/*` | `JvmTimeZoneCheck` logs one warning at startup with the region id to set. UPGRADING-6.0 has a section "The JVM's time zone" and a corrected sentence about the zone of a `ZonedDateTime`. Joda-Time's mapping isn't copied (coordinator's decision) | 11, 16 | `JvmTimeZoneCheckTest`. Seeded fault: no warning for the short ids. `namesTheRegionJodaTimeUsedForAFixedOffsetShortId` and `readsTheJvmDefaultZone` fail |
| D2, the floors in Chatham, Lord Howe, Havana and Azores | The reviewer's patch for `AggregationInterval.determineStart`. Quarter boundaries (`QuarterDetailImpl.compareTo`, the helper) use the day's start with the instant's offset, and terms use their stored midnight through the new `AcademicTermDetail.getStartDateTime` and `getEndDateTime`. The import floors the file's date with its offset, and the export writes the stored midnight | 10 | `AggregationIntervalJodaParityTest` compares 532 results recorded from Joda-Time 2.14.3 on `47d6333e2` (resource `joda-interval-results.txt`) and steps through four transition days with a cap of 2000 steps. `AggregationIntervalHelperImplZoneTest` and `TermDateZoneTest` check the helper and the import and export. On the old code all 9 events tests fail (27 of the 532 results differ, and the Chatham report test runs out of memory instead of hanging), and the Havana and Azores import cases of `TermDateZoneTest` fail |
| A1, API table | Rows for `JpaBaseAggregationDao`, `AcademicTermDetailImpl`, `QuarterDetailImpl`, `EventProcessingResult`, `AggregationIntervalHelperImpl`, `PortalEventDimensionPopulatorImpl`, `StatisticsPortletController`, the metamodel classes and `CustomDateMidnightEditor`'s 3-argument constructor | 16 | docs |
| A2, expired portlet cookies | The reviewer's test in `PortletCookieServiceImplTest` | 12 | Seeded fault K2 (`isBefore` for `isAfter`): `getAllPortletCookiesDropsExpiredPortletCookies` fails |
| A3, statistics date range | The reviewer's tests, with the JVM in New York inside the test so the UTC fault fails in any zone | 13 | Seeded faults E2, E3 and E4: E2 and E3 fail `reportRangeCoversTheWholeEndDay`, E4 fails `countsToTheLastSecondOfTheEndDay` |
| A4, terms and quarters in a New York JVM | `PortalEventDimensionPopulatorTermTest` (a term's first day), and the New York `+10:00` case of `TermDateZoneTest`, which checks the stored instant and the exported text | 14, 10 | Seeded faults Q10, Q11, I1 and I2: Q10 and Q11 fail the two populator tests. I1 fails the New York `+10:00` and the Azores `-05:00` cases, and I2 fails all six cases |
| A5, `portlet-ws-util` | The build comment, UPGRADING-6.0 (a line for overlay authors) and the note's row say that only `JodaTypeConverter` uses Joda-Time | 16, 17 | docs |
| A6, stored-value rules | `CookieDateStorageTest` (K1, K4), `PersistentPortalEventTimestampTest` (T2), and a test in `PortalRawEventsAggregatorImplTest` (T5, T7) | 12, 15 | Seeded faults K1, K4, T2, T5 and T7. K1 and K4 fail their `CookieDateStorageTest` test. T2 fails `storesTheEventTimestampToTheMillisecond`. T5 and T7 each fail `storesMillisecondStatusTimesAndEndsOnAWholeSecond` |
| D3, quarter text forms | UPGRADING-6.0 lists the forms the import reads and the ones it refuses | 16 | docs |
| D4, tzdata | UPGRADING-6.0: the rules come from the JDK, keep it updated | 16 | docs |
| A7, `hibernate.jdbc.time_zone` | UPGRADING-6.0 says that every `Instant` column relies on it, and the `hibernate.properties` comment no longer says the `LocalDate` converter builds its values for it | 16 | docs |
| A8, `dateTimeTZ` | UPGRADING-6.0: two fields, an `Instant` and the zone id | 16 | docs |
| A9, this note | The tip replay's uPortal commit, the date of the 2271-test run, 37 test tasks, the `portlet-ws-util` sentence | 17 | docs |
| A10, NOTICE and CHANGES | uPortal-start's NOTICE drops the three lines on its branch of the same name. `docs/CHANGES.md` has a Joda-Time entry and no longer says "same Joda-Time storage" | 16, uPortal-start ``bd60da15c`` | docs |

Not carried: the rest of A6 (P1, Q8, Q9 and Q14), which the coordinator left optional. They
weren't run again. The seeded faults ran with `swap.py` and `run-mutants.sh` in the scratch folder
`jtj-mut`, one Gradle run each with `--tests` for the module's new test, and each file was restored
from a copy afterwards.

## Mapping rules

| Jadira typedef | Column | Field type now | How it is stored |
| --- | --- | --- | --- |
| `dateTime` | `TIMESTAMP` | `Instant` | Hibernate's own `Instant` type, bound with the `hibernate.jdbc.time_zone=UTC` calendar: the UTC clock time, as Jadira stored it |
| `localDate` (`databaseZone=jvm`) | `DATE` | `LocalDate` | `LocalDateColumnConverter`: `java.sql.Date` at the JVM's midnight |
| `localTime` (`JvmZoneLocalTimeType`) | `TIME` | `LocalTime` | `LocalTimeColumnConverter`: the time of day on 1970-01-01 in UTC |
| `monthDay` | `VARCHAR` | `MonthDay` | `MonthDayColumnConverter`: `--MM-dd` text |
| `dateTimeTZ` | none | none | unused, removed |

API rule for the public signatures:

* A Joda `DateTime` that only marks a point in time becomes `Instant`: raw event timestamps
  (`IPortalEventDao`), the aggregation status (`IEventAggregatorStatus`), event session access
  (`EventSession`, `EventSessionDao`), `EventProcessingResult`, and the cookie dates.
* A Joda `DateTime` with calendar meaning in the JVM's zone becomes `ZonedDateTime` in that
  zone: the aggregation intervals, `BaseAggregation.getDateTime()` and the aggregation DAOs'
  ranges. Jadira read every `dateTime` column in Joda-Time's default zone. For a region id that is
  the JVM's zone. For `EST`, `MST`, `HST` and the `SystemV/` ids it wasn't (D1 below).
* A day, week, month, year, quarter or term start derived from an instant keeps the instant's
  offset where midnight repeats, and an hour or minute is floored in the instant's own offset, as
  Joda-Time's `roundFloorCopy` and `toDateMidnight` did (D2 below).
* `DateMidnight` becomes `LocalDate`. Joda's `LocalTime` and `MonthDay` become java.time's.

Values stored from "now" use `Instant.now().truncatedTo(ChronoUnit.MILLIS)`. Joda's clock had
milliseconds, and `Instant.now()` has microseconds on JDK 21, which `TIMESTAMP(6)` columns would
keep.

## Hibernate 5.2.18 and `hibernate.jdbc.time_zone`

Finding: the `DateTypeDescriptor` of Hibernate 5.2.18 binds and reads `DATE` without a calendar, and
uses one only when the value is a `Calendar`. It ignores `hibernate.jdbc.time_zone`. `TIME` and
`TIMESTAMP` do apply it. So a `LocalDate` converter that returns `Date.valueOf` stores the same
date as Jadira on 5.2, and the MOD-I221 shift applies to `LocalTime` only. A later Hibernate
version that binds `DATE` with the UTC calendar would store every date a day early in a zone
ahead of UTC. `JavaTimeColumnStorageTest` (uPortal-core) pins the stored literals and names the
converter to fix, before the goldens fail.

## Evidence

### Commit 0: Australia/Sydney goldens

* On the unchanged mappings, the goldens passed in UTC and America/New_York on HSQLDB. Then
  `Australia/Sydney` was added to `JvmTimeZone.GOLDEN_ZONES` and recorded with
  `-Duportal.golden.record=true`. Only the four Sydney files were new. The Sydney round trip
  files equal the UTC ones line for line.
* Seeded fault, not committed: a UserType on `DateDimensionImpl.date` that calls
  `setDate(i, Date.valueOf(date), <UTC calendar>)`. Only the Sydney cases failed. The round trip
  read each date dimension back a day early (`lossy SearchRequestAggregationImpl[MAX]
  .dateDimension.date: expected 2001-02-19 but was 2001-02-18`, 84 differing lines in
  `AggrEventsDb-Australia_Sydney.txt`). The aggregation failed with a
  `ConstraintViolationException` on the date dimension's unique key. UTC and New York passed.
* Jadira's own `databaseZone=UTC` parameter also fails New York, through Jadira's read side, so
  it doesn't show the trap and wasn't used as the evidence.

### Commit 1: converters

* `JavaTimeColumnStorageTest` passes in UTC, America/New_York and Australia/Sydney.
* Seeded fault, not committed: `Time.valueOf` in `LocalTimeColumnConverter` fails New York
  (`expected:<00:30:15> but was:<05:30:15>`) and Sydney (`14:30:15`), with the message that
  names MOD-I221 and the converter.
* Seeded fault, not committed: a date converter that stores what a UTC-calendar binding would
  store fails Sydney only (`expected:<2026-11-01> but was:<2026-10-31>`).

### Commits 2 to 6

* After each commit, the goldens passed unchanged on HSQLDB: the round trip (three units) and the
  aggregation in UTC, America/New_York and Australia/Sydney, and the query golden in UTC.
* Seeded fault in commit 3, not committed: `AggregationInterval.DAY` floored in UTC. The Sydney
  aggregation golden failed with 610 differing lines from the DAY bounds on
  (`- autumn interval 2025-10-31T23:58:00Z DAY 2025-10-31T13:00:00.000Z ..`). The New York
  case ran out of heap while closing intervals, the loop `notes/persistence-goldens.md`
  describes for the same kind of fault. UTC passed.
* `CustomLocalDateEditorTest` (commit 5) and `QuarterMonthDayParsingTest` (commit 5a) pin the
  formats Joda-Time parsed. The tests that compared with Joda-Time (commit 6) compare with values
  recorded from Joda-Time 2.14.3 through jshell before it was removed.
* After commit 6, the runtime and test classpaths of uPortal-webapp and uPortal-db-migrate hold
  neither joda-time nor Jadira's `usertype.core` (`dependencies`). `portlet-ws-util` declared joda-time
  2.2, and the build excludes it. Only its `org.jasig.xml.bind.JodaTypeConverter` uses Joda-Time,
  and nothing in uPortal or uPortal-start loads that class (A5 below; until 2026-11-08 this
  sentence said that none of its classes used it).

### Other databases (local Docker, 2026-10-14)

`workflow_dispatch` is refused for this token, so the manual vendors of `db-integration.yml`
ran locally instead. `~/wt/.agent-tmp/java-time-jpa/bin/vendor-run.sh <vendor>` starts the
workflow's image (same digest and settings) as `jtj-<vendor>` on a free host port, runs
`./gradlew :uPortal-webapp:test --rerun -Duportal.test.db.*` as the workflow does, and removes
the container. Each run went through `~/wt/portal-slot.sh`.

| Database | Result |
| --- | --- |
| PostgreSQL 18 | 466 tests, 0 failures, with the 13 golden cases |
| MariaDB 12.3 | 466 tests, 0 failures; goldens as above |
| MySQL 9.7 | 466 tests, 0 failures; goldens as above |
| SQL Server 2025 | 466 tests, 0 failures; goldens as above |
| Oracle 23 | 466 tests, 0 failures; goldens as above |

To show that the test JVM used the container, a second PostgreSQL run of `DaoQueryGoldenTest` with
`-Duportal.golden.record=true` failed with "Goldens are recorded on HSQLDB only;
uportal.golden.record was set while running on postgresql". No golden file changed.

### Whole test suite per zone (2026-10-14)

`TZ=<zone> ./gradlew test --rerun` ran all 38 test tasks (2271 tests) without failures in
America/New_York, Australia/Sydney and UTC on the branch head.

### CI (2026-10-15)

* uPortal `ea15ba34f6`: DB integration 38683401799 and DB copy tool 38683401845 passed. CI
  38683401816 failed on a SpotBugs finding in `AcademicTermDetailImpl` (null passed to the
  `ZonedDateTime` constructor). `b6ebdf2c6e` fixes it: CI 38686266119, DB integration
  38686266108 and DB copy tool 38686266105 passed. `spotbugsMain` passes locally on every
  module.
* uPortal-start `6448754326`, building uPortal `ea15ba34f6` (the `uPortal under test` notice of
  the `Build uPortal` job): CI 38683411836, Migration equivalence 38683411873, HTTP replay
  38683411806 and G-index 38683411851 passed. Integration 38683411805 failed on the known red
  jobs only. The Playwright jobs on HSQLDB (116253776202) and PostgreSQL 18 (116253776217) each
  had 8 failures, `pages.spec.ts:98` at 320 and 390 px and `rtl-theme.spec.ts:123`. Each also had
  1 flaky test, `favorites.spec.ts:120` and `user-locale.spec.ts:121`.
  `user-locale.spec.ts:121` also failed on batch-1 (Integration 38672648246). The cluster job
  failed on the missing `uportal-maven-local` artifact. The Integration run predates
  `b6ebdf2c6e`, which changes only the null check.

### CI (2026-11-08)

The runs built uPortal `8828a2482`, the head after both merges. Each uPortal-start run's
`Build uPortal` job gives that commit in its `uPortal under test` notice. The uPortal-start
branch of the same name is its integration tip, `9c20dbaf6`, without commits of its own.

| Repository | Run | Workflow | Result |
| --- | --- | --- | --- |
| uPortal | 42485893545 | CI (push) | passed, with the Java 21 job 128747556128 and the commit message and Prose jobs |
| uPortal | 42485893483 | DB integration (push) | passed: PostgreSQL 18 and MariaDB 12.3 |
| uPortal | 42486609200 | DB integration (started by hand) | passed: PostgreSQL 18, MariaDB 12.3, MySQL 9.7, SQL Server 2025 (job 128749757169) and Oracle 23 (job 128749757166), and the PostgreSQL 19, MariaDB 13 and MySQL 26 canaries |
| uPortal | 42485893546 | DB copy tool (push) | passed |
| uPortal | 42485893537 | Workflow lint (push) | passed |
| uPortal-start | 42485900437 | Integration (push, workstream set) | passed. Its jobs were the Playwright suites on HSQLDB, on PostgreSQL 18 and on HSQLDB with CAS, the DB harness, the cluster test and G-migrate from MariaDB 11.4 to PostgreSQL 18 |
| uPortal-start | 42485900446 | HTTP replay (push) | passed. "Replay against 5.17" ran (job 128749199145) and compared 2387 exchanges with the recording. It reported 0 unexplained differences and applied 1570 intended ones, and 128 cells of the security matrix changed, 0 of them unexplained. The tip's run 42479132157, which built uPortal `280a638dd`, has the same counts |
| uPortal-start | 42485900459 | Migration equivalence (push) | passed |
| uPortal-start | 42485900481 | CI (push) | passed |
| uPortal-start | 42485900441 | G-index (push) | passed (PostgreSQL 18, job 128748525885) |

Every job reached its tests, and none ran again. The replay didn't find a new difference.
`tests/replay/intended-differences.json` doesn't need an entry. The portal cookie's `Max-Age` comes
from the configured maximum age, and the statistics form prints its dates with the same
`M/d/yyyy` pattern.

### ArchUnit freeze store

| After commit | Joda-Time entries | Hibernate `Session` entries |
| --- | --- | --- |
| base `47d6333e2c` | 518 | 14 |
| 2 | 443 | 14 (recorded under the new `Instant` signatures) |
| 3 | 81 | 14 |
| 4 | 36 | 14 |
| 5 | 0 | 14 |
| 6 | rule unfrozen, store file removed | 14 |

`check-freeze-store.py` passed for every commit against its parent. Commit 3 added 4 entries for
the temporary `DateMidnight` conversion in the statistics controller, which commit 5 removed;
the rule shrank from 443 to 81 in that commit. A refreeze also rewrites the line numbers of the
unchanged Ehcache rule; that file was restored each time, because ArchUnit ignores line numbers.

### Schema

Before any mapping change, a throwaway test (not committed) dumped HSQLDB's
`INFORMATION_SCHEMA.COLUMNS` for the three persistence units: 1236 columns. The dump was
identical after commits 2, 3, 4, 5 and 6. The script is
`~/wt/.agent-tmp/java-time-jpa/schema-check.sh`, and the test is in the same folder.

### After the merges (2026-11-08)

The local runs used Gradle 9.8.1 on JDK 21 with `--offline`, HSQLDB, and UTC as the machine's zone.

| Command | Tree | Result |
| --- | --- | --- |
| `./gradlew --continue test` | first merge, `b2942c243` | 2158 tests from 37 test tasks, 0 failures, 153 skipped. uPortal-db-migrate's tests didn't compile offline (below). uPortal-webapp ran 473, with the 13 golden cases (the aggregation in 3 zones, the round trip of 3 units in 3 zones, and the query golden) and the merged concurrency tests of the layout store (2), the locale store (3), the user import (1) and the counter store (2) |
| `TZ=Australia/Sydney ./gradlew --continue test spotlessCheck spotbugsMain` | `1f4fb6efe`, the first merge and commit 8 | All 37 test tasks ran again, none reused: 2158 tests, 0 failures. `spotlessCheck` (69 tasks) and `spotbugsMain` (59 tasks) passed |
| `./gradlew :uPortal-security:uPortal-security-permissions:test :uPortal-webapp:test` and that module's `spotbugsMain` | second merge, `f7bbeee38` | uPortal-webapp 483 tests, with `PermissionDecisionCacheRaceTest` (10) and the goldens, and `AuthorizationImplCacheTest` (8): 0 failures. SpotBugs passed |

`:uPortal-db-migrate:compileTestJava` and `:uPortal-webcomponents:vpTest` couldn't run on this
machine. This branch changes neither module, and CI runs both. The first needs jqwik 1.10.1, junit-platform-launcher 6.0.3 and
byte-buddy-agent 1.18.11, which the offline Gradle cache doesn't have. The second needs Chromium
1248, and only 1194 is installed.

`check-freeze-store.py 280a638dd HEAD` passed: no rule grew. The Hibernate `Session` rule has 14
entries on both sides. Its 10 warnings are the entries of `JpaPortalEventStore` recorded under
the `Instant` signatures (section "ArchUnit freeze store").

The CI runs are in section "CI (2026-11-08)".

## API breaks

`docs/UPGRADING-6.0.md`, section "Joda-Time removed from the database mappings and the event
aggregation API", lists each changed class. In short, the public signatures
of the events, event aggregation, cookie and statistics code use `java.time` types,
`JodaTimeConversions` and `CustomDateMidnightEditor` are gone, and the Jadira typedefs are gone
from `global.hbm.xml`.

## Open items (2026-11-08)

* Next step: the re-review of commits 10 to 17 and the merge of `92f31a6aa`. Then the CI runs of
  the head, recorded under "CI after the review".
* The hour intervals of Australia/Lord_Howe don't move forward on its fall-back day, in 5.17 too
  (row below). The branch keeps 5.17's floors.
* The user types on Jadira's SPI (row below) must move off it before P6.
* The `DATE` binding (row below) is guarded by tests. Recheck it at P4-3 and P6.
* Commit 6's message (`75c398e4b`) says that `usertype.spi` stays for `FunctionalNameType`
  alone, and that no class of `portlet-ws-util` uses Joda-Time. The commit is pushed, so the
  coordinator rewords it at the landing replay.
* MOD-I382 (portlet cookies may fail with 1020 on MariaDB 11.6+) is still open. This branch
  doesn't change it.
* The HTTP replay ran in CI only. A local replay needs a portal and its data on a machine whose
  disk three agents share.
* The review's optional seeded faults P1, Q8, Q9 and Q14 weren't carried, as the coordinator
  allowed.
* uPortal-start's NOTICE follows uPortal's web application. Portlets that uPortal-start deploys,
  such as the Calendar portlet, may bring Joda-Time in their own war. Whether NOTICE lists those
  is for the coordinator or the owner.

## Proposed LOG rows (MOD-I? until the coordinator numbers them)

* **MOD-I?: the `DATE` binding of Hibernate 5.2.18 ignores `hibernate.jdbc.time_zone`.** Its
  `DateTypeDescriptor` binds and reads `DATE` without a calendar; `TIME` and `TIMESTAMP` use the
  setting. A `LocalDate` converter returning `Date.valueOf` stores the same date as Jadira
  (`databaseZone=jvm`). A Hibernate version that binds `DATE` with the UTC calendar would store
  dates a day early ahead of UTC. **Guarded:** `JavaTimeColumnStorageTest` asserts the stored
  literals in UTC, America/New_York and Australia/Sydney, and the Sydney goldens. Recheck at P4-3
  (Hibernate 5.6) and P6 (Hibernate 6 and 7).
* **MOD-I?: six user types must leave Jadira's SPI before Hibernate 6.** The user types of the
  `fname`, `nullSafeString`, `qname`, `json`, `stats_json` and `windowState` typedefs
  (`FunctionalNameType` in uPortal-core, `NullSafeStringType`, `QNameType`, `JacksonType` and
  `StatisticsJacksonType` in uPortal-rdbm, `WindowStateType` in uPortal-rendering) extend
  `org.jadira.usertype.spi.shared.AbstractSingleColumnUserType`, and their column mappers extend
  `AbstractStringColumnMapper`. Jadira has no Hibernate 6 support. Joda-Time and Jadira's
  `usertype.core` are gone (P3-5). **Open:** rewrite the six as `AttributeConverter`s or plain
  `UserType`s before P6, with the round trip on their columns. Until 2026-11-08 this row, the
  build comment and UPGRADING-6.0 named `fname` alone.
* **MOD-I?: the event aggregation import read only `--MM-dd` quarter dates.** Under 5.17, a file
  with `--1-1` or a full ISO date as a quarter's start imported, because Joda-Time's
  `MonthDay.parse` accepted both. **Fixed:**
  `EventAggregationConfigurationImporterExporter.parseMonthDay` reads those forms
  (`QuarterMonthDayParsingTest`). Joda-Time read more forms, which 6.0 refuses: `2026-4-1`,
  `2026-04`, `2026`, `2026-W14-3`, `2026-091` and `+2026-04-01`. UPGRADING-6.0 lists the forms the
  import reads (D3, 2026-11-08).
* **MOD-I?: `portlet-ws-util` 1.1.3 declares joda-time 2.2.** With uPortal's own joda-time gone,
  it would have put 2.2 on the classpath. **Fixed:** excluded in uPortal-webapp. Only its
  `org.jasig.xml.bind.JodaTypeConverter` uses Joda-Time, and nothing in uPortal or uPortal-start
  loads it. UPGRADING-6.0 tells overlay authors who use that class to add joda-time themselves.
* **MOD-I?: the JVM zone ids `EST`, `MST`, `HST` and `SystemV/*` meant other zones to Joda-Time.**
  `DateTimeZone.forTimeZone` maps `EST` to America/New_York, `MST` to America/Denver, `HST` to
  Pacific/Honolulu and every `SystemV/` id to UTC. `java.time` reads the first three as fixed
  offsets and the `SystemV/` ids by their own rules. With `TZ=EST`, a term that 5.17 stored during
  daylight saving time reads a day early, and 197 of 655 lines of the review's aggregation
  comparison differ. **Mitigated** (commits 11 and 16): `JvmTimeZoneCheck` logs a warning at
  startup with the region id to set, and UPGRADING-6.0 explains it. The code doesn't copy
  Joda-Time's mapping (coordinator, 2026-11-08).
* **MOD-I?: the `java.time` floors differed from Joda-Time's where a daylight saving change isn't on
  a local hour or repeats midnight.** In Pacific/Chatham, five-minute intervals stopped moving
  forward on 2026-04-05, and a statistics report of that day ran out of memory. In America/Havana
  and Atlantic/Azores, an instant in the repeated hour got the first midnight as its day's start,
  and an imported term that starts on such a day was stored an hour earlier than 5.17 stored it.
  **Fixed** (commit 10): `AggregationInterval.determineStart` floors an hour or a minute in the
  instant's own offset and a day with the instant's offset, quarters and the import do the same,
  and terms start at their stored midnight (`getStartDateTime`). 27 of 532 recorded Joda-Time
  results differed before and none after (`AggregationIntervalJodaParityTest`), and
  `AggregationIntervalHelperImplZoneTest` and `TermDateZoneTest` fail on the old code.
* **MOD-I?: the hour intervals of Australia/Lord_Howe don't move forward on its fall-back day.** The
  hour starting at 01:30 +11:00 on 2026-04-05 ends at 01:30 +10:30, whose hour starts at 01:30
  +11:00 again. 5.17 had the same loop with Joda-Time, and the branch keeps 5.17's floors. **Open**:
  an hourly aggregation in that zone doesn't finish that day.
* **MOD-I?: the time zone rules come from the JDK.** Joda-Time 2.14.3 carried tzdata 2026c, and
  the JDKs on the prototype machine have 2026b. Africa/Casablanca, Africa/El_Aaiun and
  America/Edmonton (with its aliases) differ between the two in some months of 2024 to 2027.
  **Documented** in UPGRADING-6.0: update the JDK or its data with `tzupdater` (D4).
* **MOD-I5 (update):** uPortal no longer contains Joda-Time or Jadira's Joda-Time types. The JPA
  columns keep their types and values (goldens in UTC, New York and Sydney on HSQLDB and the
  databases below). Only Jadira's SPI remains, for six user types that don't use Joda-Time (row
  above).

## Running the checks again

* Goldens: `./gradlew :uPortal-webapp:test --tests 'org.apereo.portal.*Golden*'`. They set the
  JVM zone themselves (`JvmTimeZone.GOLDEN_ZONES`).
* Whole suite per zone: `TZ=<zone> ./gradlew test`. Since retro-1 (`ed3233a47`), `TZ` is an input
  of every test task, so a run in another zone runs the tests again without `--rerun`. A zone set
  through `JAVA_TOOL_OPTIONS` still needs `--rerun` (agent guide, section 2).
* Other databases: as `db-integration.yml` does, with `-Duportal.test.db.*` and the same images,
  or a manual run of `db-integration.yml` on the branch, which runs all six vendors.
