# Upgrading to uPortal 6.0

uPortal 6.0 moves the persistence, Spring and servlet stacks forward in steps. This page lists
what adopters have to check or change when they upgrade an existing portal. Sections are added
as the steps land.

## Database servers

uPortal 6.0 runs on, and is tuned for, current database servers:

| Database | Minimum version |
| --- | --- |
| PostgreSQL | 18 |
| MySQL | 9.7 LTS |
| MariaDB | 12.3 LTS |
| SQL Server | 2025 |
| Oracle | 23.26 |
| HSQLDB | 2.7.4, for development only |

These are hard minimums. uPortal 6.0 is not tested on older servers and has no workarounds for
them. It does not check the server version, so it may start on an older server, but it fails
wherever it uses a feature that server lacks.

If your 5.17 database runs on an older server, use it as a migration source. The copy tool
(`uPortal-db-migrate`, run with `./gradlew dbMigrate` in uPortal-start) copies 5.17 data from it
into a new server of any supported vendor and checks every table after the copy.

Before it writes a row, the copy tool also checks that the target can store your text. On SQL
Server's default collation, `varchar` columns keep only windows-1252, so emoji or CJK text in
group names, portlet titles, layout names or user attributes doesn't fit. The tool then stops
and reports each such column, the number of rows and the key of one of them.

## uPortal classes moved out of Spring's packages

A few uPortal classes used to live in Spring Framework's own packages. They now live under
`org.apereo.portal.spring`, with the rest of the name unchanged. If a Spring context or Java
code in your overlay names one of them, use the new name:

| Old name | New name |
| --- | --- |
| `org.springframework.context.support.PortalPropertySourcesPlaceholderConfigurer` | `org.apereo.portal.spring.context.support.PortalPropertySourcesPlaceholderConfigurer` |
| `org.springframework.web.client.interceptors.BasicAuthInterceptor` | `org.apereo.portal.spring.web.client.interceptors.BasicAuthInterceptor` |
| `org.springframework.web.client.interceptors.ZeroLeggedOAuthInterceptor` | `org.apereo.portal.spring.web.client.interceptors.ZeroLeggedOAuthInterceptor` |
| `org.springframework.web.client.interceptors.Headers` | `org.apereo.portal.spring.web.client.interceptors.Headers` |

Bean ids, such as `primaryPropertyPlaceholderConfigurer`, are unchanged. The classes log under
their new names.

## Spring Portlet MVC is now part of uPortal

Spring Framework 5 dropped Spring Portlet MVC, and Spring Web Flow 2.5 dropped its portlet
support. uPortal's own portlets use both, so uPortal now keeps the parts it uses in a module of
its own, `uPortal-spring-portlet`. The code is copied from `spring-webmvc-portlet`
4.3.30.RELEASE and from the portlet packages of `spring-webflow` 2.4.8.RELEASE, with only the
package names changed. The `spring-webmvc-portlet` jar is no longer in the uPortal web
application.

This affects what runs inside the uPortal web application: `WEB-INF/portlet.xml`, the portlet
Spring contexts in `properties/contexts/portlet`, and Java code in your uPortal overlay. Replace
the package prefixes as follows:

| Old package prefix | New package prefix |
| --- | --- |
| `org.springframework.web.portlet` | `org.apereo.portal.spring.web.portlet` |
| `org.springframework.webflow.mvc.portlet` | `org.apereo.portal.spring.webflow.mvc.portlet` |
| `org.springframework.webflow.context.portlet` | `org.apereo.portal.spring.webflow.context.portlet` |

With these prefixes, `org.springframework.web.portlet.DispatcherPortlet` becomes
`org.apereo.portal.spring.web.portlet.DispatcherPortlet`, and
`org.springframework.webflow.mvc.portlet.FlowHandlerAdapter` becomes
`org.apereo.portal.spring.webflow.mvc.portlet.FlowHandlerAdapter`. uPortal's two
`DispatcherPortlet` subclasses moved into the same package:

| Old name | New name |
| --- | --- |
| `org.springframework.web.portlet.ForwardingDispatcherPortlet` | `org.apereo.portal.spring.web.portlet.ForwardingDispatcherPortlet` |
| `org.springframework.web.portlet.HeaderHandlingDispatcherPortlet` | `org.apereo.portal.spring.web.portlet.HeaderHandlingDispatcherPortlet` |

Only the classes uPortal uses were copied. These classes of `spring-webmvc-portlet` have no
counterpart: `PortletRequestUtils`, `StaticPortletApplicationContext`,
`ParameterHandlerMapping`, `ParameterMappingInterceptor`,
`PortletModeParameterHandlerMapping`, `PortletModeParameterLookupKey`,
`SimpleMappingExceptionResolver`, `SimplePortletHandlerAdapter`, `SimplePortletPostProcessor`,
`UserRoleAuthorizationInterceptor`, `CommonsPortletMultipartResolver`,
`DefaultMultipartActionRequest`, `ParameterizableViewController`,
`PortletModeNameViewController` and `PortletWrappingController`. Web Flow's
`PortletMvcViewFactory` has no counterpart either.

The copied classes log under their new package names. If your logging configuration sets
levels for the old packages, change it to the new ones. This includes the "page not found"
category, now `org.apereo.portal.spring.web.portlet.PageNotFound`.

Portlet applications deployed as separate web applications (Announcements, Calendar and the
other portlet WARs) are not affected: they bring their own Spring jars.

## Spring XML files use the unversioned schemas

uPortal's Spring XML contexts and Web Flow definitions now refer to the unversioned schemas, such
as `http://www.springframework.org/schema/beans/spring-beans.xsd` instead of
`spring-beans-3.1.xsd`, and `spring-security.xsd` instead of `spring-security-4.2.xsd`. Spring
resolves an unversioned schema to the one that matches the jar on the classpath. The files keep
working as the Spring version moves.

If your overlay adds or replaces Spring contexts, make the same change there. It is required for
files that use the `security` namespace, because Spring Security 5 refuses to load a file that
refers to `spring-security-4.2.xsd` or an older version. For the other namespaces it is
recommended. Spring still maps the old versioned names to the current schema.

## The view-rendering servlet moved into uPortal

`WEB-INF/web.xml` declares the servlet that renders the views of uPortal's own portlets. Spring
Framework 5 removed its class, so uPortal now has its own copy:

| Old name | New name |
| --- | --- |
| `org.springframework.web.servlet.ViewRendererServlet` | `org.apereo.portal.spring.web.servlet.ViewRendererServlet` |

If your overlay replaces `web.xml`, change the `servlet-class` of the `ViewRendererServlet`
servlet. Keep the servlet name and its mapping (`/WEB-INF/servlet/view`): uPortal's portlets
forward to that path.

## Servlet controllers are mapped by RequestMappingHandlerMapping

The servlet contexts for login and logout (`authenticationServletContext.xml`), page rendering
(`renderingServletContext.xml`, `jsonRenderingServletContext.xml`) and the `/s/*` redirection
service (`redirectionServletContext.xml`) now map their controllers with Spring MVC's
`RequestMappingHandlerMapping` and `RequestMappingHandlerAdapter`. Spring Framework 5 removed the
`DefaultAnnotationHandlerMapping` and `AnnotationMethodHandlerAdapter` they used before. URLs are
unchanged.

