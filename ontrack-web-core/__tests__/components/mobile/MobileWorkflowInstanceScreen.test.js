import "@testing-library/jest-dom"
import {act, render, screen} from "@testing-library/react"

/**
 * One workflow run on a phone.
 *
 * What a UI test running against a real instance cannot show, and what is
 * therefore pinned here: the not-found state (a real run always exists), the
 * node ordering (a fixture would need a workflow shaped on purpose and a run of
 * it), and the polling starting and - more to the point - *stopping*, which is
 * invisible from the outside and is the only auto-refresh anywhere in the mobile
 * UI.
 */

let fullResult = {data: null, loading: false, error: null, finished: true}
let progressResult = {data: null, loading: false, error: null, finished: false}
/** Every query the screen ran, so a test can count the polls. */
let calls = []

jest.mock("../../../components/services/GraphQL", () => ({
    useQuery: (query, {deps = [], condition = true, variables = {}} = {}) => {
        const text = String(query)
        const progress = text.includes('MobileWorkflowInstanceProgress')
        calls.push({progress, condition, deps: [...deps], variables})
        if (progress) return condition ? progressResult : {...progressResult, data: null}
        return fullResult
    },
}))

import MobileWorkflowInstanceScreen, {POLL_INTERVAL_MS}
    from "@/app/mobile/workflow-instance/[id]/WorkflowInstanceScreen"
import MobileWorkflowInstancePage from "@/app/mobile/workflow-instance/[id]/page"

const node = (id, {description, executorId = 'mock', parents = []} = {}) => ({
    id,
    description,
    executorId,
    parents: parents.map(parentId => ({id: parentId})),
})

const execution = (id, status, {error = null, durationMs = 12} = {}) => ({id, status, error, durationMs})

const instance = ({
                      status = 'SUCCESS',
                      finished = true,
                      durationMs = 1200,
                      trigger = 'notification-record',
                      nodes = [],
                      nodesExecutions = [],
                  } = {}) => {
    fullResult = {
        data: {
            workflowInstance: {
                id: '2026-09-12T14:27:57.595125-eb0102b5-1432-4821-8ae2-0edf5a4a0b3f',
                status,
                finished,
                startTime: '2026-09-12T14:27:57Z',
                endTime: finished ? '2026-09-12T14:27:59Z' : null,
                durationMs,
                triggerData: trigger ? {id: trigger} : null,
                workflow: {name: 'canary', nodes},
                nodesExecutions,
            },
        },
        loading: false,
        error: null,
        finished: true,
    }
}

/** What the polling query answers, once it has been allowed to run. */
const progress = ({status = 'SUCCESS', finished = true, durationMs = 2000, nodesExecutions = []} = {}) => {
    progressResult = {
        data: {workflowInstance: {status, finished, endTime: null, durationMs, nodesExecutions}},
        loading: false,
        error: null,
        finished: true,
    }
}

beforeEach(() => {
    fullResult = {data: null, loading: false, error: null, finished: true}
    progressResult = {data: null, loading: false, error: null, finished: false}
    calls = []
})

