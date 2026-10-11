# Web Components in uPortal

uPortal 6 replaces its own portlets with REST APIs and web components (decisions MOD-D18 to
MOD-D22 in the [modernization log](../modernization/LOG.md)). This page describes the parts
every conversion builds on:

* the **Web Component channel type**, which places a custom element on a portal page without the
  portlet container;
* the **`uPortal-webcomponents` workspace**, where the components are written (Lit and
  TypeScript), styled (Bootstrap 6), tested (Vitest) and built (Vite+).

## The Web Component channel type

A web-component channel is an ordinary portlet definition, so it is published, permissioned,
placed in layouts and fragments, favorited and searched like any other channel. Its portlet type
is **Web Component** (`/org/apereo/portal/portlets/webcomponent/WebComponent.cpd.xml`), whose
descriptor is the framework portlet `WebComponent`. The portal recognises the channel by that
descriptor and renders it itself.

### Publishing parameters

| Parameter | Required | Meaning |
| --- | --- | --- |
| `webComponent.tagName` | yes | The custom element, for example `up-favorites`. It must contain a hyphen. |
| `webComponent.moduleUrl` | yes | The ES module that defines the element: the name of a module bundled with uPortal (`up-favorites`), an absolute `http(s)` URL, or a path that starts with `/`. |
| `webComponent.attributes` | no | Static attributes, one `attribute-name=value` per line. |
| `webComponent.preferenceAttributes` | no | Portlet preferences passed as attributes, one `attribute-name=preferenceName` per line. The value the user saved wins over the value set on the definition; several values are passed as a JSON array. |

Invalid parameters (a tag without a hyphen, a `javascript:` URL, an `on*` event-handler
attribute, an attribute the portal sets itself) make the channel fail with the usual channel
error rather than emit unsafe markup. All values are HTML-escaped.

A portlet-definition file for such a channel looks like this:

```xml
<portlet-definition ...>
    <title>Favorites</title>
    <name>Favorites</name>
    <fname>favorites</fname>
    <type>Web Component</type>
    <timeout>50000</timeout>
    <portlet-descriptor>
        <ns2:isFramework>true</ns2:isFramework>
        <ns2:portletName>WebComponent</ns2:portletName>
    </portlet-descriptor>
    ...
    <parameter>
        <name>webComponent.tagName</name>
        <value>up-favorites</value>
    </parameter>
    <parameter>
        <name>webComponent.moduleUrl</name>
        <value>up-favorites</value>
    </parameter>
</portlet-definition>
```

### Where it plugs in

The rendering pipeline is unchanged, and no XSL changed. For every `channel` element of the
layout, `PortletRenderingInitiationStAXComponent` asks the `IPortletExecutionManager` to start
the channel's render, and the theme leaves placeholders for the channel's title and content.
`PortletRenderingIncorporationComponent` later replaces each placeholder, in page order, with
what the execution manager returns.

The execution manager creates its render workers through `IPortletWorkerFactory`. For a
web-component channel `PortletWorkerFactoryImpl` returns a `WebComponentRenderExecutionWorker`
instead of a worker that calls the portlet container. That worker:

* does nothing when submitted, and renders on the request thread the first time the execution
  manager asks for its title or output, so channels render in page order;
* gets its markup from `WebComponentChannelRenderer`;
* reports no title of its own, so the theme shows the definition's title, and no new-item count
  or external link.

Everything else stays as it is for portlets: the theme's chrome (title bar and options menu),
maximize and minimize, focus with `/p/<fname>`, lifecycle states (a channel in maintenance shows
the maintenance message), the error portlet when a channel fails, and the JSON and REST views of
a channel's output.

`WEB-INF/portlet.xml` declares the `WebComponent` portlet only because the portal's window and
entity registries key every channel on a portlet descriptor, and the container needs a class for
each descriptor. Its class, `WebComponentPortlet`, renders nothing and is never asked to render.

### What the element receives

`WebComponentChannelRenderer` emits the module script and the element:

```html
<script type="module" src="/uPortal/webcomponents/assets/up-favorites-D3mQx1aA.js"></script>
<up-favorites locale="en-US" window-state="normal" context-path="/uPortal" fname="favorites"
              csrf-header="X-CSRF-TOKEN" csrf-token="..."></up-favorites>
```