One answer changes. A GET for a path under `/api/` that no controller maps now answers 404 Not
Found. uPortal 5.17 answered 405 Method Not Allowed with `Allow: POST`, because the `/api`
dispatcher also loaded the page-rendering mapping, whose catch-all accepted POST. A client that
took a 405 as a sign that an API exists should expect a 404 for paths that never existed.

If your overlay replaces one of these files, make the same change. The old mapping's
`useDefaultSuffixPattern` property is called `useSuffixPatternMatch` on the new one. If you added
your own `@Controller` to one of these servlets and it reads
`HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE`, note that the new mapping sets it to the
whole path within the servlet, not just the part the pattern's wildcards matched.

## The `/resources/*` servlet is gone

`web.xml` no longer declares Spring Web Flow's `org.springframework.js.resource.ResourceServlet`
on `/resources/*`. It served static files packaged in jars such as `spring-js`, nothing in uPortal
requested them, and Web Flow 2.5 removed the class. If your overlay links to files under
`/uPortal/resources/`, serve them from the web application instead.

## Spring Framework 5.3

uPortal now runs on these versions:

| Library | uPortal 5.17 | uPortal 6.0 |
| --- | --- | --- |
| Spring Framework | 4.3.30 | 5.3.39 |
| Spring Security | 4.2.20 | 5.8.16 |
| Spring Web Flow | 2.4.8 | 2.5.1 |
| Spring Session | 1.3.5 | 2.7.4 |
| Spring LDAP | 2.3.4 | 2.4.4 |
| Spring-WS | 2.1.2 | 3.1.8 |

Spring Security keeps its 5.x defaults. Its settings that prepare for Spring Security 6 come in
later releases.

If your overlay or a portlet deployed inside the portal web application has its own Java code or
Spring XML, check it against the Spring Framework 5 and Spring Security 5 migration notes. Spring 5
removed, among others, Spring Portlet MVC (uPortal's own copy is described above), the
`orm.hibernate3` package, `DefaultAnnotationHandlerMapping` and `AnnotationMethodHandlerAdapter`.

### URL suffixes under `/api`

Spring 5.3 no longer adds a file extension to the paths it maps. A request for `/api/groups.json`
or `/api/groups.xml` used to reach the controller mapped to `/api/groups`, and now answers 404 Not
Found. Use the paths the controllers declare:

| 5.17 also answered | Use |
| --- | --- |
| `/api/groups.json`, `/api/groups.xml` | `/api/groups` |
| `/api/v5-0/people/me.json` | `/api/v5-0/people/me` |
| `/api/v5-0/people/{username}.json` | `/api/v5-0/people/{username}` |

Paths that uPortal declares with their extension, such as `/api/people.json`, `/api/portlets.json`
and `/api/v4-3/dlm/layout.json`, are unchanged.

A dot in the last part of the path is now part of the value. `/api/v5-0/people/john.smith`
looks up the user `john.smith`. uPortal 5.17 cut the name at the dot and looked up `john`.

### Sessions stored in Redis

* Sessions that Spring Session stored in Redis are lost at the upgrade, so everyone is signed out
  once. They hold serialized Spring Security 4 objects, which Spring Security 5 can't read. Delete
  the `spring:session:*` keys when you upgrade. Sessions Tomcat saved to disk hold the same
  objects and are dropped too.
* uPortal connects to Redis with Lettuce instead of Jedis. The `org.apereo.portal.session.redis.*`
  settings are unchanged, with one difference: `org.apereo.portal.session.redis.timeout` (in
  milliseconds) is now the time a Redis command may take. When it is not set, Lettuce allows 60
  seconds, where Jedis allowed 2 seconds. Set it to `2000` to keep the old limit.
  `org.apereo.portal.session.redis.database` applies to standalone and sentinel servers. A Redis
  cluster has only database 0.
* If your overlay defines its own `RedisConnectionFactory` with `JedisConnectionFactory`, add Jedis 3
  to the web application or move it to `LettuceConnectionFactory`.

### Logging

Spring 5 includes its own commons-logging jar, `spring-jcl`. uPortal leaves it out and keeps
`jcl-over-slf4j`, so libraries that log through commons-logging still go to SLF4J and Logback as
before. If your overlay adds Spring jars, exclude `org.springframework:spring-jcl` there too:
both jars define the `org.apache.commons.logging` classes.

## uPortal-start runs Tomcat 9

uPortal-start installs Tomcat 9.0 instead of 8.5, and these answers look different:

* Tomcat's own error pages, such as the 404 page for a missing file, show the Tomcat version,
  now 9.0. They showed 8.5 before. To hide the version, set
  `showServerInfo="false"` on the `ErrorReportValve` in `server.xml`.
* JSON error responses from the `/api` controllers (401, 403 and 404) say
  `Content-Type: application/json;charset=UTF-8`. On Tomcat 8.5 the same responses said
  `charset=ISO-8859-1`, although their bodies were UTF-8. A client that decodes by the declared
  charset now reads non-ASCII characters correctly.

## Pluto's shared jars come from uPortal

uPortal now builds Apache Pluto 2.1.0-M3 from its own copy of the sources (`uPortal-pluto`) and
fixes it there. uPortal-start's `tomcatConfig` (which `tomcatInstall` runs) puts uPortal's build of
three Pluto jars in Tomcat's `shared/lib`: `uPortal-pluto-container-api`,
`uPortal-pluto-container-driver-api` and `uPortal-pluto-taglib`, with uPortal's version. They
replace Apache's `pluto-container-api`, `pluto-container-driver-api` and `pluto-taglib` 2.1.0-M3.
The portlet API jar is unchanged.

The first fix is in the driver API's `PortletServlet` (MOD-I350). Each portlet registers with the
portal from a timer once the portal has started. A request for a portlet that hadn't registered
yet took that portlet out of service until Tomcat restarted, so a page rendered just after a
restart could break Simple Content or another portlet until the next restart. Now such a request
has the portlet registered at once and waits up to 10 seconds for that, after which Tomcat refuses
the portlet for one second only.

A site that fills `shared/lib` itself, such as in its own container image, replaces the three
Apache jars with uPortal's (`org.jasig.portal:uPortal-pluto-*` in Maven Central, the same version
as uPortal) to get the fix. Keep only one of each pair, because Apache's and uPortal's jars hold the
same classes. `tomcatConfig` removes the Pluto jars an earlier run put in `shared/lib` before it
copies the new ones. `-PsharedPluto=apache` keeps Apache's jars, and uPortal-start uses them by
default with a released uPortal 5.17, which doesn't publish uPortal's.

## Fixes made after uPortal 5.17.9

* The portal's analytics script (`scripts/portal-analytics.js`) no longer stops Bootstrap drop-down
  menus from opening (uPortal f301239691).

## Favorites is a web component

The Favorites channel (`favorites`) was the Favorites portlet. It is now a **Web Component**
channel that renders `<up-favorites>`, with its data from the new REST API
`/api/v6-0/favorites`. Users see the same list; reordering and removing happen in place instead
of in the portlet's EDIT mode.

