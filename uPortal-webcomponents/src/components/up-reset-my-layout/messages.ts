/**
 * The messages up-reset-my-layout uses, keyed as in uPortal's Messages.properties, with their
 * English text. The portal returns them in the user's locale (see lib/i18n.ts); these are the
 * defaults. Most are the keys the Reset My Layout portlet's views used.
 */
export const RESET_MY_LAYOUT_MESSAGES = {
    'reset.my.layout.description': 'Use this portlet to reset your portal layout to the defaults.',
    'reset.my.layout.note': 'NOTE: You must sign on again to see the changes.',
    'reset.now': 'Reset Now',
    'reset.user.layout': 'Reset User Layout',
    'resetting.user.layout.irreversible': "Resetting a user's layout is irreversible.",
    'are.you.sure.reset.layout': 'Are you sure you want to reset the layout for {0}?',
    'reset.layout': 'Reset Layout',
    cancel: 'Cancel',
    success: 'Success',
    'layout.for.name.has.been.reset': 'Layout for {0} has been reset',
    continue: 'Continue',
    loading: 'Loading',
    'reset.my.layout.load.fail': 'Your layout could not be found.',
    'reset.my.layout.reset.fail': 'Your layout could not be reset. Try again later.',
    'reset.my.layout.session.expired':
        'Your session has expired. Sign in again to reset your layout.',
    'reset.my.layout.try.again': 'Try again',
} as const satisfies Record<string, string>;

export type ResetMyLayoutMessageKey = keyof typeof RESET_MY_LAYOUT_MESSAGES;
