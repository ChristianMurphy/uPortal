# Handoff: Joda-Time → `java.time`, outside the JPA mappings (P3-4, first part)

Branch `ccr-43ba0785-hzlrj5-javatime` in uPortal. Plan step P3-4 (MOD-D6), the Spring
approach (MOD-D22), and the Joda half of MOD-I5.

## Summary

Everything that does not touch a JPA entity, a Hibernate type or a DAO's query is converted:
configuration, the Marketplace model and JSP, the analytics REST API, and the event jobs'
settings. What remains is the event-aggregation engine and the portlet cookies. Both are built
on JPA entities that map Joda types through Jadira, so they move with the Hibernate upgrade.
That remainder is most of the Joda code: the aggregation engine is the bulk of uPortal's date
handling.

| | Before | After |
| --- | --- | --- |
| Production files referencing `org.joda` | 73 | 62 (61 + the temporary `JodaTimeBridge`) |
| Test files referencing `org.joda` | 14 | 19 (new tests compare against Joda on purpose) |
| Joda property editors | 2 | 0 |
| `joda-time-jsptags` in the war | yes | no |

## Commits (one per intended upstream PR)

1. `refactor(spring): add java.time property editors`: `DurationEditor`
   (`java.time.Duration`) and `TemporalAmountEditor` (`TemporalAmount`), registered next to the
   Joda editors.
2. `refactor(utils): use java.time durations for cluster locks and scheduling`: cluster lock
   service and DAO, task scheduler; adds `JodaTimeBridge`.
3. `refactor(events): use java.time for event job settings and analytics API`.
4. `refactor(spring)!: remove the Joda-Time property editors`, plus `portal.properties` docs.
5. `refactor(marketplace): use java.time for portlet release dates`, plus `entry.jsp`,
   `include.jsp`, and dropping `joda-time-jsptags`.
6. `refactor(portlets): use java.time in the activity and popular portlets`.
7. Docs: `UPGRADING-6.0.md` section and this note.

## What changed, per module

* **uPortal-spring:** new `DurationEditor` and `TemporalAmountEditor`; `ReadableDurationEditor`
  and `ReadablePeriodEditor` removed. The editors stay property editors registered through
  `portalPropertyEditorRegistrar`, the mechanism the contexts already use. A Spring
  `ConversionService` with `DurationFormatter` would be the other Spring route, but there is no
  application-wide conversion service today, and adding one changes conversion for every bean.
  Spring has nothing for a mixed period like `P1DT12H`.
* **uPortal-utils-core:** `ClusterLockServiceImpl`, `JpaClusterLockDao` (only the
  `abandonedLockAge` setting, not query code) and `DelegatingThreadPoolTaskScheduler` take
  `Duration`. New package `org.apereo.portal.utils.time` (`@NullMarked`) with `JodaTimeBridge`.
* **uPortal-events:** `PortalRawEventsAggregatorImpl.aggregationDelay`,
  `PortalEventDimensionPopulatorImpl.dimensionBuffer`, `PortalEventPurgerImpl.purgeDelay` and
  `PortalEventSessionPurgerImpl.eventSessionDuration` are `TemporalAmount`.
  `IAnalyticsPortalEventService.getAnalytics` takes `ZonedDateTime`; the controller computes the
  range with `java.time`.
* **uPortal-marketplace:** `PortletReleaseNotes` dates are `LocalDateTime`;
  `MarketplacePortletDefinition` parses with a `java.time` formatter.
* **uPortal-portlets:** `ActivityController` and `PopularPortletsController` compute their
  windows with `ZonedDateTime`.
* **uPortal-webapp:** editor registrations, `portal.properties` docs, `entry.jsp`,
  `include.jsp`, `joda-time-jsptags` dependency removed (and `jodaTimeJsptagsVersion` from
  `gradle.properties`).

## Format and zone decisions

