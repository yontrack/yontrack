import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {BuildPage} from "../../core/builds/BuildPage";

/**
 * A build with a short story: created, validated, promoted.
 */
const trailedBuild = async (ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp()
    const promotionLevel = await branch.createPromotionLevel()
    const build = await branch.createBuild()
    await build.validate(validationStamp, {status: "PASSED"})
    await build.promote(promotionLevel)
    return {build, validationStamp, promotionLevel}
}

/**
 * From the build page to its audit trail, through the "Audit trail" command.
 */
const goToAuditTrail = async (page, build) => {
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()
    await page.getByTestId('build-audit-trail-command').click()
    await expect(page).toHaveURL(new RegExp(`/build/${build.id}/audit-trail$`))
}

test('audit trail of a build whose trail is intact', async ({page, ontrack}) => {
    const {build, validationStamp, promotionLevel} = await trailedBuild(ontrack)

    await login(page, ontrack)
    await goToAuditTrail(page, build)

    // The acceptance stack runs with the development licence and a provisioned instance key
    await expect(page.getByTestId('audit-trail-badge')).toHaveText('Intact')
    await expect(page.getByTestId('audit-trail-problems')).toHaveCount(0)

    // The entries of the story, in order
    const entries = page.getByTestId('audit-trail-entries')
    await expect(entries.getByText('build.created')).toBeVisible()
    await expect(entries.getByText(`Build ${build.name} created`)).toBeVisible()
    await expect(entries.getByText(`Validated ${validationStamp.name} #1: PASSED`)).toBeVisible()
    await expect(entries.getByText(`Promoted to ${promotionLevel.name}`)).toBeVisible()

    // Expanding an entry shows its payload
    await entries.getByRole('button', {name: /expand row/i}).first().click()
    await expect(entries.getByLabel('Payload of seq 1')).toContainText(`"name": "${build.name}"`)

    // The evidence is verified on demand - the build has none
    await page.getByTestId('audit-trail-verify-evidence').click()
    await expect(page.getByTestId('audit-trail-evidence-badge')).toHaveText('Evidence intact')
    await expect(page.getByTestId('audit-trail-badge')).toHaveText('Intact')

    // The JSON export is downloaded as an attachment
    const downloadPromise = page.waitForEvent('download')
    await page.getByTestId('audit-trail-export').click()
    const download = await downloadPromise
    expect(download.suggestedFilename()).toMatch(/^audit-trail-.*\.json$/)
})

test('audit trail of a build whose trail is broken', async ({page, ontrack}) => {
    const {build} = await trailedBuild(ontrack)

    // The UI tests have no access to the database where a row would be tampered with: the
    // verification the server returns is replaced by the one it computes for a payload edited in
    // place (see BuildAuditTrailGraphQLIT), the entries staying the real ones.
    await page.route('**/api/protected/graphql', async (route) => {
        const body = route.request().postDataJSON()
        if (!body?.query?.includes('query BuildAuditTrail(')) {
            return route.continue()
        }
        const response = await route.fetch()
        const json = await response.json()
        json.build.auditTrail.verification = {
            ...json.build.auditTrail.verification,
            chainIntact: false,
            firstBrokenSeq: 2,
            problems: [{seq: 2, type: 'HASH', message: 'The stored hash is not the hash of the entry.'}],
        }
        await route.fulfill({response, json})
    })

    await login(page, ontrack)
    await goToAuditTrail(page, build)

    await expect(page.getByTestId('audit-trail-badge')).toHaveText('Broken at seq 2')
    await expect(page.getByTestId('audit-trail-explanation')).toContainText('The trail was altered')
    const problems = page.getByTestId('audit-trail-problems')
    await expect(problems).toContainText('Seq 2')
    await expect(problems).toContainText('HASH')
    await expect(problems).toContainText('The stored hash is not the hash of the entry.')
    // The entry failing the verification is marked in the table, not by its colour alone
    await expect(page.getByTestId('audit-trail-entries').getByRole('img', {name: 'Fails the verification'})).toHaveCount(1)
})
