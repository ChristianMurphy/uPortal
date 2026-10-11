import { describe, expect, it } from 'vite-plus/test';
import { createActor, fromPromise, waitFor, type SnapshotFrom } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import type { UserLocaleData } from './locale-api';
import {
    errorKind,
    userLocaleMachine,
    type UserLocaleEmitted,
    type UserLocaleMachine,
} from './up-user-locale.machine';

const ENGLISH = { code: 'en_US', tag: 'en-US', name: 'English', nativeName: 'English' };
const FRENCH = { code: 'fr_FR', tag: 'fr-FR', name: 'French', nativeName: 'Français' };
const IN_ENGLISH: UserLocaleData = {
    selected: 'en_US',
    current: 'en_US',
    locales: [ENGLISH, FRENCH],
};
/** No choice, and the portal doesn't say which language the page is in. */
const NONE_CHOSEN: UserLocaleData = { selected: null, current: null, locales: [ENGLISH, FRENCH] };
/** No choice, on a page in French. */
const NONE_CHOSEN_IN_FRENCH: UserLocaleData = { ...NONE_CHOSEN, current: 'fr_FR' };
const IN_FRENCH: UserLocaleData = {
    selected: 'fr_FR',
    current: 'fr_FR',
    locales: [
        { ...ENGLISH, name: 'anglais' },
        { ...FRENCH, name: 'français' },
    ],
};

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

/**
 * Runs the machine with its REST actors replaced: each call is recorded and answered by the
 * test through the returned deferreds.
 */
function start(api = new ApiClient({ contextPath: '/uPortal' })) {
    const calls = {
        load: [] as { api: ApiClient; answer: ReturnType<typeof deferred<UserLocaleData>> }[],
        save: [] as {
            api: ApiClient;
            code: string;
            answer: ReturnType<typeof deferred<UserLocaleData>>;
        }[],
    };
    const machine: UserLocaleMachine = userLocaleMachine.provide({
        actors: {
            loadLocales: fromPromise<UserLocaleData, { api: ApiClient }>(({ input }) => {
                const answer = deferred<UserLocaleData>();
                calls.load.push({ api: input.api, answer });
                return answer.promise;
            }),
            saveLocale: fromPromise<UserLocaleData, { api: ApiClient; code: string }>(
                ({ input }) => {
                    const answer = deferred<UserLocaleData>();
                    calls.save.push({ api: input.api, code: input.code, answer });
                    return answer.promise;
                },
            ),
        },
    });
    const actor = createActor(machine, { input: { api } });
    const emitted: UserLocaleEmitted[] = [];
    actor.on('*', (event) => {
        emitted.push(event);
    });
    actor.start();
    return { actor, calls, emitted };
}

type Running = ReturnType<typeof start>;

async function settled(running: Running, done: (s: SnapshotFrom<UserLocaleMachine>) => boolean) {
    return waitFor(running.actor, done, { timeout: 1000 });
}

async function loaded(data = IN_ENGLISH): Promise<Running> {
    const running = start();
    running.calls.load[0]?.answer.resolve(data);
    await settled(running, (s) => s.matches('ready'));
    return running;
}

describe('user locale machine: loading', () => {
    it('starts loading, busy, with the api it was given', () => {
        const api = new ApiClient({ contextPath: '/x' });
        const { actor, calls } = start(api);

        expect(actor.getSnapshot().value).toBe('loading');
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);
        expect(calls.load[0]?.api).toBe(api);
    });

    it('shows the locales with the saved one chosen', async () => {
        const { actor } = await loaded();

        const snapshot = actor.getSnapshot();
        expect(snapshot.value).toEqual({ ready: 'idle' });
        expect(snapshot.hasTag('busy')).toBe(false);
        expect(snapshot.context.data).toEqual(IN_ENGLISH);
        expect(snapshot.context.choice).toBe('en_US');
    });

    it('has no choice when the user has not chosen a locale', async () => {
        const { actor } = await loaded(NONE_CHOSEN);

        expect(actor.getSnapshot().context.choice).toBeUndefined();
    });

    it('chooses the language the page is in when the user has not chosen one', async () => {
        const { actor, calls } = await loaded(NONE_CHOSEN_IN_FRENCH);

        expect(actor.getSnapshot().context.choice).toBe('fr_FR');
        // Update then saves it, which makes it the user's choice
        actor.send({ type: 'submit' });
        expect(calls.save[0]?.code).toBe('fr_FR');
    });

    it('fails, and loads again on reload', async () => {
        const running = start();
        running.calls.load[0]?.answer.reject(new ApiError('server', 500, 'boom'));
        await settled(running, (s) => s.matches('failed'));

        running.actor.send({ type: 'reload' });
        expect(running.actor.getSnapshot().value).toBe('loading');
        running.calls.load[1]?.answer.resolve(IN_ENGLISH);
        await settled(running, (s) => s.matches('ready'));
        expect(running.calls.load).toHaveLength(2);
    });

    it('uses a changed api for the next request', async () => {
        const running = await loaded();
        const api = new ApiClient({ contextPath: '/other' });

        running.actor.send({ type: 'api.changed', api });
        running.actor.send({ type: 'submit' });

        expect(running.calls.save[0]?.api).toBe(api);
    });
});

