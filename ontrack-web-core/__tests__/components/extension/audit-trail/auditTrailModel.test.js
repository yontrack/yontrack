import {actorText, entrySummary, evidenceBadge, trailBadge} from "@components/extension/audit-trail/auditTrailModel"

const verification = (overrides = {}) => ({
    chainIntact: true,
    firstBrokenSeq: null,
    endorsementsValid: true,
    firstInvalidEndorsementSeq: null,
    partial: false,
    unendorsedFromSeq: null,
    problems: [],
    missingEvidence: null,
    alteredEvidence: null,
    ...overrides,
})

describe('trailBadge', () => {

    it('reads an intact trail', () => {
        expect(trailBadge(verification())).toEqual({
            state: 'intact',
            text: 'Intact',
            color: 'success',
        })
    })

    it('reads a trail opened after its build as partial', () => {
        expect(trailBadge(verification({partial: true}))).toEqual({
            state: 'partial',
            text: 'Partial',
            color: 'processing',
        })
    })

    it('reads an unendorsed tail with the seq it starts from', () => {
        expect(trailBadge(verification({unendorsedFromSeq: 4}))).toEqual({
            state: 'unendorsed',
            text: 'Unendorsed from seq 4',
            color: 'warning',
        })
    })

    it('reads an unendorsed tail before the trail being partial', () => {
        expect(trailBadge(verification({partial: true, unendorsedFromSeq: 2})).state).toEqual('unendorsed')
    })

    it('reads a broken chain with the seq of its first broken entry', () => {
        expect(trailBadge(verification({
            chainIntact: false,
            firstBrokenSeq: 2,
            unendorsedFromSeq: 3,
            problems: [{seq: 2, type: 'HASH', message: 'Hash mismatch'}],
        }))).toEqual({
            state: 'broken',
            text: 'Broken at seq 2',
            color: 'error',
        })
    })

    it('reads an invalid endorsement as broken, at its seq', () => {
        expect(trailBadge(verification({
            endorsementsValid: false,
            firstInvalidEndorsementSeq: 3,
            problems: [{seq: 3, type: 'ENDORSEMENT', message: 'Invalid endorsement'}],
        }))).toEqual({
            state: 'broken',
            text: 'Broken at seq 3',
            color: 'error',
        })
    })

    it('reads the first of a broken chain and an invalid endorsement', () => {
        expect(trailBadge(verification({
            chainIntact: false,
            firstBrokenSeq: 5,
            endorsementsValid: false,
            firstInvalidEndorsementSeq: 3,
        })).text).toEqual('Broken at seq 3')
    })
})

describe('evidenceBadge', () => {

    it('says nothing while the evidence was not verified', () => {
        expect(evidenceBadge(verification())).toBeNull()
    })

    it('reads evidence which all checks', () => {
        expect(evidenceBadge(verification({missingEvidence: [], alteredEvidence: []}))).toEqual({
            state: 'intact',
            text: 'Evidence intact',
            color: 'success',
        })
    })

    it('reads missing and altered evidence with the seqs of their entries', () => {
        expect(evidenceBadge(verification({missingEvidence: [3, 7], alteredEvidence: [5]}))).toEqual({
            state: 'failed',
            text: 'Evidence missing at seq 3, 7; altered at seq 5',
            color: 'error',
        })
    })

    it('reads altered evidence alone', () => {
        expect(evidenceBadge(verification({missingEvidence: [], alteredEvidence: [5]})).text)
            .toEqual('Evidence altered at seq 5')
    })
})

describe('actorText', () => {

    it('reads an account through the UI', () => {
        expect(actorText({account: 'alice@example.com', via: 'ui', jwt: {iss: 'https://idp', sub: '123'}}))
            .toEqual('alice@example.com (UI)')
    })

    it('names the API token, never its value', () => {
        expect(actorText({account: 'ci@example.com', via: 'token', tokenName: 'pipeline'}))
            .toEqual('ci@example.com (token pipeline)')
    })

    it('reads a JWT which was not issued to the UI', () => {
        expect(actorText({account: 'bot@example.com', via: 'jwt', jwt: {iss: 'https://idp', sub: 'bot'}}))
            .toEqual('bot@example.com (JWT)')
    })

    it('reads a webhook with its token', () => {
        expect(actorText({account: 'hook@example.com', via: 'webhook', tokenName: 'github'}))
            .toEqual('hook@example.com (webhook github)')
    })

    it('reads the system acting for a reason on behalf of another actor', () => {
        expect(actorText({
            account: 'system',
            via: 'system',
            system: 'auto-promotion',
            onBehalfOf: {account: 'ci@example.com', via: 'token', tokenName: 'pipeline'},
        })).toEqual('System (auto-promotion) on behalf of ci@example.com (token pipeline)')
    })

    it('reads the system with no reason', () => {
        expect(actorText({account: 'system', via: 'system'})).toEqual('System')
    })

    it('reads an account restored without its channel', () => {
        expect(actorText({account: 'alice@example.com', via: 'system'})).toEqual('alice@example.com (system)')
    })

    it('reads no actor', () => {
        expect(actorText(null)).toEqual('')
    })
})

