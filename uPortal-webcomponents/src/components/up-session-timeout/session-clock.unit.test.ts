import { describe, expect, it, vi } from 'vite-plus/test';
import { ManualClock } from '../../test-support/manual-clock';
import {
    BUFFER_MS,
    dialogDisplayMs,
    laterLogoutTime,
    logoutTimeFrom,
    secondsLeft,
    systemClock,
    type LogoutTimeStore,
} from './session-clock';

const SECOND = 1000;
const MINUTE = 60 * SECOND;

function storeWith(value: number | undefined): LogoutTimeStore {
    return { read: () => value, write: () => undefined };
}

describe('dialogDisplayMs', () => {
    it.each([
        [undefined, 60 * SECOND],
        ['90', 90 * SECOND],
        [' 45 ', 45 * SECOND],
        ['0', 60 * SECOND],
        ['-5', 60 * SECOND],
        ['1.5', 60 * SECOND],
        ['soon', 60 * SECOND],
        ['', 60 * SECOND],
    ])('reads %j as %d ms', (attribute, expected) => {
        expect(dialogDisplayMs(attribute, 30 * MINUTE)).toBe(expected);
    });

    it('never lasts longer than the session', () => {
        expect(dialogDisplayMs('3600', 30 * MINUTE)).toBe(30 * MINUTE);
    });
});

describe('logoutTimeFrom', () => {
    it('logs out the buffer before the session expires', () => {
        expect(logoutTimeFrom(0, 30 * MINUTE, MINUTE)).toBe(30 * MINUTE - BUFFER_MS);
    });

    it('still shows the whole countdown when the session is shorter than buffer and countdown', () => {
        expect(logoutTimeFrom(1000, 60 * SECOND, 45 * SECOND)).toBe(1000 + 45 * SECOND);
    });
});

describe('secondsLeft', () => {
    it.each([
        [60 * SECOND, 60],
        [59_600, 60],
        [59_400, 59],
        [0, 0],
        [-2 * SECOND, -2],
    ])('rounds %d ms to %d seconds', (left, expected) => {
        expect(secondsLeft(100_000 + left, 100_000)).toBe(expected);
    });
});

describe('laterLogoutTime', () => {
    it('returns a later saved time', () => {
        expect(laterLogoutTime(storeWith(2000), 1000)).toBe(2000);
    });

    it.each([[undefined], [1000], [500]])('ignores a saved time of %j', (saved) => {
        expect(laterLogoutTime(storeWith(saved), 1000)).toBeUndefined();
    });
});

describe('systemClock', () => {
    it('reads the time and runs and cancels timers', () => {
        vi.useFakeTimers({ now: 5000 });
        try {
            const once = vi.fn();
            const repeated = vi.fn();
            const cancelled = vi.fn();
            systemClock.after(100, once);
            const stop = systemClock.every(50, repeated);
            systemClock.after(100, cancelled)();

            expect(systemClock.now()).toBe(5000);
            vi.advanceTimersByTime(100);
            stop();
            vi.advanceTimersByTime(100);

            expect(once).toHaveBeenCalledTimes(1);
            expect(repeated).toHaveBeenCalledTimes(2);
            expect(cancelled).not.toHaveBeenCalled();
        } finally {
            vi.useRealTimers();
        }
    });
});

describe('ManualClock', () => {
    it('fires timers in time order as it moves', () => {
        const clock = new ManualClock(0);
        const fired: string[] = [];
        clock.after(300, () => fired.push(`after@${clock.now()}`));
        const stop = clock.every(200, () => fired.push(`every@${clock.now()}`));

        clock.advance(500);
        stop();
        clock.advance(500);

        expect(fired).toEqual(['every@200', 'after@300', 'every@400']);
        expect(clock.now()).toBe(1000);
        expect(clock.pending).toBe(0);
    });

    it('jumps without firing timers', () => {
        const clock = new ManualClock(0);
        const fired = vi.fn();
        clock.after(100, fired);

        clock.jumpTo(1000);

        expect(fired).not.toHaveBeenCalled();
        expect(clock.now()).toBe(1000);
        clock.advance(0);
        expect(fired).toHaveBeenCalledTimes(1);
    });
});
