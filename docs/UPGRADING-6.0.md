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
