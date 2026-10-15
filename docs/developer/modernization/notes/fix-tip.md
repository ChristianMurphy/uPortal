# fix-tip: the red uPortal-start integration tip

The uPortal-start `Integration` run 38204127794 (uPortal-start 644875432, uPortal 47d6333e2c)
failed in several jobs. This note gives the cause of each failure, the fix and the evidence. The
work is on `ccr-43ba0785-hzlrj5-fix-tip` in both repositories.

## Status

| Failure | Cause | Fix |
| --- | --- | --- |
| A. RTL spec, "no #webSearchContainer" | The spec measured the Search portlet's form. The header now holds `<up-search-launcher>` | uPortal-start, the spec measures the launcher |
| B. `tests/layout/pages.spec.ts:98` at 320px | Calendar spill (MOD-I286) | owned by `ccr-43ba0785-hzlrj5-layout-calendar` |
| C. Query budgets and Cluster, "Artifact not found" | The jobs downloaded the old artifact name | uPortal-start, the jobs use the build job's artifact output |
| D. SQL Server, portlet-list names in any script | `varchar` columns until the 6.0 schema step (MOD-D44) | uPortal-start, the spec expects the conversion on SQL Server. uPortal, UPGRADING-6.0 says so |
| E. G-migrate postgres-16 to postgres-18 and postgres-15 to mariadb-12.3 | A and B in the target's whole suite, and on MariaDB concurrent layout writes | A here. B on its branch. The MariaDB part is open |

## A. The RTL spec

The pair "header: logo before search" measured `#webSearchContainer`. The search channels became
web components in uPortal-start b0b658e97. The RTL spec (459446824) was written against the
portlet markup, and both came in the same batch. The spec now measures
`#region-header-right up-search-launcher`. It measures each element by the box of its content,
through a range over the element's children. A web component's content is in its shadow root,
which that range doesn't see. A shadow host counts with its own box.

Local runs on HSQLDB with uPortal 47d6333e2c and `testPortalLocales=ar_XB`:

| Run | Result |
| --- | --- |
| The fixed spec | 11 passed |
| Seeded: the old selector | "is mirrored" and "is not mirrored" fail with "no #webSearchContainer", as in CI |
| Seeded: the range only, without the shadow host's box | "is not mirrored" fails on "header: logo before search" |

## C. The uPortal build artifact

uPortal-start 949f2cc95 renamed the build artifact to `uportal-m2-<commit>`. Commit af7c50676
changed the browsers job, but the cluster, query budgets and load jobs kept the old name
`uportal-maven-local`. The Build uPortal job uploads the artifact in every run, also when it
reuses an earlier build, so the jobs need `needs.uportal.outputs.artifact` and no run ID.
zizmor (`--offline`) and actionlint (the image the workflow uses) pass without findings.

## D. SQL Server text

SQL Server stored "Λίστα" as "??sta", its windows-1252 best fit. The JDBC URL keeps
`sendStringParametersAsUnicode` at its default (true), and the copy tool isn't involved. The
`ConstraintViolationException` lines in the G-migrate log come from the tests that check a
duplicate name is refused. The log prints them only because the job failed.

The DB harness and G-migrate pass the vendor to Playwright as `UPORTAL_DB_VENDOR`.
`tests/general-config.ts` parses it into the database family and throws on an unknown name.
Without the variable every test keeps its strict expectation. On SQL Server the portlet-list
tests expect windows-1252 names back unchanged and every other name changed, so they fail once
the column is `nvarchar` (coordinator decision, option 1).

| Run | Result |
| --- | --- |
| HSQLDB, no variable and `hsqldb` | the tests pass |
| Seeded: `UPORTAL_DB_VENDOR=sqlserver-2025` on HSQLDB | both tests fail: `stored "Λίστα POST …" for "Λίστα POST …"`, expected false |
| `UPORTAL_DB_VENDOR=bogus-1` | Playwright refuses to load: "Unknown UPORTAL_DB_VENDOR 'bogus-1'" |
| `tests/db/harness.sh --portal sqlserver-2025` in Docker (the CI image) | zero drift, 92 API specs passed |
| The strict expectation on SQL Server (the tip, CI run 38204127794, job 114669441560) | both tests fail: "??sta POST …" for "Λίστα POST …" |

## E. G-migrate with PostgreSQL sources

Both pairs ran the copy and failed in the step "target: whole Playwright suite". The failures
there are A and B. The MariaDB 12.3 target also had three tests that passed only on a retry,
all as the student between 05:18 and 05:21 (job 114669441841):
`background-preference.spec.ts:124`, `reset-my-layout.spec.ts:211` and `:338`. The portal log
has "Exception encountered while reading a layout for userId=20" at 05:18:43, and MariaDB
error 1020 "Record has changed since last read in table 'up_layout_struct'" at 05:20:07, in a
`ConnectionCallback` (`RDBMUserLayoutStore`). That is the MOD-I99 class: MariaDB 11.6 and later
refuse a read-then-update write under REPEATABLE READ. The layout store needs the same review.

## Proposed LOG rows (MOD-I? numbers to be set by the coordinator)

| ID | Area | Finding | Status |
| --- | --- | --- | --- |
| MOD-I? | Tests | The RTL spec measured `#webSearchContainer`, which the search web components removed (uPortal-start b0b658e97). Both came in one batch (459446824). | **Fixed** on `-fix-tip`: the spec measures `<up-search-launcher>` |
| MOD-I? | CI | Query budgets, Cluster and Load downloaded `uportal-maven-local` after 949f2cc95 renamed the artifact. af7c50676 changed only the browsers job. | **Fixed** on `-fix-tip` |
| MOD-I? | Persistence | The 5.17 schema on SQL Server stores portlet-list names as `varchar`, so text outside windows-1252 becomes `?`. 967daa074 brought the check into CI. | **Tests adapted** on `-fix-tip`. Fixed by MOD-D44 in the 6.0 schema step |
| MOD-I? | Concurrency | On MariaDB 12.3, parallel layout writes for one user fail with error 1020 on `up_layout_struct`, and a later read of that layout fails (G-migrate postgres-15 to mariadb-12.3, job 114669441841). | **Open.** Same review as MOD-I99 for `RDBMUserLayoutStore` |
