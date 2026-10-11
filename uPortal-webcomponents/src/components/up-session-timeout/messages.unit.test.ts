import { describe, expect, it } from 'vite-plus/test';
import { countdownMessageKey } from './messages';

describe('countdownMessageKey', () => {
    it.each([
        ['en-US', 1, 'session-timeout.body1.one'],
        ['en-US', 0, 'session-timeout.body1'],
        ['en-US', 2, 'session-timeout.body1'],
        ['es-ES', 1, 'session-timeout.body1.one'],
        ['es-ES', 0, 'session-timeout.body1'],
        ['es-ES', 60, 'session-timeout.body1'],
        ['fr-FR', 0, 'session-timeout.body1.one'],
        ['fr-FR', 1, 'session-timeout.body1.one'],
        ['fr-FR', 2, 'session-timeout.body1'],
    ])('in %s, %i seconds use %s', (locale, seconds, key) => {
        expect(countdownMessageKey(locale, seconds)).toBe(key);
    });

    it('reads a locale the browser rejects as English', () => {
        expect(countdownMessageKey('fr_FR!', 0)).toBe('session-timeout.body1');
        expect(countdownMessageKey('fr_FR!', 1)).toBe('session-timeout.body1.one');
    });
});
