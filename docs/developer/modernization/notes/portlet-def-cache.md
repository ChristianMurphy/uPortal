# Stale portlet definitions in the second-level cache (MOD-I204)

Branch `ccr-43ba0785-hzlrj5-portlet-def-cache` in uPortal, from `ccr-43ba0785-hzlrj5` at
`0ee66e54e`. uPortal-start's branch of the same name has no commit of its own: its Integration
run builds this uPortal branch.

## LOG row

| MOD-I204 | Persistence | **Found by `import-export.spec.ts` on Firefox** (uPortal-start cluster run 38136427089, job 114475288049). A re-imported portlet definition kept its old title. The retry passed with a new fname. `PortletDefinitionImpl`, its collections and its preference entities used the `NONSTRICT_READ_WRITE` second-level cache. An update evicts the entry after its commit, but hibernate-ehcache 5.2's nonstrict `putFromLoad` doesn't compare timestamps or versions. A request that read the rows before the commit can put the old state back after that eviction. The cache then serves the old definition until the 6-hour TTL. The portal shows the old title and content, and the next save of the definition fails with `ObjectOptimisticLockingFailureException` (HTTP 500), because the cached `ENTITY_VERSION` is behind the row. In the spec, the concurrent read is the `/api/portletList` request that the search launcher sends on each page. `importFile` waits for `*.uP` requests only, so that request can still be running when the upload commits. Reproduced locally by re-importing one definition 200 times while 4 `/api/portletList` requests ran from another admin session. After round 133 the export and the title bar showed the previous title, still 3 s later. The next import answered 500 (`StaleStateException` on `update UP_PORTLET_DEF ... ENTITY_VERSION=?`). | **Fixed** (d7292d928): `READ_WRITE` for the regions the re-import writes and `PortletDefinitionCacheRaceTest` shows stale. Its soft locks and versions reject a load older than the update. b203131c7 adds the test, which pauses a load between its read and its cache put while a re-import commits. The 36 classes with regions still on `NONSTRICT_READ_WRITE` are audited in `notes/portlet-def-cache.md`, for a decision. |

## The fix

`PortletDefinitionCacheRaceTest` (uPortal-webapp, `jpaPortalSecondLevelCacheTestContext.xml`: the
JPA test context with the second-level cache on) re-imports a definition the way
`PortletDefinitionImporterExporter` does. It changes the title, the French title, a parameter and
the content preference, and adds a preference. A pre-load listener pauses a reader thread after
its read and before its cache put while that re-import commits. The next request must see every
new value through the cache, and save the title and content again.

On the integration branch every value came back stale:
`[title=Old title, fr title=Ancien titre, parameter=old, preferences=[content[old content]]]`.
These regions moved to `READ_WRITE`. Each one, put back to `NONSTRICT_READ_WRITE` alone, fails the
test:

| Region | Holds | Seeded back to nonstrict |
| --- | --- | --- |
| `PortletDefinitionImpl` | title, name, description, version | `title=Old title` |
| `PortletDefinitionImpl.localizations` | localized titles, names, descriptions | `fr title=Ancien titre` |
| `PortletDefinitionParameterImpl` | parameter values | `parameter=old` |
| `PortletPreferencesImpl` | the preference list's version | the next save fails (`OptimisticLockException`) |
| `PortletPreferencesImpl.portletPreferences` | which preferences a definition has | `added` missing |
| `PortletPreferenceImpl.values` | preference values (the content) | `content[old content]` |

`PortletPreferenceImpl` (the entity) stays `NONSTRICT_READ_WRITE`: with it seeded back the test
passes, so the test doesn't show it stale. Why it doesn't go stale here isn't established. It is
in the audit below.

## MOD-D54 (clearing replicated caches after a split)

The MOD-D54 work doesn't need a change for `READ_WRITE`.

- A clear removes `READ_WRITE` items and soft locks alike. An update whose lock was cleared finds
  no lock when it commits; hibernate-ehcache then writes an expired lock, so the key isn't cached
  again until the lock timeout (`net.sf.ehcache.hibernate.cache_lock_timeout`, 60 s by default).
  Reads of that key go to the database meanwhile. Nothing stale is put back.
- Replication stays as configured in `ehcache.xml` for these regions: `replicatePuts=false`,
  `replicateUpdates=true`, `replicateUpdatesViaCopy=false`, `replicateRemovals=true`. The soft
  locks and new items that `READ_WRITE` writes over an existing entry replicate as removals on
  the other servers. A load on server B that read before server A's commit and puts after B's removal can
  still cache the old state on B, as under `NONSTRICT_READ_WRITE`. `READ_WRITE` fixes the race
  on one server only.
- A rolled-back update leaves its lock until the timeout: that key is read from the database for
  up to 60 s.

