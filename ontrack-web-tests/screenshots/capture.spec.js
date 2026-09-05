/**
 * Captures the release-notes screenshots from the demo.
 *
 * Run on demand by .github/workflows/demo-screenshots.yml when someone is writing the release
 * notes for a version - see doc/dev-guide/demo-screenshots.md. It reports nothing to Yontrack
 * and gates no promotion: the demo is the source of the images, not a step in the pipeline.
 *
 * It does not seed. `demo-smoke.yml` already resets and seeds the demo deterministically as
 * part of SILVER, and one system owning the demo's state is worth more than a guarantee here.
 *
 * The pages, and how to know each is ready, are in `catalogue.js`.
 */

const fs = require('fs')
const path = require('path')
const {test} = require('@playwright/test')
const {Connection, Credentials} = require('@ontrack/connection')
const {Ontrack} = require('@ontrack/ontrack')
const {login} = require('../tests/core/login')
const {screenshotFileName, assertVersion} = require('./naming')
const {catalogue} = require('./catalogue')

/**
 * Nothing is defaulted, for the reason `demo.spec.js` gives: a default `DEMO_URL` would point
 * a local run at the live demo - the one instance nobody means to be driving by accident.
 *
 * Read inside the hooks rather than at module load, so that the pure guards in
 * `catalogue.spec.js` can still be collected and run with no environment at all.
 */
const required = (name) => {
    const value = process.env[name]
    if (!value) throw new Error(`${name} must be set`)
    return value
}

const outputDir = path.resolve(process.env.SCREENSHOT_OUTPUT_DIR || 'reports/screenshots')

/**
 * One signed-in session for the whole run, shared by every shot. Playwright would otherwise
 * give each test a fresh context, and each shot would pay for a full Keycloak round trip.
 *
 * Deliberately *not* a serial describe: a page whose `ready` never settles must not cost the
 * remaining shots. Each entry fails on its own and the rest are still captured.
 *
 * Sharing state across tests is safe here because of what Playwright does after a failure: it
 * restarts the worker, so `beforeAll` runs again and the shots that follow get a fresh context
 * and a fresh login. A failed shot costs a re-login; it cannot leave a half-navigated page
 * behind for the next one.
 */
let context
let page
let version
let ui

test.beforeAll(async ({browser}) => {
    // Checked before the browser does any work, so a bad version fails in a second rather than
    // after the first shot.
    version = assertVersion(required('SCREENSHOT_VERSION'))

    ui = required('DEMO_URL').replace(/\/$/, '')
    const ontrack = new Ontrack(new Connection({
        ui,
        credentials: new Credentials({
            username: required('DEMO_USERNAME'),
            password: required('DEMO_PASSWORD'),
        }),
    }))

    // Inherits the project's `use` - the fixed viewport included; Playwright's `browser`
    // fixture applies the resolved context options to `newContext` too, not only to `page`.
    context = await browser.newContext()
    page = await context.newPage()
    await login(page, ontrack)

    fs.mkdirSync(outputDir, {recursive: true})
    console.log(`Capturing ${catalogue.length} screenshots for ${version} from ${ui} into ${outputDir}`)
})

test.afterAll(async () => {
    await context?.close()
})

for (const entry of catalogue) {
    test(`${entry.slug} - ${entry.description}`, async () => {
        await page.goto(`${ui}${entry.path}`)
        await entry.ready(page)

        const file = path.join(outputDir, screenshotFileName(version, entry.slug))
        await page.screenshot({path: file, animations: 'disabled', caret: 'hide'})
        console.log(`  ${path.basename(file)}`)
    })
}
