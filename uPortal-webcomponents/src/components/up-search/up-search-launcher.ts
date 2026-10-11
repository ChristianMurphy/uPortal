import { css, html, nothing, type TemplateResult } from 'lit';
import type { PropertyDeclarations } from 'lit';
import { UpElement } from '../../lib/up-element';
import { navigation } from './navigation';
import type { SearchSubmittedDetail } from './up-search-box';
import './up-search-box';

/** Where the launcher sends a query: the search channel, maximized, with `?query=`. */
export function searchUrl(contextPath: string, searchFname: string, query: string): string {
    return `${contextPath}/p/${encodeURIComponent(searchFname)}/max?${new URLSearchParams({ query }).toString()}`;
}

/**
 * The search box of the page header, the web-component replacement of the Search portlet's
 * launcher view. A search opens the search channel (`<up-search>`) maximized with the query; the
 * box suggests portlets as the user types.
 *
 * Attributes: those of {@link UpElement}, plus `search-fname`, the fname of the search channel
 * (`search` unless set; the Search portlet's `searchLaunchFname` preference).
 */
export class UpSearchLauncher extends UpElement {
    static override properties: PropertyDeclarations = {
        searchFname: { attribute: 'search-fname', reflect: true },
    };

    static override styles = [
        ...UpElement.styles,
        css`
            :host {
                max-width: 24rem;
                margin-inline-start: auto;
            }
        `,
    ];

    declare searchFname: string | undefined;

    override render(): TemplateResult {
        return html`<up-search-box
            locale=${this.locale ?? nothing}
            context-path=${this.contextPath ?? nothing}
            @up-search-submitted=${this.#onSubmitted}
        ></up-search-box>`;
    }

    #onSubmitted = (event: CustomEvent<SearchSubmittedDetail>): void => {
        navigation.assign(
            searchUrl(this.contextPath ?? '', this.searchFname || 'search', event.detail.query),
        );
    };
}

if (!customElements.get('up-search-launcher')) {
    customElements.define('up-search-launcher', UpSearchLauncher);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-search-launcher': UpSearchLauncher;
    }
}
