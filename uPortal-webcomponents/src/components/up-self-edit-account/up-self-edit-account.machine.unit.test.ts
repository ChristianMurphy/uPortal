import { describe, expect, it } from 'vite-plus/test';
import { createActor, fromPromise, waitFor } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import type { AccountChanges, AccountData } from './account-api';
import {
    errorKind,
    selfEditAccountMachine,
    type SelfEditAccountEmitted,
    type SelfEditAccountMachine,
} from './up-self-edit-account.machine';

const EDITOR: AccountData = {
    username: 'selma',
    details: [{ name: 'department', label: 'Department', value: 'Biology' }],
    canEdit: true,
    editable: [
        { name: 'department', label: 'Department', value: 'Biology' },
        { name: 'title', label: 'Title', value: null },
    ],
};

const VIEWER: AccountData = { ...EDITOR, canEdit: false, editable: [] };

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
 * Runs the machine with its REST actors replaced: each call is recorded and answered by the test
 * through the returned deferreds.
 */
function start(api = new ApiClient({ contextPath: '/uPortal' })) {
    const calls = {
        load: [] as { api: ApiClient; answer: ReturnType<typeof deferred<AccountData>> }[],
        save: [] as {
            api: ApiClient;
            changes: AccountChanges;
            answer: ReturnType<typeof deferred<undefined>>;
        }[],
    };
    const machine: SelfEditAccountMachine = selfEditAccountMachine.provide({
        actors: {
            loadAccount: fromPromise<AccountData, { api: ApiClient }>(({ input }) => {
                const answer = deferred<AccountData>();
                calls.load.push({ api: input.api, answer });
                return answer.promise;
            }),
            saveAccount: fromPromise<void, { api: ApiClient; changes: AccountChanges }>(
                ({ input }) => {
                    const answer = deferred<undefined>();
                    calls.save.push({ api: input.api, changes: input.changes, answer });
                    return answer.promise;
                },
            ),
        },
    });
    const emitted: SelfEditAccountEmitted[] = [];
    const actor = createActor(machine, { input: { api } });
    actor.on('account.updated', (event) => emitted.push(event));
    actor.start();
    return { actor, calls, emitted };
}

async function loaded(account: AccountData = EDITOR) {
    const run = start();
    run.calls.load[0]?.answer.resolve(account);
    await waitFor(run.actor, (snapshot) => snapshot.matches('viewing'));
    return run;
}

async function editing() {
    const run = await loaded();
    run.actor.send({ type: 'edit' });
    return run;
}