* **Property values:** every value the Joda editors accepted still works, checked value by value
  against Joda in the editor tests.
  * Durations: Joda accepted only `PTnS` (an optional fraction truncated to milliseconds);
    `Duration.parse` accepts all of that and more (`PT1M`, `P1D`). One edge case: a fraction
    with more than nine digits, which Joda truncated, is now rejected.
  * Periods: the date part (`PnYnMnWnD`) becomes a `Period` and the time part a `Duration`,
    applied date part first, as Joda's field order did. Days follow the calendar in the value's
    zone (23 or 25 hours across DST), and hours follow elapsed time, as with Joda. Two edges
    differ: a period with both years and months is applied as total months (Joda applied years,
    then months), and the result is resolved differently when it lands in a repeated or skipped
    DST hour.
* **Joda boundary:** `JodaTimeBridge` keeps both the instant and the zone, so a `DateTime` read
  from an entity is shifted in its own zone, as `DateTime.minus(Period)` did.
* **Default zone:** `ZoneId.systemDefault()` stands in for `DateTimeZone.getDefault()`. Both come
  from `user.timezone`, and `TimeZoneTestUtils` sets both.
* **Analytics range:** kept identical, quirk included: the date is parsed as UTC midnight, then
  the end is pushed a calendar day later in the default zone, minus one second.
* **Marketplace JSON:** `releaseDate` and `initialReleaseDate` stay epoch milliseconds (the
  local date-time taken in the default zone), whatever Jackson modules are registered. Spring
  4.3's `MappingJackson2JsonView` registered `JodaModule`, and would register `JavaTimeModule`,
  which writes `LocalDateTime` as an array.
* **Marketplace parsing:** as lenient as Joda's `yyyy-MM-dd HH:mm:ss` (short fields, years of
  1–9 digits) and as strict on invalid dates. A local time in a DST gap now parses; Joda threw,
  and the date was dropped with a warning.
* **Marketplace JSP:** `dd-MM-yyyy`, cut from the ISO text with `fn:substring`. This avoids JSTL
  `fmt` (it would format with the request locale's calendar, Buddhist for `th_TH`) and EL 3.0
  method calls.
* **Behaviour relaxed on purpose:** `dimensionBuffer` now accepts months and years, which Joda
  rejected with `UnsupportedOperationException`; values under a day are still rejected. The
  Activity portlet's "today" works where midnight falls in a DST gap, where Joda's
  `DateMidnight` threw.
* **Log text:** `ClusterLockServiceImpl` logs `maximumLockDuration` as `PT1H` instead of
  `PT3600S`.

## API breaks for adopters

All are in `docs/UPGRADING-6.0.md`: the setter types above, `IAnalyticsPortalEventService`,
`PortletReleaseNotes`, the removed editors, and the `joda:` JSP prefix.

## Tests

New: `DurationEditorTest`, `TemporalAmountEditorTest`, `JavaTimeEditorRegistrationTest`
(uPortal-spring); `JodaTimeBridgeTest`, `DelegatingThreadPoolTaskSchedulerTest`
(uPortal-utils-core); `PortletReleaseNotesTest` (uPortal-marketplace);
`PortalEventSessionPurgerImplTest`, `PortalEventDimensionPopulatorDimensionBufferTest`,
`AnalyticsPortalEventsControllerTest` (uPortal-webapp). Each converted computation is checked
against the Joda computation it replaces, across the 2026 DST changes. The cluster-lock DAO
tests exercise `DurationEditor` through the test context (`abandonedLockAge=PT0.100S`).

Two default time zones: the uPortal-spring, uPortal-utils-core and uPortal-marketplace tests
ran with `TZ=UTC` and with `TZ=America/New_York`. Gradle passes `TZ` to the test JVMs, which a
throwaway probe test confirmed (`TimeZone.getDefault()` and `DateTimeZone.getDefault()` both
followed it). All passed both times (30 + 37 + 8 tests).

## Gate results

* **Local (cloud container):** uPortal-spring, uPortal-utils-core and uPortal-marketplace tests
  green in UTC and America/New_York; uPortal-events compiles; `spotlessCheck` clean; SpotBugs
  shows no new findings in uPortal-utils-core, uPortal-spring, uPortal-marketplace and
  uPortal-events. uPortal-web, uPortal-portlets and uPortal-webapp can't build here (JitPack is
  blocked), so their compilation and the uPortal-webapp tests are covered only by CI.
