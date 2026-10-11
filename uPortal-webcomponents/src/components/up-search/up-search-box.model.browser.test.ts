/**
 * Model-based tests: @xstate/graph walks the type-ahead machine and the real `<up-search-box>`
 * replays each path in Chromium. The portlet list request is answered as the path says, and the
 * pause after typing runs on fake timers, so a step ends exactly when the path says the pause
 * ends. After every step the suggestions shown, the active one and the field's ARIA attributes
 * must match the machine's state, and axe must find nothing in the state the path reaches.
 */
import { getShortestPaths, type StatePath } from '@xstate/graph';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { userEvent } from 'vite-plus/test/browser';
import type { SnapshotFrom } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { navigation } from './navigation';
import { PORTLET_LIST_PATH, type Suggestion } from './search-api';
import './up-search-box';
import type { UpSearchBox } from './up-search-box';
import {
    searchBoxMachine,
    TYPE_AHEAD_DELAY,
    type SearchBoxEvent,
    type SearchBoxMachine,
} from './up-search-box.machine';

type Snapshot = SnapshotFrom<SearchBoxMachine>;
type Path = StatePath<Snapshot, SearchBoxEvent>;

const LIST_API = `/uPortal${PORTLET_LIST_PATH}`;
const PAUSE = 'xstate.after.typeAhead.searchBox.popup.waiting';

function suggestion(fname: string, title: string): Suggestion {
    return {
        fname,
        title,
        description: `About ${title}`,
        label: `${title}~${title}~${fname}~About ${title}`,
    };
}

const SUGGESTIONS = [suggestion('calendar', 'Calendar'), suggestion('courses', 'Courses')];

/** The portlet list that the suggestions come from. */
const REGISTRY = {
    registry: {
        channels: SUGGESTIONS.map(({ fname, title, description }) => ({
            fname,
            title,
            name: title,
            description,
        })),
        categories: [],
    },
};

const EVENTS = [
    { type: 'xstate.done.actor.loadSuggestions', output: SUGGESTIONS },
    { type: 'xstate.error.actor.loadSuggestions', error: new ApiError('server', 500, 'down') },
    { type: 'typed', term: 'c' },
    { type: 'typed', term: 'zz' },
    { type: 'typed', term: '' },
    { type: PAUSE },
    { type: 'next' },
    { type: 'previous' },
    { type: 'close' },
    { type: 'choose', index: 0 },
] as unknown as SearchBoxEvent[];

/** States differ by their value and by what the element renders from the context. */
function serialize(snapshot: Snapshot): string {
    const { term, matches, active } = snapshot.context;
    return JSON.stringify([snapshot.value, term, matches.length, active]);
}

const OPTIONS = {
    input: { api: new ApiClient({ contextPath: '/uPortal' }) },
    events: EVENTS,
    serializeState: serialize,
};

// Saved before the fake timers replace them, to poll the page in real time
const realSetTimeout = globalThis.setTimeout;

/** Retries `check` in real time until it passes, or fails with its last error. */
async function eventually(check: () => void, deadline = Date.now() + 2000): Promise<void> {
    try {
        check();
    } catch (error) {
        if (Date.now() > deadline) {
            throw error;
        }
        await new Promise((resolve) => realSetTimeout(resolve, 20));
        await eventually(check, deadline);
    }
}

/** Holds the portlet list request until a step answers it. */
class Portal {
    readonly #waiting: ((response: Response) => void)[] = [];

