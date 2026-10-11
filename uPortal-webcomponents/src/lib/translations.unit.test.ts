import { describe, expect, it } from 'vite-plus/test';

/*
 * MOD-D50: each message a web component uses has English, Spanish and French text in uPortal's
 * Messages bundles. The keys come from every src/components/<name>/messages.ts; the bundles are
 * the ones the portal serves (uPortal-webapp, read as UTF-8 since MOD-I232).
 */

const BUNDLE_DIR = '../../../uPortal-webapp/src/main/resources/properties/i18n';

const bundleSources = import.meta.glob<string>(
    '../../../uPortal-webapp/src/main/resources/properties/i18n/Messages{,_es,_fr}.properties',
    { query: '?raw', import: 'default', eager: true },
);

/** Every Messages bundle in uPortal, main and test, for the duplicate key check. */
const allBundleSources = import.meta.glob<string>(
    ['../../../**/src/*/resources/**/*Messages*.properties', '!**/node_modules/**', '!**/build/**'],
    { query: '?raw', import: 'default', eager: true },
);

const componentMessages = import.meta.glob<Record<string, Record<string, string>>>(
    '../components/*/messages.ts',
    { eager: true },
);

/**
 * Spanish or French text that is the same word as the English one. Each entry needs a reason; a
 * text that matches English and is not listed here is taken as untranslated.
 */
const SAME_AS_ENGLISH: Readonly<Record<string, Readonly<Record<string, string>>>> = {
    es: {
        'search.results.portlets':
            'the Spanish bundle keeps "portlets" as the term (fragmentAudit.portlets.label)',
    },
    fr: {
        'popular.portlets.pagination': '"pagination" is the French word too',
        'search.results.portlets':
            'the French bundle keeps "portlets" as the term (fragmentAudit.portlets.label)',
        'search.suggestions': '"suggestions" is the French word too',
        description: '"description" is the French word too',
    },
};

const ESCAPES: Readonly<Record<string, string>> = { t: '\t', n: '\n', r: '\r', f: '\f' };

/** One key and value of a .properties file, with the line it starts on (1-based). */
export interface PropertiesEntry {
    readonly key: string;
    readonly value: string;
    readonly line: number;
}

/**
 * The entries of a .properties file in order, read as java.util.Properties does: comments,
 * continuations, escapes. A key can appear more than once; Java keeps the last.
 */
export function propertiesEntries(source: string): PropertiesEntry[] {
    const entries: PropertiesEntry[] = [];
    const lines = source.split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
        const start = i + 1;
        let line = (lines[i] ?? '').replace(/^[ \t\f]+/, '');
        if (line === '' || line.startsWith('#') || line.startsWith('!')) {
            continue;
        }
        // A line ending in an odd number of backslashes continues on the next one
        while (/(?:^|[^\\])(?:\\\\)*\\$/.test(line) && i + 1 < lines.length) {
            i++;
            line = line.slice(0, -1) + (lines[i] ?? '').replace(/^[ \t\f]+/, '');
        }
        const match = /^((?:\\.|[^\\=: \t\f])*)[ \t\f]*[=:]?[ \t\f]*(.*)$/s.exec(line);
        /* v8 ignore next -- the pattern matches any line */
        if (!match) continue;
        entries.push({
            key: unescape(match[1] ?? ''),
            value: unescape(match[2] ?? ''),
            line: start,
        });
    }
    return entries;
}

/** Reads a .properties file into a map; for a key that appears twice, the last value wins. */
export function parseProperties(source: string): Map<string, string> {
    return new Map(propertiesEntries(source).map(({ key, value }) => [key, value]));
}

/** The keys a .properties file sets more than once, with the lines that set them. */
export function duplicateKeys(source: string): string[] {
    const lines = new Map<string, number[]>();
    for (const { key, line } of propertiesEntries(source)) {
        lines.set(key, [...(lines.get(key) ?? []), line]);
    }
    return [...lines]
        .filter(([, at]) => at.length > 1)
        .map(([key, at]) => `${key} (lines ${at.join(', ')})`);
}

