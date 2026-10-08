import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
})

// The inline fixes are dialogs of their own, each reading the server through the deprecated client.
// This is about which rows and which buttons appear, so the dialogs are stood in for.
jest.mock("../../../../components/extension/environments/SlotPipelineInputDialog", () => ({
    __esModule: true,
    default: () => null,
    useSlotPipelineInputDialog: () => ({start: jest.fn()}),
}))
jest.mock("../../../../components/extension/environments/SlotPipelineOverrideRuleDialog", () => ({
    __esModule: true,
    default: () => null,
    useSlotPipelineOverrideRuleDialog: () => ({start: jest.fn()}),
}))
jest.mock("../../../../components/extension/environments/SlotPipelineOverrideWorkflowDialog", () => ({
    __esModule: true,
    default: () => null,
    useSlotPipelineOverrideWorkflowDialog: () => ({start: jest.fn()}),
}))
// `SlotAdmissionRuleSummary` goes through `Dynamic`, whose webpack context does not exist under Jest.
jest.mock("../../../../components/extension/environments/SlotAdmissionRuleSummary", () => ({
    __esModule: true,
    default: ({ruleId}) => <span>{ruleId}</span>,
}))
// So does `SlotAdmissionRuleCheck`, which is the rule's own account of what happened to it (#1793).
// Stood in for here: what belongs to this component is *which rows get one*, not what a given rule
// chooses to say - that is the rule component's own business.
jest.mock("../../../../components/extension/environments/SlotAdmissionRuleCheck", () => ({
    __esModule: true,
    default: ({ruleData}) => <span>{`answered by ${ruleData?.user ?? 'nobody'}`}</span>,
}))

import WhatsBlocking from "@components/extension/environments/shared/WhatsBlocking"

const granted = (name, action) => ({name, action, authorized: true})
const refused = (name, action) => ({name, action, authorized: false})

const rule = (id, {ok = true, overridden = false, canBeOverridden = true, data = null} = {}) => ({
    canBeOverridden,
    overridden,
    check: {ok, reason: ok ? null : `${id} refuses`},
    override: overridden ? {user: 'admin', timestamp: '2026-09-18T10:00:00Z', message: 'Approved by hand'} : null,
    data,
    admissionRuleConfig: {id, name: id, ruleId: 'manual', ruleConfig: {}},
})

const deployment = ({
                        status = 'CANDIDATE',
                        rules = [],
                        requiredInputs = [],
                        authorizations = [granted('pipeline', 'create'), granted('pipeline', 'override')],
                    } = {}) => ({
    id: 'p-1',
    status,
    admissionRules: rules,
    requiredInputs,
    slot: {id: 'slot-1', candidateWorkflows: [], runningWorkflows: [], authorizations},
})

