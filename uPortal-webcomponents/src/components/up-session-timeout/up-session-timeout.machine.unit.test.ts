import { beforeEach, describe, expect, it } from 'vite-plus/test';
import { createActor } from 'xstate';
import { ApiClient } from '../../lib/api-client';
import { ManualClock } from '../../test-support/manual-clock';
import { KEEP_ALIVE_PATH, SESSION_PATH } from './session-api';
import type { LogoutTimeStore } from './session-clock';
import {
    sessionTimeoutMachine,
    type SessionTimeoutEmitted,
    type SessionTimeoutInput,
} from './up-session-timeout.machine';

const T0 = Date.UTC(2026, 9, 11, 12, 0, 0);
const SECOND = 1000;
const MINUTE = 60 * SECOND;
/** A 30-minute session warns after 28.5 minutes and logs out after 29.5. */
const WARNING_AT = T0 + 28.5 * MINUTE;
const LOGOUT_AT = T0 + 29.5 * MINUTE;

class MemoryStore implements LogoutTimeStore {
    value: number | undefined;
    writes: number[] = [];

    read(): number | undefined {
        return this.value;
    }

    write(logoutTime: number): void {
        this.value = logoutTime;
        this.writes.push(logoutTime);
    }
}

interface Answer {
    status: number;
    body?: unknown;
}

/** A portal whose session API answers from the queues; each request is recorded. */
class Portal {
    readonly requests: string[] = [];
    get: Answer[] = [{ status: 200, body: { maxInactiveIntervalSeconds: 1800 } }];
    keepAlive: Answer[] = [{ status: 200, body: { maxInactiveIntervalSeconds: 1800 } }];

    readonly fetch: typeof fetch = (input, init) => {
        const url =
            typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
        const method = init?.method ?? 'GET';
        this.requests.push(`${method} ${url}`);
        const queue = url.endsWith(KEEP_ALIVE_PATH) ? this.keepAlive : this.get;
        const answer = queue.length > 1 ? queue.shift() : queue[0];
        if (answer === undefined || answer.status === 0) {
            return Promise.reject(new TypeError('Failed to fetch'));
        }
        return Promise.resolve(
            new Response(answer.body === undefined ? null : JSON.stringify(answer.body), {
                status: answer.status,
            }),
        );
    };
}

let portal: Portal;
let store: MemoryStore;
let clock: ManualClock;

function start(overrides: Partial<SessionTimeoutInput> = {}) {
    const actor = createActor(sessionTimeoutMachine, {
        input: {
            api: new ApiClient({
                contextPath: '/uPortal',
                csrfHeader: 'X-CSRF-TOKEN',
                csrfToken: 't',
                fetch: portal.fetch,
            }),
            clock,
            store,
            enabled: true,
            displaySeconds: undefined,
            ...overrides,
        },
    });
    const emitted: SessionTimeoutEmitted[] = [];
    actor.on('*', (event) => {
        emitted.push(event);
    });
    actor.start();
    return { actor, emitted };
}

/** Lets the stubbed requests settle. */
async function settle(): Promise<void> {
    await new Promise((resolve) => {
        setTimeout(resolve, 0);
    });
}

async function advanceTo(time: number): Promise<void> {
    clock.advanceTo(time);
    await settle();
}

beforeEach(() => {
    clock = new ManualClock(T0);
    portal = new Portal();
    store = new MemoryStore();
});

