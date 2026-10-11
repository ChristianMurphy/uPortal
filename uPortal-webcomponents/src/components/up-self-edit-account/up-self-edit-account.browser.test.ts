import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { userEvent } from 'vite-plus/test/browser';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, noContent, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { ACCOUNT_PATH, type AccountData } from './account-api';
import { SELF_EDIT_ACCOUNT_MESSAGES } from './messages';
import './up-self-edit-account';
import type { UpSelfEditAccount } from './up-self-edit-account';

const API = `/uPortal${ACCOUNT_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="maximized" context-path="/uPortal" fname="passwordmgr"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

const DETAILS = [
    { name: 'title', label: 'Title', value: null },
    { name: 'department', label: 'Department', value: 'Biology' },
    { name: 'mail', label: 'Email', value: 'selma@example.org' },
];

const EDITOR: AccountData = {
    username: 'selma',
    details: DETAILS,
    canEdit: true,
    editable: [
        { name: 'title', label: 'Title', value: null },
        { name: 'department', label: 'Department', value: 'Biology' },
    ],
};

const VIEWER: AccountData = { ...EDITOR, canEdit: false, editable: [] };

let server: FetchStub;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub()
        .on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }))
        .on('GET', API, json(EDITOR))
        .on('PUT', API, noContent());
});

afterEach(() => {
    cleanupFixtures();
    vi.restoreAllMocks();
});

function shadow(element: UpSelfEditAccount): ShadowRoot {
    if (!element.shadowRoot) {
        throw new Error('no shadow root');
    }
    return element.shadowRoot;
}

function find(element: UpSelfEditAccount, selector: string): HTMLElement {
    const found = shadow(element).querySelector<HTMLElement>(selector);
    if (!found) {
        throw new Error(`nothing matches ${selector}`);
    }
    return found;
}

function heading(element: UpSelfEditAccount): string | undefined {
    return shadow(element).querySelector('h2')?.textContent?.trim();
}

/** The label and value text of each row of the first table. */
function rows(element: UpSelfEditAccount): string[][] {
    return [...shadow(element).querySelectorAll('table tr')].map((row) =>
        [...row.querySelectorAll('td')].map((cell) => cell.textContent?.trim() ?? ''),
    );
}

async function render(attributes = ATTRIBUTES): Promise<UpSelfEditAccount> {
    const element = await fixture<UpSelfEditAccount>(
        `<up-self-edit-account ${attributes}></up-self-edit-account>`,
    );
    await expect.poll(() => element.shadowRoot?.querySelector('.loading')).toBeNull();
    return element;
}

async function openForm(): Promise<UpSelfEditAccount> {
    const element = await render();
    await userEvent.click(find(element, '.edit'));
    await expect.poll(() => heading(element)).toBe('Edit My Account');
    return element;
}

function input(element: UpSelfEditAccount, id: string): HTMLInputElement {
    const found = shadow(element).querySelector<HTMLInputElement>(`input#${id}`);
    if (!found) {
        throw new Error(`no input #${id}`);
    }
    return found;
}

/** Holds requests until the test answers them. */
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

