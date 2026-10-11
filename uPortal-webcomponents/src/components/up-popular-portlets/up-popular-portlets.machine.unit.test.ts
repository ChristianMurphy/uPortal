import { describe, expect, it } from 'vite-plus/test';
import { createActor, fromPromise, waitFor } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import type { PopularPortlet, PopularPortletsData } from './popular-portlets-api';
import {
    daysOrDefault,
    errorKind,
    popularPortletsMachine,
    type PopularPortletsEmitted,
    type PopularPortletsMachine,
} from './up-popular-portlets.machine';

function portlet(fname: string, count: number): PopularPortlet {
    return { fname, title: fname, description: null, url: `/p/${fname}`, count };
}

const PORTLETS = [portlet('xkcd', 4), portlet('calendar', 2), portlet('mail', 2)];

function data(days: number, portlets: PopularPortlet[] = PORTLETS): PopularPortletsData {
    return { days, portlets };
}

/** A promise the test settles. */
function deferred<T>() {
    let settle: { resolve: (value: T) => void; reject: (error: unknown) => void } | undefined;
    const promise = new Promise<T>((resolve, reject) => {
        settle = { resolve, reject };
    });
    return {
        promise,
        resolve: (value: T) => settle?.resolve(value),
        reject: (error: unknown) => settle?.reject(error),
    };
}

interface Load {
    api: ApiClient;
    days: number;
    locale: string;
    signal: AbortSignal;
    answer: ReturnType<typeof deferred<PopularPortletsData>>;
}

/** Runs the machine with its REST actor replaced: each load is recorded and answered by the test. */
function start(input: { days?: number; locale?: string } = {}) {
    const loads: Load[] = [];
    const machine: PopularPortletsMachine = popularPortletsMachine.provide({
        actors: {
            loadPopularPortlets: fromPromise<
                PopularPortletsData,
                { api: ApiClient; days: number; locale: string }
            >(({ input: call, signal }) => {
                const answer = deferred<PopularPortletsData>();
                loads.push({ ...call, signal, answer });
                return answer.promise;
            }),
        },
    });
    const actor = createActor(machine, {
        input: {
            api: new ApiClient({ contextPath: '/uPortal' }),
            locale: input.locale ?? 'en-US',
            days: input.days,
        },
    });
    const emitted: PopularPortletsEmitted[] = [];
    actor.on('*', (event) => emitted.push(event));
    actor.start();
    return { actor, loads, emitted };
}

async function loaded(portlets: PopularPortlet[] = PORTLETS) {
    const run = start();
    run.loads[0]?.answer.resolve(data(30, portlets));
    await waitFor(run.actor, (snapshot) => !snapshot.matches('loading'));
    return run;
}

describe('popularPortletsMachine: loading', () => {
    it('loads the last 30 days in the locale, busy meanwhile', () => {
        const { actor, loads } = start();
        expect(actor.getSnapshot().matches('loading')).toBe(true);
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);
        expect(loads.map(({ days, locale }) => [days, locale])).toEqual([[30, 'en-US']]);
    });

    it('starts with the days of its input, if they are a choice', () => {
        expect(start({ days: 7 }).loads[0]?.days).toBe(7);
        expect(start({ days: 8 }).loads[0]?.days).toBe(30);
    });

    it('lists the portlets and says how many it loaded', async () => {
        const { actor, emitted } = await loaded();
        const snapshot = actor.getSnapshot();
        expect(snapshot.matches('list')).toBe(true);
        expect(snapshot.hasTag('busy')).toBe(false);
        expect(snapshot.context.portlets).toEqual(PORTLETS);
        expect(emitted).toEqual([{ type: 'popular-portlets.loaded', days: 30, portlets: 3 }]);
    });

    it('is empty when no portlet was added', async () => {
        const { actor, emitted } = await loaded([]);
        expect(actor.getSnapshot().matches('empty')).toBe(true);
        expect(emitted).toEqual([{ type: 'popular-portlets.loaded', days: 30, portlets: 0 }]);
    });

    it('offers a retry when the load fails', async () => {
        const { actor, loads } = start();
        loads[0]?.answer.reject(new ApiError('server', 500, 'failed'));
        await waitFor(actor, (snapshot) => snapshot.matches({ failed: 'retryable' }));

        actor.send({ type: 'reload' });
        expect(actor.getSnapshot().matches('loading')).toBe(true);
        expect(loads).toHaveLength(2);
    });

    it('asks the user to sign in again after a 401', async () => {
        const { actor, loads } = start();
        loads[0]?.answer.reject(new ApiError('unauthorized', 401, 'expired'));
        await waitFor(actor, (snapshot) => snapshot.matches({ failed: 'sessionExpired' }));
    });

    it('counts an error that is no ApiError as malformed', async () => {
        const { actor, loads } = start();
        loads[0]?.answer.reject(new Error('boom'));
        await waitFor(actor, (snapshot) => snapshot.matches({ failed: 'retryable' }));
        expect(errorKind(new Error('boom'))).toBe('malformed');
    });
});

