import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import DeploymentErrorCell from "@components/extension/environments/slot/DeploymentErrorCell"

/**
 * The "Error" column of a slot's deployments (#1937): a deployment waiting for a workflow to run,
 * or for an approval, has no error and must not be drawn with one.
 */
describe('the error cell of a deployment', () => {

    it('shows an error as an error', () => {
        render(<DeploymentErrorCell deployment={{id: 'p-1', errorMessage: 'Workflow is in error'}}/>)
        expect(screen.getByTestId('deployment-error-p-1-error')).toBeInTheDocument()
    })

    it('shows a pending check as in progress, not as an error', () => {
        render(<DeploymentErrorCell deployment={{id: 'p-1', errorMessage: null, pendingMessage: 'Workflow is running'}}/>)
        expect(screen.getByTestId('deployment-error-p-1-pending')).toBeInTheDocument()
        expect(screen.queryByTestId('deployment-error-p-1-error')).not.toBeInTheDocument()
    })

    it('shows nothing wrong when there is nothing', () => {
        render(<DeploymentErrorCell deployment={{id: 'p-1', errorMessage: null, pendingMessage: null}}/>)
        expect(screen.getByTestId('deployment-error-p-1-none')).toBeInTheDocument()
    })
})
