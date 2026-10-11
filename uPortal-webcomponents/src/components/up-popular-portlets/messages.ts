/**
 * The messages up-popular-portlets uses, keyed as in uPortal's Messages.properties, with their
 * English text. The portal returns them in the user's locale (see lib/i18n.ts); these are the
 * defaults. The first keys are the Popular Portlets portlet's own; `popular.portlets.*` are the
 * texts its DataTables table had in its JavaScript.
 */
export const POPULAR_PORTLETS_MESSAGES = {
    'most.frequently.added': 'Most Frequently Added',
    previous: 'Previous',
    next: 'Next',
    title: 'Title',
    'number.times': '# Times',
    // The portal's values are HTML (`&lt; Previous`); the element decodes them
    'datatables.paginate.previous': '< Previous',
    'datatables.paginate.next': 'Next >',
    loading: 'Loading',
    'popular.portlets.filters': 'Filters',
    'popular.portlets.filter.by': 'Filter by {0}',
    'popular.portlets.filter.remove': 'Remove the filter {0}',
    // A plural family (MOD-I235), chosen by the number of items, {3}. English keeps the
    // portlet's text for one item too
    'popular.portlets.page.info.one': 'Viewing page {0}. Showing records {1} to {2} of {3} items.',
    'popular.portlets.page.info.other':
        'Viewing page {0}. Showing records {1} to {2} of {3} items.',
    'popular.portlets.per.page': 'per page',
    'popular.portlets.pagination': 'pagination',
    'popular.portlets.none': 'No data available in table',
    'popular.portlets.none.matching': 'No matching records found',
    'popular.portlets.aggregation.note':
        '* These values are provided by daily statistics aggregation',
    'popular.portlets.load.fail': 'The popular portlets could not be loaded.',
    'popular.portlets.session.expired':
        'Your session has expired. Sign in again to see the popular portlets.',
    'popular.portlets.try.again': 'Try again',
} as const satisfies Record<string, string>;

export type PopularPortletsMessageKey = keyof typeof POPULAR_PORTLETS_MESSAGES;

const ENTITIES: Readonly<Record<string, string>> = {
    '&lt;': '<',
    '&gt;': '>',
    '&quot;': '"',
    '&#39;': "'",
    '&amp;': '&',
};

/** Turns the HTML entities that Messages.properties uses into text; Lit escapes text again. */
export function decodeEntities(message: string): string {
    return message.replace(
        /&(?:lt|gt|quot|#39|amp);/g,
        /* v8 ignore next -- the fallback is unreachable: the pattern matches only listed entities */
        (entity) => ENTITIES[entity] ?? entity,
    );
}
