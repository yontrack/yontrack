import {login} from "../login";
import {test} from "../../fixtures/connection";
import {BuildPage} from "../builds/BuildPage";
import {BranchPage} from "../branches/branch";
import {expect} from "@playwright/test";

test('promotion run creation fills promotion level fields', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const pl = await branch.createPromotionLevel('GOLD')
    await pl.setFields([
        {name: 'ticket', displayName: 'Ticket', type: 'TEXT', required: false},
        {name: 'status', displayName: 'Status', type: 'CHOICE', required: false, options: ['Open', 'Closed']},
    ])
    const build = await branch.createBuild()

    await login(page, ontrack)

    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const promotionsSection = await buildPage.getPromotionInfoSection()
    await promotionsSection.promote(pl, {
        textValues: {Ticket: 'MYTICKET-42'},
        selectValues: {Status: 'Open'},
    })

    await promotionsSection.checkPromotionRunCount(pl, 1)
})

test('promotion run field values are visible in the build page popover', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const pl = await branch.createPromotionLevel('SILVER')
    await pl.setFields([
        {name: 'ticket', displayName: 'Ticket', type: 'TEXT', required: false},
    ])
    const build = await branch.createBuild()
    await build.promoteWithFieldValues(pl, [{name: 'ticket', value: 'FIELD-123'}])

    await login(page, ontrack)

    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const promotionsSection = await buildPage.getPromotionInfoSection()
    await promotionsSection.checkPromotionRunCount(pl, 1)
    await promotionsSection.hoverPromotionRun(pl)

    await expect(page.getByText('FIELD-123')).toBeVisible()
})

test('promotion run field values are visible in the branch page popover', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const pl = await branch.createPromotionLevel('SILVER')
    await pl.setFields([
        {name: 'ticket', displayName: 'Ticket', type: 'TEXT', required: false},
    ])
    const build = await branch.createBuild()
    await build.promoteWithFieldValues(pl, [{name: 'ticket', value: 'FIELD-456'}])

    await login(page, ontrack)

    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await branchPage.hoverPromotionRun(pl)

    await expect(page.getByText('FIELD-456')).toBeVisible()
})
