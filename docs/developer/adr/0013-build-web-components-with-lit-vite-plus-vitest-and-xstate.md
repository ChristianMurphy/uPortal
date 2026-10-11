---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Build web components with Lit, Vite+ and Vitest, and model their state with XState

## Context and Problem Statement

ADR 0012 replaces several built-in portlets with web components that call REST APIs. uPortal's
existing web components are written in Vue 2, which reached end of life at the end of 2023, and
their repositories have been dormant since early 2024. uPortal's own JavaScript is ES2021 with
jQuery, linted with ESLint, and has no component build or component tests.

Which library and tools should the new components be built and tested with? And how should they
manage UI states such as loading or showing an error?

## Decision Drivers

* Standard custom elements that any page or client can place.
* One build and test toolchain, versioned with the REST APIs the components call.
* Tests in a real browser, covering rendering states, events, keyboard use and accessibility.
* UI states that are explicit and testable, instead of combinations of boolean properties.
* Reproducible CI with pinned versions.

## Considered Options

* Lit components, built with Vite+ and tested with Vitest in browser mode, with state in XState
  machines
* Vue 3 components
* Plain Vite and Vitest without Vite+
* Robot3 with lit-robot for state
* `@xstate/store` for state
* Plain Lit reactive properties for state

## Decision Outcome

Chosen option: "Lit components, built with Vite+ and tested with Vitest in browser mode, with state
in XState machines", because Lit is a small library for standard custom elements, which the portal's
channel type places directly, Vite+ bundles the build, lint, format and test tools in one pinned
package, and XState makes every state
explicit and testable as a model.

Versions (from npm, 10 October 2026), pinned exactly and updated by Renovate in one group:

| Package | Version |
| --- | --- |
| `lit` | 3.3.3 (BSD-3-Clause) |
| `vite-plus`, with the CLI `vp` | 1.1.0 (MIT), released 7 October 2026 |
| `vite` | 8.3.4 |
| `vitest` and `@vitest/browser` | 5.0.3 |
| `xstate` | 5.33.2 |
| `@xstate/graph` | 3.0.4, with `xstate` ^5.19.4 as its peer dependency |

How it fits into uPortal:

* One web-components workspace inside uPortal. The components then share a version with the REST
  APIs they call.
* `vite-plus` is a devDependency. It isn't installed globally with `curl | bash`.
* Node stays managed by the existing Gradle Node plugin, not by `vp env`.
* Gradle tasks call `vp check` (format, lint and types), `vp test` and `vp build`. The built ES
  modules are packaged into the uPortal web application as static resources with cache-busting
  names.
* XState goes into one shared chunk, loaded once for all components.

State machines:

* Any component with more than trivial state uses a machine, built with
  `setup(...).createMachine(...)`. Side effects such as REST calls are `fromPromise` actors declared
  in `setup`, so tests can replace them.
* Templates render from `matches()` and `hasTag()`, not from boolean properties added case by case.
* A small adapter based on Lit's public `ReactiveController` API connects the two. There is no
  official `@xstate/lit` package. The adapter starts the actor when the element connects,
  subscribes through a selector, calls `requestUpdate()` only when the selected value changes, and
  stops the actor when the element disconnects.
* The adapter works by composition, without a base class, mixin or patching. Its types come from
  the machine, so components need no `any` or casts, and an unknown event is a type error.
* Moving an element in the DOM keeps its state, because the snapshot is restored on reconnect.
  Machine `emit`s become DOM `CustomEvent`s. Host properties set the actor's input at first
  connect, and later changes become explicit events.
* The adapter depends only on `lit` and `xstate`, so it could be published on its own.

### Consequences

* Good, because each state of a component is named and reachable in tests.
* Good, because Vite+ gives format, lint, type check, test and build one command each.
* Good, because the components are standard custom elements, usable from Thymeleaf pages, the
  portal's channel type and other clients.
* Bad, because Vite+ 1.x was days old when chosen. The configuration is kept portable (a plain
  `vite.config.ts` and Vitest configuration). The build can fall back to plain Vite and Vitest.
* Bad, because uPortal maintains its own Lit and XState adapter.
* Bad, because browser-mode tests need a browser in CI, which adds time to the build.

### Confirmation

Each component needs Vitest tests in browser mode (Playwright provider, Chromium) for real custom
element and shadow DOM behaviour, plus unit tests for pure logic. They cover:

* Rendering states: loading, empty, error and populated.
* User interaction and emitted events, and attribute and property reflection.
* i18n keys, keyboard navigation and focus.
* An axe-core accessibility check in each state.
* REST calls with mocked responses, including 401, 403, 500 and malformed payloads.

Coverage thresholds start at 90% of lines and branches per package and are enforced in CI. Snapshot
tests are only allowed for small, stable markup. Machines get pure tests in Vitest, and model-based
tests with `@xstate/graph`, whose shortest and simple paths drive the real component through every
reachable state. Each adapter rule above has its own test. Playwright specs in uPortal-start remain
the required check for behaviour inside the portal. The first component, `<up-favorites>`, is being
converted with the adapter and model-based tests.

## Pros and Cons of the Options

### Lit components, built with Vite+ and tested with Vitest in browser mode, with state in XState machines

* Good, because Lit's output is standard custom elements.
* Good, because XState's types flow from the machine into the component, and `@xstate/graph`
  generates the paths for model-based tests.
* Bad, because XState adds a dependency that each component loads, kept to one shared chunk.

### Vue 3 components

* Good, because the existing components are Vue 2, and Vue 3 is its successor.
* Neutral, because moving from Vue 2 to Vue 3 is itself a migration. The existing components need
  rework either way.

### Plain Vite and Vitest without Vite+

* Good, because both are mature.
* Neutral, because it remains the fallback, and the configuration is kept compatible with it.
* Bad, because lint, format and bundling then need separate tools and configuration.

### Robot3 with lit-robot for state

* Good, because it is smaller (robot3 1.4.0, lit-robot 3.0.1).
* Bad, because it adds a second state vocabulary next to XState.

### `@xstate/store` for state

* Good, because it is small and event-driven.
* Bad, because its stores aren't statecharts and can't be tested as a model.

### Plain Lit reactive properties for state

* Good, because it doesn't add a library.
* Bad, because states are implicit and can't be tested as a model.

## More Information

* Also rejected: the community `xstate-lit` 2.0.5, last published in January 2024, with no listed
  repository or licence. Lit's template compiler (`@lit-labs/compiler` 1.1.2) stays out while it is
  a Labs prototype.
* [Lit](https://lit.dev/), [Vitest browser mode](https://vitest.dev/guide/browser/),
  [XState](https://stately.ai/docs/xstate)
* Prototype references: MOD-D18 (Lit), MOD-D20, MOD-D31, MOD-Q5 (Vue 2 end of life).
