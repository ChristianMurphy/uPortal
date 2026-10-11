/**
 * The page navigation the search components do, in one place so that tests can replace it:
 * opening a URL, and reading or updating the query in the page's URL.
 *
 * The launcher opens `/p/search/max?query=...`. The portal answers that with a redirect to the
 * channel's render URL, where the parameter becomes the portlet parameter `pP_query`
 * (`/p/search.ctf2/max/render.uP?pP_query=...`), as it did for the Search portlet. So the query is
 * read from `pP_query`, else from `query`.
 */

/** The portlet parameter the portal turns `query` into on the channel's render URL. */
export const PORTLET_QUERY_PARAMETER = 'pP_query';
export interface Navigation {
    /** Opens `url` in this window. */
    assign(url: string): void;
    /** The page URL's `pP_query` parameter, else its `query` parameter, or null. */
    query(): string | null;
    /**
     * Sets the query in the page URL, in the parameter the URL already uses (`pP_query` on the
     * channel's render URL, else `query`), without a new history entry.
     */
    replaceQuery(query: string): void;
}

/** What {@link createNavigation} uses of a window. */
export interface NavigationWindow {
    location: { href: string; assign(url: string): void };
    history: { state: unknown; replaceState(state: unknown, unused: string, url: string): void };
}

export function createNavigation(view: NavigationWindow): Navigation {
    return {
        assign(url) {
            view.location.assign(url);
        },
        query() {
            const parameters = new URL(view.location.href).searchParams;
            return parameters.get(PORTLET_QUERY_PARAMETER) ?? parameters.get('query');
        },
        replaceQuery(query) {
            const url = new URL(view.location.href);
            url.searchParams.set(
                url.searchParams.has(PORTLET_QUERY_PARAMETER) ? PORTLET_QUERY_PARAMETER : 'query',
                query,
            );
            view.history.replaceState(view.history.state, '', url.href);
        },
    };
}

/** The navigation of this window; tests spy on its methods. */
export const navigation: Navigation = createNavigation(window);

/** Where a portlet opens maximized, as the Search portlet's type-ahead opened it. */
export function portletUrl(contextPath: string, fname: string): string {
    return `${contextPath}/p/${encodeURIComponent(fname)}/max`;
}