| Attribute | Value |
| --- | --- |
| `locale` | The user's locale as a BCP 47 tag, the same locale the theme renders the page in. |
| `window-state` | `normal`, `maximized`, `detached` or `exclusive`. |
| `context-path` | The portal's context path, the base for its REST APIs. |
| `fname` | The channel's functional name. |
| `csrf-header`, `csrf-token` | Spring Security's CSRF header name and token, to send with unsafe requests. |

The channel's own attributes follow. The portal does not pass the user's identity or
attributes: components ask the REST APIs, which know the user from the session.

### Calling uPortal's REST APIs as the user

A component on a portal page calls the REST APIs on the same origin, so the browser sends the
user's session cookie and the request runs as that user. For unsafe methods (POST, PUT, PATCH,
DELETE) it also sends the CSRF token from its `csrf-header`/`csrf-token` attributes; the base
library's API client does both.

The OIDC userinfo endpoint, `/uPortal/api/v5-1/userinfo`, serves the other case. It returns a
signed ID token (a JWT) for the current user. A component that calls a service on another origin
(a microservice, a Soffit) fetches that token from the portal and sends it to the service as
`Authorization: Bearer <token>`; the service checks the signature with the portal's key. The
portal's own APIs accept the same bearer token, which lets clients that are not portal pages
(uPortal-home, a mobile app) call them; such requests carry no cookie, so they need no CSRF
token. MOD-D22 plans to move this to Spring Security's OAuth2 resource server.

### Module scripts

