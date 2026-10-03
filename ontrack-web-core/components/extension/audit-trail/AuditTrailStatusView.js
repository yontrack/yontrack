import {gql} from "graphql-request";
import {Alert, Descriptions, Space, Tag, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import LoadingContainer from "@components/common/LoadingContainer";
import PageSection from "@components/common/PageSection";
import TimestampText from "@components/common/TimestampText";

export const gqlAuditTrailStatus = gql`
    query AuditTrailStatus {
        auditTrailStatus {
            licensed
            storage {
                state
                message
                checkedAt
                endpoint
                bucket
                region
                pathStyle
            }
            keyStatus
            keys {
                keyId
                algorithm
                publicKey
            }
        }
    }
`

/**
 * How each state of the evidence storage reads, and its colour.
 */
const STORAGE_STATES = {
    OK: {text: 'OK', color: 'success'},
    UNREACHABLE: {text: 'Unreachable', color: 'error'},
    NOT_CONFIGURED: {text: 'Not configured', color: 'warning'},
}

/**
 * How each status of the instance key reads, and its colour.
 */
const KEY_STATUSES = {
    OK: {text: 'Provisioned', color: 'success'},
    NOT_PROVISIONED: {text: 'Not provisioned', color: 'error'},
}

const StateTag = ({states, state, testId}) => {
    const {text, color} = states[state] ?? {text: state, color: 'default'}
    return <Tag color={color} data-testid={testId}>{text}</Tag>
}

const NotSet = () => <Typography.Text type="secondary">Not set</Typography.Text>

/**
 * The audit trail status, read-only: the licence, the evidence storage and the instance key.
 */
export default function AuditTrailStatusView() {

    const {data: status, loading, finished, error} = useQuery(gqlAuditTrailStatus, {
        dataFn: data => data.auditTrailStatus,
    })

    if (error) {
        return <Alert type="error" showIcon title={error}/>
    }

    const licenceItems = status ? [
        {
            key: 'licence',
            label: "Licence",
            children: status.licensed ?
                <Tag color="success" data-testid="audit-trail-licence">Enabled</Tag> :
                <Tag color="default" data-testid="audit-trail-licence">Disabled</Tag>,
        },
    ] : []

    const storage = status?.storage
    const storageItems = storage ? [
        {
            key: 'state',
            label: "State",
            children: <Space orientation="vertical">
                <StateTag states={STORAGE_STATES} state={storage.state} testId="audit-trail-storage-state"/>
                {
                    storage.message &&
                    <Typography.Text data-testid="audit-trail-storage-message">{storage.message}</Typography.Text>
                }
            </Space>,
        },
        {
            key: 'checkedAt',
            label: "Checked at",
            children: <TimestampText value={storage.checkedAt} format="YYYY MMM DD, HH:mm:ss"/>,
        },
        {
            key: 'endpoint',
            label: "Endpoint",
            children: <span data-testid="audit-trail-storage-endpoint">
                {storage.endpoint ? <Typography.Text code>{storage.endpoint}</Typography.Text> : <NotSet/>}
            </span>,
        },
        {
            key: 'bucket',
            label: "Bucket",
            children: <span data-testid="audit-trail-storage-bucket">
                {storage.bucket ? <Typography.Text code>{storage.bucket}</Typography.Text> : <NotSet/>}
            </span>,
        },
        {
            key: 'addressing',
            label: "Addressing",
            children: <Typography.Text data-testid="audit-trail-storage-addressing">
                {storage.pathStyle ? 'Path-style' : 'Virtual-hosted'}, region {storage.region}
            </Typography.Text>,
        },
    ] : []

    const key = status?.keys?.[0]
    const keyItems = status ? [
        {
            key: 'status',
            label: "Status",
            children: <StateTag states={KEY_STATUSES} state={status.keyStatus} testId="audit-trail-key-status"/>,
        },
        {
            key: 'keyId',
            label: "Key ID",
            children: <span data-testid="audit-trail-key-id">
                {
                    key ?
                        <Typography.Text code copyable>{key.keyId}</Typography.Text> :
                        <Typography.Text type="secondary">No key</Typography.Text>
                }
            </span>,
        },
        ...(key ? [{
            key: 'publicKey',
            label: `Public key (${key.algorithm})`,
            children: <Typography.Paragraph copyable={{text: key.publicKey}} data-testid="audit-trail-public-key">
                <pre style={{margin: 0}}>{key.publicKey}</pre>
            </Typography.Paragraph>,
        }] : []),
    ] : []

    return (
        <LoadingContainer loading={loading || !finished}>
            <Space orientation="vertical" className="ot-line">
                <PageSection title="Licence" padding={true}>
                    <Descriptions items={licenceItems} bordered={true} column={1}/>
                </PageSection>
                <PageSection title="Evidence storage" padding={true}>
                    <Descriptions items={storageItems} bordered={true} column={1}/>
                </PageSection>
                <PageSection title="Instance key" padding={true}>
                    <Descriptions items={keyItems} bordered={true} column={1}/>
                </PageSection>
            </Space>
        </LoadingContainer>
    )
}
