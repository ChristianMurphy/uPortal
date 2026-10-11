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
| axe in right to left | waits for uPortal-start's `-test-suites`, which adds `@axe-core/playwright` |

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
session only. The test chooses the locale in `user-locales-selector` by giving one of its choices
the test locale's code. The flow takes any code. ar-XB is the right-to-left pseudo-locale of
MOD-D51. It has no messages, and the text is the English text. The spec checks, in en-US and in ar-XB:

* `lang` and `dir` on `<html>`, and the one skin stylesheet linked;
* that the logo, the first column and a portlet title start on the right in ar-XB only (by the
  centre of their content: a title spans the whole title bar in both directions);
* that the sign-in icon is mirrored in ar-XB only;
* that Popular Portlets has `dir="rtl"` and its first column on the right in ar-XB only;
* at the six MOD-D52 widths, that the ar-XB page has no layout problem that the en-US page lacks.

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

## Next step

Push both branches and check their push runs. When `-test-suites` is merged, add an axe check of
the ar-XB guest page to the spec.

## Proposed LOG rows (MOD-I253)

| ID | Area | Finding | Resolution |
| --- | --- | --- | --- |
| MOD-I253 | Theme | (MOD-I234) | **Fixed** by uPortal `eecc3d94` (`USER_DIR`, `<html dir>`) and `2934bff8` (`defaultSkin-rtl.css` made with RTLCSS and linked for right-to-left locales, mirrored direction icons). Test: uPortal-start `tests/ux/i18n/rtl-theme.spec.ts` in ar-XB. UPGRADING-6.0: "Respondr reads right to left in Arabic, Hebrew, Persian and Urdu". |
