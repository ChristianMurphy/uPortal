# Upgrading to uPortal 6.0

uPortal 6.0 moves the persistence, Spring and servlet stacks forward in steps. This page lists
what adopters have to check or change when they upgrade an existing portal. Sections are added
as the steps land.

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

For example, `org.springframework.web.portlet.DispatcherPortlet` becomes
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
`PortletModeNameViewController` and `PortletWrappingController`; nor does Web Flow's
`PortletMvcViewFactory`.

The copied classes log under their new package names. If your logging configuration sets
levels for the old packages, change it to the new ones. This includes the "page not found"
category, now `org.apereo.portal.spring.web.portlet.PageNotFound`.

Portlet applications deployed as separate web applications (Announcements, Calendar and the
other portlet WARs) are not affected: they bring their own Spring jars.
