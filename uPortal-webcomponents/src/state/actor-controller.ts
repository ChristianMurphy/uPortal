/**
 * A Lit reactive controller that runs an XState v5 actor for its host element.
 *
 * Generic on purpose: it depends on `lit` and `xstate` only, so any LitElement can use it by
 * composition, `new ActorController(this, machine, options)`, without a base class or mixin.
 *
 * Lifecycle:
 * - The first `hostConnected` creates the actor, with `options.input()` as its input, and
 *   starts it.
 * - `hostDisconnected` unsubscribes, keeps the actor's persisted snapshot and stops it. An
 *   invoked promise stops with it: its abort signal fires and its result is ignored.
 * - The next `hostConnected` (the element moved in the DOM) restores the actor from that
 *   snapshot, so the state survives the move. A promise that was in flight is started again with
 *   the same input, so invoked requests should be safe to repeat.
 * - Events sent while the host is disconnected (or before it first connects) are queued and
 *   delivered, in order, once the actor runs.
 *
 * Rendering: the host re-renders only when the selected value changes (`Object.is` unless a
 * `compare` function is given). Without a selector the whole snapshot is selected.
 *
 * Events out: machine `emit` actions whose type is mapped in `options.events` are dispatched on
 * the host as `CustomEvent`s (see {@link toCustomEvent}).
 */
import type { ReactiveController, ReactiveControllerHost } from 'lit';
import {
    createActor,
    type Actor,
    type AnyStateMachine,
    type EmittedFrom,
    type EventFromLogic,
    type InputFrom,
    type Snapshot,
    type SnapshotFrom,
    type Subscription,
} from 'xstate';

/**
 * The emitted event types of a machine, mapped to the DOM event names to dispatch. (It is `{}`
 * for a machine that emits nothing, which the lint rule below cannot tell from a mistake.)
 */
/* oxlint-disable typescript/no-generated-empty-object-type */
export type EmittedEventNames<TMachine extends AnyStateMachine> = Partial<
    Record<EmittedFrom<TMachine>['type'], string>
>;
/* oxlint-enable typescript/no-generated-empty-object-type */

export interface ActorControllerOptions<TMachine extends AnyStateMachine, TSelected> {
    /** The actor's input, read from the host when the actor is first created. */
    input?: () => InputFrom<TMachine>;
    /** Picks what the host renders from the snapshot; the host updates when it changes. */
    selector?: (snapshot: SnapshotFrom<TMachine>) => TSelected;
    /** Whether two selected values are the same; defaults to `Object.is`. */
    compare?: (previous: TSelected, next: TSelected) => boolean;
    /** Emitted events to dispatch on the host, by emitted type. */
    // oxlint-disable-next-line typescript/no-generated-empty-object-type
    events?: EmittedEventNames<TMachine>;
    /** Options of the dispatched events; defaults to bubbling and composed. */
    eventInit?: Omit<CustomEventInit, 'detail'>;
}

const DEFAULT_EVENT_INIT: Omit<CustomEventInit, 'detail'> = { bubbles: true, composed: true };

/**
 * A DOM event for an emitted machine event: the emitted event without its `type` becomes the
 * `detail`.
 */
export function toCustomEvent<TEmitted extends { type: string }>(
    name: string,
    emitted: TEmitted,
    init: Omit<CustomEventInit, 'detail'> = DEFAULT_EVENT_INIT,
): CustomEvent<Omit<TEmitted, 'type'>> {
    const { type: _type, ...detail } = emitted;
    return new CustomEvent(name, { ...init, detail });
}

export class ActorController<
    TMachine extends AnyStateMachine,
    TSelected = SnapshotFrom<TMachine>,
