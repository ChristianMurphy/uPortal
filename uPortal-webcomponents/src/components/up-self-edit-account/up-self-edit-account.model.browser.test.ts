/**
 * Model-based tests: @xstate/graph walks the self edit account machine and the real
 * `<up-self-edit-account>` replays each path in Chromium, answering its REST calls as the path
 * says. At the end of every step the rendered output and its accessibility attributes must match
 * the machine's state, and axe must find nothing in the state the path reaches.
 */
import { getShortestPaths, getSimplePaths, type StatePath } from '@xstate/graph';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vite-plus/test';
import { userEvent } from 'vite-plus/test/browser';
import type { SnapshotFrom } from 'xstate';
import { ApiClient, ApiError } from '../../lib/api-client';
import { clearMessageCache, MESSAGES_PATH } from '../../lib/i18n';
import { axeViolations } from '../../test-support/axe';
import { FetchStub, json, noContent, text } from '../../test-support/fetch-stub';
import { cleanupFixtures, fixture } from '../../test-support/fixture';
import { ACCOUNT_PATH, type AccountData } from './account-api';
import type { UpSelfEditAccount } from './up-self-edit-account';
import './up-self-edit-account';
import {
    selfEditAccountMachine,
    type SelfEditAccountEvent,
    type SelfEditAccountMachine,
} from './up-self-edit-account.machine';

type Snapshot = SnapshotFrom<SelfEditAccountMachine>;
type Path = StatePath<Snapshot, SelfEditAccountEvent>;

const API = `/uPortal${ACCOUNT_PATH}`;
const ATTRIBUTES =
    'locale="en-US" window-state="normal" context-path="/uPortal" fname="passwordmgr"' +
    ' csrf-header="X-CSRF-TOKEN" csrf-token="token-1"';

const EDITOR: AccountData = {
    username: 'selma',
    details: [
        { name: 'title', label: 'Title', value: null },
        { name: 'department', label: 'Department', value: 'Biology' },
    ],
    canEdit: true,
    editable: [
        { name: 'title', label: 'Title', value: null },
        { name: 'department', label: 'Department', value: 'Biology' },
    ],
};
const VIEWER: AccountData = { ...EDITOR, canEdit: false, editable: [] };

const LOAD_FAILED = 'Your account could not be loaded.';
const SESSION_EXPIRED = 'Your session has expired. Sign in again to see your account.';
const SAVE_FAILED = 'Your account could not be updated. Try again later.';
const PASSWORDS_DIFFER = 'Passwords must match';

/**
 * The events the traversal tries: user events, and the outcomes of the invoked REST calls
 * (`xstate.done.actor.*` / `xstate.error.actor.*`), which the test replays as responses. Reload
 * is offered only where the element shows its Try again button.
 */
const EVENTS = [
    { type: 'xstate.done.actor.loadAccount', output: EDITOR },
    { type: 'xstate.done.actor.loadAccount', output: VIEWER },
    { type: 'xstate.error.actor.loadAccount', error: new ApiError('server', 500, 'boom') },
    { type: 'xstate.error.actor.loadAccount', error: new ApiError('unauthorized', 401, 'gone') },
    { type: 'edit' },
    { type: 'change', name: 'title', value: 'Dr' },
    { type: 'password.change', field: 'password', value: 'n3w-Secret!' },
    { type: 'password.change', field: 'confirmPassword', value: 'n3w-Secret!' },
    { type: 'cancel' },
    { type: 'submit' },
    { type: 'xstate.done.actor.saveAccount' },
    { type: 'xstate.error.actor.saveAccount', error: new ApiError('server', 500, 'boom') },
    { type: 'xstate.error.actor.saveAccount', error: new ApiError('unauthorized', 401, 'gone') },
] as unknown as SelfEditAccountEvent[];

function events(snapshot: Snapshot): SelfEditAccountEvent[] {
    const retry = snapshot.matches('failed') && snapshot.context.loadError !== 'unauthorized';
    return retry ? [...EVENTS, { type: 'reload' }] : EVENTS;
}

