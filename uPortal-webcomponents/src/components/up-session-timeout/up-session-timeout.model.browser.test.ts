/**
 * Model-based tests: @xstate/graph walks the session timeout machine and the real
 * `<up-session-timeout>` replays each path in Chromium. REST outcomes are answered as the path
 * says; the timer events are produced by moving the element's clock to the time they stand for.
 * At the end of every step the rendered output and its accessibility attributes must match the
 * machine's state, and axe must find nothing in the state the path reaches.
 */
import { getShortestPaths, getSimplePaths, type StatePath } from '@xstate/graph';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { userEvent } from 'vite-plus/test/browser';
import type { SnapshotFrom } from 'xstate';
import { ApiClient } from '../../lib/api-client';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, text } from '../../test-support/fetch-stub';
import { ManualClock } from '../../test-support/manual-clock';
import { logoutTimeFrom, type LogoutTimeStore } from './session-clock';
import './up-session-timeout';
import type { UpSessionTimeout } from './up-session-timeout';
import {
    sessionTimeoutMachine,
    type SessionPeriod,
    type SessionTimeoutEvent,
    type SessionTimeoutMachine,
} from './up-session-timeout.machine';

type Snapshot = SnapshotFrom<SessionTimeoutMachine>;
type Path = StatePath<Snapshot, SessionTimeoutEvent>;

const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="session-timeout"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const SESSION_MS = 30 * MINUTE;
const DISPLAY_MS = MINUTE;
const T0 = Date.UTC(2026, 9, 11, 12, 0, 0);
/** When the keep-alive answers: later than any time a path reaches before it. */
const EXTENDED_AT = T0 + 45 * MINUTE;

function period(start: number): SessionPeriod {
    return {
        sessionTimeoutMs: SESSION_MS,
        displayMs: DISPLAY_MS,
        logoutTime: logoutTimeFrom(start, SESSION_MS, DISPLAY_MS),
    };
}

const FIRST = period(T0);
const EXTENDED = period(EXTENDED_AT);
/** A later logout time that another tab saves. */
const OTHER_TAB = FIRST.logoutTime + 10 * MINUTE;

/**
 * The events the traversal tries: user events, the timers' events, and the outcomes of the
 * invoked REST calls (`xstate.done.actor.*` / `xstate.error.actor.*`).
 */
const EVENTS = [
    { type: 'xstate.done.actor.loadSession', output: FIRST },
    { type: 'xstate.done.actor.loadSession', output: null },
    // Only a 401 fails the load; other failures fall back to the default timeout
    { type: 'xstate.error.actor.loadSession', error: new Error('401') },
    { type: 'warning.due', secondsLeft: 60 },
    { type: 'tick', secondsLeft: 30 },
    { type: 'postponed', logoutTime: OTHER_TAB },
    { type: 'expired' },
    { type: 'keepAlive' },
    { type: 'logout' },
    { type: 'xstate.done.actor.keepAlive', output: EXTENDED },
    { type: 'xstate.error.actor.keepAlive', error: new Error('500') },
] as unknown as SessionTimeoutEvent[];

/** States differ by their value and by what the element shows: the countdown and its end. */
function serialize(snapshot: Snapshot): string {
    return JSON.stringify([
        snapshot.value,
        snapshot.context.secondsLeft,
        snapshot.context.period?.logoutTime ?? null,
        snapshot.context.ended ?? null,
    ]);
}

const OPTIONS = {
    input: {
        api: new ApiClient({ contextPath: '/uPortal' }),
        clock: new ManualClock(T0),
        store: { read: () => undefined, write: () => undefined },
        enabled: true,
        displaySeconds: undefined,
    },
    events: EVENTS,
    serializeState: serialize,
};

class MemoryStore implements LogoutTimeStore {
    value: number | undefined;

    read(): number | undefined {
        return this.value;
    }

    write(logoutTime: number): void {
        this.value = logoutTime;
    }
}

/** Holds the session API's requests until a step answers them. */
class Portal {
    readonly #waiting = new Map<string, ((response: Response) => void)[]>();

