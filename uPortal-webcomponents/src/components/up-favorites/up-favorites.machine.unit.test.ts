import { describe, expect, it } from 'vite-plus/test';
import { createActor, fromPromise, waitFor, type SnapshotFrom } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import type {
    FavoriteCollection,
    FavoritePortlet,
    FavoritesData,
    MovePosition,
} from './favorites-api';
import {
    allFavorites,
    errorKind,
    favoritesMachine,
    siblingsOf,
    type FavoritesEmitted,
    type FavoritesMachine,
} from './up-favorites.machine';

function portlet(nodeId: string, name: string, overrides: Partial<FavoritePortlet> = {}) {
    return {
        kind: 'portlet',
        nodeId,
        name,
        url: `/p/${nodeId}`,
        moveAllowed: true,
        deleteAllowed: true,
        fname: nodeId,
        description: null,
        target: null,
        iconUrl: null,
        ...overrides,
    } satisfies FavoritePortlet;
}

function collection(nodeId: string, name: string, overrides: Partial<FavoriteCollection> = {}) {
    return {
        kind: 'collection',
        nodeId,
        name,
        url: `/f/${nodeId}`,
        moveAllowed: true,
        deleteAllowed: true,
        ...overrides,
    } satisfies FavoriteCollection;
}

const NEWS = collection('c1', 'News');
const CALENDAR = portlet('p1', 'Calendar');
const MAIL = portlet('p2', 'Mail');
const COURSES = portlet('p3', 'Courses');

function data(overrides: Partial<FavoritesData> = {}): FavoritesData {
    return {
        collections: [NEWS],
        portlets: [CALENDAR, MAIL, COURSES],
        marketplaceUrl: null,
        ...overrides,
    };
}

/** A promise the test settles. */
function deferred<T>() {
    let settle: { resolve: (value: T) => void; reject: (error: unknown) => void } | undefined;
    const promise = new Promise<T>((resolve, reject) => {
        settle = { resolve, reject };
    });
    return {
        promise,
        resolve: (value: T) => settle?.resolve(value),
        reject: (error: unknown) => settle?.reject(error),
    };
}

/**
 * Runs the machine with its REST actors replaced: each call is recorded and answered by the
 * test through the returned deferreds.
 */
function start(api = new ApiClient({ contextPath: '/uPortal' })) {
    const calls = {
        load: [] as { api: ApiClient; answer: ReturnType<typeof deferred<FavoritesData>> }[],
        remove: [] as { nodeId: string; answer: ReturnType<typeof deferred<undefined>> }[],
        move: [] as {
            nodeId: string;
            position: MovePosition;
            answer: ReturnType<typeof deferred<FavoritesData>>;
        }[],
    };
    const machine: FavoritesMachine = favoritesMachine.provide({
        actors: {
            loadFavorites: fromPromise<FavoritesData, { api: ApiClient }>(({ input }) => {
                const answer = deferred<FavoritesData>();
                calls.load.push({ api: input.api, answer });
                return answer.promise;
            }),
            removeFavorite: fromPromise<void, { api: ApiClient; nodeId: string }>(({ input }) => {
                const answer = deferred<undefined>();
                calls.remove.push({ nodeId: input.nodeId, answer });
                return answer.promise;
            }),
            moveFavorite: fromPromise<
                FavoritesData,
                { api: ApiClient; nodeId: string; position: MovePosition }
            >(({ input }) => {
                const answer = deferred<FavoritesData>();
                calls.move.push({ nodeId: input.nodeId, position: input.position, answer });
                return answer.promise;
            }),
        },
    });
    const actor = createActor(machine, { input: { api } });
    const emitted: FavoritesEmitted[] = [];
    actor.on('*', (event) => {
        emitted.push(event);
    });
    actor.start();
    return { actor, calls, emitted };
}

type Running = ReturnType<typeof start>;

async function settled(running: Running, done: (s: SnapshotFrom<FavoritesMachine>) => boolean) {
    return waitFor(running.actor, done, { timeout: 1000 });
}

async function loaded(favorites = data()): Promise<Running> {
    const running = start();
    running.calls.load[0]?.answer.resolve(favorites);
    await settled(running, (s) => !s.hasTag('busy'));
    return running;
}

