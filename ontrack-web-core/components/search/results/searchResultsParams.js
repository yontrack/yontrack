/**
 * Number of results per page of the results page.
 */
export const PAGE_SIZE = 20

const first = (value) => Array.isArray(value) ? value[0] : value

/**
 * What the results page shows, read from its URL - the one source of truth for it, so that a page
 * of results can be shared, and the back and forward buttons move between the searches.
 *
 * - `q` - the text searched for
 * - `type` - the ID of the only type of results to show, `null` for all of them
 * - `page` - the page of results, from 1
 */
export function searchResultsParams(query = {}) {
    const q = first(query.q) ?? ''
    const type = first(query.type) || null
    const pageText = first(query.page)
    const page = /^\d+$/.test(pageText ?? '') ? Number(pageText) : 1
    return {
        q,
        type,
        page: page >= 1 ? page : 1,
    }
}

/**
 * The route of the results page showing the given query, type and page. The defaults - all types,
 * the first page - are left out of the URL.
 */
export function searchResultsRoute({q, type = null, page = 1}) {
    const query = {q}
    if (type) {
        query.type = type
    }
    if (page > 1) {
        query.page = String(page)
    }
    return {pathname: '/search', query}
}
