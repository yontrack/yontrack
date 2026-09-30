import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider"
import ProjectFindingsWidget from "@components/widgets/extension/findings/ProjectFindingsWidget"
import BranchFindingsWidget from "@components/widgets/extension/findings/BranchFindingsWidget"

// antd's Table asks for the media queries of its responsive columns, which jsdom does not answer
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
}))

const counts = (critical, high, medium, low, unknown) => [
    {severity: 'CRITICAL', count: critical},
    {severity: 'HIGH', count: high},
    {severity: 'MEDIUM', count: medium},
    {severity: 'LOW', count: low},
    {severity: 'UNKNOWN', count: unknown},
]

const projectSummary = {
    open: counts(1, 2, 0, 0, 0),
    openCount: 3,
    acceptedCount: 1,
    resolvedCount: 2,
    branches: [
        {branch: {id: 10, name: 'main'}, open: counts(1, 1, 0, 0, 0), openCount: 2, counting: true},
        {branch: {id: 11, name: 'old'}, open: counts(0, 3, 0, 0, 0), openCount: 3, counting: false},
        {branch: {id: 12, name: 'feature'}, open: counts(0, 0, 0, 0, 0), openCount: 0, counting: true},
    ],
}

const project = (findingsSummary) => ({id: 7, name: 'petclinic', findingsSummary})

const branch = (findingsSummary, disabled = false) => ({
    id: 10,
    name: 'release-2.3',
    disabled,
    project: {id: 7, name: 'petclinic'},
    findingsSummary,
})

const renderWidget = (element, data) => {
    mockUseQuery.mockReturnValue({data, loading: false, finished: true, error: null})
    const setTitle = jest.fn()
    render(
        <DashboardWidgetCellContext.Provider value={{setTitle}}>
            {element}
        </DashboardWidgetCellContext.Provider>
    )
    return {setTitle}
}

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('Project findings widget', () => {

    it('counts the open findings of the project as severity tags, linking to them', () => {
        renderWidget(<ProjectFindingsWidget project="petclinic"/>, project(projectSummary))
        expect(screen.getByTestId('project-findings-open-CRITICAL')).toHaveTextContent('Critical 1')
        expect(screen.getByTestId('project-findings-open-HIGH').closest('a'))
            .toHaveAttribute('href', '/extension/findings/project/7?severity=HIGH&state=OPEN')
        expect(screen.getByTestId('project-findings-accepted')).toHaveTextContent('1')
        expect(screen.getByTestId('project-findings-resolved')).toHaveTextContent('2')
    })

    it('does not show the branches unless asked to', () => {
        renderWidget(<ProjectFindingsWidget project="petclinic"/>, project(projectSummary))
        expect(screen.queryByTestId('project-findings-branches')).not.toBeInTheDocument()
    })

    it('shows the branches with open findings, among those which count for the project', () => {
        renderWidget(<ProjectFindingsWidget project="petclinic" showBranches={true}/>, project(projectSummary))
        const table = screen.getByTestId('project-findings-branches')
        expect(within(table).getByRole('link', {name: 'main'})).toBeInTheDocument()
        expect(within(table).queryByText('old')).not.toBeInTheDocument()
        expect(within(table).queryByText('feature')).not.toBeInTheDocument()
    })

    it('says when no finding is open', () => {
        renderWidget(
            <ProjectFindingsWidget project="petclinic"/>,
            project({...projectSummary, open: counts(0, 0, 0, 0, 0), openCount: 0, branches: []})
        )
        expect(screen.getByText('No open findings')).toBeInTheDocument()
        expect(screen.getByTestId('project-findings-open-CRITICAL')).toHaveTextContent('Critical 0')
    })

    it('says when no finding has been reported', () => {
        renderWidget(
            <ProjectFindingsWidget project="petclinic"/>,
            project({open: counts(0, 0, 0, 0, 0), openCount: 0, acceptedCount: 0, resolvedCount: 0, branches: []})
        )
        expect(screen.getByText('No findings reported')).toBeInTheDocument()
        expect(screen.queryByTestId('project-findings-open')).not.toBeInTheDocument()
    })

    it('tells a user who cannot see the findings, rather than showing zeros', () => {
        renderWidget(<ProjectFindingsWidget project="petclinic"/>, project(null))
        expect(screen.getByText('You cannot see the findings of this project')).toBeInTheDocument()
        expect(screen.queryByTestId('project-findings-open')).not.toBeInTheDocument()
    })

    it('says when the project cannot be found', () => {
        renderWidget(<ProjectFindingsWidget project="unknown"/>, null)
        expect(screen.getByText('Project unknown not found')).toBeInTheDocument()
    })

    it('asks for a project when none is configured, and asks nothing of the server', () => {
        renderWidget(<ProjectFindingsWidget/>, null)
        expect(screen.getByText('No project selected')).toBeInTheDocument()
        expect(mockUseQuery).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({condition: false}),
        )
    })

    it('is titled after the project', () => {
        const {setTitle} = renderWidget(<ProjectFindingsWidget project="petclinic"/>, project(projectSummary))
        render(setTitle.mock.calls.at(-1)[0])
        expect(screen.getByRole('link', {name: 'petclinic'})).toHaveAttribute('href', '/project/7')
    })
})

