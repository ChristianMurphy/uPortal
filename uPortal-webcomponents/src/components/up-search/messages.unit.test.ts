import { describe, expect, it } from 'vite-plus/test';
import properties from '../../../../uPortal-webapp/src/main/resources/properties/i18n/Messages.properties?raw';
import { SEARCH_MESSAGES, SHOWN_ATTRIBUTES } from './messages';

/** The portal's English messages, which the defaults repeat. */
function portalMessages(): Map<string, string> {
    const messages = new Map<string, string>();
    for (const line of properties.split('\n')) {
        const match = /^([^#=\s][^=]*)=(.*)$/.exec(line);
        if (match?.[1] !== undefined && match[2] !== undefined) {
            messages.set(match[1], match[2]);
        }
    }
    return messages;
}

describe('search messages', () => {
    it('are the English messages of Messages.properties', () => {
        const portal = portalMessages();
        for (const [key, text] of Object.entries(SEARCH_MESSAGES)) {
            expect(portal.get(key), key).toBe(text);
        }
    });

    it('label every shown attribute', () => {
        for (const key of Object.values(SHOWN_ATTRIBUTES)) {
            expect(SEARCH_MESSAGES[key]).toBeTruthy();
        }
    });
});
