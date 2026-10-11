import { describe, expect, it, vi } from 'vite-plus/test';
import { ApiClient } from '../../lib/api-client';
import {
    BACKGROUND_PATH,
    fetchBackground,
    saveBackground,
    validateBackgroundPreference,
} from './background-api';

const GALAXY = {
    url: '/uPortal/bg/galaxy.jpg',
    thumbnailUrl: '/uPortal/bg/thumbnails/galaxy.jpg',
    caption: 'Galaxy',
};
const body = { containerSelector: '#page::before', images: [GALAXY], selected: GALAXY.url };
const API = `/uPortal${BACKGROUND_PATH}background-preference`;

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

describe('validateBackgroundPreference', () => {
    it('accepts the API body', () => {
        expect(validateBackgroundPreference(body)).toEqual(body);
    });

    it('reads a null or missing choice as none', () => {
        expect(validateBackgroundPreference({ ...body, selected: null }).selected).toBeUndefined();
        expect(
            validateBackgroundPreference({ containerSelector: 'body', images: [] }).selected,
        ).toBeUndefined();
    });

    it.each([
        ['a body that is not an object', [], 'the body is not an object'],
        ['a body without images', { containerSelector: 'body' }, 'images is not an array'],
        ['an image that is not an object', { ...body, images: ['x'] }, 'an image is not'],
        ['an image without a url', { ...body, images: [{ ...GALAXY, url: '' }] }, 'url is not'],
        ['a numeric caption', { ...body, images: [{ ...GALAXY, caption: 1 }] }, 'caption is not'],
        ['a choice that is not offered', { ...body, selected: '/elsewhere.jpg' }, 'selected'],
        ['a choice that is not a string', { ...body, selected: 3 }, 'selected'],
        ['a missing selector', { images: [], selected: null }, 'containerSelector is not'],
    ])('refuses %s', (_name, value, message) => {
        expect(() => validateBackgroundPreference(value)).toThrow(message);
    });
});

describe('fetchBackground', () => {
    it('GETs the channel by fname', async () => {
        const { api, fetch } = client(() => json(body));

        await expect(fetchBackground(api, 'background-preference')).resolves.toEqual(body);
        expect(fetch).toHaveBeenCalledWith(API, expect.objectContaining({ method: 'GET' }));
    });

    it('encodes the fname', async () => {
        const { api, fetch } = client(() => json(body));

        await fetchBackground(api, 'a/b c');
        expect(fetch.mock.calls[0]?.[0]).toBe(`/uPortal${BACKGROUND_PATH}a%2Fb%20c`);
    });
});

describe('saveBackground', () => {
    it('PUTs the choice with the CSRF token', async () => {
        const { api, fetch } = client(() => json(body));

        await expect(saveBackground(api, 'background-preference', GALAXY.url)).resolves.toEqual(
            body,
        );
        const [url, init] = fetch.mock.calls[0] ?? [];
        expect(url).toBe(API);
        expect(init?.method).toBe('PUT');
        expect(init?.headers).toMatchObject({ 'X-CSRF-TOKEN': 't' });
        expect(init?.body).toBe(JSON.stringify({ selected: GALAXY.url }));
    });

    it('sends null for none', async () => {
        const { api, fetch } = client(() => json({ ...body, selected: null }));

        await saveBackground(api, 'background-preference', undefined);
        expect(fetch.mock.calls[0]?.[1]?.body).toBe('{"selected":null}');
    });

    it('treats an empty answer as malformed', async () => {
        const { api } = client(() => new Response(null, { status: 204 }));

        await expect(saveBackground(api, 'x', undefined)).rejects.toMatchObject({
            kind: 'malformed',
        });
    });

    it('reports a refused image as http 400', async () => {
        const { api } = client(() => json({ message: 'no' }, 400));

        await expect(saveBackground(api, 'x', '/elsewhere.jpg')).rejects.toMatchObject({
            status: 400,
        });
    });
});
