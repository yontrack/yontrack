import {highestPromotionRun, highestPromotionsRows, promotionLadder} from "@components/widgets/home/highestPromotionsPerBranch";

const BRONZE = {id: 30, name: 'BRONZE'}
const SILVER = {id: 10, name: 'SILVER'}
const GOLD = {id: 20, name: 'GOLD'}
// The branch's own order, lowest first - deliberately not the order of the ids
const levels = [BRONZE, SILVER, GOLD]

const run = (id, promotionLevel) => ({id, promotionLevel})

describe('highestPromotionRun', () => {

    it('ranks by the order of the branch, not by the ids nor by the order of the runs', () => {
        const gold = run(1, GOLD)
        const runs = [gold, run(2, BRONZE), run(3, SILVER)]
        expect(highestPromotionRun(runs, levels)).toBe(gold)
        expect(highestPromotionRun([...runs].reverse(), levels)).toBe(gold)
    })

    it('does not take the promotions as cumulative', () => {
        const gold = run(1, GOLD)
        expect(highestPromotionRun([run(2, BRONZE), gold], levels)).toBe(gold)
    })

    it('ranks a level which is not in the list of the branch lowest', () => {
        const bronze = run(1, BRONZE)
        expect(highestPromotionRun([run(2, {id: 99, name: 'UNKNOWN'}), bronze], levels)).toBe(bronze)
    })

    it('still returns the run of a level which is not in the list of the branch when it is the only one', () => {
        const unknown = run(2, {id: 99, name: 'UNKNOWN'})
        expect(highestPromotionRun([unknown], levels)).toBe(unknown)
    })

    it('returns no run when there is none', () => {
        expect(highestPromotionRun([], levels)).toBeNull()
        expect(highestPromotionRun(undefined, levels)).toBeNull()
    })
})

describe('highestPromotionsRows', () => {

    const project = {id: 1, name: 'my-project'}
    const branch = (id, name, builds = [{id: id * 100, name: `${name}-1`, promotionRuns: []}]) =>
        ({id, name, project, promotionLevels: levels, builds})

    const main = branch(1, 'main')
    const release1 = branch(2, 'release-1')
    const release2 = branch(3, 'release-2')

    const explicit = name => ({project: project.name, branch: name})
    const pattern = (value, count) => ({project: project.name, pattern: value, count})

    const names = rows => rows.map(row => row.branch?.name ?? row.type)

    it('keeps the order of the entries, and the order of the branches of a pattern', () => {
        const rows = highestPromotionsRows(
            [explicit('release-2'), pattern('main|release-1', 2)],
            [[release2], [release1, main]],
        )
        expect(names(rows)).toEqual(['release-2', 'release-1', 'main'])
    })

    it('shows a branch once, at its first position, when an entry and a pattern both reach it', () => {
        const rows = highestPromotionsRows(
            [explicit('main'), pattern('main|release-.*', 3)],
            [[main], [release2, main, release1]],
        )
        expect(names(rows)).toEqual(['main', 'release-2', 'release-1'])
    })

    it('gives the latest build of each branch and its highest promotion', () => {
        const gold = run(7, GOLD)
        const build = {id: 700, name: '7', promotionRuns: [run(6, BRONZE), gold]}
        const [row] = highestPromotionsRows([explicit('main')], [[branch(1, 'main', [build])]])
        expect(row.type).toBe('BUILD')
        expect(row.build).toBe(build)
        expect(row.highestRun).toBe(gold)
    })

    it('keeps a latest build which is not promoted, without a highest promotion', () => {
        const [row] = highestPromotionsRows([explicit('main')], [[main]])
        expect(row.type).toBe('BUILD')
        expect(row.build.name).toBe('main-1')
        expect(row.highestRun).toBeNull()
    })

    it('shows a branch without any build as such', () => {
        const [row] = highestPromotionsRows([explicit('main')], [[branch(1, 'main', [])]])
        expect(row.type).toBe('NO_BUILD')
        expect(row.branch.name).toBe('main')
    })

    it('shows an explicit branch which cannot be found as such', () => {
        const [row] = highestPromotionsRows([explicit('unknown')], [[]])
        expect(row.type).toBe('NOT_FOUND')
        expect(row.entry).toEqual(explicit('unknown'))
    })

    it('shows a pattern which matches no branch as one row', () => {
        const [row, ...others] = highestPromotionsRows([pattern('feature-.*', 5)], [[]])
        expect(others).toEqual([])
        expect(row.type).toBe('NO_MATCH')
        expect(row.entry).toEqual(pattern('feature-.*', 5))
    })

    it('gives every row a distinct key', () => {
        const rows = highestPromotionsRows(
            [explicit('main'), explicit('unknown'), explicit('other'), pattern('x', 1), pattern('y', 1)],
            [[main], [], [], [], []],
        )
        expect(new Set(rows.map(row => row.key)).size).toBe(rows.length)
    })
})

describe('promotionLadder', () => {

    it('lists all the levels of the branch, lowest first, with what the build was granted', () => {
        const gold = run(1, GOLD)
        const ladder = promotionLadder(levels, [gold, run(2, BRONZE)])
        expect(ladder.map(step => [step.promotionLevel.name, step.granted, step.highest])).toEqual([
            ['BRONZE', true, false],
            ['SILVER', false, false],
            ['GOLD', true, true],
        ])
        expect(ladder[2].run).toBe(gold)
    })

    it('marks no level as granted nor highest when the build is not promoted', () => {
        expect(promotionLadder(levels, []).some(step => step.granted || step.highest)).toBe(false)
    })
})