## Audit: the other `NONSTRICT_READ_WRITE` regions

The 36 classes below use the same nonstrict `putFromLoad`. A region goes stale when a write
commits while another request loads its rows. "Yes" means a write exists that can do that, from
an import or admin screen or from a user's own save. "Why" names the write and what a stale entry does.
This branch leaves every region below as it was.

| Class (regions) | Race? | Why |
| --- | --- | --- |
| `PortletDefinitionImpl.parameters` (collection) | Yes | A re-import that adds or removes a parameter changes the map's membership. The test changes a value only, which is the parameter entity (now `READ_WRITE`). |
| `PortletDefinitionImpl.lifecycleEntries` (collection) | Yes | Every import rewrites the lifecycle. It loads by a separate `SELECT`, so the test's pause doesn't cover it. A stale list shows the old publish and expiry dates. |
| `PortletPreferenceImpl` (entity) | Yes, not shown | A re-import updates the content preference in place and bumps its version. The test's next save passed with this region nonstrict. |
| `PortletEntityImpl` (entity, `windowStates`) | Yes | A user's preference or window-state save races that user's other requests (parallel portlet renders). A stale entity fails the next save with an optimistic-lock error. |
| `PortletLocalizationData` | No | An embeddable, which Hibernate doesn't cache on its own. Its collection, `PortletDefinitionImpl.localizations`, is `READ_WRITE` now. |
| `PortletTypeImpl` | Yes, rare | Portlet types change only by import. Definitions read them on every load. |
| `AggregatedGroupConfigImpl`, `AggregatedIntervalConfigImpl` (entities, included and excluded sets) | Yes | `event-aggregation` import. The aggregation jobs read them. Stale config aggregates with the old groups or intervals until the TTL. |
| `PersonAttributesGroupDefinitionImpl` (entity, `members`, `testGroups`), `PersonAttributesGroupTestGroupDefinitionImpl` (entity, `tests`), `PersonAttributesGroupTestDefinitionImpl` | Yes | `pags-group` import and PAGS administration. Group membership is evaluated on each login. A stale definition gives users the old PAGS groups, and with them the old permissions. |
| `MessageImpl` | Yes, low impact | A message update. Pages read messages while rendering and would show the old text. |
| `StylesheetDescriptorImpl` (entity, three maps), `StylesheetParameterDescriptorImpl`, `OutputPropertyDescriptorImpl`, `LayoutAttributeDescriptorImpl` (entity, `targetElementNames`) | Yes, rare | `stylesheet-descriptor` import. Read on every render. |
| `StylesheetUserPreferencesImpl` (entity, three maps), `LayoutNodeAttributesImpl` (entity, `attributes`) | Yes | A user's layout changes (tab names, column widths, focus) save these while the same user's other requests load them. A stale entry undoes the change and fails the next save. |
| `Evaluator`, `EvaluatorGroup` (`evaluators`), `FragmentDefinition`, `Paren`, `AttributeEvaluator`, `GroupMembershipEvaluator`, `AllUsersEvaluatorFactory`, `GuestUserEvaluatorFactory`, `ProfileEvaluator` | Yes | `fragment-definition` import clears and rebuilds a fragment's evaluators. Fragment audiences are evaluated at login. A stale `evaluators` list gives users the old fragments. |
| `ProfileSelection` | Yes, low impact | A user's profile selection save. |
| `LocalAccountPersonImpl` (entity, `attributes`), `LocalAccountPersonAttributeImpl` (entity, `values`) | Yes | The password hash and `lastPasswordChange` live here. A password change, a user import or an admin edit that races a login can leave the old hash cached: the old password keeps working and the new one fails until the TTL. |
| `PermissionOwnerImpl` (entity, `activities`), `PermissionActivityImpl` | Yes, rare | `permission-owner` import. Read by permission checks. |
| `JpaTenant` (entity, `attributes`) | Yes, rare | Tenant admin edits. |
| `PortalCookieImpl` (entity, `portletCookies`), `PortletCookieImpl` | Yes | Portlet cookies are written on the requests of one user, often in parallel. A stale entry loses a cookie value and fails the next update. |
| `PortletList`, `PortletListItem` (entity, `items`) | Yes, low impact | A user's portlet list edits through `/api/portlet-list`. |

## Runs

On `d7292d928`, started 2026-10-11:

- uPortal: CI 38154732351 and DB copy tool 38154732264 (push), DB integration 38154737652
  (dispatched, all six databases). The dispatch replaced the push run 38154732262.
- uPortal-start `ccr-43ba0785-hzlrj5-portlet-def-cache` (`ad9521267`, which builds this uPortal
  branch): the real-portal run is Integration 38154742655. CI 38154742649 and Migration
  equivalence 38154742662 run with it.
