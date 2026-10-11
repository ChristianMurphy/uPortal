# Handoff: the Options menu said closed while it was open (MOD-I320)

Branch `ccr-43ba0785-hzlrj5-options-dropdown` in uPortal and uPortal-start, cut from
`ccr-43ba0785-hzlrj5`.

## Cause

About 100 ms after the load event, `scripts/portal-analytics.js` sets up every `.dropdown-toggle`
again ("Bootstrap 5 dropdown compatibility fix"). It set `aria-expanded="false"` on each toggle
and disposed and recreated its Bootstrap instance, whatever the menu's state. Disposing an
instance doesn't close its menu, so a menu opened before then was still shown, with the `show` class
on the toggle and the menu, while screen readers were told it was closed. Tests that open a
portlet's Options menu with `openDropdown` saw `aria-expanded="false"` when the page's setup ran
just after they opened it:

* `portlet-options.spec.ts:168`, "maximized portlet Options has Return to dashboard", on
  postgres-18 (uPortal-start Integration 38141888880);
* `favorites.spec.ts:120`, "remove a portlet from favorites via Options menu" (job 114477114350,
  and on HSQLDB in run 38139810571).

## Fix

* uPortal `fix(respondr): keep an open menu announced as open after page load`:
  `aria-expanded` follows the toggle's `show` class, and `getOrCreateInstance` reuses an existing
  instance, which holds the menu's state.
* uPortal-start `test(e2e): check an options menu opened during page load stays open`: a test
  that opens the first Options menu on the load event, before the page's setup, then expects the
  menu visible with `aria-expanded="true"`, and closed with Escape.

## Local results

On HSQLDB, 30 runs of each test:

| Test | Before the fix | After the fix |
| --- | --- | --- |
| The new test (menu opened on the load event) | 30 failed | 30 passed |
| `portlet-options.spec.ts:168` | 30 passed | 30 passed |
| `favorites.spec.ts:120`, one worker | 30 passed | 30 passed |

`portlet-options.spec.ts:168` and `favorites.spec.ts:120` never failed here: on this machine the page's setup runs before
they open the menu. The new test opens it first every time, which is the case CI met.

## Proposed LOG rows

* MOD-I330 (Open). Removing a favorite answers HTTP 500 when the same user changes favorites in
  two sessions at once. Seen while running `portlet-options.spec.ts` 30 times with 4 workers, all
  as student: "Favorite and unfavorite from the menu send the CSRF token" failed 4 of 30 times
  before the MOD-I320 fix and 3 of 30 after, each time on
  `POST /api/layout?action=removeFavorite&channelId=49`. The stack is neither an optimistic-lock
  failure nor a unique-key conflict: `UpdatePreferencesServlet.removeFavorite` calls
  `DistributedLayoutManager.deleteNode`, whose `getNode` throws `PortalException: Element with
  ID="n8" doesn't exist for student`. Each session loads the user's layout into its own
  `DistributedLayoutManager`, and another session had already removed that node. The same runs also logged unique-key conflicts
  on other requests: the `UP_LAYOUT_PARAM` primary key in `RDBMUserLayoutStore.setUserLayout`
  (`RDBMDistributedLayoutStore.saveStructure`) while a layout loaded, and `UP_AGGR_TAB_MAPPING`'s
  unique key in `JpaAggregatedTabLookupDao.getTabMapping` while a page rendered. To reproduce:
  `npx playwright test tests/ux/smoke/portlet-options.spec.ts --repeat-each=30 --retries=0
  --workers=4` against a local portal.
