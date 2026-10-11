import { describe, expect, it } from 'vite-plus/test';
import { createActor, fromPromise, waitFor, type SnapshotFrom } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import type { LayoutReset } from './layout-reset-api';
import {
    errorKind,
    resetMyLayoutMachine,
    type ResetMyLayoutEmitted,
    type ResetMyLayoutMachine,
} from './up-reset-my-layout.machine';

const STUDENT: LayoutReset = { userName: 'student' };

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

type Call = { api: ApiClient; answer: ReturnType<typeof deferred<LayoutReset>> };

/**
 * Runs the machine with its REST actors replaced: each call is recorded and answered by the
 * test through the returned deferreds.
 */
function start(api = new ApiClient({ contextPath: '/uPortal' })) {
    const calls = { load: [] as Call[], reset: [] as Call[] };
    const record = (list: Call[]) =>
        fromPromise<LayoutReset, { api: ApiClient }>(({ input }) => {
            const answer = deferred<LayoutReset>();
            list.push({ api: input.api, answer });
            return answer.promise;
        });
    const machine: ResetMyLayoutMachine = resetMyLayoutMachine.provide({
        actors: { loadReset: record(calls.load), resetLayout: record(calls.reset) },
    });
    const actor = createActor(machine, { input: { api } });
    const emitted: ResetMyLayoutEmitted[] = [];
    actor.on('*', (event) => {
        emitted.push(event);
    });
    actor.start();
    return { actor, calls, emitted };
}

type Running = ReturnType<typeof start>;

function settled(running: Running, done: (s: SnapshotFrom<ResetMyLayoutMachine>) => boolean) {
    return waitFor(running.actor, done, { timeout: 1000 });
}

async function loaded(): Promise<Running> {
    const running = start();
    running.calls.load[0]?.answer.resolve(STUDENT);
    await settled(running, (s) => s.matches('ready'));
    return running;
}

async function confirming(): Promise<Running> {
    const running = await loaded();
    running.actor.send({ type: 'reset' });
    return running;
}

describe('reset my layout machine: loading', () => {
    it('starts loading, busy, with the api it was given', () => {
        const api = new ApiClient({ contextPath: '/x' });
        const { actor, calls } = start(api);

        expect(actor.getSnapshot().value).toBe('loading');
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);
        expect(calls.load[0]?.api).toBe(api);
    });

    it('starts at the explanation, knowing the user', async () => {
        const { actor } = await loaded();

        const snapshot = actor.getSnapshot();
        expect(snapshot.value).toEqual({ ready: 'start' });
        expect(snapshot.hasTag('busy')).toBe(false);
        expect(snapshot.context.userName).toBe('student');
        expect(snapshot.context.notice).toBeUndefined();
    });

    it.each([
        ['server', new ApiError('server', 500, 'boom')],
        ['network', new ApiError('network', undefined, 'offline')],
        ['malformed', new ApiError('malformed', 200, 'odd')],
        ['an error that is not an ApiError', new Error('bug')],
    ])('fails on %s, and loads again on reload', async (_name, error) => {
        const running = start();
        running.calls.load[0]?.answer.reject(error);
        await settled(running, (s) => s.matches('failed'));
        expect(running.actor.getSnapshot().hasTag('busy')).toBe(false);

        running.actor.send({ type: 'reload' });
        expect(running.actor.getSnapshot().value).toBe('loading');
        running.calls.load[1]?.answer.resolve(STUDENT);
        await settled(running, (s) => s.matches({ ready: 'start' }));
    });

    it.each([
        ['401', new ApiError('unauthorized', 401, 'guest')],
        ['403', new ApiError('forbidden', 403, 'no')],
    ])('is signed out when the portal answers %s', async (_name, error) => {
        const running = start();
        running.calls.load[0]?.answer.reject(error);

        await settled(running, (s) => s.matches('signedOut'));
        running.actor.send({ type: 'reload' });
        running.actor.send({ type: 'reset' });
        expect(running.actor.getSnapshot().value).toBe('signedOut');
        expect(running.calls.load).toHaveLength(1);
    });

    it('ignores the steps of a reset until loaded', () => {
        const { actor, calls } = start();

        for (const type of ['reset', 'confirm', 'cancel', 'continue'] as const) {
            actor.send({ type });
        }
        expect(actor.getSnapshot().value).toBe('loading');
        expect(calls.reset).toHaveLength(0);
    });
});

