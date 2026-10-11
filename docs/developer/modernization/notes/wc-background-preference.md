# Handoff: WS14 Background Preference as a web component

Converts the Background Preference portlet (fname `background-preference`) to a REST API and a
Lit web component, following "Converting the next portlet" in
`docs/developer/web-components/README.md`. Topic branch
`ccr-43ba0785-hzlrj5-wc-background-preference` in uPortal and uPortal-start, from
`ccr-43ba0785-hzlrj5`. The old portlet stays until the 6.0 data migration converts the
definitions (MOD-I133), as for Favorites and Session Timeout.

## The portlet today

* Framework portlet `backgroundPreference` in `WEB-INF/portlet.xml` (Spring Portlet MVC,
  `BackgroundPreference-portlet.xml`), with `ViewBackgroundPreferenceController` and
  `RoleBasedBackgroundSetSelectionStrategy` in uPortal-portlets and
  `jsp/BackgroundPreference/viewBackgroundPreference.jsp`.
* Preferences: `defaultBackgroundImages`, `defaultBackgroundThumbnailImages`,
  `backgroundImageCaptions`, `defaultBackgroundContainerSelector` (portlet.xml default
  `body.portal #portalPageBody::before`) and the `mobile*` versions, which apply to users in the
  role "Mobile Device Access". Image paths may contain `${portalContext}`, evaluated with SpEL.
  `applyOpacityTo` and `opacityCssValue` go into the model, and the JSP doesn't use them.
* The user's choice is the portlet preference `defaultSelectedBackgroundImage` (or
  `mobileSelectedBackgroundImage`) on the user's portlet entity. Saving checks that the image is
  one of the set. An empty value resets the preference.
* The JSP writes a `<style>` with the chosen image on the container selector, and a widget,
  "Change Background Image", that opens a list of None and the images with thumbnails. Choosing
  one appends a `<style>` to the page and posts the full-size path (the thumbnail path without
  `/thumbnails`) to the portlet action, which redirects to `/api/ajax-success`.
* uPortal-start places it, unremovable, in the Customize panel of `authenticated-lo`
  (Authenticated Users), with the three images Galaxy, Mountain and Nebula. Without a choice the
  skin's own background (`furley_bg.png`) shows.

## Status

| Step | State |
| --- | --- |
| 1. Characterisation spec | Done: uPortal-start `4488d55`, `tests/ux/smoke/background-preference.spec.ts`, 6 tests pass against the portlet, twice in a row |
| 2. REST API under `/api/v6-0/` | Done: uPortal `7efc53a` (`BackgroundPreferenceRESTController`, `BackgroundPreferenceService`, 20 tests), uPortal-start `7d57aff` (API spec) |
| 3. `<up-background-preference>` | Done: uPortal `23878d7`. 110 component tests (42 unit, 39 browser, 29 model-based over 16 shortest and 11 simple paths); every new file has 100% lines |
| 4. Channel definition | Done: uPortal-start `37ce836` (Web Component definition, `UP_PORTLET_DEF_PARAM` +4 to +6, equivalence reference), uPortal `61e9afc` (skin position) |
| 5. Spec against the component, seeded faults | Done: uPortal-start `5a1daad`. The 6 characterisation tests pass unchanged against the component; 4 component tests added; 16 of 16 pass twice in a row |
| 6. UPGRADING-6.0 | Done: "Background Preference is a web component" |

## Coordinator's answers (2026-10-11)

1. The choice stays in the portlet-entity preference `defaultSelectedBackgroundImage`. The API
   reads and writes only the signed-in user's own entity for the channel.
2. The `mobile*` sets and the "Mobile Device Access" role stay, and so does `hideFromMobile`.
3. The page background goes through a constructable stylesheet adopted by the document, replaced
   and never appended, with the URL taken only from the server's list and escaped for `url()`.

## Seeded faults

Each fault was seeded alone and reverted. The e2e faults were edits of the deployed bundle or of
the service classes in the deployed `uPortal-api-rest` jar, with Tomcat restarted for each.

| Fault | Caught by |
| --- | --- |
| Service accepts any URL | 1 service test; e2e API "refuses an image that is not offered" |
| A new stylesheet per change | 6 page-background, 3 element and 18 model tests; e2e "one adopted stylesheet" and "None removes the background" |
| Machine guard takes any URL | 1 machine test |
| `url()` value not escaped | 2 page-background tests |
| No focus back to the button | 3 element and 12 model tests |
| None sent as undefined | 1 API client test, 1 element test |
| Deployed bundle puts the thumbnail behind the page | 5 e2e tests |
| Deployed bundle never saves None | 9 e2e tests |

## Design

