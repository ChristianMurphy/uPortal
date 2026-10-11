/**
 * Client for the popular portlets REST API (PopularPortletsRESTController in uPortal-api-rest).
 */
import type { ApiClient, RequestOptions } from '../../lib/api-client';

export const POPULAR_PORTLETS_PATH = '/api/v6-0/popular-portlets';

/** A portlet and how many times users added it to their layouts. */
export interface PopularPortlet {
    fname: string;
    title: string;
    description: string | null;
    /** The portlet's page; null when the portal sent no usable link. */
    url: string | null;
    count: number;
}

export interface PopularPortletsData {
    /** The number of days counted, ending now. */
    days: number;
    portlets: PopularPortlet[];
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
    if (typeof value !== 'string' || value === '') {
        throw new TypeError(`${key} is not a non-empty string`);
    }
    return value;
}

function optionalString(item: Json, key: string): string | null {
    const value = item[key];
    if (value === undefined || value === null) {
        return null;
    }
    if (typeof value !== 'string') {
        throw new TypeError(`${key} is not a string`);
    }
    return value.trim() === '' ? null : value;
}

function count(item: Json, key: string): number {
    const value = item[key];
    if (typeof value !== 'number' || !Number.isInteger(value) || value < 0) {
        throw new TypeError(`${key} is not a whole number`);
    }
    return value;
}

/**
 * Keeps links that are paths on this site or http(s) URLs. Anything else, such as a
 * `javascript:` URL, is dropped rather than put in an `href`.
 */
export function safeUrl(value: string | null): string | null {
    if (value === null) {
        return null;
    }
    const trimmed = value.trim();
    return /^(\/(?!\/)|https?:\/\/)/i.test(trimmed) ? trimmed : null;
}

function toPortlet(value: unknown): PopularPortlet {
    const item = object(value, 'a portlet');
    return {
        fname: string(item, 'fname'),
        title: string(item, 'title'),
        description: optionalString(item, 'description'),
        url: safeUrl(optionalString(item, 'url')),
        count: count(item, 'count'),
    };
}

/** Checks a `GET /api/v6-0/popular-portlets` body; throws a TypeError naming the first problem. */
export function validatePopularPortlets(body: unknown): PopularPortletsData {
    const document = object(body, 'the body');
    const portlets = document['portlets'];
    if (!Array.isArray(portlets)) {
        throw new TypeError('portlets is not an array');
    }
    return { days: count(document, 'days'), portlets: portlets.map(toPortlet) };
}

/** The portlets added in the last `days` days, with titles in `locale` (a BCP 47 tag). */
export function fetchPopularPortlets(
    api: ApiClient,
    days: number,
    locale: string,
    options: RequestOptions = {},
): Promise<PopularPortletsData> {
    const query = new URLSearchParams({ days: String(days), locale });
    return api.get(
        `${POPULAR_PORTLETS_PATH}?${query.toString()}`,
        validatePopularPortlets,
        options,
    );
}
