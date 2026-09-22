import {useEffect, useState} from "react";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {gqlNotificationRecordContent} from "@components/extension/notifications/NotificationRecordsGraphQLFragments";
import LoadingInline from "@components/common/LoadingInline";
import {Divider, Space} from "antd";
import Link from "next/link";
import {FaInfoCircle} from "react-icons/fa";
import NotificationSourceData from "@components/extension/notifications/NotificationSourceData";
import EventDetails from "@components/core/model/EventDetails";

export default function NotificationRecordSummary({recordId}) {

    const client = useGraphQLClient()
    const [record, setRecord] = useState({})
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        if (client) {
            setLoading(true)
            client.request(
                gql`
                    query NotificationRecord($recordId: String!) {
                        notificationRecord(id: $recordId) {
                            ...NotificationRecordContent
                        }
                    }

                    ${gqlNotificationRecordContent}
                `,
                {recordId}
            ).then(data => {
                setRecord(data.notificationRecord)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, recordId])

    return (
        <>
            <LoadingInline loading={loading}>
                <Space>
                    {/* Link to the record */}
                    <Link href={`/extension/notifications/recordings/${record.id}`}
                          title="Link to the full notification record">
                        <FaInfoCircle/>
                    </Link>
                    {/* Notification source */}
                    {
                        record.source && <>
                            <Divider orientation="vertical"/>
                            <NotificationSourceData source={record.source}/>
                        </>
                    }
                    {/* Event */}
                    {
                        record.event && <>
                            <Divider orientation="vertical"/>
                            <EventDetails event={record.event}/>
                        </>
                    }
                </Space>
            </LoadingInline>
        </>
    )
}