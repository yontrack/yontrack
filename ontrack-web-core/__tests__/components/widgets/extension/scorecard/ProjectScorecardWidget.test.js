import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider"
import ProjectScorecardWidget from "@components/widgets/extension/scorecard/ProjectScorecardWidget"

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

jest.mock("../../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
    callGraphQL: jest.fn(),
}))

const reading = (props) => ({
    computedAt: '2026-09-28T02:00:00Z',
    value: 7200,
    basis: 'MEASURED',
    unknownReason: null,
    direction: 'LOWER_IS_BETTER',
    target: null,
    targetMet: null,
    details: {},
    history: [],
    ...props,
})

const estate = (name, marker) => ({name, marker})

const projectSet = {
    name: 'Project',
    estate: null,
    readings: [
        reading({key: 'delivery.leadTime'}),
        reading({key: 'delivery.frequency', value: 3.5, direction: 'HIGHER_IS_BETTER'}),
    ],
}

const products = {
    name: 'Demo products',
    estate: estate('Demo products', {kind: 'PROMOTION', levelName: 'GOLD'}),
    readings: [
        reading({key: 'delivery.leadTime', target: 86400, targetMet: true}),
        reading({key: 'delivery.frequency', value: 3.5, direction: 'HIGHER_IS_BETTER', target: 5, targetMet: false}),
        reading({key: 'quality.testFlakiness', value: 2.4}),
    ],
}

const production = {
    name: 'Demo production',
    estate: estate('Demo production', {kind: 'ENVIRONMENT', environment: 'production', qualifier: ''}),
    readings: [
        reading({key: 'delivery.leadTime', target: 3600, targetMet: false}),
    ],
}

const setTitle = jest.fn()

const widget = (config) =>
    <DashboardWidgetCellContext.Provider value={{setTitle}}>
        <ProjectScorecardWidget {...config}/>
    </DashboardWidgetCellContext.Provider>

const renderWidget = ({config = {project: 'petclinic-visits'}, sets = [projectSet, products, production], found = true} = {}) => {
    mockUseQuery.mockReturnValue({
        data: found ? {id: 7, name: 'petclinic-visits', scorecard: {sets}} : null,
        loading: false,
        finished: true,
        error: null,
    })
    return render(widget(config))
}

beforeEach(() => {
    mockUseQuery.mockReset()
    setTitle.mockReset()
})