describe('sessionTimeoutMachine: starting', () => {
    it('does nothing when the channel is disabled', async () => {
        const { actor } = start({ enabled: false });
        await settle();

        expect(actor.getSnapshot().value).toBe('disabled');
        expect(portal.requests).toEqual([]);
    });

    it('loads the session timeout and waits, sharing the logout time with other tabs', async () => {
        const { actor } = start();
        expect(actor.getSnapshot().value).toBe('loading');
        await settle();

        expect(portal.requests).toEqual([`GET /uPortal${SESSION_PATH}`]);
        expect(actor.getSnapshot().value).toBe('waiting');
        expect(actor.getSnapshot().context.period).toEqual({
            sessionTimeoutMs: 30 * MINUTE,
            displayMs: MINUTE,
            logoutTime: LOGOUT_AT,
        });
        expect(store.writes).toEqual([LOGOUT_AT]);
    });

    it.each([
        ['a guest (401)', { status: 401, body: { message: 'Sign in' } }],
        ['a server error', { status: 500 }],
        ['a network failure', { status: 0 }],
        ['a malformed body', { status: 200, body: { maxInactiveIntervalSeconds: 'soon' } }],
        ['a session that never expires', { status: 200, body: { maxInactiveIntervalSeconds: -1 } }],
    ])('stays inactive for %s', async (_name, answer) => {
        portal.get = [answer];
        const { actor } = start();
        await settle();
        await advanceTo(T0 + 60 * MINUTE);

        expect(actor.getSnapshot().value).toBe('inactive');
        expect(store.writes).toEqual([]);
    });

    it('uses dialog-display-seconds, capped at the session timeout', async () => {
        portal.get = [{ status: 200, body: { maxInactiveIntervalSeconds: 100 } }];
        const { actor } = start({ displaySeconds: '300' });
        await settle();

        // No time to wait: the warning is due at once and lasts the whole session
        expect(actor.getSnapshot().context.period).toEqual({
            sessionTimeoutMs: 100 * SECOND,
            displayMs: 100 * SECOND,
            logoutTime: T0 + 100 * SECOND,
        });
        await advanceTo(T0);
        expect(actor.getSnapshot().value).toEqual({ warning: 'idle' });
        expect(actor.getSnapshot().context.secondsLeft).toBe(100);
    });
});

describe('sessionTimeoutMachine: the warning', () => {
    it('opens 90 seconds before the session expires', async () => {
        const { actor, emitted } = start();
        await settle();

        await advanceTo(WARNING_AT - 1);
        expect(actor.getSnapshot().value).toBe('waiting');
        await advanceTo(WARNING_AT);

        expect(actor.getSnapshot().value).toEqual({ warning: 'idle' });
        expect(actor.getSnapshot().hasTag('open')).toBe(true);
        expect(actor.getSnapshot().context.secondsLeft).toBe(60);
        expect(emitted).toEqual([{ type: 'session.warned', secondsLeft: 60 }]);
    });

    it('counts down every half second', async () => {
        const { actor } = start();
        await settle();
        await advanceTo(WARNING_AT + 10 * SECOND);

        expect(actor.getSnapshot().context.secondsLeft).toBe(50);
    });

    it('logs out when the countdown reaches zero', async () => {
        const { actor, emitted } = start();
        await settle();
        await advanceTo(LOGOUT_AT);

        expect(actor.getSnapshot().value).toBe('ended');
        expect(actor.getSnapshot().context.ended).toBe('expired');
        expect(emitted.at(-1)).toEqual({ type: 'session.ended', reason: 'expired' });
    });

    it('logs out when the user asks', async () => {
        const { actor, emitted } = start();
        await settle();
        await advanceTo(WARNING_AT);
        actor.send({ type: 'logout' });

        expect(actor.getSnapshot().value).toBe('ended');
        expect(emitted.at(-1)).toEqual({ type: 'session.ended', reason: 'logout' });
        // The countdown stopped with the warning
        await advanceTo(LOGOUT_AT + MINUTE);
        expect(emitted.filter((event) => event.type === 'session.ended')).toHaveLength(1);
    });

    it('ignores logout and keep-alive while it is not open', async () => {
        const { actor } = start();
        await settle();
        actor.send({ type: 'logout' });
        actor.send({ type: 'keepAlive' });

        expect(actor.getSnapshot().value).toBe('waiting');
        expect(portal.requests).toHaveLength(1);
    });
});

