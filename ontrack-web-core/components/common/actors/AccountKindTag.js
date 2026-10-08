import {Tag} from "antd";
import {FaRobot, FaUser} from "react-icons/fa";

/**
 * The kind of an account, as a tag (#2032): "Agent" or "Person".
 *
 * The word carries the kind: the icon is hidden from assistive technologies, and the purple of an
 * agent only doubles the word, as on the badge of an agent (`ActorBadge`).
 *
 * @param kind `Account.kind` - `AGENT` or `HUMAN`. Nothing is rendered for anything else.
 * @param testId Test ID
 */
export default function AccountKindTag({kind, testId}) {
    if (kind === 'AGENT') {
        return (
            <Tag color="purple" icon={<FaRobot aria-hidden="true"/>} data-testid={testId}>
                Agent
            </Tag>
        )
    } else if (kind === 'HUMAN') {
        return (
            <Tag icon={<FaUser aria-hidden="true"/>} data-testid={testId}>
                Person
            </Tag>
        )
    } else {
        return null
    }
}
