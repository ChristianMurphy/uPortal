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

uPortal's Spring XML contexts and Web Flow definitions now name the unversioned schemas, for
example `http://www.springframework.org/schema/beans/spring-beans.xsd` instead of
`spring-beans-3.1.xsd`, and `spring-security.xsd` instead of `spring-security-4.2.xsd`. Spring
resolves an unversioned schema to the one that matches the jar on the classpath, so the files keep
working as the Spring version moves.

If your overlay adds or replaces Spring contexts, make the same change there. It is required for
files that use the `security` namespace: Spring Security 5 refuses to load a file that names
`spring-security-4.2.xsd` or an older version. For the other namespaces it is recommended; Spring
still maps the old versioned names to the current schema.

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
