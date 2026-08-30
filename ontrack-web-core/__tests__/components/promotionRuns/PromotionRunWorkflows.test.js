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

let queryResult = {data: [], loading: false, error: null, finished: true}

jest.mock("../../../components/services/GraphQL", () => ({
    useQuery: () => queryResult,
}))

import PromotionRunWorkflows from "@components/promotionRuns/PromotionRunWorkflows";

const setResult = (result) => {
    queryResult = {data: [], loading: false, error: null, finished: true, ...result}
}

const instance = (id, name, status = 'SUCCESS') => ({
    id,
    status,
    durationMs: 1000,
    workflow: {name, nodes: [{id: 'only-node', executorId: 'mock'}]},
    nodesExecutions: [{id: 'only-node', status, error: null}],
})

describe('PromotionRunWorkflows', () => {

    it('shows an empty state, not a table, when there is no workflow', () => {
        setResult({data: []})
        render(<PromotionRunWorkflows promotionRunId={1}/>)
        expect(screen.getByText('No workflow was triggered by this promotion.')).toBeInTheDocument()
        expect(screen.queryByRole('table')).not.toBeInTheDocument()
    })

    it('does not show the empty state before the first fetch resolves', () => {
        setResult({data: [], finished: false, loading: true})
        render(<PromotionRunWorkflows promotionRunId={1}/>)
        expect(screen.queryByText('No workflow was triggered by this promotion.')).not.toBeInTheDocument()
    })

    it('renders one card per workflow instance', () => {
        setResult({data: [instance('a', 'Release pipeline'), instance('b', 'Downstream bumps')]})
        render(<PromotionRunWorkflows promotionRunId={1}/>)
        expect(screen.getByTestId('workflow-instance-card-a')).toBeInTheDocument()
        expect(screen.getByTestId('workflow-instance-card-b')).toBeInTheDocument()
        expect(screen.getByText('Release pipeline')).toBeInTheDocument()
        expect(screen.getByText('Downstream bumps')).toBeInTheDocument()
    })

    it('shows the number of workflows in the section title', () => {
        setResult({data: [instance('a', 'One'), instance('b', 'Two'), instance('c', 'Three')]})
        render(<PromotionRunWorkflows promotionRunId={1}/>)
        expect(screen.getByText('Workflows (3)')).toBeInTheDocument()
    })

    it('reports an error instead of the empty state when the query fails', () => {
        // `useQuery` nulls its data on error, and "nothing ran" must not be shown for
        // "we could not find out"
        setResult({data: null, error: 'Boom', finished: true})
        render(<PromotionRunWorkflows promotionRunId={1}/>)
        expect(screen.getByTestId('promotion-run-workflows-error')).toBeInTheDocument()
        expect(screen.getByText('Boom')).toBeInTheDocument()
        expect(screen.queryByText('No workflow was triggered by this promotion.')).not.toBeInTheDocument()
    })

    it('shows no count when the query fails', () => {
        setResult({data: null, error: 'Boom', finished: true})
        render(<PromotionRunWorkflows promotionRunId={1}/>)
        expect(screen.getByText('Workflows')).toBeInTheDocument()
        expect(screen.queryByText('Workflows (0)')).not.toBeInTheDocument()
    })

    it('shows no count before the first fetch resolves', () => {
        setResult({data: [], finished: false, loading: true})
        render(<PromotionRunWorkflows promotionRunId={1}/>)
        expect(screen.queryByText('Workflows (0)')).not.toBeInTheDocument()
    })
})
