/**
 * The states of `<up-favorites>`, as an XState machine (MOD-D31).
 *
 * A pure module: no DOM. The REST calls are the invoked actors `loadFavorites`, `removeFavorite`
 * and `moveFavorite`, which tests replace with `favoritesMachine.provide({ actors })`. What the
 * element shows comes from the state (`snapshot.matches(...)`, the `busy` tag) and from context
 * data; messages and focus are applied by the element.
 *
 * ```
 * loading                    GET the favorites (busy)
 * failed.retryable           the load failed; "reload" tries again
 * failed.sessionExpired      401: the user has to sign in again
 * empty                      no favorites
 * list (parallel)
 *   mode:    viewing | editing                reorder controls while editing
 *   request: idle | removing | moving         one change at a time (busy)
 * ```
 *
 * Changes are pessimistic: the list changes when the portal confirms, so a failure leaves it as
 * it was and only adds a notice. A favorite that is already gone (404) counts as removed.
 */
import { and, assign, enqueueActions, fromPromise, setup, stateIn } from 'xstate';
import { ApiError, type ApiClient, type ApiErrorKind } from '../../lib/api-client';
import {
    fetchFavorites,
    moveFavorite,
    removeFavorite,
    type Favorite,
    type FavoritesData,
    type MovePosition,
} from './favorites-api';

/** The focusable parts of a favorite, marked with `data-control`. */
export type Control = 'link' | 'up' | 'down' | 'remove';

/**
 * Where the element puts focus after an outcome: the first enabled control of a favorite, or an
 * element of the view. `id` grows with every request, so each one is applied once.
 */
export type FocusRequest =
    | { id: number; nodeId: string; controls: readonly Control[] }
    | { id: number; element: 'empty' | 'edit-toggle' };

/** A visible notice about the last change. */
export type Notice =
    | { kind: 'removed'; name: string }
    | { kind: 'remove-failed'; name: string; reason: ApiErrorKind }
    | { kind: 'move-failed'; name: string; reason: ApiErrorKind };

/** What the live region says after a change. */
export type Announcement =
    | { kind: 'removed'; name: string }
    | { kind: 'moved'; name: string; position: number; total: number };

/** The change waiting for the portal's answer. */
export type PendingRequest =
    | { kind: 'remove'; favorite: Favorite; index: number }
    | {
          kind: 'move';
          favorite: Favorite;
          position: MovePosition;
          direction: -1 | 1;
          control: Control;
      };

export interface FavoritesContext {
    api: ApiClient;
    favorites: FavoritesData;
    notice: Notice | undefined;
    announcement: Announcement | undefined;
    focus: FocusRequest | undefined;
    pending: PendingRequest | undefined;
}

export interface FavoritesInput {
    api: ApiClient;
}

export type FavoritesEvent =
    /** Load the favorites again (also how the user retries). */
    | { type: 'reload' }
    | { type: 'edit.toggle' }
    /** Stop reordering and return focus to the toggle (Escape). */
    | { type: 'edit.stop' }
    | { type: 'remove'; nodeId: string }
    /** Move a favorite one place among favorites of its kind; `control` keeps the focus. */
    | { type: 'move'; nodeId: string; direction: -1 | 1; control: Control }
    | { type: 'notice.dismiss' }
    /** The element's context path or CSRF token changed. */
    | { type: 'api.changed'; api: ApiClient };

export type FavoritesEmitted =
    | { type: 'favorites.loaded'; collections: number; portlets: number }
    | { type: 'favorite.removed'; nodeId: string; name: string; kind: Favorite['kind'] }
    | { type: 'favorite.moved'; nodeId: string; name: string; position: number };

const NO_FAVORITES: FavoritesData = { collections: [], portlets: [], marketplaceUrl: null };

/** Collections first, then portlets, as the element lists them. */
export function allFavorites(favorites: FavoritesData): Favorite[] {
    return [...favorites.collections, ...favorites.portlets];
}

