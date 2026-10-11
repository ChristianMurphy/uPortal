import { describe, expect, it } from 'vite-plus/test';
import { createActor, fromPromise, waitFor, type SnapshotFrom } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import type { BackgroundPreference } from './background-api';
import {
    backgroundPreferenceMachine,
    errorKind,
    type BackgroundPreferenceEmitted,
    type BackgroundPreferenceMachine,
} from './up-background-preference.machine';

const GALAXY = { url: '/bg/galaxy.jpg', thumbnailUrl: '/bg/t/galaxy.jpg', caption: 'Galaxy' };
const MOUNTAIN = {
    url: '/bg/mountain.jpg',
    thumbnailUrl: '/bg/t/mountain.jpg',
    caption: 'Mountain',
};
const NONE_CHOSEN: BackgroundPreference = {
    containerSelector: '#page::before',
    images: [GALAXY, MOUNTAIN],
    selected: undefined,
};
const MOUNTAIN_CHOSEN: BackgroundPreference = { ...NONE_CHOSEN, selected: MOUNTAIN.url };

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

interface Call {
    api: ApiClient;
    fname: string;
    url?: string | undefined;
    answer: ReturnType<typeof deferred<BackgroundPreference>>;
}

function start(api = new ApiClient({ contextPath: '/uPortal' })) {
    const calls = { load: [] as Call[], save: [] as Call[] };
    const machine: BackgroundPreferenceMachine = backgroundPreferenceMachine.provide({
        actors: {
            loadBackground: fromPromise<BackgroundPreference, { api: ApiClient; fname: string }>(
                ({ input }) => {
                    const answer = deferred<BackgroundPreference>();
                    calls.load.push({ ...input, answer });
                    return answer.promise;
                },
            ),
            saveBackground: fromPromise<
                BackgroundPreference,
                { api: ApiClient; fname: string; url: string | undefined }
            >(({ input }) => {
                const answer = deferred<BackgroundPreference>();
                calls.save.push({ ...input, answer });
                return answer.promise;
            }),
        },
    });
    const actor = createActor(machine, { input: { api, fname: 'background-preference' } });
    const emitted: BackgroundPreferenceEmitted[] = [];
    actor.on('*', (event) => {
        emitted.push(event);
    });
    actor.start();
    return { actor, calls, emitted };
}

type Running = ReturnType<typeof start>;

function settled(
    running: Running,
    done: (s: SnapshotFrom<BackgroundPreferenceMachine>) => boolean,
) {
    return waitFor(running.actor, done, { timeout: 1000 });
}

async function open(data = NONE_CHOSEN): Promise<Running> {
    const running = start();
    running.calls.load[0]?.answer.resolve(data);
    await settled(running, (s) => s.matches('ready'));
    running.actor.send({ type: 'toggle' });
    return running;
}

describe('background preference machine: loading', () => {
    it('starts loading, busy, with the api and fname it was given', () => {
        const api = new ApiClient({ contextPath: '/x' });
        const { actor, calls } = start(api);

        expect(actor.getSnapshot().value).toBe('loading');
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);
        expect(calls.load[0]?.api).toBe(api);
        expect(calls.load[0]?.fname).toBe('background-preference');
    });

    it('is ready and closed once loaded', async () => {
        const running = start();
        running.calls.load[0]?.answer.resolve(MOUNTAIN_CHOSEN);
        await settled(running, (s) => s.matches('ready'));

        const snapshot = running.actor.getSnapshot();
        expect(snapshot.value).toEqual({ ready: 'closed' });
        expect(snapshot.context.data).toEqual(MOUNTAIN_CHOSEN);
        expect(running.emitted).toEqual([]);
    });

    it.each([
        ['server', new ApiError('server', 500, 'boom')],
        ['network', new ApiError('network', undefined, 'offline')],
        ['not found', new ApiError('not-found', 404, 'no channel')],
        ['an error that is not an ApiError', new Error('bug')],
    ])('fails on %s, and loads again on reload', async (_name, error) => {
        const running = start();
        running.calls.load[0]?.answer.reject(error);
        await settled(running, (s) => s.matches('failed'));

        running.actor.send({ type: 'reload' });
        expect(running.actor.getSnapshot().value).toBe('loading');
        running.calls.load[1]?.answer.resolve(NONE_CHOSEN);
        await settled(running, (s) => s.matches('ready'));
    });

    it.each([
        ['401', new ApiError('unauthorized', 401, 'guest')],
        ['403', new ApiError('forbidden', 403, 'csrf')],
    ])('is signed out when the portal answers %s', async (_name, error) => {
        const running = start();
        running.calls.load[0]?.answer.reject(error);
        await settled(running, (s) => s.matches('signedOut'));

        running.actor.send({ type: 'reload' });
        running.actor.send({ type: 'toggle' });
        expect(running.actor.getSnapshot().value).toBe('signedOut');
    });

    it('ignores the list until loaded', () => {
        const { actor, calls } = start();

        actor.send({ type: 'toggle' });
        actor.send({ type: 'choose', url: GALAXY.url });
        expect(actor.getSnapshot().value).toBe('loading');
        expect(calls.save).toHaveLength(0);
    });
});

