/**
 * Model-based tests: @xstate/graph walks the favorites machine and the real `<up-favorites>`
 * replays each path in Chromium, answering its REST calls as the path says. At the end of every
 * step the rendered output and its accessibility attributes must match the machine's state, and
 * axe must find nothing in the state the path reaches.
 */
import { getShortestPaths, getSimplePaths, type StatePath } from '@xstate/graph';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { userEvent } from 'vite-plus/test/browser';
import type { SnapshotFrom } from 'xstate';
import { ApiClient, ApiError, type ApiErrorKind } from '../../lib/api-client';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, noContent, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { FAVORITES_PATH, type FavoritePortlet, type FavoritesData } from './favorites-api';
import './up-favorites';
import type { UpFavorites } from './up-favorites';
import {
    allFavorites,
    favoritesMachine,
    type FavoritesEvent,
    type FavoritesMachine,
} from './up-favorites.machine';

type Snapshot = SnapshotFrom<FavoritesMachine>;
type Path = StatePath<Snapshot, FavoritesEvent>;

const API = `/uPortal${FAVORITES_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="favorites"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

function portlet(nodeId: string, name: string): FavoritePortlet {
    return {
        kind: 'portlet',
        nodeId,
        name,
        url: `/uPortal/p/${nodeId}/render.uP`,
        moveAllowed: true,
        deleteAllowed: true,
        fname: nodeId,
        description: null,
        target: null,
        iconUrl: null,
    };
}

const CALENDAR = portlet('a', 'Calendar');
const MAIL = portlet('b', 'Mail');
const TWO: FavoritesData = { collections: [], portlets: [CALENDAR, MAIL], marketplaceUrl: null };
const NONE: FavoritesData = { ...TWO, portlets: [] };
const SWAPPED: FavoritesData = { ...TWO, portlets: [MAIL, CALENDAR] };

const STATUS: Partial<Record<ApiErrorKind, number>> = { unauthorized: 401, server: 500 };

function failure(kind: ApiErrorKind): ApiError {
    return new ApiError(kind, STATUS[kind], kind);
}

/**
 * The events the traversal tries: user events, and the outcomes of the invoked REST calls
 * (`xstate.done.actor.*` / `xstate.error.actor.*`), which the test replays as responses.
 */
const EVENTS = [
    { type: 'xstate.done.actor.loadFavorites', output: TWO },
    { type: 'xstate.done.actor.loadFavorites', output: NONE },
    { type: 'xstate.error.actor.loadFavorites', error: failure('server') },
    { type: 'xstate.error.actor.loadFavorites', error: failure('unauthorized') },
    { type: 'reload' },
    { type: 'edit.toggle' },
    { type: 'edit.stop' },
    { type: 'remove', nodeId: 'a' },
    { type: 'xstate.done.actor.removeFavorite', output: undefined },
    { type: 'xstate.error.actor.removeFavorite', error: failure('server') },
    { type: 'move', nodeId: 'a', direction: 1, control: 'down' },
    { type: 'xstate.done.actor.moveFavorite', output: SWAPPED },
    { type: 'xstate.error.actor.moveFavorite', error: failure('server') },
] as unknown as FavoritesEvent[];

/**
 * States differ by their value and by the context the element renders: the notice it shows
 * (success or failure) and the favorites, in order.
 */
function serialize(snapshot: Snapshot): string {
    return JSON.stringify([
        snapshot.value,
        snapshot.context.notice?.kind ?? null,
        allFavorites(snapshot.context.favorites).map((favorite) => favorite.nodeId),
    ]);
}

const OPTIONS = {
    input: { api: new ApiClient({ contextPath: '/uPortal' }) },
    events: EVENTS,
    serializeState: serialize,
};

/** Holds the favorites API's requests until a step answers them. */
class Portal {
    readonly #waiting = new Map<string, ((response: Response) => void)[]>();

    constructor(server: FetchStub) {
        server.on('GET', API, () => this.#hold('loadFavorites'));
        for (const { nodeId } of TWO.portlets) {
            server.on('DELETE', `${API}/${nodeId}`, () => this.#hold('removeFavorite'));
            server.on('POST', `${API}/${nodeId}/move`, () => this.#hold('moveFavorite'));
        }
    }

    #hold(call: string): Promise<Response> {
        return new Promise((resolve) => {
            this.#waiting.set(call, [...(this.#waiting.get(call) ?? []), resolve]);
        });
    }

    async answer(call: string, response: Response): Promise<void> {
        await vi.waitFor(() => {
            expect(this.#waiting.get(call)?.length, `a pending ${call} request`).toBeGreaterThan(0);
        });
        this.#waiting.get(call)?.shift()?.(response);
    }
}

function shadow(element: UpFavorites): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function find(element: UpFavorites, selector: string): HTMLElement {
    const found = shadow(element).querySelector<HTMLElement>(selector);
    if (!found) {
        throw new Error(`nothing matches ${selector}`);
    }
    return found;
}

/** Makes the element do what the step's event stands for. */
async function replay(element: UpFavorites, portal: Portal, event: FavoritesEvent) {
    const outcome = event as unknown as { type: string; output?: unknown; error?: ApiError };
    const call = /^xstate\.(done|error)\.actor\.(\w+)$/.exec(outcome.type);
    if (call?.[1] === 'done') {
        await portal.answer(
            call[2] ?? '',
            outcome.output === undefined ? noContent() : json(outcome.output),
        );
        return;
    }
    if (call?.[1] === 'error') {
        await portal.answer(call[2] ?? '', text('', outcome.error?.status ?? 500));
        return;
    }
    switch (event.type) {
        case 'reload': {
            const retry = shadow(element).querySelector<HTMLElement>('.retry');
            if (retry) {
                await userEvent.click(retry);
            } else {
                void element.reload();
            }
            return;
        }
        case 'edit.toggle':
            await userEvent.click(find(element, '.edit-toggle'));
            return;
        case 'edit.stop':
            find(element, '.edit-toggle').focus();
            await userEvent.keyboard('{Escape}');
            return;
        case 'remove':
            await userEvent.click(
                find(element, `li[data-node-id="${event.nodeId}"] [data-control="remove"]`),
            );
            return;
        case 'move':
            await userEvent.click(
                find(
                    element,
                    `li[data-node-id="${event.nodeId}"] [data-control="${event.control}"]`,
                ),
            );
            return;
        default:
            throw new Error(`no way to replay ${event.type}`);
    }
}

/** What the element must show, and how it must expose it, in a state of the machine. */
function expectRendered(element: UpFavorites, snapshot: Snapshot): void {
    const root = shadow(element);
    const list = root.querySelector('ul.favorites-list');
    const notice = root.querySelector('.notice');

    if (snapshot.matches('loading')) {
        expect(root.querySelector('.loading')?.getAttribute('role')).toBe('status');
        expect(list).toBeNull();
        return;
    }
    if (snapshot.matches('failed')) {
        const alert = root.querySelector('[role="alert"]');
        const expired = snapshot.matches({ failed: 'sessionExpired' });
        expect(alert?.textContent).toContain(
            expired ? 'Your session has expired.' : 'Your favorites could not be loaded.',
        );
        expect(root.querySelector('.retry') !== null).toBe(!expired);
        expect(list).toBeNull();
        return;
    }

    const expected = snapshot.context.notice;
    if (expected === undefined) {
        expect(notice).toBeNull();
    } else {
        expect(notice?.textContent).toContain(expected.name);
        expect(notice?.getAttribute('role')).toBe(expected.kind === 'removed' ? null : 'alert');
    }

    if (snapshot.matches('empty')) {
        expect(root.querySelector('.empty')?.textContent).toContain('You have no favorites.');
        expect(list).toBeNull();
        return;
    }

    const editing = snapshot.matches({ list: { mode: 'editing' } });
    expect(list?.getAttribute('aria-label')).toBe('Favorites');
    expect(list?.getAttribute('aria-busy')).toBe(snapshot.hasTag('busy') ? 'true' : null);
    expect([...root.querySelectorAll('.favorite-name')].map((n) => n.textContent)).toEqual(
        allFavorites(snapshot.context.favorites).map((f) => f.name),
    );
    expect(root.querySelector('.edit-toggle')?.textContent?.trim()).toBe(
        editing ? 'Stop editing favorites' : 'Reorder favorites',
    );
    const moves = [...root.querySelectorAll('[data-control="up"], [data-control="down"]')];
    expect(moves).toHaveLength(editing ? 2 * snapshot.context.favorites.portlets.length : 0);
    for (const button of moves) {
        expect(button.getAttribute('aria-keyshortcuts')).toMatch(/^Alt\+Arrow(Up|Down)$/);
        expect(button.getAttribute('aria-label')).toMatch(/^Move .+ (up|down)$/);
    }
    expect(root.querySelector('.help') !== null).toBe(editing);
}

function describePath(path: Path): string {
    const events = path.steps.slice(1).map((step) => step.event.type.replace('xstate.', ''));
    return `${serialize(path.state)} after ${events.join(' > ') || 'connecting'}`;
}

/** Mounts a fresh element and walks it along the path, checking every step in order. */
async function walk(path: Path): Promise<UpFavorites> {
    const portal = new Portal(server);
    const element = await fixture<UpFavorites>(`<up-favorites ${ATTRIBUTES}></up-favorites>`);
    // The first step is the machine's initial state, which connecting the element reaches
    const [initial, ...steps] = path.steps;
    await vi.waitFor(() => {
        expectRendered(element, initial?.state ?? path.state);
    });
    await steps.reduce(async (previous, step) => {
        await previous;
        await replay(element, portal, step.event);
        await vi.waitFor(() => {
            expectRendered(element, step.state);
        });
    }, Promise.resolve());
    return element;
}

let server: FetchStub;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub().on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }));
});

afterEach(() => {
    cleanupFixtures();
});

describe('up-favorites model: shortest paths', () => {
    const paths = getShortestPaths(favoritesMachine, OPTIONS);

    it('reaches every state of the machine', () => {
        const values = new Set(paths.map((path) => JSON.stringify(path.state.value)));
        expect([...values].toSorted()).toEqual(
            [
                '"empty"',
                '"loading"',
                '{"failed":"retryable"}',
                '{"failed":"sessionExpired"}',
                ...['viewing', 'editing'].flatMap((mode) =>
                    ['idle', 'removing', 'moving'].map(
                        (request) => `{"list":{"mode":"${mode}","request":"${request}"}}`,
                    ),
                ),
            ].toSorted(),
        );
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            const element = await walk(path);

            expect(await axeViolations(element)).toEqual([]);
        },
    );
});

describe('up-favorites model: simple paths through loading and reordering', () => {
    const paths = getSimplePaths(favoritesMachine, {
        ...OPTIONS,
        events: EVENTS.filter((event) => /loadFavorites|^reload$|^edit\./.test(event.type)),
    });

    it('is small enough to walk every path', () => {
        expect(paths.length).toBeLessThanOrEqual(20);
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            await walk(path);
        },
    );
});
