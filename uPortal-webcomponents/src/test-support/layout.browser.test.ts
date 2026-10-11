import { afterEach, beforeAll, describe, expect, it } from 'vite-plus/test';
import { page } from 'vite-plus/test/browser';
import { type Check, layoutProblems } from './layout';

/*
 * The layout checks themselves (MOD-D52): each finds nothing on markup that fits, and finds
 * the fault seeded into otherwise identical markup. The same cases as uPortal-start's
 * tests/layout/layout-checks.spec.ts.
 */

const FITS = `
  <style>
    body { margin: 0; font: 16px/1.4 sans-serif; }
    .row { display: flex; flex-wrap: wrap; gap: 8px; }
    .col { flex: 1 1 240px; min-width: 0; }
    button { white-space: normal; }
  </style>
  <ul id="portalNavigationList" style="display: flex; list-style: none; margin: 0; padding: 0">
    <li><a href="#a">Home</a></li><li><a href="#b">Work</a></li>
  </ul>
  <div class="row">
    <div class="col portal-page-column">
      <section class="up-portlet-wrapper"><p>Some text in a portlet.</p>
        <button>Save</button> <button>Cancel</button>
        <img alt="" width="40" height="20"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='40' height='20'/%3E">
      </section>
    </div>
    <div class="col portal-page-column">
      <section class="up-portlet-wrapper"><p>More text.</p></section>
    </div>
  </div>
  <div style="overflow: hidden; height: 3em">One short line.</div>`;

/* Faults: the check that should report it, a name, the node it names, the markup */
const FAULTS: [Check, string, string, string][] = [
    ['scroll', 'a 2000px box', 'div', `<div style="width: 2000px">Too wide for the screen.</div>`],
    [
        'scroll',
        'a 2000px box in a body that hides overflow',
        'div',
        `<style>body { overflow-x: hidden }</style><div style="width: 2000px">Too wide for the screen.</div>`,
    ],
    [
        'cut-off',
        'two lines in a one-line box',
        'div',
        `<div style="overflow: hidden; height: 1em">First line<br>second line</div>`,
    ],
    [
        'spill',
        'content wider than its box',
        'div',
        `<div style="width: 100px"><span style="display: inline-block; width: 150px">x</span></div>`,
    ],
    [
        'spill',
        'overlapping buttons',
        'button',
        `<div style="position: relative; height: 40px"><button style="position: absolute; left: 0">One</button><button style="position: absolute; left: 10px">Two</button></div>`,
    ],
    [
        'wrap',
        'a button on two lines',
        'button',
        `<button style="width: 60px">Save all of the changes</button>`,
    ],
    [
        'wrap',
        'a tab bar on two rows',
        '#portalNavigationList',
        `<style>#portalNavigationList { flex-wrap: wrap; width: 60px }</style>`,
    ],
    [
        'collapse',
        'a portlet of height 0',
        'section.up-portlet-wrapper',
        `<section class="up-portlet-wrapper" style="height: 0; overflow: hidden"><p>Hidden away.</p></section>`,
    ],
    [
        'collapse',
        'a stretched image',
        'img',
        `<img alt="" style="width: 200px; height: 20px" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='40' height='20'/%3E">`,
    ],
    [
        'spacing',
        'a box that fits only without text spacing',
        'div',
        `<div style="overflow: hidden; height: 1.4em; width: 300px; white-space: nowrap">Fits only without extra spacing</div>`,
    ],
    [
        'cut-off',
        'a cut-off box inside a web component',
        'up-demo >>> div',
        `<up-demo><template shadowrootmode="open"><div style="overflow: hidden; height: 1em">First line<br>second line</div></template></up-demo>`,
    ],
    [
        'spacing',
        'a box inside a web component that fits only without text spacing',
        'up-demo >>> div',
        `<up-demo><template shadowrootmode="open"><div style="overflow: hidden; height: 1.4em; width: 300px; white-space: nowrap">Fits only without extra spacing</div></template></up-demo>`,
    ],
];

/* Allowed: text for screen readers only, clipped or moved off the page */
const SCREEN_READER_ONLY = `<a href="#main" style="position: absolute; left: -1000px; top: -1000px">Skip to content</a><span style="position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap">Only for screen readers</span>`;

/* Allowed: an ellipsis whose full text is in the title */
const TITLED_ELLIPSIS = `<div title="A long name that does not fit" style="width: 80px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis">A long name that does not fit</div>`;

let container: HTMLElement | undefined;

/** Renders the markup in a fresh container; a declarative shadow root needs setHTMLUnsafe. */
function render(html: string): HTMLElement {
    container = document.createElement('div');
    container.setHTMLUnsafe(html);
    document.body.append(container);
    return container;
}

beforeAll(async () => {
    await page.viewport(1024, 768);
});

afterEach(() => {
    container?.remove();
});

describe('layout checks', () => {
    it('find nothing on markup that fits', () => {
        render(FITS);
        expect(layoutProblems()).toEqual([]);
    });

    it('allow an ellipsis with the full text in its title', () => {
        render(FITS + TITLED_ELLIPSIS);
        expect(layoutProblems()).toEqual([]);
    });

    it('ignore text for screen readers only', () => {
        render(FITS + SCREEN_READER_ONLY);
        expect(layoutProblems()).toEqual([]);
    });

    it.each(FAULTS)('%s finds %s', (check, _name, node, fault) => {
        render(FITS + fault);
        expect(layoutProblems().map((each) => `${each.check} ${each.node}`)).toContain(
            `${check} ${node}`,
        );
    });
});
