import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"

// antd's Drawer - the input and override sheets - reads the responsive
// breakpoints, and jsdom ships no `matchMedia`.
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

/**
 * One deployment on a phone: the waiting room.
 *
 * Everything here is about the three things a person can do to a `CANDIDATE`
 * deployment - answer a rule, override a rule, run it - and about who is allowed
 * to do them. The screen is also where #1725's "actions are absent for
 * unauthorized users" is actually decided, which a UI test running as the
 * suite's admin account cannot show.
 */

let queryResult = {data: null, loading: false, error: null, finished: true}
/** The `deps` of the screen's query, which is what an action changes. */
let queryDeps = null
const callGraphQL = jest.fn()

jest.mock("../../../components/services/GraphQL", () => ({
    useQuery: (query, {deps = []} = {}) => {
        queryDeps = deps
        return queryResult
    },
    callGraphQL: (...args) => callGraphQL(...args),
}))

/*
 * Both are `Dynamic`: lazily imported components chosen by rule id. Stubbed so
 * these tests assert what the screen hands the shared mapping rather than
 * re-testing the desktop components behind it - `mobile.spec.js` exercises the
 * real ones against a real slot.
 */
jest.mock("../../../components/extension/environments/SlotAdmissionRuleSummary", () => ({
    __esModule: true,
    default: ({ruleId, ruleConfig}) => <span>{`${ruleId}:${JSON.stringify(ruleConfig)}`}</span>,
}))
jest.mock("../../../components/extension/environments/SlotAdmissionRuleDataForm", () => ({
    __esModule: true,
    default: ({configId}) => <input aria-label={`data-${configId}`}/>,
}))

import MobileDeploymentScreen from "@/app/mobile/deployment/[id]/DeploymentScreen"

/** The shape `isAuthorized` reads. */
const granted = (name, action) => ({name, action, authorized: true})
const refused = (name, action) => ({name, action, authorized: false})

const rule = (id, {
    ok = true,
    canBeOverridden = true,
    overridden = false,
    override = null,
    ruleId = 'manual',
    ruleConfig = {message: 'Approval message'},
} = {}) => ({
    canBeOverridden,
    overridden,
    check: {ok},
    override,
    admissionRuleConfig: {id, name: id, description: null, ruleId, ruleConfig},
})

const deployment = ({
                        status = 'CANDIDATE',
                        rules = [],
                        requiredInputs = [],
                        runAction = {ok: true, successCount: 1, totalCount: 1},
                        authorizations = [granted('pipeline', 'create'), granted('pipeline', 'override')],
                    } = {}) => {
    queryResult = {
        data: {
            slotPipelineById: {
                id: 'pipeline-1',
                status,
                start: '2024-03-01T09:00:00Z',
                build: {id: 100, name: '20260901055547-36', displayName: '1.4.0'},
                slot: {
                    id: 'slot-1',
                    qualifier: '',
                    environment: {id: 'env-1', name: 'production'},
                    project: {id: 1, name: 'petclinic'},
                    authorizations,
                },
                admissionRules: rules,
                requiredInputs: requiredInputs.map(id => ({
                    config: {id, name: id, description: null, ruleId: 'manual', ruleConfig: {}},
                })),
                runAction,
            },
        },
        loading: false,
        error: null,
        finished: true,
    }
}

beforeEach(() => {
    queryResult = {data: null, loading: false, error: null, finished: true}
    queryDeps = null
    callGraphQL.mockReset()
    callGraphQL.mockResolvedValue({
        startSlotPipelineDeployment: {deploymentStatus: {ok: true, message: null}, errors: null},
    })
})