/** The favorites of the same kind, among which a favorite moves. */
export function siblingsOf(favorites: FavoritesData, favorite: Favorite): readonly Favorite[] {
    return favorite.kind === 'collection' ? favorites.collections : favorites.portlets;
}

export function errorKind(error: unknown): ApiErrorKind {
    return error instanceof ApiError ? error.kind : 'malformed';
}

function findFavorite(favorites: FavoritesData, nodeId: string): Favorite | undefined {
    return allFavorites(favorites).find((favorite) => favorite.nodeId === nodeId);
}

/** The favorite a move would swap places with, if the move is possible. */
function neighbourOf(
    favorites: FavoritesData,
    nodeId: string,
    direction: -1 | 1,
): { favorite: Favorite; neighbour: Favorite } | undefined {
    const favorite = findFavorite(favorites, nodeId);
    if (favorite === undefined || !favorite.moveAllowed) {
        return undefined;
    }
    const siblings = siblingsOf(favorites, favorite);
    const neighbour = siblings[siblings.indexOf(favorite) + direction];
    return neighbour === undefined ? undefined : { favorite, neighbour };
}

function nextFocusId(context: FavoritesContext): number {
    return (context.focus?.id ?? 0) + 1;
}

/** The pending removal; entering "removing" set it. */
function pendingRemoval(context: FavoritesContext): Extract<PendingRequest, { kind: 'remove' }> {
    const pending = context.pending;
    /* v8 ignore if -- unreachable: every transition into "removing" sets it */
    if (pending?.kind !== 'remove') {
        throw new Error('No pending removal');
    }
    return pending;
}

/** The pending move; entering "moving" set it. */
function pendingMove(context: FavoritesContext): Extract<PendingRequest, { kind: 'move' }> {
    const pending = context.pending;
    /* v8 ignore if -- unreachable: every transition into "moving" sets it */
    if (pending?.kind !== 'move') {
        throw new Error('No pending move');
    }
    return pending;
}

