/**
 * Clients for the APIs the search components call: the search REST API (SearchResultsRESTController
 * in uPortal-api-rest) and the portlet registry list that type-ahead suggests from.
 */
import type { ApiClient, RequestOptions } from '../../lib/api-client';
import { safeUrl } from '../../lib/safe-url';

export const SEARCH_PATH = '/api/v6-0/search';

export const PORTLET_LIST_PATH = '/api/portletList';

export interface SearchResult {
    title: string;
    /** Where the result opens; null for people, or when the link is not a safe URL. */
    url: string | null;
    /** The result's other attributes, each a list of strings, in the order the portal sent. */
    attributes: Readonly<Record<string, readonly string[]>>;
}

export interface SearchResultGroup {
    /** The search strategy's type, such as `people` or `portlets`. */
    type: string;
    /** The tab's name for a remote provider, as the portal configures it; null for the others. */
    label: string | null;
    results: readonly SearchResult[];
    /** True when this group's search failed; the other groups are unaffected. */
    failed: boolean;
}

export interface SearchResults {
    query: string;
    groups: readonly SearchResultGroup[];
}

/** A portlet that type-ahead can suggest. */
export interface Suggestion {
    fname: string;
    title: string;
    description: string;
    /** The text a typed term is matched against: name, title, fname and description. */
    label: string;
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

function array(item: Json, key: string): unknown[] {
    const value = item[key];
    if (!Array.isArray(value)) {
        throw new TypeError(`${key} is not an array`);
    }
    return value;
}

function string(item: Json, key: string): string {
    const value = item[key];
    if (typeof value !== 'string') {
        throw new TypeError(`${key} is not a string`);
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
    return value;
}

function toAttributes(value: unknown): Record<string, readonly string[]> {
    const attributes: Record<string, readonly string[]> = {};
    for (const [name, values] of Object.entries(object(value, 'attributes'))) {
        const strings = Array.isArray(values)
            ? values.filter((v): v is string => typeof v === 'string')
            : [];
        if (!Array.isArray(values) || strings.length !== values.length) {
            throw new TypeError(`attribute ${name} is not a list of strings`);
        }
        attributes[name] = strings;
    }
    return attributes;
}

function toResult(value: unknown): SearchResult {
    const item = object(value, 'a result');
    return {
        title: string(item, 'title'),
        url: safeUrl(optionalString(item, 'url')),
        attributes: toAttributes(item['attributes']),
    };
}

function toGroup(value: unknown): SearchResultGroup {
    const item = object(value, 'a group');
    const failed = item['failed'] ?? false;
    if (typeof failed !== 'boolean') {
        throw new TypeError('failed is not a boolean');
    }
    return {
        type: string(item, 'type'),
        label: optionalString(item, 'label'),
        results: array(item, 'results').map(toResult),
        failed,
    };
}

/** Checks a `GET /api/v6-0/search` body; throws a TypeError naming the first problem. */
export function validateSearchResults(body: unknown): SearchResults {
    const document = object(body, 'the body');
    return { query: string(document, 'query'), groups: array(document, 'groups').map(toGroup) };
}

/** Searches for `query`, limited to `types` when there are any. */
export function fetchSearchResults(
    api: ApiClient,
    query: string,
    types: readonly string[],
    options: RequestOptions = {},
): Promise<SearchResults> {
    const parameters = new URLSearchParams({ q: query });
    for (const type of types) {
        parameters.append('type', type);
    }
    return api.get(`${SEARCH_PATH}?${parameters.toString()}`, validateSearchResults, options);
}

function collectChannels(category: Json, seen: Map<string, Suggestion>): void {
    for (const value of array(category, 'channels')) {
        const channel = object(value, 'a portlet');
        const fname = string(channel, 'fname');
        // A portlet in several categories is suggested once
        if (!seen.has(fname)) {
            const title = string(channel, 'title');
            const description = optionalString(channel, 'description') ?? '';
            const name = optionalString(channel, 'name') ?? '';
            seen.set(fname, {
                fname,
                title,
                description,
                label: [name, title, fname, description].join('~'),
            });
        }
    }
    for (const child of array(category, 'categories')) {
        collectChannels(object(child, 'a category'), seen);
    }
}

/**
 * Checks a `GET /api/portletList` body (`{"registry": {"categories": [...], "channels": [...]}}`,
 * categories nesting further categories and channels) and lists each portlet once.
 */
export function validatePortletList(body: unknown): Suggestion[] {
    const seen = new Map<string, Suggestion>();
    collectChannels(object(object(body, 'the body')['registry'], 'registry'), seen);
    return [...seen.values()];
}

export function fetchSuggestions(
    api: ApiClient,
    options: RequestOptions = {},
): Promise<Suggestion[]> {
    return api.get(PORTLET_LIST_PATH, validatePortletList, options);
}

/**
 * The suggestions whose label contains `term`, ignoring case, as jQuery UI's autocomplete
 * matched them. A blank term matches nothing.
 */
export function matchSuggestions(suggestions: readonly Suggestion[], term: string): Suggestion[] {
    const needle = term.trim().toLowerCase();
    if (needle === '') {
        return [];
    }
    return suggestions.filter((suggestion) => suggestion.label.toLowerCase().includes(needle));
}
