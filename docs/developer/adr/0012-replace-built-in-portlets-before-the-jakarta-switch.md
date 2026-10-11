---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Replace uPortal's built-in portlets with web components or Thymeleaf pages before the Jakarta switch

## Context and Problem Statement

uPortal's own `portlet.xml` declares 37 portlets, and 26 admin flows run in Spring Web Flow's
portlet integration. They run on Spring Portlet MVC, which uPortal now keeps in its own source
(ADR 0011). Keeping that copy working through Spring 6, Spring 7 and the `jakarta` namespace would
mean porting code that Spring removed years ago. The views are 111 JSPs.

Should uPortal port its own portlets through the platform upgrade, or replace them, and with what?

## Decision Drivers

* No Spring Portlet MVC or Web Flow portlet code to port to Spring 6, Spring 7 or `jakarta`.
* Framework upgrades shouldn't wait for a full rewrite.
* Widgets inside portal pages need client-side interaction and reuse by other clients, such as
  mobile apps or uPortal-home.
* Admin tools and security-sensitive forms need CSRF protection and authentication from Spring
  Security, and work without JavaScript.
* The existing Playwright specs should keep testing the same behaviour.

## Considered Options

* Keep the copied Portlet MVC through Spring 5.3 only, and replace each portlet during P4 and P5
  with REST and web components or with Spring MVC and Thymeleaf, chosen per portlet
* Replace everything first, with no copied Portlet MVC
* Keep the copied Portlet MVC through 6.0 and replace the portlets in 6.x
* Web components for everything
* Thymeleaf pages for everything
* Servlet-mode Spring Web Flow 4 with server-side views
* Keep the JSPs

## Decision Outcome

Chosen option: "Keep the copied Portlet MVC through Spring 5.3 only, and replace each portlet
during P4 and P5 with REST and web components or with Spring MVC and Thymeleaf, chosen per
portlet", because framework upgrades can continue while the portlets are replaced, and the copied
code never has to move to Spring 6, Spring 7 or `jakarta`.

The rule for each portlet:

* **A web component with a REST API** when it is a widget inside portal pages next to other
  content, used by end users, needing client-side interaction, or reused by other clients. The
  component is a Lit custom element (ADR 0013), placed through a new web-component channel type.
* **A Spring MVC page with Thymeleaf** when it is a full-page tool or form flow: an admin console,
  a multi-step wizard, or a security-sensitive form such as login, password or account. Spring
  Security's CSRF protection and authentication apply directly, no JavaScript build is needed, and
  pages work without JavaScript. Wizards use plain controllers with session-scoped state.
  Servlet-mode Spring Web Flow 4 is allowed where a flow is complex enough to need it.

The default mapping, to be confirmed per portlet:

| Kind | Portlets |
| --- | --- |
| Thymeleaf admin console under `/uPortal/admin/` | Portlet, Groups, Permissions, User, Portal and Fragment Administration, Exit Fragment Administration, Import/Export, Cache Manager, Attribute Swapper, Fragment Audit, Statistics and Portal Activity, SQL Query, Tenant Manager, Toggle Resources Aggregation, Translator, Dynamic Respondr Skin |
| Thymeleaf full-page user flows | Login, Forgot Password, Self-Edit Account (with update and reset password), Reset My Layout |
| Web components with REST, placeable in layouts | Favorites, Portlet Marketplace, Search, Directory, Popular Portlets, User Locale, background preference, Session Timeout |
| Built-in channel types, or dropped | IFrame, Image, Error, Snooper, JspInvoker, Google Analytics (becomes skin configuration) |

The Soffit connector, itself a Spring Portlet MVC portlet today, is proposed to become a
rendering pipeline component.

Groups Manager as a Thymeleaf admin page and Favorites as a web component come first, to measure
the effort of each kind. The foundation work adds the web-component channel
type, which renders the custom element and its module script and passes it the user, locale,
window state and preferences. REST calls authenticate with the portal's existing JWT and OIDC
userinfo token. A data upgrade turns portlet definitions that reference the built-in portlets into
web-component channel definitions, both in the XML import and in the copy tool.

Thymeleaf is 3.1.5 with `thymeleaf-spring5` on Spring 5.3, then `thymeleaf-spring6` 3.1.5 after the
switch. Templates don't change at the switch. Layout dialect 4.0.1 shares the Respondr header and
footer, so admin pages keep the portal around them.

### Consequences

* Good, because the copied Portlet MVC and Web Flow portlet code is deleted before the `jakarta`
  switch, and is never ported.
* Good, because admin and account pages get Spring Security's CSRF and authentication without
  portlet-specific code.
* Good, because widgets can be reused outside the portal through their REST APIs.
* Bad, because uPortal has two UI stacks, Thymeleaf and web components.
* Bad, because 37 portlets and 26 flows have to be rewritten before P6, which puts the rewrite on
  the path to 6.0.
* Bad, because adopters who customized the JSPs of these portlets have to move their changes.

### Confirmation

The specs under `tests/ux/admin` in uPortal-start and the related portlet specs have to keep
passing against the new pages, asserting the same behaviour. Each converted portlet removes its
dependency on Spring Portlet MVC and Web Flow, and the `jakarta` switch (P6) requires that the
copied code is gone. The Favorites conversion is in progress, and the Groups Manager conversion is planned.

## Pros and Cons of the Options

### Keep the copied Portlet MVC through Spring 5.3 only, and replace each portlet during P4 and P5 with REST and web components or with Spring MVC and Thymeleaf, chosen per portlet

* Good, because each portlet gets the kind of UI that fits its use.
* Good, because the Spring 5.3 step needs only small, known changes to the copied code (37 compile
  errors in 6 files against Spring 5.3.34 and Web Flow 2.5.1).
* Bad, because the copied code still needs those Spring 5.3 changes.

### Replace everything first, with no copied Portlet MVC

* Good, because no Portlet MVC code is copied at all.
* Bad, because every framework upgrade stalls until the rewrite is done.

### Keep the copied Portlet MVC through 6.0 and replace the portlets in 6.x

* Good, because the rewrite isn't on the path to 6.0.
* Bad, because code that is about to be deleted would be ported through Spring 6, Spring 7 and
  `jakarta` first.

### Web components for everything

* Good, because uPortal would have one UI stack.
* Bad, because form-heavy admin wizards become a heavier rewrite, with more client-side security
  code and a JavaScript build for every tool.

### Thymeleaf pages for everything

* Good, because uPortal would have one server-side UI stack.
* Bad, because widgets couldn't be placed inside portal page regions without a server-fragment
  channel type.

### Servlet-mode Spring Web Flow 4 with server-side views

* Good, because the flow XML could be reused.
* Bad, because it keeps a second UI stack next to Spring MVC. It stays allowed for flows complex
  enough to need it.

### Keep the JSPs

* Good, because JSPs work on Tomcat 11.
* Bad, because they are the legacy stack this work retires, and the portlet framework behind them
  would still need porting.

## More Information

* The bundled portlet WARs from other repositories aren't affected. They keep running in Pluto
  (ADR 0011).
* [Thymeleaf with Spring](https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html)
* Prototype references: MOD-D18, MOD-D19, MOD-D26 (Spring 5.3 needs), MOD-Q9, MOD-I2, MOD-I3,
  MOD-I19, MOD-I40.
