# Handoff: rtl (Respondr right to left)

Part 3 of the hardening work (MOD-I234, MOD-D51). The project owner chose to make Respondr right
to left after the accessibility fixes (MOD-D47), which changed the same skin files. Branches:
`ccr-43ba0785-hzlrj5-rtl` in uPortal (from `a6f81aa6`) and in uPortal-start (from `6977963`).
LOG numbers come from the hardening block (MOD-I250 to MOD-I259). This branch uses MOD-I253.

## Status

| Step | State |
| --- | --- |
| `dir` from the user's locale (`USER_DIR`) | committed (uPortal `eecc3d94`) |
| Mirrored skin (`defaultSkin-rtl.css`), icons, UPGRADING-6.0 | committed (uPortal `2934bff8`) |
| Playwright spec in the ar-XB pseudo-locale | committed in uPortal-start (`tests/ux/i18n/rtl-theme.spec.ts`) |
| axe in right to left | committed in uPortal-start (`eedf209`), after merging the integration branch |
| The ar-XB test locale on CI's portals (`-PtestPortalLocales`) | committed in uPortal-start (`d493253`, `6402b5a`) |

## How it works

* `LocaleDirection` (uPortal-core) says whether a locale is written right to left: by its script
  subtag when it has one, otherwise by its language. `LocaleTransformerConfigurationSource` gives
  the theme `USER_DIR` (`rtl` or `ltr`) next to `USER_LANG`, and `respondr.xsl` writes it as
  `<html dir>`.
* The build runs RTLCSS on the compiled `defaultSkin.css` and writes `defaultSkin-rtl.css`
  (`npm run compile-rtl-css` in uPortal-webapp). Bootstrap 5 writes physical left and right in
  its CSS and makes its own RTL build the same way, so logical properties in Respondr's SCSS
  alone wouldn't mirror Bootstrap. The left-to-right file is unchanged.
* The Dynamic Respondr Skin portlet links `<skinName>-rtl.css` for a right-to-left locale when the
  webapp has it, and the skin itself otherwise. Skins that adopters build, and skins the portlet
  compiles from its preferences, have no mirrored copy. UPGRADING-6.0 says how to make one.
* `tags.scss` mirrors the arrow, chevron, angle, caret, sign-in and sign-out icons in a
  `[dir="rtl"]` page.

## Test

uPortal-start `tests/ux/i18n/rtl-theme.spec.ts`, as a guest, whose locale lasts for the browser
session only. ar-XB is the right-to-left pseudo-locale of MOD-D51. The locale API
(`PUT /api/v6-0/locale`) takes only the locales in `portal_locales`, and the release offers none
written right to left, so uPortal-start's `-PtestPortalLocales=ar_XB` appends ar_XB to
`portal_locales` in portal.home. The coordinator chose this over adding Arabic to the
quickstart or loosening the API. CI's jobs that run `tests/ux` set it. Without it the ar-XB tests
skip and say how to deploy the locale. `user-locale.spec.ts` and `user-locale-component.spec.ts`
count the release's 8 locales plus the offered pseudo-locales. It has no messages, and the text is the English text. The spec checks, in en-US and in ar-XB:

* `lang` and `dir` on `<html>`, and the one skin stylesheet linked;
* that the logo, the first column and a portlet title start on the right in ar-XB only (by the
  centre of their content: a title spans the whole title bar in both directions);
* that the sign-in icon is mirrored in ar-XB only;
* that Popular Portlets has `dir="rtl"` and its first column on the right in ar-XB only;
* at the six MOD-D52 widths, that the ar-XB page has no layout problem that the en-US page lacks;
* that axe (WCAG 2.1 A and AA) doesn't find a violation on the ar-XB page that the en-US page
  lacks.
  With links in running text left without an underline in `defaultSkin-rtl.css` only, it fails
  on seven `link-in-text-block` violations. axe can't judge colour contrast on this page (the
  theme's background image makes it "incomplete"), so a contrast fault doesn't show.

Local run: 10 passed. Seeded faults, each in the deployed copy of `defaultSkin-rtl.css` with a
Tomcat restart, because `pageCachingFilter` keeps CSS in memory:

| Fault | Result |
| --- | --- |
| the unmirrored CSS served as `defaultSkin-rtl.css` | fails on the title bar. The header and the columns use flex layout and mirror from `dir` alone |
| the icon rule removed | fails on the sign-in icon (scale 1, expected -1) |
| `.portal-logo { min-width: 1400px }` in the RTL file only | fails at 1366px on a sideways scroll and a spill of `#region-header-left` |
| no `defaultSkin-rtl.css` | fails on the stylesheet: the page links `defaultSkin.css` |

Unit tests: `LocaleDirectionTest` (6), `LocaleTransformerConfigurationSourceTest` (4) and
`DynamicRespondrSkinViewControllerTest` (3). With "ar" taken out of the language list, two of
`LocaleDirectionTest`'s tests fail.

## HTTP replay

Local run (`tests/replay/portal.sh reset .`, then `replay.mjs replay`) against uPortal `-rtl`
`116b2034`, with uPortal-start `0010794` plus the `-a11y-replay` registry (`8591298`, picked
locally only). It didn't find any difference from this branch. The outline keeps classes, ids and
the `role`, `href` and `src` attributes, not `dir` or `lang`, and the replay's users are en-US,
whose pages link `defaultSkin.css` as before. So nothing goes into `tests/replay`.

Of 2387 exchanges, 5 were unexplained, all from another change on the integration branch:
`typeAndIdHash` in `/api/entities.json`, `/api/entities/group/*.json` and
`/api/permissions/principals.json` now writes other characters as `_<hex>_` (uPortal `a939af93`,
MOD-I274), and the registry has no entry for it yet. With `defaultSkin.css` changed to
`defaultSkin-rtl.css` on the student's welcome page in the saved run (`--actual`), the replay
reports a sixth exchange, the stylesheet link.

## Dependency

RTLCSS 4.3.0 (MIT), a dev dependency of the uPortal-webapp workspace. It runs only at build
time. Its four dependencies were already in the root `package-lock.json`, which gains the one
`rtlcss` entry. picocolors is ISC; escalade, postcss and strip-json-comments are MIT.

## Next step

Check the push runs of both branches.

## Proposed LOG rows (MOD-I253)

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I253 | Theme | (MOD-I234) | **Fixed** by uPortal `eecc3d94` (`USER_DIR`, `<html dir>`) and `2934bff8` (`defaultSkin-rtl.css` made with RTLCSS and linked for right-to-left locales, mirrored direction icons). Test: uPortal-start `tests/ux/i18n/rtl-theme.spec.ts` in ar-XB. UPGRADING-6.0: "Respondr reads right to left in Arabic, Hebrew, Persian and Urdu". |
