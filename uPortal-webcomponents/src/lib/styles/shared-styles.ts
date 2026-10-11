import { css, unsafeCSS } from 'lit';
import bootstrapCss from './bootstrap.scss?inline';

/**
 * Bootstrap 6 for shadow roots: one CSSResult, so Lit adopts one shared constructable
 * stylesheet into every component that lists it in `static styles`.
 */
export const bootstrapStyles = unsafeCSS(bootstrapCss);

/**
 * Host defaults for every component. Bootstrap sets `color-scheme: light dark` on `:host`, which
 * would turn a component dark on a dark-mode OS inside a light portal page; inheriting the page's
 * color scheme keeps the component in step with the skin instead.
 */
export const hostStyles = css`
    :host {
        display: block;
        color-scheme: inherit;
    }

    :host([hidden]) {
        display: none;
    }

    /* An icon that points left or right (a chevron, an arrow) mirrors in right-to-left locales */
    .mirror-rtl:dir(rtl) {
        transform: scaleX(-1);
    }
`;
