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
import WorkflowInstanceNodesProgress from "@components/extension/workflows/WorkflowInstanceNodesProgress";

const node = (id, parents = []) => ({id, parents: parents.map(p => ({id: p}))})
const execution = (id, status) => ({id, status})

const chip = (id) => screen.queryByTestId(`workflow-node-chip-${id}`)
const column = (index) => screen.queryByTestId(`workflow-node-column-${index}`)

describe('WorkflowInstanceNodesProgress', () => {

    it('renders one chip per node, in depth columns', () => {
        render(
            <WorkflowInstanceNodesProgress
                nodes={[node('build'), node('test', ['build']), node('scan', ['build'])]}
                nodesExecutions={[
                    execution('build', 'SUCCESS'),
                    execution('test', 'SUCCESS'),
                    execution('scan', 'STARTED'),
                ]}
            />
        )
        expect(chip('build')).toBeInTheDocument()
        expect(chip('test')).toBeInTheDocument()
        expect(chip('scan')).toBeInTheDocument()
        // Two columns only
        expect(column(0)).toBeInTheDocument()
        expect(column(1)).toBeInTheDocument()
        expect(column(2)).not.toBeInTheDocument()
        // The parallel nodes are in the same column
        expect(column(1)).toContainElement(chip('test'))
        expect(column(1)).toContainElement(chip('scan'))
        expect(column(0)).toContainElement(chip('build'))
    })

    it('exposes the node status on each chip', () => {
        render(
            <WorkflowInstanceNodesProgress
                nodes={[node('ok'), node('ko')]}
                nodesExecutions={[execution('ok', 'SUCCESS'), execution('ko', 'ERROR')]}
            />
        )
        expect(chip('ok')).toHaveAttribute('data-status', 'SUCCESS')
        expect(chip('ko')).toHaveAttribute('data-status', 'ERROR')
    })

    it('falls back to CREATED for a node with no execution', () => {
        render(
            <WorkflowInstanceNodesProgress nodes={[node('pending')]} nodesExecutions={[]}/>
        )
        expect(chip('pending')).toHaveAttribute('data-status', 'CREATED')
    })

    it('shows the node id on the chip', () => {
        render(
            <WorkflowInstanceNodesProgress
                nodes={[node('publish-docker')]}
                nodesExecutions={[execution('publish-docker', 'SUCCESS')]}
            />
        )
        expect(screen.getByText('publish-docker')).toBeInTheDocument()
    })

    it('renders nothing when there is no node', () => {
        const {container} = render(<WorkflowInstanceNodesProgress nodes={[]} nodesExecutions={[]}/>)
        expect(container).toBeEmptyDOMElement()
    })

    describe('clamping', () => {

        const fanOut = (count) => {
            const nodes = [node('root')]
            const executions = [execution('root', 'SUCCESS')]
            for (let i = 0; i < count; i++) {
                nodes.push(node(`child-${i}`, ['root']))
                executions.push(execution(`child-${i}`, 'SUCCESS'))
            }
            return {nodes, nodesExecutions: executions}
        }

        it('shows every chip when a column has 4 nodes or fewer', () => {
            render(<WorkflowInstanceNodesProgress {...fanOut(4)}/>)
            for (let i = 0; i < 4; i++) {
                expect(chip(`child-${i}`)).toBeInTheDocument()
            }
            expect(screen.queryByTestId('workflow-node-overflow-1')).not.toBeInTheDocument()
        })

        it('clamps a wide column to 4 chips and shows the overflow count', () => {
            render(<WorkflowInstanceNodesProgress {...fanOut(6)}/>)
            const shown = [0, 1, 2, 3, 4, 5].filter(i => chip(`child-${i}`) !== null)
            expect(shown).toHaveLength(4)
            const overflow = screen.getByTestId('workflow-node-overflow-1')
            expect(overflow).toHaveTextContent('+2 more')
        })

        it('never hides a failed node behind the clamp', () => {
            const {nodes, nodesExecutions} = fanOut(8)
            // The last child, which the clamp would otherwise drop, is the failing one
            const failing = nodesExecutions.find(e => e.id === 'child-7')
            failing.status = 'ERROR'
            render(<WorkflowInstanceNodesProgress nodes={nodes} nodesExecutions={nodesExecutions}/>)
            expect(chip('child-7')).toBeInTheDocument()
            const shown = [0, 1, 2, 3, 4, 5, 6, 7].filter(i => chip(`child-${i}`) !== null)
            expect(shown).toHaveLength(4)
            expect(screen.getByTestId('workflow-node-overflow-1')).toHaveTextContent('+4 more')
        })

        it('keeps cancelled and timed-out nodes too', () => {
            const {nodes, nodesExecutions} = fanOut(8)
            nodesExecutions.find(e => e.id === 'child-6').status = 'CANCELLED'
            nodesExecutions.find(e => e.id === 'child-7').status = 'TIMEOUT'
            render(<WorkflowInstanceNodesProgress nodes={nodes} nodesExecutions={nodesExecutions}/>)
            expect(chip('child-6')).toBeInTheDocument()
            expect(chip('child-7')).toBeInTheDocument()
        })

        it('keeps the declaration order of the chips it shows', () => {
            const {nodes, nodesExecutions} = fanOut(8)
            nodesExecutions.find(e => e.id === 'child-7').status = 'ERROR'
            render(<WorkflowInstanceNodesProgress nodes={nodes} nodesExecutions={nodesExecutions}/>)
            const rendered = Array.from(column(1).querySelectorAll('[data-testid^="workflow-node-chip-"]'))
                .map(el => el.getAttribute('data-testid'))
            expect(rendered).toEqual([
                'workflow-node-chip-child-0',
                'workflow-node-chip-child-1',
                'workflow-node-chip-child-2',
                'workflow-node-chip-child-7',
            ])
        })
    })
})
