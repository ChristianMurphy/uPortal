import { describe, expect, it, vi } from 'vite-plus/test';
import { ApiClient, ApiError } from '../../lib/api-client';
import {
    FAVORITES_PATH,
    fetchFavorites,
    moveFavorite,
    removeFavorite,
    safeUrl,
    validateFavorites,
} from './favorites-api';

const collection = {
    nodeId: 'u12l1s7',
    name: 'News',
    url: '/uPortal/f/u12l1s7/render.uP',
    moveAllowed: true,
    deleteAllowed: false,
};

const portlet = {
    nodeId: 'u12l1n20',
    fname: 'calendar',
    name: 'Calendar',
    description: 'Your events',
    url: '/uPortal/p/calendar/render.uP',
    target: null,
    iconUrl: '/icons/calendar.png',
    moveAllowed: true,
    deleteAllowed: true,
};

const body = { collections: [collection], portlets: [portlet], marketplaceUrl: null };

describe('validateFavorites', () => {
    it('accepts the API body and marks each favorite with its kind', () => {
        expect(validateFavorites(body)).toEqual({
            collections: [{ kind: 'collection', ...collection }],
            portlets: [{ kind: 'portlet', ...portlet }],
            marketplaceUrl: null,
        });
    });

    it('treats missing and blank optional fields as null', () => {
        const { description: _description, target: _target, ...bare } = portlet;
        const favorites = validateFavorites({
            collections: [],
            portlets: [{ ...bare, iconUrl: '  ' }],
            marketplaceUrl: '/uPortal/p/portletmarketplace/max/render.uP',
        });

        expect(favorites.portlets[0]).toMatchObject({
            description: null,
            target: null,
            iconUrl: null,
        });
        expect(favorites.marketplaceUrl).toBe('/uPortal/p/portletmarketplace/max/render.uP');
    });

    it('drops links that are not paths or http(s) URLs', () => {
        const favorites = validateFavorites({
            collections: [{ ...collection, url: 'javascript:alert(1)' }],
            portlets: [{ ...portlet, url: '//evil.example', iconUrl: 'data:image/png;base64,x' }],
            marketplaceUrl: 'vbscript:x',
        });

        expect(favorites.collections[0]?.url).toBeNull();
        expect(favorites.portlets[0]?.url).toBeNull();
        expect(favorites.portlets[0]?.iconUrl).toBeNull();
        expect(favorites.marketplaceUrl).toBeNull();
    });

    it.each([
        ['a non-object body', null, 'the body is not an object'],
        ['an array body', [], 'the body is not an object'],
        ['missing collections', { portlets: [] }, 'collections is not an array'],
        ['missing portlets', { collections: [] }, 'portlets is not an array'],
        ['a collection that is not an object', { ...body, collections: ['x'] }, 'a collection'],
        ['a portlet that is not an object', { ...body, portlets: [7] }, 'a portlet'],
        [
            'a favorite without a node',
            { ...body, portlets: [{ ...portlet, nodeId: '' }] },
            'nodeId is not a non-empty string',
        ],
        [
            'a lock flag that is not a boolean',
            { ...body, collections: [{ ...collection, deleteAllowed: 'no' }] },
            'deleteAllowed is not a boolean',
        ],
        [
            'a description that is not a string',
            { ...body, portlets: [{ ...portlet, description: 3 }] },
            'description is not a string',
        ],
    ])('refuses %s', (_case, value, message) => {
        expect(() => validateFavorites(value)).toThrow(message);
    });
});

describe('safeUrl', () => {
    it.each([
        ['/uPortal/p/x', '/uPortal/p/x'],
        [' https://example.edu/a ', 'https://example.edu/a'],
        ['HTTP://example.edu', 'HTTP://example.edu'],
        ['//example.edu', null],
        ['mailto:a@example.edu', null],
        ['relative/path', null],
        [null, null],
    ])('%s -> %s', (value, expected) => {
        expect(safeUrl(value)).toBe(expected);
    });
});

function client(response: Response) {
    const fetchMock = vi.fn((_url: string, _init: RequestInit) => Promise.resolve(response));
    const api = new ApiClient({
        contextPath: '/uPortal',
        csrfHeader: 'X-CSRF-TOKEN',
        csrfToken: 't',
        fetch: fetchMock as unknown as typeof fetch,
    });
    return { api, fetchMock };
}

describe('favorites API calls', () => {
    it('fetches the favorites', async () => {
        const { api, fetchMock } = client(Response.json(body));

        await expect(fetchFavorites(api)).resolves.toMatchObject({ marketplaceUrl: null });
        expect(fetchMock.mock.calls[0]?.[0]).toBe(`/uPortal${FAVORITES_PATH}`);
    });

    it('removes a favorite by its node', async () => {
        const { api, fetchMock } = client(new Response(null, { status: 204 }));

        await expect(removeFavorite(api, 'u12 l1/n20')).resolves.toBeUndefined();
        expect(fetchMock.mock.calls[0]?.[0]).toBe(`/uPortal${FAVORITES_PATH}/u12%20l1%2Fn20`);
        expect(fetchMock.mock.calls[0]?.[1].method).toBe('DELETE');
    });

    it('moves a favorite and returns the new order', async () => {
        const { api, fetchMock } = client(Response.json(body));

        await expect(moveFavorite(api, 'u12l1n20', { after: 'u12l1n21' })).resolves.toMatchObject({
            portlets: [{ nodeId: 'u12l1n20' }],
        });
        const [url, init] = fetchMock.mock.calls[0] ?? [];
        expect(url).toBe(`/uPortal${FAVORITES_PATH}/u12l1n20/move`);
        expect(init?.method).toBe('POST');
        expect(init?.body).toBe('{"after":"u12l1n21"}');
    });

    it('reports a move that returns no favorites as malformed', async () => {
        const { api } = client(new Response(null, { status: 204 }));

        const error = await moveFavorite(api, 'u12l1n20', { before: 'u12l1n19' }).catch(
            (e: unknown) => e,
        );

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).kind).toBe('malformed');
    });
});
