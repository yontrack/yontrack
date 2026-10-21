import {login} from "../login";
import {test} from "../../fixtures/connection";
import {expect} from "@playwright/test";
import {generate} from "@ontrack/utils";
import {BranchValidationStampsPage} from "./BranchValidationStampsPage";
import {ValidationStampPage} from "../validationRuns/validationStamp";

const ON_PROMOTION_DATA_TYPE = "net.nemerosa.ontrack.extension.general.validation.OnPromotionValidationDataType"

test('creating a validation on promotion stamp through the form', async ({page, ontrack}) => {
    const dependency = await ontrack.createProject()
    const project = await ontrack.createProject()
    const branch = await project.createBranch()

    await login(page, ontrack)

    const stampsPage = new BranchValidationStampsPage(page, branch)
    await stampsPage.goTo()

    const name = generate('vs_')
    await page.getByRole('button', {name: "Create validation stamp"}).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel("Name").fill(name)
    // Data type
    await dialog.locator('.ant-form-item')
        .filter({has: page.getByText("Data type", {exact: true})})
        .locator('.ant-select')
        .click()
    await page
        .locator('.ant-select-dropdown:visible .ant-select-item-option')
        .filter({hasText: /^Validation on promotion$/})
        .click()
    // Project of the linked build, searched by name
    await dialog.getByTestId('on-promotion-project').click()
    await page.keyboard.type(dependency.name)
    await page
        .locator('.ant-select-dropdown:visible .ant-select-item-option')
        .filter({hasText: new RegExp(`^${dependency.name}$`)})
        .click()
    // Promotion
    await dialog.getByTestId('on-promotion-promotion').fill('SILVER')
    await dialog.getByRole('button', {name: "OK"}).click()
    await expect(dialog).not.toBeVisible()

    // The stamp shows its configuration
    const item = stampsPage.getListItem(name)
    await expect(item).toBeVisible()
    await expect(item.getByTestId('on-promotion-promotion')).toHaveText('SILVER')
    await expect(item.getByRole('link', {name: dependency.name})).toBeVisible()
})

test('seeing the run data on a build validated by the promotion of its dependency', async ({page, ontrack}) => {
    // Dependency, with its SILVER promotion
    const dependency = await ontrack.createProject()
    const dependencyBranch = await dependency.createBranch()
    const silver = await dependencyBranch.createPromotionLevel('SILVER')
    const dependencyBuild = await dependencyBranch.createBuild()
    // Consumer, validated when its dependency reaches SILVER
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp('DEP-SILVER', {
        dataType: ON_PROMOTION_DATA_TYPE,
        dataTypeConfig: {
            project: dependency.name,
            qualifier: '',
            promotion: 'SILVER',
        },
    })
    const build = await branch.createBuild()
    await build.linkTo(dependencyBuild)
    await dependencyBuild.promote(silver)

    await login(page, ontrack)

    const validationStampPage = new ValidationStampPage(page, validationStamp)
    await validationStampPage.goTo()

    const runData = validationStampPage.history().getByTestId('on-promotion-run-data')
    await expect(runData).toBeVisible()
    await expect(runData.locator('a[title="Linked build"]')).toContainText(dependencyBuild.name)
    await expect(runData.locator('a[title="Promotion of the linked build"]')).toHaveText('SILVER')
})
