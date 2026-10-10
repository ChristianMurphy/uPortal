import { describe, expect, it, vi } from 'vite-plus/test';
import { ApiClient, ApiError, errorKindForStatus, type ApiErrorKind } from './api-client';

type FetchArgs = [string, RequestInit];

function client(response: Response | Error, options: { csrf?: boolean } = {}) {
    const fetchMock = vi.fn((..._args: FetchArgs) =>
        response instanceof Error ? Promise.reject(response) : Promise.resolve(response),
    );
    const api = new ApiClient({
        contextPath: '/uPortal',
        fetch: fetchMock as unknown as typeof fetch,
        ...(options.csrf ? { csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'token-1' } : {}),
    });
    return { api, fetchMock };
}

const asIs = (body: unknown) => body;

async function failure(promise: Promise<unknown>): Promise<ApiError> {
    const error = await promise.then(
        () => undefined,
        (e: unknown) => e,
    );
    expect(error).toBeInstanceOf(ApiError);
    return error as ApiError;
}

describe('ApiClient', () => {
    it('GETs JSON from the context path with the session cookie', async () => {
        const { api, fetchMock } = client(Response.json({ a: 1 }), { csrf: true });

        await expect(api.get('/api/v6-0/things', asIs)).resolves.toEqual({ a: 1 });

        const [url, init] = fetchMock.mock.calls[0] as FetchArgs;
        expect(url).toBe('/uPortal/api/v6-0/things');
        expect(init.method).toBe('GET');
        expect(init.credentials).toBe('same-origin');
        // A safe method never carries the CSRF token
        expect(init.headers).toEqual({ Accept: 'application/json' });
        expect(init.body).toBeUndefined();
    });

    it('sends unsafe requests with a JSON body and the CSRF token', async () => {
        const { api, fetchMock } = client(Response.json({ id: 'n1' }, { status: 201 }), {
            csrf: true,
        });

        await expect(api.send('POST', '/api/v6-0/things', { fname: 'x' }, asIs)).resolves.toEqual({
            id: 'n1',
        });

        const [, init] = fetchMock.mock.calls[0] as FetchArgs;
        expect(init.method).toBe('POST');
        expect(init.headers).toEqual({
            Accept: 'application/json',
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': 'token-1',
        });
        expect(init.body).toBe('{"fname":"x"}');
    });

    it('sends no CSRF header when the portal gave no token', async () => {
        const { api, fetchMock } = client(new Response(null, { status: 204 }));

        await expect(api.send('DELETE', '/api/v6-0/things/1')).resolves.toBeUndefined();

        const [, init] = fetchMock.mock.calls[0] as FetchArgs;
        expect(init.headers).toEqual({ Accept: 'application/json' });
    });

    it('resolves to undefined for 204 even with a validator', async () => {
        const { api } = client(new Response(null, { status: 204 }), { csrf: true });

        await expect(api.send('PUT', '/api/x', {}, asIs)).resolves.toBeUndefined();
    });

    it('ignores a body when no validator is given', async () => {
        const { api } = client(Response.json({ ignored: true }), { csrf: true });

        await expect(api.send('POST', '/api/x')).resolves.toBeUndefined();
    });

    it.each<[number, ApiErrorKind]>([
        [401, 'unauthorized'],
        [403, 'forbidden'],
        [404, 'not-found'],
        [409, 'conflict'],
        [500, 'server'],
        [503, 'server'],
        [418, 'http'],
    ])('reports status %i as %s', async (status, kind) => {
        const { api } = client(new Response('nope', { status }));

        const error = await failure(api.get('/api/x', asIs));

        expect(error.kind).toBe(kind);
        expect(error.status).toBe(status);
        expect(error.name).toBe('ApiError');
        expect(error.message).toBe(`GET /api/x answered ${status}`);
    });

    it('reports a failed fetch as a network error', async () => {
        const { api } = client(new TypeError('Failed to fetch'));

        const error = await failure(api.get('/api/x', asIs));

        expect(error.kind).toBe('network');
        expect(error.status).toBeUndefined();
        expect(error.message).toContain('Failed to fetch');
    });

    it('reports a body that is not JSON as malformed', async () => {
        const { api } = client(new Response('<html>login</html>', { status: 200 }));

        const error = await failure(api.get('/api/x', asIs));

        expect(error.kind).toBe('malformed');
        expect(error.message).toBe('GET /api/x did not return JSON');
    });

    it('reports a GET without a body as malformed', async () => {
        const { api } = client(new Response('', { status: 200 }));

        const error = await failure(api.get('/api/x', asIs));

        expect(error.kind).toBe('malformed');
        expect(error.message).toBe('GET /api/x did not return JSON');
    });

    it('reports a body the validator refuses as malformed', async () => {
        const { api } = client(Response.json({ items: 'nope' }));

        const error = await failure(
            api.get('/api/x', () => {
                throw new TypeError('items is not an array');
            }),
        );

        expect(error.kind).toBe('malformed');
        expect(error.message).toContain('items is not an array');
    });

    it('uses the global fetch by default', async () => {
        const fetchMock = vi.fn(() => Promise.resolve(Response.json([1])));
        vi.stubGlobal('fetch', fetchMock);

        await expect(new ApiClient({ contextPath: '' }).get('/api/x', asIs)).resolves.toEqual([1]);
        expect(fetchMock).toHaveBeenCalledOnce();
    });

    it('maps statuses to error kinds', () => {
        expect(errorKindForStatus(400)).toBe('http');
        expect(errorKindForStatus(599)).toBe('server');
    });
});
