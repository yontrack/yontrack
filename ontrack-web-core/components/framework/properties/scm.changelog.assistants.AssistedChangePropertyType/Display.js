import {Space, Tag, Typography} from "antd";
import {FaRobot} from "react-icons/fa";

const basisLabels = {
    COMPUTED: "computed from the change log",
    SET_BY_CI: "set by the CI",
}

/**
 * Assisted change of a build: its assistants and its commit counts, or why it is unknown.
 */
export default function Display({property}) {
    const value = property.value
    if (value.basis === 'UNKNOWN') {
        return <Typography.Text type="secondary" data-testid="assisted-change-unknown">
            Unknown{value.unknownReason ? `: ${value.unknownReason}` : ''}
        </Typography.Text>
    }
    const assistants = value.assistants ?? []
    const sessionLinks = value.sessionLinks ?? []
    const counts = `${value.assistedCommits} of ${value.totalCommits} commits`
    return (
        <Space orientation="vertical" size={4}>
            {
                assistants.length > 0 ?
                    <Space data-testid="assisted-change-assisted">
                        <FaRobot aria-hidden="true"/>
                        <Typography.Text>Assisted by {assistants.join(', ')} ({counts})</Typography.Text>
                    </Space> :
                    <Typography.Text data-testid="assisted-change-not-assisted">
                        Not assisted ({counts})
                    </Typography.Text>
            }
            {
                basisLabels[value.basis] &&
                <Tag data-testid="assisted-change-basis">{basisLabels[value.basis]}</Tag>
            }
            {
                sessionLinks.map((link, index) => (
                    <Typography.Link
                        key={link}
                        href={link}
                        target="_blank"
                        rel="noopener noreferrer"
                    >
                        Agent session {sessionLinks.length > 1 ? index + 1 : ''}
                    </Typography.Link>
                ))
            }
        </Space>
    )
}
