import React from "react";
import {act, render, screen, waitFor} from "@testing-library/react";
import '@testing-library/jest-dom';
import BuildLinksView from "@components/links/BuildLinksView";

let mockStoredMode = undefined
const mockSetStoredMode = jest.fn()

jest.mock("../../../components/storage/local", () => ({
    getLocallySelectedDependencyLinksMode: () => mockStoredMode,
    setLocallySelectedDependencyLinksMode: (mode) => mockSetStoredMode(mode),
}))

jest.mock("next/head", () => function Head() {
    return null
})

jest.mock("../../../components/layouts/MainPage", () => function MainPage({commands, children}) {
    return (
        <div>
            <div data-testid="commands">{commands.length}</div>
            {children}
        </div>
    )
})

jest.mock("../../../components/common/Breadcrumbs", () => ({
    downToBuildBreadcrumbs: () => [],
}))

jest.mock("../../../components/links/DependencyLinksModeButton", () => function DependencyLinksModeButton({selectedMode, mode, action}) {
    return selectedMode && selectedMode !== mode ? <button onClick={() => action(mode)}>{mode}</button> : null
})

jest.mock("../../../components/links/BuildLinksGraph", () => function BuildLinksGraph({build}) {
    return <span data-testid="graph">{build.name}</span>
})

jest.mock("../../../components/links/BuildLinksTree", () => function BuildLinksTree({build}) {
    return <span data-testid="tree">{build.name}</span>
})

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

const build = {
    id: 100,
    name: '1.0.0',
    branch: {id: 10, name: 'main', project: {id: 1, name: 'my-project'}},
    releaseProperty: null,
}

describe('BuildLinksView', () => {

    afterEach(() => {
        delete global.fetch
        mockStoredMode = undefined
        mockSetStoredMode.mockReset()
    })

    it('shows the graph by default once the build is loaded', async () => {
        const pending = mockGraphQL()

        render(<BuildLinksView id="100"/>)

        expect(screen.getByTestId('commands')).toHaveTextContent('0')
        expect(screen.queryByTestId('graph')).not.toBeInTheDocument()

        await waitFor(() => expect(pending).toHaveLength(1))
        expect(JSON.parse(global.fetch.mock.calls[0][1].body).variables).toEqual({id: 100})
        await act(async () => pending[0]({build}))

        expect(await screen.findByTestId('graph')).toHaveTextContent('1.0.0')
        expect(screen.getByTestId('commands')).toHaveTextContent('1')
    })

    it('shows the mode stored locally, and stores the one chosen', async () => {
        mockStoredMode = 'tree'
        const pending = mockGraphQL()

        render(<BuildLinksView id="100"/>)
        await waitFor(() => expect(pending).toHaveLength(1))
        await act(async () => pending[0]({build}))

        expect(await screen.findByTestId('tree')).toBeInTheDocument()

        await act(async () => screen.getByText('graph').click())
        expect(screen.getByTestId('graph')).toBeInTheDocument()
        expect(mockSetStoredMode).toHaveBeenCalledWith('graph')
    })

})