describe('popularPortletsMachine: days', () => {
    it('loads the chosen days, back on the first page', async () => {
        const { actor, loads } = await loaded();
        actor.send({ type: 'page.size', size: 5 });
        actor.send({ type: 'days.change', days: 365 });
        expect(actor.getSnapshot().matches('loading')).toBe(true);
        expect(loads[1]?.days).toBe(365);

        loads[1]?.answer.resolve(data(365));
        await waitFor(actor, (snapshot) => snapshot.matches('list'));
        expect(actor.getSnapshot().context).toMatchObject({ days: 365, page: 0, pageSize: 5 });
    });

    it('abandons a load when other days are chosen meanwhile', async () => {
        const { actor, loads } = start();
        actor.send({ type: 'days.change', days: 1 });
        expect(loads[0]?.signal.aborted).toBe(true);
        expect(loads[1]?.days).toBe(1);

        loads[1]?.answer.resolve(data(1, [portlet('news', 1)]));
        await waitFor(actor, (snapshot) => snapshot.matches('list'));
        expect(actor.getSnapshot().context.portlets).toEqual([portlet('news', 1)]);
    });

    it('ignores days that are no choice, or the same days again', async () => {
        const { actor, loads } = await loaded();
        actor.send({ type: 'days.change', days: 2 });
        actor.send({ type: 'days.change', days: 30 });
        expect(actor.getSnapshot().matches('list')).toBe(true);
        expect(loads).toHaveLength(1);
    });

    it('also loads again from a failure or the empty state', async () => {
        const { actor, loads } = await loaded([]);
        actor.send({ type: 'days.change', days: 90 });
        expect(loads[1]?.days).toBe(90);
        loads[1]?.answer.reject(new ApiError('server', 500, 'failed'));
        await waitFor(actor, (snapshot) => snapshot.matches('failed'));
        actor.send({ type: 'days.change', days: 7 });
        expect(loads[2]?.days).toBe(7);
    });

    it('defaults days that are no choice', () => {
        expect(daysOrDefault(undefined)).toBe(30);
        expect(daysOrDefault(365)).toBe(365);
        expect(daysOrDefault(0)).toBe(30);
    });
});

describe('popularPortletsMachine: api and locale', () => {
    it('loads again with a new api client', async () => {
        const { actor, loads } = await loaded();
        const api = new ApiClient({ contextPath: '/portal' });
        actor.send({ type: 'api.changed', api });
        expect(actor.getSnapshot().matches('list')).toBe(true);
        actor.send({ type: 'reload' });
        expect(loads[1]?.api).toBe(api);
    });

    it('loads the titles again in a new locale', async () => {
        const { actor, loads } = await loaded();
        actor.send({ type: 'locale.changed', locale: 'de-DE' });
        expect(actor.getSnapshot().matches('loading')).toBe(true);
        expect(loads[1]?.locale).toBe('de-DE');
    });
});

