/**
 * Model-based tests: @xstate/graph walks the reset-my-layout machine and the real
 * `<up-reset-my-layout>` replays each path in Chromium, answering its REST calls as the path says.
 * At the end of every step the rendered output, its accessibility attributes and the focus must
 * match the machine's state, and axe must find nothing in the state the path reaches.
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
import { RESET_PATH } from './layout-reset-api';
import './up-reset-my-layout';
import type { UpResetMyLayout } from './up-reset-my-layout';
import {
    resetMyLayoutMachine,
    type ResetMyLayoutEvent,
    type ResetMyLayoutMachine,
} from './up-reset-my-layout.machine';

type Snapshot = SnapshotFrom<ResetMyLayoutMachine>;
type Path = StatePath<Snapshot, ResetMyLayoutEvent>;

const API = `/uPortal${RESET_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="reset-my-layout"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

/**
 * The events the traversal tries: user events, and the outcomes of the invoked REST calls
 * (`xstate.done.actor.*` / `xstate.error.actor.*`), which the test replays as responses.
 */
const EVENTS = [
    { type: 'xstate.done.actor.loadReset', output: { userName: 'student' } },
    { type: 'xstate.error.actor.loadReset', error: new ApiError('server', 500, 'boom') },
    { type: 'xstate.error.actor.loadReset', error: new ApiError('unauthorized', 401, 'guest') },
    { type: 'reload' },
    { type: 'reset' },
    { type: 'confirm' },
    { type: 'cancel' },
    { type: 'continue' },
    { type: 'xstate.done.actor.resetLayout', output: { userName: 'student' } },
    { type: 'xstate.error.actor.resetLayout', error: new ApiError('server', 500, 'boom') },
    { type: 'xstate.error.actor.resetLayout', error: new ApiError('unauthorized', 401, 'gone') },
] as unknown as ResetMyLayoutEvent[];

/** States differ by their value and by the context the element renders. */
function serialize(snapshot: Snapshot): string {
    const { notice, userName } = snapshot.context;
    return JSON.stringify([snapshot.value, notice?.kind ?? null, userName]);
}

const OPTIONS = {
    input: { api: new ApiClient({ contextPath: '/uPortal' }) },
    events: EVENTS,
    serializeState: serialize,
};

/** Holds the API's requests until a step answers them. */
class Portal {
    readonly #waiting = new Map<string, ((response: Response) => void)[]>();