describe("what's blocking", () => {

    it('says so when nothing is blocking', () => {
        render(<WhatsBlocking deployment={deployment({rules: [rule('r1')]})}/>)
        expect(screen.getByTestId('whats-blocking-clear')).toBeInTheDocument()
    })

    it('counts the checks the way the mobile deployment screen counts them', () => {
        render(<WhatsBlocking deployment={deployment({rules: [rule('r1'), rule('r2', {ok: false})]})}/>)
        expect(screen.getByTestId('whats-blocking-summary')).toHaveTextContent('1 of 2 checks passed')
    })

    it('shows a failing check as blocking', () => {
        render(<WhatsBlocking deployment={deployment({rules: [rule('r1', {ok: false})]})}/>)
        expect(screen.getByTestId('whats-blocking-rule-r1')).toHaveTextContent('Blocking')
    })

    it('collapses the checks that passed', () => {
        // The reassurance is the count; the rows themselves are noise until somebody asks for them.
        render(<WhatsBlocking deployment={deployment({rules: [rule('r1'), rule('r2', {ok: false})]})}/>)
        expect(screen.getByText('1 check passed')).toBeInTheDocument()
    })

    it('says who overrode a check, when and why', () => {
        render(<WhatsBlocking deployment={deployment({
            rules: [rule('r1', {ok: true, overridden: true}), rule('r2', {ok: false})],
        })}/>)
        expect(screen.getByTestId('whats-blocking-override-detail-rule-r1'))
            .toHaveTextContent('Overridden by admin')
        expect(screen.getByTestId('whats-blocking-override-detail-rule-r1'))
            .toHaveTextContent('Approved by hand')
    })

    it('badges an agent which overrode a check, with its owner (#2032)', () => {
        const overridden = rule('r1', {ok: true, overridden: true})
        overridden.override = {
            ...overridden.override,
            user: 'claude[agent]',
            actor: {
                kind: 'agent',
                agent: 'claude[agent]',
                displayName: 'Claude',
                tool: 'Claude Code',
                owner: 'alice@example.com',
                sessionId: null,
                sessionLink: null,
            },
        }
        render(<WhatsBlocking deployment={deployment({rules: [overridden, rule('r2', {ok: false})]})}/>)
        const detail = screen.getByTestId('whats-blocking-override-detail-rule-r1')
        expect(detail).toHaveTextContent('Overridden by Claude, owned by alice@example.com')
        expect(screen.getByRole('img', {name: 'by agent Claude, owned by alice@example.com'})).toBeInTheDocument()
    })

    it('shows what was answered to a rule, under its row', () => {
        // The approval details were in the deployment's stored rule data all along and reached no
        // screen: an approval nobody can attribute is not much of an approval.
        // A *rejected* approval, because that is the row a reader is looking at: an approved one
        // passes and folds away behind "1 check passed", which is the right place for it.
        render(<WhatsBlocking deployment={deployment({
            rules: [
                rule('r1', {
                    ok: false,
                    data: {user: 'admin', timestamp: '2026-09-18T10:00:00Z', data: {approval: false}},
                }),
            ],
        })}/>)
        expect(screen.getByTestId('whats-blocking-detail-r1')).toHaveTextContent('answered by admin')
    })

    it('shows who is being waited on by a rule still asking for an answer', () => {
        render(<WhatsBlocking deployment={deployment({
            rules: [rule('r1', {ok: false})],
            requiredInputs: [{config: {id: 'r1'}}],
        })}/>)
        expect(screen.getByTestId('whats-blocking-detail-r1')).toBeInTheDocument()
    })

    it('adds nothing under a rule which asks nobody anything', () => {
        // A promotion or branch-pattern rule has nothing to add beyond the summary already on the
        // row, and drawing its `Check` there would restate the summary underneath itself.
        render(<WhatsBlocking deployment={deployment({rules: [rule('r1', {ok: false})]})}/>)
        expect(screen.queryByTestId('whats-blocking-detail-r1')).not.toBeInTheDocument()
    })

    it('offers Answer on a rule waiting for input', () => {
        render(<WhatsBlocking deployment={deployment({
            rules: [rule('r1', {ok: false})],
            requiredInputs: [{config: {id: 'r1'}}],
        })}/>)
        expect(screen.getByTestId('whats-blocking-answer-r1')).toBeInTheDocument()
    })

    it('offers Override on a failing rule that can be overridden', () => {
        render(<WhatsBlocking deployment={deployment({rules: [rule('r1', {ok: false})]})}/>)
        expect(screen.getByTestId('whats-blocking-override-r1')).toBeInTheDocument()
    })

    it('offers no Override on a rule that cannot be overridden', () => {
        render(<WhatsBlocking deployment={deployment({rules: [rule('r1', {ok: false, canBeOverridden: false})]})}/>)
        expect(screen.queryByTestId('whats-blocking-override-r1')).not.toBeInTheDocument()
    })

    it('hides the actions from a user without the right, and keeps the reason', () => {
        // The accepted consequence of "unauthorised actions are hidden": the blocking item is
        // visible, the button for it is not. A disabled button would promise something that will
        // never become available to them.
        render(<WhatsBlocking deployment={deployment({
            rules: [rule('r1', {ok: false})],
            requiredInputs: [{config: {id: 'r1'}}],
            authorizations: [refused('pipeline', 'create'), refused('pipeline', 'override')],
        })}/>)
        expect(screen.getByTestId('whats-blocking-rule-r1')).toHaveTextContent('Blocking')
        expect(screen.queryByTestId('whats-blocking-answer-r1')).not.toBeInTheDocument()
        expect(screen.queryByTestId('whats-blocking-override-r1')).not.toBeInTheDocument()
    })

    it('offers nothing at all in a read-only rendering', () => {
        render(<WhatsBlocking deployment={deployment({rules: [rule('r1', {ok: false})]})} actions={false}/>)
        expect(screen.queryByTestId('whats-blocking-override-r1')).not.toBeInTheDocument()
    })

    it('says a finished deployment is blocked by nothing', () => {
        render(<WhatsBlocking deployment={deployment({status: 'DONE', rules: [rule('r1', {ok: false})]})}/>)
        expect(screen.getByTestId('whats-blocking-settled')).toBeInTheDocument()
    })

    it('says so when a slot has no check at all', () => {
        render(<WhatsBlocking deployment={deployment()}/>)
        expect(screen.getByTestId('whats-blocking-none')).toBeInTheDocument()
    })
})

