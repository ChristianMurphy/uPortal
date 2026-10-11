/**
 * Model-based tests: @xstate/graph walks the user locale machine and the real `<up-user-locale>`
 * replays each path in Chromium, answering its REST calls as the path says. At the end of every
 * step the rendered output and its accessibility attributes must match the machine's state, and
 * axe must find nothing in the state the path reaches.
 */
import { getShortestPaths, getSimplePaths, type StatePath } from '@xstate/graph';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { userEvent } from 'vite-plus/test/browser';
import type { SnapshotFrom } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { LOCALE_PATH, type UserLocaleData } from './locale-api';
import { page, type UpUserLocale } from './up-user-locale';
import {
    userLocaleMachine,
    type UserLocaleEvent,
    type UserLocaleMachine,
} from './up-user-locale.machine';

type Snapshot = SnapshotFrom<UserLocaleMachine>;
type Path = StatePath<Snapshot, UserLocaleEvent>;

const API = `/uPortal${LOCALE_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="user-locales-selector"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

const IN_ENGLISH: UserLocaleData = {
    selected: 'en_US',
    locales: [
        { code: 'en_US', tag: 'en-US', name: 'English' },
        { code: 'fr_FR', tag: 'fr-FR', name: 'French' },
    ],
};
const NONE_CHOSEN: UserLocaleData = { ...IN_ENGLISH, selected: null };
const IN_FRENCH: UserLocaleData = {
    selected: 'fr_FR',
    locales: [
        { code: 'en_US', tag: 'en-US', name: 'anglais' },
        { code: 'fr_FR', tag: 'fr-FR', name: 'français' },
    ],
};

/**
 * The events the traversal tries: user events, and the outcomes of the invoked REST calls
 * (`xstate.done.actor.*` / `xstate.error.actor.*`), which the test replays as responses.
 */
const EVENTS = [
    { type: 'xstate.done.actor.loadLocales', output: IN_ENGLISH },
    { type: 'xstate.done.actor.loadLocales', output: NONE_CHOSEN },
    { type: 'xstate.error.actor.loadLocales', error: new ApiError('server', 500, 'boom') },
    { type: 'reload' },
    { type: 'choose', code: 'fr_FR' },
    { type: 'submit' },
    { type: 'xstate.done.actor.saveLocale', output: IN_FRENCH },
    { type: 'xstate.error.actor.saveLocale', error: new ApiError('server', 500, 'boom') },
] as unknown as UserLocaleEvent[];

/**
 * States differ by their value and by the context the element renders: the notice, the checked
 * language and the language the names are in.
 */
function serialize(snapshot: Snapshot): string {
    const { notice, choice, data } = snapshot.context;
    return JSON.stringify([
        snapshot.value,
        notice?.kind ?? null,
        choice ?? null,
        data.locales.map((locale) => locale.name),
    ]);
}

const OPTIONS = {
    input: { api: new ApiClient({ contextPath: '/uPortal' }) },
    events: EVENTS,
    serializeState: serialize,
};

/** Holds the locale API's requests until a step answers them. */
class Portal {
    readonly #waiting = new Map<string, ((response: Response) => void)[]>();

    constructor(server: FetchStub) {
        server.on('GET', API, () => this.#hold('loadLocales'));
        server.on('PUT', API, () => this.#hold('saveLocale'));
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

function shadow(element: UpUserLocale): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function find(element: UpUserLocale, selector: string): HTMLElement {
    const found = shadow(element).querySelector<HTMLElement>(selector);
    if (!found) {
        throw new Error(`nothing matches ${selector}`);
    }
    return found;
}

/** Makes the element do what the step's event stands for. */
async function replay(element: UpUserLocale, portal: Portal, event: UserLocaleEvent) {
    const outcome = event as unknown as { type: string; output?: unknown; error?: ApiError };
    const call = /^xstate\.(done|error)\.actor\.(\w+)$/.exec(outcome.type);
    if (call?.[1] === 'done') {
        await portal.answer(call[2] ?? '', json(outcome.output));
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
        case 'choose':
            await userEvent.click(find(element, `#locale-${event.code}`));
            return;
        case 'submit':
            await userEvent.click(find(element, '.update'));
            return;
        default:
            throw new Error(`no way to replay ${event.type}`);
    }
}

/** What the element must show, and how it must expose it, in a state of the machine. */
function expectRendered(element: UpUserLocale, snapshot: Snapshot): void {
    const root = shadow(element);
    const form = root.querySelector('form');

    if (snapshot.matches('loading')) {
        expect(root.querySelector('.loading')?.getAttribute('role')).toBe('status');
        expect(form).toBeNull();
        return;
    }
    if (snapshot.matches('failed')) {
        expect(root.querySelector('[role="alert"]')?.textContent).toContain(
            'The languages could not be loaded.',
        );
        expect(root.querySelector('.retry')).not.toBeNull();
        expect(form).toBeNull();
        return;
    }

    const { notice, choice, data } = snapshot.context;
    const busy = snapshot.hasTag('busy');
    expect(form?.getAttribute('aria-busy')).toBe(busy ? 'true' : null);
    expect(root.querySelector('.update')?.hasAttribute('disabled')).toBe(busy);
    const radios = [...root.querySelectorAll<HTMLInputElement>('input[type="radio"]')];
    expect(radios.map((radio) => radio.labels?.[0]?.textContent)).toEqual(
        data.locales.map((locale) => locale.name),
    );
    expect(radios.find((radio) => radio.checked)?.value).toBe(choice);
    expect(radios.every((radio) => radio.disabled === busy)).toBe(true);
    expect(root.querySelector('.notice')?.getAttribute('role') ?? null).toBe(
        notice === undefined ? null : 'alert',
    );
    expect(reload).toHaveBeenCalledTimes(snapshot.matches({ ready: 'saved' }) ? 1 : 0);
}

function describePath(path: Path): string {
    const events = path.steps.slice(1).map((step) => step.event.type.replace('xstate.', ''));
    return `${serialize(path.state)} after ${events.join(' > ') || 'connecting'}`;
}

/** Mounts a fresh element and walks it along the path, checking every step in order. */
async function walk(path: Path): Promise<UpUserLocale> {
    const portal = new Portal(server);
    const element = await fixture<UpUserLocale>(`<up-user-locale ${ATTRIBUTES}></up-user-locale>`);
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
let reload: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub().on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }));
    reload = vi.spyOn(page, 'reload').mockImplementation(() => undefined);
});

afterEach(() => {
    cleanupFixtures();
    vi.restoreAllMocks();
});

describe('up-user-locale model: shortest paths', () => {
    const paths = getShortestPaths(userLocaleMachine, OPTIONS);

    it('reaches every state of the machine', () => {
        const values = new Set(paths.map((path) => JSON.stringify(path.state.value)));
        expect([...values].toSorted()).toEqual(
            [
                '"loading"',
                '"failed"',
                '{"ready":"idle"}',
                '{"ready":"saving"}',
                '{"ready":"saved"}',
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

describe('up-user-locale model: simple paths through choosing and saving', () => {
    const paths = getSimplePaths(userLocaleMachine, {
        ...OPTIONS,
        events: EVENTS.filter((event) => !/^reload$/.test(event.type)),
    });

    it('is small enough to walk every path', () => {
        expect(paths.length).toBeLessThanOrEqual(30);
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            await walk(path);
        },
    );
});
