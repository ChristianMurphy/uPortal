import { describe, expect, it } from 'vite-plus/test';
import { createEvent, isValidEventName } from './events';

describe('event names', () => {
    it.each(['up-favorite-removed', 'up-favorites-loaded', 'up-channel-state-changed'])(
        'accepts %s',
        (name) => {
            expect(isValidEventName(name)).toBe(true);
        },
    );

    it.each([
        'removed',
        'up-removed',
        'favorite-removed',
        'up-Favorite-removed',
        'up-favorite_removed',
    ])('refuses %s', (name) => {
        expect(isValidEventName(name)).toBe(false);
        expect(() => createEvent(name, {})).toThrow(/naming convention/);
    });

    it('creates bubbling, composed, non-cancelable events with a detail', () => {
        const event = createEvent('up-favorite-removed', { nodeId: 'n1' });

        expect(event.type).toBe('up-favorite-removed');
        expect(event.detail).toEqual({ nodeId: 'n1' });
        expect(event.bubbles).toBe(true);
        expect(event.composed).toBe(true);
        expect(event.cancelable).toBe(false);
    });
});