describe('reset my layout machine: confirming', () => {
    it('asks for confirmation on reset', async () => {
        const { actor, calls } = await confirming();

        expect(actor.getSnapshot().value).toEqual({ ready: 'confirming' });
        expect(actor.getSnapshot().hasTag('busy')).toBe(false);
        expect(calls.reset).toHaveLength(0);
    });

    it('goes back without resetting on cancel', async () => {
        const { actor, calls, emitted } = await confirming();

        actor.send({ type: 'cancel' });

        expect(actor.getSnapshot().value).toEqual({ ready: 'start' });
        expect(calls.reset).toHaveLength(0);
        expect(emitted).toEqual([]);
    });

    it('confirms only from the confirmation', async () => {
        const { actor, calls } = await loaded();

        actor.send({ type: 'confirm' });
        actor.send({ type: 'continue' });

        expect(actor.getSnapshot().value).toEqual({ ready: 'start' });
        expect(calls.reset).toHaveLength(0);
    });
});

describe('reset my layout machine: resetting', () => {
    it('resets on confirm, busy, with the current api', async () => {
        const running = await confirming();
        const api = new ApiClient({ contextPath: '/other' });
        running.actor.send({ type: 'api.changed', api });

        running.actor.send({ type: 'confirm' });

        expect(running.actor.getSnapshot().value).toEqual({ ready: 'resetting' });
        expect(running.actor.getSnapshot().hasTag('busy')).toBe(true);
        expect(running.calls.reset[0]?.api).toBe(api);
    });

    it('ignores a second confirm and cancel while resetting', async () => {
        const running = await confirming();
        running.actor.send({ type: 'confirm' });

        running.actor.send({ type: 'confirm' });
        running.actor.send({ type: 'cancel' });

        expect(running.actor.getSnapshot().value).toEqual({ ready: 'resetting' });
        expect(running.calls.reset).toHaveLength(1);
    });

    it('is done when the portal confirms, and says so once', async () => {
        const running = await confirming();
        running.actor.send({ type: 'confirm' });

        running.calls.reset[0]?.answer.resolve({ userName: 'student2' });
        await settled(running, (s) => s.matches({ ready: 'done' }));

        expect(running.actor.getSnapshot().context.userName).toBe('student2');
        expect(running.emitted).toEqual([{ type: 'layout.reset', userName: 'student2' }]);
    });

    it('starts over on continue', async () => {
        const running = await confirming();
        running.actor.send({ type: 'confirm' });
        running.calls.reset[0]?.answer.resolve(STUDENT);
        await settled(running, (s) => s.matches({ ready: 'done' }));

        running.actor.send({ type: 'continue' });

        expect(running.actor.getSnapshot().value).toEqual({ ready: 'start' });
    });

    it.each([
        ['server', new ApiError('server', 500, 'boom')],
        ['network', new ApiError('network', undefined, 'offline')],
        ['malformed', new Error('bug')],
    ])(
        'goes back to the confirmation with a %s notice when the reset fails',
        async (reason, error) => {
            const running = await confirming();
            running.actor.send({ type: 'confirm' });

            running.calls.reset[0]?.answer.reject(error);
            await settled(running, (s) => s.matches({ ready: 'confirming' }));

            expect(running.actor.getSnapshot().context.notice).toEqual({
                kind: 'reset-failed',
                reason,
            });
            expect(running.emitted).toEqual([]);
        },
    );

    it.each(['confirm', 'cancel'] as const)('clears the notice on %s', async (type) => {
        const running = await confirming();
        running.actor.send({ type: 'confirm' });
        running.calls.reset[0]?.answer.reject(new ApiError('server', 500, 'boom'));
        await settled(running, (s) => s.matches({ ready: 'confirming' }));

        running.actor.send({ type });

        expect(running.actor.getSnapshot().context.notice).toBeUndefined();
    });

    it.each([
        ['401', new ApiError('unauthorized', 401, 'expired')],
        ['403, a refused CSRF token', new ApiError('forbidden', 403, 'csrf')],
    ])('is signed out when the reset answers %s', async (_name, error) => {
        const running = await confirming();
        running.actor.send({ type: 'confirm' });

        running.calls.reset[0]?.answer.reject(error);

        await settled(running, (s) => s.matches('signedOut'));
        expect(running.emitted).toEqual([]);
    });
});

describe('errorKind', () => {
    it('is the kind of an ApiError, else malformed', () => {
        expect(errorKind(new ApiError('conflict', 409, 'x'))).toBe('conflict');
        expect(errorKind('boom')).toBe('malformed');
    });
});