* **uPortal CI:** pending when this note was written. CI run 38090727373 and DB integration run
  38090727349 for 770c4fc (the six commits merged with the integration branch at caf16b2) were
  still waiting for a runner after 40 minutes, behind the other workstreams' runs. Earlier runs
  38090173440 and 38090173400 (3e4cd32, before the merge) were also still queued. The integrator
  should check those runs, including the Static analysis job (SpotBugs for uPortal-web,
  uPortal-portlets and uPortal-webapp was not run locally), before merging.
* **uPortal-start Integration:** not run. Attaching the uPortal-start repository to this session
  (`add_repo`) was refused by the session's permission check, so the same-named uPortal-start
  branch was not pushed. Someone with access needs to push `ccr-43ba0785-hzlrj5-javatime` in
  uPortal-start (no changes needed) to run the DB harness and Playwright against this branch.

## Findings

* **Sandbox:** `jitpack.io` is blocked by the egress policy (403), so uPortal-web,
  uPortal-portlets and uPortal-webapp (`com.github.ci-rt:google-visualization-java`) can't
  build in the cloud container. Maven Central answered bursts with HTTP 429. A Gradle init
  script pointing Central at the Google mirror was refused by the session's permission
  classifier, so resolution went through repeated retries. The webapp tests ran only in CI.
* **Doc bug fixed:** the `purgeDelay=P1H` example in `portal.properties` was not a valid Joda
  period and would have failed startup if uncommented.
* **`PortalEventDimensionPopulatorImplTest` is `@Ignore`d** ("Breaks on move to Gradle"), so the
  dimension buffer had no running test; there is one now.
* **ArchUnit freeze rule (not landed yet):** `JodaTimeBridge` is a new class depending on Joda.
  A frozen "no Joda" rule would report it as new, though the store shrinks overall. The rule
  should exempt `org.apereo.portal.utils.time.JodaTimeBridge`, or freeze it once; it is the
  single place where converted code reaches Joda.

## Remaining Joda inventory (the JPA slice)

61 production files, all in the graph around the Joda-mapped entities:

* **Entities (11):** `BaseAggregationImpl`, `DateDimensionImpl`, `TimeDimensionImpl`,
  `QuarterDetailImpl`, `AcademicTermDetailImpl`, `EventAggregatorStatusImpl`,
  `EventSessionImpl`, `PersistentPortalEvent`, `PersistentAnalyticsEvent`, `PortalCookieImpl`,
  `PortletCookieImpl`, plus the `global.hbm.xml` Jadira typedefs (`dateTime`, `dateTimeTZ`,
  `localDate`, `localTime`, `monthDay`).
* **Interfaces the entities implement (public API):** `DateDimension`, `TimeDimension`,
  `QuarterDetail`, `AcademicTermDetail`, `BaseAggregation`, `IEventAggregatorStatus`,
  `EventSession`, `IPortalCookie`, `IPortletCookie`.
* **DAOs, with Joda query parameters:** `JpaBaseAggregationDao` (+ `BaseAggregationDao`,
  `BaseAggregationPrivateDao`), `JpaDateDimensionDao`, `JpaTimeDimensionDao`,
  `JpaEventSessionDao`, `JpaPortalEventStore` (+ `IPortalEventDao`),
  `JpaSearchRequestAggregationDao`, `JpaPortletLayoutAggregationDao`, `JpaPortletCookieDaoImpl`,
  and their interfaces.
* **Aggregation engine (public extension API):** `AggregationInterval`,
  `AggregationIntervalHelper(Impl)`, `AggregationIntervalInfo`, `DateRange`,
  `EventDateTimeUtils`, `IntervalAwarePortalEventAggregator`,
  `BaseIntervalAwarePortalEventAggregator`, `PortalRawEventsAggregator(Impl)`,
  `PortalEventDimensionPopulatorImpl`, `PortalEventPurgerImpl`, `PortalEventSessionPurgerImpl`,
  `PortalEventProcessingManagerImpl`, `EventProcessingResult`, `FilteredEventSession`.
