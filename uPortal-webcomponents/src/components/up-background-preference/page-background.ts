/**
 * Puts the chosen image behind the page, outside the component's shadow root: the background
 * belongs to an element of the portal page (by default `#portalPageBody::before`).
 *
 * The rule lives in one constructable stylesheet per document, adopted once into
 * `document.adoptedStyleSheets` and replaced on every change, so the page never gets more than one
 * and a Content-Security-Policy that forbids inline `<style>` elements does not block it. The URL
 * comes from the portal's list of offered images; it is still escaped for CSS `url()`, and the
 * value is set through the CSSOM, so it cannot add rules.
 */

const sheets = new WeakMap<Document, CSSStyleSheet>();

/** `url("...")` with the characters that could end the string escaped as CSS hex escapes. */
export function cssUrl(url: string): string {
    const escaped = url.replace(
        /["\\\n\r\f]/g,
        (character) => `\\${character.charCodeAt(0).toString(16)} `,
    );
    return `url("${escaped}")`;
}

/** The document's background sheet, adopted on first use. */
export function backgroundSheet(document: Document): CSSStyleSheet {
    const existing = sheets.get(document);
    if (existing !== undefined) {
        return existing;
    }
    const sheet = new CSSStyleSheet();
    sheets.set(document, sheet);
    document.adoptedStyleSheets = [...document.adoptedStyleSheets, sheet];
    return sheet;
}

/**
 * Shows `url` as the background of the elements `selector` matches, or, with no URL, removes the
 * rule so the skin's own background shows. Returns false when the selector is not valid CSS, in
 * which case nothing is applied.
 */
export function applyPageBackground(
    document: Document,
    selector: string,
    url: string | undefined,
): boolean {
    const sheet = backgroundSheet(document);
    sheet.replaceSync('');
    if (url === undefined) {
        return true;
    }
    try {
        // insertRule takes exactly one rule: a selector that tries to add more is refused
        sheet.insertRule(`${selector} {}`);
    } catch {
        return false;
    }
    const rule = sheet.cssRules[0];
    if (!(rule instanceof CSSStyleRule)) {
        sheet.replaceSync('');
        return false;
    }
    rule.style.setProperty('background-image', cssUrl(url));
    return true;
}
