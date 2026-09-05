/**
 * What gets photographed, and how to know the page is ready to be photographed.
 *
 * This is the part that changes; the capture mechanics in `capture.spec.js` do not. A feature
 * that wants a release-notes screenshot adds one entry here.
 *
 * Every entry is backed by the demo seed, so the names below mirror `DemoContent` in
 * `ontrack-demo-seed`. Changing a name there without changing it here leaves this suite
 * navigating to a page that does not exist - which is the intended failure: a screenshot of
 * the wrong data is worse than no screenshot.
 *
 * Entry shape:
 *
 * - `slug`        lower-case kebab, the second half of `<version>-<slug>.png`
 * - `description` what the shot is for, printed in the run summary
 * - `path`        rooted path, appended to DEMO_URL
 * - `ready`       runs before the shutter, and is the only assertion this suite makes
 *
 * On `ready`: assert *positive evidence* that the data arrived - a populated row, a link named
 * after a seeded entity. Never a timeout, and never the absence of a loading indicator.
 * Yontrack renders those only while loading, so "the spinner is hidden" is already true before
 * the fetch starts. Section test ids are no better: `PageSection` puts `data-testid` on the
 * Card and swaps a Skeleton in underneath, so the section is visible throughout its own load.
 */

const {expect} = require('@playwright/test')
const {expectOnPage} = require('../tests/support/page-utils')

/** Seeded names, mirroring `DemoContent`. */
const SERVICE = 'petclinic'
const MAIN = 'main'
/** The fully-promoted build: BRONZE, SILVER, CANARY, GOLD, all validations green, with links. */
const BUILD = '107'
const GOLD = 'GOLD'
const SECURITY_SCAN = 'SECURITY.SCAN'
/**
 * `DemoContent.DASHBOARD_UUID`. The seed pins it precisely so it can be addressed, and
 * `DashboardContextProvider` selects the dashboard named by `?dashboard=`. Needed because the
 * seed *shares* the dashboard rather than selecting it: Yontrack only ever selects one for the
 * account that saved it, so a visitor lands on the built-in dashboard and would otherwise be
 * photographed looking at it.
 */
const DASHBOARD_UUID = '1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a01'

/**
 * `DemoContent`'s BranchStatuses widget. Every widget cell carries its uuid as a test id, and the
 * readiness check below has to be scoped to this one: other widgets on the dashboard now link to
 * the same project, so an unscoped `petclinic` link would be satisfied by a title that renders long
 * before the branch table has any rows.
 */
const BRANCH_STATUSES_UUID = '1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a11'

const catalogue = [
    {
        slug: 'dashboard',
        description: 'The demo dashboard - branch statuses, deployments, recent projects and a promotion chart',
        path: `/?dashboard=${DASHBOARD_UUID}`,
        ready: async (page) => {
            // The BranchStatuses widget's configured title, and then a row inside it: the title
            // renders before the branches have loaded. Both are scoped to that widget - the
            // promotion chart widget's title links to the same project from its own, much smaller,
            // query, so an unscoped link would report the dashboard ready with the table empty.
            const branchStatuses = page.getByTestId(BRANCH_STATUSES_UUID)
            await expect(branchStatuses.getByText('Sample application', {exact: true}).first()).toBeVisible()
            await expect(branchStatuses.getByRole('link', {name: SERVICE, exact: true}).first()).toBeVisible()
        },
    },
    {
        slug: 'project',
        description: 'A project and its branches',
        path: `/display/project/${SERVICE}`,
        ready: async (page) => {
            await expectOnPage(page, 'project')
            await expect(page.getByRole('link', {name: MAIN, exact: true}).first()).toBeVisible()
        },
    },
    {
        slug: 'branch',
        description: 'A branch: builds, promotions and validations',
        path: `/display/branch/${SERVICE}/${MAIN}`,
        ready: async (page) => {
            // Scoped to the header: after a client-side navigation Next mirrors the document
            // title - which carries the branch name - into its route announcer, so looking for
            // the name anywhere on the page matches twice.
            await expect(page.getByTestId('branch-title')).toContainText(MAIN)
            // Only a populated row carries `data-row-key`; the "No data" placeholder is an
            // `.ant-table-placeholder` and matches neither.
            await expect(page.locator('.ant-table-row[data-row-key]').first()).toBeVisible()
            await expect(page.getByRole('link', {name: BUILD, exact: true}).first()).toBeVisible()
        },
    },
    {
        slug: 'build',
        description: 'A build: its promotions, validations and links',
        path: `/display/build/${SERVICE}/${MAIN}/${BUILD}`,
        // One assertion per panel, on content the seed puts there. The panels load
        // independently, and each renders its frame - and its test id - while still empty.
        ready: async (page) => {
            await expect(page.getByText(GOLD, {exact: true}).first()).toBeVisible()
            await expect(page.getByText(SECURITY_SCAN, {exact: true}).first()).toBeVisible()
        },
    },
    {
        slug: 'environments',
        description: 'The environments and what is deployed in each',
        path: '/extension/environments/environments',
        // Matched on the prefix: the card carries the environment's ID, which the seed does
        // not fix, so there is no name to wait on here.
        ready: async (page) => {
            await expect(page.locator('[data-testid^="environment-"]').first()).toBeVisible()
        },
    },
]

module.exports = {catalogue}
