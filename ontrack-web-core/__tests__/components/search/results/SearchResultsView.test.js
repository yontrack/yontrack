import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"

// Moving to another page scrolls back to the top
window.scrollTo = jest.fn()

// antd's Pagination follows the breakpoints
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
})

/** What the search query answers with, in the shape the server sends it. */
let searchAnswer = null
/** The query and options the page handed `useQuery` on its last render. */
let searchQuery
let searchOptions

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (query, options) => {
        searchQuery = query
        searchOptions = options
        const active = options.condition !== false
        return {
            data: active && searchAnswer ? (options.dataFn ?? (it => it))(searchAnswer) : null,
            loading: false,
            error: null,
            finished: active,
        }
    },
    callGraphQL: jest.fn(),
}))

const mockRouter = {
    isReady: true,
    query: {},
    push: jest.fn(),
}
jest.mock("next/router", () => ({
    useRouter: () => mockRouter,
}))

jest.mock("../../../../components/layouts/MainPage", () => function MainPage({title, children}) {
    return (
        <div>
            <h1>{title}</h1>
            {children}
        </div>
    )
})

jest.mock("../../../../components/common/Dynamic", () => ({
    Dynamic: ({path}) => <span data-testid={`dynamic-${path}`}/>,
}))

jest.mock("../../../../components/providers/RefDataProvider", () => ({
    useRefData: () => ({
        searchResultTypes: [
            {id: 'project', name: 'Project', description: ''},
            {id: 'branch', name: 'Branch', description: ''},
            {id: 'build', name: 'Build', description: ''},
            {id: 'scm-commit', name: 'SCM Commit', description: ''},
        ],
    }),
}))

import SearchResultsView from "@components/search/results/SearchResultsView"

const type = (id, name) => ({id, name, description: ''})
const PROJECT = type('project', 'Project')
const BRANCH = type('branch', 'Branch')
const BUILD = type('build', 'Build')

const project = (name, id = 1) => ({
    type: PROJECT,
    title: name,
    description: '',
    data: {project: {id, name}},
    highlight: null,
})

const branch = (projectName, name, id = 10) => ({
    type: BRANCH,
    title: `${projectName}/${name}`,
    description: '',
    data: {branch: {id, name, project: {id: 1, name: projectName}}},
    highlight: null,
})

const answer = ({total, facets, items = [], message = null, all = null}) => ({
    all,
    page: {total, message, facets, items},
})

const renderView = (query) => {
    mockRouter.query = query
    return render(<SearchResultsView/>)
}

const lastPush = () => mockRouter.push.mock.calls[mockRouter.push.mock.calls.length - 1][0]

beforeEach(() => {
    searchAnswer = null
    searchQuery = undefined
    searchOptions = undefined
    mockRouter.isReady = true
    mockRouter.query = {}
    mockRouter.push.mockReset()
})

describe('the URL drives the search', () => {

    it('searches for the query of the URL, in all types, on the first page', () => {
        renderView({q: 'billing'})
        expect(searchOptions.condition).toBe(true)
        expect(searchOptions.variables).toEqual({
            query: 'billing',
            types: null,
            offset: 0,
            size: 20,
            filtered: false,
        })
    })

    it('searches in the type of the URL, and for the facets of all the types', () => {
        renderView({q: 'billing', type: 'build'})
        expect(searchOptions.variables).toMatchObject({types: ['build'], filtered: true})
    })

    it('searches for the page of the URL', () => {
        renderView({q: 'billing', page: '3'})
        expect(searchOptions.variables).toMatchObject({offset: 40, size: 20})
    })

    it('searches again when the URL changes, as with the back button', () => {
        const {rerender} = renderView({q: 'billing', page: '2'})
        mockRouter.query = {q: 'billing'}
        rerender(<SearchResultsView/>)
        expect(searchOptions.variables).toMatchObject({offset: 0})
        expect(searchOptions.deps).toEqual(['billing', null, 1])
    })

    it('does not search before the router knows the URL', () => {
        mockRouter.isReady = false
        renderView({})
        expect(searchOptions.condition).toBe(false)
    })

    it('does not search for less than two characters, and says so', () => {
        renderView({q: 'b'})
        expect(searchOptions.condition).toBe(false)
        expect(screen.getByText(/at least 2 characters/)).toBeInTheDocument()
    })

    it('asks for the highlight of the free text', () => {
        renderView({q: 'billing'})
        expect(searchQuery).toMatch(/highlight\s*{\s*text\s+match\s*}/)
    })

    it('shows the query in the search field', () => {
        renderView({q: 'billing'})
        expect(screen.getByRole('searchbox', {name: 'Search'})).toHaveValue('billing')
    })

    it('searches for a new query from the search field, on all its results', () => {
        renderView({q: 'billing', type: 'build', page: '3'})
        const field = screen.getByRole('searchbox', {name: 'Search'})
        fireEvent.change(field, {target: {value: 'payment'}})
        fireEvent.keyDown(field, {key: 'Enter', code: 'Enter', keyCode: 13})
        expect(lastPush()).toEqual({pathname: '/search', query: {q: 'payment', type: 'build'}})
    })

})

