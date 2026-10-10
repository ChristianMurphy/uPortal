import { html, LitElement, type PropertyValues, type TemplateResult } from 'lit';
import { afterEach, describe, expect, expectTypeOf, it, vi } from 'vite-plus/test';
import {
    Actor,
    assign,
    emit,
    fromPromise,
    setup,
    type EventFromLogic,
    type SnapshotFrom,
    type Subscription,
} from 'xstate';
import { ActorController, toCustomEvent, type ActorControllerOptions } from './actor-controller';

/** A counter that can also load a label through an invoked promise. */
const counterMachine = setup({
    // oxlint-disable-next-line typescript/no-unnecessary-type-assertion -- XState reads the machine's types from it
    types: {} as {
        context: { count: number; label: string };
        events:
            | { type: 'increment' }
            | { type: 'rename'; label: string }
            | { type: 'reset'; start: number }
            | { type: 'load' }
            | { type: 'whisper' };
        input: { start: number };
        emitted: { type: 'counted'; count: number } | { type: 'whispered' };
    },
    actors: {
        fetchLabel: fromPromise<string, { id: string }>(() => Promise.resolve('default')),
    },
}).createMachine({
    context: ({ input }) => ({ count: input.start, label: '' }),
    initial: 'idle',
    states: {
        idle: {
            on: {
                increment: {
                    actions: [
                        assign({ count: ({ context }) => context.count + 1 }),
                        emit(({ context }) => ({ type: 'counted' as const, count: context.count })),
                    ],
                },
                rename: { actions: assign({ label: ({ event }) => event.label }) },
                reset: { actions: assign({ count: ({ event }) => event.start }) },
                whisper: { actions: emit({ type: 'whispered' }) },
                load: 'loading',
            },
        },
        loading: {
            invoke: {
                src: 'fetchLabel',
                input: ({ context }) => ({ id: `label-${context.count}` }),
                onDone: { target: 'idle', actions: assign({ label: ({ event }) => event.output }) },
            },
        },
    },
});

type CounterMachine = typeof counterMachine;

let tags = 0;
const elements: HTMLElement[] = [];

/** A plain LitElement (no uPortal base class) that composes the controller. */
abstract class CounterHost<TSelected> extends LitElement {
    static override properties = { start: { type: Number } };
    declare start: number;
    renders = 0;
    abstract readonly counter: ActorController<CounterMachine, TSelected>;

    constructor() {
        super();
        this.start = 0;
    }

    override render(): TemplateResult {
        this.renders += 1;
        const { context, value } = this.counter.snapshot;
        return html`<output>${value} ${context.count} ${context.label}</output>`;
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        // Properties in: a later change reaches the machine as an event
        if (this.hasUpdated && changed.has('start')) {
            this.counter.send({ type: 'reset', start: this.start });
        }
    }
}

function define<TSelected>(
    machine: CounterMachine,
    options: Omit<ActorControllerOptions<CounterMachine, TSelected>, 'input'> = {},
): () => CounterHost<TSelected> {
    tags += 1;
    const tag = `test-counter-host-${tags}`;
    class Host extends CounterHost<TSelected> {
        readonly counter = new ActorController(this, machine, {
            ...options,
            input: () => ({ start: this.start }),
        });
    }
    customElements.define(tag, Host);
    return () => {
        const element = document.createElement(tag);
        if (!(element instanceof Host)) {
            throw new TypeError(`${tag} is not a counter host`);
        }
        elements.push(element);
        return element;
    };
}

async function attach<T extends LitElement>(element: T, parent: HTMLElement = document.body) {
    parent.append(element);
    await element.updateComplete;
    return element;
}

function text(element: HTMLElement): string {
    return element.shadowRoot?.querySelector('output')?.textContent?.trim() ?? '';
}

/** A promise the test settles, for invoked actors. */
function deferred<T>() {
    let settle: ((value: T) => void) | undefined;
    const promise = new Promise<T>((resolve) => {
        settle = resolve;
    });
    return { promise, resolve: (value: T) => settle?.(value) };
}

afterEach(() => {
    for (const element of elements.splice(0)) {
        element.remove();
    }
    vi.restoreAllMocks();
});

