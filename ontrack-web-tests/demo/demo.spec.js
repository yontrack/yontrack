/**
 * Smoke test for the demo deployment.
 *
 * Run against the live demo by .github/workflows/demo-smoke.yml, after scripts/demo-smoke.sh
 * has waited for the deployed version to answer and the seed program has reset the dataset.
 *
 * Deliberately thin. The heavy suites already ran for BRONZE; re-running them here would
 * test the deployment, not the code, and a fat smoke suite becomes the flaky thing that blocks
 * releases. What is left is what only a real deployment can break: the Keycloak realm the chart
 * provisions, and the UI pod talking to the backend pod - and, in a second test, the four views
 * the seeded security findings are there to show (#1867), read the way a visitor reaches them.
 *
 * It lives outside `tests/` on purpose - `playwright.config.js` points `testDir` at that
 * directory, so the regular PLAYWRIGHT suite would otherwise pick this spec up and run it
 * against the local stack.
 */

const {test, expect} = require('@playwright/test')
const {Connection, Credentials} = require("@ontrack/connection")
const {Ontrack} = require("@ontrack/ontrack")
const {login} = require("../tests/core/login")
const {ProjectPage} = require("../tests/core/projects/project")
const {CommandPalette} = require("../tests/core/search/CommandPalette")

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
 * The project of the seeded security findings, its release branch and the CVE fixed on `main`
 * but still exposed there - `DemoContent.SECURITY`, `SECURITY_RELEASE` and `CVE_FIXED_ON_MAIN`.
 * Defaulted, unlike the four above: they name the dataset, not an instance.
 */
const findingsProject = process.env.DEMO_FINDINGS_PROJECT || 'petclinic-billing'
const findingsRelease = process.env.DEMO_FINDINGS_RELEASE || 'release-2.3'
const findingsCve = process.env.DEMO_FINDINGS_CVE || 'CVE-2024-38816'

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

test('the demo shows the security findings, from the project to the finding and from the search', async ({page}) => {
    const ontrack = demoOntrack()
    await login(page, ontrack)

    // The Security section of the project page: open findings, and accepted ones
    await page.goto(`${ui}/display/project/${findingsProject}`)
    await new ProjectPage(page, ontrack, {name: findingsProject}).expectOnPage()
    const section = page.getByTestId('project-security')
    await expect(section).toBeVisible()
    await expect(section.getByTestId('security-open-HIGH')).toHaveText(/^[1-9]\d*$/)
    await expect(section.getByTestId('security-accepted')).toHaveText(/^[1-9]\d*$/)
    await expect(section.getByTestId(`security-branch-${findingsRelease}-HIGH`)).toHaveText(/^[1-9]\d*$/)

    // The findings page, through the section's own link
    await section.getByRole('link', {name: 'All findings'}).click()
    const table = page.getByTestId('project-findings')
    await expect(table).toBeVisible()
    const cveLink = table.getByRole('link', {name: findingsCve, exact: true})
    await expect(cveLink).toBeVisible()

    // The finding page: resolved on main, still exposed on the release branch
    await cveLink.click()
    await expect(page).toHaveURL(/\/extension\/findings\/finding\/\d+$/)
    await expect(page.getByTestId('finding-summary').getByText(findingsCve, {exact: true})).toBeVisible()
    await expect(page.getByTestId(`finding-exposure-${findingsRelease}-SECURITY.DEPENDENCIES`)).toContainText(/exposed/i)
    await expect(page.getByTestId('finding-exposure-main-SECURITY.DEPENDENCIES')).toContainText(/resolved/i)

    // The search result of the CVE in the command palette, leading back to the finding page
    const palette = new CommandPalette(page)
    await palette.openByShortcut()
    await palette.type(findingsCve)
    // Typed again until it shows, as the search suite does: the index is written as the seed runs
    const option = palette.option(`${findingsCve}, Security finding, in ${findingsProject}`)
    await palette.expectOption(option, findingsCve)
    await palette.openWithEnter(option)
    await expect(page).toHaveURL(/\/extension\/findings\/finding\/\d+$/)
    await expect(page.getByTestId('finding-summary').getByText(findingsCve, {exact: true})).toBeVisible()
})
