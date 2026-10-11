import { describe, expect, it } from 'vite-plus/test';
import { ApiClient, ApiError } from '../../lib/api-client';
import {
    fetchPopularPortlets,
    POPULAR_PORTLETS_PATH,
    safeUrl,
    validatePopularPortlets,
} from './popular-portlets-api';

const CALENDAR = {
    fname: 'calendar',
    title: 'Calendar',
    description: 'Your events',
    url: '/uPortal/p/calendar',
    count: 3,
};

describe('validatePopularPortlets', () => {
    it('accepts the API body', () => {
        expect(validatePopularPortlets({ days: 7, portlets: [CALENDAR] })).toEqual({
            days: 7,
            portlets: [CALENDAR],
        });
    });

    it('turns a missing or blank description into null', () => {
        const { portlets } = validatePopularPortlets({
            days: 30,
            portlets: [
                { ...CALENDAR, description: null },
                { ...CALENDAR, fname: 'mail', description: '  ' },
                { fname: 'news', title: 'News', url: '/uPortal/p/news', count: 1 },
            ],
        });
        expect(portlets.map((portlet) => portlet.description)).toEqual([null, null, null]);
    });

    it('drops links that are neither site paths nor http(s) URLs', () => {
        const { portlets } = validatePopularPortlets({
            days: 30,
            portlets: [{ ...CALENDAR, url: 'javascript:alert(1)' }],
        });
        expect(portlets[0]?.url).toBeNull();
    });

    it.each([
        ['a list', []],
        ['null', null],
        ['no portlets', { days: 30 }],
        ['portlets that are no list', { days: 30, portlets: {} }],
        ['no days', { portlets: [] }],
        ['fractional days', { days: 1.5, portlets: [] }],
        ['a portlet that is no object', { days: 30, portlets: ['calendar'] }],
        ['an empty fname', { days: 30, portlets: [{ ...CALENDAR, fname: '' }] }],
        ['a missing title', { days: 30, portlets: [{ ...CALENDAR, title: undefined }] }],
        [
            'a description that is no string',
            { days: 30, portlets: [{ ...CALENDAR, description: 1 }] },
        ],
        ['a negative count', { days: 30, portlets: [{ ...CALENDAR, count: -1 }] }],
        ['a count that is a string', { days: 30, portlets: [{ ...CALENDAR, count: '3' }] }],
    ])('refuses %s', (_name, body) => {
        expect(() => validatePopularPortlets(body)).toThrow(TypeError);
    });
});

describe('safeUrl', () => {
    it.each([
        ['/uPortal/p/calendar', '/uPortal/p/calendar'],
        [' https://example.edu/x ', 'https://example.edu/x'],
        ['//evil.example', null],
        ['data:text/html,x', null],
        [null, null],
    ])('%s -> %s', (value, expected) => {
        expect(safeUrl(value)).toBe(expected);
    });
});

describe('fetchPopularPortlets', () => {
    it('asks for the days and locale and validates the answer', async () => {
        const urls: string[] = [];
        const api = new ApiClient({
            contextPath: '/uPortal',
            fetch: (input) => {
                urls.push(
                    typeof input === 'string'
                        ? input
                        : input instanceof URL
                          ? input.href
                          : input.url,
                );
                return Promise.resolve(
                    new Response(JSON.stringify({ days: 90, portlets: [CALENDAR] }), {
                        status: 200,
                    }),
                );
            },
        });

        await expect(fetchPopularPortlets(api, 90, 'fr-CA')).resolves.toEqual({
            days: 90,
            portlets: [CALENDAR],
        });
        expect(urls).toEqual([`/uPortal${POPULAR_PORTLETS_PATH}?days=90&locale=fr-CA`]);
    });

    it('reports a body of the wrong shape as malformed', async () => {
        const api = new ApiClient({
            contextPath: '',
            fetch: () => Promise.resolve(new Response('{"days":30}', { status: 200 })),
        });

        await expect(fetchPopularPortlets(api, 30, 'en')).rejects.toMatchObject({
            kind: 'malformed',
        } satisfies Partial<ApiError>);
    });
});
