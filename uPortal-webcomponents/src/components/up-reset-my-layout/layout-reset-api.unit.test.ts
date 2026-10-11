import { describe, expect, it, vi } from 'vite-plus/test';
import { ApiClient, ApiError } from '../../lib/api-client';
import { fetchLayoutReset, resetLayout, RESET_PATH, validateLayoutReset } from './layout-reset-api';

function client(respond: () => Response) {
    const fetch = vi.fn<typeof globalThis.fetch>(() => Promise.resolve(respond()));
    const api = new ApiClient({
        contextPath: '/uPortal',
        csrfHeader: 'X-CSRF-TOKEN',
        csrfToken: 't',
        fetch,
    });
    return { api, fetch };
}

function json(value: unknown, status = 200): Response {
    return new Response(JSON.stringify(value), { status });
}

describe('validateLayoutReset', () => {
    it('accepts the API body, keeping only the user name', () => {
        expect(validateLayoutReset({ userName: 'student', other: 1 })).toEqual({
            userName: 'student',
        });
    });

    it.each([
        ['a body that is not an object', ['student'], 'the body is not an object'],
        ['null', null, 'the body is not an object'],
        ['a body without a user name', {}, 'userName is not a non-empty string'],
        ['a blank user name', { userName: ' ' }, 'userName is not a non-empty string'],
        ['a numeric user name', { userName: 7 }, 'userName is not a non-empty string'],
    ])('refuses %s', (_name, value, message) => {
        expect(() => validateLayoutReset(value)).toThrow(message);
    });
});

describe('fetchLayoutReset', () => {
    it('GETs whose layout a reset acts on, without the CSRF token', async () => {
        const { api, fetch } = client(() => json({ userName: 'student' }));

        await expect(fetchLayoutReset(api)).resolves.toEqual({ userName: 'student' });
        expect(fetch).toHaveBeenCalledWith(
            `/uPortal${RESET_PATH}`,
            expect.objectContaining({ method: 'GET', headers: { Accept: 'application/json' } }),
        );
    });

    it('passes the abort signal on', async () => {
        const { api, fetch } = client(() => json({ userName: 'student' }));
        const { signal } = new AbortController();

        await fetchLayoutReset(api, { signal });
        expect(fetch).toHaveBeenCalledWith(
            `/uPortal${RESET_PATH}`,
            expect.objectContaining({ signal }),
        );
    });
});

describe('resetLayout', () => {
    it('POSTs no body, with the CSRF token, and resolves to whose layout it was', async () => {
        const { api, fetch } = client(() => json({ userName: 'student' }));

        await expect(resetLayout(api)).resolves.toEqual({ userName: 'student' });
        const [url, init] = fetch.mock.calls[0] ?? [];
        expect(url).toBe(`/uPortal${RESET_PATH}`);
        expect(init?.method).toBe('POST');
        expect(init?.headers).toEqual({ Accept: 'application/json', 'X-CSRF-TOKEN': 't' });
        expect(init?.body).toBeUndefined();
    });

    it('treats an empty answer as malformed', async () => {
        const { api } = client(() => new Response(null, { status: 204 }));

        await expect(resetLayout(api)).rejects.toMatchObject({ kind: 'malformed', status: 204 });
    });

    it.each([
        [401, 'unauthorized'],
        [403, 'forbidden'],
        [500, 'server'],
    ])('reports %i as %s', async (status, kind) => {
        const { api } = client(() => json({ message: 'no' }, status));

        const error: unknown = await resetLayout(api).catch((e: unknown) => e);
        expect(error).toBeInstanceOf(ApiError);
        expect(error).toMatchObject({ kind, status });
    });
});
