import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import FindingSummary from "@components/extension/findings/finding/FindingSummary"
import FindingAcceptance from "@components/extension/findings/finding/FindingAcceptance"
import FindingExposureTable from "@components/extension/findings/finding/FindingExposureTable"
import FindingObservationsTimeline from "@components/extension/findings/finding/FindingObservationsTimeline"

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
const release = {id: 11, name: 'release'}
const image = {id: 20, name: 'SECURITY.IMAGE'}
const dast = {id: 21, name: 'SECURITY.DAST'}

const finding = {
    id: 42,
    externalId: 'CVE-2021-44228',
    title: 'Log4Shell',
    scanner: 'trivy',
    kind: 'DEPENDENCIES',
    location: 'pkg:maven/org.apache.logging.log4j/log4j-core',
    url: 'https://avd.aquasec.com/nvd/cve-2021-44228',
    maxSeverity: 'CRITICAL',
    state: 'OPEN',
    firstSeen: '2026-09-01T10:00:00Z',
    lastSeen: '2026-09-20T10:00:00Z',
    resolvedAt: null,
    project: {id: 7, name: 'yontrack'},
}

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('Summary of a finding', () => {

    it('shows the finding and links to its URL outside of Yontrack', () => {
        render(<FindingSummary finding={finding}/>)
        expect(screen.getByText('CVE-2021-44228')).toBeInTheDocument()
        expect(screen.getByText('Log4Shell')).toBeInTheDocument()
        expect(screen.getByText('trivy')).toBeInTheDocument()
        expect(screen.getByText('Dependencies')).toBeInTheDocument()
        expect(screen.getByTestId('finding-severity-CRITICAL')).toBeInTheDocument()
        const link = screen.getByTestId('finding-url')
        expect(link).toHaveAttribute('href', 'https://avd.aquasec.com/nvd/cve-2021-44228')
        expect(link).toHaveAttribute('target', '_blank')
        expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    })

    it('says when the finding has no URL nor location', () => {
        render(<FindingSummary finding={{...finding, url: null, location: ''}}/>)
        expect(screen.queryByTestId('finding-url')).not.toBeInTheDocument()
        expect(screen.getByText('No link given by the scanner')).toBeInTheDocument()
        expect(screen.getByText('None')).toBeInTheDocument()
    })
})

describe('Acceptance of a finding', () => {

    it('shows the statement, the expiry and the source of the acceptance', () => {
        render(<FindingAcceptance acceptance={{
            effective: true,
            statement: 'Not reachable',
            expiresAt: '2026-10-03',
            source: '.trivyignore.yaml',
        }}/>)
        expect(screen.getByTestId('finding-acceptance-summary')).toHaveTextContent('Accepted until 2026-10-03')
        expect(screen.getByText('Not reachable')).toBeInTheDocument()
        expect(screen.getByText('2026-10-03')).toBeInTheDocument()
        expect(screen.getByText('.trivyignore.yaml')).toBeInTheDocument()
    })

    it('says when the acceptance has expired', () => {
        render(<FindingAcceptance acceptance={{effective: false, statement: 'Later', expiresAt: '2026-09-01'}}/>)
        expect(screen.getByTestId('finding-acceptance-summary')).toHaveTextContent('Acceptance expired on 2026-09-01')
    })

    it('says when there is no acceptance', () => {
        render(<FindingAcceptance acceptance={null}/>)
        expect(screen.getByTestId('finding-acceptance-summary')).toHaveTextContent('Not accepted')
    })
})

describe('Exposure of a finding', () => {

    it('lists the exposure per branch and stamp with its start', () => {
        render(<FindingExposureTable exposures={[
            {
                branch: main, validationStamp: dast, state: 'EXPOSED', accepted: false,
                since: '2026-09-01T10:00:00Z', resolvedAt: null, resolutionReason: null,
            },
            {
                branch: main, validationStamp: image, state: 'RESOLVED', accepted: false,
                since: '2026-09-02T10:00:00Z', resolvedAt: '2026-09-10T10:00:00Z', resolutionReason: 'ABSENT',
            },
            {
                branch: release, validationStamp: image, state: 'ACCEPTED', accepted: true,
                since: '2026-09-03T10:00:00Z', acceptanceExpiresAt: '2026-10-03',
            },
        ]}/>)
        const table = screen.getByTestId('finding-exposure')
        const rows = within(table).getAllByRole('row').slice(1)
        expect(rows).toHaveLength(3)
        // The branch spans its stamps
        expect(within(table).getAllByRole('link', {name: 'main'})).toHaveLength(1)
        expect(screen.getByTestId('finding-exposure-main-SECURITY.DAST')).toHaveTextContent('Exposed')
        expect(screen.getByTestId('finding-exposure-main-SECURITY.IMAGE')).toHaveTextContent('Resolved')
        expect(screen.getByTestId('finding-exposure-release-SECURITY.IMAGE')).toHaveTextContent('Accepted')
        expect(rows[2]).toHaveTextContent('until 2026-10-03')
    })

    it('says when the finding has never been exposed', () => {
        render(<FindingExposureTable exposures={[]}/>)
        expect(screen.getByText('This finding is not exposed on any branch')).toBeInTheDocument()
    })
})

describe('Timeline of the observations of a finding', () => {

    const observation = (time, severity, buildName, extra = {}) => ({
        time,
        severity,
        rawSeverity: severity,
        installedVersion: '2.14.0',
        fixedVersion: '2.17.1',
        acceptance: null,
        validationRun: {
            id: Number(buildName) * 10,
            runOrder: 1,
            build: {id: buildName.length, name: buildName, branch: main},
            validationStamp: image,
        },
        ...extra,
    })

    const page = (items, totalSize) => ({
        pageInfo: {totalSize},
        pageItems: items,
    })

    it('shows the observations, the most recent first, with their build and versions', () => {
        mockUseQuery.mockReturnValue({
            data: page([
                observation('2026-09-20T10:00:00Z', 'CRITICAL', '12', {
                    rawSeverity: 'critical',
                    acceptance: {effective: true, statement: 'Not reachable', expiresAt: '2026-10-03'},
                }),
                observation('2026-09-01T10:00:00Z', 'HIGH', '3'),
            ], 2),
            loading: false,
            finished: true,
        })
        render(<FindingObservationsTimeline id={42}/>)
        const items = screen.getAllByTestId('finding-observation')
        expect(items).toHaveLength(2)
        expect(within(items[0]).getByTestId('finding-severity-CRITICAL')).toBeInTheDocument()
        expect(items[0]).toHaveTextContent('reported as critical')
        expect(items[0]).toHaveTextContent('Accepted until 2026-10-03')
        expect(items[0]).toHaveTextContent('Not reachable')
        expect(within(items[0]).getByRole('link', {name: '12'})).toHaveAttribute('href', '/build/2')
        expect(items[1]).toHaveTextContent('2.14.0')
        expect(items[1]).toHaveTextContent('2.17.1')
        expect(screen.queryByRole('button', {name: /more/i})).not.toBeInTheDocument()
    })

    it('loads more observations on demand', () => {
        mockUseQuery.mockReturnValue({
            data: page([observation('2026-09-20T10:00:00Z', 'CRITICAL', '12')], 30),
            loading: false,
            finished: true,
        })
        render(<FindingObservationsTimeline id={42}/>)
        expect(mockUseQuery.mock.lastCall[1].variables).toEqual({id: 42, size: 20})
        fireEvent.click(screen.getByRole('button', {name: 'More observations'}))
        expect(mockUseQuery.mock.lastCall[1].variables).toEqual({id: 42, size: 40})
    })
})