describe('the mobile deployment screen', () => {

    describe('identity', () => {

        it('calls the deployment by its slot', () => {
            deployment()
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-screen-title')).toHaveTextContent('production')
        })

        it('places it in its project and its build', () => {
            deployment()
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            const hrefs = Array.from(screen.getByTestId('mobile-screen-subtitle').querySelectorAll('a'))
                .map(link => link.getAttribute('href'))
            expect(hrefs).toEqual(['/mobile/project/1', '/mobile/build/100'])
        })

        it('says what state it is in', () => {
            deployment({status: 'CANDIDATE'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-status')).toHaveTextContent('Candidate')
        })

        it('says so when there is no such deployment', () => {
            // `slotPipelineById` is nullable, so an id nobody can see comes back
            // as a null rather than as an error.
            queryResult = {data: {slotPipelineById: null}, loading: false, error: null, finished: true}
            render(<MobileDeploymentScreen id="nope"/>)
            expect(screen.getByTestId('mobile-deployment-missing')).toBeInTheDocument()
        })
    })

    describe('the checks', () => {

        it('lists every rule with its verdict', () => {
            deployment({rules: [rule('r1', {ok: true}), rule('r2', {ok: false})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-rule-r1')).toHaveTextContent('Passed')
            expect(screen.getByTestId('mobile-deployment-rule-r2')).toHaveTextContent('Blocking')
        })

        it('repeats the verdict as a glyph, so it survives greyscale', () => {
            deployment({rules: [rule('r1', {ok: true}), rule('r2', {ok: false})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-rule-r1-ok')).toBeInTheDocument()
            expect(screen.getByTestId('mobile-deployment-rule-r2-nok')).toBeInTheDocument()
        })

        it('phrases each rule through the shared mapping', () => {
            deployment({rules: [rule('r1', {ruleId: 'promotion', ruleConfig: {promotion: 'GOLD'}})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-rule-r1'))
                .toHaveTextContent('promotion:{"promotion":"GOLD"}')
        })

        it('says when a rule was overridden, and by whom', () => {
            deployment({
                rules: [rule('r1', {
                    ok: true,
                    overridden: true,
                    override: {user: 'alice', message: 'Hotfix, agreed with ops.'},
                })],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-rule-r1')).toHaveTextContent('alice')
            expect(screen.getByTestId('mobile-deployment-override-message-r1'))
                .toHaveTextContent('Hotfix, agreed with ops.')
        })

        it('says so when the slot has no rule at all', () => {
            deployment({rules: []})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-rules')).toHaveTextContent(/no admission rule/i)
        })
    })

    describe('running it', () => {

        it('offers the run when every check passes', () => {
            deployment({runAction: {ok: true, successCount: 2, totalCount: 2}})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-run')).toBeEnabled()
        })

        it('refuses the run while a check blocks, and says how far it got', () => {
            deployment({runAction: {ok: false, successCount: 1, totalCount: 3}})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-run')).toBeDisabled()
            expect(screen.getByTestId('mobile-deployment-run-blocked')).toHaveTextContent('1 of 3 checks passed')
        })

        it('starts the deployment and asks the server again', async () => {
            deployment()
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            const before = queryDeps
            fireEvent.click(screen.getByTestId('mobile-deployment-run'))
            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            expect(callGraphQL.mock.calls[0][0].variables).toEqual({id: 'pipeline-1'})
            await waitFor(() => expect(queryDeps).not.toEqual(before))
        })

        it('shows a refused run rather than swallowing it', async () => {
            // A refusal is not a failed request: the server answers with a
            // status saying why, and the user has to be able to read it.
            callGraphQL.mockResolvedValue({
                startSlotPipelineDeployment: {
                    deploymentStatus: {ok: false, message: "Rule manual is not satisfied"},
                    errors: null,
                },
            })
            deployment()
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            fireEvent.click(screen.getByTestId('mobile-deployment-run'))
            expect(await screen.findByTestId('mobile-deployment-error'))
                .toHaveTextContent('Rule manual is not satisfied')
        })

        it('offers nothing at all on a deployment which is no longer waiting', () => {
            // RUNNING to DONE is driven by CI, and cancelling is deliberately
            // left to the desktop UI - so a settled deployment is read-only here.
            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-run')).not.toBeInTheDocument()
            expect(screen.getByTestId('mobile-deployment-settled')).toBeInTheDocument()
        })
    })

    describe('answering a rule', () => {

        it('offers the input a rule is waiting for', () => {
            deployment({rules: [rule('r1', {ok: false})], requiredInputs: ['r1']})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-input-open-r1')).toBeInTheDocument()
        })

        it('offers no input on a rule which is not waiting for one', () => {
            deployment({rules: [rule('r1', {ok: false})], requiredInputs: []})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-input-open-r1')).not.toBeInTheDocument()
        })

        it('sends the rule its own fields, shaped as the server wants them', async () => {
            deployment({rules: [rule('r1', {ok: false}), rule('r2', {ok: false})], requiredInputs: ['r1', 'r2']})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            fireEvent.click(screen.getByTestId('mobile-deployment-input-open-r1'))

            // Only the rule that was tapped, not every rule waiting.
            expect(await screen.findByTestId('mobile-deployment-input-r1')).toBeInTheDocument()
            expect(screen.queryByTestId('mobile-deployment-input-r2')).not.toBeInTheDocument()

            fireEvent.click(screen.getByTestId('mobile-deployment-input-submit'))
            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            expect(callGraphQL.mock.calls[0][0].variables.pipelineId).toEqual('pipeline-1')
        })

        it('asks the server again once the input is in', async () => {
            callGraphQL.mockResolvedValue({updatePipelineData: {errors: null}})
            deployment({rules: [rule('r1', {ok: false})], requiredInputs: ['r1']})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            const before = queryDeps
            fireEvent.click(screen.getByTestId('mobile-deployment-input-open-r1'))
            fireEvent.click(await screen.findByTestId('mobile-deployment-input-submit'))
            await waitFor(() => expect(queryDeps).not.toEqual(before))
        })
    })

    describe('overriding a rule', () => {

        it('offers the override on a blocking rule which may be overridden', () => {
            deployment({rules: [rule('r1', {ok: false, canBeOverridden: true})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-override-open-r1')).toBeInTheDocument()
        })

        it('offers no override on a rule which passes', () => {
            deployment({rules: [rule('r1', {ok: true})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-override-open-r1')).not.toBeInTheDocument()
        })

        it('offers no override on a rule which may not be overridden', () => {
            deployment({rules: [rule('r1', {ok: false, canBeOverridden: false})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-override-open-r1')).not.toBeInTheDocument()
        })

        it('offers no second override on a rule already overridden', () => {
            deployment({
                rules: [rule('r1', {ok: false, overridden: true, override: {user: 'alice', message: 'x'}})],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-override-open-r1')).not.toBeInTheDocument()
        })

        it('requires a reason before anything leaves the phone', async () => {
            deployment({rules: [rule('r1', {ok: false})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            fireEvent.click(screen.getByTestId('mobile-deployment-override-open-r1'))
            fireEvent.click(await screen.findByTestId('mobile-deployment-override-submit'))
            expect(await screen.findByText('Reason is required.')).toBeInTheDocument()
            expect(callGraphQL).not.toHaveBeenCalled()
        })

        it('sends the override with its reason, and asks the server again', async () => {
            callGraphQL.mockResolvedValue({overridePipelineRule: {errors: null}})
            deployment({rules: [rule('r1', {ok: false})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            const before = queryDeps

            fireEvent.click(screen.getByTestId('mobile-deployment-override-open-r1'))
            const message = await screen.findByTestId('mobile-deployment-override-message')
            fireEvent.change(message, {target: {value: 'Hotfix, agreed with ops.'}})
            fireEvent.click(screen.getByTestId('mobile-deployment-override-submit'))

            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            expect(callGraphQL.mock.calls[0][0].variables).toEqual({
                pipelineId: 'pipeline-1',
                admissionRuleConfigId: 'r1',
                message: 'Hotfix, agreed with ops.',
            })
            await waitFor(() => expect(queryDeps).not.toEqual(before))
        })

        it('keeps saying the override is recorded', () => {
            // A smaller screen is a reason to be shorter, not a reason to be
            // quieter about the one irreversible thing on it.
            deployment({rules: [rule('r1', {ok: false})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            fireEvent.click(screen.getByTestId('mobile-deployment-override-open-r1'))
            expect(screen.getByTestId('mobile-deployment-override-warning')).toBeInTheDocument()
        })
    })

    describe('what an unauthorized user sees', () => {

        it('shows no run and no input to a user who may not deploy', () => {
            deployment({
                rules: [rule('r1', {ok: false})],
                requiredInputs: ['r1'],
                authorizations: [refused('pipeline', 'create'), granted('pipeline', 'override')],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-run')).not.toBeInTheDocument()
            expect(screen.queryByTestId('mobile-deployment-input-open-r1')).not.toBeInTheDocument()
        })

        it('shows no override to a user who may not override', () => {
            deployment({
                rules: [rule('r1', {ok: false, canBeOverridden: true})],
                authorizations: [granted('pipeline', 'create'), refused('pipeline', 'override')],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-override-open-r1')).not.toBeInTheDocument()
        })

        it('still shows the rules and their verdicts', () => {
            // Reading is not acting: a user who may not deploy can still need to
            // know why a deployment is stuck.
            deployment({rules: [rule('r1', {ok: false})], authorizations: []})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-rule-r1')).toHaveTextContent('Blocking')
        })
    })

    it('does not flash an empty deployment before the first answer arrives', () => {
        // `useQuery` starts with `loading` false and only flips it inside its
        // effect, so trusting `loading` alone would show "no such deployment"
        // over an answer on its way.
        queryResult = {data: null, loading: false, error: null, finished: false}
        render(<MobileDeploymentScreen id="pipeline-1"/>)
        expect(screen.queryByTestId('mobile-deployment-missing')).not.toBeInTheDocument()
    })
})
