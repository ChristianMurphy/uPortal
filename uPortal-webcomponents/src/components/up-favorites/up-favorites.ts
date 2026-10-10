import { css, html, nothing, type PropertyValues, type TemplateResult } from 'lit';
import type { PropertyDeclarations } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import { styleMap } from 'lit/directives/style-map.js';
import { waitFor } from 'xstate';
import type { Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import { ActorController } from '../../state/actor-controller';
import type { Favorite, FavoritesData } from './favorites-api';
import { arrowDownIcon, arrowUpIcon, folderIcon, lockIcon, portletIcon, starIcon } from './icons';
import { FAVORITES_MESSAGES } from './messages';
import {
    allFavorites,
    favoritesMachine,
    siblingsOf,
    type Announcement,
    type Control,
    type FavoritesEmitted,
    type FocusRequest,
    type Notice,
} from './up-favorites.machine';

/** Details of the `up-favorite-removed` event. */
export type FavoriteRemovedDetail = Omit<
    Extract<FavoritesEmitted, { type: 'favorite.removed' }>,
    'type'
>;

/** Details of the `up-favorite-moved` event. */
export type FavoriteMovedDetail = Omit<
    Extract<FavoritesEmitted, { type: 'favorite.moved' }>,
    'type'
>;

/** Details of the `up-favorites-loaded` event. */
export type FavoritesLoadedDetail = Omit<
    Extract<FavoritesEmitted, { type: 'favorites.loaded' }>,
    'type'
>;

/**
 * The user's favorite collections and portlets, the web-component replacement of the Favorites
 * portlet. It reads and changes them through `/api/v6-0/favorites`.
 *
 * Each favorite links to its collection or portlet; the star removes it. "Reorder favorites"
 * shows buttons that move a favorite up or down among favorites of its kind, which Alt+Up and
 * Alt+Down also do; Escape stops reordering. A lock marks favorites the layout does not let the
 * user remove. Maximized, the component also shows each portlet's description.
 *
 * Its states are the XState machine in `up-favorites.machine.ts`, run by an
 * {@link ActorController}: the template renders from the snapshot, and user actions are events.
 *
 * Attributes: those of {@link UpElement}, plus `max-height-pixels`, which limits the height of
 * the list (the Favorites portlet's `FavoritesController.maxHeightPixels` preference).
 *
 * Events: `up-favorites-loaded`, `up-favorite-removed` and `up-favorite-moved`.
 */
export class UpFavorites extends UpElement {
    static override properties: PropertyDeclarations = {
        maxHeightPixels: { attribute: 'max-height-pixels', type: Number, reflect: true },
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

    readonly #favorites = new ActorController(this, favoritesMachine, {
        // Properties in: the API client at the first connect, later changes as events (willUpdate)
        input: () => ({ api: this.api }),
        // Events out
        events: {
            'favorites.loaded': 'up-favorites-loaded',
            'favorite.removed': 'up-favorite-removed',
            'favorite.moved': 'up-favorite-moved',
        },
    });

    /** The focus request applied last, so each one moves focus once. */
    #focused = 0;

    protected override get defaultMessages(): Messages {
        return FAVORITES_MESSAGES;
    }

    /**
     * Loads the favorites again, showing the loading state meanwhile. Resolves once they are
     * loaded (or failed to load), or when the element leaves the page first.
     */
    async reload(): Promise<void> {
        this.#favorites.send({ type: 'reload' });
        const actor = this.#favorites.actorRef;
        if (actor === undefined) {
            return;
        }
        try {
            await waitFor(actor, (snapshot) => !snapshot.matches('loading'));
        } catch {
            // The element was disconnected (its actor stopped) before the favorites arrived
        }
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        if (
            this.hasUpdated &&
            (changed.has('contextPath') || changed.has('csrfHeader') || changed.has('csrfToken'))
        ) {
            this.#favorites.send({ type: 'api.changed', api: this.api });
        }
    }

    override render(): TemplateResult {
        const snapshot = this.#favorites.snapshot;
        if (snapshot.matches('loading')) {
            return html`<div class="loading" role="status">
                <span class="spinner-border" aria-hidden="true"></span>
                <span>${this.msg('loading')}</span>
            </div>`;
        }
        if (snapshot.matches({ failed: 'sessionExpired' })) {
            return html`<div class="alert theme-warning" role="alert">
                <p>${this.msg('favorites.session.expired')}</p>
            </div>`;
        }
        if (snapshot.matches('failed')) {
            return html`<div class="alert theme-danger" role="alert">
                <p>${this.msg('favorites.load.fail')}</p>
                <button
                    type="button"
                    class="btn-solid theme-danger btn-sm retry"
                    @click=${this.#reloadClicked}
                >
                    ${this.msg('favorites.try.again')}
                </button>
            </div>`;
        }
        const { favorites, notice, announcement } = snapshot.context;
        if (snapshot.matches('empty')) {
            return html`<div class="favorites">
                ${this.#renderNotice(notice)}
                <div class="alert theme-warning empty" tabindex="-1">
                    <p>${this.msg('favorites.have.none')}</p>
                    <p>${this.msg('favorites.instruction.add.via.contextual.menu')}</p>
                </div>
                ${this.#renderMarketplaceLink(favorites.marketplaceUrl)}
                ${this.#renderAnnouncement(announcement)}
            </div>`;
        }
        return this.#renderList(
            favorites,
            snapshot.matches({ list: { mode: 'editing' } }),
            snapshot.hasTag('busy'),
            notice,
            announcement,
        );
    }

    protected override updated(changed: PropertyValues<this>): void {
        super.updated(changed);
        const focus = this.#favorites.snapshot.context.focus;
        if (focus !== undefined && focus.id !== this.#focused) {
            this.#focused = focus.id;
            this.#focus(focus);
        }
    }

    #renderList(
        favorites: FavoritesData,
        editing: boolean,
        busy: boolean,
        notice: Notice | undefined,
        announcement: Announcement | undefined,
    ): TemplateResult {
        const items = allFavorites(favorites);
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
                                  editing
                                      ? this.msg('favorites.stop.editing')
                                      : this.msg('favorites.edit.order')
                              }
                          </button>
                      </div>`
                    : nothing
            }
            ${this.#renderNotice(notice)}
            ${
                editing && movable
                    ? html`<p class="help">${this.msg('favorites.move.keyboard.help')}</p>`
                    : nothing
            }
            <ul
                class="list-group favorites-list"
                aria-label=${this.msg('favorites')}
                aria-busy=${busy ? 'true' : nothing}
                style=${styleMap(listStyle)}
            >
                ${repeat(
                    items,
                    (item) => item.nodeId,
                    (item) => this.#renderItem(item, favorites, editing),
                )}
            </ul>
            ${
                editing && locked
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
            ${this.#renderAnnouncement(announcement)}
        </div>`;
    }

    #renderItem(item: Favorite, favorites: FavoritesData, editing: boolean): TemplateResult {
        const siblings = siblingsOf(favorites, item);
        const index = siblings.indexOf(item);
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
                    editing && item.moveAllowed
                        ? html`<button
                                  type="button"
                                  class="btn-text theme-secondary btn-icon btn-sm"
                                  data-control="up"
                                  aria-label=${this.msg('favorites.move.up', item.name)}
                                  title=${this.msg('favorites.move.up', item.name)}
                                  aria-keyshortcuts="Alt+ArrowUp"
                                  ?disabled=${index === 0}
                                  @click=${() => {
                                      this.#move(item.nodeId, -1, 'up');
                                  }}
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
                                  @click=${() => {
                                      this.#move(item.nodeId, 1, 'down');
                                  }}
                              >
                                  ${arrowDownIcon}
                              </button>`
                        : nothing
                }
                ${this.#renderRemove(item, editing)}
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

    #renderRemove(item: Favorite, editing: boolean): TemplateResult | typeof nothing {
        if (item.deleteAllowed) {
            return html`<button
                type="button"
                class="btn-text theme-warning btn-icon btn-sm"
                data-control="remove"
                aria-label=${this.msg('favorites.remove.parameterized', item.name)}
                title=${this.msg('remove.from.my.favorites')}
                @click=${() => {
                    this.#favorites.send({ type: 'remove', nodeId: item.nodeId });
                }}
            >
                ${starIcon}
            </button>`;
        }
        if (!editing) {
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

    #renderNotice(notice: Notice | undefined): TemplateResult | typeof nothing {
        if (notice === undefined) {
            return nothing;
        }
        const failed = notice.kind !== 'removed';
        // The live region announces successes; failures are alerts of their own
        return html`<div
            class="alert theme-${failed ? 'danger' : 'success'} notice"
            role=${failed ? 'alert' : nothing}
        >
            <p>${this.#noticeText(notice)}</p>
            <button
                type="button"
                class="btn-close"
                aria-label=${this.msg('close')}
                @click=${this.#dismissNotice}
            ></button>
        </div>`;
    }

    #noticeText(notice: Notice): string {
        if (notice.kind === 'removed') {
            return this.msg('favorites.unfavorite.success.parameterized', notice.name);
        }
        if (notice.reason === 'unauthorized') {
            return this.msg('favorites.session.expired');
        }
        if (notice.kind === 'move-failed') {
            return this.msg('favorites.move.fail.parameterized', notice.name);
        }
        return notice.reason === 'forbidden'
            ? this.msg('favorites.unfavorite.fail.lack.permission.parameterized', notice.name)
            : this.msg('favorites.unfavorite.fail.parameterized', notice.name);
    }

    #renderMarketplaceLink(url: string | null): TemplateResult | typeof nothing {
        return url === null
            ? nothing
            : html`<a class="marketplace-link" href=${url}
                  >${this.msg('favorites.invitation.to.marketplace.short')}</a
              >`;
    }

    #renderAnnouncement(announcement: Announcement | undefined): TemplateResult {
        const text =
            announcement === undefined
                ? ''
                : announcement.kind === 'removed'
                  ? this.msg('favorites.unfavorite.success.parameterized', announcement.name)
                  : this.msg(
                        'favorites.moved',
                        announcement.name,
                        announcement.position,
                        announcement.total,
                    );
        return html`<div class="visually-hidden" role="status" aria-live="polite">${text}</div>`;
    }

    #reloadClicked = (): void => {
        this.#favorites.send({ type: 'reload' });
    };

    #toggleEditing = (): void => {
        this.#favorites.send({ type: 'edit.toggle' });
    };

    #dismissNotice = (): void => {
        this.#favorites.send({ type: 'notice.dismiss' });
    };

    #move(nodeId: string, direction: -1 | 1, control: Control): void {
        this.#favorites.send({ type: 'move', nodeId, direction, control });
    }

    #onKeydown = (event: KeyboardEvent): void => {
        const snapshot = this.#favorites.snapshot;
        if (event.key === 'Escape') {
            if (snapshot.can({ type: 'edit.stop' })) {
                event.preventDefault();
                this.#favorites.send({ type: 'edit.stop' });
            }
            return;
        }
        const direction = event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' ? 1 : 0;
        if (!event.altKey || direction === 0) {
            return;
        }
        const origin = event.composedPath()[0];
        const row = origin instanceof Element ? origin.closest('li[data-node-id]') : null;
        const nodeId = row?.getAttribute('data-node-id');
        if (nodeId === null || nodeId === undefined) {
            return;
        }
        const control = origin instanceof Element ? origin.getAttribute('data-control') : null;
        const move = {
            type: 'move',
            nodeId,
            direction,
            control:
                control === 'link' || control === 'remove'
                    ? control
                    : direction < 0
                      ? 'up'
                      : 'down',
        } as const;
        if (snapshot.can(move)) {
            event.preventDefault();
            this.#favorites.send(move);
        }
    };

    #focus(request: FocusRequest): void {
        if ('element' in request) {
            this.renderRoot
                .querySelector<HTMLElement>(request.element === 'empty' ? '.empty' : '.edit-toggle')
                ?.focus();
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
