/**
 * The states of `<up-reset-my-layout>`, as an XState machine (MOD-D31).
 *
 * A pure module: no DOM. The REST calls are the invoked actors `loadReset` and `resetLayout`,
 * which tests replace with `resetMyLayoutMachine.provide({ actors })`. What the element shows
 * comes from the state (`snapshot.matches(...)`, the `busy` tag) and from context data.
 *
 * ```
 * loading            GET whose layout a reset acts on (busy)
 * failed             the load failed; "reload" tries again
 * signedOut          the portal answered 401 or 403: the session ended
 * ready
 *   start            what a reset does; "reset" asks for confirmation
 *   confirming       "confirm" resets, "cancel" goes back to start
 *   resetting        POST the reset (busy)
 *   done             the layout was reset; "continue" goes back to start
 * ```
 *
 * The reset is pessimistic: `done` only once the portal confirms. A failed reset goes back to
 * `confirming` with a notice, so the user can try again or cancel. The API never refuses a
 * signed-in user, so a 403 is Spring Security refusing the CSRF token, which happens when the
 * session ended (a new session has a new token): like a 401, it means signing in again.
 */
import { assign, enqueueActions, fromPromise, setup } from 'xstate';
import { ApiError, type ApiClient, type ApiErrorKind } from '../../lib/api-client';
import { fetchLayoutReset, resetLayout, type LayoutReset } from './layout-reset-api';

/** A visible notice about the last reset. */
export interface Notice {
    kind: 'reset-failed';
    reason: ApiErrorKind;
}

export interface ResetMyLayoutContext {
    api: ApiClient;
    /** The signed-in user, whose layout a reset acts on; known once loaded. */
    userName: string;
    notice: Notice | undefined;
}

export interface ResetMyLayoutInput {
    api: ApiClient;
}

export type ResetMyLayoutEvent =
    /** Load again (how the user retries after a failed load). */
    | { type: 'reload' }
    /** Reset Now: ask for confirmation. */
    | { type: 'reset' }
    | { type: 'confirm' }
    | { type: 'cancel' }
    | { type: 'continue' }
    /** The element's context path or CSRF token changed. */
    | { type: 'api.changed'; api: ApiClient };

export type ResetMyLayoutEmitted = { type: 'layout.reset'; userName: string };

export function errorKind(error: unknown): ApiErrorKind {
    return error instanceof ApiError ? error.kind : 'malformed';
}

export const resetMyLayoutMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion, typescript/no-unsafe-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: ResetMyLayoutContext;
        events: ResetMyLayoutEvent;
        input: ResetMyLayoutInput;
        emitted: ResetMyLayoutEmitted;
        tags: 'busy';
    },
    actors: {
        loadReset: fromPromise<LayoutReset, { api: ApiClient }>(({ input, signal }) =>
            fetchLayoutReset(input.api, { signal }),
        ),
        resetLayout: fromPromise<LayoutReset, { api: ApiClient }>(({ input, signal }) =>
            resetLayout(input.api, { signal }),
        ),
    },
    guards: {
        isSessionLost: (_, { error }: { error: unknown }) =>
            errorKind(error) === 'unauthorized' || errorKind(error) === 'forbidden',
    },
    actions: {
        loaded: assign((_, { reset }: { reset: LayoutReset }) => ({ userName: reset.userName })),
        resetDone: enqueueActions(({ enqueue }, { reset }: { reset: LayoutReset }) => {
            enqueue.assign({ userName: reset.userName });
            enqueue.emit({ type: 'layout.reset', userName: reset.userName });
        }),
        clearNotice: assign({ notice: undefined }),
        resetFailed: assign((_, { error }: { error: unknown }) => ({
            notice: { kind: 'reset-failed' as const, reason: errorKind(error) },
        })),
    },
}).createMachine({
    id: 'resetMyLayout',
    context: ({ input }) => ({ api: input.api, userName: '', notice: undefined }),
    initial: 'loading',
    on: {
        'api.changed': { actions: assign({ api: ({ event }) => event.api }) },
    },
    states: {
        loading: {
            tags: ['busy'],
            invoke: {
                id: 'loadReset',
                src: 'loadReset',
                input: ({ context }) => ({ api: context.api }),
                onDone: {
                    target: 'ready',
                    actions: { type: 'loaded', params: ({ event }) => ({ reset: event.output }) },
                },
                onError: [
                    {
                        guard: {
                            type: 'isSessionLost',
                            params: ({ event }) => ({ error: event.error }),
                        },
                        target: 'signedOut',
                    },
                    { target: 'failed' },
                ],
            },
        },
        failed: {
            on: { reload: 'loading' },
        },
        signedOut: {},
        ready: {
            initial: 'start',
            states: {
                start: {
                    on: { reset: 'confirming' },
                },
                confirming: {
                    on: {
                        confirm: { target: 'resetting', actions: 'clearNotice' },
                        cancel: { target: 'start', actions: 'clearNotice' },
                    },
                },
                resetting: {
                    tags: ['busy'],
                    invoke: {
                        id: 'resetLayout',
                        src: 'resetLayout',
                        input: ({ context }) => ({ api: context.api }),
                        onDone: {
                            target: 'done',
                            actions: {
                                type: 'resetDone',
                                params: ({ event }) => ({ reset: event.output }),
                            },
                        },
                        onError: [
                            {
                                guard: {
                                    type: 'isSessionLost',
                                    params: ({ event }) => ({ error: event.error }),
                                },
                                target: '#resetMyLayout.signedOut',
                            },
                            {
                                target: 'confirming',
                                actions: {
                                    type: 'resetFailed',
                                    params: ({ event }) => ({ error: event.error }),
                                },
                            },
                        ],
                    },
                },
                done: {
                    on: { continue: 'start' },
                },
            },
        },
    },
});

export type ResetMyLayoutMachine = typeof resetMyLayoutMachine;
