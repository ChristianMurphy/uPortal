import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { userEvent } from 'vite-plus/test/browser';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, text } from '../../test-support/fetch-stub';
import { ManualClock } from '../../test-support/manual-clock';
import { SESSION_TIMEOUT_MESSAGES } from './messages';
import { LOGOUT_TIME_KEY, type LogoutTimeStore } from './session-clock';
import './up-session-timeout';
import type { UpSessionTimeout } from './up-session-timeout';

// Literal paths: the tests pin the REST API's URLs instead of reusing the component's constants
const SESSION = '/uPortal/api/v6-0/session';
const KEEP_ALIVE = '/uPortal/api/v6-0/session/keep-alive';
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="session-timeout"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

const T0 = Date.UTC(2026, 9, 11, 12, 0, 0);
const SECOND = 1000;
const MINUTE = 60 * SECOND;
/** A 30-minute session warns after 28.5 minutes and logs out after 29.5. */
const WARNING_AT = T0 + 28.5 * MINUTE;
const LOGOUT_AT = T0 + 29.5 * MINUTE;

class MemoryStore implements LogoutTimeStore {
    value: number | undefined;

    read(): number | undefined {
        return this.value;
    }

    write(logoutTime: number): void {
        this.value = logoutTime;
    }
}

let server: FetchStub;
let clock: ManualClock;
let store: MemoryStore;
let navigations: string[];
const containers: HTMLElement[] = [];

beforeEach(() => {
    clearMessageCache();
    clock = new ManualClock(T0);
    store = new MemoryStore();
    navigations = [];
    server = new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', SESSION, json({ maxInactiveIntervalSeconds: 1800 }))
        .on('POST', KEEP_ALIVE, json({ maxInactiveIntervalSeconds: 1800 }));
});

afterEach(() => {
    for (const container of containers.splice(0)) {
        container.remove();
    }
    localStorage.removeItem(LOGOUT_TIME_KEY);
});

/**
 * Adds the element to the page with the test's clock, store and navigation, and waits until it
 * has asked for the session.
 */
async function mount(
    attributes = ATTRIBUTES,
    configure: (element: UpSessionTimeout) => void = (element) => {
        element.clock = clock;
        element.store = store;
    },
): Promise<UpSessionTimeout> {
    const container = document.createElement('div');
    container.innerHTML = `<up-session-timeout ${attributes}></up-session-timeout>`;
    const element = container.firstElementChild as UpSessionTimeout;
    configure(element);
    element.navigate = (url) => {
        navigations.push(url);
    };
    containers.push(container);
    document.body.append(container);
    await element.updateComplete;
    return element;
}

/** Waits for the session request to be answered and the element to be waiting. */
async function loaded(): Promise<void> {
    await expect.poll(() => server.requestsTo(SESSION).length).toBe(1);
    await expect.poll(() => clock.pending).toBe(1);
}

async function advanceTo(element: UpSessionTimeout, time: number): Promise<void> {
    clock.advanceTo(time);
    await element.updateComplete;
}

function shadow(element: UpSessionTimeout): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function dialog(element: UpSessionTimeout): HTMLDialogElement | null {
    return shadow(element).querySelector('dialog');
}

function openDialog(element: UpSessionTimeout): HTMLDialogElement {
    const found = dialog(element);
    if (found === null || !found.matches(':modal')) {
        throw new Error('the warning is not open as a modal dialog');
    }
    return found;
}

function button(element: UpSessionTimeout, name: string): HTMLButtonElement {
    const found = [...shadow(element).querySelectorAll('button')].find(
        (candidate) => candidate.textContent?.trim() === name,
    );
    if (!found) {
        throw new Error(`no button ${name}`);
    }
    return found;
}

async function openWarning(element: UpSessionTimeout): Promise<HTMLDialogElement> {
    await loaded();
    await advanceTo(element, WARNING_AT);
    return openDialog(element);
}

function collect(element: UpSessionTimeout, type: string): unknown[] {
    const details: unknown[] = [];
    element.addEventListener(type, (event) => {
        details.push((event as CustomEvent).detail);
    });
    return details;
}