/** States differ by their value and by the context the element renders. */
function serialize(snapshot: Snapshot): string {
    const { account, loadError, draft, password, confirmPassword, notice } = snapshot.context;
    return JSON.stringify([
        snapshot.value,
        loadError ?? null,
        account.canEdit,
        account.details.length,
        draft,
        password !== '',
        confirmPassword !== '',
        notice ?? null,
    ]);
}

const OPTIONS = {
    input: { api: new ApiClient({ contextPath: '/uPortal' }) },
    events,
    serializeState: serialize,
};

/** Holds the account API's requests until a step answers them. */
class Portal {
    readonly #waiting = new Map<string, ((response: Response) => void)[]>();

    constructor(server: FetchStub) {
        server.on('GET', API, () => this.#hold('loadAccount'));
        server.on('PUT', API, () => this.#hold('saveAccount'));
    }

    #hold(call: string): Promise<Response> {
        return new Promise((resolve) => {
            this.#waiting.set(call, [...(this.#waiting.get(call) ?? []), resolve]);
        });
    }

    async answer(call: string, response: Response): Promise<void> {
        await vi.waitFor(() => {
            expect(this.#waiting.get(call)?.length, `a pending ${call} request`).toBeGreaterThan(0);
        });
        this.#waiting.get(call)?.shift()?.(response);
    }
}

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

function inputValue(root: ShadowRoot, id: string): string | undefined {
    return root.querySelector<HTMLInputElement>(`input#${id}`)?.value;
}

/** Makes the element do what the step's event stands for. */
async function replay(element: UpSelfEditAccount, portal: Portal, event: SelfEditAccountEvent) {
    const outcome = event as unknown as { type: string; output?: unknown; error?: ApiError };
    const call = /^xstate\.(done|error)\.actor\.(\w+)$/.exec(outcome.type);
    if (call?.[1] === 'done') {
        await portal.answer(
            call[2] ?? '',
            outcome.output === undefined ? noContent() : json(outcome.output),
        );
        return;
    }
    if (call?.[1] === 'error') {
        await portal.answer(call[2] ?? '', text('', outcome.error?.status ?? 500));
        return;
    }
    switch (event.type) {
        case 'reload':
            await userEvent.click(find(element, '.retry'));
            return;
        case 'edit':
            await userEvent.click(find(element, '.edit'));
            return;
        case 'change':
            await userEvent.fill(find(element, `#attribute-${event.name}`), event.value);
            return;
        case 'password.change':
            await userEvent.fill(find(element, `#${event.field}`), event.value);
            return;
        case 'cancel':
            await userEvent.click(find(element, '.cancel'));
            return;
        case 'submit':
            await userEvent.click(find(element, '.save'));
            return;
        default:
            throw new Error(`no way to replay ${event.type}`);
    }
}

/** What the element must show, and how it must expose it, in a state of the machine. */
function expectRendered(element: UpSelfEditAccount, snapshot: Snapshot): void {
    const root = shadow(element);
    const heading = root.querySelector('h2')?.textContent?.trim();
    const form = root.querySelector('form');
    const { account, loadError, draft, password, confirmPassword, notice } = snapshot.context;

    if (snapshot.matches('loading')) {
        expect(root.querySelector('.loading')?.getAttribute('role')).toBe('status');
        expect(heading).toBeUndefined();
        return;
    }
    if (snapshot.matches('failed')) {
        const expired = loadError === 'unauthorized';
        expect(root.querySelector('[role="alert"]')?.textContent).toContain(
            expired ? SESSION_EXPIRED : LOAD_FAILED,
        );
        expect(root.querySelector('.retry') === null).toBe(expired);
        return;
    }
    if (snapshot.matches('viewing')) {
        expect(heading).toBe('My Account');
        expect(form).toBeNull();
        expect(root.querySelector('.edit') !== null).toBe(account.canEdit);
        expect(root.querySelectorAll('tbody tr')).toHaveLength(account.details.length);
        return;
    }
    if (snapshot.matches('saved')) {
        expect(heading).toBe('Account Updated');
        expect(root.activeElement).toBe(root.querySelector('h2'));
        return;
    }

    const busy = snapshot.hasTag('busy');
    expect(heading).toBe('Edit My Account');
    expect(form?.getAttribute('aria-busy')).toBe(busy ? 'true' : null);
    expect(root.querySelector('.save')?.hasAttribute('disabled')).toBe(busy);
    for (const attribute of account.editable) {
        expect(inputValue(root, `attribute-${attribute.name}`)).toBe(draft[attribute.name]);
    }
    expect(inputValue(root, 'password')).toBe(password);
    expect(inputValue(root, 'confirmPassword')).toBe(confirmPassword);
    const shown = root.querySelector('.notice');
    if (notice === undefined) {
        expect(shown).toBeNull();
    } else {
        expect(shown?.getAttribute('role')).toBe('alert');
        const expected =
            notice.kind === 'passwords-differ'
                ? PASSWORDS_DIFFER
                : notice.reason === 'unauthorized'
                  ? SESSION_EXPIRED
                  : SAVE_FAILED;
        expect(shown?.textContent).toContain(expected);
    }
}

function describePath(path: Path): string {
    const steps = path.steps.slice(1).map((step) => step.event.type.replace('xstate.', ''));
    return `${serialize(path.state)} after ${steps.join(' > ') || 'connecting'}`;
}

/** Mounts a fresh element and walks it along the path, checking every step in order. */
async function walk(path: Path): Promise<UpSelfEditAccount> {
    const portal = new Portal(server);
    const element = await fixture<UpSelfEditAccount>(
        `<up-self-edit-account ${ATTRIBUTES}></up-self-edit-account>`,
    );
    // The first step is the machine's initial state, which connecting the element reaches
    const [initial, ...steps] = path.steps;
    await vi.waitFor(() => {
        expectRendered(element, initial?.state ?? path.state);
    });
    await steps.reduce(async (previous, step) => {
        await previous;
        await replay(element, portal, step.event);
        await vi.waitFor(() => {
            expectRendered(element, step.state);
        });
    }, Promise.resolve());
    return element;
}

let server: FetchStub;

beforeEach(() => {
    clearMessageCache();
    server = new FetchStub().on('GET', `/uPortal${MESSAGES_PATH}`, json({ messages: {} }));
});

afterEach(() => {
    cleanupFixtures();
});

describe('up-self-edit-account model: shortest paths', () => {
    const paths = getShortestPaths(selfEditAccountMachine, OPTIONS);

    it('reaches every state of the machine', () => {
        const values = new Set(paths.map((path) => JSON.stringify(path.state.value)));
        expect([...values].toSorted()).toEqual(
            [
                '"loading"',
                '"failed"',
                '"viewing"',
                '{"editing":"idle"}',
                '{"editing":"saving"}',
                '"saved"',
            ].toSorted(),
        );
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            const element = await walk(path);

            expect(await axeViolations(element)).toEqual([]);
        },
    );
});

/**
 * The editor's paths through the form, without what the shortest paths already cover: the
 * password fields (copies of each path), the viewer's account and the expired session.
 */
const SIMPLE_EVENTS = EVENTS.filter((event) => {
    const outcome = event as unknown as { output?: unknown; error?: ApiError };
    return (
        event.type !== 'password.change' &&
        outcome.output !== VIEWER &&
        outcome.error?.kind !== 'unauthorized'
    );
});

describe('up-self-edit-account model: simple paths through editing and saving', () => {
    const paths = getSimplePaths(selfEditAccountMachine, { ...OPTIONS, events: SIMPLE_EVENTS });

    it('is small enough to walk every path', () => {
        expect(paths.length).toBeLessThanOrEqual(40);
    });

    it.each(paths.map((path) => [describePath(path), path] as const))(
        'renders %s',
        async (_name, path) => {
            await walk(path);
        },
    );
});
