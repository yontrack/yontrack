import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"
import FindingExposureTable from "@components/extension/findings/finding/FindingExposureTable"
import FindingExposureTimeline from "@components/extension/findings/finding/FindingExposureTimeline"

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

const DAY = 86400
const now = new Date('2026-10-20T12:00:00Z')
const gold = {name: 'GOLD builds', data: {withPromotionLevel: 'GOLD'}}
const image = {id: 20, name: 'SCAN'}

const period = (props) => ({
    startedAt: '2026-10-02T12:00:00',
    startedBy: null,
    startedInBuild: '3.4.0',
    endedAt: '2026-10-06T12:00:00',
    endedBy: {id: 7, runOrder: 1, build: {id: 102}},
    endedInBuild: '3.4.2',
    resolutionReason: 'ABSENT',
    ongoing: false,
    durationSeconds: 4 * DAY,
    acceptedSpans: [],
    ...props,
})

const exposure = (id, name, periods, props) => ({
    branch: {id, name},
    validationStamp: image,
    state: 'RESOLVED',
    accepted: false,
    since: '2026-10-02T12:00:00',
    resolvedAt: '2026-10-06T12:00:00',
    resolutionReason: 'ABSENT',
    periods,
    ...props,
})

const matched = exposure(1, 'main', [period({
    boundary: {
        status: 'MATCHED',
        time: '2026-10-14T12:00:00',
        durationSeconds: 12 * DAY,
        build: {id: 104, name: '104', displayName: '3.4.4'},
    },
})])

const reopened = exposure(2, 'release-2.5', [
    period({startedInBuild: '2.5.0', endedInBuild: '2.5.1', durationSeconds: 2 * DAY, boundary: {status: 'NOT_REACHED'}}),
    period({
        startedAt: '2026-10-08T12:00:00', startedInBuild: '2.5.3',
        endedAt: null, endedBy: null, endedInBuild: null, ongoing: true, durationSeconds: 12 * DAY, boundary: null,
    }),
], {state: 'EXPOSED', resolvedAt: null, resolutionReason: null})

const noneYet = exposure(3, 'release-2.4', [period({
    startedInBuild: '2.4.1', endedInBuild: '2.4.3', durationSeconds: 7 * DAY,
    boundary: {status: 'NONE_YET', durationSeconds: 17 * DAY},
})])

const notApplicable = exposure(4, 'release-2.3', [period({
    startedInBuild: '2.3.8', endedInBuild: '2.3.9', durationSeconds: 2 * DAY,
    boundary: {status: 'NOT_APPLICABLE', reason: 'No GOLD on this branch'},
})])

const onFixingBuild = exposure(5, 'release-2.2', [period({
    boundary: {
        status: 'MATCHED',
        time: '2026-10-07T12:00:00',
        durationSeconds: 5 * DAY,
        build: {id: 102, name: '102', displayName: '3.4.2'},
    },
})])

const lane = (exposure) => screen.getByTestId(`finding-exposure-lane-${exposure.branch.name}-${image.name}`)

describe('FindingExposureTimeline with a filter', () => {

    const timeline = (...exposures) =>
        render(<FindingExposureTimeline exposures={exposures} filter={gold} now={now}/>)

    it('goes red to a scan fix, then hatched to a solid dot, for a matching build', () => {
        timeline(matched)
        const main = within(lane(matched))
        expect(main.getByTestId('finding-exposure-scan-fix')).toBeInTheDocument()
        expect(main.getByTestId('finding-exposure-wait')).toBeInTheDocument()
        expect(main.getByTestId('finding-exposure-fix')).toBeInTheDocument()
        expect(lane(matched)).toHaveTextContent('3.4.0 → 3.4.2 → GOLD in 3.4.4 · 4 days, 12 until GOLD')
        expect(main.getByRole('img')).toHaveAttribute(
            'aria-label',
            'Exposed from 3.4.0, fixed in 3.4.2, 4 days. In a GOLD build, 3.4.4, on 14 Oct, 12 days.',
        )
    })

    it('has one solid dot and no hatching when the fixing build is the matching one', () => {
        timeline(onFixingBuild)
        const row = within(lane(onFixingBuild))
        expect(row.getByTestId('finding-exposure-fix')).toBeInTheDocument()
        expect(row.queryByTestId('finding-exposure-scan-fix')).toBeNull()
        expect(row.queryByTestId('finding-exposure-wait')).toBeNull()
    })

    it('has no solid dot while no build matches yet', () => {
        timeline(noneYet)
        const row = within(lane(noneYet))
        expect(row.getByTestId('finding-exposure-wait')).toBeInTheDocument()
        expect(row.queryByTestId('finding-exposure-fix')).toBeNull()
        expect(lane(noneYet)).toHaveTextContent('no GOLD build yet · 7 days, 17 so far')
    })

    it('says that a reopening came first', () => {
        timeline(reopened)
        const row = within(lane(reopened))
        expect(row.getByTestId('finding-exposure-wait')).toBeInTheDocument()
        expect(row.queryByTestId('finding-exposure-fix')).toBeNull()
        expect(row.getAllByRole('img')[0]).toHaveAccessibleName(
            'Exposed from 2.5.0, fixed in 2.5.1, 2 days. Reopened in 2.5.3 before reaching a GOLD build.'
        )
    })

    it('ends on a scan fix without any hatching when the filter does not apply, and says why', () => {
        timeline(notApplicable)
        const row = within(lane(notApplicable))
        expect(row.getByTestId('finding-exposure-scan-fix')).toBeInTheDocument()
        expect(row.queryByTestId('finding-exposure-wait')).toBeNull()
        expect(row.queryByTestId('finding-exposure-fix')).toBeNull()
        expect(lane(notApplicable)).toHaveTextContent("2 days · doesn't apply")
        expect(row.getByRole('img')).toHaveAccessibleName(
            'Exposed from 2.3.8, fixed in 2.3.9, 2 days. The filter does not apply: No GOLD on this branch'
        )
    })

    it('has the legend of a filter', () => {
        timeline(matched)
        expect(screen.getByText('Fixed, not in a matching build')).toBeInTheDocument()
        expect(screen.getByText('Scan fix')).toBeInTheDocument()
        expect(screen.getByText('Fix in a matching build')).toBeInTheDocument()
        expect(screen.queryByText('Fixed')).toBeNull()
    })
})

