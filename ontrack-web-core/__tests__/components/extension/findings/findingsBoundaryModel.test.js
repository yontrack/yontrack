import {
    boundaryBarText,
    boundaryDurationText,
    boundaryFilterCriteria,
    boundaryFilterLabel,
    boundaryFilterShortName,
    boundaryLabel,
    boundaryMatchedText,
    exposureTimeline,
    lastEndedPeriod,
    parseBoundaryFilter,
    sanitizeBoundaryFilter,
    savedBoundaryFilters,
    STANDARD_BUILD_FILTER_TYPE,
    withBoundaries,
} from "@components/extension/findings/findingsModel"

const DAY = 86400
const gold = {name: 'GOLD builds', data: {withPromotionLevel: 'GOLD'}}

describe('boundaryFilterShortName', () => {

    it('is the promotion level when the filter has only this criterion', () => {
        expect(boundaryFilterShortName({data: {withPromotionLevel: 'GOLD'}})).toBe('GOLD')
    })

    it('is "a matching build" for anything else', () => {
        expect(boundaryFilterShortName({data: {withPromotionLevel: 'GOLD', withDisplayName: '^3'}})).toBe('a matching build')
        expect(boundaryFilterShortName({data: {withValidationStamp: 'DAST'}})).toBe('a matching build')
        expect(boundaryFilterShortName(undefined)).toBe('a matching build')
    })
})

describe('boundaryFilterLabel', () => {

    it('is the name of a saved filter', () => {
        expect(boundaryFilterLabel(gold)).toBe('GOLD builds')
    })

    it('says the promotion of a filter without a name', () => {
        expect(boundaryFilterLabel({data: {withPromotionLevel: 'BRONZE'}})).toBe('With promotion BRONZE')
    })

    it('stays generic for any other filter without a name', () => {
        expect(boundaryFilterLabel({data: {withDisplayName: '^3'}})).toBe('Custom filter')
    })

    it('says when there is no filter', () => {
        expect(boundaryFilterLabel(undefined)).toBe('No filter (all builds)')
    })
})

describe('boundaryFilterCriteria', () => {

    it('keeps the usable criteria only: no count, no name, no since', () => {
        expect(boundaryFilterCriteria({
            count: 10,
            name: 'Saved',
            sincePromotionLevel: 'BRONZE',
            sinceValidationStamp: 'DAST',
            sinceProperty: 'p',
            withPromotionLevel: 'GOLD',
            withDisplayName: '^3',
            linkedTo: 'p:*',
            assisted: 'YES',
            actor: 'HUMAN',
            unknownField: 'x',
        })).toEqual({
            withPromotionLevel: 'GOLD',
            withDisplayName: '^3',
            linkedTo: 'p:*',
            assisted: 'YES',
            actor: 'HUMAN',
        })
    })

    it('leaves out what is empty', () => {
        expect(boundaryFilterCriteria({withPromotionLevel: '', withDisplayName: null, actor: undefined})).toEqual({})
        expect(boundaryFilterCriteria(undefined)).toEqual({})
    })

    it('writes the dates as days, as the server reads them', () => {
        expect(boundaryFilterCriteria({afterDate: '2026-10-02T12:00:00', beforeDate: '2026-10-05'}))
            .toEqual({afterDate: '2026-10-02', beforeDate: '2026-10-05'})
    })
})

describe('sanitizeBoundaryFilter', () => {

    it('keeps the name and the usable criteria', () => {
        expect(sanitizeBoundaryFilter({name: 'GOLD builds', data: {withPromotionLevel: 'GOLD', count: 5}})).toEqual(gold)
    })

    it('refuses a filter using a since criterion, a filter without any criterion, and anything else', () => {
        expect(sanitizeBoundaryFilter({data: {withPromotionLevel: 'GOLD', sincePromotionLevel: 'BRONZE'}})).toBeUndefined()
        expect(sanitizeBoundaryFilter({data: {}})).toBeUndefined()
        expect(sanitizeBoundaryFilter({data: {count: 10}})).toBeUndefined()
        expect(sanitizeBoundaryFilter({})).toBeUndefined()
        expect(sanitizeBoundaryFilter('GOLD')).toBeUndefined()
        expect(sanitizeBoundaryFilter(null)).toBeUndefined()
    })
})

