/**
 * The messages up-favorites uses, keyed as in uPortal's Messages.properties, with their English
 * text. The portal returns them in the user's locale (see lib/i18n.ts); these are the defaults.
 */
export const FAVORITES_MESSAGES = {
    favorites: 'Favorites',
    loading: 'Loading',
    close: 'Close',
    'favorites.have.none': 'You have no favorites.',
    'favorites.instruction.add.via.contextual.menu':
        'You can favorite any portlet you are using via the contextual options menu accessed from its title bar.',
    'favorites.invitation.to.marketplace.short': 'Browse Portlets',
    'favorites.lack.permission.to.unfavorite.collection':
        'You lack permission to unfavorite this collection.',
    'favorites.lack.permission.to.unfavorite.portlet':
        'You lack permission to unfavorite this portlet.',
    'favorites.lock.icon.legend': 'Favorites with the lock icon cannot be unfavorited.',
    'favorites.stop.editing': 'Stop editing favorites',
    'favorites.unfavorite.success.parameterized': 'You have unfavorited {0}.',
    'favorites.unfavorite.fail.parameterized':
        'The portal failed to unfavorite {0}.  Try again later.  Please contact support if this issue persists.',
    'favorites.unfavorite.fail.lack.permission.parameterized':
        'You lack permission to unfavorite {0}.',
    'remove.from.my.favorites': 'Remove from my favorites',
    'favorites.edit.order': 'Reorder favorites',
    'favorites.remove.parameterized': 'Remove {0} from my favorites',
    'favorites.move.up': 'Move {0} up',
    'favorites.move.down': 'Move {0} down',
    'favorites.moved': 'Moved {0} to position {1} of {2}.',
    'favorites.move.fail.parameterized': 'The portal failed to move {0}. Try again later.',
    'favorites.move.keyboard.help': 'Alt+Up and Alt+Down move the favorite that has focus.',
    'favorites.load.fail': 'Your favorites could not be loaded.',
    'favorites.session.expired': 'Your session has expired. Sign in again to see your favorites.',
    'favorites.try.again': 'Try again',
    'favorites.opens.new.window': '(opens in a new window)',
} as const satisfies Record<string, string>;

export type FavoritesMessageKey = keyof typeof FAVORITES_MESSAGES;
