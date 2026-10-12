# Handoff: portlets that start late (MOD-I192)

MOD-I192, a bundled portlet application missing in uPortal-start's Playwright run on PostgreSQL 18,
is two separate bugs. Both can hit a real portal.

* **MOD-I350.** A portlet requested before Pluto has registered it stays out of service until
  Tomcat restarts. Topic branch `ccr-43ba0785-hzlrj5-portlet-startup` in uPortal and uPortal-start.
* **MOD-I351.** On PostgreSQL and Oracle, Announcements, Calendar and NewsReader share one
  `hibernate_sequence`, and the schema export of each of them resets it. Topic branch
  `ccr-43ba0785-hzlrj5-portlet-sequence` in uPortal-start, based on the 1a commit of
  `-portlet-startup` (uPortal-start `e1b41fb9c`), so that its PostgreSQL job doesn't meet MOD-I350.

## MOD-I350: a portlet requested before it registers

### Cause

Pluto 2.1.0-M3's `PortletServlet` registers its portlet with the portal from a background timer,
`startTimer.schedule(..., 1, 10000)`, which succeeds only once uPortal's container services
(`PlutoServices`) exist. Tomcat starts the portlet webapps before uPortal. The last portlets
register a few seconds after Tomcat reports that it has started. Until then `dispatch()` threw
`new UnavailableException("Portlet ... unavailable")`. Tomcat takes the one-argument form as
permanent and refuses the servlet until it restarts.

In runs 38173077195 and 38153214673 the PostgreSQL harness started Playwright right after the
first 302 from `/uPortal/`. In 38173077195 SimpleContentPortlet's first servlet registered at
21:42:58.260, its `cms` servlet at 58.643, and a staff render reached `cms` at 58.312. Tomcat refused
`[cms]` and `[attachments]` for the rest of the run. The HSQLDB jobs install npm and
Playwright between the wait and the tests, which hid the race there. The replay had met the
same bug with NotificationPortlet's `EmergencyAlert` (uPortal-start
`docs/developer/modernization/notes/http-replay.md`, finding 10) and waits 30 seconds.

### Change 1a: the waits (uPortal-start)

`.github/scripts/wait-for-portlets.sh [tomcat base] [portal.log offset]` waits until every
webapp with a `WEB-INF/portlet.xml` has registered all its portlet servlets. uPortal's
`LocalPortletContextManager` writes "Registering <n> portlets for context /<ctx>" once per
webapp and a registration line for each servlet to `logs/portal/portal.log`. Waiting for the
first line of each webapp isn't enough (the timestamps above). Only the log after the offset
counts, so a restart doesn't pass on the lines of the previous start. It fails after
`PORTLET_WAIT_SECONDS` (120) and names the webapps that aren't ready, with an `::error`
annotation on GitHub Actions.

`start_portal` in `tests/db/lib.sh` (database harness, G-migrate, migration equivalence and
G-index), the replay's `tests/replay/portal.sh`, and the portal waits in `integration.yml` (HSQLDB,
CAS and the nightly browsers jobs) and `CI.yml` call it. `wait-for-portlets.test.sh` (14 rows)
runs in the Workflow lint workflow.

### Change 1b: the servlet (uPortal) and the shared jars (uPortal-start)

`uPortal-pluto-container-driver-api`'s `PortletServlet`:

* A request that finds the portlet not registered has the servlet's timer try the registration
  at once and waits for it up to 10 seconds. The attempt runs on the timer thread, so a request
  that uPortal interrupts after its render timeout can't make the portlet's `init()` fail.
* If that wait runs out, it throws `UnavailableException(msg, 1)`, and Tomcat holds the servlet
  unavailable for one second only. A portlet whose `init()` failed stays out of service, as before.
* The timer tries every second instead of every 10 seconds.
* The portlet field is volatile and set only after `init()` has returned. Before, a request
  during `init()` could reach a portlet that wasn't initialised.

`PortletServletRegistrationTest` covers a request before the timer has tried, a request that
can't wait, an interrupted request and a portlet that fails to start.

