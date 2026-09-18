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

// The build link and the promotion image both reach for things a DOM-less render has no use for.
jest.mock("../../../../components/builds/BuildLink", () => ({
    __esModule: true,
    default: ({build}) => <span>{build?.name}</span>,
}))
jest.mock("../../../../components/promotionLevels/PromotionLevelImage", () => ({
    __esModule: true,
    PromotionLevelImage: ({promotionLevel}) => <span>{promotionLevel?.name}</span>,
}))

import DeploymentHeader from "@components/extension/environments/deployment/DeploymentHeader"

const granted = (name, action) => ({name, action, authorized: true})
const refused = (name, action) => ({name, action, authorized: false})

const deployment = ({
                        status = 'CANDIDATE',
                        runAction = {ok: true},
                        finishAction = {ok: true},
                        changes = [],
                        authorizations = [granted('pipeline', 'create')],
                    } = {}) => ({
    id: 'p-1',
    number: 3,
    status,
    start: '2026-09-18T10:00:00',
    end: null,
    errorMessage: null,
    runAction,
    finishAction,
    changes,
    build: {id: 1, name: '107', branch: {id: 1, name: 'main'}, promotionRuns: []},
    slot: {id: 'slot-1', authorizations},
})

const header = (props) => render(
    <DeploymentHeader
        deployment={deployment(props)}
        acting={false}
        onStart={jest.fn()}
        onFinish={jest.fn()}
        onCancel={jest.fn()}
        refreshedAt={Date.now()}
        refresh={jest.fn()}
    />
)

describe('the deployment header', () => {

    /*
     * The decision this file exists for: **one** primary action. The old page grew a Run button, a
     * Finish button and a Cancel button which appeared among the rules according to the status; at
     * any moment a deployment has exactly one way forward, and the bar names it.
     */
    it('offers Start, and only Start, on a candidate', () => {
        header({status: 'CANDIDATE'})
        expect(screen.getByTestId('deployment-start')).toBeInTheDocument()
        expect(screen.queryByTestId('deployment-finish')).not.toBeInTheDocument()
    })

    it('offers Finish, and only Finish, on a running deployment', () => {
        header({status: 'RUNNING'})
        expect(screen.getByTestId('deployment-finish')).toBeInTheDocument()
        expect(screen.queryByTestId('deployment-start')).not.toBeInTheDocument()
    })

    it('disables the action while something is blocking, because it will become available', () => {
        header({status: 'CANDIDATE', runAction: {ok: false}})
        expect(screen.getByTestId('deployment-start')).toBeDisabled()
    })

    it('enables it once nothing is', () => {
        header({status: 'CANDIDATE', runAction: {ok: true}})
        expect(screen.getByTestId('deployment-start')).toBeEnabled()
    })

    /*
     * Hidden, not disabled. A disabled button promises an action which will become available; for a
     * user without the right it never will, and the rest of Yontrack hides those.
     */
    it('hides every action from a user who may not act', () => {
        header({status: 'CANDIDATE', authorizations: [refused('pipeline', 'create')]})
        expect(screen.queryByTestId('deployment-actions')).not.toBeInTheDocument()
        expect(screen.queryByTestId('deployment-start')).not.toBeInTheDocument()
        expect(screen.queryByTestId('deployment-cancel')).not.toBeInTheDocument()
    })

    it('offers nothing at all on a finished deployment', () => {
        header({status: 'DONE'})
        expect(screen.queryByTestId('deployment-actions')).not.toBeInTheDocument()
    })

    it('offers nothing at all on a cancelled one', () => {
        header({status: 'CANCELLED'})
        expect(screen.queryByTestId('deployment-actions')).not.toBeInTheDocument()
    })

    it('draws Cancelled in place of the step the deployment never reached', () => {
        header({status: 'CANCELLED', changes: []})
        expect(screen.getByTestId('deployment-step-CANCELLED')).toBeInTheDocument()
        expect(screen.queryByTestId('deployment-step-DONE')).not.toBeInTheDocument()
    })
})
