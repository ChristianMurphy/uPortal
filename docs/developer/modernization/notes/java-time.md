# Handoff: Joda-Time → `java.time`, outside the JPA mappings (P3-4, first part)

Branch `ccr-43ba0785-hzlrj5-javatime` in uPortal. Plan step P3-4 (MOD-D6), the Spring
approach (MOD-D22), and the Joda half of MOD-I5.

## Summary

Everything that does not touch a JPA entity, a Hibernate type or a DAO's query is converted:
configuration, the Marketplace model and JSP, the analytics REST API, and the event jobs'
settings. The event-aggregation engine and the portlet cookies remain. Both are built
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
  and `ReadablePeriodEditor` removed. The new editors are still property editors registered
  through `portalPropertyEditorRegistrar`, the mechanism the contexts already use. The other
  Spring route is a `ConversionService` with `DurationFormatter`. The portal has no
  application-wide conversion service today, and adding one would change conversion for every
  bean.
  Spring has nothing for a mixed period like `P1DT12H`.
* **uPortal-utils-core:** `ClusterLockServiceImpl`, `JpaClusterLockDao` (only the
  `abandonedLockAge` setting, not query code) and `DelegatingThreadPoolTaskScheduler` take
  `Duration`. New package `org.apereo.portal.utils.time` (`@NullMarked`) with `JodaTimeBridge`.
* **uPortal-events:** `PortalRawEventsAggregatorImpl.aggregationDelay`,
  `PortalEventDimensionPopulatorImpl.dimensionBuffer`, `PortalEventPurgerImpl.purgeDelay` and
  `PortalEventSessionPurgerImpl.eventSessionDuration` are `TemporalAmount`.
  `IAnalyticsPortalEventService.getAnalytics` takes `ZonedDateTime`; the controller computes the
  range with `java.time`.
* **uPortal-marketplace:** `PortletReleaseNotes` dates are `LocalDateTime`.
  `MarketplacePortletDefinition` parses with a `java.time` formatter.
* **uPortal-portlets:** `ActivityController` and `PopularPortletsController` compute their
  windows with `ZonedDateTime`.
* **uPortal-webapp:** editor registrations, `portal.properties` docs, `entry.jsp`,
  `include.jsp`, `joda-time-jsptags` dependency removed (and `jodaTimeJsptagsVersion` from
  `gradle.properties`).

## Format and zone decisions

* **Property values:** every value the Joda editors accepted still works, checked value by value
  against Joda in the editor tests.
  * For durations, Joda accepted only `PTnS` (an optional fraction truncated to milliseconds).
    `Duration.parse` accepts all of that and more (`PT1M`, `P1D`). A fraction with more than
    nine digits used to be truncated and is now rejected.
  * Periods: the date part (`PnYnMnWnD`) becomes a `Period` and the time part a `Duration`,
    applied date part first, as Joda's field order did. Days follow the calendar in the value's
    zone (23 or 25 hours across DST), and hours follow elapsed time, as with Joda. Two edges
    differ: a period with both years and months is applied as total months (Joda applied years,
    then months), and the result is resolved differently when it falls in a repeated or skipped
    DST hour.
* **Joda boundary:** `JodaTimeBridge` keeps both the instant and the zone, so a `DateTime` read
  from an entity is shifted in its own zone, as `DateTime.minus(Period)` did.
* **Default zone:** `ZoneId.systemDefault()` replaces `DateTimeZone.getDefault()`. Both come
  from `user.timezone`, and `TimeZoneTestUtils` sets both.
* **Analytics range:** kept identical, quirk included: the date is parsed as UTC midnight, then
  the end is pushed a calendar day later in the default zone, minus one second.
* **Marketplace JSON:** `releaseDate` and `initialReleaseDate` stay epoch milliseconds (the
  local date-time taken in the default zone), whatever Jackson modules are registered. Spring
  4.3's `MappingJackson2JsonView` registered `JodaModule`, and would register `JavaTimeModule`,
  which writes `LocalDateTime` as an array.
* **Marketplace parsing:** as lenient as Joda's `yyyy-MM-dd HH:mm:ss` (short fields, years of
  1 to 9 digits) and as strict on invalid dates. A local time in a DST gap now parses. Joda
  threw, and the date was dropped with a warning.
