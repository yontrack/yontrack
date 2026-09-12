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
 * One deployment on a phone, from candidate to settled.
 *
 * Everything here is about what a person can do to a deployment which is still
 * unsettled - answer a rule, override a rule, start it, complete it, cancel it -
 * and about who is allowed to do them. The screen is also where "actions are
 * absent for unauthorized users" is actually decided, which a UI test running as
 * the suite's admin account cannot show (#1725, #1736).
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

/**
 * One configured slot workflow, and its run for this pipeline when it has had
 * one.
 */
const slotWorkflow = (id, trigger, {
    name = `workflow-${id}`,
    instance = null,
    overridden = false,
    override = null,
} = {}) => ({
    id,
    trigger,
    workflow: {name},
    slotWorkflowInstanceForPipeline: instance ? {
        id: `swi-${id}`,
        overridden,
        override,
        workflowInstance: {
            id: `2026-09-12T14:27:57.595125-0000000${id}-1432-4821-8ae2-0edf5a4a0b3f`,
            status: instance.status ?? 'SUCCESS',
            startTime: '2026-09-12T14:27:57Z',
            durationMs: instance.durationMs ?? 1000,
            finished: instance.finished ?? true,
        },
    } : null,
})

const deployment = ({
                        status = 'CANDIDATE',
                        rules = [],
                        workflows = [],
                        requiredInputs = [],
                        runAction = {ok: true, successCount: 1, totalCount: 1},
                        finishAction = {ok: true, successCount: 1, totalCount: 1},
                        errorMessage = null,
                        lastChange = null,
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
                    candidateWorkflows: workflows.filter(it => it.trigger === 'CANDIDATE'),
                    runningWorkflows: workflows.filter(it => it.trigger === 'RUNNING'),
                    doneWorkflows: workflows.filter(it => it.trigger === 'DONE'),
                },
                admissionRules: rules,
                requiredInputs: requiredInputs.map(id => ({
                    config: {id, name: id, description: null, ruleId: 'manual', ruleConfig: {}},
                })),
                runAction,
                finishAction,
                errorMessage,
                lastChange,
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

        it('phrases each rule in the desktop UI\'s own words', () => {
            deployment({rules: [rule('r1', {ruleId: 'promotion', ruleConfig: {promotion: 'GOLD'}})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-rule-r1'))
                .toHaveTextContent('GOLD promotion is required')
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

        it('offers no start once the deployment is running', () => {
            // The lifecycle action of a RUNNING deployment is completing it, not
            // starting it again.
            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-run')).not.toBeInTheDocument()
            expect(screen.getByTestId('mobile-deployment-finish')).toBeInTheDocument()
        })
    })

    describe('completing it', () => {

        it('offers the completion on a running deployment', () => {
            deployment({status: 'RUNNING', finishAction: {ok: true}})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-finish')).toBeEnabled()
        })

        it('offers no completion on a candidate', () => {
            deployment({status: 'CANDIDATE'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-finish')).not.toBeInTheDocument()
        })

        it('gives the blocking workflow\'s own words rather than a check count', () => {
            // Admission rules gate CANDIDATE to RUNNING and nothing else, so the
            // rule list of a RUNNING deployment is all green: a "n of m checks
            // passed" caption would point at rules which have nothing to do with
            // why the button is off. `errorMessage` is the failing workflow's
            // own reason.
            deployment({
                status: 'RUNNING',
                finishAction: {ok: false},
                errorMessage: "Workflow is running",
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-finish')).toBeDisabled()
            expect(screen.getByTestId('mobile-deployment-finish-blocked'))
                .toHaveTextContent('Workflow is running')
        })

        it('asks for a confirmation naming the environment and the build', async () => {
            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            fireEvent.click(screen.getByTestId('mobile-deployment-finish'))
            const confirm = await screen.findByTestId('mobile-deployment-finish-confirm')
            expect(confirm).toHaveTextContent('production')
            expect(confirm).toHaveTextContent('1.4.0')
            expect(callGraphQL).not.toHaveBeenCalled()
        })

        it('completes the deployment and asks the server again', async () => {
            callGraphQL.mockResolvedValue({
                finishSlotPipelineDeployment: {finishStatus: {ok: true, message: ''}, errors: null},
            })
            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            const before = queryDeps

            fireEvent.click(screen.getByTestId('mobile-deployment-finish'))
            fireEvent.click(await screen.findByTestId('mobile-deployment-finish-submit'))

            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            // `forcing` stays false: forcing is the desktop's override-gated
            // command and is out of scope here.
            expect(callGraphQL.mock.calls[0][0].variables).toEqual({id: 'pipeline-1'})
            expect(callGraphQL.mock.calls[0][0].query).toContain('forcing: false')
            await waitFor(() => expect(queryDeps).not.toEqual(before))
        })

        it('shows a refused completion rather than swallowing it', async () => {
            // "Only the last pipeline can be deployed." is checked at submit time
            // and is invisible to `finishAction`, so the button can be enabled and
            // the mutation still refuse.
            callGraphQL.mockResolvedValue({
                finishSlotPipelineDeployment: {
                    finishStatus: {ok: false, message: "Only the last pipeline can be deployed."},
                    errors: null,
                },
            })
            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)

            fireEvent.click(screen.getByTestId('mobile-deployment-finish'))
            fireEvent.click(await screen.findByTestId('mobile-deployment-finish-submit'))

            expect(await screen.findByTestId('mobile-deployment-error'))
                .toHaveTextContent('Only the last pipeline can be deployed.')
        })
    })

    describe('cancelling it', () => {

        it('offers the cancellation on a candidate and on a running deployment', () => {
            deployment({status: 'CANDIDATE'})
            const {unmount} = render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-cancel')).toBeInTheDocument()
            unmount()

            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-cancel')).toBeInTheDocument()
        })

        it('keeps the destructive action off the lifecycle button\'s own row', () => {
            // The presentation is the mitigation: side by side puts a destructive
            // tap a thumb-width from the constructive one.
            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            const row = screen.getByTestId('mobile-deployment-cancel-row')
            expect(row).toContainElement(screen.getByTestId('mobile-deployment-cancel'))
            expect(row).not.toContainElement(screen.getByTestId('mobile-deployment-finish'))
        })

        it('marks the cancellation as destructive and the lifecycle action as primary', () => {
            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-cancel')).toHaveClass('ant-btn-dangerous')
            expect(screen.getByTestId('mobile-deployment-cancel')).not.toHaveClass('ant-btn-primary')
            expect(screen.getByTestId('mobile-deployment-finish')).toHaveClass('ant-btn-primary')
        })

        it('requires a reason before anything leaves the phone', async () => {
            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            fireEvent.click(screen.getByTestId('mobile-deployment-cancel'))
            fireEvent.click(await screen.findByTestId('mobile-deployment-cancel-submit'))
            expect(await screen.findByText('Reason is required.')).toBeInTheDocument()
            expect(callGraphQL).not.toHaveBeenCalled()
        })

        it('sends the cancellation with its reason, and asks the server again', async () => {
            callGraphQL.mockResolvedValue({cancelSlotPipeline: {errors: null}})
            deployment({status: 'RUNNING'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            const before = queryDeps

            fireEvent.click(screen.getByTestId('mobile-deployment-cancel'))
            const reason = await screen.findByTestId('mobile-deployment-cancel-reason')
            fireEvent.change(reason, {target: {value: 'CI died, nobody is coming.'}})
            fireEvent.click(screen.getByTestId('mobile-deployment-cancel-submit'))

            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            expect(callGraphQL.mock.calls[0][0].variables).toEqual({
                pipelineId: 'pipeline-1',
                reason: 'CI died, nobody is coming.',
            })
            await waitFor(() => expect(queryDeps).not.toEqual(before))
        })
    })

    describe('a settled deployment', () => {

        it('says the deployment is finished, and offers nothing', () => {
            deployment({status: 'DONE'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-settled')).toHaveTextContent(/finished/i)
            expect(screen.queryByTestId('mobile-deployment-run')).not.toBeInTheDocument()
            expect(screen.queryByTestId('mobile-deployment-finish')).not.toBeInTheDocument()
            expect(screen.queryByTestId('mobile-deployment-cancel')).not.toBeInTheDocument()
        })

        it('reads the cancellation reason back beside a cancelled one', () => {
            deployment({status: 'CANCELLED', lastChange: {message: 'CI died, nobody is coming.'}})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            const settled = screen.getByTestId('mobile-deployment-settled')
            expect(settled).toHaveTextContent(/cancelled/i)
            expect(settled).toHaveTextContent('CI died, nobody is coming.')
        })

        it('no longer points at the desktop version, because there is nothing left to go there for', () => {
            deployment({status: 'DONE'})
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-settled')).not.toHaveTextContent(/desktop/i)
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

    describe('the workflows', () => {

        it('is absent, not empty, on a slot which declares none', () => {
            // Most slots have none, and an empty section on every deployment
            // screen in the product to serve the minority that has one is pure
            // cost - the same reason *Deployments in progress* is absent on the
            // build screen.
            deployment()
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-workflows')).not.toBeInTheDocument()
        })

        it('shows all three triggers, candidate first, whatever the status', () => {
            // A slot workflow is configuration as much as state: one which never
            // ran is frequently *why* nothing ever deployed here, so hiding the
            // triggers whose turn has not come hides exactly that.
            deployment({
                status: 'RUNNING',
                workflows: [
                    slotWorkflow('c1', 'CANDIDATE', {name: 'gate'}),
                    slotWorkflow('r1', 'RUNNING', {name: 'smoke'}),
                    slotWorkflow('d1', 'DONE', {name: 'announce'}),
                ],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)

            const rows = screen.getAllByTestId(/^mobile-deployment-workflow-[a-z0-9]+$/)
            expect(rows.map(row => row.getAttribute('data-testid'))).toEqual([
                'mobile-deployment-workflow-c1',
                'mobile-deployment-workflow-r1',
                'mobile-deployment-workflow-d1',
            ])
            expect(screen.getByTestId('mobile-deployment-workflow-c1')).toHaveTextContent('On candidate')
            expect(screen.getByTestId('mobile-deployment-workflow-r1')).toHaveTextContent('On running')
            expect(screen.getByTestId('mobile-deployment-workflow-d1')).toHaveTextContent('On deployment done')
        })

        it('says Not started for a workflow which has never run, and offers no link', () => {
            deployment({workflows: [slotWorkflow('c1', 'CANDIDATE', {name: 'gate'})]})
            render(<MobileDeploymentScreen id="pipeline-1"/>)

            const row = screen.getByTestId('mobile-deployment-workflow-c1')
            expect(row).toHaveTextContent('gate')
            expect(screen.getByTestId('mobile-deployment-workflow-status-c1'))
                .toHaveTextContent('Not started')
            // A tap that 404s is worse than a row that does not move.
            expect(screen.queryByTestId('mobile-deployment-workflow-link-c1')).not.toBeInTheDocument()
        })

        it('taps a workflow which has run through to its own run', () => {
            deployment({
                workflows: [slotWorkflow('r1', 'RUNNING', {name: 'smoke', instance: {status: 'ERROR'}})],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)

            expect(screen.getByTestId('mobile-deployment-workflow-status-r1')).toHaveTextContent('Error')
            expect(screen.getByTestId('mobile-deployment-workflow-link-r1'))
                .toHaveAttribute(
                    'href',
                    '/mobile/workflow-instance/2026-09-12T14:27:57.595125-0000000r1-1432-4821-8ae2-0edf5a4a0b3f',
                )
        })

        it('says who overrode a workflow and what they said', () => {
            // Read-only is a decision about what the phone lets you *do*, not
            // about what it lets you know: a row reading Error with no sign that
            // a human deliberately waved it through would be actively
            // misleading.
            deployment({
                workflows: [
                    slotWorkflow('r1', 'RUNNING', {
                        name: 'smoke',
                        instance: {status: 'ERROR'},
                        overridden: true,
                        override: {user: 'alice', message: 'Known flake, agreed with ops.'},
                    }),
                ],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)

            const overridden = screen.getByTestId('mobile-deployment-workflow-overridden-r1')
            expect(overridden).toHaveTextContent('alice')
            expect(overridden).toHaveTextContent('Known flake, agreed with ops.')
        })

        it('offers no action on a workflow, whatever the user may do', () => {
            // Overriding a blocking workflow needs `SlotUpdate` *and*
            // `SlotPipelineOverride`, a pair the role this screen is built
            // around does not hold - so it stays on the desktop, and so does
            // stopping a run.
            deployment({
                status: 'RUNNING',
                workflows: [slotWorkflow('r1', 'RUNNING', {instance: {status: 'RUNNING', finished: false}})],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)

            const section = screen.getByTestId('mobile-deployment-workflows')
            expect(section.querySelectorAll('button')).toHaveLength(0)
        })

        it('does not supersede the caption saying why the button is off', () => {
            // The caption says *why the button is off* for someone who reads one
            // line and taps nothing; the section says *what is going on*. Both,
            // at different distances from the button.
            deployment({
                status: 'RUNNING',
                finishAction: {ok: false},
                errorMessage: 'Workflow is running',
                workflows: [slotWorkflow('d1', 'DONE', {instance: {status: 'RUNNING', finished: false}})],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)

            expect(screen.getByTestId('mobile-deployment-finish-blocked'))
                .toHaveTextContent('Workflow is running')
            expect(screen.getByTestId('mobile-deployment-workflows')).toBeInTheDocument()
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

        it('shows neither completion nor cancellation to a user who may not deploy', () => {
            // Both are off the slot's own `pipeline/create`, as the rest of the
            // screen is: `EnvironmentsRoleContributor` grants `SlotPipelineFinish`
            // and `SlotPipelineCancel` from exactly the roles granting
            // `SlotPipelineCreate`, and the slot publishes neither as an
            // authorization of its own.
            deployment({
                status: 'RUNNING',
                authorizations: [refused('pipeline', 'create'), granted('pipeline', 'override')],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.queryByTestId('mobile-deployment-finish')).not.toBeInTheDocument()
            expect(screen.queryByTestId('mobile-deployment-cancel')).not.toBeInTheDocument()
        })

        it('still says why a running deployment cannot be completed', () => {
            // Reading is never gated: a user who may not act can still need to
            // know what is holding the deployment up.
            deployment({
                status: 'RUNNING',
                finishAction: {ok: false},
                errorMessage: 'Workflow is in error',
                authorizations: [refused('pipeline', 'create')],
            })
            render(<MobileDeploymentScreen id="pipeline-1"/>)
            expect(screen.getByTestId('mobile-deployment-finish-blocked'))
                .toHaveTextContent('Workflow is in error')
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
