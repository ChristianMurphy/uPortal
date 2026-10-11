/**
 * Client for the background preference REST API (BackgroundPreferenceRESTController in
 * uPortal-api-rest).
 */
import { ApiError, type ApiClient, type RequestOptions } from '../../lib/api-client';

export const BACKGROUND_PATH = '/api/v6-0/background-preference/';

/** One background the user may choose. */
export interface BackgroundImage {
    /** The full-size image: what the user chooses and what the page shows. */
    url: string;
    thumbnailUrl: string;
    caption: string;
}

export interface BackgroundPreference {
    /** The CSS selector of the element whose background the image becomes. */
    containerSelector: string;
    images: BackgroundImage[];
    /** The URL of the chosen image, always one of `images`; undefined for none. */
    selected: string | undefined;
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

function toImage(value: unknown): BackgroundImage {
    const item = object(value, 'an image');
    return {
        url: string(item, 'url'),
        thumbnailUrl: string(item, 'thumbnailUrl'),
        caption: string(item, 'caption'),
    };
}

/** Checks a background preference body; throws a TypeError naming the first problem. */
export function validateBackgroundPreference(body: unknown): BackgroundPreference {
    const document = object(body, 'the body');
    const list = document['images'];
    if (!Array.isArray(list)) {
        throw new TypeError('images is not an array');
    }
    const images = list.map(toImage);
    const selected = document['selected'] ?? undefined;
    if (selected !== undefined && !images.some((image) => image.url === selected)) {
        throw new TypeError('selected is not one of the images');
    }
    return {
        containerSelector: string(document, 'containerSelector'),
        images,
        selected: images.find((image) => image.url === selected)?.url,
    };
}

function pathOf(fname: string): string {
    return `${BACKGROUND_PATH}${encodeURIComponent(fname)}`;
}

export function fetchBackground(
    api: ApiClient,
    fname: string,
    options: RequestOptions = {},
): Promise<BackgroundPreference> {
    return api.get(pathOf(fname), validateBackgroundPreference, options);
}

/** Saves the choice (undefined for none) and resolves to the preference as the portal has it. */
export async function saveBackground(
    api: ApiClient,
    fname: string,
    selected: string | undefined,
    options: RequestOptions = {},
): Promise<BackgroundPreference> {
    // JSON has no undefined: null clears the choice
    const body = { selected: selected ?? null };
    const saved = await api.send('PUT', pathOf(fname), body, validateBackgroundPreference, options);
    if (saved === undefined) {
        throw new ApiError('malformed', 204, 'The update returned no background');
    }
    return saved;
}