describe('up-session-timeout states', () => {
    it('shows nothing until the warning is due', async () => {
        const element = await mount();
        await loaded();
        await advanceTo(element, WARNING_AT - 1);

        expect(dialog(element)).toBeNull();
        expect(shadow(element).textContent?.trim()).toBe('');
        expect(await axeViolations(element)).toEqual([]);
    });

    it('opens a modal warning 90 seconds before the session expires', async () => {
        const element = await mount();
        const warned = collect(element, 'up-session-warned');
        const open = await openWarning(element);

        const title = shadow(element).getElementById(open.getAttribute('aria-labelledby') ?? '');
        expect(title?.textContent).toBe('Session expiration');
        expect(open.textContent).toMatch(/Your session will expire\s+in 60 seconds\./);
        expect(open.textContent).toContain('Do you want to stay signed in?');
        expect(button(element, 'Yes, keep me signed in')).toBeTruthy();
        expect(button(element, 'No, log me out')).toBeTruthy();
        expect(shadow(element).activeElement).toBe(button(element, 'Yes, keep me signed in'));
        expect(warned).toEqual([{ secondsLeft: 60 }]);
        expect(await axeViolations(element)).toEqual([]);
    });

    it('counts down while it is open', async () => {
        const element = await mount();
        const open = await openWarning(element);
        await advanceTo(element, WARNING_AT + 12 * SECOND);

        expect(open.textContent).toMatch(/expire\s+in 48 seconds/);
        const description = shadow(element).getElementById(
            open.getAttribute('aria-describedby') ?? '',
        );
        expect(description?.textContent).toMatch(/expire\s+in 48 seconds/);
    });

    it('stays open when the user presses Escape', async () => {
        const element = await mount();
        await openWarning(element);

        await userEvent.keyboard('{Escape}');
        await userEvent.keyboard('{Escape}');
        await element.updateComplete;

        expect(openDialog(element)).toBeTruthy();
    });

    it('refuses the cancel event that Escape fires in browsers without closedby', async () => {
        const element = await mount();
        const open = await openWarning(element);

        const cancel = new Event('cancel', { cancelable: true });
        open.dispatchEvent(cancel);

        expect(cancel.defaultPrevented).toBe(true);
        expect(openDialog(element)).toBe(open);
    });

    it('opens again if something closes the dialog while the warning applies', async () => {
        const element = await mount();
        const open = await openWarning(element);

        open.close();
        await expect.poll(() => dialog(element)?.matches(':modal')).toBe(true);
    });

    it('never warns a guest', async () => {
        server.on('GET', SESSION, json({ status: 401, message: 'Sign in' }, 401));
        const element = await mount();
        await expect.poll(() => server.requestsTo(SESSION).length).toBe(1);
        await advanceTo(element, T0 + 60 * MINUTE);

        expect(dialog(element)).toBeNull();
        expect(clock.pending).toBe(0);
    });

    it.each([
        ['a server error', () => text('', 500)],
        ['a network failure', () => Promise.reject(new TypeError('Failed to fetch'))],
        ['a malformed body', () => json({ maxInactiveIntervalSeconds: 'soon' })],
    ])(
        'warns after the default 30 minutes when the portal answers with %s',
        async (_name, respond) => {
            server.on('GET', SESSION, respond);
            const element = await mount();
            await loaded();

            await advanceTo(element, WARNING_AT - 1);
            expect(dialog(element)).toBeNull();
            await advanceTo(element, WARNING_AT);

            expect(openDialog(element).textContent).toMatch(/expire\s+in 60 seconds/);
        },
    );

    it('never warns when the session never expires', async () => {
        server.on('GET', SESSION, json({ maxInactiveIntervalSeconds: -1 }));
        const element = await mount();
        await expect.poll(() => server.requestsTo(SESSION).length).toBe(1);
        await advanceTo(element, T0 + 60 * MINUTE);

        expect(dialog(element)).toBeNull();
    });
});

