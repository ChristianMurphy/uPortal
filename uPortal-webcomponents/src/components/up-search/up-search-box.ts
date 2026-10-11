import { css, html, nothing, type PropertyValues, type TemplateResult } from 'lit';
import type { PropertyDeclarations } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import type { Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import { ActorController } from '../../state/actor-controller';
import { searchIcon } from './icons';
import { SEARCH_BOX_MESSAGES } from './messages';
import { navigation, portletUrl } from './navigation';
import { searchBoxMachine, type SearchBoxEmitted } from './up-search-box.machine';

/** The longest query the box accepts, as the Search portlet's box did. */
export const MAX_QUERY_LENGTH = 200;

/** Details of the `up-search-submitted` event. */
export interface SearchSubmittedDetail {
    query: string;
}

/** Details of the `up-suggestion-chosen` event. */
export type SuggestionChosenDetail = Omit<SearchBoxEmitted, 'type'>;

/**
 * The search box of `<up-search>` and `<up-search-launcher>`: a labelled search field and a
 * submit button, with type-ahead over the portlets the user may browse (`/api/portletList`).
 *
 * Submitting a query that is not blank dispatches `up-search-submitted`; the element that holds
 * the box decides what a search does. Choosing a suggestion (click, or Arrow keys and Enter)
 * opens the portlet maximized and dispatches `up-suggestion-chosen`. Escape closes the
 * suggestions.
 *
 * The field stays a search box for assistive technology (not a combobox), as the Search portlet's
 * was; `aria-autocomplete`, `aria-controls` and `aria-activedescendant` connect it to the list of
 * suggestions, and a live region says how many there are.
 *
 * Attributes: those of {@link UpElement}, plus `value`, the query shown in the field.
 */
export class UpSearchBox extends UpElement {
    static override properties: PropertyDeclarations = {
        value: {},
    };

    static override styles = [
        ...UpElement.styles,
        css`
            :host {
                position: relative;
            }

            .input-group {
                flex-wrap: nowrap;
            }

            .submit {
                display: inline-flex;
                align-items: center;
                gap: var(--spacer-2);
            }

            .icon {
                width: 1rem;
                height: 1rem;
            }

            .suggestions {
                position: absolute;
                z-index: 1000;
                inset-inline: 0;
                max-height: 20rem;
                margin: var(--spacer-1) 0 0;
                overflow-y: auto;
                text-align: start;
                box-shadow: var(--box-shadow);
            }

            .suggestion {
                cursor: pointer;
            }

            .suggestion-title {
                display: block;
                font-weight: bold;
            }

            .suggestion-description {
                display: block;
                font-size: var(--font-size-sm);
            }
        `,
    ];

    declare value: string;

    readonly #typeAhead = new ActorController(this, searchBoxMachine, {
        input: () => ({ api: this.api }),
        events: { 'suggestion.chosen': 'up-suggestion-chosen' },
    });

    constructor() {
        super();
        this.value = '';
    }

    protected override get defaultMessages(): Messages {
        return SEARCH_BOX_MESSAGES;
    }

    /** Moves the focus to the search field. */
    override focus(options?: FocusOptions): void {
        this.renderRoot.querySelector('input')?.focus(options);
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        if (this.hasUpdated && changed.has('contextPath')) {
            this.#typeAhead.send({ type: 'api.changed', api: this.api });
        }
    }

    override render(): TemplateResult {
        const snapshot = this.#typeAhead.snapshot;
        const open = snapshot.matches({ popup: 'open' });
        const { matches, active } = snapshot.context;
        const activeId = open && active >= 0 ? `suggestion-${active}` : undefined;
        return html`<form role="search" @submit=${this.#onSubmit}>
            <label class="visually-hidden" for="query">${this.msg('search.terms')}</label>
            <div class="input-group">
                <input
                    id="query"
                    class="form-control"
                    type="search"
                    name="query"
                    .value=${this.value}
                    placeholder=${this.msg('search')}
                    title=${this.msg('search.terms')}
                    maxlength=${MAX_QUERY_LENGTH}
                    autocomplete="off"
                    aria-autocomplete="list"
                    aria-controls="suggestions"
                    aria-activedescendant=${activeId ?? nothing}
                    @input=${this.#onInput}
                    @keydown=${this.#onKeydown}
                    @blur=${this.#onBlur}
                />
                <button class="btn-solid theme-secondary submit" type="submit">
                    <span>${this.msg('search.submit')}</span>${searchIcon}
                </button>
            </div>
            <ul
                id="suggestions"
                class="list-group suggestions"
                role="listbox"
                aria-label=${this.msg('search.suggestions')}
                ?hidden=${!open}
            >
                ${repeat(
                    open ? matches : [],
                    (suggestion) => suggestion.fname,
                    (suggestion, index) =>
                        html`<li
                            id="suggestion-${index}"
                            class="list-group-item list-group-item-action suggestion ${
                                index === active ? 'active' : ''
                            }"
                            role="option"
                            aria-selected=${index === active ? 'true' : 'false'}
                            @mousedown=${this.#keepFocus}
                            @click=${() => {
                                this.#choose(index);
                            }}
                        >
                            <span class="suggestion-title">${suggestion.title}</span>
                            <span class="suggestion-description">${suggestion.description}</span>
                        </li>`,
                )}
            </ul>
            <div class="visually-hidden" role="status" aria-live="polite">
                ${open ? this.msg('search.suggestions.count', matches.length) : ''}
            </div>
        </form>`;
    }

    /** Opens the chosen suggestion's portlet, maximized. */
    #choose(index: number): void {
        const snapshot = this.#typeAhead.snapshot;
        const suggestion = snapshot.context.matches[index];
        const choice = { type: 'choose', index } as const;
        if (suggestion !== undefined && snapshot.can(choice)) {
            this.#typeAhead.send(choice);
            navigation.assign(portletUrl(this.contextPath ?? '', suggestion.fname));
        }
    }

    #onInput = (event: Event): void => {
        if (event.target instanceof HTMLInputElement) {
            this.value = event.target.value;
            this.#typeAhead.send({ type: 'typed', term: this.value });
        }
    };

    #onKeydown = (event: KeyboardEvent): void => {
        const snapshot = this.#typeAhead.snapshot;
        const type =
            event.key === 'ArrowDown'
                ? 'next'
                : event.key === 'ArrowUp'
                  ? 'previous'
                  : event.key === 'Escape'
                    ? 'close'
                    : undefined;
        // Escape also cancels a pause; Arrow keys need open suggestions
        if (type !== undefined && snapshot.can({ type })) {
            event.preventDefault();
            this.#typeAhead.send({ type });
            return;
        }
        if (
            event.key === 'Enter' &&
            snapshot.can({ type: 'choose', index: snapshot.context.active })
        ) {
            event.preventDefault();
            this.#choose(snapshot.context.active);
        }
    };

    /** Pressing a suggestion keeps the focus in the field (see #keepFocus), so blur closes. */
    #onBlur = (): void => {
        this.#typeAhead.send({ type: 'close' });
    };

    /** Keeps the focus in the field when a suggestion is pressed, so the popup stays open. */
    #keepFocus = (event: MouseEvent): void => {
        event.preventDefault();
    };

    #onSubmit = (event: SubmitEvent): void => {
        event.preventDefault();
        const query = this.value.trim();
        if (query !== '') {
            this.#typeAhead.send({ type: 'close' });
            this.emit('up-search-submitted', { query } satisfies SearchSubmittedDetail);
        }
    };
}

if (!customElements.get('up-search-box')) {
    customElements.define('up-search-box', UpSearchBox);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-search-box': UpSearchBox;
    }
}
