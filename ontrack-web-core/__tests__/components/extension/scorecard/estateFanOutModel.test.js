import {
    branchExposures,
    fanOutExternalId,
    fanOutRows,
    fanOutSummary,
    rankedFindingProjectsText,
    rankedFindingRows,
    rankedFindingsCaption,
    searchedFindingsCaption,
} from "@components/extension/scorecard/estates/estateFanOutModel";

const branch = (id, name) => ({id, name})
const stamp = (id, name) => ({id, name})

const exposure = (b, props = {}) => ({
    branch: b,
    validationStamp: stamp(1, 'scan'),
    since: '2026-09-10T10:00:00Z',
    state: 'EXPOSED',
    counts: true,
    accepted: false,
    acceptanceExpiresAt: null,
    resolvedAt: null,
    ...props,
})

const finding = (id, project, props = {}) => ({
    id,
    project,
    externalId: 'CVE-2021-44228',
    location: 'pkg:maven/org.x/y',
    scanner: 'trivy',
    kind: 'DEPENDENCIES',
    title: 'Log4Shell',
    url: null,
    maxSeverity: 'HIGH',
    state: 'OPEN',
    firstSeen: '2026-09-01T10:00:00Z',
    resolvedAt: null,
    exposures: [],
    ...props,
})

const alpha = {id: 1, name: 'alpha'}
const beta = {id: 2, name: 'beta'}
const gamma = {id: 3, name: 'gamma'}

describe('fanOutExternalId', () => {
    it('trims what the user typed', () => {
        expect(fanOutExternalId('  CVE-2021-44228 ')).toBe('CVE-2021-44228')
    })

    it('gives nothing for a blank search', () => {
        expect(fanOutExternalId('   ')).toBeNull()
        expect(fanOutExternalId('')).toBeNull()
        expect(fanOutExternalId(undefined)).toBeNull()
    })
})

describe('branchExposures', () => {
    it('lists each branch the finding is exposed on once, since the earliest of its stamps', () => {
        const main = branch(10, 'main')
        expect(branchExposures([
            exposure(main, {validationStamp: stamp(1, 'image'), since: '2026-09-12T10:00:00Z'}),
            exposure(main, {validationStamp: stamp(2, 'code'), since: '2026-09-05T10:00:00Z'}),
        ])).toEqual([
            {branch: main, state: 'EXPOSED', since: '2026-09-05T10:00:00Z', acceptanceExpiresAt: null, counts: true},
        ])
    })

    it('leaves out the branches where the finding is resolved', () => {
        const main = branch(10, 'main')
        const release = branch(11, 'release')
        expect(branchExposures([
            exposure(main, {state: 'RESOLVED', resolvedAt: '2026-09-20T10:00:00Z'}),
            exposure(release, {since: '2026-09-15T10:00:00Z'}),
        ])).toEqual([
            {branch: release, state: 'EXPOSED', since: '2026-09-15T10:00:00Z', acceptanceExpiresAt: null, counts: true},
        ])
    })

    it('says a branch is accepted when every stamp reports the finding accepted, until the expiry', () => {
        const main = branch(10, 'main')
        expect(branchExposures([
            exposure(main, {state: 'ACCEPTED', accepted: true, acceptanceExpiresAt: '2026-12-31'}),
        ])).toEqual([
            {branch: main, state: 'ACCEPTED', since: '2026-09-10T10:00:00Z', acceptanceExpiresAt: '2026-12-31', counts: true},
        ])
    })

    it('says a branch is exposed as soon as one of its stamps reports the finding without acceptance', () => {
        const main = branch(10, 'main')
        expect(branchExposures([
            exposure(main, {validationStamp: stamp(1, 'image'), state: 'ACCEPTED', accepted: true}),
            exposure(main, {validationStamp: stamp(2, 'code'), state: 'EXPOSED'}),
        ])[0].state).toBe('EXPOSED')
    })

    it('says whether each branch counts toward the state of the finding in the project', () => {
        const main = branch(10, 'main')
        const spike = branch(12, 'spike')
        expect(branchExposures([
            exposure(main),
            exposure(spike, {counts: false}),
        ]).map(it => [it.branch.name, it.counts])).toEqual([
            ['main', true],
            ['spike', false],
        ])
    })

    it('lists the branches which do not count after the ones which count', () => {
        const develop = branch(10, 'develop')
        const featureA = branch(11, 'feature-a')
        const main = branch(12, 'main')
        const spike = branch(13, 'spike')
        expect(branchExposures([
            exposure(develop, {counts: false}),
            exposure(featureA, {counts: false}),
            exposure(main),
            exposure(spike),
        ]).map(it => it.branch.name)).toEqual(['main', 'spike', 'develop', 'feature-a'])
    })

    it('gives nothing for no exposure', () => {
        expect(branchExposures(undefined)).toEqual([])
    })
})