describe('The project scorecard widget', () => {

    it('is titled after its project', () => {
        renderWidget()
        expect(setTitle).toHaveBeenCalledWith('Scorecard · petclinic-visits')
    })

    it('asks for the project by name', () => {
        renderWidget()
        expect(mockUseQuery.mock.calls[0][1].variables).toEqual({name: 'petclinic-visits', days: 90})
    })

    it('says when no project is configured', () => {
        renderWidget({config: {}})
        expect(screen.getByText('Project has not been configured.')).toBeInTheDocument()
    })

    it('says when the project cannot be found', () => {
        renderWidget({found: false})
        expect(screen.getByText('Project petclinic-visits not found.')).toBeInTheDocument()
    })

    it('opens on the first estate by name by default', () => {
        renderWidget()
        const tabs = screen.getAllByRole('tab')
        expect(tabs.map(it => it.textContent)).toEqual(['Project', 'Demo production · 0/1', 'Demo products · 1/2'])
        expect(screen.getByRole('tab', {selected: true})).toHaveTextContent('Demo production')
        expect(screen.getByRole('img', {name: '0 of 1 target met in Demo production'})).toBeInTheDocument()
    })

    it('opens on the configured set', () => {
        renderWidget({config: {project: 'petclinic-visits', set: 'Demo products'}})
        expect(screen.getByRole('tab', {selected: true})).toHaveTextContent('Demo products')
        const ring = screen.getByRole('img', {name: '1 of 2 targets met in Demo products'})
        expect(ring).toHaveTextContent('1/2')
        expect(ring).toHaveTextContent('targets met')
    })

    it('lists the judged readings of an estate, met or missed in words', () => {
        renderWidget({config: {project: 'petclinic-visits', set: 'Demo products'}})
        const list = screen.getByTestId('scorecard-widget-judged')
        const lines = within(list).getAllByRole('listitem')
        expect(lines).toHaveLength(2)
        expect(lines[0]).toHaveTextContent('Met')
        expect(lines[0]).toHaveTextContent('Lead time')
        expect(lines[0]).toHaveTextContent('2h')
        expect(within(lines[0]).getByTestId('scorecard-widget-delivery.leadTime-judgement')).toHaveAttribute('data-judgement', 'MET')
        expect(lines[1]).toHaveTextContent('Missed')
        expect(lines[1]).toHaveTextContent('Frequency')
        expect(within(lines[1]).getByTestId('scorecard-widget-delivery.frequency-judgement')).toHaveAttribute('data-judgement', 'MISSED')
    })

    it('says what the estate reads up to, and links to the scorecard page on the shown set', () => {
        renderWidget({config: {project: 'petclinic-visits', set: 'Demo products'}})
        expect(screen.getByTestId('scorecard-widget-footer')).toHaveTextContent('Up to promotion GOLD')
        expect(screen.getByTestId('scorecard-widget-footer')).toHaveTextContent('computed')
        expect(screen.getByRole('link', {name: 'Open scorecard'}))
            .toHaveAttribute('href', '/extension/scorecard/project/7?set=Demo%20products')
    })

    it('switches the set with its tabs, locally', () => {
        renderWidget()
        fireEvent.click(screen.getByRole('tab', {name: 'Project'}))
        expect(screen.getByRole('tab', {selected: true})).toHaveTextContent('Project')
        expect(screen.getByTestId('scorecard-widget-project')).toBeInTheDocument()
        expect(screen.getByRole('link', {name: 'Open scorecard'}))
            .toHaveAttribute('href', '/extension/scorecard/project/7?set=project')
    })

    it('shows the readings of the Project set, never judged', () => {
        renderWidget({config: {project: 'petclinic-visits', set: 'project'}})
        const view = screen.getByTestId('scorecard-widget-project')
        expect(view).toHaveTextContent('No targets')
        expect(view).toHaveTextContent('Readings only · 90-day trend')
        expect(within(view).getByTestId('scorecard-widget-tile-delivery.leadTime')).toHaveTextContent('Lead time')
        expect(within(view).getByTestId('scorecard-widget-tile-delivery.leadTime')).toHaveTextContent('2h')
        expect(within(view).getByTestId('scorecard-widget-tile-delivery.frequency')).toHaveTextContent('3.5 / week')
        expect(screen.getByTestId('scorecard-widget-footer')).toHaveTextContent("Up to each branch's last promotion")
    })

    it('has no tab bar for a project in no estate, and shows the Project set', () => {
        renderWidget({sets: [projectSet]})
        expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
        expect(screen.queryByRole('tab')).not.toBeInTheDocument()
        expect(screen.getByTestId('scorecard-widget-project')).toBeInTheDocument()
    })

    it('falls back on the default set for an estate the project is no longer in, and says so', () => {
        renderWidget({config: {project: 'petclinic-visits', set: 'Gone'}})
        expect(screen.getByRole('tab', {selected: true})).toHaveTextContent('Demo production')
        expect(screen.getByTestId('scorecard-widget-fallback')).toHaveTextContent('Gone')
    })

    it('says nothing of a fallback when the configured set is found', () => {
        renderWidget({config: {project: 'petclinic-visits', set: 'Demo products'}})
        expect(screen.queryByTestId('scorecard-widget-fallback')).not.toBeInTheDocument()
    })

    it('forgets the fallback note once a tab is chosen', () => {
        renderWidget({config: {project: 'petclinic-visits', set: 'Gone'}})
        fireEvent.click(screen.getByRole('tab', {name: 'Project'}))
        expect(screen.queryByTestId('scorecard-widget-fallback')).not.toBeInTheDocument()
    })

    it('opens on the configured set again when its configuration changes', () => {
        const {rerender} = renderWidget({config: {project: 'petclinic-visits', set: 'Demo products'}})
        fireEvent.click(screen.getByRole('tab', {name: 'Project'}))
        expect(screen.getByRole('tab', {selected: true})).toHaveTextContent('Project')
        rerender(widget({project: 'petclinic-visits', set: 'Demo production'}))
        expect(screen.getByRole('tab', {selected: true})).toHaveTextContent('Demo production')
    })

    it('says that the targets of an estate are not judged when none could be', () => {
        const unjudged = {
            ...production,
            readings: [reading({key: 'delivery.mttr', value: null, basis: 'UNKNOWN', unknownReason: 'NO_FAILURE', target: 86400})],
        }
        renderWidget({sets: [projectSet, unjudged]})
        expect(screen.getByRole('tab', {selected: true})).toHaveTextContent(/^Demo production$/)
        const view = screen.getByTestId('scorecard-widget-project')
        expect(view).toHaveTextContent('No target judged')
        expect(view).not.toHaveTextContent('No targets')
    })
})
