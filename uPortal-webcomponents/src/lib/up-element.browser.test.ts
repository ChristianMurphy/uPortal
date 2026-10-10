import { html } from 'lit';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { cleanupFixtures, fixture } from '../test-support/fixture';
import { FetchStub, json, noContent } from '../test-support/fetch-stub';
import { clearMessageCache, MESSAGES_PATH } from './i18n';
import { UpElement } from './up-element';

class TestElement extends UpElement {
    protected override get defaultMessages() {
        return { greeting: 'Hello {0}', farewell: 'Goodbye' };
    }

    override render() {
        return html`<p>${this.msg('greeting', 'Ada')}</p>
            <button type="button" class="btn-solid theme-primary">${this.msg('farewell')}</button>`;
    }

    message(key: string, ...args: string[]): string {
        return this.msg(key, ...args);
    }

    fire(): boolean {
        return this.emit('up-test-fired', { value: 1 });
    }

    post(): Promise<unknown> {
        return this.api.send('POST', '/api/v6-0/test');
    }
}
customElements.define('test-up-element', TestElement);

class SilentElement extends UpElement {}
customElements.define('test-silent-element', SilentElement);

let server: FetchStub;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub().on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }));
});

afterEach(() => {
    cleanupFixtures();
    document.documentElement.removeAttribute('lang');
    document.documentElement.style.colorScheme = '';
});

const portalAttributes =
    'locale="fr-CA" window-state="normal" context-path="/uPortal" fname="demo"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

describe('UpElement context', () => {
    it('reads the attributes the channel renderer sets', async () => {
        const element = await fixture<TestElement>(
            `<test-up-element ${portalAttributes}></test-up-element>`,
        );

        expect(element.locale).toBe('fr-CA');
        expect(element.windowState).toBe('normal');
        expect(element.contextPath).toBe('/uPortal');
        expect(element.fname).toBe('demo');
        expect(element.csrfHeader).toBe('X-CSRF-TOKEN');
        expect(element.csrfToken).toBe('token-1');
        expect(element.isMaximized).toBe(false);
    });

    it('reflects context properties to attributes', async () => {
        const element = await fixture<TestElement>('<test-up-element></test-up-element>');

        element.windowState = 'maximized';
        element.locale = 'de';
        element.contextPath = '/portal';
        element.fname = 'clock';
        await element.updateComplete;

        expect(element.getAttribute('window-state')).toBe('maximized');
        expect(element.getAttribute('locale')).toBe('de');
        expect(element.getAttribute('context-path')).toBe('/portal');
        expect(element.getAttribute('fname')).toBe('clock');
        expect(element.isMaximized).toBe(true);
        // Unset context is not reflected as empty attributes
        expect(element.hasAttribute('csrf-token')).toBe(false);
    });

    it('treats the detached window state like maximized', async () => {
        const element = await fixture<TestElement>(
            '<test-up-element window-state="detached"></test-up-element>',
        );

        expect(element.isMaximized).toBe(true);
    });

    it('falls back to the nearest lang, then the document, then the browser', async () => {
        const nested = await fixture<TestElement>(
            '<div lang="sv"><test-up-element></test-up-element></div>',
        );
        expect((nested.firstElementChild as TestElement).resolvedLocale).toBe('sv');

        document.documentElement.lang = 'ja';
        const element = await fixture<TestElement>('<test-up-element></test-up-element>');
        expect(element.resolvedLocale).toBe('ja');

        document.documentElement.removeAttribute('lang');
        expect(element.resolvedLocale).toBe(navigator.language);

        vi.spyOn(navigator, 'language', 'get').mockReturnValue('');
        expect(element.resolvedLocale).toBe('en');
        vi.restoreAllMocks();
    });
});

