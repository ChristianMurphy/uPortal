import { describe, expect, it } from 'vite-plus/test';
import plugin, { checkTemplate, findPhysicalCss } from './logical-css';

const scssSources = import.meta.glob<string>('../**/*.scss', {
    query: '?raw',
    import: 'default',
    eager: true,
});

describe('findPhysicalCss', () => {
    it('finds physical sides', () => {
        const text = `
            .a { margin-left: 1rem; padding-right:0; border-left-color: red; }
            .b { left: 0; right : 2px; border-top-right-radius: 4px; }
            .c { text-align: left; float:right; clear: left; scroll-margin-right: 1px; }`;
        expect(findPhysicalCss(text).map((found) => found.declaration)).toEqual([
            'margin-left:',
            'padding-right:',
            'border-left-color:',
            'left:',
            'right :',
            'border-top-right-radius:',
            'text-align: left',
            'float:right',
            'clear: left',
            'scroll-margin-right:',
        ]);
    });

    it('accepts logical properties and values', () => {
        const text = `
            .a { margin-inline-start: 1rem; padding-inline-end: 0; inset-inline-start: 0; }
            .b { text-align: start; float: inline-end; border-start-end-radius: 4px; }
            .c { inset-inline: 0; text-align: center; --left-gap: 1px; }`;
        expect(findPhysicalCss(text)).toEqual([]);
    });
});

function lint(tag: { type: string; name?: string }, ...raws: string[]): string[] {
    const messages: string[] = [];
    checkTemplate(
        { tag, quasi: { quasis: raws.map((raw) => ({ value: { raw } })) } },
        { report: (problem) => messages.push(problem.message) },
    );
    return messages;
}

describe('the uportal/logical-css rule', () => {
    it('reports physical CSS in css templates', () => {
        const messages = lint({ type: 'Identifier', name: 'css' }, '.a { margin-left: 0; }', '');
        expect(messages).toHaveLength(1);
        expect(messages[0]).toContain('"margin-left:"');
    });

    it('ignores other templates', () => {
        expect(lint({ type: 'Identifier', name: 'html' }, '<p style="left: 0">')).toEqual([]);
        expect(lint({ type: 'MemberExpression' }, 'left: 0')).toEqual([]);
    });

    it('is the plugin oxlint loads', () => {
        const messages: string[] = [];
        const visitor = plugin.rules['logical-css'].create({
            report: (problem) => messages.push(problem.message),
        });
        visitor.TaggedTemplateExpression({
            tag: { type: 'Identifier', name: 'css' },
            quasi: { quasis: [{ value: { raw: 'float: right;' } }] },
        });
        expect(plugin.meta.name).toBe('uportal');
        expect(messages).toHaveLength(1);
    });
});

describe('the SCSS files', () => {
    it.each(Object.keys(scssSources))('%s has no physical CSS', (path) => {
        expect(findPhysicalCss(scssSources[path] ?? '')).toEqual([]);
    });
});
