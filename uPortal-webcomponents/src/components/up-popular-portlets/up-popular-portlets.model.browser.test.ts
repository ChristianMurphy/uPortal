/**
 * Model-based tests: @xstate/graph walks the popular portlets machine and the real
 * `<up-popular-portlets>` replays each path in Chromium, answering its REST calls as the path
 * says. At the end of every step the rendered table, controls and their accessibility attributes
 * must match the machine's state and context, and axe must find nothing in the state the path
 * reaches.
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
import {
    POPULAR_PORTLETS_PATH,
    type PopularPortlet,
    type PopularPortletsData,
} from './popular-portlets-api';
import { tableView } from './table';
import './up-popular-portlets';
import type { UpPopularPortlets } from './up-popular-portlets';
import {
    popularPortletsMachine,
    type PopularPortletsEvent,
    type PopularPortletsMachine,
} from './up-popular-portlets.machine';

type Snapshot = SnapshotFrom<PopularPortletsMachine>;
type Path = StatePath<Snapshot, PopularPortletsEvent>;

const API = `/uPortal${POPULAR_PORTLETS_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="popular-portlets"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

function portlet(fname: string, count: number): PopularPortlet {
    return { fname, title: `Title ${fname}`, description: null, url: `/uPortal/p/${fname}`, count };
}

/** Six portlets: two pages of five. */
const SIX: PopularPortletsData = {
    days: 30,
    portlets: ['p1', 'p2', 'p3', 'p4', 'p5', 'p6'].map((fname, index) => portlet(fname, index + 1)),
};
const NONE: PopularPortletsData = { days: 30, portlets: [] };

const STATUS: Partial<Record<ApiErrorKind, number>> = { unauthorized: 401, server: 500 };

function failure(kind: ApiErrorKind): ApiError {
    return new ApiError(kind, STATUS[kind], kind);
}

/**
 * The events the traversal tries: user events, and the outcomes of the invoked REST call
 * (`xstate.done.actor.*` / `xstate.error.actor.*`), which the test replays as responses.
 */
const EVENTS = [
    { type: 'xstate.done.actor.loadPopularPortlets', output: SIX },
    { type: 'xstate.done.actor.loadPopularPortlets', output: NONE },
    { type: 'xstate.error.actor.loadPopularPortlets', error: failure('server') },
    { type: 'xstate.error.actor.loadPopularPortlets', error: failure('unauthorized') },
    { type: 'reload' },
    { type: 'days.change', days: 7 },
    { type: 'sort', column: 'title' },
    { type: 'page.size', size: 5 },
    { type: 'page.go', page: 1 },
    { type: 'filter.add', column: 'title', value: 'p6' },
    { type: 'filter.remove', column: 'title', value: 'p6' },
] as unknown as PopularPortletsEvent[];

/**
 * States differ by their value and by the context the element renders: the days, the rows, the
 * sort, the filters and the page.
 */
function serialize(snapshot: Snapshot): string {
    const { context } = snapshot;
    return JSON.stringify([
        snapshot.value,
        context.days,
        context.portlets.length,
        context.sort.direction,
        context.filters.title,
        context.pageSize,
        context.page,
    ]);
}

const OPTIONS = {
    input: { api: new ApiClient({ contextPath: '/uPortal' }), locale: 'en-US' },
    events: EVENTS,
    serializeState: serialize,
};

/** Holds the API's requests until a step answers them; the newest request is the live one. */
class Portal {
    readonly #waiting: ((response: Response) => void)[] = [];

    constructor(server: FetchStub) {
        server.on(
            'GET',
            API,
            () =>
                new Promise<Response>((resolve) => {
                    this.#waiting.push(resolve);
                }),
        );
    }

