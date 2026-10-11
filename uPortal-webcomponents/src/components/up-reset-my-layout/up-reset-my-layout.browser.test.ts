import { afterEach, beforeEach, describe, expect, it } from 'vite-plus/test';
import { page as browserPage, userEvent } from 'vite-plus/test/browser';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { RESET_PATH } from './layout-reset-api';
import { RESET_MY_LAYOUT_MESSAGES } from './messages';
import './up-reset-my-layout';
import type { LayoutResetDetail, UpResetMyLayout } from './up-reset-my-layout';

const API = `/uPortal${RESET_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="reset-my-layout"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';
const STUDENT = { userName: 'student' };

let server: FetchStub;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', API, json(STUDENT))
        .on('POST', API, json(STUDENT));
});

afterEach(() => {
    cleanupFixtures();
});

/** A response the test sends later; the request waits until then. */
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

function shadow(element: UpResetMyLayout): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function textOf(element: UpResetMyLayout): string {
    return shadow(element).textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

function button(element: UpResetMyLayout, name: string) {
    return browserPage.elementLocator(element).getByRole('button', { name, exact: true });
}

function focused(element: UpResetMyLayout): Element | null {
    return shadow(element).activeElement;
}

function posts() {
    return server.requestsTo(API).filter((request) => request.method === 'POST');
}

async function render(attributes = ATTRIBUTES): Promise<UpResetMyLayout> {
    const element = await fixture<UpResetMyLayout>(
        `<up-reset-my-layout ${attributes}></up-reset-my-layout>`,
    );
    await expect.poll(() => shadow(element).querySelector('.loading')).toBeNull();
    return element;
}

async function confirmation(): Promise<UpResetMyLayout> {
    const element = await render();
    await button(element, 'Reset Now').click();
    await expect.poll(() => shadow(element).querySelector('.confirmation')).not.toBeNull();
    return element;
}

describe('up-reset-my-layout states', () => {
    it('shows a loading state until it knows the user', async () => {
        const load = held();
        server.on('GET', API, load.respond);
        const element = await fixture<UpResetMyLayout>(
            `<up-reset-my-layout ${ATTRIBUTES}></up-reset-my-layout>`,
        );

        const loading = shadow(element).querySelector('.loading');
        expect(loading?.getAttribute('role')).toBe('status');
        expect(loading?.textContent).toContain('Loading');
        expect(await axeViolations(element)).toEqual([]);

        load.answer(json(STUDENT));
        await expect.poll(() => shadow(element).querySelector('.reset-now')).not.toBeNull();
    });

    it('explains the reset and offers Reset Now, without taking focus', async () => {
        const element = await render();

        expect(textOf(element)).toBe(
            'Use this portlet to reset your portal layout to the defaults. ' +
                'NOTE: You must sign on again to see the changes. Reset Now',
        );
        expect(shadow(element).querySelector('em')?.textContent).toBe(
            'NOTE: You must sign on again to see the changes.',
        );
        await expect.element(button(element, 'Reset Now')).toBeVisible();
        expect(focused(element)).toBeNull();
        expect(posts()).toEqual([]);
        expect(await axeViolations(element)).toEqual([]);
    });

    it.each([
        ['a server error', () => text('boom', 500)],
        ['a refusal', () => json({ message: 'no' }, 403)],
        ['a body that is not JSON', () => text('<html>')],
        ['an unexpected body', () => json({ user: 'student' })],
        [
            'a network failure',
            () => {
                throw new TypeError('Failed to fetch');
            },
        ],
    ])('says the layout could not be found after %s, and tries again', async (_name, respond) => {
        server.on('GET', API, respond);
        const element = await render();

        const alert = shadow(element).querySelector('[role="alert"]');
        expect(alert?.textContent).toContain('Your layout could not be found.');
        expect(await axeViolations(element)).toEqual([]);

        server.on('GET', API, json(STUDENT));
        await button(element, 'Try again').click();
        await expect.element(button(element, 'Reset Now')).toBeVisible();
        expect(server.requestsTo(API)).toHaveLength(2);
    });

    it('asks a signed-out user to sign in again', async () => {
        server.on('GET', API, json({ status: 401, message: 'Sign in' }, 401));
        const element = await render();

        expect(shadow(element).querySelector('[role="alert"]')?.textContent?.trim()).toBe(
            'Your session has expired. Sign in again to reset your layout.',
        );
        expect(shadow(element).querySelector('button')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });
});

describe('up-reset-my-layout confirmation', () => {
    it('asks for confirmation, naming the user, and moves focus to it', async () => {
        const element = await confirmation();

        const heading = shadow(element).querySelector('h2');
        expect(heading?.textContent?.trim()).toBe('Reset User Layout');
        expect(focused(element)).toBe(heading);
        expect(shadow(element).querySelector('section')?.getAttribute('aria-labelledby')).toBe(
            heading?.id,
        );
        expect(textOf(element)).toContain(
            "Resetting a user's layout is irreversible. " +
                'Are you sure you want to reset the layout for student?',
        );
        await expect.element(button(element, 'Reset Layout')).toBeEnabled();
        await expect.element(button(element, 'Cancel')).toBeEnabled();
        expect(shadow(element).querySelector('.reset-now')).toBeNull();
        expect(posts()).toEqual([]);
        expect(await axeViolations(element)).toEqual([]);
    });

    it('goes back without resetting on Cancel, with focus on Reset Now', async () => {
        const element = await confirmation();

        await button(element, 'Cancel').click();

        await expect.poll(() => focused(element)?.textContent?.trim()).toBe('Reset Now');
        expect(textOf(element)).toContain('Use this portlet to reset your portal layout');
        expect(posts()).toEqual([]);
    });
});

describe('up-reset-my-layout resetting', () => {
    it('resets with the CSRF token, busy until the portal answers', async () => {
        const reset = held();
        server.on('POST', API, reset.respond);
        const element = await confirmation();

        await button(element, 'Reset Layout').click();

        await expect.poll(() => posts()).toHaveLength(1);
        expect(posts()[0]?.headers).toEqual({
            Accept: 'application/json',
            'X-CSRF-TOKEN': 'token-1',
        });
        expect(posts()[0]?.body).toBeUndefined();
        await expect
            .poll(() => shadow(element).querySelector('section')?.getAttribute('aria-busy'))
            .toBe('true');
        await expect.element(button(element, 'Reset Layout')).toBeDisabled();
        await expect.element(button(element, 'Cancel')).toBeDisabled();
        expect(shadow(element).querySelector('.spinner-border')).not.toBeNull();
        expect(await axeViolations(element)).toEqual([]);

        reset.answer(json(STUDENT));
        await expect.poll(() => textOf(element)).toContain('Success');
    });

    it('says the layout has been reset, moves focus there and tells the page', async () => {
        const events: LayoutResetDetail[] = [];
        const element = await confirmation();
        element.addEventListener('up-layout-reset', (event) => {
            events.push((event as CustomEvent<LayoutResetDetail>).detail);
        });

        await button(element, 'Reset Layout').click();

        await expect.poll(() => focused(element)?.textContent?.trim()).toBe('Success');
        const status = shadow(element).querySelector('[role="status"]');
        expect(status?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
            'Success Layout for student has been reset',
        );
        expect(events).toEqual([{ userName: 'student' }]);
        await expect.element(button(element, 'Reset Layout')).not.toBeInTheDocument();
        expect(await axeViolations(element)).toEqual([]);
    });

    it('bubbles its event out of the shadow root', async () => {
        const events: Event[] = [];
        document.addEventListener('up-layout-reset', (event) => events.push(event), {
            once: true,
        });
        const element = await confirmation();

        await button(element, 'Reset Layout').click();

        await expect.poll(() => events).toHaveLength(1);
        expect(events[0]?.bubbles).toBe(true);
        expect(events[0]?.composed).toBe(true);
    });

    it('starts over on Continue, with focus on Reset Now', async () => {
        const element = await confirmation();
        await button(element, 'Reset Layout').click();
        await expect.element(button(element, 'Continue')).toBeVisible();

        await button(element, 'Continue').click();

        await expect.poll(() => focused(element)?.textContent?.trim()).toBe('Reset Now');
        expect(posts()).toHaveLength(1);
    });

    it.each([
        ['a server error', () => text('boom', 500)],
        ['a refused CSRF token', () => json({ message: 'csrf' }, 403)],
        ['an unexpected body', () => json({})],
        [
            'a network failure',
            () => {
                throw new TypeError('Failed to fetch');
            },
        ],
    ])('keeps the confirmation with a notice after %s', async (_name, respond) => {
        server.on('POST', API, respond);
        const element = await confirmation();

        await button(element, 'Reset Layout').click();

        await expect
            .poll(() => shadow(element).querySelector('.notice')?.textContent?.trim())
            .toBe('Your layout could not be reset. Try again later.');
        expect(shadow(element).querySelector('.notice')?.getAttribute('role')).toBe('alert');
        await expect.element(button(element, 'Reset Layout')).toBeEnabled();
        expect(focused(element)?.textContent?.trim()).toBe('Reset User Layout');
        expect(await axeViolations(element)).toEqual([]);

        // Trying again clears the notice
        server.on('POST', API, json(STUDENT));
        await button(element, 'Reset Layout').click();
        await expect.poll(() => textOf(element)).toContain('has been reset');
        expect(shadow(element).querySelector('.notice')).toBeNull();
    });

    it('asks the user to sign in again when the session ended', async () => {
        server.on('POST', API, json({ status: 401, message: 'Sign in' }, 401));
        const element = await confirmation();

        await button(element, 'Reset Layout').click();

        await expect
            .poll(() => focused(element)?.textContent?.trim())
            .toBe('Your session has expired. Sign in again to reset your layout.');
    });
});

describe('up-reset-my-layout keyboard', () => {
    it('goes through a reset with Tab, Enter and Space', async () => {
        const element = await render();

        await userEvent.tab();
        expect(focused(element)?.textContent?.trim()).toBe('Reset Now');
        await userEvent.keyboard('{Enter}');
        await expect.poll(() => focused(element)?.textContent?.trim()).toBe('Reset User Layout');

        await userEvent.tab();
        expect(focused(element)?.textContent?.trim()).toBe('Reset Layout');
        await userEvent.tab();
        expect(focused(element)?.textContent?.trim()).toBe('Cancel');
        await userEvent.tab({ shift: true });
        await userEvent.keyboard(' ');

        await expect.poll(() => focused(element)?.textContent?.trim()).toBe('Success');
        await userEvent.tab();
        expect(focused(element)?.textContent?.trim()).toBe('Continue');
        await userEvent.keyboard('{Enter}');
        await expect.poll(() => focused(element)?.textContent?.trim()).toBe('Reset Now');
        expect(posts()).toHaveLength(1);
    });
});

describe('up-reset-my-layout attributes and messages', () => {
    it('reflects the context attributes', async () => {
        const element = await render();

        element.locale = 'fr-FR';
        element.windowState = 'maximized';
        await element.updateComplete;

        expect(element.getAttribute('locale')).toBe('fr-FR');
        expect(element.getAttribute('window-state')).toBe('maximized');
        expect(element.getAttribute('context-path')).toBe('/uPortal');
        expect(element.getAttribute('fname')).toBe('reset-my-layout');
    });

    it('sends the reset to a changed context path with a changed token', async () => {
        server.on('GET', '/portal/api/v6-0/messages', json({ messages: {} }));
        server.on('POST', `/portal${RESET_PATH}`, json(STUDENT));
        const element = await confirmation();

        element.contextPath = '/portal';
        element.csrfToken = 'token-2';
        await element.updateComplete;
        await button(element, 'Reset Layout').click();

        await expect.poll(() => server.requestsTo(`/portal${RESET_PATH}`)).toHaveLength(1);
        expect(server.requestsTo(`/portal${RESET_PATH}`)[0]?.headers['X-CSRF-TOKEN']).toBe(
            'token-2',
        );
        expect(posts()).toEqual([]);
    });

    it('asks the portal for its messages in the user locale', async () => {
        const element = await render(ATTRIBUTES.replace('en-US', 'fr-FR'));

        const request = server.requestsTo(`/uPortal${MESSAGES_PATH}`)[0];
        const url = new URL(request?.url ?? '', window.location.href);
        expect(url.searchParams.get('locale')).toBe('fr-FR');
        expect(url.searchParams.get('keys')?.split(',').toSorted()).toEqual(
            Object.keys(RESET_MY_LAYOUT_MESSAGES).toSorted(),
        );
        await element.messagesReady;
    });

    it('renders the messages the portal returns, with the user name', async () => {
        server.on(
            'GET',
            `/uPortal${MESSAGES_PATH}`,
            json({
                messages: {
                    'reset.now': 'Réinitialiser maintenant',
                    'are.you.sure.reset.layout':
                        'Etes-vous sûr de vouloir réinitialiser la mise en page de {0} ?',
                },
            }),
        );
        const element = await render(ATTRIBUTES.replace('en-US', 'fr-FR'));
        await element.messagesReady;

        await button(element, 'Réinitialiser maintenant').click();

        await expect
            .poll(() => textOf(element))
            .toContain('Etes-vous sûr de vouloir réinitialiser la mise en page de student ?');
    });

    it('keeps its step when moved in the page', async () => {
        const element = await confirmation();
        const other = document.createElement('div');
        document.body.append(other);

        other.append(element);
        await element.updateComplete;

        expect(shadow(element).querySelector('h2')?.textContent?.trim()).toBe('Reset User Layout');
        other.remove();
    });
});
