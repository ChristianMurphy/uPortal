<!-- markdownlint-disable -->

# Stale local accounts from the nonstrict second-level cache (MOD-I310)

Branch `ccr-43ba0785-hzlrj5-cache-races` in uPortal, from the integration branch. It lands alone,
ahead of the feature branches, because it is a security fix. The uPortal-start branch of the same
name has no commit of its own. Its Integration run builds this uPortal branch.

`ccr-43ba0785-hzlrj5-cache-races-2` continues the same workstream for PAGS definitions (MOD-I311)
and stylesheet user preferences (MOD-I312); its `notes/cache-races.md` is the fuller version of
this note, and the two notes overlap on the sections below. When both branches are merged, keep the
`cache-races-2` version.

This follows MOD-I204, found by `import-export.spec.ts` on Firefox: a request that reads an entity
before a change commits can put the old state back into the second-level cache after the change
evicted it, because hibernate-ehcache 5.2's `NONSTRICT_READ_WRITE` `putFromLoad` compares no
timestamp or version. The audit in `notes/portlet-def-cache.md` listed the other entities on that
strategy, and named the local account as the first to fix.

## The race test

`org.apereo.portal.test.CacheRace` runs a reader and a writer in the order that leaves a stale
entry, if the strategy allows it. Hibernate reads an entity's row, calls the pre-load listeners,
then puts the loaded state into the cache. `CacheRace` pauses the reader at its pre-load of an
entity class, runs the writer to its commit in the calling thread, then lets the reader go on.
`jpaPortalCacheRaceTestContext.xml` is the JPA test context with the second-level and query caches
and minimal puts on, as `properties/db/hibernate.cfg.xml` sets them for the portal. The shared test
context turns them off.

## Proposed LOG row (MOD-I310)

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I310 | Persistence | **Security.** `LocalAccountPersonImpl`, its attributes and the attribute values used `NONSTRICT_READ_WRITE` (the MOD-I204 race). A login (by user name, then the password check) that read the account before a password change, an attribute change or a delete committed put the old account back into the cache. Until the entry expired, the old password logged in whenever the login's name query came from the query cache, the new password failed, the account screens and password self-service (which load by id) saw the old hash and attributes, and a deleted account still loaded by id. `LocalAccountCacheRaceTest` fails on the integration branch with `[login with old password=true, login with new password=false, ...]`, the old-password login after a removal, and the deleted account still loading by id. | **Fixed** (e71d3e7f4): `READ_WRITE` for `LocalAccountPersonImpl`, its `attributes` collection and `LocalAccountPersonAttributeImpl.values`. The attribute entity keeps `NONSTRICT_READ_WRITE` (the test passes with it seeded back). |

## How long a stale entry lasts (for UPGRADING and the security report)

`ehcache.xml` gives the `LocalAccountPersonImpl` region, its attributes, the attribute values and
the per-name query region `timeToLiveSeconds=1800` and `timeToIdleSeconds=900`.

* A stale entry from one lost race lasts at most 30 minutes from the put, and goes sooner if
  nothing reads it for 15 minutes. A login that reads it resets the idle clock but not the
  30-minute limit.
* The old password works on some attempts and not others during that window. A login looks the
  account up by name through its own cached query region (`LocalAccountPersonImpl.Query`, same
  1800/900 limits). When that query runs SQL (its entry expired, or `UP_PERSON_DIR` changed
  since), the login reads the current row and the new password works, and it caches the query
  result again. When the query is served from the cache, the login takes the stale entity and the
  old password works. A by-id lookup takes the stale entity every time.
* An account change commits a write to `UP_PERSON_DIR`, which invalidates the cached name query, so
  the next login runs SQL.
* It ends sooner on a restart (these regions are memory only), on a cache clear, on LRU eviction
  (`maxElementsInMemory=500`), or on another committed change to the account.
* In a cluster the stale entry exists only on the server that lost the race: entity puts aren't
  replicated (`replicatePuts=false`), so other servers read the current row.
* After the fix a load that read before the change can't put its old state back; the entry holds
  the committed state, or nothing.

## MOD-D54 (clearing replicated caches after a split)

`READ_WRITE` doesn't change what the MOD-D54 work must do. A clear removes items and soft locks; an
update whose lock was cleared leaves the key uncacheable until the lock timeout
(`net.sf.ehcache.hibernate.cache_lock_timeout`, 60 s by default), and reads go to the database
meanwhile. Replication still sends removals only, so a load on another server that read before a
commit can still cache the old state there, as under `NONSTRICT_READ_WRITE`. `READ_WRITE` fixes the
single-server race.

## Runs

On e71d3e7f4, uPortal CI 38156609053, DB copy tool 38156609027 and DB integration 38156613032 on
six databases ran, with uPortal-start Integration 38156619164.
