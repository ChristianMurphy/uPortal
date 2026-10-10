import { vi } from 'vite-plus/test';

/** One request a stubbed fetch received. */
export interface RecordedRequest {
    method: string;
    url: string;
    headers: Record<string, string>;
    body: unknown;
}

type Responder = (request: RecordedRequest) => Response | Promise<Response>;

interface Route {
    method: string;
    path: string;
    respond: Responder;
}

export function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

export function text(body: string, status = 200): Response {
    return new Response(body, { status });
}

export function noContent(): Response {
    return new Response(null, { status: 204 });
}

/**
 * Replaces the global fetch with a router over canned responses (restored after each test by
 * `unstubGlobals`). A path matches the request URL without its query string; the last route
 * added for a method and path wins, so a test can override a default. Unrouted requests answer
 * 404. Every request is recorded for assertions.
 */
export class FetchStub {
    readonly requests: RecordedRequest[] = [];
    readonly #routes: Route[] = [];

    constructor() {
        vi.stubGlobal('fetch', (input: RequestInfo | URL, init: RequestInit = {}) =>
            this.#handle(input, init),
        );
    }

    on(method: string, path: string, respond: Responder | Response): this {
        this.#routes.unshift({
            method,
            path,
            respond: respond instanceof Response ? () => respond.clone() : respond,
        });
        return this;
    }

    /** Requests to a path (any method), in order. */
    requestsTo(path: string): RecordedRequest[] {
        return this.requests.filter((request) => request.url.split('?')[0] === path);
    }

    async #handle(input: RequestInfo | URL, init: RequestInit): Promise<Response> {
        const request: RecordedRequest = {
            method: init.method ?? 'GET',
            url: typeof input === 'string' ? input : input instanceof URL ? input.href : input.url,
            headers: { ...(init.headers as Record<string, string> | undefined) },
            body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
        };
        this.requests.push(request);
        const path = request.url.split('?')[0];
        const route = this.#routes.find((r) => r.method === request.method && r.path === path);
        return route ? route.respond(request) : new Response('Not Found', { status: 404 });
    }
}
