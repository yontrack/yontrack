import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import ProjectFindingsTable from "@components/extension/findings/project/ProjectFindingsTable"
import ScorecardRemediationLink from "@components/extension/findings/finding/ScorecardRemediationLink"

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

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
}))

const main = {id: 10, name: 'main'}
const scan = {id: 20, name: 'scan'}

const finding = (id, exposedFor) => ({
    id,
    scanner: 'trivy',
    externalId: `CVE-${id}`,
    location: 'pkg:maven/org.x/y',
    kind: 'IMAGE',
    title: `Title of CVE-${id}`,
    lastSeen: '2026-10-08T10:00:00',
    maxSeverity: 'HIGH',
    state: exposedFor.ongoing ? (exposedFor.accepted ? 'ACCEPTED' : 'OPEN') : 'RESOLVED',
    exposures: [],
    exposedFor,
})

const open = finding(1, {
    ongoing: true, ongoingSeconds: 47 * 86400, since: '2026-08-22T10:00:00', branch: main, validationStamp: scan,
    accepted: false, reopened: true, lastEpisodeSeconds: 47 * 86400,
})
const accepted = finding(2, {
    ongoing: true, ongoingSeconds: 12 * 86400, since: '2026-09-26T10:00:00', branch: main, validationStamp: scan,
    accepted: true, reopened: false, lastEpisodeSeconds: 12 * 86400,
})
const fixed = finding(3, {
    ongoing: false, ongoingSeconds: null, since: null, branch: null, validationStamp: null,
    accepted: false, reopened: false, lastEpisodeSeconds: 3 * 86400,
})

const renderTable = (props = {}) => render(
    <ProjectFindingsTable
        findings={[open, accepted, fixed]}
        totalSize={3}
        loading={false}
        current={1}
        pageSize={20}
        onPageChange={jest.fn()}
        {...props}
    />
)

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('The Exposed for column of the findings of a project', () => {

    it('shows the duration of the longest ongoing period after a decorative age bar', () => {
        renderTable()
        const cell = screen.getByTestId('finding-exposed-for-1')
        expect(cell).toHaveTextContent('47 days')
        const bar = within(cell).getByTestId('finding-exposed-for-bar')
        expect(bar.parentElement).toHaveAttribute('aria-hidden', 'true')
        expect(screen.getByTestId('finding-exposed-for-2')).toHaveTextContent('12 days')
    })

    it('says how long the last fix took for a fixed finding, without bar', () => {
        renderTable()
        const cell = screen.getByTestId('finding-exposed-for-3')
        expect(cell).toHaveTextContent('fixed after 3 days')
        expect(within(cell).queryByTestId('finding-exposed-for-bar')).not.toBeInTheDocument()
    })

    // The sticky header is rendered apart from the body: its cell is the first one
    const exposedForHeader = () => screen.getAllByText('Exposed for')[0].closest('th')

    it('asks for the sort by exposure from its header, and back to the default order', () => {
        const onSortChange = jest.fn()
        const {rerender} = renderTable({onSortChange})
        fireEvent.click(exposedForHeader())
        expect(onSortChange).toHaveBeenLastCalledWith('EXPOSED_FOR')

        rerender(
            <ProjectFindingsTable
                findings={[open, accepted, fixed]}
                totalSize={3}
                loading={false}
                current={1}
                pageSize={20}
                onPageChange={jest.fn()}
                sort="EXPOSED_FOR"
                onSortChange={onSortChange}
            />
        )
        expect(exposedForHeader()).toHaveAttribute('aria-sort', 'descending')
        fireEvent.click(exposedForHeader())
        expect(onSortChange).toHaveBeenLastCalledWith('DEFAULT')
    })
})

describe('The link from a finding to the remediation time of its project', () => {

    const licence = (enabled) => mockUseQuery.mockReturnValue({
        data: [{id: 'extension.scorecard', enabled}],
        loading: false,
        finished: true,
    })

    it('goes to the reading on the scorecard page of the project', () => {
        licence(true)
        render(<ScorecardRemediationLink project={{id: 7}}/>)
        expect(screen.getByTestId('finding-scorecard-remediation-link')).toHaveAttribute(
            'href',
            '/extension/scorecard/project/7?set=project#reading-project-security.remediationTime',
        )
    })

    it('is not shown without the scorecard', () => {
        licence(false)
        render(<ScorecardRemediationLink project={{id: 7}}/>)
        expect(screen.queryByTestId('finding-scorecard-remediation-link')).not.toBeInTheDocument()
    })
})