* **Marketplace JSP:** `dd-MM-yyyy`, cut from the ISO text with `fn:substring`. This avoids JSTL
  `fmt` (it would format with the request locale's calendar, Buddhist for `th_TH`) and EL 3.0
  method calls.
* **Behaviour relaxed on purpose:** `dimensionBuffer` now accepts months and years, which Joda
  rejected with `UnsupportedOperationException`. Values under a day are still rejected. The
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

Every affected module's tests ran with `TZ=UTC` and again with
`TZ=America/New_York` (counts under Check results). Gradle passes `TZ` to the test JVMs; a
throwaway probe test confirmed that `TimeZone.getDefault()` and `DateTimeZone.getDefault()` both
followed it.

## Check results

* **Local, all affected modules:** after installing `google-visualization-java` from the 5.17.9
  war into mavenLocal (the coordinator's MOD-I51 workaround, sha256 checked), every module
  builds here. Tests are green with `TZ=UTC` and again with `TZ=America/New_York`:
  * uPortal-webapp: 390 tests with 0 failures (78 are skipped by existing `@Ignore`s)
  * uPortal-portlets: 32 tests, 0 failures
  * uPortal-web: 10 tests, 0 failures
  * uPortal-spring: 37 tests, 0 failures
  * uPortal-utils-core: 30 tests, 0 failures
  * uPortal-marketplace: 8 tests, 0 failures

  `spotlessCheck` is clean, and SpotBugs doesn't report new findings in uPortal-utils-core,
  uPortal-spring, uPortal-marketplace, uPortal-events, uPortal-web, uPortal-portlets and
  uPortal-webapp, so the baseline did not grow. `check-commit-messages.sh` passes (5d02277's
  subject is 73 characters; it was pushed before the check existed, and history is not
  rewritten).
* **uPortal CI:** DB integration run 38090173400 passed on the six code commits (3e4cd32). The
  CI runs before the latest merge were cancelled as superseded. CI run 38093285084 (light
  profile) and DB integration run 38093285090 for 8aad4ea (merged with the integration branch)
  were queued when this note was written. The integrator should check them.
* **uPortal-start Integration:** not run. Attaching the uPortal-start repository to this session
  (`add_repo`) was refused by the session's permission check, so the same-named uPortal-start
  branch was not pushed. Someone with access needs to push `ccr-43ba0785-hzlrj5-javatime` in
  uPortal-start (no changes needed) to run the DB harness and Playwright against this branch.

## Findings

* **Sandbox:** `jitpack.io` is blocked by the egress policy (403, already MOD-I51); the
  coordinator's mavenLocal workaround made the full build possible. Maven Central answered
  bursts with HTTP 429. A Gradle init script pointing Central at the Google mirror was refused
  by the session's permission classifier, so resolution went through repeated retries.
* **Doc bug fixed:** the `purgeDelay=P1H` example in `portal.properties` was not a valid Joda
  period and would have failed startup if uncommented.
* **`PortalEventDimensionPopulatorImplTest` is `@Ignore`d** ("Breaks on move to Gradle"), so the
  dimension buffer had no running test. It has one now.
* **ArchUnit freeze rule (not landed yet):** `JodaTimeBridge` is a new class depending on Joda.
  A frozen "no Joda" rule would report it as new, though the store shrinks overall. The rule
  should exempt `org.apereo.portal.utils.time.JodaTimeBridge`, or freeze it once; it is the
  single place where converted code reaches Joda.

## Remaining Joda inventory (the JPA step)

61 production files, all in the graph around the Joda-mapped entities:

* **Entities (11):** `BaseAggregationImpl`, `DateDimensionImpl`, `TimeDimensionImpl`,
  `QuarterDetailImpl`, `AcademicTermDetailImpl`, `EventAggregatorStatusImpl`,
  `EventSessionImpl`, `PersistentPortalEvent`, `PersistentAnalyticsEvent`, `PortalCookieImpl`,
  `PortletCookieImpl`, plus the `global.hbm.xml` Jadira typedefs (`dateTime`, `dateTimeTZ`,
  `localDate`, `localTime`, `monthDay`).
* **Interfaces of the entities (public API):** `DateDimension`, `TimeDimension`,
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

### Remaining Joda call sites for the JPA step

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
   `MonthDay`. Required check: the persistence goldens (MOD-D29 item 7) unchanged on every vendor.
2. **Same PR or the next:** convert the entity interfaces, the DAOs' parameters and the
   aggregation engine together (`AggregationInterval` maps onto `ChronoUnit` and
   `TemporalAdjusters`; `DateMidnight` becomes `LocalDate`, with `atStartOfDay(zone)` where an
   instant is needed). Required check: the analytics goldens (MOD-D29 item 3) unchanged. They cover DST
   changes and month and year ends in several zones.
3. **Then:** the statistics portlet (form as `LocalDate` with a `M/d/yyyy` editor), the
   aggregation import/export, the cookie service, and `PortalEvent.getTimestampAsDate()`.
   Delete `JodaTimeBridge`; drop `joda-time`, Jadira and `jackson-datatype-joda`; the ArchUnit
   Joda rule becomes a plain (unfrozen) rule.

## Proposed LOG entries

* **MOD-D?: `java.time` replaces Joda-Time outside the JPA mappings.** The property
  editors (`DurationEditor`, `TemporalAmountEditor`) accept every value the Joda editors
  accepted, and periods keep calendar-day semantics in the value's zone. Code still meeting Joda
  entities goes through `JodaTimeBridge` only. The Marketplace JSON keeps epoch milliseconds.
  The entities, DAOs and the aggregation engine (61 files) remain, to be converted with the
  Hibernate 5.2 step behind the persistence and analytics goldens.
* **MOD-I?: the `portal.properties` example `purgeDelay=P1H` was invalid** (fixed to `PT1H`).
