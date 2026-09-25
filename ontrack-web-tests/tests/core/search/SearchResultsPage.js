import {expect} from "@playwright/test";

/**
 * The results page, `/search` (#1885).
 */
export class SearchResultsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    /**
     * Opens the page for a URL, as a shared link would.
     */
    async goTo({q, type, page}) {
        const params = new URLSearchParams({q})
        if (type) params.set('type', type)
        if (page) params.set('page', String(page))
        await this.page.goto(`${this.ontrack.connection.ui}/search?${params}`)
        await expect(this.status()).toBeVisible()
    }

    status() {
        return this.page.getByTestId('search-results-count')
    }

    filters() {
        return this.page.getByRole('group', {name: 'Filter by type'})
    }

    /**
     * A filter, by its label: `All (<count>)` or `<type> (<count>)`.
     */
    filter(name) {
        return this.filters().getByRole('button', {name})
    }

    results() {
        return this.page.getByRole('list', {name: 'Search results'}).getByRole('listitem')
    }

    pages() {
        return this.page.getByRole('navigation', {name: 'Pages of results'})
    }

    /**
     * The link to a page of results, in the pagination.
     */
    pageLink(number) {
        return this.pages().getByText(String(number), {exact: true})
    }
}
