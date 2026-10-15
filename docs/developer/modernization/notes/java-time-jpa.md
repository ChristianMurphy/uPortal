# Handoff: Joda-Time out of the JPA mappings (P3-5)

Branch `ccr-43ba0785-hzlrj5-java-time-jpa` in uPortal, based on the integration branch at
`47d6333e2c`. Plan step P3-5 in the LOG: `notes/java-time.md` plan steps 1 to 3, ADR 0007,
MOD-I5 and MOD-D22. The schema is frozen (ADR 0006). Column types, names and stored values
stay as they are, and a golden difference is a bug, never a re-record.

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
  ranges. Jadira read every `dateTime` column in the JVM's default zone.
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
  2.2, and no class in it uses Joda-Time.

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

### Whole test suite per zone

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

## API breaks

`docs/UPGRADING-6.0.md`, section "Joda-Time removed from the database mappings and the event
aggregation API", lists each changed class. In short, the public signatures
of the events, event aggregation, cookie and statistics code use `java.time` types,
`JodaTimeConversions` and `CustomDateMidnightEditor` are gone, and the Jadira typedefs are gone
from `global.hbm.xml`.

## Proposed LOG rows (MOD-I? until the coordinator numbers them)

* **MOD-I?: the `DATE` binding of Hibernate 5.2.18 ignores `hibernate.jdbc.time_zone`.** Its
  `DateTypeDescriptor` binds and reads `DATE` without a calendar; `TIME` and `TIMESTAMP` use the
  setting. A `LocalDate` converter returning `Date.valueOf` stores the same date as Jadira
  (`databaseZone=jvm`). A Hibernate version that binds `DATE` with the UTC calendar would store
  dates a day early ahead of UTC. **Guarded:** `JavaTimeColumnStorageTest` asserts the stored
  literals in UTC, America/New_York and Australia/Sydney, and the Sydney goldens. Recheck at P4-3
  (Hibernate 5.6) and P6 (Hibernate 6 and 7).
* **MOD-I?: `fname` must leave Jadira before Hibernate 6.** `FunctionalNameType` extends
  `org.jadira.usertype.spi.shared.AbstractSingleColumnUserType`, and Jadira has no Hibernate 6
  support. Joda-Time and Jadira's `usertype.core` are gone (P3-5). **Open:** rewrite
  `FunctionalNameType` as an `AttributeConverter` or a plain `UserType` before P6, with the round
  trip on the `fname` columns.
* **MOD-I?: the event aggregation import read only `--MM-dd` quarter dates.** Under 5.17, a file
  with `--1-1` or a full ISO date as a quarter's start imported, because Joda-Time's
  `MonthDay.parse` accepted both. **Fixed:**
  `EventAggregationConfigurationImporterExporter.parseMonthDay` reads those forms
  (`QuarterMonthDayParsingTest`).
* **MOD-I?: `portlet-ws-util` 1.1.3 declares joda-time 2.2.** With uPortal's own joda-time gone,
  it would have put 2.2 on the classpath. **Fixed:** excluded in uPortal-webapp. None of its
  classes use Joda-Time.
* **MOD-I5 (update):** uPortal no longer contains Joda-Time or Jadira's Joda-Time types. The JPA
  columns keep their types and values (goldens in UTC, New York and Sydney on HSQLDB and the
  databases below). Only Jadira's SPI remains, for `fname` (row above).

## Running the checks again

* Goldens: `./gradlew :uPortal-webapp:test --tests 'org.apereo.portal.*Golden*'`. They set the
  JVM zone themselves (`JvmTimeZone.GOLDEN_ZONES`).
* Whole suite per zone: `TZ=<zone> ./gradlew test --rerun`. Without `--rerun`, Gradle reuses the
  results of a run in another zone, because `TZ` isn't a task input.
* Other databases: as `db-integration.yml` does, with `-Duportal.test.db.*` and the same images.
