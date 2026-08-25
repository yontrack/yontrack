import {expect} from "@playwright/test";
import {AbstractImagePage} from "../common/AbstractImagePage";
import {AutoVersioningTrail} from "../../extensions/auto-versioning/AutoVersioningTrail";
import {PropertiesSection} from "../properties/PropertiesSection";

export class PromotionLevelPage extends AbstractImagePage {

    constructor(page, promotionLevel) {
        super(page)
        this.promotionLevel = promotionLevel
    }

    id() {
        return `promotion-level-image-${this.promotionLevel.id}`
    }

    async goTo() {
        await this.page.goto(`${this.promotionLevel.ontrack.connection.ui}/promotionLevel/${this.promotionLevel.id}`)
        await expect(this.page.getByText(this.promotionLevel.name)).toBeVisible()
    }

    /**
     * Opens the information drawer and returns its properties section.
     */
    async openProperties() {
        const button = this.page.getByTestId('promotion-level-info')
        await expect(button).toBeVisible()
        await button.click()
        return new PropertiesSection(this.page)
    }

    async getAVTrail() {
        const section = this.page.getByTestId('auto-versioning-trail')
        await expect(section).toBeVisible()
        return new AutoVersioningTrail(this.page, section)
    }

}