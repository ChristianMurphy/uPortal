/**
 * The type-ahead of `<up-search-box>`, as an XState machine (MOD-D31).
 *
 * A pure module: no DOM. The portlet list comes from the invoked actor `loadSuggestions`, which
 * tests replace with `searchBoxMachine.provide({ actors })`; the 200 ms pause after typing is the
 * delay `typeAhead`.
 *
 * ```
 * source (parallel with popup)
 *   loading        GET /api/portletList
 *   ready          suggestions to match
 *   unavailable    the list failed to load: the box works without type-ahead
 * popup
 *   closed         nothing shown
 *   waiting        the user typed; match after a pause
 *   open           the matches, with an active one or none (-1)
 * ```
 *
 * Choosing a suggestion emits `suggestion.chosen`; the element opens the portlet.
 */
import { assign, emit, fromPromise, setup } from 'xstate';
import type { ApiClient } from '../../lib/api-client';
import { fetchSuggestions, matchSuggestions, type Suggestion } from './search-api';

export interface SearchBoxContext {
    api: ApiClient;
    suggestions: readonly Suggestion[];
    /** What the user typed last. */
    term: string;
    /** The suggestions shown while the popup is open. */
    matches: readonly Suggestion[];
    /** The highlighted match, or -1 when the input itself is active. */
    active: number;
}

export interface SearchBoxInput {
    api: ApiClient;
}

export type SearchBoxEvent =
    | { type: 'typed'; term: string }
    /** Arrow Down and Arrow Up: the next or previous match, through "none" at either end. */
    | { type: 'next' }
    | { type: 'previous' }
    /** Escape, or the focus left the box. */
    | { type: 'close' }
    | { type: 'choose'; index: number }
    /** The element's context path changed. */
    | { type: 'api.changed'; api: ApiClient };

export type SearchBoxEmitted = { type: 'suggestion.chosen'; fname: string; title: string };

/** How long type-ahead waits after the last keystroke, as the Search portlet's did. */
export const TYPE_AHEAD_DELAY = 200;

const isBlank = (_: unknown, { term }: { term: string }): boolean => term.trim() === '';

/**
 * Typing, in every state of the popup: a blank term closes it, anything else (re)starts the
 * pause. Only "waiting" is re-entered, so the pause restarts and the list is not loaded again.
 */
const typed = [
    {
        guard: { type: 'isBlank', params: ({ event }: { event: { term: string } }) => event },
        target: 'closed',
        actions: [
            { type: 'remember', params: ({ event }: { event: { term: string } }) => event },
            'hide',
        ],
    },
    {
        target: 'waiting',
        reenter: true,
        actions: { type: 'remember', params: ({ event }: { event: { term: string } }) => event },
    },
] as const;

export const searchBoxMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion, typescript/no-unsafe-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: SearchBoxContext;
        events: SearchBoxEvent;
        input: SearchBoxInput;
        emitted: SearchBoxEmitted;
    },
    actors: {
        loadSuggestions: fromPromise<Suggestion[], { api: ApiClient }>(({ input, signal }) =>
            fetchSuggestions(input.api, { signal }),
        ),
    },
    delays: {
        typeAhead: TYPE_AHEAD_DELAY,
    },
    guards: {
        isBlank,
        hasMatches: ({ context }) => matchSuggestions(context.suggestions, context.term).length > 0,
        isMatch: ({ context }, { index }: { index: number }) =>
            index >= 0 && index < context.matches.length,
    },
    actions: {
        remember: assign((_, { term }: { term: string }) => ({ term })),
        showMatches: assign(({ context }) => ({
            matches: matchSuggestions(context.suggestions, context.term),
            active: -1,
        })),
        hide: assign({ matches: [], active: -1 }),
        next: assign(({ context }) => ({
            active: context.active + 1 < context.matches.length ? context.active + 1 : -1,
        })),
        previous: assign(({ context }) => ({
            active: context.active < 0 ? context.matches.length - 1 : context.active - 1,
        })),
        chosen: emit(({ context }, { index }: { index: number }) => {
            const suggestion = context.matches[index];
            /* v8 ignore if -- unreachable: the isMatch guard checked the index */
            if (suggestion === undefined) {
                throw new Error(`No match ${index}`);
            }
            return {
                type: 'suggestion.chosen' as const,
                fname: suggestion.fname,
                title: suggestion.title,
            };
        }),
    },
}).createMachine({
    id: 'searchBox',
    type: 'parallel',
    context: ({ input }) => ({
        api: input.api,
        suggestions: [],
        term: '',
        matches: [],
        active: -1,
    }),
    on: {
        'api.changed': { actions: assign(({ event }) => ({ api: event.api })) },
    },
    states: {
        source: {
            initial: 'loading',
            states: {
                loading: {
                    invoke: {
                        id: 'loadSuggestions',
                        src: 'loadSuggestions',
                        input: ({ context }) => ({ api: context.api }),
                        onDone: {
                            target: 'ready',
                            actions: assign(({ event }) => ({ suggestions: event.output })),
                        },
                        onError: 'unavailable',
                    },
                },
                ready: {},
                unavailable: {},
            },
        },
        popup: {
            initial: 'closed',
            states: {
                closed: {
                    on: { typed },
                },
                waiting: {
                    after: {
                        typeAhead: [
                            { guard: 'hasMatches', target: 'open', actions: 'showMatches' },
                            { target: 'closed', actions: 'hide' },
                        ],
                    },
                    on: {
                        typed,
                        close: { target: 'closed', actions: 'hide' },
                    },
                },
                open: {
                    on: {
                        typed,
                        next: { actions: 'next' },
                        previous: { actions: 'previous' },
                        close: { target: 'closed', actions: 'hide' },
                        choose: {
                            guard: { type: 'isMatch', params: ({ event }) => event },
                            target: 'closed',
                            actions: [{ type: 'chosen', params: ({ event }) => event }, 'hide'],
                        },
                    },
                },
            },
        },
    },
});

export type SearchBoxMachine = typeof searchBoxMachine;
