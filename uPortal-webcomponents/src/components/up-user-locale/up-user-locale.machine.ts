/**
 * The states of `<up-user-locale>`, as an XState machine (MOD-D31).
 *
 * A pure module: no DOM. The REST calls are the invoked actors `loadLocales` and `saveLocale`,
 * which tests replace with `userLocaleMachine.provide({ actors })`. What the element shows comes
 * from the state (`snapshot.matches(...)`, the `busy` tag) and from context data.
 *
 * ```
 * loading            GET the locales (busy)
 * failed             the load failed; "reload" tries again
 * ready
 *   idle             the user picks a language ("choose") and presses Update ("submit")
 *   saving           PUT the choice (busy)
 *   saved            the portal took it; the element reloads the page in the new language (busy)
 * ```
 *
 * The change is pessimistic: the chosen language is saved only when the portal confirms, so a
 * failure leaves the saved one as it was and adds a notice.
 */
import { assign, enqueueActions, fromPromise, setup } from 'xstate';
import { ApiError, type ApiClient, type ApiErrorKind } from '../../lib/api-client';
import { fetchUserLocale, saveUserLocale, type UserLocaleData } from './locale-api';

/** A visible notice about the last change. */
export interface Notice {
    kind: 'save-failed';
    reason: ApiErrorKind;
}

export interface UserLocaleContext {
    api: ApiClient;
    data: UserLocaleData;
    /** The code of the language the user picked; the saved one until they pick another. */
    choice: string | undefined;
    notice: Notice | undefined;
}

export interface UserLocaleInput {
    api: ApiClient;
}

export type UserLocaleEvent =
    /** Load the locales again (also how the user retries). */
    | { type: 'reload' }
    | { type: 'choose'; code: string }
    | { type: 'submit' }
    /** The element's context path or CSRF token changed. */
    | { type: 'api.changed'; api: ApiClient };

export type UserLocaleEmitted = { type: 'locale.changed'; code: string; tag: string };

const NO_LOCALES: UserLocaleData = { selected: null, locales: [] };

export function errorKind(error: unknown): ApiErrorKind {
    return error instanceof ApiError ? error.kind : 'malformed';
}

/** The choice to save; the submit guard checked that there is one. */
function choiceOf(context: UserLocaleContext): string {
    /* v8 ignore if -- unreachable: the hasChoice guard let only a choice through */
    if (context.choice === undefined) {
        throw new Error('No choice to save');
    }
    return context.choice;
}

export const userLocaleMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion, typescript/no-unsafe-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: UserLocaleContext;
        events: UserLocaleEvent;
        input: UserLocaleInput;
        emitted: UserLocaleEmitted;
        tags: 'busy';
    },
    actors: {
        loadLocales: fromPromise<UserLocaleData, { api: ApiClient }>(({ input, signal }) =>
            fetchUserLocale(input.api, { signal }),
        ),
        saveLocale: fromPromise<UserLocaleData, { api: ApiClient; code: string }>(
            ({ input, signal }) => saveUserLocale(input.api, input.code, { signal }),
        ),
    },
    guards: {
        hasChoice: ({ context }) => context.choice !== undefined,
        isOffered: ({ context }, { code }: { code: string }) =>
            context.data.locales.some((locale) => locale.code === code),
    },
    actions: {
        loaded: assign((_, { data }: { data: UserLocaleData }) => ({
            data,
            choice: data.selected ?? undefined,
        })),
        saved: enqueueActions(({ context, enqueue }, { data }: { data: UserLocaleData }) => {
            const code = choiceOf(context);
            enqueue.assign({ data, choice: code, notice: undefined });
            const tag = data.locales.find((locale) => locale.code === code)?.tag ?? code;
            enqueue.emit({ type: 'locale.changed', code, tag });
        }),
        saveFailed: assign((_, { error }: { error: unknown }) => ({
            notice: { kind: 'save-failed' as const, reason: errorKind(error) },
        })),
    },
}).createMachine({
    id: 'userLocale',
    context: ({ input }) => ({
        api: input.api,
        data: NO_LOCALES,
        choice: undefined,
        notice: undefined,
    }),
    initial: 'loading',
    on: {
        // A fresh view: the outcome of an earlier change no longer applies
        reload: {
            target: '.loading',
            reenter: true,
            actions: assign({ notice: undefined }),
        },
        'api.changed': { actions: assign({ api: ({ event }) => event.api }) },
    },
    states: {
        loading: {
            tags: ['busy'],
            invoke: {
                id: 'loadLocales',
                src: 'loadLocales',
                input: ({ context }) => ({ api: context.api }),
                onDone: {
                    target: 'ready',
                    actions: { type: 'loaded', params: ({ event }) => ({ data: event.output }) },
                },
                onError: 'failed',
            },
        },
        failed: {},
        ready: {
            initial: 'idle',
            states: {
                idle: {
                    on: {
                        choose: {
                            guard: {
                                type: 'isOffered',
                                params: ({ event }) => ({ code: event.code }),
                            },
                            actions: assign({
                                choice: ({ event }) => event.code,
                                notice: undefined,
                            }),
                        },
                        submit: { guard: 'hasChoice', target: 'saving' },
                    },
                },
                saving: {
                    tags: ['busy'],
                    invoke: {
                        id: 'saveLocale',
                        src: 'saveLocale',
                        input: ({ context }) => ({ api: context.api, code: choiceOf(context) }),
                        onDone: {
                            target: 'saved',
                            actions: {
                                type: 'saved',
                                params: ({ event }) => ({ data: event.output }),
                            },
                        },
                        onError: {
                            target: 'idle',
                            actions: {
                                type: 'saveFailed',
                                params: ({ event }) => ({ error: event.error }),
                            },
                        },
                    },
                },
                saved: { tags: ['busy'] },
            },
        },
    },
});

export type UserLocaleMachine = typeof userLocaleMachine;
