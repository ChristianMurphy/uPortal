import { describe, expect, it, vi } from 'vite-plus/test';
import { ApiClient, ApiError } from '../../lib/api-client';
import {
    fetchUserLocale,
    LOCALE_PATH,
    ownName,
    saveUserLocale,
    validateUserLocale,
} from './locale-api';

const ENGLISH = { code: 'en_US', tag: 'en-US', name: 'English', nativeName: 'English' };
const FRENCH = { code: 'fr_FR', tag: 'fr-FR', name: 'French', nativeName: 'Français' };
const body = { selected: 'fr_FR', locales: [ENGLISH, FRENCH] };

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

describe('validateUserLocale', () => {
    it('accepts the API body', () => {
        expect(validateUserLocale(body)).toEqual(body);
    });

    it('accepts a missing or null choice', () => {
        expect(validateUserLocale({ locales: [ENGLISH] }).selected).toBeNull();
        expect(validateUserLocale({ selected: null, locales: [] })).toEqual({
            selected: null,
            locales: [],
        });
    });

    it.each([
        ['a body that is not an object', [], 'the body is not an object'],
        ['a body without locales', { selected: null }, 'locales is not an array'],
        ['a locale that is not an object', { locales: ['en_US'] }, 'a locale is not an object'],
        ['a locale without a name', { locales: [{ ...ENGLISH, name: ' ' }] }, 'name is not'],
        [
            'a locale without its own name',
            { locales: [{ ...ENGLISH, nativeName: undefined }] },
            'nativeName is not',
        ],
        ['a locale with a numeric code', { locales: [{ ...ENGLISH, code: 1 }] }, 'code is not'],
        ['a choice that is not offered', { selected: 'de_DE', locales: [ENGLISH] }, 'selected'],
        ['a choice that is not a string', { selected: 1, locales: [ENGLISH] }, 'selected'],
    ])('refuses %s', (_name, value, message) => {
        expect(() => validateUserLocale(value)).toThrow(message);
    });
});

describe('fetchUserLocale', () => {
    it('GETs the locales', async () => {
        const { api, fetch } = client(() => json(body));

        await expect(fetchUserLocale(api)).resolves.toEqual(body);
        expect(fetch).toHaveBeenCalledWith(
            `/uPortal${LOCALE_PATH}`,
            expect.objectContaining({ method: 'GET' }),
        );
    });

    it('fails as malformed on a body it cannot use', async () => {
        const { api } = client(() => json({ locales: 'en_US' }));

        await expect(fetchUserLocale(api)).rejects.toMatchObject({ kind: 'malformed' });
    });
});

describe('saveUserLocale', () => {
    it('PUTs the code with the CSRF token and returns the new locales', async () => {
        const { api, fetch } = client(() => json(body));

        await expect(saveUserLocale(api, 'fr_FR')).resolves.toEqual(body);
        const [url, init] = fetch.mock.calls[0] ?? [];
        expect(url).toBe(`/uPortal${LOCALE_PATH}`);
        expect(init?.method).toBe('PUT');
        expect(init?.body).toBe('{"code":"fr_FR"}');
        expect(init?.headers).toMatchObject({ 'X-CSRF-TOKEN': 't' });
    });

    it('fails when the portal answers without a body', async () => {
        const { api } = client(() => new Response(null, { status: 204 }));

        await expect(saveUserLocale(api, 'fr_FR')).rejects.toEqual(
            new ApiError('malformed', 204, 'The update returned no locales'),
        );
    });

    it('passes on the portal refusing the code', async () => {
        const { api } = client(() => json({ status: 400, message: 'no' }, 400));

        await expect(saveUserLocale(api, 'xx_YY')).rejects.toMatchObject({
            kind: 'http',
            status: 400,
        });
    });
});

describe('ownName', () => {
    it.each([
        ['fr-FR', 'Français'],
        ['es-ES', 'Español'],
        ['de-DE', 'Deutsch'],
        ['pl-PL', 'Polski'],
        ['ar', 'العربية'],
        ['zh-CN', '中文'],
        ['ja-JP', '日本語'],
    ])('names %s in its own language: %s', (tag, name) => {
        expect(ownName({ code: tag.replace('-', '_'), tag, name: 'x', nativeName: 'x' })).toBe(
            name,
        );
    });

    it.each([
        ['a language the browser cannot name', 'qaa'],
        ['a tag the browser cannot read', 'not a tag'],
    ])('falls back to the portal name for %s', (_case, tag) => {
        expect(ownName({ code: 'qaa', tag, name: 'Private', nativeName: 'Privé' })).toBe('Privé');
    });
});