function unescape(text: string): string {
    return text.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, escape: string) =>
        escape.length === 5
            ? String.fromCharCode(Number.parseInt(escape.slice(1), 16))
            : (ESCAPES[escape] ?? escape),
    );
}

function bundle(suffix: '' | '_es' | '_fr'): Map<string, string> {
    const source = bundleSources[`${BUNDLE_DIR}/Messages${suffix}.properties`];
    if (source === undefined) {
        throw new Error(`Messages${suffix}.properties not found under ${BUNDLE_DIR}`);
    }
    return parseProperties(source);
}

/** The keys each component's messages.ts lists, by component directory. */
function componentKeys(): Map<string, string[]> {
    const keys = new Map<string, string[]>();
    for (const [path, module] of Object.entries(componentMessages)) {
        const component = /components\/([^/]+)\/messages\.ts$/.exec(path)?.[1] ?? path;
        const messages = Object.entries(module).filter(
            ([name, value]) => name.endsWith('_MESSAGES') && typeof value === 'object',
        );
        keys.set(
            component,
            messages.flatMap(([, value]) => Object.keys(value)),
        );
    }
    return keys;
}

/**
 * The keys the three bundles must have for a component's keys: each key, and for a plural family
 * (a key ending in a CLDR category, MOD-I235) at least its `.one` and `.other`, which English,
 * Spanish and French use.
 */
export function requiredKeys(keys: readonly string[]): string[] {
    const required = new Set(keys);
    for (const key of keys) {
        const family = /^(.+)\.(?:zero|one|two|few|many|other)$/.exec(key)?.[1];
        if (family !== undefined) {
            required.add(`${family}.one`);
            required.add(`${family}.other`);
        }
    }
    return [...required];
}

/** The {n} placeholders of a message, sorted. */
function placeholders(message: string): string[] {
    return [...message.matchAll(/\{(\d+)[^}]*\}/g)].map((match) => match[1] ?? '').toSorted();
}

