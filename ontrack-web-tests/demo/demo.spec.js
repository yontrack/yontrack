/**
 * Smoke test for the demo deployment.
 *
 * Run against the live demo by .github/workflows/demo-smoke.yml, after scripts/demo-smoke.sh
 * has waited for the deployed version to answer and the seed program has reset the dataset.
 *
 * Deliberately one test. The heavy suites already ran for BRONZE; re-running them here would
 * test the deployment, not the code, and a fat smoke suite becomes the flaky thing that blocks
 * releases. What is left is what only a real deployment can break: the Keycloak realm the chart
 * provisions, and the UI pod talking to the backend pod.
 *
 * It lives outside `tests/` on purpose - `playwright.config.js` points `testDir` at that
 * directory, so the regular PLAYWRIGHT suite would otherwise pick this spec up and run it
 * against the local stack.
 */

const {test} = require('@playwright/test')
const {Connection, Credentials} = require("@ontrack/connection")
const {Ontrack} = require("@ontrack/ontrack")
const {login} = require("../tests/core/login")
const {ProjectPage} = require("../tests/core/projects/project")

/**
 * Nothing is defaulted. The workflow sets all four, and a default `DEMO_URL` would point a
 * local run at the live demo - the one instance nobody means to be driving by accident.
 */
const required = (name) => {
    const value = process.env[name]
    if (!value) throw new Error(`${name} must be set`)
    return value
}

const ui = required('DEMO_URL').replace(/\/$/, '')
const projectName = required('DEMO_SEEDED_PROJECT')

/**
 * The demo has no management port exposed, so the usual `connection` fixture - which fetches
 * an admin token from :8800 - cannot be used. The browser flow needs neither: it signs in
 * through Keycloak like a visitor does.
 */
const demoOntrack = () => new Ontrack(new Connection({
    ui,
    credentials: new Credentials({
        username: required('DEMO_USERNAME'),
        password: required('DEMO_PASSWORD'),
    }),
}))

test('the demo signs in and renders the seeded project', async ({page}) => {
    const ontrack = demoOntrack()

    // Keycloak login. `login` ends on the home page, so this covers "a page renders" for the
    // UI pod as well as "the realm is there" for Keycloak.
    await login(page, ontrack)

    // One entity page, resolved by name so the test needs no ID from the seed. This is the
    // page that fails when the UI pod cannot reach the backend pod.
    await page.goto(`${ui}/display/project/${projectName}`)
    await new ProjectPage(page, ontrack, {name: projectName}).expectOnPage()
})
