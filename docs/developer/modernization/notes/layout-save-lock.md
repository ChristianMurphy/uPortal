<!-- markdownlint-disable -->

# Overlapping saves of one user's layout (MOD-I399)

Branch `ccr-43ba0785-hzlrj5-layout-save-lock` in uPortal, from the integration tip `280a638dd`,
with landing 4 (`0cf02679f`) merged in. The uPortal-start branch of the same name has no commit
of its own. Its push runs build this uPortal branch.

## What failed

uPortal-start Integration run 38775913145, job 116563567769 (G-migrate postgres-16 to postgres-18,
target Playwright): `background-preference.spec.ts:124` passed only on a retry. The portal log
(downloaded by the portal-races agent) has three sign-ins of the student at 17:43:53, on threads
`exec-1`, `exec-3` and `exec-10`. The one on `exec-3` answered 500 for
`/uPortal/Login?userName=student`:

```
Caused by: org.springframework.dao.DuplicateKeyException: ConnectionCallback; ERROR: duplicate key value violates unique constraint "up_layout_param_pkey"
  Detail: Key (user_id, layout_id, struct_id, struct_parm_nm)=(21, 1, 22, dlm:origin) already exists.
	at org.apereo.portal.layout.simple.RDBMUserLayoutStore.setUserLayout(RDBMUserLayoutStore.java:1152)
	at org.apereo.portal.layout.dlm.RDBMDistributedLayoutStore._getUserLayout(RDBMDistributedLayoutStore.java:1291)
	at org.apereo.portal.layout.dlm.DistributedLayoutManager.getDistributedUserLayout(DistributedLayoutManager.java:223)
	at org.apereo.portal.layout.dlm.DistributedLayoutManager.afterPropertiesSet(DistributedLayoutManager.java:164)
```

Each sign-in loads the layout, and `_getUserLayout` saves the merged PLF when the merge changed
it. No transaction surrounds a sign-in. Each save runs in a transaction of its own.

## Cause

`RDBMUserLayoutStore.setUserLayout` reads the layout id from `UP_USER_PROFILE`, deletes the user's
`UP_LAYOUT_PARAM` and `UP_LAYOUT_STRUCT` rows and inserts the new ones. The saves of one user
can overlap:

