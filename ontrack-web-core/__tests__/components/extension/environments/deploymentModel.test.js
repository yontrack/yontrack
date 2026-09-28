import {
    deploymentSteps,
    earlierPhases,
    isSettled,
    timelineEntries,
} from "@components/extension/environments/deployment/deploymentModel"

/**
 * What the deployment page reads off a deployment.
 *
 * The three functions under test are the three questions the page answers - where is it, what is
 * holding it up, how did it get here - and each one has a case which could plausibly have gone the
 * other way. Those are the cases here; the drawing is left to the Playwright suite.
 */

const statusChange = (status, {user = 'admin', timestamp, overrideMessage = null, message = null} = {}) => ({
    id: `change-${status}`,
    type: 'STATUS',
    status,
    user,
    timestamp,
    message,
    overrideMessage,
})

const rule = (id, {ok = true} = {}) => ({
    canBeOverridden: true,
    overridden: false,
    check: {ok, reason: ok ? null : `${id} refuses`},
    override: null,
    admissionRuleConfig: {id, name: id, ruleId: 'manual', ruleConfig: {}},
})

const workflow = (id, {ok = true} = {}) => ({
    id,
    workflow: {name: id},
    slotWorkflowInstanceForPipeline: {
        id: `${id}-instance`,
        canBeOverridden: true,
        overridden: false,
        check: {ok, reason: null},
        override: null,
        workflowInstance: {id: `${id}-wi`, status: 'SUCCESS'},
    },
})

const deployment = ({
                        status = 'CANDIDATE',
                        changes = [],
                        rules = [],
                        candidateWorkflows = [],
                        runningWorkflows = [],
                        doneWorkflows = [],
                        failedWorkflows = [],
                    } = {}) => ({
    id: 'p-1',
    status,
    changes,
    admissionRules: rules,
    requiredInputs: [],
    slot: {id: 'slot-1', candidateWorkflows, runningWorkflows, doneWorkflows, failedWorkflows, authorizations: []},
})

describe('the steps bar', () => {

    it('puts a candidate on the first step', () => {
        const {items, current} = deploymentSteps(deployment({status: 'CANDIDATE'}))
        expect(items.map(item => item.key)).toEqual(['CANDIDATE', 'RUNNING', 'DONE'])
        expect(current).toBe(0)
    })

    it('puts a running deployment on the second', () => {
        expect(deploymentSteps(deployment({status: 'RUNNING'})).current).toBe(1)
    })

    it('finishes on Deployed', () => {
        const steps = deploymentSteps(deployment({status: 'DONE'}))
        expect(steps.current).toBe(2)
        expect(steps.status).toBe('finish')
    })

    it('replaces the step a cancelled deployment never reached, rather than adding a fourth', () => {
        // A candidate which was cancelled never started, so "Running" is not a step it skipped -
        // it is the step it was cancelled instead of taking.
        const steps = deploymentSteps(deployment({status: 'CANCELLED', changes: [statusChange('CANDIDATE')]}))
        expect(steps.items.map(item => item.key)).toEqual(['CANDIDATE', 'CANCELLED'])
        expect(steps.status).toBe('error')
    })

    it('replaces Deployed with Failed on a deployment which failed', () => {
        // A failure is reachable from RUNNING only, so the bar always shows the running step before
        // it - and never a Deployed step the deployment did not reach.
        const steps = deploymentSteps(deployment({
            status: 'FAILED',
            changes: [statusChange('CANDIDATE'), statusChange('RUNNING'), statusChange('FAILED')],
        }))
        expect(steps.items.map(item => item.key)).toEqual(['CANDIDATE', 'RUNNING', 'FAILED'])
        expect(steps.current).toBe(2)
        expect(steps.status).toBe('error')
    })

    it('keeps Running on a deployment which was cancelled after it started', () => {
        const steps = deploymentSteps(deployment({
            status: 'CANCELLED',
            changes: [statusChange('CANDIDATE'), statusChange('RUNNING')],
        }))
        expect(steps.items.map(item => item.key)).toEqual(['CANDIDATE', 'RUNNING', 'CANCELLED'])
        expect(steps.current).toBe(2)
    })
})

