import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"
import FindingBoundaryFilter from "@components/extension/findings/finding/FindingBoundaryFilter"
import {STANDARD_BUILD_FILTER_TYPE} from "@components/extension/findings/findingsModel"

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

const resource = (name, data) => ({type: STANDARD_BUILD_FILTER_TYPE, name, data, error: null})

const main = {
    id: 1, name: 'main',
    buildFilterResources: [
        resource('GOLD builds', {count: 10, withPromotionLevel: 'GOLD'}),
        resource('Since last RC', {sincePromotionLevel: 'RC'}),
    ],
}
const release = {
    id: 2, name: 'release-2.4',
    buildFilterResources: [resource('GOLD builds', {count: 5, withPromotionLevel: 'GOLD'})],
}

const mockResponse = {finding: {exposures: [{branch: main}, {branch: release}]}}

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (query, {dataFn}) => ({data: dataFn(mockResponse), loading: false, finished: true}),
    useMutation: () => ({mutate: jest.fn(), data: {}, loading: false}),
}))

const gold = {name: 'GOLD builds', data: {withPromotionLevel: 'GOLD'}}

const open = ({filter, onFilter = jest.fn(), onPermalink = jest.fn()} = {}) => {
    render(<FindingBoundaryFilter
        findingId={1}
        exposures={[{branch: main}, {branch: release}]}
        filter={filter}
        onFilter={onFilter}
        onPermalink={onPermalink}
    />)
    fireEvent.click(screen.getByTestId('finding-boundary-filter'))
    return {onFilter, onPermalink}
}

describe('FindingBoundaryFilter', () => {

    it('says what the filter is for, and that there is none by default', () => {
        open()
        expect(screen.getByText('A fix counts once a build matches')).toBeInTheDocument()
        expect(screen.getByTestId('finding-boundary-filter')).toHaveTextContent('No filter (all builds)')
        expect(screen.getByRole('menuitem', {name: /New filter/})).toBeInTheDocument()
        expect(screen.queryByRole('menuitem', {name: /Permalink/})).toBeNull()
    })

    it('copies the criteria of a saved filter, listed once with its branches', () => {
        const {onFilter} = open()
        const item = screen.getByRole('menuitem', {name: /GOLD builds/})
        expect(item).toHaveTextContent('main, release-2.4')
        fireEvent.click(item)
        expect(onFilter).toHaveBeenCalledWith(gold)
    })

    it('lists a filter using since, which cannot be selected and says why', () => {
        const {onFilter} = open()
        const item = screen.getByRole('menuitem', {name: /Since last RC/})
        expect(item).toHaveTextContent(`main · uses "since", can't set a boundary`)
        expect(item).toHaveAttribute('aria-disabled', 'true')
        fireEvent.click(item)
        expect(onFilter).not.toHaveBeenCalled()
    })

    it('shows the active filter, gives its permalink, and goes back to no filter', () => {
        const {onFilter, onPermalink} = open({filter: gold})
        expect(screen.getByTestId('finding-boundary-filter')).toHaveTextContent('GOLD builds')
        fireEvent.click(screen.getByRole('menuitem', {name: /Permalink/}))
        expect(onPermalink).toHaveBeenCalled()
        fireEvent.click(screen.getByTestId('finding-boundary-filter'))
        fireEvent.click(screen.getByRole('menuitem', {name: /No filter/}))
        expect(onFilter).toHaveBeenCalledWith(undefined)
    })
})
