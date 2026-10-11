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
`DefaultAnnotationHandlerMapping` and `AnnotationMethodHandlerAdapter` they used before. URLs and
behaviour are unchanged.

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
bearer token), unlike the older APIs under `/api`.

## Search is a web component, and the Search portlet is gone

uPortal-start's `search` channel was the Search portlet in its REST mode (`RESTSearch=true`), and
its `search-launcher` channel was the same portlet showing the search box in the page header.
Both are now **Web Component** channels from one module, `up-search`:

* `search-launcher` renders `<up-search-launcher>`. A search opens the search channel maximized,
  `/p/search/max?query=...`, with a plain link instead of a portlet action.
* `search` renders `<up-search>`, which reads the query from that URL, shows the results of the
  new REST API `/api/v6-0/search` with one tab per result type, and has a search box of its own
  that searches again in place.

Users see the same box, results, details and "No Results Found" message. Both boxes keep the
type-ahead over the portlet list (`/api/portletList`). It suggests after one character and a 200 ms
pause, and opens the chosen portlet maximized. `/api/v5-0/portal/search` stays, and the Lucene
index is unchanged.

Convert existing channels by importing uPortal-start's
`data/base/portlet-type/Web_Component.portlet-type.xml` (once), then its
`data/base/portlet-definition/search.portlet-definition.xml` and
`data/base/portlet-definition/searchlauncher.portlet-definition.xml` (or your copies of them).
The import updates each channel in place by fname, so layouts that place them keep working. A
definition whose descriptor is still the `Search` framework portlet fails to render, because that
portlet no longer exists.

The channels no longer read the Search portlet's preferences:

* `searchLaunchFname` becomes the launcher's `search-fname` attribute, set with the
  `webComponent.attributes` parameter (`search-fname=search`).
* `RESTSearch.type`, if you set it, becomes `<up-search>`'s `result-types` attribute: add the
  parameter `webComponent.preferenceAttributes` with the value `result-types=RESTSearch.type`.
* `RESTSearch`, `prepopulateAutoSuggestUrl`, `prepopulateUrlPattern`, `autoSuggestResultsProcessor`
  and `autocompleteMaxTextSize` have no effect. Type-ahead always uses the portlet list.

### What is removed

* The Search portlet (`SearchPortletController`, its JSPs and `Search-portlet.xml`) and its
  event mode (`RESTSearch=false`).
* Portlet-event search. uPortal no longer defines the `SearchRequest` and `SearchResults` portlet
  events, and the `uPortal-api-search` module with their JAXB types is gone. **Portlets can no
  longer contribute search results through events.** A portlet WAR that answers `SearchRequest`
  events keeps working, but nothing sends it the event any more. uPortal's Directory portlet no
  longer answers it, because the people search already covers the directory.
* `IPortalSearchService` and its implementations. They are the services for the Google Search
  Appliance (Google ended that product in 2019) and Google Custom Search, and the portlet registry
  and marketplace searches.
* The `searchServices`, `searchTabs`, `searchAutocompletePriorityMap` and
  `searchAutocompleteIgnoreResultTypes` beans in `servicesContext.xml`, the
  `org.apereo.portal.portlets.searchSearchPortletController.*` and `*.search.result.type`
  properties, `autosuggestHandler.js`, and `PortletUtils.getStringFromPortletUrl` with
  `portletUrl.tld`.

### Remote search providers instead

`/api/v6-0/search` gets all results from server-side search strategies (`ISearchStrategy` beans).
Besides the portlet index and the people search, these can be **remote JSON search providers**,
configured in `portal.properties` (or `uPortal.properties`). Each provider gets its own results
tab. The portal calls the provider's URL itself, with only the configured headers. The user's
cookies and session stay in the portal. Redirects are refused, and the portal waits at most the
timeout. A provider that fails shows an error in its own tab, and the other tabs keep their
results.

| Property under `org.apereo.portal.search.remote.` | Meaning |
| --- | --- |
| `providers` | The providers' names, comma-separated |
| `<name>.url` | The search URL, with `{query}` where the URL-encoded query goes (required) |
| `<name>.headers` | Request headers, `Name: value` pairs separated by `;` |
| `<name>.label` | The name of the results tab (default: the provider's name) |
| `<name>.results` | JSON Pointer to the array of results (default `/items`) |
| `<name>.title`, `<name>.link`, `<name>.description` | JSON Pointers into each result (default `/title`, `/link`, `/snippet`) |
| `<name>.timeout` | Milliseconds to wait for an answer (default 5000) |

The portal checks the settings at startup and stops with an error that cites the property when
one is unusable, such as a `url` without `{query}` or a pointer without its leading `/`.

**From Google Custom Search or the Google Search Appliance.** Google's Programmable Search JSON
API answers in the format the defaults expect. Move the search engine ID (`customSearchId`) and
API key (`key`) of the old `GoogleCustomSearchService` bean into the URL:

```properties
org.apereo.portal.search.remote.providers=web
org.apereo.portal.search.remote.web.label=Campus web
org.apereo.portal.search.remote.web.url=https://www.googleapis.com/customsearch/v1?key=YOUR_KEY&cx=YOUR_ENGINE_ID&q={query}
```

A Search Appliance site has no direct successor. Point the provider at the search service that
replaced it, if that service answers JSON, and map its fields.

**From a portlet that answered `SearchRequest` events.** Search its data through the service
behind the portlet instead. If that service has a JSON search API (a campus directory, a CMS, a
calendar), add a provider for it:

```properties
org.apereo.portal.search.remote.providers=web,directory
org.apereo.portal.search.remote.directory.label=Directory
org.apereo.portal.search.remote.directory.url=https://directory.example.edu/api/people?q={query}
org.apereo.portal.search.remote.directory.headers=X-Api-Key: YOUR_KEY
org.apereo.portal.search.remote.directory.results=/data/people
org.apereo.portal.search.remote.directory.title=/displayName
org.apereo.portal.search.remote.directory.link=/profileUrl
org.apereo.portal.search.remote.directory.description=/department
org.apereo.portal.search.remote.directory.timeout=3000
```

A search that needs more than a URL and a field mapping, such as results that depend on the
user, becomes an `ISearchStrategy` bean of its own, like the people and portlet searches.

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

## uPortal-start signs users in locally; CAS is opt-in

uPortal-start no longer deploys a CAS 3.6 server (`overlays/cas`) into the portal's Tomcat.
CAS 3.6 is end of life, runs on Spring 3.2 and the javax Servlet API, and cannot follow the
portal to Tomcat 10.1 and 11. A portal built from uPortal-start now signs users in with
uPortal's own login form (`SimpleSecurityContextFactory`) against the accounts in the portal
database:

* `org.apereo.portal.security.provider.cas.CasAssertionSecurityContextFactory.enabled=false`
* `org.apereo.portal.channels.CLogin.CasLoginUrl=${portal.context}/p/login?refUrl=`
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
