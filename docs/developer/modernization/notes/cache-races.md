<!-- markdownlint-disable -->

# Stale entities from nonstrict second-level caches (MOD-I310 to MOD-I312)

Branches `ccr-43ba0785-hzlrj5-cache-races` (MOD-I310, lands alone, it is a security fix) and
`ccr-43ba0785-hzlrj5-cache-races-2` (MOD-I311, MOD-I312) in uPortal, both from the integration
branch. The uPortal-start branches of the same name have no commit of their own. Their
Integration runs build the uPortal branch of the same name.

This follows MOD-I204, found by `import-export.spec.ts` on Firefox: a request that reads an entity
before a change commits can put the old state back into the second-level cache after the change
evicted it, because hibernate-ehcache 5.2's `NONSTRICT_READ_WRITE` `putFromLoad` compares no
timestamp or version. The audit in `notes/portlet-def-cache.md` listed the other entities on that
strategy. This workstream takes the ones a change can race in place and switches the regions a
test shows stale to `READ_WRITE`, whose soft locks reject a load older than the change.

## The race test

`org.apereo.portal.test.CacheRace` runs a reader and a writer in the order that leaves a stale
entry, if the strategy allows it. Hibernate reads an entity's row, calls the pre-load listeners,
then puts the loaded state into the cache. `CacheRace` pauses the reader at its pre-load of an
entity class (optionally one id, for a collection element), runs the writer to its commit in the
calling thread, then lets the reader go on. `jpaPortalCacheRaceTestContext.xml` is the JPA test
context with the second-level and query caches and minimal puts on, as
`properties/db/hibernate.cfg.xml` sets them for the portal. The shared test context turns them off.

Each test caches the whole entity and evicts only the part the change touches. The reader then
reads that part's rows and pauses there. Each region a test shows stale moves to `READ_WRITE`. A region
put back to `NONSTRICT_READ_WRITE` on its own then fails the test, which the commit bodies record.

## Proposed LOG rows (MOD-I310 to MOD-I312)

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I310 | Persistence | **Security.** `LocalAccountPersonImpl`, its attributes and the attribute values used `NONSTRICT_READ_WRITE` (the MOD-I204 race). A login (by user name, then the password check) that read the account before a password change, an attribute change or a delete committed put the old account back into the cache. Until the entry expired, the old password logged in whenever the login's name query came from the query cache, the new password failed, the account screens and password self-service (which load by id) saw the old hash and attributes, and a deleted account still loaded by id. `LocalAccountCacheRaceTest` fails on the integration branch with `[login with old password=true, login with new password=false, ...]`, the old-password login after a removal, and the deleted account still loading by id. | **Fixed** (`cache-races` e71d3e7f4): `READ_WRITE` for `LocalAccountPersonImpl`, its `attributes` collection and `LocalAccountPersonAttributeImpl.values`. The attribute entity keeps `NONSTRICT_READ_WRITE` (the test passes with it seeded back). |
| MOD-I311 | Persistence | A PAGS group definition's `members` and its test group's `tests` used `NONSTRICT_READ_WRITE`. PAGS group membership, and so the permissions a group grants, is evaluated from these on each login. A load of a definition racing an import or a PAGS admin change could keep the old membership or tests cached. `PagsDefinitionCacheRaceTest` fails on the integration branch for a changed test value, an added test and an added member group. | **Fixed** (`cache-races-2` 7e4d2e5f0): `READ_WRITE` for `PersonAttributesGroupDefinitionImpl.members` and `PersonAttributesGroupTestGroupDefinitionImpl.tests`. The `testGroups` collection and the three entities keep `NONSTRICT_READ_WRITE`. |
| MOD-I312 | Persistence | A user's `StylesheetUserPreferencesImpl`, its `parameters` and its per-node `layoutAttributes` used `NONSTRICT_READ_WRITE`. One request of a user loading the preferences while another saved a layout change could undo the change until the entry expired and fail the next save with an optimistic-lock error. `StylesheetUserPreferencesCacheRaceTest` fails on the integration branch with every value stale. | **Fixed** (`cache-races-2` 1e632deb1): `READ_WRITE` for `StylesheetUserPreferencesImpl`, its `parameters` and `layoutAttributes` collections, and `LayoutNodeAttributesImpl.attributes`. The `LayoutNodeAttributesImpl` entity and the `outputProperties` collection keep `NONSTRICT_READ_WRITE` (the test doesn't change output properties, and the entity seeded back passes). |

## The DLM evaluators don't go stale

The audit listed the DLM audience evaluators (`Evaluator`, `EvaluatorGroup.evaluators`) as a
candidate: a fragment import clears and rebuilds a fragment's audience, which decides who sees the
fragment. `FragmentEvaluatorCacheRaceTest`, a load of a fragment's audience racing an audience
re-import, passed on the integration branch three times running. A re-import replaces the
evaluators by deleting the old rows and inserting new ones with new ids, not by updating rows in
place. A load that read the old evaluators caches entities whose ids are then deleted, and the next
load reads the fragment's new evaluator ids, so nothing stale is served. These regions keep
`NONSTRICT_READ_WRITE`, and the test is not committed.

## How long a stale entry lasts (MOD-I310, for UPGRADING and the security report)

`ehcache.xml` gives the `LocalAccountPersonImpl` region, its attributes, the attribute values and
the per-name query region `timeToLiveSeconds=1800` and `timeToIdleSeconds=900`.

* A stale entry from one lost race lasts at most 30 minutes from the put, and goes sooner if
  nothing reads it for 15 minutes. A login that reads it resets the idle clock but not the
  30-minute limit.
* The old password works on some attempts and not others during that window. A login looks the
  account up by name through its own cached query region
  (`LocalAccountPersonImpl.Query`, same 1800/900 limits). When that query runs SQL (its entry
  expired, or `UP_PERSON_DIR` changed since), the login reads the current row and the new password
  works, and it caches the query result again. When the query is served from the cache, the login
  takes the stale entity and the old password works. A by-id lookup takes the stale entity every
  time.
* An account change commits a write to `UP_PERSON_DIR`, which invalidates the cached name query, so
  the next login runs SQL.
* It ends sooner on a restart (these regions are memory only), on a cache clear, on LRU eviction
  (`maxElementsInMemory=500`), or on another committed change to the account.
* In a cluster the stale entry exists only on the server that lost the race: entity puts aren't
  replicated (`replicatePuts=false`), so other servers read the current row.
* After the fix a load that read before the change can't put its old state back; the entry holds
  the committed state, or nothing.

## MOD-D54 (clearing replicated caches after a split)

`READ_WRITE` doesn't change what the MOD-D54 work must do. A clear removes items and soft locks; an update
whose lock was cleared leaves the key uncacheable until the lock timeout
(`net.sf.ehcache.hibernate.cache_lock_timeout`, 60 s by default), and reads go to the database
meanwhile. Replication still sends removals only, so a load on another server that read before a
commit can still cache the old state there, as under `NONSTRICT_READ_WRITE`. `READ_WRITE` fixes the
single-server race.

## Runs

On `cache-races` e71d3e7f4 (MOD-I310), uPortal CI 38156609053, DB copy tool 38156609027 and
DB integration 38156613032 on six databases ran, with uPortal-start Integration 38156619164.

On `cache-races-2` 1e632deb1 (MOD-I311, MOD-I312), uPortal CI 38160040204, DB copy tool
38160040169 and DB integration 38160044193 on six databases ran, with uPortal-start Integration
38160049629.
