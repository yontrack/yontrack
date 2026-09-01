// @ts-check
const {defineConfig, devices} = require('@playwright/test');

/**
 * Playwright configuration for the demo smoke test.
 *
 * Separate from playwright.config.js because the two point at different things: that one runs
 * the whole suite against a stack the build just started, this one runs a single spec against a
 * live deployment. Keeping `testDir` distinct is also what stops the regular PLAYWRIGHT suite
 * from picking the demo spec up.
 *
 * No retries. A retry here would mask exactly what this test exists to catch - a demo that only
 * works on the second try is a demo that is broken.
 */

module.exports = defineConfig({
    testDir: './demo',
    timeout: 120000,
    expect: {timeout: 30000},
    fullyParallel: false,
    forbidOnly: !!process.env.CI,
    retries: 0,
    workers: 1,
    reporter: [
        ['list'],
        ['junit', {outputFile: process.env.JUNIT_REPORT_PATH || 'reports/demo/junit/report.xml'}],
        ['html', {outputFolder: process.env.HTML_REPORT_PATH || 'reports/demo/html', open: 'never'}],
    ],
    use: {
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: {
            mode: 'retain-on-failure',
            size: {width: 1280, height: 720},
        },
    },
    projects: [
        {
            name: 'chromium',
            use: {...devices['Desktop Chrome']},
        },
    ],
});
