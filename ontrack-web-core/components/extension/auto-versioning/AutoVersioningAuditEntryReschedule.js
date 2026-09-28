import {Button, Popconfirm, Space, Typography} from "antd";
import {FaRedo} from "react-icons/fa";
import {useMutation} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {useRouter} from "next/router";

export default function AutoVersioningAuditEntryReschedule({entry}) {

    const router = useRouter()

    const {mutate, loading} = useMutation(
        gql`
            mutation RescheduleAutoVersioningAuditEntry($uuid: String!) {
                rescheduleAutoVersioning(input: {uuid: $uuid}) {
                    order {
                        uuid
                    }
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'rescheduleAutoVersioning',
            onSuccess: async (userNode) => {
                await router.push(`/extension/auto-versioning/audit/detail/${userNode.order.uuid}`)
            }
        }
    )

    const reschedule = async () => {
        await mutate({uuid: entry.order.uuid})
    }

    const retryMaxCount = entry.lineage?.configuredRetryMaxCount ?? 0
    const description = (
        <Space direction="vertical" data-testid="av-reschedule-confirmation">
            <Typography.Text>This creates a new auto-versioning order.</Typography.Text>
            {
                retryMaxCount > 0 ?
                    <Typography.Text>
                        Its automatic retry count starts again from 0 (max {retryMaxCount}).
                    </Typography.Text> :
                    <Typography.Text>
                        Automatic retries are disabled: it will not be retried automatically if it fails.
                    </Typography.Text>
            }
        </Space>
    )

    return (
        <>
            <Popconfirm
                title="Reschedule auto-versioning"
                description={description}
                okText="Yes"
                cancelText="No"
                onConfirm={reschedule}
            >
                <Button disabled={loading} icon={<FaRedo/>}>
                    <Typography.Text>Reschedule</Typography.Text>
                </Button>
            </Popconfirm>
        </>
    )
}