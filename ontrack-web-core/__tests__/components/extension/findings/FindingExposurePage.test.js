import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import FindingSummary from "@components/extension/findings/finding/FindingSummary"
import FindingExposureTable from "@components/extension/findings/finding/FindingExposureTable"
import FindingExposureTimeline from "@components/extension/findings/finding/FindingExposureTimeline"
import FindingHistory from "@components/extension/findings/finding/FindingHistory"

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

const main = {id: '10', name: 'main'}
const release = {id: 11, name: 'release-2.4'}
const patch = {id: 12, name: 'release-2.5'}
const image = {id: '20', name: 'SECURITY.IMAGE'}

const run = (id, buildId) => ({id, runOrder: 1, build: {id: buildId}})

const fixedOnMain = {
    branch: main, validationStamp: image, state: 'RESOLVED', accepted: false,
    since: '2026-10-02T09:41:00', resolvedAt: '2026-10-06T14:12:00', resolutionReason: 'ABSENT',
    periods: [{
        startedAt: '2026-10-02T09:41:00', startedBy: run(1, 100), startedInBuild: '3.4.0',
        endedAt: '2026-10-06T14:12:00', endedBy: run(7, 102), endedInBuild: '3.4.2',
        resolutionReason: 'ABSENT', ongoing: false, durationSeconds: 4 * 86400 + 4 * 3600, acceptedSpans: [],
    }],
}

const acceptedOnRelease = {
    branch: release, validationStamp: image, state: 'ACCEPTED', accepted: true, acceptanceExpiresAt: '2026-12-31',
    since: '2026-10-02T11:05:00', resolvedAt: null,
    periods: [{
        startedAt: '2026-10-02T11:05:00', startedBy: null, startedInBuild: '2.4.7',
        endedAt: null, endedBy: null, endedInBuild: null,
        ongoing: true, durationSeconds: 6 * 86400,
        acceptedSpans: [{from: '2026-10-03T08:00:00', to: null, fromBuild: '2.4.8', acceptance: {expiresAt: '2026-12-31'}}],
    }],
}

const reopenedOnPatch = {
    branch: patch, validationStamp: image, state: 'EXPOSED', accepted: false,
    since: '2026-10-05T08:30:00', resolvedAt: null,
    periods: [
        {
            startedAt: '2026-10-02T15:20:00', startedBy: null, startedInBuild: '2.5.0',
            endedAt: '2026-10-03T10:02:00', endedBy: null, endedInBuild: '2.5.1',
            ongoing: false, durationSeconds: 19 * 3600, acceptedSpans: [],
        },
        {
            startedAt: '2026-10-05T08:30:00', startedBy: run(4, 203), startedInBuild: '2.5.3',
            endedAt: null, endedBy: null, endedInBuild: null,
            ongoing: true, durationSeconds: 3 * 86400, acceptedSpans: [],
        },
    ],
}

const exposures = [fixedOnMain, acceptedOnRelease, reopenedOnPatch]

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('Exposure per branch, with builds and durations', () => {

    it('gives the builds a finding was discovered and fixed in, and for how long it was exposed', () => {
        render(<FindingExposureTable exposures={exposures}/>)
        const rows = within(screen.getByTestId('finding-exposure')).getAllByRole('row').slice(1)
        expect(rows).toHaveLength(3)
        // Fixed on main
        expect(within(rows[0]).getByRole('link', {name: '3.4.0'})).toHaveAttribute('href', '/build/100')
        expect(within(rows[0]).getByRole('link', {name: '3.4.2'})).toHaveAttribute('href', '/build/102')
        expect(rows[0]).toHaveTextContent('no longer reported')
        expect(rows[0]).toHaveTextContent('4 days')
        // Accepted, still counting as exposed; its build is no longer linked
        expect(rows[1]).toHaveTextContent('2.4.7')
        expect(within(rows[1]).queryByRole('link', {name: '2.4.7'})).not.toBeInTheDocument()
        expect(rows[1]).toHaveTextContent('Not resolved')
        expect(rows[1]).toHaveTextContent('6 days, ongoing')
        expect(rows[1]).toHaveTextContent('accepted')
        // Reopened, with its earlier period
        expect(screen.getByTestId('finding-exposure-release-2.5-SECURITY.IMAGE')).toHaveTextContent('reopened')
        expect(rows[2]).toHaveTextContent('2.5.3')
        expect(rows[2]).toHaveTextContent('3 days, ongoing')
        expect(rows[2]).toHaveTextContent('earlier: 19 h, 2.5.0 → 2.5.1')
    })

    it('says when the build is unknown', () => {
        render(<FindingExposureTable exposures={[{
            ...fixedOnMain,
            periods: [{...fixedOnMain.periods[0], startedBy: null, startedInBuild: null}],
        }]}/>)
        expect(screen.getByText('build unknown')).toBeInTheDocument()
    })
})