async function editing(favorites = data()): Promise<Running> {
    const running = await loaded(favorites);
    running.actor.send({ type: 'edit.toggle' });
    return running;
}

describe('favorites machine: loading', () => {
    it('starts loading, busy, with the api it was given', () => {
        const api = new ApiClient({ contextPath: '/x' });
        const { actor, calls } = start(api);

        expect(actor.getSnapshot().value).toBe('loading');
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);
        expect(calls.load).toHaveLength(1);
        expect(calls.load[0]?.api).toBe(api);
    });

    it('lists the favorites it loaded, viewing and idle, and says so', async () => {
        const { actor, emitted } = await loaded();

        expect(actor.getSnapshot().value).toEqual({ list: { mode: 'viewing', request: 'idle' } });
        expect(actor.getSnapshot().context.favorites).toEqual(data());
        expect(emitted).toEqual([{ type: 'favorites.loaded', collections: 1, portlets: 3 }]);
    });

    it('is empty when there are no favorites', async () => {
        const { actor, emitted } = await loaded(data({ collections: [], portlets: [] }));

        expect(actor.getSnapshot().value).toBe('empty');
        expect(emitted).toEqual([{ type: 'favorites.loaded', collections: 0, portlets: 0 }]);
    });

    it.each([
        [new ApiError('unauthorized', 401, 'gone'), { failed: 'sessionExpired' }],
        [new ApiError('server', 500, 'down'), { failed: 'retryable' }],
        [new TypeError('not an ApiError'), { failed: 'retryable' }],
    ])('fails on %s', async (error, state) => {
        const running = start();
        running.calls.load[0]?.answer.reject(error);

        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.value).toEqual(state);
    });

    it('loads again on reload, also while loading', async () => {
        const running = start();
        running.actor.send({ type: 'reload' });
        expect(running.calls.load).toHaveLength(2);

        running.calls.load[0]?.answer.reject(new ApiError('server', 500, 'late'));
        running.calls.load[1]?.answer.resolve(data());
        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.matches('list')).toBe(true);
    });

    it('forgets the outcome of an earlier change when it reloads', async () => {
        const running = await loaded();
        running.actor.send({ type: 'remove', nodeId: 'p1' });
        running.calls.remove[0]?.answer.resolve(undefined);
        await settled(running, (s) => s.context.notice !== undefined);

        running.actor.send({ type: 'reload' });

        expect(running.actor.getSnapshot().context.notice).toBeUndefined();
        expect(running.actor.getSnapshot().context.announcement).toBeUndefined();
    });

    it('retries after a failure', async () => {
        const running = start();
        running.calls.load[0]?.answer.reject(new ApiError('server', 500, 'down'));
        await settled(running, (s) => s.matches('failed'));

        running.actor.send({ type: 'reload' });
        running.calls.load[1]?.answer.resolve(data());

        expect((await settled(running, (s) => !s.hasTag('busy'))).matches('list')).toBe(true);
    });
});

describe('favorites machine: editing', () => {
    it('toggles reordering, and stopping asks for focus on the toggle', async () => {
        const { actor } = await loaded();

        actor.send({ type: 'edit.toggle' });
        expect(actor.getSnapshot().matches({ list: { mode: 'editing' } })).toBe(true);
        actor.send({ type: 'edit.toggle' });
        expect(actor.getSnapshot().matches({ list: { mode: 'viewing' } })).toBe(true);
        expect(actor.getSnapshot().can({ type: 'edit.stop' })).toBe(false);

        actor.send({ type: 'edit.toggle' });
        actor.send({ type: 'edit.stop' });
        expect(actor.getSnapshot().matches({ list: { mode: 'viewing' } })).toBe(true);
        expect(actor.getSnapshot().context.focus).toEqual({ id: 1, element: 'edit-toggle' });
    });
});

