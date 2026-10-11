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

/** A message argument: text as it is, numbers and dates formatted for the locale. */
export type MessageArg = string | number | Date;

/**
 * The locale as a canonical BCP 47 tag (`en_US` and `en-us` give `en-US`), or English when `Intl`
 * does not accept it.
 */
export function intlLocale(locale: string): string {
    try {
        return Intl.getCanonicalLocales(locale.replaceAll('_', '-'))[0] ?? 'en';
    } catch {
        return 'en';
    }
}

/** Languages written right to left, for browsers without `Intl.Locale` text info. */
const RIGHT_TO_LEFT = new Set(['ar', 'he', 'fa', 'ur']);

interface TextInfo {
    readonly direction?: string;
}

/** The text direction the browser knows for a locale (`Intl.Locale` text info), if it does. */
export function intlTextDirection(locale: Intl.Locale): string | undefined {
    const withInfo = locale as Intl.Locale & {
        getTextInfo?: () => TextInfo;
        textInfo?: TextInfo;
    };
    return (withInfo.getTextInfo?.() ?? withInfo.textInfo)?.direction;
}

/**
 * The direction of a locale's text: `rtl` for Arabic, Hebrew, Persian and Urdu (and any other
 * language the browser's `Intl.Locale` says is written right to left), else `ltr`.
 */
export function textDirection(
    locale: string,
    browserDirection: (locale: Intl.Locale) => string | undefined = intlTextDirection,
): 'ltr' | 'rtl' {
    const parsed = new Intl.Locale(intlLocale(locale));
    const direction = browserDirection(parsed);
    if (direction === 'rtl' || direction === 'ltr') {
        return direction;
    }
    return RIGHT_TO_LEFT.has(parsed.language) ? 'rtl' : 'ltr';
}

/**
 * Replaces `{0}`, `{1}`, … with the arguments; placeholders without an argument stay as they are.
 * Numbers are formatted with `Intl.NumberFormat` and dates with `Intl.DateTimeFormat` for the
 * locale (MOD-I235), so `1234.5` reads `1.234,5` in German and `١٬٢٣٤٫٥` in Egyptian Arabic.
 */
export function formatMessage(
    pattern: string,
    args: readonly MessageArg[] = [],
    locale = 'en',
): string {
    const tag = intlLocale(locale);
    return pattern.replace(/\{(\d+)\}/g, (placeholder, index: string) => {
        const arg = args[Number(index)];
        if (arg === undefined) {
            return placeholder;
        }
        if (typeof arg === 'number') {
            return new Intl.NumberFormat(tag).format(arg);
        }
        if (arg instanceof Date) {
            return new Intl.DateTimeFormat(tag, { dateStyle: 'medium' }).format(arg);
        }
        return arg;
    });
}

/** The plural categories of Unicode CLDR, which `Intl.PluralRules` picks from. */
export const PLURAL_CATEGORIES: readonly Intl.LDMLPluralRule[] = [
    'zero',
    'one',
    'two',
    'few',
    'many',
    'other',
];

const pluralRules = new Map<string, Intl.PluralRules>();

function pluralRulesFor(locale: string): Intl.PluralRules {
    let rules = pluralRules.get(locale);
    if (rules === undefined) {
        rules = new Intl.PluralRules(intlLocale(locale));
        pluralRules.set(locale, rules);
    }
    return rules;
}

/**
 * The key of the plural form of `key` for `count` in the locale (MOD-I235). A plural message is a
 * family of keys, one per CLDR category the language uses: `key.one` and `key.other` in English,
 * Spanish and French, `key.one`, `key.few`, `key.many` and `key.other` in Polish, all six in
 * Arabic. The result is `key.<category>` when `has` knows it, else `key.other`, else `key`.
 */
export function pluralKey(
    key: string,
    count: number,
    locale: string,
    has: (key: string) => boolean,
): string {
    const category = pluralRulesFor(locale).select(count);
    for (const candidate of [`${key}.${category}`, `${key}.other`]) {
        if (has(candidate)) {
            return candidate;
        }
    }
    return key;
}

/**
 * The keys to ask the portal for: the given ones, and every category of each plural family (a key
 * ending in `.other`), so a language's bundle can supply forms that English has no key for.
 */
export function messageKeys(keys: readonly string[]): string[] {
    const result = new Set(keys);
    for (const key of keys) {
        if (key.endsWith('.other')) {
            const family = key.slice(0, -'.other'.length);
            for (const category of PLURAL_CATEGORIES) {
                result.add(`${family}.${category}`);
            }
        }
    }
    return [...result];
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
