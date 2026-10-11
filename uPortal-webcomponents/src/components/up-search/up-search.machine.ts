/**
 * The states of `<up-search>`, as an XState machine (MOD-D31).
 *
 * A pure module: no DOM. The search is the invoked actor `search`, which tests replace with
 * `searchMachine.provide({ actors })`. The element renders from the state (`snapshot.matches`,
 * the `busy` tag) and the context.
 *
 * ```
 * start                    to searching when there is a query, else idle
 * idle                     no query yet: only the search box
 * searching                GET /api/v6-0/search (busy)
 * results                  one tab per result type; `selected` is the tab shown
 * failed.retryable         the search failed; "retry" searches again
 * failed.sessionExpired    401: the user has to sign in again
 * ```
 *
 * A `search` event with a query that is not blank starts a new search from any state.
 */
import { assign, emit, fromPromise, setup } from 'xstate';
import { ApiError, type ApiClient, type ApiErrorKind } from '../../lib/api-client';
import { fetchSearchResults, type SearchResults } from './search-api';

export interface SearchContext {
    api: ApiClient;
    /** The result types to search, as the channel set them; empty means every type. */
    types: readonly string[];
    query: string;
    results: SearchResults | undefined;
    /** The type of the tab shown. */
    selected: string | undefined;
}

export interface SearchInput {
    api: ApiClient;
    query: string;
    types: readonly string[];
}

export type SearchEvent =
    | { type: 'search'; query: string }
    | { type: 'retry' }
    | { type: 'tab.select'; resultType: string }
    /** The element's context path changed. */
    | { type: 'api.changed'; api: ApiClient };

export type SearchEmitted = { type: 'search.completed'; query: string; results: number };

export function errorKind(error: unknown): ApiErrorKind {
    return error instanceof ApiError ? error.kind : 'malformed';
}

export const searchMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion, typescript/no-unsafe-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: SearchContext;
        events: SearchEvent;
        input: SearchInput;
        emitted: SearchEmitted;
        tags: 'busy';
    },
    actors: {
        search: fromPromise<
            SearchResults,
            { api: ApiClient; query: string; types: readonly string[] }
        >(({ input, signal }) =>
            fetchSearchResults(input.api, input.query, input.types, { signal }),
        ),
    },
    guards: {
        hasQuery: ({ context }) => context.query.trim() !== '',
        isQuery: (_, { query }: { query: string }) => query.trim() !== '',
        isUnauthorized: (_, { error }: { error: unknown }) => errorKind(error) === 'unauthorized',
        isResultType: ({ context }, { resultType }: { resultType: string }) =>
            context.results?.groups.some((group) => group.type === resultType) === true,
    },
    actions: {
        found: assign((_, { results }: { results: SearchResults }) => ({
            results,
            selected: results.groups[0]?.type,
        })),
        completed: emit((_, { results }: { results: SearchResults }) => ({
            type: 'search.completed' as const,
            query: results.query,
            results: results.groups.reduce((total, group) => total + group.results.length, 0),
        })),
    },
}).createMachine({
    id: 'search',
    initial: 'start',
    context: ({ input }) => ({
        api: input.api,
        types: input.types,
        query: input.query.trim(),
        results: undefined,
        selected: undefined,
    }),
    on: {
        search: {
            guard: { type: 'isQuery', params: ({ event }) => event },
            target: '.searching',
            reenter: true,
            actions: assign(({ event }) => ({ query: event.query.trim() })),
        },
        'api.changed': { actions: assign(({ event }) => ({ api: event.api })) },
    },
    states: {
        start: {
            always: [{ guard: 'hasQuery', target: 'searching' }, { target: 'idle' }],
        },
        idle: {},
        searching: {
            tags: 'busy',
            invoke: {
                id: 'search',
                src: 'search',
                input: ({ context }) => ({
                    api: context.api,
                    query: context.query,
                    types: context.types,
                }),
                onDone: {
                    target: 'results',
                    actions: [
                        { type: 'found', params: ({ event }) => ({ results: event.output }) },
                        { type: 'completed', params: ({ event }) => ({ results: event.output }) },
                    ],
                },
                onError: [
                    {
                        guard: { type: 'isUnauthorized', params: ({ event }) => event },
                        target: 'failed.sessionExpired',
                    },
                    { target: 'failed.retryable' },
                ],
            },
        },
        results: {
            on: {
                'tab.select': {
                    guard: { type: 'isResultType', params: ({ event }) => event },
                    actions: assign(({ event }) => ({ selected: event.resultType })),
                },
            },
        },
        failed: {
            initial: 'retryable',
            states: {
                retryable: {
                    on: { retry: '#search.searching' },
                },
                sessionExpired: {},
            },
        },
    },
});

export type SearchMachine = typeof searchMachine;
