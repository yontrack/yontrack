import {
    checksSummary,
    currentPhaseItems,
    isClear,
    phaseItems,
} from "@components/extension/environments/shared/whatsBlockingModel"

/**
 * Which checks "What's blocking" is looking at.
 *
 * The rule it exists for is the phase rule: the deployment page today lists every rule and workflow
 * of every phase with equal weight, and the failing one has to be found among them. These tests are
 * what say the new list does not.
 */

const rule = (id, {ok = true, overridden = false, canBeOverridden = true} = {}) => ({
    canBeOverridden,
    overridden,
    check: {ok, reason: ok ? null : `${id} refuses`},
    override: overridden ? {user: 'admin', timestamp: '2026-09-18T10:00:00Z', message: 'Approved'} : null,
    admissionRuleConfig: {id, name: id, ruleId: 'manual', ruleConfig: {}},
})

const workflow = (id, {ok = true, started = true, overridden = false} = {}) => ({
    id,
    trigger: 'CANDIDATE',
    workflow: {name: id},
    slotWorkflowInstanceForPipeline: started ? {
        id: `${id}-instance`,
        canBeOverridden: true,
        overridden,
        check: {ok, reason: ok ? null : `${id} failed`},
        override: null,
        workflowInstance: {id: `${id}-wi`, status: ok ? 'SUCCESS' : 'ERROR'},
    } : null,
})

const deployment = ({status = 'CANDIDATE', rules = [], candidateWorkflows = [], runningWorkflows = [], requiredInputs = []} = {}) => ({
    id: 'p-1',
    status,
    admissionRules: rules,
    requiredInputs,
    slot: {id: 'slot-1', candidateWorkflows, runningWorkflows, authorizations: []},
})

describe('the current phase', () => {

    it('for a candidate, is the admission rules and the CANDIDATE workflows', () => {
        const items = currentPhaseItems(deployment({
            rules: [rule('r1')],
            candidateWorkflows: [workflow('w1')],
            runningWorkflows: [workflow('w2')],
        }))
        expect(items.map(item => item.key)).toEqual(['rule-r1', 'workflow-w1'])
    })

    it('for a running deployment, is the RUNNING workflows alone', () => {
        // Its admission rules were settled when it started. Re-listing them would put passed history
        // in front of the one thing that is not passing.
        const items = currentPhaseItems(deployment({
            status: 'RUNNING',
            rules: [rule('r1')],
            candidateWorkflows: [workflow('w1')],
            runningWorkflows: [workflow('w2')],
        }))
        expect(items.map(item => item.key)).toEqual(['workflow-w2'])
    })

    it('for a finished deployment, is nothing', () => {
        expect(currentPhaseItems(deployment({status: 'DONE', rules: [rule('r1')]}))).toEqual([])
    })

    it('puts failing items first', () => {
        const items = currentPhaseItems(deployment({
            rules: [rule('passing'), rule('failing', {ok: false}), rule('overridden', {ok: true, overridden: true})],
        }))
        expect(items.map(item => item.key)).toEqual(['rule-failing', 'rule-overridden', 'rule-passing'])
    })

    it('treats a workflow which has not started as not yet passed', () => {
        // "No verdict" is not a pass: the deployment is still waiting on it.
        const items = currentPhaseItems(deployment({candidateWorkflows: [workflow('w1', {started: false})]}))
        expect(items[0].ok).toBe(false)
    })

    it('marks a rule the deployment is waiting on for input', () => {
        const items = currentPhaseItems(deployment({
            rules: [rule('r1', {ok: false})],
            requiredInputs: [{config: {id: 'r1'}}],
        }))
        expect(items[0].needsInput).toBe(true)
    })

    it('does not mark a rule nobody is being asked about', () => {
        const items = currentPhaseItems(deployment({rules: [rule('r1', {ok: false})]}))
        expect(items[0].needsInput).toBe(false)
    })
})