describe('up-session-timeout keeping the session', () => {
    it('extends the session with the CSRF token and closes the warning', async () => {
        const element = await mount();
        const extended = collect(element, 'up-session-extended');
        await openWarning(element);
        await advanceTo(element, WARNING_AT + 5 * SECOND);

        await userEvent.click(button(element, 'Yes, keep me signed in'));
        await expect.poll(() => dialog(element)).toBeNull();

        const [request] = server.requestsTo(KEEP_ALIVE);
        expect(request?.method).toBe('POST');
        expect(request?.headers['X-CSRF-TOKEN']).toBe('token-1');
        const next = WARNING_AT + 5 * SECOND + 29.5 * MINUTE;
        expect(extended).toEqual([{ logoutTime: next }]);
        expect(store.value).toBe(next);

        // The next warning comes a whole period later
        await advanceTo(element, LOGOUT_AT + SECOND);
        expect(dialog(element)).toBeNull();
        expect(navigations).toEqual([]);
        await advanceTo(element, next - MINUTE);
        expect(openDialog(element)).toBeTruthy();
    });

    it('marks the warning busy while the portal answers', async () => {
        const answers: ((response: Response) => void)[] = [];
        server.on(
            'POST',
            KEEP_ALIVE,
            () =>
                new Promise<Response>((resolve) => {
                    answers.push(resolve);
                }),
        );
        const element = await mount();
        const open = await openWarning(element);

        await userEvent.click(button(element, 'Yes, keep me signed in'));
        await expect.poll(() => open.getAttribute('aria-busy')).toBe('true');
        // A second click while busy sends nothing more
        await userEvent.click(button(element, 'Yes, keep me signed in'));
        expect(server.requestsTo(KEEP_ALIVE)).toHaveLength(1);

        answers[0]?.(json({ maxInactiveIntervalSeconds: 1800 }));
        await expect.poll(() => dialog(element)).toBeNull();
    });

    it.each([
        ['a server error', text('', 500)],
        ['a refused token', text('', 403)],
        ['a malformed body', json({ maxInactiveIntervalSeconds: '30 minutes' })],
    ])(
        'says so when the session cannot be extended (%s), and can try again',
        async (_name, response) => {
            server.on('POST', KEEP_ALIVE, response);
            const element = await mount();
            const open = await openWarning(element);

            await userEvent.click(button(element, 'Yes, keep me signed in'));
            await expect
                .poll(() => shadow(element).querySelector('[role="alert"]')?.textContent?.trim())
                .toBe('Error resetting your session!');
            expect(openDialog(element)).toBe(open);
            expect(await axeViolations(element)).toEqual([]);

            server.on('POST', KEEP_ALIVE, json({ maxInactiveIntervalSeconds: 1800 }));
            await userEvent.click(button(element, 'Yes, keep me signed in'));
            await expect.poll(() => dialog(element)).toBeNull();
        },
    );

    it('calls the portal with the new CSRF token after the attribute changes', async () => {
        const element = await mount();
        await openWarning(element);
        element.setAttribute('csrf-token', 'token-2');
        await element.updateComplete;

        await userEvent.click(button(element, 'Yes, keep me signed in'));
        await expect.poll(() => server.requestsTo(KEEP_ALIVE).length).toBe(1);
        expect(server.requestsTo(KEEP_ALIVE)[0]?.headers['X-CSRF-TOKEN']).toBe('token-2');
    });
});

describe('up-session-timeout logging out', () => {
    it('logs out when the user asks', async () => {
        const element = await mount();
        const ended = collect(element, 'up-session-ended');
        await openWarning(element);

        await userEvent.click(button(element, 'No, log me out'));

        await expect.poll(() => navigations).toEqual(['/uPortal/Logout']);
        expect(ended).toEqual([{ reason: 'logout' }]);
        expect(dialog(element)).toBeNull();
    });

    it('logs out when the countdown reaches zero', async () => {
        const element = await mount();
        const ended = collect(element, 'up-session-ended');
        await openWarning(element);
        await advanceTo(element, LOGOUT_AT - SECOND);
        expect(navigations).toEqual([]);

        await advanceTo(element, LOGOUT_AT);

        expect(navigations).toEqual(['/uPortal/Logout']);
        expect(ended).toEqual([{ reason: 'expired' }]);
        // Once only
        await advanceTo(element, LOGOUT_AT + MINUTE);
        expect(navigations).toHaveLength(1);
    });

    it.each([
        ['/CustomLogout?from=timeout', '/uPortal/CustomLogout?from=timeout'],
        [' /Logout ', '/uPortal/Logout'],
        ['//elsewhere.example/Logout', '/uPortal/Logout'],
        ['https://elsewhere.example/Logout', '/uPortal/Logout'],
        ['javascript:alert(1)', '/uPortal/Logout'],
    ])('goes to logout-url %j as %s', async (logoutUrl, expected) => {
        const element = await mount(`${ATTRIBUTES} logout-url="${logoutUrl}"`);
        await openWarning(element);

        await userEvent.click(button(element, 'No, log me out'));

        await expect.poll(() => navigations).toEqual([expected]);
    });

    it('leaves the page through location.assign by default', async () => {
        const element = document.createElement('up-session-timeout');
        try {
            element.navigate('#up-session-timeout-logout');

            expect(location.hash).toBe('#up-session-timeout-logout');
        } finally {
            history.replaceState(null, '', location.pathname + location.search);
        }
    });

    it('logs out to /Logout without a context path', async () => {
        const element = await mount('locale="en-US"');
        expect(element.resolvedLogoutUrl).toBe('/Logout');
    });
});