describe('up-self-edit-account states', () => {
    it('shows a loading state until the account arrives', async () => {
        const pending = held();
        server.on('GET', API, pending.respond);
        const element = await fixture<UpSelfEditAccount>(
            `<up-self-edit-account ${ATTRIBUTES}></up-self-edit-account>`,
        );

        const loading = find(element, '.loading');
        expect(loading.getAttribute('role')).toBe('status');
        expect(loading.textContent).toContain('Loading');
        expect(await axeViolations(element)).toEqual([]);

        pending.answer(json(EDITOR));
        await expect.poll(() => heading(element)).toBe('My Account');
    });

    it('lists the details of a user who may not edit, without Edit', async () => {
        server.on('GET', API, json(VIEWER));
        const element = await render();

        expect(heading(element)).toBe('My Account');
        expect(rows(element)).toEqual([
            ['Title', ''],
            ['Department', 'Biology'],
            ['Email', 'selma@example.org'],
        ]);
        expect(shadow(element).querySelector('.edit')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });

    it('offers Edit to a user who may edit', async () => {
        const element = await render();

        expect(find(element, '.edit').textContent?.trim()).toBe('Edit');
        expect(await axeViolations(element)).toEqual([]);
    });

    it.each([
        ['a server error', text('boom', 500)],
        ['a forbidden answer', text('', 403)],
        ['a body that is not JSON', text('<html></html>')],
        ['a body that is not an account', json({ username: 'selma' })],
    ])('offers to try again after %s', async (_name, response) => {
        server.on('GET', API, response);
        const element = await render();

        expect(find(element, '[role="alert"]').textContent).toContain(
            'Your account could not be loaded.',
        );
        expect(await axeViolations(element)).toEqual([]);

        server.on('GET', API, json(EDITOR));
        await userEvent.click(find(element, '.retry'));
        await expect.poll(() => heading(element)).toBe('My Account');
    });

    it('offers to try again when the network fails', async () => {
        server.on('GET', API, () => Promise.reject(new TypeError('offline')));
        const element = await render();

        expect(find(element, '.retry')).not.toBeNull();
    });

    it('asks the user to sign in again when the session expired', async () => {
        server.on('GET', API, text('', 401));
        const element = await render();

        expect(find(element, '[role="alert"]').textContent).toContain(
            'Your session has expired. Sign in again to see your account.',
        );
        expect(shadow(element).querySelector('.retry')).toBeNull();
        expect(await axeViolations(element)).toEqual([]);
    });
});

describe('up-self-edit-account editing', () => {
    it('opens a form with the stored values, empty passwords and focus on the first input', async () => {
        const element = await openForm();

        expect(input(element, 'attribute-title').value).toBe('');
        expect(input(element, 'attribute-department').value).toBe('Biology');
        expect(input(element, 'attribute-department').labels?.[0]?.textContent).toBe('Department');
        expect(input(element, 'password').type).toBe('password');
        expect(input(element, 'password').value).toBe('');
        expect(input(element, 'password').autocomplete).toBe('new-password');
        expect(input(element, 'confirmPassword').labels?.[0]?.textContent).toBe('Confirm Password');
        expect(input(element, 'confirmPassword').value).toBe('');
        expect(shadow(element).activeElement).toBe(input(element, 'attribute-title'));
        expect(
            [...shadow(element).querySelectorAll('h3')].map((h3) => h3.textContent?.trim()),
        ).toEqual(['My Account Details', 'Password']);
        expect(await axeViolations(element)).toEqual([]);
    });

    it('goes back to the details on Cancel, with focus on Edit and nothing saved', async () => {
        const element = await openForm();
        await userEvent.fill(input(element, 'attribute-department'), 'Not saved');
        await userEvent.click(find(element, '.cancel'));

        await expect.poll(() => heading(element)).toBe('My Account');
        expect(shadow(element).activeElement).toBe(find(element, '.edit'));
        expect(server.requests.filter((request) => request.method === 'PUT')).toEqual([]);
    });

    it('saves every attribute and the password with the CSRF token', async () => {
        const element = await openForm();
        const details: unknown[] = [];
        element.addEventListener('up-account-updated', (event) => {
            details.push(event instanceof CustomEvent ? event.detail : undefined);
        });

        await userEvent.fill(input(element, 'attribute-title'), 'Dr');
        await userEvent.fill(input(element, 'attribute-department'), '');
        await userEvent.fill(input(element, 'password'), 'n3w-Secret!');
        await userEvent.fill(input(element, 'confirmPassword'), 'n3w-Secret!');
        await userEvent.click(find(element, '.save'));

        await expect.poll(() => heading(element)).toBe('Account Updated');
        const put = server.requests.find((request) => request.method === 'PUT');
        expect(put?.headers['X-CSRF-TOKEN']).toBe('token-1');
        expect(put?.body).toEqual({
            attributes: { title: 'Dr', department: '' },
            password: 'n3w-Secret!',
            confirmPassword: 'n3w-Secret!',
        });
        expect(shadow(element).textContent).toContain(
            'Your account has been successfully updated.  Your changes will be visible upon your next login.',
        );
        expect(shadow(element).activeElement).toBe(find(element, 'h2'));
        expect(details).toEqual([{ passwordChanged: true }]);
        expect(await axeViolations(element)).toEqual([]);
    });

    it('refuses a confirmation that differs without sending it', async () => {
        const element = await openForm();
        await userEvent.fill(input(element, 'password'), 'n3w-Secret!');
        await userEvent.fill(input(element, 'confirmPassword'), 'n3w-Secret?');
        await userEvent.click(find(element, '.save'));

        await expect
            .poll(() => shadow(element).querySelector('.notice')?.textContent)
            .toContain('Passwords must match');
        expect(find(element, '.notice').getAttribute('role')).toBe('alert');
        expect(input(element, 'password').value).toBe('');
        expect(input(element, 'confirmPassword').value).toBe('');
        expect(server.requests.some((request) => request.method === 'PUT')).toBe(false);
        expect(await axeViolations(element)).toEqual([]);
    });

    it('saves with Enter', async () => {
        const element = await openForm();
        await userEvent.type(input(element, 'attribute-title'), 'Dr{Enter}');

        await expect.poll(() => heading(element)).toBe('Account Updated');
    });

    it('is busy and disabled while saving', async () => {
        const pending = held();
        server.on('PUT', API, pending.respond);
        const element = await openForm();
        await userEvent.click(find(element, '.save'));

        await expect.poll(() => find(element, 'form').getAttribute('aria-busy')).toBe('true');
        expect(find(element, '.save').hasAttribute('disabled')).toBe(true);
        expect(find(element, '.cancel').hasAttribute('disabled')).toBe(true);
        expect(input(element, 'password').disabled).toBe(true);
        expect(await axeViolations(element)).toEqual([]);

        pending.answer(noContent());
        await expect.poll(() => heading(element)).toBe('Account Updated');
    });

    it.each([
        [
            'a refusal',
            text('{"status":403}', 403),
            'Your account could not be updated. Try again later.',
        ],
        ['a server error', text('', 500), 'Your account could not be updated. Try again later.'],
        [
            'an expired session',
            text('', 401),
            'Your session has expired. Sign in again to see your account.',
        ],
    ])('keeps the form after %s and empties the passwords', async (_name, response, message) => {
        server.on('PUT', API, response);
        const element = await openForm();
        await userEvent.fill(input(element, 'attribute-title'), 'Dr');
        await userEvent.fill(input(element, 'password'), 'n3w-Secret!');
        await userEvent.fill(input(element, 'confirmPassword'), 'n3w-Secret!');
        await userEvent.click(find(element, '.save'));

        await expect
            .poll(() => shadow(element).querySelector('.notice')?.textContent)
            .toContain(message);
        expect(find(element, '.notice').getAttribute('role')).toBe('alert');
        expect(heading(element)).toBe('Edit My Account');
        expect(input(element, 'attribute-title').value).toBe('Dr');
        expect(input(element, 'password').value).toBe('');
        expect(await axeViolations(element)).toEqual([]);
    });
});

describe('up-self-edit-account messages', () => {
    it('asks the portal for every message it uses, in the element locale', async () => {
        await render();

        const [request] = server.requestsTo(`/uPortal${MESSAGES_PATH}`);
        const query = new URL(request?.url ?? '', 'http://portal').searchParams;
        expect(query.get('locale')).toBe('en-US');
        expect(query.get('keys')?.split(',').toSorted()).toEqual(
            Object.keys(SELF_EDIT_ACCOUNT_MESSAGES).toSorted(),
        );
    });

    it('renders the messages the portal returns', async () => {
        server.on(
            'GET',
            `/uPortal${MESSAGES_PATH}`,
            json({ messages: { 'my.account': 'Mon compte', edit: 'Modifier' } }),
        );
        const element = await render(ATTRIBUTES.replace('en-US', 'fr-FR'));

        await expect.poll(() => heading(element)).toBe('Mon compte');
        expect(find(element, '.edit').textContent?.trim()).toBe('Modifier');
    });
});

describe('up-self-edit-account lifecycle', () => {
    it('keeps the open form when it moves in the page', async () => {
        const element = await openForm();
        await userEvent.fill(input(element, 'attribute-title'), 'Dr');

        const other = document.createElement('div');
        document.body.append(other);
        other.append(element);
        await element.updateComplete;

        expect(heading(element)).toBe('Edit My Account');
        expect(input(element, 'attribute-title').value).toBe('Dr');
        other.remove();
    });

    it('calls the portal with the new CSRF token after the attribute changes', async () => {
        const element = await openForm();
        element.setAttribute('csrf-token', 'token-2');
        await element.updateComplete;
        await userEvent.click(find(element, '.save'));

        await expect.poll(() => heading(element)).toBe('Account Updated');
        const put = server.requests.find((request) => request.method === 'PUT');
        expect(put?.headers['X-CSRF-TOKEN']).toBe('token-2');
    });
});
