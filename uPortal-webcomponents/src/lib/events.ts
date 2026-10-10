/**
 * Event naming convention for uPortal web components.
 *
 * Components announce what happened with a `CustomEvent` named `up-<subject>-<past-tense verb>`,
 * all lowercase, such as `up-favorite-removed` or `up-favorites-loaded`. Events bubble and are
 * composed, so a listener on the page sees them through shadow roots; `detail` carries plain data.
 * Events report facts after the fact and are not cancelable: the portal's REST API, not the page,
 * decides whether something may happen.
 */
const EVENT_NAME = /^up(-[a-z]+){2,}$/;

export function isValidEventName(name: string): boolean {
    return EVENT_NAME.test(name);
}

/** Creates an event that follows the convention; throws for a name that does not. */
export function createEvent<T>(name: string, detail: T): CustomEvent<T> {
    if (!isValidEventName(name)) {
        throw new Error(
            `'${name}' does not follow the up-<subject>-<verb> event naming convention`,
        );
    }
    return new CustomEvent<T>(name, { detail, bubbles: true, composed: true });
}