A module bundled with uPortal is named in `webComponent.moduleUrl` by its entry name, such as
`up-favorites`. The build gives every file a content hash (`up-favorites-D3mQx1aA.js`) and
writes `/webcomponents/manifest.json` (Vite's build manifest); `WebComponentModuleResolver` reads
the manifest once and maps the name to the current file. A new build changes the URL, so the
files can be cached for a year (see `cacheContext.xml`). Absolute URLs and paths are used as
they are; whoever hosts such a module handles its caching.

The renderer emits a module's script only for the first channel in a request that uses it, so a
module loads once per page however many times its channel is placed. The browser would run a
module only once anyway; this keeps the page free of duplicate script tags.

### Window states, portlet modes and other portlet concepts

| Portlet concept | Web Component channel |
| --- | --- |
| Normal and maximized window states | Rendered; the element's `window-state` attribute says which, so it can show more when maximized. |
| Detached | Rendered like maximized, with `window-state="detached"`. |
| Exclusive | The element and its module script are the whole response. |
| Minimized | Renders nothing, like a portlet that honours the minimized state; the theme still shows the title bar. |
| Edit, help, about and config modes | Not supported. The channel renders the same element in every mode, and the type's publishing definition offers no `editable`, `hasHelp` or `hasAbout` parameters, so the theme shows no such menu items. Editing happens inside the component (the Favorites component reorders and removes in place); help and about become links or text in the component; configuration is the publishing parameters and preferences. |
| Portlet preferences | Read-only, passed as attributes through `webComponent.preferenceAttributes`. A component that lets users change settings needs a REST API that stores them. |
| Actions, events, resource URLs, render parameters | Not used. Components call REST APIs instead. A portlet URL that targets the channel reaches `WebComponentPortlet`, which does nothing. |
| Portlet title, new-item count, external link | The definition's title; no count; no link. |
| Portlet caching | Not used. The markup is a few hundred bytes and differs per user (CSRF token). |
| `RENDER_HEADERS` | Not used: the module script is emitted with the element. |

### What the XSL-free design cannot do

* The module script lives in the body, next to the first element that needs it, not in the
  page's `<head>`. Module scripts are deferred, so this only delays the start of the download a
  little.
* There is no page-wide `<meta>` CSRF token: each element carries the token itself.
* Channel output delivered as an HTML fragment that a client inserts with `innerHTML` (for
  example the portlet-content REST API) does not run its `<script>`. Such clients must load the
  module themselves.

## The `uPortal-webcomponents` workspace

All of uPortal's web components live in one npm workspace, which is also a Gradle subproject of
uPortal, so they are versioned, built and released together with the REST APIs they call.

```text
uPortal-webcomponents/
  build.gradle            Gradle tasks that run the npm scripts
  package.json            exact versions; package-lock.json pins the rest
  .npmrc                  no install scripts, exact versions, engine check
  tsconfig.json           strict TypeScript for the browser
  vite.config.ts          Vite+ config: build, Vitest projects, lint, format
  src/lib/                the base library every component uses
  src/state/              ActorController: XState actors in Lit elements
  src/components/<tag>/   one directory per component; <tag>.ts is its entry
  src/test-support/       test helpers (fetch stub, fixtures, axe)
```

A separate subproject, rather than a directory of `uPortal-webapp`, keeps the JavaScript
toolchain out of the webapp's build: the workspace has its own lockfile and `node_modules`, its
tasks are up to date unless its own files change, and `./gradlew :uPortal-webcomponents:check`
checks it alone. Its jar is a webjar (the modules under `META-INF/resources/webcomponents`);
`uPortal-webapp` unpacks it into the war, so the modules are served from `/uPortal/webcomponents/`
whatever the servlet container's jar scanning settings.

Libraries that several components use go into shared chunks (`output.codeSplitting.groups` in
`vite.config.ts`): XState is one, `assets/xstate-<hash>.js`, which each component's entry imports,
so a page loads it once however many components it shows.

### Toolchain and Gradle tasks

The workspace is built with [Vite+](https://viteplus.dev) (`vp`), which bundles Vite, Vitest,
Oxlint, Oxfmt and Rolldown. `vite-plus` is an exact-version devDependency; it is never installed
globally with `curl | bash`. Node comes from the node-gradle plugin (`nodejsVersion` in
`gradle.properties`), like the rest of the build, and `npm ci` installs exactly what
`package-lock.json` pins.

| Gradle task | npm script | Part of |
| --- | --- | --- |
| `vpCheck` | `vp check`: formatting (Oxfmt), lint (Oxlint, warnings fail) and type checks | `check` |
| `vpTest` | `vp test run --coverage`: unit and browser tests with coverage thresholds | `test`, so `check` and `build` |
| `vpBuild` | `vp build`: ES modules with content-hashed names, and `manifest.json` | `jar`, so `build` and `install` |
| `installChromium` | `playwright install chromium` (with system libraries on Linux CI) | CI only |

The configuration is plain `vite.config.ts` through `defineConfig` from `vite-plus`, with the
Vitest settings in its `test` block, so the workspace could fall back to plain Vite and Vitest by
changing the imports.

For day-to-day work, use the Node that Gradle downloaded, or any Node that `package.json`'s
`engines` accepts:

```sh
cd uPortal-webcomponents
npm ci
npx vp check --fix        # format and lint
npx vp test watch         # tests in watch mode
npx vp build
```

The browser tests need the Chromium build that the pinned Playwright expects. Run
`./gradlew :uPortal-webcomponents:installChromium` once, or point `PLAYWRIGHT_BROWSERS_PATH` at
an existing install of that revision.

### The base library (`src/lib`)

* **`UpElement`** (`up-element.ts`): the base class. It declares the context attributes the
  channel renderer sets (`locale`, `window-state`, `context-path`, `fname`, `csrf-header`,
  `csrf-token`) as reflected properties, adopts the shared Bootstrap stylesheet, and offers
  `this.api` (an `ApiClient` set up from those attributes), `this.msg(key, ...args)` and
  `this.emit(name, detail)`. A component lists its messages, with English defaults, by
  overriding `defaultMessages`.
* **`ApiClient`** (`api-client.ts`): same-origin `fetch` with the session cookie, JSON in and
  out, the CSRF header on unsafe methods, and one error type, `ApiError`, whose `kind` is
  `unauthorized` (401), `forbidden` (403), `not-found` (404), `conflict` (409), `server` (5xx),
  `http` (other statuses), `network` (no response) or `malformed` (not JSON, or a body the
  component's validator refuses). Components validate every response body.
* **Messages** (`i18n.ts`): a component renders its English defaults at once, then loads the
  same keys in the user's locale from `GET /api/v6-0/messages`, which reads the portal's message
  source: `Messages.properties`, its translations, and messages edited in the portal. One request
  per locale and set of keys serves every instance on the page; if it fails, the defaults stay.
  Keys follow `Messages.properties`, and placeholders are `{0}`, `{1}`, …
* **Events** (`events.ts`): components report what happened with `CustomEvent`s named
  `up-<subject>-<past-tense verb>`, such as `up-favorite-removed`. They bubble, are composed
  (they leave the shadow root) and are not cancelable; `detail` is plain data.

### Bootstrap 6 and shadow DOM

Components render into a shadow root, so neither the portal's skin (still Bootstrap 5) nor
legacy CSS leaks into them, and Bootstrap's CSS does not reach in either. Each component
therefore adopts **one shared constructable stylesheet**: `src/lib/styles/bootstrap.scss`
compiles the parts of Bootstrap 6 that the components use (root and color tokens, reboot,
buttons, close button, list group, alert, spinner, placeholder, theme colors, focus ring and
visually-hidden helpers) from Bootstrap's Sass sources, and Lit adopts the resulting sheet, a
single `CSSStyleSheet` object, into every shadow root. It is about 60 kB minified (10 kB
gzipped), loaded once per page with the first component.

Light DOM was the alternative. It would let the page's own CSS style the markup, but the page's
CSS is Bootstrap 5, so Bootstrap 6 markup would render wrongly until the skin moves (MOD-D21),
and legacy portal styles would leak in. Light DOM can still suit a later, very simple component
once the skin is Bootstrap 6.

Consequences:

* Markup is Bootstrap 6 from the start: `btn-solid theme-primary`, `list-group`,
  `alert theme-danger`, `visually-hidden`, and so on. Fonts and text color are inherited from the
  page.
* Bootstrap 6 puts its design tokens on `:root, :host`. Compiled from source without Bootstrap's
  PostCSS step, they are unprefixed (`--primary-bg`, `--spacer`), which is also how a skin
  compiled with plain Sass names them. Because the tokens are set on each component's host, a
  skin themes components by setting tokens on the elements themselves, for example
  `up-favorites { --primary-bg: #8c1d40; }`; tokens set only on the page's `:root` do not reach
  them.
* Components follow the page's `color-scheme` rather than Bootstrap's `light dark` default, so a
  component does not turn dark inside a light portal page.
* Components draw their own small SVG icons; icon fonts (Font Awesome) do not reach into shadow
  roots.
* Add a partial to `bootstrap.scss` only when a component needs it: the sheet is shared, but
  every byte is parsed.

The browser tests check that the sheet is shared, that tokens and components apply inside the
shadow root, that the page can theme a component, and that the color scheme follows the page.

### Charts (MOD-D34)

Components that draw charts use Apache ECharts. Import it from `echarts/core` and register only
the chart types, components and renderer the component needs (`echarts.use([...])`), so a page
loads only the chart code it shows. Do not use Google Charts or Chart.js. No component draws a
chart yet. The first one adds `echarts` to `package.json` at an exact version.

The statistics reports already build their data as a `ReportTable` (uPortal-web,
`org.apereo.portal.web.report`): typed columns and one value per column in each row. Jackson
writes it as the DataTable JSON that today's Google Charts pages read, and `ReportTableRenderer`
writes CSV and HTML. When a report becomes a component (MOD-D34 step 2), its REST endpoint returns
that table's columns and rows as plain JSON, and the component passes the rows to ECharts as a
`dataset`. Turn on ECharts' `aria` option so screen readers get a description of each chart.

### State machines (MOD-D31)

**Rule:** a component with more than trivial state models it as an
[XState v5](https://stately.ai/docs/xstate) state machine and runs it through
`ActorController`. Trivial means one or two independent flags, such as a disclosure that is open
or closed; anything with loading, failures, requests in flight or modes is not trivial.

* **The machine** is a pure module next to the component, `<tag>.machine.ts`, built with
  `setup({ types, actors, guards, actions }).createMachine(...)`. It has no DOM access. Side
  effects such as REST calls are `fromPromise` actors declared in `setup` (pass their `signal`
  to `ApiClient`), so tests replace them with `machine.provide({ actors })`. Outcomes the
  element shows but the machine should not format, such as notices, focus targets and live
  region text, are plain data in the context; the element turns them into messages and DOM.
* **The template** renders from the snapshot: `snapshot.matches(...)` for states,
  `snapshot.hasTag(...)` for cross-cutting facts such as `busy` (rendered as `aria-busy`), and
  context data. No ad hoc boolean properties duplicate the machine's state. User actions send
  events; a key handler calls `preventDefault()` only when `snapshot.can(event)`.
* **The adapter**, `src/state/actor-controller.ts`, is a Lit `ReactiveController` used by
  composition, `new ActorController(this, machine, options)`, on any `LitElement`. It depends on
  `lit` and `xstate` only, so it could be published on its own. Snapshot, selected value and
  events are inferred from the machine (sending an event the machine does not define is a type
  error).

```ts
readonly #favorites = new ActorController(this, favoritesMachine, {
    input: () => ({ api: this.api }),                       // properties in, at the first connect
    events: { 'favorite.removed': 'up-favorite-removed' },  // emitted events out, as DOM events
    // selector: (snapshot) => ..., compare: (a, b) => ...  // re-render only when this changes
});

protected override willUpdate(changed: PropertyValues<this>): void {
    super.willUpdate(changed);
    // Later property changes reach the machine as explicit events; no two-way binding
    if (this.hasUpdated && changed.has('csrfToken')) {
        this.#favorites.send({ type: 'api.changed', api: this.api });
    }
}
```

What the adapter guarantees:

| Concern | Behaviour |
| --- | --- |
| First connect | Creates the actor with `input()` and starts it. Its first snapshot joins the element's first update, so the element renders once. |
| Disconnect | Unsubscribes, keeps `getPersistedSnapshot()` and stops the actor. The host gets no update afterwards. |
| Reconnect (the element moved) | Restores the actor from that snapshot, so the state survives the move. |
| A promise in flight across a move | Stopping aborts it (its `signal` fires) and ignores its result; the restored actor starts it again with the same input. Invoked requests must therefore be safe to repeat; the favorites API's DELETE (404 counts as removed) and move (no-op when in place) are. |
| Events sent while disconnected | Queued and delivered in order once the actor runs. |
| Rendering | `requestUpdate()` only when the selected value changes (`Object.is`, or `compare`). |
| Machine `emit` | Mapped emitted events become bubbling, composed `CustomEvent`s on the host whose `detail` is the emitted event without its `type` (`toCustomEvent`). |

Alternatives considered:

* **Robot3 with lit-robot:** smaller, but a second state vocabulary next to the statecharts that
  the model-based tests and the Stately tooling understand.
* **@xstate/store:** event-driven stores, not statecharts: no hierarchy, parallel regions,
  guards, invoked actors or the graph tooling.
* **Plain reactive properties:** the states stay implicit, spread over flags such as `busy` and
  `editing`, and nothing enumerates them for tests.
* **`xstate-lit` 2.0.5:** rejected. It was last published in January 2024 and lists no
  repository or licence.
* There is no official `@xstate/lit`. Lit's template compiler (`@lit-labs/compiler` 1.1.2) also
  stays out, because it is still a Labs prototype.

Development-time inspection (`@statelyai/inspect`) is not wired in. Adding it would need a
dev-only import that production bundles leave out.

### Testing

`vp test` runs two Vitest projects:

* `unit` (`*.unit.test.ts`), in Node, for logic without a DOM;
* `browser` (`*.browser.test.ts`), in headless Chromium through Playwright, for everything that
  touches custom elements, shadow DOM, styles, focus and the keyboard.

Every component's browser tests cover each rendering state (loading, empty, error, populated);
interactions and the events they emit; attribute and property reflection; the messages it uses;
keyboard navigation and focus; an axe-core accessibility check in each state
(`src/test-support/axe.ts`, WCAG 2.2 A and AA); and its REST calls against stubbed responses,
including 401, 403, 500, network failures and malformed bodies. REST calls are stubbed by
replacing `fetch` with a small router of real `Response` objects (`src/test-support/fetch-stub.ts`)
rather than with MSW: there is no service worker to install in the browser runner, and the stub
records each request for assertions. Snapshots are only for small, stable markup.

A component with a machine adds two kinds of tests:

* **Machine tests** (`<tag>.machine.unit.test.ts`, Node): every transition, guard, action and
  error path, with the REST actors replaced through `machine.provide({ actors })` by promises
  the test settles.
* **Model-based tests** (`<tag>.model.browser.test.ts`, Chromium): `@xstate/graph`'s
  `getShortestPaths` (and `getSimplePaths` where the graph is small) generate paths through the
  machine. The test mounts the real element for each path, plays each step as a user action or
  as the REST response the step stands for (`xstate.done.actor.*`, `xstate.error.actor.*`), and
  checks that the rendered output and its accessibility attributes match the state after every
  step, with axe in the state the path reaches. The state key passed as `serializeState` must
  include all the context the element renders, not only the state value.

Coverage (V8) must stay at 90% or more of lines, branches, functions and statements in every
source file, not only in total; `vp test` fails below that. The report is in `build/coverage`.

## REST APIs for web components

Components get their data from Spring MVC `@RestController`s in `uPortal-api-rest`, with
Jackson for JSON (MOD-D22). Conventions:

* Paths start with `/api/v6-0/`, for example `/api/v6-0/favorites`.
* The user is the one of the portal session, established by Spring Security's
  pre-authentication filter (or an OIDC bearer token, see above). Controllers use the portal's
  services (`IPersonManager`, the user's layout manager, `IAuthorizationService`) and answer 401
  to guests when the resource is personal.
* Unsafe requests to `/api/v6-0/` need Spring Security's CSRF token. The older APIs under `/api`
  are exempt from CSRF protection; `PortalCsrfSecurityRequestMatcher` protects the new ones,
  except requests that carry a bearer token.
* Refusals answer a JSON body with a `message`, and a status the component can map to text: 400,
  401, 403, 404 or 409.
* Code against annotations and types that Spring 5, 6 and 7 keep (`@GetMapping`,
  `ResponseEntity`, constructor injection), and test controllers with MockMvc.

### Favorites

| Request | Answer |
| --- | --- |
| `GET /api/v6-0/favorites` | `{"collections": [...], "portlets": [...], "marketplaceUrl": ...}` in layout order; one entry per portlet, only portlets the user may render. |
| `POST /api/v6-0/favorites` with `{"fname": "calendar"}` | 201 and the new favorite (or 200 and the existing one); 403 without the `FAVORITE` permission; 404 for an unknown portlet; 409 when the layout has no favorites folder. |
| `DELETE /api/v6-0/favorites/{nodeId}` | 204; removes every copy of a portlet; 403 for a locked favorite. |
| `POST /api/v6-0/favorites/{nodeId}/move` with `{"before": "<nodeId>"}` or `{"after": "<nodeId>"}` | 200 and the favorites; moves among favorites of the same kind; 403 for a locked one. |

A favorite collection or portlet carries its layout `nodeId`, `name`, `url`, `moveAllowed` and
`deleteAllowed`; a portlet also its `fname`, `description`, `target` and `iconUrl`. The service
reuses `FavoritesUtils` and the user's layout manager, so the distributed layout manager's rules
for locked fragments apply.

### Popular Portlets

| Request | Answer |
| --- | --- |
| `GET /api/v6-0/popular-portlets?days=30&locale=en-US` | `{"days": 30, "portlets": [...]}`: the portlets users added to their layouts in the last `days` days (1 to 365, default 30), most added first and ties by fname; 400 for other days. Guests may ask. |

A portlet carries its `fname`, `title` and `description` in the `locale` (default the request's),
its `url` (`<context path>/p/<fname>`) and its `count`. `PopularPortletsService` sums the daily
portlet-layout aggregations of the group of all users, as the portlet did, and leaves out portlets
the user may not subscribe to and portlets that no longer exist.

### Self edit account

| Request | Answer |
| --- | --- |
| `GET /api/v6-0/account` | `{"username": ..., "details": [...], "canEdit": ..., "editable": [...]}`; each attribute has its `name`, its `label` in the user's language and its first `value` (or `null`). `editable` is empty unless `canEdit`. |
| `PUT /api/v6-0/account` with `{"attributes": {"department": "..."}, "password": "...", "confirmPassword": "...", "currentPassword": "..."}` | 204. An attribute left out keeps its value, an empty one is removed. 403 when the user may not edit their account or names an attribute they may not edit; 400 with a `code` (`passwords.must.match`, `please.enter.current.password`, `current.password.doesnt.match`) for a refused password. |

`SelfEditAccountService` works on the user of the portal session and never takes a user name from
the request; the permission rules stay in `UserAccountHelper` (a local account, `UP_USERS`
`EDIT_USER` on `SELF`, `EDIT_USER_ATTRIBUTE` per attribute). The API never logs, returns or
reports a password, and answers an unreadable body without the parser's message, which can quote
the body.
`<up-self-edit-account>` replaces the Password Management portlet: the machine's states are
`loading`, `failed`, `viewing`, `editing` (`idle` or `saving`) and `saved`, and the passwords stay
in its context only while the form is open.

## Reference conversion: Favorites

The Favorites portlet (Spring Portlet MVC, four JSPs, an EDIT mode) is the first portlet
converted to a web component, and the pattern for the next ones.

| Portlet | Web component |
| --- | --- |
| `FavoritesController` (VIEW) and `FavoritesEditController` (EDIT), `jsp/Favorites/*.jsp` | `<up-favorites>` (`uPortal-webcomponents/src/components/up-favorites`) |
| Model built from the layout in the controllers | `GET /api/v6-0/favorites` |
| Unfavorite: a portlet action in EDIT mode, `/api/layout?action=removeFavorite` in VIEW | `DELETE /api/v6-0/favorites/{nodeId}` |
| Drag-and-drop reordering in EDIT mode (`/api/layout?action=movePortletAjax` / `moveTab`) | "Reorder favorites": move-up and move-down buttons, and Alt+Up / Alt+Down, through `POST /api/v6-0/favorites/{nodeId}/move` |
| EDIT mode, with lock icons and their legend | The same view, with reordering switched on in place; Escape switches it off |
| `FavoritesController.maxHeightPixels` preference | `max-height-pixels` attribute, through `webComponent.preferenceAttributes` |
| `org.apereo.portal.portlets.favorites.MarketplaceFunctionalName` | The same property, returned by the API as `marketplaceUrl` |
| Messages in `Messages.properties` | The same keys, plus new ones for the new controls |

Adding a favorite stays where it was: the theme's portlet options menu ("Add to my Favorites"),
which still calls `/api/layout?action=addFavorite`. The API also accepts
`POST /api/v6-0/favorites` for clients that add favorites themselves.

The component follows the testing bar above. Its browser tests cover the loading, empty, error
(500, 403, 401, malformed and non-JSON bodies, network failure) and populated states, each
checked with axe; removing, with focus moving to the next favorite and the outcome announced in
a live region; reordering with the buttons and the keyboard, focus kept on the moved favorite;
`max-height-pixels`, the maximized window state and the messages loaded from the portal.

Its states are the machine in `up-favorites.machine.ts`: `loading`, `failed.retryable`,
`failed.sessionExpired`, `empty`, and `list`, which runs two regions in parallel, `mode`
(`viewing` or `editing`) and `request` (`idle`, `removing` or `moving`; one change at a time).
Changes are pessimistic: the list changes when the portal confirms, so a failure leaves it as it
was and adds a notice. The model-based tests reach all ten state values through 41 shortest
paths. Those paths cover every combination of state, notice and order the test events can
produce, each checked with axe.

### Converting existing Favorites channels

uPortal-start's quickstart data publishes `favorites` as a Web Component channel. A portal
upgraded from 5.x still has a channel whose descriptor is the framework portlet `Favorites`, and
a channel is a Favorites channel by that descriptor, whatever its fname. Convert each one by
changing, and keeping everything else (fname, ID, title, categories, permissions, layout
placements):

* the type to `Web Component`, and the descriptor to the framework portlet `WebComponent`;
* parameters: add `webComponent.tagName=up-favorites` and `webComponent.moduleUrl=up-favorites`,
  remove `editable` (the component edits in place);
* add `webComponent.preferenceAttributes=max-height-pixels=FavoritesController.maxHeightPixels`,
  so that a `FavoritesController.maxHeightPixels` preference set on the definition or by users
  still applies. uPortal-start's definition always has it: without the preference the element
  gets no attribute and lists every favorite.

For a deployment that upgrades now, the simplest way is to import the Web Component portlet
type, then the converted definition, with uPortal-start:

```sh
./gradlew dataImport -Dfile=data/base/portlet-type/Web_Component.portlet-type.xml
./gradlew dataImport -Dfile=data/base/portlet-definition/favorites.portlet-definition.xml
```

The import finds the definition by fname and updates it in place (its ID does not change), so
layouts that place or favorite the channel keep working.

The automated conversion belongs to the 6.0 data migration (MOD-D11), with one rule per
converted portlet:

* **Import upgrader.** uPortal only upgrades an import whose data version has no importer, so
  the rule needs the 6.0 data version of `portlet-definition` files. An `XsltDataUpgrader`
  from the 5.x version then rewrites definitions with the `Favorites` framework descriptor as
  above, the same way `channel30DataUpgrader` upgrades older channel files.
* **Copy-tool rule.** The copy tool's rules map tables and columns; this conversion rewrites
  rows (`UP_PORTLET_DEF`: type and portlet name; `UP_PORTLET_DEF_PARAM`: added and removed
  parameters). It needs a new rule kind that runs after the copy, for example
  `convert-portlet descriptor=Favorites type="Web Component" add-param=... remove-param=editable`,
  and the copy tool's verification must then expect those differences.

### Deleting the Favorites portlet

The portlet stays in uPortal for now, no longer used by uPortal-start's data, so that portals
whose data still names the `Favorites` descriptor keep rendering until they convert it. Deleting
it means removing `AbstractFavoritesController`, `FavoritesController`,
`FavoritesEditController`, `properties/contexts/portlet/Favorites-portlet.xml`,
`WEB-INF/jsp/Favorites/*.jsp` and the `Favorites` entry of `WEB-INF/portlet.xml`, and the
messages only those JSPs use. `FavoritesUtils` and the options-menu script
(`modern-favorite.js`) stay: the API and the theme use them. Do it once the conversion above
ships in the 6.0 data migration, and say so in UPGRADING-6.0.

### Converting the next portlet

1. Write the REST API first (`@RestController` under `/api/v6-0/`, a service on the portal's
   existing services, MockMvc and unit tests).
2. Add `src/components/<tag>/<tag>.ts` (a subclass of `UpElement`), its machine
   (`<tag>.machine.ts`, see "State machines"), its API client and validators, its messages, and
   its tests (machine, model-based and browser); add the tag to `components` in
   `vite.config.ts`.
3. Turn portlet modes into in-place states, preferences into `webComponent.preferenceAttributes`,
   and portlet actions into REST calls.
4. Publish the channel as a Web Component in uPortal-start's data, retarget its Playwright specs,
   and document how existing definitions are converted.

## Conversion: Popular Portlets

| Portlet | Web component |
| --- | --- |
| `PopularPortletsController` (VIEW) and `jsp/PopularPortlets/listPortlets.jsp`, a DataTables table | `<up-popular-portlets>` (`uPortal-webcomponents/src/components/up-popular-portlets`) |
| The `popularPortletCounts` resource URL | `GET /api/v6-0/popular-portlets` |
| The days `<select>` (1, 7, 30, 90, 365) | The same choice; a `days` attribute sets the first one |
| DataTables sorting, column filters and paging | `table.ts`: the same order (the Title column sorts by fname), filters, page sizes and texts |
| Texts in the JSP's script | New `popular.portlets.*` keys in `Messages.properties` |

The table needs no chart, so ECharts (MOD-D34) does not apply. The machine,
`up-popular-portlets.machine.ts`, has the states `loading`, `failed.retryable`,
`failed.sessionExpired`, `empty` and `list`. Sorting, filters and paging are context, and
`table.ts` derives the rows shown from it. Choosing other days loads again from any state; the
earlier request is abandoned.

uPortal-start's `tests/ux/portlets/popular-portlets.spec.ts` was written against the portlet
first and passes unchanged against the component. It finds the controls by role, label and text.
Its last three tests are about the component.

### Converting existing Popular Portlets channels

Change, and keep everything else (fname, ID, title, categories, permissions):

* the type to `Web Component`, and the descriptor to the framework portlet `WebComponent`;
* parameters: add `webComponent.tagName=up-popular-portlets` and
  `webComponent.moduleUrl=up-popular-portlets`.

The portlet had no preferences. The import upgrader and copy-tool rule described for Favorites
apply in the same way, with the `PopularPortlets` descriptor.