describe('ActorController lifecycle', () => {
    it('creates and starts the actor on the first connect, with input from host properties', async () => {
        const create = define(counterMachine);
        const element = create();
        element.start = 5;

        expect(element.counter.actorRef).toBeUndefined();
        expect(() => element.counter.snapshot).toThrow('has not been connected yet');
        expect(() => element.counter.selected).toThrow('has not been connected yet');

        await attach(element);

        expect(element.counter.actorRef?.getSnapshot().status).toBe('active');
        expect(element.counter.snapshot.context.count).toBe(5);
        expect(text(element)).toBe('idle 5');
        // The actor's first snapshot joins the element's first update: one render
        expect(element.renders).toBe(1);
    });

    it('keeps its state when the element moves, and restores it on reconnect', async () => {
        const element = await attach(define(counterMachine)());
        element.counter.send({ type: 'increment' });
        element.counter.send({ type: 'increment' });
        const first = element.counter.actorRef;

        const other = document.createElement('section');
        document.body.append(other);
        elements.push(other);
        element.remove();

        expect(element.counter.actorRef).toBeUndefined();
        expect(first?.getSnapshot().status).toBe('stopped');
        // The last snapshot stays readable while disconnected
        expect(element.counter.snapshot.context.count).toBe(2);

        await attach(element, other);

        expect(element.counter.actorRef).not.toBe(first);
        expect(element.counter.snapshot.context.count).toBe(2);
        element.counter.send({ type: 'increment' });
        await element.updateComplete;
        expect(text(element)).toBe('idle 3');
    });

    it('takes input only at the first connect; later property changes arrive as events', async () => {
        const element = define(counterMachine)();
        element.start = 10;
        await attach(element);
        element.remove();
        element.start = 20;
        await attach(element);

        // The restored actor kept its context; the willUpdate pattern sent the change as an event
        expect(element.counter.snapshot.context.count).toBe(20);
        element.start = 30;
        await element.updateComplete;
        expect(text(element)).toBe('idle 30');
    });

    it('queues events sent before the first connect or while disconnected', async () => {
        const element = define(counterMachine)();
        element.counter.send({ type: 'increment' });
        await attach(element);
        expect(element.counter.snapshot.context.count).toBe(1);

        element.remove();
        element.counter.send({ type: 'increment' });
        element.counter.send({ type: 'rename', label: 'queued' });
        await attach(element);

        expect(text(element)).toBe('idle 2 queued');
    });

    it('stops updating the host after disconnect and leaks no subscriptions', async () => {
        const subscriptions: ReturnType<typeof vi.fn>[] = [];
        for (const method of ['subscribe', 'on'] as const) {
            const original = Actor.prototype[method];
            vi.spyOn(Actor.prototype, method).mockImplementation(function (
                this: Actor<CounterMachine>,
                ...args: unknown[]
            ): Subscription {
                const subscription = (original as (...a: unknown[]) => Subscription).apply(
                    this,
                    args,
                );
                const unsubscribe = vi.fn(() => {
                    subscription.unsubscribe();
                });
                subscriptions.push(unsubscribe);
                return { unsubscribe };
            });
        }
        const element = await attach(define(counterMachine, { events: { counted: 'counted' } })());
        const actor = element.counter.actorRef;
        const listener = vi.fn();
        document.addEventListener('counted', listener);
        const update = vi.spyOn(element, 'requestUpdate');

        element.remove();

        expect(subscriptions.length).toBeGreaterThanOrEqual(2);
        for (const unsubscribe of subscriptions) {
            expect(unsubscribe).toHaveBeenCalledOnce();
        }
        actor?.send({ type: 'increment' });
        expect(update).not.toHaveBeenCalled();
        expect(listener).not.toHaveBeenCalled();
        document.removeEventListener('counted', listener);
    });

    it('ignores a second connect or disconnect', async () => {
        const element = await attach(define(counterMachine)());
        const actor = element.counter.actorRef;

        element.counter.hostConnected();
        expect(element.counter.actorRef).toBe(actor);

        element.remove();
        element.counter.hostDisconnected();
        expect(element.counter.actorRef).toBeUndefined();
    });
});

describe('ActorController and invoked promises', () => {
    it('aborts an in-flight promise when the element moves, and starts it again after', async () => {
        const calls: { input: { id: string }; signal: AbortSignal; answer: (v: string) => void }[] =
            [];
        const machine = counterMachine.provide({
            actors: {
                fetchLabel: fromPromise<string, { id: string }>(({ input, signal }) => {
                    const { promise, resolve } = deferred<string>();
                    calls.push({ input, signal, answer: resolve });
                    return promise;
                }),
            },
        });
        const element = await attach(define(machine)());
        element.counter.send({ type: 'load' });
        expect(calls).toHaveLength(1);

        element.remove();
        expect(calls[0]?.signal.aborted).toBe(true);
        const update = vi.spyOn(element, 'requestUpdate');
        calls[0]?.answer('stale');
        await Promise.resolve();
        expect(update).not.toHaveBeenCalled();

        await attach(element);
        // Restored in "loading", the invoked promise runs again with the same input
        expect(element.counter.snapshot.value).toBe('loading');
        expect(calls).toHaveLength(2);
        expect(calls[1]?.input).toEqual({ id: 'label-0' });

        calls[1]?.answer('fresh');
        await vi.waitFor(() => {
            expect(text(element)).toBe('idle 0 fresh');
        });
    });
});

