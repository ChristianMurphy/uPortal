import { LitElement, type PropertyDeclarations, type PropertyValues } from 'lit';
import { ApiClient } from './api-client';
import { createEvent } from './events';
import {
    formatMessage,
    intlLocale,
    loadMessages,
    messageKeys,
    pluralKey,
    textDirection,
    type MessageArg,
    type Messages,
} from './i18n';
import { bootstrapStyles, hostStyles } from './styles/shared-styles';

/**
 * Base class of uPortal's web components.
 *
 * The portal's web-component channel sets these attributes on every element it renders:
 *
 * - `locale`: the user's locale (BCP 47), which selects the messages;
 * - `window-state`: `normal`, `maximized`, `detached` or `exclusive`;
 * - `context-path`: the portal's context path, the base for its REST APIs;
 * - `fname`: the channel's functional name;
 * - `csrf-header` and `csrf-token`: sent with unsafe requests through {@link api}.
 *
 * The element renders into a shadow root styled with the shared Bootstrap 6 sheet. Subclasses
 * declare the messages they use by overriding {@link defaultMessages} (English text, keyed like
 * `Messages.properties`) and read them with {@link msg}; they announce changes with {@link emit}.
 */
export abstract class UpElement extends LitElement {
    static override properties: PropertyDeclarations = {
        locale: { reflect: true },
        windowState: { attribute: 'window-state', reflect: true },
        contextPath: { attribute: 'context-path', reflect: true },
        fname: { reflect: true },
        csrfHeader: { attribute: 'csrf-header' },
        csrfToken: { attribute: 'csrf-token' },
        localizedMessages: { state: true },
    };

    static override styles = [bootstrapStyles, hostStyles];

    declare locale: string | undefined;
    declare windowState: string | undefined;
    declare contextPath: string | undefined;
    declare fname: string | undefined;
    declare csrfHeader: string | undefined;
    declare csrfToken: string | undefined;
    /** Messages the portal returned for {@link resolvedLocale}. */
    declare protected localizedMessages: Messages;

    #messagesLocale: string | undefined;
    #messagesReady: Promise<void> = Promise.resolve();

    constructor() {
        super();
        this.localizedMessages = {};
    }

    /** The `locale` attribute, else the nearest `lang`, else the browser's language. */
    get resolvedLocale(): string {
        return (
            this.locale ||
            // From the parent: the element sets its own lang from this
            this.parentElement?.closest('[lang]')?.getAttribute('lang') ||
            document.documentElement.lang ||
            navigator.language ||
            'en'
        );
    }

    /** True when the channel is maximized (or detached), so the component may show more. */
    get isMaximized(): boolean {
        return this.windowState === 'maximized' || this.windowState === 'detached';
    }

    /** Settles once the messages for the current locale have been loaded (or failed to load). */
    get messagesReady(): Promise<void> {
        return this.#messagesReady;
    }

    /** A client for the portal's REST APIs, authenticated as the current user. */
    protected get api(): ApiClient {
        return new ApiClient({
            contextPath: this.contextPath ?? '',
            csrfHeader: this.csrfHeader,
            csrfToken: this.csrfToken,
        });
    }

    /** The messages this component uses: English defaults by `Messages.properties` key. */
    protected get defaultMessages(): Messages {
        return {};
    }

    /** The localized message, else its English default, else the key; `{0}`… take `args`. */
    protected msg(key: string, ...args: MessageArg[]): string {
        return formatMessage(
            this.localizedMessages[key] ?? this.defaultMessages[key] ?? key,
            args,
            this.resolvedLocale,
        );
    }

    /**
     * The plural form of `key` for `count` in the user's locale (see {@link pluralKey}), formatted
     * like {@link msg}. The component's English defaults list the family's `.one` and `.other`.
     */
    protected msgPlural(key: string, count: number, ...args: MessageArg[]): string {
        const pattern = pluralKey(
            key,
            count,
            this.resolvedLocale,
            (candidate) =>
                this.localizedMessages[candidate] !== undefined ||
                this.defaultMessages[candidate] !== undefined,
        );
        return this.msg(pattern, ...args);
    }

    /** Dispatches an `up-<subject>-<verb>` event (see events.ts). */
    protected emit(name: string, detail: unknown): boolean {
        return this.dispatchEvent(createEvent(name, detail));
    }

    protected override willUpdate(changed: PropertyValues<this>): void {
        super.willUpdate(changed);
        const locale = this.resolvedLocale;
        // The host's language and direction, so the browser picks fonts, hyphenation and
        // right-to-left layout for the locale (MOD-D51)
        const lang = intlLocale(locale);
        const dir = textDirection(locale);
        if (this.getAttribute('lang') !== lang) {
            this.setAttribute('lang', lang);
        }
        if (this.getAttribute('dir') !== dir) {
            this.setAttribute('dir', dir);
        }
        const keys = messageKeys(Object.keys(this.defaultMessages));
        if (keys.length > 0 && locale !== this.#messagesLocale) {
            this.#messagesLocale = locale;
            this.#messagesReady = loadMessages(this.api, locale, keys).then((messages) => {
                // A newer locale may have been requested meanwhile
                if (this.#messagesLocale === locale) {
                    this.localizedMessages = messages;
                }
            });
        }
    }
}