describe('selfEditAccountMachine', () => {
    it('starts by loading the account, busy', () => {
        const { actor, calls } = start();

        expect(actor.getSnapshot().matches('loading')).toBe(true);
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);
        expect(calls.load).toHaveLength(1);
    });

    it('shows the account once it is loaded', async () => {
        const { actor } = await loaded();

        expect(actor.getSnapshot().context.account).toEqual(EDITOR);
        expect(actor.getSnapshot().hasTag('busy')).toBe(false);
    });

    it.each([
        ['server', new ApiError('server', 500, 'boom')],
        ['unauthorized', new ApiError('unauthorized', 401, 'expired')],
        ['malformed', new TypeError('not an account')],
    ])('fails with the reason %s', async (reason, error) => {
        const { actor, calls } = start();
        calls.load[0]?.answer.reject(error);
        await waitFor(actor, (snapshot) => snapshot.matches('failed'));

        expect(actor.getSnapshot().context.loadError).toBe(reason);
    });

    it('loads again on reload, forgetting the failure once it works', async () => {
        const { actor, calls } = start();
        calls.load[0]?.answer.reject(new ApiError('server', 500, 'boom'));
        await waitFor(actor, (snapshot) => snapshot.matches('failed'));

        actor.send({ type: 'reload' });
        expect(actor.getSnapshot().matches('loading')).toBe(true);
        calls.load[1]?.answer.resolve(VIEWER);
        await waitFor(actor, (snapshot) => snapshot.matches('viewing'));
        expect(actor.getSnapshot().context.loadError).toBeUndefined();
    });

    it('does not open the form for a user who may not edit', async () => {
        const { actor } = await loaded(VIEWER);

        expect(actor.getSnapshot().can({ type: 'edit' })).toBe(false);
        actor.send({ type: 'edit' });
        expect(actor.getSnapshot().matches('viewing')).toBe(true);
    });

    it('opens the form with the stored values and empty passwords', async () => {
        const { actor } = await editing();

        const { context } = actor.getSnapshot();
        expect(actor.getSnapshot().matches({ editing: 'idle' })).toBe(true);
        expect(context.draft).toEqual({ department: 'Biology', title: '' });
        expect(context.password).toBe('');
        expect(context.confirmPassword).toBe('');
    });

    it('changes editable attributes only', async () => {
        const { actor } = await editing();

        actor.send({ type: 'change', name: 'department', value: 'Physics' });
        expect(actor.getSnapshot().can({ type: 'change', name: 'sn', value: 'X' })).toBe(false);
        actor.send({ type: 'change', name: 'sn', value: 'Somebody Else' });
        expect(actor.getSnapshot().context.draft).toEqual({ department: 'Physics', title: '' });
    });

    it('holds the passwords while the form is open and empties them on cancel', async () => {
        const { actor } = await editing();
        actor.send({ type: 'password.change', field: 'password', value: 'secret' });
        actor.send({ type: 'password.change', field: 'confirmPassword', value: 'secret' });
        expect(actor.getSnapshot().context.password).toBe('secret');
        expect(actor.getSnapshot().context.confirmPassword).toBe('secret');

        actor.send({ type: 'cancel' });
        expect(actor.getSnapshot().matches('viewing')).toBe(true);
        expect(actor.getSnapshot().context.password).toBe('');
        expect(actor.getSnapshot().context.confirmPassword).toBe('');
        expect(actor.getSnapshot().context.draft).toEqual({});
    });

    it('saves every editable attribute and the passwords, then confirms', async () => {
        const { actor, calls, emitted } = await editing();
        actor.send({ type: 'change', name: 'title', value: 'Dr' });
        actor.send({ type: 'password.change', field: 'password', value: 'secret' });
        actor.send({ type: 'password.change', field: 'confirmPassword', value: 'secret' });
        actor.send({ type: 'submit' });

        expect(actor.getSnapshot().matches({ editing: 'saving' })).toBe(true);
        expect(actor.getSnapshot().hasTag('busy')).toBe(true);
        expect(calls.save[0]?.changes).toEqual({
            attributes: { department: 'Biology', title: 'Dr' },
            password: 'secret',
            confirmPassword: 'secret',
        });

        calls.save[0]?.answer.resolve(undefined);
        await waitFor(actor, (snapshot) => snapshot.matches('saved'));
        expect(emitted).toEqual([{ type: 'account.updated', passwordChanged: true }]);
        expect(actor.getSnapshot().context.password).toBe('');
        expect(actor.getSnapshot().context.confirmPassword).toBe('');
    });

    it('reports a save without a password as such', async () => {
        const { actor, calls, emitted } = await editing();
        actor.send({ type: 'submit' });
        calls.save[0]?.answer.resolve(undefined);
        await waitFor(actor, (snapshot) => snapshot.matches('saved'));

        expect(emitted).toEqual([{ type: 'account.updated', passwordChanged: false }]);
    });

    it('ignores changes while saving', async () => {
        const { actor } = await editing();
        actor.send({ type: 'submit' });

        expect(actor.getSnapshot().can({ type: 'cancel' })).toBe(false);
        expect(actor.getSnapshot().can({ type: 'submit' })).toBe(false);
        expect(actor.getSnapshot().can({ type: 'change', name: 'title', value: 'X' })).toBe(false);
    });

    it.each([
        ['forbidden', new ApiError('forbidden', 403, 'no')],
        ['unauthorized', new ApiError('unauthorized', 401, 'expired')],
        ['network', new ApiError('network', undefined, 'offline')],
    ])(
        'keeps the form after a %s failure, with a notice and no passwords',
        async (reason, error) => {
            const { actor, calls, emitted } = await editing();
            actor.send({ type: 'change', name: 'title', value: 'Dr' });
            actor.send({ type: 'password.change', field: 'password', value: 'secret' });
            actor.send({ type: 'submit' });
            calls.save[0]?.answer.reject(error);
            await waitFor(actor, (snapshot) => snapshot.matches({ editing: 'idle' }));

            const { context } = actor.getSnapshot();
            expect(context.notice).toEqual({ kind: 'save-failed', reason });
            expect(context.draft['title']).toBe('Dr');
            expect(context.password).toBe('');
            expect(emitted).toEqual([]);
        },
    );

    it('clears the notice when the form opens again', async () => {
        const { actor, calls } = await editing();
        actor.send({ type: 'submit' });
        calls.save[0]?.answer.reject(new ApiError('server', 500, 'boom'));
        await waitFor(actor, (snapshot) => snapshot.matches({ editing: 'idle' }));
        actor.send({ type: 'cancel' });
        actor.send({ type: 'edit' });

        expect(actor.getSnapshot().context.notice).toBeUndefined();
    });

    it('reload leaves the form and forgets the passwords', async () => {
        const { actor, calls } = await editing();
        actor.send({ type: 'password.change', field: 'password', value: 'secret' });
        actor.send({ type: 'reload' });

        expect(actor.getSnapshot().matches('loading')).toBe(true);
        expect(actor.getSnapshot().context.password).toBe('');
        calls.load[1]?.answer.resolve(EDITOR);
        await waitFor(actor, (snapshot) => snapshot.matches('viewing'));
    });

    it('uses the new API client after api.changed', async () => {
        const { actor, calls } = await editing();
        const api = new ApiClient({ contextPath: '/portal', csrfToken: 'token-2' });
        actor.send({ type: 'api.changed', api });
        actor.send({ type: 'submit' });

        expect(calls.save[0]?.api).toBe(api);
    });
});

describe('errorKind', () => {
    it('reads the kind of an ApiError and calls anything else malformed', () => {
        expect(errorKind(new ApiError('conflict', 409, 'x'))).toBe('conflict');
        expect(errorKind(new Error('x'))).toBe('malformed');
    });
});