describe('fanOutRows', () => {
    it('puts the exposed projects first, then the accepted ones, then the resolved ones, each by name', () => {
        const rows = fanOutRows([
            finding(1, alpha, {state: 'RESOLVED'}),
            finding(2, beta, {state: 'ACCEPTED'}),
            finding(3, gamma, {state: 'OPEN'}),
        ])
        expect(rows.map(it => it.project.name)).toEqual(['gamma', 'beta', 'alpha'])
    })

    it('groups the findings of a project, the first row spanning the others', () => {
        const rows = fanOutRows([
            finding(1, alpha, {location: 'pkg:maven/org.c/d', state: 'RESOLVED'}),
            finding(2, beta, {state: 'OPEN'}),
            finding(3, alpha, {location: 'pkg:maven/org.a/b', state: 'OPEN'}),
        ])
        expect(rows.map(it => [it.project.name, it.finding.id, it.projectRowSpan])).toEqual([
            ['alpha', 3, 2],
            ['alpha', 1, 0],
            ['beta', 2, 1],
        ])
    })

    it('gives each row the branches its finding is exposed on', () => {
        const main = branch(10, 'main')
        const [row] = fanOutRows([finding(1, alpha, {exposures: [exposure(main)]})])
        expect(row.key).toBe(1)
        expect(row.branches.map(it => it.branch.name)).toEqual(['main'])
    })

    it('gives no row for no finding', () => {
        expect(fanOutRows(undefined)).toEqual([])
    })
})

describe('fanOutSummary', () => {
    it('counts the projects by their most exposed finding', () => {
        expect(fanOutSummary([
            finding(1, alpha, {state: 'OPEN'}),
            finding(2, alpha, {state: 'RESOLVED'}),
            finding(3, beta, {state: 'ACCEPTED'}),
            finding(4, gamma, {state: 'RESOLVED'}),
        ])).toEqual({projects: 3, exposed: 1, accepted: 1, resolved: 1})
    })

    it('counts nothing for no finding', () => {
        expect(fanOutSummary([])).toEqual({projects: 0, exposed: 0, accepted: 0, resolved: 0})
    })
})

describe('The ranked findings of an estate', () => {

    const ranked = (externalId, props = {}) => ({
        externalId,
        title: `Title of ${externalId}`,
        severity: 'HIGH',
        openProjects: 1,
        acceptedProjects: 0,
        resolvedProjects: 0,
        firstSeen: '2026-09-01T10:00:00Z',
        ...props,
    })

    it('keeps the order of the server, one row per external ID, with the number of projects reporting it', () => {
        const rows = rankedFindingRows([
            ranked('CVE-2', {openProjects: 3, acceptedProjects: 1, resolvedProjects: 2}),
            ranked('CVE-1'),
        ])
        expect(rows.map(it => [it.key, it.projects])).toEqual([['CVE-2', 6], ['CVE-1', 1]])
        expect(rows[0].title).toBe('Title of CVE-2')
    })

    it('has no row for no finding', () => {
        expect(rankedFindingRows(null)).toEqual([])
        expect(rankedFindingRows(undefined)).toEqual([])
    })

    it('says in how many projects a finding is open, accepted and resolved', () => {
        expect(rankedFindingProjectsText(ranked('CVE-1', {openProjects: 3, acceptedProjects: 1, resolvedProjects: 0})))
            .toBe('Open in 3 · accepted in 1 · resolved in 0')
    })

    it('says the list is the top of the ranking when it is as long as asked for', () => {
        expect(rankedFindingsCaption(20, 20)).toBe('The 20 findings open in the most projects of this estate, the most widespread first')
    })

    it('says how many findings are open in the estate when they are all listed', () => {
        expect(rankedFindingsCaption(1, 20)).toBe('1 finding open in this estate')
        expect(rankedFindingsCaption(3, 20)).toBe('3 findings open in this estate, the most widespread first')
    })
})

describe('The caption of the findings found by a search', () => {

    it('asks for more of the external ID when the list is as long as asked for', () => {
        expect(searchedFindingsCaption(20, 20, 'cve'))
            .toBe('The first 20 findings whose external ID contains "cve": type more of it to narrow the search')
    })

    it('says how many findings were found when they are all listed', () => {
        expect(searchedFindingsCaption(1, 20, 'cve-2024')).toBe('1 finding whose external ID contains "cve-2024"')
        expect(searchedFindingsCaption(3, 20, 'cve-2024'))
            .toBe('3 findings whose external ID contains "cve-2024", the most widespread first')
    })
})
