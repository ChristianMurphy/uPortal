/** A clock for tests: time stands still until the test moves it, and timers fire as it does. */
export class ManualClock {
    #now: number;
    #nextId = 0;
    readonly #timers = new Map<
        number,
        { at: number; every: number | undefined; fire: () => void }
    >();

    constructor(now: number) {
        this.#now = now;
    }

    now(): number {
        return this.#now;
    }

    after(ms: number, callback: () => void): () => void {
        return this.#add(ms, undefined, callback);
    }

    every(ms: number, callback: () => void): () => void {
        return this.#add(ms, ms, callback);
    }

    /** The number of timers waiting to fire. */
    get pending(): number {
        return this.#timers.size;
    }

    /** Moves time forward by `ms`, firing every timer that falls due, in order. */
    advance(ms: number): void {
        this.advanceTo(this.#now + ms);
    }

    /** Moves time forward to `time`, firing every timer that falls due, in order. */
    advanceTo(time: number): void {
        for (;;) {
            let due:
                | [number, { at: number; every: number | undefined; fire: () => void }]
                | undefined;
            for (const entry of this.#timers) {
                if (entry[1].at <= time && (due === undefined || entry[1].at < due[1].at)) {
                    due = entry;
                }
            }
            if (due === undefined) {
                break;
            }
            const [id, timer] = due;
            this.#now = Math.max(this.#now, timer.at);
            if (timer.every === undefined) {
                this.#timers.delete(id);
            } else {
                timer.at += timer.every;
            }
            timer.fire();
        }
        this.#now = Math.max(this.#now, time);
    }

    /** Sets the time without firing timers, as when a computer wakes from sleep. */
    jumpTo(time: number): void {
        this.#now = time;
    }

    #add(ms: number, every: number | undefined, fire: () => void): () => void {
        const id = this.#nextId++;
        this.#timers.set(id, { at: this.#now + Math.max(ms, 0), every, fire });
        return () => {
            this.#timers.delete(id);
        };
    }
}
