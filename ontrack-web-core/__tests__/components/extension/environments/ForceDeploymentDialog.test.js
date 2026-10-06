import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"

// antd reads the responsive breakpoints; jsdom ships no `matchMedia`.
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

import ForceDeploymentDialog, {
    useForceDeploymentDialog
} from "@components/extension/environments/ForceDeploymentDialog"

/**
 * Forcing a deployment (#1738): the backend refuses some forcings — a deployment superseded on
 * its slot — through `finishStatus`, not through `errors`, and the dialog must not report them as
 * a success.
 */

const DEPLOYMENT = {id: 'pipeline-1'}

const answer = (payload) => {
    global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: async () => ({finishSlotPipelineDeployment: payload}),
    })
}

const sentRequest = () => JSON.parse(global.fetch.mock.calls[0][1].body)

function Harness({onForced}) {
    const dialog = useForceDeploymentDialog({onForced})
    return (
        <>
            <button onClick={() => dialog.start({deployment: DEPLOYMENT})}>Force</button>
            <ForceDeploymentDialog dialog={dialog}/>
        </>
    )
}

const force = async (onForced) => {
    render(<Harness onForced={onForced}/>)
    fireEvent.click(screen.getByRole('button', {name: 'Force'}))
    fireEvent.change(await screen.findByLabelText('Message'), {target: {value: 'Deployed by hand'}})
    fireEvent.click(screen.getByRole('button', {name: 'OK'}))
    await waitFor(() => expect(global.fetch).toHaveBeenCalled())
}

afterEach(() => {
    delete global.fetch
})

describe('forcing a deployment', () => {

    it('asks for the finish status', async () => {
        answer({errors: [], finishStatus: {ok: true, message: null}})
        await force(jest.fn())
        expect(sentRequest().query).toMatch(/finishStatus\s*\{\s*ok\s+message\s*}/)
        expect(sentRequest().variables).toEqual({deploymentId: 'pipeline-1', message: 'Deployed by hand'})
    })

    it('closes and reports the forcing when the backend accepts it', async () => {
        answer({errors: [], finishStatus: {ok: true, message: null}})
        const onForced = jest.fn()
        await force(onForced)
        await waitFor(() => expect(onForced).toHaveBeenCalled())
    })

    it('stays open and shows the refusal when the backend refuses it', async () => {
        answer({errors: [], finishStatus: {ok: false, message: 'Only the last pipeline can be deployed.'}})
        const onForced = jest.fn()
        await force(onForced)
        expect(await screen.findByText('Only the last pipeline can be deployed.')).toBeInTheDocument()
        expect(onForced).not.toHaveBeenCalled()
        expect(screen.getByLabelText('Message')).toBeInTheDocument()
    })

    it('shows the GraphQL errors', async () => {
        answer({errors: [{message: 'Not authorized.'}], finishStatus: null})
        const onForced = jest.fn()
        await force(onForced)
        expect(await screen.findByText('Not authorized.')).toBeInTheDocument()
        expect(onForced).not.toHaveBeenCalled()
    })
})