describe('Branch findings widget', () => {

    const branchSummary = {
        open: counts(0, 1, 2, 0, 0),
        openCount: 3,
        acceptedCount: 1,
        resolvedCount: 4,
        hasExposures: true,
    }

    it('counts the findings open on the branch, linking to them filtered on the branch', () => {
        renderWidget(<BranchFindingsWidget project="petclinic" branch="release-2.3"/>, branch(branchSummary))
        expect(screen.getByTestId('branch-findings-open-HIGH')).toHaveTextContent('High 1')
        expect(screen.getByTestId('branch-findings-open-MEDIUM').closest('a'))
            .toHaveAttribute('href', '/extension/findings/project/7?severity=MEDIUM&state=OPEN&branch=release-2.3')
        expect(screen.getByTestId('branch-findings-resolved'))
            .toHaveAttribute('href', '/extension/findings/project/7?state=RESOLVED&branch=release-2.3')
    })

    it('says when no finding has been reported on the branch', () => {
        renderWidget(
            <BranchFindingsWidget project="petclinic" branch="release-2.3"/>,
            branch({open: counts(0, 0, 0, 0, 0), openCount: 0, acceptedCount: 0, resolvedCount: 0, hasExposures: false})
        )
        expect(screen.getByText('No findings reported')).toBeInTheDocument()
    })

    it('says when no finding is open on the branch', () => {
        renderWidget(
            <BranchFindingsWidget project="petclinic" branch="release-2.3"/>,
            branch({...branchSummary, open: counts(0, 0, 0, 0, 0), openCount: 0})
        )
        expect(screen.getByText('No open findings')).toBeInTheDocument()
    })

    it('shows the findings of a disabled branch, saying it is disabled', () => {
        renderWidget(<BranchFindingsWidget project="petclinic" branch="release-2.3"/>, branch(branchSummary, true))
        expect(screen.getByText(/disabled branch/)).toBeInTheDocument()
        expect(screen.getByTestId('branch-findings-open-HIGH')).toHaveTextContent('High 1')
    })

    it('tells a user who cannot see the findings, rather than showing zeros', () => {
        renderWidget(<BranchFindingsWidget project="petclinic" branch="release-2.3"/>, branch(null))
        expect(screen.getByText('You cannot see the findings of this project')).toBeInTheDocument()
    })

    it('says when the branch cannot be found', () => {
        renderWidget(<BranchFindingsWidget project="petclinic" branch="unknown"/>, null)
        expect(screen.getByText('Branch petclinic/unknown not found')).toBeInTheDocument()
    })

    it('asks for a branch when none is configured', () => {
        renderWidget(<BranchFindingsWidget/>, null)
        expect(screen.getByText('No branch selected')).toBeInTheDocument()
    })

    it('is titled after the branch and its project', () => {
        const {setTitle} = renderWidget(<BranchFindingsWidget project="petclinic" branch="release-2.3"/>, branch(branchSummary))
        render(setTitle.mock.calls.at(-1)[0])
        expect(screen.getByRole('link', {name: 'release-2.3'})).toHaveAttribute('href', '/branch/10')
        expect(screen.getByRole('link', {name: 'petclinic'})).toHaveAttribute('href', '/project/7')
    })
})
