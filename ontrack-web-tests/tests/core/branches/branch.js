const {expect} = require("@playwright/test");
const {ValidationRunHistoryDialog} = require("../validationRuns/ValidationRunHistoryDialog");
const {SCMChangeLogPage} = require("../../extensions/scm/scm");
const {PromotionsPage} = require("../promotionLevels/PromotionsPage");
const {confirmBox} = require("../../support/confirm");
const {BranchLinksPage} = require("./BranchLinksPage");
const {AutoVersioningConfigPage} = require("../../extensions/auto-versioning/AutoVersioningConfigPage");

class BranchPage {
    constructor(page, branch) {
        this.page = page;
        this.branch = branch;
        this.changeLogButton = this.page.getByRole('button', {name: 'Change log', exact: true})
    }

    async checkOnPage() {
        await expect(this.page.getByText(this.branch.name)).toBeVisible()
        // Loading finished
        await expect(this.page.getByTestId('loading-builds')).toBeHidden()
    }

    async goTo() {
        await this.page.goto(`${this.branch.ontrack.connection.ui}/branch/${this.branch.id}`)
        await this.checkOnPage()
    }

    async checkChangeLogButtonPresent({disabled}) {
        await expect(this.changeLogButton).toBeVisible()
        if (disabled) {
            await expect(this.changeLogButton).toBeDisabled()
        } else {
            await expect(this.changeLogButton).toBeEnabled()
        }
    }

    async selectBuild({id}) {
        await this.page.locator(`#range-${id}`).click()
    }

    async goToChangeLog() {
        await this.changeLogButton.click()
        const changeLogPage = new SCMChangeLogPage(this.page)
        await changeLogPage.checkDisplayed()
        return changeLogPage
    }

    async validationRunHistory(run) {
        const {build, validationStamp} = run
        const cell = this.page.getByTestId(`${build.id}-${validationStamp.id}`);
        await expect(cell).toBeVisible()
        await cell.click()
        const dialog = new ValidationRunHistoryDialog(this.page, run)
        await dialog.waitFor()
        return dialog
    }

    async checkNoDisabledBanner() {
        await expect(this.getDisabledBanner()).not.toBeVisible()
    }

    async checkDisabledBanner() {
        await expect(this.getDisabledBanner()).toBeVisible()
    }

    async disableBranch() {
        await this.page.getByRole('button', {name: "Disable branch"}).click()
    }

    async enableBranch() {
        await this.page.getByRole('button', {name: "Enable branch"}).click()
    }

    getDisabledBanner() {
        return this.page.getByTestId("banner-disabled")
    }

    async navigateToPromotions() {
        const promotionsButton = this.page.getByRole('button', {name: "Promotions", exact: true})
        await expect(promotionsButton).toBeVisible()
        await promotionsButton.click()
        const promotionsPage = new PromotionsPage(this.page, this.branch)
        await promotionsPage.checkOnPage()
        return promotionsPage
    }

    async hoverPromotionRun(promotionLevel) {
        const trigger = this.page.locator(`.promotion-run-pl-${promotionLevel.id}`).first()
        await expect(trigger).toBeVisible()
        await trigger.hover()
    }

    async deleteBranch() {
        const button = this.page.getByRole('button', {name: 'Delete branch'})
        await expect(button).toBeVisible()
        await button.click()
        await confirmBox(this.page, "Delete branch", {okText: "Delete"})
    }

    async displayBranchLinks() {
        const button = this.page.getByRole('button', {name: 'Links', exact: true})
        await expect(button).toBeVisible()
        await button.click()
        const branchLinksPage = new BranchLinksPage(this.page, this.branch.ontrack, this.branch)
        await branchLinksPage.expectOnPage()
        return branchLinksPage
    }

    async navigateToAutoVersioningConfig() {
        const button = this.page.getByTestId('branch-info')
        await expect(button).toBeVisible()
        await button.click()

        const avInfoSection = this.page.getByTestId('av.config.AutoVersioningConfigInformationExtension')
        await expect(avInfoSection.getByText("Auto-versioning")).toBeVisible()
        const avConfigLink = avInfoSection.getByRole('link', {name: 'Configuration'})
        await expect(avConfigLink).toBeVisible()
        await avConfigLink.click()

        const avConfigPage = new AutoVersioningConfigPage(this.page, this.branch.ontrack)
        await avConfigPage.expectOnPage()
        return avConfigPage
    }
}


module.exports = {BranchPage}
