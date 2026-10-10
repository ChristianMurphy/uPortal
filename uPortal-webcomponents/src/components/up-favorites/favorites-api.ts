/**
 * Client for the favorites REST API (FavoritesRESTController in uPortal-api-rest).
 */
import { ApiError, type ApiClient, type RequestOptions } from '../../lib/api-client';

export const FAVORITES_PATH = '/api/v6-0/favorites';

interface FavoriteBase {
    /** The layout node; identifies the favorite in the API. */
    nodeId: string;
    name: string;
    /** Where the favorite opens; null when the portal sent no usable link. */
    url: string | null;
    moveAllowed: boolean;
    deleteAllowed: boolean;
}

export interface FavoriteCollection extends FavoriteBase {
    kind: 'collection';
}

export interface FavoritePortlet extends FavoriteBase {
    kind: 'portlet';
    fname: string;
    description: string | null;
    /** The window to open the link in, such as `_blank`; null for the portal's window. */
    target: string | null;
    iconUrl: string | null;
}

export type Favorite = FavoriteCollection | FavoritePortlet;

export interface FavoritesData {
    collections: FavoriteCollection[];
    portlets: FavoritePortlet[];
    marketplaceUrl: string | null;
}

/** Where a favorite moves: just before or just after another favorite of its kind. */
export type MovePosition = { before: string } | { after: string };

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

function boolean(item: Json, key: string): boolean {
    const value = item[key];
    if (typeof value !== 'boolean') {
        throw new TypeError(`${key} is not a boolean`);
    }
    return value;
}

function array(body: Json, key: string): unknown[] {
    const value = body[key];
    if (!Array.isArray(value)) {
        throw new TypeError(`${key} is not an array`);
    }
    return value;
}

/**
 * Keeps links that are paths on this site or http(s) URLs. Anything else, such as a
 * `javascript:` URL in a channel's parameters, is dropped rather than put in an `href`.
 */
export function safeUrl(value: string | null): string | null {
    if (value === null) {
        return null;
    }
    const trimmed = value.trim();
    return /^(\/(?!\/)|https?:\/\/)/i.test(trimmed) ? trimmed : null;
}

function toCollection(value: unknown): FavoriteCollection {
    const item = object(value, 'a collection');
    return {
        kind: 'collection',
        nodeId: string(item, 'nodeId'),
        name: string(item, 'name'),
        url: safeUrl(optionalString(item, 'url')),
        moveAllowed: boolean(item, 'moveAllowed'),
        deleteAllowed: boolean(item, 'deleteAllowed'),
    };
}

function toPortlet(value: unknown): FavoritePortlet {
    const item = object(value, 'a portlet');
    return {
        kind: 'portlet',
        nodeId: string(item, 'nodeId'),
        name: string(item, 'name'),
        url: safeUrl(optionalString(item, 'url')),
        moveAllowed: boolean(item, 'moveAllowed'),
        deleteAllowed: boolean(item, 'deleteAllowed'),
        fname: string(item, 'fname'),
        description: optionalString(item, 'description'),
        target: optionalString(item, 'target'),
        iconUrl: safeUrl(optionalString(item, 'iconUrl')),
    };
}

/** Checks a `GET /api/v6-0/favorites` body; throws a TypeError naming the first problem. */
export function validateFavorites(body: unknown): FavoritesData {
    const document = object(body, 'the body');
    return {
        collections: array(document, 'collections').map(toCollection),
        portlets: array(document, 'portlets').map(toPortlet),
        marketplaceUrl: safeUrl(optionalString(document, 'marketplaceUrl')),
    };
}

export function fetchFavorites(
    api: ApiClient,
    options: RequestOptions = {},
): Promise<FavoritesData> {
    return api.get(FAVORITES_PATH, validateFavorites, options);
}

export async function removeFavorite(
    api: ApiClient,
    nodeId: string,
    options: RequestOptions = {},
): Promise<void> {
    await api.send(
        'DELETE',
        `${FAVORITES_PATH}/${encodeURIComponent(nodeId)}`,
        undefined,
        undefined,
        options,
    );
}

/** Moves a favorite and resolves to the favorites in their new order. */
export async function moveFavorite(
    api: ApiClient,
    nodeId: string,
    position: MovePosition,
    options: RequestOptions = {},
): Promise<FavoritesData> {
    const favorites = await api.send(
        'POST',
        `${FAVORITES_PATH}/${encodeURIComponent(nodeId)}/move`,
        position,
        validateFavorites,
        options,
    );
    if (favorites === undefined) {
        throw new ApiError('malformed', 204, 'The move returned no favorites');
    }
    return favorites;
}
