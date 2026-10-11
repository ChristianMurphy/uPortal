/**
 * The messages up-self-edit-account uses, keyed as in uPortal's Messages.properties, with their
 * English text. The portal returns them in the user's locale (see lib/i18n.ts); these are the
 * defaults. All but the account.* keys are the ones the portlet's JSPs used.
 */
export const SELF_EDIT_ACCOUNT_MESSAGES = {
    'my.account': 'My Account',
    edit: 'Edit',
    'edit.my.account': 'Edit My Account',
    'my.account.details': 'My Account Details',
    password: 'Password',
    'confirm.password': 'Confirm Password',
    'passwords.must.match': 'Passwords must match',
    save: 'Save',
    cancel: 'Cancel',
    'account.updated': 'Account Updated',
    'your.account.has.been.successfully.updated.your.changes.will.be.visible.upon.your.next.login':
        'Your account has been successfully updated.  Your changes will be visible upon your next login.',
    loading: 'Loading',
    'account.load.fail': 'Your account could not be loaded.',
    'account.session.expired': 'Your session has expired. Sign in again to see your account.',
    'account.save.fail': 'Your account could not be updated. Try again later.',
    'account.try.again': 'Try again',
} as const satisfies Record<string, string>;

export type SelfEditAccountMessageKey = keyof typeof SELF_EDIT_ACCOUNT_MESSAGES;
