// @ts-check
const {defineConfig, devices} = require('@playwright/test');

/**
 * Playwright configuration for capturing release-notes screenshots from the demo.
 *
 * A third config beside `playwright.config.js` and `playwright.demo.config.js` for the same
 * reason the second one exists: `testDir` is what keeps the regular PLAYWRIGHT suite from
 * picking these specs up and running them against the local stack.
 *
 * This is not a test suite. It asserts only enough to know a page rendered before shooting it,
 * and it reports nothing to Yontrack - see doc/dev-guide/demo-screenshots.md.
 *
 * Two projects, because the guards must run *first*: `capture` depends on `guard`, so a
 * duplicate slug or a malformed version fails in milliseconds rather than being discovered by
 * one shot silently overwriting another, after three minutes of driving the demo. File order
 * would not do it - `capture.spec.js` sorts before `catalogue.spec.js`.
 *
 * `guard` also runs on its own, with no demo and no environment to point at:
 *
 *     npx playwright test --config playwright.screenshots.config.js --project guard
 */

module.exports = defineConfig({
    testDir: './screenshots',
    timeout: 120000,
    expect: {timeout: 30000},
    fullyParallel: false,
    forbidOnly: !!process.env.CI,
    // One shot per page, taken in order, sharing one signed-in session.
    retries: 0,
    workers: 1,
    reporter: [['list']],
    use: {
        trace: 'retain-on-failure',
        // `only-on-failure` would drop a *failure* screenshot into the same run as the ones
        // being captured on purpose. They go to different directories, but the noise is not
        // worth it: the trace already shows what the page looked like.
        screenshot: 'off',
        video: 'off',
    },
    projects: [
        {
            name: 'guard',
            testMatch: /catalogue\.spec\.js/,
        },
        {
            name: 'capture',
            testMatch: /capture\.spec\.js/,
            dependencies: ['guard'],
            // A fixed viewport rather than the `Desktop Chrome` default, and no
            // `deviceScaleFactor`: the images land in the wiki beside hand-taken ones, so they
            // should not be twice their size.
            use: {
                ...devices['Desktop Chrome'],
                viewport: {width: 1440, height: 900},
                deviceScaleFactor: 1,
            },
        },
    ],
});