describe('the mobile workflow instance screen', () => {

    describe('identity', () => {

        it('is called after the workflow', () => {
            instance()
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            expect(screen.getByTestId('mobile-screen-title')).toHaveTextContent('canary')
        })

        it('names what set the run off, in words', () => {
            // No upward link: a slot instance carries a pipeline id, a
            // promotion-triggered one only an opaque notification-record id
            // whose resolution needs a right this user may not hold. Two
            // branches so the user can avoid a back gesture they already have is
            // not worth it, so the origin is named instead.
            instance({trigger: 'slot-pipeline'})
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            expect(screen.getByTestId('mobile-workflow-instance-origin'))
                .toHaveTextContent('Run for a deployment')
        })

        it('says the run is finished and how long it took', () => {
            instance({status: 'SUCCESS', finished: true, durationMs: 1200})
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            const status = screen.getByTestId('mobile-workflow-instance-status')
            expect(status).toHaveTextContent('Success')
            expect(status).toHaveTextContent('1200 ms')
        })

        it('does not put a duration on a run that is still going', () => {
            // A duration on a running workflow is the age of a snapshot, not how
            // long it took.
            instance({status: 'RUNNING', finished: false, durationMs: 900})
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            const status = screen.getByTestId('mobile-workflow-instance-status')
            expect(status).toHaveTextContent('Running')
            expect(status).not.toHaveTextContent('900 ms')
        })
    })

    describe('the route parameter', () => {

        it('is decoded before it reaches the query', () => {
            // An instance id is `ISO_LOCAL_DATE_TIME-UUID`, so it carries colons,
            // and the App Router hands a dynamic segment back exactly as it
            // appears in the URL - percent-encoded. Passed on as it arrives it is
            // an id no instance has, and the screen says a run that is right
            // there could not be found.
            instance()
            render(<MobileWorkflowInstancePage params={{
                id: '2026-09-12T14%3A27%3A57.595125-eb0102b5-1432-4821-8ae2-0edf5a4a0b3f',
            }}/>)
            const asked = calls.filter(call => !call.progress).map(call => call.variables.id)
            expect(asked).toContain('2026-09-12T14:27:57.595125-eb0102b5-1432-4821-8ae2-0edf5a4a0b3f')
        })
    })

    describe('the not-found state', () => {

        it('says the run could not be found rather than showing an empty page', () => {
            // `MobileAsyncContent` would draw an `Empty` for a null instance,
            // which reads as "this workflow has no content" rather than "no such
            // workflow" - and following a stale shared link is how someone gets
            // here. It is also what a run the user may not read answers with
            // (#1739), which must not say so either way.
            fullResult = {data: {workflowInstance: null}, loading: false, error: null, finished: true}
            render(<MobileWorkflowInstanceScreen id="gone"/>)
            expect(screen.getByTestId('mobile-workflow-instance-missing'))
                .toHaveTextContent('This workflow run could not be found.')
            expect(screen.queryByTestId('mobile-workflow-instance-nodes')).not.toBeInTheDocument()
        })
    })

    describe('the nodes', () => {

        it('are ordered by depth, then by declaration order', () => {
            // The desktop's own `workflowNodeDepths`, flattened - no second
            // ordering rule is invented for the phone. Declared out of order on
            // purpose: `deploy` depends on both tests, so it can only come last
            // whatever the declaration says.
            instance({
                nodes: [
                    node('deploy', {parents: ['unit', 'integration']}),
                    node('integration', {parents: ['build']}),
                    node('build'),
                    node('unit', {parents: ['build']}),
                ],
                nodesExecutions: [
                    execution('build', 'SUCCESS'),
                    execution('unit', 'SUCCESS'),
                    execution('integration', 'SUCCESS'),
                    execution('deploy', 'CREATED'),
                ],
            })
            render(<MobileWorkflowInstanceScreen id="run-1"/>)

            const rows = screen.getAllByTestId(/^mobile-workflow-node-[a-z]+$/)
            expect(rows.map(row => row.getAttribute('data-testid'))).toEqual([
                'mobile-workflow-node-build',
                'mobile-workflow-node-integration',
                'mobile-workflow-node-unit',
                'mobile-workflow-node-deploy',
            ])
        })

        it('name themselves by description, and fall back to their id', () => {
            instance({
                nodes: [node('build', {description: 'Building the thing'}), node('publish')],
                nodesExecutions: [execution('build', 'SUCCESS'), execution('publish', 'SUCCESS')],
            })
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            expect(screen.getByTestId('mobile-workflow-node-build')).toHaveTextContent('Building the thing')
            expect(screen.getByTestId('mobile-workflow-node-publish')).toHaveTextContent('publish')
        })

        it('say what the node actually does, which is its executor', () => {
            instance({
                nodes: [node('build', {description: 'Building', executorId: 'notification'})],
                nodesExecutions: [execution('build', 'SUCCESS')],
            })
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            expect(screen.getByTestId('mobile-workflow-node-build')).toHaveTextContent('notification')
        })

        it('carry their status and their duration', () => {
            instance({
                nodes: [node('build')],
                nodesExecutions: [execution('build', 'ERROR', {durationMs: 42})],
            })
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            const row = screen.getByTestId('mobile-workflow-node-build')
            expect(row).toHaveTextContent('Error')
            expect(row).toHaveTextContent('42 ms')
        })

        it('show the error of every failed node, not only the first', () => {
            // There is no side panel on a phone to go and find the others in.
            instance({
                nodes: [node('unit'), node('integration')],
                nodesExecutions: [
                    execution('unit', 'ERROR', {error: 'Unit tests failed'}),
                    execution('integration', 'ERROR', {error: 'Integration tests failed'}),
                ],
            })
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            expect(screen.getByTestId('mobile-workflow-error-unit'))
                .toHaveTextContent('Unit tests failed')
            expect(screen.getByTestId('mobile-workflow-error-integration'))
                .toHaveTextContent('Integration tests failed')
        })

        it('say so when the workflow declares none', () => {
            instance({nodes: [], nodesExecutions: []})
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            expect(screen.getByTestId('mobile-workflow-instance-nodes'))
                .toHaveTextContent('This workflow has no node.')
        })
    })

    describe('no action is offered', () => {

        it('has neither a stop nor an override anywhere', () => {
            instance({
                status: 'RUNNING',
                finished: false,
                nodes: [node('build')],
                nodesExecutions: [execution('build', 'STARTED')],
            })
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            expect(screen.queryByRole('button', {name: /stop/i})).not.toBeInTheDocument()
            expect(screen.queryByRole('button', {name: /override/i})).not.toBeInTheDocument()
        })
    })

    describe('polling', () => {

        beforeEach(() => jest.useFakeTimers())
        afterEach(() => jest.useRealTimers())

        /** How many times the narrow progress query was actually allowed to run. */
        const polls = () => calls.filter(call => call.progress && call.condition).length

        it('does not run at all on a run that is already finished', () => {
            // Nothing else in the mobile UI auto-refreshes, and a finished run is
            // nothing else.
            instance({status: 'SUCCESS', finished: true})
            render(<MobileWorkflowInstanceScreen id="run-1"/>)
            act(() => jest.advanceTimersByTime(POLL_INTERVAL_MS * 4))
            expect(polls()).toEqual(0)
        })

        it('asks again while the run is not finished', () => {
            instance({
                status: 'RUNNING',
                finished: false,
                nodes: [node('build')],
                nodesExecutions: [execution('build', 'STARTED')],
            })
            progress({
                status: 'RUNNING',
                finished: false,
                nodesExecutions: [execution('build', 'STARTED')],
            })
            render(<MobileWorkflowInstanceScreen id="run-1"/>)

            expect(polls()).toEqual(0)
            act(() => jest.advanceTimersByTime(POLL_INTERVAL_MS))
            expect(polls()).toBeGreaterThan(0)
        })

        it('stops asking once the run finishes, and shows the answer that said so', () => {
            instance({
                status: 'RUNNING',
                finished: false,
                nodes: [node('build')],
                nodesExecutions: [execution('build', 'STARTED')],
            })
            progress({
                status: 'SUCCESS',
                finished: true,
                durationMs: 2000,
                nodesExecutions: [execution('build', 'SUCCESS')],
            })
            render(<MobileWorkflowInstanceScreen id="run-1"/>)

            act(() => jest.advanceTimersByTime(POLL_INTERVAL_MS))
            const after = polls()

            // The finished answer is what the screen now shows...
            expect(screen.getByTestId('mobile-workflow-instance-status')).toHaveTextContent('Success')
            expect(screen.getByTestId('mobile-workflow-node-build')).toHaveTextContent('Success')

            // ... and no further poll is scheduled, however long nobody touches
            // the phone.
            act(() => jest.advanceTimersByTime(POLL_INTERVAL_MS * 10))
            expect(polls()).toEqual(after)
        })
    })
})
