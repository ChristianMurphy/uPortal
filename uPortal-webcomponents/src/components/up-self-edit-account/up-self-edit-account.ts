/**
 * `<up-self-edit-account>`: the signed-in user's account details and, when they may edit their
 * local account, a form for its attributes and password. It replaces the Password Management
 * portlet (the self-edit-account Web Flow) and talks to `/api/v6-0/account`.
 *
 * Its states are the machine in `up-self-edit-account.machine.ts`. Saved changes show in the
 * details from the next login, as in the portlet, because the details come from the session.
 *
 * Events: `up-account-updated` (`detail.passwordChanged`) after a save.
 */
import { css, html, nothing, type PropertyValues, type TemplateResult } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import type { Messages } from '../../lib/i18n';
import { UpElement } from '../../lib/up-element';
import { ActorController } from '../../state/actor-controller';
import type { AccountAttribute } from './account-api';
import { SELF_EDIT_ACCOUNT_MESSAGES, type SelfEditAccountMessageKey } from './messages';
import {
    selfEditAccountMachine,
    type Notice,
    type PasswordField,
} from './up-self-edit-account.machine';

/** The value of the input an `input` event came from. */
function inputValue(event: Event): string {
    const input = event.target;
    /* v8 ignore if -- unreachable: the listeners sit on inputs */
    if (!(input instanceof HTMLInputElement)) {
        return '';
    }
    return input.value;
}

/** The message key for a notice. */
function noticeMessage(notice: Notice): SelfEditAccountMessageKey {
    if (notice.kind === 'save-failed') {
        return notice.reason === 'unauthorized' ? 'account.session.expired' : 'account.save.fail';
    }
    const keys = {
        'passwords-differ': 'passwords.must.match',
        'current-password-missing': 'please.enter.current.password',
        'current-password-wrong': 'current.password.doesnt.match',
    } as const;
    return keys[notice.kind];
}

/** Which view is showing, to move focus when it changes. */
type View = 'loading' | 'failed' | 'viewing' | 'editing' | 'saved';