describe('ActorController rendering', () => {
    it('re-renders only when the selected value changes', async () => {
        const element = await attach(
            define(counterMachine, { selector: (snapshot) => snapshot.context.count })(),
        );
        const renders = element.renders;

        element.counter.send({ type: 'rename', label: 'quiet' });
        await element.updateComplete;
        expect(element.renders).toBe(renders);
        expect(element.counter.selected).toBe(0);

        element.counter.send({ type: 'increment' });
        await element.updateComplete;
        expect(element.renders).toBe(renders + 1);
        expect(element.counter.selected).toBe(1);
    });

    it('compares selected values with the given comparator', async () => {
        const element = await attach(
            define(counterMachine, {
                selector: (snapshot) => ({ count: snapshot.context.count }),
                compare: (previous, next) => previous.count === next.count,
            })(),
        );
        const renders = element.renders;
        const selected = element.counter.selected;

        element.counter.send({ type: 'rename', label: 'quiet' });
        await element.updateComplete;
        expect(element.renders).toBe(renders);
        expect(element.counter.selected).toBe(selected);
    });

    it('renders the error snapshot when the machine fails', async () => {
        const failing = counterMachine.provide({
            actors: {
                fetchLabel: fromPromise<string, { id: string }>(() =>
                    Promise.reject(new Error('down')),
                ),
            },
        });
        const element = await attach(define(failing)());

        element.counter.send({ type: 'load' });

        await vi.waitFor(() => {
            expect(element.counter.snapshot.status).toBe('error');
        });
    });
});

describe('ActorController events out', () => {
    it('dispatches mapped emitted events as bubbling, composed CustomEvents', async () => {
        const element = await attach(define(counterMachine, { events: { counted: 'counted' } })());
        const received: CustomEvent[] = [];
        const listener = (event: Event) => {
            if (event instanceof CustomEvent) {
                received.push(event);
            }
        };
        document.addEventListener('counted', listener);
        document.addEventListener('whispered', listener);

        element.counter.send({ type: 'increment' });
        element.counter.send({ type: 'whisper' });

        expect(received).toHaveLength(1);
        expect(received[0]?.detail).toEqual({ count: 1 });
        expect(received[0]?.bubbles).toBe(true);
        expect(received[0]?.composed).toBe(true);
        expect(received[0]?.target).toBe(element);
        document.removeEventListener('counted', listener);
        document.removeEventListener('whispered', listener);
    });

    it('uses the given event options', async () => {
        const element = await attach(
            define(counterMachine, {
                events: { counted: 'counted' },
                eventInit: { bubbles: false },
            })(),
        );
        const onDocument = vi.fn();
        const onElement = vi.fn();
        document.addEventListener('counted', onDocument);
        element.addEventListener('counted', onElement);

        element.counter.send({ type: 'increment' });

        expect(onElement).toHaveBeenCalledOnce();
        expect(onDocument).not.toHaveBeenCalled();
        document.removeEventListener('counted', onDocument);
    });

    it('turns an emitted event into a CustomEvent without its type', () => {
        const event = toCustomEvent('up-thing-done', { type: 'thing.done', id: 'a' });

        expect(event.type).toBe('up-thing-done');
        expect(event.detail).toEqual({ id: 'a' });
        expect(event.bubbles && event.composed).toBe(true);
    });
});

describe('ActorController types', () => {
    it('infers the snapshot, the selected value and the events from the machine', () => {
        const element = define(counterMachine, {
            selector: (snapshot) => snapshot.context.label,
        })();

        // Type-level only: the element is not connected, so the getters would throw
        expectTypeOf<typeof element.counter.snapshot>().toEqualTypeOf<
            SnapshotFrom<CounterMachine>
        >();
        expectTypeOf<typeof element.counter.selected>().toEqualTypeOf<string>();
        expectTypeOf<Parameters<typeof element.counter.send>[0]>().toEqualTypeOf<
            EventFromLogic<CounterMachine>
        >();

        // @ts-expect-error -- the machine defines no such event
        element.counter.send({ type: 'decrement' });
        // @ts-expect-error -- "rename" needs its label
        element.counter.send({ type: 'rename' });
        expect(element.counter.actorRef).toBeUndefined();
    });
});
