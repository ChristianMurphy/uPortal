import { afterEach, describe, expect, it, vi } from 'vite-plus/test';
import { ApiClient } from './api-client';
import {
    clearMessageCache,
    formatMessage,
    loadMessages,
    MESSAGES_PATH,
    validateMessages,
} from './i18n';

function stubbedClient(...responses: (Response | Error)[]) {
    const fetchMock = vi.fn((_url: string) => {
        const next = responses.shift() ?? new Error('no more responses');
        return next instanceof Error ? Promise.reject(next) : Promise.resolve(next);
    });
    return {
        api: new ApiClient({
            contextPath: '/uPortal',
            fetch: fetchMock as unknown as typeof fetch,
        }),
        fetchMock,
    };
}

afterEach(() => {
    clearMessageCache();
});

describe('formatMessage', () => {
    it('fills numbered placeholders', () => {
        expect(formatMessage('You have added {0} as a favorite.', ['Calendar'])).toBe(
            'You have added Calendar as a favorite.',
        );
        expect(formatMessage('{1} before {0}, {0} again', ['a', 2])).toBe('2 before a, a again');
    });

    it('keeps placeholders it has no argument for', () => {
        expect(formatMessage('Move {0} to {1}', ['Calendar'])).toBe('Move Calendar to {1}');
        expect(formatMessage('No arguments {0}')).toBe('No arguments {0}');
    });

    it('leaves other braces and quotes alone', () => {
        expect(formatMessage("It's {name} {", ['x'])).toBe("It's {name} {");
    });
});

describe('validateMessages', () => {
    it('keeps string messages', () => {
        expect(validateMessages({ locale: 'fr', messages: { a: 'A', b: 7, c: null } })).toEqual({
            a: 'A',
        });
    });

    it.each([null, 'text', 3, {}, { messages: null }, { messages: ['a'] }, { messages: 'a' }])(
        'refuses %j',
        (body) => {
            expect(() => validateMessages(body)).toThrow(TypeError);
        },
    );
});

describe('loadMessages', () => {
    it('asks the portal for the keys in the locale', async () => {
        const { api, fetchMock } = stubbedClient(
            Response.json({ locale: 'fr-CA', messages: { b: 'Bé', a: 'À' } }),
        );

        await expect(loadMessages(api, 'fr-CA', ['b', 'a'])).resolves.toEqual({ a: 'À', b: 'Bé' });

        expect(fetchMock).toHaveBeenCalledWith(
            `/uPortal${MESSAGES_PATH}?locale=fr-CA&keys=a%2Cb`,
            expect.objectContaining({ method: 'GET' }),
        );
    });

    it('asks once per locale and set of keys, however many components ask', async () => {
        const { api, fetchMock } = stubbedClient(
            Response.json({ messages: { a: 'A' } }),
            Response.json({ messages: { a: 'Ä' } }),
        );

        const [first, second] = await Promise.all([
            loadMessages(api, 'de', ['a']),
            loadMessages(api, 'de', ['a']),
        ]);
        const english = await loadMessages(api, 'en', ['a']);

        expect(first).toEqual({ a: 'A' });
        expect(second).toBe(first);
        expect(english).toEqual({ a: 'Ä' });
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it('resolves to no messages when loading fails, and tries again later', async () => {
        const { api, fetchMock } = stubbedClient(
            new Response('', { status: 500 }),
            Response.json({ messages: { a: 'A' } }),
        );

        await expect(loadMessages(api, 'en', ['a'])).resolves.toEqual({});
        await expect(loadMessages(api, 'en', ['a'])).resolves.toEqual({ a: 'A' });
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });
});
