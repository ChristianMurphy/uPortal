/**
 * Keyboard handling that works with input methods (MOD-D51). While an input method composes text
 * (Chinese pinyin, Japanese kana to kanji, Korean jamo), the keys it uses, Enter and the arrows
 * among them, choose a candidate. They must not submit a form or move anything on the page.
 */

/**
 * True while an input method is composing. Some browsers send the keydown that starts or ends a
 * composition with `isComposing` false and `keyCode` 229, the code reserved for input methods.
 */
export function isComposing(event: KeyboardEvent): boolean {
    return event.isComposing || event.keyCode === 229;
}

/** A keydown handler that ignores the keys an input method is composing with. */
export function ignoreComposing<E extends KeyboardEvent>(
    handler: (event: E) => void,
): (event: E) => void {
    return (event) => {
        if (!isComposing(event)) {
            handler(event);
        }
    };
}