* **Consumers of the engine:** the statistics portlet (`BaseReportForm`,
  `CustomDateMidnightEditor`, `BaseStatisticsReportController`, `StatisticsPortletController`),
  aggregation import/export (`EventAggregationConfigurationImporterExporter`,
  `ExternalQuarterDetailComparator`), `PortalEvent.getTimestampAsDate()`, and the cookie
  service (`PortletCookieServiceImpl`, `SessionOnlyPortletCookieImpl`).
* **Build:** `joda-time` (uPortal-core, uPortal-web), Jadira `usertype.core`/`usertype.spi`
  (uPortal-core), `jackson-datatype-joda` (uPortal-portlets; keep it while any Joda value can
  reach JSON).

### Seams left for the JPA slice

`JodaTimeBridge` is the only crossing. Its call sites, each removed by deleting the call once
the other side is `java.time`:

| Call site | Joda value it crosses to |
| --- | --- |
| `PortalEventPurgerImpl` | `IEventAggregatorStatus.getLastEventDate()` |
| `PortalEventSessionPurgerImpl` | `IEventAggregatorStatus.getLastEventDate()` |
| `PortalEventDimensionPopulatorImpl` (4) | `IPortalEventDao` timestamps, `AggregationIntervalHelper` |
| `PortalRawEventsAggregatorImpl` | the aggregation engine's `DateTime` |
| `AnalyticsPortalEventService` | `IPortalEventDao.getAnalyticsEvents` |
| `ActivityController` (3) | `ConcurrentUserAggregationDao`, `LoginAggregationDao`, `SearchRequestAggregationDao` |
| `PopularPortletsController` | `PortletLayoutAggregationDao` |

### Plan

1. **With Hibernate 5.2 (P2 merged):** replace each Jadira typedef with an `AttributeConverter`
   that reproduces today's storage (same column types and the same zone handling as Jadira's
   `PersistentDateTime`/`PersistentLocalDate`/`PersistentLocalTime`/`PersistentMonthDayAsString`),
   and change the entity fields to `Instant`/`ZonedDateTime`, `LocalDate`, `LocalTime` and
   `MonthDay`. Gate: the persistence goldens (MOD-D29 item 7) unchanged on every vendor.
2. **Same PR or the next:** convert the entity interfaces, the DAOs' parameters and the
   aggregation engine together (`AggregationInterval` maps onto `ChronoUnit` and
   `TemporalAdjusters`; `DateMidnight` becomes `LocalDate`, with `atStartOfDay(zone)` where an
   instant is needed). Gate: the analytics goldens (MOD-D29 item 3, across DST, month and year
   ends, several zones) unchanged.
3. **Then:** the statistics portlet (form as `LocalDate` with a `M/d/yyyy` editor), the
   aggregation import/export, the cookie service, and `PortalEvent.getTimestampAsDate()`.
   Delete `JodaTimeBridge`; drop `joda-time`, Jadira and `jackson-datatype-joda`; the ArchUnit
   Joda rule becomes a plain (unfrozen) rule.

## Proposed LOG entries

* **MOD-D?: Joda-Time outside the JPA mappings is replaced by `java.time`.** Property editors
  (`DurationEditor`, `TemporalAmountEditor`) accept every value the Joda editors accepted;
  periods keep calendar-day semantics in the value's zone. Code still meeting Joda entities goes
  through the single `JodaTimeBridge`. The Marketplace JSON keeps epoch milliseconds. Remaining:
  the entities, DAOs and the aggregation engine (61 files), converted with the Hibernate 5.2
  slice behind the persistence and analytics goldens.
* **MOD-I?: jitpack.io is blocked in the cloud sandbox.** uPortal-web needs
  `com.github.ci-rt:google-visualization-java` from JitPack, so uPortal-web, uPortal-portlets
  and uPortal-webapp can't build in cloud sessions; their tests run only in CI. Either allow
  the host or vendor the artifact.
* **MOD-I?: the `portal.properties` example `purgeDelay=P1H` was invalid** (fixed to `PT1H`).
