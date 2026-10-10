import {
    css,
    html,
    nothing,
    type PropertyDeclarations,
    type PropertyValues,
    type TemplateResult,
} from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import { styleMap } from 'lit/directives/style-map.js';
import { ApiError, type ApiErrorKind } from '../../lib/api-client';
import type { Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import {
    fetchFavorites,
    moveFavorite,
    removeFavorite,
    type Favorite,
    type FavoritesData,
} from './favorites-api';
import { arrowDownIcon, arrowUpIcon, folderIcon, lockIcon, portletIcon, starIcon } from './icons';
import { FAVORITES_MESSAGES } from './messages';

type ViewState =
    | { status: 'loading' }
    | { status: 'error'; kind: ApiErrorKind }
    | { status: 'ready'; favorites: FavoritesData };

interface Notice {
    theme: 'success' | 'danger';
    text: string;
}

/** The focusable parts of a favorite, marked with `data-control`. */
type Control = 'link' | 'up' | 'down' | 'remove';

type FocusRequest = { nodeId: string; controls: Control[] } | { selector: string };

/** Details of the `up-favorite-removed` event. */
export interface FavoriteRemovedDetail {
    nodeId: string;
    name: string;
    kind: Favorite['kind'];
}

/** Details of the `up-favorite-moved` event. */
export interface FavoriteMovedDetail {
    nodeId: string;
    name: string;
    /** The favorite's new place among favorites of its kind, from 0. */
    position: number;
}

/** Details of the `up-favorites-loaded` event. */
export interface FavoritesLoadedDetail {
    collections: number;
    portlets: number;
}

function errorKind(error: unknown): ApiErrorKind {
    return error instanceof ApiError ? error.kind : 'malformed';
}

/**
 * The user's favorite collections and portlets, the web-component replacement of the Favorites
 * portlet. It reads and changes them through `/api/v6-0/favorites`.
 *
 * Each favorite links to its collection or portlet; the star removes it. "Reorder favorites"
 * shows buttons that move a favorite up or down among favorites of its kind, which Alt+Up and
 * Alt+Down also do; Escape stops reordering. A lock marks favorites the layout does not let the
 * user remove. Maximized, the component also shows each portlet's description.
 *
 * Attributes: those of {@link UpElement}, plus `max-height-pixels`, which limits the height of
 * the list (the Favorites portlet's `FavoritesController.maxHeightPixels` preference).
 *
 * Events: `up-favorites-loaded`, `up-favorite-removed` and `up-favorite-moved`.
 */
export class UpFavorites extends UpElement {
    static override properties: PropertyDeclarations = {
        maxHeightPixels: { attribute: 'max-height-pixels', type: Number, reflect: true },
        view: { state: true },
        editing: { state: true },
        notice: { state: true },
        announcement: { state: true },
    };

    static override styles = [
        ...UpElement.styles,
        css`
            .favorites {
                display: flex;
                flex-direction: column;
                gap: var(--spacer-3);
            }

            .toolbar {
                display: flex;
                justify-content: flex-end;
            }

            .favorite {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                gap: var(--spacer-3);
            }

            .favorite-link {
                display: flex;
                flex: 1 1 0;
                align-items: center;
                gap: var(--spacer-3);
                min-width: 0;
            }

            .favorite-name {
                overflow-wrap: anywhere;
            }

            .icon,
            .favorite-image {
                flex: none;
                width: 1.25rem;
                height: 1.25rem;
            }

            .favorite-description {
                flex-basis: 100%;
                margin: 0;
                color: var(--fg-2);
                font-size: var(--font-size-sm);
            }

            .favorite-actions {
                display: flex;
                flex: none;
                align-items: center;
                gap: var(--spacer-1);
            }

            .lock {
                display: inline-flex;
                color: var(--fg-2);
            }

            .alert p,
            .help,
            .legend,
            .instruction {
                margin: 0;
            }

            .help,
            .legend,
            .instruction {
                color: var(--fg-2);
                font-size: var(--font-size-sm);
            }

            .footer {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                justify-content: space-between;
                gap: var(--spacer-3);
            }

            .loading {
                display: flex;
                align-items: center;
                gap: var(--spacer-3);
            }

            .empty:focus {
                outline: var(--focus-ring);
            }
        `,
    ];

    declare maxHeightPixels: number | undefined;
    declare protected view: ViewState;
    declare protected editing: boolean;
    declare protected notice: Notice | undefined;
    declare protected announcement: string;

    #busy = false;
    #focusRequest: FocusRequest | undefined;

    constructor() {
        super();
        this.view = { status: 'loading' };
        this.editing = false;
        this.announcement = '';
    }

    protected override get defaultMessages(): Messages {
        return FAVORITES_MESSAGES;
    }

    override connectedCallback(): void {
        super.connectedCallback();
        void this.reload();
    }

    /** Loads the favorites again, showing the loading state meanwhile. */
    async reload(): Promise<void> {
        this.view = { status: 'loading' };
        try {
            const favorites = await fetchFavorites(this.api);
            this.view = { status: 'ready', favorites };
            this.emit('up-favorites-loaded', {
                collections: favorites.collections.length,
                portlets: favorites.portlets.length,
            } satisfies FavoritesLoadedDetail);
        } catch (error) {
            this.view = { status: 'error', kind: errorKind(error) };
        }
    }

    override render(): TemplateResult {
        const view = this.view;
        if (view.status === 'error') {
            return this.#renderError(view.kind);
        }
        if (view.status === 'ready') {
            return this.#renderFavorites(view.favorites);
        }
        return html`<div class="loading" role="status">
            <span class="spinner-border" aria-hidden="true"></span>
            <span>${this.msg('loading')}</span>
        </div>`;
    }

    protected override updated(changed: PropertyValues<this>): void {
        super.updated(changed);
        const request = this.#focusRequest;
        this.#focusRequest = undefined;
        if (request !== undefined) {
            this.#focus(request);
        }
    }

    #renderError(kind: ApiErrorKind): TemplateResult {
        if (kind === 'unauthorized') {
            return html`<div class="alert theme-warning" role="alert">
                <p>${this.msg('favorites.session.expired')}</p>
            </div>`;
        }
        return html`<div class="alert theme-danger" role="alert">
            <p>${this.msg('favorites.load.fail')}</p>
            <button
                type="button"
                class="btn-solid theme-danger btn-sm retry"
                @click=${() => void this.reload()}
            >
                ${this.msg('favorites.try.again')}
            </button>
        </div>`;
    }

    #renderFavorites(favorites: FavoritesData): TemplateResult {
        const items: Favorite[] = [...favorites.collections, ...favorites.portlets];
        if (items.length === 0) {
            return html`<div class="favorites">
                ${this.#renderNotice()}
                <div class="alert theme-warning empty" tabindex="-1">
                    <p>${this.msg('favorites.have.none')}</p>
                    <p>${this.msg('favorites.instruction.add.via.contextual.menu')}</p>
                </div>
                ${this.#renderMarketplaceLink(favorites.marketplaceUrl)}
                ${this.#renderAnnouncement()}
            </div>`;
        }

        const movable = items.some((item) => item.moveAllowed);
        const locked = items.some((item) => !item.deleteAllowed);
        const listStyle =
            this.maxHeightPixels !== undefined &&
            Number.isFinite(this.maxHeightPixels) &&
            this.maxHeightPixels > 0
                ? { maxHeight: `${this.maxHeightPixels}px`, overflowY: 'auto' }
                : {};
        return html`<div class="favorites" @keydown=${this.#onKeydown}>
            ${
                movable
                    ? html`<div class="toolbar">
                          <button
                              type="button"
                              class="btn-text theme-secondary btn-sm edit-toggle"
                              @click=${this.#toggleEditing}
                          >
                              ${
                                  this.editing
                                      ? this.msg('favorites.stop.editing')
                                      : this.msg('favorites.edit.order')
                              }
                          </button>
                      </div>`
                    : nothing
            }
            ${this.#renderNotice()}
            ${
                this.editing && movable
                    ? html`<p class="help">${this.msg('favorites.move.keyboard.help')}</p>`
                    : nothing
            }
            <ul
                class="list-group favorites-list"
                aria-label=${this.msg('favorites')}
                style=${styleMap(listStyle)}
            >
                ${repeat(
                    items,
                    (item) => item.nodeId,
                    (item) => this.#renderItem(item, favorites),
                )}
            </ul>
            ${
                this.editing && locked
                    ? html`<p class="legend">
                          ${lockIcon} ${this.msg('favorites.lock.icon.legend')}
                      </p>`
                    : nothing
            }
            <div class="footer">
                <p class="instruction">
                    ${this.msg('favorites.instruction.add.via.contextual.menu')}
                </p>
                ${this.#renderMarketplaceLink(favorites.marketplaceUrl)}
            </div>
            ${this.#renderAnnouncement()}
        </div>`;
    }

    #renderItem(item: Favorite, favorites: FavoritesData): TemplateResult {
        const siblings = item.kind === 'collection' ? favorites.collections : favorites.portlets;
        const index = siblings.findIndex((sibling) => sibling.nodeId === item.nodeId);
        const description =
            item.kind === 'portlet' && this.isMaximized && item.description !== null
                ? html`<p class="favorite-description">${item.description}</p>`
                : nothing;
        return html`<li
            class="list-group-item favorite"
            data-node-id=${item.nodeId}
            data-kind=${item.kind}
        >
            ${this.#renderLink(item)}
            <span class="favorite-actions">
                ${
                    this.editing && item.moveAllowed
                        ? html`<button
                                  type="button"
                                  class="btn-text theme-secondary btn-icon btn-sm"
                                  data-control="up"
                                  aria-label=${this.msg('favorites.move.up', item.name)}
                                  title=${this.msg('favorites.move.up', item.name)}
                                  aria-keyshortcuts="Alt+ArrowUp"
                                  ?disabled=${index === 0}
                                  @click=${() => void this.#move(item, -1, 'up')}
                              >
                                  ${arrowUpIcon}
                              </button>
                              <button
                                  type="button"
                                  class="btn-text theme-secondary btn-icon btn-sm"
                                  data-control="down"
                                  aria-label=${this.msg('favorites.move.down', item.name)}
                                  title=${this.msg('favorites.move.down', item.name)}
                                  aria-keyshortcuts="Alt+ArrowDown"
                                  ?disabled=${index === siblings.length - 1}
                                  @click=${() => void this.#move(item, 1, 'down')}
                              >
                                  ${arrowDownIcon}
                              </button>`
                        : nothing
                }
                ${this.#renderRemove(item)}
            </span>
            ${description}
        </li>`;
    }

    #renderLink(item: Favorite): TemplateResult {
        const icon =
            item.kind === 'collection'
                ? folderIcon
                : item.iconUrl !== null
                  ? html`<img class="favorite-image" src=${item.iconUrl} alt="" />`
                  : portletIcon;
        const target = item.kind === 'portlet' ? item.target : null;
        const content = html`${icon}<span class="favorite-name">${item.name}</span>${
                target === '_blank'
                    ? html`<span class="visually-hidden"
                          >${this.msg('favorites.opens.new.window')}</span
                      >`
                    : nothing
            }`;
        if (item.url === null) {
            return html`<span class="favorite-link">${content}</span>`;
        }
        return html`<a
            class="favorite-link"
            data-control="link"
            href=${item.url}
            target=${target ?? nothing}
            rel=${target === null ? nothing : 'noopener noreferrer'}
            >${content}</a
        >`;
    }

    #renderRemove(item: Favorite): TemplateResult | typeof nothing {
        if (item.deleteAllowed) {
            return html`<button
                type="button"
                class="btn-text theme-warning btn-icon btn-sm"
                data-control="remove"
                aria-label=${this.msg('favorites.remove.parameterized', item.name)}
                title=${this.msg('remove.from.my.favorites')}
                @click=${() => void this.#remove(item)}
            >
                ${starIcon}
            </button>`;
        }
        if (!this.editing) {
            return nothing;
        }
        const reason = this.msg(
            item.kind === 'collection'
                ? 'favorites.lack.permission.to.unfavorite.collection'
                : 'favorites.lack.permission.to.unfavorite.portlet',
        );
        return html`<span class="lock" title=${reason}
            >${lockIcon}<span class="visually-hidden">${reason}</span></span
        >`;
    }

    #renderNotice(): TemplateResult | typeof nothing {
        const notice = this.notice;
        if (notice === undefined) {
            return nothing;
        }
        // The live region announces successes; failures are alerts of their own
        return html`<div
            class="alert theme-${notice.theme} notice"
            role=${notice.theme === 'danger' ? 'alert' : nothing}
        >
            <p>${notice.text}</p>
            <button
                type="button"
                class="btn-close"
                aria-label=${this.msg('close')}
                @click=${() => {
                    this.notice = undefined;
                }}
            ></button>
        </div>`;
    }

    #renderMarketplaceLink(url: string | null): TemplateResult | typeof nothing {
        return url === null
            ? nothing
            : html`<a class="marketplace-link" href=${url}
                  >${this.msg('favorites.invitation.to.marketplace.short')}</a
              >`;
    }

    #renderAnnouncement(): TemplateResult {
        return html`<div class="visually-hidden" role="status" aria-live="polite">
            ${this.announcement}
        </div>`;
    }

    #toggleEditing = (): void => {
        this.editing = !this.editing;
    };

    #onKeydown = (event: KeyboardEvent): void => {
        if (event.key === 'Escape' && this.editing) {
            event.preventDefault();
            this.editing = false;
            this.#focusRequest = { selector: '.edit-toggle' };
            return;
        }
        if (!this.editing || !event.altKey || this.view.status !== 'ready') {
            return;
        }
        const direction = event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' ? 1 : 0;
        const origin = event.composedPath()[0];
        const row = origin instanceof Element ? origin.closest('li[data-node-id]') : null;
        const { collections, portlets } = this.view.favorites;
        const item = [...collections, ...portlets].find(
            (favorite) => favorite.nodeId === row?.getAttribute('data-node-id'),
        );
        if (direction === 0 || item === undefined || !item.moveAllowed) {
            return;
        }
        event.preventDefault();
        const control = origin instanceof Element ? origin.getAttribute('data-control') : null;
        void this.#move(
            item,
            direction,
            control === 'link' || control === 'remove' ? control : direction < 0 ? 'up' : 'down',
        );
    };

    async #move(item: Favorite, direction: -1 | 1, control: Control): Promise<void> {
        if (this.#busy || this.view.status !== 'ready') {
            return;
        }
        const favorites = this.view.favorites;
        const siblings = item.kind === 'collection' ? favorites.collections : favorites.portlets;
        const index = siblings.findIndex((sibling) => sibling.nodeId === item.nodeId);
        const neighbour = siblings[index + direction];
        if (neighbour === undefined) {
            return;
        }

        this.#busy = true;
        try {
            const moved = await moveFavorite(
                this.api,
                item.nodeId,
                direction < 0 ? { before: neighbour.nodeId } : { after: neighbour.nodeId },
            );
            this.view = { status: 'ready', favorites: moved };
            this.notice = undefined;
            const movedSiblings = item.kind === 'collection' ? moved.collections : moved.portlets;
            const position = movedSiblings.findIndex((sibling) => sibling.nodeId === item.nodeId);
            this.announcement = this.msg(
                'favorites.moved',
                item.name,
                position + 1,
                movedSiblings.length,
            );
            this.emit('up-favorite-moved', {
                nodeId: item.nodeId,
                name: item.name,
                position,
            } satisfies FavoriteMovedDetail);
        } catch (error) {
            this.notice = {
                theme: 'danger',
                text:
                    errorKind(error) === 'unauthorized'
                        ? this.msg('favorites.session.expired')
                        : this.msg('favorites.move.fail.parameterized', item.name),
            };
        } finally {
            this.#busy = false;
        }
        // The same control keeps focus if it still applies, else the nearest one that does
        this.#focusRequest = {
            nodeId: item.nodeId,
            controls: [control, direction < 0 ? 'down' : 'up', 'link'],
        };
    }

    async #remove(item: Favorite): Promise<void> {
        if (this.#busy || this.view.status !== 'ready') {
            return;
        }
        const favorites = this.view.favorites;
        const items = [...favorites.collections, ...favorites.portlets];
        const index = items.indexOf(item);

        this.#busy = true;
        try {
            await removeFavorite(this.api, item.nodeId);
            this.#removed(item, favorites);
        } catch (error) {
            const kind = errorKind(error);
            if (kind === 'not-found') {
                // Already gone, for example removed in another window
                this.#removed(item, favorites);
            } else {
                this.notice = {
                    theme: 'danger',
                    text:
                        kind === 'unauthorized'
                            ? this.msg('favorites.session.expired')
                            : kind === 'forbidden'
                              ? this.msg(
                                    'favorites.unfavorite.fail.lack.permission.parameterized',
                                    item.name,
                                )
                              : this.msg('favorites.unfavorite.fail.parameterized', item.name),
                };
                this.#focusRequest = { nodeId: item.nodeId, controls: ['remove'] };
                return;
            }
        } finally {
            this.#busy = false;
        }
        // Focus the next favorite, else the previous one, else the empty state
        const next = items[index + 1] ?? items[index - 1];
        this.#focusRequest =
            next === undefined
                ? { selector: '.empty' }
                : { nodeId: next.nodeId, controls: ['link', 'remove'] };
    }

    #removed(item: Favorite, favorites: FavoritesData): void {
        this.view = {
            status: 'ready',
            favorites: {
                ...favorites,
                collections: favorites.collections.filter((c) => c.nodeId !== item.nodeId),
                portlets: favorites.portlets.filter((p) => p.nodeId !== item.nodeId),
            },
        };
        const text = this.msg('favorites.unfavorite.success.parameterized', item.name);
        this.notice = { theme: 'success', text };
        this.announcement = text;
        this.emit('up-favorite-removed', {
            nodeId: item.nodeId,
            name: item.name,
            kind: item.kind,
        } satisfies FavoriteRemovedDetail);
    }

    #focus(request: FocusRequest): void {
        if ('selector' in request) {
            this.renderRoot.querySelector<HTMLElement>(request.selector)?.focus();
            return;
        }
        const row = this.renderRoot.querySelector(
            `li[data-node-id="${CSS.escape(request.nodeId)}"]`,
        );
        for (const control of request.controls) {
            const target = row?.querySelector<HTMLElement>(
                `[data-control="${control}"]:not([disabled])`,
            );
            if (target) {
                target.focus();
                return;
            }
        }
    }
}

if (!customElements.get('up-favorites')) {
    customElements.define('up-favorites', UpFavorites);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-favorites': UpFavorites;
    }
}
