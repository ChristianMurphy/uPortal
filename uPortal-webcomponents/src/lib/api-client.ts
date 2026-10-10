/**
 * A small fetch client for uPortal's REST APIs, used by every component.
 *
 * Components run on the portal's own pages, so calls are same-origin and authenticated by the
 * user's session cookie. Unsafe methods (POST, PUT, PATCH, DELETE) also send the CSRF token the
 * portal puts on each component (`csrf-header` / `csrf-token`). Every failure becomes an
 * {@link ApiError} with a {@link ApiErrorKind} the component can turn into a message.
 */

/** Why a call failed. */
export type ApiErrorKind =
    /** 401: the session expired or the user is not signed in. */
    | 'unauthorized'
    /** 403: signed in, but not allowed (this includes a missing or stale CSRF token). */
    | 'forbidden'
    /** 404: the resource does not exist (any more). */
    | 'not-found'
    /** 409: the request conflicts with the resource's current state. */
    | 'conflict'
    /** 5xx: the portal failed. */
    | 'server'
    /** Any other unsuccessful status. */
    | 'http'
    /** No response at all: offline, aborted, or blocked. */
    | 'network'
    /** A response the component cannot use: not JSON, or not the expected shape. */
    | 'malformed';

export class ApiError extends Error {
    readonly kind: ApiErrorKind;
    readonly status: number | undefined;

    constructor(kind: ApiErrorKind, status: number | undefined, message: string) {
        super(message);
        this.name = 'ApiError';
        this.kind = kind;
        this.status = status;
    }
}

/**
 * Checks a parsed response body and returns it typed; throws (anything) when it does not have
 * the expected shape. The client reports such a throw as a `malformed` {@link ApiError}.
 */
export type Validator<T> = (body: unknown) => T;

export interface ApiClientOptions {
    /** The portal's context path, such as `/uPortal`; the base of every API path. */
    contextPath: string;
    /** The header that carries the CSRF token, such as `X-CSRF-TOKEN`. */
    csrfHeader?: string | undefined;
    csrfToken?: string | undefined;
    /** For tests; defaults to the global fetch. */
    fetch?: typeof fetch;
}

/** Per-request options. */
export interface RequestOptions {
    /** Aborts the request; it then fails as a `network` error. */
    signal?: AbortSignal | undefined;
}

const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export function errorKindForStatus(status: number): ApiErrorKind {
    switch (status) {
        case 401:
            return 'unauthorized';
        case 403:
            return 'forbidden';
        case 404:
            return 'not-found';
        case 409:
            return 'conflict';
        default:
            return status >= 500 ? 'server' : 'http';
    }
}

export class ApiClient {
    readonly #options: ApiClientOptions;

    constructor(options: ApiClientOptions) {
        this.#options = options;
    }

    /** GETs `path` (relative to the context path) and validates the JSON body. */
    async get<T>(path: string, validate: Validator<T>, options: RequestOptions = {}): Promise<T> {
        const response = await this.#fetch('GET', path, undefined, options);
        return this.#parse(response, 'GET', path, validate);
    }

    /**
     * Sends an unsafe request with an optional JSON body. Resolves to the validated body, or to
     * `undefined` when there is no validator or the response has no content (204).
     */
    async send<T>(
        method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
        path: string,
        body?: unknown,
        validate?: Validator<T>,
        options: RequestOptions = {},
    ): Promise<T | undefined> {
        const response = await this.#fetch(method, path, body, options);
        if (validate === undefined || response.status === 204) {
            return undefined;
        }
        return this.#parse(response, method, path, validate);
    }

    /** Fetches with the session cookie (and CSRF token); throws unless the status is 2xx. */
    async #fetch(
        method: string,
        path: string,
        body: unknown,
        { signal }: RequestOptions,
    ): Promise<Response> {
        const headers: Record<string, string> = { Accept: 'application/json' };
        if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        const { csrfHeader, csrfToken } = this.#options;
        if (UNSAFE_METHODS.has(method) && csrfHeader && csrfToken) {
            headers[csrfHeader] = csrfToken;
        }

        const fetchImpl = this.#options.fetch ?? globalThis.fetch.bind(globalThis);
        let response: Response;
        try {
            response = await fetchImpl(`${this.#options.contextPath}${path}`, {
                method,
                headers,
                credentials: 'same-origin',
                ...(body === undefined ? {} : { body: JSON.stringify(body) }),
                ...(signal === undefined ? {} : { signal }),
            });
        } catch (cause) {
            throw new ApiError('network', undefined, `${method} ${path} failed: ${String(cause)}`);
        }

        if (!response.ok) {
            throw new ApiError(
                errorKindForStatus(response.status),
                response.status,
                `${method} ${path} answered ${response.status}`,
            );
        }
        return response;
    }

    /** Parses and validates a JSON body; anything unusable is a `malformed` error. */
    async #parse<T>(
        response: Response,
        method: string,
        path: string,
        validate: Validator<T>,
    ): Promise<T> {
        const text = await response.text();
        let parsed: unknown;
        try {
            parsed = JSON.parse(text);
        } catch {
            throw new ApiError(
                'malformed',
                response.status,
                `${method} ${path} did not return JSON`,
            );
        }
        try {
            return validate(parsed);
        } catch (cause) {
            throw new ApiError(
                'malformed',
                response.status,
                `${method} ${path} returned an unexpected body: ${String(cause)}`,
            );
        }
    }
}
