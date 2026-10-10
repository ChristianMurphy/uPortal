/**
 * Messages for components, backed by uPortal's message bundles (`Messages.properties`, its
 * translations, and any messages edited in the portal).
 *
 * Each component ships English defaults for the keys it uses, so it renders at once and still
 * works if loading fails. It then asks the portal for the same keys in the user's locale
 * (`GET /api/v6-0/messages`), which returns them as the portal's MessageSource resolves them:
 * quotes unescaped and `{0}`-style placeholders kept for {@link formatMessage}.
 */
import type { ApiClient } from './api-client';

export type Messages = Readonly<Record<string, string>>;

export const MESSAGES_PATH = '/api/v6-0/messages';

/** Replaces `{0}`, `{1}`, … with the arguments; placeholders without an argument stay as they are. */
export function formatMessage(pattern: string, args: readonly (string | number)[] = []): string {
    return pattern.replace(/\{(\d+)\}/g, (placeholder, index: string) => {
        const arg = args[Number(index)];
        return arg === undefined ? placeholder : String(arg);
    });
}

/** Accepts `{ messages: { key: string } }`; ignores entries that are not strings. */
export function validateMessages(body: unknown): Messages {
    if (typeof body !== 'object' || body === null) {
        throw new TypeError('not an object');
    }
    const { messages } = body as { messages?: unknown };
    if (typeof messages !== 'object' || messages === null || Array.isArray(messages)) {
        throw new TypeError('no messages object');
    }
    const result: Record<string, string> = {};
    for (const [key, value] of Object.entries(messages)) {
        if (typeof value === 'string') {
            result[key] = value;
        }
    }
    return result;
}

const cache = new Map<string, Promise<Messages>>();

/**
 * Loads the keys in the locale, once per page for each locale and set of keys, however many
 * components ask. Resolves to the messages the portal has; never rejects, because the component's
 * English defaults cover any key that is missing.
 */
export function loadMessages(
    client: ApiClient,
    locale: string,
    keys: readonly string[],
): Promise<Messages> {
    const sortedKeys = keys.toSorted();
    const cacheKey = `${locale}|${sortedKeys.join(',')}`;
    let messages = cache.get(cacheKey);
    if (messages === undefined) {
        const query = new URLSearchParams({ locale, keys: sortedKeys.join(',') });
        messages = client
            .get(`${MESSAGES_PATH}?${query.toString()}`, validateMessages)
            .catch(() => {
                // Let a later component try again; this one keeps its defaults
                cache.delete(cacheKey);
                return {};
            });
        cache.set(cacheKey, messages);
    }
    return messages;
}

/** Forgets every loaded message; for tests. */
export function clearMessageCache(): void {
    cache.clear();
}