Existing portals keep their Favorites portlet definition until it is converted. Convert it by
importing uPortal-start's `data/base/portlet-type/Web_Component.portlet-type.xml`, then its
`data/base/portlet-definition/favorites.portlet-definition.xml` (or your copy of it), which
updates the channel in place; see
[Converting existing Favorites channels](developer/web-components/README.md#converting-existing-favorites-channels)
for definitions with another fname or customized preferences. The Favorites portlet itself will
be removed in a later release.

Unsafe requests to the new `/api/v6-0/` REST APIs need Spring Security's CSRF token (or an OIDC
bearer token), like every unsafe request under `/api` (see
[Unsafe requests to `/api` need the CSRF token](#unsafe-requests-to-api-need-the-csrf-token)).

## Popular Portlets is a web component

The Popular Portlets channel (`popular-portlets`, titled "Most Popular Apps") was the
PopularPortlets portlet, a DataTables table. It is now a **Web Component** channel that renders
`<up-popular-portlets>`, with its data from the new REST API `/api/v6-0/popular-portlets`. Users
see the same table: the choice of previous days, a link and count for each portlet, sorting,
the Title and # Times filters, and paging. The differences:

* A filter keeps the rows it names. The portlet's filters kept the right number of rows but
  showed other portlets once the table was sorted.
* A portlet without a description has no tooltip. The portlet showed
  `[no description available]`.
* Choosing other days keeps the sort, the filters and the rows per page, and shows the first
  page.

The texts the portlet's script had in English are new `popular.portlets.*` keys in
`Messages.properties`. Translations that lack them show the English text.

Existing portals keep their PopularPortlets definition until it is converted. Convert it by
importing uPortal-start's `data/base/portlet-type/Web_Component.portlet-type.xml`, then its
`data/base/portlet-definition/popular-portlets.portlet-definition.xml` (or your copy of it). A
definition with another fname needs the type `Web Component`, the framework descriptor
`WebComponent` and the parameters `webComponent.tagName` and `webComponent.moduleUrl`, both
`up-popular-portlets`. See
[Converting existing Popular Portlets channels](developer/web-components/README.md#converting-existing-popular-portlets-channels).
The PopularPortlets portlet itself will be removed in a later release.

## Background Preference is a web component

The Background Preference channel (`background-preference`) was the backgroundPreference portlet.
It is now a **Web Component** channel that renders `<up-background-preference>`. The element
reads the offered images and the user's choice from
`GET /api/v6-0/background-preference/{fname}` and saves a choice with a `PUT` to the same path.
The API accepts only one of the offered images (or none), and answers anything else with 400.
Guests get 401.

Users see the same control: "Change Background Image" opens a list of None and the images with
their thumbnails, and the chosen image shows behind the page at once and on every page after.
Some things are different:

* The background is set through one stylesheet that the element adopts into the page
  (`document.adoptedStyleSheets`), not through `<style>` elements, so a Content-Security-Policy
  that forbids inline styles no longer blocks it.
* The element applies the background after it has loaded the choice, so it appears a moment
  after the page.
* None removes the chosen image, and the skin's own background shows, as after a reload with the
  portlet. The portlet also set the page's background color to transparent when an image was
  chosen, until the next page.
* "Change Background Image" and "None" are messages now (`background.preference.change`,
  `background.preference.none`), translated into French and Spanish.

The preferences keep their names and meaning: `defaultBackgroundImages`,
`defaultBackgroundThumbnailImages`, `backgroundImageCaptions`,
`defaultBackgroundContainerSelector`, and the `mobile*` versions for users in the "Mobile Device
Access" group. Paths may still contain `${portalContext}`. The user's choice is still the
`defaultSelectedBackgroundImage` (or `mobileSelectedBackgroundImage`) preference of their portlet
entity, so choices made with the portlet stay. The container selector defaults to
`body.portal #portalPageBody::before` (`body.portal #portalPageBody` for mobile), as the
portlet's `portlet.xml` did, and must be one CSS style rule's selector. `applyOpacityTo` and
`opacityCssValue` were never used by the portlet's view and are ignored.

To convert an existing portal, import uPortal-start's Web Component portlet type (if another
conversion has not imported it already), then its
`data/quickstart/portlet-definition/background-preference.portlet-definition.xml`. The import
updates the channel by fname and keeps its ID, layout placements and users' choices. A
definition with another fname is converted by the same changes: the type becomes `Web Component`
with the `WebComponent` framework portlet, and two parameters are added,
`webComponent.tagName` and `webComponent.moduleUrl`, both `up-background-preference`. The
backgroundPreference portlet, `ViewBackgroundPreferenceController` and its JSP stay in this
release for definitions that are not converted yet, and will be removed later.

## The user locale selector is a web component

The User Locales Selector channel (`user-locales-selector`) was the `UserLocale` portlet, a Web
Flow. It is now a **Web Component** channel that renders `<up-user-locale>`, with its data from
the new REST API `/api/v6-0/locale`. Users see the same list of the portal's languages, and
Update still reloads the page in the chosen language. A signed-in user's choice is stored, and a
guest's lasts the session, as before. What changed:

* Each language is named in its own language, then in the user's current language when that reads
  differently: "Français (French)" for an English user, "English (anglais)" for a French one. A
  user who can't read the current language can still find their own. The browser supplies each
  own name, so a language added to `portal_locales` doesn't need a new message.

* A user who hasn't chosen a language sees the one the page is in checked. The portlet checked
  none until the user saved a choice.
* The flag icons next to the languages are gone. A flag stands for a country, and several
  countries share a language.
* The API accepts only the locales in `org.apereo.portal.i18n.LocaleManager.portal_locales`, and
  answers 400 to any other code. The portlet stored whatever code the form sent.

Existing portals keep their User Locales Selector definition until it is converted. Convert it by
importing uPortal-start's `data/base/portlet-type/Web_Component.portlet-type.xml`, then its
`data/base/portlet-definition/user-locales-selector.portlet-definition.xml`, which updates the
channel in place. For a definition with another fname, make these changes and keep everything
else:

* the type to `Web Component`, and the descriptor to the framework portlet `WebComponent`;
* add the parameters `webComponent.tagName=up-user-locale` and
  `webComponent.moduleUrl=up-user-locale`.

The `UserLocale` portlet and its `locale-selector` flow will be removed in a later release.
`UserLocaleHelper` stays, because the REST API and the Translator portlet use it.

## Session Timeout is a web component

The Session Timeout channel (`session-timeout`) was the SessionTimeout portlet. It is now a **Web
Component** channel that renders `<up-session-timeout>`. The element reads the session's timeout
from `GET /api/v6-0/session` and keeps the session with `POST /api/v6-0/session/keep-alive`. Users
see the same warning with the same text and buttons, 30 seconds plus `dialogDisplaySeconds`
before the session expires. One thing looks different:
when the keep-alive fails, "Error resetting your session!" appears inside the warning instead of
in a browser alert. Tabs that still run the portlet and tabs that run the component share the
logout time, so they postpone each other's warning while a portal is being upgraded.

To convert an existing portal, import uPortal-start's Web Component portlet type (if the
Favorites conversion has not imported it already), then its
`data/base/portlet-definition/session-timeout.portlet-definition.xml`. The import updates the
channel by fname and keeps its ID and layout placements. A definition with another fname is
converted by the same changes: the type becomes `Web Component` with the `WebComponent`
framework portlet, and three parameters are added:

* `webComponent.tagName` and `webComponent.moduleUrl`, both `up-session-timeout`;
* `webComponent.preferenceAttributes`, one mapping per line: `enabled=enabled`,
  `dialog-display-seconds=dialogDisplaySeconds` and `logout-url=logoutURLFragment`.

The `enabled`, `dialogDisplaySeconds` and `logoutURLFragment` preferences keep their meaning and
defaults (`true`, 60 and `/Logout`). As with the portlet's script, a portal that can't report the
session timeout (a server error, a network failure or an unreadable body) gets a 30-minute default
and the warning still shows. A guest's 401 turns it off. `logoutURLFragment` must be a path in the
portal: a value that starts with `//` or names another host falls back to `/Logout`. A portal that
pointed it at another site, such as a CAS or single sign-on logout page, should leave it at
`/Logout` and set `logout.redirect` in `security.properties` to that page instead. `/Logout` ends
the portal session and then sends the browser there.
`resetSessionURLFragment` is no longer read, because the keep-alive is the REST API. A portal that
pointed it at its own URL should check that `POST /api/v6-0/session/keep-alive` reaches uPortal. The
SessionTimeout portlet, `SessionTimeoutViewController` and `scripts/session-timeout.js` stay in this
release for definitions that are not converted yet, and will be removed later.

## Joda-Time replaced by `java.time` (outside the database mappings)

Spring 6 dropped its Joda-Time support, so uPortal moves to `java.time`. This step converts the
code that does not map database columns; the JPA entities and the event-aggregation code built
on them follow with the Hibernate upgrade.

### Settings in `portal.properties`

Every duration and period setting accepts the same values as before. The duration settings
(`ClusterLockDao.abandonedLockAge`, `maximumLockDuration`, `updateLockRate` and
`dbLockAcquireTimeout`) take ISO-8601 durations such as `PT30S`, and now also accept forms
such as `PT1M`. The event settings (`PortalRawEventsAggregatorImpl.aggregationDelay`,
`PortalEventDimensionPopulatorImpl.dimensionBuffer`, `PortalEventPurgerImpl.purgeDelay` and
`PortalEventSessionPurgerImpl.eventSessionDuration`) take ISO-8601 periods such as `P1D`,
`PT30S` or `P1DT12H`: days, weeks, months and years count calendar days in the portal's time
zone, and hours, minutes and seconds count elapsed time, as they did with Joda-Time. The old
comment's `purgeDelay=P1H` example was never valid. The default is `PT1H`.

### Spring property editors

`org.apereo.portal.spring.properties.ReadableDurationEditor` and `ReadablePeriodEditor` are
removed, with their entries in `portalPropertyEditorRegistrar` (`applicationContext.xml`). The
registrar now converts text to `java.time.Duration` (`DurationEditor`) and to
`java.time.temporal.TemporalAmount` (`TemporalAmountEditor`). If your overlay has beans with
`org.joda.time.ReadableDuration` or `ReadablePeriod` properties set from text, change those
properties to `java.time.Duration` or `TemporalAmount`.

### Java API changes

| Class | Change |
| --- | --- |
| `ClusterLockServiceImpl` | `setUpdateLockRate`, `setMaximumLockDuration`, `setDbLockTimeout` take `java.time.Duration` |
| `JpaClusterLockDao` | `setAbandonedLockAge` takes `java.time.Duration` |
| `DelegatingThreadPoolTaskScheduler` | `setInitialDelay` takes `java.time.Duration` |
| `PortalRawEventsAggregatorImpl` | `setAggregationDelay` takes `java.time.temporal.TemporalAmount` |
| `PortalEventDimensionPopulatorImpl` | `setDimensionBuffer` takes `TemporalAmount` |
| `PortalEventPurgerImpl` | `setPurgeDelay` takes `TemporalAmount` |
| `PortalEventSessionPurgerImpl` | `setEventSessionDuration` takes `TemporalAmount` |
| `IAnalyticsPortalEventService` | `getAnalytics` takes `java.time.ZonedDateTime` start and end dates |
| `PortletReleaseNotes` (Marketplace) | `releaseDate` and `initialReleaseDate` are `java.time.LocalDateTime` |

The Marketplace JSON (`/api/marketplace/entries.json` and `entry/{fname}.json`) still writes
`releaseDate` and `initialReleaseDate` as epoch milliseconds in the portal's time zone.

### JSP tags

The Joda-Time JSP tag library (`joda-time-jsptags`) is no longer in the web application, and
`WEB-INF/jsp/include.jsp` no longer declares the `joda` prefix. If a JSP in your overlay uses
`<joda:format>` or another `joda:` tag, format the value another way, such as with
`fn:substring` on a `java.time` value's ISO text, or by formatting it in the controller.

## spring-modules-cache is gone; the user attribute cache uses Spring's cache API

The uPortal web application no longer includes spring-modules-cache 0.8. Person Directory's
`CachingPersonAttributeDaoImpl` needs that library, so uPortal's `cachingPersonAttributeDao`
bean is now an `org.apereo.portal.utils.cache.CachingPersonAttributeDao`. It caches the same
results under the same keys in the same cache, `org.apereo.services.persondir.USER_INFO.merged`,
and still caches "not found" results. Logging out still purges the user's entries.

If your overlay defines its own caching person attribute DAO:

* Replace `org.apereo.services.persondir.support.CachingPersonAttributeDaoImpl` with
  `org.apereo.portal.utils.cache.CachingPersonAttributeDao`.
* Its `userInfoCache` property now takes a Spring `org.springframework.cache.Cache`, not a `Map`.
  Look it up from the `ehCacheManager` bean, as in
  `<property name="userInfoCache" value="#{ehCacheManager.getCache('your.cache.name')}" />`,
  and make sure the cache is defined in `ehcache.xml`.
* The `userAttributeCacheKeyGenerator` bean is now a Spring
  `org.springframework.cache.interceptor.KeyGenerator`. It is no longer a spring-modules
  `CacheKeyGenerator`.
* The `cacheKeyAttributes`, `nullResultsObject`, `getQueries()` and `getMisses()` members are not
  carried over. Cache hit and miss counts are in the Cache Manager portlet.

## uPortal's caches are looked up through Spring's cache manager

uPortal is moving its own caching onto Spring's cache abstraction, so that a later release can
switch the cache provider without changing code. The provider is still Ehcache 2, and
`ehcache.xml` (or the file named by `org.apereo.portal.ehcache.filename`) still defines every
cache. Cache names, sizes, expiry and JGroups replication are unchanged.

What is different is how some classes find their caches. They no longer inject an Ehcache bean
named after the cache. Instead they look the cache up by name in the `ehCacheManager` bean (the
`EhCacheCacheManager` in `cacheContext.xml`). If `ehcache.xml` has no cache of that name, the
portal fails to start. This applies to these classes and caches:

| Class | Cache |
| --- | --- |
| `PortalSpELServiceImpl` | `SpELExpressionCache` |
| `PortletSpELServiceImpl` | `portletSpELExpressionCache` |
| `LayoutCachingService` | `org.apereo.portal.layout.dlm.LAYOUT_CACHE` |
| `AuthorizationImpl` | `...AuthorizationImpl.AUTH_PRINCIPAL_CACHE`, `...ENTITY_PARENTS_CACHE`, `...PRINCIPAL_HAS_PERMISSION` |
| `AnyUnblockedGrantPermissionPolicy` | `...AnyUnblockedGrantPermissionPolicy.HAS_UNBLOCKED_GRANT` |
| `LrsActorService` | `org.apereo.portal.events.tincan.LrsActorCache` |
| `RDBMUserIdentityStore` | `org.apereo.portal.RDBMUserIdentityStore.userLockCache` |
| `PortletEntityRegistryImpl` | `org.apereo.portal.portlet.dao.jpa.PortletEntityImpl.idParseCache` |
| `PortletEventCoordinationHelper`, `PortletEventCoordinatationService` | `org.apereo.portal.portlet.rendering.SupportedEventCache` |
| `JpaAggregatedTabLookupDao` | `org.apereo.portal.events.aggr.tabrender.TabRenderAggregator.layoutNodeIdNameResolver` |
| `FileSystemDynamicSkinService`, `AwsS3DynamicSkinService` | `org.apereo.portal.skinManager.failureCache` |

If your overlay has its own `ehcache.xml`, keep these cache names in it. If it replaced one of
these caches by defining a bean with the cache's name, that bean is no longer used: configure
the cache in `ehcache.xml` instead. In Java code, the `setXCache` setters of these classes now
take an `org.springframework.cache.Cache`. The skin services'
constructors take the Spring `CacheManager` instead of the failure cache.

`CacheHealthReporterService.reportCacheSizeNotLargeEnough` now takes the cache name instead of
the Ehcache `Cache` and `Element`. This matters only if you wrote your own cache event listener
that reports to it.

## Statistics reports use uPortal's own table model

The statistics reports used to build their data with google-visualization-java, a library that
was only published on jitpack.io. uPortal now has a small table model of its own in
`org.apereo.portal.web.report`, and the library is no longer in the uPortal web application.

The report pages, their charts and the CSV and HTML downloads look and work as before. The JSON
the charts read is unchanged, except that a missing value is now always `{"v":null}`; the
library wrote invalid JSON for a missing value that wasn't in a row's last column.

If your overlay has Java code that extends the statistics controllers, or a Spring context that
names the table views, change it as follows:

| Old | New |
| --- | --- |
| `com.google.visualization.datasource.datatable.DataTable` (and uPortal's `JsonDataTable`) | `org.apereo.portal.web.report.ReportTable` |
| `com.google.visualization.datasource.datatable.ColumnDescription` | `org.apereo.portal.web.report.ReportColumn` |
| `com.google.visualization.datasource.datatable.value.ValueType` | `org.apereo.portal.web.report.ReportValueType` (`TEXT`, `NUMBER`, `DATE`, `DATETIME`, `TIMEOFDAY`) |
| `new NumberValue(n)`, `new DateValue(...)`, `new DateTimeValue(...)`, `new TimeOfDayValue(...)`, `new TextValue(s)` | `ReportValue.number(n)`, `ReportValue.date(LocalDate)`, `ReportValue.dateTime(LocalDateTime)`, `ReportValue.timeOfDay(LocalTime)`, `ReportValue.text(s)` (months count from 1, as in `java.time`) |
| `Value.getNullValueFromValueType(type)` | `ReportValue.nullValue(type)` |
| `List<Value> createRowValues(...)`, `List<ColumnDescription> getColumnDescriptions(...)` | `List<ReportValue> createRowValues(...)`, `List<ReportColumn> getColumnDescriptions(...)` |
| `org.apereo.portal.web.view.GoogleCsvDataTableView` | `org.apereo.portal.web.view.ReportTableCsvView` |
| `org.apereo.portal.web.view.GoogleHtmlDataTableView` | `org.apereo.portal.web.view.ReportTableHtmlView` |
| `org.apereo.portal.web.view.AbstractGoogleDataTableView` | `org.apereo.portal.web.view.AbstractReportTableView` |

The controllers no longer declare `throws TypeMismatchException`; adding a row with a value of
the wrong type throws `IllegalArgumentException` instead. The view bean names,
`dataTableCsvView` and `dataTableHtmlView`, are unchanged.

The CSV and HTML downloads format numbers and dates as before. In locales whose default calendar
isn't Gregorian or Buddhist, such as `ar-SA` and `fa-IR`, dates are now Gregorian.

## Hibernate 5.2

uPortal now runs on Hibernate ORM 5.2.18 instead of 4.3.11. An existing database upgrades in
place: no SQL needs to be run, and new rows keep the identifiers the old ones were given.

uPortal keeps every database on the schema and identifier generation it had under Hibernate
4.3. The settings that do this are in `hibernate.properties` (a "freeze block"), together with
the dialect choice in `PortalDialectResolver`. Do not override them in your own
`hibernate.properties` or `hibernate*.cfg.xml`:

| Setting | Why it is there |
| --- | --- |
| `hibernate.dialect_resolvers=org.apereo.portal.utils.PortalDialectResolver` | Keeps each database on the dialect Hibernate 4.3 chose. MySQL 8, and MariaDB reporting MySQL metadata, get `MySQL55Dialect`, where Hibernate 5.2 would pick `MySQL57Dialect` with `datetime(6)` columns, or a MariaDB dialect with sequences. PostgreSQL 10 and later get `PostgreSQL81Dialect` (as `PostgreSQL81TextClobDialect`, see below). SQL Server gets `SQLServer2005Dialect`, which doesn't use sequences. |
| `hibernate.implicit_naming_strategy=org.apereo.portal.hibernate.PortalImplicitNamingStrategy` | Join column names and the `FK_…`/`UK_…` constraint names of Hibernate 4.3 |
| `hibernate.physical_naming_strategy=…PhysicalNamingStrategyStandardImpl` | Names are used as they are mapped |
| `hibernate.id.optimizer.pooled.preferred=pooled` | Identifiers are allocated as before from the `UP_*_SEQ` sequences or one-row tables |
| `hibernate.id.sequence.increment_size_mismatch_strategy=EXCEPTION` | Takes effect from Hibernate 5.4: startup stops if a sequence's increment no longer matches its mapping |

If you customized any of the following, adapt it before you upgrade:

* **Code that uses Hibernate APIs directly**, in overlays or custom portlets deployed inside the
  portal web application: `org.hibernate.Query` is deprecated in favour of
  `org.hibernate.query.Query`, `Session.setFlushMode(FlushMode)` in favour of
  `setHibernateFlushMode`, and `Integrator` implementations receive a
  `org.hibernate.boot.Metadata` instead of a `Configuration`. `HibernateConfigurationAware`
  beans get the dialect and a `SchemaTool` from `HibernateConfiguration`; `getConfiguration()` is
  gone.
* **Custom dialects or `UserType`s.** User types now receive a
  `SharedSessionContractImplementor`. uPortal uses Jadira Usertype 6.0.1.GA; its Joda-Time types
  store values exactly as Jadira 3.2.0.GA did.
* **`@Type` on a `Map` element collection.** Hibernate 4.3 applied it to the map keys as well as
  the values. Hibernate 5 applies it to the values only. uPortal's own maps declare `@MapKeyType`
  so that existing rows are read and written as before. An entity of your own that relies on
  the old behaviour needs `@MapKeyType(@Type(type = "…"))` too.
* **`hibernate.dialect.storage_engine`.** Hibernate 5.2's `MySQL5Dialect` creates MyISAM tables
  unless this is set. uPortal does not use that dialect, but a dialect you configured yourself
  might.
* **dom4j.** Hibernate 5.2.18 depends on `org.dom4j:dom4j` 2.1. The portal keeps
  `dom4j:dom4j` 1.6.1, which the uPortal code is written against, and excludes 2.1.

On Oracle, some deletes that failed in 5.17 now work. Oracle stores an empty string as NULL, and
Hibernate does not load a NULL value of an element collection, so it did not delete that row with
its owner, and the owner's delete failed with ORA-02292. This hit a portlet entity with an empty
window state, a layout attribute with an empty target element name, and a portlet definition with
an empty localization. uPortal now deletes those rows with their owner.

## Timestamps are stored in UTC

uPortal 5.17 stored some timestamp columns as the clock time of the portal server's JVM. The value
in the database depended on the server's time zone, and in the hour that repeats when daylight
time ends, two different instants were stored as the same clock time. uPortal 6.0 stores these
columns as UTC clock time (`hibernate.jdbc.time_zone=UTC` in `hibernate.properties`):

| Table | Columns |
| --- | --- |
| `UP_PORTLET_LIFECYCLE` | `ENTRY_DATE` (portlet publish and expiry dates) |
| `UP_MUTEX` | `LOCK_START`, `LOCK_UPDATE`, `LOCK_END` (cluster locks) |
| `UP_PERSON_DIR` | `LST_PSWD_CGH_DT` (last password change of local accounts) |
| `UP_PORTLET_LIST` | `CREATED_ON`, `UPDATED_ON` |

`UP_MUTEX.LOCK_END` was not on the first list of affected columns. The persistence tests found
that it is stored the same way, so it is converted too.

The other timestamps (raw events, aggregation, event sessions, cookies) were already stored in
UTC, and the analytics date and time dimensions keep their local dates and times.

**Upgrade with the copy tool and the source server's zone.** Copy the 5.17 database into a 6.0
database with `--target-profile 6.0` (the default) and `--source-zone` set to the time zone of the
5.17 portal's JVM (its `user.timezone`, or the operating system's zone if that isn't set):

```sh
db-migrate --source-properties 517.properties --target-properties 60.properties \
    --target-profile 6.0 --source-zone America/New_York
```

The copy converts the columns above from that zone's clock time to UTC. If any of them holds a
value and `--source-zone` is missing, the copy stops before writing anything and names the
columns. A copy between two 5.17 databases (`--target-profile 5.17`) leaves the values as they
are.

**The repeated hour.** A clock time in the hour that repeats when daylight time ends (01:00 to
01:59 on the first Sunday of November in New York) can be either of two instants, and 5.17 didn't
record which. The copy takes the earlier one, the daylight-time offset: 01:30 on 2026-11-01 in New
York becomes 05:30 UTC. A value that was written in the second 01:00 to 01:59 hour ends up one hour
early. A clock time in the hour that is skipped when daylight time starts can't come from 5.17;
if one is there, the copy moves it forward by an hour, as `java.time` does.

Code that reads these columns with SQL of its own gets UTC clock times from 6.0 on. The Java API
(`IPortletLifecycleEntry.getDate()`, `ILocalAccountPerson.getLastPasswordChange()`, the portlet
list REST API) returns the same instants as before.

## Passwords with letters outside ASCII

Local account passwords may contain any Unicode text. uPortal hashes a password as its UTF-8 bytes
(Jasypt's digester), as 5.x did, so stored hashes are the same whatever the server's default
charset was. You don't need to reset any password.

The sign-in path changed. 5.x turned the typed password into bytes with the
JVM's default charset before checking it. On a server whose default charset wasn't UTF-8 (Java 11
to 17 on Windows, or a Linux container in the POSIX locale), a password with letters outside that
charset didn't sign in, although its hash was right. 6.0 uses UTF-8 on every step, for local
accounts, LDAP and JAAS.

`CliPasswordUpdateTool` reads the new password from standard input as UTF-8. Run it in a terminal
that uses UTF-8 when the password has letters outside ASCII.

## PostgreSQL: do not run `vacuumlo`

On PostgreSQL, uPortal stores the raw event data (`UP_RAW_EVENTS.EVENT_DATA` and
`UP_ANALYTICS_EVENTS.EVENT_DATA`) as large objects and keeps each large object's OID in a `text`
column. uPortal 5.17 does this, and 6.0 keeps it: `PortalDialectResolver` picks
`PostgreSQL81TextClobDialect` for PostgreSQL 10 and later, which still declares these columns
`text` where Hibernate 5.6.2 and later would declare them `oid`.

`vacuumlo` only counts references from `oid` and `lo` columns, so it treats the data of
every stored raw event as orphaned and deletes it: on a 5.17 test portal, `vacuumlo -n` reported
that it would remove all 720 large objects written during one portal session. Do not run
`vacuumlo` (or any other clean-up of "orphaned" large objects) against a uPortal database, and
exclude it from scheduled maintenance.

A later release will migrate these columns to a large-object type. After that, `vacuumlo` is safe
to use again.

## uPortal-start signs users in locally; CAS is opt-in

uPortal-start no longer deploys a CAS 3.6 server (`overlays/cas`) into the portal's Tomcat.
CAS 3.6 is end of life, runs on Spring 3.2 and the javax Servlet API, and cannot follow the
portal to Tomcat 10.1 and 11. A portal built from uPortal-start now signs users in with
uPortal's own login form (`SimpleSecurityContextFactory`) against the accounts in the portal
database:

* `org.apereo.portal.security.provider.cas.CasAssertionSecurityContextFactory.enabled=false`
* `org.apereo.portal.channels.CLogin.CasLoginUrl=${portal.context}/p/login`
* `login.refUrlEncoder=local` (see the next section)
* `logout.redirect=` (empty: back to the guest page)

To keep signing in through CAS, build with `authMode=cas` and `casServerUrl` pointing at your CAS
server (uPortal-start `docs/authentication.md`). Register the portal's `/Login` URL as a CAS
service, and allow proxying to `/CasProxyServlet` if portlets use proxy tickets. CAS 5 and later
take `logout?service=` (not `logout?url=`) and follow it only with
`cas.logout.follow-service-redirects=true`. Drop `cas.ticketValidationFilter.encodeServiceUrl=false`
if you set it for CAS 3.6.

uPortal's defaults in `security.properties` are unchanged: they still point the login and logout
URLs at `${cas.protocol.server.context}`, so a portal not built from uPortal-start keeps its CAS
settings.

## Clusters: permission changes and portal events

**Permission changes take effect at once, on every node.** uPortal 5 cached each permission
decision on the node that made it, in `AuthorizationImpl.PRINCIPAL_HAS_PERMISSION` (180 s) and
`AnyUnblockedGrantPermissionPolicy.HAS_UNBLOCKED_GRANT` (300 s), and kept it after a grant or a
revocation. A revoked permission was still granted for up to 5 minutes to sessions that had
already used it. uPortal 6.0 clears both caches whenever a permission is added, removed or
updated. `ehcache.xml` now replicates their removals over JGroups, and the other nodes clear
theirs too. A portal with its own `ehcache.xml` needs the JGroups replicator on both caches,
with `replicatePuts=false, replicateUpdates=false, replicateRemovals=true` (see uPortal's
copy). Each permission change now costs a round of fresh permission checks, on every
node.

**Portal events are written from the start.** A node used to queue its portal events in memory
for the first 120 s after it started, with its other scheduled tasks. Another node's aggregator
had passed their time by then, so they were never aggregated, and every restart in a running
cluster lost the statistics of its first minutes. The queue is now flushed from the start, on
its own scheduler. The new setting
`org.apereo.portal.events.handlers.db.PortalEventDaoQueuingEventHandler.flushInitialDelay`
(default `PT0S`) sets when the first flush runs. Keep it well under
`org.apereo.portal.events.aggr.PortalRawEventsAggregatorImpl.aggregationDelay` (default `PT30S`).

**A starting node keeps trying to join the cluster.** In uPortal 5 a node that started next to a
running one sent its JGroups JOIN 4 times and waited 1 s for each answer. The running node can
answer only once it has the new node's address, which took a little over 4 s in a two-node test.
In 6 of 20 starts the new node gave up and formed a cluster of its own. Until JGroups merged the
two, 48 s or more later, it dropped the other node's cache invalidations, and what it cached in
that time kept its old value until it expired (6 hours for portlet definitions). `jgroups.xml`
now tries 8 times (`<pbcast.GMS join_timeout="1000" max_join_attempts="8">`), and the new node
joined in all of 20 starts. A portal with its own `jgroups.xml` should make the same change.

The cost falls on a portal with one server that restarts after a crash. Its old row in
`JGROUPSPING` still names it the coordinator, so it sends JOINs to that dead address before it
starts on its own: about 8.6 s with the new setting, 4.3 s before. A clean shutdown removes the
row, and the restarted server then starts at once.

**Caches are emptied when a split heals.** Servers split by the network, or by a server that
failed to join, drop each other's cache invalidations. JGroups doesn't resend them when the two
sides merge again, and in uPortal 5 an entry read during the split kept its old value until it
expired. In 6.0 the JGroups stack in `jgroups.xml` ends with a protocol that reports each
MergeView, and `MergeViewCacheClearer` then empties the caches and logs one INFO line with the
view and the number of caches. It leaves alone the caches that store the only copy of something,
such as lock objects and CAS tickets (`excludedCacheNames` in `cacheContext.xml`). The servers read the entries again from
the database, which costs a short burst of load right after the heal. A plain view change, such as
a server joining or leaving, clears nothing.

A portal with its own `jgroups.xml` must add this line as the last protocol, after
`pbcast.STATE_TRANSFER`:

```xml
<org.apereo.portal.utils.cache.MergeViewNotifier />
```

Without it, the servers don't empty their caches after a heal, and an entry read during a split
keeps its old value until it expires (6 hours for portlet definitions), as in uPortal 5. At start
uPortal logs at INFO when no JGroups stack has the protocol. The setting
`org.apereo.portal.cache.clearCachesOnMerge` (default `true`) switches the clear off.

## Deep links and failed logins with local login

The new setting `login.refUrlEncoder` (in `security.properties`) says how uPortal sends a guest
who opens a page that needs a login, such as `/uPortal/p/user-administration`, to the Sign In URL
(`org.apereo.portal.channels.CLogin.CasLoginUrl`):

* `cas`, the default, works as in 5.x: the page is encoded into the CAS `service` URL.
* `local`: the Sign In URL is the portal's own login form, such as `${portal.context}/p/login`.
  The page goes in a `refUrl` parameter and the form posts it to `/Login`, which opens the page
  after signing in. A wrong user name or password returns to the form, which shows "Invalid
  username or password" at once. In 5.x it went to the guest page without a message.

A portal that signs users in with uPortal's login form sets `login.refUrlEncoder=local`. In 5.x
such a portal sent guests' deep links to the form in the CAS encoding, which failed with HTTP 400
unless the Sign In URL ended in a query parameter such as `?refUrl=`. Remove that suffix. An
unknown value stops the portal at startup.

`LocalLoginRefUrlEncoder` now appends the page to its `loginUrl` (the Sign In URL) instead of
always sending guests to `/Login`. If you wired it in your own Spring files, set `loginUrl`, or
use `login.refUrlEncoder=local` instead.

## Unsafe requests to `/api` need the CSRF token

Every POST, PUT, PATCH and DELETE under `/api` now needs Spring Security's CSRF token, as
`/api/v6-0/` already did. 5.x exempted all of `/api`, although browsers call these endpoints with
the session cookie, so another web site could make a signed-in user's browser change their
layout, permissions or portal data. The endpoints concerned are `/api/layout`,
`/api/marketplace/{fname}/rating/{rating}`, `/api/preferences/{fname}`,
`/api/preferences/definition/{fname}`, `/api/portlet-list`, `/api/v4-3/pags/{group}.json`,
`/api/import`, `/api/entity/{type}/{id}`, `/api/deletePermission`, `/api/updatePermission`,
`/api/v5-4/caches` and `/api/analytics`. Without a valid token they answer HTTP 403.

A client sends the token in the `X-CSRF-TOKEN` header, or as a `_csrf` request parameter. Every
Respondr page has the session's token in `<meta name="_csrf">` and the header name in
`<meta name="_csrf_header">`. Scripts on the page get both with `up.csrfHeaders()` from
`modern-util.js`. A client outside the page reads the meta tags of a portal page that it loaded
with the same session; the token changes when the session does, at login and logout.

These requests don't need the CSRF token:

* requests with an `Authorization: Bearer` header, such as the ID token from
  `/api/v5-1/userinfo`;
* `/api/v5-5/oauth/token` and `/api/v5-1/userinfo`;
* `/Login` and `/Logout`.

`/api/updatePermission` changed permissions on a GET, which no CSRF check covers. It now accepts
POST only.

Customizations that call these endpoints with the session cookie must send the header. The
archived projects uPortal-home and uportal-app-framework don't send a CSRF token, so their layout
calls (uPortal-home's `addPortlet`, `removeByFName` and `movePortletAjax` with `useOldLayout`
on), Marketplace rating (`saveRating`), skin (`chooseSkin`) and add-to-home calls fail against
6.0. Adopters still running them must change their own copies to send the token or a Bearer
token, or stop using those calls.

## The account forms check what they are given

In 5.x the Self Edit Account and User Administration forms saved a password whose confirmation
differed, and "Create a User" with a user name that was already taken saved over that account
(MOD-I195). Web Flow looks for a validator bean named after the form's model, `accountFormValidator`,
but the bean was `personValidator`, so `PersonFormValidator` never ran. `flowsContext.xml` now
gives the bean the alias `accountFormValidator`. Both forms now refuse a differing confirmation
("Passwords must match") and a new account with a taken user name ("Username is already in use").

A site that overrides `self-edit-account/editLocalAccount.jsp` should start again from the new
page. The old page's script compared the inputs' `textLength`, which is undefined on an input. Its
error block showed the errors of a model called `person`, not `accountForm`.

## Self Edit Account asks for the current password

In 5.x anyone at a signed-in user's screen could change that user's password in Self Edit Account
without knowing it. The form now has a "Current Password" field, and the portal saves a new
password only when that field holds the user's password. If it is empty, the form shows "Please
enter your current password"; if it is wrong, "Provided password does not match the current
account password". Saving changed attributes without a new password doesn't need it. User
Administration, where an administrator sets other users' passwords, does not ask for one.

The check is `PersonFormValidator.validateEditOwnAccount`. Web Flow calls it because the form's
view-state in `self-edit-account.xml` is now `editOwnAccount` (the view is still
`editLocalAccount`). A site that overrides `self-edit-account.xml` or
`self-edit-account/editLocalAccount.jsp` should take the new state id and the `currentPassword`
input from this version. A site with its own message bundles can translate the new key
`please.enter.current.password`.

## Password reset links expire

In 5.x the link in a Forgot Password email worked until it was used or a new one replaced it,
however old it was. Forgot Password now also stores when it issued the token, in the local
account's `loginTokenIssued` attribute, and the link works for
`org.apereo.portal.portlets.account.resetTokenLifetime` after that. The setting is an ISO-8601
duration in `portal.properties` and defaults to `PT1H` (one hour). A value that is zero, negative
or not a duration stops the portal at startup.

An older link shows "Invalid password reset URL" with "This password reset URL has expired. Send a
new email to get a new one." and the link to send one. A link sent by 5.x has no
`loginTokenIssued`, so it has expired once the portal runs 6.0. Users who still need one ask for a
new email.

`UserAccountHelper.issueLoginToken(account)` sets both attributes; the `forgot-password` flow
calls it. A site with its own flow that sets `loginToken` directly should call it instead, or its
links expire at once. A site with its own message bundles can translate the new key
`password.reset.url.expired`.

## Respondr fixes for the accessibility checks

The accessibility check (axe-core, WCAG 2.1 A and AA) found these problems on the portal's key
pages. Respondr and the portlets in uPortal now avoid them:

* Cards and title bars of the uPortal portlets and of Respondr's portlet chrome had
  `role="section"` or `role="sectionhead"`. Both are abstract ARIA roles, which a page must not
  use. The attributes are gone. A skin or script that selects on `[role="section"]` or
  `[role="sectionhead"]` must select on the classes instead, such as `.portlet` and
  `.portlet-titlebar`.
* `#portalNavigation` and each region portlet's `section` had `aria-selected="false"`, which
  their roles don't allow. The attribute is gone.
* Links in paragraphs and in the footer regions are underlined. Before, only their colour marked
  them, and they were underlined only on hover. To keep the old look, a skin overrides
  `p a` and `footer a` in Respondr's `tags.scss` (the colour must then give a contrast of at
  least 3:1 with the text around it).
* The Permissions search inputs and the Portlet Manager's State and Category filters have
  accessible names.
* The header's search button showed only its icon, because `header.scss` hid its text with
  `display: none`, which also hides it from screen readers. The text is now hidden only visually
  (`.visually-hidden`), so the button's name is the `search.submit` message. The button looks
  the same.

## Tomcat's error pages no longer show the Tomcat version

uPortal-start's `etc/tomcat/conf/server.xml` now puts an `ErrorReportValve` on the Host with
`showReport="false"` and `showServerInfo="false"`. Tomcat writes its own error page when no
webapp's `error-page` answers: a 404 in uPortal (whose `web.xml` maps only 500) or outside every
webapp, a request the connector rejects (such as an invalid percent-encoding), and an exception in
a webapp without an `error-page`. These pages used to end in "Apache Tomcat/9.0.x" and show the
exception message. Now their title and heading give only the status code and its reason phrase.
Tomcat's logs still record the cause. The connector sends no `Server` header unless its `server`
attribute is set, and uPortal-start doesn't set it.

A portal that keeps its own `server.xml` adds the same valve inside its `<Host>`. Monitoring that
read the message text of these pages has to read the status code instead. uPortal-start
`tests/api/error-pages.spec.ts` checks one page of each kind.

## Tomcat's documentation, examples and ROOT page are gone

uPortal-start's `tomcatConfig` (which `tomcatInstall` runs) now deletes Tomcat's `docs`,
`examples` and `ROOT` webapps, and installs `etc/tomcat/webapps/ROOT`, which only redirects `/`
to `/uPortal/`. Tomcat's ROOT page named the Tomcat version, and anyone could open the
documentation and the examples, some of which echo request data. `/docs/` and `/examples/` now
answer 404. A portal whose context path is not `/uPortal` changes the target in
`etc/tomcat/webapps/ROOT/index.jsp`.

The `manager` and `host-manager` webapps stay. They have no users by default, so they answer 401.
A site that uses them must add a user with the `manager-gui` or `admin-gui` role to
`conf/tomcat-users.xml`. A site that doesn't can delete `webapps/manager` and
`webapps/host-manager` from its Tomcat. uPortal-start `tests/api/tomcat-webapps.spec.ts` checks
the redirect and that the documentation and examples are gone.

## Respondr's tab bar, header and phone menu fit their boxes again

Respondr's header, phone menu and tab bar broke in the move to Bootstrap 5, whose `.row > *` and `.container-fluid`
now set `width: 100%`. The layout checks at standard screen sizes (uPortal-start
`tests/layout`) found them, and the skin now fixes them:

* Below 576 pixels, the menu button was as wide as the screen, plus a 12-pixel margin that put
  its right end past the edge. The user links went on a second row, and the fixed header covered
  the top of the logo. The menu button is now a small button on the left, with the user links on
  the same row.
* Below 576 pixels, the open menu showed the tabs side by side in a sidebar wider than the 270
  pixels that the page moves over. The first tab was cut off and the last ones could not be
  reached. The tabs now stack in a 270-pixel sidebar.
* From 576 pixels, the first tab started 4 pixels left of the tab bar, because every tab has a
  -4px left margin. The tab bar now starts with the first tab, so all tabs sit 4 pixels further
  right.

A portlet in the pre-content region that floats its content, such as the quickstart's feedback
link, now has the height of that content instead of 0. Skins that copy Respondr's
`navigation.scss`, `header.scss` or `regions.scss` need the same changes.

## Entity keys (typeAndIdHash) spell out every character

`typeAndIdHash`, the key that `/api/entities.json`, `/api/entities/{type}/{id}.json` and
`/api/permissions/principals.json` return for each group and person, keeps the letters A to Z and
a to z and the digits as they are. It now writes every other character, `_` included, as `_`, the
character's code point in hexadecimal, and `_`. 5.x wrote each of them as `__`:

| Group or person | 5.x | 6.0 |
| --- | --- | --- |
| group `pags.Authenticated Users` | `group_pags__Authenticated__Users` | `group_pags_2e_Authenticated_20_Users` |
| person `admin-lo` | `person_admin__lo` | `person_admin_2d_lo` |

In 5.x different ids could get the same key: `pags.Café` and `pags.Cafè` both became
`group_pags__Caf__`, and `a.b` and `a_b` collided too. The Portlet Manager puts this key in the
`name` of each principal's permission checkboxes, so choosing BROWSE for one of those groups also
chose it for the other (MOD-I274).

The key isn't stored. A script or skin that builds a key itself from a group or person id, or
that compares keys it saved from 5.x, must use the new form. Code that only passes on the key the
API returned keeps working.

## Size the database's connection limit

uPortal's three pools (`PortalDb`, `RawEventsDb`, `AggrEventsDb`) can each open 100 connections,
and the bundled portlets open their own pools on top. Under load they need more than PostgreSQL's
default `max_connections` of 100: with 16 people using a portal at once they opened 127. The web
application that asks last then fails with "too many clients". Each page keeps a connection open
while its portlets render, and each portlet render takes another.

Set PostgreSQL's `max_connections` to at least 150, or size it from your own peak. uPortal-start
`docs/connection-budget.md` lists every pool, the measurements, and how to read your peak from the
pools' JMX beans (`uPortal:section=DataSource,name=PortalDb`) or from `pg_stat_activity`.
