import { css, html, nothing, type PropertyValues, type TemplateResult } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import { waitFor } from 'xstate';
import type { Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import { ActorController } from '../../state/actor-controller';
import { USER_LOCALE_MESSAGES } from './messages';
import { userLocaleMachine, type UserLocaleEmitted } from './up-user-locale.machine';

/** Details of the `up-locale-changed` event. */
export type LocaleChangedDetail = Omit<UserLocaleEmitted, 'type'>;

/** How the element reloads the page; tests replace `reload`. */
export const page = {
    /* v8 ignore next 3 -- reloading would end the test run */
    reload(): void {
        window.location.reload();
    },
};

/**
 * The user's language, the web-component replacement of the User Locales Selector portlet. It
 * reads and sets it through `/api/v6-0/locale`.
 *
 * It lists the portal's languages as radio buttons, each named in the user's current language,
 * with the current one checked. Update saves the chosen one and reloads the page, which the
 * portal then renders in that language. A signed-in user's choice is kept for their next
 * session; a guest's lasts this session.
 *
 * Its states are the XState machine in `up-user-locale.machine.ts`, run by an
 * {@link ActorController}: the template renders from the snapshot, and user actions are events.
 *
 * Attributes: those of {@link UpElement}.
 *
 * Events: `up-locale-changed`, just before the page reloads.
 */
export class UpUserLocale extends UpElement {
    static override styles = [
        ...UpElement.styles,
        css`
            form {
                display: flex;
                flex-direction: column;
                align-items: flex-start;
                gap: var(--spacer-3);
            }

            h2 {
                margin: 0;
                font-size: var(--font-size-lg, 1.25rem);
            }

            .locales {
                display: flex;
                flex-direction: column;
                gap: var(--spacer-2);
            }

            .alert p {
                margin: 0;
            }

            .loading {
                display: flex;
                align-items: center;
                gap: var(--spacer-3);
            }
        `,
    ];

    readonly #locale = new ActorController(this, userLocaleMachine, {
        // Properties in: the API client at the first connect, later changes as events (willUpdate)
        input: () => ({ api: this.api }),
        // Events out
        events: { 'locale.changed': 'up-locale-changed' },
    });

    constructor() {
        super();
        // The portal renders the page in the user's language, so a new one needs a new page
        this.addEventListener('up-locale-changed', () => {
            page.reload();
        });
    }

    protected override get defaultMessages(): Messages {
        return USER_LOCALE_MESSAGES;
    }

    /**
     * Loads the locales again, showing the loading state meanwhile. Resolves once they are loaded
     * (or failed to load), or when the element leaves the page first.
     */
    async reload(): Promise<void> {
        this.#locale.send({ type: 'reload' });
        const actor = this.#locale.actorRef;
        if (actor === undefined) {
            return;
        }
        try {
            await waitFor(actor, (snapshot) => !snapshot.matches('loading'));
        } catch {
            // The element was disconnected (its actor stopped) before the locales arrived
        }
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        if (
            this.hasUpdated &&
            (changed.has('contextPath') || changed.has('csrfHeader') || changed.has('csrfToken'))
        ) {
            this.#locale.send({ type: 'api.changed', api: this.api });
        }
    }

    override render(): TemplateResult {
        const snapshot = this.#locale.snapshot;
        if (snapshot.matches('loading')) {
            return html`<div class="loading" role="status">
                <span class="spinner-border" aria-hidden="true"></span>
                <span>${this.msg('loading')}</span>
            </div>`;
        }
        if (snapshot.matches('failed')) {
            return html`<div class="alert theme-danger" role="alert">
                <p>${this.msg('locale.load.fail')}</p>
                <button
                    type="button"
                    class="btn-solid theme-danger btn-sm retry"
                    @click=${this.#reloadClicked}
                >
                    ${this.msg('locale.try.again')}
                </button>
            </div>`;
        }
        const { data, choice, notice } = snapshot.context;
        const busy = snapshot.hasTag('busy');
        return html`<form aria-busy=${busy ? 'true' : nothing} @submit=${this.#submitted}>
            <h2 id="heading">${this.msg('set.language.preference')}</h2>
            ${
                notice === undefined
                    ? nothing
                    : html`<div class="alert theme-danger notice" role="alert">
                          <p>${this.msg('locale.save.fail')}</p>
                      </div>`
            }
            <div class="locales" role="radiogroup" aria-labelledby="heading">
                ${repeat(
                    data.locales,
                    (locale) => locale.code,
                    (locale) =>
                        html`<div class="form-field">
                            <input
                                type="radio"
                                class="radio"
                                name="locale"
                                id=${`locale-${locale.code}`}
                                .value=${locale.code}
                                .checked=${locale.code === choice}
                                ?disabled=${busy}
                                @change=${() => {
                                    this.#locale.send({ type: 'choose', code: locale.code });
                                }}
                            />
                            <label for=${`locale-${locale.code}`}>${locale.name}</label>
                        </div>`,
                )}
            </div>
            <button type="submit" class="btn-solid theme-primary update" ?disabled=${busy}>
                ${this.msg('update')}
            </button>
        </form>`;
    }

    readonly #reloadClicked = (): void => {
        this.#locale.send({ type: 'reload' });
    };

    readonly #submitted = (event: SubmitEvent): void => {
        event.preventDefault();
        this.#locale.send({ type: 'submit' });
    };
}

if (!customElements.get('up-user-locale')) {
    customElements.define('up-user-locale', UpUserLocale);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-user-locale': UpUserLocale;
    }
}