    constructor(server: FetchStub) {
        server.on('GET', LIST_API, () => new Promise((resolve) => this.#waiting.push(resolve)));
    }

    async answer(response: Response): Promise<void> {
        await eventually(() => {
            expect(this.#waiting.length, 'a pending portlet list request').toBeGreaterThan(0);
        });
        this.#waiting.shift()?.(response);
    }
}

function shadow(element: Element): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function field(element: UpSearchBox): HTMLInputElement {
    const input = shadow(element).querySelector('input');
    if (!input) {
        throw new Error('no search field');
    }
    return input;
}

/** Makes the element do what the step's event stands for. */
async function replay(element: UpSearchBox, portal: Portal, event: SearchBoxEvent) {
    const type: string = event.type;
    if (type === 'xstate.done.actor.loadSuggestions') {
        await portal.answer(json(REGISTRY));
        return;
    }
    if (type === 'xstate.error.actor.loadSuggestions') {
        await portal.answer(text('', 500));
        return;
    }
    if (type === PAUSE) {
        vi.advanceTimersByTime(TYPE_AHEAD_DELAY);
        return;
    }
    switch (event.type) {
        case 'typed':
            await userEvent.fill(field(element), event.term);
            return;
        case 'next':
            await userEvent.keyboard('{ArrowDown}');
            return;
        case 'previous':
            await userEvent.keyboard('{ArrowUp}');
            return;
        case 'close':
            await userEvent.keyboard('{Escape}');
            return;
        case 'choose': {
            const option = shadow(element).querySelector<HTMLElement>(`#suggestion-${event.index}`);
            if (!option) {
                throw new Error(`no suggestion ${event.index}`);
            }
            await userEvent.click(option);
            return;
        }
        default:
            throw new Error(`no way to replay ${event.type}`);
    }
}

/** What the element must show, and how it must expose it, in a state of the machine. */
function expectRendered(element: UpSearchBox, snapshot: Snapshot): void {
    const root = shadow(element);
    const input = field(element);
    const listbox = root.querySelector('[role="listbox"]');
    const options = [...root.querySelectorAll('[role="option"]')];
    const open = snapshot.matches({ popup: 'open' });
    const { matches, active } = snapshot.context;

    expect(input.value).toBe(snapshot.context.term);
    expect(listbox?.hasAttribute('hidden')).toBe(!open);
    expect(options.map((option) => option.querySelector('.suggestion-title')?.textContent)).toEqual(
        open ? matches.map((match) => match.title) : [],
    );
    for (const [index, option] of options.entries()) {
        expect(option.getAttribute('aria-selected')).toBe(index === active ? 'true' : 'false');
    }
    expect(input.getAttribute('aria-activedescendant')).toBe(
        open && active >= 0 ? `suggestion-${active}` : null,
    );
    expect(root.querySelector('[role="status"]')?.textContent?.trim()).toBe(
        open
            ? `${matches.length} results are available, use up and down arrow keys to navigate.`
            : '',
    );
}

function describePath(path: Path): string {
    const events = path.steps.slice(1).map((step) => {
        const type: string = step.event.type;
        return type === PAUSE ? 'pause' : type.replace('xstate.', '');
    });
    return `${serialize(path.state)} after ${events.join(' > ') || 'connecting'}`;
}

let server: FetchStub;
let assign: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
    clearMessageCache();
    assign = vi.spyOn(navigation, 'assign').mockImplementation(() => {});
    server = new FetchStub().on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }));
});

afterEach(() => {
    vi.useRealTimers();
    cleanupFixtures();
    vi.restoreAllMocks();
});

/** Mounts a fresh box and walks it along the path, checking every step in order. */
async function walk(path: Path): Promise<UpSearchBox> {
    const portal = new Portal(server);
    const element = await fixture<UpSearchBox>(
        '<up-search-box locale="en-US" context-path="/uPortal"></up-search-box>',
    );
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    field(element).focus();
    const [initial, ...steps] = path.steps;
    await eventually(() => {
        expectRendered(element, initial?.state ?? path.state);
    });
    await steps.reduce(async (previous, step) => {
        await previous;
        await replay(element, portal, step.event);
        await eventually(() => {
            expectRendered(element, step.state);
        });
    }, Promise.resolve());
    return element;
}

describe('up-search-box model: shortest paths', () => {
    const paths = getShortestPaths(searchBoxMachine, OPTIONS);

    it('reaches every state of the machine', () => {
        const values = new Set(paths.map((path) => JSON.stringify(path.state.value)));
        expect([...values].toSorted()).toEqual(
            ['loading', 'ready', 'unavailable']
                .flatMap((source) =>
                    ['closed', 'waiting', 'open'].map(
                        (popup) => `{"source":"${source}","popup":"${popup}"}`,
                    ),
                )
                // The suggestions only open once the list has loaded
                .filter((value) => !/"(loading|unavailable)","popup":"open"/.test(value))
                .toSorted(),
        );
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            const element = await walk(path);

            const chosen = path.steps.some((step) => step.event.type === 'choose');
            expect(assign).toHaveBeenCalledTimes(chosen ? 1 : 0);
            if (chosen) {
                expect(assign).toHaveBeenCalledWith('/uPortal/p/calendar/max');
            }
            vi.useRealTimers();
            expect(await axeViolations(element)).toEqual([]);
        },
    );
});