describe('UpElement messages', () => {
    it('renders English defaults until the portal answers, then its messages', async () => {
        server.on('GET', `/uPortal${MESSAGES_PATH}`, () =>
            json({ locale: 'fr-CA', messages: { greeting: 'Bonjour {0}' } }),
        );
        const element = await fixture<TestElement>(
            `<test-up-element ${portalAttributes}></test-up-element>`,
        );

        await element.messagesReady;
        await element.updateComplete;

        expect(element.shadowRoot?.querySelector('p')?.textContent).toBe('Bonjour Ada');
        // Keys the portal does not know keep their default; unknown keys show the key
        expect(element.message('farewell')).toBe('Goodbye');
        expect(element.message('no.such.key')).toBe('no.such.key');
        const [request] = server.requestsTo(`/uPortal${MESSAGES_PATH}`);
        expect(request?.url).toBe(`/uPortal${MESSAGES_PATH}?locale=fr-CA&keys=farewell%2Cgreeting`);
    });

    it('loads the messages again when the locale changes', async () => {
        server.on('GET', `/uPortal${MESSAGES_PATH}`, (request) =>
            json({
                messages: { farewell: request.url.includes('locale=de') ? 'Tschüss' : 'Au revoir' },
            }),
        );
        const element = await fixture<TestElement>(
            `<test-up-element ${portalAttributes}></test-up-element>`,
        );
        await element.messagesReady;

        element.locale = 'de';
        await element.updateComplete;
        await element.messagesReady;
        await element.updateComplete;

        expect(element.shadowRoot?.querySelector('button')?.textContent).toBe('Tschüss');
        expect(server.requestsTo(`/uPortal${MESSAGES_PATH}`)).toHaveLength(2);
    });

    it('ignores messages that arrive after the locale changed again', async () => {
        let answerFrench: ((response: Response) => void) | undefined;
        server.on('GET', `/uPortal${MESSAGES_PATH}`, (request) =>
            request.url.includes('locale=fr')
                ? new Promise<Response>((resolve) => {
                      answerFrench = resolve;
                  })
                : json({ messages: { farewell: 'Hej då' } }),
        );
        const element = await fixture<TestElement>(
            '<test-up-element locale="fr" context-path="/uPortal"></test-up-element>',
        );
        const french = element.messagesReady;

        element.locale = 'sv';
        await element.updateComplete;
        await element.messagesReady;
        answerFrench?.(json({ messages: { farewell: 'Au revoir' } }));
        await french;
        await element.updateComplete;

        expect(element.shadowRoot?.querySelector('button')?.textContent).toBe('Hej då');
    });

    it('keeps the defaults when the messages cannot be loaded', async () => {
        server.on('GET', `/uPortal${MESSAGES_PATH}`, () => new Response('', { status: 500 }));
        const element = await fixture<TestElement>(
            `<test-up-element ${portalAttributes}></test-up-element>`,
        );

        await element.messagesReady;
        await element.updateComplete;

        expect(element.shadowRoot?.querySelector('p')?.textContent).toBe('Hello Ada');
    });

    it('asks for nothing when the component has no messages', async () => {
        await fixture<SilentElement>('<test-silent-element locale="en"></test-silent-element>');

        expect(server.requests).toHaveLength(0);
    });
});

describe('UpElement events and API', () => {
    it('emits composed, bubbling events that reach the page', async () => {
        const element = await fixture<TestElement>('<test-up-element></test-up-element>');
        const received: CustomEvent[] = [];
        document.addEventListener('up-test-fired', (event) => received.push(event as CustomEvent), {
            once: true,
        });

        element.fire();

        expect(received).toHaveLength(1);
        expect(received[0]?.detail).toEqual({ value: 1 });
    });

    it('calls the portal with the CSRF token from its attributes', async () => {
        server.on('POST', '/uPortal/api/v6-0/test', noContent());
        const element = await fixture<TestElement>(
            `<test-up-element ${portalAttributes}></test-up-element>`,
        );

        await element.post();

        const [request] = server.requestsTo('/uPortal/api/v6-0/test');
        expect(request?.method).toBe('POST');
        expect(request?.headers['X-CSRF-TOKEN']).toBe('token-1');
    });
});

describe('UpElement styles', () => {
    it('shares one Bootstrap stylesheet between all instances', async () => {
        const first = await fixture<TestElement>('<test-up-element></test-up-element>');
        const second = await fixture<TestElement>('<test-up-element></test-up-element>');

        const firstSheets = first.shadowRoot?.adoptedStyleSheets ?? [];
        const secondSheets = second.shadowRoot?.adoptedStyleSheets ?? [];
        expect(firstSheets.length).toBeGreaterThan(0);
        expect(firstSheets[0]).toBe(secondSheets[0]);
    });

    it('applies Bootstrap 6 tokens and components inside the shadow root', async () => {
        const element = await fixture<TestElement>('<test-up-element></test-up-element>');
        const button = element.shadowRoot?.querySelector('button') as HTMLButtonElement;

        expect(getComputedStyle(element).getPropertyValue('--primary-base').trim()).not.toBe('');
        expect(getComputedStyle(button).backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
        expect(getComputedStyle(element).display).toBe('block');
    });

    it('lets the page theme a component through its tokens', async () => {
        const style = document.createElement('style');
        style.textContent = 'test-up-element { --primary-bg: rgb(1, 2, 3); }';
        document.head.append(style);
        try {
            const element = await fixture<TestElement>('<test-up-element></test-up-element>');
            const button = element.shadowRoot?.querySelector('button') as HTMLButtonElement;

            expect(getComputedStyle(button).backgroundColor).toBe('rgb(1, 2, 3)');
        } finally {
            style.remove();
        }
    });

    it('follows the color scheme of the page', async () => {
        const element = await fixture<TestElement>('<test-up-element></test-up-element>');
        expect(getComputedStyle(element).colorScheme).toBe('normal');

        document.documentElement.style.colorScheme = 'dark';

        expect(getComputedStyle(element).colorScheme).toBe('dark');
    });

    it('can be hidden', async () => {
        const element = await fixture<TestElement>('<test-up-element hidden></test-up-element>');

        expect(getComputedStyle(element).display).toBe('none');
    });
});
