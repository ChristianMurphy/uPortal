/**
 * The states of `<up-self-edit-account>` (MOD-D31): loading the account, the account view, the edit
 * form and saving it, and the confirmation. Pure: the REST calls are the `loadAccount` and
 * `saveAccount` actors, which tests replace with `machine.provide({ actors })`.
 *
 * Passwords stay in the context only while the form is open: leaving the form, saving and a failed
 * or refused save all empty them. A password and a confirmation that differ are refused before
 * anything is sent.
 */
import { assign, emit, fromPromise, setup } from 'xstate';
import { ApiError, type ApiClient, type ApiErrorKind } from '../../lib/api-client';
import { fetchAccount, saveAccount, type AccountChanges, type AccountData } from './account-api';

/** A visible notice about the last save. */
export type Notice =
    | { kind: 'save-failed'; reason: ApiErrorKind }
    /** The password and its confirmation differ, so nothing was sent. */
    | { kind: 'passwords-differ' };

export type PasswordField = 'password' | 'confirmPassword';

export interface SelfEditAccountContext {
    api: ApiClient;
    account: AccountData;
    /** Why the account could not be loaded. */
    loadError: ApiErrorKind | undefined;
    /** The form's attribute values, by name. */
    draft: Record<string, string>;
    password: string;
    confirmPassword: string;
    notice: Notice | undefined;
}

export interface SelfEditAccountInput {
    api: ApiClient;
}

export type SelfEditAccountEvent =
    /** Load the account again (also how the user retries). */
    | { type: 'reload' }
    | { type: 'edit' }
    | { type: 'change'; name: string; value: string }
    | { type: 'password.change'; field: PasswordField; value: string }
    | { type: 'cancel' }
    | { type: 'submit' }
    /** The element's context path or CSRF token changed. */
    | { type: 'api.changed'; api: ApiClient };

export type SelfEditAccountEmitted = { type: 'account.updated'; passwordChanged: boolean };

const NO_ACCOUNT: AccountData = { username: '', details: [], canEdit: false, editable: [] };

const NO_PASSWORDS = { password: '', confirmPassword: '' };

export function errorKind(error: unknown): ApiErrorKind {
    return error instanceof ApiError ? error.kind : 'malformed';
}

export const selfEditAccountMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion, typescript/no-unsafe-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: SelfEditAccountContext;
        events: SelfEditAccountEvent;
        input: SelfEditAccountInput;
        emitted: SelfEditAccountEmitted;
        tags: 'busy';
    },
    actors: {
        loadAccount: fromPromise<AccountData, { api: ApiClient }>(({ input, signal }) =>
            fetchAccount(input.api, { signal }),
        ),
        saveAccount: fromPromise<void, { api: ApiClient; changes: AccountChanges }>(
            ({ input, signal }) => saveAccount(input.api, input.changes, { signal }),
        ),
    },
    guards: {
        canEdit: ({ context }) => context.account.canEdit,
        // As PersonFormValidator meant to: a password or a confirmation must match the other
        passwordsMatch: ({ context }) =>
            (context.password.trim() === '' && context.confirmPassword.trim() === '') ||
            context.password === context.confirmPassword,
        isEditable: ({ context }, { name }: { name: string }) =>
            context.account.editable.some((attribute) => attribute.name === name),
    },
    actions: {
        openForm: assign(({ context }) => ({
            draft: Object.fromEntries(
                context.account.editable.map((attribute) => [
                    attribute.name,
                    attribute.value ?? '',
                ]),
            ),
            ...NO_PASSWORDS,
            notice: undefined,
        })),
        closeForm: assign({ draft: {}, ...NO_PASSWORDS }),
    },
}).createMachine({
    id: 'selfEditAccount',
    context: ({ input }) => ({
        api: input.api,
        account: NO_ACCOUNT,
        loadError: undefined,
        draft: {},
        ...NO_PASSWORDS,
        notice: undefined,
    }),
    initial: 'loading',
    on: {
        // A fresh view: whatever the form held no longer applies
        reload: {
            target: '.loading',
            reenter: true,
            actions: ['closeForm', assign({ notice: undefined })],
        },
        'api.changed': { actions: assign({ api: ({ event }) => event.api }) },
    },
    states: {
        loading: {
            tags: ['busy'],
            invoke: {
                id: 'loadAccount',
                src: 'loadAccount',
                input: ({ context }) => ({ api: context.api }),
                onDone: {
                    target: 'viewing',
                    actions: assign({ account: ({ event }) => event.output, loadError: undefined }),
                },
                onError: {
                    target: 'failed',
                    actions: assign({ loadError: ({ event }) => errorKind(event.error) }),
                },
            },
        },
        failed: {},
        viewing: {
            on: {
                edit: { guard: 'canEdit', target: 'editing', actions: 'openForm' },
            },
        },
        editing: {
            initial: 'idle',
            states: {
                idle: {
                    on: {
                        change: {
                            guard: {
                                type: 'isEditable',
                                params: ({ event }) => ({ name: event.name }),
                            },
                            actions: assign({
                                draft: ({ context, event }) => ({
                                    ...context.draft,
                                    [event.name]: event.value,
                                }),
                            }),
                        },
                        'password.change': {
                            actions: assign(({ event }) => ({ [event.field]: event.value })),
                        },
                        cancel: { target: '#selfEditAccount.viewing', actions: 'closeForm' },
                        submit: [
                            { guard: 'passwordsMatch', target: 'saving' },
                            {
                                actions: assign({
                                    ...NO_PASSWORDS,
                                    notice: { kind: 'passwords-differ' as const },
                                }),
                            },
                        ],
                    },
                },
                saving: {
                    tags: ['busy'],
                    invoke: {
                        id: 'saveAccount',
                        src: 'saveAccount',
                        input: ({ context }) => ({
                            api: context.api,
                            changes: {
                                attributes: context.draft,
                                password: context.password,
                                confirmPassword: context.confirmPassword,
                            },
                        }),
                        onDone: {
                            target: '#selfEditAccount.saved',
                            actions: [
                                emit(({ context }) => ({
                                    type: 'account.updated' as const,
                                    passwordChanged: context.password.trim() !== '',
                                })),
                                'closeForm',
                                assign({ notice: undefined }),
                            ],
                        },
                        onError: {
                            target: 'idle',
                            actions: assign(({ event }) => ({
                                ...NO_PASSWORDS,
                                notice: {
                                    kind: 'save-failed' as const,
                                    reason: errorKind(event.error),
                                },
                            })),
                        },
                    },
                },
            },
        },
        saved: {},
    },
});

export type SelfEditAccountMachine = typeof selfEditAccountMachine;
