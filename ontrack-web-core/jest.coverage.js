/**
 * Coverage settings of the Jest suite (#1820), spread into `jest.config.js`.
 *
 * They sit in a module of their own for one reason: `jest.config.js` cannot be required from
 * inside a running Jest suite - its first line pulls in `next/jest`, which needs `TextDecoder` and
 * an ESM-capable VM - so `__tests__/jest.coverage.test.js`, the guard that keeps the denominator
 * honest, has nothing to assert against unless the values live somewhere it can reach. This file
 * is that somewhere; it is plain data and requires nothing.
 */
module.exports = {

    /**
     * The denominator: **all** production sources, listed explicitly.
     *
     * Jest's default is to report only the files a test happened to load, which makes the
     * percentage a measure of the tests rather than of the code - a module nobody imports would
     * simply not appear. Listing the roots makes an untested file count as 0%, exactly as JaCoCo
     * counts a class no test touched, which is what `docs/grilling/2026-09-coverage/README.md`
     * settled for both halves of the build.
     *
     * The roots are the production ones of `ontrack-web-core`: the `app` router (the `/mobile` UI
     * among it), the shared `components` tree, the `pages` router and the `middleware.js` that
     * routes between them. `styles` is CSS, `public` is static assets, and `__tests__` is the test
     * tree - none of them is production JavaScript. `.jsx` is not in use today and is matched
     * anyway, so that the first one to appear is counted rather than silently skipped.
     *
     * `__tests__/jest.coverage.test.js` fails when a new root of production JavaScript is added
     * here and not declared.
     */
    collectCoverageFrom: [
        'app/**/*.{js,jsx}',
        'components/**/*.{js,jsx}',
        'pages/**/*.{js,jsx}',
        'middleware.js',
        '!**/node_modules/**',
    ],

    /**
     * - `json-summary` writes `coverage/coverage-summary.json`. Its `total.lines.pct` and
     *   `total.branches.pct` are the **machine-readable contract** with the rest of the coverage
     *   initiative: the report job of #1821 reads them and #1822 records them as the two metrics
     *   of the `COVERAGE.UI_UNIT` validation stamp. Renaming the directory or dropping the
     *   reporter breaks a consumer that lives in a workflow file, not in this module.
     * - `html` is the human report #1821 uploads as a CI artifact.
     * - `lcov` is the interchange format, kept for whatever later wants to read it.
     * - `text-summary` puts the figures in the console of whoever ran the task.
     */
    coverageReporters: ['lcov', 'json-summary', 'html', 'text-summary'],

    coverageDirectory: 'coverage',

    /*
     * One thing to know before reading the branch figure. The suite runs under
     * `coverageProvider: 'v8'` (set in `jest.config.js` long before this initiative), and v8
     * reports a file no test ever loaded as a single uncovered branch rather than as its real
     * branch count. Line coverage is unaffected - such a file reports every one of its lines as
     * uncovered, which is the whole point of the denominator above - but the branch percentage is
     * computed over a total that undercounts the untested files. It is the headline's companion,
     * not the headline: `docs/grilling/2026-09-coverage/README.md` makes line coverage the figure
     * that matters, and the two are recorded side by side.
     */
}
