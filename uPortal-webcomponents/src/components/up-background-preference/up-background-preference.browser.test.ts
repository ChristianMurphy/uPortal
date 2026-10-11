import { afterEach, beforeEach, describe, expect, it } from 'vite-plus/test';
import { page as browserPage, userEvent } from 'vite-plus/test/browser';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { BACKGROUND_PATH } from './background-api';
import { BACKGROUND_PREFERENCE_MESSAGES } from './messages';
import { applyPageBackground, backgroundSheet } from './page-background';
import './up-background-preference';
import type { BackgroundChangedDetail, UpBackgroundPreference } from './up-background-preference';

const API = `/uPortal${BACKGROUND_PATH}background-preference`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="background-preference"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

const IMAGES = ['galaxy', 'mountain', 'nebula'].map((name) => ({
    url: `/uPortal/bg/${name}.jpg`,
    thumbnailUrl: `/uPortal/bg/thumbnails/${name}.jpg`,
    caption: name.charAt(0).toUpperCase() + name.slice(1),
}));
const NONE_CHOSEN = {
    containerSelector: 'body #portalPageBody::before',
    images: IMAGES,
    selected: null,
};
const MOUNTAIN = '/uPortal/bg/mountain.jpg';

// The portal page element the background belongs to
const portalPageBody = document.createElement('div');
portalPageBody.id = 'portalPageBody';
document.body.append(portalPageBody);

let server: FetchStub;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', API, json(NONE_CHOSEN))
        .on('PUT', API, (request) =>
            json({ ...NONE_CHOSEN, selected: (request.body as { selected: unknown }).selected }),
        );
});

afterEach(() => {
    cleanupFixtures();
    applyPageBackground(document, '#portalPageBody', undefined);
});

function held() {
    const waiting: ((response: Response) => void)[] = [];
    return {
        respond: () =>
            new Promise<Response>((resolve) => {
                waiting.push(resolve);
            }),
        answer: (response: Response) => waiting.shift()?.(response),
    };
}

function shadow(element: UpBackgroundPreference): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function button(element: UpBackgroundPreference, name: string) {
    return browserPage.elementLocator(element).getByRole('button', { name, exact: true });
}

function toggle(element: UpBackgroundPreference): HTMLButtonElement | null {
    return shadow(element).querySelector<HTMLButtonElement>('.toggle');
}

function focused(element: UpBackgroundPreference): Element | null {
    return shadow(element).activeElement;
}

function pageBackground(): string {
    return getComputedStyle(portalPageBody, '::before').backgroundImage;
}

function puts() {
    return server.requestsTo(API).filter((request) => request.method === 'PUT');
}

async function render(attributes = ATTRIBUTES): Promise<UpBackgroundPreference> {
    const element = await fixture<UpBackgroundPreference>(
        `<up-background-preference ${attributes}></up-background-preference>`,
    );
    await expect.poll(() => toggle(element)).not.toBeNull();
    return element;
}

async function opened(): Promise<UpBackgroundPreference> {
    const element = await render();
    await button(element, 'Change Background Image').click();
    await expect.poll(() => shadow(element).querySelector('#choices')).not.toBeNull();
    return element;
}

