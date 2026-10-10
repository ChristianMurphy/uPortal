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
