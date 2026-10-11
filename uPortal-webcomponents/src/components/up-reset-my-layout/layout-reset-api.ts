/**
 * Client for the reset-my-layout REST API (ResetMyLayoutRESTController in uPortal-api-rest).
 */
import { ApiError, type ApiClient, type RequestOptions } from '../../lib/api-client';

export const RESET_PATH = '/api/v6-0/layout/reset';

export interface LayoutReset {
    /** The user whose layout a reset acts on: always the signed-in one. */
    userName: string;
}

function isJson(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** Checks a `/api/v6-0/layout/reset` body; throws a TypeError naming the problem. */
export function validateLayoutReset(body: unknown): LayoutReset {
    if (!isJson(body)) {
        throw new TypeError('the body is not an object');
    }
    const userName = body['userName'];
    if (typeof userName !== 'string' || userName.trim() === '') {
        throw new TypeError('userName is not a non-empty string');
    }
    return { userName };
}

/** Whose layout a reset would act on. */
export function fetchLayoutReset(
    api: ApiClient,
    options: RequestOptions = {},
): Promise<LayoutReset> {
    return api.get(RESET_PATH, validateLayoutReset, options);
}

/** Resets the signed-in user's layout; resolves to whose it was. */
export async function resetLayout(
    api: ApiClient,
    options: RequestOptions = {},
): Promise<LayoutReset> {
    const reset = await api.send('POST', RESET_PATH, undefined, validateLayoutReset, options);
    if (reset === undefined) {
        throw new ApiError('malformed', 204, 'The reset returned no body');
    }
    return reset;
}
