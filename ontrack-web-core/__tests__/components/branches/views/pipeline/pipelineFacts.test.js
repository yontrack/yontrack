import {
    buildVersion,
    filterValidations,
    nextPromotionLevel,
    topPromotionRuns,
    validationStatusId,
    validationStrip,
    visibleDecorations,
} from "@components/branches/views/pipeline/pipelineFacts"

describe('buildVersion', () => {

    it('is the display name when it differs from the build name', () => {
        expect(buildVersion({name: "20260901055547-36", displayName: "5.3.0"})).toBe("5.3.0")
    })

    it('is nothing when the project sets no release property', () => {
        // `displayName` falls back to `name` on the backend; printing it twice tells nobody anything
        const name = "20260901055547-36"
        expect(buildVersion({name, displayName: name})).toBeNull()
    })

    it('is nothing when there is no build at all', () => {
        expect(buildVersion(undefined)).toBeNull()
        expect(buildVersion({name: "x"})).toBeNull()
    })

})

describe('topPromotionRuns', () => {

    // Lowest first, the order the branch declares
    const levels = [{id: "1"}, {id: "2"}, {id: "3"}, {id: "4"}]
    const run = (levelId) => ({id: `run-${levelId}`, promotionLevel: {id: levelId}})

    it('keeps every run when they fit', () => {
        const {shown, overflow} = topPromotionRuns([run("2"), run("1")], levels)
        expect(shown.map(it => it.promotionLevel.id)).toEqual(["1", "2"])
        expect(overflow).toBe(0)
    })

    it('keeps the highest levels and counts the rest', () => {
        const {shown, overflow} = topPromotionRuns([run("1"), run("4"), run("2"), run("3")], levels)
        expect(shown.map(it => it.promotionLevel.id)).toEqual(["2", "3", "4"])
        expect(overflow).toBe(1)
    })

    it('ranks by the branch order, not by the order the runs arrive in', () => {
        // Ids are not the ranking either: a level added later has a higher id and a lower position
        const reordered = [{id: "9"}, {id: "1"}, {id: "5"}]
        const {shown} = topPromotionRuns([run("5"), run("9"), run("1")], reordered, 2)
        expect(shown.map(it => it.promotionLevel.id)).toEqual(["1", "5"])
    })

    it('keeps a run whose level the branch does not list rather than dropping it', () => {
        const {shown, overflow} = topPromotionRuns([run("1"), run("99")], levels, 3)
        expect(shown.map(it => it.promotionLevel.id)).toEqual(["99", "1"])
        expect(overflow).toBe(0)
    })

    it('says nothing for a build with no promotions', () => {
        expect(topPromotionRuns([], levels)).toEqual({shown: [], overflow: 0})
        expect(topPromotionRuns(undefined, undefined)).toEqual({shown: [], overflow: 0})
    })

})

describe('filterValidations', () => {

    const validations = [
        {validationStamp: {name: "BUILD"}},
        {validationStamp: {name: "SMOKE"}},
    ]

    it('keeps everything when no filter is selected', () => {
        expect(filterValidations(validations, undefined)).toHaveLength(2)
    })

    it('keeps only the stamps the filter names', () => {
        expect(filterValidations(validations, {vsNames: ["SMOKE"]})).toEqual([validations[1]])
    })

    it('keeps nothing for a filter which names nothing', () => {
        // An empty filter is a choice, unlike no filter at all
        expect(filterValidations(validations, {vsNames: []})).toEqual([])
    })

})

describe('validationStatusId', () => {

    it('reads the last status of the most recent run', () => {
        expect(validationStatusId({
            validationRuns: [{lastStatus: {statusID: {id: 'FAILED'}}}],
        })).toBe('FAILED')
    })

    it('is NONE for a stamp which never ran, never an outcome', () => {
        expect(validationStatusId({validationRuns: []})).toBe('NONE')
        expect(validationStatusId(undefined)).toBe('NONE')
    })

})