* `GET /api/v6-0/background-preference/{fname}` answers `containerSelector`, `images` (`url`,
  `thumbnailUrl`, `caption`) and `selected`. `PUT` with `{"selected": url or null}` saves; a URL
  that is not offered gets 400. The fname must be a Background Preference channel (tag
  `up-background-preference`, or the `backgroundPreference` framework portlet) that the user may
  use, else 404. Guests get 401. The entity is `getOrCreateDefaultPortletEntity`, the one the
  portlet used, and the preferences come from `createAPIPortletPreferences`.
* REST requests are not wrapped by `PortalHttpServletRequestWrapper`, so `isUserInRole` doesn't
  answer with groups there. `MobileDeviceRole` asks the group service the same way.
* The container selector defaults to the old `portlet.xml` values, because the WebComponent
  descriptor has no preferences of its own. A choice that is no longer offered reads as none.
* The component is pessimistic: the page background changes once the portal saved the choice. It
  loads on every page and then applies the background, a moment after the page renders.
* `regions.scss` places the element at the top right of the Customize panel, like the portlet's
  control.

## Findings

* When one user saves portlet-entity preferences from two sessions concurrently, Hibernate
  refuses one save (optimistic locking, 500), and can leave a stale cached
  `PortletPreferenceImpl` that makes the user's next sign-in fail with
  `EntityNotFoundException` until Tomcat restarts. The store code is the existing
  `PortletEntityPreferencesAPIImpl`; the portlet's own `PortletEntityPreferencesImpl` stores the
  same way. Found when two Playwright workers changed the student's background at once. Logged
  as MOD-I230, root cause and fix on `ccr-43ba0785-hzlrj5-preference-cache`
  (`preference-cache.md`): a stale preference list in a `NONSTRICT_READ_WRITE` cache region.
* `PortletEntityRegistryImpl.getPortletEntityMap` reads the request attribute
  `PORTLET_ENTITY_ATTRIBUTE + threadId` but stores it under `PORTLET_ENTITY_ATTRIBUTE`, so the
  request-level entity cache never hits.
* The old portlet's output is cached for five minutes (`expiration-cache` 300), so a choice made
  elsewhere showed only at the next sign-in. The component has no such cache.

## Proposed LOG rows

* MOD-D18 status line, Background Preference converted. `/api/v6-0/background-preference/{fname}`
  and `<up-background-preference>` in uPortal, the Web Component definition in uPortal-start. The
  characterisation spec passes on the portlet and on the component. The portlet stays until
  MOD-I133.
* MOD-I230 (logged): the stale preference cache of the first finding.
* Issue: the request-level portlet entity cache never hits (second finding).

## Running locally

As in `wc-reset-my-layout.md`, with Google's Maven mirror through a Gradle init script, jasypt in
`~/.m2`, `JDK_JAVA_OPTIONS` with the two `--add-opens`, and a Chromium 1248 symlink tree for
Playwright 1.64.

## Owner rulings of 2026-10-11

* MOD-D49: the six new keys have Spanish and French text, with `\u` escapes and the French
  apostrophes doubled. The captions Galaxy, Mountain and Nebula come from the definition's
  `backgroundImageCaptions` preference, not from the message bundles, so they stay as the data
  has them.
* MOD-D50: uPortal-start's spec renders `/p/background-preference` for a user whose language is
  es_ES and one whose language is fr_FR, through the User Locales Selector channel, signed in as
  the quickstart developer user (as in `wc-reset-my-layout.md`). The component has no text
  inputs, so the Unicode round trip doesn't apply.
* MOD-D51: the component ignores Escape while `event.isComposing` is true. The list uses `min-inline-size`,
  `max-block-size` and `overflow-block`, the thumbnails `inline-size`, and the theme rule that
  places the channel `inset-inline-end` and `inset-block-start`. Long captions wrap
  (`overflow-wrap: anywhere`). Browser tests cover the composing key and the long caption.

| Seeded fault | Caught by |
| --- | --- |
| The `isComposing` check removed | browser "leaves Escape to an input method that is composing" |
| The wrap removed | browser "wraps a caption too long for its width" (2061px in a 200px element) |
| The test doesn't set the language | e2e "speaks es": the element's locale stays `en-US` |

## Landing

The coordinator landed uPortal `7769bf8` and uPortal-start `283b22d` as uPortal `5a7276803` and
uPortal-start `ad9521267`. Evidence: uPortal CI 38130739840 (full matrix, green) and uPortal-start
Integration 38130741635 (18 jobs, green). The MOD-D49 to MOD-D51 work above (`ee07cbf`, `0ea55db`
and the uPortal-start es/fr spec) follows in a later batch with its own push run.

## Next step

Run CI.yml by hand on the uPortal topic branch and integration.yml on the uPortal-start topic
branch, then send the coordinator the run IDs and head SHAs.
