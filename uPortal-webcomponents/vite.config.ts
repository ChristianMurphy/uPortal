import { defineConfig } from 'vite-plus';
import { playwright } from 'vite-plus/test/browser-playwright';

/**
 * The components to build, by custom element name. Each one is an entry,
 * src/components/<name>/<name>.ts, that the portal loads by its name through the build manifest
 * (build/dist/manifest.json; see WebComponentModuleResolver in uPortal-rendering).
 */
const components = ['up-favorites', 'up-popular-portlets', 'up-search'];

export default defineConfig({
    // Built files import each other relatively, so the bundle works under any context path
    base: './',
    build: {
        outDir: 'build/dist',
        emptyOutDir: true,
        target: 'es2023',
        // build/dist/manifest.json maps each entry name to its content-hashed file
        manifest: 'manifest.json',
        rolldownOptions: {
            input: Object.fromEntries(
                components.map((name) => [name, `src/components/${name}/${name}.ts`]),
            ),
            output: {
                entryFileNames: 'assets/[name]-[hash].js',
                chunkFileNames: 'assets/[name]-[hash].js',
                assetFileNames: 'assets/[name]-[hash][extname]',
                // XState in one shared chunk that every component imports, so a page with
                // several components loads it once (MOD-D31)
                codeSplitting: {
                    groups: [
                        { name: 'xstate', test: /[\\/]node_modules[\\/]xstate[\\/]/ },
                        // Lit, the base library and the shared stylesheet, which every component
                        // uses: one chunk for all of them
                        {
                            name: 'shared',
                            test: /[\\/](src[\\/](lib|state)|node_modules[\\/](lit|lit-html|lit-element|@lit[\\/][^\\/]+))[\\/]/,
                        },
                    ],
                },
            },
        },
    },
    test: {
        // Restore globals stubbed with vi.stubGlobal (fetch) after every test
        unstubGlobals: true,
        projects: [
            {
                extends: true,
                test: {
                    name: 'unit',
                    include: ['src/**/*.unit.test.ts'],
                    environment: 'node',
                },
            },
            {
                extends: true,
                test: {
                    name: 'browser',
                    include: ['src/**/*.browser.test.ts'],
                    browser: {
                        enabled: true,
                        headless: true,
                        provider: playwright(),
                        instances: [{ browser: 'chromium' }],
                        screenshotFailures: false,
                    },
                },
            },
        ],
        coverage: {
            provider: 'v8',
            include: ['src/**/*.ts'],
            exclude: ['src/**/*.test.ts', 'src/test-support/**'],
            reportsDirectory: 'build/coverage',
            reporter: ['text', 'html', 'lcov', 'json-summary'],
            // Every file, not just the total, as MOD-D20 asks per package
            thresholds: {
                perFile: true,
                lines: 90,
                branches: 90,
                functions: 90,
                statements: 90,
            },
        },
    },
    lint: {
        ignorePatterns: ['build/**', 'node_modules/**'],
        categories: {
            correctness: 'error',
            suspicious: 'error',
            perf: 'error',
        },
        options: {
            typeAware: true,
            typeCheck: true,
            denyWarnings: true,
            reportUnusedDisableDirectives: 'error',
        },
        overrides: [
            {
                // Tests narrow DOM queries and mocks on purpose; a wrong guess fails the test
                files: ['src/**/*.test.ts', 'src/test-support/**'],
                rules: { 'typescript/no-unsafe-type-assertion': 'off' },
            },
        ],
    },
    fmt: {
        ignorePatterns: ['build/**', 'node_modules/**', 'package-lock.json'],
        singleQuote: true,
        printWidth: 100,
    },
});