describe('sessionTimeoutMachine: keeping the session', () => {
    it('extends the session and starts a new period', async () => {
        const { actor, emitted } = start();
        await settle();
        await advanceTo(WARNING_AT + 5 * SECOND);
        actor.send({ type: 'keepAlive' });

        expect(actor.getSnapshot().value).toEqual({ warning: 'extending' });
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);
        await settle();

        const next = WARNING_AT + 5 * SECOND + 29.5 * MINUTE;
        expect(portal.requests.at(-1)).toBe(`POST /uPortal${KEEP_ALIVE_PATH}`);
        expect(actor.getSnapshot().value).toBe('waiting');
        expect(actor.getSnapshot().context.period?.logoutTime).toBe(next);
        expect(store.writes.at(-1)).toBe(next);
        expect(emitted.at(-1)).toEqual({ type: 'session.extended', logoutTime: next });

        // The old logout time passes quietly; the next warning comes a period later
        await advanceTo(LOGOUT_AT + SECOND);
        expect(actor.getSnapshot().value).toBe('waiting');
        await advanceTo(next - MINUTE);
        expect(actor.getSnapshot().value).toEqual({ warning: 'idle' });
    });

    it.each([
        ['a server error', { status: 500 }],
        ['an expired session (403 from the CSRF filter)', { status: 403 }],
        ['a network failure', { status: 0 }],
        ['no body', { status: 204 }],
    ])('keeps the warning open after %s, and can try again', async (_name, answer) => {
        portal.keepAlive = [answer, { status: 200, body: { maxInactiveIntervalSeconds: 1800 } }];
        const { actor } = start();
        await settle();
        await advanceTo(WARNING_AT);
        actor.send({ type: 'keepAlive' });
        await settle();

        expect(actor.getSnapshot().value).toEqual({ warning: 'failed' });
        // The countdown goes on meanwhile
        await advanceTo(WARNING_AT + 2 * SECOND);
        expect(actor.getSnapshot().context.secondsLeft).toBe(58);

        actor.send({ type: 'keepAlive' });
        await settle();
        expect(actor.getSnapshot().value).toBe('waiting');
    });

    it('stops warning when the extended session never expires', async () => {
        portal.keepAlive = [{ status: 200, body: { maxInactiveIntervalSeconds: 0 } }];
        const { actor } = start();
        await settle();
        await advanceTo(WARNING_AT);
        actor.send({ type: 'keepAlive' });
        await settle();

        expect(actor.getSnapshot().value).toBe('inactive');
    });

    it('logs out if the countdown ends while the keep-alive is in flight', async () => {
        portal.keepAlive = [{ status: 200, body: { maxInactiveIntervalSeconds: 1800 } }];
        const { actor } = start();
        await settle();
        await advanceTo(LOGOUT_AT - SECOND);
        actor.send({ type: 'keepAlive' });
        actor.send({ type: 'expired' });

        expect(actor.getSnapshot().value).toBe('ended');
    });
});

describe('sessionTimeoutMachine: other tabs', () => {
    it('postpones the warning to a later logout time another tab saved', async () => {
        const { actor } = start();
        await settle();
        const later = LOGOUT_AT + 10 * MINUTE;
        store.value = later;

        await advanceTo(WARNING_AT);
        expect(actor.getSnapshot().value).toBe('waiting');
        expect(actor.getSnapshot().context.period?.logoutTime).toBe(later);

        await advanceTo(later - MINUTE);
        expect(actor.getSnapshot().value).toEqual({ warning: 'idle' });
    });

    it('closes an open warning when another tab extends the session', async () => {
        const { actor } = start();
        await settle();
        await advanceTo(WARNING_AT);
        const later = WARNING_AT + 29.5 * MINUTE;
        store.value = later;

        await advanceTo(WARNING_AT + SECOND);
        expect(actor.getSnapshot().value).toBe('waiting');
        expect(actor.getSnapshot().context.period?.logoutTime).toBe(later);
    });

    it('ignores an earlier or equal saved time', async () => {
        const { actor } = start();
        await settle();
        store.value = LOGOUT_AT - MINUTE;
        await advanceTo(WARNING_AT);

        expect(actor.getSnapshot().value).toEqual({ warning: 'idle' });
        actor.send({ type: 'postponed', logoutTime: LOGOUT_AT });
        expect(actor.getSnapshot().value).toEqual({ warning: 'idle' });
    });
});

describe('sessionTimeoutMachine: properties', () => {
    it('uses a changed API client for the next request', async () => {
        const other = new Portal();
        const { actor } = start();
        await settle();
        actor.send({
            type: 'api.changed',
            api: new ApiClient({ contextPath: '/portal', fetch: other.fetch }),
        });
        await advanceTo(WARNING_AT);
        actor.send({ type: 'keepAlive' });
        await settle();

        expect(other.requests).toEqual([`POST /portal${KEEP_ALIVE_PATH}`]);
    });
});
