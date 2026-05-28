import {expect} from "@playwright/test";
import {Table, TableRow} from "../../support/antd-table-support";
import {AutoVersioningConfigDetails} from "./AutoVersioningConfigDetails";

export class AutoVersioningConfigPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async expectOnPage() {
        await expect(this.page.getByText("targetPath")).toBeVisible()
    }

    async displayConfig(projectName) {
        const tableComponent = this.page.getByTestId("auto-versioning-config-table")
        const table = new Table(this.page, tableComponent)

        const row = await table.findRow(async row => {
            const tableRow = new TableRow(this.page, table, row)
            const cell = await tableRow.getCell("sourceProject")
            const link = cell.getByRole('link', {name: projectName, exact: true})
            return await link.isVisible()
        })

        if (row) {
            const details = await row.expand()
            return new AutoVersioningConfigDetails(this.page, details)
        } else {
            throw new Error(`No row found for project ${projectName}`)
        }
    }

}