describe('FindingExposureTimeline without a filter', () => {

    it('stays as it is, whatever the periods carry', () => {
        render(<FindingExposureTimeline exposures={[matched]} now={now}/>)
        const main = within(lane(matched))
        expect(main.getByTestId('finding-exposure-fix')).toBeInTheDocument()
        expect(main.queryByTestId('finding-exposure-scan-fix')).toBeNull()
        expect(main.queryByTestId('finding-exposure-wait')).toBeNull()
        expect(lane(matched)).toHaveTextContent('3.4.0 → 3.4.2 · 4 days')
        expect(lane(matched)).not.toHaveTextContent('GOLD')
        expect(screen.getByText('Fixed')).toBeInTheDocument()
        expect(screen.queryByText('Scan fix')).toBeNull()
    })
})

describe('FindingExposureTable with a filter', () => {

    const rowOf = (name) => screen.getByRole('link', {name}).closest('tr')

    beforeEach(() => {
        render(<FindingExposureTable
            exposures={[matched, reopened, noneYet, notApplicable]}
            filter={gold}
        />)
    })

    it('has a column after the one of the fix', () => {
        const titles = screen.getAllByRole('columnheader').map(it => it.textContent)
        expect(titles.indexOf('In a matching build')).toBe(titles.indexOf('Fixed in') + 1)
    })

    it('gives the matching build, and the filtered duration as a second line', () => {
        const row = rowOf('main')
        expect(within(row).getByTestId('finding-exposure-matching-build')).toHaveTextContent('3.4.4GOLD on 14 Oct')
        const duration = within(row).getByTestId('finding-exposure-duration')
        expect(duration).toHaveTextContent('4 days')
        expect(within(row).getByTestId('finding-exposure-duration-filtered')).toHaveTextContent('12 days until GOLD')
    })

    it('shows the last period which ended of a reopened exposure', () => {
        const row = rowOf('release-2.5')
        expect(row).toHaveTextContent('2.5.1, reopened')
        expect(within(row).getByTestId('finding-exposure-matching-build')).toHaveTextContent('Not reachedreopened in 2.5.3 first')
        expect(within(row).queryByTestId('finding-exposure-duration-filtered')).toBeNull()
    })

    it('says when no build matches yet', () => {
        const row = rowOf('release-2.4')
        expect(within(row).getByTestId('finding-exposure-matching-build')).toHaveTextContent('None yet')
        expect(within(row).getByTestId('finding-exposure-duration-filtered')).toHaveTextContent('17 days so far until GOLD')
    })

    it('says why the filter does not apply, without any filtered duration', () => {
        const row = rowOf('release-2.3')
        expect(within(row).getByTestId('finding-exposure-matching-build')).toHaveTextContent("Doesn't applyNo GOLD on this branch")
        expect(within(row).queryByTestId('finding-exposure-duration-filtered')).toBeNull()
    })
})

describe('FindingExposureTable without a filter', () => {

    it('stays as it is, whatever the periods carry', () => {
        render(<FindingExposureTable exposures={[matched, reopened]}/>)
        expect(screen.queryByRole('columnheader', {name: 'In a matching build'})).toBeNull()
        expect(screen.queryByTestId('finding-exposure-duration-filtered')).toBeNull()
        const row = screen.getByRole('link', {name: 'release-2.5'}).closest('tr')
        expect(row).toHaveTextContent('Not resolved')
        expect(row).not.toHaveTextContent('2.5.1, reopened')
    })
})
