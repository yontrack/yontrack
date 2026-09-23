import {
    acceptanceSummary,
    exposedBranches,
    exposureRows,
    FINDINGS_VALIDATION_DATA_TYPE,
    isFindingsRun,
    findingsFilterFromQuery,
    findingsFilterToQuery,
} from "@components/extension/findings/findingsModel"
import {projectFindingsUri} from "@components/common/Links"

describe('findingsFilterFromQuery', () => {

    it('reads every criterion from the query of the URL', () => {
        expect(findingsFilterFromQuery({
            id: '12',
            severity: 'HIGH',
            state: 'OPEN',
            branch: 'release/1.0',
            scanner: 'trivy',
            kind: 'IMAGE',
        })).toEqual({
            severity: 'HIGH',
            state: 'OPEN',
            branch: 'release/1.0',
            scanner: 'trivy',
            kind: 'IMAGE',
        })
    })

    it('leaves out a blank criterion and a value the server would refuse', () => {
        expect(findingsFilterFromQuery({
            severity: 'SEVERE',
            state: 'open',
            kind: 'SBOM',
            branch: ' ',
            scanner: '',
        })).toEqual({})
    })

    it('takes the first value of a repeated parameter', () => {
        expect(findingsFilterFromQuery({severity: ['LOW', 'HIGH']})).toEqual({severity: 'LOW'})
    })

    it('reads an empty query as no filter', () => {
        expect(findingsFilterFromQuery()).toEqual({})
    })
})

describe('findingsFilterToQuery', () => {

    it('keeps the criteria which apply only', () => {
        expect(findingsFilterToQuery({severity: 'HIGH', state: undefined, branch: null, scanner: ''}))
            .toEqual({severity: 'HIGH'})
    })
})

describe('projectFindingsUri', () => {

    it('links to the findings page of a project', () => {
        expect(projectFindingsUri({id: 12})).toBe('/extension/findings/project/12')
    })

    it('carries the filter in the query, encoded', () => {
        expect(projectFindingsUri({id: 12}, {state: 'OPEN', branch: 'release/1.0', severity: 'HIGH'}))
            .toBe('/extension/findings/project/12?severity=HIGH&state=OPEN&branch=release%2F1.0')
    })

    it('reads back the filter it was given', () => {
        const filter = {severity: 'LOW', state: 'ACCEPTED', branch: 'main', scanner: 'zap', kind: 'DAST'}
        const url = new URL(projectFindingsUri({id: 1}, filter), 'http://localhost')
        expect(findingsFilterFromQuery(Object.fromEntries(url.searchParams))).toEqual(filter)
    })
})

describe('exposedBranches', () => {

    const main = {id: 1, name: 'main'}
    const release = {id: 2, name: 'release'}
    const feature = {id: 3, name: 'feature'}

    it('lists a branch once, exposed as soon as one of its stamps exposes it', () => {
        expect(exposedBranches([
            {branch: main, state: 'ACCEPTED'},
            {branch: main, state: 'EXPOSED'},
            {branch: release, state: 'ACCEPTED'},
            {branch: feature, state: 'RESOLVED'},
        ])).toEqual([
            {branch: main, state: 'EXPOSED'},
            {branch: release, state: 'ACCEPTED'},
        ])
    })

    it('lists no branch for a finding with no exposure', () => {
        expect(exposedBranches()).toEqual([])
    })
})

describe('exposureRows', () => {

    const main = {id: 1, name: 'main'}
    const release = {id: 2, name: 'release'}
    const image = {id: 10, name: 'SECURITY.IMAGE'}
    const dast = {id: 11, name: 'SECURITY.DAST'}

    it('spans the branch over its stamps, keeping the order of the exposures', () => {
        const rows = exposureRows([
            {branch: main, validationStamp: dast, state: 'EXPOSED'},
            {branch: main, validationStamp: image, state: 'RESOLVED'},
            {branch: release, validationStamp: image, state: 'ACCEPTED'},
        ])
        expect(rows.map(it => [it.key, it.branchRowSpan, it.state])).toEqual([
            ['1-11', 2, 'EXPOSED'],
            ['1-10', 0, 'RESOLVED'],
            ['2-10', 1, 'ACCEPTED'],
        ])
    })

    it('has no row for a finding with no exposure', () => {
        expect(exposureRows()).toEqual([])
    })
})

describe('acceptanceSummary', () => {

    it('says there is no acceptance', () => {
        expect(acceptanceSummary(null)).toBe('Not accepted')
    })

    it('says an acceptance without expiry holds', () => {
        expect(acceptanceSummary({effective: true})).toBe('Accepted, without expiry')
    })

    it('gives the last day of an acceptance which holds', () => {
        expect(acceptanceSummary({effective: true, expiresAt: '2026-10-03'})).toBe('Accepted until 2026-10-03')
    })

    it('says when an acceptance has expired', () => {
        expect(acceptanceSummary({effective: false, expiresAt: '2026-09-01'})).toBe('Acceptance expired on 2026-09-01')
    })
})

describe('isFindingsRun', () => {

    const dataType = (id) => ({descriptor: {id}})

    it('is a run whose data is a report of security scan', () => {
        expect(isFindingsRun({data: dataType(FINDINGS_VALIDATION_DATA_TYPE)})).toBe(true)
    })

    it('is a run of a stamp of security scans, even without data', () => {
        expect(isFindingsRun({validationStamp: {dataType: dataType(FINDINGS_VALIDATION_DATA_TYPE)}})).toBe(true)
    })

    it('is not a run of another data type, nor one without any', () => {
        expect(isFindingsRun({
            data: dataType('net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType'),
        })).toBe(false)
        expect(isFindingsRun({})).toBe(false)
        expect(isFindingsRun(undefined)).toBe(false)
    })
})
