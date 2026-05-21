import {expect} from "@playwright/test";
import {antdDescriptionsGetCellByLabel} from "../../support/antd-descriptions";

export class AutoVersioningConfigDetails {

    constructor(page, details) {
        this.page = page
        this.details = details
    }

    async expectCronSchedule(cronSchedule) {
        const cell = this.details.getByTestId('auto-versioning-schedule')
        await expect(cell).toHaveText(cronSchedule)
    }

    async expectGitHubPostProcessing({workflow, dockerImage, dockerCommand, commitMessage}) {
        const ppSection = this.details.getByTestId('av-post-processing-github')
        await expect(ppSection.getByText('github', {exact: true}).first()).toBeVisible()
        await expect(antdDescriptionsGetCellByLabel(ppSection, "Specific GitHub workflow job")).toHaveText(workflow)
        await expect(antdDescriptionsGetCellByLabel(ppSection, "Docker image")).toHaveText(dockerImage)
        await expect(antdDescriptionsGetCellByLabel(ppSection, "Docker command")).toHaveText(dockerCommand)
        await expect(antdDescriptionsGetCellByLabel(ppSection, "Commit message")).toHaveText(commitMessage)
    }

}