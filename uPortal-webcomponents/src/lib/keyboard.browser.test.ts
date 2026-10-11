import { describe, expect, it, vi } from 'vite-plus/test';
import { ignoreComposing, isComposing } from './keyboard';

function keydown(init: KeyboardEventInit & { keyCode?: number }): KeyboardEvent {
    return new KeyboardEvent('keydown', init);
}

describe('isComposing', () => {
    it('is true while an input method composes', () => {
        expect(isComposing(keydown({ key: 'Enter', isComposing: true }))).toBe(true);
        // The keydown that ends a composition in some browsers
        expect(isComposing(keydown({ key: 'Process', keyCode: 229 }))).toBe(true);
    });

    it('is false for other keys', () => {
        expect(isComposing(keydown({ key: 'Enter' }))).toBe(false);
        expect(isComposing(keydown({ key: 'ArrowUp', altKey: true, keyCode: 38 }))).toBe(false);
    });
});

describe('ignoreComposing', () => {
    it('passes other keys to the handler', () => {
        const handler = vi.fn();
        const event = keydown({ key: 'Enter' });
        ignoreComposing(handler)(event);
        expect(handler).toHaveBeenCalledWith(event);
    });

    it('drops the keys an input method composes with', () => {
        const handler = vi.fn();
        const wrapped = ignoreComposing(handler);
        wrapped(keydown({ key: 'Enter', isComposing: true }));
        wrapped(keydown({ key: 'ArrowDown', altKey: true, isComposing: true }));
        wrapped(keydown({ key: 'Process', keyCode: 229 }));
        expect(handler).not.toHaveBeenCalled();
    });
});
