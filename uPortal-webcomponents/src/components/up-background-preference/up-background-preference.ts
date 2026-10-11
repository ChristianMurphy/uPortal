import { css, html, nothing, type PropertyValues, type TemplateResult } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import type { Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import { ActorController } from '../../state/actor-controller';
import type { BackgroundPreference } from './background-api';
import { BACKGROUND_PREFERENCE_MESSAGES } from './messages';
import { applyPageBackground } from './page-background';
import {
    backgroundPreferenceMachine,
    type BackgroundPreferenceEmitted,
} from './up-background-preference.machine';

/** Details of the `up-background-changed` event. */
export type BackgroundChangedDetail = Omit<BackgroundPreferenceEmitted, 'type'>;

/** The fname of the quickstart's channel, used when the element has none. */
export const DEFAULT_FNAME = 'background-preference';

/**
 * The user's page background, the web-component replacement of the Background Preference
 * portlet. It reads and saves the choice through `/api/v6-0/background-preference/{fname}`.
 *
 * "Change Background Image" opens a list of None and the offered images, each with its thumbnail.
 * Choosing one saves it, closes the list and puts the full-size image behind the page (the
 * container selector the API names, `#portalPageBody::before` by default) through one
 * constructable stylesheet adopted by the document. On every page the element loads the choice
 * and puts it there again.
 *
 * Its states are the XState machine in `up-background-preference.machine.ts`, run by an
 * {@link ActorController}: the template renders from the snapshot, and user actions are events.
 *
 * Attributes: those of {@link UpElement}; `fname` names the channel whose choice it is.
 *
 * Events: `up-background-changed` once the portal saved a new choice.
 */
export class UpBackgroundPreference extends UpElement {
    static override styles = [
        ...UpElement.styles,
        css`
            :host {
                display: block;
                position: relative;
            }

            .toggle {
                display: inline-flex;
                align-items: center;
                gap: var(--spacer-2);
            }

            .choices {
                position: absolute;
                inset-inline-end: 0;
                z-index: 1000;
                min-width: 100%;
                max-height: 515px;
                overflow-y: auto;
                background: var(--bg-body);
                box-shadow: var(--box-shadow);
            }

            .list-group {
                margin: 0;
            }

            .choice {
                display: flex;
                flex-direction: column;
                align-items: center;
                gap: var(--spacer-1);
                font-weight: bold;
            }

            .choice img {
                width: 80%;
            }

            .alert p {
                margin: 0;
            }

            .loading {
                display: flex;
                align-items: center;
                gap: var(--spacer-2);
            }
        `,
    ];

    readonly #background = new ActorController(this, backgroundPreferenceMachine, {
        // Properties in: the API client at the first connect, later changes as events (willUpdate)
        input: () => ({ api: this.api, fname: this.fname || DEFAULT_FNAME }),
        // Events out
        events: { 'background.changed': 'up-background-changed' },
    });

    /** The data last put behind the page. */
    #applied: BackgroundPreference | undefined;
    /** Whether the list was open at the last render, to give focus back to the button. */
    #wasOpen = false;

    protected override get defaultMessages(): Messages {
        return BACKGROUND_PREFERENCE_MESSAGES;
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        if (
            this.hasUpdated &&
            (changed.has('contextPath') || changed.has('csrfHeader') || changed.has('csrfToken'))
        ) {
            this.#background.send({ type: 'api.changed', api: this.api });
        }
    }

    protected override updated(changed: PropertyValues<this>): void {
        super.updated(changed);
        const snapshot = this.#background.snapshot;
        const { data } = snapshot.context;
        if (snapshot.matches('ready') && data !== this.#applied) {
            this.#applied = data;
            applyPageBackground(document, data.containerSelector, data.selected);
        }
        const open = snapshot.matches({ ready: 'open' }) || snapshot.matches({ ready: 'saving' });
        if (this.#wasOpen && !open && snapshot.matches('ready')) {
            this.renderRoot.querySelector<HTMLElement>('.toggle')?.focus();
        }
        this.#wasOpen = open;
    }

    override render(): TemplateResult {
        const snapshot = this.#background.snapshot;
        if (snapshot.matches('loading')) {
            return html`<div class="loading" role="status">
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                <span class="visually-hidden">${this.msg('loading')}</span>
            </div>`;
        }
        if (snapshot.matches('failed')) {
            return html`<div class="alert theme-danger" role="alert">
                <p>${this.msg('background.preference.load.fail')}</p>
                <button
                    type="button"
                    class="btn-solid theme-danger btn-sm retry"
                    @click=${() => {
                        this.#background.send({ type: 'reload' });
                    }}
                >
                    ${this.msg('background.preference.try.again')}
                </button>
            </div>`;
        }
        if (snapshot.matches('signedOut')) {
            return html`<div class="alert theme-danger" role="alert">
                <p>${this.msg('background.preference.session.expired')}</p>
            </div>`;
        }
        const open = !snapshot.matches({ ready: 'closed' });
        return html`<div @keydown=${this.#keydown}>
            <button
                type="button"
                class="btn-outline theme-secondary btn-sm toggle"
                aria-expanded=${open ? 'true' : 'false'}
                aria-controls="choices"
                @click=${() => {
                    this.#background.send({ type: 'toggle' });
                }}
            >
                <svg aria-hidden="true" width="16" height="16" viewBox="0 0 16 16">
                    <path
                        fill="currentColor"
                        d="M2 2h12a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zm0 1v7.6l3-3 3 3 2-2 4 4V3zm9 1.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z"
                    />
                </svg>
                ${this.msg('background.preference.change')}
            </button>
            ${open ? this.#renderChoices() : nothing}
        </div>`;
    }

    #renderChoices(): TemplateResult {
        const snapshot = this.#background.snapshot;
        const { data, notice } = snapshot.context;
        const busy = snapshot.hasTag('busy');
        return html`<div id="choices" class="choices" aria-busy=${busy ? 'true' : nothing}>
            ${
                notice === undefined
                    ? nothing
                    : html`<div class="alert theme-danger notice" role="alert">
                          <p>${this.msg('background.preference.save.fail')}</p>
                      </div>`
            }
            <ul class="list-group">
                <li>${this.#renderChoice(undefined, data.selected === undefined, busy)}</li>
                ${repeat(
                    data.images,
                    (image) => image.url,
                    (image) =>
                        html`<li>
                            ${this.#renderChoice(image.url, data.selected === image.url, busy, {
                                thumbnailUrl: image.thumbnailUrl,
                                caption: image.caption,
                            })}
                        </li>`,
                )}
            </ul>
        </div>`;
    }

    #renderChoice(
        url: string | undefined,
        chosen: boolean,
        busy: boolean,
        image?: { thumbnailUrl: string; caption: string },
    ): TemplateResult {
        return html`<button
            type="button"
            class="list-group-item list-group-item-action choice"
            aria-pressed=${chosen ? 'true' : 'false'}
            ?disabled=${busy}
            @click=${() => {
                this.#background.send({ type: 'choose', url });
            }}
        >
            ${image === undefined ? nothing : html`<img src=${image.thumbnailUrl} alt="" />`}
            <span class="caption"
                >${image === undefined ? this.msg('background.preference.none') : image.caption}</span
            >
        </button>`;
    }

    readonly #keydown = (event: KeyboardEvent): void => {
        if (event.key === 'Escape' && this.#background.snapshot.can({ type: 'close' })) {
            event.preventDefault();
            this.#background.send({ type: 'close' });
        }
    };
}

if (!customElements.get('up-background-preference')) {
    customElements.define('up-background-preference', UpBackgroundPreference);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-background-preference': UpBackgroundPreference;
    }
}
