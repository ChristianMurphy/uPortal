/**
 * The states of `<up-popular-portlets>`, as an XState machine (MOD-D31).
 *
 * A pure module: no DOM. The REST call is the invoked actor `loadPopularPortlets`, which tests
 * replace with `popularPortletsMachine.provide({ actors })`. What the element shows comes from
 * the state, the `busy` tag and the context; table.ts derives the visible rows from the context.
 *
 * ```
 * loading                    GET the popular portlets of the chosen days (busy)
 * failed.retryable           the load failed; "reload" tries again
 * failed.sessionExpired      401: the user has to sign in again
 * empty                      no portlet was added in those days
 * list                       the table: sort, filter and page through it
 * ```
 *
 * Choosing other days loads again from any state, also while loading (the earlier request is
 * abandoned). Sorting, filtering and paging only change the context, and keep the loaded rows.
 */
import { assign, emit, fromPromise, setup } from 'xstate';
import { ApiError, type ApiClient, type ApiErrorKind } from '../../lib/api-client';
import {
    fetchPopularPortlets,
    type PopularPortlet,
    type PopularPortletsData,
} from './popular-portlets-api';
import {
    DAY_CHOICES,
    DEFAULT_DAYS,
    DEFAULT_PAGE_SIZE,
    DEFAULT_SORT,
    filterOptions,
    nextSort,
    NO_FILTERS,
    PAGE_SIZES,
    tableView,
    type Column,
    type Filters,
    type Sort,
} from './table';

/**
 * Where the element puts focus: the filter of a column, after its last term went away with the
 * button that had focus. `id` grows with every request, so each one is applied once.
 */
export interface FocusRequest {
    id: number;
    filter: Column;
}

export interface PopularPortletsContext {
    api: ApiClient;
    locale: string;
    days: number;
    portlets: readonly PopularPortlet[];
    sort: Sort;
    filters: Filters;
    pageSize: number;
    /** 0-based. */
    page: number;
    focus: FocusRequest | undefined;
}

export interface PopularPortletsInput {
    api: ApiClient;
    locale: string;
    /** One of DAY_CHOICES; anything else counts 30 days. */
    days?: number | undefined;
}

export type PopularPortletsEvent =
    /** Load the same days again (also how the user retries). */
    | { type: 'reload' }
    | { type: 'days.change'; days: number }
    /** Click on a column header. */
    | { type: 'sort'; column: Column }
    | { type: 'page.size'; size: number }
    /** 0-based. */
    | { type: 'page.go'; page: number }
    | { type: 'filter.add'; column: Column; value: string }
    | { type: 'filter.remove'; column: Column; value: string }
    /** The element's context path or CSRF token changed. */
    | { type: 'api.changed'; api: ApiClient }
    /** The element's locale changed: titles and descriptions load again in it. */
    | { type: 'locale.changed'; locale: string };

export type PopularPortletsEmitted = {
    type: 'popular-portlets.loaded';
    days: number;
    portlets: number;
};

export function errorKind(error: unknown): ApiErrorKind {
    return error instanceof ApiError ? error.kind : 'malformed';
}

/** A valid choice of days, else the default. */
export function daysOrDefault(days: number | undefined): number {
    const choice = DAY_CHOICES.find((each) => each === days);
    return choice ?? DEFAULT_DAYS;
}

