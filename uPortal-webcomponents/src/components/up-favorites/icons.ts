import { html, type TemplateResult } from 'lit';

/*
 * Small icons for up-favorites, drawn on a 16x16 grid in the current text color. Icon fonts do
 * not reach into shadow roots, so components carry the few icons they need. Every icon is
 * decorative: the control it sits in has its own accessible name.
 */
function icon(path: string): TemplateResult {
    return html`<svg
        class="icon"
        viewBox="0 0 16 16"
        width="16"
        height="16"
        fill="currentColor"
        aria-hidden="true"
        focusable="false"
    >
        <path d=${path}></path>
    </svg>`;
}

/** A filled star: the portlet is a favorite; the button removes it. */
export const starIcon = icon(
    'M8 1.3l2 4.2 4.6.6-3.4 3.2.9 4.6L8 11.6l-4.1 2.3.9-4.6-3.4-3.2 4.6-.6z',
);

/** A folder: a favorite collection. */
export const folderIcon = icon('M1.5 3h4.6l1.5 1.5h6.9V13h-13zm1.5 3v5.5h10V6z');

/** A grid of four squares: a portlet without an icon of its own. */
export const portletIcon = icon('M2 2h5v5H2zm7 0h5v5H9zM2 9h5v5H2zm7 0h5v5H9z');

export const arrowUpIcon = icon('M8 2.5l4.6 4.6-1.1 1.1-2.7-2.7V13.5H7.2V5.5L4.5 8.2 3.4 7.1z');

export const arrowDownIcon = icon('M8 13.5l4.6-4.6-1.1-1.1-2.7 2.7V2.5H7.2v8L4.5 7.8 3.4 8.9z');

/** A padlock: the favorite cannot be removed. */
export const lockIcon = icon(
    'M4.5 7V5a3.5 3.5 0 0 1 7 0v2h1v7h-9V7zm1.6 0h3.8V5a1.9 1.9 0 0 0-3.8 0z',
);