describe('parseBoundaryFilter', () => {

    it('reads a permalink', () => {
        expect(parseBoundaryFilter(JSON.stringify(gold))).toEqual(gold)
    })

    it('ignores what is not a filter', () => {
        expect(parseBoundaryFilter('{not json')).toBeUndefined()
        expect(parseBoundaryFilter(undefined)).toBeUndefined()
        expect(parseBoundaryFilter('{"data":{"sinceProperty":"p"}}')).toBeUndefined()
    })
})

describe('savedBoundaryFilters', () => {

    const resource = (name, data, type = STANDARD_BUILD_FILTER_TYPE, error = null) => ({name, data, type, error})

    it('keeps the standard filters only, without those in error', () => {
        expect(savedBoundaryFilters([{
            name: 'main',
            buildFilterResources: [
                resource('Other type', {count: 5}, 'net.nemerosa.ontrack.service.PromotionLevelBuildFilterProvider'),
                resource('In error', {withPromotionLevel: 'GOLD'}, STANDARD_BUILD_FILTER_TYPE, 'Broken'),
                resource('GOLD builds', {count: 10, withPromotionLevel: 'GOLD'}),
            ],
        }]).map(it => it.name)).toEqual(['GOLD builds'])
    })

    it('copies the criteria, with the name of the filter', () => {
        const [saved] = savedBoundaryFilters([{
            name: 'main',
            buildFilterResources: [resource('GOLD builds', {name: 'GOLD builds', count: 10, withPromotionLevel: 'GOLD'})],
        }])
        expect(saved.filter).toEqual(gold)
        expect(saved.branches).toEqual(['main'])
        expect(saved.disabledReason).toBeUndefined()
    })

    it('lists once a filter with the same name and the same criteria on several branches', () => {
        const saved = savedBoundaryFilters([
            {name: 'main', buildFilterResources: [resource('GOLD builds', {withPromotionLevel: 'GOLD', count: 10})]},
            {name: 'release-2.4', buildFilterResources: [resource('GOLD builds', {count: 5, withPromotionLevel: 'GOLD'})]},
        ])
        expect(saved).toHaveLength(1)
        expect(saved[0].branches).toEqual(['main', 'release-2.4'])
    })

    it('lists apart, each with its branch, the same name with different criteria', () => {
        const saved = savedBoundaryFilters([
            {name: 'main', buildFilterResources: [resource('Released', {withPromotionLevel: 'GOLD'})]},
            {name: 'release-2.4', buildFilterResources: [resource('Released', {withPromotionLevel: 'SILVER'})]},
        ])
        expect(saved.map(it => it.branches)).toEqual([['main'], ['release-2.4']])
        expect(saved.map(it => it.filter.data.withPromotionLevel)).toEqual(['GOLD', 'SILVER'])
    })

    it('lists a filter using a since criterion, which cannot be selected and says why', () => {
        const [saved] = savedBoundaryFilters([{
            name: 'main',
            buildFilterResources: [resource('Since last RC', {sincePromotionLevel: 'RC', withPromotionLevel: 'GOLD'})],
        }])
        expect(saved.name).toBe('Since last RC')
        expect(saved.filter).toBeUndefined()
        expect(saved.disabledReason).toContain('since')
    })
})

describe('savedBoundaryFilters, a filter without any usable criterion', () => {

    it('cannot be selected, and says why', () => {
        const [saved] = savedBoundaryFilters([{
            name: 'main',
            buildFilterResources: [{name: 'Last 5', type: STANDARD_BUILD_FILTER_TYPE, data: {count: 5}, error: null}],
        }])
        expect(saved.filter).toBeUndefined()
        expect(saved.disabledReason).toContain("can't set a boundary")
    })
})