    constructor(server: FetchStub) {
        server.on('GET', API, () => this.#hold('loadReset'));
        server.on('POST', API, () => this.#hold('resetLayout'));
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

function shadow(element: UpResetMyLayout): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function find(element: UpResetMyLayout, selector: string): HTMLElement {
    const found = shadow(element).querySelector<HTMLElement>(selector);
    if (!found) {
        throw new Error(`nothing matches ${selector}`);
    }
    return found;
}

const BUTTONS: Partial<Record<ResetMyLayoutEvent['type'], string>> = {
    reload: '.retry',
    reset: '.reset-now',
    confirm: '.confirm',
    cancel: '.cancel',
    continue: '.continue',
};

/** Makes the element do what the step's event stands for. */
async function replay(element: UpResetMyLayout, portal: Portal, event: ResetMyLayoutEvent) {
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
    const selector = BUTTONS[event.type];
    if (selector === undefined) {
        throw new Error(`no way to replay ${event.type}`);
    }
    await userEvent.click(find(element, selector));
}

/** Where focus goes when the user reaches the state: the new view's heading or main control. */
function focusTarget(snapshot: Snapshot): string | undefined {
    if (snapshot.matches('loading')) {
        return undefined;
    }
    if (snapshot.matches('failed') || snapshot.matches('signedOut')) {
        return '[role="alert"]';
    }
    return snapshot.matches({ ready: 'start' }) ? '.reset-now' : 'h2';
}

/**
 * What the element must show, and how it must expose it, in a state of the machine. `moved` is
 * true when the step changed the state after the first load, which moves focus to the new view.
 */
function expectRendered(element: UpResetMyLayout, snapshot: Snapshot, moved: boolean): void {
    const root = shadow(element);
    const content = root.textContent?.replace(/\s+/g, ' ') ?? '';
    const buttons = [...root.querySelectorAll('button')].map((b) => b.textContent?.trim());
    const alert = root.querySelector('[role="alert"]');
    const { notice, userName } = snapshot.context;

    if (snapshot.matches('loading')) {
        expect(root.querySelector('.loading')?.getAttribute('role')).toBe('status');
        expect(buttons).toEqual([]);
    } else if (snapshot.matches('failed')) {
        expect(alert?.textContent).toContain('Your layout could not be found.');
        expect(buttons).toEqual(['Try again']);
    } else if (snapshot.matches('signedOut')) {
        expect(alert?.textContent).toContain('Sign in again to reset your layout.');
        expect(buttons).toEqual([]);
    } else if (snapshot.matches({ ready: 'start' })) {
        expect(content).toContain('NOTE: You must sign on again to see the changes.');
        expect(buttons).toEqual(['Reset Now']);
    } else if (snapshot.matches({ ready: 'done' })) {
        expect(root.querySelector('[role="status"]')?.textContent).toContain(
            `Layout for ${userName} has been reset`,
        );
        expect(buttons).toEqual(['Continue']);
    } else {
        // confirming or resetting
        const busy = snapshot.hasTag('busy');
        expect(root.querySelector('section')?.getAttribute('aria-busy')).toBe(busy ? 'true' : null);
        expect(content).toContain(`Are you sure you want to reset the layout for ${userName}?`);
        expect(buttons).toEqual(['Reset Layout', 'Cancel']);
        expect([...root.querySelectorAll('button')].every((b) => b.disabled === busy)).toBe(true);
        expect(root.querySelector('.notice')?.getAttribute('role') ?? null).toBe(
            notice === undefined ? null : 'alert',
        );
    }

    const target = focusTarget(snapshot);
    if (moved && target !== undefined) {
        expect(root.activeElement).toBe(root.querySelector(target));
    }
    if (!moved && snapshot.matches('ready')) {
        expect(root.activeElement).toBeNull();
    }
}

function describePath(path: Path): string {
    const events = path.steps.slice(1).map((step) => step.event.type.replace('xstate.', ''));
    return `${serialize(path.state)} after ${events.join(' > ') || 'connecting'}`;
}

/** Mounts a fresh element and walks it along the path, checking every step in order. */
async function walk(path: Path): Promise<UpResetMyLayout> {
    const portal = new Portal(server);
    const element = await fixture<UpResetMyLayout>(
        `<up-reset-my-layout ${ATTRIBUTES}></up-reset-my-layout>`,
    );
    // The first step is the machine's initial state, which connecting the element reaches
    const [initial, ...steps] = path.steps;
    const first = initial?.state ?? path.state;
    await vi.waitFor(() => {
        expectRendered(element, first, false);
    });
    // Focus moves on every change of state after the first one (the first load)
    let changes = 0;
    await steps.reduce(async (previous, step) => {
        const before = await previous;
        await replay(element, portal, step.event);
        const changed = JSON.stringify(step.state.value) !== JSON.stringify(before.value);
        const moved = changed && changes > 0;
        changes += changed ? 1 : 0;
        await vi.waitFor(() => {
            expectRendered(element, step.state, moved);
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
});

describe('up-reset-my-layout model: shortest paths', () => {
    const paths = getShortestPaths(resetMyLayoutMachine, OPTIONS);

    it('reaches every state of the machine', () => {
        const values = new Set(paths.map((path) => JSON.stringify(path.state.value)));
        expect([...values].toSorted()).toEqual(
            [
                '"loading"',
                '"failed"',
                '"signedOut"',
                '{"ready":"start"}',
                '{"ready":"confirming"}',
                '{"ready":"resetting"}',
                '{"ready":"done"}',
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

describe('up-reset-my-layout model: simple paths through a reset', () => {
    // From the first load on, without the load failures that the shortest paths cover
    const paths = getSimplePaths(resetMyLayoutMachine, {
        ...OPTIONS,
        events: EVENTS.filter((event) => {
            const type: string = event.type;
            return type === 'xstate.done.actor.loadReset' || !/loadReset$|^reload$/.test(type);
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
