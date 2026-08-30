import {expect} from "@playwright/test";
import {confirmBox} from "../../support/confirm";
import {AutoVersioningTrail} from "../../extensions/auto-versioning/AutoVersioningTrail";
import {NotificationsTable} from "../../extensions/notifications/NotificationsTable";
import {WorkflowInstanceCard} from "../../extensions/workflows/WorkflowInstanceCard";

export class PromotionRunPage {

    constructor(page, promotionRun) {
        this.page = page
        this.promotionRun = promotionRun
    }

    async goTo() {
        await this.page.goto(`${this.promotionRun.ontrack.connection.ui}/promotionRun/${this.promotionRun.id}`)
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByTestId('promotion-run-summary')).toBeVisible()
    }

    /**
     * The auto-versioning trail and the notifications live in panels which are collapsed by
     * default; antd does not mount their content until they are expanded.
     */
    async expandPanel(name) {
        const header = this.page.getByRole('button', {name: new RegExp(name)})
        await expect(header).toBeVisible()
        if (await header.getAttribute('aria-expanded') !== 'true') {
            await header.click()
        }
    }

    async assertNotificationPresent(text) {
        await this.expandPanel("Notifications")
        await expect(
            this.page.locator("#promotion-run-notifications")
                .getByText(text, {exact: true})
        ).toBeVisible()
    }

    async deletePromotionRun() {
        const button = this.page.getByRole('button', {name: 'Delete'})
        await expect(button).toBeVisible()
        await button.click()
        await confirmBox(this.page, "Removing this promotion run", {okText: "Confirm deletion"})
    }

    async getAVTrail() {
        await this.expandPanel("Auto-versioning trail")
        const section = this.page.getByTestId('auto-versioning-trail')
        await expect(section).toBeVisible()
        return new AutoVersioningTrail(this.page, section)
    }

    async getNotificationsTable() {
        await this.expandPanel("Notifications")
        const table = this.page.getByTestId('promotion-run-notifications').getByTestId('notification-recordings-table')
        await expect(table).toBeVisible()
        return new NotificationsTable(this.page, table)
    }

    /**
     * The Workflows section, which is the primary content of the page.
     */
    async expectWorkflowsSection() {
        await expect(this.page.getByTestId('promotion-run-workflows')).toBeVisible()
    }

    async expectNoWorkflow() {
        await expect(
            this.page.getByText("No workflow was triggered by this promotion.")
        ).toBeVisible()
    }

    workflowCardLocator(workflowName) {
        return this.page
            .getByTestId('promotion-run-workflows')
            .locator('[data-testid^="workflow-instance-card-"]')
            .filter({hasText: workflowName})
    }

    /**
     * Waits for a workflow card carrying the given workflow name and returns it.
     *
     * A workflow is launched asynchronously by the notification of the promotion event, and the
     * page does not auto-refresh unless the user asks it to, so the page is reloaded until the card
     * shows up — and, when a `status` is given, until it reaches that status.
     */
    async getWorkflowCard(workflowName, {status, timeout = 60_000} = {}) {
        const deadline = Date.now() + timeout
        // eslint-disable-next-line no-constant-condition
        while (true) {
            const card = this.workflowCardLocator(workflowName)
            // `count()` rather than `isVisible()`: it neither waits nor trips strict mode, which
            // keeps the polling here and not inside the locator.
            const ready = await card.count() > 0 &&
                (!status || await card.getByText(status, {exact: true}).count() > 0)
            if (ready) {
                return new WorkflowInstanceCard(this.page, card)
            }
            if (Date.now() > deadline) {
                throw new Error(
                    `No workflow card for "${workflowName}"${status ? ` with status "${status}"` : ''} after ${timeout} ms`
                )
            }
            await this.page.waitForTimeout(1000)
            await this.page.reload()
            await this.expectOnPage()
        }
    }

}
