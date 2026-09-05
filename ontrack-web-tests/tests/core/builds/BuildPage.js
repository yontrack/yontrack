import {BuildLinksPage} from "./buildLinks";
import {PromotionInfoSection} from "./PromotionInfoSection";
import {confirmBox} from "../../support/confirm";
import {BuildLinksSection} from "./BuildLinksSection";

const {expect} = require("@playwright/test");

export class BuildPage {

    constructor(page, build) {
        this.page = page
        this.build = build
    }

    async goTo() {
        await this.page.goto(`${this.build.ontrack.connection.ui}/build/${this.build.id}`)
        await this.checkOnBuildPage()
    }

    /**
     * The gate almost every build-page spec goes through.
     *
     * Each section is asserted through its own `data-testid`, not by a bare `getByText`: an
     * unscoped string locator matches by SUBSTRING over the whole page, so it asserts "this word is
     * somewhere in the DOM" rather than "this section is on screen". A branch page carries both a
     * "Promotions" command link and, in the pipeline view, an `inspector-promotions` panel, which
     * made the old locator resolve to two elements and fail in strict mode (issue #1692).
     *
     * The failure was not the transient overlap it looked like. `expect` RETRIES past a strict-mode
     * violation, so a branch page still in the tree while the build page loads heals itself - that
     * was reproduced here by holding back the build page's chunk, and the assertion still passed.
     * Both matches surviving the full 30s means the browser never left the branch page at all, and
     * the unscoped locator reported that as an ambiguity instead of as "the build page is not
     * here". Scoping does not paper over the navigation; it makes the failure say the true thing.
     *
     * Asserting the title INSIDE the section also waits out that section's own "Loading..." head,
     * so a pass means the section is there and loaded.
     */
    async checkOnBuildPage() {
        await this.checkSection('promotions', "Promotions")
        await this.checkSection('validations', "Validations")
        await this.checkSection('links-using', "Downstream links")
        await this.checkSection('links-usedby', "Upstream links")
        await this.assertName(this.build.name)
    }

    /**
     * One of the build page's sections, addressed by the `id` its `GridCell` publishes as a
     * `data-testid`, and carrying `title` in its head.
     */
    async checkSection(id, title) {
        await expect(this.page.getByTestId(id).getByText(title)).toBeVisible()
    }

    async goToLinks() {
        await this.page.getByRole("button", {name: "Links"}).click()
        return new BuildLinksPage(this.page)
    }

    async getPromotionInfoSection() {
        const section = this.page.getByTestId('promotions')
        await expect(section).toBeVisible()
        return new PromotionInfoSection(this.page, section, this.build)
    }

    async deleteBuild() {
        const button = this.page.getByRole('button', {name: 'Delete build'})
        await expect(button).toBeVisible()
        await button.click()
        await confirmBox(this.page, "Delete build", {okText: "Delete"})
    }

    async update({name, description}) {
        const button = this.page.getByRole('button', {name: 'Edit'})
        await expect(button).toBeVisible()
        await button.click()

        const buildNameField = this.page.getByPlaceholder('Build name');
        await expect(buildNameField).toBeVisible()
        await buildNameField.fill(name)
        if (description) await this.page.getByPlaceholder('Build description').fill(description)
        await this.page.getByRole('button', {name: 'OK'}).click()
    }

    async assertName(name) {
        await expect(this.page.getByText(name).nth(0)).toBeVisible()
    }

    async assertDescription(description) {
        await expect(this.page.getByText(description)).toBeVisible()
    }

    async expectPreviousBuild({visible = true}) {
        await expect(this.page.getByRole('button', {name: 'Previous build'})).toBeVisible({visible})
    }

    async expectNextBuild({visible = true}) {
        await expect(this.page.getByRole('button', {name: 'Next build'})).toBeVisible({visible})
    }

    async nextBuild() {
        await this.page.getByRole('button', {name: 'Next build'}).click()
    }

    async previousBuild() {
        await this.page.getByRole('button', {name: 'Previous build'}).click()
    }

    async getDownstreamLinks() {
        const downstreamLinks =  this.page.getByTestId('links-using')
        await expect(downstreamLinks).toBeVisible()
        return new BuildLinksSection(this.page, this.build, downstreamLinks)
    }

    async getUpstreamLinks() {
        const upstreamLinks =  this.page.getByTestId('links-usedby')
        await expect(upstreamLinks).toBeVisible()
        return new BuildLinksSection(this.page, this.build, upstreamLinks)
    }

}