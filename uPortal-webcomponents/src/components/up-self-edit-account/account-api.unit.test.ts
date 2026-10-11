import { describe, expect, it } from 'vite-plus/test';
import { ApiClient, ApiError } from '../../lib/api-client';
import {
    ACCOUNT_PATH,
    fetchAccount,
    saveAccount,
    validateAccount,
    type AccountData,
} from './account-api';

const ACCOUNT: AccountData = {
    username: 'student',
    details: [
        { name: 'title', label: 'Title', value: null },
        { name: 'mail', label: 'Email', value: 'student@example.org' },
    ],
    canEdit: true,
    editable: [{ name: 'department', label: 'Department', value: 'Biology' }],
};

function respond(response: Response) {
    const requests: { url: string; init: RequestInit }[] = [];
    const api = new ApiClient({
        contextPath: '/uPortal',
        csrfHeader: 'X-CSRF-TOKEN',
        csrfToken: 'token-1',
        fetch: (input, init = {}) => {
            requests.push({ url: typeof input === 'string' ? input : 'not a string', init });
            return Promise.resolve(response);
        },
    });
    return { api, requests };
}

describe('validateAccount', () => {
    it('accepts an account', () => {
        expect(validateAccount(ACCOUNT)).toEqual(ACCOUNT);
    });

    it('reads a missing value as null', () => {
        const body = { ...ACCOUNT, details: [{ name: 'title', label: 'Title' }] };
        expect(validateAccount(body).details).toEqual([
            { name: 'title', label: 'Title', value: null },
        ]);
    });

    it.each([
        ['a non-object body', null, 'the body is not an object'],
        ['an array body', [], 'the body is not an object'],
        ['a missing canEdit', { ...ACCOUNT, canEdit: 'yes' }, 'canEdit is not a boolean'],
        ['a missing username', { ...ACCOUNT, username: '' }, 'username is not a non-empty string'],
        ['details that are not a list', { ...ACCOUNT, details: {} }, 'details is not an array'],
        [
            'an attribute that is not an object',
            { ...ACCOUNT, details: [1] },
            'an attribute is not an object',
        ],
        [
            'an attribute without a label',
            { ...ACCOUNT, details: [{ name: 'title', value: null }] },
            'label is not a non-empty string',
        ],
        [
            'a value that is not a string',
            { ...ACCOUNT, details: [{ name: 'title', label: 'Title', value: 3 }] },
            'value is not a string or null',
        ],
        [
            'editable attributes for a user who may not edit',
            { ...ACCOUNT, canEdit: false },
            'editable attributes without canEdit',
        ],
    ])('refuses %s', (_name, body, message) => {
        expect(() => validateAccount(body)).toThrow(message);
    });
});

describe('fetchAccount', () => {
    it('gets the account', async () => {
        const { api, requests } = respond(new Response(JSON.stringify(ACCOUNT), { status: 200 }));

        await expect(fetchAccount(api)).resolves.toEqual(ACCOUNT);
        expect(requests[0]?.url).toBe(`/uPortal${ACCOUNT_PATH}`);
        expect(requests[0]?.init.method).toBe('GET');
    });

    it('reports a body it cannot use as malformed', async () => {
        const { api } = respond(new Response('{"username":"x"}', { status: 200 }));

        await expect(fetchAccount(api)).rejects.toMatchObject({ kind: 'malformed' });
    });
});

describe('saveAccount', () => {
    it('puts the changes with the CSRF token', async () => {
        const { api, requests } = respond(new Response(null, { status: 204 }));
        const changes = {
            attributes: { department: 'Physics' },
            password: 'secret',
            confirmPassword: 'secret',
            currentPassword: 'old-secret',
        };

        await expect(saveAccount(api, changes)).resolves.toBeUndefined();
        const [request] = requests;
        expect(request?.url).toBe(`/uPortal${ACCOUNT_PATH}`);
        expect(request?.init.method).toBe('PUT');
        expect(request?.init.headers).toMatchObject({ 'X-CSRF-TOKEN': 'token-1' });
        expect(request?.init.body).toBe(JSON.stringify(changes));
    });

    it('fails with the kind of the refusal', async () => {
        const { api } = respond(new Response('{}', { status: 403 }));

        const failure = saveAccount(api, {
            attributes: {},
            password: '',
            confirmPassword: '',
            currentPassword: '',
        });
        await expect(failure).rejects.toBeInstanceOf(ApiError);
        await expect(failure).rejects.toMatchObject({ kind: 'forbidden' });
    });
});
