import axe from 'axe-core';

/**
 * Runs axe-core (WCAG 2.2 A/AA plus best practices) on an element, shadow root included, once its
 * transitions have ended, and returns the violations as readable strings, so a failing assertion
 * names the rule and node.
 */
export async function axeViolations(element: Element): Promise<string[]> {
    // Let CSS transitions end first (a tab's color fading after a click): axe would measure the
    // color halfway and report a contrast it never settles on
    const roots: (Document | ShadowRoot)[] = [document];
    if (element.shadowRoot) {
        roots.push(element.shadowRoot);
    }
    await Promise.all(
        roots
            .flatMap((root) => root.getAnimations())
            // Not the endless ones, such as a loading spinner's
            .filter((animation) => animation.effect?.getComputedTiming().endTime !== Infinity)
            .map((animation) => animation.finished.catch(() => undefined)),
    );
    const results = await axe.run(element, {
        runOnly: {
            type: 'tag',
            values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'],
        },
        // A lone component is not a page: it has no landmarks, main heading or title of its own
        rules: {
            region: { enabled: false },
            'page-has-heading-one': { enabled: false },
            'landmark-one-main': { enabled: false },
        },
    });
    return results.violations.map(
        (violation) =>
            `${violation.id}: ${violation.help} (${violation.nodes.map((node) => node.target.join(' ')).join(', ')})`,
    );
}
