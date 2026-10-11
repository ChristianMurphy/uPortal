import { describe, expect, it, vi } from 'vite-plus/test';
import { createActor, fromPromise, waitFor } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import type { Suggestion } from './search-api';
import { searchBoxMachine, TYPE_AHEAD_DELAY } from './up-search-box.machine';

function suggestion(fname: string, title: string): Suggestion {
    return { fname, title, description: '', label: `${title}~${fname}` };
}

const CARTOON = suggestion('cartoon', 'Daily Business Cartoon');
const CALENDAR = suggestion('calendar', 'Calendar');
const COURSES = suggestion('courses', 'Courses');

const API = new ApiClient({ contextPath: '/uPortal' });

function start(
    load: () => Promise<Suggestion[]> = () => Promise.resolve([CARTOON, CALENDAR, COURSES]),
) {
    const loadSuggestions = vi.fn(load);
    const actor = createActor(
        searchBoxMachine.provide({
            actors: { loadSuggestions: fromPromise(() => loadSuggestions()) },
            delays: { typeAhead: 0 },
        }),
        { input: { api: API } },
    );
    const chosen: unknown[] = [];
    actor.on('suggestion.chosen', (event) => chosen.push(event));
    actor.start();
    return { actor, chosen, loadSuggestions };
}

async function ready(actor: ReturnType<typeof start>['actor']) {
    await waitFor(actor, (snapshot) => !snapshot.matches({ source: 'loading' }));
}

async function typed(actor: ReturnType<typeof start>['actor'], term: string) {
    actor.send({ type: 'typed', term });
    await waitFor(actor, (snapshot) => !snapshot.matches({ popup: 'waiting' }));
}

describe('searchBoxMachine', () => {
    it('waits 200 ms after typing, as the Search portlet did', () => {
        expect(TYPE_AHEAD_DELAY).toBe(200);
    });

    it('loads the portlet list once and starts closed', async () => {
        const { actor, loadSuggestions } = start();
        expect(actor.getSnapshot().matches({ source: 'loading', popup: 'closed' })).toBe(true);

        await ready(actor);

        expect(actor.getSnapshot().matches({ source: 'ready' })).toBe(true);
        expect(actor.getSnapshot().context.suggestions).toHaveLength(3);
        expect(loadSuggestions).toHaveBeenCalledTimes(1);
    });

    it('opens with the matches after a pause, none active', async () => {
        const { actor } = start();
        await ready(actor);

        actor.send({ type: 'typed', term: 'c' });
        expect(actor.getSnapshot().matches({ popup: 'waiting' })).toBe(true);
        await typed(actor, 'ca');

        const snapshot = actor.getSnapshot();
        expect(snapshot.matches({ popup: 'open' })).toBe(true);
        expect(snapshot.context.matches).toEqual([CARTOON, CALENDAR]);
        expect(snapshot.context.active).toBe(-1);
        expect(snapshot.context.term).toBe('ca');
    });

    it('keeps the list it loaded while the user types on', async () => {
        const { actor, loadSuggestions } = start();
        await ready(actor);

        await typed(actor, 'c');
        actor.send({ type: 'typed', term: 'ca' });
        actor.send({ type: 'typed', term: 'cal' });
        await typed(actor, 'cale');

        expect(actor.getSnapshot().matches({ source: 'ready', popup: 'open' })).toBe(true);
        expect(loadSuggestions).toHaveBeenCalledTimes(1);
    });

    it('stays closed when nothing matches, and closes when the term is cleared', async () => {
        const { actor } = start();
        await ready(actor);

        await typed(actor, 'zz');
        expect(actor.getSnapshot().matches({ popup: 'closed' })).toBe(true);

        await typed(actor, 'cal');
        expect(actor.getSnapshot().matches({ popup: 'open' })).toBe(true);
        actor.send({ type: 'typed', term: ' ' });
        expect(actor.getSnapshot().matches({ popup: 'closed' })).toBe(true);
        expect(actor.getSnapshot().context.matches).toEqual([]);
    });

    it('works without type-ahead when the list fails to load', async () => {
        const { actor } = start(() => Promise.reject(new ApiError('server', 500, 'down')));
        await ready(actor);
        expect(actor.getSnapshot().matches({ source: 'unavailable' })).toBe(true);

        await typed(actor, 'cal');

        expect(actor.getSnapshot().matches({ popup: 'closed' })).toBe(true);
    });

    it('moves the active match down and up through "none"', async () => {
        const { actor } = start();
        await ready(actor);
        await typed(actor, 'c');
        const active = () => actor.getSnapshot().context.active;

        actor.send({ type: 'next' });
        expect(active()).toBe(0);
        actor.send({ type: 'next' });
        actor.send({ type: 'next' });
        expect(active()).toBe(2);
        actor.send({ type: 'next' });
        expect(active()).toBe(-1);
        actor.send({ type: 'previous' });
        expect(active()).toBe(2);
        actor.send({ type: 'previous' });
        expect(active()).toBe(1);
    });

    it('emits the chosen match and closes', async () => {
        const { actor, chosen } = start();
        await ready(actor);
        await typed(actor, 'cal');

        expect(actor.getSnapshot().can({ type: 'choose', index: 1 })).toBe(false);
        actor.send({ type: 'choose', index: 0 });

        expect(chosen).toEqual([
            { type: 'suggestion.chosen', fname: 'calendar', title: 'Calendar' },
        ]);
        expect(actor.getSnapshot().matches({ popup: 'closed' })).toBe(true);
    });

    it('closes on close, while waiting or open', async () => {
        const { actor } = start();
        await ready(actor);

        actor.send({ type: 'typed', term: 'c' });
        actor.send({ type: 'close' });
        expect(actor.getSnapshot().matches({ popup: 'closed' })).toBe(true);

        await typed(actor, 'c');
        actor.send({ type: 'close' });
        expect(actor.getSnapshot().matches({ popup: 'closed' })).toBe(true);
        expect(actor.getSnapshot().context.active).toBe(-1);
    });

    it('takes a new api client', () => {
        const { actor } = start();
        const api = new ApiClient({ contextPath: '/portal' });

        actor.send({ type: 'api.changed', api });

        expect(actor.getSnapshot().context.api).toBe(api);
    });
});
