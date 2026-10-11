/**
 * The messages up-session-timeout uses, keyed as in uPortal's Messages.properties, with their
 * English text. The portal returns them in the user's locale (see lib/i18n.ts); these are the
 * defaults.
 */
export const SESSION_TIMEOUT_MESSAGES = {
    'session-timeout.title': 'Session expiration',
    'session-timeout.body1': 'Your session will expire  in {0} seconds.',
    'session-timeout.body2': 'Do you want to stay signed in?',
    'session-timeout.button.keep-alive': 'Yes, keep me signed in',
    'session-timeout.button.logout': 'No, log me out',
    'session-timeout.keep-alive.failed': 'Error resetting your session!',
} as const satisfies Record<string, string>;

export type SessionTimeoutMessageKey = keyof typeof SESSION_TIMEOUT_MESSAGES;