uPortal-start's `gradle/tasks/tomcat.gradle` puts uPortal's `uPortal-pluto-container-api`,
`-container-driver-api` and `-taglib` in `shared/lib` instead of Apache's 2.1.0-M3 jars, unless
`uPortalVersion` is a released 5.17 (which doesn't publish them) or `-PsharedPluto=apache` is
set. `tomcatConfig` removes the Pluto jars of an earlier run first. The command-line tools
(`shell` in `overlays/build.gradle`) still take Apache's driver API. Its classes are the same apart
from `PortletServlet`, which the tools don't run. UPGRADING-6.0: "Pluto's shared jars come
from uPortal".

### Evidence

Local portal on ports 8380/8305/9187, uPortal 5.17.10-SNAPSHOT, HSQLDB.

| Check | Result |
| --- | --- |
| Unseeded, the old wait (requests right after `tomcatStart`), 3 starts | No servlet marked unavailable. Locally `SimpleContentPortlet` registered before the first answer |
| Unseeded, the new wait, 2 starts | The wait returned 0.47 s and 2 s after the last registration |
| Seeded (Apache's `PortletServlet`, `cms` registers 90 s after its `init`), old wait | `cms` marked unavailable at 00:00:36.98 by the staff render. `cms` initialised at 00:01:15, and the second staff login at 00:03:15 still got `Servlet [cms] is currently unavailable` (20 such lines) |
| Same seed, new wait | The wait reported "/SimpleContentPortlet (3 of 4)" for 52 s and returned 0.4 s after `cms` registered. No unavailable lines |
| Same seed, the 1b `PortletServlet`, old wait | The first staff render registered `cms` on its timer thread at 00:06:31.7, about a minute before the seeded attempt. No unavailable lines |
| Table test, seeded faults in the script | Counting only the first line of a webapp fails 3 rows, ignoring the offset fails 1, the wrong log path fails 11 |
| Unit test, seeded faults in `PortletServlet` | With Pluto's permanent exception back in `dispatch()`, 3 of 4 fail ("permanently unavailable", and `UnavailableException: Portlet cms unavailable`). The one-argument exception only when the wait runs out: 2 fail. No immediate timer attempt: 1 fails |

One local run is left out: a start whose wait ran while the table test's wrong-path fault was in
the working tree. It timed out and named all 13 webapps.

### CI evidence

| Piece | Head | Runs |
| --- | --- | --- |
| 1a | uPortal-start `e1b41fb9c` (landed as `8946c0a64`) | CI 38186707123, Workflow lint 38186707217 and Migration equivalence 38186707099 green. Integration 38186707117: harness, G-migrate and CAS green. Playwright hsqldb and postgres-18 each failed only the 4 `web-components-locale.spec.ts:165` tests that fail on the integration branch too (420 passed). The wait logged "All 13 portlet webapps have registered" 3 s (HSQLDB) and 5 s (PostgreSQL) after the first answer. HTTP replay 38186707129: 5 unexplained exchanges, the entity key escapes that integration `62559d980` registers after 1a's base |
| 1b | uPortal `57c71b597` (`18ece654f` after a merge of the integration branch), uPortal-start `cc35b026a` | uPortal CI 38188704512, DB integration 38188704511 and DB copy tool 38188704541 green. uPortal-start CI 38192421247 and Migration equivalence 38192421260 green. Integration 38192421230 built uPortal `57c71b597`: harness, G-migrate and CAS green, Playwright only the 4 locale tests. Manual HTTP replay 38192452052: 0 unexplained exchanges, security cells and logins. It failed only the registry check for the entity keys section, which uPortal's integration branch added after `57c71b597`'s base |
| MOD-I351 | uPortal-start `664c3eeb5` (landed as `c562f35bb`) | CI 38188560329, Migration equivalence 38188560225 and G-index 38188560286 green. Integration 38188560340: harness (zero drift), G-migrate and CAS green, Playwright only the 4 locale tests. The full suite on PostgreSQL logged "hibernate_sequence: moved from 6 past the highest id 14 (NEWS_DEFINITION)" |

## MOD-I351: the shared `hibernate_sequence`

### Cause

Hibernate's `native` generator gives these tables their ids from one sequence named
`hibernate_sequence` on PostgreSQL and Oracle (Hibernate 3.6 and 5.6 alike), and identity columns
on HSQLDB, MySQL, MariaDB and SQL Server:

| Portlet | Tables (id column) |
| --- | --- |
| Announcements | `ANNOUNCEMENT` (`ANN_ID`), `TOPIC` (`TOPIC_ID`), `SUBSCRIPTION` (`SUB_ID`) |
| CalendarPortlet | `CALENDAR_CONFIGURATION` (`CALENDAR_CONFIGURATION_ID`), `CALENDAR_STORE` (`CALENDAR_ID`) |
| NewsReaderPortlet | `NEWS_CONFIGURATION` (`NEWS_CONFIGURATION_ID`), `NEWS_DEFINITION` (`NEWS_ID`), `NEWS_SET` (`SET_ID`) |
| BookmarksPortlet | `BOOKMARK_STORE` (`ENTRY_ID`), `BOOKMARK_PREFS` (`PREF_ID`) |
| FeedbackPortlet | `FEEDBACK_STORE` (`ITEM_ID`) |
| NotificationPortlet | the six `NOTICE_*` tables (`ID`), JPA `AUTO` on Hibernate 5 |

The dataInit of Announcements, CalendarPortlet and NewsReaderPortlet exports the portlet's
schema, which drops and creates `hibernate_sequence`, and then imports the portlet's data.
Announcements' export does it too, though it doesn't log its DDL. The dataInit tasks already run
one at a time (MOD-I107's build service, `maxParallelUsages` 1). Only their order varies. The ids
a portlet imported before the last reset come out of the sequence again. In run 38160049629
Calendar's came last, and the EMERGENCY topic that Announcements inserts when it starts
(`UserConfiguredTopicSubscriptionService.java:104`) took a used id. Every order leaves some
portlet behind: with NewsReader before Announcements, the sequence was at 6 and `NEWS_DEFINITION`
at 14.

### Change (uPortal-start)

* `gradle/tasks/db.gradle`: the root task `dataInitHibernateSequence` finalizes every dataInit
  task and runs after all of them. uPortal's dataInit is one of them, so that the database
  harness, which runs only uPortal's, logs the task's line on every vendor. With the database in `${portal.home}/global.properties` on
  PostgreSQL or Oracle, it moves `hibernate_sequence` past the highest id of the tables above, and
  never back. The JDBC driver comes from a deployed webapp's `WEB-INF/lib`. On other databases it
  logs that there is nothing to do.
* `overlays/NewsReaderPortlet` and `overlays/Announcements`: `mustRunAfter` gives the order
  CalendarPortlet, NewsReaderPortlet, Announcements, so that every run gives the same ids.

### Evidence

Local PostgreSQL 18 (the harness image in a container of its own on port 15492), every overlay
deployed, uPortal's dataInit, then the portlets' dataInit without Calendar's, then Calendar's in a
second Gradle run, then Tomcat:

| Code | Result |
| --- | --- |
| Integration branch `62559d980` | The EMERGENCY insert took `TOPIC_ID` 3: `topic_pkey` violation, `Context [/Announcements] startup failed`, Announcements didn't register with uPortal |
| `-portlet-sequence` | After the first run: "moved from 6 past the highest id 14 (NEWS_DEFINITION)". After Calendar's: "moved from 3 past the highest id 14". Announcements registered its 2 portlets and the EMERGENCY topic got id 15 |

`dataInit --dry-run` lists CalendarPortlet, NewsReaderPortlet, Announcements, SimpleContentPortlet,
uPortal, then `dataInitHibernateSequence`. On HSQLDB the task logs "nothing to do". On an empty
PostgreSQL database it logs "no such sequence", with a sequence and a `TOPIC` row with id 41
"moved from 1 past the highest id 41 (TOPIC)", and run again "next value 42". The task's Oracle
statements haven't run here: this disk has no room for an Oracle 23 container image. The full
Integration run's `DB harness: oracle-23` job runs uPortal's dataInit and so the task.

## Proposed LOG rows (MOD-I350 to MOD-I359)

| ID | Row |
| --- | --- |
| MOD-I350 | A portlet requested before Pluto's `PortletServlet` had registered it stayed out of service until Tomcat restarted (Pluto threw the one-argument `UnavailableException`). After a start, uPortal-start's PostgreSQL harness rendered pages before SimpleContentPortlet's `cms` servlet had registered (runs 38173077195, 38153214673). Fixed in two steps: uPortal-start waits for every portlet servlet to register (`wait-for-portlets.sh`), and uPortal's `PortletServlet` registers at once on a request and is unavailable for one second at most. uPortal-start puts uPortal's Pluto jars in `shared/lib` |
| MOD-I351 | On PostgreSQL and Oracle, Announcements, Calendar and NewsReader (and Bookmarks, Feedback and Notification) draw their ids from one `hibernate_sequence`, and the schema export in the dataInit of the first three drops and creates it. When Calendar's dataInit came last, Announcements' EMERGENCY topic took a used id and Announcements failed to start (run 38160049629). uPortal-start's `dataInitHibernateSequence` now moves the sequence past every portlet id after the portlets' dataInit, and a `mustRunAfter` chain fixes the order |
| MOD-I352 | Long-term fix for MOD-I351, proposed: give Announcements, Calendar and NewsReader a sequence each (`<generator class="sequence">` with their own sequence name). They are separate Apereo projects |
