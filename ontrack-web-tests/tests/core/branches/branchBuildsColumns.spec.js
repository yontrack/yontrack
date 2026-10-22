import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../login";
import {BranchPage} from "./branch";
import {setPreferences} from "@ontrack/preferences";

/**
 * Runs in the browser: for every validation stamp column of the builds matrix, the horizontal
 * centre of its header and of its cell in the first row of builds.
 */
const measureStampColumns = (matrix) => {
    const centre = cell => {
        const rect = cell.getBoundingClientRect()
        return (rect.left + rect.right) / 2
    }
    const headers = [...matrix.querySelectorAll('thead th.ot-validation-stamp')]
    // Past antd's hidden measure row
    const cells = [...matrix.querySelector('tbody tr:not([aria-hidden])').querySelectorAll('td.ot-validation-stamp')]
    return headers.map((header, index) => ({
        header: centre(header),
        cell: cells[index] ? centre(cells[index]) : undefined,
    }))
}

/**
 * Wide, the table is narrower than the page and gets stretched: the drift of #2108 was there, the
 * stamp columns each getting a fractional share of the stretch. Narrow, the stamps overflow and
 * the table scrolls sideways: it never drifted, and guards that the fix keeps it so.
 */
const viewports = [
    {name: 'wider', width: 2560},
    {name: 'narrower', width: 1280},
]

for (const viewport of viewports) {
    test(`the validation stamp headers stay over their run columns when there are many stamps, on a page ${viewport.name} than the matrix`, async ({page, ontrack}) => {
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        const validationStamps = []
        for (let i = 0; i < 30; i++) {
            validationStamps.push(await branch.createValidationStamp(`vs-${'x'.repeat(i % 7)}-${i}`))
        }
        const build = await branch.createBuild()
        for (const [index, validationStamp] of validationStamps.entries()) {
            // Some runs missing, as on a real branch
            if (index % 4 !== 3) {
                await build.validate(validationStamp, {status: index % 5 === 0 ? 'FAILED' : 'PASSED'})
            }
        }

        // Grouped per status, the stamps have no column of their own
        await setPreferences(ontrack, {branchViewVsGroups: false})

        await page.setViewportSize({width: viewport.width, height: 900})
        await login(page, ontrack)
        const branchPage = new BranchPage(page, branch)
        await branchPage.goTo()

        const matrix = page.getByTestId('branch-builds')
        await expect(matrix.locator('tbody tr:not([aria-hidden])').first()).toBeVisible()

        await expect.poll(async () => {
            const columns = await matrix.evaluate(measureStampColumns)
            return {
                count: columns.length,
                misaligned: columns
                    .map((column, index) => ({
                        column: index + 1,
                        offset: column.cell === undefined ? undefined : column.header - column.cell,
                    }))
                    .filter(({offset}) => offset === undefined || Math.abs(offset) > 1),
            }
        }, {message: 'Every validation stamp header must sit over its run column'}).toEqual({
            count: validationStamps.length,
            misaligned: [],
        })
    })
}
