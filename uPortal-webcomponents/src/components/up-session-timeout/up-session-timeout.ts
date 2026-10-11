import { css, html, nothing, type PropertyValues, type TemplateResult } from 'lit';
import type { PropertyDeclarations } from 'lit';
import type { Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import { ActorController } from '../../state/actor-controller';
import { SESSION_TIMEOUT_MESSAGES } from './messages';
import {
    browserLogoutTimeStore,
    systemClock,
    type Clock,
    type LogoutTimeStore,
} from './session-clock';
import { sessionTimeoutMachine, type SessionTimeoutEmitted } from './up-session-timeout.machine';

/** Details of the `up-session-warned` event. */
export type SessionWarnedDetail = Omit<
    Extract<SessionTimeoutEmitted, { type: 'session.warned' }>,
    'type'
>;

/** Details of the `up-session-extended` event. */
export type SessionExtendedDetail = Omit<
    Extract<SessionTimeoutEmitted, { type: 'session.extended' }>,
    'type'
>;

/** Details of the `up-session-ended` event. */
export type SessionEndedDetail = Omit<
    Extract<SessionTimeoutEmitted, { type: 'session.ended' }>,
    'type'
>;

const DEFAULT_LOGOUT_PATH = '/Logout';

/**
 * Warns the user before their portal session expires, the web-component replacement of the
 * Session Timeout portlet. It reads the session's timeout from `/api/v6-0/session` and extends
 * the session with `POST /api/v6-0/session/keep-alive`.
 *
 * It shows nothing until the warning is due: 30 seconds plus `dialog-display-seconds` before the
 * session would expire. Then a modal dialog counts down the seconds left and offers to keep the
 * user signed in or to log out; Escape does not close it. When the countdown reaches zero, the
 * element logs the user out. The user's tabs share the logout time, so a tab used later
 * postpones the warning in the others.
 *
 * Its states are the XState machine in `up-session-timeout.machine.ts`, run by an
 * {@link ActorController}.
 *
 * Attributes: those of {@link UpElement}, plus the Session Timeout portlet's preferences:
 * `enabled` (anything but `true` turns the warning off), `dialog-display-seconds` (default 60)
 * and `logout-url` (a path in the portal, default `/Logout`).
 *
 * Events: `up-session-warned`, `up-session-extended` and `up-session-ended`.
 */
export class UpSessionTimeout extends UpElement {
    static override properties: PropertyDeclarations = {
        enabled: { reflect: true },
        dialogDisplaySeconds: { attribute: 'dialog-display-seconds', reflect: true },
        logoutUrl: { attribute: 'logout-url', reflect: true },
    };

    static override styles = [
        ...UpElement.styles,
        css`
            .dialog-body p {
                margin: 0 0 var(--spacer-3);
            }

            .dialog-body .alert {
                margin: 0;
            }
        `,
    ];

    declare enabled: string | undefined;
    declare dialogDisplaySeconds: string | undefined;
    declare logoutUrl: string | undefined;

    /** The time and timers the warning runs on; tests replace it before the element connects. */
    clock: Clock = systemClock;

    /** Where the tabs share the logout time; tests replace it before the element connects. */
    store: LogoutTimeStore = browserLogoutTimeStore;

    /** Leaves the page for the logout URL; tests replace it. */
    navigate: (url: string) => void = (url) => {
        window.location.assign(url);
    };

    readonly #session = new ActorController(this, sessionTimeoutMachine, {
        // Properties in: read at the first connect; later API changes arrive as events
        input: () => ({
            api: this.api,
            clock: this.clock,
            store: this.store,
            enabled: (this.enabled ?? 'true').trim().toLowerCase() === 'true',
            displaySeconds: this.dialogDisplaySeconds,
        }),
        events: {
            'session.warned': 'up-session-warned',
            'session.extended': 'up-session-extended',
            'session.ended': 'up-session-ended',
        },
    });

    #navigated = false;

    protected override get defaultMessages(): Messages {
        return SESSION_TIMEOUT_MESSAGES;
    }

    /** The logout URL in this portal: a path from `logout-url`, else `/Logout`. */
    get resolvedLogoutUrl(): string {
        const path = this.logoutUrl?.trim();
        const safe =
            path !== undefined && path.startsWith('/') && !path.startsWith('//')
                ? path
                : DEFAULT_LOGOUT_PATH;
        return `${this.contextPath ?? ''}${safe}`;
    }

    override connectedCallback(): void {
        super.connectedCallback();
        // A move in the page takes an open dialog out of the top layer; render to restore it
        if (this.hasUpdated) {
            this.requestUpdate();
        }
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        if (
            this.hasUpdated &&
            (changed.has('contextPath') || changed.has('csrfHeader') || changed.has('csrfToken'))
        ) {
            this.#session.send({ type: 'api.changed', api: this.api });
        }
    }

    override render(): TemplateResult | typeof nothing {
        const snapshot = this.#session.snapshot;
        if (!snapshot.hasTag('open')) {
            return nothing;
        }
        const failed = snapshot.matches({ warning: 'failed' });
        return html`<dialog
            class="dialog dialog-instant"
            closedby="none"
            aria-labelledby="title"
            aria-describedby="remaining"
            aria-busy=${snapshot.hasTag('busy') ? 'true' : nothing}
            @cancel=${this.#onCancel}
            @close=${this.#onClose}
        >
            <div class="dialog-header">
                <h2 class="dialog-title" id="title">${this.msg('session-timeout.title')}</h2>
            </div>
            <div class="dialog-body">
                <p id="remaining">
                    ${this.msg('session-timeout.body1', snapshot.context.secondsLeft)}
                </p>
                <p>${this.msg('session-timeout.body2')}</p>
                ${
                    failed
                        ? html`<div class="alert theme-danger" role="alert">
                              ${this.msg('session-timeout.keep-alive.failed')}
                          </div>`
                        : nothing
                }
            </div>
            <div class="dialog-footer">
                <button
                    type="button"
                    class="btn-outline theme-secondary keep-alive"
                    @click=${this.#keepAlive}
                >
                    ${this.msg('session-timeout.button.keep-alive')}
                </button>
                <button type="button" class="btn-solid theme-primary logout" @click=${this.#logout}>
                    ${this.msg('session-timeout.button.logout')}
                </button>
            </div>
        </dialog>`;
    }

    protected override updated(changed: PropertyValues<this>): void {
        super.updated(changed);
        // Shown modal on first render; again after a move in the page, which drops it from the
        // top layer, or after something else closed it
        const dialog = this.renderRoot.querySelector('dialog');
        if (dialog !== null && this.isConnected && !dialog.matches(':modal')) {
            if (dialog.open) {
                dialog.close();
            }
            dialog.showModal();
        }
        if (this.#session.snapshot.matches('ended') && !this.#navigated) {
            this.#navigated = true;
            this.navigate(this.resolvedLogoutUrl);
        }
    }

    #keepAlive = (): void => {
        this.#session.send({ type: 'keepAlive' });
    };

    #logout = (): void => {
        this.#session.send({ type: 'logout' });
    };

    /** Escape does not close the warning, as in the portlet. */
    #onCancel = (event: Event): void => {
        event.preventDefault();
    };

    /** Reopens the dialog if the browser closed it while the warning still applies. */
    #onClose = (): void => {
        this.requestUpdate();
    };
}

if (!customElements.get('up-session-timeout')) {
    customElements.define('up-session-timeout', UpSessionTimeout);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-session-timeout': UpSessionTimeout;
    }
}
