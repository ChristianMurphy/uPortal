/**
 * The messages up-background-preference uses, keyed as in uPortal's Messages.properties, with
 * their English text. The portal returns them in the user's locale (see lib/i18n.ts); these are the
 * defaults. The portlet wrote "Change Background Image" and "None" into its JSP; the component
 * gives them keys.
 */
export const BACKGROUND_PREFERENCE_MESSAGES = {
    'background.preference.change': 'Change Background Image',
    'background.preference.none': 'None',
    loading: 'Loading',
    'background.preference.load.fail': 'The backgrounds could not be loaded.',
    'background.preference.save.fail': 'Your background could not be changed. Try again later.',
    'background.preference.session.expired':
        'Your session has expired. Sign in again to change your background.',
    'background.preference.try.again': 'Try again',
} as const satisfies Record<string, string>;

export type BackgroundPreferenceMessageKey = keyof typeof BACKGROUND_PREFERENCE_MESSAGES;
