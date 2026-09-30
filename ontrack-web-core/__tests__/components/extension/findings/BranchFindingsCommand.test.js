import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import BranchFindingsCommand from "@components/extension/findings/branch/BranchFindingsCommand"

const counts = (critical, high, medium, low, unknown) => [
    {severity: 'CRITICAL', count: critical},
    {severity: 'HIGH', count: high},
    {severity: 'MEDIUM', count: medium},
    {severity: 'LOW', count: low},
    {severity: 'UNKNOWN', count: unknown},
]

const branch = (findingsSummary) => ({
    id: 10,
    name: 'release/2.3',
    project: {id: 7, name: 'petclinic'},
    findingsSummary,
})

describe('Findings command of the branch page', () => {

    it('leads to the findings open on the branch, spelling them out', () => {
        render(<BranchFindingsCommand branch={branch({open: counts(1, 2, 0, 0, 0), openCount: 3, hasExposures: true})}/>)
        const link = screen.getByRole('link', {name: '3 open findings: 1 critical, 2 high'})
        expect(link).toHaveAttribute('href', '/extension/findings/project/7?state=OPEN&branch=release%2F2.3')
        expect(screen.getByText('Findings')).toBeInTheDocument()
    })

    it('carries the number of open findings in a badge of the colour of the most severe one', () => {
        render(<BranchFindingsCommand branch={branch({open: counts(0, 2, 1, 0, 0), openCount: 3, hasExposures: true})}/>)
        const badge = screen.getByTestId('branch-findings-badge').querySelector('.ant-badge-count')
        expect(badge).toHaveAttribute('title', '3')
        expect(badge).toHaveStyle({backgroundColor: '#d4380d'})
    })

    it('has no badge when no finding is open on the branch', () => {
        render(<BranchFindingsCommand branch={branch({open: counts(0, 0, 0, 0, 0), openCount: 0, hasExposures: true})}/>)
        expect(screen.getByRole('link', {name: 'No open finding'})).toBeInTheDocument()
        expect(screen.getByTestId('branch-findings-badge').querySelector('.ant-badge-count')).toBeNull()
    })

    it('is hidden when no finding has ever been reported on the branch', () => {
        const {container} = render(<BranchFindingsCommand branch={branch({open: counts(0, 0, 0, 0, 0), openCount: 0, hasExposures: false})}/>)
        expect(container).toBeEmptyDOMElement()
    })

    it('is hidden from a user who cannot see the findings', () => {
        const {container} = render(<BranchFindingsCommand branch={branch(null)}/>)
        expect(container).toBeEmptyDOMElement()
    })
})
