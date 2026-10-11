/**
 * The states of `<up-session-timeout>`, as an XState machine (MOD-D31).
 *
 * A pure module: no DOM. The REST calls (`loadSession`, `keepAlive`) and the two timers
 * (`waitForWarning`, `countdown`) are invoked actors, which tests replace with
 * `sessionTimeoutMachine.provide({ actors })`. The timers report what happened as events that
 * carry the times, so the machine itself never reads the clock.
 *
 * ```
 * disabled              the channel's `enabled` preference is not true
 * loading               GET the session's timeout
 * inactive              no warning: a guest, a session that never expires, or no answer
 * waiting               until the warning is due; another tab can postpone it
 * warning               the dialog with its countdown (tag open)
 *   idle | extending | failed     "keep me signed in" and its outcome (extending is busy)
 * ended                 logged out by the user or by the countdown; the element leaves the page
 * ```
 */
import { assign, emit, fromCallback, fromPromise, setup } from 'xstate';
import type { ApiClient } from '../../lib/api-client';
import { extendSession, fetchSession } from './session-api';
import {
    dialogDisplayMs,
    laterLogoutTime,
    logoutTimeFrom,
    secondsLeft,
    TICK_MS,
    type Clock,
    type LogoutTimeStore,
} from './session-clock';

/** One period of the session: from a page load or a keep-alive until the logout time. */
export interface SessionPeriod {
    sessionTimeoutMs: number;
    displayMs: number;
    /** When the user is logged out unless the session is extended (epoch milliseconds). */
    logoutTime: number;
}

export type EndReason = 'logout' | 'expired';

export interface SessionTimeoutContext {
    api: ApiClient;
    clock: Clock;
    store: LogoutTimeStore;
    enabled: boolean;
    /** The `dialog-display-seconds` attribute, checked once the session timeout is known. */
    displaySeconds: string | undefined;
    period: SessionPeriod | undefined;
    secondsLeft: number;
    ended: EndReason | undefined;
}

export interface SessionTimeoutInput {
    api: ApiClient;
    clock: Clock;
    store: LogoutTimeStore;
    enabled: boolean;
    displaySeconds: string | undefined;
}

export type SessionTimeoutEvent =
    | { type: 'warning.due'; secondsLeft: number }
    | { type: 'tick'; secondsLeft: number }
    /** Another tab saved a later logout time. */
    | { type: 'postponed'; logoutTime: number }
    | { type: 'expired' }
    | { type: 'keepAlive' }
    | { type: 'logout' }
    /** The element's context path or CSRF token changed. */
    | { type: 'api.changed'; api: ApiClient };

export type SessionTimeoutEmitted =
    | { type: 'session.warned'; secondsLeft: number }
    | { type: 'session.extended'; logoutTime: number }
    | { type: 'session.ended'; reason: EndReason };

interface PeriodInput {
    api: ApiClient;
    clock: Clock;
    store: LogoutTimeStore;
    displaySeconds: string | undefined;
}

interface TimerInput {
    clock: Clock;
    store: LogoutTimeStore;
    period: SessionPeriod;
}

/** Starts a period now and shares its logout time with the other tabs; none if never expiring. */
function startPeriod(
    { clock, store, displaySeconds }: PeriodInput,
    maxInactiveIntervalSeconds: number,
): SessionPeriod | null {
    if (maxInactiveIntervalSeconds <= 0) {
        return null;
    }
    const sessionTimeoutMs = maxInactiveIntervalSeconds * 1000;
    const displayMs = dialogDisplayMs(displaySeconds, sessionTimeoutMs);
    const logoutTime = logoutTimeFrom(clock.now(), sessionTimeoutMs, displayMs);
    store.write(logoutTime);
    return { sessionTimeoutMs, displayMs, logoutTime };
}

function timerInput(context: SessionTimeoutContext): TimerInput {
    return { clock: context.clock, store: context.store, period: periodOf(context) };
}

/** The period the machine is in; every state after loading has one. */
function periodOf(context: SessionTimeoutContext): SessionPeriod {
    /* v8 ignore if -- unreachable: loading assigns it before any state that reads it */
    if (context.period === undefined) {
        throw new Error('No session period');
    }
    return context.period;
}

