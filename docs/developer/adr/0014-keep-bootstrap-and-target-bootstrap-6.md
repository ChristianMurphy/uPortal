---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Keep Bootstrap as the theme framework and target Bootstrap 6

## Context and Problem Statement

uPortal bundles Bootstrap 5.3.8 as a webjar. The Respondr skin is written in SCSS, with a legacy
`common.less`, and adopters build custom skins from it with SCSS `@import`. In uPortal-webapp and
uPortal-portlets the markup uses Bootstrap's dropdown in 20 files, modal in 15, offcanvas in 7,
`data-bs-` attributes in 15, tooltip in 3, `btn-` classes in 48 and `form-control` in 22. Four skin
files use `@import`.

Bootstrap 6.0.0-alpha.1 was published on npm on 8 October 2026 (MIT, dist-tag `next`). Compared
with 5.3, Bootstrap's own migration guidance lists these changes:

* Modal becomes Dialog, a native `<dialog>` element without the `.modal-dialog` and
  `.modal-content` wrappers. Offcanvas becomes Drawer, and Dropdown becomes Menu without the
  `.dropdown` wrapper or `.dropdown-toggle`.
* `.btn-primary` becomes `btn-solid theme-primary`, and every variant moves to theme tokens.
  Responsive prefixes take the form `md:`, as in `.md:navbar-expand`.
* Utilities, font sizes and the spacer scale are renamed, and form validation uses
  `data-bs-validate`.
* The JavaScript is TypeScript, published as ES modules only, with Floating UI in place of Popper,
  and lifecycle methods return promises.
* The Sass uses modules only (`@use` and `@forward`), with renamed variables and functions.
* The CSS uses cascade layers: `colors`, `config`, `root`, `reboot`, `layout`, `content`, `forms`,
  `components`, `custom`, `helpers` and `utilities`. Skins belong in `custom`.

The new pages and web components (ADR 0012, ADR 0013) need a theme. Which version of which
framework should they use?

## Decision Drivers

* New UI code shouldn't be written against markup that is about to change.
* Adopters' skins should keep working, or have one clear migration.
* Bundled portlets from other repositories still emit Bootstrap 3 and 5 markup.
* Web components use shadow DOM, which page CSS doesn't reach.

## Considered Options

* Keep Bootstrap and target Bootstrap 6, pinned through its alpha and beta releases to GA
* Stay on Bootstrap 5.3 for 6.0

## Decision Outcome

Chosen option: "Keep Bootstrap and target Bootstrap 6, pinned through its alpha and beta releases
to GA", because new UI written for 6.0 would otherwise be rewritten for Bootstrap 6 soon after.

The plan:

1. New pages and web components use Bootstrap 6 markup from the start, with the alpha version
   pinned and upgraded on purpose through beta to GA.
2. The Respondr skin moves to Sass modules, with skin overrides in the `custom` layer.
3. Existing JSP and XSL markup moves to Menu, Dialog, Drawer and Floating UI as ADR 0012 replaces
   those views. A focused markup change handles whatever remains.
4. A small compatibility stylesheet in its own cascade layer keeps the bundled portlets' Bootstrap
   3 and 5 markup rendering until each portlet is ported.
5. Web components are styled through Bootstrap 6's CSS custom properties, which do cross shadow
   roots, plus one shared constructable stylesheet, or simple components render in light DOM. The
   web-components foundation work picks one of the two and covers it with Vitest browser-mode tests.

If Bootstrap 6 GA comes after uPortal 6.0, 6.0 is released on Bootstrap 5.3 with the same layer
structure and moves to 6 in 6.x. That choice is made at the 6.0 release candidate.

### Consequences

* Good, because new UI is written once, for the version it will run on.
* Good, because cascade layers give skins a defined place, separate from Bootstrap's own rules.
* Bad, because the alpha API can change. Bootstrap-specific markup is kept in Thymeleaf fragments
  and a small set of Lit base classes to limit the rework.
* Bad, because adopters' custom skins and uPortal-start's `skinGenerate` task have to move from
  `@import` to Sass modules.
* Bad, because the compatibility stylesheet for older portlet markup is extra CSS to maintain.

### Confirmation

Vitest browser-mode tests check component styling inside shadow roots (ADR 0013). Playwright visual
smoke specs in uPortal-start check that bundled portlets still render with the compatibility
stylesheet. These steps are planned and not yet done.

## Pros and Cons of the Options

### Keep Bootstrap and target Bootstrap 6, pinned through its alpha and beta releases to GA

* Good, because the new pages and components skip one migration.
* Good, because Bootstrap 6's native `<dialog>` and Floating UI replace older patterns.
* Bad, because uPortal tracks a prerelease for a while.

### Stay on Bootstrap 5.3 for 6.0

* Good, because 5.3 is stable and already bundled.
* Bad, because the new UI would be migrated again when Bootstrap 6 is released.
* Neutral, because it remains the fallback if Bootstrap 6 GA comes too late.

## More Information

* The prototype didn't compare other CSS frameworks. The decision was to keep Bootstrap.
* [Bootstrap on npm](https://www.npmjs.com/package/bootstrap),
  [Bootstrap source](https://github.com/twbs/bootstrap)
* Prototype references: MOD-D21, MOD-I81.