describe('the results', () => {

    it('lists the results, with their count', () => {
        searchAnswer = answer({
            total: 2,
            facets: [{type: PROJECT, count: 1}, {type: BRANCH, count: 1}],
            items: [project('billing'), branch('billing', 'main')],
        })
        renderView({q: 'billing'})
        expect(screen.getByRole('status')).toHaveTextContent('2 results')
        const results = within(screen.getByRole('list', {name: 'Search results'})).getAllByRole('listitem')
        expect(results).toHaveLength(2)
        // The title is highlighted in parts: jsdom does not know `mark` is inline, and would compute the
        // name of the link with spaces between them
        expect(within(results[0]).getByRole('link')).toHaveAttribute('href', '/project/1')
        expect(within(results[0]).getByRole('link')).toHaveTextContent(/^billing$/)
        expect(within(results[1]).getByRole('link')).toHaveAttribute('href', '/branch/10')
        expect(within(results[1]).getByRole('link')).toHaveTextContent(/^billing\/main$/)
        expect(results[1]).toHaveTextContent('Branch')
        expect(results[1]).toHaveTextContent('in billing')
    })

    it('says when there is no result', () => {
        searchAnswer = answer({total: 0, facets: [], items: []})
        renderView({q: 'nothing'})
        expect(screen.getByRole('status')).toHaveTextContent('No results')
        expect(screen.getByText('No results for "nothing".')).toBeInTheDocument()
    })

    it('highlights the query in the titles', () => {
        searchAnswer = answer({
            total: 1,
            facets: [{type: BRANCH, count: 1}],
            items: [branch('billing', 'main')],
        })
        renderView({q: 'MAIN'})
        const link = within(screen.getByRole('listitem')).getByRole('link')
        expect(link).toHaveTextContent(/^billing\/main$/)
        expect(within(link).getByText('main', {selector: 'mark'})).toBeInTheDocument()
    })

    it('shows the highlight of the free text, as text', () => {
        searchAnswer = answer({
            total: 1,
            facets: [{type: PROJECT, count: 1}],
            items: [{
                ...project('billing'),
                description: 'About <b>billing</b>',
                highlight: [
                    {text: 'About <b>', match: false},
                    {text: 'billing', match: true},
                    {text: '</b>', match: false},
                ],
            }],
        })
        const {container} = renderView({q: 'billing'})
        const item = screen.getByRole('listitem')
        expect(item).toHaveTextContent('About <b>billing</b>')
        expect(container.querySelector('b')).toBeNull()
        expect(within(item).getAllByText('billing', {selector: 'mark'})).toHaveLength(2)
    })

    it('shows the free text when it has no highlight', () => {
        searchAnswer = answer({
            total: 1,
            facets: [{type: PROJECT, count: 1}],
            items: [{...project('billing'), description: 'The billing service'}],
        })
        renderView({q: 'billing'})
        expect(screen.getByRole('listitem')).toHaveTextContent('The billing service')
    })

    it('shows the message of the search', () => {
        searchAnswer = answer({
            total: 0, facets: [], items: [], message: 'Search index is being built',
        })
        renderView({q: 'billing'})
        expect(screen.getByText('Search index is being built')).toBeInTheDocument()
    })

})