describe("what's blocking, when a check is only pending (#1937)", () => {

    const runningWorkflow = ({status = 'RUNNING', started = true} = {}) => ({
        id: 'w1',
        trigger: 'RUNNING',
        workflow: {name: 'Deploying'},
        slotWorkflowInstanceForPipeline: started ? {
            id: 'w1-instance',
            canBeOverridden: true,
            overridden: false,
            check: {ok: false, state: 'PENDING', reason: 'Workflow is running'},
            override: null,
            workflowInstance: {id: 'w1-wi', status},
        } : null,
    })

    const runningDeployment = (slotWorkflow) => ({
        ...deployment({status: 'RUNNING'}),
        slot: {
            id: 'slot-1',
            candidateWorkflows: [],
            runningWorkflows: [slotWorkflow],
            authorizations: [granted('pipeline', 'create'), granted('pipeline', 'override')],
        },
    })

    it('shows a running workflow as running, not as an error', () => {
        render(<WhatsBlocking deployment={runningDeployment(runningWorkflow())}/>)
        const row = screen.getByTestId('whats-blocking-workflow-w1')
        expect(row).toHaveTextContent('Running')
        expect(row).not.toHaveTextContent('Blocking')
        expect(screen.getByTestId('whats-blocking-workflow-w1-pending')).toBeInTheDocument()
        expect(screen.queryByTestId('whats-blocking-workflow-w1-nok')).not.toBeInTheDocument()
    })

    it('shows a workflow which has not started as waiting', () => {
        render(<WhatsBlocking deployment={runningDeployment({
            ...runningWorkflow({started: false}),
            // No instance, so no check from the server: the model reads it as pending
        })}/>)
        const row = screen.getByTestId('whats-blocking-workflow-w1')
        expect(row).toHaveTextContent('Waiting')
        expect(screen.getByTestId('whats-blocking-workflow-w1-pending')).toBeInTheDocument()
    })

    it('offers a plain Override on a running workflow, not a danger one', () => {
        render(<WhatsBlocking deployment={runningDeployment(runningWorkflow())}/>)
        const button = screen.getByTestId('whats-blocking-override-w1')
        expect(button).not.toHaveClass('ant-btn-dangerous')
    })

    it('keeps a danger Override on a failed workflow', () => {
        const failed = runningWorkflow({status: 'ERROR'})
        failed.slotWorkflowInstanceForPipeline.check = {ok: false, state: 'FAILED', reason: 'Workflow is in error'}
        render(<WhatsBlocking deployment={runningDeployment(failed)}/>)
        expect(screen.getByTestId('whats-blocking-workflow-w1')).toHaveTextContent('Blocking')
        expect(screen.getByTestId('whats-blocking-workflow-w1-nok')).toBeInTheDocument()
        expect(screen.getByTestId('whats-blocking-override-w1')).toHaveClass('ant-btn-dangerous')
    })

    it('shows a manual approval still waiting for an answer as waiting for approval', () => {
        const waiting = {...rule('r1', {ok: false}), check: {ok: false, state: 'PENDING', reason: 'No approval'}}
        render(<WhatsBlocking deployment={deployment({rules: [waiting], requiredInputs: [{config: {id: 'r1'}}]})}/>)
        const row = screen.getByTestId('whats-blocking-rule-r1')
        expect(row).toHaveTextContent('Waiting for approval')
        expect(row).not.toHaveTextContent('Blocking')
        expect(screen.getByTestId('whats-blocking-rule-r1-pending')).toBeInTheDocument()
        expect(screen.getByTestId('whats-blocking-override-r1')).not.toHaveClass('ant-btn-dangerous')
    })

    it('says how many checks are in progress', () => {
        render(<WhatsBlocking deployment={runningDeployment(runningWorkflow())}/>)
        expect(screen.getByTestId('whats-blocking-summary')).toHaveTextContent('0 of 1 checks passed · 1 in progress')
    })
})
