import {login} from "../login";
import {test} from "../../fixtures/connection";
import path from "node:path";
import {ValidationStampPage} from "../validationRuns/validationStamp";
import {BranchValidationStampsPage} from "./BranchValidationStampsPage";
import {generate} from "@ontrack/utils";
import {expect} from "@playwright/test";
import {CHMLConfigForm} from "./CHMLConfigForm";
import {PredefinedValidationStampsPage} from "../config/PredefinedValidationStampsPage";

test('uploading and getting the image for a validation stamp', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp("helm")

    await login(page, ontrack)

    const validationStampPage = new ValidationStampPage(page, validationStamp)
    await validationStampPage.goTo()

    await validationStampPage.changeImage(path.join(__dirname, 'helm.png'))

    await validationStampPage.checkImage()
})

test('creating a new validation stamp', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()

    await login(page, ontrack)

    const stampsPage = new BranchValidationStampsPage(page, branch)
    await stampsPage.goTo()

    const name = generate('vs_')
    await stampsPage.createValidationStamp({name})

    await stampsPage.checkValidationStampVisible({name})
})

test('deleting a validation stamp', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const vs1 = await branch.createValidationStamp()
    const vs2 = await branch.createValidationStamp()

    await login(page, ontrack)

    const stampsPage = new BranchValidationStampsPage(page, branch)
    await stampsPage.goTo()

    await stampsPage.deleteValidationStamp({name: vs1.name})

    await stampsPage.checkValidationStampNotVisible({name: vs1.name})
    await stampsPage.checkValidationStampVisible({name: vs2.name})
})

test('reordering the validation stamps', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    await branch.createValidationStamp("ALPHA")
    await branch.createValidationStamp("BETA")
    await branch.createValidationStamp("GAMMA")

    await login(page, ontrack)

    const stampsPage = new BranchValidationStampsPage(page, branch)
    await stampsPage.goTo()

    await stampsPage.waitForOrder(["ALPHA", "BETA", "GAMMA"])

    // Drag GAMMA (index 2) to ALPHA (index 0) → expected order: [GAMMA, ALPHA, BETA]
    await stampsPage.dragToReorder("GAMMA", "ALPHA")

    await stampsPage.waitForOrder(["GAMMA", "ALPHA", "BETA"])
})

const CHML_DATA_TYPE = "net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType"

/**
 * Creates a CHML validation stamp
 *
 * @param branch Parent branch
 * @param config CHML configuration, in its form shape: `failedLevel`, `failedValue`, `warningLevel`, `warningValue`
 */
const chmlStamp = async (branch, config) =>
    branch.createValidationStamp(undefined, {
        dataType: CHML_DATA_TYPE,
        dataTypeConfig: config,
    })

test('editing a CHML validation stamp shows its levels and saves it unchanged', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await chmlStamp(branch, {failedLevel: "CRITICAL", failedValue: 1, warningLevel: "HIGH", warningValue: 2})
    const {config} = await validationStamp.getDataType()

    await login(page, ontrack)

    const validationStampPage = new ValidationStampPage(page, validationStamp)
    await validationStampPage.goTo()

    const dialog = await validationStampPage.openUpdateDialog()
    await new CHMLConfigForm(page, dialog).checkConfig({
        failedLevel: "Critical",
        failedValue: 1,
        warningLevel: "High",
        warningValue: 2,
    })

    await dialog.getByRole('button', {name: "OK"}).click()
    await expect(dialog).not.toBeVisible()

    expect((await validationStamp.getDataType()).config).toEqual(config)
})

test('editing the levels of a CHML validation stamp', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await chmlStamp(branch, {failedLevel: "CRITICAL", failedValue: 1, warningLevel: "HIGH", warningValue: 2})
    // A second stamp with different levels, opened after the first one
    const otherStamp = await chmlStamp(branch, {failedLevel: "HIGH", failedValue: 5, warningLevel: "LOW", warningValue: 10})

    await login(page, ontrack)

    const validationStampPage = new ValidationStampPage(page, validationStamp)
    await validationStampPage.goTo()

    const dialog = await validationStampPage.openUpdateDialog()
    await new CHMLConfigForm(page, dialog).setWarning("Medium", 3)
    await dialog.getByRole('button', {name: "OK"}).click()
    await expect(dialog).not.toBeVisible()

    expect((await validationStamp.getDataType()).formConfig).toEqual({
        failedLevel: "CRITICAL",
        failedValue: 1,
        warningLevel: "MEDIUM",
        warningValue: 3,
        warningPassesAutoPromotion: false,
    })

    // Reopening the dialog shows the saved values
    const reopened = await validationStampPage.openUpdateDialog()
    await new CHMLConfigForm(page, reopened).checkConfig({
        failedLevel: "Critical",
        failedValue: 1,
        warningLevel: "Medium",
        warningValue: 3,
    })
    await reopened.getByRole('button', {name: "Cancel"}).click()

    // The other stamp shows its own values
    const otherPage = new ValidationStampPage(page, otherStamp)
    await otherPage.goTo()
    const otherDialog = await otherPage.openUpdateDialog()
    await new CHMLConfigForm(page, otherDialog).checkConfig({
        failedLevel: "High",
        failedValue: 5,
        warningLevel: "Low",
        warningValue: 10,
    })
})

test('editing a CHML predefined validation stamp shows its levels and saves it unchanged', async ({page, ontrack}) => {
    const pvs = await ontrack.createPredefinedValidationStamp({
        dataType: CHML_DATA_TYPE,
        dataTypeConfig: {failedLevel: "CRITICAL", failedValue: 1, warningLevel: "HIGH", warningValue: 2},
    })
    const {config} = await pvs.getDataType()

    await login(page, ontrack)

    const pvsPage = new PredefinedValidationStampsPage(page, ontrack)
    await pvsPage.goTo()

    const dialog = await pvsPage.openUpdateDialog(pvs.name)
    await new CHMLConfigForm(page, dialog).checkConfig({
        failedLevel: "Critical",
        failedValue: 1,
        warningLevel: "High",
        warningValue: 2,
    })

    await dialog.getByRole('button', {name: "OK"}).click()
    await expect(dialog).not.toBeVisible()

    expect((await pvs.getDataType()).config).toEqual(config)
})
