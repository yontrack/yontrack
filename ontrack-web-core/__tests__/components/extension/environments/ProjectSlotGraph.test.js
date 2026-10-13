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

/*
 * What the graph's query answered. Set as the real hook leaves it: on a failure, `data` is null and
 * `error` holds the message.
 */
let queryResult = {data: null, loading: false, error: null, finished: false}

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: () => queryResult,
}))

// React Flow does not render in jsdom, and what is under test is which of the graph, the empty
// state and the error is drawn - not how the graph draws.
jest.mock("reactflow", () => ({
    __esModule: true,
    ReactFlow: ({children}) => <div data-testid="flow">{children}</div>,
    Background: () => null,
    Controls: ({children}) => <div>{children}</div>,
    ControlButton: ({children, ...rest}) => <button {...rest}>{children}</button>,
    applyNodeChanges: (changes, nodes) => nodes,
    Handle: () => null,
    Position: {Left: 'left', Right: 'right'},
}))

jest.mock("../../../../components/links/GraphUtils", () => ({
    autoLayout: jest.fn(({nodes, edges, setNodes, setEdges}) => {
        setNodes(nodes)
        setEdges(edges)
        return Promise.resolve()
    }),
}))

import ProjectSlotGraph from "@components/extension/environments/project/ProjectSlotGraph"

const project = {id: 1, name: 'petclinic'}

describe('the project slot graph', () => {

    it('says the project has no slot when the answer has none', () => {
        queryResult = {data: {slotNodes: [], edges: []}, loading: false, error: null, finished: true}
        render(<ProjectSlotGraph project={project} qualifier=""/>)
        expect(screen.getByTestId('project-slot-graph-empty')).toBeInTheDocument()
        expect(screen.queryByTestId('project-slot-graph-error')).not.toBeInTheDocument()
    })

    it('says the graph could not be loaded when the query failed, rather than drawing an empty canvas', () => {
        queryResult = {data: null, loading: false, error: "Expected a rule of type versionPattern", finished: true}
        render(<ProjectSlotGraph project={project} qualifier=""/>)
        const alert = screen.getByTestId('project-slot-graph-error')
        expect(alert).toHaveTextContent('Could not load the environments.')
        expect(alert).toHaveTextContent('Expected a rule of type versionPattern')
        expect(screen.queryByTestId('flow')).not.toBeInTheDocument()
        expect(screen.queryByTestId('project-slot-graph-empty')).not.toBeInTheDocument()
    })
})
