import {searchResultsParams, searchResultsRoute} from "@components/search/results/searchResultsParams"

describe('searchResultsParams', () => {

    it('reads the query, the type and the page', () => {
        expect(searchResultsParams({q: 'billing', type: 'build', page: '3'}))
            .toEqual({q: 'billing', type: 'build', page: 3})
    })

    it('defaults to all types and the first page', () => {
        expect(searchResultsParams({q: 'billing'}))
            .toEqual({q: 'billing', type: null, page: 1})
    })

    it('has no query when there is none', () => {
        expect(searchResultsParams({})).toEqual({q: '', type: null, page: 1})
    })

    it('ignores an empty type', () => {
        expect(searchResultsParams({q: 'x', type: ''}).type).toBeNull()
    })

    it('takes the first value of a repeated parameter', () => {
        expect(searchResultsParams({q: ['a', 'b'], type: ['build', 'branch'], page: ['2', '5']}))
            .toEqual({q: 'a', type: 'build', page: 2})
    })

    it('falls back to the first page for a page which is not a positive number', () => {
        expect(searchResultsParams({q: 'x', page: 'abc'}).page).toBe(1)
        expect(searchResultsParams({q: 'x', page: '0'}).page).toBe(1)
        expect(searchResultsParams({q: 'x', page: '-2'}).page).toBe(1)
        expect(searchResultsParams({q: 'x', page: '2.5'}).page).toBe(1)
    })

})

describe('searchResultsRoute', () => {

    it('keeps only the query for all types on the first page', () => {
        expect(searchResultsRoute({q: 'billing', type: null, page: 1}))
            .toEqual({pathname: '/search', query: {q: 'billing'}})
    })

    it('adds the type and the page when set', () => {
        expect(searchResultsRoute({q: 'billing', type: 'build', page: 2}))
            .toEqual({pathname: '/search', query: {q: 'billing', type: 'build', page: '2'}})
    })

})