describe('boundary texts', () => {

    const matched = {status: 'MATCHED', time: '2026-10-14T12:00:00', durationSeconds: 12 * DAY, build: {id: 104, name: '104', displayName: '3.4.4'}}
    const noneYet = {status: 'NONE_YET', durationSeconds: 17 * DAY}
    const notReached = {status: 'NOT_REACHED'}
    const notApplicable = {status: 'NOT_APPLICABLE', reason: 'No GOLD on this branch'}
    const period = {durationSeconds: 4 * DAY, startedInBuild: '3.4.0', endedInBuild: '3.4.2'}

    it('gives the filtered duration', () => {
        expect(boundaryDurationText(matched, 'GOLD')).toBe('12 days until GOLD')
        expect(boundaryDurationText(noneYet, 'GOLD')).toBe('17 days so far until GOLD')
        expect(boundaryDurationText(matched, 'a matching build')).toBe('12 days until a matching build')
    })

    it('gives no filtered duration otherwise', () => {
        expect(boundaryDurationText(notReached, 'GOLD')).toBe('')
        expect(boundaryDurationText(notApplicable, 'GOLD')).toBe('')
        expect(boundaryDurationText(null, 'GOLD')).toBe('')
    })

    it('says when the build started to match', () => {
        expect(boundaryMatchedText(matched, 'GOLD')).toBe('GOLD on 14 Oct')
        expect(boundaryMatchedText(matched, 'a matching build')).toBe('matching on 14 Oct')
    })

    it('completes the label of the bar', () => {
        expect(boundaryBarText(period, matched, 'GOLD')).toBe('4 days, 12 until GOLD')
        expect(boundaryBarText(period, noneYet, 'GOLD')).toBe('no GOLD build yet · 4 days, 17 so far')
        expect(boundaryBarText(period, noneYet, 'a matching build')).toBe('no matching build yet · 4 days, 17 so far')
        expect(boundaryBarText(period, notReached, 'GOLD')).toBe('4 days · not reached')
        expect(boundaryBarText(period, notApplicable, 'GOLD')).toBe("4 days · doesn't apply")
    })

    it('keeps the units when they differ', () => {
        expect(boundaryBarText({durationSeconds: 19 * 3600}, matched, 'GOLD')).toBe('19 h, 12 days until GOLD')
    })

    it('says the same in words for the label of the bars and their tooltips', () => {
        expect(boundaryLabel(matched, 'GOLD')).toBe('In a GOLD build, 3.4.4, on 14 Oct, 12 days.')
        expect(boundaryLabel(matched, 'a matching build')).toBe('In a matching build, 3.4.4, on 14 Oct, 12 days.')
        expect(boundaryLabel(noneYet, 'GOLD')).toBe('No GOLD build yet, 17 days so far.')
        expect(boundaryLabel(notReached, 'GOLD', {startedInBuild: '2.5.3'})).toBe('Reopened in 2.5.3 before reaching a GOLD build.')
        expect(boundaryLabel(notApplicable, 'GOLD')).toBe('The filter does not apply: No GOLD on this branch')
        expect(boundaryLabel(null, 'GOLD')).toBe('')
    })

    it('uses "an" where the name needs it', () => {
        expect(boundaryLabel(noneYet, 'ACCEPTANCE')).toBe('No ACCEPTANCE build yet, 17 days so far.')
        expect(boundaryLabel(notReached, 'ACCEPTANCE', {startedInBuild: '2'})).toBe('Reopened in 2 before reaching an ACCEPTANCE build.')
    })
})

