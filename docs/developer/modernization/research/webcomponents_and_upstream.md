# uPortal non-portlet content alternatives (Soffit, web components), bundled-portlet porting effort, and upstream community/contribution state for a uPortal 6.0 (Java 17+/25, Gradle 9, Spring 7, Hibernate 7, Jakarta/Tomcat 11) modernization, as of 2026-10-10

Method note: most facts below come from metadata-only `git clone`s of the public repos, read on 2026-10-10. They include tag dates and commit logs, plus build files at `master` HEAD. Other facts come from GitHub search, the CAS repo release notes, and web search snippets. Direct fetches of apereo.org, groups.google.com, unicon.net and apereo.github.io failed in this environment (DNS/proxy), so mailing-list content is reported only from search-engine snippets, and the text says so. Several old org names now redirect to `uPortal-Project/*`: `Jasig/*`, `uPortal-contrib/*` (for portlets and web components), and the `EsupPortail` repo. Links below use the canonical `uPortal-Project` name where GitHub search confirmed it.

## Q1. What is Soffit (how it works, its stack, maintenance status, docs), and does it still depend on javax.servlet/Pluto inside uPortal?

### Takeaway
Soffit is uPortal's REST/JWT-based "portlet alternative". uPortal POSTs JWT-signed user, request, preference and definition headers to a remote Spring MVC app, and that app returns HTML. The portal-side Soffit connector, though, is itself a JSR-286 portlet built with Spring Portlet MVC (`javax.portlet`, `spring-webmvc-portlet`, Pluto). So Soffit as released today still depends on Pluto and javax. The code is stable but has not changed its design since about 2017. The samples are on Spring Boot 1.3.5 and Java 8.

