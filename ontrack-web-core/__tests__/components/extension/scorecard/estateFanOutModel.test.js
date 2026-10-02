import {
    branchExposures,
    fanOutExternalId,
    fanOutRows,
    fanOutSummary,
} from "@components/extension/scorecard/estates/estateFanOutModel";

const branch = (id, name) => ({id, name})
const stamp = (id, name) => ({id, name})

const exposure = (b, props = {}) => ({
    branch: b,
    validationStamp: stamp(1, 'scan'),
    since: '2026-09-10T10:00:00Z',
    state: 'EXPOSED',
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
            {branch: main, state: 'EXPOSED', since: '2026-09-05T10:00:00Z', acceptanceExpiresAt: null},
        ])
    })

    it('leaves out the branches where the finding is resolved', () => {
        const main = branch(10, 'main')
        const release = branch(11, 'release')
        expect(branchExposures([
            exposure(main, {state: 'RESOLVED', resolvedAt: '2026-09-20T10:00:00Z'}),
            exposure(release, {since: '2026-09-15T10:00:00Z'}),
        ])).toEqual([
            {branch: release, state: 'EXPOSED', since: '2026-09-15T10:00:00Z', acceptanceExpiresAt: null},
        ])
    })

    it('says a branch is accepted when every stamp reports the finding accepted, until the expiry', () => {
        const main = branch(10, 'main')
        expect(branchExposures([
            exposure(main, {state: 'ACCEPTED', accepted: true, acceptanceExpiresAt: '2026-12-31'}),
        ])).toEqual([
            {branch: main, state: 'ACCEPTED', since: '2026-09-10T10:00:00Z', acceptanceExpiresAt: '2026-12-31'},
        ])
    })

    it('says a branch is exposed as soon as one of its stamps reports the finding without acceptance', () => {
        const main = branch(10, 'main')
        expect(branchExposures([
            exposure(main, {validationStamp: stamp(1, 'image'), state: 'ACCEPTED', accepted: true}),
            exposure(main, {validationStamp: stamp(2, 'code'), state: 'EXPOSED'}),
        ])[0].state).toBe('EXPOSED')
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
