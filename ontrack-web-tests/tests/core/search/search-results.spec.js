const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {SearchResultsPage} = require("./SearchResultsPage");

/**
 * The results page, `/search` (#1885): filters, pagination, and a URL which can be shared.
 */

/**
 * A text no other document starts with, nor is similar to.
 */
const uniqueToken = () => `srch${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`

/**
 * A project named after the token, with 22 branches: the project and its branches are found by
 * the token - 23 results, of which 22 branches, filling one page and a bit.
 */
const projectWithBranches = async (ontrack) => {
    const token = uniqueToken()
    const project = await ontrack.createProject(token)
    for (let i = 1; i <= 22; i++) {
        await project.createBranch(`b${String(i).padStart(2, '0')}`)
    }
    return {token, project}
}

test('filtering the results on a type', async ({page, ontrack}) => {
    const {token} = await projectWithBranches(ontrack)
    await login(page, ontrack)

    const results = new SearchResultsPage(page, ontrack)
    await results.goTo({q: token})

    await expect(results.filter(/^All \(\d+\)$/)).toHaveAttribute('aria-pressed', 'true')
    await expect(results.filter('Project (1)')).toHaveAttribute('aria-pressed', 'false')

    // Filtering on the branches
    await results.filter('Branch (22)').click()
    await expect(page).toHaveURL(new RegExp(`/search\\?q=${token}&type=branch$`))
    await expect(results.filter('Branch (22)')).toHaveAttribute('aria-pressed', 'true')
    await expect(results.status()).toHaveText('22 results')
    await expect(results.results()).toHaveCount(20)
    await expect(results.results().first()).toContainText('Branch')
    // The counts of the other types stay
    await expect(results.filter('Project (1)')).toBeVisible()

    // Back to all the types
    await page.goBack()
    await expect(page).toHaveURL(new RegExp(`/search\\?q=${token}$`))
    await expect(results.filter(/^All \(\d+\)$/)).toHaveAttribute('aria-pressed', 'true')
})

test('paging through the results', async ({page, ontrack}) => {
    const {token} = await projectWithBranches(ontrack)
    await login(page, ontrack)

    const results = new SearchResultsPage(page, ontrack)
    await results.goTo({q: token, type: 'branch'})
    await expect(results.results()).toHaveCount(20)
    await expect(results.pageLink(1)).toHaveAttribute('aria-current', 'page')

    await results.pageLink(2).click()
    await expect(page).toHaveURL(new RegExp(`/search\\?q=${token}&type=branch&page=2$`))
    await expect(results.results()).toHaveCount(2)
    await expect(results.pageLink(2)).toHaveAttribute('aria-current', 'page')

    // Back to the first page
    await page.goBack()
    await expect(results.results()).toHaveCount(20)
    await expect(results.pageLink(1)).toHaveAttribute('aria-current', 'page')
})

test('a shared URL shows the same results, the query highlighted', async ({page, ontrack}) => {
    const {token, project} = await projectWithBranches(ontrack)
    await login(page, ontrack)

    // Opening the URL of the second page of branches, as a shared link
    const results = new SearchResultsPage(page, ontrack)
    await results.goTo({q: token, type: 'branch', page: 2})

    await expect(results.filter('Branch (22)')).toHaveAttribute('aria-pressed', 'true')
    await expect(results.status()).toHaveText('22 results')
    await expect(results.results()).toHaveCount(2)
    await expect(results.pageLink(2)).toHaveAttribute('aria-current', 'page')
    await expect(page.getByRole('searchbox', {name: 'Search'})).toHaveValue(token)

    // The titles link to the branches, the query highlighted in them
    const first = results.results().first()
    const link = first.getByRole('link', {name: new RegExp(`^${project.name}/b\\d\\d$`)})
    await expect(link).toBeVisible()
    await expect(link.locator('mark')).toHaveText(token)

    await link.click()
    await expect(page).toHaveURL(/\/branch\/\d+$/)
})

test('searching again from the results page', async ({page, ontrack}) => {
    const {token, project} = await projectWithBranches(ontrack)
    await login(page, ontrack)

    const results = new SearchResultsPage(page, ontrack)
    await results.goTo({q: uniqueToken()})
    await expect(results.status()).toHaveText('No results')

    const field = page.getByRole('searchbox', {name: 'Search'})
    await field.fill(`${project.name}/b07`)
    await field.press('Enter')
    await expect(page).toHaveURL(/\/search\?q=/)
    await expect(results.results().first()).toContainText(`${token}/b07`)
})
