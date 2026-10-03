import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";

/**
 * A validation run without any evidence.
 */
const validationRun = async (ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp()
    const build = await branch.createBuild()
    return await build.validate(validationStamp, {status: "PASSED"})
}

const goToValidationRun = async (page, ontrack, run) => {
    await page.goto(`${ontrack.connection.ui}/validationRun/${run.id}`)
}

/**
 * Uploads a file through the upload dialog of the Evidence cell.
 */
const upload = async (page, {name, mimeType, content, sourceTool}) => {
    await page.getByTestId('validation-run-evidence-upload').click()
    const dialog = page.getByRole('dialog', {name: 'Upload evidence'})
    await dialog.locator('input[type="file"]').setInputFiles({name, mimeType, buffer: Buffer.from(content)})
    if (sourceTool) {
        await dialog.getByLabel('Source tool').fill(sourceTool)
    }
    await dialog.getByRole('button', {name: 'Upload'}).click()
    await expect(dialog).toBeHidden()
}

test('evidence of a validation run: upload, preview, download, delete', async ({page, ontrack}) => {
    const run = await validationRun(ontrack)

    await login(page, ontrack)
    await goToValidationRun(page, ontrack, run)

    // The acceptance stack runs with the development licence and its evidence storage: the cell is
    // shown to the administrator, who may upload
    const evidence = page.getByTestId('validation-run-evidence')
    await expect(evidence).toContainText('No evidence attached to this validation run')

    // Upload of a text, with markup which must never be interpreted
    const text = '3 tests passed <b>not bold</b>'
    await upload(page, {name: 'summary.txt', mimeType: 'text/plain', content: text, sourceTool: 'pytest'})
    await expect(evidence.getByText('summary.txt')).toBeVisible()
    await expect(evidence.getByText('text/plain')).toBeVisible()
    await expect(evidence.getByText('pytest')).toBeVisible()

    // Upload of an HTML page, which is never previewed
    await upload(page, {name: 'report.html', mimeType: 'text/html', content: '<html><script>alert(1)</script></html>'})
    await expect(evidence.getByText('report.html')).toBeVisible()
    await expect(evidence.getByRole('button', {name: 'Preview report.html'})).toHaveCount(0)

    // Preview of the text, as text
    await evidence.getByRole('button', {name: 'Preview summary.txt'}).click()
    const preview = page.getByRole('dialog', {name: 'Preview of summary.txt'})
    await expect(preview.getByTestId('evidence-preview-text')).toHaveText(text)
    await expect(preview.locator('b')).toHaveCount(0)
    // The Close button of the footer, not the cross of the modal
    await preview.locator('.ant-modal-footer').getByRole('button', {name: 'Close'}).click()
    await expect(preview).toBeHidden()

    // Download of the text
    const textDownloadPromise = page.waitForEvent('download')
    await evidence.getByRole('link', {name: 'Download summary.txt'}).click()
    const textDownload = await textDownloadPromise
    expect(textDownload.suggestedFilename()).toBe('summary.txt')

    // Download of the HTML page: served as an attachment, with the headers which keep it from
    // being rendered
    const htmlHref = await evidence.getByRole('link', {name: 'Download report.html'}).getAttribute('href')
    const htmlResponse = await page.request.get(`${ontrack.connection.ui}${htmlHref}`)
    expect(htmlResponse.status()).toBe(200)
    expect(htmlResponse.headers()['content-type']).toBe('application/octet-stream')
    expect(htmlResponse.headers()['content-disposition']).toMatch(/^attachment;/)
    expect(htmlResponse.headers()['x-content-type-options']).toBe('nosniff')
    expect(htmlResponse.headers()['content-security-policy']).toContain('sandbox')

    // Deletion: the evidence stays listed, marked as deleted
    await evidence.getByRole('button', {name: 'Delete summary.txt'}).click()
    await page.getByRole('button', {name: 'Delete', exact: true}).click()
    await expect(evidence.getByText('Deleted')).toBeVisible()
    await expect(evidence.getByRole('link', {name: 'Download summary.txt'})).toHaveCount(0)
    await expect(evidence.getByRole('link', {name: 'Download report.html'})).toBeVisible()
})

test('evidence of a validation run while the evidence storage is not configured', async ({page, ontrack}) => {
    const run = await validationRun(ontrack)

    // The UI tests cannot unconfigure the storage of the stack: the state the server returns is
    // replaced in the response
    await page.route('**/api/protected/graphql', async (route) => {
        const body = route.request().postDataJSON()
        if (!body?.query?.includes('query ValidationRunEvidence(')) {
            return route.continue()
        }
        const response = await route.fetch()
        const json = await response.json()
        json.auditTrailStorageState = 'NOT_CONFIGURED'
        await route.fulfill({response, json})
    })

    await login(page, ontrack)
    await goToValidationRun(page, ontrack, run)

    await expect(page.getByTestId('validation-run-evidence-storage')).toContainText('Evidence storage is not configured.')
    await expect(page.getByTestId('validation-run-evidence-upload')).toHaveCount(0)
    await expect(page.getByTestId('validation-run-evidence')).toHaveCount(0)
})
