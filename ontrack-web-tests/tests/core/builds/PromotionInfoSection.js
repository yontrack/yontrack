import {expect} from "@playwright/test";
import {PromotionRunDialog} from "../promotionRuns/PromotionRunDialog";
import {confirmBox} from "../../support/confirm";

export class PromotionInfoSection {

    constructor(page, section, build) {
        this.page = page
        this.section = section
        this.build = build
    }

    async promote(pl, fieldValues = {}) {
        const promoteButton = this.section.getByTestId(`build-promote-${this.build.id}-${pl.id}`)
        await expect(promoteButton).toBeVisible()
        await promoteButton.click()
        const dialog = new PromotionRunDialog(this.page)
        await dialog.createPromotionRun(fieldValues)
    }

    async repromote(run) {
        const runLink = this.section.getByTestId(`build-promote-${this.build.id}-${run.promotionLevel.id}`)
        await expect(runLink).toBeVisible()
        await runLink.click()
        const dialog = new PromotionRunDialog(this.page)
        await dialog.createPromotionRun()
    }

    async checkPromotionRunCount(promotionLevel, expectedCount) {
        const links = this.section.locator(`.promotion-run-pl-${promotionLevel.id}`)
        await expect(links).toHaveCount(expectedCount)
    }

    async deletePromotionRun(run) {
        const deleteButton = this.section.getByTestId(`build-promotion-delete-${run.id}`)
        await expect(deleteButton).toBeVisible()
        await deleteButton.click()
        await confirmBox(this.page, "Deleting a promotion", {okText: "Confirm deletion"})
    }

    async hoverPromotionRun(promotionLevel) {
        const trigger = this.section.getByTestId(`build-promotion-run-trigger-${promotionLevel.id}`).first()
        await expect(trigger).toBeVisible()
        await trigger.hover()
    }

}