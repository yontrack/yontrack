import {expect} from "@playwright/test";
import {antdDescriptionsGetCellByLabel} from "../../support/antd-descriptions";

export class WorkflowInstancePage {

    constructor(page, workflowInstanceId) {
        this.page = page
        this.workflowInstanceId = workflowInstanceId
        this.graphSection = page.getByTestId('workflow-instance-graph')
    }

    async expectToBeVisible() {
        const idField = antdDescriptionsGetCellByLabel(this.page, 'ID')
        await expect(idField).toBeVisible()
        await expect(idField).toHaveText(this.workflowInstanceId)
    }

    async checkStatus(status) {
        const statusField = antdDescriptionsGetCellByLabel(this.page, 'Status')
            .getByTestId("workflow-instance-status")
        await expect(statusField).toBeVisible()
        await expect(statusField).toHaveText(status)
    }

    /**
     * The graph section fills the width of the window, but for the gutters of the page.
     */
    async expectGraphSectionToSpanThePage() {
        await expect(this.graphSection).toBeVisible()
        const {width} = await this.graphSection.boundingBox()
        const viewportWidth = this.page.viewportSize().width
        expect(width).toBeGreaterThan(viewportWidth * 0.95)
    }

    async selectNode(nodeId) {
        await this.page.locator(`.react-flow__node[data-id="${nodeId}"]`).click()
        await expect(this.graphSection.getByRole('button', {name: 'Configuration'})).toBeVisible()
    }

    /**
     * The status is one line of text, however narrow its cell.
     */
    async expectStatusOnOneLine() {
        const {height} = await this.page.getByTestId('workflow-instance-status').boundingBox()
        expect(height).toBeLessThan(30)
    }

    /**
     * All the nodes are in the graph column, which is the first two thirds of the section.
     * Polled, since the graph refits itself after its section has changed size.
     */
    async expectNodesToFitInTheGraphColumn(count) {
        const nodes = this.graphSection.locator('.react-flow__node')
        await expect(nodes).toHaveCount(count)
        await expect.poll(async () => {
            const section = await this.graphSection.boundingBox()
            const columnRight = section.x + section.width * 2 / 3
            const boxes = await Promise.all((await nodes.all()).map(node => node.boundingBox()))
            return boxes.every(box => box.x >= section.x && box.x + box.width <= columnRight)
        }).toBe(true)
    }

    /**
     * No node covers another. Polled, since the nodes are laid out asynchronously.
     */
    async expectNodesNotToOverlap() {
        const nodes = this.graphSection.locator('.react-flow__node')
        await expect.poll(async () => {
            const boxes = await Promise.all((await nodes.all()).map(node => node.boundingBox()))
            return boxes.some((a, i) => boxes.slice(i + 1).some(b =>
                a.x < b.x + b.width && b.x < a.x + a.width &&
                a.y < b.y + b.height && b.y < a.y + a.height
            ))
        }).toBe(false)
    }

    /**
     * Toggles the details displayed in the graph nodes.
     */
    async toggleNodeDetails() {
        await this.graphSection.getByTitle(/^Show (more|less) information$/).click()
    }
}
