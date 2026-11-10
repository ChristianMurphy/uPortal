# Handoff: the login query budgets after Hibernate 5.6

Branch `ccr-43ba0785-hzlrj5-h56-query-budget` in uPortal, based on the integration branch at
`6a9697af39` (landing 10: `-mssql-id-lock` and `-hibernate56`). uPortal-start has a branch of the
same name at its tip `df45e44523`, without a change, so that its runs build this uPortal branch.

## Status

| Commit | Subject | State |
| --- | --- | --- |
| 1 | `fix(layout): cache the dlm evaluator subclasses again on hibernate 5.6` | done |

## The failure

uPortal-start Integration run 42743207405 on the tip after landing 10, job "Query budgets:
hsqldb" (129590476248): student-login ran 119 statements on the first login (budget 100) and 31
on a warm one (budget 15), and admin-login 78 and 31 (budgets 62 and 16).

## Cause

Reproduced on this machine with `tests/query-budget/run.sh` (JDK 11 for the portal, HSQLDB), and
with a local, uncommitted change to `query-budget.mjs` that writes each request's statements to a
file:

| Build | student-login, first and warm | admin-login, first and warm |
| --- | --- | --- |
| `a00134ea50` (Hibernate 5.2.18) | 97, 12 | 59, 12 |
| `6a9697af39` (the tip, Hibernate 5.6.15) | 120, 31 | 81, 34 |
| this branch | 98, 12 | 59, 12 |

The 19 extra statements of a warm student login on the tip are 15 selects on `UP_DLM_EVALUATOR`
by evaluator id and 4 by fragment definition. Each login reads the fragment definitions and
their evaluators, and on 5.2 the second-level cache answered. The admin login has the same 19
and 3 group membership lookups that follow from them.

`Evaluator` has eight entity subclasses (`EvaluatorGroup`, `FragmentDefinition`, `Paren`,
`AllUsersEvaluatorFactory`, `AttributeEvaluator`, `GroupMembershipEvaluator`,
`GuestUserEvaluatorFactory`, `ProfileEvaluator`), and each carried `@Cacheable` and `@Cache`. Up to
Hibernate 5.2 the root's setting applied to the hierarchy, and `@Cache` on a subclass only logged
HHH000482 (MOD-I125, MOD-I424). Hibernate 5.3 gave each class its own setting. In
`EntityBinder.applyCaching` of 5.6.15, a subclass with `@Cache` gets the warning and stays
uncached, and a subclass with neither annotation inherits the superclass's setting.

The other suspects weren't the cause. The lock timeout, the region names, the natural-id and
query caches and the proxy of `SearchRequestAggregationImpl` don't add a statement to a login.
With the fix, a warm login runs the same 12 statements as on 5.2.

## Fix

These subclasses lose `@Cacheable` and `@Cache`. They inherit `Evaluator`'s
`NONSTRICT_READ_WRITE` setting and use its region, as on 5.2. The mapping and the DDL goldens
don't change. HHH000482 is no longer logged at `dataInit` (0 lines, 8 before).

`CachedEntityHierarchyTest` (uPortal-webapp) boots `PortalDb` with the second-level cache and
fails for an entity that isn't cached below a cached root. Before the fix it failed with the
eight class names. UPGRADING-6.0 has a bullet for sites with entity hierarchies of their own.

On the first student login this branch runs one statement more than 5.2 did,
`call next value for UP_AGGR_TAB_MAPPING_SEQ`, which depends on what the aggregation has stored
by then and is within the budget.

## The two "Schema Create Failed" lines

They are not new. Both come from portlets' own schema tools during `dataInit`: CalendarPortlet's
`SchemaCreator` and NewsReaderPortlet's, each with its bundled Hibernate 3. Their drop step
removes a foreign key that a new database doesn't have yet ("object not found:
FKF8F0C5957886B9ED" and "FK6B30F46A9BF7DB25"), and the create step then succeeds. The local
`portalInit` log of `a00134ea50` has the same two lines as the one of the tip. uPortal's own drop
script and delimiter aren't involved.

## The topic's own Integration runs

The "Query budgets" job doesn't run on a topic push: its condition in `integration.yml` is
`needs.uportal.outputs.set != 'workstream'`. Runs 42706549755 and 42723874429 of `-hibernate56`
were topic pushes, so neither ran it. It runs on the integration branch, in manual runs and
nightly. A persistence or cache topic needs it before it is merged.

## Evidence

Local, JDK 21 for Gradle and the tests, HSQLDB:

* `CachedEntityHierarchyTest` failed before the fix with the eight class names and passes after.
* `spotlessCheck`, the tests of uPortal-webapp, uPortal-layout-core and uPortal-layout-impl, and
  SpotBugs on the two layout modules passed.
* `tests/query-budget/run.sh` with the portal on JDK 11: the table in "Cause". With the fix, all
  14 requests are within their budgets.

The CI runs of this branch, with the "Query budgets" job of a manual uPortal-start Integration
run, are in the report to the coordinator.

## Proposed LOG rows

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I437 | Caching | On Hibernate 5.6 the eight subclasses of `Evaluator` weren't in the second-level cache: since 5.3, `@Cache` on a subclass (HHH000482) leaves that class uncached, where 5.2 applied the root's setting to the hierarchy. Each login read the fragment definitions and evaluators from the database, 19 statements more on a warm login. Found by the "Query budgets" job on the tip after landing 10 (run 42743207405) | **Fixed** in `-h56-query-budget`: the subclasses inherit the root's setting. `CachedEntityHierarchyTest` |
| MOD-I125 (update) | Runtime | HHH000482 is gone: its eight lines were these subclasses | Done |
| MOD-I424 (update) | Caching | The 8 `@Cache` annotations on `Evaluator` subclasses are removed here. On 5.6 they weren't harmless | Done for those 8. The rest stays with step S2 |
| MOD-I438 | CI | uPortal-start's "Query budgets" job doesn't run on a topic push, so `-hibernate56` was merged without it | Open, for the coordinator: add it to the topic profile for persistence and cache changes |
