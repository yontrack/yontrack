import {login} from "../login";
import {test} from "../../fixtures/connection";
import path from "node:path";
import {ValidationStampPage} from "../validationRuns/validationStamp";
import {BranchValidationStampsPage} from "./BranchValidationStampsPage";
import {generate} from "@ontrack/utils";
import {expect} from "@playwright/test";

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
