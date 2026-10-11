/**
 * The session timeout's arithmetic and its shared logout time, as plain functions.
 *
 * The rules are those of the Session Timeout portlet (`session-timeout.js`): the warning opens a
 * buffer of 30 seconds plus the dialog's display time before the session would expire, and the
 * user is logged out when the dialog's time runs out, so the session never expires on the
 * server first. Tabs share the logout time in `localStorage` under the portlet's key, and a
 * later time saved by another tab postpones the warning.
 */

export const SECOND = 1000;

/** Opens the warning this long before the session would expire, on top of the display time. */
export const BUFFER_MS = 30 * SECOND;

/** The portlet's default `dialogDisplaySeconds`. */
export const DEFAULT_DIALOG_DISPLAY_SECONDS = 60;

/** The timeout used when the portal does not say (the portlet script's default too). */
export const DEFAULT_SESSION_TIMEOUT_SECONDS = 30 * 60;

/** Where the portlet saved the logout time; kept so portlet and component tabs agree. */
export const LOGOUT_TIME_KEY = 'UportalSessionTimeoutLogoutTime';

/** How often the countdown updates while the warning is open. */
export const TICK_MS = 500;

/** The time and the timers the warning runs on; tests use a clock they move by hand. */
export interface Clock {
    now(): number;
    /** Calls back once after `ms`; returns a function that cancels the call. */
    after(ms: number, callback: () => void): () => void;
    /** Calls back every `ms`; returns a function that stops the calls. */
    every(ms: number, callback: () => void): () => void;
}

/** The browser's clock and timers. */
export const systemClock: Clock = {
    now: () => Date.now(),
    after(ms, callback) {
        const timer = setTimeout(callback, ms);
        return () => {
            clearTimeout(timer);
        };
    },
    every(ms, callback) {
        const timer = setInterval(callback, ms);
        return () => {
            clearInterval(timer);
        };
    },
};

/** Reads and writes the logout time that the user's tabs share. */
export interface LogoutTimeStore {
    read(): number | undefined;
    write(logoutTime: number): void;
}

/** The logout time in `localStorage`; reading and writing fail quietly where storage is off. */
export const browserLogoutTimeStore: LogoutTimeStore = {
    read() {
        try {
            const value = Number(localStorage.getItem(LOGOUT_TIME_KEY));
            return Number.isFinite(value) && value > 0 ? value : undefined;
        } catch {
            return undefined;
        }
    },
    write(logoutTime) {
        try {
            localStorage.setItem(LOGOUT_TIME_KEY, String(logoutTime));
        } catch {
            // Without storage each tab keeps its own time
        }
    },
};

/**
 * The display time in milliseconds from the `dialog-display-seconds` attribute: a whole number
 * of seconds above zero, else the default, and at most the session timeout.
 */
export function dialogDisplayMs(attribute: string | undefined, sessionTimeoutMs: number): number {
    const seconds = attribute === undefined ? Number.NaN : Number(attribute.trim());
    const valid =
        Number.isInteger(seconds) && seconds > 0 ? seconds : DEFAULT_DIALOG_DISPLAY_SECONDS;
    return Math.min(valid * SECOND, sessionTimeoutMs);
}

/** When the user is logged out, for a session period that starts now. */
export function logoutTimeFrom(now: number, sessionTimeoutMs: number, displayMs: number): number {
    return now + Math.max(sessionTimeoutMs - BUFFER_MS - displayMs, 0) + displayMs;
}

/** Whole seconds left until the logout time, as the countdown shows them. */
export function secondsLeft(logoutTime: number, now: number): number {
    return Math.round((logoutTime - now) / SECOND);
}

/** A later logout time that another tab saved, if there is one. */
export function laterLogoutTime(store: LogoutTimeStore, logoutTime: number): number | undefined {
    const saved = store.read();
    return saved !== undefined && saved > logoutTime ? saved : undefined;
}
