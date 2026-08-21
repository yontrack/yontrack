const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {PromotionLevelPage} = require("../promotionLevels/PromotionLevelPage");

/**
 * Key used by the promotion level page to store its layout.
 */
const layoutKey = 'page-promotion-level-layout'

/**
 * Layout where the lead time chart takes the full width of the grid, instead of
 * the half width it uses by default.
 */
const storedLayout = [
    {i: 'chart-lead-time', x: 0, y: 0, w: 12, h: 12},
    {i: 'chart-frequency', x: 0, y: 12, w: 6, h: 12},
    {i: 'chart-ttr', x: 6, y: 12, w: 6, h: 12},
    {i: 'chart-stability', x: 0, y: 24, w: 6, h: 12},
    {i: 'section-history', x: 0, y: 36, w: 12, h: 12},
    {i: 'section-auto-versioning', x: 0, y: 48, w: 12, h: 12},
]

test('the layout of a page is restored from the local storage', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const promotionLevel = await branch.createPromotionLevel()

    // The layout is stored before the page is ever displayed
    await page.addInitScript(([key, layout]) => {
        localStorage.setItem(key, JSON.stringify(layout))
    }, [layoutKey, storedLayout])

    await login(page, ontrack)

    const promotionLevelPage = new PromotionLevelPage(page, promotionLevel)
    await promotionLevelPage.goTo()

    // The lead time chart must use the stored layout: the full width of the grid
    const grid = page.locator('.react-grid-layout')
    await expect(grid).toBeVisible()
    const leadTimeCell = page.locator('.react-grid-item').filter({has: page.getByTestId('chart-lead-time')})
    await expect(leadTimeCell).toBeVisible()

    const gridBox = await grid.boundingBox()
    const cellBox = await leadTimeCell.boundingBox()
    expect(cellBox.width).toBeGreaterThan(gridBox.width * 0.9)
})

test('the layout of a page can be reset to its default', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const promotionLevel = await branch.createPromotionLevel()

    // The layout is stored before the page is ever displayed
    await page.addInitScript(([key, layout]) => {
        localStorage.setItem(key, JSON.stringify(layout))
    }, [layoutKey, storedLayout])

    await login(page, ontrack)

    const promotionLevelPage = new PromotionLevelPage(page, promotionLevel)
    await promotionLevelPage.goTo()

    const grid = page.locator('.react-grid-layout')
    const leadTimeCell = page.locator('.react-grid-item').filter({has: page.getByTestId('chart-lead-time')})
    await expect(leadTimeCell).toBeVisible()

    // Resetting the layout
    await page.getByRole('button', {name: 'Reset layout'}).click()

    // The lead time chart goes back to the half width of the default layout
    await expect.poll(async () => {
        const gridBox = await grid.boundingBox()
        const cellBox = await leadTimeCell.boundingBox()
        return cellBox.width < gridBox.width * 0.6
    }).toBe(true)
})
