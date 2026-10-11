/**
 * Small inline SVG icons (shadow DOM keeps icon fonts out). Decorative: `aria-hidden`, and the
 * text next to them says what they mean.
 */
import { svg } from 'lit';

const icon = (path: ReturnType<typeof svg>) =>
    svg`<svg class="icon" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true" focusable="false">${path}</svg>`;

export const searchIcon = icon(
    svg`<path d="M11.7 10.3a6 6 0 1 0-1.4 1.4l3.6 3.6 1.4-1.4-3.6-3.6zM6.5 11a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9z"/>`,
);

export const personIcon = icon(
    svg`<path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm-5 6c0-2.8 2.2-4.5 5-4.5s5 1.7 5 4.5v1H3v-1z"/>`,
);

export const portletIcon = icon(
    svg`<path d="M1 1h6v6H1V1zm8 0h6v6H9V1zM1 9h6v6H1V9zm8 0h6v6H9V9z"/>`,
);
