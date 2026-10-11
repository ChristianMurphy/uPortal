# Handoff: a11y-fixes (the accessibility baseline's violations)

This is part 2b of the hardening work (see `hardening.md` on `ccr-43ba0785-hzlrj5-hardening`). It
fixes the axe-core violations that the accessibility baseline records. Branches:
`ccr-43ba0785-hzlrj5-a11y-fixes` in uPortal and in uPortal-start, both from `ccr-43ba0785-hzlrj5`
at `2a9f6b00` and `ad95212`. LOG numbers come from the hardening block (MOD-I250 to MOD-I259).

The accessibility baseline (`tests/a11y/baseline.ts`) and its check are on uPortal-start's
`ccr-43ba0785-hzlrj5-test-suites` branch (`210b46a`), which isn't merged yet. The fixes were
measured with that branch checked out locally. When it's on the integration branch, merge the
integration branch into uPortal-start's `-a11y-fixes` and remove the fixed entries from the
baseline in an ordinary commit (the list is under "Local results").

## Status

| Step | State |
| --- | --- |
| Abstract ARIA roles, `aria-selected`, form control names | committed (uPortal `89cd875b`) |
| Underlined links in running text, UPGRADING-6.0 section | committed (uPortal `0cb136be`) |
| Search button name | committed (uPortal `d3ddcf03`), approved by the coordinator |
| Baseline in uPortal-start | waits for `-test-suites` to be merged |

## What was wrong and what changed

The baseline was recorded from Integration run 38100770775 on 10 key pages. Its entries come
down to these causes:

| axe rule | Pages | Cause | Fix |
| --- | --- | --- | --- |
| `aria-roles` | admin pages, guest | `role="section"` and `role="sectionhead"` on cards and title bars (64 JSPs, `respondr.xsl`). Both are abstract roles | removed. No script or stylesheet in uPortal selects on them |
| `aria-allowed-attr` | all | `aria-selected="false"` on `#portalNavigation` and on each region portlet's `section` | removed |
| `label` | Permissions | `aria-labelledby` held message text, not an id, and the labels pointed at the wrapper divs | inputs get ids, labels point at them, `aria-label` from the `permission.suggest.*.label` messages |
| `select-name` | Portlet Manager | the State and Category filters had no name | `aria-label` from the `state` and `category` messages |
| `link-in-text-block` | all | `tags.scss` removes every link's underline, and the footer's link colour is 1.4:1 against its text | links in `p` and in `footer` are underlined |
| `button-name` | all | `header.scss` hid the search button's text with `display: none` | the span extends `.visually-hidden` |

The fixes use existing message keys only.

These baseline entries are outside uPortal and uPortal-start:

* `select-name` on `esco-content-grid >>> select` (admin home): the `esco-content-menu` webjar.
* `label` on `.upcal-date-input` and `input[multiple="multiple"]` (student, staff and faculty
  home): the Calendar portlet.

## Local results

A local portal on HSQLDB with uPortal `-a11y-fixes` (`d3ddcf03`) and uPortal-start
`-test-suites` (`210b46a`), with `npx playwright test --config=tests/a11y-pw.config.ts`.

* Every entry of the table above is gone on every page where the baseline records it. What
  remains is the three entries outside these repositories.
* student-home reports `button-name` on `notification-icon >>> #dropdownMenuButton`, which the
  baseline doesn't have. The Notification portlet's web component renders it in its shadow DOM,
  which page CSS can't reach. The changes on this branch don't touch it.

The search button, checked with a Playwright script against the same portal:

* At 320, 390, 768, 1024, 1366 and 1920 pixels, the header's screenshot and the boxes of
  `#webSearchContainer`, `#webSearchSubmit` and the input are the same with the new rule as with
  the old `display: none` set on the span. With the span made visible instead (seeded fault),
  the button grows from 39 to 89 pixels and the screenshots differ at every width.
* After choosing the locale in `user-locales-selector`, the button's accessible name is
  "Search" (en_US), "Búsqueda" (es_ES) and "Rechercher" (fr_FR). de_DE has no translation and
  falls back to "Search".

## Build note

On this branch, `prepareSkinResources` doesn't list the Respondr sources as inputs, so after an
SCSS edit `./gradlew :uPortal-webapp:install` keeps the old CSS. Delete
`uPortal-webapp/build/generated-sources/skin` first. `-hardening` fixes the task's inputs
(`886c73b`).

## Proposed LOG rows (MOD-I252)

| ID | Row |
| --- | --- |
| MOD-I252 | Respondr and uPortal's portlet JSPs fix the violations in the accessibility baseline: abstract `section` and `sectionhead` roles removed, `aria-selected` removed from `#portalNavigation` and region portlets, names for the Permissions inputs, the Portlet Manager filters and the search button, and links in running text underlined. UPGRADING-6.0 "Respondr fixes for the accessibility checks" |
