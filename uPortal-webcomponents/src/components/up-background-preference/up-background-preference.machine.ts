/**
 * The states of `<up-background-preference>`, as an XState machine (MOD-D31).
 *
 * A pure module: no DOM. The REST calls are the invoked actors `loadBackground` and
 * `saveBackground`, which tests replace with `backgroundPreferenceMachine.provide({ actors })`.
 * What the element shows comes from the state (`snapshot.matches(...)`, the `busy` tag) and from
 * context data; the element also puts `data.selected` behind the page.
 *
 * ```
 * loading            GET the backgrounds (busy)
 * failed             the load failed; "reload" tries again
 * signedOut          the portal answered 401 or 403: the session ended
 * ready
 *   closed           the "Change Background Image" button; "toggle" opens the list
 *   open             None and the images; "choose" saves one, "toggle" or "close" closes
 *   saving           PUT the choice (busy)
 * ```
 *
 * The change is pessimistic: the page shows a new background only when the portal saved it. A
 * failed save keeps the list open with a notice. Only None and the offered images can be chosen.
 */
import { assign, enqueueActions, fromPromise, setup } from 'xstate';
import { ApiError, type ApiClient, type ApiErrorKind } from '../../lib/api-client';
import { fetchBackground, saveBackground, type BackgroundPreference } from './background-api';

/** A visible notice about the last change. */
export interface Notice {
    kind: 'save-failed';
    reason: ApiErrorKind;
}

export interface BackgroundPreferenceContext {
    api: ApiClient;
    fname: string;
    data: BackgroundPreference;
    /** The choice being saved; undefined is None. */
    pending: string | undefined;
    notice: Notice | undefined;
}

export interface BackgroundPreferenceInput {
    api: ApiClient;
    fname: string;
}

export type BackgroundPreferenceEvent =
    | { type: 'reload' }
    | { type: 'toggle' }
    | { type: 'close' }
    /** Choose an image by its URL, or None with undefined. */
    | { type: 'choose'; url: string | undefined }
    /** The element's context path or CSRF token changed. */
    | { type: 'api.changed'; api: ApiClient };

export type BackgroundPreferenceEmitted = {
    type: 'background.changed';
    url: string | undefined;
};

const NO_BACKGROUNDS: BackgroundPreference = {
    containerSelector: '',
    images: [],
    selected: undefined,
};

export function errorKind(error: unknown): ApiErrorKind {
    return error instanceof ApiError ? error.kind : 'malformed';
}

export const backgroundPreferenceMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion, typescript/no-unsafe-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: BackgroundPreferenceContext;
        events: BackgroundPreferenceEvent;
        input: BackgroundPreferenceInput;
        emitted: BackgroundPreferenceEmitted;
        tags: 'busy';
    },
    actors: {
        loadBackground: fromPromise<BackgroundPreference, { api: ApiClient; fname: string }>(
            ({ input, signal }) => fetchBackground(input.api, input.fname, { signal }),
        ),
        saveBackground: fromPromise<
            BackgroundPreference,
            { api: ApiClient; fname: string; url: string | undefined }
        >(({ input, signal }) => saveBackground(input.api, input.fname, input.url, { signal })),
    },
    guards: {
        isOffered: ({ context }, { url }: { url: string | undefined }) =>
            url === undefined || context.data.images.some((image) => image.url === url),
        isSessionLost: (_, { error }: { error: unknown }) =>
            errorKind(error) === 'unauthorized' || errorKind(error) === 'forbidden',
    },
    actions: {
        loaded: assign((_, { data }: { data: BackgroundPreference }) => ({ data })),
        saved: enqueueActions(({ enqueue }, { data }: { data: BackgroundPreference }) => {
            enqueue.assign({ data, pending: undefined, notice: undefined });
            enqueue.emit({ type: 'background.changed', url: data.selected });
        }),
        saveFailed: assign((_, { error }: { error: unknown }) => ({
            pending: undefined,
            notice: { kind: 'save-failed' as const, reason: errorKind(error) },
        })),
        clearNotice: assign({ notice: undefined }),
    },
}).createMachine({
    id: 'backgroundPreference',
    context: ({ input }) => ({
        api: input.api,
        fname: input.fname,
        data: NO_BACKGROUNDS,
        pending: undefined,
        notice: undefined,
    }),
    initial: 'loading',
    on: {
        'api.changed': { actions: assign({ api: ({ event }) => event.api }) },
    },
    states: {
        loading: {
            tags: ['busy'],
            invoke: {
                id: 'loadBackground',
                src: 'loadBackground',
                input: ({ context }) => ({ api: context.api, fname: context.fname }),
                onDone: {
                    target: 'ready',
                    actions: { type: 'loaded', params: ({ event }) => ({ data: event.output }) },
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
            initial: 'closed',
            states: {
                closed: {
                    on: { toggle: { target: 'open', actions: 'clearNotice' } },
                },
                open: {
                    on: {
                        toggle: { target: 'closed', actions: 'clearNotice' },
                        close: { target: 'closed', actions: 'clearNotice' },
                        choose: {
                            guard: {
                                type: 'isOffered',
                                params: ({ event }) => ({ url: event.url }),
                            },
                            target: 'saving',
                            actions: assign({
                                pending: ({ event }) => event.url,
                                notice: undefined,
                            }),
                        },
                    },
                },
                saving: {
                    tags: ['busy'],
                    invoke: {
                        id: 'saveBackground',
                        src: 'saveBackground',
                        input: ({ context }) => ({
                            api: context.api,
                            fname: context.fname,
                            url: context.pending,
                        }),
                        onDone: {
                            target: 'closed',
                            actions: {
                                type: 'saved',
                                params: ({ event }) => ({ data: event.output }),
                            },
                        },
                        onError: [
                            {
                                guard: {
                                    type: 'isSessionLost',
                                    params: ({ event }) => ({ error: event.error }),
                                },
                                target: '#backgroundPreference.signedOut',
                            },
                            {
                                target: 'open',
                                actions: {
                                    type: 'saveFailed',
                                    params: ({ event }) => ({ error: event.error }),
                                },
                            },
                        ],
                    },
                },
            },
        },
    },
});

export type BackgroundPreferenceMachine = typeof backgroundPreferenceMachine;