export const sessionTimeoutMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion, typescript/no-unsafe-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: SessionTimeoutContext;
        events: SessionTimeoutEvent;
        input: SessionTimeoutInput;
        emitted: SessionTimeoutEmitted;
        tags: 'open' | 'busy';
    },
    actors: {
        loadSession: fromPromise<SessionPeriod | null, PeriodInput>(async ({ input, signal }) => {
            const session = await fetchSession(input.api, { signal });
            return startPeriod(input, session.maxInactiveIntervalSeconds);
        }),
        keepAlive: fromPromise<SessionPeriod | null, PeriodInput>(async ({ input, signal }) => {
            const session = await extendSession(input.api, { signal });
            return startPeriod(input, session.maxInactiveIntervalSeconds);
        }),
        waitForWarning: fromCallback<SessionTimeoutEvent, TimerInput>(({ input, sendBack }) => {
            const { clock, store, period } = input;
            const { logoutTime, displayMs } = period;
            return clock.after(Math.max(logoutTime - displayMs - clock.now(), 0), () => {
                const later = laterLogoutTime(store, logoutTime);
                sendBack(
                    later === undefined
                        ? { type: 'warning.due', secondsLeft: secondsLeft(logoutTime, clock.now()) }
                        : { type: 'postponed', logoutTime: later },
                );
            });
        }),
        countdown: fromCallback<SessionTimeoutEvent, TimerInput>(({ input, sendBack }) => {
            const { clock, store, period } = input;
            const { logoutTime } = period;
            return clock.every(TICK_MS, () => {
                const later = laterLogoutTime(store, logoutTime);
                const left = secondsLeft(logoutTime, clock.now());
                sendBack(
                    later !== undefined
                        ? { type: 'postponed', logoutTime: later }
                        : left <= 0
                          ? { type: 'expired' }
                          : { type: 'tick', secondsLeft: left },
                );
            });
        }),
    },
    guards: {
        isDisabled: ({ context }) => !context.enabled,
        neverExpires: (_, { period }: { period: SessionPeriod | null }) => period === null,
        isLater: ({ context }, { logoutTime }: { logoutTime: number }) =>
            logoutTime > periodOf(context).logoutTime,
    },
    actions: {
        startedPeriod: assign((_, { period }: { period: SessionPeriod | null }) => {
            /* v8 ignore if -- unreachable: the neverExpires guard takes a null period first */
            if (period === null) {
                return {};
            }
            return { period };
        }),
        postpone: assign(({ context }, { logoutTime }: { logoutTime: number }) => ({
            period: { ...periodOf(context), logoutTime },
        })),
    },
}).createMachine({
    id: 'sessionTimeout',
    context: ({ input }) => ({
        api: input.api,
        clock: input.clock,
        store: input.store,
        enabled: input.enabled,
        displaySeconds: input.displaySeconds,
        period: undefined,
        secondsLeft: 0,
        ended: undefined,
    }),
    initial: 'starting',
    on: {
        'api.changed': { actions: assign({ api: ({ event }) => event.api }) },
    },
    states: {
        starting: {
            always: [{ guard: 'isDisabled', target: 'disabled' }, { target: 'loading' }],
        },
        disabled: {},
        loading: {
            invoke: {
                id: 'loadSession',
                src: 'loadSession',
                input: ({ context }) => ({
                    api: context.api,
                    clock: context.clock,
                    store: context.store,
                    displaySeconds: context.displaySeconds,
                }),
                onDone: [
                    {
                        guard: {
                            type: 'neverExpires',
                            params: ({ event }) => ({ period: event.output }),
                        },
                        target: 'inactive',
                    },
                    {
                        target: 'waiting',
                        actions: {
                            type: 'startedPeriod',
                            params: ({ event }) => ({ period: event.output }),
                        },
                    },
                ],
                // A guest (401) or a portal that does not answer: no warning
                onError: { target: 'inactive' },
            },
        },
        inactive: {},
        waiting: {
            invoke: {
                src: 'waitForWarning',
                input: ({ context }) => timerInput(context),
            },
            on: {
                'warning.due': {
                    target: 'warning',
                    actions: [
                        assign({ secondsLeft: ({ event }) => event.secondsLeft }),
                        emit(({ event }) => ({
                            type: 'session.warned' as const,
                            secondsLeft: event.secondsLeft,
                        })),
                    ],
                },
                postponed: {
                    guard: {
                        type: 'isLater',
                        params: ({ event }) => ({ logoutTime: event.logoutTime }),
                    },
                    target: 'waiting',
                    reenter: true,
                    actions: {
                        type: 'postpone',
                        params: ({ event }) => ({ logoutTime: event.logoutTime }),
                    },
                },
            },
        },
        warning: {
            tags: ['open'],
            invoke: {
                src: 'countdown',
                input: ({ context }) => timerInput(context),
            },
            on: {
                tick: { actions: assign({ secondsLeft: ({ event }) => event.secondsLeft }) },
                postponed: {
                    guard: {
                        type: 'isLater',
                        params: ({ event }) => ({ logoutTime: event.logoutTime }),
                    },
                    target: 'waiting',
                    actions: {
                        type: 'postpone',
                        params: ({ event }) => ({ logoutTime: event.logoutTime }),
                    },
                },
                expired: {
                    target: 'ended',
                    actions: [
                        assign({ ended: 'expired' as const }),
                        emit({ type: 'session.ended', reason: 'expired' } as const),
                    ],
                },
                logout: {
                    target: 'ended',
                    actions: [
                        assign({ ended: 'logout' as const }),
                        emit({ type: 'session.ended', reason: 'logout' } as const),
                    ],
                },
            },
            initial: 'idle',
            states: {
                idle: { on: { keepAlive: 'extending' } },
                extending: {
                    tags: ['busy'],
                    invoke: {
                        id: 'keepAlive',
                        src: 'keepAlive',
                        input: ({ context }) => ({
                            api: context.api,
                            clock: context.clock,
                            store: context.store,
                            displaySeconds: context.displaySeconds,
                        }),
                        onDone: [
                            {
                                guard: {
                                    type: 'neverExpires',
                                    params: ({ event }) => ({ period: event.output }),
                                },
                                target: '#sessionTimeout.inactive',
                            },
                            {
                                target: '#sessionTimeout.waiting',
                                actions: [
                                    {
                                        type: 'startedPeriod',
                                        params: ({ event }) => ({ period: event.output }),
                                    },
                                    emit(({ context }) => ({
                                        type: 'session.extended' as const,
                                        logoutTime: periodOf(context).logoutTime,
                                    })),
                                ],
                            },
                        ],
                        onError: { target: 'failed' },
                    },
                },
                failed: { on: { keepAlive: 'extending' } },
            },
        },
        ended: {},
    },
});

export type SessionTimeoutMachine = typeof sessionTimeoutMachine;