### Cited Findings
- Soffit is described as "a technology for creating content that runs in Apereo uPortal… intended as an alternative to JSR-286 portlet development… not based on JSR-286 or the portlet container." The docs assume JS UIs (React/AngularJS/Backbone) and say <!-- vale ai-tells.SemicolonUsage = NO --><!-- direct quote from the Soffit docs -->"Normally a Soffit component will render one time; … state changes… are typically handled with Javascript and REST APIs."<!-- vale ai-tells.SemicolonUsage = YES --> See [docs/developer/soffits/README.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/developer/soffits/README.md).
- The doc topics are Minimal Soffit, Publishing a Soffit, Soffit Data Model, Configuration Options and Securing APIs. The README still links samples at `drewwills/soffit-samples`. See [docs/developer/soffits/](https://github.com/uPortal-Project/uPortal/tree/master/docs/developer/soffits).
- Soffit has three Gradle modules in uPortal core: `uPortal-soffit-core` (JWT model and services: `Bearer`, `Definition`, `PortalRequest`, `Preferences`, `JwtEncryptor`, `JwtSignatureAlgorithmFactory`), `uPortal-soffit-connector`, and `uPortal-soffit-renderer`. See [uPortal-soffit/](https://github.com/uPortal-Project/uPortal/tree/master/uPortal-soffit).
- **The connector is a portlet.** `SoffitConnectorController` imports `javax.portlet.PortletPreferences/RenderRequest/RenderResponse` and `org.springframework.web.portlet.bind.annotation.RenderMapping`, and is mapped to portlet modes `VIEW/EDIT/HELP`. The module includes `connector-portlet.xml` and depends on `spring-webmvc-portlet:${springVersion}`, with `pluto-container-api`, `portlet-api` and `servlet-api` as compileOnly. See [SoffitConnectorController.java](https://github.com/uPortal-Project/uPortal/blob/master/uPortal-soffit/uPortal-soffit-connector/src/main/java/org/apereo/portal/soffit/connector/SoffitConnectorController.java) and [connector build.gradle](https://github.com/uPortal-Project/uPortal/blob/master/uPortal-soffit/uPortal-soffit-connector/build.gradle).
- The renderer, the library a remote Soffit app embeds, is a plain Spring MVC servlet controller. `SoffitRendererController` is mapped to `/soffit/{module}` and uses `javax.servlet.http.*`. It depends on `spring-webmvc` and `spring-security-web` at uPortal's versions (Spring 4.3.30.RELEASE, Spring Security 4.2.20.RELEASE), with `javax.servlet-api:3.1.0` compileOnly. See [renderer build.gradle](https://github.com/uPortal-Project/uPortal/blob/master/uPortal-soffit/uPortal-soffit-renderer/build.gradle) and [uPortal gradle.properties](https://github.com/uPortal-Project/uPortal/blob/master/gradle.properties).
- The JWT library is `jjwt 0.11.5`. Renovate's PR to update to 0.13.0 (#2704) was closed unmerged on 2026-04-27. See [gradle.properties](https://github.com/uPortal-Project/uPortal/blob/master/gradle.properties) and [PR #2704](https://github.com/uPortal-Project/uPortal/pull/2704).
- Securing module APIs: `SoffitApiPreAuthenticatedProcessingFilter` (in `uPortal-soffit-renderer`) reads a `Bearer` token from the `Authorization` header. The token comes from `/uPortal/api/v5-1/userinfo`. "Any portal user (including unauthenticated users) may obtain a personal token from this endpoint… obtained with JavaScript and passed… as a Bearer token." See [securing_apis.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/developer/soffits/securing_apis.md). The endpoint is implemented by [OidcUserInfoController.java](https://github.com/uPortal-Project/uPortal/blob/master/uPortal-api/uPortal-api-rest/src/main/java/org/apereo/portal/rest/oauth/OidcUserInfoController.java).
- The shared signing key is configured through `org.apereo.portal.soffit.jwt.signatureKey`, `...signatureAlgorithm=HS512` and `...encryptionPassword` in uPortal-start `etc/portal/uPortal.properties`. The same key appears in `notification.properties`, so NotificationPortlet's REST API accepts the same JWTs. See [uPortal-start etc/portal](https://github.com/uPortal-Project/uPortal-start/tree/master/etc/portal).
- uPortal-start wires the connector into the uPortal overlay: `shell "org.jasig.portal:uPortal-soffit-connector:${uPortalVersion}"`. See [overlays/build.gradle](https://github.com/uPortal-Project/uPortal-start/blob/master/overlays/build.gradle).
- Sample apps: `soffit-samples` uses `springBootVersion = '1.3.5.RELEASE'` and `sourceCompatibility = 1.8`. Its last commit is 2017-01-30 (Drew Wills, now emeritus). The repo is `uPortal-Project/soffit-samples` (GitHub `updated_at` 2019-04-17). A Kotlin example exists at `uPortal-contrib/soffit-kotlin-example` (updated 2023-11-13). See [soffit-samples](https://github.com/uPortal-Project/soffit-samples), [soffit-kotlin-example](https://github.com/uPortal-contrib/soffit-kotlin-example) and [COMMITTERS.md (Drew Wills emeritus)](https://github.com/uPortal-Project/uPortal/blob/master/docs/COMMITTERS.md).
- The Feb 2026 maintainer analysis calls `soffit-core` and `soffit-renderer` <!-- vale ai-tells.NegatedObject = NO --><!-- direct quote from the subsystem analysis -->"already standalone with zero internal dependencies"<!-- vale ai-tells.NegatedObject = YES --> and Soffit "the blueprint for the post-portlet architecture". It lists `soffit-connector` (7 javax.portlet files) as "Bridge code, designed for separation". First move #7 is: "Expand Soffit as the bridge to uPortal 7 … Make the connector work as a servlet filter (not just a portlet) so content integration doesn't require the portlet container." See [docs/architecture/subsystem-analysis.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/architecture/subsystem-analysis.md) (added in [PR #2917](https://github.com/uPortal-Project/uPortal/pull/2917), merged 2026-03-24, status "Draft - Team Review").

### Inferences
- Choosing "Soffit instead of portlets" in uPortal 6 still needs Pluto or the portlet layer inside uPortal until the connector is reimplemented as a non-portlet rendering-pipeline component. That reimplementation is first-move #7 in the maintainers' own plan, which makes it a natural upstreamable PR in a modernization series.
- The renderer and core libraries depend on Spring 4.3, Spring Security 4.2 and `javax.servlet` coordinates. A Jakarta uPortal 6 would have to publish Jakarta-compatible `uPortal-soffit-renderer` and `-core` artifacts, which is a breaking change for any external Soffit apps. Those apps would also have to move to Spring Boot 3 or 4.
- The JWT wire format (HS512 signed claims) is framework-neutral, so existing remote Soffits would keep working across a 5→6 jump if the format is unchanged and only the connector changes.
- From background knowledge not verified in this session, Spring Framework 5.0 removed `spring-webmvc-portlet`. For that reason the portlet repos use Liferay's `portletmvc4spring` (see Q4) and uPortal pins Spring < 5.

### Gaps
- There is no usage data on how many adopters run production Soffits. Google Groups was unreachable, so uportal-dev threads about Soffit's future could not be read directly.
- I did not check whether any third-party Soffit has been built on Spring Boot 3 or Jakarta.

## Q2. What are uPortal web components (which exist, which framework, maintenance status, how they are embedded, and how they authenticate)?

### Takeaway
The main web-components monorepo, `uPortal-Project/uPortal-web-components`, has 12 packages. They are almost all Vue 2 custom elements, with one Lit 2 package. The last release was v1.40.2 on 2023-09-11 and the last commit was 2024-03-21. `notification-web-components` is also Vue 2, last released 2019-08-06. In uPortal-start the components are served as webjars from resource-server and placed on pages through SimpleContentPortlet "Advanced CMS" portlet definitions, so today they ride on the portlet stack. They authenticate by fetching a JWT from `/uPortal/api/v5-1/userinfo` and sending it as a Bearer token.

### Cited Findings
- Repo description: "A collection of reusable, standardized, and accessible web components for uPortal". It installs through npm `@uportal/*` or webjars `org.webjars.npm:uportal__*`. The components are API Template Vue, Content Carousel, Dashboard Carousel, ESCO Content Menu, Eyebrow User Info, Waffle Menu, User Profile Menu and Grid Carousel. The utilities are Open ID Connect and Portlet Registry to Array. The packages directory also has `content-renderer` and `sidebar-nav`. See [uPortal-web-components README](https://github.com/uPortal-Project/uPortal-web-components).
- Frameworks per package.json at HEAD (v1.40.2):
  - Vue 2: `api-template-vue` (vue ^2.5.21), `content-carousel` (vue ^2.5.20 + vue-class-component/vue-property-decorator/vue-slick), `content-renderer` (^2.5.17), `dashboard-carousel`, `esco-content-menu` (vue ^2.6.14, vue-i18n ^8, vue-awesome-swiper ^4), `eyebrow-user-info`, `sidebar-nav`, `user-profile-menu`, `waffle-menu` (^2.6.2).
  - Lit: `grid-carousel` (`lit ^2.0.0`).
  - No framework: `open-id-connect` (`jwt-decode ^3`).
  - Toolchain: lerna 7, `lerna-webjar`, tslint, eslint-plugin-vue 7.
  - [@uportal/ packages](https://github.com/uPortal-Project/uPortal-web-components/tree/master/@uportal)
- Activity: latest tag `v1.40.2` on 2023-09-11 (`v1.40.1` on 2023-07-12, and uPortal-start pins 1.40.1). Last commit 2024-03-21 ("fix/grid-carousel-package"). Zero commits since 2024-10-01. 67 open issues (GitHub `open_issues_count`). See [tags](https://github.com/uPortal-Project/uPortal-web-components/tags) and [repo](https://github.com/uPortal-Project/uPortal-web-components).
- `notification-web-components` (`notification-banner`, `notification-icon`, `notification-list`, `notification-modal`) use Vue `2.6.11` / `^2.6.x`. Latest tag `v1.0.4` on 2019-08-06, last commit 2022-09-16. See [notification-web-components](https://github.com/uPortal-Project/notification-web-components).
- Other web-component repos in the org, all with no substantive activity: `CardWebComponents` ("Card Web Components for the uPortal ecosystem", topic `hyperhtml`), `navigation-web-components` (Vue), and `form-builder` (React, "Create html input forms on the fly") with `fbms` (Form Builder Microservice). See [uPortal-Project org repo list](https://github.com/orgs/uPortal-Project/repositories).
- Auth: `@uportal/open-id-connect` defaults to `userInfoApiUrl = '/uPortal/api/v5-1/userinfo'` and returns `{encoded, decoded}`. `notification-icon` calls `/NotificationPortlet/api/v2/notifications` with `Authorization: 'Bearer ' + (await oidc()).encoded`. See [open-id-connect.js](https://github.com/uPortal-Project/uPortal-web-components/blob/master/@uportal/open-id-connect/src/open-id-connect.js) and [NotificationIcon.vue](https://github.com/uPortal-Project/notification-web-components/blob/master/@uportal/notification-icon/src/components/NotificationIcon.vue). The server side is `NotificationRestV2Controller`. See [NotificationPortlet](https://github.com/uPortal-Project/NotificationPortlet/blob/master/notification-portlet-webapp/src/main/java/org/jasig/portlet/notice/controller/rest/NotificationRestV2Controller.java).
- Embedding in uPortal-start: `overlays/resource-server/build.gradle` adds runtime webjars `org.webjars.npm:vue:${vueVersion}`, `uportal__api-template-vue`, `uportal__content-carousel`, `uportal__esco-content-menu`, `uportal__waffle-menu` (at `uPortalWebComponentsVersion`) and `uportal__notification-{banner,icon,list,modal}` (at `notificationWebComponentsVersion`). See [overlays/resource-server/build.gradle](https://github.com/uPortal-Project/uPortal-start/blob/master/overlays/resource-server/build.gradle).
- Placement: `waffle-menu.portlet-definition.xml` has `<type>Advanced CMS</type>`, `webAppName /SimpleContentPortlet`, `portletName cms`. Its content contains `<script src="/resource-server/webjars/uportal__waffle-menu/dist/waffle-menu.min.js" defer>` and a `<waffle-menu>` tag. `notification-icon` follows the same pattern. Both are placed in the eyebrow of `authenticated-lo.fragment-layout.xml` (`<channel fname="waffle-menu" …/>`). Other quickstart definitions follow it too: favorites-carousel, notification-list, notification-banner, notification-modal, admin-dashboard. See [waffle-menu.portlet-definition.xml](https://github.com/uPortal-Project/uPortal-start/blob/master/data/quickstart/portlet-definition/waffle-menu.portlet-definition.xml) and [authenticated-lo.fragment-layout.xml](https://github.com/uPortal-Project/uPortal-start/blob/master/data/quickstart/fragment-layout/authenticated-lo.fragment-layout.xml).
- uPortal core itself: "The `webcomponents-js` polyfill (v2.8.0) is included but **no custom elements are defined in this codebase yet**." The recommended direction is "ES6 modules as the component model (no framework dependency)" with Web Components. See [subsystem-analysis.md §Web Components / Frontend Modernization](https://github.com/uPortal-Project/uPortal/blob/master/docs/architecture/subsystem-analysis.md).
- A 2026 example inside a portlet: CalendarPortlet PR #409 (2026-05-09) replaced its Backbone/Underscore/jQuery narrow view with a `<calendar-portlet>` vanilla custom element of about 300 LOC, "loaded as an ES module… no build tooling". It keeps `upcal-*` classes so the uPortal-start Playwright `calendar.spec.ts` carries over. See [CalendarPortlet PR #409](https://github.com/uPortal-Project/CalendarPortlet/pull/409).
- The 2023 uPortal Dev Days covered "Web Components using Lit" (search snippet). See [apereo.org 2023 uPortal Dev Days](https://www.apereo.org/content/2023-uportal-dev-days).

### Inferences
- Web components are not yet a portlet-free path in uPortal. Their in-layout hosting goes through SimpleContentPortlet's `cms` portlet. Removing Pluto in a Jakarta uPortal 6 would need a native "static HTML / web-component channel type" in the rendering pipeline. That is the same extension point the subsystem analysis proposes (first moves #2 and #3: `core-api` vs `core-portlet`, `rendering-core` vs `rendering-portlet`).
- The JWT/userinfo auth model is framework-neutral and already works for cross-webapp REST (NotificationPortlet). It is the right contract to keep for replacement components.
- The web-components repos are effectively dormant: no release in 3 years, and no committer activity since early 2024. Any replacement work would effectively start a new codebase. In practice that means a framework rewrite (Vue 2 → Lit or vanilla) rather than incremental maintenance.

### Gaps
- No download statistics for the npm or webjar packages. I could not confirm which adopters use `esco-content-menu` beyond its GIP-RECIA/ESCO origin (the `GIP-RECIA/esco-content-menu` repo was not reachable).
- I did not check whether `@uportal/*` packages on npm carry npm deprecation notices.

## Q3. What are the implications of Vue 2 EOL (Dec 31, 2023)?

### Takeaway
Vue 2 has been EOL since 2023-12-31 and doesn't get fixes. uPortal-start still bundles Vue 2.7.16 as a webjar, plus 13 Vue-2-based component packages. Any "port to web components" plan should treat the existing Vue 2 components as needing a rewrite, not as a foundation.

### Cited Findings
- "Vue 2 has reached End of Life on December 31st, 2023. It no longer receives new features, updates, or fixes." The code remains available on CDNs and package managers, and new projects are told to use Vue 3. See [v2.vuejs.org/eol](https://v2.vuejs.org/eol/) and <!-- vale ai-tells.ColonUsage = NO --><!-- citation link text kept verbatim -->[Vue blog: Vue 2 EOL](https://blog.vuejs.org/posts/vue-2-eol)<!-- vale ai-tells.ColonUsage = YES -->.
- uPortal-start pins `vueVersion=2.7.16`, `uPortalWebComponentsVersion=1.40.1` and `notificationWebComponentsVersion=1.0.4`. See [gradle.properties.example](https://github.com/uPortal-Project/uPortal-start/blob/master/gradle.properties.example).
- Vue 2 dependencies are spread across 9 of the 12 `uPortal-web-components` packages and all 4 notification components. Several depend on Vue-2-only ecosystem libraries: `vue-i18n@^8`, `vue-class-component`, `vue-property-decorator`, `vue-slick`, `vue-awesome-swiper@^4`, `vue-async-computed`. See [@uportal packages](https://github.com/uPortal-Project/uPortal-web-components/tree/master/@uportal) and [notification-web-components](https://github.com/uPortal-Project/notification-web-components).
- uPortal maintainers' stated direction is "ES6 modules… no framework dependency" plus Web Components, and they recommend using as few libraries as possible. See [subsystem-analysis.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/architecture/subsystem-analysis.md).

### Inferences
- Vue 2 → 3 is not drop-in for these packages. Vue 3's `defineCustomElement` differs from Vue 2's `vue-custom-element`/wrapper approach, and vue-i18n 8 → 9 and the class-component libraries all change. Given the maintainers' stated preference, Lit or vanilla custom elements are more consistent with upstream direction than Vue 3. CalendarPortlet #409 and the Lit `grid-carousel` are the in-repo precedents.
- Security exposure: the Vue 2 runtime is client-side only. The risk is unpatched XSS-class bugs in Vue 2.7 or its ecosystem libraries plus increasing build-toolchain rot (lerna 7, tslint, eslint 7).

### Gaps
- I did not assess whether HeroDevs or other commercial "Vue 2 NES" extended support is used by any adopter.

## Q4. Per-portlet inventory: repo, latest release and date, stack, activity, existing replacements, rough porting effort

### Takeaway
Every bundled portlet except cas-proxy-test-portlet got a coordinated "2026-05 fleet release wave" from two maintainers (Benito Gonzalez, Bill Smith). All of them are still javax / Portlet 2.0. Most run Spring 4.3 (Feedback runs Spring 3.2 and SimpleContent runs Spring 5.3 with portletmvc4spring), and Hibernate versions range from 3.2 to 5.6. In April and May 2026 maintainers deliberately pinned them to Java 11 and blocked Jakarta, JUnit 5, Spring 5+ and Hibernate-major updates in Renovate. Only esup-filemanager has already left the portlet model: since April 2026 its master branch is a standalone Spring Boot 3.5 / Java 21 app, though it has no release yet. The portlets total about 60k Java LOC plus about 8k JSP LOC, excluding esup-filemanager.

### Cited Findings
- Fleet wave: uPortal-start PR #692 (bjagg, merged 2026-05-14) moved to <!-- vale ai-tells.NounString = NO --><!-- direct quote from PR #692 -->"the 2026-05 fleet release baseline"<!-- vale ai-tells.NounString = YES -->. It lists the "Tier 2 portlets" as Announcements 2.5.3, basiclti 1.5.1, Bookmarks 1.3.1, Calendar 2.7.2, Feedback 1.3.1, jasig-widget-portlets 2.4.2, NewsReader 5.1.4, SimpleContent 3.4.2 and WebProxy 2.4.1. "Tier 1" is NotificationPortlet 4.8.3. The resulting Playwright run was "99 passed / 7 failed". It also notes <!-- vale ai-tells.EmDashUsage = NO --><!-- vale ai-tells.SemicolonUsage = NO --><!-- direct quotes from PR #692 -->"`esupFilemanagerPortletVersion` (4.0.0) — not part of the wave; converted to web component + API in a prior cycle", and "`resourceServerVersion` (1.0.48) intentionally stays — legacy `ResourceServingWebapp` overlay is needed by webapps that haven't migrated."<!-- vale ai-tells.SemicolonUsage = YES --><!-- vale ai-tells.EmDashUsage = YES --> See [uPortal-start PR #692](https://github.com/uPortal-Project/uPortal-start/pull/692).
- Pruning: JasigWidgetPortlets overlay retired ([uPortal-start PR #696](https://github.com/uPortal-Project/uPortal-start/pull/696), 2026-05-10). FunctionalTestsPortlet dropped as "archived upstream" ([PR #690](https://github.com/uPortal-Project/uPortal-start/pull/690)).
- Shared parent `org.jasig.portlet:uportal-portlet-parent` (portlets use 51; 52-SNAPSHOT on `main`) sets `maven.compiler.source/target=11`, `tomcat.version 8.5.89`, `spring.version 5.3.28`, `spring-data.version 2.7.18`, `portletmvc4spring.version 5.2.0`, `portlet-api.version 2.0`. Its pins are commented as "When the Tomcat version changes in uPortal-start, update tomcat.version". See [uportal-portlet-parent pom.xml](https://github.com/uPortal-Project/uportal-portlet-parent/blob/main/pom.xml).
- Java/Jakarta freeze across portlets, April to September 2026:
  - "chore(ci): align Java matrix to 11-only" in Bookmarks, Feedback and Calendar. Calendar's version was "+ drop vestigial source/target 17". See [CalendarPortlet PR #370](https://github.com/uPortal-Project/CalendarPortlet/pull/370).
  - Calendar #406, Feedback #113, NewsReader #439, SimpleContent #555 and WebProxy #278 are titled "chore(deps): block renovate noise on jakarta-migration + retired-project deps". WebProxy #278 caps JAXB < 3.0, `pluto-taglib` < 3.0 and `com.liferay.portletmvc4spring` < 6.0, and disables JUnit 5. See [WebproxyPortlet PR #278](https://github.com/uPortal-Project/WebproxyPortlet/pull/278).
  - "disable hibernate updates to block replacement PRs" in Announcements #373, Bookmarks #153 and SimpleContent #576. See [AnnouncementsPortlet](https://github.com/uPortal-Project/AnnouncementsPortlet/pull/373).
  - SimpleContentPortlet: "fix: pin portletmvc4spring to 5.2.0 (last Portlet 2.0 compatible)". See [SimpleContentPortlet PR #550](https://github.com/uPortal-Project/SimpleContentPortlet/pull/550).
  - NotificationPortlet: "cap gradle-wrapper Renovate to < 9 (Java 17 floor)" (#686) and "cap json-path to < 3 (Java 17 floor)" (#688). See [NotificationPortlet](https://github.com/uPortal-Project/NotificationPortlet/pull/686).

Per-portlet data. Tag dates come from git tags. LOC = `src/main/**/*.java` line counts at HEAD on 2026-10-09/10. "Commits since 2025-10" includes bot commits.

| Portlet (repo) | Latest tag (date) | Stack at HEAD | Size | Activity | Existing non-portlet replacement |
|---|---|---|---|---|---|
| AnnouncementsPortlet ([repo](https://github.com/uPortal-Project/AnnouncementsPortlet)) | Announcements-2.5.3 (2026-05-04) | parent 51, Spring 4.3.30.RELEASE, Hibernate 5.6.15.Final, bundles TinyMCE + date-picker JS | 55 Java files / 7,623 LOC; 18 JSP / 1,956 LOC | 76 commits since 2025-10; human authors bjagg, Naenyn | None found |
| BookmarksPortlet ([repo](https://github.com/uPortal-Project/BookmarksPortlet)) | BookmarksPortlet-1.3.1 (2026-05-02) | parent 51, Spring 4.3.30, uportal-libs 5.17.9 | 40 / 4,155; 4 JSP / 366 | 41 since 2025-10 | None found. The uPortal "favorites" feature and favorites-carousel WC partly overlap |
| CalendarPortlet ([repo](https://github.com/uPortal-Project/CalendarPortlet)) | CalendarPortlet-2.7.4 (2026-05-09) | parent 51, Spring 4.3.30, Hibernate 3.6.10.Final, spring-ws 2.4.7, servlet-api 4.0.1, resource-server 1.5.3 | 85 / 10,225; 9 JSP / 807 | 77 since 2025-10 (most active portlet) | Partial: `<calendar-portlet>` vanilla custom element for the narrow view ([#409](https://github.com/uPortal-Project/CalendarPortlet/pull/409)) |
| FeedbackPortlet ([repo](https://github.com/uPortal-Project/FeedbackPortlet)) | FeedbackPortlet-1.3.2 (2026-05-13) | parent 51, **Spring 3.2.18.RELEASE**, **Hibernate 3.2.7.ga** | 17 / 2,627; 4 JSP / 678 | 53 since 2025-10 | None found |
| NewsReaderPortlet ([repo](https://github.com/uPortal-Project/NewsReaderPortlet)) | NewsReaderPortlet-5.1.5 (2026-05-13) | parent 51, Spring 4.3.30, Hibernate 3.6.10.Final-atlassian-4, servlet-api 3.1.0 | 52 / 6,990; 11 JSP / 1,299 | 61 since 2025-10 | None found |
| NotificationPortlet ([repo](https://github.com/uPortal-Project/NotificationPortlet)) | v4.8.4 (2026-05-05) | **Gradle** build, Spring 4.3.30, Spring Security 4.2.9 + OAuth2 2.5.2, Hibernate 5.6.15, Ehcache 3.12, jjwt 0.11.5 | 126 / 13,884 (largest); 9 JSP / 933 | 50 since 2025-10 | Yes, partially: REST API v2 already consumed by notification-web-components (Vue 2) |
| SimpleContentPortlet ([repo](https://github.com/uPortal-Project/SimpleContentPortlet)) | SimpleContentPortlet-3.4.4 (2026-06-23) | parent 51, **Spring 5.3.39** + Spring Data 2.7.18, Hibernate 5.6.15, portletmvc4spring 5.2.0, AWS SDK v2 S3 | 28 / 2,577; 8 JSP / 811 | 107 since 2025-10 (most commits) | None. It is itself the host for web components ("Advanced CMS") |
| WebproxyPortlet ([repo](https://github.com/uPortal-Project/WebproxyPortlet)) | WebProxyPortlet-2.4.1 (2026-05-03) | parent 51, Spring 4.3.30, servlet-api 2.5, portlet-mvc-util 1.1.3 | 53 / 6,414; 11 JSP / 750 | 62 since 2025-10 | None found. Gateway-SSO/proxy semantics are inherently server-side |
| basiclti-portlet ([repo](https://github.com/uPortal-Project/basiclti-portlet)) | basiclti-portlet-1.5.1 (2026-05-02). Previous 1.5.0 (2023-01-19), 1.4.2 (2013) | parent 51, **portlet-api 1.0 (JSR-168)**, pluto-taglib 1.1.7, `net.oauth` 20100527 | 16 / 1,728; 5 JSP / 404 | 33 since 2025-10 (mostly Renovate) | None found |
| cas-proxy-test-portlet ([repo](https://github.com/Jasig/cas-proxy-test-portlet)) | cas-proxy-test-portlet-1.0.2 (2018-08-22) | jasig-parent 41, Spring 4.2.5, uportal-libs 5.1.0 | 1 / 109; 2 JSP / 60 | **Dormant**: last commit 2018-08-22 (Drew Wills). Not in the 2026 wave and not listed in the uPortal-Project org search | Test or demo only |
| esup-filemanager ([repo](https://github.com/EsupPortail/esup-filemanager); also listed as [uPortal-Project/esup-filemanager](https://github.com/uPortal-Project/esup-filemanager)) | esup-filemanager-4.0.0 (2019-05-17). No newer tag | **Spring Boot 3.5.15 parent, Java 21, Thymeleaf, vanilla ES6 + Bootstrap 5** on master | 52 / 8,205; 0 JSP | 103 commits since 2025-10, ~all by Vincent Bonamy (ESUP) | **Already de-portletized on master**: "Since April 2026, esup-filemanager is now a standalone servlet application… no longer a portlet". Config is moving to `application.properties` + `drives.xml`, and uPortal group/attribute evaluators are replaced by Spring EL |
| resource-server ([repo](https://github.com/uPortal-Project/resource-server)) | resource-server-1.5.3 (2026-04-28). uPortal-start also runs legacy 1.0.48 | parent 50, springVersion 5.3.39, webapp module on Spring Boot 2.7.18 / java 1.8 | 30 / 3,840; 1 JSP | 127 since 2025-10 (most active non-core repo) | n/a. It is the static-asset/webjar server; a plain static server or CDN could replace it |

- esup-filemanager breaking changes listed in its README:
  - "no longer a portlet and cannot be deployed in a portlet container"
  - <!-- vale ai-tells.ContrastiveNegation = NO --><!-- direct quote from the esup-filemanager README -->"frontend stack… fully replaced (no jQuery, no jQuery Mobile)"<!-- vale ai-tells.ContrastiveNegation = YES -->
  - "Java 21 is required"
  - "CMIS support has been removed"
  - SMB moved to smbj
  - S3 via the AWS SDK

  See [esup-filemanager README](https://github.com/EsupPortail/esup-filemanager).
- CAS server overlay in uPortal-start: `casServerVersion=3.6.0`, `casSpringVersion=3.2.18.RELEASE`, `tomcatVersion=8.5.100`, `plutoVersion=2.1.0-M3`. See [gradle.properties.example](https://github.com/uPortal-Project/uPortal-start/blob/master/gradle.properties.example).

### Inferences
Rough porting-effort estimates below are mine, from LOC and feature scope. They are not upstream estimates.
- **Low** (days to about 1 to 2 weeks each):
  - cas-proxy-test-portlet: drop it, or replace with a tiny servlet/test page in a Jakarta world. CAS 3.6 proxy semantics are obsolete anyway.
  - basiclti-portlet: replace with an LTI launch endpoint or form-post page. Note that LTI 1.1/"Basic LTI" with OAuth1 is legacy. LTI 1.3 would be a redesign.
  - FeedbackPortlet: a small form plus storage.
  - BookmarksPortlet: CRUD on per-user data. It could merge into uPortal favorites.
- **Medium** (2 to 6 weeks):
  - SimpleContentPortlet: the CMS editor (CKEditor 4, also EOL) and preference storage. Critically, it must be replaced by a native uPortal "HTML/web-component channel" because every WC placement depends on it.
  - NewsReaderPortlet: RSS fetch, cache and sanitize must stay server-side as a REST service.
  - WebproxyPortlet: proxying, gateway SSO and content rewriting are server-side by nature, so it needs a Jakarta servlet service, not just a WC.
- **High** (1 to 3 months):
  - CalendarPortlet: 10k LOC with CalDAV, Exchange (spring-ws) and iCal adapters plus admin config.
  - AnnouncementsPortlet: topics, roles, approval workflow, TinyMCE editing.
  - NotificationPortlet: 13.9k LOC, but it already has the REST v2 API and WCs, so the work is mostly de-portletizing the webapp and moving it to Spring 6/7 and Jakarta.
- Most of the business logic in these portlets is server-side (feeds, CalDAV, proxying, LTI/OAuth, persistence). So "Port to web components" really means "split each portlet into a Jakarta Spring Boot REST service + a custom element", which is the esup-filemanager and NotificationPortlet pattern. It does not mean "rewrite in JS".
- The upstream portlet maintainers spent April to September 2026 explicitly pinning Java 11, Spring < 5 and Hibernate below the next major in the portlets. A Jakarta port of a portlet would go against current repo policy and would need prior agreement through uportal-dev or the `community` repo.

### Gaps
- No feature-level inventory or test coverage numbers per portlet were collected.
- I did not verify whether each portlet's REST endpoints (if any besides NotificationPortlet's) are JWT-secured.
- I could not reconcile PR #692's claim that esup-filemanager was "converted to web component + API" with its README, which says "standalone Spring Boot servlet app (Thymeleaf)". Its 4.0.0 portlet is still what uPortal-start deploys.

## Q5. How do uPortal-home and uportal-app-framework (AngularJS, UW-Madison MyUW) fit, and what is their status?

### Takeaway
Both are publicly unmaintained since May 2022. UW-Madison maintains them in a private repo. They are not a viable base for a 2026 modernization and are not part of uPortal-start.

### Cited Findings
- uPortal-home README: "UNMAINTAINED… In May 2022, the University of Wisconsin switched to building uPortal-home for use in MyUW from a private repository… this repository is effectively unmaintained." The last tag is `uportal-home-parent-14.0.4` (2022-03-07) and the last commit is 2022-05-03. See [uportal-home](https://github.com/uPortal-Project/uportal-home).
- uportal-app-framework README: "UNMAINTAINED… in 2022 the University of Wisconsin switched… to an internal private repository". It is described as "a front-end framework for building web applications that users experience as 'apps in the portal'". Last tag `uportal-app-framework-maven-21.0.4` (2022-03-07). See [uportal-app-framework](https://github.com/uPortal-Project/uportal-app-framework).
- Search results list both under `uPortal-Attic`, e.g. [uPortal-Attic/uportal-home releases](https://github.com/uPortal-Attic/uportal-home/releases). They do not appear in the current `uPortal-Project` org listing. See [uPortal-Project org](https://github.com/orgs/uPortal-Project/repositories).

### Inferences
- UW-Madison (MyUW) is a known adopter whose portal UI diverged into private code. Their constraints probably won't show up in public upstream discussion.
- AngularJS itself reached EOL in Dec 2021 (background knowledge, not verified here). There is nothing to port forward from these repos except ideas, such as widget/"app" metadata in the portlet registry.

### Gaps
- MyUW's current uPortal version and servlet container are unknown, because they run private repos.

## Q6. uPortal-Project current state: latest release, cadence, active maintainers, and existing uPortal 6 / Spring 5+ / Hibernate 5+ / Java 17+ / Jakarta / Tomcat 9 to 11 discussion

### Takeaway
The latest release is uPortal **5.17.9 (2026-06-24)**. After a near-dormant 2024 to 2025 (5.17.0 in Sep 2024, 5.17.1 in Apr 2025), a release burst of 5.17.2 → 5.17.9 came out from April to June 2026. Active maintenance is concentrated in about three people. Benito Gonzalez (bjagg) does most commits and nearly all merges. Bill Smith (Naenyn) and Christian Murphy (ChristianMurphy) are the others.

uPortal 6 has been discussed since at least 2022. In 2026 the work is framed as:
- uP6 = modernization;
- uP7 = drop portlets and convert to web components.

But the actual 2026 decisions are conservative:
- Java 11 became the minimum only in 5.17.2;
- Hibernate was raised just from 4.2 to 4.3 (Aug 2026);
- Renovate pins Spring < 5 and Spring Security < 5, and blocks Hibernate majors;
- AGENTS.md bans Java 12+ features;
- a Gradle 8.5 upgrade PR has been open and unmerged since Feb 2026.

### Cited Findings
- Release history from git tags:
  - 5.0.0 GA 2017-10-24, after M10 (2017-10-08) and RC1 (2017-10-19).
  - 5.x cadence: roughly monthly in 2018 to 2019, then 5.9.0 (2021-01-12), 5.10.0 (2021-06-25), 5.11.0 (2021-08-06), 5.12.0 (2022-09-12), 5.13.0 (2022-12-16), 5.14.0 (2023-01-20), 5.15.0 (2023-06-19), 5.16.0 (2023-11-10), 5.16.1 (2023-12-21).
  - **5.17.0 (2024-09-24), 5.17.1 (2025-04-18)**.
  - Then 5.17.2 (2026-04-01), 5.17.3 (2026-04-28), 5.17.4 and 5.17.5 (2026-05-04), 5.17.7 and 5.17.8 (2026-05-13), **5.17.9 (2026-06-24)**.
  - [uPortal tags](https://github.com/uPortal-Project/uPortal/tags)
- Branches: `rel-4-3-patches` (last 2018-09-24), `rel-5-6-patches` (2019-07-03), `rel-5-17-patches` (last 2026-03-16), `master`. There is no uP6 or Jakarta branch in the upstream repo. See [uPortal branches](https://github.com/uPortal-Project/uPortal/branches).
- Activity: 229 commits on master since 2024-10-01 and 157 since 2025-10-01. Non-bot authors since 2024-10 are Benito Gonzalez (88, under two name spellings), Christian Murphy (40), Bill Smith (11), 555vedant (2) and Chris Beach (1). Since 2025-10-01, 43 of 45 merge commits were by Benito Gonzalez. See [uPortal commits](https://github.com/uPortal-Project/uPortal/commits/master).
- Committer roster (12 active): apetro, bjagg, Naenyn, cbeach47, ChristianMurphy, groybal, jimhelwig, jonathanmtran, jgribonvald, loulou2u, mgillian, rmathis. Rule: "committers that have not authored a commit to any uPortal related repos for two or more years are automatically 'emeriti'". See [docs/COMMITTERS.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/COMMITTERS.md).
- The current stack on master is Gradle wrapper **6.9.4**, `.sdkmanrc` java=11.0.30-amzn, `sourceCompatibility = 11`, `springVersion=4.3.30.RELEASE`, `springSecurityVersion=4.2.20.RELEASE`, `springWebflowVersion=2.4.8.RELEASE`, `springSessionVersion=1.3.5.RELEASE`, `springfoxSwaggerVersion=2.9.2`, `hibernateVersion=4.3.11.Final`, `plutoVersion=2.1.0-M3`, `servletApiDependency=javax.servlet:javax.servlet-api:3.1.0`, `portletApiDependency=…portlet-api_2.1.0_spec:1.0`, `ehcacheVersion=2.6.11`, `jjwtVersion=0.11.5` and `bootstrapVersion=5.3.8`. See [gradle.properties](https://github.com/uPortal-Project/uPortal/blob/master/gradle.properties) and [gradle-wrapper.properties](https://github.com/uPortal-Project/uPortal/blob/master/gradle/wrapper/gradle-wrapper.properties).
- 5.17.2 CHANGES entry: "**Java 11 is now the minimum required version.** `sourceCompatibility` was bumped from `1.8` to `11`… driven by a CVE fix in a transitive dependency". 5.17.3 entry: <!-- vale ai-tells.FigurativeLands = NO --><!-- vale ai-tells.ColonUsage = NO --><!-- vale ai-tells.VerbTricolon = NO --><!-- direct quote from CHANGES.md -->"substantial frontend modernization landed in #2915: Bootstrap 3 → 5, jQuery upgraded, LESS → SCSS, and Fluid Infusion removed from the respondr skin. **Customized skins will need updating**"<!-- vale ai-tells.VerbTricolon = YES --><!-- vale ai-tells.ColonUsage = YES --><!-- vale ai-tells.FigurativeLands = YES -->. These breaking changes came out in patch releases. See [docs/CHANGES.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/CHANGES.md).
- Hibernate 4.2.21 → 4.3.11 was merged 2026-08-28 ([PR #3012](https://github.com/uPortal-Project/uPortal/pull/3012), "Refs GH-2961"). Its notes say <!-- vale ai-tells.FormalRegister = NO --><!-- direct quote from PR #3012 -->"The 4.3 line implements JPA 2.1 and is the last 4.x release before the Hibernate 5 rewrite"<!-- vale ai-tells.FormalRegister = YES -->. It rewrote `PortalDialectResolver` to the 4.3 `DialectResolver` SPI, switched `POOLED` → `POOLED_LO` "to preserve ID generation semantics", and replaced the removed `org.hibernate.jmx.StatisticsService`. See [commit fafb9b6ef](https://github.com/uPortal-Project/uPortal/commit/fafb9b6ef).
- Renovate pinning ([PR #2948](https://github.com/uPortal-Project/uPortal/pull/2948), 2026-04-27): "Renovate keeps opening major-bump PRs (Spring 5/6, Spring Security 5+, Hibernate 5+)… Each major is a coordinated migration we are not ready for". The PR adds `allowedVersions` `org.springframework:*` < 5.0 (<!-- vale ai-tells.SemicolonUsage = NO --><!-- direct quote from PR #2948 -->"Spring 5+ requires a coordinated API migration; Spring 6+ adds Jakarta EE + Java 17 on top"<!-- vale ai-tells.SemicolonUsage = YES -->) and Spring Security/OAuth < 5.0.
  Closed Renovate PRs include #2940 (hibernate-core v5 \[security]), #2941 (spring security v5 \[security]) and #2942 (springversion \[security] major). Dependabot's 2024 Hibernate 5.3.20 bump (#2836) was closed 2026-04-27. See [commit f81e7564e](https://github.com/uPortal-Project/uPortal/commit/f81e7564e) and [PR #2836](https://github.com/uPortal-Project/uPortal/pull/2836).
- AGENTS.md alignment ([PR #2947](https://github.com/uPortal-Project/uPortal/pull/2947), 2026-04-24): <!-- vale ai-tells.SemicolonUsage = NO --><!-- direct quote from PR #2947 -->"Java 11 is allowed in full, Java 12+ is off-limits; JUnit 4 remains the test stack"<!-- vale ai-tells.SemicolonUsage = YES -->. uPortal-start mirrored this in [PR #706](https://github.com/uPortal-Project/uPortal-start/pull/706) ("docs: add AGENTS.md and target Java 11 in uPortal-start", 2026-06-17).
- Open modernization PRs:
  - [uPortal #2921](https://github.com/uPortal-Project/uPortal/pull/2921), "build!: upgrade Gradle 6.9.4 to 8.5", by ChristianMurphy. Opened 2026-02-16, still open, 2 comments. Its rationale: "pins Gradle 6.9.4, which blocks the Spring 5/6 migration". Changes: drop jcenter, `compile` → `implementation`, `maven` → `maven-publish`, replace the `org.openrepose` JAXB plugin with Ant XJC, nebula.lint 20.6.2, errorprone 5.1.0 and SpotBugs 6.0.28. Verified "1052 tests, 973 passed, 79 skipped". It lists remaining Gradle-9 blockers: `JavaPluginConvention`, `annotationProcessorGeneratedSourcesDirectory`, and `net.researchgate.release` 3.1.0's use of `StartParameter.settingsFile`.
  - Companion [uPortal-start #681](https://github.com/uPortal-Project/uPortal-start/pull/681), still open.
  - [uPortal #2988](https://github.com/uPortal-Project/uPortal/pull/2988), "replace sherter google-java-format plugin with Spotless", opened 2026-05-24, still open.
- Subsystem analysis (bjagg, 2026-02-14, merged 2026-03-24, "Draft - Team Review"). Purpose: "modularization targeting uPortal 6 (modernization) and uPortal 7 (portlet removal, cloud deployment)". Key numbers: 39 Gradle submodules, `uPortal-utils-core` "aggregates 80+ external dependencies", "Portlet APIs are embedded in ~180 files (~21,000 LOC)", "~61" files import `org.apache.pluto`, and "only 4 of its 14 primary \[rendering] stages are portlet-specific". It says the frontend "is the most urgent modernization target alongside the Spring upgrade". It orders the first moves as follows:
  1. decompose utils-core
  2. split core-api/core-portlet
  3. extract rendering from the portlet container
  4. replace the `GroupService` singleton (129 call sites)
  5. extract persistence commons and "Adopt Liquibase or Flyway"
  6. modernize frontend
  7. expand Soffit

  See [subsystem-analysis.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/architecture/subsystem-analysis.md).
- Published roadmap table (last substantive edit "Update roadmap to generalized multiyear format", Jim Helwig, 2025-03-11). See [roadmap.md](https://github.com/uPortal-Project/uportal-project.github.io/blob/master/roadmap.md).
  - 2025 (uPv6): "Modernize Rendered Pages", "Convert External Portlets to WCs", "Add CI Tools (i.e. Accessibility testing)", "Java 21/Maven/Tomcat Upgrade".
  - 2026 (uPv7): "Revamp Rendering Pipeline", "Convert Internal Portlets to WCs", Dropped: "Drop Portlet Support".
  - 2027 (uPv8): "Modularize Core", "Adopt Latest Java LTS".
  - 2028 (uPv9): "Replace Gradle".
- uportal-user thread "uPortal 6 Update for April 11" (April 2025, Benito Gonzalez). These are search-engine summaries only. I could not read the thread directly. See [Google Groups thread](https://groups.google.com/a/apereo.org/g/uportal-user/c/d9ktfsL_f6E).
  - Initial uP6 targets: "Update uPortal and community portlets to use Java 21. Upgrade Gradle to v8. Upgrade Tomcat to v11." Spring updates were held back by portlet support.
  - Revised targets after feedback: "Update uPortal and community portlets to use Java 17", with Tomcat pulled back to 9 because "by pulling back to Tomcat 9 we do not have to worry about the change from 'javax' to 'jakarta' packages".
  - A maintainer said the portlet library doesn't support Jakarta names and that "the next major release of uPortal (v7) will no longer support portlets. uPortal v6 will support portlets."
  - An earlier post said uP6 effort was "to make sure that the code compiles under Java 21".
- Earlier roadmap context: a Unicon "Q2 2022 uPortal Open Source Support Briefing" listed "converting portlets to web components… Java 11/17, upgrading to Spring 5". It said some enhancements "will be held until uPortal v6". This comes from a search snippet, because the direct fetch failed. See [Unicon briefing](https://www.unicon.net/insights/blogs/q2-2022-uportal-open-source-support-briefing).
- uPortal-start Dependency Dashboard lists an update "org.apache.tomcat:tomcat to v11" (search snippet). See [uPortal-start #440](https://github.com/uPortal-Project/uPortal-start/issues/440).
- New coordination home: `uPortal-Project/community` (created 2026-07-31). It is for <!-- vale ai-tells.EmDashUsage = NO --><!-- direct quotes from the community README -->"Discussions — announcements, design proposals / RFCs, Q&A" and "Cross-repo tracking issues — umbrella/epic issues for initiatives that span multiple repos (release waves, the portlet-removal campaign, the web-components migration)"<!-- vale ai-tells.EmDashUsage = YES -->. It notes the "uPortal Roadmap org Project". "The project moved its planning and knowledge base off Confluence and Jira to GitHub." Issue types are Task/Bug/Feature, and governance is a private `uPortal-steering-cmte`. See [community README](https://github.com/uPortal-Project/community).
- Platform EOL context:
  - Apache Pluto, uPortal's portlet container, carries a "this project has retired" notice. Its last release was 3.1.2 (announced 2023-12-21), which is a Portlet 3.0 container (JSR 362, still `javax.portlet`). uPortal is on Pluto 2.1.0-M3. See [portals.apache.org/pluto](https://portals.apache.org/pluto/) and [Pluto news](https://portals.apache.org/pluto/news.html).
  - Tomcat 8.5 (uPortal-start default 8.5.100) reached end of support on 2024-03-31. See [tomcat-85-eol](https://tomcat.apache.org/tomcat-85-eol.html).
  - Tomcat 9.0.x support ends 2027-03-31 (announced 2026-02-11). A new 9.1.x branch will get "releases… until 31 December 2030". See [tomcat-9.0.x-eos](https://tomcat.apache.org/tomcat-9.0.x-eos.html).
  - Spring Framework 7.0 (GA Nov 2025) keeps a JDK 17 baseline and recommends JDK 25. It moves to Jakarta EE 11 with Tomcat 11 and Hibernate ORM 7 as minimums. See [Spring 7.0.0-M1 blog](https://spring.io/blog/2025/01/23/spring-framework-7-0-0-M1-available-now) and [InfoQ Nov 2025](https://www.infoq.com/news/2025/11/spring-7-spring-boot-4).

### Inferences
- The upstream "uP6" definition has drifted. The 2025 roadmap said Java 21/Tomcat. The April 2025 thread revised that to Java 17 + Tomcat 9 with javax kept. The 2026 practice is Java 11 + Spring 4.3 + Hibernate 4.3 on 5.17.x. A Spring 7 / Hibernate 7 / Jakarta / Tomcat 11 proposal is effectively a combined "uP6 + uP7". Under the maintainers' own framing it implies dropping or bridging portlets. Expect it to need an explicit RFC in `community` Discussions plus a uportal-dev thread before large PRs are reviewed.
- A javax-preserving intermediate step is possible. Tomcat 9.1.x is supported to 2030-12-31, and javax runs on Spring 5.3 + portletmvc4spring 5.2. That could be a "uP6" release that keeps portlets, with a separate Jakarta major later. In that release, uP6 means Java 17, Gradle 8/9, Spring 5.3, Hibernate 5.6 and Tomcat 9.x. This matches the revised April 2025 targets. Spring 5.3 OSS support ended 2024-08-31, though (third-party wiki only, unverified).
- Review bandwidth is the main upstream risk. One person performs about 95% of merges, and a well-documented Gradle PR from a committer has been open for about 8 months. Each PR in a series should be small and independently mergeable, and it should pass CI with uPortal-start Playwright.
- Gradle 9 conflicts with the roadmap's 2028 "Replace Gradle" item and the 2025 "Maven" mention. Raise it early so the build work isn't rejected on direction.

### Gaps
- I could not read the uportal-dev or uportal-user archives directly (groups.google.com unreachable). Any 2025-H2/2026 threads, votes or steering-committee decisions on uP6 scope are unverified.
- I could not list Discussions or the "uPortal Roadmap" org Project in `uPortal-Project/community`. The API was restricted to forks in this session.
- I could not read issue GH-2961 (Hibernate) or the comments on PR #2921 for the same reason.
- I didn't find any Open Apereo 2024/2025/2026 uPortal session listings. Searches only returned 2017 to 2023 material.

## Q7. Contribution process, release mechanics, uPortal-start versioning, and how prior majors (4 → 5) were managed

### Takeaway
To contribute, sign the Apereo ICLA and open a GitHub issue (Jira is retired). Then branch as `GH-<n>`, post intent on uportal-dev, and send a rebased PR. The PR follows the template checklist: tests, `CHANGES.md` for config/DDL, WCAG 2.0 AA, google-java-format. Governance is Apache-style lazy consensus among committers.

Releases use the Gradle `net.researchgate.release` plugin, legacy `uploadArchives`, and a manual push to Sonatype's Central Publisher Portal (OSSRH was sunset June 2025). uPortal-start has no tags or releases. Adopters fork master. The 4 → 5 major was done with milestone and RC tags, `rel-*-patches` maintenance branches, a new adoption harness (uPortal-start), and XML data export/import as the migration path.

### Cited Findings
- CONTRIBUTING: "Apereo requires that contributions be through Individual Contributor License Agreements" ([ICLA form](https://www.apereo.org/licensing/agreements/icla)). Process steps:
  - "Your potential bugfix or improvement… should be represented by a GitHub issue";
  - "post on uportal-dev@ signaling your intentions";
  - branch `git checkout -b GH-42 master`;
  - AOSP Java style checked via `./gradlew verGJF`, formatted via `./gradlew goJF`;
  - "use rebasing rather than merging";
  - "Name the Pull Request prefixed with the issue identifier";
  - run `./gradlew check`;
  - user-facing changes must not regress supported browsers.
  - [docs/CONTRIBUTING.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md)
- PR template checklist: ICLA signed, commit guidelines, tests, docs, new keys in `security.properties`/`portal.properties`, "any changes that impact configuration or the database DDL added to `CHANGES.md`", message properties and "view conforms with WCAG 2.0 AA". See [.github/PULL_REQUEST_TEMPLATE.md](https://github.com/uPortal-Project/uPortal/blob/master/.github/PULL_REQUEST_TEMPLATE.md).
- Observed commit style is conventional commits (`feat:`, `fix:`, `chore:`, `build!:`, `refactor(build)!:`). Maintainer PR bodies use a "Problem / Goal / Changes" layout, e.g. PRs #2948, #3012 and #2921. See [uPortal commits](https://github.com/uPortal-Project/uPortal/commits/master).
- Governance: "We adopt the rules and culture of the Apache Software Foundation… Committers form the 'PMC'… the go-to list is public `uportal-dev@apereo.org`". It references lazy consensus and voting, and has a steering-committee fail-safe. See [docs/COMMITTERS.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/COMMITTERS.md).
- Issue tracking moved from Jira and Confluence to GitHub. Issues go in code repos. Epics and roadmap go in `community` plus the org Project, and RFCs go in `community` Discussions. See [community README](https://github.com/uPortal-Project/community).
- Releasing uPortal: "Sonatype Account at Central Publisher Portal… The legacy OSSRH service (oss.sonatype.org) was sunset June 2025." The namespace is `org.jasig.portal`. Steps:
  - publish the GPG key on `keys.openpgp.org`;
  - "email a notice to `uportal-dev` a couple of working days prior";
  - run `./gradlew clean release --no-parallel`;
  - because uPortal "uses Gradle's legacy `uploadArchives`", the staging repo is not auto-closed, so you must `curl -X POST https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/org.jasig.portal` from the same IP, then publish in the portal UI.
  - Releases are smoke-tested by pointing uPortal-start at the SNAPSHOT (`./gradlew clean portalInit`). There is no Gradle NOTICE or license-header automation, so the review is manual.
  - [docs/developer/other/RELEASE.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/developer/other/RELEASE.md)
- A preflight-and-cut script `release-uportal.sh` was added in [PR #2986](https://github.com/uPortal-Project/uPortal/pull/2986) (merged 2026-06-24). Maven-built portlets follow the separate [maven-release-process.md](https://github.com/uPortal-Project/uportal-project.github.io/blob/master/manuals/en/uportal5-manual/developer/maven-release-process.md), which each portlet's own `release-portlet.sh` also references (e.g. FeedbackPortlet #119, NewsReaderPortlet #443).
- uPortal-start: "uPortal-start is the mechanism through which individuals and institutions adopt Apereo uPortal… **uPortal-start is new for uPortal 5.0**." It has no git tags. `gradle.properties.example` has `version=5.0.0-SNAPSHOT` and pins `uPortalVersion=5.17.9`. Dependency bumps arrive as PRs such as [#708](https://github.com/uPortal-Project/uPortal-start/pull/708) "bump uPortal to 5.17.9". See [uPortal-start README](https://github.com/uPortal-Project/uPortal-start) and [gradle.properties.example](https://github.com/uPortal-Project/uPortal-start/blob/master/gradle.properties.example).
- Prior majors:
  - uPortal 5.0.0 went through milestones (`v5.0.0-M10` 2017-10-08), `v5.0.0-RC1` (2017-10-19) and GA `v5.0.0` (2017-10-24).
  - 4.3 kept a `rel-4-3-patches` branch, active to 2018-09-24. 5.x patch lines use `rel-5-6-patches` and `rel-5-17-patches`. See [tags](https://github.com/uPortal-Project/uPortal/tags) and [branches](https://github.com/uPortal-Project/uPortal/branches).
  - Data migration tooling is the Import/Export framework (`dataExport`, `dataImport`, `dataInit`, `dataList`, `dataDelete`), with a separate "Import Single File (uPortal 4.3+)" path. See [docs/import_export/README.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/import_export/README.md).
  - HISTORY's compatibility tables list 5.x as Servlet 3.1, "Tomcat 8 or 8.5", and Java 8 ("9: ?"). The tables are stale relative to 5.17.2's Java 11 floor. See [docs/HISTORY.md](https://github.com/uPortal-Project/uPortal/blob/master/docs/HISTORY.md).
- Unicon has a "How to Upgrade uPortal Versions" article (search result only, not read). See [unicon.net](https://www.unicon.net/insights/articles/how-to-upgrade-uportal-versions).

### Inferences
- For a uP6 series, the 4 → 5 precedent suggests:
  - cut `rel-5-17-patches` as the maintenance line (it already exists);
  - land breaking work on `master` under `6.0.0-M*`/`-RC*` tags;
  - add a "Next Release" section in `CHANGES.md` per PR;
  - give uPortal-start a parallel branch, or a `6.x` branch while `master` stays on 5.17 for adopters who fork it.
- The release mechanics are themselves a modernization dependency. Gradle 7+ removed `uploadArchives`, and PR #2921 replaces it with `maven-publish`. The Central Portal flow, which currently relies on the manual OSSRH-staging-API workaround, should be re-validated as part of the Gradle PR. A Gradle publishing plugin native to the Central Portal is another option, but none is chosen upstream.
- Hibernate 4 → 7 crosses ID-generator semantics: #3012 already had to switch `POOLED` → `POOLED_LO`. Schema and data compatibility probably needs to be proven via `dataExport` from 5.17 → `dataImport` into 6.0, in line with the analysis's "Adopt Liquibase or Flyway" suggestion.

### Gaps
- I didn't find a written policy on the number of required approvals or on branch protection. Observed practice: almost all uPortal merges are done by bjagg, and PR #2921 has had no quick review. In resource-server, Bill Smith also merges PRs (e.g. resource-server PR #373, 2026-10-09).
- I did not find or verify a CCLA requirement for institution-employed contributors. Apereo licensing pages were unreachable.
- I did not find a formal uPortal-start versioning or release policy beyond "track master".

## Q8. Apereo CAS's Jakarta / Spring 6 migration (CAS 7) and Spring Boot 4 / JDK 25 (CAS 8) as an in-Apereo precedent

### Takeaway
CAS staged its Jakarta move inside one long major-release-candidate train (7.0.0-RC1 Oct 2022 → GA Dec 2023):
- it raised the JDK to 17 first;
- it finished "Spring Boot 3.0.0 and Jakarta APIs" by RC3;
- it moved the JDK baseline to 21 late (RC8);
- it required Tomcat 10.1+.

CAS 8.0 (RC1 Jan 2026 → GA 2026-07-17) repeated the pattern for Spring Boot 4.x / JDK 25 / Gradle 9.6.1. It is direct prior art for the exact target stack.

### Cited Findings
- CAS tag dates are v7.0.0-RC1 2022-10-07, RC3 2022-12-25, RC8 2023-10-06, **v7.0.0 GA 2023-12-29**, v7.3.0 2025-10-02, v8.0.0-RC1 2026-01-16 and **v8.0.0 GA 2026-07-17**. See [apereo/cas tags](https://github.com/apereo/cas/tags).
- CAS 7.0 RC1/RC2: "The JDK baseline requirement… **MUST** be JDK `17`." RC2: "The migration of the entire codebase to Spring Boot 3 and Jakarta APIs is ongoing… waiting for the wider ecosystem of supporting frameworks and libraries to catch up". See [7.0.x RC2.md](https://github.com/apereo/cas/blob/7.0.x/docs/cas-server-documentation/release_notes/RC2.md).
- CAS 7.0 RC3: <!-- vale ai-tells.VerbTricolon = NO --><!-- direct quote from the CAS release notes -->"The migration of the entire codebase to Spring Boot `3.0.0` and Jakarta APIs is now complete… affects almost every CAS module… Spring Data, Spring Security, Spring Cloud, Spring Shell, Pac4j…"<!-- vale ai-tells.VerbTricolon = YES -->. "CAS has now switched to support Jakarta EE 10 and Servlet specification `6.0.0`… Apache Tomcat… minimum supported version is now required to be `10.1.x`". See [7.0.x RC3.md](https://github.com/apereo/cas/blob/7.0.x/docs/cas-server-documentation/release_notes/RC3.md).
- CAS 7.0 RC6/RC7: CI builds against JDK 21 EA, and "it is likely that CAS v7 would switch to using JDK 21 as its baseline". RC8: "JDK baseline… **MUST** be JDK `21`". See [7.0.x RC8.md](https://github.com/apereo/cas/blob/7.0.x/docs/cas-server-documentation/release_notes/RC8.md).
- CAS 7.2 was announced as "migrated to Spring Boot 3.4" (search snippet). See [apereo.org CAS 7.2 announcement](https://www.apereo.org/news/2025/apereo-cas-72-release-announcement).
- CAS 8.0 RC1: "JDK baseline… **MUST be JDK `25`**", "CAS is now built with Spring Boot `4.0.x`", "Support for Undertow… has been dropped… until Undertow adds support for Servlet `6.1`", and Google Cloud integrations are "not yet fully compatible with Spring Boot 4". RC2/RC3: "Spring Boot `4.1.x`", plus JSpecify and NullAway adoption. The 8.0.x build has `sourceCompatibility=25` and a Gradle wrapper of `gradle-9.6.1`. See [8.0.x RC1.md](https://github.com/apereo/cas/blob/8.0.x/docs/cas-server-documentation/release_notes/RC1.md), [8.0.x gradle.properties](https://github.com/apereo/cas/blob/8.0.x/gradle.properties) and [8.0.x gradle-wrapper.properties](https://github.com/apereo/cas/blob/8.0.x/gradle/wrapper/gradle-wrapper.properties).
- uPortal-start still bundles CAS server **3.6.0** (with Spring 3.2.18) as its local SSO overlay. See [gradle.properties.example](https://github.com/uPortal-Project/uPortal-start/blob/master/gradle.properties.example).

### Inferences
- The transferable pattern is:
  - one major version with many RCs;
  - raise the JDK floor early (17) and finalize late (21 or 25);
  - do the javax → jakarta + Spring Boot major as one atomic step once dependencies are ready;
  - publish explicit "minimum container" statements, and drop unsupported integrations rather than block the release.
- CAS had a large full-time maintainer, so uPortal's bandwidth is far smaller. CAS's "RC train" model maps well to `6.0.0-M*`/`RC*` tags plus adopter testing via uPortal-start.
- The bundled CAS 3.6.0 overlay cannot run on Tomcat 10/11. A Jakarta uPortal-start needs a separate CAS 7.x/8.x overlay (Spring Boot, Java 21/25) or a mock or alternative IdP for local SSO. That is a separate, sizable PR.

### Gaps
- No public write-up was found on how CAS handled adopters stuck on Tomcat 9 during 7.0, beyond the release-note statements.

## Q9. Downstream adopters whose deployment constraints matter (e.g., still on Tomcat 8.5/9)

### Takeaway
Public evidence on adopters' current runtimes is thin. The reference harness itself defaults to Tomcat 8.5.100, which has been EOL since March 2024, and Java 11. The portlet parent pins Tomcat 8.5.89 APIs. Named active institutions in 2026 activity are:
- ESUP-Portail (France; esup-filemanager, now Java 21 / Spring Boot 3.5);
- GIP-RECIA / ESCO (France; esco-content-menu; committer jgribonvald);
- UW-Madison (MyUW, private fork).

The April 2025 uP6 thread suggests adopter feedback pushed the Tomcat target from 11 back to 9 to avoid javax → jakarta.

### Cited Findings
- uPortal-start: `tomcatVersion=8.5.100` (comment: "Dependencies driven by Tomcat version"). The README requires "A Java Development Kit (JDK), version 11". See [gradle.properties.example](https://github.com/uPortal-Project/uPortal-start/blob/master/gradle.properties.example) and [README](https://github.com/uPortal-Project/uPortal-start).
- uportal-portlet-parent: `tomcat.version 8.5.89`, commented "When uPortal-start's Tomcat version bumps, update tomcat.version". See [pom.xml](https://github.com/uPortal-Project/uportal-portlet-parent/blob/main/pom.xml).
- Tomcat 8.5 support ended 2024-03-31. Tomcat's security page says vulnerabilities reported afterwards "will not be listed or fixed" (search snippet). See [tomcat-85-eol](https://tomcat.apache.org/tomcat-85-eol.html) and [security-8](https://tomcat.apache.org/security-8).
- April 2025 uP6 thread (search snippet): targets revised from Tomcat 11 to Tomcat 9 "since by pulling back to Tomcat 9 we do not have to worry about the change from 'javax' to 'jarkarta' packages" \[sic]. See [uportal-user thread](https://groups.google.com/a/apereo.org/g/uportal-user/c/d9ktfsL_f6E).
- ESUP: esup-filemanager is maintained by Vincent Bonamy (ESUP-Portail) and moved to Java 21 / Spring Boot 3.5 in 2026. See [esup-filemanager](https://github.com/EsupPortail/esup-filemanager).
- UW-Madison builds uPortal-home and app-framework from private repos (`git.doit.wisc.edu/wps/myuw-service/...`). See [uportal-home README](https://github.com/uPortal-Project/uportal-home).
- Apereo maintains a wiki space of "institutional usage profiles" (search snippet, listing not readable). See [Apereo Confluence UPCS](https://apereo.atlassian.net/wiki/spaces/browsespace.action?key=UPCS).

### Inferences
- Adopters customize skins, data and portlets through forked uPortal-start. A Jakarta uPortal 6 breaks every custom portlet WAR they run unless a portlet bridge exists. That is the strongest argument for either:
  - a javax-preserving uP6 (Tomcat 9.0 → 9.1.x, supported to 2030), with Jakarta deferred to uP7 alongside portlet removal; or
  - a Jakarta uP6 that comes with a documented "custom-portlet → Soffit or REST+WC" migration guide and a native WC channel type.
- The 5.17.3 skin-breaking change (Bootstrap 5, SCSS) came out as a patch release. Adopters tolerated it, which suggests the active adopter base is small and close to the maintainers.

### Gaps
- No current, citable list of production adopters, or of their Tomcat or Java versions, was found. The Apereo adopter wiki and mailing lists were not readable here. A direct poll on uportal-user or `community` Discussions would be the way to get this.
- Whether Tomcat's Jakarta migration tool (or the `webapps-javaee` auto-conversion directory in Tomcat 10+) can run Pluto-based portlet WARs with cross-context dispatch was not researched. It is a possible bridge for keeping portlet WARs on Tomcat 10/11 and needs a spike.