    constructor(server: FetchStub) {
        server.on('GET', '/uPortal/api/v6-0/session', () => this.#hold('loadSession'));
        server.on('POST', '/uPortal/api/v6-0/session/keep-alive', () => this.#hold('keepAlive'));
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

interface Harness {
    element: UpSessionTimeout;
    portal: Portal;
    clock: ManualClock;
    store: MemoryStore;
    navigations: string[];
}

function shadow(element: UpSessionTimeout): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function button(element: UpSessionTimeout, selector: string): HTMLElement {
    const found = shadow(element).querySelector<HTMLElement>(selector);
    if (!found) {
        throw new Error(`nothing matches ${selector}`);
    }
    return found;
}

/** The logout time of the state a step starts from. */
function logoutTimeIn(snapshot: Snapshot): number {
    const logoutTime = snapshot.context.period?.logoutTime;
    if (logoutTime === undefined) {
        throw new Error('no session period in this state');
    }
    return logoutTime;
}

/** Makes the element do what the step's event stands for, from the state before the step. */
async function replay(harness: Harness, before: Snapshot, event: SessionTimeoutEvent) {
    const { element, portal, clock, store } = harness;
    const outcome = event as unknown as { type: string; output?: SessionPeriod | null };
    switch (outcome.type) {
        case 'xstate.done.actor.loadSession':
            await portal.answer(
                'loadSession',
                json({
                    maxInactiveIntervalSeconds: outcome.output === null ? -1 : SESSION_MS / SECOND,
                }),
            );
            return;
        case 'xstate.error.actor.loadSession':
            await portal.answer('loadSession', text('', 401));
            return;
        case 'xstate.done.actor.keepAlive':
            // The answer arrives at a known time, so the element computes the expected period
            clock.jumpTo(EXTENDED_AT);
            await portal.answer(
                'keepAlive',
                json({ maxInactiveIntervalSeconds: SESSION_MS / SECOND }),
            );
            return;
        case 'xstate.error.actor.keepAlive':
            await portal.answer('keepAlive', text('', 500));
            return;
        default:
            break;
    }
    switch (event.type) {
        case 'warning.due':
            clock.advanceTo(logoutTimeIn(before) - DISPLAY_MS);
            return;
        case 'tick':
            clock.advanceTo(logoutTimeIn(before) - event.secondsLeft * SECOND);
            return;
        case 'expired':
            clock.advanceTo(logoutTimeIn(before));
            return;
        case 'postponed':
            store.value = event.logoutTime;
            // The waiting timer reads it when the warning falls due; the countdown on its next tick
            clock.advanceTo(
                before.matches('waiting') ? logoutTimeIn(before) - DISPLAY_MS : clock.now() + 500,
            );
            return;
        case 'keepAlive':
            await userEvent.click(button(element, '.keep-alive'));
            return;
        case 'logout':
            await userEvent.click(button(element, '.logout'));
            return;
        default:
            throw new Error(`no way to replay ${event.type}`);
    }
}

/**
 * What the element must show, how it must expose it, and which timers it runs, in a state of
 * the machine.
 */
function expectRendered(harness: Harness, snapshot: Snapshot): void {
    const root = shadow(harness.element);
    const dialog = root.querySelector('dialog');

    expect(harness.navigations).toEqual(snapshot.matches('ended') ? ['/uPortal/Logout'] : []);
    // One timer while waiting (for the warning) or warning (the countdown), none otherwise
    expect(harness.clock.pending, 'timers').toBe(
        snapshot.matches('waiting') || snapshot.matches('warning') ? 1 : 0,
    );
    if (!snapshot.hasTag('open')) {
        expect(dialog).toBeNull();
        return;
    }
    expect(dialog?.matches(':modal')).toBe(true);
    expect(dialog?.getAttribute('aria-labelledby')).toBe('title');
    expect(root.getElementById('title')?.textContent).toBe('Session expiration');
    expect(root.getElementById('remaining')?.textContent).toMatch(
        new RegExp(`expire\\s+in ${snapshot.context.secondsLeft} seconds\\.`),
    );
    expect(dialog?.getAttribute('aria-busy')).toBe(snapshot.hasTag('busy') ? 'true' : null);
    expect(root.querySelector('[role="alert"]')?.textContent?.trim()).toBe(
        snapshot.matches({ warning: 'failed' }) ? 'Error resetting your session!' : undefined,
    );
}

function describePath(path: Path): string {
    const events = path.steps.slice(1).map((step) => step.event.type.replace('xstate.', ''));
    return `${serialize(path.state)} after ${events.join(' > ') || 'connecting'}`;
}

let server: FetchStub;
const containers: HTMLElement[] = [];

/** Mounts a fresh element and walks it along the path, checking every step in order. */
async function walk(path: Path): Promise<Harness> {
    const container = document.createElement('div');
    container.innerHTML = `<up-session-timeout ${ATTRIBUTES}></up-session-timeout>`;
    const element = container.firstElementChild as UpSessionTimeout;
    const harness: Harness = {
        element,
        portal: new Portal(server),
        clock: new ManualClock(T0),
        store: new MemoryStore(),
        navigations: [],
    };
    element.clock = harness.clock;
    element.store = harness.store;
    element.navigate = (url) => {
        harness.navigations.push(url);
    };
    containers.push(container);
    document.body.append(container);
    await element.updateComplete;

    // The first step is the machine's state once connected (loading)
    const [initial, ...steps] = path.steps;
    await vi.waitFor(() => {
        expectRendered(harness, initial?.state ?? path.state);
    });
    // Each step replays from the state the previous one reached
    await steps.reduce(async (previous, step, index) => {
        await previous;
        await replay(harness, (steps[index - 1] ?? initial)?.state ?? path.state, step.event);
        await vi.waitFor(async () => {
            await element.updateComplete;
            expectRendered(harness, step.state);
        });
    }, Promise.resolve());
    return harness;
}

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub().on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }));
});

afterEach(() => {
    for (const container of containers.splice(0)) {
        container.remove();
    }
});

describe('up-session-timeout model: shortest paths', () => {
    const paths = getShortestPaths(sessionTimeoutMachine, OPTIONS);

    it('reaches every state of the machine but the disabled one', () => {
        const values = new Set(paths.map((path) => JSON.stringify(path.state.value)));
        expect([...values].toSorted()).toEqual(
            [
                '"loading"',
                '"inactive"',
                '"waiting"',
                '{"warning":"idle"}',
                '{"warning":"extending"}',
                '{"warning":"failed"}',
                '"ended"',
            ].toSorted(),
        );
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            const { element } = await walk(path);

            expect(await axeViolations(element)).toEqual([]);
        },
    );
});

describe('up-session-timeout model: simple paths through the warning', () => {
    const paths = getSimplePaths(sessionTimeoutMachine, {
        ...OPTIONS,
        events: EVENTS.filter((event) => !['postponed', 'tick'].includes(event.type)),
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