describe('up-background-preference states', () => {
    it('shows a loading state until the backgrounds arrive', async () => {
        const load = held();
        server.on('GET', API, load.respond);
        const element = await fixture<UpBackgroundPreference>(
            `<up-background-preference ${ATTRIBUTES}></up-background-preference>`,
        );

        const loading = shadow(element).querySelector('.loading');
        expect(loading?.getAttribute('role')).toBe('status');
        expect(loading?.textContent).toContain('Loading');
        expect(await axeViolations(element)).toEqual([]);

        load.answer(json(NONE_CHOSEN));
        await expect.poll(() => toggle(element)).not.toBeNull();
    });

    it('offers Change Background Image, closed, without taking focus', async () => {
        const element = await render();

        expect(toggle(element)?.textContent?.trim()).toBe('Change Background Image');
        expect(toggle(element)?.getAttribute('aria-expanded')).toBe('false');
        expect(shadow(element).querySelector('#choices')).toBeNull();
        expect(focused(element)).toBeNull();
        expect(pageBackground()).toBe('none');
        expect(await axeViolations(element)).toEqual([]);
    });

    it('puts the saved choice behind the page when it loads', async () => {
        server.on('GET', API, json({ ...NONE_CHOSEN, selected: MOUNTAIN }));

        await render();

        await expect.poll(pageBackground).toContain('/uPortal/bg/mountain.jpg');
    });

    it.each([
        ['a server error', () => text('boom', 500)],
        ['an unknown channel', () => json({ message: 'no' }, 404)],
        ['a body that is not JSON', () => text('<html>')],
        ['an unexpected body', () => json({ images: 'none' })],
        [
            'a network failure',
            () => {
                throw new TypeError('Failed to fetch');
            },
        ],
    ])(
        'says the backgrounds could not be loaded after %s, and tries again',
        async (_n, respond) => {
            server.on('GET', API, respond);
            const element = await fixture<UpBackgroundPreference>(
                `<up-background-preference ${ATTRIBUTES}></up-background-preference>`,
            );
            await expect
                .poll(() => shadow(element).querySelector('[role="alert"]')?.textContent)
                .toContain('The backgrounds could not be loaded.');
            expect(await axeViolations(element)).toEqual([]);

            server.on('GET', API, json(NONE_CHOSEN));
            await button(element, 'Try again').click();
            await expect.poll(() => toggle(element)).not.toBeNull();
        },
    );

    it.each([
        ['401', () => json({ status: 401, message: 'Sign in' }, 401)],
        ['403', () => json({ message: 'csrf' }, 403)],
    ])('asks a signed-out user to sign in again after %s', async (_n, respond) => {
        server.on('GET', API, respond);
        const element = await fixture<UpBackgroundPreference>(
            `<up-background-preference ${ATTRIBUTES}></up-background-preference>`,
        );

        await expect
            .poll(() => shadow(element).querySelector('[role="alert"]')?.textContent?.trim())
            .toBe('Your session has expired. Sign in again to change your background.');
        expect(shadow(element).querySelector('button')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });
});

describe('up-background-preference list', () => {
    it('lists None and the images with thumbnails, the choice pressed', async () => {
        server.on('GET', API, json({ ...NONE_CHOSEN, selected: MOUNTAIN }));
        const element = await opened();

        expect(toggle(element)?.getAttribute('aria-expanded')).toBe('true');
        const choices = [...shadow(element).querySelectorAll('.choice')];
        expect(choices.map((choice) => choice.textContent?.trim())).toEqual([
            'None',
            'Galaxy',
            'Mountain',
            'Nebula',
        ]);
        expect(choices.map((choice) => choice.getAttribute('aria-pressed'))).toEqual([
            'false',
            'false',
            'true',
            'false',
        ]);
        const thumbnails = [...shadow(element).querySelectorAll('img')];
        expect(thumbnails.map((img) => img.getAttribute('src'))).toEqual(
            IMAGES.map((image) => image.thumbnailUrl),
        );
        expect(thumbnails.every((img) => img.getAttribute('alt') === '')).toBe(true);
        expect(await axeViolations(element)).toEqual([]);
    });

    it('closes with the button and with Escape, giving focus back to the button', async () => {
        const element = await opened();

        await button(element, 'Change Background Image').click();
        expect(shadow(element).querySelector('#choices')).toBeNull();

        await button(element, 'Change Background Image').click();
        (button(element, 'Galaxy').element() as HTMLElement).focus();
        await userEvent.keyboard('{Escape}');

        await expect.poll(() => shadow(element).querySelector('#choices')).toBeNull();
        expect(focused(element)).toBe(toggle(element));
    });

    it('leaves Escape alone when the list is closed', async () => {
        const element = await render();
        const escape = new KeyboardEvent('keydown', {
            key: 'Escape',
            bubbles: true,
            cancelable: true,
        });

        toggle(element)?.dispatchEvent(escape);

        expect(escape.defaultPrevented).toBe(false);
    });
});

describe('up-background-preference choosing', () => {
    it('saves the choice with the CSRF token, busy until the portal answers', async () => {
        const save = held();
        server.on('PUT', API, save.respond);
        const element = await opened();

        await button(element, 'Mountain').click();

        await expect.poll(() => puts()).toHaveLength(1);
        expect(puts()[0]?.headers['X-CSRF-TOKEN']).toBe('token-1');
        expect(puts()[0]?.body).toEqual({ selected: MOUNTAIN });
        await expect
            .poll(() => shadow(element).querySelector('#choices')?.getAttribute('aria-busy'))
            .toBe('true');
        await expect.element(button(element, 'Galaxy')).toBeDisabled();
        // Pessimistic: the page changes once the portal saved the choice
        expect(pageBackground()).toBe('none');

        save.answer(json({ ...NONE_CHOSEN, selected: MOUNTAIN }));
        await expect.poll(pageBackground).toContain('/uPortal/bg/mountain.jpg');
    });

    it('closes the list, focuses the button, and tells the page', async () => {
        const events: BackgroundChangedDetail[] = [];
        const element = await opened();
        element.addEventListener('up-background-changed', (event) => {
            events.push((event as CustomEvent<BackgroundChangedDetail>).detail);
        });

        await button(element, 'Mountain').click();

        await expect.poll(() => focused(element)).toBe(toggle(element));
        expect(shadow(element).querySelector('#choices')).toBeNull();
        expect(events).toEqual([{ url: MOUNTAIN }]);
        expect(pageBackground()).toContain('/uPortal/bg/mountain.jpg');
    });

    it('removes the background for None', async () => {
        server.on('GET', API, json({ ...NONE_CHOSEN, selected: MOUNTAIN }));
        const element = await opened();
        await expect.poll(pageBackground).toContain('mountain.jpg');

        await button(element, 'None').click();

        await expect.poll(pageBackground).toBe('none');
        expect(puts()[0]?.body).toEqual({ selected: null });
    });

    it('keeps one page sheet after switching twice', async () => {
        const element = await opened();
        await button(element, 'Galaxy').click();
        await expect.poll(pageBackground).toContain('galaxy.jpg');
        const sheets = document.adoptedStyleSheets.length;

        await button(element, 'Change Background Image').click();
        await button(element, 'Nebula').click();
        await expect.poll(pageBackground).toContain('nebula.jpg');

        expect(document.adoptedStyleSheets).toHaveLength(sheets);
        expect(
            document.adoptedStyleSheets.filter((sheet) => sheet === backgroundSheet(document)),
        ).toHaveLength(1);
        expect(backgroundSheet(document).cssRules).toHaveLength(1);
    });

    it.each([
        ['a server error', () => text('boom', 500)],
        ['an image the portal does not offer', () => json({ message: 'no' }, 400)],
        ['an unexpected body', () => json({})],
        [
            'a network failure',
            () => {
                throw new TypeError('Failed to fetch');
            },
        ],
    ])('keeps the list open with a notice after %s', async (_n, respond) => {
        server.on('GET', API, json({ ...NONE_CHOSEN, selected: MOUNTAIN }));
        server.on('PUT', API, respond);
        const element = await opened();

        await button(element, 'Galaxy').click();

        await expect
            .poll(() => shadow(element).querySelector('.notice')?.textContent?.trim())
            .toBe('Your background could not be changed. Try again later.');
        expect(shadow(element).querySelector('.notice')?.getAttribute('role')).toBe('alert');
        expect(pageBackground()).toContain('mountain.jpg');
        await expect.element(button(element, 'Galaxy')).toBeEnabled();
        expect(await axeViolations(element)).toEqual([]);
    });

    it.each([
        ['401', () => json({ status: 401, message: 'Sign in' }, 401)],
        ['403', () => json({ message: 'csrf' }, 403)],
    ])('asks the user to sign in again when the save answers %s', async (_n, respond) => {
        server.on('PUT', API, respond);
        const element = await opened();

        await button(element, 'Galaxy').click();

        await expect
            .poll(() => shadow(element).querySelector('[role="alert"]')?.textContent?.trim())
            .toBe('Your session has expired. Sign in again to change your background.');
    });
});

describe('up-background-preference keyboard', () => {
    it('opens the list and chooses with Tab, Enter and Space', async () => {
        const element = await render();

        await userEvent.tab();
        expect(focused(element)).toBe(toggle(element));
        await userEvent.keyboard('{Enter}');
        await expect.poll(() => shadow(element).querySelector('#choices')).not.toBeNull();

        await userEvent.tab();
        expect(focused(element)?.textContent?.trim()).toBe('None');
        await userEvent.tab();
        expect(focused(element)?.textContent?.trim()).toBe('Galaxy');
        await userEvent.keyboard(' ');

        await expect.poll(pageBackground).toContain('galaxy.jpg');
        expect(focused(element)).toBe(toggle(element));
    });
});

describe('up-background-preference attributes and messages', () => {
    it('reads the channel named by fname, and the quickstart one without it', async () => {
        server.on('GET', `/uPortal${BACKGROUND_PATH}my-background`, json(NONE_CHOSEN));
        await render(ATTRIBUTES.replace('fname="background-preference"', 'fname="my-background"'));
        expect(server.requestsTo(`/uPortal${BACKGROUND_PATH}my-background`)).toHaveLength(1);

        await render(ATTRIBUTES.replace(' fname="background-preference"', ''));
        expect(server.requestsTo(API)).toHaveLength(1);
    });

    it('saves to a changed context path with a changed token', async () => {
        const moved = `/portal${BACKGROUND_PATH}background-preference`;
        server.on('GET', '/portal/api/v6-0/messages', json({ messages: {} }));
        server.on('PUT', moved, json({ ...NONE_CHOSEN, selected: MOUNTAIN }));
        const element = await opened();

        element.contextPath = '/portal';
        element.csrfToken = 'token-2';
        await element.updateComplete;
        await button(element, 'Mountain').click();

        await expect.poll(() => server.requestsTo(moved)).toHaveLength(1);
        expect(server.requestsTo(moved)[0]?.headers['X-CSRF-TOKEN']).toBe('token-2');
        expect(puts()).toEqual([]);
    });

    it('asks the portal for its messages in the user locale', async () => {
        const element = await render(ATTRIBUTES.replace('en-US', 'fr-FR'));

        const request = server.requestsTo(`/uPortal${MESSAGES_PATH}`)[0];
        const url = new URL(request?.url ?? '', window.location.href);
        expect(url.searchParams.get('locale')).toBe('fr-FR');
        expect(url.searchParams.get('keys')?.split(',').toSorted()).toEqual(
            Object.keys(BACKGROUND_PREFERENCE_MESSAGES).toSorted(),
        );
        await element.messagesReady;
    });

    it('renders the messages the portal returns', async () => {
        server.on(
            'GET',
            `/uPortal${MESSAGES_PATH}`,
            json({
                messages: {
                    'background.preference.change': "Changer l'image de fond",
                    'background.preference.none': 'Aucune',
                },
            }),
        );
        const element = await render(ATTRIBUTES.replace('en-US', 'fr-FR'));
        await element.messagesReady;

        await button(element, "Changer l'image de fond").click();

        await expect.element(button(element, 'Aucune')).toBeVisible();
    });

    it('keeps the open list when moved in the page', async () => {
        const element = await opened();
        const other = document.createElement('div');
        document.body.append(other);

        other.append(element);
        await element.updateComplete;

        expect(shadow(element).querySelector('#choices')).not.toBeNull();
        other.remove();
    });
});