describe('up-session-timeout attributes', () => {
    it.each(['false', 'no', ''])('does nothing when enabled is %j', async (enabled) => {
        const element = await mount(`${ATTRIBUTES} enabled="${enabled}"`);
        await advanceTo(element, T0 + 60 * MINUTE);

        expect(server.requestsTo(SESSION)).toEqual([]);
        expect(dialog(element)).toBeNull();
    });

    it('is enabled by a true preference in any case', async () => {
        const element = await mount(`${ATTRIBUTES} enabled=" TRUE "`);
        await openWarning(element);
    });

    it('opens earlier and counts from dialog-display-seconds', async () => {
        const element = await mount(`${ATTRIBUTES} dialog-display-seconds="120"`);
        await loaded();
        await advanceTo(element, LOGOUT_AT - 120 * SECOND - 1);
        expect(dialog(element)).toBeNull();

        await advanceTo(element, LOGOUT_AT - 120 * SECOND);

        expect(openDialog(element).textContent).toMatch(/expire\s+in 120 seconds/);
    });

    it('reflects its attributes as properties', async () => {
        const element = await mount(
            `${ATTRIBUTES} enabled="true" dialog-display-seconds="45" logout-url="/Logout"`,
        );
        expect(element.enabled).toBe('true');
        expect(element.dialogDisplaySeconds).toBe('45');
        expect(element.logoutUrl).toBe('/Logout');

        element.dialogDisplaySeconds = '30';
        await element.updateComplete;
        expect(element.getAttribute('dialog-display-seconds')).toBe('30');
    });
});

describe('up-session-timeout other tabs', () => {
    it('postpones the warning to the later logout time another tab saved', async () => {
        const element = await mount();
        await loaded();
        store.value = LOGOUT_AT + 10 * MINUTE;

        await advanceTo(element, WARNING_AT);
        expect(dialog(element)).toBeNull();

        await advanceTo(element, LOGOUT_AT + 9 * MINUTE);
        expect(openDialog(element)).toBeTruthy();
    });

    it('closes its warning when another tab extends the session', async () => {
        const element = await mount();
        await openWarning(element);
        store.value = WARNING_AT + 29.5 * MINUTE;

        await advanceTo(element, WARNING_AT + SECOND);

        expect(dialog(element)).toBeNull();
    });

    it('shares the logout time in localStorage under the portlet key by default', async () => {
        localStorage.setItem(LOGOUT_TIME_KEY, String(LOGOUT_AT + 10 * MINUTE));
        const element = await mount(ATTRIBUTES, (candidate) => {
            candidate.clock = clock;
        });
        await loaded();
        expect(localStorage.getItem(LOGOUT_TIME_KEY)).toBe(String(LOGOUT_AT));

        // A later value written meanwhile by another tab postpones the warning
        localStorage.setItem(LOGOUT_TIME_KEY, String(LOGOUT_AT + 10 * MINUTE));
        await advanceTo(element, WARNING_AT);
        expect(dialog(element)).toBeNull();
        await advanceTo(element, LOGOUT_AT + 9 * MINUTE);
        expect(openDialog(element)).toBeTruthy();
    });

    it('ignores a saved value that is not a time', async () => {
        const element = await mount(ATTRIBUTES, (candidate) => {
            candidate.clock = clock;
        });
        await loaded();
        localStorage.setItem(LOGOUT_TIME_KEY, 'tomorrow');

        await advanceTo(element, WARNING_AT);

        expect(openDialog(element)).toBeTruthy();
    });

    it('works without storage', async () => {
        const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
            throw new DOMException('denied', 'SecurityError');
        });
        const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new DOMException('denied', 'SecurityError');
        });
        try {
            const element = await mount(ATTRIBUTES, (candidate) => {
                candidate.clock = clock;
            });
            await openWarning(element);
            expect(setItem).toHaveBeenCalled();
            expect(getItem).toHaveBeenCalled();
        } finally {
            getItem.mockRestore();
            setItem.mockRestore();
        }
    });
});