describe('exposureTimeline with a filter', () => {

    const now = new Date('2026-10-21T00:00:00Z')
    const branch = {id: 10, name: 'main'}
    const stamp = {id: 20, name: 'SCAN'}
    const period = (props) => ({
        startedAt: '2026-10-01T00:00:00',
        endedAt: '2026-10-06T00:00:00',
        endedBy: {id: 7, build: {id: 102}},
        durationSeconds: 5 * DAY,
        ongoing: false,
        acceptedSpans: [],
        ...props,
    })
    const lane = (periods, options) =>
        exposureTimeline([{branch, validationStamp: stamp, periods}], now, options).lanes[0]

    // The axis runs on 20 days: one day is 5%

    it('leaves the bars as they are without a filter, whatever the boundary says', () => {
        const [bar] = lane([period({boundary: {status: 'NONE_YET'}})]).bars
        expect(bar.fixed).toBe(true)
        expect(bar.fixDot).toBe('SOLID')
        expect(bar.wait).toBeNull()
        expect(bar.matchDot).toBeNull()
        expect(bar.boundary).toBeNull()
    })

    it('goes through a scan fix and hatching to the matching build', () => {
        const [bar] = lane([period({
            boundary: {status: 'MATCHED', time: '2026-10-11T00:00:00', build: {id: 104}},
        })], {filtered: true}).bars
        expect(bar.width).toBeCloseTo(25)
        expect(bar.fixDot).toBe('HOLLOW')
        expect(bar.wait.left).toBeCloseTo(25)
        expect(bar.wait.width).toBeCloseTo(25)
        expect(bar.matchDot).toBeCloseTo(50)
        expect(bar.onFixingBuild).toBe(false)
    })

    it('has one solid dot at the fix when the boundary is the fixing build itself', () => {
        const [bar] = lane([period({
            boundary: {status: 'MATCHED', time: '2026-10-11T00:00:00', build: {id: 102}},
        })], {filtered: true}).bars
        expect(bar.fixDot).toBe('SOLID')
        expect(bar.wait).toBeNull()
        expect(bar.matchDot).toBeNull()
        expect(bar.onFixingBuild).toBe(true)
    })

    it('never takes a purged fixing build for the boundary', () => {
        const [bar] = lane([period({
            endedBy: null,
            boundary: {status: 'MATCHED', time: '2026-10-11T00:00:00', build: {id: 104}},
        })], {filtered: true}).bars
        expect(bar.fixDot).toBe('HOLLOW')
        expect(bar.wait).not.toBeNull()
        expect(bar.onFixingBuild).toBe(false)
    })

    it('runs the hatching to now, without any solid dot, when no build matches yet', () => {
        const [bar] = lane([period({boundary: {status: 'NONE_YET', durationSeconds: DAY}})], {filtered: true}).bars
        expect(bar.fixDot).toBe('HOLLOW')
        expect(bar.wait.left).toBeCloseTo(25)
        expect(bar.wait.width).toBeCloseTo(75)
        expect(bar.matchDot).toBeNull()
    })

    it('stops the hatching at the reopening when not reached', () => {
        const {bars} = lane([
            period({boundary: {status: 'NOT_REACHED'}}),
            period({startedAt: '2026-10-11T00:00:00', endedAt: null, endedBy: null, ongoing: true, boundary: null}),
        ], {filtered: true})
        expect(bars[0].fixDot).toBe('HOLLOW')
        expect(bars[0].wait.width).toBeCloseTo(25)
        expect(bars[0].matchDot).toBeNull()
        expect(bars[0].nextPeriod.startedAt).toBe('2026-10-11T00:00:00')
    })

    it('ends on a scan fix, without any hatching, when the filter does not apply', () => {
        const [bar] = lane([period({boundary: {status: 'NOT_APPLICABLE', reason: 'No GOLD'}})], {filtered: true}).bars
        expect(bar.fixDot).toBe('HOLLOW')
        expect(bar.wait).toBeNull()
        expect(bar.matchDot).toBeNull()
    })

    it('renders an open period as today', () => {
        const [bar] = lane([
            period({endedAt: null, endedBy: null, ongoing: true, boundary: null}),
        ], {filtered: true}).bars
        expect(bar.fixed).toBe(false)
        expect(bar.fixDot).toBeNull()
        expect(bar.wait).toBeNull()
        expect(bar.width).toBeCloseTo(100)
    })

    it('stretches the axis to a boundary later than now', () => {
        const timeline = exposureTimeline([{
            branch, validationStamp: stamp,
            periods: [period({boundary: {status: 'MATCHED', time: '2026-10-31T00:00:00', build: {id: 104}}})],
        }], now, {filtered: true})
        expect(timeline.end.toISOString()).toBe('2026-10-31T00:00:00.000Z')
    })
})

describe('lastEndedPeriod', () => {

    it('is the last period which ended', () => {
        const fixed = {endedAt: '2026-10-06T00:00:00'}
        const open = {endedAt: null}
        expect(lastEndedPeriod([fixed, open])).toBe(fixed)
        expect(lastEndedPeriod([open, fixed])).toBe(fixed)
        expect(lastEndedPeriod([open])).toBeNull()
        expect(lastEndedPeriod(undefined)).toBeNull()
    })
})

describe('withBoundaries', () => {

    const branch = {id: 10}
    const stamp = {id: 20}
    const startedAt = '2026-10-01T00:00:00'
    const boundary = {status: 'NONE_YET'}

    it('adds the boundaries to the periods they belong to, and nothing else changes', () => {
        const period = {startedAt, durationSeconds: 5}
        const other = {startedAt: '2026-10-11T00:00:00', durationSeconds: 7}
        const [exposure] = withBoundaries(
            [{branch, validationStamp: stamp, state: 'RESOLVED', periods: [period, other]}],
            [{branch, validationStamp: stamp, periods: [{startedAt, boundary}]}],
        )
        expect(exposure.state).toBe('RESOLVED')
        expect(exposure.periods[0]).toEqual({...period, boundary})
        expect(exposure.periods[1]).toEqual(other)
    })
})
