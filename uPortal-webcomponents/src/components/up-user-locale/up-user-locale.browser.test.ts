import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { page as browserPage, userEvent } from 'vite-plus/test/browser';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, noContent, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { LOCALE_PATH } from './locale-api';
import { USER_LOCALE_MESSAGES } from './messages';
import { page, type LocaleChangedDetail, type UpUserLocale } from './up-user-locale';

const API = `/uPortal${LOCALE_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="user-locales-selector"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

const IN_ENGLISH = {
    selected: 'en_US',
    locales: [
        { code: 'en_US', tag: 'en-US', name: 'English', nativeName: 'English' },
        { code: 'fr_FR', tag: 'fr-FR', name: 'French', nativeName: 'Français' },
        { code: 'ja_JP', tag: 'ja-JP', name: 'Japanese', nativeName: '日本語' },
    ],
};

const IN_FRENCH = {
    selected: 'fr_FR',
    locales: [
        { code: 'en_US', tag: 'en-US', name: 'anglais', nativeName: 'English' },
        { code: 'fr_FR', tag: 'fr-FR', name: 'français', nativeName: 'Français' },
        { code: 'ja_JP', tag: 'ja-JP', name: 'japonais', nativeName: '日本語' },
    ],
};

let server: FetchStub;
let reload: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', API, json(IN_ENGLISH));
    reload = vi.spyOn(page, 'reload').mockImplementation(() => undefined);
});

afterEach(() => {
    cleanupFixtures();
    reload.mockRestore();
});

async function render(attributes = ATTRIBUTES): Promise<UpUserLocale> {
    const element = await fixture<UpUserLocale>(`<up-user-locale ${attributes}></up-user-locale>`);
    await expect.poll(() => element.shadowRoot?.querySelector('.loading')).toBeNull();
    return element;
}

function shadow(element: UpUserLocale): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function radios(element: UpUserLocale): HTMLInputElement[] {
    return [...shadow(element).querySelectorAll<HTMLInputElement>('input[type="radio"]')];
}

function labels(element: UpUserLocale): string[] {
    return [...shadow(element).querySelectorAll('label')].map((l) => l.textContent?.trim() ?? '');
}

function checked(element: UpUserLocale): string | undefined {
    return radios(element).find((input) => input.checked)?.value;
}

function radio(element: UpUserLocale, name: string) {
    return browserPage.elementLocator(element).getByRole('radio', { name, exact: true });
}

function updateButton(element: UpUserLocale) {
    return browserPage.elementLocator(element).getByRole('button', { name: 'Update' });
}

function puts() {
    return server.requestsTo(API).filter((request) => request.method === 'PUT');
}

describe('up-user-locale states', () => {
    it('shows a loading state until the locales arrive', async () => {
        let answer: ((response: Response) => void) | undefined;
        server.on(
            'GET',
            API,
            () =>
                new Promise<Response>((resolve) => {
                    answer = resolve;
                }),
        );
        const element = await fixture<UpUserLocale>(
            `<up-user-locale ${ATTRIBUTES}></up-user-locale>`,
        );

        const loading = shadow(element).querySelector('.loading');
        expect(loading?.getAttribute('role')).toBe('status');
        expect(loading?.textContent).toContain('Loading');
        expect(await axeViolations(element)).toEqual([]);

        answer?.(json(IN_ENGLISH));
        await expect
            .poll(() => labels(element))
            .toEqual(['English', 'Français (French)', '日本語 (Japanese)']);
    });

    it('lists the languages as a labelled radio group with the saved one checked', async () => {
        const element = await render();

        const heading = shadow(element).querySelector('h2');
        expect(heading?.textContent).toBe('Set Language Preference');
        const group = shadow(element).querySelector('[role="radiogroup"]');
        expect(group?.getAttribute('aria-labelledby')).toBe(heading?.id);
        expect(checked(element)).toBe('en_US');
        await expect.element(radio(element, 'Français (French)')).not.toBeChecked();
        // Each language's own name carries its tag, so a screen reader reads it in that language
        const names = [...shadow(element).querySelectorAll('label span')];
        expect(names.map((name) => [name.textContent, name.getAttribute('lang')])).toEqual([
            ['English', 'en-US'],
            ['Français', 'fr-FR'],
            ['日本語', 'ja-JP'],
        ]);
        await expect.element(updateButton(element)).toBeEnabled();
        expect(shadow(element).querySelector('form')?.getAttribute('aria-busy')).toBeNull();
        expect(shadow(element).querySelector('.notice')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });

    it('checks nothing when the user has not chosen a language', async () => {
        server.on('GET', API, json({ ...IN_ENGLISH, selected: null }));

        const element = await render();

        expect(checked(element)).toBeUndefined();
        await userEvent.click(updateButton(element));
        expect(puts()).toHaveLength(0);
        expect(await axeViolations(element)).toEqual([]);
    });

    it.each([
        ['a server error', () => text('boom', 500)],
        ['a forbidden answer', () => text('no', 403)],
        ['an unauthorized answer', () => text('', 401)],
        ['a malformed body', () => json({ locales: 'en_US' })],
        ['a body that is not JSON', () => text('<html>login</html>')],
        ['a network failure', () => Promise.reject(new TypeError('Failed to fetch'))],
    ])('shows an error and a retry button after %s', async (_case, respond) => {
        server.on('GET', API, respond);

        const element = await render();

        const alert = shadow(element).querySelector('[role="alert"]');
        expect(alert?.textContent).toContain('The languages could not be loaded.');
        expect(radios(element)).toHaveLength(0);
        expect(await axeViolations(element)).toEqual([]);

        server.on('GET', API, json(IN_ENGLISH));
        await userEvent.click(
            browserPage.elementLocator(element).getByRole('button', { name: 'Try again' }),
        );
        await expect
            .poll(() => labels(element))
            .toEqual(['English', 'Français (French)', '日本語 (Japanese)']);
    });

    it('reloads on demand', async () => {
        const element = await render();
        server.on('GET', API, json(IN_FRENCH));

        await element.reload();
        await element.updateComplete;

        expect(labels(element)).toEqual(['English (anglais)', 'Français', '日本語 (japonais)']);
        expect(checked(element)).toBe('fr_FR');
    });
});

describe('up-user-locale changing the language', () => {
    it('saves the chosen language with the CSRF token, reports it and reloads the page', async () => {
        let answer: ((response: Response) => void) | undefined;
        server.on(
            'PUT',
            API,
            () =>
                new Promise<Response>((resolve) => {
                    answer = resolve;
                }),
        );
        const element = await render();
        const changed: LocaleChangedDetail[] = [];
        element.addEventListener('up-locale-changed', (event) => {
            changed.push((event as CustomEvent<LocaleChangedDetail>).detail);
        });

        await userEvent.click(radio(element, 'Français (French)'));
        expect(checked(element)).toBe('fr_FR');
        expect(puts()).toHaveLength(0);
        await userEvent.click(updateButton(element));

        // While the portal answers, the form is busy and does not take another change
        await expect.element(updateButton(element)).toBeDisabled();
        expect(shadow(element).querySelector('form')?.getAttribute('aria-busy')).toBe('true');
        expect(radios(element).every((input) => input.disabled)).toBe(true);
        expect(await axeViolations(element)).toEqual([]);
        expect(puts()).toEqual([
            expect.objectContaining({
                body: { code: 'fr_FR' },
                headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'token-1' }),
            }),
        ]);
        expect(reload).not.toHaveBeenCalled();

        answer?.(json(IN_FRENCH));
        await expect.poll(() => reload.mock.calls.length).toBe(1);
        expect(changed).toEqual([{ code: 'fr_FR', tag: 'fr-FR' }]);
        await element.updateComplete;
        expect(labels(element)).toEqual(['English (anglais)', 'Français', '日本語 (japonais)']);
        await expect.element(updateButton(element)).toBeDisabled();
    });

    it('saves with the keyboard: arrow keys pick, Enter submits', async () => {
        server.on('PUT', API, json(IN_FRENCH));
        const element = await render();

        (shadow(element).querySelector<HTMLInputElement>('#locale-en_US') ?? element).focus();
        await userEvent.keyboard('{ArrowDown}');
        expect(checked(element)).toBe('fr_FR');
        expect(shadow(element).activeElement?.id).toBe('locale-fr_FR');
        await userEvent.keyboard('{Enter}');

        await expect.poll(() => reload.mock.calls.length).toBe(1);
        expect(puts()[0]?.body).toEqual({ code: 'fr_FR' });
    });

    it.each([
        ['a server error', () => text('boom', 500)],
        ['a stale CSRF token', () => text('', 403)],
        ['an unknown locale', () => json({ status: 400, message: 'no' }, 400)],
        ['an empty answer', () => noContent()],
        ['a network failure', () => Promise.reject(new TypeError('Failed to fetch'))],
    ])('keeps the page and says so after %s', async (_case, respond) => {
        server.on('PUT', API, respond);
        const element = await render();

        await userEvent.click(radio(element, '日本語 (Japanese)'));
        await userEvent.click(updateButton(element));

        await expect
            .poll(() => shadow(element).querySelector('.notice')?.textContent)
            .toContain('Your language could not be changed.');
        expect(shadow(element).querySelector('.notice')?.getAttribute('role')).toBe('alert');
        expect(reload).not.toHaveBeenCalled();
        expect(checked(element)).toBe('ja_JP');
        await expect.element(updateButton(element)).toBeEnabled();
        expect(await axeViolations(element)).toEqual([]);

        // Picking another language clears the notice
        await userEvent.click(radio(element, 'English'));
        expect(shadow(element).querySelector('.notice')).toBeNull();
    });
});

describe('up-user-locale messages', () => {
    it('asks the portal for every message it uses, in the element locale', async () => {
        await render();

        const [request] = server.requestsTo(`/uPortal${MESSAGES_PATH}`);
        const query = new URL(request?.url ?? '', location.href).searchParams;
        expect(query.get('locale')).toBe('en-US');
        expect(query.get('keys')?.split(',').toSorted()).toEqual(
            Object.keys(USER_LOCALE_MESSAGES).toSorted(),
        );
    });

    it('renders the messages the portal returns', async () => {
        server
            .on(
                'GET',
                `/uPortal${MESSAGES_PATH}`,
                json({
                    messages: {
                        'set.language.preference': 'Modifier le choix de la langue',
                        update: 'Mise à jour',
                    },
                }),
            )
            .on('GET', API, json(IN_FRENCH));
        const element = await render(ATTRIBUTES.replace('en-US', 'fr-FR'));
        await element.messagesReady;
        await element.updateComplete;

        expect(shadow(element).querySelector('h2')?.textContent).toBe(
            'Modifier le choix de la langue',
        );
        await expect
            .element(
                browserPage.elementLocator(element).getByRole('button', { name: 'Mise à jour' }),
            )
            .toBeVisible();
        await expect.element(radio(element, 'Français')).toBeChecked();
    });
});

describe('up-user-locale lifecycle', () => {
    it('keeps the picked language when it moves in the page', async () => {
        const element = await render();
        await userEvent.click(radio(element, 'Français (French)'));
        const other = document.createElement('section');
        document.body.append(other);

        other.append(element);
        await element.updateComplete;

        expect(server.requestsTo(API)).toHaveLength(1);
        expect(checked(element)).toBe('fr_FR');
        other.remove();
    });

    it('resolves a reload when the element leaves the page before the locales arrive', async () => {
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

    it('calls the portal with the new CSRF token after the attribute changes', async () => {
        server.on('PUT', API, json(IN_FRENCH));
        const element = await render();

        element.setAttribute('csrf-token', 'token-2');
        await element.updateComplete;
        await userEvent.click(radio(element, 'Français (French)'));
        await userEvent.click(updateButton(element));

        await expect.poll(() => puts()[0]?.headers['X-CSRF-TOKEN']).toBe('token-2');
    });
});