describe('entrySummary', () => {

    const vs = {id: 10, name: 'unit-tests'}
    const run = {id: 100, order: 2}
    const pl = {id: 20, name: 'GOLD'}
    const deployment = {id: 'abc', number: 3, environment: 'production', slot: {id: 'slot', qualifier: ''}}
    const target = {id: 30, project: 'library', branch: 'main', name: '1.2.0'}

    const summary = (type, payload) => entrySummary({type, payload})

    it.each([
        ['trail.opened', {build: {name: '1.0'}, buildCreatedAt: '2026-01-01T00:00:00.000Z', partial: true},
            'Trail opened on a build created before it'],
        ['build.created', {build: {id: 1, project: 'p', branch: 'main', name: '1.0'}},
            'Build 1.0 created'],
        ['build.updated', {old: {name: '1.0', description: 'a'}, new: {name: '1.1', description: 'a'}},
            'Build renamed from 1.0 to 1.1'],
        ['build.updated', {old: {name: '1.0', description: 'a', creation: 'x'}, new: {name: '1.0', description: 'b', creation: 'y'}},
            'Build updated: description, creation'],
        ['property.set', {propertyType: 'net.nemerosa.ontrack.extension.general.ReleasePropertyType', value: {name: '1.0'}},
            'Property ReleasePropertyType set'],
        ['property.deleted', {propertyType: 'net.nemerosa.ontrack.extension.general.ReleasePropertyType'},
            'Property ReleasePropertyType deleted'],
        ['link.added', {target}, 'Linked to library 1.2.0'],
        ['link.added', {target, qualifier: 'dev'}, 'Linked to library 1.2.0 [dev]'],
        ['link.removed', {target, reason: 'cascade/target-build-deleted'},
            'Link to library 1.2.0 removed, with its target build'],
        ['validation.run', {validationStamp: vs, validationRun: run, status: 'PASSED'},
            'Validated unit-tests #2: PASSED'],
        ['validation.status', {validationStamp: vs, validationRun: run, status: 'FAILED'},
            'Status of unit-tests #2 changed to FAILED'],
        ['validation.comment', {validationStamp: vs, validationRun: run, validationRunStatusId: 5, comment: 'x'},
            'Comment edited on unit-tests #2'],
        ['validation.data', {validationStamp: vs, validationRun: run, data: {type: 't', sha256: 'abc'}},
            'Data of unit-tests #2 replaced'],
        ['validation.data', {validationStamp: vs, validationRun: run},
            'Data of unit-tests #2 removed'],
        ['validation.deleted', {validationStamp: vs, validationRun: run, status: 'PASSED'},
            'Validation unit-tests #2 deleted'],
        ['validation.deleted', {validationStamp: vs, validationRun: run, status: 'PASSED', reason: 'cascade/validation-stamp-deleted'},
            'Validation unit-tests #2 deleted, with its validation stamp'],
        ['evidence.attached', {validationStamp: vs, validationRun: run, evidence: {id: 1, fileName: 'report.pdf'}},
            'Evidence report.pdf attached to unit-tests #2'],
        ['evidence.deleted', {validationStamp: vs, validationRun: run, evidence: {id: 1, fileName: 'report.pdf'}},
            'Evidence report.pdf deleted from unit-tests #2'],
        ['promotion.added', {promotionLevel: pl, promotionRun: {id: 7}}, 'Promoted to GOLD'],
        ['promotion.removed', {promotionLevel: pl, promotionRun: {id: 7}}, 'Promotion to GOLD removed'],
        ['promotion.removed', {promotionLevel: pl, promotionRun: {id: 7}, reason: 'cascade/promotion-level-deleted'},
            'Promotion to GOLD removed, with its promotion level'],
        ['runinfo.set', {runnable: {type: 'build'}, runInfo: {sourceType: 'github'}}, 'Run info set on the build'],
        ['runinfo.set', {runnable: {type: 'validation_run', validationStamp: vs, validationRun: run}},
            'Run info set on unit-tests #2'],
        ['runinfo.deleted', {runnable: {type: 'build'}}, 'Run info deleted from the build'],
        ['deployment.created', {deployment}, 'Deployment #3 to production created'],
        ['deployment.running', {deployment: {...deployment, slot: {id: 'slot', qualifier: 'eu'}}},
            'Deployment #3 to production [eu] running'],
        ['deployment.done', {deployment}, 'Deployment #3 to production done'],
        ['deployment.failed', {deployment}, 'Deployment #3 to production failed'],
        ['deployment.cancelled', {deployment}, 'Deployment #3 to production cancelled'],
        ['deployment.rule-data', {deployment, rule: {id: 'r', name: 'Approval', ruleId: 'manual'}},
            'Deployment #3 to production: data set for rule Approval'],
        ['deployment.rule-overridden', {deployment, rule: {id: 'r', name: 'Approval', ruleId: 'manual'}, message: 'ok'},
            'Deployment #3 to production: rule Approval overridden'],
        ['deployment.workflow-overridden', {deployment, slotWorkflow: {id: 'w', instanceId: 'i', workflow: 'smoke'}},
            'Deployment #3 to production: workflow smoke overridden'],
        ['deployment.deleted', {deployment, status: 'DONE'}, 'Deployment #3 to production deleted'],
    ])('summarizes %s', (type, payload, expected) => {
        expect(summary(type, payload)).toEqual(expected)
    })

    it('names an unknown reason as it is', () => {
        expect(summary('link.removed', {target, reason: 'cascade/something-else'}))
            .toEqual('Link to library 1.2.0 removed, cascade/something-else')
    })

    it('falls back on the type of an entry it does not know', () => {
        expect(summary('something.new', {})).toEqual('something.new')
    })

    it('survives a payload missing what it expects', () => {
        expect(summary('promotion.added', {})).toEqual('Promoted to ?')
    })
})
