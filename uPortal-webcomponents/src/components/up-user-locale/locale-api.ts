/**
 * Client for the user locale REST API (UserLocaleRESTController in uPortal-api-rest).
 */
import { ApiError, type ApiClient, type RequestOptions } from '../../lib/api-client';

export const LOCALE_PATH = '/api/v6-0/locale';

/** One of the portal's languages. */
export interface LocaleOption {
    /** The portal's code, such as `fr_FR`; what the API takes back. */
    code: string;
    /** The BCP 47 tag, such as `fr-FR`. */
    tag: string;
    /** The language's name in the user's current language, such as `français`. */
    name: string;
}

export interface UserLocaleData {
    /** The code of the user's chosen locale; null when the user has not chosen one. */
    selected: string | null;
    locales: LocaleOption[];
}

type Json = Record<string, unknown>;

function isJson(value: unknown): value is Json {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function object(value: unknown, what: string): Json {
    if (!isJson(value)) {
        throw new TypeError(`${what} is not an object`);
    }
    return value;
}

function string(item: Json, key: string): string {
    const value = item[key];
    if (typeof value !== 'string' || value.trim() === '') {
        throw new TypeError(`${key} is not a non-empty string`);
    }
    return value;
}

function toOption(value: unknown): LocaleOption {
    const item = object(value, 'a locale');
    return { code: string(item, 'code'), tag: string(item, 'tag'), name: string(item, 'name') };
}

/** Checks a `/api/v6-0/locale` body; throws a TypeError naming the first problem. */
export function validateUserLocale(body: unknown): UserLocaleData {
    const document = object(body, 'the body');
    const list = document['locales'];
    if (!Array.isArray(list)) {
        throw new TypeError('locales is not an array');
    }
    const locales = list.map(toOption);
    const selected = document['selected'] ?? null;
    const chosen = locales.find((locale) => locale.code === selected);
    if (selected !== null && chosen === undefined) {
        throw new TypeError('selected is not one of the locales');
    }
    return { selected: chosen?.code ?? null, locales };
}

export function fetchUserLocale(
    api: ApiClient,
    options: RequestOptions = {},
): Promise<UserLocaleData> {
    return api.get(LOCALE_PATH, validateUserLocale, options);
}

/** Makes `code` the user's locale and resolves to the locales, named in the new language. */
export async function saveUserLocale(
    api: ApiClient,
    code: string,
    options: RequestOptions = {},
): Promise<UserLocaleData> {
    const saved = await api.send('PUT', LOCALE_PATH, { code }, validateUserLocale, options);
    if (saved === undefined) {
        throw new ApiError('malformed', 204, 'The update returned no locales');
    }
    return saved;
}
