import "@testing-library/jest-dom"
import {act, fireEvent, render, screen, waitFor} from "@testing-library/react"
import StandardTable from "@components/common/table/StandardTable"

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

/**
 * `StandardTable` runs its query through `useQuery` (#1929). A page beyond the first one is
 * appended to the items already loaded, while a first page - a reload - replaces them.
 */

const QUERY = `query Items($offset: Int!, $size: Int!) { items(offset: $offset, size: $size) { pageItems { name } } }`

const pages = {
    0: {
        pageItems: [{name: "one"}, {name: "two"}],
        pageInfo: {nextPage: {offset: 2, size: 2}},
    },
    2: {
        pageItems: [{name: "three"}],
        pageInfo: {nextPage: null},
    },
}

const filter = {}

const requestedOffsets = () => global.fetch.mock.calls.map(([, init]) => JSON.parse(init.body).variables.offset)

// Lets the pending requests and effects run: a table which refetches on every render would keep
// calling `fetch` meanwhile
const settle = () => act(() => new Promise(resolve => setTimeout(resolve, 100)))

const table = (reloadCount = 0, props = {filter}) => (
    <StandardTable
        id="items"
        query={QUERY}
        queryNode="items"
        reloadCount={reloadCount}
        {...props}
        size={2}
        rowKey="name"
        columns={[{key: "name", title: "Name", dataIndex: "name"}]}
    />
)

describe('StandardTable', () => {

    beforeEach(() => {
        global.fetch = jest.fn().mockImplementation(async (_, init) => {
            const {variables} = JSON.parse(init.body)
            // Answering on a later task, like a real server, keeps a refetch loop from starving
            // the timers of the test
            await new Promise(resolve => setTimeout(resolve, 0))
            return {
                ok: true,
                status: 200,
                json: async () => ({items: pages[variables.offset]}),
            }
        })
    })

    afterEach(() => {
        delete global.fetch
    })

    it('appends the next page to the items already loaded', async () => {
        render(table())

        await waitFor(() => expect(screen.getByText("two")).toBeInTheDocument())

        fireEvent.click(screen.getByText("Load more..."))

        await waitFor(() => expect(screen.getByText("three")).toBeInTheDocument())
        expect(screen.getByText("one")).toBeInTheDocument()
        expect(screen.getByText("two")).toBeInTheDocument()
        expect(requestedOffsets()).toEqual([0, 2])
    })

    it('replaces the items when the first page is loaded again', async () => {
        const {rerender} = render(table(0))
        await waitFor(() => expect(screen.getByText("two")).toBeInTheDocument())

        rerender(table(1))

        await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(2))
        await waitFor(() => expect(screen.getByText("two")).toBeInTheDocument())
        expect(screen.getAllByText("one")).toHaveLength(1)
        expect(requestedOffsets()).toEqual([0, 0])
    })

    it('sends the query once when no filter is given', async () => {
        // The default filter is an effect dependency: it must be the same object from one
        // render to the next, or every render would send the query again (#1935)
        render(table(0, {}))

        await settle()

        expect(requestedOffsets()).toEqual([0])
        expect(screen.getByText("two")).toBeInTheDocument()
    })

})
