/**
 * Text that must survive every input and API untouched (MOD-D50): accented letters, a non-Latin
 * script and a four-byte character, which is two UTF-16 units.
 */
export const UNICODE_QUERY = 'Zoë Ñandú 李小龍 😀';

/** {@link UNICODE_QUERY} as a URL query value: UTF-8, percent-encoded once. */
export const UNICODE_QUERY_ENCODED =
    'Zo%C3%AB+%C3%91and%C3%BA+%E6%9D%8E%E5%B0%8F%E9%BE%8D+%F0%9F%98%80';
