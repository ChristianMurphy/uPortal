import { afterEach, describe, expect, it } from 'vite-plus/test';
import { applyPageBackground, backgroundSheet, cssUrl } from './page-background';

const target = document.createElement('div');
target.id = 'page-under-test';
document.body.append(target);

function backgroundOf(element: Element): string {
    return getComputedStyle(element).backgroundImage;
}

afterEach(() => {
    applyPageBackground(document, '#page-under-test', undefined);
});

describe('cssUrl', () => {
    it('quotes a plain URL', () => {
        expect(cssUrl('/uPortal/bg/galaxy.jpg')).toBe('url("/uPortal/bg/galaxy.jpg")');
    });

    it('escapes the characters that could end the string', () => {
        expect(cssUrl('a"b\\c\nd')).toBe('url("a\\22 b\\5c c\\a d")');
    });
});

describe('applyPageBackground', () => {
    it('puts the image behind the elements the selector matches', () => {
        expect(applyPageBackground(document, '#page-under-test', '/bg/galaxy.jpg')).toBe(true);

        expect(backgroundOf(target)).toContain('/bg/galaxy.jpg');
    });

    it('keeps one adopted sheet however often the background changes', () => {
        applyPageBackground(document, '#page-under-test', '/bg/galaxy.jpg');
        const sheets = document.adoptedStyleSheets.length;

        applyPageBackground(document, '#page-under-test', '/bg/mountain.jpg');
        applyPageBackground(document, '#page-under-test', '/bg/nebula.jpg');

        expect(document.adoptedStyleSheets).toHaveLength(sheets);
        expect(
            document.adoptedStyleSheets.filter((sheet) => sheet === backgroundSheet(document)),
        ).toHaveLength(1);
        expect(backgroundSheet(document).cssRules).toHaveLength(1);
        expect(backgroundOf(target)).toContain('/bg/nebula.jpg');
    });

    it('removes the rule for none', () => {
        applyPageBackground(document, '#page-under-test', '/bg/galaxy.jpg');

        expect(applyPageBackground(document, '#page-under-test', undefined)).toBe(true);

        expect(backgroundSheet(document).cssRules).toHaveLength(0);
        expect(backgroundOf(target)).toBe('none');
    });

    it('keeps a URL that tries to end the value inside it', () => {
        applyPageBackground(document, '#page-under-test', '/bg/x.jpg"); color: red; x: url("y');

        expect(backgroundSheet(document).cssRules).toHaveLength(1);
        expect(getComputedStyle(target).color).not.toBe('rgb(255, 0, 0)');
        expect(backgroundOf(target)).toContain('x.jpg');
    });

    it.each([
        ['a selector that adds a rule', '#page-under-test {} body'],
        ['an at-rule', '@media screen'],
        ['a selector that is not CSS', '#page-under-test[['],
    ])('applies nothing for %s', (_name, selector) => {
        applyPageBackground(document, '#page-under-test', '/bg/galaxy.jpg');

        expect(applyPageBackground(document, selector, '/bg/mountain.jpg')).toBe(false);

        expect(backgroundSheet(document).cssRules).toHaveLength(0);
        expect(backgroundOf(target)).toBe('none');
    });

    it('works on a pseudo-element selector', () => {
        applyPageBackground(document, '#page-under-test::before', '/bg/galaxy.jpg');

        expect(getComputedStyle(target, '::before').backgroundImage).toContain('/bg/galaxy.jpg');
    });
});