export const favoritesMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion, typescript/no-unsafe-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: FavoritesContext;
        events: FavoritesEvent;
        input: FavoritesInput;
        emitted: FavoritesEmitted;
        tags: 'busy';
    },
    actors: {
        loadFavorites: fromPromise<FavoritesData, { api: ApiClient }>(({ input, signal }) =>
            fetchFavorites(input.api, { signal }),
        ),
        removeFavorite: fromPromise<void, { api: ApiClient; nodeId: string }>(({ input, signal }) =>
            removeFavorite(input.api, input.nodeId, { signal }),
        ),
        moveFavorite: fromPromise<
            FavoritesData,
            { api: ApiClient; nodeId: string; position: MovePosition }
        >(({ input, signal }) => moveFavorite(input.api, input.nodeId, input.position, { signal })),
    },
    guards: {
        hasNoFavorites: ({ context }) => allFavorites(context.favorites).length === 0,
        isUnauthorized: (_, { error }: { error: unknown }) => errorKind(error) === 'unauthorized',
        isAlreadyGone: (_, { error }: { error: unknown }) => errorKind(error) === 'not-found',
        canRemove: ({ context }, { nodeId }: { nodeId: string }) =>
            findFavorite(context.favorites, nodeId)?.deleteAllowed === true,
    },
    actions: {
        loaded: enqueueActions(({ enqueue }, { favorites }: { favorites: FavoritesData }) => {
            enqueue.assign({ favorites });
            enqueue.emit({
                type: 'favorites.loaded',
                collections: favorites.collections.length,
                portlets: favorites.portlets.length,
            });
        }),
        startRemoval: assign(({ context }, { nodeId }: { nodeId: string }) => {
            const items = allFavorites(context.favorites);
            const index = items.findIndex((favorite) => favorite.nodeId === nodeId);
            const favorite = items[index];
            /* v8 ignore if -- unreachable: the canRemove guard found it */
            if (favorite === undefined) {
                return {};
            }
            return { pending: { kind: 'remove' as const, favorite, index } };
        }),
        removed: enqueueActions(({ context, enqueue }) => {
            const { favorite, index } = pendingRemoval(context);
            const before = allFavorites(context.favorites);
            const next = before[index + 1] ?? before[index - 1];
            const id = nextFocusId(context);
            enqueue.assign({
                favorites: {
                    ...context.favorites,
                    collections: context.favorites.collections.filter(
                        (c) => c.nodeId !== favorite.nodeId,
                    ),
                    portlets: context.favorites.portlets.filter(
                        (p) => p.nodeId !== favorite.nodeId,
                    ),
                },
                pending: undefined,
                notice: { kind: 'removed', name: favorite.name },
                announcement: { kind: 'removed', name: favorite.name },
                // The next favorite, else the previous one, else the empty state
                focus:
                    next === undefined
                        ? { id, element: 'empty' }
                        : { id, nodeId: next.nodeId, controls: ['link', 'remove'] },
            });
            enqueue.emit({
                type: 'favorite.removed',
                nodeId: favorite.nodeId,
                name: favorite.name,
                kind: favorite.kind,
            });
        }),
        removalFailed: assign(({ context }, { error }: { error: unknown }) => {
            const { favorite } = pendingRemoval(context);
            return {
                pending: undefined,
                notice: {
                    kind: 'remove-failed' as const,
                    name: favorite.name,
                    reason: errorKind(error),
                },
                focus: { id: nextFocusId(context), nodeId: favorite.nodeId, controls: ['remove'] },
            };
        }),
        startMove: assign(
            (
                { context },
                {
                    nodeId,
                    direction,
                    control,
                }: { nodeId: string; direction: -1 | 1; control: Control },
            ) => {
                const found = neighbourOf(context.favorites, nodeId, direction);
                /* v8 ignore if -- unreachable: the move guard found it */
                if (found === undefined) {
                    return {};
                }
                return {
                    pending: {
                        kind: 'move' as const,
                        favorite: found.favorite,
                        position:
                            direction < 0
                                ? { before: found.neighbour.nodeId }
                                : { after: found.neighbour.nodeId },
                        direction,
                        control,
                    },
                };
            },
        ),
        moved: enqueueActions(
            ({ context, enqueue }, { favorites }: { favorites: FavoritesData }) => {
                const { favorite, direction, control } = pendingMove(context);
                const siblings = siblingsOf(favorites, favorite);
                const position = siblings.findIndex(
                    (sibling) => sibling.nodeId === favorite.nodeId,
                );
                enqueue.assign({
                    favorites,
                    pending: undefined,
                    notice: undefined,
                    announcement: {
                        kind: 'moved',
                        name: favorite.name,
                        position: position + 1,
                        total: siblings.length,
                    },
                    // The same control keeps focus if it still applies, else the nearest one that does
                    focus: {
                        id: nextFocusId(context),
                        nodeId: favorite.nodeId,
                        controls: [control, direction < 0 ? 'down' : 'up', 'link'],
                    },
                });
                enqueue.emit({
                    type: 'favorite.moved',
                    nodeId: favorite.nodeId,
                    name: favorite.name,
                    position,
                });
            },
        ),
        moveFailed: assign(({ context }, { error }: { error: unknown }) => {
            const { favorite, direction, control } = pendingMove(context);
            return {
                pending: undefined,
                notice: {
                    kind: 'move-failed' as const,
                    name: favorite.name,
                    reason: errorKind(error),
                },
                focus: {
                    id: nextFocusId(context),
                    nodeId: favorite.nodeId,
                    controls: [control, direction < 0 ? 'down' : 'up', 'link'],
                },
            };
        }),
        focusEditToggle: assign(({ context }) => ({
            focus: { id: nextFocusId(context), element: 'edit-toggle' as const },
        })),
    },
}).createMachine({
    id: 'favorites',
    context: ({ input }) => ({
        api: input.api,
        favorites: NO_FAVORITES,
        notice: undefined,
        announcement: undefined,
        focus: undefined,
        pending: undefined,
    }),
    initial: 'loading',
    on: {
        // A fresh view: the outcome of an earlier change no longer applies
        reload: {
            target: '.loading',
            reenter: true,
            actions: assign({ notice: undefined, announcement: undefined }),
        },
        'notice.dismiss': { actions: assign({ notice: undefined }) },
        'api.changed': { actions: assign({ api: ({ event }) => event.api }) },
    },
    states: {
        loading: {
            tags: ['busy'],
            invoke: {
                id: 'loadFavorites',
                src: 'loadFavorites',
                input: ({ context }) => ({ api: context.api }),
                // An empty list moves on to "empty" (see list.request.idle)
                onDone: {
                    target: 'list',
                    actions: {
                        type: 'loaded',
                        params: ({ event }) => ({ favorites: event.output }),
                    },
                },
                onError: [
                    {
                        guard: {
                            type: 'isUnauthorized',
                            params: ({ event }) => ({ error: event.error }),
                        },
                        target: 'failed.sessionExpired',
                    },
                    { target: 'failed.retryable' },
                ],
            },
        },
        failed: {
            initial: 'retryable',
            states: {
                retryable: {},
                sessionExpired: {},
            },
        },
        empty: {},
        list: {
            type: 'parallel',
            states: {
                mode: {
                    initial: 'viewing',
                    states: {
                        viewing: {
                            on: { 'edit.toggle': 'editing' },
                        },
                        editing: {
                            on: {
                                'edit.toggle': 'viewing',
                                'edit.stop': { target: 'viewing', actions: 'focusEditToggle' },
                            },
                        },
                    },
                },
                request: {
                    initial: 'idle',
                    states: {
                        idle: {
                            // Also after removing the last favorite
                            always: { guard: 'hasNoFavorites', target: '#favorites.empty' },
                            on: {
                                remove: {
                                    guard: {
                                        type: 'canRemove',
                                        params: ({ event }) => ({ nodeId: event.nodeId }),
                                    },
                                    target: 'removing',
                                    actions: {
                                        type: 'startRemoval',
                                        params: ({ event }) => ({ nodeId: event.nodeId }),
                                    },
                                },
                                move: {
                                    guard: and([
                                        stateIn('#favorites.list.mode.editing'),
                                        ({ context, event }) =>
                                            neighbourOf(
                                                context.favorites,
                                                event.nodeId,
                                                event.direction,
                                            ) !== undefined,
                                    ]),
                                    target: 'moving',
                                    actions: {
                                        type: 'startMove',
                                        params: ({ event }) => ({
                                            nodeId: event.nodeId,
                                            direction: event.direction,
                                            control: event.control,
                                        }),
                                    },
                                },
                            },
                        },
                        removing: {
                            tags: ['busy'],
                            invoke: {
                                id: 'removeFavorite',
                                src: 'removeFavorite',
                                input: ({ context }) => ({
                                    api: context.api,
                                    nodeId: pendingRemoval(context).favorite.nodeId,
                                }),
                                onDone: { target: 'idle', actions: 'removed' },
                                onError: [
                                    {
                                        guard: {
                                            type: 'isAlreadyGone',
                                            params: ({ event }) => ({ error: event.error }),
                                        },
                                        target: 'idle',
                                        actions: 'removed',
                                    },
                                    {
                                        target: 'idle',
                                        actions: {
                                            type: 'removalFailed',
                                            params: ({ event }) => ({ error: event.error }),
                                        },
                                    },
                                ],
                            },
                        },
                        moving: {
                            tags: ['busy'],
                            invoke: {
                                id: 'moveFavorite',
                                src: 'moveFavorite',
                                input: ({ context }) => {
                                    const { favorite, position } = pendingMove(context);
                                    return { api: context.api, nodeId: favorite.nodeId, position };
                                },
                                onDone: {
                                    target: 'idle',
                                    actions: {
                                        type: 'moved',
                                        params: ({ event }) => ({ favorites: event.output }),
                                    },
                                },
                                onError: {
                                    target: 'idle',
                                    actions: {
                                        type: 'moveFailed',
                                        params: ({ event }) => ({ error: event.error }),
                                    },
                                },
                            },
                        },
                    },
                },
            },
        },
    },
});

export type FavoritesMachine = typeof favoritesMachine;
