/**
 * The messages the search components use, keyed as in uPortal's Messages.properties, with their
 * English text. The portal returns them in the user's locale (see lib/i18n.ts); these are the
 * defaults.
 */
export const SEARCH_BOX_MESSAGES = {
    search: 'Search',
    'search.terms': 'Search Terms',
    'search.submit': 'Search',
    'search.suggestions': 'Suggestions',
    'search.suggestions.count':
        '{0} results are available, use up and down arrow keys to navigate.',
} as const;

export const SEARCH_MESSAGES = {
    ...SEARCH_BOX_MESSAGES,
    loading: 'Loading',
    'search.results': 'Search Results',
    'search.results.found': '{0} results for {1}.',
    'search.results.people': 'People',
    'search.results.portlets': 'Portlets',
    'search.no.results.found': 'No Results Found',
    'search.group.fail': 'These results could not be loaded. The other results are complete.',
    'search.load.fail': 'The search failed.',
    'search.try.again': 'Try again',
    'search.session.expired': 'Your session has expired. Sign in again to search.',
    'attribute.displayName.username': 'Net ID',
    'attribute.displayName.givenName': 'First Name',
    'attribute.displayName.sn': 'Last Name',
    'attribute.displayName.mail': 'Email',
    'attribute.displayName.telephoneNumber': 'Phone',
    name: 'Name',
    description: 'Description',
    'search.score': 'Relevance',
} as const;

/**
 * The attributes a result shows, with the message that labels each; others are not shown. The
 * Search portlet's results page showed the same ones.
 */
export const SHOWN_ATTRIBUTES: Readonly<Record<string, keyof typeof SEARCH_MESSAGES>> = {
    username: 'attribute.displayName.username',
    givenName: 'attribute.displayName.givenName',
    sn: 'attribute.displayName.sn',
    mail: 'attribute.displayName.mail',
    telephoneNumber: 'attribute.displayName.telephoneNumber',
    name: 'name',
    description: 'description',
    score: 'search.score',
};
