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