describe('Exposure timeline', () => {

    const now = new Date('2026-10-08T12:00:00Z')

    it('draws a lane per branch and stamp, with a bar per period, its acceptance and its fix', () => {
        render(<FindingExposureTimeline exposures={exposures} now={now}/>)
        const onMain = screen.getByTestId('finding-exposure-lane-main-SECURITY.IMAGE')
        expect(within(onMain).getAllByTestId('finding-exposure-bar')).toHaveLength(1)
        expect(within(onMain).getAllByTestId('finding-exposure-fix')).toHaveLength(1)
        expect(within(onMain).getByRole('img'))
            .toHaveAccessibleName('Exposed from 3.4.0, fixed in 3.4.2, 4 days')

        const onRelease = screen.getByTestId('finding-exposure-lane-release-2.4-SECURITY.IMAGE')
        expect(within(onRelease).getAllByTestId('finding-exposure-accepted')).toHaveLength(1)
        expect(within(onRelease).queryByTestId('finding-exposure-fix')).not.toBeInTheDocument()
        expect(onRelease).toHaveTextContent('accepted')
        expect(within(onRelease).getByRole('img')).toHaveAccessibleName('Exposed since 2.4.7, accepted, 6 days, ongoing')

        const onPatch = screen.getByTestId('finding-exposure-lane-release-2.5-SECURITY.IMAGE')
        expect(within(onPatch).getAllByTestId('finding-exposure-bar')).toHaveLength(2)
        expect(within(onPatch).getAllByTestId('finding-exposure-fix')).toHaveLength(1)

        expect(screen.getByText('Accepted (still counts as exposed)')).toBeInTheDocument()
    })

    it('draws nothing without any period', () => {
        const {container} = render(<FindingExposureTimeline exposures={[]} now={now}/>)
        expect(container).toBeEmptyDOMElement()
    })
})

describe('Summary of a finding, with where it was first seen and resolved', () => {

    it('names the branch and the build of the discovery and of the resolution', () => {
        render(<FindingSummary finding={{
            id: 1, externalId: 'CVE-2024-38816', title: 'Path traversal', scanner: 'trivy', kind: 'IMAGE',
            location: '', url: null, maxSeverity: 'HIGH', state: 'RESOLVED',
            firstSeen: '2026-10-02T09:41:00', lastSeen: '2026-10-05T09:41:00', resolvedAt: '2026-10-06T14:12:00',
            firstSeenIn: {time: '2026-10-02T09:41:00', branch: main, validationRun: run(1, 100), build: '3.4.0'},
            resolvedIn: {time: '2026-10-06T14:12:00', branch: release, validationRun: null, build: '2.4.9'},
        }}/>)
        const [first, resolved] = screen.getAllByTestId('finding-sighting')
        expect(first).toHaveTextContent('main')
        expect(within(first).getByRole('link', {name: '3.4.0'})).toHaveAttribute('href', '/build/100')
        expect(resolved).toHaveTextContent('release-2.4')
        expect(resolved).toHaveTextContent('2.4.9')
    })
})

describe('History of a finding', () => {

    const entry = (type, extra = {}) => ({
        type,
        time: '2026-10-06T14:12:00',
        branch: main,
        validationStamp: image,
        validationRun: run(7, 102),
        build: '3.4.2',
        resolutionReason: null,
        acceptance: null,
        count: 0,
        firstTime: null,
        lastTime: null,
        firstBuild: null,
        lastBuild: null,
        ...extra,
    })

    it('shows the fix, the reopening, the acceptance and the discovery, with their builds', () => {
        mockUseQuery.mockReturnValue({
            data: {
                pageInfo: {totalSize: 4},
                pageItems: [
                    entry('REOPENED', {branch: patch, build: '2.5.3', validationRun: run(4, 203)}),
                    entry('RESOLVED', {resolutionReason: 'ABSENT'}),
                    entry('ACCEPTED', {
                        branch: release, build: '2.4.8', validationRun: null,
                        acceptance: {statement: 'Not reachable', expiresAt: '2026-12-31'},
                    }),
                    entry('DISCOVERED', {build: '3.4.0', validationRun: run(1, 100)}),
                ],
            },
            loading: false,
            finished: true,
        })
        render(<FindingHistory id={42}/>)
        expect(screen.getByTestId('finding-history-REOPENED')).toHaveTextContent('Reopened on release-2.5 in build 2.5.3')
        expect(screen.getByTestId('finding-history-RESOLVED')).toHaveTextContent('Fixed on main in build 3.4.2, no longer reported by SECURITY.IMAGE #1')
        expect(screen.getByTestId('finding-history-ACCEPTED')).toHaveTextContent('until 2026-12-31')
        expect(screen.getByTestId('finding-history-ACCEPTED')).toHaveTextContent('Not reachable')
        expect(screen.getByTestId('finding-history-DISCOVERED')).toHaveTextContent('Discovered on main in build 3.4.0')
        expect(screen.queryByRole('button', {name: 'More history'})).not.toBeInTheDocument()
    })

    it('expands a group of observations into them', () => {
        mockUseQuery.mockReturnValue({
            data: {
                pageInfo: {totalSize: 30},
                pageItems: [
                    entry('OBSERVATIONS', {
                        validationRun: null, build: null, count: 9,
                        firstTime: '2026-10-03T09:00:00', lastTime: '2026-10-05T09:00:00',
                        firstBuild: '3.4.0', lastBuild: '3.4.1',
                    }),
                ],
            },
            loading: false,
            finished: true,
        })
        render(<FindingHistory id={42}/>)
        const group = screen.getByTestId('finding-history-group')
        expect(group).toHaveTextContent('Reported by 9 scans on main, builds 3.4.0 → 3.4.1')
        mockUseQuery.mockReturnValue({data: {pageInfo: {totalSize: 0}, pageItems: []}, loading: false, finished: true})
        fireEvent.click(within(group).getByRole('button', {name: 'Show'}))
        expect(mockUseQuery.mock.lastCall[1].variables).toEqual({
            id: 42,
            size: 20,
            branchId: 10,
            validationStampId: 20,
            from: '2026-10-03T09:00:00',
            to: '2026-10-05T09:00:00',
        })
        expect(screen.getByRole('button', {name: 'More history'})).toBeInTheDocument()
    })
})
