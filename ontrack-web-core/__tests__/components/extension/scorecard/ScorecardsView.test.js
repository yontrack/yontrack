import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import ScorecardsView from "@components/extension/scorecard/estates/ScorecardsView"

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

const mockUseQuery = jest.fn()

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
    callGraphQL: jest.fn(),
}))

const estates = [
    {
        id: 1,
        name: 'Demo products',
        description: 'Our products',
        labels: [{id: 1, category: 'type', name: 'product', color: '#ff0000', foregroundColor: '#ffffff'}],
        marker: {kind: 'PROMOTION', levelName: 'GOLD', environment: null, qualifier: null},
        projects: [{id: 7}, {id: 8}],
    },
    {
        id: 2,
        name: 'Demo production',
        description: null,
        labels: [],
        marker: null,
        projects: [{id: 7}],
    },
]

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('The scorecards of the estates', () => {

    it('lists the estates, each linking to its scorecard', () => {
        mockUseQuery.mockReturnValue({data: estates, loading: false, finished: true, error: null})
        render(<ScorecardsView/>)
        expect(screen.getByRole('link', {name: 'Demo products'})).toHaveAttribute('href', '/extension/scorecard/estate/Demo%20products')
        expect(screen.getByRole('link', {name: 'Demo production'})).toHaveAttribute('href', '/extension/scorecard/estate/Demo%20production')
        expect(screen.getByText('Our products')).toBeInTheDocument()
        expect(screen.getByTestId('scorecards-marker-Demo products')).toHaveTextContent('Promotion: GOLD')
        expect(screen.getByTestId('scorecards-marker-Demo production')).toHaveTextContent('Default')
        expect(screen.getByTestId('scorecards-projects-Demo products')).toHaveTextContent('2 projects')
        expect(screen.getByTestId('scorecards-projects-Demo production')).toHaveTextContent('1 project')
    })

    it('says when there is no estate', () => {
        mockUseQuery.mockReturnValue({data: [], loading: false, finished: true, error: null})
        render(<ScorecardsView/>)
        expect(screen.getAllByText('No estate yet: an estate groups projects by labels to read them together.')[0]).toBeInTheDocument()
    })
})
