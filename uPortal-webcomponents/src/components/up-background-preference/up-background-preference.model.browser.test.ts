/**
 * Model-based tests: @xstate/graph walks the background preference machine and the real
 * `<up-background-preference>` replays each path in Chromium, answering its REST calls as the path
 * says. At the end of every step the rendered output, its accessibility attributes, the focus and
 * the page background must match the machine's state, and axe must find nothing in the state the
 * path reaches.
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
import { BACKGROUND_PATH, type BackgroundPreference } from './background-api';
import { applyPageBackground } from './page-background';
import './up-background-preference';
import type { UpBackgroundPreference } from './up-background-preference';
import {
    backgroundPreferenceMachine,
    type BackgroundPreferenceEvent,
    type BackgroundPreferenceMachine,
} from './up-background-preference.machine';

type Snapshot = SnapshotFrom<BackgroundPreferenceMachine>;
type Path = StatePath<Snapshot, BackgroundPreferenceEvent>;

const API = `/uPortal${BACKGROUND_PATH}background-preference`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="background-preference"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';
const GALAXY = {
    url: '/uPortal/bg/galaxy.jpg',
    thumbnailUrl: '/uPortal/bg/t/galaxy.jpg',
    caption: 'Galaxy',
};
const NONE_CHOSEN: BackgroundPreference = {
    containerSelector: 'body #portalPageBody::before',
    images: [GALAXY],
    selected: undefined,
};
const GALAXY_CHOSEN: BackgroundPreference = { ...NONE_CHOSEN, selected: GALAXY.url };

/** The answer as the API sends it: null, not undefined, for none. */
function body(data: BackgroundPreference): unknown {
    return { ...data, selected: data.selected ?? null };
}

const EVENTS = [
    { type: 'xstate.done.actor.loadBackground', output: NONE_CHOSEN },
    { type: 'xstate.error.actor.loadBackground', error: new ApiError('server', 500, 'boom') },
    { type: 'xstate.error.actor.loadBackground', error: new ApiError('unauthorized', 401, 'x') },
    { type: 'reload' },
    { type: 'toggle' },
    { type: 'close' },
    { type: 'choose', url: GALAXY.url },
    { type: 'choose', url: undefined },
    { type: 'xstate.done.actor.saveBackground', output: GALAXY_CHOSEN },
    { type: 'xstate.done.actor.saveBackground', output: NONE_CHOSEN },
    { type: 'xstate.error.actor.saveBackground', error: new ApiError('server', 500, 'boom') },
    { type: 'xstate.error.actor.saveBackground', error: new ApiError('forbidden', 403, 'csrf') },
] as unknown as BackgroundPreferenceEvent[];

/** States differ by their value and by the context the element renders. */
function serialize(snapshot: Snapshot): string {
    const { notice, data, pending } = snapshot.context;
    return JSON.stringify([
        snapshot.value,
        notice?.kind ?? null,
        data.selected ?? null,
        pending ?? null,
    ]);
}

const OPTIONS = {
    input: { api: new ApiClient({ contextPath: '/uPortal' }), fname: 'background-preference' },
    events: EVENTS,
    serializeState: serialize,
};

/** Holds the API's requests until a step answers them. */
class Portal {
    readonly #waiting = new Map<string, ((response: Response) => void)[]>();