describe('background preference machine: the list', () => {
    it('opens and closes with toggle, and closes with close', async () => {
        const { actor } = await open();
        expect(actor.getSnapshot().value).toEqual({ ready: 'open' });

        actor.send({ type: 'toggle' });
        expect(actor.getSnapshot().value).toEqual({ ready: 'closed' });

        actor.send({ type: 'toggle' });
        actor.send({ type: 'close' });
        expect(actor.getSnapshot().value).toEqual({ ready: 'closed' });
    });

    it('chooses only when the list is open', async () => {
        const running = start();
        running.calls.load[0]?.answer.resolve(NONE_CHOSEN);
        await settled(running, (s) => s.matches('ready'));

        running.actor.send({ type: 'choose', url: GALAXY.url });
        running.actor.send({ type: 'close' });

        expect(running.actor.getSnapshot().value).toEqual({ ready: 'closed' });
        expect(running.calls.save).toHaveLength(0);
    });

    it('refuses an image that is not offered', async () => {
        const { actor, calls } = await open();

        actor.send({ type: 'choose', url: '/elsewhere.jpg' });

        expect(actor.getSnapshot().value).toEqual({ ready: 'open' });
        expect(calls.save).toHaveLength(0);
    });
});

describe('background preference machine: saving', () => {
    it('saves an offered image, busy, with the current api', async () => {
        const running = await open();
        const api = new ApiClient({ contextPath: '/other' });
        running.actor.send({ type: 'api.changed', api });

        running.actor.send({ type: 'choose', url: GALAXY.url });

        const snapshot = running.actor.getSnapshot();
        expect(snapshot.value).toEqual({ ready: 'saving' });
        expect(snapshot.hasTag('busy')).toBe(true);
        expect(snapshot.context.pending).toBe(GALAXY.url);
        expect(running.calls.save[0]).toMatchObject({ api, url: GALAXY.url });
    });

    it('saves none', async () => {
        const running = await open(MOUNTAIN_CHOSEN);

        running.actor.send({ type: 'choose', url: undefined });

        expect(running.calls.save[0]?.url).toBeUndefined();
    });

    it('ignores the list while saving', async () => {
        const running = await open();
        running.actor.send({ type: 'choose', url: GALAXY.url });

        running.actor.send({ type: 'choose', url: MOUNTAIN.url });
        running.actor.send({ type: 'toggle' });
        running.actor.send({ type: 'close' });

        expect(running.actor.getSnapshot().value).toEqual({ ready: 'saving' });
        expect(running.calls.save).toHaveLength(1);
    });

    it('closes the list and says so once the portal saved it', async () => {
        const running = await open();
        running.actor.send({ type: 'choose', url: MOUNTAIN.url });

        running.calls.save[0]?.answer.resolve(MOUNTAIN_CHOSEN);
        await settled(running, (s) => s.matches({ ready: 'closed' }));

        expect(running.actor.getSnapshot().context.data).toEqual(MOUNTAIN_CHOSEN);
        expect(running.actor.getSnapshot().context.pending).toBeUndefined();
        expect(running.emitted).toEqual([{ type: 'background.changed', url: MOUNTAIN.url }]);
    });

    it.each([
        ['server', new ApiError('server', 500, 'boom')],
        ['refused', new ApiError('http', 400, 'not offered')],
        ['network', new ApiError('network', undefined, 'offline')],
        ['malformed', new Error('bug')],
    ])('keeps the list open with a %s notice when the save fails', async (_name, error) => {
        const running = await open(MOUNTAIN_CHOSEN);
        running.actor.send({ type: 'choose', url: GALAXY.url });

        running.calls.save[0]?.answer.reject(error);
        await settled(running, (s) => s.matches({ ready: 'open' }));

        const { context } = running.actor.getSnapshot();
        expect(context.notice).toEqual({ kind: 'save-failed', reason: errorKind(error) });
        expect(context.data).toEqual(MOUNTAIN_CHOSEN);
        expect(running.emitted).toEqual([]);
    });

    it.each(['toggle', 'close'] as const)('clears the notice on %s', async (type) => {
        const running = await open();
        running.actor.send({ type: 'choose', url: GALAXY.url });
        running.calls.save[0]?.answer.reject(new ApiError('server', 500, 'boom'));
        await settled(running, (s) => s.matches({ ready: 'open' }));

        running.actor.send({ type });

        expect(running.actor.getSnapshot().context.notice).toBeUndefined();
    });

    it('clears the notice when choosing again', async () => {
        const running = await open();
        running.actor.send({ type: 'choose', url: GALAXY.url });
        running.calls.save[0]?.answer.reject(new ApiError('server', 500, 'boom'));
        await settled(running, (s) => s.matches({ ready: 'open' }));

        running.actor.send({ type: 'choose', url: GALAXY.url });

        expect(running.actor.getSnapshot().context.notice).toBeUndefined();
    });

    it.each([
        ['401', new ApiError('unauthorized', 401, 'expired')],
        ['403', new ApiError('forbidden', 403, 'csrf')],
    ])('is signed out when the save answers %s', async (_name, error) => {
        const running = await open();
        running.actor.send({ type: 'choose', url: GALAXY.url });

        running.calls.save[0]?.answer.reject(error);

        await settled(running, (s) => s.matches('signedOut'));
    });
});

describe('errorKind', () => {
    it('is the kind of an ApiError, else malformed', () => {
        expect(errorKind(new ApiError('conflict', 409, 'x'))).toBe('conflict');
        expect(errorKind('boom')).toBe('malformed');
    });
});
