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

import WhatsBlocking from "@components/extension/environments/shared/WhatsBlocking"

const granted = (name, action) => ({name, action, authorized: true})
const refused = (name, action) => ({name, action, authorized: false})

const rule = (id, {ok = true, overridden = false, canBeOverridden = true} = {}) => ({
    canBeOverridden,
    overridden,
    check: {ok, reason: ok ? null : `${id} refuses`},
    override: overridden ? {user: 'admin', timestamp: '2026-09-18T10:00:00Z', message: 'Approved by hand'} : null,
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
