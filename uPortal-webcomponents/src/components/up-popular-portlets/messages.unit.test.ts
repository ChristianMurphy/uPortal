import { describe, expect, it } from 'vite-plus/test';
import { decodeEntities, POPULAR_PORTLETS_MESSAGES } from './messages';

describe('decodeEntities', () => {
    it('turns the entities Messages.properties uses into text', () => {
        expect(decodeEntities('&lt; Previous')).toBe('< Previous');
        expect(decodeEntities('Next &gt;')).toBe('Next >');
        expect(decodeEntities('&quot;A&quot; &#39;B&#39; C &amp; D')).toBe(`"A" 'B' C & D`);
    });

    it('decodes once and leaves other text alone', () => {
        expect(decodeEntities('&amp;lt; &nbsp; 5 & 6')).toBe('&lt; &nbsp; 5 & 6');
    });

    it('has English defaults that need no decoding', () => {
        for (const message of Object.values(POPULAR_PORTLETS_MESSAGES)) {
            expect(decodeEntities(message)).toBe(message);
        }
    });
});
