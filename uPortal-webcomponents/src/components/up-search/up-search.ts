import { css, html, nothing, type PropertyValues, type TemplateResult } from 'lit';
import type { PropertyDeclarations } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import type { Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import { ActorController } from '../../state/actor-controller';
import { personIcon, portletIcon } from './icons';
import { SEARCH_MESSAGES, SHOWN_ATTRIBUTES } from './messages';
import { navigation } from './navigation';
import type { SearchResult, SearchResultGroup } from './search-api';
import type { SearchSubmittedDetail } from './up-search-box';
import { searchMachine, type SearchEmitted } from './up-search.machine';
// The module defines both search elements and the box they share
import './up-search-box';
import './up-search-launcher';

/** Details of the `up-search-completed` event. */
export type SearchCompletedDetail = Omit<SearchEmitted, 'type'>;

/** Reads the `result-types` attribute: a JSON array (several values) or one type. */
export function parseResultTypes(value: string | undefined): string[] {
    const trimmed = value?.trim() ?? '';
    if (trimmed === '') {
        return [];
    }
    if (trimmed.startsWith('[')) {
        try {
            const parsed: unknown = JSON.parse(trimmed);
            if (Array.isArray(parsed)) {
                return parsed.filter(
                    (type): type is string => typeof type === 'string' && type !== '',
                );
            }
        } catch {
            // Not JSON after all: one type that starts with a bracket
        }
    }
    return [trimmed];
}

/** "peopleTab" to "People Tab", for result types without a message of their own. */
export function startCase(type: string): string {
    return type
        .replace(/([a-z])([A-Z])/g, '$1 $2')
        .replace(/[-_\s]+/g, ' ')
        .trim()
        .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

/**
 * Search, the web-component replacement of the Search portlet: a search box and the results of
 * `/api/v6-0/search`, one tab per result type ("People", "Portlets").
 *
 * The query comes from the `query` attribute, else from the page URL's `query` parameter, which
 * `<up-search-launcher>` sets. A search from the element's own box runs in place and updates that
 * parameter. A result type whose search failed (a remote provider that did not answer, say) shows
 * an error in its own tab. Each result shows its title, linked when it opens something (a portlet, maximized),
 * and the attributes the Search portlet showed (net ID, names, email, phone, name, description,
 * relevance).
 *
 * Its states are the XState machine in `up-search.machine.ts`, run by an {@link ActorController}.
 *
 * Attributes: those of {@link UpElement}, plus `query` and `result-types` (the result types to
 * search, a JSON array or one type; the Search portlet's `RESTSearch.type` preference).
 *
 * Events: `up-search-completed`.
 */
export class UpSearch extends UpElement {
    static override properties: PropertyDeclarations = {
        query: { reflect: true },
        resultTypes: { attribute: 'result-types' },
    };

    static override styles = [
        ...UpElement.styles,
        css`
            .search {
                display: flex;
                flex-direction: column;
                gap: var(--spacer-3);
            }

            .loading {
                display: flex;
                align-items: center;
                gap: var(--spacer-3);
            }

            .alert p {
                margin: 0;
            }

            .nav {
                justify-content: center;
            }

            .result {
                display: flex;
                gap: var(--spacer-3);
            }

            .result .icon {
                flex: none;
                width: 2rem;
                height: 2rem;
                color: var(--fg-2);
            }

            .result-title {
                margin: 0 0 var(--spacer-2);
                font-size: var(--font-size-lg);
                font-weight: bold;
            }

            dl {
                margin: 0;
            }

            dt {
                display: inline;
                font-weight: bold;
            }

            dd {
                display: inline;
                margin: 0 0 0 var(--spacer-1);
                overflow-wrap: anywhere;
            }

            dd::after {
                display: block;
                content: '';
            }

            .no-results {
                margin: var(--spacer-3) 0;
                font-size: var(--font-size-lg);
                text-align: center;
            }
        `,
    ];

    declare query: string | undefined;
    declare resultTypes: string | undefined;

    readonly #search = new ActorController(this, searchMachine, {
        // Properties in: the query, types and API client at the first connect
        input: () => ({
            api: this.api,
            query: this.query ?? navigation.query() ?? '',
            types: parseResultTypes(this.resultTypes),
        }),
        events: { 'search.completed': 'up-search-completed' },
    });

    /** Set when the keyboard changed the tab, so the new tab gets the focus. */
    #focusTab = false;

    protected override get defaultMessages(): Messages {
        return SEARCH_MESSAGES;
    }

    /** Searches for `query`, as if the user had submitted it in the element's search box. */
    search(query: string): void {
        if (query.trim() !== '') {
            this.query = query.trim();
            navigation.replaceQuery(this.query);
            this.#search.send({ type: 'search', query });
        }
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        if (this.hasUpdated && changed.has('contextPath')) {
            this.#search.send({ type: 'api.changed', api: this.api });
        }
    }

    override render(): TemplateResult {
        const snapshot = this.#search.snapshot;
        return html`<div class="search" aria-busy=${snapshot.hasTag('busy') ? 'true' : nothing}>
            <up-search-box
                locale=${this.locale ?? nothing}
                context-path=${this.contextPath ?? nothing}
                .value=${snapshot.context.query}
                @up-search-submitted=${this.#onSubmitted}
            ></up-search-box>
            ${this.#renderState()}
        </div>`;
    }

    protected override updated(changed: PropertyValues<this>): void {
        super.updated(changed);
        if (this.#focusTab) {
            this.#focusTab = false;
            this.renderRoot
                .querySelector<HTMLElement>('[role="tab"][aria-selected="true"]')
                ?.focus();
        }
    }

    #renderState(): TemplateResult | typeof nothing {
        const snapshot = this.#search.snapshot;
        if (snapshot.matches('searching')) {
            return html`<div class="loading" role="status">
                <span class="spinner-border" aria-hidden="true"></span>
                <span>${this.msg('loading')}</span>
            </div>`;
        }
        if (snapshot.matches({ failed: 'sessionExpired' })) {
            return html`<div class="alert theme-warning" role="alert">
                <p>${this.msg('search.session.expired')}</p>
            </div>`;
        }
        if (snapshot.matches('failed')) {
            return html`<div class="alert theme-danger" role="alert">
                <p>${this.msg('search.load.fail')}</p>
                <button
                    type="button"
                    class="btn-solid theme-danger btn-sm retry"
                    @click=${this.#retry}
                >
                    ${this.msg('search.try.again')}
                </button>
            </div>`;
        }
        const { results, selected } = snapshot.context;
        if (!snapshot.matches('results') || results === undefined) {
            return nothing;
        }
        const total = results.groups.reduce((sum, group) => sum + group.results.length, 0);
        const status = html`<div class="visually-hidden" role="status">
            ${this.msg('search.results.found', total, results.query)}
        </div>`;
        const group = results.groups.find((candidate) => candidate.type === selected);
        if (group === undefined) {
            return html`${status}${this.#renderNoResults()}`;
        }
        return html`${status}
            <div
                class="nav nav-pills"
                role="tablist"
                aria-label=${this.msg('search.results')}
                @keydown=${this.#onTabKeydown}
            >
                ${repeat(
                    results.groups,
                    (candidate) => candidate.type,
                    (candidate) => {
                        const active = candidate.type === selected;
                        return html`<button
                            type="button"
                            class="nav-link ${active ? 'active' : ''}"
                            role="tab"
                            id="tab-${candidate.type}"
                            data-type=${candidate.type}
                            aria-selected=${active ? 'true' : 'false'}
                            aria-controls="panel"
                            tabindex=${active ? 0 : -1}
                            @click=${() => {
                                this.#select(candidate.type);
                            }}
                        >
                            ${candidate.label ?? this.#groupLabel(candidate.type)}
                        </button>`;
                    },
                )}
            </div>
            <div id="panel" role="tabpanel" aria-labelledby="tab-${group.type}" tabindex="0">
                ${this.#renderGroup(group)}
            </div>`;
    }

    #renderGroup(group: SearchResultGroup): TemplateResult {
        if (group.failed) {
            return html`<div class="alert theme-warning group-failed" role="alert">
                <p>${this.msg('search.group.fail')}</p>
            </div>`;
        }
        if (group.results.length === 0) {
            return this.#renderNoResults();
        }
        const icon = group.type === 'people' ? personIcon : portletIcon;
        return html`<ul class="list-group results">
            ${group.results.map(
                (result) =>
                    html`<li class="list-group-item result">
                        ${icon}
                        <div>
                            <h3 class="result-title">
                                ${result.url === null ? result.title : html`<a href=${result.url}>${result.title}</a>`}
                            </h3>
                            ${this.#renderAttributes(result)}
                        </div>
                    </li>`,
            )}
        </ul>`;
    }

    #renderAttributes(result: SearchResult): TemplateResult | typeof nothing {
        const shown = Object.entries(result.attributes).filter(
            ([name, values]) => SHOWN_ATTRIBUTES[name] !== undefined && values.length > 0,
        );
        if (shown.length === 0) {
            return nothing;
        }
        return html`<dl>
            ${shown.map(
                ([name, values]) =>
                    html`<dt>${this.msg(SHOWN_ATTRIBUTES[name] ?? name)}:</dt>
                        <dd>${values.join(', ')}</dd>`,
            )}
        </dl>`;
    }

    #renderNoResults(): TemplateResult {
        return html`<p class="no-results">${this.msg('search.no.results.found')}</p>`;
    }

    #groupLabel(type: string): string {
        const key = `search.results.${type}`;
        return key in SEARCH_MESSAGES ? this.msg(key) : startCase(type);
    }

    #select(resultType: string): void {
        this.#search.send({ type: 'tab.select', resultType });
    }

    #onSubmitted = (event: CustomEvent<SearchSubmittedDetail>): void => {
        this.search(event.detail.query);
    };

    #retry = (): void => {
        this.#search.send({ type: 'retry' });
    };

    /** Arrow Left and Right, Home and End move between the tabs, as the ARIA tabs pattern says. */
    #onTabKeydown = (event: KeyboardEvent): void => {
        const { results, selected } = this.#search.snapshot.context;
        const types = results?.groups.map((group) => group.type) ?? [];
        const index = types.indexOf(selected ?? '');
        const next =
            event.key === 'ArrowRight'
                ? (index + 1) % types.length
                : event.key === 'ArrowLeft'
                  ? (index - 1 + types.length) % types.length
                  : event.key === 'Home'
                    ? 0
                    : event.key === 'End'
                      ? types.length - 1
                      : undefined;
        const type = next === undefined ? undefined : types[next];
        if (type !== undefined) {
            event.preventDefault();
            this.#focusTab = true;
            this.#select(type);
            // The selected tab may not change (Home on the first tab); focus it all the same
            this.requestUpdate();
        }
    };
}

if (!customElements.get('up-search')) {
    customElements.define('up-search', UpSearch);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-search': UpSearch;
    }
}