* On PostgreSQL under READ COMMITTED (the store's isolation since MOD-I360), save B's delete waits
  for the rows save A deleted. When A commits, B skips those rows and doesn't see the rows A
  inserted, since they aren't in the snapshot of B's statement. B's next delete, on
  `UP_LAYOUT_STRUCT`, takes a new snapshot and removes A's structure rows, so B's insert fails on
  `up_layout_param_pkey`. That is the CI failure.
* For a user's first layout there is nothing to delete, and under READ COMMITTED with row locks
  nothing makes B wait. Both saves insert the same keys, and the later insert fails on every
  database of the matrix (HSQLDB with its default table locks makes B wait).

MOD-I360's test let B commit before A deleted, so it didn't cover a delete that starts while the
other save is still open.

## Fix: lock the user's profile rows first

`setUserLayout` starts its transaction with
`UPDATE UP_USER_PROFILE SET LAYOUT_ID=LAYOUT_ID WHERE USER_ID=?`. A second save of the same user
waits there until the first commits, then reads the layout id and deletes the rows the first one
wrote. A user without profile rows, such as one deleted while signed in, logs a warning and saves
as before. The store keeps READ COMMITTED (MOD-I360). The second save's statements read the
committed rows whatever isolation the database defaults to.

### Lock order

1. The layout manager's monitor. `DistributedLayoutManager` is one object per session, and the
   sign-in (`afterPropertiesSet`, `loadUserLayout`), the reload (`getDistributedUserLayout`,
   MOD-I363), `saveUserLayout` and `updateNode` are synchronized on it.
2. The user's `UP_USER_PROFILE` rows, in key order (the new lock).
3. The user's `UP_LAYOUT_PARAM` and `UP_LAYOUT_STRUCT` rows, then `UP_USER_LAYOUT`, then
   `UP_USER_PROFILE` again for a first layout, then `UP_USER` when channels were added.

When the save starts, the sign-in and the reload have taken the monitor and nothing else.
Neither runs inside a transaction. The save commits, and releases 2 and 3, before they release
the monitor. Besides
saves, resets, new profiles and user deletion write the profile rows:

* A save that now waits for another save at step 2 would have waited for that save's layout rows
  at step 3 before.
* A reset (`UserLayoutHelperImpl`, `@PortalTransactional`) updates the profile rows and deletes
  stylesheet preferences. It doesn't take the monitor or a layout row.
* A new profile (`addUserProfile`, or `RDBMUserIdentityStore` creating a user) is an insert, and
  the lock doesn't wait for rows that aren't committed yet.
* `removePortalUID` deletes the layout rows too, so a save waited for it before as well.

So the lock doesn't add a cycle that the layout rows didn't already allow.

### Why not the `UP_USER` row

MOD-I391 locks the user's `UP_USER` row for the locale store, and the LOG proposed the same here.
For the layout store that row closes a cycle that the database can't see, since one side waits
for a Java monitor:

* `updateNode` holds the monitor and, for an attribute of a fragment node, calls
  `getNextStructDirectiveId`, which updates `UP_USER` in a transaction of its own (MOD-I361).
* `StylesheetUserPreferencesServiceImpl.setLayoutAttribute` is `@Transactional`. Its
  `getUserLayout()` can reload the layout, and that save joins its transaction and keeps its locks
  after the monitor is released. When the value equals the fragment's value, it calls
  `removeLayoutAttribute`, which calls `getUserLayout()` and so takes the monitor again.

With an `UP_USER` lock in the save, an `updateNode` of the same session between those two calls
waits for `UP_USER` while holding the monitor, and `setLayoutAttribute` waits for the monitor
while holding `UP_USER`. PostgreSQL has no lock timeout by default, so both requests would hang,
and every later save of that user with them. The profile rows aren't taken under the monitor by
anything but a save.

The same pattern already exists without this change, with the layout rows: a save under the
monitor in another request of the session waits for the rows `setLayoutAttribute`'s joined save
holds (proposed row below).

### Other paths checked

| Path | Locks | With the new lock |
| --- | --- | --- |
| `RDBMUserIdentityStore.removePortalUID` | `UP_USER`, `UP_USER_LAYOUT`, `UP_USER_PROFILE`, layout rows | can meet a save that added channels in a deadlock the database detects, as before (the old save had locked the layout rows and wanted `UP_USER`) |
| `RDBMDistributedLayoutStore.importLayout` (`@Transactional`) | profile import, `UP_USER`, then the save joins | a profile import that writes one of several profile rows can meet a concurrent save in a deadlock the database detects. Import and sign-in of one user at once only |
| `getNextStructId` (MOD-I361) | `UP_USER` only, own transaction | unchanged |
| `getPersonalUserLayout`, first layout | `UP_USER` in autocommit | unchanged |
| `RDBMLocaleStore.updateUserLocales` (MOD-I391) | `UP_USER`, `UP_USER_LOCALE` | unchanged |

No caller of `saveUserLayout` (`UpdatePreferencesServlet`, `FavoritesService`,
`FavoritesEditController`) runs in a transaction.

## MOD-I364

The fix doesn't change MOD-I364. Each save still writes the whole layout, and the save that
takes the lock last replaces the other one, so a session that saves an old copy still overwrites
a newer save. The lock does give a version check a place without a race: a save could compare a
layout version right after it locks the profile rows. A save that waits for an open reset now
runs after the reset's commit and, with an old copy, writes it back as the first layout, as a
save just after the reset did before.

## Tests

`RDBMUserLayoutStoreConcurrencyTest` (uPortal-webapp). Thread A stops before one statement,
thread B saves, and after B has had two seconds to finish, unless the database makes it wait, A
goes on. B now runs in a thread of its own, since it waits for A's lock. On HSQLDB the test uses a
database of its own with MVCC transactions, as uPortal-start's HSQLDB server runs. With HSQLDB's
default table locks the first-layout test passed on the old code.

| Test | What it does |
| --- | --- |
| `testLayoutSavedBetweenReadAndWrite` (MOD-I360) | A stops before its delete. Both save, and B's layout remains |
| `testLayoutSavedBetweenDeleteAndInsert` (new) | A stops after its deletes, before its inserts. Both save, and B's layout remains |
| `testFirstLayoutSavedTwiceAtOnce` (new) | the same with no rows and `LAYOUT_ID=0`. Both save, B's layout remains, and the profile has `LAYOUT_ID=1` |
| `testStructIdTakenWhileALayoutSaves` (new) | A takes the save's lock and stops, and B takes a structure id. B must finish before A goes on |
| `testUserWithoutProfileRowStillSaves` (new) | no profile row: the save goes on |
| `testStructIdTakenBetweenReadAndWrite` (MOD-I361) | unchanged, with B in a thread |

Results (local, JDK 25, `--offline --no-daemon`):

| Store | HSQLDB, MVCC | PostgreSQL 16 | MariaDB 10.11, `innodb_snapshot_isolation=ON` |
| --- | --- | --- | --- |
| `aaa3740f9^` (before MOD-I360) | not run | not run | `testLayoutSavedBetweenReadAndWrite` fails, as A gets 1020 "Record has changed since last read in table 'UP_LAYOUT_PARAM'". In `testLayoutSavedBetweenDeleteAndInsert` B gets the same 1020, `testFirstLayoutSavedTwiceAtOnce` fails with deadlock 1213 and `testStructIdTakenBetweenReadAndWrite` with 1020 on `UP_USER` |
| tip `280a638dd` | `testFirstLayoutSavedTwiceAtOnce` fails with a unique constraint violation on `UP_LAYOUT_STRUCT`. `testLayoutSavedBetweenReadAndWrite` fails with `[A]` where `[B]` was expected | In `testLayoutSavedBetweenDeleteAndInsert` B fails with the CI error, "duplicate key value violates unique constraint up_layout_param_pkey" for the key `(9001, 1, 1, name)`. `testFirstLayoutSavedTwiceAtOnce` fails on `up_layout_struct_pkey`. `testLayoutSavedBetweenReadAndWrite` fails: `[A]` | `testFirstLayoutSavedTwiceAtOnce` fails: duplicate entry `1-9001-1` for key PRIMARY. `testLayoutSavedBetweenReadAndWrite` fails: `[A]` |
| this branch | pass, 6 runs | pass, 6 runs | pass, 6 runs |

The first 4 runs on this branch had the 5 tests before `testStructIdTakenWhileALayoutSaves`, and
the last 2 had all 6. The tip and `aaa3740f9^` rows were run before that test was added.

A seeded fault, the lock moved to `UPDATE UP_USER SET NEXT_STRUCT_ID=NEXT_STRUCT_ID`, failed
`testStructIdTakenWhileALayoutSaves` on HSQLDB with "B waited for A's save". The other 5 tests
passed with it, since that lock orders the saves too.

On the tip, `testLayoutSavedBetweenReadAndWrite` fails on every database because B commits
between A's read and A's write and A replaces B's rows. With the lock that order can't happen.
With HSQLDB's default table locks (the shared test database before this branch), the tip failed
only `testLayoutSavedBetweenReadAndWrite`.

HSQLDB, the changed modules: uPortal-layout-impl 12 tests and uPortal-webapp 473 (76 of them
`@Ignore`d before this branch), with the persistence goldens (`EntityRoundTripGoldenTest` 6,
`DaoQueryGoldenTest` 1, `AggregationGoldenTest` 2), pass. `spotlessCheck` of both modules,
`spotbugsMain` of uPortal-layout-impl and `check-freeze-store.py` (no rule grew) pass. After the
merge of landing 4, uPortal-webapp's 483 tests pass again on HSQLDB, and `spotlessCheck` and
`spotbugsMain` pass.

## Commands

The uPortal worktree is `/home/user/wt/uPortal-layoutlock`. PostgreSQL 16 ran from the system's
binaries, as the `postgres` user, with a data directory of its own on port 8287. MariaDB 10.11.14
ran from Ubuntu's `mariadb-server-core` package, extracted with `dpkg -x`, as `nobody`, on port
8280 with `--innodb-snapshot-isolation=ON`. No portal ran. For the old code, the store's source
was restored from the tip or from `aaa3740f9^`, and the test run again.

```sh
./gradlew --offline --no-daemon :uPortal-webapp:test --rerun \
  --tests org.apereo.portal.layout.simple.RDBMUserLayoutStoreConcurrencyTest \
  -Duportal.test.db.driver=org.postgresql.Driver \
  -Duportal.test.db.url=jdbc:postgresql://127.0.0.1:8287/uportal \
  -Duportal.test.db.username=uportal -Duportal.test.db.password=uportal
```

## Proposed LOG rows

MOD-I399 has its number. This row replaces its "Open" resolution.

| ID | Area | Issue | Status |
| --- | --- | --- | --- |
| MOD-I399 | Layout | Saves of one user's layout that overlap fail on PostgreSQL with `DuplicateKeyException` on `up_layout_param_pkey`: `setUserLayout` deletes, then inserts, and the second save's delete doesn't see the rows the first one committed. For a first layout both saves insert the same keys on every database. Parallel sign-ins of one user each save the merged layout. `background-preference.spec.ts:124` failed at the student's sign-in in job 116563567769 (run 38775913145). | **Fixed** (uPortal `fix(layout): save one user's layout from two requests in turn`): a save first locks the user's `UP_USER_PROFILE` rows. The `UP_USER` row would close a cycle with `updateNode`'s directive ids under the layout manager's monitor, and `testStructIdTakenWhileALayoutSaves` fails with it. `RDBMUserLayoutStoreConcurrencyTest` fails on the old code on HSQLDB (MVCC), PostgreSQL 16 (the CI error) and MariaDB 10.11. MOD-I360's test runs its second save in a thread and still fails before MOD-I360 (1020) (`notes/layout-save-lock.md`). |
| MOD-I? | Layout | A layout reload inside a caller's transaction keeps the user's layout row locks after the layout manager's monitor is released. `StylesheetUserPreferencesServiceImpl.setLayoutAttribute` (`@Transactional`) can reload and save in its first `getUserLayout()`, then take the monitor again through `removeLayoutAttribute`. A save in another request of the same session that holds the monitor and waits for those rows then deadlocks with it, and the database can't see it: on PostgreSQL both requests hang. A first-layout reload in that transaction also holds `UP_USER` (MOD-I361). From the review for MOD-I399, not seen in a run. | **Open.** Read the layout before the transaction starts, or keep the reload out of the caller's transaction (`notes/layout-save-lock.md`). |

## Runs

The first push was uPortal `1ca924f62`. The uPortal-start branch of the same name pointed at the
integration tip `09ccc9cbb` and had no commit of its own.

| Run | Result |
| --- | --- |
| uPortal CI 42482810742 | passed (build and tests on JDK 21, Prose, Commit messages) |
| uPortal DB integration 42482816258, started by hand | passed. uPortal-webapp's 473 tests (76 `@Ignore`d, as on HSQLDB), the 6 of `RDBMUserLayoutStoreConcurrencyTest` included, pass on PostgreSQL 18, MariaDB 12.3, MySQL 9.7, SQL Server 2025 and Oracle 23, and on the canaries PostgreSQL 19, MariaDB 13 and MySQL 26. It cancelled the push run 42482810680, as they share a group |
| uPortal DB copy tool 42482810749 | passed |
| uPortal-start Integration 42482825596 | passed. Build uPortal built uPortal `1ca924f62` from this branch (notice of job 128738294124). Playwright on HSQLDB and on PostgreSQL 18 passed 512 tests each, with no test that passed only on a retry. Playwright with CAS, the PostgreSQL 18 harness, G-migrate mariadb-11.4 to postgres-18 and Cluster passed. The portal logs of both Playwright jobs have no duplicate key on a layout table and no "No UP_USER_PROFILE row" warning |
| uPortal-start CI 42482825645 | passed |
| uPortal-start HTTP replay 42482825581 | passed, "Replay against 5.17" included |
| uPortal-start Migration equivalence 42482825719 | passed |
| uPortal-start G-index 42482825617 | passed, with its seeded faults |

After the merge of landing 4, the push was uPortal `26789dc9d`, and the uPortal-start branch
moved to the integration tip `9c20dbaf6`.

| Run | Result |
| --- | --- |
| uPortal CI 42486435686 | passed |
| uPortal DB integration 42486463935, started by hand | passed. uPortal-webapp's 483 tests pass on PostgreSQL 18, MariaDB 12.3, MySQL 9.7, SQL Server 2025 and Oracle 23, and on the three canaries. It cancelled the push run 42486435689 |
| uPortal DB copy tool 42486435682 | passed |
| uPortal-start Integration 42486476143 | passed. Build uPortal built uPortal `26789dc9d` from this branch (notice of job 128749348083). Playwright on HSQLDB and on PostgreSQL 18 passed 512 tests each, with no test that passed only on a retry. The other jobs of the push profile passed |
| uPortal-start CI 42486476098, HTTP replay 42486476172, Migration equivalence 42486476336 | passed. "Replay against 5.17" was skipped, as on a topic push that doesn't change `tests/replay`. It ran and passed on the first push |
| uPortal-start G-index 42486476104 | passed, with its seeded faults |

The postgres-16 to postgres-18 G-migrate pair, where the failure was, runs only in the full matrix
of a batch.
