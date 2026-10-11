/**
 * Model-based tests: @xstate/graph walks the search machine and the real `<up-search>` replays
 * each path in Chromium, answering its REST calls as the path says. At the end of every step the
 * rendered output and its accessibility attributes must match the machine's state, and axe must
 * find nothing in the state the path reaches.
 */
import { getShortestPaths, getSimplePaths, type StatePath } from '@xstate/graph';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { userEvent } from 'vite-plus/test/browser';
import type { SnapshotFrom } from 'xstate';
import { ApiClient, ApiError, type ApiErrorKind } from '../../lib/api-client';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { navigation } from './navigation';
import { PORTLET_LIST_PATH, SEARCH_PATH, type SearchResults } from './search-api';
import './up-search';
import type { UpSearch } from './up-search';
import { searchMachine, type SearchEvent, type SearchMachine } from './up-search.machine';

type Snapshot = SnapshotFrom<SearchMachine>;
type Path = StatePath<Snapshot, SearchEvent>;

const API = `/uPortal${SEARCH_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="maximized" context-path="/uPortal" fname="search"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

const FOUND: SearchResults = {
    query: 'cal',
    groups: [
        { type: 'people', label: null, results: [], failed: false },
        {
            type: 'portlets',
            label: null,
            failed: false,
            results: [
                {
                    title: 'Calendar',
                    url: '/uPortal/p/calendar/max',
                    attributes: { name: ['Calendar'], score: ['2.0'] },
                },
            ],
        },
    ],
};
const NO_TYPES: SearchResults = { query: 'cal', groups: [] };

const STATUS: Partial<Record<ApiErrorKind, number>> = { unauthorized: 401, server: 500 };

function failure(kind: ApiErrorKind): ApiError {
    return new ApiError(kind, STATUS[kind], kind);
}

/**
 * The events the traversal tries: user events, and the outcomes of the invoked search
 * (`xstate.done.actor.*` / `xstate.error.actor.*`), which the test replays as responses.
 */
const EVENTS = [
    { type: 'xstate.done.actor.search', output: FOUND },
    { type: 'xstate.done.actor.search', output: NO_TYPES },
    { type: 'xstate.error.actor.search', error: failure('server') },
    { type: 'xstate.error.actor.search', error: failure('unauthorized') },
    { type: 'search', query: 'mail' },
    { type: 'retry' },
    { type: 'tab.select', resultType: 'portlets' },
] as unknown as SearchEvent[];

/** States differ by their value and by what the element renders from the context. */
function serialize(snapshot: Snapshot): string {
    const { query, results, selected } = snapshot.context;
    return JSON.stringify([
        snapshot.value,
        query,
        results?.groups.length ?? null,
        selected ?? null,
    ]);
}

function options(query: string) {
    return {
        input: { api: new ApiClient({ contextPath: '/uPortal' }), query, types: [] },
        events: EVENTS,
        serializeState: serialize,
    };
}

/** Holds the search requests until a step answers the newest one. */
class Portal {
    readonly #waiting: ((response: Response) => void)[] = [];

    constructor(server: FetchStub) {
        server.on('GET', API, () => new Promise((resolve) => this.#waiting.push(resolve)));
    }

    async answer(response: Response): Promise<void> {
        await vi.waitFor(() => {
            expect(this.#waiting.length, 'a pending search').toBeGreaterThan(0);
        });
        // A new search aborts the one in flight, so only the newest request is still awaited
        const newest = this.#waiting.pop();
        this.#waiting.length = 0;
        newest?.(response);
    }
}

function shadow(element: Element): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function field(element: UpSearch): HTMLInputElement {
    const box = shadow(element).querySelector('up-search-box');
    const input = box ? shadow(box).querySelector('input') : null;
    if (!input) {
        throw new Error('no search field');
    }
    return input;
}

/** Makes the element do what the step's event stands for. */
async function replay(element: UpSearch, portal: Portal, event: SearchEvent) {
    const outcome = event as unknown as { type: string; output?: unknown; error?: ApiError };
    if (outcome.type === 'xstate.done.actor.search') {
        await portal.answer(json(outcome.output));
        return;
    }
    if (outcome.type === 'xstate.error.actor.search') {
        await portal.answer(text('', outcome.error?.status ?? 500));
        return;
    }
    switch (event.type) {
        case 'search':
            await userEvent.fill(field(element), event.query);
            await userEvent.keyboard('{Enter}');
            return;
        case 'retry': {
            const retry = shadow(element).querySelector<HTMLElement>('.retry');
            if (!retry) {
                throw new Error('no retry button');
            }
            await userEvent.click(retry);
            return;
        }
        case 'tab.select': {
            const tab = shadow(element).querySelector<HTMLElement>(
                `[role="tab"][data-type="${event.resultType}"]`,
            );
            if (!tab) {
                throw new Error(`no ${event.resultType} tab`);
            }
            await userEvent.click(tab);
            return;
        }
        default:
            throw new Error(`no way to replay ${event.type}`);
    }
}

/** What the element must show, and how it must expose it, in a state of the machine. */
function expectRendered(element: UpSearch, snapshot: Snapshot): void {
    const root = shadow(element);
    const tablist = root.querySelector('[role="tablist"]');
    const alert = root.querySelector('[role="alert"]');

    expect(field(element).value).toBe(snapshot.context.query);
    expect(root.querySelector('.search')?.getAttribute('aria-busy')).toBe(
        snapshot.hasTag('busy') ? 'true' : null,
    );
    expect(root.querySelector('.loading') !== null).toBe(snapshot.matches('searching'));

    if (snapshot.matches('failed')) {
        const expired = snapshot.matches({ failed: 'sessionExpired' });
        expect(alert?.textContent).toContain(
            expired ? 'Your session has expired.' : 'The search failed.',
        );
        expect(root.querySelector('.retry') !== null).toBe(!expired);
        expect(tablist).toBeNull();
        return;
    }
    expect(alert).toBeNull();

    if (!snapshot.matches('results')) {
        expect(tablist).toBeNull();
        expect(root.querySelector('.no-results')).toBeNull();
        return;
    }
    const { results, selected } = snapshot.context;
    if (results === undefined || selected === undefined) {
        expect(tablist).toBeNull();
        expect(root.querySelector('.no-results')?.textContent).toBe('No Results Found');
        return;
    }
    const tabs = [...root.querySelectorAll('[role="tab"]')];
    expect(tabs.map((tab) => tab.getAttribute('data-type'))).toEqual(
        results.groups.map((group) => group.type),
    );
    for (const tab of tabs) {
        const active = tab.getAttribute('data-type') === selected;
        expect(tab.getAttribute('aria-selected')).toBe(active ? 'true' : 'false');
        expect(tab.getAttribute('tabindex')).toBe(active ? '0' : '-1');
    }
    const panel = root.querySelector('[role="tabpanel"]');
    expect(panel?.getAttribute('aria-labelledby')).toBe(`tab-${selected}`);
    const group = results.groups.find((candidate) => candidate.type === selected);
    expect([...(panel?.querySelectorAll('h3') ?? [])].map((h) => h.textContent?.trim())).toEqual(
        group?.results.map((result) => result.title),
    );
    expect(panel?.querySelector('.no-results') !== null).toBe(group?.results.length === 0);
}

function describePath(path: Path): string {
    const events = path.steps.slice(1).map((step) => step.event.type.replace('xstate.', ''));
    return `${serialize(path.state)} after ${events.join(' > ') || 'connecting'}`;
}

/** Mounts a fresh element and walks it along the path, checking every step in order. */
async function walk(path: Path, query: string): Promise<UpSearch> {
    const portal = new Portal(server);
    const element = await fixture<UpSearch>(
        `<up-search ${ATTRIBUTES} query="${query}"></up-search>`,
    );
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
    vi.spyOn(navigation, 'replaceQuery').mockImplementation(() => {});
    server = new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', `/uPortal${PORTLET_LIST_PATH}`, text('', 500));
});

afterEach(() => {
    cleanupFixtures();
    vi.restoreAllMocks();
});

describe.each(['cal', ''])('up-search model, starting with the query "%s"', (query) => {
    const paths = getShortestPaths(searchMachine, options(query));

    it('reaches every state of the machine', () => {
        const values = new Set(paths.map((path) => JSON.stringify(path.state.value)));
        expect([...values].toSorted()).toEqual(
            [
                ...(query === '' ? ['"idle"'] : []),
                '"searching"',
                '"results"',
                '{"failed":"retryable"}',
                '{"failed":"sessionExpired"}',
            ].toSorted(),
        );
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            const element = await walk(path, query);

            expect(await axeViolations(element)).toEqual([]);
        },
    );
});

describe('up-search model: simple paths through searching and tabs', () => {
    const paths = getSimplePaths(searchMachine, {
        ...options('cal'),
        // Without a new query or a 401, which end or restart the walk
        events: EVENTS.filter(
            (event) => event.type !== 'search' && !JSON.stringify(event).includes('unauthorized'),
        ),
    });

    it('is small enough to walk every path', () => {
        expect(paths.length).toBeLessThanOrEqual(30);
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            await walk(path, 'cal');
        },
    );
});
