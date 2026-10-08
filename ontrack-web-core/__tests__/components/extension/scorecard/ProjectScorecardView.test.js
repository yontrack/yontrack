import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import ProjectScorecardView from "@components/extension/scorecard/project/ProjectScorecardView"

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

jest.mock("next/head", () => function Head() {
    return null
})

jest.mock("../../../../components/layouts/MainPage", () => function MainPage({title, children}) {
    return (
        <div>
            <h1>{title}</h1>
            {children}
        </div>
    )
})

const reading = (props) => ({
    day: '2026-09-28',
    computedAt: '2026-09-28T02:00:00Z',
    windowStart: '2026-06-30T02:00:00Z',
    windowEnd: '2026-09-28T02:00:00Z',
    value: 7200,
    basis: 'MEASURED',
    unknownReason: null,
    direction: 'LOWER_IS_BETTER',
    target: null,
    targetMet: null,
    details: {count: 12, markerKind: 'PROMOTION', marker: {levels: {main: 'GOLD'}}, scope: {kind: 'ALL_BRANCHES', branches: ['main']}},
    history: [],
    ...props,
})

const estate = (name) => ({name, description: null, labels: [], marker: {kind: 'PROMOTION', levelName: 'GOLD'}})

const project = {
    id: 7,
    name: 'yontrack',
    authorizations: [],
    scorecard: {
        sets: [
            {name: 'Project', estate: null, readings: [reading({key: 'delivery.frequency', value: 3.5, direction: 'HIGHER_IS_BETTER'}), reading({key: 'delivery.leadTime'})]},
            {name: 'Demo products', estate: estate('Demo products'), readings: [reading({key: 'delivery.leadTime', target: 86400, targetMet: true})]},
            {name: 'Demo production', estate: estate('Demo production'), readings: [reading({key: 'delivery.leadTime', target: 3600, targetMet: false})]},
        ],
    },
}

const renderView = (props = {}) => {
    mockUseQuery.mockReturnValue({data: project, loading: false, finished: true, error: null})
    return render(<ProjectScorecardView id={7} {...props}/>)
}

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('The scorecard page of a project', () => {

    it('has a card per set', () => {
        renderView()
        expect(screen.getByTestId('scorecard-set-card-Project')).toBeInTheDocument()
        expect(screen.getByTestId('scorecard-set-card-Demo products')).toBeInTheDocument()
        expect(screen.getByTestId('scorecard-set-card-Demo production')).toBeInTheDocument()
    })

    it('shows the first estate by name by default, and that set only', () => {
        renderView()
        expect(screen.getByTestId('scorecard-set-card-Demo production')).toHaveAttribute('data-selected', 'true')
        expect(screen.getByText('Estate: Demo production')).toBeInTheDocument()
        expect(screen.getByTestId('reading-Demo production-delivery.leadTime')).toBeInTheDocument()
        expect(screen.queryByTestId('scorecard-set-Project')).not.toBeInTheDocument()
        expect(screen.queryByTestId('reading-Demo products-delivery.leadTime')).not.toBeInTheDocument()
    })

    it('shows the set of the URL', () => {
        renderView({set: 'project'})
        expect(screen.getByTestId('scorecard-set-Project')).toBeInTheDocument()
        expect(screen.getByTestId('scorecard-set-explanation-Project')).toHaveTextContent('Marker: Last promotion level of each branch')
        // In the catalogue order
        const tiles = screen.getAllByTestId(/^reading-Project-[a-zA-Z.]+$/)
        expect(tiles.map(it => it.getAttribute('data-testid'))).toEqual(['reading-Project-delivery.leadTime', 'reading-Project-delivery.frequency'])
    })

    it('falls back on the default set for an unknown one', () => {
        renderView({set: 'Gone'})
        expect(screen.getByTestId('scorecard-set-card-Demo production')).toHaveAttribute('data-selected', 'true')
    })

    it('gives each reading as a large tile, with what explains it', () => {
        renderView({set: 'Demo products'})
        const tile = screen.getByTestId('reading-Demo products-delivery.leadTime')
        expect(within(tile).getByTestId('reading-Demo products-delivery.leadTime-description')).toHaveTextContent(/first promotion at the marker level/)
        expect(within(tile).getByTestId('reading-Demo products-delivery.leadTime-details')).toHaveTextContent('Promotion: GOLD on main')
        expect(within(tile).getByTestId('reading-Demo products-delivery.leadTime-details')).toHaveTextContent('≤ 1d')
    })

    it('puts the set a card selects in the URL', () => {
        const onSetChange = jest.fn()
        renderView({onSetChange})
        fireEvent.click(within(screen.getByTestId('scorecard-set-card-Project')).getByRole('button', {pressed: false}))
        expect(onSetChange).toHaveBeenCalledWith('project')
        fireEvent.click(within(screen.getByTestId('scorecard-set-card-Demo products')).getByRole('button', {pressed: false}))
        expect(onSetChange).toHaveBeenCalledWith('Demo products')
    })

    it('gives each tile the DOM id of its reading in its set', () => {
        renderView({set: 'project'})
        expect(screen.getByTestId('reading-Project-delivery.leadTime')).toHaveAttribute('id', 'reading-project-delivery.leadTime')
        renderView({set: 'Demo products'})
        expect(screen.getByTestId('reading-Demo products-delivery.leadTime')).toHaveAttribute('id', 'reading-Demo products-delivery.leadTime')
    })

    it('scrolls to the reading the hash of the URL names', () => {
        const scrollIntoView = jest.fn()
        window.HTMLElement.prototype.scrollIntoView = scrollIntoView
        window.location.hash = '#reading-project-delivery.leadTime'
        try {
            renderView({set: 'project'})
            expect(scrollIntoView).toHaveBeenCalledTimes(1)
            expect(scrollIntoView.mock.contexts[0]).toBe(screen.getByTestId('reading-Project-delivery.leadTime'))
        } finally {
            window.location.hash = ''
            delete window.HTMLElement.prototype.scrollIntoView
        }
    })
})
