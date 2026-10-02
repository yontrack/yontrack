import {
    readingDescription,
    readingCoverage,
    rungDescription,
    rungOf,
    SECURITY_MATURITY_RUNGS,
    securityMaturityRungs,
    targetWords,
} from "@components/extension/scorecard/scorecardModel";

describe('SECURITY_MATURITY_RUNGS', () => {

    it('names and describes every rung, from 0 up', () => {
        expect(SECURITY_MATURITY_RUNGS.map(it => it.name)).toEqual(['None', 'Reported', 'Covered', 'Gating'])
        SECURITY_MATURITY_RUNGS.forEach(rung => {
            expect(rung.description).toEqual(expect.any(String))
        })
    })

    it('builds the definition of the security maturity from the rungs', () => {
        const description = readingDescription('security.maturity')
        SECURITY_MATURITY_RUNGS.forEach(rung => {
            expect(description).toContain(rung.name.toLowerCase())
        })
    })
})

describe('rungOf', () => {

    it('gives the rung of a value of the security maturity', () => {
        expect(rungOf('security.maturity', 0)).toEqual(0)
        expect(rungOf('security.maturity', 3)).toEqual(3)
    })

    it('has no rung for a value between two rungs, out of the ladder, or of another reading', () => {
        expect(rungOf('security.maturity', 2.5)).toBeNull()
        expect(rungOf('security.maturity', 4)).toBeNull()
        expect(rungOf('security.maturity', null)).toBeNull()
        expect(rungOf('delivery.leadTime', 2)).toBeNull()
    })
})

describe('rungDescription', () => {

    it('describes the covered rung in general terms with no coverage', () => {
        expect(rungDescription(2)).toMatch(/every kind of scan the estate expects/i)
        expect(rungDescription(2)).toMatch(/any recent scan with no estate/i)
    })

    it('names the expected kinds and the freshness of a coverage', () => {
        expect(rungDescription(2, {expectedKinds: ['DEPENDENCIES', 'CODE'], freshnessDays: 7}))
            .toEqual('Every expected kind scanned within the last 7 days: Dependencies, Code.')
    })

    it('says any scan covers a project when no kind is expected', () => {
        expect(rungDescription(2, {expectedKinds: [], freshnessDays: 1}))
            .toEqual('Any security scan within the last 1 day.')
    })

    it('falls back on the freshness of the settings when the coverage has none', () => {
        expect(rungDescription(2, {expectedKinds: ['CODE'], freshnessDays: null}))
            .toEqual('Every expected kind scanned within the freshness of the scorecard settings: Code.')
    })

    it('describes the other rungs the same way whatever the coverage', () => {
        const coverage = {expectedKinds: ['CODE'], freshnessDays: 7}
        ;[0, 1, 3].forEach(value => {
            expect(rungDescription(value, coverage)).toEqual(SECURITY_MATURITY_RUNGS[value].description)
        })
    })

    it('has nothing to say of a value which is no rung', () => {
        expect(rungDescription(2.5)).toBeNull()
        expect(rungDescription(7)).toBeNull()
    })
})

describe('readingCoverage', () => {

    it('takes what covered means from the details of a security maturity', () => {
        expect(readingCoverage({key: 'security.maturity', details: {expectedKinds: ['CODE'], freshnessDays: 7}}))
            .toEqual({expectedKinds: ['CODE'], freshnessDays: 7})
    })

    it('has no coverage without the details', () => {
        expect(readingCoverage({key: 'security.maturity', details: null})).toBeNull()
        expect(readingCoverage({key: 'security.maturity'})).toBeNull()
    })
})

describe('securityMaturityRungs', () => {

    it('lists the four rungs, with their descriptions in the terms of the coverage', () => {
        const rungs = securityMaturityRungs({coverage: {expectedKinds: [], freshnessDays: 7}})
        expect(rungs.map(it => it.value)).toEqual([0, 1, 2, 3])
        expect(rungs.map(it => it.name)).toEqual(['None', 'Reported', 'Covered', 'Gating'])
        expect(rungs[2].description).toEqual('Any security scan within the last 7 days.')
        rungs.forEach(it => expect(it.marks).toEqual([]))
    })

    it('marks the current rung and the target rung', () => {
        const rungs = securityMaturityRungs({current: 1, target: 2})
        expect(rungs.map(it => it.marks)).toEqual([[], ['current'], ['target'], []])
    })

    it('marks one rung as both current and target', () => {
        const rungs = securityMaturityRungs({current: 2, target: 2})
        expect(rungs[2].marks).toEqual(['current', 'target'])
    })
})

describe('targetWords', () => {

    it('says a target when lower is better', () => {
        expect(targetWords({key: 'delivery.leadTime', target: 172800, direction: 'LOWER_IS_BETTER'}))
            .toEqual('Target of this estate: 2d or less — met at or under it, missed above.')
    })

    it('says a target when higher is better', () => {
        expect(targetWords({key: 'delivery.successRate', target: 90, direction: 'HIGHER_IS_BETTER'}))
            .toEqual('Target of this estate: 90% or more — met at or above it, missed below.')
        expect(targetWords({key: 'security.maturity', target: 2, direction: 'HIGHER_IS_BETTER'}))
            .toEqual('Target of this estate: 2 · Covered or more — met at or above it, missed below.')
    })

    it('says there is no target', () => {
        expect(targetWords({key: 'quality.testPassRate', target: null, direction: 'HIGHER_IS_BETTER'}))
            .toEqual('No target set by this estate: values are shown, not judged.')
    })
})
