# Handoff: connections the portal holds while a page renders (MOD-I243 onward)

Branch `ccr-43ba0785-hzlrj5-connection-holding` in uPortal. The work stopped here when the owner
paused new work. This note gives the state, the evidence and the next step.

## The problem

Under the G-index driver (16 people browsing and signing in at once), the portal opened 130
database connections with the default pools, more than PostgreSQL's default `max_connections` of
100. Almost none of them ran a statement: at the peak the server saw one or two active backends
and the rest idle. With `PortalDb` at 30, requests waited 5 seconds for a connection and failed
("Pool empty"). A thread dump at 30 (uPortal 5.17.9, MOD-I190 work in uPortal-start's
db-connections note) showed two causes:

1. Each portlet render opened a transaction to read its portlet definition, and Spring took a JDBC
   connection when that transaction began, even when the definition came from the second-level
   cache.
2. Request threads waited for their portlets to render with the page's OpenEntityManagerInView
   session open, and that session appeared to keep the connection it used earlier in the request.

## Changes on this branch

* **Fix 1 (commit `bd48dc4`).** On PostgreSQL, the ten read methods of `JpaPortletDefinitionDao`
  and `JpaPortletEntityDao` ran in a read-only transaction (`@DialectAwareTransactional` for
  `PostgreSQL81Dialect`). The other vendors already ran them without one. The transaction existed
  for large objects, and the portlet tables have none: uPortal-start's PostgreSQL LOB report
  (`tests/db/snapshots/5.17/postgres-18/lob-report.txt`) lists only the two event tables. The
  branch drops the annotations, so PostgreSQL reads these like every other vendor.

Fix 2 isn't started.

## Evidence

The uPortal-web tests pass locally (60 tests, the portlet DAO tests among them).

Measured locally with uPortal at this branch's base (`9de4895`, Hibernate 5.2), deployed by
uPortal-start's db-connections branch, which reports p50, p95 and "Pool empty". G-index driver on
PostgreSQL 18 at scale 0.01, 16 users for 240 s, `--no-suite`, `max_connections=300`. "Peak" is
the most client connections at once, sampled every second.

| `PortalDb` `maxActive` | Before: p50 / p95 ms | Before: "Pool empty" | Before: peak | Fix 1: p50 / p95 ms | Fix 1: "Pool empty" | Fix 1: peak |
| --- | --- | ---: | ---: | --- | ---: | ---: |
| 100 (default) | 758 / 3,992 | 0 | 130 | 653 / 4,015 | 0 | 92 |
| 30 | 728 / 3,462 | 194 | 64 | 708 / 3,311 | 32 | 73 |
| 60 | not measured | | | 594 / 3,272 | 0 | 89 |

Fix 1 removes most of the waiting at 30 and lowers the default peak from 130 to 92. It doesn't
reach the 80 the owner asked for, and 30 still fails some requests, so fix 2 is still needed.

The audits of these runs failed only on the two required R4 entries, because a driver-only run
doesn't close an interval. That doesn't affect the measurement.

## Next step

1. Confirm fix 2 on Hibernate 5.2 with a thread dump at `PortalDb` 30. `jstack` couldn't attach
   to the Tomcat started by Gradle here, and a `SIGQUIT` dump went to Tomcat's console, which
   Gradle doesn't keep. Start Tomcat with `-XX:+UnlockDiagnosticVMOptions -XX:+LogVMOutput
   -XX:LogFile=<path>` in `CATALINA_OPTS`, and check that the process receiving `SIGQUIT` is the
   Tomcat JVM (`org.apache.catalina.startup.Bootstrap` in its command line).
2. If the page's session holds a connection after a read outside a transaction, release it after
   each statement or transaction. Spring 4.3's `HibernateJpaDialect` and Hibernate 5.2's
   `hibernate.connection.handling_mode` are the settings to look at. Check carefully for lazy
   loading that needs the connection after the read.
3. Measure with the driver at the default pools and at `PortalDb` 30 and 60, run the whole
   Playwright suite, and seed a fault (a pool too small for the load must show "Pool empty").
4. Evidence for merging, per the agent guide: uPortal push runs, a `db-integration.yml` run started
   by hand, and a uPortal-start Integration run on a branch with the same name.

## Proposed LOG rows (MOD-I243 onward)

* **MOD-I243 Portlet definition and entity reads took a connection per render on PostgreSQL.**
  A read-only transaction, needed only for large objects the portlet tables no longer have,
  took a JDBC connection even for cached entities. Fixed on this branch: peak connections 130 to
  92 at 16 users, "Pool empty" at `PortalDb` 30 from 194 to 32.
* **MOD-I244 The page's session keeps a connection while its portlets render.** To confirm on
  Hibernate 5.2 and fix (next step 1 and 2).
