import { css, html, nothing, type PropertyValues, type TemplateResult } from 'lit';
import type { Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import { ActorController } from '../../state/actor-controller';
import { RESET_MY_LAYOUT_MESSAGES } from './messages';
import { resetMyLayoutMachine, type ResetMyLayoutEmitted } from './up-reset-my-layout.machine';

/** Details of the `up-layout-reset` event. */
export type LayoutResetDetail = Omit<ResetMyLayoutEmitted, 'type'>;

/**
 * Resets the signed-in user's layout to the default, the web-component replacement of the Reset
 * My Layout portlet. It calls `/api/v6-0/layout/reset`.
 *
 * It says what a reset does and that the user must sign in again to see it. Reset Now asks for
 * confirmation, naming the user; Reset Layout resets and says so, Cancel goes back. Continue,
 * after a reset, goes back to the start. The reset acts on the portal session's user only.
 *
 * Its states are the XState machine in `up-reset-my-layout.machine.ts`, run by an
 * {@link ActorController}: the template renders from the snapshot, and user actions are events.
 * When the user moves from one step to the next, focus moves to the new step's heading or main
 * button, because the control they used is gone.
 *
 * Attributes: those of {@link UpElement}.
 *
 * Events: `up-layout-reset` once the portal has reset the layout.
 */
export class UpResetMyLayout extends UpElement {
    static override styles = [
        ...UpElement.styles,
        css`
            .view {
                display: flex;
                flex-direction: column;
                align-items: flex-start;
                gap: var(--spacer-3);
            }

            h2 {
                margin: 0;
                font-size: var(--font-size-lg, 1.25rem);
            }

            p {
                margin: 0;
            }

            .alert {
                display: flex;
                flex-direction: column;
                gap: var(--spacer-2);
                align-self: stretch;
                margin: 0;
            }

            .buttons {
                display: flex;
                flex-wrap: wrap;
                gap: var(--spacer-2);
            }

            .loading {
                display: flex;
                align-items: center;
                gap: var(--spacer-3);
            }

            [tabindex='-1']:focus {
                outline: none;
            }
        `,
    ];

    readonly #reset = new ActorController(this, resetMyLayoutMachine, {
        // Properties in: the API client at the first connect, later changes as events (willUpdate)
        input: () => ({ api: this.api }),
        // Events out
        events: { 'layout.reset': 'up-layout-reset' },
    });

    /** The state rendered last, to move focus when the user goes to another step. */
    #renderedState: string | undefined;
    /** False until the first load has finished; loading a page never takes focus. */
    #loadedOnce = false;

    protected override get defaultMessages(): Messages {
        return RESET_MY_LAYOUT_MESSAGES;
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        if (
            this.hasUpdated &&
            (changed.has('contextPath') || changed.has('csrfHeader') || changed.has('csrfToken'))
        ) {
            this.#reset.send({ type: 'api.changed', api: this.api });
        }
    }

    protected override updated(changed: PropertyValues<this>): void {
        super.updated(changed);
        const state = JSON.stringify(this.#reset.snapshot.value);
        if (state === this.#renderedState) {
            return;
        }
        if (this.#loadedOnce) {
            this.renderRoot.querySelector<HTMLElement>('[data-autofocus]')?.focus();
        }
        this.#loadedOnce = this.#renderedState !== undefined;
        this.#renderedState = state;
    }

    override render(): TemplateResult {
        const snapshot = this.#reset.snapshot;
        if (snapshot.matches('loading')) {
            return html`<div class="loading" role="status">
                <span class="spinner-border" aria-hidden="true"></span>
                <span>${this.msg('loading')}</span>
            </div>`;
        }
        if (snapshot.matches('failed')) {
            return html`<div class="alert theme-danger" role="alert" tabindex="-1" data-autofocus>
                <p>${this.msg('reset.my.layout.load.fail')}</p>
                <div>
                    <button
                        type="button"
                        class="btn-solid theme-danger btn-sm retry"
                        @click=${this.#send('reload')}
                    >
                        ${this.msg('reset.my.layout.try.again')}
                    </button>
                </div>
            </div>`;
        }
        if (snapshot.matches('signedOut')) {
            return html`<div class="alert theme-danger" role="alert" tabindex="-1" data-autofocus>
                <p>${this.msg('reset.my.layout.session.expired')}</p>
            </div>`;
        }
        const { userName, notice } = snapshot.context;
        if (snapshot.matches({ ready: 'done' })) {
            return html`<div class="view done">
                <div class="alert theme-success" role="status">
                    <h2 tabindex="-1" data-autofocus>${this.msg('success')}</h2>
                    <p>${this.msg('layout.for.name.has.been.reset', userName)}</p>
                </div>
                <button
                    type="button"
                    class="btn-solid theme-primary continue"
                    @click=${this.#send('continue')}
                >
                    ${this.msg('continue')}
                </button>
            </div>`;
        }
        if (snapshot.matches({ ready: 'start' })) {
            return html`<div class="view start">
                <p>${this.msg('reset.my.layout.description')}</p>
                <p><em>${this.msg('reset.my.layout.note')}</em></p>
                <button
                    type="button"
                    class="btn-solid theme-primary reset-now"
                    data-autofocus
                    @click=${this.#send('reset')}
                >
                    ${this.msg('reset.now')}
                </button>
            </div>`;
        }
        // Confirming, or resetting after the user confirmed
        const busy = snapshot.hasTag('busy');
        return html`<section
            class="view confirmation"
            aria-labelledby="confirm-heading"
            aria-busy=${busy ? 'true' : nothing}
        >
            <h2 id="confirm-heading" tabindex="-1" data-autofocus>
                ${this.msg('reset.user.layout')}
            </h2>
            <p>
                ${this.msg('resetting.user.layout.irreversible')}
                ${this.msg('are.you.sure.reset.layout', userName)}
            </p>
            ${
                notice === undefined
                    ? nothing
                    : html`<div class="alert theme-danger notice" role="alert">
                          <p>${this.msg('reset.my.layout.reset.fail')}</p>
                      </div>`
            }
            <div class="buttons">
                <button
                    type="button"
                    class="btn-solid theme-danger confirm"
                    ?disabled=${busy}
                    @click=${this.#send('confirm')}
                >
                    ${
                        busy
                            ? html`<span
                                  class="spinner-border spinner-border-sm"
                                  aria-hidden="true"
                              ></span>`
                            : nothing
                    }
                    ${this.msg('reset.layout')}
                </button>
                <button
                    type="button"
                    class="btn-outline theme-secondary cancel"
                    ?disabled=${busy}
                    @click=${this.#send('cancel')}
                >
                    ${this.msg('cancel')}
                </button>
            </div>
        </section>`;
    }

    /** A click handler that sends the machine an event that carries no data. */
    #send(type: 'reload' | 'reset' | 'confirm' | 'cancel' | 'continue'): () => void {
        return () => {
            this.#reset.send({ type });
        };
    }
}

if (!customElements.get('up-reset-my-layout')) {
    customElements.define('up-reset-my-layout', UpResetMyLayout);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-reset-my-layout': UpResetMyLayout;
    }
}
