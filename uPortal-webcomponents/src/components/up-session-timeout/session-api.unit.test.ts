import { describe, expect, it } from 'vite-plus/test';
import { ApiClient, ApiError } from '../../lib/api-client';
import {
    extendSession,
    fetchSession,
    KEEP_ALIVE_PATH,
    SESSION_PATH,
    validateSession,
} from './session-api';

function client(response: Response, requests: { url: string; init: RequestInit }[] = []) {
    return new ApiClient({
        contextPath: '/uPortal',
        csrfHeader: 'X-CSRF-TOKEN',
        csrfToken: 'token-1',
        fetch: (input, init = {}) => {
            requests.push({
                url:
                    typeof input === 'string'
                        ? input
                        : input instanceof URL
                          ? input.href
                          : input.url,
                init,
            });
            return Promise.resolve(response);
        },
    });
}

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status });
}

describe('validateSession', () => {
    it('accepts an integer timeout, including one that never expires', () => {
        expect(validateSession({ maxInactiveIntervalSeconds: 1800 })).toEqual({
            maxInactiveIntervalSeconds: 1800,
        });
        expect(validateSession({ maxInactiveIntervalSeconds: -1 })).toEqual({
            maxInactiveIntervalSeconds: -1,
        });
    });

    it.each([
        [null],
        [[]],
        ['1800'],
        [{}],
        [{ maxInactiveIntervalSeconds: '1800' }],
        [{ maxInactiveIntervalSeconds: 1.5 }],
    ])('refuses %j', (body) => {
        expect(() => validateSession(body)).toThrow(TypeError);
    });
});

describe('fetchSession', () => {
    it('GETs the session', async () => {
        const requests: { url: string; init: RequestInit }[] = [];

        const session = await fetchSession(
            client(json({ maxInactiveIntervalSeconds: 900 }), requests),
        );

        expect(session).toEqual({ maxInactiveIntervalSeconds: 900 });
        expect(requests[0]?.url).toBe(`/uPortal${SESSION_PATH}`);
        expect(requests[0]?.init.method).toBe('GET');
    });
});

describe('extendSession', () => {
    it('POSTs the keep-alive with the CSRF token', async () => {
        const requests: { url: string; init: RequestInit }[] = [];

        const session = await extendSession(
            client(json({ maxInactiveIntervalSeconds: 1800 }), requests),
        );

        expect(session).toEqual({ maxInactiveIntervalSeconds: 1800 });
        expect(requests[0]?.url).toBe(`/uPortal${KEEP_ALIVE_PATH}`);
        expect(requests[0]?.init.method).toBe('POST');
        expect(requests[0]?.init.headers).toMatchObject({ 'X-CSRF-TOKEN': 'token-1' });
    });

    it('refuses an answer without a body', async () => {
        await expect(extendSession(client(new Response(null, { status: 204 })))).rejects.toEqual(
            new ApiError('malformed', 204, `POST ${KEEP_ALIVE_PATH} returned no body`),
        );
    });

    it('reports a malformed body', async () => {
        await expect(
            extendSession(client(json({ maxInactiveIntervalSeconds: 'later' }))),
        ).rejects.toMatchObject({ kind: 'malformed' });
    });
});