describe('favorites machine: removing', () => {
    it('removes after the portal confirms, says so and focuses the next favorite', async () => {
        const running = await loaded();
        const { actor, calls, emitted } = running;

        actor.send({ type: 'remove', nodeId: 'p2' });
        expect(actor.getSnapshot().matches({ list: { request: 'removing' } })).toBe(true);
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);
        // Still listed until the portal answers; one change at a time
        expect(allFavorites(actor.getSnapshot().context.favorites)).toHaveLength(4);
        actor.send({ type: 'remove', nodeId: 'p1' });
        expect(calls.remove.map((call) => call.nodeId)).toEqual(['p2']);

        calls.remove[0]?.answer.resolve(undefined);
        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.matches({ list: { request: 'idle' } })).toBe(true);
        expect(snapshot.context.favorites.portlets.map((p) => p.nodeId)).toEqual(['p1', 'p3']);
        expect(snapshot.context.notice).toEqual({ kind: 'removed', name: 'Mail' });
        expect(snapshot.context.announcement).toEqual({ kind: 'removed', name: 'Mail' });
        expect(snapshot.context.focus).toEqual({
            id: 1,
            nodeId: 'p3',
            controls: ['link', 'remove'],
        });
        expect(snapshot.context.pending).toBeUndefined();
        expect(emitted.at(-1)).toEqual({
            type: 'favorite.removed',
            nodeId: 'p2',
            name: 'Mail',
            kind: 'portlet',
        });
    });

    it('focuses the previous favorite after removing the last one listed', async () => {
        const running = await loaded();
        running.actor.send({ type: 'remove', nodeId: 'p3' });
        running.calls.remove[0]?.answer.resolve(undefined);

        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.context.focus).toMatchObject({ nodeId: 'p2' });
    });

    it('collections too', async () => {
        const running = await loaded();
        running.actor.send({ type: 'remove', nodeId: 'c1' });
        running.calls.remove[0]?.answer.resolve(undefined);

        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.context.favorites.collections).toEqual([]);
        expect(running.emitted.at(-1)).toMatchObject({ kind: 'collection' });
    });

    it('is empty, focused, after removing every favorite', async () => {
        const running = await loaded(data({ collections: [], portlets: [CALENDAR] }));
        running.actor.send({ type: 'remove', nodeId: 'p1' });
        running.calls.remove[0]?.answer.resolve(undefined);

        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.value).toBe('empty');
        expect(snapshot.context.focus).toEqual({ id: 1, element: 'empty' });
        expect(snapshot.context.notice).toEqual({ kind: 'removed', name: 'Calendar' });
    });

    it('treats a favorite that is already gone as removed', async () => {
        const running = await loaded();
        running.actor.send({ type: 'remove', nodeId: 'p1' });
        running.calls.remove[0]?.answer.reject(new ApiError('not-found', 404, 'gone'));

        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.context.notice).toEqual({ kind: 'removed', name: 'Calendar' });
        expect(allFavorites(snapshot.context.favorites)).toHaveLength(3);
    });

    it('keeps the favorite and explains when the portal refuses', async () => {
        const running = await loaded();
        running.actor.send({ type: 'remove', nodeId: 'p1' });
        running.calls.remove[0]?.answer.reject(new ApiError('forbidden', 403, 'no'));

        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.context.favorites).toEqual(data());
        expect(snapshot.context.notice).toEqual({
            kind: 'remove-failed',
            name: 'Calendar',
            reason: 'forbidden',
        });
        expect(snapshot.context.focus).toEqual({ id: 1, nodeId: 'p1', controls: ['remove'] });
        expect(running.emitted).toHaveLength(1);
    });

    it('ignores favorites that are locked or unknown', async () => {
        const locked = portlet('p9', 'Locked', { deleteAllowed: false });
        const { actor, calls } = await loaded(data({ portlets: [locked] }));

        actor.send({ type: 'remove', nodeId: 'p9' });
        actor.send({ type: 'remove', nodeId: 'nope' });

        expect(actor.getSnapshot().matches({ list: { request: 'idle' } })).toBe(true);
        expect(calls.remove).toEqual([]);
    });
});

