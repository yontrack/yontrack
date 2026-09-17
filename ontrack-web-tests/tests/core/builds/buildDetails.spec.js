import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../login";
import {BuildPage} from "./BuildPage";

const RELEASE = 'general.ReleasePropertyType'

test('the build details are opened from a header command, not from a floating button', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    await expect(buildPage.detailsCommand()).toHaveText('Details')
    await expect(page.locator('.ant-float-btn')).toHaveCount(0)

    await buildPage.openDetails()
    await expect(page.getByText('No properties set yet. Use + to add one.')).toBeVisible()
})

test('the dot on the build details reflects whether a property is set', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    // Nothing set, no dot
    await expect(buildPage.detailsCommand()).toBeVisible()
    await expect(buildPage.detailsDot()).toHaveCount(0)

    // Property set, dot visible
    await build.setRelease('1.0.0')
    await buildPage.goTo()
    await expect(buildPage.detailsDot()).toBeVisible()

    // Property deleted from the drawer, dot gone without reloading the page
    const properties = await buildPage.openDetails()
    await properties.deleteProperty(RELEASE)
    await expect(buildPage.detailsDot()).toHaveCount(0)
})
