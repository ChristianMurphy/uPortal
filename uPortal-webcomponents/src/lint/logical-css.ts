/**
 * An oxlint rule, `uportal/logical-css`: the components' styles use logical CSS (`margin-inline-start`,
 * `inset-inline-end`, `text-align: start`), so a right-to-left locale mirrors them without other
 * styles. The rule reports physical sides in Lit `css` templates; logical-css.unit.test.ts runs
 * the same matcher over the SCSS files, which oxlint does not read.
 */

/** A physical declaration found in a style text. */
export interface PhysicalCss {
    readonly declaration: string;
    readonly index: number;
}

const PHYSICAL = [
    // margin-left, padding-right, border-left-color, scroll-margin-right, …
    /(?<![\w-])(?:scroll-)?(?:margin|padding|border)-(?:left|right)(?:-[a-z]+)?\s*:/g,
    // border-top-left-radius, …
    /(?<![\w-])border-(?:top|bottom)-(?:left|right)-radius\s*:/g,
    // left: 0, right: 1rem
    /(?<![\w-])(?:left|right)\s*:/g,
    // text-align: left, float: right, clear: left
    /(?<![\w-])(?:text-align|float|clear)\s*:\s*(?:left|right)\b/g,
];

/** The physical declarations in a style text, in the order they appear. */
export function findPhysicalCss(text: string): PhysicalCss[] {
    const found: PhysicalCss[] = [];
    for (const pattern of PHYSICAL) {
        for (const match of text.matchAll(pattern)) {
            found.push({ declaration: match[0].replace(/\s+/g, ' '), index: match.index });
        }
    }
    return found.toSorted((a, b) => a.index - b.index);
}

interface TemplateElement {
    readonly value: { readonly raw: string };
}

interface TaggedTemplate {
    readonly tag: { readonly type: string; readonly name?: string };
    readonly quasi: { readonly quasis: readonly TemplateElement[] };
}

interface RuleContext {
    report(problem: { node: TemplateElement; message: string }): void;
}

/** Reports each physical declaration in a template tagged `css`. */
export function checkTemplate(node: TaggedTemplate, context: RuleContext): void {
    if (node.tag.type !== 'Identifier' || node.tag.name !== 'css') {
        return;
    }
    for (const quasi of node.quasi.quasis) {
        for (const { declaration } of findPhysicalCss(quasi.value.raw)) {
            context.report({
                node: quasi,
                message: `Physical CSS "${declaration}" does not mirror in right-to-left locales; use the logical property or value (inline-start, inline-end, start, end).`,
            });
        }
    }
}

export default {
    meta: { name: 'uportal' },
    rules: {
        'logical-css': {
            create(context: RuleContext) {
                return {
                    TaggedTemplateExpression(node: TaggedTemplate) {
                        checkTemplate(node, context);
                    },
                };
            },
        },
    },
};
