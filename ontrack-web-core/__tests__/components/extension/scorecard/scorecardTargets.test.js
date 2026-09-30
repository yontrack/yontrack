import {
    defaultSetParam,
    estateMarkerHeadline,
    hasTargets,
    headlineTone,
    judgedReadings,
    orderedSets,
    PROJECT_SET_PARAM,
    resolveSet,
    ringSegments,
    setParam,
    setTargetCount,
    sortedReadings,
    sparklineDomain,
    sparklineZone,
    targetHeadline,
    targetLineText,
} from "@components/extension/scorecard/scorecardModel";

const reading = (props = {}) => ({
    key: 'delivery.leadTime',
    value: 7200,
    basis: 'MEASURED',
    unknownReason: null,
    direction: 'LOWER_IS_BETTER',
    target: null,
    targetMet: null,
    ...props,
})

const projectSet = {name: 'Project', estate: null, readings: [reading()]}
const estateSet = (name, readings = []) => ({name, estate: {name, marker: null}, readings})

describe('setTargetCount', () => {
    it('counts the met readings among the judged ones', () => {
        expect(setTargetCount(estateSet('E', [
            reading({target: 86400, targetMet: true}),
            reading({key: 'delivery.frequency', target: 5, targetMet: false}),
            reading({key: 'delivery.successRate', target: 80, targetMet: true}),
            reading({key: 'quality.testFlakiness'}),
        ]))).toEqual({met: 2, count: 3, notJudged: 0})
    })

    it('leaves out a reading with a target but no judgement, unknown or with no failure, and says so', () => {
        expect(setTargetCount(estateSet('E', [
            reading({target: 86400, targetMet: true}),
            reading({key: 'delivery.mttr', target: 86400, targetMet: null, value: null, basis: 'UNKNOWN', unknownReason: 'NO_FAILURE'}),
            reading({key: 'delivery.frequency', target: 5, targetMet: null, value: null, basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES'}),
        ]))).toEqual({met: 1, count: 1, notJudged: 2})
    })

    it('judges nothing in the Project set', () => {
        expect(setTargetCount(projectSet)).toEqual({met: 0, count: 0, notJudged: 0})
    })
})

describe('targetHeadline', () => {
    it('is a count of the targets met, never a score', () => {
        expect(targetHeadline({met: 3, count: 4, notJudged: 0})).toEqual({headline: '3 of 4 targets met', secondary: null})
        expect(targetHeadline({met: 1, count: 1, notJudged: 0})).toEqual({headline: '1 of 1 target met', secondary: null})
    })

    it('mentions the readings with a target and no judgement as secondary text', () => {
        expect(targetHeadline({met: 3, count: 4, notJudged: 1})).toEqual({headline: '3 of 4 targets met', secondary: '1 not judged'})
    })
})

describe('headlineTone', () => {
    it('is met when every target is met', () => {
        expect(headlineTone({met: 4, count: 4})).toBe('met')
    })
    it('is missed when fewer than half the targets are met', () => {
        expect(headlineTone({met: 1, count: 4})).toBe('missed')
        expect(headlineTone({met: 0, count: 1})).toBe('missed')
    })
    it('is normal otherwise', () => {
        expect(headlineTone({met: 2, count: 4})).toBe('normal')
        expect(headlineTone({met: 3, count: 4})).toBe('normal')
    })
})

describe('judgedReadings', () => {
    it('keeps the readings with a judgement, in their order', () => {
        const readings = [
            reading({target: 1, targetMet: true}),
            reading({key: 'quality.testFlakiness'}),
            reading({key: 'delivery.frequency', target: 5, targetMet: false}),
        ]
        expect(judgedReadings(estateSet('E', readings)).map(it => it.key)).toEqual(['delivery.leadTime', 'delivery.frequency'])
    })
})

describe('ringSegments', () => {
    it('draws the met segments first, then the missed ones, with gaps between them', () => {
        const segments = ringSegments({met: 1, count: 3}, 0.02)
        expect(segments.map(it => it.met)).toEqual([true, false, false])
        expect(segments[0].start).toBeCloseTo(0)
        expect(segments[0].length).toBeCloseTo(1 / 3 - 0.02)
        expect(segments[1].start).toBeCloseTo(1 / 3)
        expect(segments[2].start).toBeCloseTo(2 / 3)
    })

    it('draws a single target as a full circle', () => {
        expect(ringSegments({met: 1, count: 1}, 0.02)).toEqual([{met: true, start: 0, length: 1}])
    })

    it('draws nothing with no judged reading', () => {
        expect(ringSegments({met: 0, count: 0}, 0.02)).toEqual([])
    })
})

describe('orderedSets', () => {
    it('gives the Project set first, then the estates by name', () => {
        const scorecard = {sets: [estateSet('Demo products'), projectSet, estateSet('Demo production')]}
        expect(orderedSets(scorecard).map(it => it.name)).toEqual(['Project', 'Demo production', 'Demo products'])
        expect(orderedSets(null)).toEqual([])
    })
})

describe('the selected set', () => {
    const sets = [projectSet, estateSet('Demo production'), estateSet('Demo products')]

    it('is named in the URL as project, or by the name of its estate', () => {
        expect(setParam(projectSet)).toBe(PROJECT_SET_PARAM)
        expect(PROJECT_SET_PARAM).toBe('project')
        expect(setParam(sets[1])).toBe('Demo production')
    })

    it('is by default the first estate by name', () => {
        expect(defaultSetParam([projectSet, estateSet('Demo products'), estateSet('Demo production')])).toBe('Demo production')
    })

    it('is by default the Project set for a project in no estate', () => {
        expect(defaultSetParam([projectSet])).toBe('project')
        expect(defaultSetParam([])).toBe('project')
    })

    it('is the one asked for when there is one', () => {
        expect(resolveSet(sets, 'project')).toEqual({set: projectSet, unknown: false})
        expect(resolveSet(sets, 'Demo products')).toEqual({set: sets[2], unknown: false})
    })

    it('falls back on the default for an unknown one, and says so', () => {
        expect(resolveSet(sets, 'Gone')).toEqual({set: sets[1], unknown: true})
    })

    it('is the default when none is asked for', () => {
        expect(resolveSet(sets, null)).toEqual({set: sets[1], unknown: false})
        expect(resolveSet(sets, undefined)).toEqual({set: sets[1], unknown: false})
        expect(resolveSet(sets, '')).toEqual({set: sets[1], unknown: false})
    })

    it('is nothing when there is no set', () => {
        expect(resolveSet([], 'project')).toEqual({set: null, unknown: false})
    })
})

describe('estateMarkerHeadline', () => {
    it('says what an estate is read up to', () => {
        expect(estateMarkerHeadline({kind: 'PROMOTION', levelName: 'GOLD'})).toBe('Up to promotion GOLD')
        expect(estateMarkerHeadline({kind: 'ENVIRONMENT', environment: 'production', qualifier: ''})).toBe('Up to environment production')
        expect(estateMarkerHeadline({kind: 'ENVIRONMENT', environment: 'production', qualifier: 'eu'})).toBe('Up to environment production [eu]')
        expect(estateMarkerHeadline(null)).toBe('Up to the default marker')
    })
})

describe('targetLineText', () => {
    it('gives the target with its direction', () => {
        expect(targetLineText(reading({target: 86400}))).toBe('target ≤ 1d')
        expect(targetLineText(reading({key: 'delivery.successRate', direction: 'HIGHER_IS_BETTER', target: 80}))).toBe('target ≥ 80%')
    })
    it('says when the set has no target for the reading', () => {
        expect(targetLineText(reading())).toBe('no target in this set')
    })
})

describe('sparklineZone', () => {
    it('shades below the target when lower is better, above it when higher is', () => {
        expect(sparklineZone(reading({target: 86400}))).toBe('below')
        expect(sparklineZone(reading({direction: 'HIGHER_IS_BETTER', target: 80}))).toBe('above')
    })
    it('shades nothing with no target', () => {
        expect(sparklineZone(reading())).toBeNull()
    })
})

describe('sparklineDomain', () => {
    const points = [{day: 'a', value: 10}, {day: 'b', value: null}, {day: 'c', value: 20}]

    it('pads the values so that the line never touches the edges', () => {
        expect(sparklineDomain(points, null)).toEqual([8.5, 21.5])
    })

    it('holds the target, so that the zone meeting it always shows', () => {
        expect(sparklineDomain(points, 30)).toEqual([7, 33])
    })

    it('pads a flat line', () => {
        expect(sparklineDomain([{day: 'a', value: 5}, {day: 'b', value: 5}], null)).toEqual([4, 6])
    })
})

describe('sortedReadings', () => {
    it('gives the readings of a set in the catalogue order, those out of it last', () => {
        const set = estateSet('E', [reading({key: 'other'}), reading({key: 'quality.testPassRate'}), reading({key: 'delivery.leadTime'})])
        expect(sortedReadings(set).map(it => it.key)).toEqual(['delivery.leadTime', 'quality.testPassRate', 'other'])
        expect(sortedReadings(null)).toEqual([])
    })
})

describe('hasTargets', () => {
    it('tells whether a set has a target, judged or not', () => {
        expect(hasTargets(estateSet('E', [reading({target: 1, targetMet: null, basis: 'UNKNOWN'})]))).toBe(true)
        expect(hasTargets(estateSet('E', [reading()]))).toBe(false)
        expect(hasTargets(projectSet)).toBe(false)
    })
})
