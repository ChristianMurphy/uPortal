import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { page, userEvent } from 'vite-plus/test/browser';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { createNavigation, navigation, portletUrl } from './navigation';
import { PORTLET_LIST_PATH, SEARCH_PATH } from './search-api';
import './up-search';
import { parseResultTypes, startCase, type UpSearch } from './up-search';
import type { UpSearchBox } from './up-search-box';
import { searchUrl, type UpSearchLauncher } from './up-search-launcher';

const SEARCH_API = `/uPortal${SEARCH_PATH}`;
const LIST_API = `/uPortal${PORTLET_LIST_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="maximized" context-path="/uPortal" fname="search"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

const STEVEN = {
    title: 'Steven Student',
    url: null,
    attributes: {
        uid: ['student'],
        telephoneNumber: ['(555) 555-5555'],
        mail: ['steven.student@example.org'],
        displayName: ['Steven Student'],
        givenName: ['Steven'],
        sn: ['Student'],
        username: ['student'],
    },
};

const CARTOON = {
    title: 'Daily Business Cartoon',
    url: '/uPortal/p/daily-business-cartoon.ctf3/max/render.uP',
    attributes: {
        description: ['Daily Business Cartoon by Ted Goff, www.tedgoff.com'],
        favorite: ['false'],
        fname: ['daily-business-cartoon'],
        name: ['Daily Business Cartoon'],
        score: ['4.0'],
        title: ['Daily Business Cartoon'],
    },
};

function searchResults(query: string, people: object[] = [], portlets: object[] = [CARTOON]) {
    return {
        query,
        groups: [
            { type: 'people', results: people },
            { type: 'portlets', results: portlets },
        ],
    };
}

const REGISTRY = {
    registry: {
        channels: [],
        categories: [
            {
                channels: [
                    {
                        id: '3',
                        fname: 'daily-business-cartoon',
                        name: 'Daily Business Cartoon',
                        title: 'Daily Business Cartoon',
                        description: 'Daily Business Cartoon by Ted Goff, www.tedgoff.com',
                    },
                    {
                        id: '7',
                        fname: 'calendar',
                        name: 'Calendar',
                        title: 'Calendar',
                        description: null,
                    },
                    {
                        id: '8',
                        fname: 'courses',
                        name: 'Courses',
                        title: 'Courses',
                        description: 'Classes',
                    },
                ],
                categories: [],
            },
        ],
    },
};

let server: FetchStub;
let assign: ReturnType<typeof vi.spyOn>;
let replaceQuery: ReturnType<typeof vi.spyOn>;
let pageQuery: string | null;

beforeEach(() => {
    clearMessageCache();
    pageQuery = null;
    assign = vi.spyOn(navigation, 'assign').mockImplementation(() => {});
    replaceQuery = vi.spyOn(navigation, 'replaceQuery').mockImplementation(() => {});
    vi.spyOn(navigation, 'query').mockImplementation(() => pageQuery);
    server = new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', LIST_API, json(REGISTRY))
        .on('GET', SEARCH_API, (request) =>
            json(searchResults(new URL(request.url, location.href).searchParams.get('q') ?? '')),
        );
});

afterEach(() => {
    cleanupFixtures();
    vi.restoreAllMocks();
});

/** The detail of the last event a listener mock received. */
function detailOf(listener: ReturnType<typeof vi.fn>): unknown {
    const event: unknown = listener.mock.lastCall?.[0];
    expect(event).toBeInstanceOf(CustomEvent);
    return (event as CustomEvent).detail;
}

function shadow(element: Element): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function boxOf(element: UpSearch | UpSearchLauncher): UpSearchBox {
    const box = shadow(element).querySelector('up-search-box');
    if (!box) {
        throw new Error('no search box');
    }
    return box;
}

function input(box: UpSearchBox): HTMLInputElement {
    const found = shadow(box).querySelector('input');
    if (!found) {
        throw new Error('no input');
    }
    return found;
}

function suggestionTitles(box: UpSearchBox): string[] {
    return [...shadow(box).querySelectorAll('[role="option"] .suggestion-title')].map(
        (node) => node.textContent ?? '',
    );
}

async function renderSearch(attributes = `${ATTRIBUTES} query="cartoon"`): Promise<UpSearch> {
    const element = await fixture<UpSearch>(`<up-search ${attributes}></up-search>`);
    await expect.poll(() => shadow(element).querySelector('.loading')).toBeNull();
    return element;
}

async function renderLauncher(attributes = ATTRIBUTES): Promise<UpSearchLauncher> {
    const element = await fixture<UpSearchLauncher>(
        `<up-search-launcher ${attributes}></up-search-launcher>`,
    );
    // The portlet list has loaded once a request for it was answered
    await expect.poll(() => server.requestsTo(LIST_API).length).toBe(1);
    return element;
}

async function typeInto(box: UpSearchBox, keys: string): Promise<void> {
    await userEvent.click(input(box));
    await userEvent.keyboard(keys);
}

describe('up-search-launcher', () => {
    it('shows a labelled search box and a submit button', async () => {
        const launcher = await renderLauncher();
        const locator = page.elementLocator(launcher);

        const box = locator.getByRole('searchbox', { name: 'Search Terms' });
        await expect.element(box).toHaveAttribute('placeholder', 'Search');
        await expect.element(box).toHaveAttribute('maxlength', '200');
        await expect.element(locator.getByRole('button', { name: 'Search' })).toBeVisible();
        await expect.element(locator.getByRole('search')).toBeVisible();
        expect(await axeViolations(launcher)).toEqual([]);
    });

    it('opens the search channel maximized with the query', async () => {
        const launcher = await renderLauncher();
        const box = boxOf(launcher);
        const submitted = vi.fn();
        launcher.addEventListener('up-search-submitted', submitted);

        await typeInto(box, ' tea & cake {Enter}');

        expect(assign).toHaveBeenCalledWith('/uPortal/p/search/max?query=tea+%26+cake');
        expect(submitted).toHaveBeenCalledTimes(1);
        expect(detailOf(submitted)).toEqual({
            query: 'tea & cake',
        });
    });

    it('sends the query to the search channel it names', async () => {
        const launcher = await renderLauncher(`${ATTRIBUTES} search-fname="find"`);

        await typeInto(boxOf(launcher), 'x');
        await userEvent.click(
            page.elementLocator(launcher).getByRole('button', { name: 'Search' }),
        );

        expect(assign).toHaveBeenCalledWith('/uPortal/p/find/max?query=x');
    });

    it('does not search for a blank query', async () => {
        const launcher = await renderLauncher();

        await typeInto(boxOf(launcher), '   {Enter}');
        await userEvent.click(
            page.elementLocator(launcher).getByRole('button', { name: 'Search' }),
        );

        expect(assign).not.toHaveBeenCalled();
    });

    it('works without a context path or locale, at the root of the site', async () => {
        server.on('GET', PORTLET_LIST_PATH, json(REGISTRY));
        const launcher = await fixture<UpSearchLauncher>(
            '<up-search-launcher></up-search-launcher>',
        );
        await expect.poll(() => server.requestsTo(PORTLET_LIST_PATH).length).toBe(1);

        await typeInto(boxOf(launcher), 'x{Enter}');

        expect(assign).toHaveBeenCalledWith('/p/search/max?query=x');
    });

    it('builds search urls', () => {
        expect(searchUrl('', 'search', 'a b')).toBe('/p/search/max?query=a+b');
        expect(portletUrl('/uPortal', 'a/b')).toBe('/uPortal/p/a%2Fb/max');
    });
});

describe('type-ahead', () => {
    it('suggests portlets after a pause, and announces how many', async () => {
        const launcher = await renderLauncher();
        const box = boxOf(launcher);

        await typeInto(box, 'cartoo');

        await expect.poll(() => suggestionTitles(box)).toEqual(['Daily Business Cartoon']);
        const root = shadow(box);
        expect(root.querySelector('.suggestion-description')?.textContent).toBe(
            'Daily Business Cartoon by Ted Goff, www.tedgoff.com',
        );
        expect(root.querySelector('[role="listbox"]')?.hasAttribute('hidden')).toBe(false);
        expect(root.querySelector('[role="status"]')?.textContent?.trim()).toBe(
            '1 results are available, use up and down arrow keys to navigate.',
        );
        expect(input(box).getAttribute('aria-autocomplete')).toBe('list');
        expect(input(box).getAttribute('aria-controls')).toBe('suggestions');
        expect(await axeViolations(launcher)).toEqual([]);
    });

    it('opens the portlet chosen with the mouse', async () => {
        const launcher = await renderLauncher();
        const box = boxOf(launcher);
        const chosen = vi.fn();
        launcher.addEventListener('up-suggestion-chosen', chosen);
        await typeInto(box, 'cal');
        await expect.poll(() => suggestionTitles(box)).toEqual(['Calendar']);

        await userEvent.click(page.elementLocator(box).getByRole('option', { name: /Calendar/ }));

        expect(assign).toHaveBeenCalledWith('/uPortal/p/calendar/max');
        expect(detailOf(chosen)).toEqual({
            fname: 'calendar',
            title: 'Calendar',
        });
        await expect.poll(() => suggestionTitles(box)).toEqual([]);
    });

    it('moves through the suggestions with the arrow keys and opens one with Enter', async () => {
        const launcher = await renderLauncher();
        const box = boxOf(launcher);
        await typeInto(box, 'c');
        await expect.poll(() => suggestionTitles(box)).toHaveLength(3);

        await userEvent.keyboard('{ArrowDown}{ArrowDown}');
        await box.updateComplete;
        expect(input(box).getAttribute('aria-activedescendant')).toBe('suggestion-1');
        expect(shadow(box).querySelector('#suggestion-1')?.getAttribute('aria-selected')).toBe(
            'true',
        );
        await userEvent.keyboard('{ArrowUp}{ArrowUp}');
        await box.updateComplete;
        expect(input(box).hasAttribute('aria-activedescendant')).toBe(false);
        await userEvent.keyboard('{ArrowUp}{Enter}');

        expect(assign).toHaveBeenCalledWith('/uPortal/p/courses/max');
    });

    it('closes with Escape and when the focus leaves, and Enter then searches', async () => {
        const launcher = await renderLauncher();
        const box = boxOf(launcher);
        await typeInto(box, 'c');
        await expect.poll(() => suggestionTitles(box)).toHaveLength(3);

        await userEvent.keyboard('{Escape}');
        await expect.poll(() => suggestionTitles(box)).toEqual([]);
        await userEvent.keyboard('a');
        await expect.poll(() => suggestionTitles(box)).toHaveLength(2);
        await userEvent.tab();
        await expect.poll(() => suggestionTitles(box)).toEqual([]);

        input(box).focus();
        await userEvent.keyboard('{Enter}');
        expect(assign).toHaveBeenCalledWith('/uPortal/p/search/max?query=ca');
    });

    it('stays closed when nothing matches', async () => {
        const launcher = await renderLauncher();
        const box = boxOf(launcher);

        await typeInto(box, 'zzqxjv');
        await new Promise((resolve) => setTimeout(resolve, 400));

        expect(suggestionTitles(box)).toEqual([]);
        expect(shadow(box).querySelector('[role="status"]')?.textContent?.trim()).toBe('');
    });

    it('is left out when the portlet list fails to load', async () => {
        server.on('GET', LIST_API, text('', 500));
        const launcher = await renderLauncher();
        const box = boxOf(launcher);

        await typeInto(box, 'cal');
        await new Promise((resolve) => setTimeout(resolve, 400));

        expect(suggestionTitles(box)).toEqual([]);
        await userEvent.keyboard('{Enter}');
        expect(assign).toHaveBeenCalledWith('/uPortal/p/search/max?query=cal');
    });

    it('asks the new context path for the list after a change', async () => {
        const launcher = await renderLauncher();
        const box = boxOf(launcher);
        server.on('GET', `/portal${PORTLET_LIST_PATH}`, json(REGISTRY));

        launcher.setAttribute('context-path', '/portal');
        await launcher.updateComplete;
        await box.updateComplete;
        await typeInto(box, 'cal');
        await expect.poll(() => suggestionTitles(box)).toEqual(['Calendar']);
        await userEvent.keyboard('{ArrowDown}{Enter}');

        expect(assign).toHaveBeenCalledWith('/portal/p/calendar/max');
    });

    it('focuses its field', async () => {
        const launcher = await renderLauncher();
        const box = boxOf(launcher);

        box.focus();

        expect(shadow(box).activeElement).toBe(input(box));
    });
});

describe('up-search states', () => {
    it('shows only the search box without a query', async () => {
        const element = await renderSearch(ATTRIBUTES);

        expect(shadow(element).querySelector('[role="tablist"]')).toBeNull();
        expect(server.requestsTo(SEARCH_API)).toHaveLength(0);
        expect(await axeViolations(element)).toEqual([]);
    });

    it('shows a loading state while it searches', async () => {
        const answers: ((response: Response) => void)[] = [];
        server.on('GET', SEARCH_API, () => new Promise((resolve) => answers.push(resolve)));
        const element = await fixture<UpSearch>(
            `<up-search ${ATTRIBUTES} query="cal"></up-search>`,
        );

        const loading = shadow(element).querySelector('.loading');
        expect(loading?.getAttribute('role')).toBe('status');
        expect(loading?.textContent).toContain('Loading');
        expect(await axeViolations(element)).toEqual([]);

        answers[0]?.(json(searchResults('cal')));
        await expect.poll(() => shadow(element).querySelector('.loading')).toBeNull();
    });

    it('lists the results in a tab per type, the first one shown', async () => {
        const completed = vi.fn();
        document.addEventListener('up-search-completed', completed);
        const element = await renderSearch();
        document.removeEventListener('up-search-completed', completed);
        const locator = page.elementLocator(element);

        expect(server.requestsTo(SEARCH_API)[0]?.url).toBe(
            `${SEARCH_API}?q=cartoon&submitted=true`,
        );
        const tabs = locator.getByRole('tab');
        await expect.element(tabs.nth(0)).toHaveTextContent('People');
        await expect.element(tabs.nth(1)).toHaveTextContent('Portlets');
        await expect
            .element(locator.getByRole('tab', { name: 'People' }))
            .toHaveAttribute('aria-selected', 'true');
        await expect.element(locator.getByRole('tabpanel')).toHaveTextContent('No Results Found');
        expect(shadow(element).querySelector('[role="status"]')?.textContent?.trim()).toBe(
            '1 results for cartoon.',
        );
        expect(detailOf(completed)).toEqual({
            query: 'cartoon',
            results: 1,
        });
        expect(await axeViolations(element)).toEqual([]);
    });

    it('shows a portlet with its link, name, description and relevance', async () => {
        const element = await renderSearch();
        const locator = page.elementLocator(element);

        await userEvent.click(locator.getByRole('tab', { name: 'Portlets' }));

        const heading = locator.getByRole('heading', { level: 3, name: 'Daily Business Cartoon' });
        await expect.element(heading).toBeVisible();
        await expect
            .element(locator.getByRole('link', { name: 'Daily Business Cartoon' }))
            .toHaveAttribute('href', CARTOON.url);
        const pairs = [...shadow(element).querySelectorAll('dt')].map(
            (dt) => `${dt.textContent} ${dt.nextElementSibling?.textContent}`,
        );
        expect(pairs).toEqual([
            'Description: Daily Business Cartoon by Ted Goff, www.tedgoff.com',
            'Name: Daily Business Cartoon',
            'Relevance: 4.0',
        ]);
        expect(await axeViolations(element)).toEqual([]);
    });

    it('shows a person with their details and no link', async () => {
        server.on('GET', SEARCH_API, json(searchResults('steven', [STEVEN], [])));
        const element = await renderSearch(`${ATTRIBUTES} query="steven"`);

        const pairs = [...shadow(element).querySelectorAll('dt')].map(
            (dt) => `${dt.textContent} ${dt.nextElementSibling?.textContent}`,
        );
        expect(pairs).toEqual([
            'Phone: (555) 555-5555',
            'Email: steven.student@example.org',
            'First Name: Steven',
            'Last Name: Student',
            'Net ID: student',
        ]);
        expect(shadow(element).querySelector('h3')?.textContent?.trim()).toBe('Steven Student');
        expect(shadow(element).querySelector('[role="tabpanel"] a')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });

    it('joins several values and leaves out results without shown attributes', async () => {
        server.on(
            'GET',
            SEARCH_API,
            json({
                query: 'x',
                groups: [
                    {
                        type: 'people',
                        results: [
                            { title: 'A', url: null, attributes: { mail: ['a@x.org', 'b@x.org'] } },
                            { title: 'B', url: null, attributes: { uid: ['b'], mail: [] } },
                        ],
                    },
                ],
            }),
        );
        const element = await renderSearch(`${ATTRIBUTES} query="x"`);

        expect([...shadow(element).querySelectorAll('dd')].map((dd) => dd.textContent)).toEqual([
            'a@x.org, b@x.org',
        ]);
        expect(shadow(element).querySelectorAll('dl')).toHaveLength(1);
    });

    it('names a remote provider tab by its label and shows its failure in the tab only', async () => {
        server.on(
            'GET',
            SEARCH_API,
            json({
                query: 'x',
                groups: [
                    { type: 'people', label: null, results: [STEVEN], failed: false },
                    { type: 'web', label: 'Campus web', results: [], failed: true },
                ],
            }),
        );
        const element = await renderSearch(`${ATTRIBUTES} query="x"`);
        const locator = page.elementLocator(element);

        expect(shadow(element).querySelector('h3')?.textContent?.trim()).toBe('Steven Student');
        await userEvent.click(locator.getByRole('tab', { name: 'Campus web' }));

        await expect
            .poll(
                () =>
                    shadow(element).querySelector('[role="tabpanel"] [role="alert"] p')
                        ?.textContent,
            )
            .toBe('These results could not be loaded. The other results are complete.');
        expect(await axeViolations(element)).toEqual([]);
    });

    it('names other result types after the type', async () => {
        server.on(
            'GET',
            SEARCH_API,
            json({ query: 'x', groups: [{ type: 'campusBuildings', results: [] }] }),
        );
        const element = await renderSearch(`${ATTRIBUTES} query="x"`);

        await expect
            .element(page.elementLocator(element).getByRole('tab'))
            .toHaveTextContent('Campus Buildings');
    });

    it('says there are no results when no type was searched', async () => {
        server.on('GET', SEARCH_API, json({ query: 'x', groups: [] }));
        const element = await renderSearch(`${ATTRIBUTES} query="x"`);

        expect(shadow(element).querySelector('[role="tablist"]')).toBeNull();
        expect(shadow(element).querySelector('.no-results')?.textContent).toBe('No Results Found');
        expect(await axeViolations(element)).toEqual([]);
    });

    it.each([
        [500, 'The search failed.'],
        [403, 'The search failed.'],
        [200, 'The search failed.'],
    ])('shows an error with a retry after a %i answer', async (status, message) => {
        server.on('GET', SEARCH_API, text(status === 200 ? 'not json' : '', status));
        const element = await renderSearch();

        await expect
            .poll(() => shadow(element).querySelector('[role="alert"] p')?.textContent)
            .toBe(message);
        expect(await axeViolations(element)).toEqual([]);

        server.on('GET', SEARCH_API, json(searchResults('cartoon')));
        await userEvent.click(
            page.elementLocator(element).getByRole('button', { name: 'Try again' }),
        );
        await expect
            .element(page.elementLocator(element).getByRole('tab', { name: 'People' }))
            .toBeVisible();
    });

    it('shows an error when the network fails', async () => {
        server.on('GET', SEARCH_API, () => Promise.reject(new TypeError('offline')));
        const element = await renderSearch();

        await expect
            .poll(() => shadow(element).querySelector('[role="alert"] p')?.textContent)
            .toBe('The search failed.');
    });

    it('asks the user to sign in again after a 401', async () => {
        server.on('GET', SEARCH_API, text('', 401));
        const element = await renderSearch();

        await expect
            .element(page.elementLocator(element).getByRole('alert'))
            .toHaveTextContent('Your session has expired. Sign in again to search.');
        expect(shadow(element).querySelector('.retry')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });
});

describe('up-search behaviour', () => {
    it('takes the query from the page URL when it has no query attribute', async () => {
        pageQuery = 'steven';
        const element = await renderSearch(ATTRIBUTES);

        expect(server.requestsTo(SEARCH_API)[0]?.url).toBe(`${SEARCH_API}?q=steven&submitted=true`);
        expect(input(boxOf(element)).value).toBe('steven');
    });

    it('searches its result types', async () => {
        await renderSearch(`${ATTRIBUTES} query="x" result-types='["people","portlets"]'`);

        expect(server.requestsTo(SEARCH_API)[0]?.url).toBe(
            `${SEARCH_API}?q=x&type=people&type=portlets&submitted=true`,
        );
    });

    it('searches again in place from its own box and updates the page URL', async () => {
        const element = await renderSearch();
        const box = boxOf(element);
        expect(input(box).value).toBe('cartoon');

        await userEvent.clear(input(box));
        await userEvent.type(input(box), 'calendar{Enter}');

        await expect.poll(() => server.requestsTo(SEARCH_API).length).toBe(2);
        expect(server.requestsTo(SEARCH_API)[1]?.url).toBe(
            `${SEARCH_API}?q=calendar&submitted=true`,
        );
        expect(replaceQuery).toHaveBeenCalledWith('calendar');
        expect(element.query).toBe('calendar');
        expect(assign).not.toHaveBeenCalled();
    });

    it('ignores a blank search', async () => {
        const element = await renderSearch();

        element.search('  ');

        expect(server.requestsTo(SEARCH_API)).toHaveLength(1);
        expect(replaceQuery).not.toHaveBeenCalled();
    });

    it('moves between the tabs with the arrow, Home and End keys', async () => {
        server.on(
            'GET',
            SEARCH_API,
            json({
                query: 'x',
                groups: ['people', 'portlets', 'places'].map((type) => ({ type, results: [] })),
            }),
        );
        const element = await renderSearch(`${ATTRIBUTES} query="x"`);
        const selected = () =>
            shadow(element)
                .querySelector('[role="tab"][aria-selected="true"]')
                ?.textContent?.trim();
        const focused = () => shadow(element).activeElement?.textContent?.trim();

        shadow(element).querySelector<HTMLElement>('[role="tab"]')?.focus();
        await userEvent.keyboard('{ArrowRight}');
        await expect.poll(selected).toBe('Portlets');
        expect(focused()).toBe('Portlets');
        await userEvent.keyboard('{End}');
        await expect.poll(selected).toBe('Places');
        await userEvent.keyboard('{ArrowRight}');
        await expect.poll(selected).toBe('People');
        await userEvent.keyboard('{ArrowLeft}');
        await expect.poll(selected).toBe('Places');
        await userEvent.keyboard('{Home}');
        await expect.poll(selected).toBe('People');
        await userEvent.keyboard('{Home}');
        expect(focused()).toBe('People');
        await userEvent.keyboard('a');
        expect(selected()).toBe('People');
        const inactive = shadow(element).querySelectorAll('[role="tab"][tabindex="-1"]');
        expect(inactive).toHaveLength(2);
    });

    it('calls the API under a new context path', async () => {
        const element = await renderSearch();
        server.on('GET', `/portal${SEARCH_PATH}`, json(searchResults('x')));

        element.setAttribute('context-path', '/portal');
        await element.updateComplete;
        element.search('x');

        await expect.poll(() => server.requestsTo(`/portal${SEARCH_PATH}`).length).toBe(1);
    });

    it('loads its messages in the user locale', async () => {
        server.on(
            'GET',
            `/uPortal${MESSAGES_PATH}`,
            json({ messages: { 'search.results.people': 'Personnes', 'search.terms': 'Termes' } }),
        );
        const element = await renderSearch(
            `${ATTRIBUTES.replace('en-US', 'fr-FR')} query="cartoon"`,
        );

        await expect
            .element(page.elementLocator(element).getByRole('tab', { name: 'Personnes' }))
            .toBeVisible();
        await expect
            .element(page.elementLocator(element).getByRole('searchbox', { name: 'Termes' }))
            .toBeVisible();
    });
});

describe('result types and labels', () => {
    it('reads one type, a JSON array, or nothing', () => {
        expect(parseResultTypes(undefined)).toEqual([]);
        expect(parseResultTypes(' ')).toEqual([]);
        expect(parseResultTypes('people')).toEqual(['people']);
        expect(parseResultTypes('["people", "", 3, "portlets"]')).toEqual(['people', 'portlets']);
        expect(parseResultTypes('[people')).toEqual(['[people']);
        expect(parseResultTypes('{"a":1}')).toEqual(['{"a":1}']);
        expect(parseResultTypes('[1')).toEqual(['[1']);
        expect(parseResultTypes('["a"')).toEqual(['["a"']);
    });

    it('turns type names into words', () => {
        expect(startCase('peopleTab')).toBe('People Tab');
        expect(startCase('campus_buildings')).toBe('Campus Buildings');
    });
});

describe('navigation', () => {
    it('uses the window it was made for', () => {
        const view = {
            location: {
                href: 'https://portal.example.edu/uPortal/p/search/max?query=a&x=1',
                assign: vi.fn(),
            },
            history: { state: { s: 1 }, replaceState: vi.fn() },
        };
        const nav = createNavigation(view);

        expect(nav.query()).toBe('a');
        nav.replaceQuery('b c');
        expect(view.history.replaceState).toHaveBeenCalledWith(
            { s: 1 },
            '',
            'https://portal.example.edu/uPortal/p/search/max?query=b+c&x=1',
        );
        nav.assign('/uPortal/p/calendar/max');
        expect(view.location.assign).toHaveBeenCalledWith('/uPortal/p/calendar/max');
        expect(
            createNavigation({
                ...view,
                location: { ...view.location, href: 'https://x.org/' },
            }).query(),
        ).toBeNull();
    });
});