describe('the summary', () => {

    it('counts the checks of the current phase, the way the mobile screen counts them', () => {
        const items = currentPhaseItems(deployment({
            rules: [rule('r1'), rule('r2', {ok: false})],
            candidateWorkflows: [workflow('w1')],
        }))
        expect(checksSummary(items).text).toBe('2 of 3 checks passed')
    })

    it('calls a phase with every check passing clear', () => {
        const items = currentPhaseItems(deployment({rules: [rule('r1')]}))
        expect(isClear(items)).toBe(true)
    })

    it('calls a phase with one failing check not clear', () => {
        const items = currentPhaseItems(deployment({rules: [rule('r1'), rule('r2', {ok: false})]}))
        expect(isClear(items)).toBe(false)
    })

    it('counts an overridden check as passed', () => {
        // The server already folds an override into `check.ok`; the list must not count it twice.
        const items = currentPhaseItems(deployment({rules: [rule('r1', {ok: true, overridden: true})]}))
        expect(isClear(items)).toBe(true)
    })
})

describe('pending checks (#1937)', () => {

    const runningWorkflow = (id) => ({
        id,
        trigger: 'RUNNING',
        workflow: {name: id},
        slotWorkflowInstanceForPipeline: {
            id: `${id}-instance`,
            canBeOverridden: true,
            overridden: false,
            check: {ok: false, state: 'PENDING', reason: 'Workflow is running'},
            override: null,
            workflowInstance: {id: `${id}-wi`, status: 'RUNNING'},
        },
    })

    it('carries the state of the check', () => {
        const items = currentPhaseItems(deployment({status: 'RUNNING', runningWorkflows: [runningWorkflow('w1')]}))
        expect(items[0].ok).toBe(false)
        expect(items[0].state).toBe('PENDING')
    })

    it('reads a check without a state from its verdict', () => {
        const items = currentPhaseItems(deployment({rules: [rule('passing'), rule('failing', {ok: false})]}))
        expect(items.map(item => [item.key, item.state])).toEqual([
            ['rule-failing', 'FAILED'],
            ['rule-passing', 'OK'],
        ])
    })

    it('puts failed checks before pending ones, and pending ones before passed ones', () => {
        const pendingRule = {...rule('pending', {ok: false}), check: {ok: false, state: 'PENDING', reason: 'No approval'}}
        const items = currentPhaseItems(deployment({
            rules: [rule('passing'), pendingRule, rule('failing', {ok: false})],
        }))
        expect(items.map(item => item.key)).toEqual(['rule-failing', 'rule-pending', 'rule-passing'])
    })

    it('says how many checks are still in progress', () => {
        const items = currentPhaseItems(deployment({status: 'RUNNING', runningWorkflows: [runningWorkflow('w1')]}))
        expect(checksSummary(items).text).toBe('0 of 1 checks passed · 1 in progress')
        expect(isClear(items)).toBe(false)
    })

    it('does not call a workflow which never ran in a phase already over waiting', () => {
        // A forced deployment left its CANDIDATE workflows unstarted: nothing is waiting for them
        // any longer, they simply did not pass.
        const items = phaseItems(
            deployment({status: 'DONE', candidateWorkflows: [workflow('w1', {started: false})]}),
            'CANDIDATE',
        )
        expect(items[0].state).toBe('FAILED')
    })

    it('does not call an approval never given in a phase already over waiting', () => {
        const pendingRule = {...rule('r1', {ok: false}), check: {ok: false, state: 'PENDING', reason: 'No approval'}}
        const items = phaseItems(deployment({status: 'RUNNING', rules: [pendingRule]}), 'CANDIDATE')
        expect(items[0].state).toBe('FAILED')
    })

    it('keeps a not-started workflow of the phase a finished deployment ended in as pending', () => {
        // DONE workflows run once the deployment is done: that phase is the server's current one,
        // and the row must agree with the header's pending message.
        const items = phaseItems(
            {...deployment({status: 'DONE'}), slot: {id: 'slot-1', doneWorkflows: [workflow('w1', {started: false})]}},
            'DONE',
        )
        expect(items[0].state).toBe('PENDING')
    })

    it('keeps a workflow still running in a phase already over as pending', () => {
        const items = phaseItems(
            deployment({status: 'DONE', runningWorkflows: [runningWorkflow('w1')]}),
            'RUNNING',
        )
        expect(items[0].state).toBe('PENDING')
    })

    it('says nothing about progress when nothing is pending', () => {
        const items = currentPhaseItems(deployment({rules: [rule('r1'), rule('r2', {ok: false})]}))
        expect(checksSummary(items).text).toBe('1 of 2 checks passed')
    })
})