describe('user locale machine: choosing and saving', () => {
    it('remembers the chosen locale without saving it', async () => {
        const { actor, calls } = await loaded();

        actor.send({ type: 'choose', code: 'fr_FR' });

        expect(actor.getSnapshot().context.choice).toBe('fr_FR');
        expect(actor.getSnapshot().context.data.selected).toBe('en_US');
        expect(calls.save).toHaveLength(0);
    });

    it('ignores a locale the portal does not offer', async () => {
        const { actor } = await loaded();

        actor.send({ type: 'choose', code: 'de_DE' });

        expect(actor.getSnapshot().context.choice).toBe('en_US');
    });

    it('does not save without a choice', async () => {
        const { actor, calls } = await loaded(NONE_CHOSEN);

        actor.send({ type: 'submit' });

        expect(actor.getSnapshot().value).toEqual({ ready: 'idle' });
        expect(calls.save).toHaveLength(0);
    });

    it('saves the choice, then holds the new locales and reports the change', async () => {
        const running = await loaded();
        running.actor.send({ type: 'choose', code: 'fr_FR' });
        running.actor.send({ type: 'submit' });

        expect(running.actor.getSnapshot().value).toEqual({ ready: 'saving' });
        expect(running.actor.getSnapshot().hasTag('busy')).toBe(true);
        expect(running.calls.save[0]?.code).toBe('fr_FR');

        // Choosing again while the portal answers changes nothing
        running.actor.send({ type: 'choose', code: 'en_US' });
        expect(running.actor.getSnapshot().context.choice).toBe('fr_FR');

        running.calls.save[0]?.answer.resolve(IN_FRENCH);
        await settled(running, (s) => s.matches({ ready: 'saved' }));
        const snapshot = running.actor.getSnapshot();
        expect(snapshot.hasTag('busy')).toBe(true);
        expect(snapshot.context.data).toEqual(IN_FRENCH);
        expect(snapshot.context.choice).toBe('fr_FR');
        expect(running.emitted).toEqual([{ type: 'locale.changed', code: 'fr_FR', tag: 'fr-FR' }]);
    });

    it('reports the code as the tag when the answer lacks the locale', async () => {
        const running = await loaded();
        running.actor.send({ type: 'choose', code: 'fr_FR' });
        running.actor.send({ type: 'submit' });

        running.calls.save[0]?.answer.resolve({
            selected: null,
            current: null,
            locales: [ENGLISH],
        });
        await settled(running, (s) => s.matches({ ready: 'saved' }));

        expect(running.emitted).toEqual([{ type: 'locale.changed', code: 'fr_FR', tag: 'fr_FR' }]);
    });

    it.each([
        [new ApiError('forbidden', 403, 'stale token'), 'forbidden'],
        [new ApiError('network', undefined, 'offline'), 'network'],
        [new Error('not an api error'), 'malformed'],
    ] as const)(
        'keeps the saved locale and adds a notice when saving fails (%s)',
        async (error, reason) => {
            const running = await loaded();
            running.actor.send({ type: 'choose', code: 'fr_FR' });
            running.actor.send({ type: 'submit' });

            running.calls.save[0]?.answer.reject(error);
            await settled(running, (s) => s.matches({ ready: 'idle' }));

            const { context } = running.actor.getSnapshot();
            expect(context.notice).toEqual({ kind: 'save-failed', reason });
            expect(context.data.selected).toBe('en_US');
            expect(context.choice).toBe('fr_FR');
            expect(running.emitted).toEqual([]);
        },
    );

    it('clears the notice when the user chooses again or reloads', async () => {
        const running = await loaded();
        running.actor.send({ type: 'submit' });
        running.calls.save[0]?.answer.reject(new ApiError('server', 500, 'boom'));
        await settled(running, (s) => s.matches({ ready: 'idle' }));

        running.actor.send({ type: 'choose', code: 'fr_FR' });
        expect(running.actor.getSnapshot().context.notice).toBeUndefined();

        running.actor.send({ type: 'submit' });
        running.calls.save[1]?.answer.reject(new ApiError('server', 500, 'boom'));
        await settled(running, (s) => s.context.notice !== undefined);
        running.actor.send({ type: 'reload' });
        expect(running.actor.getSnapshot().context.notice).toBeUndefined();
    });
});

describe('errorKind', () => {
    it('reads the kind of an ApiError and treats anything else as malformed', () => {
        expect(errorKind(new ApiError('conflict', 409, 'x'))).toBe('conflict');
        expect(errorKind('nope')).toBe('malformed');
    });
});
