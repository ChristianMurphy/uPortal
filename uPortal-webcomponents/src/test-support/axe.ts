import axe from 'axe-core';

/**
 * Runs axe-core (WCAG 2.2 A/AA plus best practices) on an element, shadow root included, and
 * returns the violations as readable strings, so a failing assertion names the rule and node.
 */
export async function axeViolations(element: Element): Promise<string[]> {
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
