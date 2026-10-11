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
    /** The language's name in the user's current language, such as `French` for English users. */
    name: string;
    /** The language's name in that language, such as `Français`. */
    nativeName: string;
}

export interface UserLocaleData {
    /** The code of the user's chosen locale; null when the user has not chosen one. */
    selected: string | null;
    /**
     * The code of the locale the page is rendered in, the one to check when the user has not
     * chosen one; null when the portal doesn't say or names a locale it doesn't list.
     */
    current: string | null;
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
    return {
        code: string(item, 'code'),
        tag: string(item, 'tag'),
        name: string(item, 'name'),
        nativeName: string(item, 'nativeName'),
    };
}

/**
 * The language's name in that language, as a list item starts ("Français", "العربية", "中文"). The
 * browser names it (`Intl.DisplayNames`), so a language a site adds needs no message; the API's
 * `nativeName` stands in when the browser can't name it, or the tag isn't one it reads.
 */
export function ownName(locale: LocaleOption): string {
    let name: string | undefined;
    try {
        const language = new Intl.Locale(locale.tag).language;
        name = new Intl.DisplayNames([locale.tag], { type: 'language', fallback: 'none' }).of(
            language,
        );
    } catch {
        name = undefined;
    }
    if (name === undefined || name === '') {
        return locale.nativeName;
    }
    const [first = '', ...rest] = name;
    return first.toLocaleUpperCase(locale.tag) + rest.join('');
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
    // Only a hint for the checked radio button, so a code that isn't listed is ignored
    const current = locales.find((locale) => locale.code === document['current']);
    return { selected: chosen?.code ?? null, current: current?.code ?? null, locales };
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
