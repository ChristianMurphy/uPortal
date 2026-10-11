/**
 * The page navigation the search components do, in one place so that tests can replace it:
 * opening a URL, and reading or updating the `query` parameter of the page's URL.
 */
export interface Navigation {
    /** Opens `url` in this window. */
    assign(url: string): void;
    /** The page URL's `query` parameter, or null. */
    query(): string | null;
    /** Sets the page URL's `query` parameter without a new history entry. */
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
            return new URL(view.location.href).searchParams.get('query');
        },
        replaceQuery(query) {
            const url = new URL(view.location.href);
            url.searchParams.set('query', query);
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
