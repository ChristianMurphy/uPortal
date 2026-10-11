import { css, html, nothing, svg, type PropertyValues, type TemplateResult } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import { waitFor } from 'xstate';
import { formatMessage, type MessageArg, type Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import { ActorController } from '../../state/actor-controller';
import { decodeEntities, POPULAR_PORTLETS_MESSAGES } from './messages';
import type { PopularPortlet } from './popular-portlets-api';
import {
    DAY_CHOICES,
    filterOptions,
    PAGE_SIZES,
    pageButtons,
    tableView,
    type Column,
    type TableView,
} from './table';
import {
    popularPortletsMachine,
    type FocusRequest,
    type PopularPortletsContext,
    type PopularPortletsEmitted,
} from './up-popular-portlets.machine';

/** The select element a change event came from. */
function selectOf(event: Event): HTMLSelectElement {
    const select = event.currentTarget;
    /* v8 ignore if -- unreachable: the handlers are bound to select elements only */
    if (!(select instanceof HTMLSelectElement)) {
        throw new TypeError('not a select element');
    }
    return select;
}

/** Details of the `up-popular-portlets-loaded` event. */
export type PopularPortletsLoadedDetail = Omit<PopularPortletsEmitted, 'type'>;

const COLUMNS: readonly { column: Column; label: 'title' | 'number.times' }[] = [
    { column: 'title', label: 'title' },
    { column: 'count', label: 'number.times' },
];

const sortIcon = (direction: 'ascending' | 'descending' | undefined) =>
    html`<svg class="sort-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
        ${
            direction === 'descending'
                ? nothing
                : svg`<path d="M8 3l4 5H4z" fill=${direction === 'ascending' ? 'currentColor' : 'none'} stroke="currentColor"/>`
        }
        ${
            direction === 'ascending'
                ? nothing
                : svg`<path d="M8 13l4-5H4z" fill=${direction === 'descending' ? 'currentColor' : 'none'} stroke="currentColor"/>`
        }
    </svg>`;

/**
 * The portlets users added to their layouts most often, the web-component replacement of the
 * Popular Portlets portlet. It reads them from `/api/v6-0/popular-portlets`.
 *
 * The user chooses how many previous days to count (1, 7, 30, 90 or 365). The table links each
 * portlet to its page and shows how many times it was added; the column headers sort it, the
 * filters keep the rows with the chosen fnames or counts, and the paging controls choose the page
 * and the number of rows per page.
 *
 * Its states are the XState machine in `up-popular-portlets.machine.ts`, run by an
 * {@link ActorController}: the template renders from the snapshot, and user actions are events.
 *
 * Attributes: those of {@link UpElement}, plus `days`, the number of days counted first (30 when
 * absent or not one of the choices).
 *
 * Events: `up-popular-portlets-loaded`.
 */
export class UpPopularPortlets extends UpElement {
    static override properties = {
        days: { type: Number },
    };

    static override styles = [
        ...UpElement.styles,
        css`
            .popular {
                display: flex;
                flex-direction: column;
                gap: var(--spacer-3);
            }

            h2 {
                margin: 0;
                font-size: var(--font-size-lg, 1.25rem);
            }

            .toolbar,
            .paging-row,
            .page-size,
            .filter-widgets,
            .filter-widget {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                gap: var(--spacer-2);
            }

            .controls {
                display: flex;
                flex-direction: column;
                gap: var(--spacer-2);
                padding: var(--spacer-3);
                border: 1px solid var(--border-color);
                border-radius: var(--border-radius);
            }

            select.form-control {
                width: auto;
                /* A filter lists fnames, which can be long: the select and the flex items
                   around it may shrink below their longest option */
                min-width: 0;
                max-width: 100%;
            }

            .filter-widgets,
            .filter-widget {
                min-width: 0;
                max-width: 100%;
            }

            .filter-term {
                padding: 0 var(--spacer-2);
            }

            .pagination {
                flex-wrap: wrap;
                margin: 0;
            }

            .table {
                margin: 0;
            }

            th {
                padding: 0;
            }

            .sort {
                display: flex;
                align-items: center;
                justify-content: space-between;
                width: 100%;
                padding: var(--spacer-2);
                font-weight: inherit;
                text-align: start;
                color: inherit;
                background: none;
                border: 0;
            }

            .sort:focus-visible {
                outline: var(--focus-ring);
            }

            .sort-icon {
                flex: none;
                width: 0.75rem;
                height: 0.75rem;
            }

            .alert p,
            .note {
                margin: 0;
            }

            .note {
                color: var(--fg-2);
                font-size: var(--font-size-sm);
            }

            .loading {
                display: flex;
                align-items: center;
                gap: var(--spacer-3);
            }
        `,
    ];

    declare days: number | undefined;

    readonly #machine = new ActorController(this, popularPortletsMachine, {
        // Properties in: at the first connect; later changes as events (willUpdate)
        input: () => ({ api: this.api, locale: this.resolvedLocale, days: this.days }),
        events: { 'popular-portlets.loaded': 'up-popular-portlets-loaded' },
    });

    /** The focus request applied last, so each one moves focus once. */
    #focused = 0;

    /**
     * Loads the same days again, showing the loading state meanwhile. Resolves once they are
     * loaded (or failed to load), or when the element leaves the page first.
     */
    async reload(): Promise<void> {
        this.#machine.send({ type: 'reload' });
        const actor = this.#machine.actorRef;
        if (actor === undefined) {
            return;
        }
        try {
            await waitFor(actor, (snapshot) => !snapshot.matches('loading'));
        } catch {
            // The element was disconnected (its actor stopped) before the portlets arrived
        }
    }

    protected override get defaultMessages(): Messages {
        return POPULAR_PORTLETS_MESSAGES;
    }

    /** Messages.properties holds some of these texts as HTML; they are shown as text. */
    protected override msg(key: string, ...args: MessageArg[]): string {
        const pattern = this.localizedMessages[key] ?? this.defaultMessages[key] ?? key;
        return formatMessage(decodeEntities(pattern), args, this.resolvedLocale);
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        if (!this.hasUpdated) {
            return;
        }
        if (changed.has('contextPath') || changed.has('csrfHeader') || changed.has('csrfToken')) {
            this.#machine.send({ type: 'api.changed', api: this.api });
        }
        if (changed.has('locale')) {
            this.#machine.send({ type: 'locale.changed', locale: this.resolvedLocale });
        }
        if (changed.has('days') && this.days !== undefined) {
            this.#machine.send({ type: 'days.change', days: this.days });
        }
    }

    override render(): TemplateResult {
        const snapshot = this.#machine.snapshot;
        const { days } = snapshot.context;
        return html`<div class="popular">
            <h2>${this.msg('most.frequently.added')}</h2>
            <div class="toolbar">
                <label for="days" class="form-label">${this.msg('previous')}</label>
                <select
                    id="days"
                    class="form-control form-control-sm"
                    name="days"
                    @change=${this.#daysChanged}
                >
                    ${DAY_CHOICES.map(
                        (choice) =>
                            html`<option value=${choice} ?selected=${choice === days}>
                                ${choice}
                            </option>`,
                    )}
                </select>
            </div>
            ${this.#renderBody()}
            <p class="note">${this.msg('popular.portlets.aggregation.note')}</p>
        </div>`;
    }

    protected override updated(changed: PropertyValues<this>): void {
        super.updated(changed);
        const focus = this.#machine.snapshot.context.focus;
        if (focus !== undefined && focus.id !== this.#focused) {
            this.#focused = focus.id;
            this.#focus(focus);
        }
    }

    #renderBody(): TemplateResult {
        const snapshot = this.#machine.snapshot;
        if (snapshot.matches('loading')) {
            return html`<div class="loading" role="status">
                <span class="spinner-border" aria-hidden="true"></span>
                <span>${this.msg('loading')}</span>
            </div>`;
        }
        if (snapshot.matches({ failed: 'sessionExpired' })) {
            return html`<div class="alert theme-warning" role="alert">
                <p>${this.msg('popular.portlets.session.expired')}</p>
            </div>`;
        }
        if (snapshot.matches('failed')) {
            return html`<div class="alert theme-danger" role="alert">
                <p>${this.msg('popular.portlets.load.fail')}</p>
                <button
                    type="button"
                    class="btn-solid theme-danger btn-sm retry"
                    @click=${this.#reloadClicked}
                >
                    ${this.msg('popular.portlets.try.again')}
                </button>
            </div>`;
        }
        const { context } = snapshot;
        const view = tableView(
            context.portlets,
            context.filters,
            context.sort,
            context.pageSize,
            context.page,
        );
        return html`<div class="controls">
                ${snapshot.matches('list') ? this.#renderFilters(context) : nothing}
                ${this.#renderPaging(view, context.pageSize, snapshot.matches('list'))}
            </div>
            ${this.#renderTable(view, context)}`;
    }

    #renderFilters(context: PopularPortletsContext): TemplateResult {
        return html`<div class="filters">
            <span><b>${this.msg('popular.portlets.filters')}</b>:</span>
            <div class="filter-widgets">
                ${COLUMNS.map(({ column, label }) => {
                    const options = filterOptions(context.portlets, column);
                    return html`<div class="filter-widget">
                        <select
                            class="form-control form-control-sm"
                            data-filter=${column}
                            aria-label=${this.msg('popular.portlets.filter.by', this.msg(label))}
                            ?disabled=${options.length <= 1}
                            @change=${(event: Event) => {
                                this.#filterChosen(event, column);
                            }}
                        >
                            <option value="">${this.msg(label)}</option>
                            ${options.map((value) => html`<option value=${value}>${value}</option>`)}
                        </select>
                        ${repeat(
                            context.filters[column],
                            (value) => value,
                            (value) =>
                                html`<button
                                    type="button"
                                    class="btn-outline theme-secondary btn-sm filter-term"
                                    aria-label=${this.msg('popular.portlets.filter.remove', value)}
                                    @click=${() => {
                                        this.#machine.send({
                                            type: 'filter.remove',
                                            column,
                                            value,
                                        });
                                    }}
                                >
                                    ${value} <span aria-hidden="true">×</span>
                                </button>`,
                        )}
                    </div>`;
                })}
            </div>
        </div>`;
    }

    #renderPaging(view: TableView, pageSize: number, enabled: boolean): TemplateResult {
        const previous = !enabled || view.page === 0;
        const next = !enabled || view.page >= view.pageCount - 1;
        const info = this.msgPlural(
            'popular.portlets.page.info',
            view.total,
            view.page + 1,
            view.from,
            view.to,
            view.total,
        );
        return html`<div class="paging-row">
            <div id="info" class="info" role="status" aria-live="polite">${info}</div>
            <div class="page-size">
                <select
                    id="page-size"
                    class="form-control form-control-sm"
                    ?disabled=${!enabled}
                    @change=${this.#pageSizeChanged}
                >
                    ${PAGE_SIZES.map(
                        (size) =>
                            html`<option value=${size} ?selected=${size === pageSize}>
                                ${size}
                            </option>`,
                    )}
                </select>
                <label for="page-size">${this.msg('popular.portlets.per.page')}</label>
            </div>
            <nav aria-label=${this.msg('popular.portlets.pagination')}>
                <ul class="pagination pagination-sm">
                    ${this.#renderPageLink(
                        'previous',
                        this.msg('datatables.paginate.previous'),
                        this.msg('previous'),
                        previous,
                        view.page - 1,
                    )}
                    ${pageButtons(view.page, view.pageCount).map((page) =>
                        page === null
                            ? html`<li class="page-item disabled">
                                  <span class="page-link" aria-hidden="true">…</span>
                              </li>`
                            : html`<li class="page-item ${page === view.page ? 'active' : ''}">
                                  <button
                                      type="button"
                                      class="page-link"
                                      data-page=${page}
                                      aria-current=${page === view.page ? 'page' : nothing}
                                      @click=${() => {
                                          this.#machine.send({ type: 'page.go', page });
                                      }}
                                  >
                                      ${page + 1}
                                  </button>
                              </li>`,
                    )}
                    ${this.#renderPageLink(
                        'next',
                        this.msg('datatables.paginate.next'),
                        this.msg('next'),
                        next,
                        view.page + 1,
                    )}
                </ul>
            </nav>
        </div>`;
    }

    #renderPageLink(
        control: 'previous' | 'next',
        text: string,
        label: string,
        disabled: boolean,
        page: number,
    ): TemplateResult {
        return html`<li class="page-item ${disabled ? 'disabled' : ''}">
            <button
                type="button"
                class="page-link ${control}"
                aria-label=${label}
                aria-disabled=${disabled ? 'true' : nothing}
                tabindex=${disabled ? -1 : nothing}
                @click=${() => {
                    if (!disabled) {
                        this.#machine.send({ type: 'page.go', page });
                    }
                }}
            >
                ${text}
            </button>
        </li>`;
    }

    #renderTable(view: TableView, context: PopularPortletsContext): TemplateResult {
        const { sort } = context;
        return html`<table class="table" aria-describedby="info">
            <thead>
                <tr>
                    ${COLUMNS.map(({ column, label }) => {
                        const direction = sort.column === column ? sort.direction : undefined;
                        return html`<th scope="col" aria-sort=${direction ?? nothing}>
                            <button
                                type="button"
                                class="sort"
                                data-sort=${column}
                                @click=${() => {
                                    this.#machine.send({ type: 'sort', column });
                                }}
                            >
                                <span>${this.msg(label)}</span>${sortIcon(direction)}
                            </button>
                        </th>`;
                    })}
                </tr>
            </thead>
            <tbody>
                ${
                    view.rows.length === 0
                        ? html`<tr>
                              <td colspan="2" class="none">
                                  ${this.msg(
                                      context.portlets.length === 0
                                          ? 'popular.portlets.none'
                                          : 'popular.portlets.none.matching',
                                  )}
                              </td>
                          </tr>`
                        : repeat(
                              view.rows,
                              (portlet) => portlet.fname,
                              (portlet) => this.#renderRow(portlet),
                          )
                }
            </tbody>
        </table>`;
    }

    #renderRow(portlet: PopularPortlet): TemplateResult {
        const title =
            portlet.url === null
                ? html`${portlet.title}`
                : html`<a href=${portlet.url} title=${portlet.description ?? nothing}
                      >${portlet.title}</a
                  >`;
        return html`<tr data-fname=${portlet.fname}>
            <td>${title}</td>
            <td>${portlet.count}</td>
        </tr>`;
    }

    #reloadClicked = (): void => {
        this.#machine.send({ type: 'reload' });
    };

    #daysChanged = (event: Event): void => {
        const select = selectOf(event);
        this.#machine.send({ type: 'days.change', days: Number(select.value) });
    };

    #pageSizeChanged = (event: Event): void => {
        const select = selectOf(event);
        this.#machine.send({ type: 'page.size', size: Number(select.value) });
    };

    #filterChosen(event: Event, column: Column): void {
        const select = selectOf(event);
        const value = select.value;
        // The filter offers its values again, as the portlet's did
        select.value = '';
        if (value !== '') {
            this.#machine.send({ type: 'filter.add', column, value });
        }
    }

    #focus(request: FocusRequest): void {
        this.renderRoot
            .querySelector<HTMLElement>(`select[data-filter="${request.filter}"]`)
            ?.focus();
    }
}

if (!customElements.get('up-popular-portlets')) {
    customElements.define('up-popular-portlets', UpPopularPortlets);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-popular-portlets': UpPopularPortlets;
    }
}
