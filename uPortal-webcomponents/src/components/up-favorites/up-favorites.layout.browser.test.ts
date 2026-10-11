import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vite-plus/test';
import { page } from 'vite-plus/test/browser';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { FetchStub, json } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { CONTAINER_WIDTHS, layoutProblems, setContainerWidth } from '../../test-support/layout';
import { FAVORITES_PATH } from './favorites-api';
import './up-favorites';
import type { UpFavorites } from './up-favorites';

/*
 * The layout checks of MOD-D52 (test-support/layout.ts) on the component in a portlet column
 * of each standard container width, with a long name that has to wrap or end in an ellipsis.
 */

function portlet(nodeId: string, fname: string, name: string) {
    return {
        nodeId,
        fname,
        name,
        description: `About ${name}, with a description long enough to wrap in a narrow column`,
        url: `/uPortal/p/${fname}/render.uP`,
        target: null,
        iconUrl: null,
        moveAllowed: true,
        deleteAllowed: true,
    };
}

const FAVORITES = {
    collections: [
        {
            nodeId: 'u12l1s7',
            name: 'News',
            url: '/uPortal/f/u12l1s7/render.uP',
            moveAllowed: true,
            deleteAllowed: true,
        },
    ],
    portlets: [
        portlet('u12l1n20', 'calendar', 'Calendar'),
        portlet('u12l1n21', 'registration', 'Course Registration and Academic Planning Worksheet'),
    ],
    marketplaceUrl: '/uPortal/p/portletmarketplace/max/render.uP',
};

beforeAll(async () => {
    await page.viewport(1366, 900);
});

beforeEach(() => {
    clearMessageCache();
    new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', `/uPortal${FAVORITES_PATH}`, json(FAVORITES));
});

afterEach(() => {
    cleanupFixtures();
});

describe.each(CONTAINER_WIDTHS)('up-favorites in a %ipx container', (width) => {
    it.each(['normal', 'maximized'])('fits when %s', async (state) => {
        const element = await fixture<UpFavorites>(
            `<up-favorites locale="en-US" window-state="${state}" context-path="/uPortal"` +
                ' fname="favorites" csrf-header="X-CSRF-TOKEN" csrf-token="token-1"></up-favorites>',
        );
        await expect.poll(() => element.shadowRoot?.querySelector('.loading')).toBeNull();
        const container = await setContainerWidth(element, width);
        expect(layoutProblems(container)).toEqual([]);
    });
});