    constructor(server: FetchStub) {
        server.on('GET', API, () => this.#hold('loadBackground'));
        server.on('PUT', API, () => this.#hold('saveBackground'));
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

function shadow(element: UpBackgroundPreference): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function find(element: UpBackgroundPreference, selector: string): HTMLElement {
    const found = shadow(element).querySelector<HTMLElement>(selector);
    if (!found) {
        throw new Error(`nothing matches ${selector}`);
    }
    return found;
}

function choice(element: UpBackgroundPreference, caption: string): HTMLElement {
    const found = [...shadow(element).querySelectorAll<HTMLElement>('.choice')].find(
        (button) => button.textContent?.trim() === caption,
    );
    if (!found) {
        throw new Error(`no choice ${caption}`);
    }
    return found;
}

/** Makes the element do what the step's event stands for. */
async function replay(
    element: UpBackgroundPreference,
    portal: Portal,
    event: BackgroundPreferenceEvent,
) {
    const outcome = event as unknown as {
        type: string;
        output?: BackgroundPreference;
        error?: ApiError;
    };
    const call = /^xstate\.(done|error)\.actor\.(\w+)$/.exec(outcome.type);
    if (call?.[1] === 'done') {
        await portal.answer(call[2] ?? '', json(body(outcome.output ?? NONE_CHOSEN)));
        return;
    }
    if (call?.[1] === 'error') {
        await portal.answer(call[2] ?? '', text('', outcome.error?.status ?? 500));
        return;
    }
    switch (event.type) {
        case 'reload':
            await userEvent.click(find(element, '.retry'));
            return;
        case 'toggle':
            await userEvent.click(find(element, '.toggle'));
            return;
        case 'close':
            choice(element, 'None').focus();
            await userEvent.keyboard('{Escape}');
            return;
        case 'choose':
            await userEvent.click(choice(element, event.url === undefined ? 'None' : 'Galaxy'));
            return;
        default:
            throw new Error(`no way to replay ${event.type}`);
    }
}

const portalPageBody = document.createElement('div');
portalPageBody.id = 'portalPageBody';
document.body.append(portalPageBody);

/**
 * What the element must show, and how it must expose it, in a state of the machine. `closedByUser`
 * is true when the step closed the list, which gives focus back to the button.
 */
function expectRendered(
    element: UpBackgroundPreference,
    snapshot: Snapshot,
    closedByUser: boolean,
) {
    const root = shadow(element);
    const buttons = [...root.querySelectorAll('button')].map((b) => b.textContent?.trim());
    const alert = root.querySelector('[role="alert"]');

    if (snapshot.matches('loading')) {
        expect(root.querySelector('.loading')?.getAttribute('role')).toBe('status');
        expect(buttons).toEqual([]);
        return;
    }
    if (snapshot.matches('failed')) {
        expect(alert?.textContent).toContain('The backgrounds could not be loaded.');
        expect(buttons).toEqual(['Try again']);
        return;
    }
    if (snapshot.matches('signedOut')) {
        expect(alert?.textContent).toContain('Sign in again to change your background.');
        expect(buttons).toEqual([]);
        return;
    }

    const { data, notice } = snapshot.context;
    const background = getComputedStyle(portalPageBody, '::before').backgroundImage;
    if (data.selected === undefined) {
        expect(background).toBe('none');
    } else {
        expect(background).toContain(data.selected);
    }
    const toggle = root.querySelector('.toggle');
    if (snapshot.matches({ ready: 'closed' })) {
        expect(toggle?.getAttribute('aria-expanded')).toBe('false');
        expect(buttons).toEqual(['Change Background Image']);
        if (closedByUser) {
            expect(root.activeElement).toBe(toggle);
        }
        return;
    }
    const busy = snapshot.hasTag('busy');
    expect(toggle?.getAttribute('aria-expanded')).toBe('true');
    expect(buttons).toEqual(['Change Background Image', 'None', 'Galaxy']);
    expect(root.querySelector('#choices')?.getAttribute('aria-busy')).toBe(busy ? 'true' : null);
    const choices = [...root.querySelectorAll<HTMLButtonElement>('.choice')];
    expect(choices.every((b) => b.disabled === busy)).toBe(true);
    expect(choices.map((b) => b.getAttribute('aria-pressed'))).toEqual([
        String(data.selected === undefined),
        String(data.selected === GALAXY.url),
    ]);
    expect(root.querySelector('.notice')?.getAttribute('role') ?? null).toBe(
        notice === undefined ? null : 'alert',
    );
}

function describePath(path: Path): string {
    const events = path.steps.slice(1).map((step) => step.event.type.replace('xstate.', ''));
    return `${serialize(path.state)} after ${events.join(' > ') || 'connecting'}`;
}

function isOpen(snapshot: Snapshot): boolean {
    return snapshot.matches({ ready: 'open' }) || snapshot.matches({ ready: 'saving' });
}

/** Mounts a fresh element and walks it along the path, checking every step in order. */
async function walk(path: Path): Promise<UpBackgroundPreference> {
    const portal = new Portal(server);
    const element = await fixture<UpBackgroundPreference>(
        `<up-background-preference ${ATTRIBUTES}></up-background-preference>`,
    );
    const [initial, ...steps] = path.steps;
    const first = initial?.state ?? path.state;
    await vi.waitFor(() => {
        expectRendered(element, first, false);
    });
    await steps.reduce(async (previous, step) => {
        const before = await previous;
        await replay(element, portal, step.event);
        const closedByUser = isOpen(before) && step.state.matches({ ready: 'closed' });
        await vi.waitFor(() => {
            expectRendered(element, step.state, closedByUser);
        });
        return step.state;
    }, Promise.resolve(first));
    return element;
}

let server: FetchStub;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub().on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }));
});

afterEach(() => {
    cleanupFixtures();
    applyPageBackground(document, '#portalPageBody', undefined);
});

describe('up-background-preference model: shortest paths', () => {
    const paths = getShortestPaths(backgroundPreferenceMachine, OPTIONS);

    it('reaches every state of the machine', () => {
        const values = new Set(paths.map((path) => JSON.stringify(path.state.value)));
        expect([...values].toSorted()).toEqual(
            [
                '"loading"',
                '"failed"',
                '"signedOut"',
                '{"ready":"closed"}',
                '{"ready":"open"}',
                '{"ready":"saving"}',
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

describe('up-background-preference model: simple paths through one change', () => {
    // From the first load on: open and close the list, choose the image, and every outcome of
    // saving it. The shortest paths cover the load failures, Escape and None.
    const skipped = new Set(['xstate.error.actor.loadBackground', 'reload', 'close']);
    const paths = getSimplePaths(backgroundPreferenceMachine, {
        ...OPTIONS,
        events: EVENTS.filter((event) => {
            const type: string = event.type;
            const { url, output } = event as { url?: string; output?: BackgroundPreference };
            const choosesNone = type === 'choose' && url === undefined;
            const savesNone = type === 'xstate.done.actor.saveBackground' && output === NONE_CHOSEN;
            return !skipped.has(type) && !choosesNone && !savesNone;
        }),
    });

    it('is small enough to walk every path', () => {
        expect(paths.length).toBeLessThanOrEqual(40);
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            await walk(path);
        },
    );
});
