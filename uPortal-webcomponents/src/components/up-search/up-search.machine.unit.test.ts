import { describe, expect, it, vi } from 'vite-plus/test';
import { createActor, fromPromise, waitFor } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import type { SearchResults } from './search-api';
import { errorKind, searchMachine } from './up-search.machine';

const API = new ApiClient({ contextPath: '/uPortal' });

function results(query: string): SearchResults {
    return {
        query,
        groups: [
            { type: 'people', label: null, results: [], failed: false },
            {
                type: 'portlets',
                label: null,
                failed: false,
                results: [{ title: 'Calendar', url: '/p/calendar/max', attributes: {} }],
            },
        ],
    };
}

type Search = (input: { query: string; types: readonly string[] }) => Promise<SearchResults>;

function start(query: string, search: Search = ({ query: q }) => Promise.resolve(results(q))) {
    const calls = vi.fn(search);
    const actor = createActor(
        searchMachine.provide({ actors: { search: fromPromise(({ input }) => calls(input)) } }),
        { input: { api: API, query, types: ['portlets'] } },
    );
    const completed: unknown[] = [];
    actor.on('search.completed', (event) => completed.push(event));
    actor.start();
    return { actor, calls, completed };
}

function settled(actor: ReturnType<typeof start>['actor']) {
    return waitFor(actor, (snapshot) => !snapshot.matches('searching'));
}

describe('searchMachine', () => {
    it('waits for a query when it starts without one', () => {
        const { actor, calls } = start('  ');

        expect(actor.getSnapshot().matches('idle')).toBe(true);
        expect(calls).not.toHaveBeenCalled();
    });

    it('searches at once for the query it starts with, as busy', async () => {
        const { actor, calls, completed } = start(' cal ');
        expect(actor.getSnapshot().matches('searching')).toBe(true);
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);

        await settled(actor);

        expect(calls).toHaveBeenCalledWith(
            expect.objectContaining({ query: 'cal', types: ['portlets'] }),
        );
        const snapshot = actor.getSnapshot();
        expect(snapshot.matches('results')).toBe(true);
        expect(snapshot.context.results).toEqual(results('cal'));
        expect(snapshot.context.selected).toBe('people');
        expect(completed).toEqual([{ type: 'search.completed', query: 'cal', results: 1 }]);
    });

    it('selects a tab of the results, and only a result type', async () => {
        const { actor } = start('cal');
        await settled(actor);

        actor.send({ type: 'tab.select', resultType: 'portlets' });
        expect(actor.getSnapshot().context.selected).toBe('portlets');
        expect(actor.getSnapshot().can({ type: 'tab.select', resultType: 'places' })).toBe(false);
    });

    it('selects nothing when no type was searched', async () => {
        const { actor } = start('cal', ({ query }) => Promise.resolve({ query, groups: [] }));
        await settled(actor);

        expect(actor.getSnapshot().context.selected).toBeUndefined();
    });

    it('searches again for a new query from any state, but not for a blank one', async () => {
        const { actor, calls } = start('');

        expect(actor.getSnapshot().can({ type: 'search', query: ' ' })).toBe(false);
        actor.send({ type: 'search', query: ' mail ' });
        expect(actor.getSnapshot().context.query).toBe('mail');
        await settled(actor);
        actor.send({ type: 'search', query: 'news' });
        await settled(actor);

        expect(calls.mock.calls.map(([input]) => input.query)).toEqual(['mail', 'news']);
        expect(actor.getSnapshot().context.results?.query).toBe('news');
    });

    it('offers a retry after a failure', async () => {
        let fail = true;
        const { actor } = start('cal', ({ query }) =>
            fail
                ? Promise.reject(new ApiError('server', 500, 'down'))
                : Promise.resolve(results(query)),
        );
        await settled(actor);
        expect(actor.getSnapshot().matches({ failed: 'retryable' })).toBe(true);

        fail = false;
        actor.send({ type: 'retry' });
        await settled(actor);

        expect(actor.getSnapshot().matches('results')).toBe(true);
    });

    it('asks the user to sign in again after a 401', async () => {
        const { actor } = start('cal', () =>
            Promise.reject(new ApiError('unauthorized', 401, 'gone')),
        );
        await settled(actor);

        expect(actor.getSnapshot().matches({ failed: 'sessionExpired' })).toBe(true);
        expect(actor.getSnapshot().can({ type: 'retry' })).toBe(false);
    });

    it('takes a new api client', () => {
        const { actor } = start('');
        const api = new ApiClient({ contextPath: '/portal' });

        actor.send({ type: 'api.changed', api });

        expect(actor.getSnapshot().context.api).toBe(api);
    });

    it('treats anything but an api error as malformed', () => {
        expect(errorKind(new ApiError('forbidden', 403, 'no'))).toBe('forbidden');
        expect(errorKind(new Error('boom'))).toBe('malformed');
    });
});
