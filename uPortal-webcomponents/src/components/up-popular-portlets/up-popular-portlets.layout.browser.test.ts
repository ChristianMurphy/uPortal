import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vite-plus/test';
import { page } from 'vite-plus/test/browser';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { FetchStub, json } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { CONTAINER_WIDTHS, layoutProblems, setContainerWidth } from '../../test-support/layout';
import { POPULAR_PORTLETS_PATH } from './popular-portlets-api';
import './up-popular-portlets';
import type { UpPopularPortlets } from './up-popular-portlets';

/*
 * The layout checks of MOD-D52 (test-support/layout.ts) on the component in a portlet column
 * of each standard container width, with a long title and description in the table.
 */

const PORTLETS = [
    {
        fname: 'registration',
        title: 'Course Registration and Academic Planning Worksheet',
        description: 'Plan next term, register for courses and see what each one requires.',
        url: '/uPortal/p/registration',
        count: 12,
    },
    {
        fname: 'calendar',
        title: 'Calendar',
        description: null,
        url: '/uPortal/p/calendar',
        count: 5,
    },
];

beforeAll(async () => {
    await page.viewport(1366, 900);
});

beforeEach(() => {
    clearMessageCache();
    new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', `/uPortal${POPULAR_PORTLETS_PATH}`, json({ days: 30, portlets: PORTLETS }));
});

afterEach(() => {
    cleanupFixtures();
});

describe.each(CONTAINER_WIDTHS)('up-popular-portlets in a %ipx container', (width) => {
    it.each(['normal', 'maximized'])('fits when %s', async (state) => {
        const element = await fixture<UpPopularPortlets>(
            `<up-popular-portlets locale="en-US" window-state="${state}" context-path="/uPortal"` +
                ' fname="popular-portlets" csrf-header="X-CSRF-TOKEN" csrf-token="token-1">' +
                '</up-popular-portlets>',
        );
        await expect.poll(() => element.shadowRoot?.querySelector('.loading')).toBeNull();
        const container = await setContainerWidth(element, width);
        expect(layoutProblems(container)).toEqual([]);
    });
});
