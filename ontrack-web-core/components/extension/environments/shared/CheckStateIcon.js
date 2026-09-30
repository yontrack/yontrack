import {FaHourglassHalf, FaSpinner} from "react-icons/fa"
import CheckIcon from "@components/common/CheckIcon"

/**
 * The icon of a deployment check, telling a check still waiting for something from one which failed.
 *
 * `OK` and `FAILED` are `CheckIcon`'s green tick and red cross, test ids included. `PENDING` (#1937)
 * is neither: a spinner when something is actually running - a workflow - and an hourglass when the
 * deployment is waiting for something to start or for somebody to answer.
 *
 * @param {string} id Prefix of the test id: `${id}-ok`, `${id}-nok` or `${id}-pending`
 * @param {string} state `OK`, `PENDING` or `FAILED`
 * @param {boolean} running Whether a pending check is running rather than waiting
 */
export default function CheckStateIcon({id = "check", state, running = false}) {
    if (state === 'PENDING') {
        return running ?
            <FaSpinner
                data-testid={`${id}-pending`}
                color="#1677ff"
                className="anticon-spin"
                role="img"
                aria-label="Running"
            /> :
            <FaHourglassHalf
                data-testid={`${id}-pending`}
                color="#1677ff"
                role="img"
                aria-label="Waiting"
            />
    }
    return <CheckIcon id={id} value={state === 'OK'}/>
}