describe('popularPortletsMachine: sorting, paging and filters', () => {
    it('sorts and goes back to the first page', async () => {
        const { actor } = await loaded();
        actor.send({ type: 'page.size', size: 5 });
        actor.send({ type: 'sort', column: 'count' });
        expect(actor.getSnapshot().context.sort).toEqual({
            column: 'count',
            direction: 'ascending',
        });
        actor.send({ type: 'sort', column: 'count' });
        expect(actor.getSnapshot().context.sort.direction).toBe('descending');
    });

    it('accepts only the offered page sizes', async () => {
        const { actor } = await loaded();
        actor.send({ type: 'page.size', size: 20 });
        expect(actor.getSnapshot().context.pageSize).toBe(20);
        actor.send({ type: 'page.size', size: 3 });
        expect(actor.getSnapshot().context.pageSize).toBe(20);
        expect(actor.getSnapshot().can({ type: 'page.size', size: 20 })).toBe(false);
    });

    it('goes to pages that exist', async () => {
        const many = Array.from({ length: 12 }, (_, index) => portlet(`p${index}`, index + 1));
        const { actor } = await loaded(many);
        actor.send({ type: 'page.go', page: 1 });
        expect(actor.getSnapshot().context.page).toBe(1);
        for (const page of [1, 2, -1, 0.5]) {
            actor.send({ type: 'page.go', page });
            expect(actor.getSnapshot().context.page).toBe(1);
        }
        actor.send({ type: 'page.size', size: 5 });
        expect(actor.getSnapshot().context.page).toBe(0);
        actor.send({ type: 'page.go', page: 2 });
        expect(actor.getSnapshot().context.page).toBe(2);
        expect(actor.getSnapshot().can({ type: 'page.go', page: 3 })).toBe(false);
    });

    it('adds and removes filter values, and asks to focus the filter', async () => {
        const { actor } = await loaded();
        actor.send({ type: 'filter.add', column: 'title', value: 'mail' });
        actor.send({ type: 'filter.add', column: 'count', value: '2' });
        expect(actor.getSnapshot().context.filters).toEqual({ title: ['mail'], count: ['2'] });

        actor.send({ type: 'filter.remove', column: 'title', value: 'mail' });
        expect(actor.getSnapshot().context.filters).toEqual({ title: [], count: ['2'] });
        expect(actor.getSnapshot().context.focus).toEqual({ id: 1, filter: 'title' });
        actor.send({ type: 'filter.remove', column: 'count', value: '2' });
        expect(actor.getSnapshot().context.focus).toEqual({ id: 2, filter: 'count' });
    });

    it('ignores filter values twice, unknown, or not set', async () => {
        const { actor } = await loaded();
        actor.send({ type: 'filter.add', column: 'title', value: 'mail' });
        actor.send({ type: 'filter.add', column: 'title', value: 'mail' });
        actor.send({ type: 'filter.add', column: 'title', value: 'unknown' });
        actor.send({ type: 'filter.remove', column: 'count', value: '2' });
        expect(actor.getSnapshot().context.filters).toEqual({ title: ['mail'], count: [] });
        expect(actor.getSnapshot().context.focus).toBeUndefined();
    });

    it('keeps the filters, sort and page size when other days load', async () => {
        const { actor, loads } = await loaded();
        actor.send({ type: 'filter.add', column: 'title', value: 'mail' });
        actor.send({ type: 'sort', column: 'count' });
        actor.send({ type: 'days.change', days: 7 });
        loads[1]?.answer.resolve(data(7));
        await waitFor(actor, (snapshot) => snapshot.matches('list'));
        expect(actor.getSnapshot().context).toMatchObject({
            filters: { title: ['mail'], count: [] },
            sort: { column: 'count', direction: 'ascending' },
        });
    });

    it('does not sort, page or filter without a list', async () => {
        const { actor } = await loaded([]);
        for (const event of [
            { type: 'sort', column: 'count' },
            { type: 'page.size', size: 5 },
            { type: 'filter.add', column: 'title', value: 'mail' },
        ] as const) {
            expect(actor.getSnapshot().can(event)).toBe(false);
        }
    });
});