    async answer(response: Response): Promise<void> {
        await vi.waitFor(() => {
            expect(this.#waiting.length, 'a pending request').toBeGreaterThan(0);
        });
        // An earlier request was abandoned when other days were chosen
        const live = this.#waiting.pop();
        this.#waiting.length = 0;
        live?.(response);
    }
}

function shadow(element: UpPopularPortlets): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function find(element: UpPopularPortlets, selector: string): HTMLElement {
    const found = shadow(element).querySelector<HTMLElement>(selector);
    if (!found) {
        throw new Error(`nothing matches ${selector}`);
    }
    return found;
}

function select(element: UpPopularPortlets, selector: string): HTMLSelectElement {
    const found = find(element, selector);
    if (!(found instanceof HTMLSelectElement)) {
        throw new TypeError(`${selector} is no select`);
    }
    return found;
}

/** Makes the element do what the step's event stands for. */
async function replay(element: UpPopularPortlets, portal: Portal, event: PopularPortletsEvent) {
    const outcome = event as unknown as { type: string; output?: unknown; error?: ApiError };
    if (outcome.type === 'xstate.done.actor.loadPopularPortlets') {
        await portal.answer(json(outcome.output));
        return;
    }
    if (outcome.type === 'xstate.error.actor.loadPopularPortlets') {
        await portal.answer(text('', outcome.error?.status ?? 500));
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
        case 'days.change':
            await userEvent.selectOptions(select(element, '#days'), String(event.days));
            return;
        case 'sort':
            await userEvent.click(find(element, `button[data-sort="${event.column}"]`));
            return;
        case 'page.size':
            await userEvent.selectOptions(select(element, '#page-size'), String(event.size));
            return;
        case 'page.go':
            await userEvent.click(find(element, `button[data-page="${event.page}"]`));
            return;
        case 'filter.add':
            await userEvent.selectOptions(
                select(element, `select[data-filter="${event.column}"]`),
                event.value,
            );
            return;
        case 'filter.remove':
            await userEvent.click(find(element, '.filter-term'));
            return;
        default:
            throw new Error(`no way to replay ${event.type}`);
    }
}

/** What the element must show, and how it must expose it, in a state of the machine. */
function expectRendered(element: UpPopularPortlets, snapshot: Snapshot): void {
    const root = shadow(element);
    const { context } = snapshot;
    const table = root.querySelector('table');
    expect(select(element, '#days').value).toBe(String(context.days));

    if (snapshot.matches('loading')) {
        expect(root.querySelector('.loading')?.getAttribute('role')).toBe('status');
        expect(table).toBeNull();
        return;
    }
    if (snapshot.matches('failed')) {
        const expired = snapshot.matches({ failed: 'sessionExpired' });
        expect(root.querySelector('[role="alert"]')?.textContent).toContain(
            expired ? 'Your session has expired.' : 'The popular portlets could not be loaded.',
        );
        expect(root.querySelector('.retry') !== null).toBe(!expired);
        expect(table).toBeNull();
        return;
    }

    const view = tableView(
        context.portlets,
        context.filters,
        context.sort,
        context.pageSize,
        context.page,
    );
    expect(root.querySelector('.info')?.textContent?.trim()).toBe(
        `Viewing page ${view.page + 1}. Showing records ${view.from} to ${view.to} of ${view.total} items.`,
    );
    expect(select(element, '#page-size').value).toBe(String(context.pageSize));
    expect(
        [...root.querySelectorAll('tbody tr[data-fname]')].map((row) =>
            row.getAttribute('data-fname'),
        ),
    ).toEqual(view.rows.map((row) => row.fname));
    expect(
        [...root.querySelectorAll('[aria-current="page"]')].map((button) =>
            button.textContent?.trim(),
        ),
    ).toEqual([String(view.page + 1)]);

    if (snapshot.matches('empty')) {
        expect(root.querySelector('td.none')?.textContent).toContain('No data available');
        expect(root.querySelector('.filters')).toBeNull();
        return;
    }

    const sorted = [...root.querySelectorAll('th[aria-sort]')];
    expect(sorted).toHaveLength(1);
    expect(sorted[0]?.getAttribute('aria-sort')).toBe(context.sort.direction);
    expect(
        [...root.querySelectorAll('.filter-term')].map((term) => term.getAttribute('aria-label')),
    ).toEqual(context.filters.title.map((value) => `Remove the filter ${value}`));
    expect(root.querySelector('.page-link.previous')?.getAttribute('aria-disabled')).toBe(
        view.page === 0 ? 'true' : null,
    );
    expect(root.querySelector('.page-link.next')?.getAttribute('aria-disabled')).toBe(
        view.page === view.pageCount - 1 ? 'true' : null,
    );
}

function describePath(path: Path): string {
    const events = path.steps.slice(1).map((step) => step.event.type.replace('xstate.', ''));
    return `${serialize(path.state)} after ${events.join(' > ') || 'connecting'}`;
}

/** Mounts a fresh element and walks it along the path, checking every step in order. */
async function walk(path: Path): Promise<UpPopularPortlets> {
    const portal = new Portal(server);
    const element = await fixture<UpPopularPortlets>(
        `<up-popular-portlets ${ATTRIBUTES}></up-popular-portlets>`,
    );
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

describe('up-popular-portlets model: shortest paths', () => {
    const paths = getShortestPaths(popularPortletsMachine, OPTIONS);

    it('reaches every state of the machine', () => {
        const values = new Set(paths.map((path) => JSON.stringify(path.state.value)));
        expect([...values].toSorted()).toEqual(
            [
                '"empty"',
                '"list"',
                '"loading"',
                '{"failed":"retryable"}',
                '{"failed":"sessionExpired"}',
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

describe('up-popular-portlets model: simple paths through loading and days', () => {
    const paths = getSimplePaths(popularPortletsMachine, {
        ...OPTIONS,
        events: EVENTS.filter((event) => /loadPopularPortlets|^reload$|^days\./.test(event.type)),
    });

    it('is small enough to walk every path', () => {
        expect(paths.length).toBeLessThanOrEqual(100);
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            await walk(path);
        },
    );
});
