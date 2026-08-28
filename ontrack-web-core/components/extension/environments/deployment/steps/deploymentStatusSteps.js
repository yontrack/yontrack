import SlotPipelineStatusIcon from "@components/extension/environments/SlotPipelineStatusIcon";
import {List, Space, Typography} from "antd";
import {slotPipelineStatusLabels} from "@components/extension/environments/SlotPipelineStatusLabel";
import TimestampText from "@components/common/TimestampText";
import {FaExclamationTriangle} from "react-icons/fa";

export const findChange = (deployment, status) => deployment.changes.find(it => it.type === 'STATUS' && it.status === status)

export function DeploymentErrorStep({deployment, errorMessage}) {
    if (deployment.errorMessage) {
        return <DeploymentStep
            id={`deployment-error-${deployment.id}`}
            avatar={<FaExclamationTriangle color="red"/>}
            title={deployment.errorMessage}
            description=""
            style={{background: "var(--ot-bg-error-subtle)"}}
        />
    } else {
        return undefined
    }
}

function DeploymentStatusSignature({deployment, status}) {
    const change = findChange(deployment, status)
    return (
        <>
            {
                change &&
                <Typography.Text type="secondary">
                    <TimestampText value={change.timestamp}/> by {change.user}
                </Typography.Text>
            }
        </>
    )
}

function DeploymentStatusMessage({deployment, status}) {
    const change = findChange(deployment, status)
    return (
        <>
            {
                change && change.overrideMessage &&
                <Space>
                    <FaExclamationTriangle color="red"/>
                    <Typography.Text strong italic data-testid={`${deployment.id}-status-message-${status}`}>
                        {change.overrideMessage}
                    </Typography.Text>
                </Space>
            }
        </>
    )
}

export function DeploymentStep({id, avatar, title, description, style = {}}) {
    return (
        <>
            <List.Item className="ot-list-item" data-testid={id} style={{paddingLeft: "3em", ...style}}>
                <List.Item.Meta
                    avatar={avatar}
                    title={title}
                    description={description}
                />
            </List.Item>
        </>
    )
}

function DeploymentStatusStep({deployment, status}) {
    return (
        <>
            <List.Item
                className="ot-list-item"
                data-testid={`${deployment.id}-status-${status}`}
                style={{
                    background: "var(--ot-bg-neutral-subtle)",
                    paddingLeft: "1em",
                }}
            >
                <Space>
                    <SlotPipelineStatusIcon status={status}/>
                    <Typography.Text strong>{slotPipelineStatusLabels[status]}</Typography.Text>
                    <Space>
                        <DeploymentStatusSignature deployment={deployment} status={status}/>
                        <DeploymentStatusMessage deployment={deployment} status={status}/>
                    </Space>
                </Space>
            </List.Item>
        </>
    )
}

export function DeploymentCandidateStatusStep({deployment}) {
    return <DeploymentStatusStep
        deployment={deployment}
        status="CANDIDATE"
    />
}

export function DeploymentRunningStatusStep({deployment}) {
    return <DeploymentStatusStep
        deployment={deployment}
        status="RUNNING"
    />
}

export function DeploymentDoneStatusStep({deployment}) {
    return <DeploymentStatusStep
        deployment={deployment}
        status="DONE"
    />
}

export function DeploymentCancelledStatusStep({deployment}) {
    return <DeploymentStatusStep
        deployment={deployment}
        status="CANCELLED"
    />
}