import React from "react";
import {render, screen} from "@testing-library/react";

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
import '@testing-library/jest-dom';
import WorkflowInstanceCard from "@components/extension/workflows/WorkflowInstanceCard";

const instance = ({
                      id = 'instance-1',
                      name = 'Release pipeline',
                      status = 'SUCCESS',
                      durationMs = 261000,
                      nodes = [{id: 'build'}, {id: 'test', parents: [{id: 'build'}]}],
                      nodesExecutions = [
                          {id: 'build', status: 'SUCCESS'},
                          {id: 'test', status: 'SUCCESS'},
                      ],
                  } = {}) => ({
    id,
    status,
    durationMs,
    workflow: {name, nodes},
    nodesExecutions,
})

describe('WorkflowInstanceCard', () => {

    it('shows the workflow name', () => {
        render(<WorkflowInstanceCard instance={instance({name: 'Downstream bumps'})}/>)
        expect(screen.getByText('Downstream bumps')).toBeInTheDocument()
    })

    it('shows the instance status', () => {
        render(<WorkflowInstanceCard instance={instance({status: 'SUCCESS'})}/>)
        expect(screen.getByText('Success')).toBeInTheDocument()
    })

    it('shows the duration', () => {
        render(<WorkflowInstanceCard instance={instance({durationMs: 261000})}/>)
        expect(screen.getByTestId('workflow-instance-duration')).toBeInTheDocument()
    })

    it('shows no duration for a workflow with no finished node yet', () => {
        // durationMs is 0 until a node finishes; "0 ms" would read as "completed instantly"
        render(<WorkflowInstanceCard instance={instance({status: 'STARTED', durationMs: 0})}/>)
        expect(screen.queryByTestId('workflow-instance-duration')).not.toBeInTheDocument()
    })

    it('links to the workflow instance page', () => {
        render(<WorkflowInstanceCard instance={instance({id: 'abc-123'})}/>)
        const link = screen.getByRole('link', {name: /Open workflow/})
        expect(link).toHaveAttribute('href', '/extension/workflows/instances/abc-123')
    })

    it('renders the node strip', () => {
        render(<WorkflowInstanceCard instance={instance()}/>)
        expect(screen.getByTestId('workflow-node-chip-build')).toBeInTheDocument()
        expect(screen.getByTestId('workflow-node-chip-test')).toBeInTheDocument()
    })

    it('is identifiable by the instance id', () => {
        render(<WorkflowInstanceCard instance={instance({id: 'abc-123'})}/>)
        expect(screen.getByTestId('workflow-instance-card-abc-123')).toBeInTheDocument()
    })

    describe('error block', () => {

        const failing = () => instance({
            status: 'ERROR',
            nodes: [{id: 'promote-check'}, {id: 'bump-kdsl', parents: [{id: 'promote-check'}]}],
            nodesExecutions: [
                {id: 'promote-check', status: 'SUCCESS'},
                {
                    id: 'bump-kdsl',
                    status: 'ERROR',
                    error: 'No branch matching "release/.*" in project "ontrack-kdsl"',
                },
            ],
        })

        it('is absent on success', () => {
            render(<WorkflowInstanceCard instance={instance({status: 'SUCCESS'})}/>)
            expect(screen.queryByTestId('workflow-node-error')).not.toBeInTheDocument()
        })

        it('names the failing node and shows its error output', () => {
            render(<WorkflowInstanceCard instance={failing()}/>)
            const error = screen.getByTestId('workflow-node-error')
            expect(error).toBeInTheDocument()
            expect(error).toHaveTextContent('bump-kdsl')
            expect(error).toHaveTextContent('No branch matching "release/.*" in project "ontrack-kdsl"')
        })

        it('shows the first failed node only', () => {
            const twoFailures = instance({
                status: 'ERROR',
                nodes: [{id: 'first'}, {id: 'second'}],
                nodesExecutions: [
                    {id: 'first', status: 'ERROR', error: 'First failure'},
                    {id: 'second', status: 'ERROR', error: 'Second failure'},
                ],
            })
            render(<WorkflowInstanceCard instance={twoFailures}/>)
            expect(screen.getAllByTestId('workflow-node-error')).toHaveLength(1)
            expect(screen.getByTestId('workflow-node-error')).toHaveTextContent('First failure')
        })

        it('is absent when the failed node carries no error message', () => {
            const noMessage = instance({
                status: 'ERROR',
                nodes: [{id: 'silent'}],
                nodesExecutions: [{id: 'silent', status: 'ERROR', error: null}],
            })
            render(<WorkflowInstanceCard instance={noMessage}/>)
            expect(screen.queryByTestId('workflow-node-error')).not.toBeInTheDocument()
        })

        it('is shown for a stopped workflow too', () => {
            const stopped = instance({
                status: 'STOPPED',
                nodes: [{id: 'cancelled-node'}],
                nodesExecutions: [{id: 'cancelled-node', status: 'CANCELLED', error: 'Stopped by user'}],
            })
            render(<WorkflowInstanceCard instance={stopped}/>)
            expect(screen.getByTestId('workflow-node-error')).toHaveTextContent('Stopped by user')
        })
    })
})