export class UpSelfEditAccount extends UpElement {
    static override styles = [
        ...UpElement.styles,
        css`
            .header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: var(--spacer-3);
                margin-block-end: var(--spacer-3);
            }

            h2 {
                margin: 0;
                font-size: var(--font-size-lg, 1.25rem);
            }

            h3 {
                margin-block: var(--spacer-3) var(--spacer-2);
                font-size: var(--font-size-md, 1.1rem);
            }

            h2:focus {
                outline: var(--focus-ring);
            }

            table {
                width: 100%;
            }

            td {
                padding: var(--spacer-1) var(--spacer-2);
                vertical-align: middle;
            }

            .attribute-name {
                width: 33%;
                font-weight: var(--font-weight-bold, 700);
            }

            .buttons {
                display: flex;
                gap: var(--spacer-2);
                margin-block-start: var(--spacer-3);
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

    readonly #account = new ActorController(this, selfEditAccountMachine, {
        // Properties in: the API client at the first connect, later changes as events (willUpdate)
        input: () => ({ api: this.api }),
        // Events out
        events: { 'account.updated': 'up-account-updated' },
    });

    #view: View | undefined;

    protected override get defaultMessages(): Messages {
        return SELF_EDIT_ACCOUNT_MESSAGES;
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        if (
            this.hasUpdated &&
            (changed.has('contextPath') || changed.has('csrfHeader') || changed.has('csrfToken'))
        ) {
            this.#account.send({ type: 'api.changed', api: this.api });
        }
    }

    protected override updated(changed: PropertyValues<this>): void {
        super.updated(changed);
        const view = this.#currentView();
        const previous = this.#view;
        this.#view = view;
        if (previous === undefined || previous === view) {
            return;
        }
        // Keep the keyboard where the user is: in the form, on Edit after it, on the outcome
        if (view === 'editing') {
            this.renderRoot.querySelector<HTMLInputElement>('form input')?.focus();
        } else if (view === 'viewing' && previous === 'editing') {
            this.renderRoot.querySelector<HTMLButtonElement>('.edit')?.focus();
        } else if (view === 'saved') {
            this.renderRoot.querySelector<HTMLElement>('h2')?.focus();
        }
    }

    #currentView(): View {
        const snapshot = this.#account.snapshot;
        for (const view of ['loading', 'failed', 'viewing', 'editing'] as const) {
            if (snapshot.matches(view)) {
                return view;
            }
        }
        return 'saved';
    }

    override render(): TemplateResult {
        const snapshot = this.#account.snapshot;
        switch (this.#currentView()) {
            case 'loading':
                return html`<div class="loading" role="status">
                    <span class="spinner-border" aria-hidden="true"></span>
                    <span>${this.msg('loading')}</span>
                </div>`;
            case 'failed':
                return snapshot.context.loadError === 'unauthorized'
                    ? html`<div class="alert theme-warning" role="alert">
                          <p>${this.msg('account.session.expired')}</p>
                      </div>`
                    : html`<div class="alert theme-danger" role="alert">
                          <p>${this.msg('account.load.fail')}</p>
                          <button
                              type="button"
                              class="btn-solid theme-danger btn-sm retry"
                              @click=${this.#reloadClicked}
                          >
                              ${this.msg('account.try.again')}
                          </button>
                      </div>`;
            case 'viewing':
                return this.#renderDetails();
            case 'editing':
                return this.#renderForm();
            default:
                return html`<h2 tabindex="-1">${this.msg('account.updated')}</h2>
                    <p>
                        ${this.msg(
                            'your.account.has.been.successfully.updated.your.changes.will.be.visible.upon.your.next.login',
                        )}
                    </p>`;
        }
    }

    #renderDetails(): TemplateResult {
        const { account } = this.#account.snapshot.context;
        return html`<div class="header">
                <h2>${this.msg('my.account')}</h2>
                ${
                    account.canEdit
                        ? html`<button
                              type="button"
                              class="btn-outline theme-secondary edit"
                              @click=${this.#editClicked}
                          >
                              ${this.msg('edit')}
                          </button>`
                        : nothing
                }
            </div>
            <table class="table">
                <tbody>
                    ${repeat(
                        account.details,
                        (attribute) => attribute.name,
                        (attribute) =>
                            html`<tr>
                                <td class="attribute-name">${attribute.label}</td>
                                <td>${attribute.value ?? ''}</td>
                            </tr>`,
                    )}
                </tbody>
            </table>`;
    }

    #renderForm(): TemplateResult {
        const snapshot = this.#account.snapshot;
        const { account, draft, currentPassword, password, confirmPassword, notice } =
            snapshot.context;
        const busy = snapshot.hasTag('busy');
        return html`<div class="header"><h2>${this.msg('edit.my.account')}</h2></div>
            <form aria-busy=${busy ? 'true' : nothing} @submit=${this.#submitted}>
                ${
                    notice === undefined
                        ? nothing
                        : html`<div class="alert theme-danger notice" role="alert">
                              <p>${this.msg(noticeMessage(notice))}</p>
                          </div>`
                }
                <h3>${this.msg('my.account.details')}</h3>
                <table class="table">
                    <tbody>
                        ${repeat(
                            account.editable,
                            (attribute) => attribute.name,
                            (attribute) => this.#renderAttributeInput(attribute, draft, busy),
                        )}
                    </tbody>
                </table>
                <h3>${this.msg('password')}</h3>
                <table class="table">
                    <tbody>
                        ${this.#renderPasswordInput(
                            'currentPassword',
                            'current.password',
                            currentPassword,
                            busy,
                        )}
                        ${this.#renderPasswordInput('password', 'password', password, busy)}
                        ${this.#renderPasswordInput(
                            'confirmPassword',
                            'confirm.password',
                            confirmPassword,
                            busy,
                        )}
                    </tbody>
                </table>
                <div class="buttons">
                    <button type="submit" class="btn-solid theme-primary save" ?disabled=${busy}>
                        ${this.msg('save')}
                    </button>
                    <button
                        type="button"
                        class="btn-outline theme-secondary cancel"
                        ?disabled=${busy}
                        @click=${this.#cancelClicked}
                    >
                        ${this.msg('cancel')}
                    </button>
                </div>
            </form>`;
    }

    #renderAttributeInput(
        attribute: AccountAttribute,
        draft: Record<string, string>,
        busy: boolean,
    ): TemplateResult {
        const id = `attribute-${attribute.name}`;
        return html`<tr>
            <td class="attribute-name">
                <label class="form-label" for=${id}>${attribute.label}</label>
            </td>
            <td>
                <input
                    type="text"
                    class="form-control"
                    id=${id}
                    name=${attribute.name}
                    .value=${draft[attribute.name] ?? ''}
                    ?disabled=${busy}
                    @input=${(event: InputEvent) => {
                        this.#account.send({
                            type: 'change',
                            name: attribute.name,
                            value: inputValue(event),
                        });
                    }}
                />
            </td>
        </tr>`;
    }

    #renderPasswordInput(
        field: PasswordField,
        labelKey: 'current.password' | 'password' | 'confirm.password',
        value: string,
        busy: boolean,
    ): TemplateResult {
        return html`<tr>
            <td class="attribute-name">
                <label class="form-label" for=${field}>${this.msg(labelKey)}</label>
            </td>
            <td>
                <input
                    type="password"
                    class="form-control"
                    id=${field}
                    autocomplete=${field === 'currentPassword' ? 'current-password' : 'new-password'}
                    .value=${value}
                    ?disabled=${busy}
                    @input=${(event: InputEvent) => {
                        this.#account.send({
                            type: 'password.change',
                            field,
                            value: inputValue(event),
                        });
                    }}
                />
            </td>
        </tr>`;
    }

    readonly #reloadClicked = (): void => {
        this.#account.send({ type: 'reload' });
    };

    readonly #editClicked = (): void => {
        this.#account.send({ type: 'edit' });
    };

    readonly #cancelClicked = (): void => {
        this.#account.send({ type: 'cancel' });
    };

    readonly #submitted = (event: SubmitEvent): void => {
        event.preventDefault();
        this.#account.send({ type: 'submit' });
    };
}

if (!customElements.get('up-self-edit-account')) {
    customElements.define('up-self-edit-account', UpSelfEditAccount);
}

declare global {
    interface HTMLElementTagNameMap {
        'up-self-edit-account': UpSelfEditAccount;
    }
}
