/**
 * The messages up-session-timeout uses, keyed as in uPortal's Messages.properties, with their
 * English text. The portal returns them in the user's locale (see lib/i18n.ts); these are the
 * defaults.
 */
export const SESSION_TIMEOUT_MESSAGES = {
    'session-timeout.title': 'Session expiration',
    'session-timeout.body1': 'Your session will expire  in {0} seconds.',
    'session-timeout.body1.one': 'Your session will expire in {0} second.',
    'session-timeout.body2': 'Do you want to stay signed in?',
    'session-timeout.button.keep-alive': 'Yes, keep me signed in',
    'session-timeout.button.logout': 'No, log me out',
    'session-timeout.keep-alive.failed': 'Error resetting your session!',
} as const satisfies Record<string, string>;

export type SessionTimeoutMessageKey = keyof typeof SESSION_TIMEOUT_MESSAGES;

/**
 * The countdown's key for the number of seconds left: `session-timeout.body1.one` where the
 * locale's plural rules call the number singular (1 in English and Spanish, 0 and 1 in French),
 * else `session-timeout.body1`. A locale the browser can't read counts as English.
 */
export function countdownMessageKey(locale: string, secondsLeft: number): SessionTimeoutMessageKey {
    let rules: Intl.PluralRules;
    try {
        rules = new Intl.PluralRules(locale);
    } catch {
        rules = new Intl.PluralRules('en');
    }
    return rules.select(secondsLeft) === 'one'
        ? 'session-timeout.body1.one'
        : 'session-timeout.body1';
}
