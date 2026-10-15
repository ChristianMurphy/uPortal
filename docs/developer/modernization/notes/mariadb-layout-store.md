<!-- markdownlint-disable -->

# Layout writes that overlap, on MariaDB and everywhere (MOD-I360 to MOD-I364)

Branch `ccr-43ba0785-hzlrj5-mariadb-layout-store` in uPortal and in uPortal-start, both from the
integration branch. uPortal has the database fixes. uPortal-start has the spec isolation. The
portal side of the reset flakes (MOD-I363) waits for the owner's choice of semantics.

## What failed

uPortal-start Integration run 38204127794, job 114669441841 (G-migrate postgres-15 to mariadb-12.3,
target Playwright): background-preference:124 and reset-my-layout:211 and :338 flaked as the
student. The portal log had MariaDB error 1020 at 05:18:43 on `up_layout_param` and at 05:20:07 on
`up_layout_struct`, both at `RDBMUserLayoutStore.setUserLayout` (`RDBMUserLayoutStore.java:1176`,
the `DELETE FROM UP_LAYOUT_PARAM`), called from `RDBMDistributedLayoutStore._getUserLayout:1291`.
That call saves the merged PLF (the user's personal layout fragment) when the merge changed it.
Several Playwright workers signed in as the student at once (`/Login?userName=student`, threads
exec-7, 8, 11 and 12). Each saved the merged layout, and a later save's delete hit rows an earlier
save committed after the later one first read.

Run 38672160727 then showed reset-my-layout:211 failing on HSQLDB and :181 flaky on PostgreSQL 18.
The HSQLDB artifact shows two different failures, and neither is an empty stored layout:

* Attempt 1 ("Received Array []") is a guest page. The welcome render after the sign-in answered
  500 at 03:03:48, from `org.w3c.dom.DOMException: NOT_FOUND_ERR` in
  `PLFIntegrator.applyChildChanges(PLFIntegrator.java:79)`, called from `createCompositeILF:1324`.
  The MariaDB run has the same exception at 05:18:43 on exec-7.
* Retries 1 and 2: after the reset and a new sign-in, the test's own tab is still there. The reset
  was undone.

## Causes

1. **Snapshot isolation (MOD-I360).** MariaDB 11.6+ defaults to `innodb_snapshot_isolation=ON`.
   Under REPEATABLE READ, a write to a row that changed after the transaction's first read fails
   with 1020. `setUserLayout` reads the layout id, then deletes the user's rows and inserts them
   again, in one transaction. It is the MOD-I99 class.
2. **Duplicate structure ids (MOD-I361).** `getNextStructId` read `NEXT_STRUCT_ID`, updated it
   where it was unchanged and returned the new id without checking the update count. Two requests
   of one user could get the same id on every database. MariaDB gave 1020 instead.
3. **Counter blocks (MOD-I362).** `HibernateStyleCounterStore` reads `UP_SEQUENCE`, then updates it
   where it is unchanged and tries again on a lost race. On MariaDB a lost race between two servers
   was 1020 instead of a retry.
4. **Stale PLF reloads (MOD-I363).** `LayoutCachingService` keys the cached layout by user name and
   profile, so all sessions of a user share it. Every non-guest sign-in evicts it
   (`DistributedLayoutManager.afterPropertiesSet`). Each other session then reloads in
   `getDistributedUserLayout`, which isn't synchronized, merging into the PLF that its IPerson has
   held since that session's sign-in (`RDBMDistributedLayoutStore.getPLF`). The merge edits that
   DOM in place. Requests of one session that reload at the same moment give the NOT_FOUND_ERR
   and a 500.
   When the merge changed the PLF, `_getUserLayout` saves the session's old copy, which deletes a
   tab another session of the user added or, after a reset (`LAYOUT_ID=0`), writes the old layout
   back. This is the same on every database and isn't new in 6.0.

## Fixes on this branch

* `RDBMUserLayoutStore`'s transactions run under READ COMMITTED (commit `fix(layout): save layouts
  under read committed`). A transaction that joins an outer one keeps the outer one's isolation.
  The first-layout copy in `getPersonalUserLayout` runs its statements on the outer connection in
  autocommit, so it isn't affected.
* `getNextStructId` increments first and reads after (commit `fix(layout): give each structure id
  to one request only`).
* `counterStoreTransactionOperations` in `persistenceContext.xml` runs under READ COMMITTED (commit
  `fix(persistence): fetch counter blocks under read committed`). The test builds that bean from
  the production XML, so reverting the XML fails it.
* uPortal-start: the Reset My Layout API spec's user tests sign in as the developer and run in
  serial mode, and the UX spec's Reset My Layout describe runs in serial mode (commit
  `test(e2e): keep the reset api spec off the student's layout`). The developer's layout isn't
  changed by any other spec. The Spanish and French tests in the UX spec still sign in as the
  developer and change only the locale, so until MOD-I363 is fixed their reloads could still save
  an old copy.

## Tests

`RDBMUserLayoutStoreConcurrencyTest` (uPortal-webapp) uses a test subclass of
`RDBMUserLayoutStore` that writes one structure row and one parameter row. Its JDBC connections
stop one thread before a chosen statement until the test lets it go on. `CounterStoreTest`
`testBlockTakenBetweenReadAndWrite` stops a second counter store between its read and its write.
Both run on whatever database the `uportal.test.db.*` properties select, as `db-integration.yml`
runs them.

| Test | Old code | Fixed code |
| --- | --- | --- |
| `testLayoutSavedBetweenReadAndWrite` | fails on MariaDB 12.3 (1020 on UP_LAYOUT_PARAM). Passes on HSQLDB | passes on all six vendors |
| `testStructIdTakenBetweenReadAndWrite` | fails on HSQLDB ("A and B got the same id. Actual: s1") and on MariaDB 12.3 (1020 on UP_USER) | passes on all six vendors |
| `testBlockTakenBetweenReadAndWrite` | fails on MariaDB 12.3 (1020 on UP_SEQUENCE) | passes on all six vendors |

The six-vendor results are in "Evidence" below.

## Other read-then-update candidates

From a read-only review. None of them is fixed here.

* `StylesheetUserPreferencesServiceImpl` `@Transactional` setters (`setLayoutAttribute`,
  `setStylesheetParameter` and the removes): load the user's preferences, then persist. Parallel
  layout AJAX calls of one user. It has no `@Version`. Elsewhere the later write replaces the
  earlier one, and MariaDB would give 1020. MOD-I312 already covers its cache. Candidate for the same READ COMMITTED treatment.
* Portlet preferences (`PortletEntityPreferencesImpl`, `PortletEntityPreferencesAPIImpl` through
  `JpaPortletEntityDao.updatePortletEntity`): `PortletPreferencesCacheTest` already accepts 1020 as
  a refused save. A user-visible failure, but not data loss.
* `JpaPortletCookieDaoImpl.addOrUpdatePortletCookie` and `purgeExpiredCookies`: parallel portlet
  requests with the same cookie. The expiration update already catches the failure.
* `UserLayoutHelperImpl.resetUserLayout` (`@PortalTransactional`): reads profiles and preferences,
  then updates the profile and deletes preferences. Overlap needs the same user's layout change at
  the same moment.
* Admin and import paths (`importLayout`, `RDBMUserIdentityStore.removePortalUID`,
  `FragmentDefinitionDao`, event aggregation under its cluster lock): low.
* `RDBMLocaleStore.updateUserLocales` deletes before it reads anything, which doesn't trigger
  1020.

## Evidence

* Old code, MariaDB 12.3, local Docker: `testLayoutSavedBetweenReadAndWrite` and
  `testStructIdTakenBetweenReadAndWrite` failed with 1020 (UP_LAYOUT_PARAM, UP_USER).
  `testBlockTakenBetweenReadAndWrite` failed with 1020 on UP_SEQUENCE.
* Old code, HSQLDB: `testStructIdTakenBetweenReadAndWrite` failed with duplicate id `s1`.
* Fixed code, local Docker, one vendor at a time (the images and URLs of `db-integration.yml`):
  `RDBMUserLayoutStoreConcurrencyTest` (2 tests) and `CounterStoreTest` (2 tests) pass with no
  failures, errors or skips on HSQLDB, PostgreSQL 18, MariaDB 12.3, MySQL 9.7, SQL Server 2025 and
  Oracle 23.

## Proposed LOG rows (MOD-I360 to MOD-I364)

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I360 | Persistence | **Data loss on MariaDB 11.6+.** A layout save that overlaps another save of the same user fails with 1020 "Record has changed since last read in table 'up_layout_param'": `setUserLayout` reads, then deletes and inserts the user's rows, under REPEATABLE READ with `innodb_snapshot_isolation`. Parallel sign-ins of one user each save the merged layout. Found by uPortal-start run 38204127794, job 114669441841 (G-migrate to MariaDB 12.3). | **Fixed**: the layout store's transactions run under READ COMMITTED. `testLayoutSavedBetweenReadAndWrite` fails on the old code on MariaDB 12.3 and passes on all six vendors. Upstream candidate. |
| MOD-I361 | Persistence | `RDBMUserLayoutStore.getNextStructId` read `NEXT_STRUCT_ID`, updated it where unchanged and ignored the update count, so two requests of one user could get the same structure id on every database (1020 on MariaDB 11.6+). | **Fixed**: increment first, then read. `testStructIdTakenBetweenReadAndWrite` fails on the old code on HSQLDB (`s1` twice) and MariaDB 12.3, and passes on all six vendors. Upstream candidate. |
| MOD-I362 | Persistence | `HibernateStyleCounterStore`'s compare-and-set on `UP_SEQUENCE` fails with 1020 on MariaDB 11.6+ when two servers fetch a block of the same counter at once. The store's retry never runs. User and group creation in a cluster. | **Fixed**: `counterStoreTransactionOperations` runs under READ COMMITTED. `testBlockTakenBetweenReadAndWrite` builds the production bean, fails on the old definition on MariaDB 12.3 and passes on all six vendors. Upstream candidate. |
| MOD-I363 | Layout | **Lost layout changes.** The layout cache is shared by all sessions of a user, and every sign-in evicts it. Each other session then reloads with the PLF copy it took at its own sign-in, merges into it in place (unsynchronized, so two requests of one session give `DOMException NOT_FOUND_ERR` and a 500) and saves that old copy when the merge changed it. A tab added in another session is deleted, and a reset is undone. All databases, not new in 6.0. Found by reset-my-layout:181 and :211 (runs 38204127794, 38672160727). | uPortal-start: the reset API spec signs in as the developer, and both reset describes run serially. Portal: **open, waiting for the owner** to choose between reloading the PLF from the database on a cache miss with a per-session lock (A), only the lock (B) or a per-session cache (C). |
| MOD-I364 | Layout | An explicit layout edit (such as addTab) in a session whose PLF is older than another session's save still overwrites that save with a whole-layout copy. Users with two devices or windows can lose changes. | **Open.** Needs per-user layout versioning (a version on `UP_USER_LAYOUT`, checked when the layout is saved), so that an old copy is refused or merged. Not on this topic. |
