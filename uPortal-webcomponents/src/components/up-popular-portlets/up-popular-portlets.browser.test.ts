import { afterEach, beforeEach, describe, expect, it } from 'vite-plus/test';
import { page, userEvent } from 'vite-plus/test/browser';
import { clearMessageCache, messageKeys, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { POPULAR_PORTLETS_MESSAGES } from './messages';
import { POPULAR_PORTLETS_PATH } from './popular-portlets-api';
import './up-popular-portlets';
import type { PopularPortletsLoadedDetail, UpPopularPortlets } from './up-popular-portlets';

const API = `/uPortal${POPULAR_PORTLETS_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="popular-portlets"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

function portlet(fname: string, title: string, count: number, description: string | null = null) {
    return { fname, title, description, url: `/uPortal/p/${fname}`, count };
}

const COURSES = portlet('courses', 'Courses', 1, 'Course links and announcements.');
const CALENDAR = portlet('calendar', 'Calendar', 5, 'Your events');
const XKCD = portlet('xkcd', 'XKCD', 2);
const THREE = [CALENDAR, XKCD, COURSES];

/** Twelve portlets, `p00` to `p11`, added 1 to 12 times. */
const TWELVE = Array.from({ length: 12 }, (_, index) =>
    portlet(`p${String(index).padStart(2, '0')}`, `Portlet ${index}`, index + 1),
);

function body(portlets: object[], days = 30) {
    return { days, portlets };
}

let server: FetchStub;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', API, (request) => {
            const days = Number(new URL(request.url, location.href).searchParams.get('days'));
            return json(body(THREE, days));
        });
});

afterEach(() => {
    cleanupFixtures();
});

async function render(attributes = ATTRIBUTES): Promise<UpPopularPortlets> {
    const element = await fixture<UpPopularPortlets>(
        `<up-popular-portlets ${attributes}></up-popular-portlets>`,
    );
    await expect.poll(() => element.shadowRoot?.querySelector('.loading')).toBeNull();
    return element;
}

function shadow(element: UpPopularPortlets): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function find(element: UpPopularPortlets, selector: string): HTMLElement {
    const found = shadow(element).querySelector<HTMLElement>(selector);
    if (!found) {
        throw new Error(`nothing matches ${selector}`);
    }
    return found;
}

function select(element: UpPopularPortlets, selector: string): HTMLSelectElement {
    const found = find(element, selector);
    if (!(found instanceof HTMLSelectElement)) {
        throw new TypeError(`${selector} is no select`);
    }
    return found;
}

function alertText(element: UpPopularPortlets): string | null | undefined {
    return shadow(element).querySelector('[role="alert"]')?.textContent;
}

/** The fnames of the rows shown, in order. */
function fnames(element: UpPopularPortlets): string[] {
    return [...shadow(element).querySelectorAll('tbody tr[data-fname]')].map(
        (row) => row.getAttribute('data-fname') ?? '',
    );
}

/** The paging text, as screen readers and text matching get it: without surrounding space. */
function info(element: UpPopularPortlets): string {
    return find(element, '.info').textContent ?? '';
}

function view(element: UpPopularPortlets) {
    return page.elementLocator(element);
}

async function choose(target: HTMLSelectElement, value: string): Promise<void> {
    await userEvent.selectOptions(target, value);
}

describe('up-popular-portlets states', () => {
    it('shows a loading state until the portlets arrive, the controls already there', async () => {
        let answer: ((response: Response) => void) | undefined;
        server.on(
            'GET',
            API,
            () =>
                new Promise<Response>((resolve) => {
                    answer = resolve;
                }),
        );
        const element = await fixture<UpPopularPortlets>(
            `<up-popular-portlets ${ATTRIBUTES}></up-popular-portlets>`,
        );

        const loading = find(element, '.loading');
        expect(loading.getAttribute('role')).toBe('status');
        expect(loading.textContent).toContain('Loading');
        await expect
            .element(view(element).getByRole('combobox', { name: 'Previous' }))
            .toBeVisible();
        expect(shadow(element).querySelector('table')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);

        answer?.(json(body(THREE)));
        await expect.poll(() => fnames(element)).toEqual(['calendar', 'courses', 'xkcd']);
    });

    it('lists the portlets by fname with links, descriptions and counts', async () => {
        const element = await render();

        await expect
            .element(view(element).getByRole('heading', { name: 'Most Frequently Added' }))
            .toBeVisible();
        const courses = view(element).getByRole('link', { name: 'Courses' });
        await expect.element(courses).toHaveAttribute('href', '/uPortal/p/courses');
        await expect.element(courses).toHaveAttribute('title', 'Course links and announcements.');
        // No description, no title attribute
        expect(find(element, 'tr[data-fname="xkcd"] a').hasAttribute('title')).toBe(false);
        expect(
            [...shadow(element).querySelectorAll('tbody td:nth-child(2)')].map(
                (cell) => cell.textContent,
            ),
        ).toEqual(['5', '1', '2']);
        expect(info(element)).toBe('Viewing page 1. Showing records 1 to 3 of 3 items.');
        expect(find(element, 'th[aria-sort]').textContent).toContain('Title');
        expect(find(element, 'th[aria-sort]').getAttribute('aria-sort')).toBe('ascending');
        expect(find(element, '.note').textContent).toBe(
            '* These values are provided by daily statistics aggregation',
        );
        expect(await axeViolations(element)).toEqual([]);
    });

    it('shows a portlet without a usable link as text', async () => {
        server.on('GET', API, json(body([{ ...XKCD, url: 'javascript:alert(1)' }])));
        const element = await render();

        expect(find(element, 'tr[data-fname="xkcd"] td').textContent?.trim()).toBe('XKCD');
        expect(shadow(element).querySelector('tbody a')).toBeNull();
    });

    it('says when no portlet was added', async () => {
        server.on('GET', API, json(body([])));
        const element = await render();

        expect(find(element, 'td.none').textContent?.trim()).toBe('No data available in table');
        expect(info(element)).toBe('Viewing page 1. Showing records 0 to 0 of 0 items.');
        expect(shadow(element).querySelector('.filters')).toBeNull();
        expect(select(element, '#page-size').disabled).toBe(true);
        expect(find(element, '.page-link.next').getAttribute('aria-disabled')).toBe('true');
        expect(await axeViolations(element)).toEqual([]);
    });

    it.each([
        ['a server error', () => text('Internal Server Error', 500)],
        ['a refusal', () => text('Forbidden', 403)],
        ['a body that is not JSON', () => text('<html></html>')],
        ['a body of the wrong shape', () => json({ portlets: 'none' })],
        ['a network failure', () => Promise.reject(new TypeError('Failed to fetch'))],
    ])('offers a retry after %s', async (_name, respond) => {
        server.on('GET', API, respond);
        const element = await render();

        await expect
            .poll(() => alertText(element))
            .toContain('The popular portlets could not be loaded.');
        expect(await axeViolations(element)).toEqual([]);

        server.on('GET', API, json(body(THREE)));
        await userEvent.click(view(element).getByRole('button', { name: 'Try again' }));
        await expect.poll(() => fnames(element)).toEqual(['calendar', 'courses', 'xkcd']);
    });

    it('asks to sign in again after a 401, without a retry', async () => {
        server.on('GET', API, text('', 401));
        const element = await render();

        await expect.poll(() => alertText(element)).toContain('Your session has expired.');
        expect(shadow(element).querySelector('.retry')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });
});

describe('up-popular-portlets days', () => {
    it('loads the last 30 days in the element locale', async () => {
        await render();
        const [request] = server.requestsTo(API);
        const query = new URL(request?.url ?? '', location.href).searchParams;
        expect([query.get('days'), query.get('locale')]).toEqual(['30', 'en-US']);
        expect(request?.method).toBe('GET');
    });

    it('offers 1, 7, 30, 90 and 365 days and loads the chosen ones', async () => {
        const element = await render();
        const days = select(element, '#days');
        expect([...days.options].map((option) => option.value)).toEqual([
            '1',
            '7',
            '30',
            '90',
            '365',
        ]);
        expect(days.value).toBe('30');

        server.on('GET', API, json(body([XKCD], 7)));
        days.focus();
        await choose(days, '7');
        await expect.poll(() => fnames(element)).toEqual(['xkcd']);
        const last = server.requestsTo(API).at(-1);
        expect(new URL(last?.url ?? '', location.href).searchParams.get('days')).toBe('7');
        expect(days.value).toBe('7');
        // The choice keeps focus while the portlets load
        expect(shadow(element).activeElement).toBe(days);
    });

    it('starts with the days of its days attribute, and loads when it changes', async () => {
        const element = await render(`${ATTRIBUTES} days="90"`);
        expect(select(element, '#days').value).toBe('90');

        element.days = 365;
        await expect
            .poll(() => server.requestsTo(API).map((r) => r.url.match(/days=(\d+)/)?.[1]))
            .toEqual(['90', '365']);
    });

    it('loads the titles again when the locale changes', async () => {
        const element = await render();
        element.locale = 'fr-CA';
        await expect
            .poll(() => server.requestsTo(API).map((r) => r.url.match(/locale=([\w-]+)/)?.[1]))
            .toEqual(['en-US', 'fr-CA']);
    });

    it('uses a new context path for the next load', async () => {
        const element = await render();
        server.on('GET', `/portal${POPULAR_PORTLETS_PATH}`, json(body([COURSES])));
        element.contextPath = '/portal';
        await element.updateComplete;
        await choose(select(element, '#days'), '90');
        await expect.poll(() => fnames(element)).toEqual(['courses']);
    });
});

describe('up-popular-portlets sorting', () => {
    it('sorts by a column header, then turns the order around', async () => {
        const element = await render();
        const times = view(element).getByRole('button', { name: '# Times' });

        await userEvent.click(times);
        expect(fnames(element)).toEqual(['courses', 'xkcd', 'calendar']);
        expect(find(element, 'th[aria-sort]').textContent).toContain('# Times');
        expect(find(element, 'th[aria-sort]').getAttribute('aria-sort')).toBe('ascending');

        await userEvent.click(times);
        expect(fnames(element)).toEqual(['calendar', 'xkcd', 'courses']);
        expect(find(element, 'th[aria-sort]').getAttribute('aria-sort')).toBe('descending');

        await userEvent.click(view(element).getByRole('button', { name: 'Title' }));
        expect(fnames(element)).toEqual(['calendar', 'courses', 'xkcd']);
        expect(shadow(element).querySelectorAll('th[aria-sort]')).toHaveLength(1);
        expect(await axeViolations(element)).toEqual([]);
    });

    it('sorts from the keyboard', async () => {
        const element = await render();
        find(element, 'button[data-sort="title"]').focus();
        await userEvent.keyboard('{Enter}');
        expect(fnames(element)).toEqual(['xkcd', 'courses', 'calendar']);
        await userEvent.tab();
        await userEvent.keyboard(' ');
        expect(fnames(element)).toEqual(['courses', 'xkcd', 'calendar']);
    });
});

describe('up-popular-portlets paging', () => {
    beforeEach(() => {
        server.on('GET', API, json(body(TWELVE)));
    });

    it('shows ten rows per page and pages with Next and Previous', async () => {
        const element = await render();
        expect(fnames(element)).toHaveLength(10);
        expect(info(element)).toBe('Viewing page 1. Showing records 1 to 10 of 12 items.');
        const previous = view(element).getByLabelText('Previous', { exact: true }).last();
        const next = view(element).getByLabelText('Next', { exact: true });
        await expect.element(previous).toHaveTextContent('< Previous');
        await expect.element(next).toHaveTextContent('Next >');
        await expect.element(previous).toHaveAttribute('aria-disabled', 'true');
        await expect.element(previous).toHaveAttribute('tabindex', '-1');

        await userEvent.click(next);
        expect(info(element)).toBe('Viewing page 2. Showing records 11 to 12 of 12 items.');
        expect(fnames(element)).toEqual(['p10', 'p11']);
        await expect.element(next).toHaveAttribute('aria-disabled', 'true');
        expect(find(element, '[aria-current="page"]').textContent?.trim()).toBe('2');
        expect(await axeViolations(element)).toEqual([]);

        // A disabled control does nothing
        find(element, '.page-link.next').click();
        await element.updateComplete;
        expect(info(element)).toMatch(/^Viewing page 2\./);

        await userEvent.click(previous);
        expect(info(element)).toMatch(/^Viewing page 1\./);
    });

    it('changes the rows per page, back on the first page', async () => {
        const element = await render();
        const size = select(element, '#page-size');
        expect([...size.options].map((option) => option.value)).toEqual(['5', '10', '20', '50']);
        expect(size.value).toBe('10');
        await expect
            .element(view(element).getByRole('combobox', { name: 'per page' }))
            .toBeVisible();

        await choose(size, '5');
        expect(info(element)).toBe('Viewing page 1. Showing records 1 to 5 of 12 items.');
        await userEvent.click(find(element, 'button[data-page="2"]'));
        expect(info(element)).toBe('Viewing page 3. Showing records 11 to 12 of 12 items.');

        await choose(size, '20');
        expect(info(element)).toBe('Viewing page 1. Showing records 1 to 12 of 12 items.');
        expect(shadow(element).querySelectorAll('button[data-page]')).toHaveLength(1);
    });

    it('shows a gap among many pages', async () => {
        server.on(
            'GET',
            API,
            json(body(Array.from({ length: 50 }, (_, index) => portlet(`q${index}`, 'Q', 1)))),
        );
        const element = await render();
        await choose(select(element, '#page-size'), '5');

        expect(
            [...shadow(element).querySelectorAll('.pagination .page-link')].map((link) =>
                link.textContent?.trim(),
            ),
        ).toEqual(['< Previous', '1', '2', '3', '4', '5', '…', '10', 'Next >']);
        expect(await axeViolations(element)).toEqual([]);
    });
});

describe('up-popular-portlets filters', () => {
    it('offers each fname and count, the column name first', async () => {
        const element = await render();
        await expect.element(view(element).getByText('Filters', { exact: true })).toBeVisible();
        const title = select(element, 'select[data-filter="title"]');
        const count = select(element, 'select[data-filter="count"]');
        expect([...title.options].map((option) => option.textContent?.trim())).toEqual([
            'Title',
            'calendar',
            'courses',
            'xkcd',
        ]);
        expect([...count.options].map((option) => option.textContent?.trim())).toEqual([
            '# Times',
            '1',
            '2',
            '5',
        ]);
        expect(title.getAttribute('aria-label')).toBe('Filter by Title');
        expect(title.disabled).toBe(false);
    });

    it('disables a filter with one value to choose', async () => {
        server.on('GET', API, json(body([CALENDAR, { ...XKCD, count: 5 }])));
        const element = await render();
        expect(select(element, 'select[data-filter="count"]').disabled).toBe(true);
    });

    it('keeps the rows of the chosen values, and removes a value with its term', async () => {
        const element = await render();
        const title = select(element, 'select[data-filter="title"]');

        await choose(title, 'courses');
        expect(title.value).toBe('');
        expect(fnames(element)).toEqual(['courses']);
        expect(info(element)).toBe('Viewing page 1. Showing records 1 to 1 of 1 items.');
        const term = view(element).getByRole('button', { name: 'Remove the filter courses' });
        await expect.element(term).toBeVisible();
        expect(find(element, '.filter-term').textContent).toContain('courses');

        await choose(title, 'xkcd');
        expect(fnames(element)).toEqual(['courses', 'xkcd']);

        await choose(select(element, 'select[data-filter="count"]'), '5');
        expect(fnames(element)).toEqual([]);
        expect(find(element, 'td.none').textContent?.trim()).toBe('No matching records found');
        expect(await axeViolations(element)).toEqual([]);

        await userEvent.click(view(element).getByRole('button', { name: 'Remove the filter 5' }));
        expect(fnames(element)).toEqual(['courses', 'xkcd']);
        // Focus moves from the removed term to its filter
        expect(shadow(element).activeElement?.getAttribute('data-filter')).toBe('count');

        await userEvent.click(term);
        await userEvent.click(
            view(element).getByRole('button', { name: 'Remove the filter xkcd' }),
        );
        expect(fnames(element)).toEqual(['calendar', 'courses', 'xkcd']);
        expect(shadow(element).activeElement?.getAttribute('data-filter')).toBe('title');
    });

    it('keeps the rows it names after a sort (the 5.17 portlet showed other rows)', async () => {
        const element = await render();
        await userEvent.click(view(element).getByRole('button', { name: '# Times' }));
        expect(fnames(element)).toEqual(['courses', 'xkcd', 'calendar']);

        await choose(select(element, 'select[data-filter="title"]'), 'calendar');

        expect(fnames(element)).toEqual(['calendar']);
        expect(find(element, 'tbody a').textContent).toBe('Calendar');
    });

    it('keeps the filters when other days load', async () => {
        const element = await render();
        await choose(select(element, 'select[data-filter="title"]'), 'xkcd');
        await choose(select(element, '#days'), '365');
        await expect.poll(() => server.requestsTo(API)).toHaveLength(2);
        await expect.poll(() => fnames(element)).toEqual(['xkcd']);
    });
});

describe('up-popular-portlets messages and events', () => {
    it('uses the messages the portal returns, also those written as HTML', async () => {
        server.on(
            'GET',
            `/uPortal${MESSAGES_PATH}`,
            json({
                messages: {
                    'most.frequently.added': 'Les plus ajoutés',
                    'datatables.paginate.previous': '&lt; Précédent',
                    'popular.portlets.page.info.other': 'Page {0} : {1} à {2} sur {3}.',
                },
            }),
        );
        const element = await render();

        await expect
            .element(view(element).getByRole('heading', { name: 'Les plus ajoutés' }))
            .toBeVisible();
        expect(find(element, '.page-link.previous').textContent?.trim()).toBe('< Précédent');
        expect(info(element)).toBe('Page 1 : 1 à 3 sur 3.');
        const [request] = server.requestsTo(`/uPortal${MESSAGES_PATH}`);
        const keys = new URL(request?.url ?? '', location.href).searchParams.get('keys');
        // With every plural category of popular.portlets.page.info, for the user's language
        expect(keys?.split(',').toSorted()).toEqual(
            messageKeys(Object.keys(POPULAR_PORTLETS_MESSAGES)).toSorted(),
        );
    });

    it('picks the plural form of the page info for the locale, also one English lacks', async () => {
        // A language a site adds with only a bundle: Polish has one, few, many and other
        server.on(
            'GET',
            `/uPortal${MESSAGES_PATH}`,
            json({
                messages: {
                    'popular.portlets.page.info.one': 'Strona {0}: {1}–{2} z {3} pozycji (one)',
                    'popular.portlets.page.info.few': 'Strona {0}: {1}–{2} z {3} pozycji (few)',
                    'popular.portlets.page.info.many': 'Strona {0}: {1}–{2} z {3} pozycji (many)',
                    'popular.portlets.page.info.other': 'Strona {0}: {1}–{2} z {3} pozycji (other)',
                },
            }),
        );
        const element = await render(ATTRIBUTES.replace('locale="en-US"', 'locale="pl"'));

        // Three portlets: "few" in Polish
        await expect.poll(() => info(element)).toBe('Strona 1: 1–3 z 3 pozycji (few)');
    });

    it('announces each load with up-popular-portlets-loaded', async () => {
        const details: PopularPortletsLoadedDetail[] = [];
        document.addEventListener('up-popular-portlets-loaded', (event) => {
            details.push((event as CustomEvent<PopularPortletsLoadedDetail>).detail);
        });
        const element = await render();
        await choose(select(element, '#days'), '1');
        await expect
            .poll(() => details)
            .toEqual([
                { days: 30, portlets: 3 },
                { days: 1, portlets: 3 },
            ]);
    });
});

describe('up-popular-portlets on the page', () => {
    it('keeps its choices when it moves in the page', async () => {
        server.on('GET', API, json(body(TWELVE)));
        const element = await render();
        await choose(select(element, '#page-size'), '5');
        await userEvent.click(find(element, 'button[data-page="1"]'));
        const parent = element.parentElement;

        element.remove();
        parent?.append(element);
        await element.updateComplete;

        expect(info(element)).toBe('Viewing page 2. Showing records 6 to 10 of 12 items.');
        expect(server.requestsTo(API)).toHaveLength(1);
    });

    it('reloads on request', async () => {
        const element = await render();
        server.on('GET', API, json(body([XKCD])));

        await element.reload();

        expect(fnames(element)).toEqual(['xkcd']);
    });

    it('resolves a reload when the element leaves the page before the portlets arrive', async () => {
        const element = await render();
        server.on('GET', API, () => new Promise<Response>(() => undefined));

        const reloaded = element.reload();
        element.remove();

        await expect(reloaded).resolves.toBeUndefined();
    });

    it('queues a reload asked for while the element is out of the page', async () => {
        const element = await render();
        const parent = element.parentElement;
        element.remove();

        await element.reload();
        parent?.append(element);

        await expect.poll(() => server.requestsTo(API)).toHaveLength(2);
    });
});
