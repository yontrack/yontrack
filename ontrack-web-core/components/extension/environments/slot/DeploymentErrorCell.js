import {Tooltip} from "antd"
import {FaBan, FaExclamationCircle, FaHourglassHalf} from "react-icons/fa"

/**
 * The "Error" column of a slot's deployments.
 *
 * Red only for an actual error (#1937): a deployment waiting for a workflow still running, or for
 * an approval nobody has given yet, is not in error, and is drawn as in progress instead.
 *
 * @param {Object} deployment The deployment, with its `errorMessage` and `pendingMessage`.
 */
export default function DeploymentErrorCell({deployment}) {
    const id = `deployment-error-${deployment.id}`
    if (deployment.errorMessage) {
        return <Tooltip title={deployment.errorMessage}>
            <FaExclamationCircle color="red" data-testid={`${id}-error`} role="img" aria-label="Error"/>
        </Tooltip>
    }
    if (deployment.pendingMessage) {
        return <Tooltip title={deployment.pendingMessage}>
            <FaHourglassHalf color="#1677ff" data-testid={`${id}-pending`} role="img" aria-label="In progress"/>
        </Tooltip>
    }
    return <Tooltip title="No error">
        <FaBan color="gray" data-testid={`${id}-none`}/>
    </Tooltip>
}