describe('up-session-timeout messages', () => {
    it('asks the portal for every message it uses, in the element locale', async () => {
        await mount();
        await expect.poll(() => server.requestsTo(`/uPortal${MESSAGES_PATH}`).length).toBe(1);

        const [request] = server.requestsTo(`/uPortal${MESSAGES_PATH}`);
        const query = new URL(request?.url ?? '', location.href).searchParams;
        expect(query.get('locale')).toBe('en-US');
        expect(query.get('keys')?.split(',').toSorted()).toEqual(
            Object.keys(SESSION_TIMEOUT_MESSAGES).toSorted(),
        );
    });

    it('renders the messages the portal returns', async () => {
        server.on(
            'GET',
            `/uPortal${MESSAGES_PATH}`,
            json({
                messages: {
                    'session-timeout.title': 'Expiration de la session',
                    'session-timeout.body1': 'Votre session expirera dans {0} secondes.',
                    'session-timeout.button.keep-alive': 'Oui, garder ma session',
                },
            }),
        );
        const element = await mount(ATTRIBUTES.replace('en-US', 'fr-FR'));
        await element.messagesReady;
        const open = await openWarning(element);

        expect(open.textContent).toContain('Expiration de la session');
        expect(open.textContent).toContain('Votre session expirera dans 60 secondes.');
        expect(button(element, 'Oui, garder ma session')).toBeTruthy();
        // Keys the portal did not return keep their English text
        expect(button(element, 'No, log me out')).toBeTruthy();
    });

    it('counts down to one second in the singular', async () => {
        const element = await mount();
        await openWarning(element);

        await advanceTo(element, LOGOUT_AT - 2 * SECOND);
        expect(openDialog(element).textContent).toMatch(/expire\s+in 2 seconds\./);
        await advanceTo(element, LOGOUT_AT - SECOND);
        expect(openDialog(element).textContent).toContain('Your session will expire in 1 second.');
    });

    it('takes the singular from the portal in the element locale', async () => {
        server.on(
            'GET',
            `/uPortal${MESSAGES_PATH}`,
            json({
                messages: {
                    'session-timeout.body1': 'Su sesión expirará en {0} segundos.',
                    'session-timeout.body1.one': 'Su sesión expirará en {0} segundo.',
                },
            }),
        );
        const element = await mount(ATTRIBUTES.replace('en-US', 'es-ES'));
        await element.messagesReady;
        await openWarning(element);

        await advanceTo(element, LOGOUT_AT - SECOND);
        expect(openDialog(element).textContent).toContain('Su sesión expirará en 1 segundo.');
    });
});

describe('up-session-timeout lifecycle', () => {
    it('keeps its warning open when it moves in the page', async () => {
        const element = await mount();
        await openWarning(element);
        const other = document.createElement('div');
        containers.push(other);
        document.body.append(other);

        other.append(element);
        await element.updateComplete;

        await expect.poll(() => dialog(element)?.matches(':modal')).toBe(true);
        await advanceTo(element, WARNING_AT + 10 * SECOND);
        expect(openDialog(element).textContent).toMatch(/expire\s+in 50 seconds/);
    });

    it('runs on the browser clock by default', async () => {
        // A session so short that the warning is due at once and lasts one second
        server.on('GET', SESSION, json({ maxInactiveIntervalSeconds: 31 }));
        const element = await mount(`${ATTRIBUTES} dialog-display-seconds="1"`, (candidate) => {
            candidate.store = store;
        });

        await expect.poll(() => dialog(element)?.matches(':modal')).toBe(true);
        await expect.poll(() => navigations, { timeout: 5000 }).toEqual(['/uPortal/Logout']);
    });

    it('stops its timers when it leaves the page', async () => {
        const element = await mount();
        await loaded();

        element.remove();

        expect(clock.pending).toBe(0);
    });
});

describe('up-session-timeout in a page', () => {
    it('keeps focus in the warning while it is open', async () => {
        const element = await mount();
        const behind = document.createElement('button');
        behind.textContent = 'Behind';
        containers[0]?.append(behind);
        await openWarning(element);

        await [1, 2, 3, 4].reduce(async (previous) => {
            await previous;
            await userEvent.tab();
            expect(document.activeElement).not.toBe(behind);
        }, Promise.resolve());
        expect(['Yes, keep me signed in', 'No, log me out']).toContain(
            shadow(element).activeElement?.textContent?.trim(),
        );
    });
});
