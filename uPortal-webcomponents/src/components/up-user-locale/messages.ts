/**
 * The messages up-user-locale uses, keyed as in uPortal's Messages.properties, with their English
 * text. The portal returns them in the user's locale (see lib/i18n.ts); these are the defaults.
 */
export const USER_LOCALE_MESSAGES = {
    'set.language.preference': 'Set Language Preference',
    update: 'Update',
    loading: 'Loading',
    'locale.load.fail': 'The languages could not be loaded.',
    'locale.save.fail': 'Your language could not be changed. Try again later.',
    'locale.try.again': 'Try again',
} as const satisfies Record<string, string>;

export type UserLocaleMessageKey = keyof typeof USER_LOCALE_MESSAGES;