export const popularPortletsMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion, typescript/no-unsafe-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: PopularPortletsContext;
        events: PopularPortletsEvent;
        input: PopularPortletsInput;
        emitted: PopularPortletsEmitted;
        tags: 'busy';
    },
    actors: {
        loadPopularPortlets: fromPromise<
            PopularPortletsData,
            { api: ApiClient; days: number; locale: string }
        >(({ input, signal }) =>
            fetchPopularPortlets(input.api, input.days, input.locale, { signal }),
        ),
    },
    guards: {
        hasNone: (_, { data }: { data: PopularPortletsData }) => data.portlets.length === 0,
        isUnauthorized: (_, { error }: { error: unknown }) => errorKind(error) === 'unauthorized',
        /** Other days among the choices; choosing the same days again changes nothing. */
        isOtherDays: ({ context }, { days }: { days: number }) =>
            days !== context.days && DAY_CHOICES.some((choice) => choice === days),
        isOtherPageSize: ({ context }, { size }: { size: number }) =>
            size !== context.pageSize && PAGE_SIZES.some((choice) => choice === size),
        isOtherPage: ({ context }, { page }: { page: number }) => {
            const { pageCount, page: current } = tableView(
                context.portlets,
                context.filters,
                context.sort,
                context.pageSize,
                context.page,
            );
            return Number.isInteger(page) && page >= 0 && page < pageCount && page !== current;
        },
        canAddFilter: ({ context }, { column, value }: { column: Column; value: string }) =>
            !context.filters[column].includes(value) &&
            filterOptions(context.portlets, column).includes(value),
        hasFilter: ({ context }, { column, value }: { column: Column; value: string }) =>
            context.filters[column].includes(value),
    },
    actions: {
        loaded: assign((_, { data }: { data: PopularPortletsData }) => ({
            portlets: data.portlets,
            page: 0,
        })),
        announceLoaded: emit(({ context }) => ({
            type: 'popular-portlets.loaded' as const,
            days: context.days,
            portlets: context.portlets.length,
        })),
    },
}).createMachine({
    id: 'popularPortlets',
    context: ({ input }) => ({
        api: input.api,
        locale: input.locale,
        days: daysOrDefault(input.days),
        portlets: [],
        sort: DEFAULT_SORT,
        filters: NO_FILTERS,
        pageSize: DEFAULT_PAGE_SIZE,
        page: 0,
        focus: undefined,
    }),
    initial: 'loading',
    on: {
        reload: { target: '.loading', reenter: true },
        'days.change': {
            guard: { type: 'isOtherDays', params: ({ event }) => ({ days: event.days }) },
            target: '.loading',
            reenter: true,
            actions: assign({ days: ({ event }) => event.days }),
        },
        'api.changed': { actions: assign({ api: ({ event }) => event.api }) },
        'locale.changed': {
            target: '.loading',
            reenter: true,
            actions: assign({ locale: ({ event }) => event.locale }),
        },
    },
    states: {
        loading: {
            tags: ['busy'],
            invoke: {
                id: 'loadPopularPortlets',
                src: 'loadPopularPortlets',
                input: ({ context }) => ({
                    api: context.api,
                    days: context.days,
                    locale: context.locale,
                }),
                onDone: [
                    {
                        guard: { type: 'hasNone', params: ({ event }) => ({ data: event.output }) },
                        target: 'empty',
                        actions: [
                            { type: 'loaded', params: ({ event }) => ({ data: event.output }) },
                            'announceLoaded',
                        ],
                    },
                    {
                        target: 'list',
                        actions: [
                            { type: 'loaded', params: ({ event }) => ({ data: event.output }) },
                            'announceLoaded',
                        ],
                    },
                ],
                onError: [
                    {
                        guard: {
                            type: 'isUnauthorized',
                            params: ({ event }) => ({ error: event.error }),
                        },
                        target: 'failed.sessionExpired',
                    },
                    { target: 'failed.retryable' },
                ],
            },
        },
        failed: {
            initial: 'retryable',
            states: {
                retryable: {},
                sessionExpired: {},
            },
        },
        empty: {},
        list: {
            on: {
                sort: {
                    actions: assign({
                        sort: ({ context, event }) => nextSort(context.sort, event.column),
                        page: 0,
                    }),
                },
                'page.size': {
                    guard: {
                        type: 'isOtherPageSize',
                        params: ({ event }) => ({ size: event.size }),
                    },
                    actions: assign({ pageSize: ({ event }) => event.size, page: 0 }),
                },
                'page.go': {
                    guard: { type: 'isOtherPage', params: ({ event }) => ({ page: event.page }) },
                    actions: assign({ page: ({ event }) => event.page }),
                },
                'filter.add': {
                    guard: {
                        type: 'canAddFilter',
                        params: ({ event }) => ({ column: event.column, value: event.value }),
                    },
                    actions: assign({
                        filters: ({ context, event }) => ({
                            ...context.filters,
                            [event.column]: [...context.filters[event.column], event.value],
                        }),
                        page: 0,
                    }),
                },
                'filter.remove': {
                    guard: {
                        type: 'hasFilter',
                        params: ({ event }) => ({ column: event.column, value: event.value }),
                    },
                    actions: assign({
                        filters: ({ context, event }) => ({
                            ...context.filters,
                            [event.column]: context.filters[event.column].filter(
                                (value) => value !== event.value,
                            ),
                        }),
                        page: 0,
                        focus: ({ context, event }) => ({
                            id: (context.focus?.id ?? 0) + 1,
                            filter: event.column,
                        }),
                    }),
                },
            },
        },
    },
});

export type PopularPortletsMachine = typeof popularPortletsMachine;
