import { afterEach, beforeEach, describe, expect, it } from 'vite-plus/test';
import { page, userEvent } from 'vite-plus/test/browser';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, noContent, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { FAVORITES_PATH } from './favorites-api';
import { FAVORITES_MESSAGES } from './messages';
import './up-favorites';
import type { UpFavorites } from './up-favorites';

const API = `/uPortal${FAVORITES_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="favorites"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

function collection(nodeId: string, name: string, overrides: object = {}) {
    return {
        nodeId,
        name,
        url: `/uPortal/f/${nodeId}/render.uP`,
        moveAllowed: true,
        deleteAllowed: true,
        ...overrides,
    };
}

function portlet(nodeId: string, fname: string, name: string, overrides: object = {}) {
    return {
        nodeId,
        fname,
        name,
        description: `About ${name}`,
        url: `/uPortal/p/${fname}/render.uP`,
        target: null,
        iconUrl: null,
        moveAllowed: true,
        deleteAllowed: true,
        ...overrides,
    };
}

const NEWS = collection('u12l1s7', 'News');
const CALENDAR = portlet('u12l1n20', 'calendar', 'Calendar', { iconUrl: '/icons/calendar.png' });
const MAIL = portlet('u12l1n21', 'mail', 'Mail', {
    url: 'https://mail.example.edu',
    target: '_blank',
});
const COURSES = portlet('u12l1n22', 'courses', 'Courses');

function favorites(overrides: object = {}) {
    return {
        collections: [NEWS],
        portlets: [CALENDAR, MAIL, COURSES],
        marketplaceUrl: '/uPortal/p/portletmarketplace/max/render.uP',
        ...overrides,
    };
}

let server: FetchStub;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', API, json(favorites()));
});

afterEach(() => {
    cleanupFixtures();
});

async function render(attributes = ATTRIBUTES): Promise<UpFavorites> {
    const element = await fixture<UpFavorites>(`<up-favorites ${attributes}></up-favorites>`);
    await expect.poll(() => element.shadowRoot?.querySelector('.loading')).toBeNull();
    return element;
}

function shadow(element: UpFavorites): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function names(element: UpFavorites): string[] {
    return [...shadow(element).querySelectorAll('.favorite-name')].map(
        (node) => node.textContent ?? '',
    );
}

function row(element: UpFavorites, nodeId: string): HTMLElement {
    const found = shadow(element).querySelector<HTMLElement>(`li[data-node-id="${nodeId}"]`);
    if (!found) {
        throw new Error(`no favorite ${nodeId}`);
    }
    return found;
}

function control(element: UpFavorites, nodeId: string, name: string): HTMLElement {
    const found = row(element, nodeId).querySelector<HTMLElement>(`[data-control="${name}"]`);
    if (!found) {
        throw new Error(`no ${name} control on ${nodeId}`);
    }
    return found;
}

function activeElement(element: UpFavorites): Element | null {
    return shadow(element).activeElement;
}

async function startEditing(element: UpFavorites): Promise<void> {
    await userEvent.click(
        page.elementLocator(element).getByRole('button', { name: 'Reorder favorites' }),
    );
    await element.updateComplete;
}

describe('up-favorites states', () => {
    it('shows a loading state until the favorites arrive', async () => {
        let answer: ((response: Response) => void) | undefined;
        server.on(
            'GET',
            API,
            () =>
                new Promise<Response>((resolve) => {
                    answer = resolve;
                }),
        );
        const element = await fixture<UpFavorites>(`<up-favorites ${ATTRIBUTES}></up-favorites>`);

        const loading = shadow(element).querySelector('.loading');
        expect(loading?.getAttribute('role')).toBe('status');
        expect(loading?.textContent).toContain('Loading');
        expect(await axeViolations(element)).toEqual([]);

        answer?.(json(favorites()));
        await expect.poll(() => names(element)).toEqual(['News', 'Calendar', 'Mail', 'Courses']);
    });

    it('lists collections, then portlets, with their links and icons', async () => {
        const loaded: CustomEvent[] = [];
        document.addEventListener(
            'up-favorites-loaded',
            (event) => loaded.push(event as CustomEvent),
            {
                once: true,
            },
        );

        const element = await render();

        expect(names(element)).toEqual(['News', 'Calendar', 'Mail', 'Courses']);
        expect(control(element, 'u12l1s7', 'link').getAttribute('href')).toBe(
            '/uPortal/f/u12l1s7/render.uP',
        );
        expect(row(element, 'u12l1s7').dataset['kind']).toBe('collection');
        expect(row(element, 'u12l1n20').querySelector('img')?.getAttribute('src')).toBe(
            '/icons/calendar.png',
        );
        expect(row(element, 'u12l1n20').querySelector('img')?.getAttribute('alt')).toBe('');
        expect(row(element, 'u12l1n22').querySelector('svg.icon')).not.toBeNull();

        const mail = control(element, 'u12l1n21', 'link');
        expect(mail.getAttribute('href')).toBe('https://mail.example.edu');
        expect(mail.getAttribute('target')).toBe('_blank');
        expect(mail.getAttribute('rel')).toBe('noopener noreferrer');
        expect(mail.textContent).toContain('(opens in a new window)');
        expect(control(element, 'u12l1n22', 'link').hasAttribute('target')).toBe(false);

        const marketplace = shadow(element).querySelector('.marketplace-link');
        expect(marketplace?.getAttribute('href')).toBe(
            '/uPortal/p/portletmarketplace/max/render.uP',
        );
        expect(marketplace?.textContent?.trim()).toBe('Browse Portlets');
        expect(shadow(element).querySelector('.instruction')?.textContent?.trim()).toBe(
            FAVORITES_MESSAGES['favorites.instruction.add.via.contextual.menu'],
        );
        expect(shadow(element).querySelector('ul')?.getAttribute('aria-label')).toBe('Favorites');
        // Descriptions only show when maximized
        expect(shadow(element).querySelector('.favorite-description')).toBeNull();

        expect(loaded[0]?.detail).toEqual({ collections: 1, portlets: 3 });
        expect(await axeViolations(element)).toEqual([]);
    });

    it('shows a favorite without a usable link as text', async () => {
        server.on(
            'GET',
            API,
            json(
                favorites({
                    portlets: [portlet('u1', 'x', 'Broken', { url: 'javascript:alert(1)' })],
                }),
            ),
        );

        const element = await render();

        expect(row(element, 'u1').querySelector('a')).toBeNull();
        expect(row(element, 'u1').querySelector('.favorite-link')?.textContent).toContain('Broken');
    });

    it('shows an empty state that invites the user to add favorites', async () => {
        server.on('GET', API, json(favorites({ collections: [], portlets: [] })));

        const element = await render();

        const empty = shadow(element).querySelector('.empty');
        expect(empty?.textContent).toContain('You have no favorites.');
        expect(empty?.textContent).toContain('contextual options menu');
        expect(shadow(element).querySelector('.marketplace-link')).not.toBeNull();
        expect(shadow(element).querySelector('ul')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });

    it('omits the marketplace link when the portal has no marketplace', async () => {
        server.on('GET', API, json(favorites({ marketplaceUrl: null })));

        const element = await render();

        expect(shadow(element).querySelector('.marketplace-link')).toBeNull();
    });

    it.each([
        ['a server error', () => text('boom', 500)],
        ['a forbidden answer', () => text('no', 403)],
        ['a malformed body', () => json({ collections: 'nope' })],
        ['a body that is not JSON', () => text('<html>login</html>')],
        ['a network failure', () => Promise.reject(new TypeError('Failed to fetch'))],
    ])('shows an error and a retry button after %s', async (_case, respond) => {
        server.on('GET', API, respond);

        const element = await render();

        const alert = shadow(element).querySelector('[role="alert"]');
        expect(alert?.textContent).toContain('Your favorites could not be loaded.');
        expect(await axeViolations(element)).toEqual([]);

        server.on('GET', API, json(favorites()));
        await userEvent.click(
            page.elementLocator(element).getByRole('button', { name: 'Try again' }),
        );
        await expect.poll(() => names(element)).toEqual(['News', 'Calendar', 'Mail', 'Courses']);
    });

    it('asks the user to sign in again when the session expired', async () => {
        server.on('GET', API, text('', 401));

        const element = await render();

        expect(shadow(element).querySelector('[role="alert"]')?.textContent).toContain(
            'Your session has expired.',
        );
        expect(shadow(element).querySelector('.retry')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });

    it('reloads on demand', async () => {
        const element = await render();
        server.on('GET', API, json(favorites({ portlets: [COURSES] })));

        await element.reload();
        await element.updateComplete;

        expect(names(element)).toEqual(['News', 'Courses']);
        expect(server.requestsTo(API).filter((r) => r.method === 'GET')).toHaveLength(2);
    });
});

describe('up-favorites attributes', () => {
    it('limits the height of the list with max-height-pixels', async () => {
        const element = await render(`${ATTRIBUTES} max-height-pixels="120"`);
        const list = shadow(element).querySelector('ul') as HTMLElement;

        expect(element.maxHeightPixels).toBe(120);
        expect(list.style.maxHeight).toBe('120px');
        expect(list.style.overflowY).toBe('auto');

        element.maxHeightPixels = 80;
        await element.updateComplete;
        expect(element.getAttribute('max-height-pixels')).toBe('80');
        expect(list.style.maxHeight).toBe('80px');

        element.setAttribute('max-height-pixels', '0');
        await element.updateComplete;
        expect(list.style.maxHeight).toBe('');
    });

    it('shows descriptions when maximized', async () => {
        const element = await render(
            ATTRIBUTES.replace('window-state="normal"', 'window-state="maximized"'),
        );

        expect(row(element, 'u12l1n20').querySelector('.favorite-description')?.textContent).toBe(
            'About Calendar',
        );
        expect(row(element, 'u12l1s7').querySelector('.favorite-description')).toBeNull();

        element.windowState = 'normal';
        await element.updateComplete;
        expect(shadow(element).querySelector('.favorite-description')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });
});

describe('up-favorites messages', () => {
    it('asks the portal for every message it uses, in the element locale', async () => {
        await render();

        const [request] = server.requestsTo(`/uPortal${MESSAGES_PATH}`);
        const query = new URL(request?.url ?? '', location.href).searchParams;
        expect(query.get('locale')).toBe('en-US');
        expect(query.get('keys')?.split(',').toSorted()).toEqual(
            Object.keys(FAVORITES_MESSAGES).toSorted(),
        );
    });

    it('renders the messages the portal returns', async () => {
        server
            .on(
                'GET',
                `/uPortal${MESSAGES_PATH}`,
                json({
                    messages: {
                        'favorites.have.none': "Vous n'avez aucun favori.",
                        'favorites.remove.parameterized': 'Retirer {0} de mes favoris',
                        'favorites.invitation.to.marketplace.short': 'Parcourir les portlets',
                    },
                }),
            )
            .on('GET', API, json(favorites({ collections: [], portlets: [] })));
        const element = await render(ATTRIBUTES.replace('en-US', 'fr-CA'));
        await element.messagesReady;
        await element.updateComplete;

        expect(shadow(element).querySelector('.empty')?.textContent).toContain(
            "Vous n'avez aucun favori.",
        );
        expect(shadow(element).querySelector('.marketplace-link')?.textContent?.trim()).toBe(
            'Parcourir les portlets',
        );

        server.on('GET', API, json(favorites()));
        await element.reload();
        await element.updateComplete;
        expect(control(element, 'u12l1n20', 'remove').getAttribute('aria-label')).toBe(
            'Retirer Calendar de mes favoris',
        );
    });
});

describe('up-favorites removing', () => {
    it('removes a favorite, says so and moves focus to the next one', async () => {
        server.on('DELETE', `${API}/u12l1n20`, noContent());
        const removed: CustomEvent[] = [];
        document.addEventListener(
            'up-favorite-removed',
            (event) => removed.push(event as CustomEvent),
            {
                once: true,
            },
        );
        const element = await render();

        const remove = control(element, 'u12l1n20', 'remove');
        expect(remove.getAttribute('aria-label')).toBe('Remove Calendar from my favorites');
        expect(remove.getAttribute('title')).toBe('Remove from my favorites');
        await userEvent.click(remove);
        await expect.poll(() => names(element)).toEqual(['News', 'Mail', 'Courses']);

        const [request] = server.requestsTo(`${API}/u12l1n20`);
        expect(request?.method).toBe('DELETE');
        expect(request?.headers['X-CSRF-TOKEN']).toBe('token-1');
        expect(shadow(element).querySelector('.notice')?.textContent).toContain(
            'You have unfavorited Calendar.',
        );
        expect(shadow(element).querySelector('[aria-live]')?.textContent?.trim()).toBe(
            'You have unfavorited Calendar.',
        );
        expect(removed[0]?.detail).toEqual({
            nodeId: 'u12l1n20',
            name: 'Calendar',
            kind: 'portlet',
        });
        expect(activeElement(element)).toBe(control(element, 'u12l1n21', 'link'));
        expect(await axeViolations(element)).toEqual([]);
    });

    it('moves focus to the previous favorite after removing the last one', async () => {
        server.on('DELETE', `${API}/u12l1n22`, noContent());
        const element = await render();

        await userEvent.click(control(element, 'u12l1n22', 'remove'));

        await expect.poll(() => activeElement(element)).toBe(control(element, 'u12l1n21', 'link'));
    });

    it('shows the empty state, focused, after removing every favorite', async () => {
        server
            .on('GET', API, json(favorites({ collections: [], portlets: [COURSES] })))
            .on('DELETE', `${API}/u12l1n22`, noContent());
        const element = await render();

        await userEvent.click(control(element, 'u12l1n22', 'remove'));

        await expect.poll(() => shadow(element).querySelector('.empty')).not.toBeNull();
        expect(activeElement(element)).toBe(shadow(element).querySelector('.empty'));
        expect(shadow(element).querySelector('.notice')?.textContent).toContain(
            'You have unfavorited Courses.',
        );
    });

    it('treats a favorite that is already gone as removed', async () => {
        server.on('DELETE', `${API}/u12l1s7`, text('', 404));
        const element = await render();

        await userEvent.click(control(element, 'u12l1s7', 'remove'));

        await expect.poll(() => names(element)).toEqual(['Calendar', 'Mail', 'Courses']);
    });

    it.each([
        [403, 'You lack permission to unfavorite Calendar.'],
        [500, 'The portal failed to unfavorite Calendar.'],
        [401, 'Your session has expired.'],
    ])('keeps the favorite and explains a %i', async (status, message) => {
        server.on('DELETE', `${API}/u12l1n20`, text('', status));
        const element = await render();

        await userEvent.click(control(element, 'u12l1n20', 'remove'));

        await expect
            .poll(() => shadow(element).querySelector('.notice[role="alert"]')?.textContent)
            .toContain(message);
        expect(names(element)).toContain('Calendar');
        expect(activeElement(element)).toBe(control(element, 'u12l1n20', 'remove'));
        expect(await axeViolations(element)).toEqual([]);

        await userEvent.click(page.elementLocator(element).getByRole('button', { name: 'Close' }));
        expect(shadow(element).querySelector('.notice')).toBeNull();
    });

    it('sends one request when the star is clicked twice', async () => {
        let answer: ((response: Response) => void) | undefined;
        server.on(
            'DELETE',
            `${API}/u12l1n20`,
            () =>
                new Promise<Response>((resolve) => {
                    answer = resolve;
                }),
        );
        const element = await render();
        const remove = control(element, 'u12l1n20', 'remove');

        remove.click();
        remove.click();
        answer?.(noContent());

        await expect.poll(() => names(element)).not.toContain('Calendar');
        expect(server.requestsTo(`${API}/u12l1n20`)).toHaveLength(1);
    });
});

describe('up-favorites reordering', () => {
    it('shows move buttons and locks only while reordering', async () => {
        server.on(
            'GET',
            API,
            json(
                favorites({
                    collections: [collection('u12l1s7', 'News', { deleteAllowed: false })],
                }),
            ),
        );
        const element = await render();
        expect(shadow(element).querySelector('[data-control="up"]')).toBeNull();
        expect(shadow(element).querySelector('.lock')).toBeNull();

        await startEditing(element);

        expect(control(element, 'u12l1n20', 'up')).toHaveProperty('disabled', true);
        expect(control(element, 'u12l1n20', 'down')).toHaveProperty('disabled', false);
        expect(control(element, 'u12l1n22', 'down')).toHaveProperty('disabled', true);
        expect(control(element, 'u12l1n21', 'up').getAttribute('aria-label')).toBe('Move Mail up');
        expect(control(element, 'u12l1n21', 'down').getAttribute('aria-keyshortcuts')).toBe(
            'Alt+ArrowDown',
        );
        expect(row(element, 'u12l1s7').querySelector('.lock')?.textContent).toContain(
            'You lack permission to unfavorite this collection.',
        );
        expect(shadow(element).querySelector('.legend')?.textContent).toContain(
            'Favorites with the lock icon cannot be unfavorited.',
        );
        expect(shadow(element).querySelector('.help')?.textContent).toContain('Alt+Up');
        expect(shadow(element).querySelector('.edit-toggle')?.textContent?.trim()).toBe(
            'Stop editing favorites',
        );
        expect(await axeViolations(element)).toEqual([]);

        await userEvent.click(
            page.elementLocator(element).getByRole('button', { name: 'Stop editing favorites' }),
        );
        expect(shadow(element).querySelector('[data-control="up"]')).toBeNull();
    });

    it('explains why a locked portlet cannot be removed', async () => {
        server.on(
            'GET',
            API,
            json(favorites({ portlets: [portlet('u1', 'x', 'Locked', { deleteAllowed: false })] })),
        );
        const element = await render();

        await startEditing(element);

        expect(row(element, 'u1').querySelector('.lock')?.getAttribute('title')).toBe(
            'You lack permission to unfavorite this portlet.',
        );
        expect(row(element, 'u1').querySelector('[data-control="remove"]')).toBeNull();
    });

    it('offers no reordering when nothing can move', async () => {
        server.on(
            'GET',
            API,
            json(
                favorites({
                    collections: [],
                    portlets: [portlet('u1', 'x', 'Fixed', { moveAllowed: false })],
                }),
            ),
        );

        const element = await render();

        expect(shadow(element).querySelector('.edit-toggle')).toBeNull();
    });

    it('moves a favorite down with its button and keeps focus on it', async () => {
        server.on(
            'POST',
            `${API}/u12l1n20/move`,
            json(favorites({ portlets: [MAIL, CALENDAR, COURSES] })),
        );
        const moved: CustomEvent[] = [];
        document.addEventListener(
            'up-favorite-moved',
            (event) => moved.push(event as CustomEvent),
            {
                once: true,
            },
        );
        const element = await render();
        await startEditing(element);

        await userEvent.click(control(element, 'u12l1n20', 'down'));

        await expect.poll(() => names(element)).toEqual(['News', 'Mail', 'Calendar', 'Courses']);
        const [request] = server.requestsTo(`${API}/u12l1n20/move`);
        expect(request?.method).toBe('POST');
        expect(request?.body).toEqual({ after: 'u12l1n21' });
        expect(request?.headers['X-CSRF-TOKEN']).toBe('token-1');
        expect(moved[0]?.detail).toEqual({ nodeId: 'u12l1n20', name: 'Calendar', position: 1 });
        expect(shadow(element).querySelector('[aria-live]')?.textContent?.trim()).toBe(
            'Moved Calendar to position 2 of 3.',
        );
        expect(activeElement(element)).toBe(control(element, 'u12l1n20', 'down'));
    });

    it('sends one move request when asked twice before the first answers', async () => {
        let answer: ((response: Response) => void) | undefined;
        server.on(
            'POST',
            `${API}/u12l1n20/move`,
            () =>
                new Promise<Response>((resolve) => {
                    answer = resolve;
                }),
        );
        const element = await render();
        await startEditing(element);
        const down = control(element, 'u12l1n20', 'down');

        down.click();
        down.click();
        answer?.(json(favorites({ portlets: [MAIL, CALENDAR, COURSES] })));

        await expect.poll(() => names(element)).toEqual(['News', 'Mail', 'Calendar', 'Courses']);
        expect(server.requestsTo(`${API}/u12l1n20/move`)).toHaveLength(1);
    });

    it('moves focus to the other button when the favorite reaches an end', async () => {
        server.on(
            'POST',
            `${API}/u12l1n21/move`,
            json(favorites({ portlets: [MAIL, CALENDAR, COURSES] })),
        );
        const element = await render();
        await startEditing(element);

        await userEvent.click(control(element, 'u12l1n21', 'up'));

        await expect.poll(() => activeElement(element)).toBe(control(element, 'u12l1n21', 'down'));
        expect(server.requestsTo(`${API}/u12l1n21/move`)[0]?.body).toEqual({ before: 'u12l1n20' });
    });

    it('moves the focused favorite with Alt+Up and keeps focus on its link', async () => {
        server.on(
            'POST',
            `${API}/u12l1n22/move`,
            json(favorites({ portlets: [CALENDAR, COURSES, MAIL] })),
        );
        const element = await render();
        await startEditing(element);
        control(element, 'u12l1n22', 'link').focus();

        await userEvent.keyboard('{Alt>}{ArrowUp}{/Alt}');

        await expect.poll(() => names(element)).toEqual(['News', 'Calendar', 'Courses', 'Mail']);
        expect(server.requestsTo(`${API}/u12l1n22/move`)[0]?.body).toEqual({ before: 'u12l1n21' });
        expect(activeElement(element)).toBe(control(element, 'u12l1n22', 'link'));
    });

    it('moves collections among collections with Alt+Down', async () => {
        const sports = collection('u12l1s8', 'Sports');
        server
            .on('GET', API, json(favorites({ collections: [NEWS, sports] })))
            .on('POST', `${API}/u12l1s7/move`, json(favorites({ collections: [sports, NEWS] })));
        const element = await render();
        await startEditing(element);
        control(element, 'u12l1s7', 'remove').focus();

        await userEvent.keyboard('{Alt>}{ArrowDown}{/Alt}');

        await expect.poll(() => names(element).slice(0, 2)).toEqual(['Sports', 'News']);
        expect(server.requestsTo(`${API}/u12l1s7/move`)[0]?.body).toEqual({ after: 'u12l1s8' });
        expect(activeElement(element)).toBe(control(element, 'u12l1s7', 'remove'));
    });

    it('ignores keys that do not move a favorite', async () => {
        server.on(
            'GET',
            API,
            json(
                favorites({
                    portlets: [CALENDAR, portlet('u9', 'f', 'Fixed', { moveAllowed: false })],
                }),
            ),
        );
        const element = await render();
        control(element, 'u12l1n20', 'link').focus();

        // Not reordering
        await userEvent.keyboard('{Alt>}{ArrowDown}{/Alt}');
        await startEditing(element);
        control(element, 'u12l1n20', 'link').focus();
        // Without Alt, another key, the first one up, a favorite that cannot move
        await userEvent.keyboard('{ArrowDown}');
        await userEvent.keyboard('{Alt>}{ArrowLeft}{/Alt}');
        await userEvent.keyboard('{Alt>}{ArrowUp}{/Alt}');
        control(element, 'u9', 'link').focus();
        await userEvent.keyboard('{Alt>}{ArrowUp}{/Alt}');
        (shadow(element).querySelector('.edit-toggle') as HTMLElement).focus();
        await userEvent.keyboard('{Alt>}{ArrowUp}{/Alt}');

        expect(server.requests.filter((request) => request.method === 'POST')).toHaveLength(0);
    });

    it('stops reordering with Escape and returns focus to the toggle', async () => {
        const element = await render();
        await startEditing(element);
        control(element, 'u12l1n21', 'up').focus();

        await userEvent.keyboard('{Escape}');
        await element.updateComplete;

        expect(shadow(element).querySelector('[data-control="up"]')).toBeNull();
        expect(activeElement(element)).toBe(shadow(element).querySelector('.edit-toggle'));

        // Escape outside reordering does nothing
        await userEvent.keyboard('{Escape}');
        expect(shadow(element).querySelector('.edit-toggle')?.textContent?.trim()).toBe(
            'Reorder favorites',
        );
    });

    it.each([
        [500, 'The portal failed to move Calendar.'],
        [401, 'Your session has expired.'],
    ])('keeps the order and explains a %i', async (status, message) => {
        server.on('POST', `${API}/u12l1n20/move`, text('', status));
        const element = await render();
        await startEditing(element);

        await userEvent.click(control(element, 'u12l1n20', 'down'));

        await expect
            .poll(() => shadow(element).querySelector('.notice')?.textContent)
            .toContain(message);
        expect(names(element)).toEqual(['News', 'Calendar', 'Mail', 'Courses']);
        expect(activeElement(element)).toBe(control(element, 'u12l1n20', 'down'));
    });
});

describe('up-favorites lifecycle', () => {
    it('keeps its favorites and reorder mode when it moves in the page', async () => {
        const element = await render();
        await startEditing(element);
        const other = document.createElement('section');
        document.body.append(other);

        other.append(element);
        await element.updateComplete;

        // Restored from its snapshot: no new request, still reordering
        expect(server.requestsTo(API).filter((r) => r.method === 'GET')).toHaveLength(1);
        expect(names(element)).toEqual(['News', 'Calendar', 'Mail', 'Courses']);
        expect(control(element, 'u12l1n20', 'down')).toBeTruthy();
        other.remove();
    });

    it('sends a removal again after a move that interrupted it', async () => {
        let answers = 0;
        server.on('DELETE', `${API}/u12l1n21`, () => {
            answers += 1;
            // The first request never answers: the element moves meanwhile
            return answers === 1 ? new Promise<Response>(() => undefined) : noContent();
        });
        const element = await render();
        await userEvent.click(control(element, 'u12l1n21', 'remove'));
        const parent = element.parentElement;

        element.remove();
        parent?.append(element);

        await expect.poll(() => names(element)).toEqual(['News', 'Calendar', 'Courses']);
        expect(answers).toBe(2);
    });

    it('resolves a reload when the element leaves the page before the favorites arrive', async () => {
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

        await expect
            .poll(() => server.requestsTo(API).filter((r) => r.method === 'GET'))
            .toHaveLength(2);
    });

    it('calls the portal with the new CSRF token after the attribute changes', async () => {
        server.on('DELETE', `${API}/u12l1n20`, noContent());
        const element = await render();

        element.setAttribute('csrf-token', 'token-2');
        await element.updateComplete;
        await userEvent.click(control(element, 'u12l1n20', 'remove'));

        await expect
            .poll(() => server.requestsTo(`${API}/u12l1n20`)[0]?.headers['X-CSRF-TOKEN'])
            .toBe('token-2');
    });
});
