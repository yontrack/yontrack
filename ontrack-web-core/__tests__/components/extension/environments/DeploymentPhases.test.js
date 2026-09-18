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
jest.mock("../../../../components/extension/environments/SlotAdmissionRuleSummary", () => ({
    __esModule: true,
    default: ({ruleId}) => <span>{ruleId}</span>,
}))

import DeploymentPhases from "@components/extension/environments/deployment/DeploymentPhases"

const granted = (name, action) => ({name, action, authorized: true})

const statusChange = (status) => ({
    id: `change-${status}`,
    type: 'STATUS',
    status,
    user: 'admin',
    timestamp: '2026-09-18T10:00:00',
    message: null,
    overrideMessage: null,
})

const rule = (id, {ok = false} = {}) => ({
    canBeOverridden: true,
    overridden: false,
    check: {ok, reason: ok ? null : `${id} refuses`},
    override: null,
    admissionRuleConfig: {id, name: id, ruleId: 'manual', ruleConfig: {}},
})

const deployment = ({status, changes, rules = [rule('r1')]}) => ({
    id: 'p-1',
    status,
    changes,
    admissionRules: rules,
    requiredInputs: [],
    slot: {
        id: 'slot-1',
        candidateWorkflows: [],
        runningWorkflows: [],
        doneWorkflows: [],
        authorizations: [granted('pipeline', 'create'), granted('pipeline', 'override')],
    },
})

describe('the phases already over', () => {

    it('are not drawn at all for a candidate, which has not been anywhere', () => {
        const {container} = render(<DeploymentPhases deployment={deployment({
            status: 'CANDIDATE',
            changes: [statusChange('CANDIDATE')],
        })}/>)
        expect(container).toBeEmptyDOMElement()
    })

    /*
     * Collapsed while the deployment is still moving: the candidate phase of a running deployment
     * is history, and history a reader has to scroll past is how the failing check got lost on the
     * old page.
     */
    it('are collapsed while the deployment is still moving', () => {
        render(<DeploymentPhases deployment={deployment({
            status: 'RUNNING',
            changes: [statusChange('CANDIDATE'), statusChange('RUNNING')],
        })}/>)
        expect(screen.getByTestId('deployment-phase-label-CANDIDATE')).toBeInTheDocument()
        expect(screen.queryByTestId('deployment-phase-CANDIDATE-rule-r1')).not.toBeInTheDocument()
    })

    /*
     * Expanded once it is over: a finished deployment has no current phase, so there is nothing to
     * read past, and the record is what the page is for.
     */
    it('are expanded on a finished deployment, which is all record and no action', () => {
        render(<DeploymentPhases deployment={deployment({
            status: 'DONE',
            changes: [statusChange('CANDIDATE'), statusChange('RUNNING'), statusChange('DONE')],
        })}/>)
        expect(screen.getByTestId('deployment-phase-CANDIDATE-rule-r1')).toBeInTheDocument()
    })

    it('offer no inline fix, whatever the user may do elsewhere', () => {
        render(<DeploymentPhases deployment={deployment({
            status: 'DONE',
            changes: [statusChange('CANDIDATE'), statusChange('RUNNING'), statusChange('DONE')],
        })}/>)
        // The rule is failing and overridable, and the user holds both rights - and it is history.
        expect(screen.queryByTestId('deployment-phase-CANDIDATE-override-r1')).not.toBeInTheDocument()
    })
})