> implements ReactiveController {
    readonly #host: ReactiveControllerHost & EventTarget;
    readonly #machine: TMachine;
    readonly #options: ActorControllerOptions<TMachine, TSelected>;
    readonly #select: (snapshot: SnapshotFrom<TMachine>) => TSelected;
    readonly #compare: (previous: TSelected, next: TSelected) => boolean;
    readonly #queue: EventFromLogic<TMachine>[] = [];
    #actor: Actor<TMachine> | undefined;
    #subscriptions: Subscription[] = [];
    #persisted: Snapshot<unknown> | undefined;
    #snapshot: SnapshotFrom<TMachine> | undefined;
    #selected: { value: TSelected } | undefined;

    constructor(
        host: ReactiveControllerHost & EventTarget,
        machine: TMachine,
        options: ActorControllerOptions<TMachine, TSelected> = {},
    ) {
        this.#host = host;
        this.#machine = machine;
        this.#options = options;
        this.#select =
            options.selector ??
            // Without a selector, TSelected is the snapshot type (the class default)
            // oxlint-disable-next-line typescript/no-unsafe-type-assertion, typescript/no-unnecessary-type-assertion
            ((snapshot) => snapshot as unknown as TSelected);
        this.#compare = options.compare ?? Object.is;
        host.addController(this);
    }

    /** The running actor; undefined before the first connect and while disconnected. */
    get actorRef(): Actor<TMachine> | undefined {
        return this.#actor;
    }

    /** The latest snapshot; while disconnected, the one the actor had when it stopped. */
    get snapshot(): SnapshotFrom<TMachine> {
        if (this.#snapshot === undefined) {
            throw new Error('ActorController: the host has not been connected yet');
        }
        return this.#snapshot;
    }

    /** The selected value the host last rendered for (see `selector` and `compare`). */
    get selected(): TSelected {
        if (this.#selected === undefined) {
            throw new Error('ActorController: the host has not been connected yet');
        }
        return this.#selected.value;
    }

    /** Sends an event to the actor, or queues it until the host is connected. */
    send(event: EventFromLogic<TMachine>): void {
        if (this.#actor === undefined) {
            this.#queue.push(event);
            return;
        }
        this.#actor.send(event);
    }

    hostConnected(): void {
        if (this.#actor !== undefined) {
            return;
        }
        const persisted = this.#persisted;
        // Input only matters for a new actor; a restored one keeps its context
        const actor = createActor(this.#machine, {
            input: persisted === undefined ? this.#options.input?.() : undefined,
            snapshot: persisted,
        });
        this.#persisted = undefined;
        this.#actor = actor;
        this.#subscriptions = [
            actor.subscribe({
                next: (snapshot) => {
                    this.#update(snapshot);
                },
                // A machine that fails stops in an error snapshot; render that one
                error: () => {
                    this.#update(actor.getSnapshot());
                },
            }),
        ];
        const names = this.#options.events;
        if (names !== undefined) {
            const lookup: Readonly<Record<string, string | undefined>> = names;
            const init = this.#options.eventInit ?? DEFAULT_EVENT_INIT;
            this.#subscriptions.push(
                actor.on('*', (emitted: { type: string }) => {
                    const name = lookup[emitted.type];
                    if (name !== undefined) {
                        this.#host.dispatchEvent(toCustomEvent(name, emitted, init));
                    }
                }),
            );
        }
        actor.start();
        this.#update(actor.getSnapshot());
        for (const event of this.#queue.splice(0)) {
            actor.send(event);
        }
    }

    hostDisconnected(): void {
        const actor = this.#actor;
        if (actor === undefined) {
            return;
        }
        for (const subscription of this.#subscriptions.splice(0)) {
            subscription.unsubscribe();
        }
        this.#persisted = actor.getPersistedSnapshot();
        this.#actor = undefined;
        actor.stop();
    }

    #update(snapshot: SnapshotFrom<TMachine>): void {
        this.#snapshot = snapshot;
        const next = this.#select(snapshot);
        if (this.#selected !== undefined && this.#compare(this.#selected.value, next)) {
            return;
        }
        this.#selected = { value: next };
        this.#host.requestUpdate();
    }
}