describe('filtering on a type', () => {

    const facets = [{type: PROJECT, count: 1}, {type: BRANCH, count: 3}]

    const filters = () => within(screen.getByRole('group', {name: 'Filter by type'})).getAllByRole('button')

    it('offers the types having results, with their counts', () => {
        searchAnswer = answer({total: 4, facets, items: [project('billing')]})
        renderView({q: 'billing'})
        expect(filters().map(it => it.textContent)).toEqual(['All (4)', 'Project (1)', 'Branch (3)'])
        expect(screen.getByRole('button', {name: 'All (4)'})).toHaveAttribute('aria-pressed', 'true')
        expect(screen.getByRole('button', {name: 'Branch (3)'})).toHaveAttribute('aria-pressed', 'false')
    })

    it('filters on a type, from its first page', () => {
        searchAnswer = answer({total: 4, facets, items: [project('billing')]})
        renderView({q: 'billing', page: '2'})
        fireEvent.click(screen.getByRole('button', {name: 'Branch (3)'}))
        expect(lastPush()).toEqual({pathname: '/search', query: {q: 'billing', type: 'branch'}})
    })

    it('keeps the counts of all the types while filtering', () => {
        searchAnswer = answer({
            all: {total: 4, facets},
            total: 3,
            facets: [{type: BRANCH, count: 3}],
            items: [branch('billing', 'main')],
        })
        renderView({q: 'billing', type: 'branch'})
        expect(filters().map(it => it.textContent)).toEqual(['All (4)', 'Project (1)', 'Branch (3)'])
        expect(screen.getByRole('button', {name: 'Branch (3)'})).toHaveAttribute('aria-pressed', 'true')
        expect(screen.getByRole('status')).toHaveTextContent('3 results')
    })

    it('goes back to all the types', () => {
        searchAnswer = answer({
            all: {total: 4, facets},
            total: 3,
            facets: [{type: BRANCH, count: 3}],
            items: [branch('billing', 'main')],
        })
        renderView({q: 'billing', type: 'branch'})
        fireEvent.click(screen.getByRole('button', {name: 'All (4)'}))
        expect(lastPush()).toEqual({pathname: '/search', query: {q: 'billing'}})
    })

    it('keeps a type of the URL which has no result', () => {
        searchAnswer = answer({
            all: {total: 1, facets: [{type: PROJECT, count: 1}]},
            total: 0,
            facets: [],
            items: [],
        })
        renderView({q: 'billing', type: 'build'})
        expect(screen.getByRole('button', {name: 'Build (0)'})).toHaveAttribute('aria-pressed', 'true')
    })

})

describe('pagination', () => {

    const manyResults = (total) => answer({
        total,
        facets: [{type: PROJECT, count: total}],
        items: Array.from({length: 20}, (_, i) => project(`billing-${i}`, i)),
    })

    it('is not shown for one page of results', () => {
        searchAnswer = manyResults(20)
        renderView({q: 'billing'})
        expect(screen.queryByRole('navigation', {name: 'Pages of results'})).not.toBeInTheDocument()
    })

    it('moves to another page', () => {
        searchAnswer = manyResults(45)
        renderView({q: 'billing', type: 'project'})
        const pages = screen.getByRole('navigation', {name: 'Pages of results'})
        fireEvent.click(within(pages).getByText('3'))
        expect(lastPush()).toEqual({pathname: '/search', query: {q: 'billing', type: 'project', page: '3'}})
        expect(window.scrollTo).toHaveBeenCalledWith({top: 0})
    })

    it('shows the page of the URL as the current one', () => {
        searchAnswer = manyResults(45)
        renderView({q: 'billing', page: '2'})
        const pages = screen.getByRole('navigation', {name: 'Pages of results'})
        expect(within(pages).getByText('2')).toHaveAttribute('aria-current', 'page')
        expect(within(pages).getByText('1')).not.toHaveAttribute('aria-current')
    })

    it('goes back to the first page without a page in the URL', () => {
        searchAnswer = manyResults(45)
        renderView({q: 'billing', page: '2'})
        const pages = screen.getByRole('navigation', {name: 'Pages of results'})
        fireEvent.click(within(pages).getByText('1'))
        expect(lastPush()).toEqual({pathname: '/search', query: {q: 'billing'}})
    })

})