describe('validationStrip', () => {

    const validation = (name, statusId) => ({
        validationStamp: {id: name, name},
        validationRuns: statusId ? [{lastStatus: {statusID: {id: statusId}}}] : [],
    })

    it('draws one bar per stamp and counts the passed ones', () => {
        const {bars, passed, total} = validationStrip([
            validation("BUILD", 'PASSED'),
            validation("SMOKE", 'FAILED'),
            validation("E2E", 'PASSED'),
        ])
        expect(bars.map(bar => bar.statusId)).toEqual(['PASSED', 'FAILED', 'PASSED'])
        expect(passed).toBe(2)
        expect(total).toBe(3)
    })

    it('draws no bars at all for a build with no validations', () => {
        // Not a row of empty bars: that would claim stamps this build never ran
        expect(validationStrip([])).toEqual({bars: [], passed: 0, total: 0})
        expect(validationStrip(undefined)).toEqual({bars: [], passed: 0, total: 0})
    })

    it('counts a stamp which never ran as not passed', () => {
        const {passed, total} = validationStrip([validation("BUILD", null)])
        expect(passed).toBe(0)
        expect(total).toBe(1)
    })

})

describe('nextPromotionLevel', () => {

    const levels = [{id: "1"}, {id: "2"}, {id: "3"}]
    const run = (levelId) => ({promotionLevel: {id: levelId}})

    it('is the first level for a build with no promotions', () => {
        expect(nextPromotionLevel(levels, [])).toEqual({id: "1"})
    })

    it('is the lowest level not yet reached', () => {
        expect(nextPromotionLevel(levels, [run("1")])).toEqual({id: "2"})
    })

    it('skips over a gap rather than stopping at it', () => {
        expect(nextPromotionLevel(levels, [run("1"), run("3")])).toEqual({id: "2"})
    })

    it('is nothing once every level is reached', () => {
        expect(nextPromotionLevel(levels, [run("1"), run("2"), run("3")])).toBeNull()
    })

    it('is nothing on a branch with no promotion levels', () => {
        expect(nextPromotionLevel([], [])).toBeNull()
        expect(nextPromotionLevel(undefined, undefined)).toBeNull()
    })

})

describe('visibleDecorations', () => {

    const decoration = (type) => ({decorationType: type, data: []})

    it('keeps every decoration when they fit', () => {
        const {shown, overflow} = visibleDecorations([decoration("a"), decoration("b")])
        expect(shown.map(it => it.decorationType)).toEqual(["a", "b"])
        expect(overflow).toBe(0)
    })

    it('clips at the ceiling and counts the rest', () => {
        // The card is fixed-width by design, and decorations are the one element on it with no
        // length ceiling of their own
        const {shown, overflow} = visibleDecorations(
            ["a", "b", "c", "d", "e"].map(decoration),
        )
        expect(shown.map(it => it.decorationType)).toEqual(["a", "b", "c", "d"])
        expect(overflow).toBe(1)
    })

    it('fits the three a deployed build already carries, with room for a fourth', () => {
        // The build link, the release and the environments one. The environments decoration sorts
        // last, so a tighter ceiling would drop the very thing this row exists to show.
        const deployed = ["buildLink", "release", "environments"].map(decoration)
        expect(visibleDecorations(deployed).overflow).toBe(0)
        expect(visibleDecorations([...deployed, decoration("other")]).shown)
            .toHaveLength(4)
    })

    it('keeps the server order rather than ranking, there being nothing to rank on', () => {
        // Unlike promotion levels, decorations carry no ordinal: core cannot know which extension's
        // decoration matters more, so the order the backend returns is the order shown
        const {shown} = visibleDecorations([decoration("z"), decoration("a")], 2)
        expect(shown.map(it => it.decorationType)).toEqual(["z", "a"])
    })

    it('says nothing for a build with no decorations', () => {
        expect(visibleDecorations([])).toEqual({shown: [], overflow: 0})
        expect(visibleDecorations(undefined)).toEqual({shown: [], overflow: 0})
    })

})
