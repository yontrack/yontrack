import {expect} from "@playwright/test";
import {randomUUID} from "crypto";
import {test} from "../../fixtures/connection";
import {login} from "../login";
import {ValidationStampPage} from "../validationRuns/validationStamp";
import {BuildPage} from "../builds/BuildPage";
import {BranchPage} from "../branches/branch";
import {graphQLCallMutation} from "@ontrack/graphql";
import {expectStickyHeader} from "../../support/sticky-header";

/**
 * Sticky table headers (#1932), one test per screen the issue cites.
 *
 * Each test seeds enough data for its table to overflow what it is shown in, scrolls, and checks
 * that the header row is still inside the visible area of the container which scrolled.
 */

/** Runs `count` async calls in sequence, so that the order of creation is the order given. */
const times = async (count, fn) => {
    const results = []
    for (let i = 0; i < count; i++) {
        results.push(await fn(i))
    }
    return results
}

test('the validation stamp history keeps its header when its section scrolls', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp()
    await times(30, async () => {
        const build = await branch.createBuild()
        await build.validate(validationStamp)
    })

    await login(page, ontrack)
    const vsPage = new ValidationStampPage(page, validationStamp)
    await vsPage.goTo()

    // Five runs per page: loading more until the section overflows
    const section = page.getByTestId('section-history')
    const loadMore = section.getByRole('button', {name: 'Load more...'})
    const rows = section.locator('tbody tr:not([aria-hidden])')
    await expect(rows).toHaveCount(5)
    for (let expected = 10; expected <= 20; expected += 5) {
        await loadMore.click()
        await expect(rows).toHaveCount(expected)
    }

    await expectStickyHeader(section, {inWindow: false})
})

test('the build page validations keep their header when their section scrolls', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamps = await times(30, () => branch.createValidationStamp())
    const build = await branch.createBuild()
    for (const validationStamp of validationStamps) {
        await build.validate(validationStamp)
    }

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const section = page.getByTestId('validations')
    await expect(section.locator('tbody tr:not([aria-hidden])').first()).toBeVisible()

    await expectStickyHeader(section, {inWindow: false})
})

test('the branch statuses widget keeps its header when the widget scrolls', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branches = await times(15, () => project.createBranch())
    for (const branch of branches) {
        const promotionLevel = await branch.createPromotionLevel('BRONZE')
        const validationStamp = await branch.createValidationStamp('BUILD')
        const build = await branch.createBuild()
        await build.validate(validationStamp)
        await build.promote(promotionLevel)
    }

    const dashboardName = `sticky-${Date.now()}`
    const widgetUuid = randomUUID()
    const yaml = [
        `- name: "${dashboardName}"`,
        `  widgets:`,
        `    - uuid: "${widgetUuid}"`,
        `      key: "home/BranchStatuses"`,
        `      layout: {x: 0, y: 0, w: 12, h: 30}`,
        `      config:`,
        `        promotionConfigs:`,
        `          - promotionLevel: "BRONZE"`,
        `        validationConfigs:`,
        `          - validationStamp: "BUILD"`,
        `        branches:`,
        ...branches.flatMap(branch => [
            `          - project: "${project.name}"`,
            `            branch: "${branch.name}"`,
        ]),
    ].join('\n')
    await graphQLCallMutation(
        ontrack.connection,
        'applyDashboards',
        `
            mutation ApplyDashboards($yaml: String!) {
                applyDashboards(input: { yaml: $yaml }) {
                    dashboards { uuid name }
                    errors { message }
                }
            }
        `,
        {yaml}
    )

    await login(page, ontrack)
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText(dashboardName).click()

    const widget = page.getByTestId(widgetUuid)
    await expect(widget.getByRole('link', {name: branches[branches.length - 1].name})).toBeAttached()

    await expectStickyHeader(widget, {inWindow: false})
})

test('the branch builds matrix keeps its header when the window scrolls, and aligned when it scrolls sideways', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamps = await times(30, () => branch.createValidationStamp())
    const builds = await times(30, () => branch.createBuild())
    // The first and the last stamp are run on every build, so that the matrix has cells at both ends
    for (const build of builds) {
        await build.validate(validationStamps[0])
        await build.validate(validationStamps[validationStamps.length - 1])
    }

    // Narrow enough for 30 stamps to overflow sideways
    await page.setViewportSize({width: 1024, height: 700})
    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const matrix = page.getByTestId('branch-builds')
    const rows = matrix.locator('tbody tr:not([aria-hidden])')
    await expect(rows.first()).toBeVisible()
    const initialCount = await rows.count()
    await matrix.getByRole('button', {name: 'Load more...'}).click()
    await expect.poll(() => rows.count()).toBeGreaterThan(initialCount)

    await expectStickyHeader(matrix, {inWindow: true})

    // Scrolling sideways to the last stamp. The body is the one part of the table scrolling
    // sideways: the sticky header follows it.
    await matrix.evaluate(el => {
        const body = [...el.querySelectorAll('div')].find(div => div.querySelector('tbody') && div.scrollWidth > div.clientWidth)
        body.scrollLeft = body.scrollWidth
    })

    // The header follows the body: the last header cell sits over the last body cell, the toolbar
    // spans exactly the three fixed columns, and those stay in place on the left
    await expect.poll(() => matrix.evaluate(el => {
        const body = [...el.querySelectorAll('div')].find(div => div.querySelector('tbody') && div.scrollWidth > div.clientWidth)
        const headerCells = [...el.querySelectorAll('thead th')]
            .filter(th => !th.className.includes('scrollbar'))
        const bodyCells = [...el.querySelector('tbody tr:not([aria-hidden])').querySelectorAll('td')]
        const rect = cell => cell.getBoundingClientRect()
        const near = (a, b) => Math.abs(a - b) <= 1
        const wrapper = el.getBoundingClientRect()
        const lastHeader = headerCells[headerCells.length - 1]
        const lastBody = bodyCells[bodyCells.length - 1]
        return {
            scrolledSideways: body.scrollLeft > 0,
            lastColumnAligned: near(rect(lastHeader).left, rect(lastBody).left) && near(rect(lastHeader).right, rect(lastBody).right),
            lastColumnVisible: rect(lastBody).right <= wrapper.right + 1,
            toolbarOverFixedColumns: near(rect(headerCells[0]).left, rect(bodyCells[0]).left) && near(rect(headerCells[0]).right, rect(bodyCells[2]).right),
            fixedColumnsInPlace: near(rect(bodyCells[0]).left, wrapper.left),
        }
    }), {message: 'The header must stay aligned with the body after a horizontal scroll'}).toEqual({
        scrolledSideways: true,
        lastColumnAligned: true,
        lastColumnVisible: true,
        toolbarOverFixedColumns: true,
        fixedColumnsInPlace: true,
    })
})
