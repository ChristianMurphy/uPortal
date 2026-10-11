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
| 2. REST API under `/api/v6-0/` | Waiting for the coordinator on the questions below |
| 3. Web component | Not started |
| 4. Channel definition | Not started |
| 5. Spec against the component, seeded faults | Not started |
| 6. UPGRADING-6.0 | Not started |

## Questions sent to the coordinator

1. Where the user's choice is stored. Keeping it in the portlet entity's preference
   (`defaultSelectedBackgroundImage`) keeps every existing choice through the upgrade, but the API
   then writes portlet-entity preferences of the user's channel. The alternative is a new store,
   which loses existing choices unless the data migration copies them.
2. Whether to keep the `mobile*` image sets and the "Mobile Device Access" role. The definition
   sets `hideFromMobile`, so those users don't get the channel in the quickstart.
3. The component writes one `<style>` element into the page (outside its shadow root), because
   the background is on `#portalPageBody::before`. Fine under the current CSP?

## Running locally

As in `wc-reset-my-layout.md`, with Google's Maven mirror through a Gradle init script, jasypt in
`~/.m2`, `JDK_JAVA_OPTIONS` with the two `--add-opens`, and a Chromium 1248 symlink tree for
Playwright 1.64.

## Next step

Answers to the questions, then the REST API.
