import {expect} from "@playwright/test";

export class BuildLinksPage {
    constructor(page) {
        this.page = page
    }

    async expectOnGraphView() {
        // We expect the switch to the opposite view ("tree") to be available
        await expect(this.page.getByTestId("build-links-mode-tree")).toBeVisible()
    }

    async expectOnTreeView() {
        // We expect the switch to the opposite view ("graph") to be available
        await expect(this.page.getByTestId("build-links-mode-graph")).toBeVisible()
    }

    async expectBuildGraphNodeVisible(build) {
        await expect(this.page
            .getByTestId(`ot-build-link-node-${build.id}`)
            .getByRole("link", {name: build.name})
        ).toBeVisible()
    }

    async expectBuildTreeNodeVisible(build) {
        await expect(this.page
            .getByRole('tree')
            .getByRole("link", {name: build.name})
        ).toBeVisible()
    }

    async switchView() {
        await this.page.locator(".ot-build-links-mode-button").click()
    }

    treeViewAlert() {
        return this.page
            .locator(".ant-alert")
            .filter({hasText: "The tree view below displays only downstream dependencies"})
    }

    async expectTreeViewAlert({visible = true}) {
        if (visible) {
            await expect(this.treeViewAlert()).toBeVisible()
        } else {
            // The alert is revealed by an effect which runs just after the component is
            // mounted, so it is not enough to check that it is absent right away: we must
            // leave it the time it would need to appear.
            await this.page.waitForTimeout(1000)
            await expect(this.treeViewAlert()).toHaveCount(0)
        }
    }

    async closeTreeViewAlert() {
        await this.treeViewAlert().locator(".ant-alert-close-icon").click()
    }
}