/** An apostrophe that is not doubled, which MessageFormat would take as a quote and drop. */
function hasLoneApostrophe(message: string): boolean {
    return /(?<!')'(?!')/.test(message.replaceAll("''", ''));
}

/** The problems with one key in the three bundles; empty when the key is fine. */
export function problems(
    key: string,
    english: Map<string, string>,
    translations: Readonly<Record<'es' | 'fr', Map<string, string>>>,
    sameAsEnglish: typeof SAME_AS_ENGLISH = SAME_AS_ENGLISH,
): string[] {
    const found: string[] = [];
    const en = english.get(key);
    if (en === undefined) {
        return [`${key}: missing from Messages.properties`];
    }
    if (hasLoneApostrophe(en)) {
        found.push(`${key}: Messages.properties has an apostrophe that is not doubled`);
    }
    for (const lang of ['es', 'fr'] as const) {
        const file = `Messages_${lang}.properties`;
        const text = translations[lang].get(key);
        if (text === undefined) {
            found.push(`${key}: missing from ${file}`);
            continue;
        }
        if (text === en && sameAsEnglish[lang]?.[key] === undefined) {
            found.push(`${key}: ${file} has the English text "${en}"`);
        }
        if (placeholders(text).join() !== placeholders(en).join()) {
            found.push(`${key}: ${file} has other placeholders than English`);
        }
        if (hasLoneApostrophe(text)) {
            found.push(`${key}: ${file} has an apostrophe that is not doubled`);
        }
    }
    return found;
}

describe('parseProperties', () => {
    it('reads keys, escapes, continuations and comments', () => {
        const parsed = parseProperties(
            [
                '# a comment',
                '! another',
                '',
                'plain=Text',
                '  spaced : Value with spaces ',
                'escaped=Cat\\u00e9gories',
                'utf8=Catégories',
                'tab=a\\tb',
                'continued=first \\',
                '    second',
                'bare',
                'colon\\:key=value',
            ].join('\n'),
        );
        expect(Object.fromEntries(parsed)).toEqual({
            plain: 'Text',
            spaced: 'Value with spaces ',
            escaped: 'Catégories',
            utf8: 'Catégories',
            tab: 'a\tb',
            continued: 'first second',
            bare: '',
            'colon:key': 'value',
        });
    });
});

describe('requiredKeys', () => {
    it('asks for .one and .other of each plural family', () => {
        expect(requiredKeys(['title', 'items.other', 'rows.few'])).toEqual([
            'title',
            'items.other',
            'rows.few',
            'items.one',
            'rows.one',
            'rows.other',
        ]);
    });
});

describe('problems', () => {
    const english = new Map([
        ['k', 'Move {0} up'],
        ['same', 'pagination'],
        ['quote', "It's"],
    ]);
    const ok = { es: new Map([['k', 'Mover {0}']]), fr: new Map([['k', 'Déplacer {0}']]) };

    it('accepts a key with both translations', () => {
        expect(problems('k', english, ok)).toEqual([]);
    });

    it('finds a key missing from each bundle', () => {
        expect(problems('none', english, ok)).toEqual(['none: missing from Messages.properties']);
        expect(problems('k', english, { es: new Map(), fr: ok.fr })).toEqual([
            'k: missing from Messages_es.properties',
        ]);
    });

    it('finds English text unless the allowlist names it', () => {
        const same = {
            es: new Map([['same', 'paginación']]),
            fr: new Map([['same', 'pagination']]),
        };
        expect(problems('same', english, same, { es: {}, fr: {} })).toEqual([
            'same: Messages_fr.properties has the English text "pagination"',
        ]);
        expect(problems('same', english, same, { es: {}, fr: { same: 'the same word' } })).toEqual(
            [],
        );
    });

    it('finds lost placeholders and lone apostrophes', () => {
        const bad = { es: new Map([['k', 'Mover']]), fr: new Map([['k', "L'élément {0}"]]) };
        expect(problems('k', english, bad)).toEqual([
            'k: Messages_es.properties has other placeholders than English',
            'k: Messages_fr.properties has an apostrophe that is not doubled',
        ]);
        expect(problems('quote', english, { es: new Map(), fr: new Map() })[0]).toBe(
            'quote: Messages.properties has an apostrophe that is not doubled',
        );
    });
});

describe('duplicateKeys', () => {
    it('lists each key set more than once, with its lines', () => {
        const source = ['a=1', '# comment', 'b=2', 'a = 3', 'c=4 \\', '  continued', 'b=5'].join(
            '\n',
        );
        expect(duplicateKeys(source)).toEqual(['a (lines 1, 4)', 'b (lines 3, 7)']);
        expect(parseProperties(source).get('a')).toBe('3');
    });

    it('finds none in a file without them', () => {
        expect(duplicateKeys('a=1\nb=2\n')).toEqual([]);
    });
});

describe('the Messages bundles', () => {
    it('finds the main and the test bundles', () => {
        const paths = Object.keys(allBundleSources);
        expect(paths.some((path) => path.endsWith('/i18n/Messages_es.properties'))).toBe(true);
        expect(paths.some((path) => path.includes('/src/test/resources/'))).toBe(true);
    });

    it.each(Object.keys(allBundleSources))('%s sets each key once', (path) => {
        expect(duplicateKeys(allBundleSources[path] ?? '')).toEqual([]);
    });
});

describe('the web components’ messages', () => {
    const english = bundle('');
    const translations = { es: bundle('_es'), fr: bundle('_fr') };
    const components = componentKeys();

    it('finds every component', () => {
        expect(components.size).toBeGreaterThan(0);
        for (const keys of components.values()) {
            expect(keys.length).toBeGreaterThan(0);
        }
    });

    it.each([...components.keys()])(
        '%s has English, Spanish and French text for each key',
        (component) => {
            const found = requiredKeys(components.get(component) ?? []).flatMap((key) =>
                problems(key, english, translations),
            );
            expect(found).toEqual([]);
        },
    );

    it('lists only keys some component uses in the allowlist', () => {
        const used = new Set([...components.values()].flat());
        for (const keys of Object.values(SAME_AS_ENGLISH)) {
            for (const key of Object.keys(keys)) {
                expect(used, key).toContain(key);
            }
        }
    });
});
