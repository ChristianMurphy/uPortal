/**
 * Client for the self edit account REST API (SelfEditAccountRESTController in uPortal-api-rest).
 */
import type { ApiClient, RequestOptions } from '../../lib/api-client';

export const ACCOUNT_PATH = '/api/v6-0/account';

/** One attribute of the account. */
export interface AccountAttribute {
    name: string;
    /** The attribute's label in the user's language. */
    label: string;
    /** Its first value; null when it has none. */
    value: string | null;
}

export interface AccountData {
    username: string;
    /** The details the account view lists, from the user's session. */
    details: AccountAttribute[];
    /** Whether the user may edit their own account. */
    canEdit: boolean;
    /** The attributes the user may edit, with their stored values; empty unless canEdit. */
    editable: AccountAttribute[];
}

/** What `PUT /api/v6-0/account` takes. */
export interface AccountChanges {
    /** Every editable attribute, by name; an empty value removes it. */
    attributes: Record<string, string>;
    /** A new password; empty to keep the current one. */
    password: string;
    confirmPassword: string;
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

function toAttribute(value: unknown): AccountAttribute {
    const item = object(value, 'an attribute');
    const attributeValue = item['value'] ?? null;
    if (attributeValue !== null && typeof attributeValue !== 'string') {
        throw new TypeError('value is not a string or null');
    }
    return { name: string(item, 'name'), label: string(item, 'label'), value: attributeValue };
}

function attributes(document: Json, key: string): AccountAttribute[] {
    const list = document[key];
    if (!Array.isArray(list)) {
        throw new TypeError(`${key} is not an array`);
    }
    return list.map(toAttribute);
}

/** Checks a `GET /api/v6-0/account` body; throws a TypeError naming the first problem. */
export function validateAccount(body: unknown): AccountData {
    const document = object(body, 'the body');
    const canEdit = document['canEdit'];
    if (typeof canEdit !== 'boolean') {
        throw new TypeError('canEdit is not a boolean');
    }
    const editable = attributes(document, 'editable');
    if (!canEdit && editable.length > 0) {
        throw new TypeError('editable attributes without canEdit');
    }
    return {
        username: string(document, 'username'),
        details: attributes(document, 'details'),
        canEdit,
        editable,
    };
}

export function fetchAccount(api: ApiClient, options: RequestOptions = {}): Promise<AccountData> {
    return api.get(ACCOUNT_PATH, validateAccount, options);
}

/** Saves the changes; the portal answers 204. */
export async function saveAccount(
    api: ApiClient,
    changes: AccountChanges,
    options: RequestOptions = {},
): Promise<void> {
    await api.send('PUT', ACCOUNT_PATH, changes, undefined, options);
}