describe('the phases already over', () => {

    it('are none for a candidate: it has not been anywhere yet', () => {
        expect(earlierPhases(deployment({rules: [rule('r1')]}))).toEqual([])
    })

    it('are the candidate phase for a running deployment', () => {
        const phases = earlierPhases(deployment({
            status: 'RUNNING',
            changes: [statusChange('CANDIDATE'), statusChange('RUNNING')],
            rules: [rule('r1')],
            runningWorkflows: [workflow('w-run')],
        }))
        expect(phases.map(phase => phase.phase)).toEqual(['CANDIDATE'])
        // And the running phase is *not* repeated: it is the current one, shown at the top.
        expect(phases[0].items.map(item => item.key)).toEqual(['rule-r1'])
    })

    it('are all of them for a finished deployment, which is the whole record', () => {
        const phases = earlierPhases(deployment({
            status: 'DONE',
            changes: [statusChange('CANDIDATE'), statusChange('RUNNING'), statusChange('DONE')],
            rules: [rule('r1')],
            candidateWorkflows: [workflow('w-cand')],
            runningWorkflows: [workflow('w-run')],
            doneWorkflows: [workflow('w-done')],
        }))
        expect(phases.map(phase => phase.phase)).toEqual(['CANDIDATE', 'RUNNING', 'DONE'])
    })

    it('keep the running phase of a forced deployment, which never was running', () => {
        // Forcing goes straight from candidate to deployed. The RUNNING workflows did not run, and
        // that is part of what the person who forced it left behind.
        const phases = earlierPhases(deployment({
            status: 'DONE',
            changes: [statusChange('CANDIDATE'), statusChange('DONE')],
            rules: [rule('r1')],
            runningWorkflows: [workflow('w-run')],
            doneWorkflows: [workflow('w-done')],
        }))
        expect(phases.map(phase => phase.phase)).toEqual(['CANDIDATE', 'RUNNING', 'DONE'])
    })

    it('are candidate, running and failed for a failed deployment', () => {
        // The FAILED workflows are the ones which ran because it failed; DONE ones never did.
        const phases = earlierPhases(deployment({
            status: 'FAILED',
            changes: [statusChange('CANDIDATE'), statusChange('RUNNING'), statusChange('FAILED')],
            rules: [rule('r1')],
            runningWorkflows: [workflow('w-run')],
            doneWorkflows: [workflow('w-done')],
            failedWorkflows: [workflow('w-failed')],
        }))
        expect(phases.map(phase => phase.phase)).toEqual(['CANDIDATE', 'RUNNING', 'FAILED'])
        expect(phases[2].title).toBe('Failed phase')
        expect(phases[2].items.map(item => item.key)).toEqual(['workflow-w-failed'])
    })

    it('drop the running phase of a deployment cancelled before it started', () => {
        const phases = earlierPhases(deployment({
            status: 'CANCELLED',
            changes: [statusChange('CANDIDATE'), statusChange('CANCELLED')],
            rules: [rule('r1')],
            runningWorkflows: [workflow('w-run')],
        }))
        expect(phases.map(phase => phase.phase)).toEqual(['CANDIDATE'])
    })

    it('leave out a phase with no checks rather than drawing it empty', () => {
        const phases = earlierPhases(deployment({
            status: 'DONE',
            changes: [statusChange('CANDIDATE'), statusChange('RUNNING'), statusChange('DONE')],
            rules: [rule('r1')],
        }))
        expect(phases.map(phase => phase.phase)).toEqual(['CANDIDATE'])
    })
})

describe('the audit timeline', () => {

    it('is newest first, whatever order the server answered in', () => {
        const entries = timelineEntries(deployment({
            changes: [
                statusChange('CANDIDATE', {timestamp: '2026-09-18T10:00:00'}),
                statusChange('RUNNING', {timestamp: '2026-09-18T12:00:00'}),
            ],
        }))
        expect(entries.map(entry => entry.title)).toEqual(['Running', 'Candidate'])
    })

    it('names a status change by the status it reached, as the steps bar does', () => {
        const [entry] = timelineEntries(deployment({
            changes: [statusChange('DONE', {timestamp: '2026-09-18T12:00:00'})],
        }))
        expect(entry.title).toBe('Deployed')
    })

    it('carries an override message, which is the only place the page shows it', () => {
        const [entry] = timelineEntries(deployment({
            changes: [{
                id: 'c1',
                type: 'WORKFLOW_OVERRIDDEN',
                user: 'admin',
                timestamp: '2026-09-18T12:00:00',
                message: 'Workflow "Canary window open" overridden',
                overrideMessage: 'Window checked by phone with the on-call.',
            }],
        }))
        expect(entry.title).toBe('Workflow "Canary window open" overridden')
        expect(entry.message).toBe('Window checked by phone with the on-call.')
    })

    it('names a failure, and carries the message it was recorded with', () => {
        // The failure message is the one thing somebody reading the timeline wants to know: why.
        const [entry] = timelineEntries(deployment({
            changes: [statusChange('FAILED', {
                timestamp: '2026-09-18T12:00:00',
                message: 'Helm upgrade timed out',
            })],
        }))
        expect(entry.title).toBe('Failed')
        expect(entry.message).toBe('Helm upgrade timed out')
    })

    it('falls back to a name for a change the server recorded with no message', () => {
        const [entry] = timelineEntries(deployment({
            changes: [{
                id: 'c1',
                type: 'RULE_OVERRIDDEN',
                user: 'admin',
                timestamp: '2026-09-18T12:00:00',
                message: null,
                overrideMessage: 'We cannot wait',
            }],
        }))
        expect(entry.title).toBe('Rule overridden')
    })
})

describe('a settled deployment', () => {
    it('is one which is deployed, cancelled or failed', () => {
        expect(isSettled(deployment({status: 'DONE'}))).toBe(true)
        expect(isSettled(deployment({status: 'CANCELLED'}))).toBe(true)
        expect(isSettled(deployment({status: 'FAILED'}))).toBe(true)
        expect(isSettled(deployment({status: 'RUNNING'}))).toBe(false)
        expect(isSettled(deployment({status: 'CANDIDATE'}))).toBe(false)
    })
})
