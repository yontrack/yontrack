import {Button, message, Popconfirm, Space} from "antd";
import {FaTrash} from "react-icons/fa";
import {callGraphQL} from "@components/services/GraphQL";
import {useState} from "react";
import {gql} from "graphql-request";
import {processGraphQLErrors} from "@components/services/graphql-utils";

export default function QueuePurgeButton({onDone}) {

    const [messageApi, contextHolder] = message.useMessage()

    const [loading, setLoading] = useState(false)
    const purge = async () => {
        setLoading(true)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation QueuePurgeRecords {
                        purgeQueueRecordings {
                            errors {
                                message
                            }
                        }
                    }
                `,
            })
            if (processGraphQLErrors(data, 'purgeQueueRecordings', messageApi) && onDone) {
                onDone()
            }
        } finally {
            setLoading(false)
        }
    }

    return (
        <>
            {contextHolder}
            <Popconfirm
                title="Purging the queue records"
                description="Do you really want to remove all records of all messages in all queues?"
                onConfirm={purge}
            >
                <Button
                    type="default"
                    color="danger"
                    variant="solid"
                    loading={loading}
                    disabled={loading}
                >
                    <Space>
                        <FaTrash/>
                        Purge
                    </Space>
                </Button>
            </Popconfirm>
        </>
    )
}