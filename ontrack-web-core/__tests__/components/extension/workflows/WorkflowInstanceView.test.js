import React from "react";
import {act, render, screen, waitFor} from "@testing-library/react";
import '@testing-library/jest-dom';

// Ant Design uses window.matchMedia for responsive features; jsdom doesn't provide it
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
import WorkflowInstanceView from "@components/extension/workflows/WorkflowInstanceView";
import {UserContext} from "@components/providers/UserProvider";

jest.mock("next/head", () => function Head() {
    return null
})

jest.mock("../../../../components/layouts/MainPage", () => function MainPage({title, children}) {
    return (
        <div>
            <h1>{title}</h1>
            {children}
        </div>
    )
})

// Exposes the auto-refresh callback as a button, in place of the timer
jest.mock("../../../../components/common/AutoRefresh", () => ({
    AutoRefreshContextProvider: ({onRefresh, children}) => (
        <>
            <button onClick={onRefresh}>refresh</button>
            {children}
        </>
    ),
    AutoRefreshButton: () => null,
}))

jest.mock("../../../../components/extension/workflows/WorkflowNodeExecutorContext", () => function WorkflowNodeExecutorContextProvider({children}) {
    return <>{children}</>
})

jest.mock("../../../../components/extension/workflows/WorkflowInstanceStatus", () => function WorkflowInstanceStatus({status}) {
    return <span data-testid="status">{status}</span>
})

jest.mock("../../../../components/extension/workflows/WorkflowInstanceGraph", () => function WorkflowInstanceGraph({instanceNodeExecutions}) {
    return <span data-testid="node-executions">{instanceNodeExecutions ? instanceNodeExecutions.length : 'none'}</span>
})

jest.mock("../../../../components/extension/workflows/WorkflowInstanceStopButton", () => function WorkflowInstanceStopButton({onStopped}) {
    return <button onClick={onStopped}>stop</button>
})
jest.mock("../../../../components/common/TimestampText", () => () => null)
jest.mock("../../../../components/common/DurationMs", () => () => null)

/**
 * Holds each GraphQL call pending until the test answers it with `pending[n](body)`.
 */
const mockGraphQL = () => {
    const pending = []
    global.fetch = jest.fn().mockImplementation(() => new Promise(resolve => {
        pending.push((body) => resolve({
            ok: true,
            status: 200,
            json: async () => body,
        }))
    }))
    return pending
}

const requestOf = (index) => JSON.parse(global.fetch.mock.calls[index][1].body)

const instance = (status, nodesExecutions = []) => ({
    id: 'instance-1',
    status,
    endTime: null,
    durationMs: 0,
    timestamp: '2026-09-29T10:00:00Z',
    workflow: {name: 'Release', nodes: []},
    nodesExecutions,
})

describe('WorkflowInstanceView', () => {

    afterEach(() => {
        delete global.fetch
    })

    it('loads the instance, then refreshes its status and node executions', async () => {
        const pending = mockGraphQL()

        render(<WorkflowInstanceView id="instance-1"/>)

        await waitFor(() => expect(pending).toHaveLength(1))
        expect(requestOf(0).variables).toEqual({id: 'instance-1'})
        await act(async () => pending[0]({workflowInstance: instance('STARTED')}))

        expect(await screen.findByTestId('status')).toHaveTextContent('STARTED')
        expect(screen.getByText('Release [instance-1]')).toBeInTheDocument()
        expect(screen.getByTestId('node-executions')).toHaveTextContent('none')

        // Auto-refresh
        await act(async () => screen.getByText('refresh').click())
        await waitFor(() => expect(pending).toHaveLength(2))
        expect(requestOf(1).variables).toEqual({workflowInstanceId: 'instance-1'})
        await act(async () => pending[1]({workflowInstance: instance('RUNNING', [{id: 'a'}, {id: 'b'}])}))

        expect(screen.getByTestId('status')).toHaveTextContent('RUNNING')
        expect(screen.getByTestId('node-executions')).toHaveTextContent('2')
    })

    it('shows the status of whichever of the load and the refresh answered last', async () => {
        const pending = mockGraphQL()

        render(
            <UserContext.Provider value={{authorizations: {workflow: {stop: true}}}}>
                <WorkflowInstanceView id="instance-1"/>
            </UserContext.Provider>
        )

        await waitFor(() => expect(pending).toHaveLength(1))
        await act(async () => pending[0]({workflowInstance: instance('STARTED')}))
        expect(await screen.findByTestId('status')).toHaveTextContent('STARTED')

        // The refresh answers after the load
        await act(async () => screen.getByText('refresh').click())
        await waitFor(() => expect(pending).toHaveLength(2))
        await act(async () => pending[1]({workflowInstance: instance('RUNNING')}))
        expect(screen.getByTestId('status')).toHaveTextContent('RUNNING')

        // Stopping reloads the whole instance, which then answers after the refresh
        await act(async () => screen.getByText('stop').click())
        await waitFor(() => expect(pending).toHaveLength(3))
        expect(requestOf(2).variables).toEqual({id: 'instance-1'})
        await act(async () => pending[2]({workflowInstance: instance('STOPPED')}))
        await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('STOPPED'))
    })

})
