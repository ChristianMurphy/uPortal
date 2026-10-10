# Web Components in uPortal

uPortal 6 replaces its own portlets with REST APIs and web components (decisions MOD-D18 to
MOD-D22 in the [modernization log](../modernization/LOG.md)). This page describes the parts
every conversion builds on: the **Web Component channel type**, which places a custom element on
a portal page without the portlet container.

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
