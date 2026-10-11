# Portlet preference cache (MOD-I230): handoff note

Branch `ccr-43ba0785-hzlrj5-preference-cache`, from the integration branch.
LOG.md MOD-I230 has the report.

## The failure

One user saved portlet-entity preferences from two sessions at once.
Hibernate refused one of the saves with an optimistic locking failure.
After that, loading the user's portlet entity failed with
`EntityNotFoundException: Unable to find PortletPreferenceImpl with id N`,
where N was a preference the other save had deleted. The failure lasted
until the cache entry expired (15 minutes idle, 30 minutes at most) or
Tomcat restarted. It showed first when the UX and API Playwright specs
changed the student's background in parallel workers.

## Root cause

The cache region is
`org.apereo.portal.portlet.dao.jpa.PortletPreferencesImpl.portletPreferences`.
It stores each preference set's list of `PortletPreferenceImpl` ids, and
its strategy was `NONSTRICT_READ_WRITE`.

With that strategy a save takes no lock in the cache: after the commit it
only removes the entry. A session that loads the list puts what it read
into the cache when the load ends, and nothing checks whether a save
committed in between. So the order below leaves the old list in the
cache:

1. Session B reads the preference list from the database: `[156]`.
2. Session A saves new preferences. Preference 156 is deleted, the
   preference set's version goes up, and after the commit the cached list
   is removed.
3. Session B puts the list it read in step 1, `[156]`, into the cache.
4. Session B saves its own choice. The version check fails, Hibernate
   refuses the save, and nothing removes the list from step 3.

The refused save is not what leaves the stale entry; the load before it
is. The refusal only shows that the order happened. The next session that
loads the preference set from the database gets the right list, but
`hibernate.cache.use_minimal_puts` keeps it from replacing the entry that
is already there. A later session that reads the set from the cache also
reads the list `[156]` with it, and fails to find preference 156. Through
`EntityManager.find` the whole portlet entity then comes back as null;
through a query it is the `EntityNotFoundException`.

## The fix

The collection now uses `READ_WRITE`. A save puts a lock in the region,
and when the lock is released it records the time. A load that started
before then may not put its list in the region, so step 3 does nothing,
and the next load reads the database.

Evicting the entry when a save is refused would not cover it. A session
that only reads, such as a page render, can put the old list in the same
way, and then no save follows to remove it. Retrying the refused save
does not remove the entry either.

The preference set and preference entities, and the preference values
collection, keep `NONSTRICT_READ_WRITE`. The same order could leave an
old version or old values in their regions. No test shows a failure from
them yet, so they are not changed: `READ_WRITE` adds a lock entry and a
timestamp check to every save and load of those regions, and nothing yet
shows what that buys. To change them, a test would have to hold a load in
the same way and then show a later session reading an old set version
(every save refused) or an old preference value from the cache.

With JGroups replication (`ehcache.xml`) the region still replicates by
invalidation. The lock entries count as updates, so other servers remove
their copy.

## Test

`PortletPreferencesCacheTest` (uPortal-webapp) runs the JPA test context
with the portal's cache settings from `properties/db/hibernate.cfg.xml`
and the regions of `properties/ehcache-no-jgroups.xml`
(`jpaPortletPreferencesCacheTestContext.xml`). A Hibernate pre-load
listener holds the second session after it has read a preference and
before the list is cached. The order above then happens on every run.

* `refusedConcurrentSaveLeavesNoStaleCachedPreferences`: the first save
  clears the background while the second chooses another. The second save
  is refused. Each of the next three loads finds the background cleared,
  and a later save works.
* `saveAfterSaveKeepsTheLaterChoice`: saves that do not overlap.

Before the fix the first test failed on the second load: the entity came
back as null because preference 1 was gone.

| Seeded fault | Caught by |
| --- | --- |
| The collection back to `NONSTRICT_READ_WRITE` | `refusedConcurrentSaveLeavesNoStaleCachedPreferences`, second load |

Setting only the preference set entity to `READ_WRITE` also makes the
test pass, for an unrelated reason. The refused update takes a lock in
the set's region and never releases it, so the set is not cached until
the lock times out, and each load reads the list from the database.
Setting only the preference entity to `READ_WRITE` does not help.

The test cleans up through the DAOs before `deleteAllEntities`. That
method deletes each entity type on its own, which leaves a cached list
naming deleted preferences: the same failure, caused by the cleanup.

## Background preference API spec

The uPortal-start API spec for the background stays on the faculty user.
Even with this fix, the UX spec and the API spec would change the same
user's background in parallel and see each other's choices, so
using one user for both is not more realistic.

## Proposed LOG row

| MOD-I230 | Persistence | (as now) | **Fixed** on `ccr-43ba0785-hzlrj5-preference-cache`. With `NONSTRICT_READ_WRITE` on the preference list region, a session that read the list before another session's save could cache the old list after the save removed it. The region now uses `READ_WRITE`. `PortletPreferencesCacheTest` reproduces the order with the portal's cache settings. |
