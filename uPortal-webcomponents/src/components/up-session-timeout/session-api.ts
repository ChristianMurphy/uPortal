/**
 * Client for the session REST API (SessionRESTController in uPortal-api-rest).
 */
import { ApiError, type ApiClient, type RequestOptions } from '../../lib/api-client';

export const SESSION_PATH = '/api/v6-0/session';
export const KEEP_ALIVE_PATH = `${SESSION_PATH}/keep-alive`;

export interface SessionData {
    /** The session's timeout in seconds; zero or less when it never expires. */
    maxInactiveIntervalSeconds: number;
}

function isJson(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** Accepts `{"maxInactiveIntervalSeconds": <integer>}` and nothing else. */
export function validateSession(body: unknown): SessionData {
    const value = isJson(body) ? body['maxInactiveIntervalSeconds'] : undefined;
    if (typeof value !== 'number' || !Number.isInteger(value)) {
        throw new TypeError('maxInactiveIntervalSeconds is not an integer');
    }
    return { maxInactiveIntervalSeconds: value };
}

/** The current session's timeout. */
export function fetchSession(api: ApiClient, options: RequestOptions = {}): Promise<SessionData> {
    return api.get(SESSION_PATH, validateSession, options);
}

/** Extends the current session and returns its timeout. */
export async function extendSession(
    api: ApiClient,
    options: RequestOptions = {},
): Promise<SessionData> {
    const session = await api.send('POST', KEEP_ALIVE_PATH, undefined, validateSession, options);
    // A 204 carries no timeout to start the next period from
    if (session === undefined) {
        throw new ApiError('malformed', 204, `POST ${KEEP_ALIVE_PATH} returned no body`);
    }
    return session;
}
