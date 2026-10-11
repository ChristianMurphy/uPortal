# Null handling that Spring 5.3 exposes (MOD-I225)

Branch `ccr-43ba0785-hzlrj5-spring53-nullness`, made from `ccr-43ba0785-hzlrj5` at `2b0da97`. It
lands before P4-1 (`ccr-43ba0785-hzlrj5-spring53`), on Spring 4.3.

Spring 5 annotates its API with `@Nullable` and `@NonNullApi`. On the P4-1 branch, SpotBugs reads
those annotations and reports 41 findings in 15 modules, all in existing code. This branch fixes
them on Spring 4.3, one commit per module, in the code itself: the SpotBugs baselines and suppressions stay as they were.

## Reproducing the findings

P4-1 wasn't on the remote when this work started. The same findings come from the integration
branch with a local Gradle init script, kept outside the repository, that runs `spotbugsMain` with
the Spring 5.3 probe classpath (`spring53ProbeMainCompileClasspath`) as its aux classpath. That run
reported 42 findings in the same 15 modules.

One of the 42 comes from running SpotBugs on classes compiled against Spring 4.3:
`TransactionManagerCachingTransactionInterceptor` calls `super.determineTransactionManager`, which
returns `PlatformTransactionManager` in 4.3 and `TransactionManager` in 5.3. On the classes the
`spring53Probe` task compiles against 5.3, SpotBugs doesn't report it, so that class doesn't
change. The other 41 match the count from P4-1.

After the branch, the same run reports only that one. The check against P4-1 itself (a local merge
of this branch into `ccr-43ba0785-hzlrj5-spring53`, then `spotbugsMain`) waits for P4-1 to be
pushed.

## The sites

"Can't be null" sites use `Objects.requireNonNull` with a message that states the query or the
missing object. The old code would have thrown a `NullPointerException` at those sites. The new
code throws one with that message.

| Module | Site | Spring 5 marks | Case |
| --- | --- | --- | --- |
| api-rest | `MarketplaceRESTController.getPortletRatings`, `getUserRating` | `ModelAndView` model object non-null | can be null: no rating is the answer; `addObject(name, null)` keeps `{"rating": null}` |
| events | `JpaAggregatedTabLookupDao.resolveTabName` | `Cache.get` and `put` key non-null | can be null: an event without a node id; resolved before the cache (it threw with `ConcurrentMapCache`) |
| events | `BatchTinCanAPIProvider.postStatementList` | `ResponseEntity.getBody()` nullable | can be null: an LRS may answer 204; logged as an argument |
| events | `CachingPersonAttributeDao.getPeopleWithMultivaluedAttributes` | `KeyGenerator.generate` non-null | redundant check: both checks removed |
| health | `DatabaseHealthChecker.getDbResponseTime` | `FactoryBean.getObject()` nullable | can't be null: an initialized factory bean |
| layout-impl | `RDBMDistributedLayoutStore.layoutExistsForUser` | `queryForObject` nullable | can't be null: `COUNT(*)` |
| layout-impl | `RDBMUserLayoutStore` system user, `getPersonalUserLayout`, `getLayoutID` | `queryForObject`, `execute` nullable | can't be null: a primary key, and callbacks that always return |
| persondir | `RDBMUserIdentityStore.removePortalUID` (two), `addNewUser` | `queryForObject`, `execute` nullable | can't be null: primary keys, and a callback that returns the new id |
| pluto-portal-driver | `PortalStartupListener.contextInitialized` | `getBean` non-null | redundant check: `getBean` throws for a missing bean |
| portlets | `GoogleCustomSearchService.getSearchResults`, `GsaSearchService.search` | `getForObject` nullable | can be null: a 204 answer; no results |
| portlets | `SqlQueryPortletController.handleRenderRequest` | `getApplicationContext()` nullable | can't be null: set before requests |
| rdbm | `JacksonColumnMapper`, `HibernateStyleCounterStore` | `getObject()`, `query` nullable | can't be null: an initialized factory bean, an extractor that returns true or false |
| rendering | the three preferences `storeInternal` methods | `TransactionOperations.execute` nullable | can't be null: callbacks return `TRUE` or `FALSE` |
| rendering | `JpaPortletExecutionInterceptor` pre and post execution | `getEntityManagerFactory()` nullable | can't be null: the interceptor's required factory |
| session | `SpringSessionRedisEnabledCondition` | `getProperty` default non-null | can be null: a missing property; `getProperty(key, type)` |
| spring-portlet | `HandlerMethodInvoker` (three), `AbstractHandlerMapping`, `AbstractMapBasedHandlerMapping` (two), `DefaultAnnotationHandlerMapping` | `findAnnotation`, `getHeaderValues`, `getApplicationContext()` nullable | can't be null. The resolver lists only annotated methods. The header names come from `getHeaderNames`. Spring sets the context before use |
| spring | `AspectJAroundAdviceFactory.createInstance` | `findMethod` nullable | can't be null in a working configuration |
| spring | `DialectAwareTransactionInterceptor`'s no-op transaction manager | `getTransaction` definition nullable | can be null: the contract's default; a `DefaultTransactionDefinition` |
| spring | `ZeroLeggedOAuthInterceptor.getOAuthAuthString` | `HttpRequest.getMethod()` nullable | can't be null: a `RestTemplate` request has a standard method |
| tools | `DataSourceSchemaExport.update` | `execute` nullable | can't be null: the schema tool returns a list |
| utils-core | `MediatingFactoryBean.createInstance` (two), `JdbcUtils.doesTableExist`, `CacheKey` | `getBeanFactory()`, `execute`, `getObject()` nullable | can't be null. Spring sets the bean factory before use. The table check answers true or false. `CacheKey` gets its mapper from an initialized factory bean |
| web | `PortletDelegationView.render` | `View.render` model nullable | can be null: the contract allows it; `IllegalArgumentException` naming the entries the view needs |

Wrapping a callback's result moves it right, so the layout, persondir, rendering, rdbm and
utils-core diffs are mostly google-java-format re-indenting it. The vendored Portlet MVC and Pluto
classes use Spring's and Pluto's tab style, and spotless leaves them out.

## Tests

The sites where null can happen have a test that failed first:
`MarketplaceRESTControllerTest` (the `rating` key is present with no value),
`JpaAggregatedTabLookupDaoNullIdTest`, `SearchServiceEmptyBodyTest` (Google and GSA) and
`PortletDelegationViewTest`. Removing a redundant check or adding a message to a site that can't
be null has no test of its own. The module tests and the full suite cover the code around it.

On the branch, every Java test task passes (1363 tests, 0 failed). `spotbugsMain` and
`spotlessCheck` pass on Spring 4.3 too. `uPortal-webcomponents:vpTest` didn't run here, because the
container's Chromium doesn't match the Playwright version. CI runs it.

## Proposed LOG row

| ID | Area | Issue | Resolution |
| --- | --- | --- | --- |
| MOD-I225 | Spring | Spring 5's nullness annotations make SpotBugs report 41 findings in 15 modules on the P4-1 branch: 35 possible null dereferences, 3 nulls passed for non-null parameters and 3 redundant null checks. Null can really reach some sites, and the table marks them "can be null". Four of them threw a `NullPointerException` before the fix. | **Fixed** on `ccr-43ba0785-hzlrj5-spring53-nullness`, one commit per module, each with the module as its scope (this note's table). |