describe('favorites machine: moving', () => {
    it('moves only while reordering', async () => {
        const { actor, calls } = await loaded();

        actor.send({ type: 'move', nodeId: 'p1', direction: 1, control: 'down' });

        expect(calls.move).toEqual([]);
    });

    it('moves after the portal confirms, announces the place and keeps focus', async () => {
        const running = await editing();
        const { actor, calls, emitted } = running;

        actor.send({ type: 'move', nodeId: 'p1', direction: 1, control: 'down' });
        expect(actor.getSnapshot().matches({ list: { request: 'moving', mode: 'editing' } })).toBe(
            true,
        );
        actor.send({ type: 'move', nodeId: 'p2', direction: 1, control: 'down' });
        expect(calls.move).toEqual([
            expect.objectContaining({ nodeId: 'p1', position: { after: 'p2' } }),
        ]);

        const reordered = data({ portlets: [MAIL, CALENDAR, COURSES] });
        calls.move[0]?.answer.resolve(reordered);
        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.context.favorites).toEqual(reordered);
        expect(snapshot.context.announcement).toEqual({
            kind: 'moved',
            name: 'Calendar',
            position: 2,
            total: 3,
        });
        expect(snapshot.context.focus).toEqual({
            id: 1,
            nodeId: 'p1',
            controls: ['down', 'up', 'link'],
        });
        expect(emitted.at(-1)).toEqual({
            type: 'favorite.moved',
            nodeId: 'p1',
            name: 'Calendar',
            position: 1,
        });
    });

    it('moves up before the previous favorite of its kind', async () => {
        const running = await editing();

        running.actor.send({ type: 'move', nodeId: 'p2', direction: -1, control: 'link' });

        expect(running.calls.move[0]?.position).toEqual({ before: 'p1' });
    });

    it.each([
        ['the first one up', 'p1', -1],
        ['the last one down', 'p3', 1],
        ['a collection past the collections', 'c1', 1],
        ['an unknown favorite', 'nope', 1],
    ] as const)('does not move %s', async (_name, nodeId, direction) => {
        const { actor, calls } = await editing();

        actor.send({ type: 'move', nodeId, direction, control: 'up' });

        expect(calls.move).toEqual([]);
    });

    it('does not move a favorite the layout keeps in place', async () => {
        const fixed = portlet('p8', 'Fixed', { moveAllowed: false });
        const { actor, calls } = await editing(data({ portlets: [fixed, MAIL] }));

        actor.send({ type: 'move', nodeId: 'p8', direction: 1, control: 'down' });

        expect(calls.move).toEqual([]);
    });

    it('keeps the order and explains when the move fails', async () => {
        const running = await editing();
        running.actor.send({ type: 'move', nodeId: 'p2', direction: -1, control: 'up' });
        running.calls.move[0]?.answer.reject(new ApiError('unauthorized', 401, 'gone'));

        const snapshot = await settled(running, (s) => !s.hasTag('busy'));

        expect(snapshot.context.favorites).toEqual(data());
        expect(snapshot.context.notice).toEqual({
            kind: 'move-failed',
            name: 'Mail',
            reason: 'unauthorized',
        });
        expect(snapshot.context.focus).toEqual({
            id: 1,
            nodeId: 'p2',
            controls: ['up', 'down', 'link'],
        });
    });
});

describe('favorites machine: context events', () => {
    it('dismisses the notice', async () => {
        const running = await loaded();
        running.actor.send({ type: 'remove', nodeId: 'p1' });
        running.calls.remove[0]?.answer.resolve(undefined);
        await settled(running, (s) => s.context.notice !== undefined);

        running.actor.send({ type: 'notice.dismiss' });

        expect(running.actor.getSnapshot().context.notice).toBeUndefined();
    });

    it('calls the portal with a new api after the element changes it', async () => {
        const { actor, calls } = await loaded();
        const api = new ApiClient({ contextPath: '/other', csrfToken: 't2' });

        actor.send({ type: 'api.changed', api });
        actor.send({ type: 'reload' });

        expect(calls.load.at(-1)?.api).toBe(api);
    });
});

describe('favorites machine helpers', () => {
    it('lists collections first and finds the siblings of a favorite', () => {
        expect(allFavorites(data()).map((f) => f.nodeId)).toEqual(['c1', 'p1', 'p2', 'p3']);
        expect(siblingsOf(data(), NEWS)).toEqual([NEWS]);
        expect(siblingsOf(data(), MAIL)).toEqual([CALENDAR, MAIL, COURSES]);
    });

    it('reads the kind of an API error, and calls anything else malformed', () => {
        expect(errorKind(new ApiError('conflict', 409, 'x'))).toBe('conflict');
        expect(errorKind(new Error('x'))).toBe('malformed');
    });
});
